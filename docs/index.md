---
title: Overview
layout: default
nav_order: 1
---

# Log Masking Spring Boot Starter
{: .fs-9 }

Annotation-based sensitive data masking for Spring Boot 3 logs.
Zero-config, non-invasive, OWASP-aligned.
{: .fs-6 .fw-300 }

---

## What it does

Add `@Masked` to any field — its value is automatically replaced with a masked
representation in **every** log message, without touching your logging
configuration, layouts, or encoders.

```java
public class UserDto {

    private String name;

    @Masked(type = MaskType.PII)
    private String email;

    @Masked(type = MaskType.CREDENTIAL)
    private String password;

    @Masked(type = MaskType.FINANCIAL)
    private String cardNumber;
}
```

```java
log.info("Created user: {}", user);
// Output: Created user: UserDto(name=John, email=j*************om, password=***, cardNumber=************1111)
```

The original object is **never mutated** — masking applies only to the string
representation produced for log output.

---

## Key features

| Feature | Description |
|:--------|:------------|
| **Annotation-driven** | Mark fields with `@Masked` — no regex, no XML, no marker boilerplate |
| **Non-invasive** | Does not replace your `PatternLayout`, `Encoder`, or `logback.xml`. Works with any appender configuration |
| **OWASP-aligned categories** | Built-in `MaskType` enum: `CREDENTIAL`, `PII`, `FINANCIAL`, `CUSTOM` — each with sensible defaults |
| **Configurable** | Override defaults per mask type via `application.yml` or per field via annotation parameters |
| **Extensible** | Provide your own `MaskingStrategy` bean to fully control masking logic |
| **OpenAPI integration** | Automatically enriches Swagger schemas with `x-masked` extensions when springdoc is present |
| **Spring Boot 3** | Built for Spring Boot 3.2+ with `@AutoConfiguration` and type-safe properties |

---

## Quick start

**1. Add the dependency**

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.munsun:log-masking-starter:0.1.0-SNAPSHOT")
}
```

**2. Annotate your fields**

```java
public class UserDto {
    @Masked(type = MaskType.CREDENTIAL)
    private String password;
}
```

**3. Log as usual**

```java
log.info("User: {}", user);
// password is masked automatically
```

No additional configuration required.
{: .fs-3 .fw-300 }

[Get started]({{ site.baseurl }}/getting-started){: .btn .btn-primary .fs-5 .mb-4 .mb-md-0 .mr-2 }
[Configuration reference]({{ site.baseurl }}/configuration){: .btn .fs-5 .mb-4 .mb-md-0 }
