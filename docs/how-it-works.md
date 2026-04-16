---
title: How It Works
layout: default
nav_order: 5
---

# How It Works
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Architecture overview

```
log.info("User: {}", userDto)
        │
        ▼
┌─────────────────────┐
│   Logback Logger     │  Creates LoggingEvent with original arguments
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  MaskingAppender     │  Wraps original appender (Console, File, etc.)
│                     │
│  ① Check arguments   │  Does any argument's class have @Masked fields?
│  ② Wrap if needed    │  Replace arg with MaskedObjectWrapper
│  ③ Decorate event    │  Create MaskedLoggingEventDecorator
│  ④ Forward           │  delegate.doAppend(maskedEvent)
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Original Appender   │  Your ConsoleAppender / FileAppender / etc.
│  (unchanged config)  │  Encoder, Layout, Pattern — all untouched
│                     │
│  Calls event         │
│  .getFormattedMessage()
│    └─▶ MaskedObjectWrapper.toString()
│         └─▶ FieldMaskingService.toMaskedString(obj)
│              └─▶ MaskingStrategy.mask(value, annotation)
└─────────────────────┘
         │
         ▼
    Masked log output
```

---

## Key components

### `MaskingAppender`

A Logback appender that **wraps** an existing appender. At application startup,
`MaskingAppenderRegistrar` replaces each appender on every Logback logger with a
`MaskingAppender` that delegates to the original:

```
Before:  Logger → ConsoleAppender
After:   Logger → MaskingAppender → ConsoleAppender
```

The original appender's configuration (layout, encoder, filters, pattern) is
**completely preserved**.

### `MaskedLoggingEventDecorator`

Implements `ILoggingEvent` and wraps the original log event. Overrides two
methods:

- `getArgumentArray()` — returns arguments with `@Masked` objects replaced by
  `MaskedObjectWrapper` instances
- `getFormattedMessage()` — recomputes the message using the masked arguments

All other methods (timestamp, level, thread name, MDC, etc.) delegate to the
original event unchanged.

### `MaskedObjectWrapper`

A lightweight proxy whose `toString()` returns the masked string representation
of the wrapped object. The original object is never modified.

### `FieldMaskingService`

Scans classes for `@Masked` fields using reflection and caches the metadata.
Builds masked string representations in the format:

```
ClassName(field1=value1, field2=****, field3=value3)
```

Class metadata is scanned **once per class** and cached for the lifetime of the
application using a `ConcurrentHashMap`.

### `DefaultMaskingStrategy`

Implements `MaskingStrategy`. Resolves parameters from the `@Masked` annotation
and falls back to `LogMaskingProperties` for unset values:

```
@Masked(type=PII, showLast=4)
         │              │
         ▼              ▼
    PII defaults    annotation override
    showFirst=1     showLast=4 (not -1, so use this)
    showLast=2      ← overridden by annotation
    maskChar=*      ← from properties
```

---

## Why appender wrapping?

Several approaches were considered. Here's why appender wrapping was chosen:

| Approach | Invasiveness | Works for 1-arg log calls? | Requires logback.xml? |
|:---------|:-------------|:---------------------------|:----------------------|
| **Custom PatternLayout** | Replaces layout entirely | Yes | Yes |
| **Custom MessageConverter** | Modifies pattern (`%msg` → `%maskedMsg`) | Yes | Yes |
| **TurboFilter** | Non-invasive | **No** — cannot modify arguments for 1/2-arg calls | No |
| **Appender wrapping** | Non-invasive | **Yes** | **No** |

**TurboFilter limitation:** Logback's `TurboFilter.decide()` receives arguments
as individual `Object` parameters for 1-arg and 2-arg log calls. Since Java
passes references by value, modifying the parameter inside the filter does not
affect the `LoggingEvent` created afterward. This makes TurboFilter unsuitable
for argument masking.

**Appender wrapping** operates after the `LoggingEvent` is created but before the
encoder formats it. By replacing the event with a decorator, it transparently
masks arguments regardless of the log call signature.

---

## Startup sequence

1. Spring Boot auto-configuration creates `MaskingStrategy`, `FieldMaskingService`,
   and `MaskingAppenderRegistrar` beans.

2. `MaskingAppenderRegistrar` implements `SmartInitializingSingleton` — it runs
   after all singleton beans are instantiated.

3. The registrar obtains the `LoggerContext` from SLF4J and iterates all loggers.

4. For each appender, it wraps the appender with a `MaskingAppender` and attaches
   the wrapper to the logger.

5. From this point on, every log event passes through the masking layer.

{: .note }
> Log messages emitted **during** Spring context initialization (before the
> registrar runs) are not masked. This is by design — startup logs rarely
> contain user PII, and early logging must remain functional.

---

## Performance

| Operation | Cost |
|:----------|:-----|
| Class scan (reflection) | **Once per class**, result cached in `ConcurrentHashMap` |
| `hasMaskedFields()` check | Hash map lookup — O(1) |
| Argument wrapping | Only for arguments whose class has `@Masked` fields |
| String building | Only when `toString()` is called by the encoder |

For log calls where **no argument** has `@Masked` fields, the overhead is a
single hash map lookup per argument — typically under 100 nanoseconds.

JDK and platform classes (`java.*`, `javax.*`) are excluded from scanning
to avoid `InaccessibleObjectException` on Java 16+.
