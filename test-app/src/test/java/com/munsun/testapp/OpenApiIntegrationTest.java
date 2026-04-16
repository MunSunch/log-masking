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
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.password.x-masked").value(true))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.password.x-mask-type").value("CREDENTIAL"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.password.format").value("password"))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.email.x-masked").value(true))
                .andExpect(jsonPath("$.components.schemas.UserDto.properties.email.x-mask-type").value("PII"));
    }
}
