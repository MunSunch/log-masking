import org.jreleaser.model.Active

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jreleaser)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
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

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
    }
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("log-masking-starter")
                description.set(
                    "Spring Boot 3 starter for annotation-driven masking of sensitive " +
                        "fields (PII, credentials, financial data) in log output. Zero-config, " +
                        "non-invasive, works with any Logback appender and layout."
                )
                url.set("https://github.com/MunSunch/log-masking")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("msunchalyaev")
                        name.set("msunchalyaev")
                        email.set("msunchalyaev@gmail.com")
                        url.set("https://github.com/MunSunch")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/MunSunch/log-masking.git")
                    developerConnection.set("scm:git:ssh://git@github.com/MunSunch/log-masking.git")
                    url.set("https://github.com/MunSunch/log-masking")
                }

                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/MunSunch/log-masking/issues")
                }
            }
        }
    }
}

jreleaser {
    gitRootSearch.set(true)

    project {
        description.set("Annotation-driven masking of sensitive fields in Spring Boot 3 logs.")
        copyright.set("2026 msunchalyaev")
        inceptionYear.set("2026")
        authors.set(listOf("msunchalyaev"))
        license.set("Apache-2.0")
        links {
            homepage.set("https://github.com/MunSunch/log-masking")
        }
    }

    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        verify.set(true)
    }

    release {
        github {
            skipRelease.set(true)
            skipTag.set(true)
            overwrite.set(true)
        }
    }

    deploy {
        maven {
            mavenCentral {
                register("sonatype") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                    applyMavenCentralRules.set(true)
                    sign.set(true)
                    checksums.set(true)
                    sourceJar.set(true)
                    javadocJar.set(true)
                    retryDelay.set(60)
                }
            }
        }
    }
}
