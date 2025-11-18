package edu.ucsal.fiadopay.annotation;

import java.lang.annotation.*;

/**
 * Anotação para marcar métodos que enviam webhooks.
 * Usada para rastrear quais métodos disparam eventos de webhook.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebhookSink {
    String eventType();
    String description() default "";
}
