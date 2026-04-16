package com.munsun.logmasking.core;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import com.munsun.logmasking.autoconfigure.LogMaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMaskingStrategyTest {

    private DefaultMaskingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultMaskingStrategy(new LogMaskingProperties());
    }

    // --- CREDENTIAL --------------------------------------------------------

    @Test
    void credential_replacesWithFixedString() {
        Masked ann = masked(MaskType.CREDENTIAL, '\0', -1, -1, "");
        assertThat(strategy.mask("superSecretPassword", ann)).isEqualTo("***");
    }

    // --- PII ---------------------------------------------------------------

    @Test
    void pii_defaultShowFirstAndLast() {
        // defaults: showFirst=1, showLast=2
        Masked ann = masked(MaskType.PII, '\0', -1, -1, "");
        assertThat(strategy.mask("john@example.com", ann)).isEqualTo("j*************om");
    }

    @Test
    void pii_annotationOverridesShowLast() {
        Masked ann = masked(MaskType.PII, '\0', -1, 4, "");
        // 16 chars, showFirst=1 (PII default), showLast=4 → "j" + 11*"*" + ".com"
        assertThat(strategy.mask("john@example.com", ann)).isEqualTo("j***********.com");
    }

    // --- FINANCIAL ---------------------------------------------------------

    @Test
    void financial_showsLastFour() {
        Masked ann = masked(MaskType.FINANCIAL, '\0', -1, -1, "");
        assertThat(strategy.mask("4111111111111111", ann)).isEqualTo("************1111");
    }

    // --- CUSTOM ------------------------------------------------------------

    @Test
    void custom_fullMaskByDefault() {
        Masked ann = masked(MaskType.CUSTOM, '\0', -1, -1, "");
        assertThat(strategy.mask("secret", ann)).isEqualTo("******");
    }

    @Test
    void custom_withExplicitParams() {
        Masked ann = masked(MaskType.CUSTOM, '#', 2, 2, "");
        assertThat(strategy.mask("Hello World", ann)).isEqualTo("He#######ld");
    }

    // --- replacement takes priority ----------------------------------------

    @Test
    void replacement_overridesEverything() {
        Masked ann = masked(MaskType.PII, '*', 1, 2, "[REDACTED]");
        assertThat(strategy.mask("anything", ann)).isEqualTo("[REDACTED]");
    }

    // --- edge cases --------------------------------------------------------

    @Test
    void emptyString_returnsEmpty() {
        Masked ann = masked(MaskType.PII, '\0', -1, -1, "");
        assertThat(strategy.mask("", ann)).isEmpty();
    }

    @Test
    void shortValue_fullMaskWhenNotEnoughChars() {
        // showFirst=1, showLast=2 but value length is 2
        Masked ann = masked(MaskType.PII, '\0', -1, -1, "");
        assertThat(strategy.mask("ab", ann)).isEqualTo("**");
    }

    // --- helper ------------------------------------------------------------

    private static Masked masked(MaskType type, char maskChar, int showFirst, int showLast, String replacement) {
        return new Masked() {
            @Override public Class<? extends Annotation> annotationType() { return Masked.class; }
            @Override public MaskType type()      { return type; }
            @Override public char maskChar()      { return maskChar; }
            @Override public int showFirst()      { return showFirst; }
            @Override public int showLast()       { return showLast; }
            @Override public String replacement() { return replacement; }
        };
    }
}
