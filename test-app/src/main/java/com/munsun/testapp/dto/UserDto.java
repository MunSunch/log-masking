package com.munsun.testapp.dto;

import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import io.swagger.v3.oas.annotations.media.Schema;

public class UserDto {

    @Schema(description = "User name")
    private String name;

    @Masked(type = MaskType.PII)
    @Schema(description = "User email")
    private String email;

    @Masked(type = MaskType.CREDENTIAL)
    @Schema(description = "User password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Masked(type = MaskType.PII, showFirst = 2, showLast = 2)
    @Schema(description = "Phone number")
    private String phone;

    public UserDto() {}

    public UserDto(String name, String email, String password, String phone) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
    }

    public String getName()                 { return name; }
    public void setName(String v)           { this.name = v; }
    public String getEmail()                { return email; }
    public void setEmail(String v)          { this.email = v; }
    public String getPassword()             { return password; }
    public void setPassword(String v)       { this.password = v; }
    public String getPhone()                { return phone; }
    public void setPhone(String v)          { this.phone = v; }

    @Override
    public String toString() {
        return "UserDto{name=" + name + ", email=" + email + ", password=" + password + ", phone=" + phone + '}';
    }
}
