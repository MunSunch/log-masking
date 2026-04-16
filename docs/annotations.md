---
title: Annotations
layout: default
nav_order: 3
---

# Annotations
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## `@Masked`

Marks a field as containing sensitive data. When the owning object is passed as
a log argument via SLF4J `{}` placeholders, the field value is replaced with a
masked representation.

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Masked { ... }
```

### Parameters

| Parameter | Type | Default | Description |
|:----------|:-----|:--------|:------------|
| `type` | `MaskType` | `CUSTOM` | Category of sensitive data. Determines the default masking strategy. |
| `maskChar` | `char` | `'\0'` | Mask character. `'\0'` means "use default from properties" (default: `*`). |
| `showFirst` | `int` | `-1` | Characters to leave unmasked at the start. `-1` means "use default for this MaskType". |
| `showLast` | `int` | `-1` | Characters to leave unmasked at the end. `-1` means "use default for this MaskType". |
| `replacement` | `String` | `""` | Fixed replacement string. **If non-empty, takes priority over all other parameters.** |

### Parameter resolution

Parameters are resolved in order of priority (highest first):

```
replacement (if non-empty)
  └─▶ annotation parameters (showFirst, showLast, maskChar — if explicitly set)
       └─▶ application.yml defaults for the MaskType
            └─▶ built-in defaults
```

### Examples

```java
// PII with type defaults (showFirst=1, showLast=2)
@Masked(type = MaskType.PII)
private String email;
// "john@example.com" → "j*************om"

// PII with annotation override
@Masked(type = MaskType.PII, showLast = 4)
private String email;
// "john@example.com" → "j***********.com"

// CREDENTIAL — always fixed replacement
@Masked(type = MaskType.CREDENTIAL)
private String password;
// "anyPassword" → "***"

// FINANCIAL — show last 4
@Masked(type = MaskType.FINANCIAL)
private String cardNumber;
// "4111111111111111" → "************1111"

// CUSTOM — full mask by default
@Masked
private String secret;
// "mysecret" → "********"

// CUSTOM with explicit parameters
@Masked(showFirst = 2, showLast = 2, maskChar = '#')
private String phone;
// "+79001234567" → "+7########67"

// Fixed replacement (ignores everything else)
@Masked(replacement = "[REDACTED]")
private String ssn;
// "123-45-6789" → "[REDACTED]"
```

---

## `MaskType`

Enum defining categories of sensitive data. Each category has its own default
masking behavior, configurable via `application.yml`.

```java
public enum MaskType {
    CREDENTIAL,   // passwords, tokens, API keys
    PII,          // email, phone, name
    FINANCIAL,    // card numbers, bank accounts
    CUSTOM        // user-defined behavior
}
```

### Default behavior per type

| Type | showFirst | showLast | maskChar | Special |
|:-----|:----------|:---------|:---------|:--------|
| `CREDENTIAL` | — | — | — | Full replacement with `"***"` (hides original length) |
| `PII` | 1 | 2 | `*` | — |
| `FINANCIAL` | 0 | 4 | `*` | — |
| `CUSTOM` | 0 | 0 | `*` | Full mask, length preserved |

{: .tip }
> `CREDENTIAL` always uses a **fixed-length** replacement. This is intentional — revealing
> the length of a password or token is itself a security risk.

---

## Inheritance

`@Masked` annotations are inherited through the class hierarchy. If a parent class
declares masked fields, they are included when masking a subclass instance:

```java
public class BaseUser {
    @Masked(type = MaskType.CREDENTIAL)
    private String password;
}

public class AdminUser extends BaseUser {
    @Masked(type = MaskType.PII)
    private String adminEmail;
}

// log.info("Admin: {}", adminUser);
// → Admin: AdminUser(password=***, adminEmail=a****om)
```

---

## Where masking applies

| Scenario | Masked? | Why |
|:---------|:--------|:----|
| `log.info("User: {}", user)` | Yes | Object is a log argument — intercepted by `MaskingAppender` |
| `log.info("User: " + user)` | **No** | `toString()` is called before the logging framework sees the argument |
| `log.info("email={}", user.getEmail())` | **No** | The argument is a `String`, not an annotated object |
| `objectMapper.writeValueAsString(user)` | **No** | Jackson serialization is unrelated to logging |
| `System.out.println(user)` | **No** | Not a logging call |

{: .important }
> Always use SLF4J `{}` placeholders for objects that contain `@Masked` fields.
> This is already a best practice for performance
> (see [SLF4J FAQ](https://www.slf4j.org/faq.html#logging_performance)).
