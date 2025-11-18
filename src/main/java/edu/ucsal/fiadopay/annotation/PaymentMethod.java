package edu.ucsal.fiadopay.annotation;

import java.lang.annotation.*;

/**
 * Anotação para marcar métodos de pagamento válidos.
 * Usada com reflexão para validar se o método de pagamento é suportado.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PaymentMethod {
    String type();
    String description() default "";
    int maxInstallments() default 1;
}
