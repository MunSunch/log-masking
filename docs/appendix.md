---
title: Appendix
layout: default
nav_order: 8
---

# Appendix
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## A. Comparison with other solutions

| Criteria | owasp-security-logging | compliance-logging | logback-obfuscator | sensitive-annotation | **log-masking-starter** |
|:---------|:---:|:---:|:---:|:---:|:---:|
| Approach | Marker per log call | Marker + regex | Regex PatternLayout | Annotation + toString | **Annotation + Appender** |
| Partial masking | No | No | FIRST-LEN-LAST | No | **showFirst / showLast** |
| OWASP data categories | Markers | No | No | No | **MaskType enum** |
| Spring Boot auto-config | No | No | No | No | **Yes** |
| `application.yml` config | No | No | No | No | **Yes** |
| Preserves layout/encoder | No | No | No | N/A | **Yes** |
| OpenAPI integration | No | No | No | No | **Yes** |
| Custom strategy | No | No | Custom regex | No | **@ConditionalOnMissingBean** |
| Maven Central | Yes | No | Yes | No | **Yes** |
| Actively maintained | No (2021) | No (2022) | Solo dev | No (2018) | **Yes** |

---

## B. OWASP alignment

This starter follows the [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
recommendations:

| OWASP Recommendation | How the starter addresses it |
|:---------------------|:----------------------------|
| Do not log passwords, tokens, API keys | `MaskType.CREDENTIAL` — full replacement, hides length |
| Mask PII (email, phone, name) | `MaskType.PII` — partial masking with configurable visibility |
| Protect financial data (card numbers, accounts) | `MaskType.FINANCIAL` — shows last 4 digits only |
| Use de-identification techniques | Configurable `showFirst`/`showLast`/`replacement` |
| Sanitize log data before examination | Masking happens at the appender level, before output |
| Protect log data in transit | Out of scope (use TLS for log transport) |

---

## C. Spring Boot Actuator compatibility

Spring Boot Actuator uses `SanitizingFunction` to mask sensitive values in
`/actuator/env` and `/actuator/configprops` endpoints. The default keys include
`password`, `secret`, `key`, `token`, `credentials`.

This starter does **not** interfere with Actuator sanitization — they operate at
different levels:

| Mechanism | Scope | Level |
|:----------|:------|:------|
| Actuator `SanitizingFunction` | Configuration properties in actuator endpoints | HTTP response |
| `@Masked` annotation | Object fields in log messages | Log output |

They are complementary and can be used together.

---

## D. Bean reference

| Bean | Type | Condition | Description |
|:-----|:-----|:----------|:------------|
| `maskingStrategy` | `MaskingStrategy` | `@ConditionalOnMissingBean` | Masks individual field values |
| `fieldMaskingService` | `FieldMaskingService` | `@ConditionalOnMissingBean` | Scans classes, builds masked strings |
| `maskingAppenderRegistrar` | `MaskingAppenderRegistrar` | `@ConditionalOnClass(LoggerContext)` | Wraps Logback appenders at startup |
| `openApiMaskingCustomizer` | `OpenApiMaskingCustomizer` | `@ConditionalOnClass(OpenApiCustomizer)` | Enriches OpenAPI schemas |

---

## E. Supported data types

`@Masked` works on fields of any type. The value is converted to a `String` via
`String.valueOf(value)` before masking. Common types and their behavior:

| Field type | Conversion | Masking |
|:-----------|:-----------|:--------|
| `String` | Identity | Masked directly |
| `int`, `long`, etc. | `String.valueOf()` | Masked (e.g., `12345` → `***`) |
| `BigDecimal` | `toString()` | Masked |
| `UUID` | `toString()` | Masked |
| `null` | `"null"` | Not masked (rendered as `null`) |
| `Enum` | `name()` | Masked |

---

## F. Migration from other solutions

### From owasp-security-logging

**Before:**
```java
// Every log call must use the marker
LOGGER.info(SecurityMarkers.CONFIDENTIAL, "password={}", password);
```

**After:**
```java
// Annotate the field once — all log calls are automatically masked
@Masked(type = MaskType.CREDENTIAL)
private String password;

// Then just log normally
log.info("user={}", user);
```

### From custom PatternLayout with regex

**Before:**
```xml
<!-- logback.xml — replaces your entire layout -->
<layout class="com.myapp.MaskingPatternLayout">
    <maskPattern>"password"\s*:\s*"(.*?)"</maskPattern>
    <maskPattern>"email"\s*:\s*"(.*?)"</maskPattern>
</layout>
```

**After:**
```yaml
# application.yml — no logback.xml changes needed
log:
  masking:
    enabled: true
```

```java
// Annotate fields instead of writing regex
@Masked(type = MaskType.CREDENTIAL)
private String password;

@Masked(type = MaskType.PII)
private String email;
```

Remove the custom `MaskingPatternLayout` class and the `<layout>` configuration
from `logback.xml`. Your original layout/encoder configuration is restored.

---

## G. Frequently asked questions

**Q: Does masking affect performance?**

Minimal impact. Class metadata is scanned once and cached. For log calls without
`@Masked` arguments, the overhead is a hash map lookup per argument (~100ns).

**Q: Can I mask fields in nested objects?**

Not in the current version. Only top-level log arguments are checked. If a nested
object has `@Masked` fields and is logged directly, it will be masked. If it's a
field of another object, its `toString()` output will appear unmasked.

**Q: Does it work with structured logging (JSON)?**

The masking operates at the `ILoggingEvent` level, so it works with any encoder
including logstash-logback-encoder. The masked values appear in the
`formattedMessage` field of the JSON output.

**Q: Can I use it with Log4j2?**

Not currently. The appender wrapping mechanism is Logback-specific. Log4j2
support is planned for a future release.

**Q: What happens if I annotate a field in a record?**

Java records work the same as classes. `@Masked` on record components is
supported:

```java
public record UserRecord(
    String name,
    @Masked(type = MaskType.PII) String email
) {}
```

**Q: Why does the masked output format differ from my `toString()`?**

The masking service builds its own representation
(`ClassName(field1=value, field2=****)`) to ensure every field is accounted for.
Your original `toString()` is not called when the object is intercepted by the
masking appender.

**Q: Does it work with `@Slf4j` (Lombok)?**

Yes. Lombok's `@Slf4j` generates a standard SLF4J logger. As long as you use
`{}` placeholders, masking works as expected.
