package com.munsun.testapp.controller;

import com.munsun.testapp.dto.PaymentDto;
import com.munsun.testapp.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @PostMapping("/users")
    public UserDto createUser(@RequestBody UserDto user) {
        log.info("Creating user: {}", user);
        return user;
    }

    @PostMapping("/payments")
    public PaymentDto createPayment(@RequestBody PaymentDto payment) {
        log.info("Processing payment: {}", payment);
        return payment;
    }
}
