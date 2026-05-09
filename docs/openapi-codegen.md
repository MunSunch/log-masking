---
title: OpenAPI Code Generation
nav_order: 7
---

# OpenAPI Code Generation

`log-masking-starter` ships Mustache templates for
[OpenAPI Generator](https://openapi-generator.tech/) so that DTOs declared in
an OpenAPI document are emitted with the `@Masked` annotation already in place.
This is the contract-first counterpart to the [OpenAPI customizer](openapi-integration.md):
the customizer writes the `x-masked` extension *from* code into the OpenAPI
document, and the templates here read that same extension *back* into Java
sources.

The two directions use the same vocabulary, so an OpenAPI document published by
one Spring Boot service can be fed straight into another service's codegen and
the masking annotations round-trip without manual editing.

---

## Marking sensitive fields in OpenAPI

Add an `x-masked` vendor extension to any property whose value should be masked
in logs. The shape of the extension mirrors the
[`@Masked`](annotations.md) annotation one-for-one:

```yaml
components:
  schemas:
    UserDto:
      type: object
      properties:
        name:
          type: string
        password:
          type: string
          x-masked:
            type: CREDENTIAL
        email:
          type: string
          x-masked:
            type: PII
            showFirst: 1
            showLast: 2
        cardNumber:
          type: string
          x-masked:
            type: FINANCIAL
            showLast: 4
        ssn:
          type: string
          x-masked:
            replacement: "[REDACTED]"
        legacyToken:
          type: string
          x-masked:
            type: CUSTOM
            maskChar: "#"
```

### Supported parameters

| Key           | Type                                            | Default   | Notes |
|:--------------|:------------------------------------------------|:----------|:------|
| `type`        | `CREDENTIAL` \| `PII` \| `FINANCIAL` \| `CUSTOM`| `CUSTOM`  | Maps to `MaskType` enum |
| `showFirst`   | int                                             | annotation default (`-1`) | Characters left unmasked at the start |
| `showLast`    | int                                             | annotation default (`-1`) | Characters left unmasked at the end |
| `maskChar`    | string of length 1                              | annotation default (`'\0'`) | Override the global mask character |
| `replacement` | string                                          | annotation default (empty) | Fixed replacement string; takes priority over other parameters |

All parameters except `type` are optional. Omitted parameters fall back to the
defaults configured via `application.yml` at runtime — so you can keep the YAML
minimal and tune masking behaviour from properties.

---

## Hooking templates into your build

The templates live inside the `log-masking-starter` JAR under
`openapi-templates/v{6,7}/{spring,java}/pojo.mustache`. OpenAPI Generator wants
a filesystem path for `templateDir`, so use a small `Sync` task to unpack the
templates from the dependency JAR before generation runs.

### Gradle (Kotlin DSL)

```kotlin
plugins {
    java
    id("org.openapi.generator") version "7.22.0"
}

// Pull the starter JAR a second time into a build-only configuration so the
// codegen artifact never reaches the runtime classpath.
val maskingTemplates: Configuration by configurations.creating
val templatesDir = layout.buildDirectory.dir("openapi-templates")

dependencies {
    implementation("io.github.munsunch:log-masking-starter:0.2.0")
    maskingTemplates("io.github.munsunch:log-masking-starter:0.2.0")
}

val unpackMaskingTemplates by tasks.registering(Sync::class) {
    from(maskingTemplates.map(::zipTree))
    include("openapi-templates/v7/spring/**")            // or v7/java, v6/...
    eachFile {
        relativePath = RelativePath(true,
            *relativePath.segments.drop(3).toTypedArray()) // strip openapi-templates/v7/spring/
    }
    includeEmptyDirs = false
    into(templatesDir)
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$projectDir/src/main/resources/openapi.yaml")
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

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}
```

`globalProperties` selects "apis + models" only and skips supporting files
(`pom.xml`, `README.md`, `ApiUtil.java`, etc.) — those make sense for a
standalone codegen project but get in the way when you're integrating the
output into an existing Spring Boot module. `skipDefaultInterface=true` makes
the generated API methods abstract, so your controller is forced to implement
them explicitly (otherwise the generated default body references the skipped
`ApiUtil`).

The same pattern lives in the repo at
[`test-app/build.gradle.kts`](../test-app/build.gradle.kts), differing only in
that the demo points `templateDir` at the colocated source folder rather than
unpacking from a JAR.

### Maven

```xml
<project>
  <build>
    <plugins>
      <!-- 1. Unpack templates from the starter JAR -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <executions>
          <execution>
            <id>unpack-masking-templates</id>
            <phase>generate-sources</phase>
            <goals><goal>unpack</goal></goals>
            <configuration>
              <artifactItems>
                <artifactItem>
                  <groupId>io.github.munsunch</groupId>
                  <artifactId>log-masking-starter</artifactId>
                  <version>0.2.0</version>
                  <includes>openapi-templates/v7/spring/**</includes>
                  <outputDirectory>${project.build.directory}/openapi-templates-raw</outputDirectory>
                </artifactItem>
              </artifactItems>
            </configuration>
          </execution>
        </executions>
      </plugin>

      <!-- 2. Run openapi-generator with the unpacked templates -->
      <plugin>
        <groupId>org.openapitools</groupId>
        <artifactId>openapi-generator-maven-plugin</artifactId>
        <version>7.22.0</version>
        <executions>
          <execution>
            <goals><goal>generate</goal></goals>
            <configuration>
              <generatorName>spring</generatorName>
              <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
              <templateDirectory>${project.build.directory}/openapi-templates-raw/openapi-templates/v7/spring</templateDirectory>
              <apiPackage>com.example.api</apiPackage>
              <modelPackage>com.example.model</modelPackage>
              <configOptions>
                <useSpringBoot3>true</useSpringBoot3>
                <interfaceOnly>true</interfaceOnly>
                <skipDefaultInterface>true</skipDefaultInterface>
              </configOptions>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## Picking the right template variant

| Variant directory                   | OpenAPI Generator version | Generator name |
|:------------------------------------|:--------------------------|:---------------|
| `openapi-templates/v7/spring/`      | 7.0.0 – 7.x               | `spring`       |
| `openapi-templates/v7/java/`        | 7.0.0 – 7.x               | `java`         |
| `openapi-templates/v6/spring/`      | 6.0.0 – 6.x               | `spring`       |
| `openapi-templates/v6/java/`        | 6.0.0 – 6.x               | `java`         |

The fork only adds one Mustache section per template and leaves the rest of
upstream `pojo.mustache` untouched, so other generator features (Lombok,
JsonNullable, bean validation, etc.) continue to work unchanged.

If you upgrade OpenAPI Generator across a major version, bump
`include("openapi-templates/v{N}/...")` in your sync task accordingly.

---

## What the generated code looks like

For the YAML at the top of this page the spring generator emits:

```java
public class UserDto {

  private String name;

  @com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CREDENTIAL)
  private String password;

  @com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.PII, showFirst = 1, showLast = 2)
  private String email;

  @com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.FINANCIAL, showLast = 4)
  private String cardNumber;

  @com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CUSTOM, replacement = "[REDACTED]")
  private String ssn;

  @com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CUSTOM, maskChar = '#')
  private String legacyToken;

  // ... getters / setters / equals / hashCode / toString
}
```

Fully-qualified class names are used on purpose — that way the templates do not
need to maintain a separate import list and the emitted code compiles regardless
of how the rest of the model file is structured.

---

## Round-trip with the OpenAPI customizer

When `springdoc-openapi` is on the classpath, `OpenApiMaskingCustomizer` writes
the same `x-masked` shape into `/v3/api-docs`:

```json
{
  "components": {
    "schemas": {
      "UserDto": {
        "properties": {
          "password": {
            "type": "string",
            "format": "password",
            "x-masked": { "type": "CREDENTIAL" },
            "description": "User password [MASKED IN LOGS]"
          }
        }
      }
    }
  }
}
```

You can therefore export the live OpenAPI document from one Spring Boot service
and feed it straight into another service's codegen — `@Masked` survives the
round-trip end-to-end.

---

## Limitations

- **`showFirst: 0` / `showLast: 0` are treated as "not specified".** Mustache's
  section semantics render `0` as falsy, so the corresponding parameter is
  omitted from the generated annotation and falls back to the
  `application.yml` / annotation default. Use any non-zero value, or omit the
  field entirely, when you don't want first/last characters revealed.
- **Generators other than `spring` / `java` are not covered.** The fork only
  ships templates for those two. To support `kotlin`, `jaxrs-spec`, etc., copy
  the matching upstream `pojo.mustache` and add the same `{{#vendorExtensions.x-masked}}` block.
- **Template upstream drift.** OpenAPI Generator occasionally restructures
  `pojo.mustache`. If you upgrade the generator inside a minor track and start
  losing fields or annotations, re-fork from the matching upstream tag and
  re-apply the `x-masked` block.
