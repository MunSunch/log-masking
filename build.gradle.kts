plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

subprojects {
    group = "io.github.munsunch"
    version = findProperty("releaseVersion") as String? ?: "0.1.0-SNAPSHOT"

    // Relocate build output to an ASCII-safe path to avoid class-loading
    // issues when the project directory contains non-ASCII characters (e.g. Cyrillic on Windows).
    // Skipped on non-Windows (CI) where the default build/ directory works fine.
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        layout.buildDirectory = file("C:/tmp/log-masking-build/${project.name}")
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
