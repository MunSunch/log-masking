package com.munsun.testapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiSchema_containsMaskingExtensions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.password.x-masked.type").value("CREDENTIAL"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.password.format").value("password"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.email.x-masked.type").value("PII"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.phone.x-masked.type").value("PII"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.phone.x-masked.showFirst").value(2))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.phone.x-masked.showLast").value(2));
    }

    /**
     * Round-trip check: openapi-generator produced the {@code Customer*}/{@code Order*}
     * DTOs from {@code api.yaml} with {@code @Masked} on the right fields. At
     * runtime, {@link com.munsun.logmasking.openapi.OpenApiMaskingCustomizer}
     * reads those annotations off the generated classes and emits a fresh
     * {@code x-masked} extension into {@code /v3/api-docs}, matching the
     * extension that was originally written into {@code api.yaml}.
     */
    @Test
    void openApiSchema_generatedDtosCarryMaskingExtensions() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // CustomerRequest — covers all four MaskType variants
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.fullName.x-masked.type").value("PII"))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.fullName.x-masked.showFirst").value(1))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.fullName.x-masked.showLast").value(1))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.email.x-masked.type").value("PII"))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.password.x-masked.type").value("CREDENTIAL"))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.password.format").value("password"))
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.taxId.x-masked.replacement").value("[CLASSIFIED]"))

                // OrderRequest — financial + credential
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.cardNumber.x-masked.type").value("FINANCIAL"))
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.cardNumber.x-masked.showLast").value(4))
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.cvv.x-masked.type").value("CREDENTIAL"));
    }
}
