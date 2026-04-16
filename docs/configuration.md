---
title: Configuration
layout: default
nav_order: 4
---

# Configuration Reference
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Properties

All properties are optional. The starter works out of the box with sensible
defaults.

### Core properties

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `log.masking.enabled` | `boolean` | `true` | Master switch. When `false`, disables the entire starter — no beans are created, no appenders are modified. |
| `log.masking.mask-char` | `char` | `*` | Global default mask character. Can be overridden per field via `@Masked(maskChar = '#')`. |

### Credential defaults

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `log.masking.credential.replacement` | `String` | `***` | Fixed replacement string for `MaskType.CREDENTIAL`. Uses a constant length to avoid leaking the original value's length. |

### PII defaults

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `log.masking.pii.show-first` | `int` | `1` | Characters to leave unmasked at the start for `MaskType.PII` fields. |
| `log.masking.pii.show-last` | `int` | `2` | Characters to leave unmasked at the end for `MaskType.PII` fields. |

### Financial defaults

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `log.masking.financial.show-last` | `int` | `4` | Characters to leave unmasked at the end for `MaskType.FINANCIAL` fields. Typically shows the last 4 digits of a card number. |

### OpenAPI properties

| Property | Type | Default | Description |
|:---------|:-----|:--------|:------------|
| `log.masking.openapi.enabled` | `boolean` | `true` | Enable automatic OpenAPI schema enrichment. Only takes effect when `springdoc-openapi` is on the classpath. |
| `log.masking.openapi.description-suffix` | `String` | `[MASKED IN LOGS]` | Text appended to the `description` of masked fields in OpenAPI schemas. |
| `log.masking.openapi.credential-format` | `String` | `password` | OpenAPI `format` value applied to `CREDENTIAL` fields. Swagger UI renders `password` format as dots. |

---

## Example configuration

```yaml
log:
  masking:
    enabled: true
    mask-char: '*'

    credential:
      replacement: "***"

    pii:
      show-first: 1
      show-last: 2

    financial:
      show-last: 4

    openapi:
      enabled: true
      description-suffix: "[MASKED IN LOGS]"
      credential-format: "password"
```

---

## Per-environment configuration

Use Spring profiles to adjust masking behavior per environment:

```yaml
# application.yml — production (strict masking)
log:
  masking:
    credential:
      replacement: "***"
    pii:
      show-first: 0
      show-last: 0

---
# application-dev.yml — development (relaxed masking for debugging)
log:
  masking:
    pii:
      show-first: 3
      show-last: 4
    financial:
      show-last: 8
```

{: .tip }
> In development, you may want to see more of the original value for debugging.
> In production, consider setting `show-first: 0` and `show-last: 0` for maximum
> protection.

---

## IDE auto-completion

The starter ships with `additional-spring-configuration-metadata.json`, which
provides auto-completion and documentation hints for all `log.masking.*`
properties in IntelliJ IDEA and VS Code with the Spring Boot extension.

{: .note }
> If auto-completion does not appear, ensure that the
> `spring-boot-configuration-processor` annotation processor has run.
> In Gradle: `./gradlew :log-masking-starter:compileJava`.

---

## Conditional activation

The auto-configuration uses the following conditions:

| Bean | Condition |
|:-----|:----------|
| `MaskingStrategy` | `@ConditionalOnMissingBean` — skipped if you provide your own |
| `FieldMaskingService` | `@ConditionalOnMissingBean` — skipped if you provide your own |
| `MaskingAppenderRegistrar` | `@ConditionalOnClass(LoggerContext)` — only when Logback is present |
| `OpenApiMaskingCustomizer` | `@ConditionalOnClass(OpenApiCustomizer)` + `log.masking.openapi.enabled=true` |

All beans are skipped entirely when `log.masking.enabled=false`.
