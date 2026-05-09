# log-masking

**Маскирование чувствительных данных в логах Spring Boot 3 на основе аннотаций.**
Нулевая конфигурация, неинвазивный подход, соответствие рекомендациям OWASP.
Поддерживает два сценария: пометка полей вручную в коде и **генерация DTO с
аннотациями `@Masked` напрямую из OpenAPI-спецификации**.

Пометьте поле аннотацией `@Masked` (или поставьте `x-masked` в OpenAPI-документ)
— и значение автоматически заменится маской в каждом сообщении лога. Без правок
`logback.xml`, без собственных layout’ов и энкодеров, без изменения самих
объектов.

---

## Два сценария использования

| | **Code-first** | **Contract-first** |
|:---|:---|:---|
| Где описаны DTO | Java-классы в проекте | OpenAPI-документ (YAML/JSON) |
| Где помечается «секретность» | `@Masked` на поле | `x-masked` в схеме |
| Что генерируется | Ничего | Java-DTO + Spring API-интерфейсы |
| Когда использовать | DTO живут в коде, OpenAPI генерируется из них через `springdoc` | Контракт — единственный источник правды, реализации синхронизируются с ним |

Обе ветки используют одну и ту же runtime-инфраструктуру стартера: маскирование
происходит на уровне Logback-аппендеров, и не важно, поставлена `@Masked`
вручную или сгенерирована из спеки.

---

## Сценарий 1 — code-first

### Пример

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

### Быстрый старт

**1. Подключите зависимость:**

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation("io.github.munsunch:log-masking-starter:0.1.0")
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.munsunch</groupId>
    <artifactId>log-masking-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**2. Пометьте чувствительные поля:**

```java
@Masked(type = MaskType.CREDENTIAL)
private String password;
```

**3. Логируйте через плейсхолдеры SLF4J:**

```java
log.info("Пользователь: {}", user);
```

> **Важно:** маскирование работает **только** с синтаксисом плейсхолдеров `{}`.
> Конкатенация (`log.info("user: " + user)`) вызывает `toString()` ещё до того,
> как Logback видит аргумент, поэтому перехватить её невозможно.

---

## Сценарий 2 — contract-first

Если контракт API живёт в OpenAPI-документе, маскирование описывается прямо в
нём расширением `x-masked`. Стартер поставляет Mustache-шаблоны для
[OpenAPI Generator](https://openapi-generator.tech/), которые при сборке
переводят `x-masked` в аннотацию `@Masked` на сгенерированных Java-классах.

Полный рабочий пример — в `test-app/`. Ниже — пошаговый разбор.

### Шаг 1. Опишите чувствительность в спецификации

`src/main/resources/openapi/api.yaml`:

```yaml
openapi: 3.0.3
info: { title: Customer & Order API, version: 1.0.0 }

paths:
  /api/v2/customers:
    post:
      tags: [customers]
      operationId: createCustomer
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CustomerRequest' }
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Customer' }

  /api/v2/orders:
    post:
      tags: [orders]
      operationId: placeOrder
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/OrderRequest' }
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Order' }

components:
  schemas:
    CustomerRequest:
      type: object
      required: [fullName, email, password, taxId]
      properties:
        fullName:
          type: string
          x-masked: { type: PII, showFirst: 1, showLast: 1 }   # I*********v
        email:
          type: string
          x-masked: { type: PII, showFirst: 1, showLast: 2 }   # i**************om
        phone:
          type: string
          x-masked: { type: PII, showFirst: 2, showLast: 2 }   # +7********67
        password:
          type: string
          format: password
          x-masked: { type: CREDENTIAL }                       # ***
        taxId:
          type: string
          x-masked: { replacement: "[CLASSIFIED]" }            # фиксированная замена

    OrderRequest:
      type: object
      required: [customerId, cardNumber, cvv, amount]
      properties:
        customerId: { type: string }
        amount:     { type: number, format: double }
        cardNumber:
          type: string
          x-masked: { type: FINANCIAL, showLast: 4 }           # ************1234
        cvv:
          type: string
          x-masked: { type: CREDENTIAL }                       # ***
```

Поля внутри `x-masked` повторяют параметры аннотации: `type`, `showFirst`,
`showLast`, `maskChar`, `replacement`. Все, кроме `type`, — опциональны.

### Шаг 2. Подключите openapi-generator

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openapi.generator") version "7.22.0"
}

