package edu.ucsal.fiadopay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

@Service
public class PaymentService {
  private static final Logger logger = Logger.getLogger(PaymentService.class.getName());
  
  private final MerchantRepository merchants;
  private final PaymentRepository payments;
  private final WebhookDeliveryRepository deliveries;
  private final ObjectMapper objectMapper;
  private final PaymentMethodValidator paymentMethodValidator;
  private final AntiFraudService antiFraudService;
  
  // ExecutorService para processamento assíncrono
  private final ExecutorService paymentProcessorExecutor;
  private final ExecutorService webhookExecutor;
  private final ScheduledExecutorService webhookRetryExecutor;

  @Value("${fiadopay.webhook-secret}") String secret;
  @Value("${fiadopay.processing-delay-ms}") long delay;
  @Value("${fiadopay.failure-rate}") double failRate;

  public PaymentService(
      MerchantRepository merchants, 
      PaymentRepository payments, 
      WebhookDeliveryRepository deliveries, 
      ObjectMapper objectMapper,
      PaymentMethodValidator paymentMethodValidator,
      AntiFraudService antiFraudService) {
    this.merchants = merchants;
    this.payments = payments;
    this.deliveries = deliveries;
    this.objectMapper = objectMapper;
    this.paymentMethodValidator = paymentMethodValidator;
    this.antiFraudService = antiFraudService;
    
    // Inicializar ExecutorServices com pool de threads
    this.paymentProcessorExecutor = Executors.newFixedThreadPool(4);
    this.webhookExecutor = Executors.newFixedThreadPool(8);
    this.webhookRetryExecutor = Executors.newScheduledThreadPool(2);
  }

