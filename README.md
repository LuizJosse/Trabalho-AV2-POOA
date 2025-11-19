# FiadoPay - Refatoração com Engenharia Avançada

Simulador de gateway de pagamento refatorado para a disciplina AVI/POOA. Implementamos três pilares de engenharia Java avançada:

1. **Anotações Customizadas** - Para marcar comportamentos específicos
2. **Reflexão** - Para validação dinâmica de métodos de pagamento
3. **Threads Profissionais** - ExecutorService para processamento assíncrono (sem `Thread.sleep()` bloqueante)

---

## Anotações Implementadas

Implementamos três anotações customizadas:

- **@PaymentMethod** - Marca métodos de pagamento (CARD, PIX, DEBIT, BOLETO) com limite de parcelas
- **@AntiFraud** - Marca regras anti-fraude com threshold e descrição
- **@WebhookSink** - Marca métodos que disparam eventos de webhook

## Reflexão Implementada

Dois serviços usam Java Reflection API para validação dinâmica:

- **PaymentMethodValidator** - Lê anotações em tempo de execução e valida métodos/parcelas
- **AntiFraudService** - Implementa três regras: alerta (>R$ 5k), rejeita (>R$ 10k), valida parcelas (máx 12)

## Threads Profissionais

Três pools de threads gerenciados:

- **paymentProcessorExecutor** (4 threads) - Processamento de pagamentos
- **webhookExecutor** (8 threads) - Entrega de webhooks
- **webhookRetryExecutor** (2 threads) - Retry com backoff exponencial

Substituímos `Thread.sleep()` bloqueante por `ScheduledExecutorService` não-bloqueante.

---

## Como Rodar

### Pré-requisitos
- Java 21+
- Maven 3.8+

### Iniciar a aplicação

```bash
./mvnw spring-boot:run
# ou no Windows PowerShell:
.\mvnw.cmd spring-boot:run
```

Acessa em `http://localhost:8080`

### Consoles

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 Console:** http://localhost:8080/h2 (user: `sa`, password: vazio)

---

## Fluxo de Teste

### 1. Cadastrar Merchant
```bash
curl -X POST http://localhost:8080/fiadopay/admin/merchants \
  -H "Content-Type: application/json" \
  -d '{"name":"Loja","webhookUrl":"http://localhost:8081/webhooks"}'
```
Retorna: `{"id":"merchant_123","clientId":"abc123","clientSecret":"xyz789"}`

### 2. Obter Token
```bash
curl -X POST http://localhost:8080/fiadopay/auth/token \
  -H "Content-Type: application/json" \
  -d '{"client_id":"abc123","client_secret":"xyz789"}'
```
Retorna: `{"access_token":"FAKE-merchant_123","token_type":"Bearer"}`

### 3. Criar Pagamento
```bash
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-merchant_123" \
  -H "Content-Type: application/json" \
  -d '{"method":"CARD","currency":"BRL","amount":250.50,"installments":12}'
```
Validações: método suportado, parcelas permitidas, anti-fraude OK

### 4. Consultar Pagamento
```bash
curl http://localhost:8080/fiadopay/gateway/payments/pay_abc123
```
Status muda para `APPROVED` após processamento assíncrono

---

## Testes

34 testes automatizados cobrindo:

- **PaymentMethodValidatorTest** (13 testes) - Métodos e parcelas
- **AntiFraudServiceTest** (11 testes) - Validações de fraude
- **PaymentServiceIntegrationTest** (10 testes) - Fluxo completo

```bash
./mvnw test
```

---

## Tecnologias

Spring Boot 3.5.7, Java 21, Maven, H2, JUnit 5, Swagger

---

**Data:** 18 de Novembro de 2025
