package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.controller.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AntiFraudService Tests")
class AntiFraudServiceTest {

    private AntiFraudService antiFraudService;

    @BeforeEach
    void setUp() {
        antiFraudService = new AntiFraudService();
    }

    @Test
    @DisplayName("Deve aceitar pagamento com valor baixo")
    void testLowAmountPayment() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("100.00"), 1, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve aceitar pagamento com valor até 5000")
    void testHighAmountThreshold() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("5000.00"), 1, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve alertar para valor acima de 5000 mas permitir")
    void testHighAmountAlert() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("5001.00"), 1, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve rejeitar pagamento com valor suspeito (>10000)")
    void testSuspiciousAmount() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("10001.00"), 1, "ORD-001");
        assertFalse(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve aceitar pagamento com valor exatamente 10000")
    void testSuspiciousAmountThreshold() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("10000.00"), 1, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve aceitar parcelas até 12")
    void testInstallmentsValid() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("1000.00"), 12, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve rejeitar parcelas acima de 12")
    void testInstallmentsInvalid() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("1000.00"), 13, "ORD-001");
        assertFalse(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve aceitar null para parcelas")
    void testInstallmentsNull() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("1000.00"), null, "ORD-001");
        assertTrue(antiFraudService.validatePayment(request));
    }

    @Test
    @DisplayName("Deve calcular risco baixo para valor < 5000")
    void testFraudRiskLow() {
        double risk = antiFraudService.calculateFraudRisk(new BigDecimal("1000.00"));
        assertEquals(0.0, risk);
    }

    @Test
    @DisplayName("Deve calcular risco médio para valor entre 5000 e 10000")
    void testFraudRiskMedium() {
        double risk = antiFraudService.calculateFraudRisk(new BigDecimal("7500.00"));
        assertEquals(50.0, risk);
    }

    @Test
    @DisplayName("Deve calcular risco alto para valor > 10000")
    void testFraudRiskHigh() {
        double risk = antiFraudService.calculateFraudRisk(new BigDecimal("15000.00"));
        assertEquals(100.0, risk);
    }

    @Test
    @DisplayName("Deve rejeitar pagamento que falha em múltiplas validações")
    void testMultipleValidationFailures() {
        var request = new PaymentRequest("CARD", "BRL", new BigDecimal("15000.00"), 15, "ORD-001");
        assertFalse(antiFraudService.validatePayment(request));
    }
}
