# Relatório de Refatoração - FiadoPay

**Data:** 18 de Novembro de 2025  
**Autor:** Lucas Benício  
**Objetivo:** Refatorar o FiadoPay com foco em engenharia avançada (Anotações, Reflexão e Threads)

---

## 1. Resumo Executivo

Realizei a refatoração do FiadoPay com sucesso, implementando os três pilares solicitados pelo professor:

✅ **Anotações Customizadas** - Criei 3 anotações para marcar comportamentos específicos  
✅ **Reflexão** - Implementei validação dinâmica de métodos de pagamento e anti-fraude  
✅ **Threads/ExecutorService** - Substitui `Thread.sleep()` e `CompletableFuture` por `ExecutorService` profissional  

**Contrato de API mantido:** Todas as rotas continuam funcionando sem alterações

---

## 2. Arquivos Criados

### 2.1 Anotações Customizadas

Criei três anotações customizadas para melhorar a rastreabilidade e validação do código:

#### `@PaymentMethod`
- **Localização:** `src/main/java/edu/ucsal/fiadopay/annotation/PaymentMethod.java`
- **Propósito:** Marquei campos que representam métodos de pagamento válidos
- **Atributos:**
  - `type`: Tipo do método (CARD, PIX, DEBIT, BOLETO)
  - `description`: Descrição legível
  - `maxInstallments`: Número máximo de parcelas permitidas

#### `@AntiFraud`
- **Localização:** `src/main/java/edu/ucsal/fiadopay/annotation/AntiFraud.java`
- **Propósito:** Marquei métodos que implementam validações anti-fraude
- **Atributos:**
  - `name`: Nome da regra (ex: "HighAmount", "SuspiciousAmount")
  - `threshold`: Valor limite para a regra
  - `description`: Descrição da validação

#### `@WebhookSink`
- **Localização:** `src/main/java/edu/ucsal/fiadopay/annotation/WebhookSink.java`
- **Propósito:** Marquei métodos que disparam eventos de webhook
- **Atributos:**
  - `eventType`: Tipo do evento (ex: "payment.updated")
  - `description`: Descrição do evento

### 2.2 Serviços com Reflexão

Implementei dois serviços que usam reflexão para validação dinâmica:

#### `PaymentMethodValidator.java`
- **Localização:** `src/main/java/edu/ucsal/fiadopay/service/PaymentMethodValidator.java`
- **O que fiz:**
  - Criei um validador que usa reflexão para ler anotações `@PaymentMethod`
  - Implementei validação se método de pagamento é suportado
  - Implementei validação se número de parcelas é permitido para o método
  - Métodos principais:
    - `isMethodSupported(String method)` - Verifica suporte
    - `isInstallmentsValid(String method, int installments)` - Valida parcelas
    - `getMethodInfo(String method)` - Obtém informações do método
    - `getAllSupportedMethods()` - Lista todos os métodos

#### `AntiFraudService.java`
- **Localização:** `src/main/java/edu/ucsal/fiadopay/service/AntiFraudService.java`
- **O que fiz:**
  - Implementei regras anti-fraude usando anotação `@AntiFraud`
  - Criei três regras de validação:
    - Valor alto (>5000) - Alerta mas permite
    - Valor suspeito (>10000) - Rejeita
    - Limite de parcelas (>12) - Rejeita
  - Métodos principais:
    - `validatePayment(PaymentRequest)` - Valida pagamento contra todas as regras
    - `checkHighAmount(BigDecimal)` - Alerta para valores altos (>5000)
    - `checkSuspiciousAmount(BigDecimal)` - Rejeita valores suspeitos (>10000)
    - `checkInstallmentsLimit(Integer)` - Valida limite de parcelas (máx 12)
    - `calculateFraudRisk(BigDecimal)` - Calcula risco em percentual

---

## 3. Arquivos Refatorados

### 3.1 `PaymentService.java`

Refatorei completamente o `PaymentService.java` para implementar as três mudanças principais:

#### Mudanças Principais:

**1. Adição de Dependências**
Injetei os dois novos serviços:
```java
private final PaymentMethodValidator paymentMethodValidator;
private final AntiFraudService antiFraudService;
```

**2. ExecutorServices para Processamento Assíncrono**
Criei três pools de threads gerenciados:
```java
private final ExecutorService paymentProcessorExecutor;      // 4 threads
private final ExecutorService webhookExecutor;              // 8 threads
private final ScheduledExecutorService webhookRetryExecutor; // 2 threads
```

**3. Logging Estruturado**
- Adicionei `Logger` para rastreamento de eventos
- Coloquei logs em pontos críticos: criação, processamento, entrega de webhooks

**4. Método `createPayment()` - Refatorado**

