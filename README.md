# log-masking

**Маскирование чувствительных данных в логах Spring Boot 3 на основе аннотаций.**
Нулевая конфигурация, неинвазивный подход, соответствие рекомендациям OWASP.

Пометьте поле аннотацией `@Masked` — и его значение автоматически заменится
маской в каждом сообщении лога. Без правок `logback.xml`, без собственных layout’ов
и энкодеров, без изменения самих объектов.

---

## Зачем это нужно

В корпоративных приложениях чувствительные данные (пароли, токены, email, номера
карт) постоянно утекают в логи: из-за `toString()`, дебаг-логов, случайной
сериализации DTO, трассировок исключений. Классические решения — регулярные
выражения в `PatternLayout`, ручные `DataMasker` в каждом сервисе, Jackson
mixin’ы — либо хрупкие, либо требуют переписывать код.

`log-masking-starter` решает задачу декларативно: один раз ставите аннотацию на
поле DTO — и значение маскируется во всех вызовах SLF4J с плейсхолдерами `{}`,
независимо от того, какой именно appender, layout или pattern используется.

---

## Пример

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
log.info("Создан пользователь: {}", user);
// Создан пользователь: UserDto(name=Иван, email=i*************om, password=***, cardNumber=************1111)
```

Исходный объект **никогда не изменяется** — маскирование применяется только к
строковому представлению, создаваемому для вывода в лог. Методы `getEmail()`,
сериализация в JSON, работа с БД остаются нетронутыми.

---

## Быстрый старт

### 1. Подключите зависимость

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation("io.github.munsunch:log-masking-starter:0.1.0")
}
```

**Gradle (Groovy):**

```groovy
dependencies {
    implementation 'io.github.munsunch:log-masking-starter:0.1.0'
}
```

**Maven:**

```xml
<dependency>
    <groupId>io.github.munsunch</groupId>
    <artifactId>log-masking-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Больше ничего делать не нужно — стартер подключается автоматически через Spring
Boot AutoConfiguration.

### 2. Пометьте чувствительные поля

```java
@Masked(type = MaskType.CREDENTIAL)
private String password;
```

### 3. Логируйте через плейсхолдеры SLF4J

```java
log.info("Пользователь: {}", user);
```

> **Важно:** маскирование работает **только** с синтаксисом плейсхолдеров `{}`.
> Конкатенация (`log.info("user: " + user)`) вызывает `toString()` ещё до того,
> как Logback видит аргумент, поэтому перехватить её невозможно. Всегда
> передавайте объекты как параметры, а не в склеенной строке.

---

## Возможности

| Возможность | Описание |
|:------------|:---------|
| Декларативный подход | Аннотация `@Masked` на поле — без regex, без XML, без маркеров |
| Неинвазивность | Ваши `PatternLayout`, `Encoder` и `logback.xml` остаются без изменений |
| Категории по OWASP | Встроенные `MaskType`: `CREDENTIAL`, `PII`, `FINANCIAL`, `CUSTOM` |
| Гибкая настройка | Переопределение дефолтов для каждого типа через `application.yml` или параметры аннотации |
| Расширяемость | Собственный `MaskingStrategy`-бин полностью заменяет стандартный |
| Интеграция с OpenAPI | Обогащение Swagger-схем расширениями `x-masked` / `x-mask-type` при наличии springdoc |
| Поддержка Spring Boot 3 | Spring Boot 3.2+, Java 17, типобезопасные `@ConfigurationProperties` |
| Кеширование метаданных | Рефлексия выполняется один раз на класс, результат хранится в `ConcurrentHashMap` |

---

## Типы маскирования

Четыре встроенных типа соответствуют категориям чувствительных данных из
[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html):

| Тип | Поведение по умолчанию | Пример входа | Пример выхода |
|:----|:-----------------------|:-------------|:--------------|
| `CREDENTIAL` | Полная замена фиксированной строкой `"***"` (длина скрыта) | `superSecret123` | `***` |
| `PII` | Оставить первый 1 и последние 2 символа | `john@example.com` | `j*************om` |
| `FINANCIAL` | Оставить последние 4 символа | `4111111111111111` | `************1111` |
| `CUSTOM` | Полная маска `*` в длину значения, если не переопределено | `anything` | `********` |

### Приоритет параметров

Значения разрешаются в порядке убывания приоритета:

1. `replacement` в аннотации (если не пустой)
2. Явно заданные `showFirst` / `showLast` / `maskChar` в аннотации
3. Настройки в `application.yml` для выбранного `MaskType`
4. Встроенные дефолты `DefaultMaskingStrategy`

### Примеры

```java
// PII с дефолтами типа
@Masked(type = MaskType.PII)
private String email;
// "john@example.com" → "j*************om"

// Переопределение в аннотации
@Masked(type = MaskType.PII, showFirst = 3, showLast = 4, maskChar = '#')
private String email;
// "john@example.com" → "joh#########.com"

// FINANCIAL — последние 4 символа
@Masked(type = MaskType.FINANCIAL)
private String cardNumber;
// "4111111111111111" → "************1111"

