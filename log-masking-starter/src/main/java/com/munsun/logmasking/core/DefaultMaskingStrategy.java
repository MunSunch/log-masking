package com.munsun.logmasking.core;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import com.munsun.logmasking.autoconfigure.LogMaskingProperties;

/**
 * Default masking strategy that resolves parameters from the annotation
 * and falls back to {@link LogMaskingProperties} for unset values.
 */
public class DefaultMaskingStrategy implements MaskingStrategy {

    private final LogMaskingProperties properties;

    public DefaultMaskingStrategy(LogMaskingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mask(String value, Masked annotation) {
        if (value.isEmpty()) {
            return value;
        }

        if (!annotation.replacement().isEmpty()) {
            return annotation.replacement();
        }

        return switch (annotation.type()) {
            case CREDENTIAL -> properties.getCredential().getReplacement();
            case PII -> applyMask(value,
                    resolveInt(annotation.showFirst(), properties.getPii().getShowFirst()),
                    resolveInt(annotation.showLast(), properties.getPii().getShowLast()),
                    resolveChar(annotation.maskChar(), properties.getMaskChar()));
            case FINANCIAL -> applyMask(value,
                    0,
                    resolveInt(annotation.showLast(), properties.getFinancial().getShowLast()),
                    resolveChar(annotation.maskChar(), properties.getMaskChar()));
            case CUSTOM -> applyMask(value,
                    Math.max(0, annotation.showFirst()),
                    Math.max(0, annotation.showLast()),
                    resolveChar(annotation.maskChar(), properties.getMaskChar()));
        };
    }

    private static int resolveInt(int annotationValue, int propertyDefault) {
        return annotationValue >= 0 ? annotationValue : propertyDefault;
    }

    private static char resolveChar(char annotationValue, char propertyDefault) {
        return annotationValue != '\0' ? annotationValue : propertyDefault;
    }

    static String applyMask(String value, int showFirst, int showLast, char maskChar) {
        int len = value.length();
        if (showFirst + showLast >= len) {
            return String.valueOf(maskChar).repeat(len);
        }
        StringBuilder sb = new StringBuilder(len);
        sb.append(value, 0, showFirst);
        sb.append(String.valueOf(maskChar).repeat(len - showFirst - showLast));
        sb.append(value, len - showLast, len);
        return sb.toString();
    }
}
