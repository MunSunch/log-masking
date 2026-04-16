package com.munsun.logmasking.core;

import com.munsun.logmasking.annotation.Masked;

/**
 * Strategy for masking a single field value.
 * <p>
 * Provide a custom bean of this type to override the default masking behavior.
 * The starter registers a {@code DefaultMaskingStrategy} only when no user-defined
 * bean is present ({@code @ConditionalOnMissingBean}).
 */
@FunctionalInterface
public interface MaskingStrategy {

    /**
     * @param value      the original string value of the field (never {@code null})
     * @param annotation the {@link Masked} annotation present on the field
     * @return masked representation of the value
     */
    String mask(String value, Masked annotation);
}