// Фиксированная замена
@Masked(replacement = "[СКРЫТО]")
private String ssn;
// "123-45-6789" → "[СКРЫТО]"
```

---

## Конфигурация

Все свойства опциональны — стартер работает из коробки.

```yaml
log:
  masking:
    enabled: true          # главный выключатель
    mask-char: '*'         # глобальный символ маски

    credential:
      replacement: "***"   # фиксированная замена для CREDENTIAL

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

### Полный справочник

| Свойство | Тип | По умолчанию | Описание |
|:---------|:----|:-------------|:---------|
| `log.masking.enabled` | `boolean` | `true` | Полностью отключает стартер — бины не создаются, аппендеры не оборачиваются |
| `log.masking.mask-char` | `char` | `*` | Глобальный символ маски |
| `log.masking.credential.replacement` | `String` | `***` | Замена для полей `CREDENTIAL` |
| `log.masking.pii.show-first` | `int` | `1` | Количество открытых символов в начале для `PII` |
| `log.masking.pii.show-last` | `int` | `2` | Количество открытых символов в конце для `PII` |
| `log.masking.financial.show-last` | `int` | `4` | Количество открытых символов в конце для `FINANCIAL` |
| `log.masking.openapi.enabled` | `boolean` | `true` | Включает обогащение OpenAPI-схем |
| `log.masking.openapi.description-suffix` | `String` | `[MASKED IN LOGS]` | Суффикс к описанию замаскированных полей |
| `log.masking.openapi.credential-format` | `String` | `password` | Значение `format` в OpenAPI для `CREDENTIAL` |

IDE-автодополнение работает благодаря
`additional-spring-configuration-metadata.json` в IntelliJ IDEA и VS Code.

---

## Кастомная стратегия маскирования

Хотите собственную логику? Определите свой `MaskingStrategy`-бин — стартер
использует `@ConditionalOnMissingBean` и не зарегистрирует дефолтный:

```java
@Bean
public MaskingStrategy maskingStrategy() {
    return (value, annotation) -> switch (annotation.type()) {
        case CREDENTIAL -> "●●●●●";
        case PII        -> maskEmail(value);
        case FINANCIAL  -> maskPan(value);      // PCI-DSS совместимо
        case CUSTOM     -> "***";
    };
}
```

Или подмените сервис обхода полей `FieldMaskingService` — например, чтобы
маскировать элементы коллекций:

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

Можно переключать стратегию по Spring-профилям (например, строгая маска
в `prod`, расслабленная — в `dev`).

---

## Интеграция с OpenAPI / Swagger

Если `springdoc-openapi` есть в classpath, стартер **автоматически** обогащает
схемы. Для каждого поля с `@Masked` добавляются:

| Расширение | Значение |
|:-----------|:---------|
| `x-masked` | `true` |
| `x-mask-type` | `"CREDENTIAL"` / `"PII"` / `"FINANCIAL"` / `"CUSTOM"` |

Дополнительно:
- К `description` поля добавляется суффикс `[MASKED IN LOGS]`
- Поля `CREDENTIAL` получают `format: "password"` (Swagger UI рисует точки)

Пример результата:

```json
{
  "password": {
    "type": "string",
    "description": "User password [MASKED IN LOGS]",
    "format": "password",
    "x-masked": true,
    "x-mask-type": "CREDENTIAL"
  }
}
```

Подключите springdoc в своём приложении (в стартере он `compileOnly`):

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
```

Отключить обогащение, не убирая springdoc: `log.masking.openapi.enabled: false`.

---

## Как это устроено

```
log.info("Пользователь: {}", userDto)
        │
        ▼
┌──────────────────────┐
│   Logback Logger     │  Создаёт LoggingEvent с исходными аргументами
└─────────┬────────────┘
          │
          ▼
┌──────────────────────┐
│  MaskingAppender     │  Оборачивает оригинальный appender
│                      │
│  ① проверяет аргументы    (есть ли у класса поля @Masked?)
│  ② заменяет на MaskedObjectWrapper
│  ③ декорирует событие через MaskedLoggingEventDecorator
│  ④ передаёт оригинальному аппендеру
└─────────┬────────────┘
          │
          ▼
┌──────────────────────┐
│  Original Appender   │  Ваш ConsoleAppender / FileAppender / JSON-encoder
│  (без изменений)     │  вызывает event.getFormattedMessage()
│                      │    → MaskedObjectWrapper.toString()
│                      │       → FieldMaskingService.toMaskedString(obj)
│                      │          → MaskingStrategy.mask(value, annotation)
└──────────────────────┘
          │
          ▼
   Замаскированный вывод
