package com.munsun.logmasking.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the log-masking starter.
 * <p>
 * All defaults can be overridden per {@code MaskType} via
 * {@code log.masking.*} in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "log.masking")
public class LogMaskingProperties {

    /** Master switch — disables the entire starter when false. */
    private boolean enabled = true;

    /** Global default mask character. */
    private char maskChar = '*';

    private final Credential credential = new Credential();
    private final Pii pii = new Pii();
    private final Financial financial = new Financial();
    private final OpenApi openapi = new OpenApi();

    // -- getters / setters --------------------------------------------------

    public boolean isEnabled()            { return enabled; }
    public void setEnabled(boolean v)     { this.enabled = v; }
    public char getMaskChar()             { return maskChar; }
    public void setMaskChar(char v)       { this.maskChar = v; }
    public Credential getCredential()     { return credential; }
    public Pii getPii()                   { return pii; }
    public Financial getFinancial()       { return financial; }
    public OpenApi getOpenapi()           { return openapi; }

    // -- nested classes -----------------------------------------------------

    public static class Credential {
        /** Fixed replacement string for credentials (hides original length). */
        private String replacement = "***";

        public String getReplacement()          { return replacement; }
        public void setReplacement(String v)    { this.replacement = v; }
    }

    public static class Pii {
        /** Characters to leave unmasked at the start. */
        private int showFirst = 1;
        /** Characters to leave unmasked at the end. */
        private int showLast = 2;

        public int getShowFirst()           { return showFirst; }
        public void setShowFirst(int v)     { this.showFirst = v; }
        public int getShowLast()            { return showLast; }
        public void setShowLast(int v)      { this.showLast = v; }
    }

    public static class Financial {
        /** Characters to leave unmasked at the end (typically last 4 digits). */
        private int showLast = 4;

        public int getShowLast()            { return showLast; }
        public void setShowLast(int v)      { this.showLast = v; }
    }

    public static class OpenApi {
        /** Enable automatic OpenAPI schema enrichment when springdoc is on classpath. */
        private boolean enabled = true;
        /** Suffix appended to field descriptions. */
        private String descriptionSuffix = "[MASKED IN LOGS]";
        /** OpenAPI format value for CREDENTIAL fields. */
        private String credentialFormat = "password";

        public boolean isEnabled()                  { return enabled; }
        public void setEnabled(boolean v)           { this.enabled = v; }
        public String getDescriptionSuffix()        { return descriptionSuffix; }
        public void setDescriptionSuffix(String v)  { this.descriptionSuffix = v; }
        public String getCredentialFormat()         { return credentialFormat; }
        public void setCredentialFormat(String v)   { this.credentialFormat = v; }
    }
}
