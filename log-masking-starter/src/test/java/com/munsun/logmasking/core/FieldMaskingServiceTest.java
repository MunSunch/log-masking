package com.munsun.logmasking.core;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import com.munsun.logmasking.autoconfigure.LogMaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldMaskingServiceTest {

    private FieldMaskingService service;

    @BeforeEach
    void setUp() {
        service = new FieldMaskingService(new DefaultMaskingStrategy(new LogMaskingProperties()));
    }

    // -- hasMaskedFields ----------------------------------------------------

    @Test
    void hasMaskedFields_trueForAnnotatedClass() {
        assertThat(service.hasMaskedFields(UserDto.class)).isTrue();
    }

    @Test
    void hasMaskedFields_falseForPlainClass() {
        assertThat(service.hasMaskedFields(PlainDto.class)).isFalse();
    }

    // -- toMaskedString -----------------------------------------------------

    @Test
    void toMaskedString_masksAnnotatedFields() {
        UserDto user = new UserDto("John", "john@example.com", "secret123");
        String result = service.toMaskedString(user);

        assertThat(result).contains("name=John");
        assertThat(result).contains("email=j*************om");
        assertThat(result).contains("password=***");
        assertThat(result).startsWith("UserDto(");
        assertThat(result).endsWith(")");
    }

    @Test
    void toMaskedString_handlesNullFields() {
        UserDto user = new UserDto("John", null, null);
        String result = service.toMaskedString(user);

        assertThat(result).contains("email=null");
        assertThat(result).contains("password=null");
    }

    @Test
    void toMaskedString_handlesNullObject() {
        assertThat(service.toMaskedString(null)).isEqualTo("null");
    }

    @Test
    void toMaskedString_plainClassUsesAllFields() {
        PlainDto plain = new PlainDto("hello", 42);
        String result = service.toMaskedString(plain);

        assertThat(result).isEqualTo("PlainDto(value=hello, count=42)");
    }

    // -- inheritance --------------------------------------------------------

    @Test
    void toMaskedString_includesParentFields() {
        ExtendedUserDto ext = new ExtendedUserDto("John", "john@example.com", "secret", "1234567890123456");
        String result = service.toMaskedString(ext);

        assertThat(result).contains("password=***");
        assertThat(result).contains("cardNumber=************3456");
    }

    // -- test DTOs ----------------------------------------------------------

    static class UserDto {
        private final String name;
        @Masked(type = MaskType.PII)
        private final String email;
        @Masked(type = MaskType.CREDENTIAL)
        private final String password;

        UserDto(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }
    }

    static class PlainDto {
        private final String value;
        private final int count;

        PlainDto(String value, int count) {
            this.value = value;
            this.count = count;
        }
    }

    static class ExtendedUserDto extends UserDto {
        @Masked(type = MaskType.FINANCIAL)
        private final String cardNumber;

        ExtendedUserDto(String name, String email, String password, String cardNumber) {
            super(name, email, password);
            this.cardNumber = cardNumber;
        }
    }
}