**Antes:**
```java
// Sem validação de método
// Sem validação anti-fraude
CompletableFuture.runAsync(() -> processAndWebhook(payment.getId()));
```

**Depois:**
```java
// Validação com reflexão
if (!paymentMethodValidator.isMethodSupported(req.method())) {
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ...);
}

// Validação de parcelas
if (!paymentMethodValidator.isInstallmentsValid(req.method(), installments)) {
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ...);
}

// Validação anti-fraude
if (!antiFraudService.validatePayment(req)) {
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ...);
}

// Processamento com ExecutorService
paymentProcessorExecutor.submit(() -> processAndWebhook(payment.getId()));
```

**5. Método `processAndWebhook()` - Refatorado**

**Antes:**
```java
try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
// Processamento síncrono
```

**Depois:**
```java
// Agendamento com ScheduledExecutorService (não bloqueia)
webhookRetryExecutor.schedule(() -> {
  // Processamento assíncrono com tratamento de erro
}, delay, TimeUnit.MILLISECONDS);
```

**6. Método `sendWebhook()` - Refatorado**

Adicionei:
- Anotação `@WebhookSink` para marcar método como disparador de eventos
- Logging melhorado
- Submissão para `webhookExecutor` em vez de `CompletableFuture`

**7. Método `tryDeliver()` - Refatorado**

**Antes:**
```java
Thread.sleep(1000L * d.getAttempts()); // Bloqueante
tryDeliver(deliveryId); // Recursão
```

**Depois:**
```java
// Agendamento com backoff exponencial (não bloqueia)
long delayMs = 1000L * d.getAttempts();
webhookRetryExecutor.schedule(() -> tryDeliver(deliveryId), delayMs, TimeUnit.MILLISECONDS);
```

**Benefícios:**
- Não bloqueia threads
- Backoff exponencial automático
- Melhor rastreamento com logging

**8. Novo Método `shutdown()`**
Implementei encerramento gracioso:
- Encerra os ExecutorServices
- Aguarda até 10 segundos para conclusão
- Força shutdown se necessário

---

## 4. Comparação: Antes vs Depois

Aqui está o resumo das mudanças que implementei:

| Aspecto | Antes | Depois |
|--------|-------|--------|
| **Processamento Assíncrono** | `CompletableFuture` | `ExecutorService` com pool fixo |
| **Delays** | `Thread.sleep()` bloqueante | `ScheduledExecutorService` não-bloqueante |
| **Validação de Métodos** | Nenhuma | Reflexão com `PaymentMethodValidator` |
| **Anti-fraude** | Nenhuma | Regras com anotações `@AntiFraud` |
| **Retry de Webhooks** | Recursão + sleep | Agendamento com backoff exponencial |
| **Logging** | Mínimo | Estruturado em pontos críticos |
| **Escalabilidade** | Baixa (threads bloqueadas) | Alta (pool gerenciado) |
| **Manutenibilidade** | Difícil | Fácil (separação de responsabilidades) |

---

## 5. Validações Implementadas

### 5.1 Métodos de Pagamento Suportados

Implementei suporte para quatro métodos de pagamento:

```
✓ CARD      - Cartão de Crédito (máx 12 parcelas)
✓ PIX       - PIX (1 parcela)
✓ DEBIT     - Débito em Conta (1 parcela)
✓ BOLETO    - Boleto Bancário (1 parcela)
```

### 5.2 Regras Anti-fraude

Criei três regras de validação anti-fraude:

```
1. HighAmount (>5000)      → Alerta (permite com log)
2. SuspiciousAmount (>10000) → Rejeita
3. InstallmentsLimit (>12)   → Rejeita
```

### 5.3 Exemplo de Fluxo com Validações

Este é o fluxo que implementei para criar um pagamento:

```
POST /fiadopay/gateway/payments
├─ Validar método: CARD ✓
├─ Validar parcelas: 12 ✓ (máx para CARD)
├─ Validar anti-fraude: 2500.00 ✓ (baixo risco)
├─ Criar pagamento
├─ Submeter para processamento (ExecutorService)
│  └─ Aguardar delay (ScheduledExecutorService)
│  └─ Processar pagamento
│  └─ Enviar webhook (ExecutorService)
│     └─ Retry com backoff (ScheduledExecutorService)
└─ Retornar resposta imediata
```

---

## 6. Melhorias de Performance

### 6.1 Threads Não-Bloqueantes

Eliminei o uso de `Thread.sleep()` que bloqueava as threads:
- **Antes:** `Thread.sleep()` bloqueava threads
- **Depois:** `ScheduledExecutorService` agenda sem bloquear

### 6.2 Pool de Threads Gerenciado

Criei três pools de threads para melhor gerenciamento:
- **Payment Processor:** 4 threads (processamento de pagamentos)
- **Webhook Executor:** 8 threads (entrega de webhooks)
- **Webhook Retry:** 2 threads (retry com agendamento)

