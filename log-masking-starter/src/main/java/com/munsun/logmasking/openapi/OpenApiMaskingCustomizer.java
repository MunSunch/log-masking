package com.munsun.logmasking.openapi;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import com.munsun.logmasking.autoconfigure.LogMaskingProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Enriches OpenAPI schemas for classes that contain {@link Masked} fields.
 * Adds {@code x-masked}, {@code x-mask-type} extensions and adjusts
 * {@code description} / {@code format} so Swagger UI clearly indicates
 * which fields are masked in logs.
 * <p>
 * Requires a pre-built map of schema name to Java class, constructed at
 * startup by the auto-configuration via classpath scanning.
 */
public class OpenApiMaskingCustomizer implements OpenApiCustomizer {

    private final LogMaskingProperties.OpenApi config;
    private final Map<String, Class<?>> maskedClasses;

    /**
     * @param config         OpenAPI-specific masking properties
     * @param maskedClasses  map of simple class name to Java class for classes
     *                       that contain at least one {@link Masked} field
     */
    public OpenApiMaskingCustomizer(LogMaskingProperties.OpenApi config,
                                    Map<String, Class<?>> maskedClasses) {
        this.config = config;
        this.maskedClasses = maskedClasses;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().forEach((schemaName, schema) -> {
            Class<?> clazz = maskedClasses.get(schemaName);
            if (clazz != null) {
                enrichSchema(clazz, schema);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private void enrichSchema(Class<?> clazz, Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                Masked masked = field.getAnnotation(Masked.class);
                if (masked == null) {
                    continue;
                }
                Schema<?> propSchema = properties.get(field.getName());
                if (propSchema == null) {
                    continue;
                }
                propSchema.addExtension("x-masked", true);
                propSchema.addExtension("x-mask-type", masked.type().name());

                String desc = propSchema.getDescription();
                String suffix = config.getDescriptionSuffix();
                if (desc == null || desc.isEmpty()) {
                    propSchema.setDescription(suffix);
                } else if (!desc.contains(suffix)) {
                    propSchema.setDescription(desc + " " + suffix);
                }

                if (masked.type() == MaskType.CREDENTIAL) {
                    propSchema.setFormat(config.getCredentialFormat());
                }
            }
            current = current.getSuperclass();
        }
    }
}
