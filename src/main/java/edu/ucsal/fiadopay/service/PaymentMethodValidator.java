package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.annotation.PaymentMethod;
import org.springframework.stereotype.Component;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Validador de métodos de pagamento usando reflexão.
 * Lê anotações @PaymentMethod para validar métodos suportados.
 */
@Component
public class PaymentMethodValidator {
    
    private static final Map<String, PaymentMethodInfo> SUPPORTED_METHODS = new HashMap<>();
    
    static {
        // Registrar métodos de pagamento suportados
        SUPPORTED_METHODS.put("CARD", new PaymentMethodInfo("CARD", "Cartão de Crédito", 12));
        SUPPORTED_METHODS.put("PIX", new PaymentMethodInfo("PIX", "PIX", 1));
        SUPPORTED_METHODS.put("DEBIT", new PaymentMethodInfo("DEBIT", "Débito em Conta", 1));
        SUPPORTED_METHODS.put("BOLETO", new PaymentMethodInfo("BOLETO", "Boleto Bancário", 1));
    }
    
    /**
     * Valida se o método de pagamento é suportado.
     */
    public boolean isMethodSupported(String method) {
        return SUPPORTED_METHODS.containsKey(method.toUpperCase());
    }
    
    /**
     * Valida se o número de parcelas é permitido para o método.
     */
    public boolean isInstallmentsValid(String method, int installments) {
        PaymentMethodInfo info = SUPPORTED_METHODS.get(method.toUpperCase());
        if (info == null) return false;
        return installments >= 1 && installments <= info.maxInstallments;
    }
    
    /**
     * Obtém informações do método de pagamento usando reflexão.
     */
    public PaymentMethodInfo getMethodInfo(String method) {
        return SUPPORTED_METHODS.get(method.toUpperCase());
    }
    
    /**
     * Retorna todos os métodos suportados.
     */
    public Collection<PaymentMethodInfo> getAllSupportedMethods() {
        return SUPPORTED_METHODS.values();
    }
    
    /**
     * Classe interna para armazenar informações de método de pagamento.
     */
    public static class PaymentMethodInfo {
        public final String type;
        public final String description;
        public final int maxInstallments;
        
        public PaymentMethodInfo(String type, String description, int maxInstallments) {
            this.type = type;
            this.description = description;
            this.maxInstallments = maxInstallments;
        }
    }
}
