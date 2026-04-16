# Plan: spring-boot-log-masking-starter

## Цель

Библиотека-стартер для Spring Boot 3, которая маскирует чувствительные данные в логах
на основе аннотаций на полях. Не перезаписывает дефолтную или пользовательскую конфигурацию логирования.

---

## Анализ существующих решений и стандартов

### Стандарты и рекомендации

| Источник | Суть | Ссылка |
|----------|------|--------|
| **OWASP Logging Cheat Sheet** | Категории данных, которые нельзя логировать: пароли, токены, PII, номера карт, медицинские данные. Рекомендует удалять, маскировать, хешировать или шифровать | [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) |
| **OWASP Top 10 (A09:2025)** | Security Logging and Alerting Failures — утечка PII через логи является уязвимостью | [OWASP Top 10](https://owasp.org/Top10/2025/A09_2025-Security_Logging_and_Alerting_Failures/) |
| **Logback 1.5.22+** | Автоматическое маскирование переменных подстановки, чьи имена содержат `password`, `secret`, `confidential`. Только переменные в `logback.xml`, не поля в объектах | [Logback docs](https://logback.qos.ch/manual/configuration.html) |
| **Spring Boot Actuator** | `SanitizingFunction` для `/env`, `/configprops`. Дефолтные ключи: `password`, `secret`, `key`, `token`, `credentials`. В Boot 3.x — allowlist-модель (всё скрыто по умолчанию). Шаблон для наших дефолтных имён полей | [Spring Boot docs](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) |
| **AWS CloudWatch Data Protection** | Инфраструктурный уровень: managed data identifiers для PII в CloudWatch Logs. Замена на `[MASKED]`. Дополняет, но не заменяет in-app маскирование | [AWS docs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/protect-sensitive-log-data.html) |

---

### Детальный анализ существующих библиотек

#### 1. owasp-security-logging

| | |
|---|---|
| **Репо** | [github.com/augustd/owasp-security-logging](https://github.com/augustd/owasp-security-logging) |
| **Stars / Forks** | ~121 / ~34 |
| **Последний релиз** | v1.1.7 (декабрь 2021, патч Log4Shell). **Заброшен.** |
| **Лицензия** | Apache 2.0 |

**Архитектура:**
- `MaskingConverter` наследует `ReplacingCompositeConverter<ILoggingEvent>` (Logback).
- Проверяет наличие маркера `CONFIDENTIAL` на лог-событии.
- Если маркер есть — **все** аргументы заменяются на `"********"` через `MessageFormatter.arrayFormat()`.

**Использование:**
```java
LOGGER.info(SecurityMarkers.CONFIDENTIAL, "password={}", password);
// Output: password=********
```

**Плюсы:**
- OWASP-бренд, знакомый подход через SLF4J маркеры.
- Набор маркеров: `CONFIDENTIAL`, `SECRET`, `RESTRICTED`, `SECURITY_SUCCESS/FAILURE/AUDIT`.
- Поддержка Logback (MaskingConverter) + Log4j (MaskingRewritePolicy).

**Минусы:**
- **All-or-nothing**: если маркер стоит — маскируются ВСЕ аргументы, нет выборочного контроля.
- **Нет частичного маскирования** (showFirst/showLast). Захардкожено `"********"`.
- **Нет Spring Boot автоконфигурации**. Открытый issue #60: маркеры не работают со Spring Boot.
- **Требует `logback.xml`**: замена `%msg` на `%mask` + `<conversionRule>`.
- **Гранулярность** — уровень лог-вызова, не уровень поля. Разработчик обязан помечать каждый `log.info()`.
- **Заброшен**: 15 open issues, 2 open PR, нет коммитов 4+ года.

---

#### 2. compliance-logging (SlalomBuild)

| | |
|---|---|
| **Репо** | [github.com/SlalomBuild/compliance-logging](https://github.com/SlalomBuild/compliance-logging) |
| **Stars** | 2 |
| **Последний коммит** | ноябрь 2022. **Заброшен.** |
| **Maven Central** | **Не опубликован.** Сборка настроена, но артефакты не выложены. |
| **Лицензия** | MIT |

**Архитектура (4 модуля):**
- `compliance-logging-core` — `MaskService` интерфейс, `JsonMaskService`, `LombokMaskService`.
- `compliance-logging-logback` — `PatternMaskingLayout` (заменяет `PatternLayout`).
- `compliance-logging-log4j2` — `ComplianceConverter` (`%mm` / `%maskMessage`).
- `compliance-logging-logback-logstash` — `MaskingMessageJsonProvider` для logstash-logback-encoder.

**Как маскирует:**
- **Regex по именам полей** (не аннотации). `BaseMaskService` собирает `Pattern` из имён полей через `|`.
- Два формата: `JsonMaskService` (ищет `"field": "value"`) и `LombokMaskService` (ищет `field=value`).
- Маска **захардкожена**: `***********` (11 звёздочек), не конфигурируется.
- **Маркер обязателен**: разработчик должен передать `MaskType.JSON` или `MaskType.LOMBOK` в каждый лог-вызов.

**Конфигурация:**
```xml
<!-- logback.xml -->
<layout class="com.slalom.logging.compliance.logback.PatternMaskingLayout">
    <fields>password,ssn,email</fields>
    <pattern>%d %-5level - %msg%n</pattern>
</layout>
```

**Минусы:**
- **Не на Maven Central** — использовать нельзя без сборки из исходников.
- **Маркер обязателен** на каждом лог-вызове — забыл маркер = утечка данных.
- **Только 2 формата данных**: JSON и Lombok toString. Нет поддержки plain-text key=value.
- **Захардкоженная маска** — нет частичного маскирования.
- **Заменяет PatternLayout** в Logback — ломает пользовательский layout.
- **Нет Spring Boot автоконфигурации.**

---

#### 3. logback-sensitive-data-obfuscator

| | |
|---|---|
| **Репо** | [github.com/orczykowski/logback-sensitive-data-obfuscator-pattern-layout](https://github.com/orczykowski/logback-sensitive-data-obfuscator-pattern-layout) |
| **Stars** | 0 |
| **Последний релиз** | v2.0.0 (апрель 2026). v1.0.0 — июль 2023. |
| **Maven Central** | `io.github.orczykowski:logback-sensitive-data-obfuscator-pattern-layout:2.0.0` |
| **Требует** | Java 25+ (v2.0.0), Logback 1.5.x+ |

**Архитектура (6 классов):**
- `AbstractSensitiveDataObfuscatorLayout` — наследует `PatternLayout`. `doLayout()` вызывает родителя, затем применяет regex-замены к готовой строке.
- `MaskSensitiveDataLayout` — полная маска: значение заменяется на `********`.
- `MaskSensitiveDataAsShortcutLayout` — формат `FIRST-LENGTH-LAST` (например `john.doe@example.com` → `j-20-m`). Полезно для staging — видна "форма" данных без раскрытия.
- `SensitiveDataPatternFactory` — 4 встроенных regex-паттерна: `JSON`, `EQUAL_AND_SQUARE_BRACKETS`, `EQUAL_AND_BRACKETS`, `EQUAL_AND_DOUBLE_QUOTES`.
- `TimeoutRegexCharSequence` — защита от ReDoS: таймаут на regex (дефолт 500мс).

**Конфигурация:**
```xml
<layout class="...MaskSensitiveDataLayout">
    <patternName>JSON</patternName>
    <patternName>EQUAL_AND_SQUARE_BRACKETS</patternName>
    <fieldName>email</fieldName>
    <fieldName>ssn</fieldName>
    <customPattern>my-regex-with-[PROPERTY_NAME]-placeholder</customPattern>
    <mask>***</mask>
    <regexTimeoutMillis>500</regexTimeoutMillis>
</layout>
```

**Плюсы:**
- Формат `FIRST-LENGTH-LAST` — уникальная идея, удобна для дебага.
- Кастомные regex с `[PROPERTY_NAME]` placeholder.
- Защита от ReDoS.
- Опубликован на Maven Central.

**Минусы:**
- **Java 25+** в v2.0.0 — отсекает подавляющее большинство проектов.
- **Заменяет PatternLayout** — ломает пользовательскую конфигурацию.
- **0 stars**, один разработчик, 2 релиза за 3 года.
- **Только XML-конфигурация**, нет Spring Boot автоконфигурации.
- **Regex по готовой строке** — производительность пропорциональна длине лога.
- Нет поддержки structured logging (работает только с отформатированной строкой).

**Logstash-вариант** (`logstash-logback-sensitive-data-obfuscator`):
- Тот же автор, та же логика, но наследует `MaskingJsonGeneratorDecorator` вместо `PatternLayout`.
- Для JSON-логирования в ELK-стек. 1 star.

---

#### 4. sensitive-annotation

| | |
|---|---|
| **Репо** | [github.com/hbagchi/sensitive-annotation](https://github.com/hbagchi/sensitive-annotation) |
| **Stars** | 3 |
| **Последний коммит** | март 2018. **Заброшен.** |
| **Maven Central** | Не опубликован. |

**Архитектура:**
- Аннотация `@Sensitive` на полях.
- Модели наследуют `BaseModel`, который переопределяет `toString()` через рефлексию.
- Поля с `@Sensitive` заменяются символом `+` (длина = длине оригинала).

**Минусы:**
- **Требует наследования** от `BaseModel` — инвазивно.
- **Нет Logback-интеграции** — работает только через `toString()`.
- **Нет конфигурации** — захардкожен символ `+`, нет частичного маскирования.
- **Proof-of-concept**: 5 KB кода, нет тестов, нет документации, 8 лет без обновлений.

---

#### 5. logback-masking-pattern-layouts (mediquest-nl)

| | |
|---|---|
| **Репо** | [github.com/mediquest-nl/logback-masking-pattern-layouts](https://github.com/mediquest-nl/logback-masking-pattern-layouts) |
| **Stars** | 8 |
| **Последний коммит** | июль 2025. **Активен.** |
| **Артефакт** | Clojars `mediquest-nl/logback-masking-pattern-layouts:2.0.0` |
| **Язык** | Clojure (gen-class) |

**Архитектура:**
- Наследует `PatternLayout` (через Clojure `gen-class`). `doLayout()` применяет regex-scrub.
- Пары `<regex>` + `<replacement>` в `logback.xml`. Поддержка capture groups (`$1`).
- Флаг `<useDefaultMediquestReplacements>` — встроенные паттерны для email, телефонов, IBAN, номеров карт, BSN, паролей, имён.
- Вариант `GoogleCloudMaskingPatternLayout` для structured JSON в GCP.

**Плюсы:**
- Активно поддерживается, production-ready.
- Хороший набор дефолтных паттернов.
- Гибкие regex + replacement с capture groups.

**Минусы:**
- **Заменяет PatternLayout**.
- **Clojure** — необычная зависимость для Java-проекта.
- **Нет Spring Boot автоконфигурации.**
- **Clojars**, не Maven Central.

---

#### 6. Baeldung-подход (туториал)

**Не библиотека, а паттерн.** Описан в [статье Baeldung](https://www.baeldung.com/logback-mask-sensitive-data).

**Суть:**
- Один класс `MaskingPatternLayout` наследует `PatternLayout`.
- `List<Pattern>` заполняется из `<maskPattern>` элементов в `logback.xml`.
- `doLayout()` вызывает родителя, затем последовательно `Matcher.replaceAll()` по каждому паттерну.

**Используется как шаблон** большинством решений выше (compliance-logging, logback-sensitive-data-obfuscator, mediquest).

---

### Смежные подходы (не Logback-специфичные)

#### Jackson-based маскирование

Кастомный `@JsonSerialize(using = MaskingSerializer.class)` или `BeanSerializerModifier` через Jackson `Module`.
- **Плюс**: маскирование на границе сериализации — работает в API-ответах, логах (если объекты сериализуются в JSON), аудите.
- **Минус**: работает только при Jackson-сериализации, не при `toString()` / plain-text логировании.
- Нет доминирующей библиотеки, большинство команд пишут свою (~50 строк кода).

#### Lombok @ToString.Mask

- **Issue #2197** в lombok: запрос на `@ToString.Mask` — **не реализован**, issue открыт.
- **Workaround**: `@ToString.Exclude` + `@ToString.Include(name = "field")` на кастомном геттере, возвращающем маску.
- `@ToString(onlyExplicitlyIncluded = true)` для явного контроля.

#### Spring Boot Actuator SanitizingFunction

- Интерфейс `SanitizingFunction` (Boot 2.6+) для `/env`, `/configprops`.
- Дефолтные ключи: `password`, `secret`, `key`, `token`, `credentials`, `vcap_services`.
- Boot 3.x: **allowlist-модель** — всё скрыто по умолчанию.
- **Релевантно**: можем использовать тот же список ключей как дефолтные имена полей для автодетекта.

---

### Сводная таблица сравнения

| Критерий | owasp-security-logging | compliance-logging | logback-obfuscator | sensitive-annotation | mediquest | **Наш стартер** |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|
| Подход | Marker | Marker + Regex | Regex PatternLayout | Annotation + toString | Regex PatternLayout | **Annotation + TurboFilter** |
| Частичное маскирование | - | - | shortcut (F-L-L) | - | regex groups | **showFirst/showLast** |
| Категории данных (OWASP) | маркеры | - | - | - | - | **MaskType enum** |
| Spring Boot автоконфигурация | - | - | - | - | - | **+** |
| application.yml конфиг | - | - | - | - | - | **+** |
| Не ломает layout/encoder | - | - | - | + (toString) | - | **+ (TurboFilter)** |
| OpenAPI интеграция | - | - | - | - | - | **+ (опциональная)** |
| Кастомная стратегия | - | - | custom regex | - | custom regex | **@ConditionalOnMissingBean** |
| Structured logging | - | logstash модуль | logstash вариант | - | GCP вариант | через toString-proxy |
| Maven Central | + | - | + | - | Clojars | **+** |
| Активно поддерживается | - (2021) | - (2022) | ~(2026, solo) | - (2018) | + (2025) | **новый** |

---

### Выводы из анализа

1. **Ниша свободна.** Ни одна библиотека не предоставляет: аннотация на полях + Spring Boot автоконфигурация + неинвазивность (не ломает layout).
2. **PatternLayout-подход доминирует**, но он **инвазивен** — заменяет layout, конфликтует с пользовательской конфигурацией.
3. **Marker-подход (OWASP)** эргономически слаб — требует ручной разметки каждого лог-вызова.
4. **Regex-подход хрупок** — нужно поддерживать паттерны для каждого формата данных (JSON, toString, key=value).
5. **Annotation-подход (sensitive-annotation)** был правильной идеей, но остался proof-of-concept без Logback-интеграции.
6. **Наш подход** комбинирует: аннотация (декларативно на поле) + TurboFilter (не трогает layout) + Spring Boot стартер (zero-config) + OWASP-aligned категории + конфигурируемость через `application.yml`.

**Что взять из существующих решений:**
- Из **logback-obfuscator**: формат `FIRST-LENGTH-LAST` как опция в `MaskType` или отдельная стратегия.
- Из **Spring Boot Actuator**: список дефолтных ключевых слов (`password`, `secret`, `key`, `token`) для будущего автодетекта.
- Из **OWASP**: классификация маркерами (`CONFIDENTIAL`, `SECRET`) — поддержка SLF4J маркеров как дополнительный механизм.

---

## Структура проекта

```
log-masking/
├── log-masking-starter/                  # Сам стартер (библиотека)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/example/logmasking/
│       │   │   ├── annotation/
│       │   │   │   ├── Masked.java                   # Аннотация для полей
│       │   │   │   └── MaskType.java                 # Enum категорий: PII, CREDENTIAL, FINANCIAL, CUSTOM
│       │   │   ├── core/
│       │   │   │   ├── MaskingStrategy.java           # Интерфейс стратегии маскирования
│       │   │   │   ├── DefaultMaskingStrategy.java    # Реализация по умолчанию
│       │   │   │   └── FieldMaskingService.java       # Рефлексия + кэш аннотированных полей
│       │   │   ├── logback/
│       │   │   │   ├── MaskingTurboFilter.java        # Logback TurboFilter — оборачивает аргументы
│       │   │   │   └── MaskedObjectWrapper.java       # toString-proxy для замаскированного объекта
│       │   │   └── autoconfigure/
│       │   │       ├── LogMaskingAutoConfiguration.java
│       │   │       └── LogMaskingProperties.java
│       │   └── resources/
│       │       └── META-INF/
│       │           └── spring/
│       │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       └── test/
│           └── java/com/example/logmasking/
│               ├── core/
│               │   ├── FieldMaskingServiceTest.java
│               │   └── DefaultMaskingStrategyTest.java
│               └── logback/
│                   └── MaskingTurboFilterTest.java
│
├── test-app/                             # Тестовое Spring Boot приложение
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/example/testapp/
│       │   │   ├── TestApplication.java
│       │   │   ├── controller/UserController.java
│       │   │   └── dto/
│       │   │       ├── UserDto.java                   # email @Masked(type=PII), phone @Masked(showFirst=2, showLast=2)
│       │   │       └── PaymentDto.java                # cardNumber @Masked(type=FINANCIAL, showLast=4)
│       │   └── resources/
│       │       └── application.yml
│       └── test/
│           └── java/com/example/testapp/
│               └── MaskingIntegrationTest.java         # E2E: лог содержит маску
│
├── build.gradle.kts                      # Корневой build file
├── settings.gradle.kts
└── PLAN.md
```

---

## Компоненты

### 1. Аннотация `@Masked`

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Masked {
    /**
     * Категория данных (OWASP-aligned).
     * Влияет на стратегию маскирования по умолчанию.
     */
    MaskType type() default MaskType.CUSTOM;

    /** Символ маски (используется когда type=CUSTOM или для переопределения) */
    char maskChar() default '*';

    /** Сколько символов оставить в начале открытыми */
    int showFirst() default 0;

    /** Сколько символов оставить в конце открытыми */
    int showLast() default 0;

    /** Полная замена на фиксированную строку (приоритетнее maskChar) */
    String replacement() default "";
}
```

### 2. `MaskType` enum

Категории данных по рекомендациям OWASP:

```java
public enum MaskType {
    /** Credentials: пароли, токены, API-ключи — полная замена на "***" */
    CREDENTIAL,

    /** PII: email, телефон, имя — частичное маскирование */
    PII,

    /** Финансовые данные: номера карт, счетов — показать последние 4 символа */
    FINANCIAL,

    /** Пользовательская стратегия — поведение определяется параметрами аннотации */
    CUSTOM
}
```

Поведение по умолчанию для каждого типа:
- `CREDENTIAL` → `"***"` (полная замена, фиксированная длина чтобы не выдавать длину оригинала)
- `PII` → `j***@***.com` / `+7*****89` (showFirst=1, showLast=2 для email; showFirst=2, showLast=2 для остального)
- `FINANCIAL` → `****-****-****-1234` (showLast=4)
- `CUSTOM` → поведение определяется `maskChar`, `showFirst`, `showLast`, `replacement`

### 3. `MaskingStrategy` (интерфейс)

```java
public interface MaskingStrategy {
    String mask(String value, Masked annotation);
}
```

- `DefaultMaskingStrategy` — реализация по умолчанию: учитывает `MaskType` + параметры аннотации.
- Пользователь может зарегистрировать свой бин `MaskingStrategy` — он будет использован вместо дефолтного (`@ConditionalOnMissingBean`).

### 4. `FieldMaskingService`

Центральный сервис:
- Принимает объект, через рефлексию находит поля с `@Masked`.
- Кэширует метаданные классов (`ConcurrentHashMap<Class<?>, List<MaskedFieldMeta>>`), чтобы рефлексия выполнялась один раз на класс.
- Возвращает **новую строку** (masked representation), не мутирует объект.
- Метод: `String toMaskedString(Object obj)` — строит строковое представление с замаскированными полями.

### 5. Logback-интеграция (TurboFilter + Wrapper)

**Выбранный подход: TurboFilter + обёртка аргументов.**

Почему TurboFilter, а не PatternLayout / MessageConverter:
- **PatternLayout** — заменяет layout целиком, ломает пользовательскую конфигурацию
- **MessageConverter** — требует модификации `logback.xml` (`%maskedMsg` вместо `%msg`)
- **TurboFilter** — работает до форматирования, не трогает аппендеры/энкодеры/паттерны

Как работает:
1. `MaskingTurboFilter` — регистрируется в `LoggerContext` при старте приложения.
2. В методе `decide()` проверяет аргументы лог-события.
3. Если аргумент — объект с `@Masked` полями, оборачивает его в `MaskedObjectWrapper`.
4. `MaskedObjectWrapper.toString()` возвращает замаскированное представление.
5. Прозрачно для любого encoder/layout — они вызывают `toString()` на аргументах.

### 6. Автоконфигурация

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "log.masking", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogMaskingProperties.class)
public class LogMaskingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MaskingStrategy maskingStrategy() {
        return new DefaultMaskingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldMaskingService fieldMaskingService(MaskingStrategy strategy) {
        return new FieldMaskingService(strategy);
    }

    @Bean
    public MaskingTurboFilterRegistrar maskingTurboFilterRegistrar(FieldMaskingService service) {
        return new MaskingTurboFilterRegistrar(service);
    }
}
```

### 7. `LogMaskingProperties` — полная конфигурация

```yaml
# application.yml — все доступные настройки с дефолтами
log:
  masking:
    enabled: true                    # Мастер-переключатель (выключает весь стартер)
    mask-char: '*'                   # Глобальный символ маски (переопределяется в @Masked)

    # Дефолты для каждого MaskType (переопределяются параметрами @Masked на поле)
    credential:
      replacement: "***"             # Фиксированная замена, не выдаёт длину оригинала
    pii:
      show-first: 1                  # j***@***.com
      show-last: 2
    financial:
      show-last: 4                   # ****-****-****-1234
```

```java
@ConfigurationProperties(prefix = "log.masking")
public class LogMaskingProperties {

    private boolean enabled = true;
    private char maskChar = '*';

    private CredentialProperties credential = new CredentialProperties();
    private PiiProperties pii = new PiiProperties();
    private FinancialProperties financial = new FinancialProperties();

    public static class CredentialProperties {
        private String replacement = "***";
        // getters/setters
    }

    public static class PiiProperties {
        private int showFirst = 1;
        private int showLast = 2;
        // getters/setters
    }

    public static class FinancialProperties {
        private int showLast = 4;
        // getters/setters
    }

    // getters/setters
}
```

**Приоритет настроек** (от высшего к низшему):
1. Параметры `@Masked` на поле (`showFirst`, `showLast`, `replacement`, `maskChar`)
2. `application.yml` (`log.masking.pii.show-first`, и т.д.)
3. Встроенные дефолты `DefaultMaskingStrategy`

Если в `@Masked` параметр не задан (дефолтное значение) — берётся из `application.yml`.
Если и там не задано — берётся встроенный дефолт.

---

## Интеграция с OpenAPI (springdoc-openapi)

### Проблема

`@Masked` — log-level concern. В API-ответах данные приходят **немаскированными**.
Но пользователю может быть полезно видеть в Swagger UI, что поле содержит чувствительные данные.

### Решение: опциональный `OpenApiMaskingCustomizer`

Активируется автоматически, если `springdoc-openapi` на classpath (`@ConditionalOnClass`).

```java
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
@Bean
@ConditionalOnMissingBean
public OpenApiMaskingCustomizer openApiMaskingCustomizer() {
    return new OpenApiMaskingCustomizer();
}
```

Что делает:
- Сканирует схемы OpenAPI на этапе генерации
- Для полей с `@Masked` добавляет extension `x-masked: true` и дополняет `description` меткой `[MASKED IN LOGS]`
- Для `MaskType.CREDENTIAL` ставит `format: "password"` (Swagger UI отображает звёздочками)
- **Не изменяет** сами данные в запросах/ответах — только схему документации

### Пример результата в OpenAPI-схеме

```json
{
  "UserDto": {
    "properties": {
      "name": { "type": "string" },
      "email": {
        "type": "string",
        "description": "User email [MASKED IN LOGS]",
        "x-masked": true,
        "x-mask-type": "PII"
      },
      "password": {
        "type": "string",
        "format": "password",
        "description": "[MASKED IN LOGS]",
        "x-masked": true,
        "x-mask-type": "CREDENTIAL"
      }
    }
  }
}
```

### Ручная интеграция (без автокастомайзера)

Если пользователь не хочет автоматическую интеграцию (`log.masking.openapi.enabled=false`)
или использует свою кастомизацию OpenAPI, рекомендуется комбинировать аннотации:

```java
public class UserDto {
    @Masked(type = MaskType.CREDENTIAL)
    @Schema(description = "Пароль пользователя", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Masked(type = MaskType.PII, showFirst = 1, showLast = 2)
    @Schema(description = "Email", example = "j***@***.com")
    private String email;
}
```

### Конфигурация OpenAPI-интеграции

```yaml
log:
  masking:
    openapi:
      enabled: true                  # Включить OpenAPI-кастомайзер (default: true если springdoc на classpath)
      description-suffix: "[MASKED IN LOGS]"   # Суффикс к описанию поля
      credential-format: "password"            # OpenAPI format для CREDENTIAL полей
```

---

## Обновлённая структура проекта

```
log-masking/
├── log-masking-starter/                  # Сам стартер (библиотека)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/example/logmasking/
│       │   │   ├── annotation/
│       │   │   │   ├── Masked.java
│       │   │   │   └── MaskType.java
│       │   │   ├── core/
│       │   │   │   ├── MaskingStrategy.java
│       │   │   │   ├── DefaultMaskingStrategy.java
│       │   │   │   └── FieldMaskingService.java
│       │   │   ├── logback/
│       │   │   │   ├── MaskingTurboFilter.java
│       │   │   │   └── MaskedObjectWrapper.java
│       │   │   ├── openapi/
│       │   │   │   └── OpenApiMaskingCustomizer.java    # @ConditionalOnClass(springdoc)
│       │   │   └── autoconfigure/
│       │   │       ├── LogMaskingAutoConfiguration.java
│       │   │       ├── LogMaskingProperties.java
│       │   │       └── OpenApiMaskingAutoConfiguration.java
│       │   └── resources/
│       │       └── META-INF/
│       │           ├── spring/
│       │           │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       │           └── additional-spring-configuration-metadata.json  # IDE autocomplete для properties
│       └── test/
│           └── java/com/example/logmasking/
│               ├── core/
│               │   ├── FieldMaskingServiceTest.java
│               │   └── DefaultMaskingStrategyTest.java
│               ├── logback/
│               │   └── MaskingTurboFilterTest.java
│               ├── openapi/
│               │   └── OpenApiMaskingCustomizerTest.java
│               └── autoconfigure/
│                   └── LogMaskingAutoConfigurationTest.java
│
├── test-app/                             # Тестовое Spring Boot приложение
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/example/testapp/
│       │   │   ├── TestApplication.java
│       │   │   ├── controller/UserController.java
│       │   │   └── dto/
│       │   │       ├── UserDto.java                    # @Masked + @Schema
│       │   │       └── PaymentDto.java
│       │   └── resources/
│       │       └── application.yml
│       └── test/
│           └── java/com/example/testapp/
│               ├── MaskingIntegrationTest.java
│               └── OpenApiIntegrationTest.java          # Проверка OpenAPI-схемы
│
├── build.gradle.kts                      # Корневой Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml                # Gradle version catalog
└── PLAN.md
```

---

## Порядок реализации

### Шаг 1 — Скелет проекта (Gradle)
- Корневой `build.gradle.kts` (multi-module, Gradle Kotlin DSL).
- `settings.gradle.kts` с `include("log-masking-starter", "test-app")`.
- `gradle/libs.versions.toml` — version catalog для централизации версий.
- Минимальные `build.gradle.kts` для каждого модуля.
- Java 17, Spring Boot 3.2+, Logback (транзитивно через `spring-boot-starter-logging`).
- `spring-boot-configuration-processor` для генерации metadata (IDE autocomplete).

### Шаг 2 — Аннотация и стратегии
- `@Masked` аннотация с `MaskType`.
- `MaskType` enum (CREDENTIAL, PII, FINANCIAL, CUSTOM).
- `MaskingStrategy` интерфейс + `DefaultMaskingStrategy`.
- `DefaultMaskingStrategy` читает дефолты из `LogMaskingProperties` (инжектится).
- Unit-тесты на стратегию: каждый `MaskType` + edge cases (null, пустая строка, короткая строка).

### Шаг 3 — FieldMaskingService
- Рефлексия, кэш, `toMaskedString()`.
- Unit-тесты: простые DTO, вложенные объекты, null-поля, поля без аннотации, наследование.

### Шаг 4 — Logback-интеграция
- `MaskedObjectWrapper` — toString-proxy.
- `MaskingTurboFilter` — перехватывает и оборачивает аргументы.
- `MaskingTurboFilterRegistrar` — регистрирует фильтр в `LoggerContext`.
- Unit-тест: проверяем корректную обёртку аргументов.

### Шаг 5 — Автоконфигурация + Properties
- `LogMaskingAutoConfiguration` + `OpenApiMaskingAutoConfiguration`.
- `LogMaskingProperties` со всеми вложенными классами.
- `AutoConfiguration.imports`.
- `additional-spring-configuration-metadata.json` — описания properties для IDE.
- Тесты:
  - Контекст поднимается, бины создаются.
  - `@ConditionalOnMissingBean` работает (пользовательский `MaskingStrategy` побеждает).
  - `enabled=false` отключает весь стартер.
  - Кастомные значения из `application.yml` подхватываются.

### Шаг 6 — OpenAPI-интеграция
- `OpenApiMaskingCustomizer` с `@ConditionalOnClass(OpenApiCustomizer.class)`.
- Unit-тест: кастомайзер обогащает OpenAPI-схему.
- `log.masking.openapi.enabled=false` отключает.

### Шаг 7 — Тестовое приложение
- `test-app` с `UserDto` и `PaymentDto` (комбинация `@Masked` + `@Schema`).
- `UserController` — логирует DTO.
- `application.yml` — демо конфигурации с переопределением дефолтов.
- Зависимость `springdoc-openapi-starter-webmvc-ui` для Swagger UI.
- `MaskingIntegrationTest` — поднимает контекст, делает запрос, проверяет captured log output.
- `OpenApiIntegrationTest` — проверяет что `/v3/api-docs` содержит `x-masked`.
- Тест с кастомной `MaskingStrategy` — убеждаемся что `@ConditionalOnMissingBean` работает.

---

## Сборка (Gradle)

### `gradle/libs.versions.toml`

```toml
[versions]
spring-boot = "3.2.5"
springdoc = "2.5.0"
logback = "1.4.14"   # Приходит через spring-boot-starter-logging

[libraries]
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-autoconfigure = { module = "org.springframework.boot:spring-boot-autoconfigure" }
spring-boot-configuration-processor = { module = "org.springframework.boot:spring-boot-configuration-processor" }
springdoc-openapi = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }
logback-classic = { module = "ch.qos.logback:logback-classic" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.5" }
```

### `log-masking-starter/build.gradle.kts` (ключевые моменты)

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter)      // transitively brings logback
    annotationProcessor(libs.spring.boot.configuration.processor)

    // OpenAPI — optional, не тащим транзитивно
    compileOnly(libs.springdoc.openapi)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.springdoc.openapi)
}
```

Стартер **не** использует `spring-boot` plugin (он не является Boot-приложением).
`springdoc-openapi` — `compileOnly`, чтобы не навязывать зависимость потребителям.

---

## Ключевые решения

| Вопрос | Решение | Почему |
|--------|---------|--------|
| Сборка | Gradle Kotlin DSL + version catalog | Стандарт для multi-module Spring Boot проектов |
| Как не сломать пользовательский логгинг? | TurboFilter на уровне LoggerContext | Не трогает аппендеры, энкодеры, паттерны. Работает до форматирования |
| Как маскировать? | Обёртка аргументов в toString-proxy | Прозрачно для любого layout/encoder |
| Как классифицировать данные? | `MaskType` enum (OWASP-aligned) | CREDENTIAL/PII/FINANCIAL имеют разные стратегии по умолчанию |
| Как позволить кастомизацию? | `@ConditionalOnMissingBean` на `MaskingStrategy` | Пользователь определяет свой бин — он побеждает |
| Конфигурация дефолтов | `LogMaskingProperties` с вложенными классами per-MaskType | Переопределяемо через `application.yml`, аннотация имеет приоритет |
| Как избежать overhead? | Кэш метаданных + быстрая проверка `hasAnnotation` | Рефлексия один раз на класс |
| Как выключить? | `log.masking.enabled=false` + `@ConditionalOnProperty` | Стандартный паттерн для стартеров |
| OpenAPI | `@ConditionalOnClass` + опциональный кастомайзер | Не навязывает springdoc, но обогащает схему если он есть |

---

## Ссылки

- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [OWASP Security Logging — Masking](https://github.com/augustd/owasp-security-logging/wiki/Masking)
- [Baeldung — Mask Sensitive Data in Logs With Logback](https://www.baeldung.com/logback-mask-sensitive-data)
- [Masking Sensitive Data with Logback (howtodoinjava)](https://howtodoinjava.com/logback/masking-sensitive-data/)
- [compliance-logging (Slalom)](https://github.com/SlalomBuild/compliance-logging)
- [logback-sensitive-data-obfuscator](https://github.com/orczykowski/logback-sensitive-data-obfuscator-pattern-layout)
- [sensitive-annotation](https://github.com/hbagchi/sensitive-annotation)
