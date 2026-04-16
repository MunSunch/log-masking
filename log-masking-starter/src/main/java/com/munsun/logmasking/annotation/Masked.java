package com.munsun.logmasking.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as containing sensitive data that should be masked in log output.
 * <p>
 * The field value is never mutated — masking applies only to the string representation
 * produced for logging purposes.
 * <p>
 * Parameter resolution priority (highest to lowest):
 * <ol>
 *   <li>{@code replacement} on the annotation (if non-empty)</li>
 *   <li>Explicit {@code showFirst}/{@code showLast}/{@code maskChar} on the annotation (if &ge; 0 / non-{@code '\0'})</li>
 *   <li>Defaults for the chosen {@link MaskType} from {@code application.yml}</li>
 *   <li>Built-in defaults in {@code DefaultMaskingStrategy}</li>
 * </ol>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Masked {

    /**
     * Category of sensitive data. Determines the default masking strategy.
     */
    MaskType type() default MaskType.CUSTOM;

    /**
     * Mask character. {@code '\0'} means "use default from properties".
     */
    char maskChar() default '\0';

    /**
     * Number of characters to leave unmasked at the start. {@code -1} means "use default".
     */
    int showFirst() default -1;

    /**
     * Number of characters to leave unmasked at the end. {@code -1} means "use default".
     */
    int showLast() default -1;

    /**
     * Fixed replacement string. If non-empty, takes priority over all other parameters.
     */
    String replacement() default "";
}
