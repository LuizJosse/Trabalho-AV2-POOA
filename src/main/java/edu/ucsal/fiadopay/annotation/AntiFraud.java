package edu.ucsal.fiadopay.annotation;

import java.lang.annotation.*;

/**
 * Anotação para marcar validações anti-fraude.
 * Usada para aplicar regras de detecção de fraude em pagamentos.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AntiFraud {
    String name();
    double threshold() default 0.0;
    String description() default "";
}
