package com.munsun.testapp;

import com.munsun.testapp.dto.PaymentDto;
import com.munsun.testapp.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MaskingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MaskingIntegrationTest.class);

    @Test
    void userDto_sensitiveFieldsAreMaskedInLogs(CapturedOutput output) {
        UserDto user = new UserDto("John Doe", "john@example.com", "secret123", "+79001234567");
        log.info("User created: {}", user);

        String out = output.getOut();

        // name is NOT masked
        assertThat(out).contains("name=John Doe");

        // email is PII-masked (showFirst=1, showLast=2)
        assertThat(out).doesNotContain("john@example.com");
        assertThat(out).contains("email=j");

        // password is CREDENTIAL-masked
        assertThat(out).doesNotContain("secret123");
        assertThat(out).contains("password=***");

        // phone is PII-masked with custom params (showFirst=2, showLast=2)
        assertThat(out).doesNotContain("+79001234567");
        assertThat(out).contains("phone=+7");
    }

    @Test
    void paymentDto_cardNumberShowsLastFour(CapturedOutput output) {
        PaymentDto payment = new PaymentDto(99.99, "4111111111111111", "John Doe");
        log.info("Payment processed: {}", payment);

        String out = output.getOut();

        // card number shows last 4
        assertThat(out).doesNotContain("4111111111111111");
        assertThat(out).contains("1111");

        // cardholder name is PII-masked
        assertThat(out).doesNotContain("cardholderName=John Doe");
    }

    @Test
    void plainArguments_notAffected(CapturedOutput output) {
        log.info("Simple message: {}", "hello world");

        assertThat(output.getOut()).contains("Simple message: hello world");
    }
}
