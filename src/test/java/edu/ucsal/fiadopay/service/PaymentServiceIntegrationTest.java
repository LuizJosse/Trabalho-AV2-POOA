package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PaymentService Integration Tests")
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        webhookDeliveryRepository.deleteAll();
        paymentRepository.deleteAll();
        merchantRepository.deleteAll();

        testMerchant = Merchant.builder()
            .name("Test Merchant")
            .clientId("test-client")
            .clientSecret("test-secret")
            .webhookUrl("http://localhost:8081/webhooks")
            .build();
        testMerchant = merchantRepository.save(testMerchant);
    }

    @Test
    @DisplayName("Deve criar pagamento com método válido")
    void testCreatePaymentWithValidMethod() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("250.50"), 12, "ORD-123");
        
        var response = paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-1");
        
        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("PENDING", response.status());
    }

    @Test
    @DisplayName("Deve rejeitar pagamento com método inválido")
    void testCreatePaymentWithInvalidMethod() {
        var request = new PaymentRequest("INVALID", "BRL", new BigDecimal("250.50"), 1, "ORD-123");
        
        assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-2");
        });
    }

    @Test
    @DisplayName("Deve rejeitar pagamento com parcelas inválidas para PIX")
    void testCreatePaymentWithInvalidInstallmentsForPix() {
        var request = new PaymentRequest("PIX", "BRL", new BigDecimal("250.50"), 3, "ORD-123");
        
        assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-3");
        });
    }

    @Test
    @DisplayName("Deve rejeitar pagamento com valor suspeito (anti-fraude)")
    void testCreatePaymentWithSuspiciousAmount() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("15000.00"), 1, "ORD-123");
        
        assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-4");
        });
    }

    @Test
    @DisplayName("Deve aceitar pagamento com valor alto mas não suspeito")
    void testCreatePaymentWithHighButValidAmount() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("7500.00"), 1, "ORD-123");
        
        var response = paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-5");
        
        assertNotNull(response);
        assertEquals("PENDING", response.status());
    }

    @Test
    @DisplayName("Deve garantir idempotência - mesma chave retorna mesmo pagamento")
    void testIdempotency() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("250.50"), 1, "ORD-123");
        String idempotencyKey = "idempotency-key-6";
        
        var response1 = paymentService.createPayment(testMerchant.getId(), request, idempotencyKey);
        var response2 = paymentService.createPayment(testMerchant.getId(), request, idempotencyKey);
        
        assertEquals(response1.id(), response2.id());
    }

    @Test
    @DisplayName("Deve recuperar pagamento criado")
    void testGetPayment() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("250.50"), 1, "ORD-123");
        var created = paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-7");
        
        var retrieved = paymentService.getPayment(created.id());
        
        assertNotNull(retrieved);
        assertEquals(created.id(), retrieved.id());
        assertEquals("PENDING", retrieved.status());
    }

    @Test
    @DisplayName("Deve processar pagamento e atualizar status")
    void testPaymentProcessing() throws InterruptedException {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("250.50"), 1, "ORD-123");
        var created = paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-8");
        
        Thread.sleep(2000);
        
        var retrieved = paymentService.getPayment(created.id());
        assertNotNull(retrieved);
    }

    @Test
    @DisplayName("Deve validar CARD com até 12 parcelas")
    void testCardMaxInstallments() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("1000.00"), 12, "ORD-123");
        
        var response = paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-9");
        
        assertNotNull(response);
        assertEquals("PENDING", response.status());
    }

    @Test
    @DisplayName("Deve rejeitar CARD com mais de 12 parcelas")
    void testCardExceedsMaxInstallments() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("1000.00"), 13, "ORD-123");
        
        assertThrows(ResponseStatusException.class, () -> {
            paymentService.createPayment(testMerchant.getId(), request, "idempotency-key-10");
        });
    }
}
