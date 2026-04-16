---
title: Customization
layout: default
nav_order: 7
---

# Customization
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Custom masking strategy

The default `MaskingStrategy` covers common use cases. For specialized
requirements, provide your own bean:

```java
@Bean
public MaskingStrategy maskingStrategy() {
    return (value, annotation) -> {
        return switch (annotation.type()) {
            case CREDENTIAL -> "●●●●●";
            case PII        -> maskEmail(value);    // your custom logic
            case FINANCIAL  -> maskPan(value);      // PCI-DSS compliant
            case CUSTOM     -> "***";
        };
    };
}
```

Since the auto-configuration uses `@ConditionalOnMissingBean`, your bean
**replaces** the default entirely — the starter will not create
`DefaultMaskingStrategy`.

### Accessing properties in a custom strategy

If your custom strategy needs the `LogMaskingProperties`:

```java
@Bean
public MaskingStrategy maskingStrategy(LogMaskingProperties properties) {
    return (value, annotation) -> {
        char maskChar = annotation.maskChar() != '\0'
            ? annotation.maskChar()
            : properties.getMaskChar();
        // your logic using maskChar
        return doMask(value, maskChar);
    };
}
```

---

## Custom field masking service

For advanced scenarios (e.g., masking nested objects, handling collections),
provide your own `FieldMaskingService`:

```java
@Bean
public FieldMaskingService fieldMaskingService(MaskingStrategy strategy) {
    return new FieldMaskingService(strategy) {
        @Override
        public String toMaskedString(Object obj) {
            if (obj instanceof Collection<?> coll) {
                return coll.stream()
                    .map(this::toMaskedString)
                    .collect(Collectors.joining(", ", "[", "]"));
            }
            return super.toMaskedString(obj);
        }
    };
}
```

---

## Selective masking by environment

Combine custom strategies with Spring profiles:

```java
@Bean
@Profile("production")
public MaskingStrategy productionStrategy() {
    // Strict: mask everything, fixed-length output
    return (value, annotation) -> "***";
}

@Bean
@Profile("!production")
public MaskingStrategy developmentStrategy(LogMaskingProperties properties) {
    // Relaxed: use defaults, show more characters
    return new DefaultMaskingStrategy(properties);
}
```

---

## Working with Lombok

### `@ToString.Exclude` + `@Masked`

If you use Lombok's `@ToString`, the generated `toString()` runs before the
logging framework can intercept it (when using string concatenation). With
SLF4J `{}` placeholders, the masking appender replaces `toString()` output
entirely, so Lombok's `@ToString` has no effect on the masked representation.

{: .note }
> The masked output format is always `ClassName(field1=value1, field2=****)`,
> regardless of any `@ToString` configuration. This is by design — the masking
> service builds its own representation to ensure consistent, safe output.

### Recommended pattern

```java
@Data
@ToString(exclude = {"password", "cardNumber"}) // safety net for non-log usage
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

This way:
- In logs (via `{}`): the masking appender produces `UserDto(name=..., email=j***om, password=***, cardNumber=****1111)`
- In non-log `toString()`: Lombok omits `password` and `cardNumber` entirely

---

## Working with Jackson

`@Masked` is a **log-level** concern. It does not affect JSON serialization.
If you also need to mask fields in API responses, combine it with Jackson:

```java
public class UserDto {

    @Masked(type = MaskType.CREDENTIAL)       // masked in logs
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)  // hidden in responses
    private String password;

    @Masked(type = MaskType.PII)               // masked in logs
    @JsonSerialize(using = EmailMaskSerializer.class)  // masked in responses
    private String email;
}
```

---

## Adding masking to an existing project

### Step 1 — Add the dependency

```kotlin
implementation("com.munsun:log-masking-starter:0.1.0-SNAPSHOT")
```

### Step 2 — Identify sensitive fields

Search your codebase for fields that match common sensitive data patterns:

```bash
# Find potential candidates
grep -rn "password\|secret\|token\|apiKey\|cardNumber\|ssn\|email\|phone" \
  --include="*.java" src/
```

### Step 3 — Add annotations incrementally

Start with the highest-risk fields (`CREDENTIAL`), then move to `PII` and
`FINANCIAL`:

```java
// Phase 1: Credentials (highest risk)
@Masked(type = MaskType.CREDENTIAL)
private String password;

// Phase 2: PII
@Masked(type = MaskType.PII)
private String email;

// Phase 3: Financial
@Masked(type = MaskType.FINANCIAL)
private String cardNumber;
```

### Step 4 — Verify with tests

```java
@Test
void noSensitiveDataInLogs(CapturedOutput output) {
    service.processUser(testUser);

    assertThat(output.getOut())
        .doesNotContain("realPassword")
        .doesNotContain("4111111111111111")
        .doesNotContain("real@email.com");
}
```

---

## Limitations

| Limitation | Workaround |
|:-----------|:-----------|
| Only works with SLF4J `{}` placeholders | Avoid string concatenation in log calls |
| Does not mask inside collections/maps | Annotate the element class, not the collection field |
| Does not mask `toString()` output directly | Use `@ToString.Exclude` in Lombok as a safety net |
| Startup logs (before registrar runs) are not masked | Acceptable — startup logs rarely contain user PII |
| Only Logback is supported | Log4j2 support planned for a future release |
