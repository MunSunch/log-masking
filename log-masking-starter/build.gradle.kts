plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api("org.springframework.boot:spring-boot-autoconfigure")
    implementation("ch.qos.logback:logback-classic")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly(libs.springdoc.openapi)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.springdoc.openapi)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Dfile.encoding=UTF-8")
}
