package edu.ucsal.fiadopay.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentMethodValidator Tests")
class PaymentMethodValidatorTest {

    private PaymentMethodValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentMethodValidator();
    }

    @Test
    @DisplayName("Deve validar método CARD como suportado")
    void testCardMethodSupported() {
        assertTrue(validator.isMethodSupported("CARD"));
    }

    @Test
    @DisplayName("Deve validar método PIX como suportado")
    void testPixMethodSupported() {
        assertTrue(validator.isMethodSupported("PIX"));
    }

    @Test
    @DisplayName("Deve validar método DEBIT como suportado")
    void testDebitMethodSupported() {
        assertTrue(validator.isMethodSupported("DEBIT"));
    }

    @Test
    @DisplayName("Deve validar método BOLETO como suportado")
    void testBoletoMethodSupported() {
        assertTrue(validator.isMethodSupported("BOLETO"));
    }

    @Test
    @DisplayName("Deve rejeitar método inválido")
    void testInvalidMethodNotSupported() {
        assertFalse(validator.isMethodSupported("INVALID"));
        assertFalse(validator.isMethodSupported("CRYPTO"));
    }

    @Test
    @DisplayName("Deve aceitar case-insensitive")
    void testCaseInsensitive() {
        assertTrue(validator.isMethodSupported("card"));
        assertTrue(validator.isMethodSupported("Card"));
        assertTrue(validator.isMethodSupported("CARD"));
    }

    @Test
    @DisplayName("Deve validar parcelas para CARD (máx 12)")
    void testCardInstallments() {
        assertTrue(validator.isInstallmentsValid("CARD", 1));
        assertTrue(validator.isInstallmentsValid("CARD", 6));
        assertTrue(validator.isInstallmentsValid("CARD", 12));
        assertFalse(validator.isInstallmentsValid("CARD", 13));
        assertFalse(validator.isInstallmentsValid("CARD", 0));
    }

    @Test
    @DisplayName("Deve validar parcelas para PIX (apenas 1)")
    void testPixInstallments() {
        assertTrue(validator.isInstallmentsValid("PIX", 1));
        assertFalse(validator.isInstallmentsValid("PIX", 2));
        assertFalse(validator.isInstallmentsValid("PIX", 12));
    }

    @Test
    @DisplayName("Deve validar parcelas para DEBIT (apenas 1)")
    void testDebitInstallments() {
        assertTrue(validator.isInstallmentsValid("DEBIT", 1));
        assertFalse(validator.isInstallmentsValid("DEBIT", 2));
    }

    @Test
    @DisplayName("Deve validar parcelas para BOLETO (apenas 1)")
    void testBoletoInstallments() {
        assertTrue(validator.isInstallmentsValid("BOLETO", 1));
        assertFalse(validator.isInstallmentsValid("BOLETO", 2));
    }

    @Test
    @DisplayName("Deve rejeitar parcelas para método inválido")
    void testInvalidMethodInstallments() {
        assertFalse(validator.isInstallmentsValid("INVALID", 1));
    }

    @Test
    @DisplayName("Deve retornar informações do método")
    void testGetMethodInfo() {
        var cardInfo = validator.getMethodInfo("CARD");
        assertNotNull(cardInfo);
        assertEquals("CARD", cardInfo.type);
        assertEquals(12, cardInfo.maxInstallments);

        var pixInfo = validator.getMethodInfo("PIX");
        assertNotNull(pixInfo);
        assertEquals("PIX", pixInfo.type);
        assertEquals(1, pixInfo.maxInstallments);
    }

    @Test
    @DisplayName("Deve retornar null para método inválido")
    void testGetMethodInfoInvalid() {
        assertNull(validator.getMethodInfo("INVALID"));
    }

    @Test
    @DisplayName("Deve listar todos os métodos suportados")
    void testGetAllSupportedMethods() {
        var methods = validator.getAllSupportedMethods();
        assertNotNull(methods);
        assertEquals(4, methods.size());
    }
}
