package com.munsun.logmasking.autoconfigure;

import com.munsun.logmasking.core.DefaultMaskingStrategy;
import com.munsun.logmasking.core.FieldMaskingService;
import com.munsun.logmasking.core.MaskingStrategy;
import com.munsun.logmasking.logback.MaskingAppenderRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogMaskingAutoConfiguration.class));

    @Test
    void createsDefaultBeans() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MaskingStrategy.class);
            assertThat(ctx).hasSingleBean(FieldMaskingService.class);
            assertThat(ctx).hasSingleBean(MaskingAppenderRegistrar.class);
            assertThat(ctx.getBean(MaskingStrategy.class)).isInstanceOf(DefaultMaskingStrategy.class);
        });
    }

    @Test
    void disabledWhenPropertyFalse() {
        runner.withPropertyValues("log.masking.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(MaskingStrategy.class);
                    assertThat(ctx).doesNotHaveBean(FieldMaskingService.class);
                });
    }

    @Test
    void customStrategyWins() {
        runner.withUserConfiguration(CustomStrategyConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(MaskingStrategy.class);
                    assertThat(ctx.getBean(MaskingStrategy.class)).isInstanceOf(NoOpMaskingStrategy.class);
                });
    }

    @Test
    void propertiesAreBound() {
        runner.withPropertyValues(
                        "log.masking.mask-char=#",
                        "log.masking.credential.replacement=[HIDDEN]",
                        "log.masking.pii.show-first=3",
                        "log.masking.pii.show-last=4",
                        "log.masking.financial.show-last=6")
                .run(ctx -> {
                    LogMaskingProperties props = ctx.getBean(LogMaskingProperties.class);
                    assertThat(props.getMaskChar()).isEqualTo('#');
                    assertThat(props.getCredential().getReplacement()).isEqualTo("[HIDDEN]");
                    assertThat(props.getPii().getShowFirst()).isEqualTo(3);
                    assertThat(props.getPii().getShowLast()).isEqualTo(4);
                    assertThat(props.getFinancial().getShowLast()).isEqualTo(6);
                });
    }

    // -- test configs ------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class CustomStrategyConfig {
        @Bean
        MaskingStrategy maskingStrategy() {
            return new NoOpMaskingStrategy();
        }
    }

    static class NoOpMaskingStrategy implements MaskingStrategy {
        @Override
        public String mask(String value, com.munsun.logmasking.annotation.Masked annotation) {
            return value;
        }
    }
}
