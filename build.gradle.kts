import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.allure)
    alias(libs.plugins.versions)
    alias(libs.plugins.graalvm.native)
}

group = "com.example.starter"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

allure {
    adapter {
        aspectjWeaver.set(false)
    }
}

dependencies {
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.grpc.server)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.reactor.kotlin.extensions)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.grpc.server.test)
    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.strikt.core)
    testImplementation(libs.allure.junit5)
    testImplementation(libs.junit.platform.launcher)

    implementation(libs.commons.math3)
    implementation(libs.tablesaw.core)
    implementation(libs.caffeine)
    implementation(libs.okhttp)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.shell.starter)
    testImplementation(libs.wiremock)
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
    create("e2eTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
    create("nativeTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

kotlin {
    sourceSets {
        named("integrationTest") {
            kotlin.srcDir("src/integrationTest/kotlin")
        }
        named("e2eTest") {
            kotlin.srcDir("src/e2eTest/kotlin")
        }
        named("nativeTest") {
            kotlin.srcDir("src/nativeTest/kotlin")
        }
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
    named("e2eTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("e2eTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
    named("nativeTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("nativeTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    plugins {
        named("grpc") {
            artifact = libs.grpc.protoc.gen.java.get().toString()
        }
        id("grpckt") {
            artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                named("grpc") { }
                id("grpckt") { }
            }
        }
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("kotlin-grpc-rest-starter")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
        named("test") {
            imageName.set("kotlin-grpc-rest-starter-test")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
    environment(
        "DOCKER_HOST",
        System.getenv("DOCKER_HOST") ?: "unix://${System.getProperty("user.home")}/.local/share/containers/podman/machine/podman.sock"
    )
    environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "true")
}

tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end tests."
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    shouldRunAfter("integrationTest")
    environment(
        "DOCKER_HOST",
        System.getenv("DOCKER_HOST") ?: "unix://${System.getProperty("user.home")}/.local/share/containers/podman/machine/podman.sock"
    )
    environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "true")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    failOnNoDiscoveredTests = false
}