```

### Ключевые моменты

- **`MaskingAppenderRegistrar`** реализует `SmartInitializingSingleton` и запускается
  после создания всех singleton-бинов Spring. Он проходит по каждому Logback-логгеру,
  снимает существующие аппендеры и присоединяет их обратно, обёрнутыми в
  `MaskingAppender`. Операция идемпотентна (обёртки помечены префиксом `masking:`).
- **`MaskedLoggingEventDecorator`** реализует `ILoggingEvent`, переопределяет только
  `getArgumentArray()` и `getFormattedMessage()` — всё остальное (timestamp, level,
  MDC, поток) делегируется оригинальному событию.
- **Кеш метаданных** живёт в `ConcurrentHashMap<Class<?>, ClassMetadata>`. Рефлексия
  выполняется один раз на класс.
- **JDK-классы пропускаются** при сканировании полей (модули, чьё имя начинается на
  `java.`), чтобы не ловить `InaccessibleObjectException` на Java 16+.

### Почему именно оборачивание аппендеров?

| Подход | Инвазивность | Работает для 1-арг вызовов? | Требует `logback.xml`? |
|:-------|:-------------|:----------------------------|:-----------------------|
| Кастомный `PatternLayout` | Заменяет layout | Да | **Да** |
| Кастомный `MessageConverter` | Патчит pattern | Да | **Да** |
| `TurboFilter` | Неинвазивный | **Нет** — не меняет аргументы 1/2-арг вызовов | Нет |
| **Оборачивание аппендеров** | Неинвазивный | **Да** | **Нет** |

`TurboFilter` получает аргументы как `Object` по значению — модификация
параметров не влияет на создаваемый `LoggingEvent`. Поэтому единственный
способ массово перехватить аргументы без правок конфигурации Logback —
обернуть аппендеры.

---

## Ограничения и нюансы

| Ограничение | Обходной путь |
|:------------|:--------------|
| Работает только с плейсхолдерами SLF4J `{}` | Не использовать конкатенацию в вызовах лога |
| Не маскирует элементы коллекций «из коробки» | Аннотировать поля элемент-класса или переопределить `FieldMaskingService` |
| `toString()`, вызванный напрямую (без логгера), маскированием не затронут | Для не-логового вывода использовать, например, `@ToString(exclude = ...)` |
| Логи в ходе старта Spring до срабатывания `MaskingAppenderRegistrar` не маскируются | Осознанное решение: стартап-логи редко содержат PII, раннее логирование должно работать |
| Поддерживается только Logback | Log4j2 планируется в будущих версиях |

---

## Модули

- **`log-masking-starter/`** — библиотека. Spring Boot auto-configured starter
  (`java-library`, без `bootJar`). Регистрируется через
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- **`test-app/`** — демонстрационное Spring Boot-приложение, использующее стартер.
  Интеграционные тесты (`MaskingIntegrationTest`, `OpenApiIntegrationTest`) и
  ручная проверка через Swagger UI.

---

## Сборка и тесты

Gradle multi-module проект (Kotlin DSL), Java 17, Spring Boot 3.3.5. Версии
централизованы в `gradle/libs.versions.toml`.

```bash
./gradlew build                                      # собрать всё
./gradlew :log-masking-starter:test                  # юнит-тесты стартера
./gradlew :test-app:test                             # интеграционные тесты
./gradlew :test-app:bootRun                          # поднять demo-приложение на :8080
                                                     #   Swagger: /swagger-ui.html
./gradlew :log-masking-starter:compileJava           # перегенерировать spring-configuration-metadata.json
```

**Замечание про вывод сборки на Windows.** Вывод сборки перенаправлен в
`C:/tmp/log-masking-build/<module>` (см. `build.gradle.kts:12`). Это обход
проблем с загрузкой классов, когда путь к проекту содержит не-ASCII символы
(например, кириллица в имени профиля пользователя Windows). Не убирайте
переопределение `layout.buildDirectory`, если только весь проект не перенесён
в ASCII-путь.

### Проверка, что маскирование работает

```java
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MaskingVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(MaskingVerificationTest.class);

    @Test
    void sensitiveDataIsMasked(CapturedOutput output) {
        var user = new UserDto("Иван", "ivan@example.com", "secret123", "+79001234567");
        log.info("User: {}", user);

        assertThat(output.getOut()).doesNotContain("ivan@example.com");
        assertThat(output.getOut()).doesNotContain("secret123");
        assertThat(output.getOut()).contains("password=***");
    }
}
```

---

## Требования

- **Java 17+**
- **Spring Boot 3.2+**
- **Logback** (входит в состав `spring-boot-starter`)

---

## Документация

Полная документация — в каталоге [`docs/`](docs/) (Jekyll / GitHub Pages):

- [Getting Started](docs/getting-started.md) — установка и базовое использование
- [Annotations](docs/annotations.md) — справочник `@Masked` и `MaskType`
- [Configuration](docs/configuration.md) — все свойства и примеры
- [How It Works](docs/how-it-works.md) — архитектура и обоснование решений
- [Customization](docs/customization.md) — собственные стратегии, работа с Lombok и Jackson
- [OpenAPI Integration](docs/openapi-integration.md) — обогащение Swagger-схем

Исходный план проектирования со сравнением альтернатив и обоснованием
архитектурных решений: [`PLAN.md`](PLAN.md).

---

## Лицензия

См. файл лицензии в репозитории.