  private Merchant merchantFromAuth(String auth){
    if (auth == null || !auth.startsWith("Bearer FAKE-")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    var raw = auth.substring("Bearer FAKE-".length());
    long id;
    try {
      id = Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    var merchant = merchants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (merchant.getStatus() != Merchant.Status.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return merchant;
  }

  @Transactional
  public PaymentResponse createPayment(String auth, String idemKey, PaymentRequest req){
    var merchant = merchantFromAuth(auth);
    var mid = merchant.getId();

    if (idemKey != null) {
      var existing = payments.findByIdempotencyKeyAndMerchantId(idemKey, mid);
      if(existing.isPresent()) return toResponse(existing.get());
    }

    // Validar método de pagamento usando reflexão
    if (!paymentMethodValidator.isMethodSupported(req.method())) {
      logger.warning("Método de pagamento não suportado: " + req.method());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Método de pagamento não suportado");
    }

    // Validar número de parcelas
    int installments = req.installments() == null ? 1 : req.installments();
    if (!paymentMethodValidator.isInstallmentsValid(req.method(), installments)) {
      logger.warning("Parcelas inválidas para método: " + req.method() + ", parcelas: " + installments);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Número de parcelas inválido para este método");
    }

    // Validar anti-fraude
    if (!antiFraudService.validatePayment(req)) {
      logger.warning("Pagamento rejeitado por anti-fraude: " + req.amount());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagamento rejeitado por validação anti-fraude");
    }

    Double interest = null;
    BigDecimal total = req.amount();
    if ("CARD".equalsIgnoreCase(req.method()) && installments > 1){
      interest = 1.0; // 1%/mês
      var base = new BigDecimal("1.01");
      var factor = base.pow(installments);
      total = req.amount().multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    var payment = Payment.builder()
        .id("pay_"+UUID.randomUUID().toString().substring(0,8))
        .merchantId(mid)
        .method(req.method().toUpperCase())
        .amount(req.amount())
        .currency(req.currency())
        .installments(installments)
        .monthlyInterest(interest)
        .totalWithInterest(total)
        .status(Payment.Status.PENDING)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .idempotencyKey(idemKey)
        .metadataOrderId(req.metadataOrderId())
        .build();

    payments.save(payment);
    logger.info("Pagamento criado: " + payment.getId());

    // Submeter para processamento assíncrono usando ExecutorService
    paymentProcessorExecutor.submit(() -> processAndWebhook(payment.getId()));

    return toResponse(payment);
  }

  public PaymentResponse getPayment(String id){
    return toResponse(payments.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
  }

  public Map<String,Object> refund(String auth, String paymentId){
    var merchant = merchantFromAuth(auth);
    var p = payments.findById(paymentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!merchant.getId().equals(p.getMerchantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    p.setStatus(Payment.Status.REFUNDED);
    p.setUpdatedAt(Instant.now());
    payments.save(p);
    sendWebhook(p);
    return Map.of("id","ref_"+UUID.randomUUID(),"status","PENDING");
  }

  /**
   * Processa o pagamento de forma assíncrona usando ScheduledExecutorService.
   * Substitui Thread.sleep() por agendamento com delay.
   */
  private void processAndWebhook(String paymentId){
    // Usar ScheduledExecutorService em vez de Thread.sleep()
    webhookRetryExecutor.schedule(() -> {
      try {
        var p = payments.findById(paymentId).orElse(null);
        if (p == null) {
          logger.warning("Pagamento não encontrado: " + paymentId);
          return;
        }

        var approved = Math.random() > failRate;
        p.setStatus(approved ? Payment.Status.APPROVED : Payment.Status.DECLINED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);
        
        logger.info("Pagamento processado: " + paymentId + " - Status: " + p.getStatus());

        sendWebhook(p);
      } catch (Exception e) {
        logger.severe("Erro ao processar pagamento: " + e.getMessage());
      }
    }, delay, TimeUnit.MILLISECONDS);
  }

  /**
   * Envia webhook para o merchant.
   * Anotação @WebhookSink marca este método como disparador de eventos.
   */
  @WebhookSink(eventType = "payment.updated", description = "Envia webhook quando pagamento é atualizado")
  private void sendWebhook(Payment p){
    var merchant = merchants.findById(p.getMerchantId()).orElse(null);
    if (merchant==null || merchant.getWebhookUrl()==null || merchant.getWebhookUrl().isBlank()) {
      logger.info("Webhook não configurado para merchant: " + p.getMerchantId());
      return;
    }

    String payload;
    try {
      var data = Map.of(
          "paymentId", p.getId(),
          "status", p.getStatus().name(),
          "occurredAt", Instant.now().toString()
      );
      var event = Map.of(
          "id", "evt_"+UUID.randomUUID().toString().substring(0,8),
          "type", "payment.updated",
          "data", data
      );
      payload = objectMapper.writeValueAsString(event);
    } catch (Exception e) {
      logger.severe("Erro ao serializar webhook: " + e.getMessage());
      return;
    }

    var signature = hmac(payload, secret);

    var delivery = deliveries.save(WebhookDelivery.builder()
        .eventId("evt_"+UUID.randomUUID().toString().substring(0,8))
        .eventType("payment.updated")
        .paymentId(p.getId())
        .targetUrl(merchant.getWebhookUrl())
        .signature(signature)
        .payload(payload)
        .attempts(0)
        .delivered(false)
        .lastAttemptAt(null)
        .build());

    logger.info("Webhook criado para entrega: " + delivery.getId());
    
    // Submeter para entrega assíncrona usando ExecutorService
    webhookExecutor.submit(() -> tryDeliver(delivery.getId()));
  }

  /**
   * Tenta entregar webhook com retry usando ScheduledExecutorService.
   * Substitui Thread.sleep() e recursão por agendamento com backoff exponencial.
   */
  private void tryDeliver(Long deliveryId){
    var d = deliveries.findById(deliveryId).orElse(null);
    if (d == null) {
      logger.warning("Entrega de webhook não encontrada: " + deliveryId);
      return;
    }
    
    try {
      var client = HttpClient.newHttpClient();
      var req = HttpRequest.newBuilder(URI.create(d.getTargetUrl()))
        .header("Content-Type","application/json")
        .header("X-Event-Type", d.getEventType())
        .header("X-Signature", d.getSignature())
        .POST(HttpRequest.BodyPublishers.ofString(d.getPayload()))
        .build();
      
      var res = client.send(req, HttpResponse.BodyHandlers.ofString());
      d.setAttempts(d.getAttempts() + 1);
      d.setLastAttemptAt(Instant.now());
      d.setDelivered(res.statusCode() >= 200 && res.statusCode() < 300);
      deliveries.save(d);
      
      logger.info("Webhook entregue: " + deliveryId + " - Status: " + res.statusCode() + " - Tentativa: " + d.getAttempts());
      
      // Se não foi entregue e ainda há tentativas, agendar retry com backoff exponencial
      if (!d.isDelivered() && d.getAttempts() < 5) {
        long delayMs = 1000L * d.getAttempts(); // Backoff exponencial
        logger.info("Agendando retry para webhook " + deliveryId + " em " + delayMs + "ms");
        webhookRetryExecutor.schedule(() -> tryDeliver(deliveryId), delayMs, TimeUnit.MILLISECONDS);
      }
    } catch (Exception e) {
      logger.severe("Erro ao entregar webhook: " + e.getMessage());
      d.setAttempts(d.getAttempts() + 1);
      d.setLastAttemptAt(Instant.now());
      d.setDelivered(false);
      deliveries.save(d);
      
      // Se ainda há tentativas, agendar retry
      if (d.getAttempts() < 5) {
        long delayMs = 1000L * d.getAttempts();
        logger.info("Agendando retry para webhook " + deliveryId + " em " + delayMs + "ms após erro");
        webhookRetryExecutor.schedule(() -> tryDeliver(deliveryId), delayMs, TimeUnit.MILLISECONDS);
      }
    }
  }

  private static String hmac(String payload, String secret){
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes()));
    } catch (Exception e){ return ""; }
  }

  private PaymentResponse toResponse(Payment p){
    return new PaymentResponse(
        p.getId(), p.getStatus().name(), p.getMethod(),
        p.getAmount(), p.getInstallments(), p.getMonthlyInterest(),
        p.getTotalWithInterest()
    );
  }
  
  /**
   * Shutdown gracioso dos ExecutorServices.
   * Chamado ao desligar a aplicação.
   */
  public void shutdown() {
    logger.info("Encerrando ExecutorServices...");
    paymentProcessorExecutor.shutdown();
    webhookExecutor.shutdown();
    webhookRetryExecutor.shutdown();
    
    try {
      if (!paymentProcessorExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        paymentProcessorExecutor.shutdownNow();
      }
      if (!webhookExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        webhookExecutor.shutdownNow();
      }
      if (!webhookRetryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        webhookRetryExecutor.shutdownNow();
      }
      logger.info("ExecutorServices encerrados com sucesso");
    } catch (InterruptedException e) {
      paymentProcessorExecutor.shutdownNow();
      webhookExecutor.shutdownNow();
      webhookRetryExecutor.shutdownNow();
      logger.severe("Erro ao encerrar ExecutorServices: " + e.getMessage());
    }
  }
}
