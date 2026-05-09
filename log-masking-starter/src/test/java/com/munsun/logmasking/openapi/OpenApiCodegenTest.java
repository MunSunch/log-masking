package com.munsun.logmasking.openapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the bundled Mustache templates make openapi-generator emit
 * {@code @Masked} annotations on fields whose schema carries the
 * {@code x-masked} vendor extension.
 * <p>
 * Tests run against openapi-generator 7.x; for v6 templates the same Mustache
 * block is shipped (round-trip-verified by {@link #v6TemplatesContainMaskedBlock}).
 */
class OpenApiCodegenTest {

    private static final String FIXTURE = "/codegen-fixtures/sample.yaml";
    private static final Path V7_SPRING_TEMPLATES = Paths.get("src/main/resources/openapi-templates/v7/spring").toAbsolutePath();
    private static final Path V7_JAVA_TEMPLATES = Paths.get("src/main/resources/openapi-templates/v7/java").toAbsolutePath();
    private static final Path V6_SPRING_TEMPLATES = Paths.get("src/main/resources/openapi-templates/v6/spring").toAbsolutePath();
    private static final Path V6_JAVA_TEMPLATES = Paths.get("src/main/resources/openapi-templates/v6/java").toAbsolutePath();

    @Test
    void springGenerator_v7_emitsMaskedAnnotations(@TempDir Path outputDir) throws Exception {
        runGenerator("spring", outputDir, V7_SPRING_TEMPLATES);
        String generated = Files.readString(outputDir.resolve("src/main/java/com/example/test/model/UserDto.java"));
        assertMaskedAnnotations(generated);
    }

    @Test
    void javaGenerator_v7_emitsMaskedAnnotations(@TempDir Path outputDir) throws Exception {
        runGenerator("java", outputDir, V7_JAVA_TEMPLATES);
        String generated = Files.readString(outputDir.resolve("src/main/java/com/example/test/model/UserDto.java"));
        assertMaskedAnnotations(generated);
    }

    @Test
    void v6TemplatesContainMaskedBlock() throws Exception {
        Stream.of(V6_SPRING_TEMPLATES, V6_JAVA_TEMPLATES).forEach(dir -> {
            try {
                String content = Files.readString(dir.resolve("pojo.mustache"));
                assertThat(content)
                        .as("v6 template at %s must carry the x-masked Mustache block", dir)
                        .contains("{{#vendorExtensions.x-masked}}")
                        .contains("@com.munsun.logmasking.annotation.Masked")
                        .contains("com.munsun.logmasking.annotation.MaskType.{{type}}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // -- helpers --------------------------------------------------------------

    private static void runGenerator(String generatorName, Path outputDir, Path templateDir) {
        String specUrl = OpenApiCodegenTest.class.getResource(FIXTURE).toString();
        CodegenConfigurator cfg = new CodegenConfigurator()
                .setGeneratorName(generatorName)
                .setInputSpec(specUrl)
                .setOutputDir(outputDir.toString())
                .setTemplateDir(templateDir.toString())
                .setApiPackage("com.example.test.api")
                .setModelPackage("com.example.test.model")
                .setInvokerPackage("com.example.test");
        new DefaultGenerator().opts(cfg.toClientOptInput()).generate();
    }

    private static void assertMaskedAnnotations(String generated) {
        assertThat(generated)
                .contains("@com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CREDENTIAL)")
                .contains("@com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.PII, showFirst = 1, showLast = 2)")
                .contains("@com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.FINANCIAL, showLast = 4)")
                .contains("@com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CUSTOM, replacement = \"[REDACTED]\")")
                .contains("@com.munsun.logmasking.annotation.Masked(type = com.munsun.logmasking.annotation.MaskType.CUSTOM, maskChar = '#')");

        assertThat(generated)
                .as("the unmarked 'name' field must NOT receive a @Masked annotation")
                .doesNotContainPattern("private String name[^;]*\\n\\s*@com\\.munsun\\.logmasking\\.annotation\\.Masked");
    }
}
