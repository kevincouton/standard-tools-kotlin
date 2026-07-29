import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.allure)
    alias(libs.plugins.versions)
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

dependencies {
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.grpc.server)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.reactor.kotlin.extensions)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.grpc.server.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.strikt.core)
    testImplementation(libs.allure.junit5)
    testImplementation(libs.junit.platform.launcher)
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
}

kotlin {
    sourceSets {
        named("integrationTest") {
            kotlin.srcDir("src/integrationTest/kotlin")
        }
        named("e2eTest") {
            kotlin.srcDir("src/e2eTest/kotlin")
        }
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
    named("e2eTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("e2eTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
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

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
}

tasks.register<Test>("e2eTest") {
    description = "Runs end-to-end tests."
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    shouldRunAfter("integrationTest")
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