dependencies {
    implementation("io.github.munsunch:log-masking-starter:0.1.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

// Распаковываем Mustache-шаблоны из JAR стартера в build/
val maskingTemplates: Configuration by configurations.creating
val templatesDir = layout.buildDirectory.dir("openapi-templates")

dependencies {
    maskingTemplates("io.github.munsunch:log-masking-starter:0.1.0")
}

val unpackMaskingTemplates by tasks.registering(Sync::class) {
    from(maskingTemplates.map(::zipTree))
    include("openapi-templates/v7/spring/**")
    eachFile {
        relativePath = RelativePath(true,
            *relativePath.segments.drop(3).toTypedArray())
    }
    includeEmptyDirs = false
    into(templatesDir)
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi/api.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.example.api")
    modelPackage.set("com.example.model")
    templateDir.set(templatesDir.get().asFile.absolutePath)
    configOptions.set(mapOf(
        "useSpringBoot3"       to "true",
        "interfaceOnly"        to "true",
        "useTags"              to "true",
        "skipDefaultInterface" to "true"
    ))
    globalProperties.set(mapOf(
        "apis"            to "",
        "models"          to "",
        "supportingFiles" to "false"
    ))
    dependsOn(unpackMaskingTemplates)
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
}

tasks.compileJava { dependsOn(tasks.openApiGenerate) }
```

> Внутри multi-module-репозитория (как в `test-app/`) `Sync`-таска не нужна:
> `templateDir` указывают прямо на
> `rootProject.file("log-masking-starter/src/main/resources/openapi-templates/v7/spring")`.
> Полная инструкция, в т.ч. для Maven и для openapi-generator 6.x — в
> [`docs/openapi-codegen.md`](docs/openapi-codegen.md).

### Шаг 3. На выходе — DTO с уже навешанной аннотацией

```java
public class CustomerRequest {

    @com.munsun.logmasking.annotation.Masked(
        type = com.munsun.logmasking.annotation.MaskType.PII, showFirst = 1, showLast = 1)
    private String fullName;

    @com.munsun.logmasking.annotation.Masked(
        type = com.munsun.logmasking.annotation.MaskType.PII, showFirst = 1, showLast = 2)
    private String email;

    @com.munsun.logmasking.annotation.Masked(
        type = com.munsun.logmasking.annotation.MaskType.PII, showFirst = 2, showLast = 2)
    private String phone;

    @com.munsun.logmasking.annotation.Masked(
        type = com.munsun.logmasking.annotation.MaskType.CREDENTIAL)
    private String password;

    @com.munsun.logmasking.annotation.Masked(
        type = com.munsun.logmasking.annotation.MaskType.CUSTOM, replacement = "[CLASSIFIED]")
    private String taxId;
    // + getters/setters/equals/hashCode/toString
}
```

### Шаг 4. Реализуйте сгенерированный API-интерфейс

Сгенерированный `CustomersApi` — обычный Spring-интерфейс с `@RequestMapping`,
`@Operation`, `@Validated` и абстрактными методами. Реализация — это обычный
`@RestController`, который ничего не знает про маскирование:

```java
@RestController
public class CustomerOrderController implements CustomersApi, OrdersApi {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderController.class);

    @Override
    public ResponseEntity<Customer> createCustomer(CustomerRequest request) {
        log.info("Creating customer: {}", request);
        Customer created = new Customer()
                .id(UUID.randomUUID().toString())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone());
        log.info("Customer created: {}", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Order> placeOrder(OrderRequest request) {
        log.info("Placing order: {}", request);
        Order order = new Order()
                .id(UUID.randomUUID().toString())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .cardNumber(request.getCardNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
    // ...
}
```

Полная версия — в
[`test-app/.../CustomerOrderController.java`](test-app/src/main/java/com/munsun/testapp/controller/CustomerOrderController.java).

### Шаг 5. Маскирование работает автоматически

POST `/api/v2/customers` с телом:
```json
{
  "fullName": "Ivan Petrov",
  "email": "ivan@example.com",
  "phone": "+79001234567",
  "password": "hunter2-very-secret",
  "taxId": "123-45-6789"
}
```

В логах:
```
Creating customer: CustomerRequest{fullName=I*********v, email=i**************om, phone=+7********67, password=***, taxId=[CLASSIFIED]}
```

Те же значения в HTTP-ответе остаются настоящими — `@Masked` живёт исключительно
на уровне `toString()`, который вызывает Logback.

### Round-trip с runtime-обогащением

Когда springdoc на classpath, `OpenApiMaskingCustomizer` дополнительно дописывает
`x-masked` обратно в живой документ `/v3/api-docs` — на основании аннотаций,
которые сгенерировал openapi-generator. Получается замкнутый цикл:

```
api.yaml ──► openapi-generator ──► @Masked в коде ──► customizer ──► /v3/api-docs
   │                                                                       │
   └─────────────────── один и тот же x-masked словарь ────────────────────┘
```

YAML, опубликованный сервисом `A` через `/v3/api-docs`, можно скормить codegen
сервиса `B` — разметка маскирования восстановится без ручного синхронизирования.

---

## Возможности

| Возможность | Описание |
|:------------|:---------|
| Декларативный подход | Аннотация `@Masked` на поле — без regex, без XML, без маркеров |
| Contract-first генерация | `x-masked` в OpenAPI → `@Masked` в Java через Mustache-шаблоны для openapi-generator |
| Неинвазивность | Ваши `PatternLayout`, `Encoder` и `logback.xml` остаются без изменений |
| Категории по OWASP | Встроенные `MaskType`: `CREDENTIAL`, `PII`, `FINANCIAL`, `CUSTOM` |
| Гибкая настройка | Переопределение дефолтов для каждого типа через `application.yml` или параметры аннотации |
| Расширяемость | Собственный `MaskingStrategy`-бин полностью заменяет стандартный |
| Round-trip с OpenAPI | `@Masked` ↔ `x-masked` — один словарь работает в обе стороны |
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
маскировать элементы коллекций. Можно переключать стратегию по Spring-профилям
(строгая в `prod`, расслабленная в `dev`).

---

## Runtime-обогащение `/v3/api-docs`

Кроме codegen-шаблонов из «Сценария 2», стартер обогащает живой OpenAPI-документ.
Если `springdoc-openapi` есть в classpath, к каждому полю с `@Masked`
автоматически дописывается то же самое компактное расширение `x-masked` — то
есть аннотации, поставленные вручную или сгенерированные из спеки, отдаются
обратно в `/v3/api-docs` без потерь:

```json
{
  "password": {
    "type": "string",
    "description": "User password [MASKED IN LOGS]",
    "format": "password",
    "x-masked": { "type": "CREDENTIAL" }
  },
  "email": {
    "type": "string",
    "description": "User email [MASKED IN LOGS]",
    "x-masked": { "type": "PII", "showFirst": 1, "showLast": 2 }
  }
}
```

Дополнительно: к `description` дописывается суффикс `[MASKED IN LOGS]`,
`CREDENTIAL`-поля получают `format: "password"` (Swagger UI рисует точки).

springdoc объявлен как `compileOnly` — добавьте его в свой проект, если хотите
обогащение:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
```

Отключить, не убирая springdoc: `log.masking.openapi.enabled: false`.

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

- **`MaskingAppenderRegistrar`** реализует `SmartInitializingSingleton` и
  запускается после создания всех singleton-бинов Spring. Он проходит по
  каждому Logback-логгеру, снимает существующие аппендеры и присоединяет их
  обратно, обёрнутыми в `MaskingAppender`. Операция идемпотентна (обёртки
  помечены префиксом `masking:`).
- **`MaskedLoggingEventDecorator`** реализует `ILoggingEvent`, переопределяет
  только `getArgumentArray()` и `getFormattedMessage()` — всё остальное
  (timestamp, level, MDC, поток) делегируется оригинальному событию.
- **Кеш метаданных** живёт в `ConcurrentHashMap<Class<?>, ClassMetadata>`.
  Рефлексия выполняется один раз на класс.
- **JDK-классы пропускаются** при сканировании полей (модули, чьё имя
  начинается на `java.`), чтобы не ловить `InaccessibleObjectException` на
  Java 16+.

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
| В codegen `showFirst: 0` / `showLast: 0` трактуются как «не задано» | Mustache считает 0 falsy; используйте любое ненулевое значение или просто не указывайте параметр |

---

## Модули

- **`log-masking-starter/`** — публикуемая библиотека. Spring Boot
  auto-configured starter (`java-library`, без `bootJar`). Регистрируется через
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  Шаблоны для openapi-generator лежат в
  `src/main/resources/openapi-templates/v{6,7}/{spring,java}/`.

- **`test-app/`** — демонстрационное Spring Boot-приложение. Показывает оба
  сценария:
  - **Code-first.** `UserController` (`/api/users`, `/api/payments`) обслуживает
    написанные вручную DTO `UserDto` / `PaymentDto` с аннотациями `@Masked`.
  - **Contract-first.** `CustomerOrderController` реализует интерфейсы
    `CustomersApi` и `OrdersApi`, сгенерированные при сборке из
    [`src/main/resources/openapi/api.yaml`](test-app/src/main/resources/openapi/api.yaml)
    через openapi-generator + наши Mustache-шаблоны. Эндпоинты:
    `/api/v2/customers`, `/api/v2/orders`.

  Интеграционные тесты:
  - `MaskingIntegrationTest` — code-first DTO маскируются в логах
  - `GeneratedDtoMaskingTest` — сгенерированные DTO маскируются в логах
  - `OpenApiIntegrationTest` — `/v3/api-docs` содержит `x-masked` для обоих
    наборов схем (round-trip end-to-end)

---

## Сборка и тесты

Gradle multi-module проект (Kotlin DSL), Java 17, Spring Boot 3.3.5. Версии
централизованы в `gradle/libs.versions.toml`.

```bash
./gradlew build                                      # собрать всё
./gradlew :log-masking-starter:test                  # юнит-тесты стартера
./gradlew :test-app:openApiGenerate                  # сгенерировать DTO+API из api.yaml
./gradlew :test-app:test                             # интеграционные тесты
./gradlew :test-app:bootRun                          # поднять demo-приложение на :8080
                                                     #   Swagger: /swagger-ui.html
./gradlew :log-masking-starter:compileJava           # перегенерировать spring-configuration-metadata.json
```

Codegen в `test-app` запускается автоматически перед `compileJava` через
`tasks.compileJava { dependsOn(openApiGenerate) }` — отдельная команда не
нужна для обычной сборки.

**Замечание про вывод сборки на Windows.** Вывод сборки перенаправлен в
`C:/tmp/log-masking-build/<module>` (см. `build.gradle.kts:14`). Это обход
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
- *(опционально)* **springdoc-openapi 2.x** — для runtime-обогащения схемы
- *(опционально)* **openapi-generator 6.x / 7.x** — для contract-first генерации
  DTO

---

## Документация

Полная документация — в каталоге [`docs/`](docs/) (Jekyll / GitHub Pages):

- [Getting Started](docs/getting-started.md) — установка и базовое использование
- [Annotations](docs/annotations.md) — справочник `@Masked` и `MaskType`
- [Configuration](docs/configuration.md) — все свойства и примеры
- [How It Works](docs/how-it-works.md) — архитектура и обоснование решений
- [Customization](docs/customization.md) — собственные стратегии, работа с Lombok и Jackson
- [OpenAPI Integration](docs/openapi-integration.md) — обогащение Swagger-схем
- [OpenAPI Code Generation](docs/openapi-codegen.md) — генерация DTO с `@Masked` из OpenAPI-документа

Исходный план проектирования со сравнением альтернатив и обоснованием
архитектурных решений: [`PLAN.md`](PLAN.md).

---

## Лицензия

См. файл лицензии в репозитории.
