---
title: Getting Started
layout: default
nav_order: 2
---

# Getting Started
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Requirements

- Java 17+
- Spring Boot 3.2+
- Logback (included by default with `spring-boot-starter`)

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.munsun:log-masking-starter:0.1.0-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.munsun:log-masking-starter:0.1.0-SNAPSHOT'
}
```

### Maven

```xml
<dependency>
    <groupId>com.munsun</groupId>
    <artifactId>log-masking-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

That's it — the starter auto-configures itself. No XML, no additional beans, no `logback.xml` changes.

---

## Basic usage

### 1. Annotate sensitive fields

```java
import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;

public class UserDto {

    private String name;

    @Masked(type = MaskType.PII)
    private String email;

    @Masked(type = MaskType.CREDENTIAL)
    private String password;

    @Masked(type = MaskType.PII, showFirst = 2, showLast = 2)
    private String phone;

    // constructors, getters, setters
}
```

### 2. Log objects using SLF4J placeholders

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @PostMapping("/users")
    public UserDto create(@RequestBody UserDto user) {
        log.info("Creating user: {}", user);
        return user;
    }
}
```

### 3. Observe masked output

```
2026-04-16 12:00:00 INFO  c.e.t.c.UserController - Creating user: UserDto(name=John Doe, email=j*************om, password=***, phone=+7********67)
```

The original `UserDto` object is **unchanged** — your API still returns unmasked data.
Only the log output is affected.

{: .note }
> Masking only works with SLF4J placeholder syntax (`{}`).
> String concatenation like `log.info("User: " + user)` calls `toString()` before the
> logging framework sees the argument, so it cannot be intercepted.

---

## Mask types

The starter provides four built-in mask types aligned with
[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
recommendations:

| Type | Default behavior | Example input | Example output |
|:-----|:-----------------|:--------------|:---------------|
| `CREDENTIAL` | Full replacement with fixed string `"***"` | `superSecret123` | `***` |
| `PII` | Show first 1, last 2 characters | `john@example.com` | `j*************om` |
| `FINANCIAL` | Show last 4 characters | `4111111111111111` | `************1111` |
| `CUSTOM` | Full mask (all `*`) unless overridden | `anything` | `********` |

All defaults are [configurable]({{ site.baseurl }}/configuration) via `application.yml`.

---

## Annotation parameters

Each `@Masked` annotation accepts optional parameters that override the
`MaskType` defaults:

```java
// Show first 3 and last 4 characters, mask with '#'
@Masked(type = MaskType.PII, showFirst = 3, showLast = 4, maskChar = '#')
private String email;

// Fixed replacement string (highest priority)
@Masked(replacement = "[REDACTED]")
private String ssn;
```

**Parameter resolution priority** (highest wins):

1. `replacement` on the annotation (if non-empty)
2. `showFirst` / `showLast` / `maskChar` on the annotation (if explicitly set)
3. Defaults from `application.yml` for the chosen `MaskType`
4. Built-in defaults in `DefaultMaskingStrategy`

See [Annotations]({{ site.baseurl }}/annotations) for the full parameter reference.

---

## Verifying it works

Add this to any `@SpringBootTest`:

```java
@ExtendWith(OutputCaptureExtension.class)
class MaskingVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(MaskingVerificationTest.class);

    @Test
    void sensitiveDataIsMasked(CapturedOutput output) {
        var user = new UserDto("John", "john@example.com", "secret123", "+79001234567");
        log.info("User: {}", user);

        assertThat(output.getOut()).doesNotContain("john@example.com");
        assertThat(output.getOut()).doesNotContain("secret123");
        assertThat(output.getOut()).contains("password=***");
    }
}
```

---

## Disabling the starter

```yaml
log:
  masking:
    enabled: false
```

When disabled, no beans are created and no appenders are modified.
Your application behaves as if the starter were not on the classpath.
