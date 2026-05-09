plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(layout.projectDirectory.file("src/main/resources/openapi/api.yaml").asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("com.munsun.testapp.generated.api")
    modelPackage.set("com.munsun.testapp.generated.model")
    invokerPackage.set("com.munsun.testapp.generated")
    templateDir.set(rootProject.file("log-masking-starter/src/main/resources/openapi-templates/v7/spring").absolutePath)
    configOptions.set(mapOf(
        "useSpringBoot3"        to "true",
        "interfaceOnly"         to "true",
        "useTags"               to "true",
        "openApiNullable"       to "false",
        "skipDefaultInterface"  to "true",
        "useBeanValidation"     to "true"
    ))
    globalProperties.set(mapOf(
        "apis"            to "",
        "models"          to "",
        "supportingFiles" to "false"
    ))
}

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    }
}

tasks.named("compileJava") {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {
    implementation(project(":log-masking-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.springdoc.openapi)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
