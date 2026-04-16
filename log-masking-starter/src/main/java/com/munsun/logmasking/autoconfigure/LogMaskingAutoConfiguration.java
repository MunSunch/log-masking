package com.munsun.logmasking.autoconfigure;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.core.DefaultMaskingStrategy;
import com.munsun.logmasking.core.FieldMaskingService;
import com.munsun.logmasking.core.MaskingStrategy;
import com.munsun.logmasking.logback.MaskingAppenderRegistrar;
import com.munsun.logmasking.openapi.OpenApiMaskingCustomizer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for the log-masking starter.
 * <p>
 * Activated by default; disable with {@code log.masking.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "log.masking", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogMaskingProperties.class)
public class LogMaskingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MaskingStrategy maskingStrategy(LogMaskingProperties properties) {
        return new DefaultMaskingStrategy(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldMaskingService fieldMaskingService(MaskingStrategy strategy) {
        return new FieldMaskingService(strategy);
    }

    @Bean
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    public MaskingAppenderRegistrar maskingAppenderRegistrar(FieldMaskingService service) {
        return new MaskingAppenderRegistrar(service);
    }

    /**
     * OpenAPI enrichment — only activates when springdoc is on the classpath.
     * Scans the application's base packages for classes containing {@link Masked}
     * fields and builds a mapping used to enrich OpenAPI schemas.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
    @ConditionalOnProperty(prefix = "log.masking.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class OpenApiMaskingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OpenApiMaskingCustomizer openApiMaskingCustomizer(LogMaskingProperties properties,
                                                          BeanFactory beanFactory) {
            Map<String, Class<?>> maskedClasses = discoverMaskedClasses(beanFactory);
            return new OpenApiMaskingCustomizer(properties.getOpenapi(), maskedClasses);
        }

        private static Map<String, Class<?>> discoverMaskedClasses(BeanFactory beanFactory) {
            Map<String, Class<?>> result = new HashMap<>();

            List<String> basePackages;
            try {
                basePackages = AutoConfigurationPackages.get(beanFactory);
            } catch (IllegalStateException e) {
                return result;
            }

            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter((reader, factory) -> true);

            for (String pkg : basePackages) {
                for (var bd : scanner.findCandidateComponents(pkg)) {
                    try {
                        Class<?> clazz = Class.forName(bd.getBeanClassName());
                        if (hasMaskedField(clazz)) {
                            result.put(clazz.getSimpleName(), clazz);
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            return result;
        }

        private static boolean hasMaskedField(Class<?> clazz) {
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Masked.class)) {
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        }
    }
}