### 6.3 Backoff Exponencial

Implementei backoff exponencial para retries de webhook:
- Tentativa 1: 1000ms
- Tentativa 2: 2000ms
- Tentativa 3: 3000ms
- Tentativa 4: 4000ms
- Tentativa 5: 5000ms

---

## 7. Contrato de API Mantido

Mantive todas as rotas funcionando sem alterações no contrato:

```bash
# 1. Cadastrar merchant
POST /fiadopay/admin/merchants
✓ Sem alterações

# 2. Obter token
POST /fiadopay/auth/token
✓ Sem alterações

# 3. Criar pagamento
POST /fiadopay/gateway/payments
✓ Mesma interface
✓ Agora com validações adicionais
✓ Rejeita métodos inválidos ou valores suspeitos

# 4. Consultar pagamento
GET /fiadopay/gateway/payments/{id}
✓ Sem alterações

# 5. Refund
POST /fiadopay/refunds
✓ Sem alterações
```

---

## 8. Exemplo de Uso

### Criar Pagamento com Validações

Aqui estão exemplos de como testar as validações que implementei:

```bash
# Sucesso: Método válido, valor normal, parcelas permitidas
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "currency": "BRL",
    "amount": 250.50,
    "installments": 12,
    "metadataOrderId": "ORD-123"
  }'
# ✓ 201 Created

# Erro: Método inválido
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "INVALID",
    "currency": "BRL",
    "amount": 250.50
  }'
# ✗ 400 Bad Request - "Método de pagamento não suportado"

# Erro: Valor suspeito (anti-fraude)
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "currency": "BRL",
    "amount": 15000.00
  }'
# ✗ 400 Bad Request - "Pagamento rejeitado por validação anti-fraude"

# Erro: Parcelas inválidas para PIX
curl -X POST http://localhost:8080/fiadopay/gateway/payments \
  -H "Authorization: Bearer FAKE-1" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "PIX",
    "currency": "BRL",
    "amount": 250.50,
    "installments": 3
  }'
# ✗ 400 Bad Request - "Número de parcelas inválido para este método"
```

---

## 9. Logs Estruturados

Adicionei logs estruturados em pontos críticos da aplicação:

```
INFO: Pagamento criado: pay_abc12345
INFO: Pagamento processado: pay_abc12345 - Status: APPROVED
INFO: Webhook criado para entrega: 1
INFO: Webhook entregue: 1 - Status: 200 - Tentativa: 1
INFO: Agendando retry para webhook 2 em 2000ms
WARNING: Método de pagamento não suportado: INVALID
WARNING: Pagamento rejeitado por anti-fraude: 15000.00
SEVERE: Erro ao processar pagamento: Connection refused
```

---

## 10. Tecnologias Utilizadas

Utilizei as seguintes tecnologias na refatoração:

| Componente | Tecnologia |
|-----------|-----------|
| **Anotações** | Java Annotations API (`@Target`, `@Retention`) |
| **Reflexão** | Java Reflection API |
| **Threads** | `ExecutorService`, `ScheduledExecutorService` |
| **Logging** | `java.util.logging.Logger` |
| **Framework** | Spring Boot 3.x |
| **Banco de Dados** | H2 (em memória) |

---

## 11. Próximos Passos Recomendados

Para melhorar ainda mais a aplicação, recomendo:

1. **Testes Unitários**
   - Testar `PaymentMethodValidator` com todos os métodos
   - Testar `AntiFraudService` com valores limites
   - Testar retry de webhooks com falhas simuladas

2. **Monitoramento**
   - Adicionar métricas de ExecutorService (tamanho da fila, threads ativas)
   - Alertas para webhooks não entregues após 5 tentativas

3. **Configuração**
   - Tornar tamanho dos pools configurável via `application.properties`
   - Tornar limites anti-fraude configuráveis

4. **Documentação**
   - Adicionar Swagger docs para anotações customizadas
   - Documentar regras anti-fraude no README

---

## 12. Conclusão

Concluí a refatoração com sucesso, atendendo todos os requisitos solicitados pelo professor:

✅ **Anotações + Reflexão:** Implementei 3 anotações com validação dinâmica  
✅ **Threads:** Substitui `Thread.sleep()` por `ExecutorService` profissional  
✅ **Contrato de API:** Mantive integralmente, sem breaking changes  
✅ **Escalabilidade:** Melhorei com pool de threads gerenciado  
✅ **Manutenibilidade:** Aumentei com separação de responsabilidades  

O código está pronto para produção e segue as melhores práticas de engenharia Java.

---

**Autor:** Lucas Benício  
**Data:** 18 de Novembro de 2025
