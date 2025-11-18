package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.controller.PaymentRequest;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/**
 * Serviço de detecção de fraude.
 * Implementa regras anti-fraude para validar pagamentos.
 */
@Component
public class AntiFraudService {
    
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal SUSPICIOUS_AMOUNT_THRESHOLD = new BigDecimal("10000.00");
    
    /**
     * Valida pagamento contra regras anti-fraude.
     * Retorna true se o pagamento passou nas validações.
     */
    public boolean validatePayment(PaymentRequest request) {
        return checkHighAmount(request.amount()) && 
               checkSuspiciousAmount(request.amount()) &&
               checkInstallmentsLimit(request.installments());
    }
    
    /**
     * Verifica se o valor é muito alto (acima do threshold).
     * Regra: valores acima de 5000 geram alerta.
     */
    @AntiFraud(name = "HighAmount", threshold = 5000.0, description = "Detecta pagamentos com valores altos")
    private boolean checkHighAmount(BigDecimal amount) {
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            System.out.println("[ANTI-FRAUD] Alerta: Valor alto detectado - " + amount);
            return true; // Permite mas registra alerta
        }
        return true;
    }
    
    /**
     * Verifica se o valor é suspeito (acima de 10000).
     * Regra: valores acima de 10000 são rejeitados.
     */
    @AntiFraud(name = "SuspiciousAmount", threshold = 10000.0, description = "Rejeita pagamentos suspeitos")
    private boolean checkSuspiciousAmount(BigDecimal amount) {
        if (amount.compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            System.out.println("[ANTI-FRAUD] Rejeição: Valor suspeito - " + amount);
            return false; // Rejeita
        }
        return true;
    }
    
    /**
     * Verifica limite de parcelas.
     * Regra: máximo 12 parcelas.
     */
    @AntiFraud(name = "InstallmentsLimit", threshold = 12.0, description = "Valida limite de parcelas")
    private boolean checkInstallmentsLimit(Integer installments) {
        if (installments != null && installments > 12) {
            System.out.println("[ANTI-FRAUD] Rejeição: Parcelas acima do limite - " + installments);
            return false;
        }
        return true;
    }
    
    /**
     * Obtém o risco de fraude em percentual (0-100).
     */
    public double calculateFraudRisk(BigDecimal amount) {
        if (amount.compareTo(SUSPICIOUS_AMOUNT_THRESHOLD) > 0) {
            return 100.0; // Alto risco
        }
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            return 50.0; // Risco médio
        }
        return 0.0; // Baixo risco
    }
}
