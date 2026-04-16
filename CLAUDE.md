# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test

Gradle multi-module project (Kotlin DSL). Java 17, Spring Boot 3.3.5, versions centralized in `gradle/libs.versions.toml`.

```bash
./gradlew build                                      # build everything
./gradlew :log-masking-starter:test                  # starter unit tests
./gradlew :test-app:test                             # integration tests via test-app
./gradlew :log-masking-starter:test --tests '*DefaultMaskingStrategyTest*'   # single test
./gradlew :test-app:bootRun                          # run the sample app (port 8080, Swagger at /swagger-ui.html)
./gradlew :log-masking-starter:compileJava           # regenerate spring-configuration-metadata.json
```

**Build output is relocated to `C:/tmp/log-masking-build/<module>`** (see `build.gradle.kts:12`). This is a workaround for class-loading failures when the project path contains non-ASCII characters (Cyrillic user profile on Windows). Do not revert the `layout.buildDirectory` override — move it only if you also move the whole project to an ASCII path.

## Modules

- **`log-masking-starter/`** — the published library. Spring Boot auto-configured starter (`java-library` plugin, no `bootJar`). Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **`test-app/`** — sample Spring Boot web app consuming the starter. Used for integration tests (`MaskingIntegrationTest`, `OpenApiIntegrationTest`) and manual verification via Swagger UI.

## Architecture

The starter masks sensitive fields in log output **without modifying `logback.xml`, layouts, encoders, or the fields themselves**. It achieves this by wrapping Logback appenders at startup and substituting SLF4J arguments with masking proxies.

### Pipeline (read top-to-bottom)

1. **`LogMaskingAutoConfiguration`** — gated by `log.masking.enabled` (default `true`). Creates `MaskingStrategy`, `FieldMaskingService`, and `MaskingAppenderRegistrar` beans. OpenAPI enrichment is a nested `@Configuration` gated on springdoc being on the classpath.
2. **`MaskingAppenderRegistrar`** (`SmartInitializingSingleton`) — runs *after* all singletons exist. Walks every Logback `Logger`, detaches each existing `Appender`, and reattaches it wrapped in a `MaskingAppender`. Wrapped appenders are tagged with the `masking:` name prefix to make the operation idempotent.
3. **`MaskingAppender`** — intercepts each `ILoggingEvent`. For every argument whose class has `@Masked` fields (checked via `FieldMaskingService.hasMaskedFields` — a cached O(1) lookup), it substitutes a `MaskedObjectWrapper`, then wraps the whole event in `MaskedLoggingEventDecorator` so `getFormattedMessage()` recomputes with the masked args. The original appender's encoder/layout/pattern runs unchanged.
4. **`MaskedObjectWrapper.toString()`** → `FieldMaskingService.toMaskedString()` → `MaskingStrategy.mask()` produces `ClassName(field1=value1, field2=****)`.

### Key invariants & gotchas

- **Only SLF4J `{}` placeholders are masked.** `log.info("user: " + user)` calls `toString()` before Logback ever sees the arg, so no interception is possible. If you add a feature or test, use `log.info("...: {}", obj)`.
- **Startup logs are not masked** — anything logged before `MaskingAppenderRegistrar.afterSingletonsInstantiated()` passes through untouched. By design.
- **Collections are not auto-masked.** `FieldMaskingService` masks fields of the top-level arg only. A `List<UserDto>` argument is rendered by `String.valueOf()`. To mask element-wise, subclass `FieldMaskingService` (see `docs/customization.md`) or annotate elements individually.
- **Reflection skips JDK/platform classes.** `FieldMaskingService.scan` stops ascending at modules whose name starts with `java.` to avoid `InaccessibleObjectException` on Java 16+. Don't remove this guard.
- **Class metadata is cached forever** in a `ConcurrentHashMap` keyed by `Class<?>`. Safe because classes are effectively permanent; don't add mutation paths.
- **Parameter resolution priority** (used throughout `DefaultMaskingStrategy`): annotation `replacement` > explicit annotation `showFirst`/`showLast`/`maskChar` (sentinels: `-1` / `'\0'`) > `LogMaskingProperties` defaults per `MaskType` > built-in defaults. Preserve this order when changing the strategy.
- **`@ConditionalOnMissingBean` on `MaskingStrategy` and `FieldMaskingService`** means a user bean fully replaces the default — if you add new defaults, respect this contract.

### OpenAPI integration

`OpenApiMaskingCustomizer` enriches Swagger schemas with `x-masked` / `x-mask-type` extensions, appends a description suffix, and sets `format: password` for `CREDENTIAL` fields. The list of `@Masked` classes is built once at startup via `ClassPathScanningCandidateComponentProvider` over the application's `AutoConfigurationPackages`. The customizer bean is conditional on `org.springdoc.core.customizers.OpenApiCustomizer` being on the classpath — the starter declares springdoc as `compileOnly`, so consumers opt in by adding it themselves.

## Documentation

End-user docs live in `docs/` (Jekyll / GitHub Pages). The full design rationale, customization recipes, and alternatives-considered table are in `docs/how-it-works.md` and `docs/customization.md`. `PLAN.md` at the root captures the original design plan.
