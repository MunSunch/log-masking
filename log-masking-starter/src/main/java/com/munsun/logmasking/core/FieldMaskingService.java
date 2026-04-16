package com.munsun.logmasking.core;

import com.munsun.logmasking.annotation.Masked;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service that inspects objects for {@link Masked} fields
 * and produces masked string representations.
 * <p>
 * Class metadata is scanned once and cached for the lifetime of the application.
 */
public class FieldMaskingService {

    private final MaskingStrategy strategy;
    private final ConcurrentHashMap<Class<?>, ClassMeta> cache = new ConcurrentHashMap<>();

    public FieldMaskingService(MaskingStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Returns {@code true} if the given class (or any superclass) declares
     * at least one field annotated with {@link Masked}.
     */
    public boolean hasMaskedFields(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, FieldMaskingService::scan).hasMaskedFields;
    }

    /**
     * Builds a string representation of the object where annotated fields are masked.
     * Format: {@code ClassName(field1=value1, field2=****)}.
     * Non-annotated fields are rendered via {@code String.valueOf()}.
     */
    public String toMaskedString(Object obj) {
        if (obj == null) {
            return "null";
        }
        Class<?> clazz = obj.getClass();
        ClassMeta meta = cache.computeIfAbsent(clazz, FieldMaskingService::scan);

        StringBuilder sb = new StringBuilder(clazz.getSimpleName()).append('(');
        boolean first = true;
        for (FieldEntry entry : meta.fields) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(entry.field.getName()).append('=');
            try {
                Object value = entry.field.get(obj);
                if (value == null) {
                    sb.append("null");
                } else if (entry.masked != null) {
                    sb.append(strategy.mask(String.valueOf(value), entry.masked));
                } else {
                    sb.append(value);
                }
            } catch (IllegalAccessException e) {
                sb.append("<access denied>");
            }
        }
        sb.append(')');
        return sb.toString();
    }

    // ---- internal metadata ------------------------------------------------

    private static ClassMeta scan(Class<?> clazz) {
        // Skip JDK and platform classes — they never carry @Masked and
        // calling setAccessible on their fields fails on Java 16+.
        if (clazz.getModule().isNamed() && clazz.getModule().getName().startsWith("java.")) {
            return EMPTY;
        }

        List<FieldEntry> fields = new ArrayList<>();
        boolean hasMasked = false;

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            // Stop ascending once we hit a platform class
            if (current.getModule().isNamed() && current.getModule().getName().startsWith("java.")) {
                break;
            }
            for (Field field : current.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (Exception e) {
                    continue; // inaccessible — skip
                }
                Masked ann = field.getAnnotation(Masked.class);
                fields.add(new FieldEntry(field, ann));
                if (ann != null) {
                    hasMasked = true;
                }
            }
            current = current.getSuperclass();
        }
        return new ClassMeta(fields, hasMasked);
    }

    private static final ClassMeta EMPTY = new ClassMeta(List.of(), false);

    record FieldEntry(Field field, Masked masked) {}

    record ClassMeta(List<FieldEntry> fields, boolean hasMaskedFields) {}
}
