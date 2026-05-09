package com.munsun.testapp;

import com.munsun.testapp.generated.model.Customer;
import com.munsun.testapp.generated.model.CustomerRequest;
import com.munsun.testapp.generated.model.Order;
import com.munsun.testapp.generated.model.OrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that DTOs generated from {@code openapi/api.yaml} via the bundled
 * Mustache templates are masked in logs exactly the same way as hand-written
 * DTOs annotated with {@link com.munsun.logmasking.annotation.Masked}.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class GeneratedDtoMaskingTest {

    private static final Logger log = LoggerFactory.getLogger(GeneratedDtoMaskingTest.class);

    @Test
    void customerRequest_allSensitiveFieldsMasked(CapturedOutput output) {
        CustomerRequest req = new CustomerRequest()
                .fullName("Ivan Petrov")
                .email("ivan@example.com")
                .phone("+79001234567")
                .password("hunter2-very-secret")
                .taxId("123-45-6789");

        log.info("Customer request: {}", req);
        String out = output.getOut();

        // password — CREDENTIAL → "***", original must not appear
        assertThat(out).doesNotContain("hunter2-very-secret");
        assertThat(out).contains("password=***");

        // taxId — replacement override → "[CLASSIFIED]"
        assertThat(out).doesNotContain("123-45-6789");
        assertThat(out).contains("taxId=[CLASSIFIED]");

        // fullName — PII showFirst=1 showLast=1 → "I*********v"
        assertThat(out).doesNotContain("Ivan Petrov");
        assertThat(out).contains("fullName=I").contains("v,");

        // email — PII showFirst=1 showLast=2 → "i**************om"
        assertThat(out).doesNotContain("ivan@example.com");

        // phone — PII showFirst=2 showLast=2 → "+7********67"
        assertThat(out).doesNotContain("+79001234567");
    }

    @Test
    void customerResponse_responseSchemaAlsoMasked(CapturedOutput output) {
        Customer c = new Customer()
                .id("c-1")
                .fullName("Ivan Petrov")
                .email("ivan@example.com")
                .phone("+79001234567");

        log.info("Customer: {}", c);
        String out = output.getOut();

        assertThat(out).contains("id=c-1");
        assertThat(out).doesNotContain("Ivan Petrov");
        assertThat(out).doesNotContain("ivan@example.com");
        assertThat(out).doesNotContain("+79001234567");
    }

    @Test
    void orderRequest_financialAndCredentialMasked(CapturedOutput output) {
        OrderRequest req = new OrderRequest()
                .customerId("c-1")
                .amount(199.99)
                .cardNumber("4111111111111234")
                .cvv("987");

        log.info("Order request: {}", req);
        String out = output.getOut();

        // customerId — not masked
        assertThat(out).contains("customerId=c-1");

        // cardNumber — FINANCIAL showLast=4 → "************1234"
        assertThat(out).doesNotContain("4111111111111234");
        assertThat(out).contains("1234");

        // cvv — CREDENTIAL → "***"
        assertThat(out).doesNotContain("cvv=987");
        assertThat(out).contains("cvv=***");
    }

    @Test
    void order_responseCardNumberMasked(CapturedOutput output) {
        Order order = new Order()
                .id("o-42")
                .customerId("c-1")
                .amount(199.99)
                .cardNumber("4111111111111234");

        log.info("Order: {}", order);

        assertThat(output.getOut())
                .contains("id=o-42")
                .contains("customerId=c-1")
                .doesNotContain("4111111111111234")
                .contains("1234");
    }
}
