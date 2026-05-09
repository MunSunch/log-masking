package com.munsun.testapp.controller;

import com.munsun.testapp.generated.api.CustomersApi;
import com.munsun.testapp.generated.api.OrdersApi;
import com.munsun.testapp.generated.model.Customer;
import com.munsun.testapp.generated.model.CustomerRequest;
import com.munsun.testapp.generated.model.Order;
import com.munsun.testapp.generated.model.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Demonstrates the contract-first flow: this controller implements two API
 * interfaces ({@link CustomersApi}, {@link OrdersApi}) generated from
 * {@code src/main/resources/openapi/api.yaml} by openapi-generator. The
 * generated DTOs already carry {@code @Masked} annotations (emitted via the
 * Mustache templates shipped in {@code log-masking-starter}), so logging them
 * with SLF4J placeholders is enough to mask the sensitive fields.
 */
@RestController
public class CustomerOrderController implements CustomersApi, OrdersApi {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderController.class);

    @Override
    public ResponseEntity<Customer> createCustomer(CustomerRequest request) {
        log.info("Creating customer: {}", request);
        Customer created = new Customer()
                .id(UUID.randomUUID().toString())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone());
        log.info("Customer created: {}", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Customer> getCustomer(String customerId) {
        Customer customer = new Customer()
                .id(customerId)
                .fullName("Ivan Petrov")
                .email("ivan@example.com")
                .phone("+79161234567");
        log.info("Returning customer: {}", customer);
        return ResponseEntity.ok(customer);
    }

    @Override
    public ResponseEntity<Order> placeOrder(OrderRequest request) {
        log.info("Placing order: {}", request);
        Order order = new Order()
                .id(UUID.randomUUID().toString())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .cardNumber(request.getCardNumber());
        log.info("Order placed: {}", order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
