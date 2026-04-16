---
title: OpenAPI Integration
layout: default
nav_order: 6
---

# OpenAPI Integration
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

When [springdoc-openapi](https://springdoc.org) is on the classpath, the starter
automatically enriches your OpenAPI schemas to indicate which fields are masked
in logs. This helps API consumers understand the sensitivity of each field
directly from the Swagger UI.

No additional configuration is required — the integration activates
automatically via `@ConditionalOnClass`.

---

## What gets added

For each field annotated with `@Masked`, the following extensions are added to
the corresponding OpenAPI property schema:

| Extension | Value | Purpose |
|:----------|:------|:--------|
| `x-masked` | `true` | Marks the field as sensitive |
| `x-mask-type` | `"CREDENTIAL"` / `"PII"` / `"FINANCIAL"` / `"CUSTOM"` | Indicates the category |

Additionally:

- The field's `description` is appended with `[MASKED IN LOGS]`
  (configurable via `log.masking.openapi.description-suffix`)
- `CREDENTIAL` fields get `format: "password"`, which makes Swagger UI render
  the value as dots

### Example schema output

```json
{
  "components": {
    "schemas": {
      "UserDto": {
        "properties": {
          "name": {
            "type": "string",
            "description": "User name"
          },
          "email": {
            "type": "string",
            "description": "User email [MASKED IN LOGS]",
            "x-masked": true,
            "x-mask-type": "PII"
          },
          "password": {
            "type": "string",
            "description": "User password [MASKED IN LOGS]",
            "format": "password",
            "x-masked": true,
            "x-mask-type": "CREDENTIAL"
          }
        }
      }
    }
  }
}
```

---

## Combining with `@Schema`

You can use springdoc's `@Schema` annotation alongside `@Masked`. The
starter appends to the existing description rather than replacing it:

```java
public class UserDto {

    @Masked(type = MaskType.CREDENTIAL)
    @Schema(
        description = "User password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        example = "***"
    )
    private String password;

    @Masked(type = MaskType.PII)
    @Schema(
        description = "Email address",
        example = "j***@***.com"
    )
    private String email;
}
```

Result in OpenAPI schema:
```json
{
  "password": {
    "description": "User password [MASKED IN LOGS]",
    "format": "password",
    "writeOnly": true,
    "example": "***",
    "x-masked": true,
    "x-mask-type": "CREDENTIAL"
  }
}
```

{: .tip }
> Setting `accessMode = WRITE_ONLY` on credential fields prevents them from
> appearing in response schemas — a good practice regardless of log masking.

---

## Dependency setup

Add springdoc to your application (not the starter — it's a `compileOnly`
dependency there):

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.munsun:log-masking-starter:0.1.0-SNAPSHOT")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
}
```

---

## Disabling the integration

If you use springdoc but don't want the automatic schema enrichment:

```yaml
log:
  masking:
    openapi:
      enabled: false
```

---

## Custom OpenAPI customizer

If you need full control over how `@Masked` fields appear in OpenAPI schemas,
provide your own `OpenApiMaskingCustomizer` bean:

```java
@Bean
OpenApiMaskingCustomizer openApiMaskingCustomizer() {
    return new OpenApiMaskingCustomizer(
        myCustomOpenApiProperties,
        Map.of("UserDto", UserDto.class, "PaymentDto", PaymentDto.class)
    );
}
```

Since the starter uses `@ConditionalOnMissingBean`, your bean takes precedence.

---

## How class discovery works

The starter needs to map OpenAPI schema names (e.g., `"UserDto"`) to Java
classes to find `@Masked` annotations. It does this by scanning the
application's base packages (detected via `AutoConfigurationPackages`) at
startup, looking for classes that have at least one `@Masked` field.

The scanning is lightweight — it only reads class metadata, and the result
is a simple `Map<String, Class<?>>` of simple class name to Java class.
