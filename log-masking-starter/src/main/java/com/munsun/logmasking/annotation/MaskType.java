package com.munsun.logmasking.annotation;

/**
 * Categories of sensitive data aligned with OWASP recommendations.
 * Each type has default masking behavior configurable via application properties.
 */
public enum MaskType {

    /**
     * Credentials: passwords, tokens, API keys.
     * Default: full replacement with a fixed string ("***").
     */
    CREDENTIAL,

    /**
     * Personally Identifiable Information: email, phone, name.
     * Default: partial masking (showFirst=1, showLast=2).
     */
    PII,

    /**
     * Financial data: card numbers, bank accounts.
     * Default: show last 4 characters.
     */
    FINANCIAL,

    /**
     * Custom masking — behavior determined by annotation parameters or application properties.
     */
    CUSTOM
}
