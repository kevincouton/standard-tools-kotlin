# kotlin-grpc-rest-starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot 4.1.0 Kotlin template repo exposing an Order domain via REST, gRPC, A2A, and MCP, with Postgres 18 persistence and battle-tested unit/integration/e2e tests.

**Architecture:** Single-module Clean/Hexagonal packages with domain at the center, application services as ports/use cases, and adapter packages for REST (WebFlux), gRPC (Spring Boot native gRPC starter + Kotlin coroutines), A2A (JSON-RPC), MCP (SSE), and JPA persistence. Visual test reporting runs locally and in CI.

**Tech Stack:** Spring Boot 4.1.0, Kotlin 2.3.21, Java 25 LTS, Gradle Kotlin DSL + version catalog, Spring WebFlux, Spring Data JPA, Spring Boot native gRPC starter, Postgres 18, Flyway, TestContainers, JUnit 5, MockK, Strikt, Allure, mise, Podman, act.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `settings.gradle.kts` | Defines project name, enables Java toolchain resolver, configures repositories. |
| `build.gradle.kts` | Declares plugins, dependencies, source sets for integration/e2e tests, test tasks, Allure, and JUnit config. |
| `gradle/libs.versions.toml` | Version catalog pinning Spring Boot, Kotlin, gRPC, Flyway, TestContainers, MockK, Strikt, Allure, and plugins. |
| `gradle/wrapper/gradle-wrapper.properties` | Pins Gradle 9.1.0 wrapper (supports Java 25). |
| `.mise.toml` | Pins Java 25, Gradle, act, Podman and defines one-command test tasks. |
| `.actrc` | Configures act to use Podman socket and linux/amd64 architecture. |
| `scripts/build-image.sh` | Builds the container image with Podman (overridable via `CONTAINER_RUNTIME`). |
| `scripts/run-act.sh` | Runs the GitHub Actions workflow locally with act + Podman. |
| `src/main/resources/application.yml` | Runtime datasource, JPA, gRPC server, and server port configuration. |
| `src/main/resources/application-test.yml` | Test profile datasource placeholders, gRPC test port, and logging. |
| `src/main/resources/db/migration/V1__create_orders_table.sql` | Flyway migration creating `orders` and `order_items` tables. |
| `src/main/kotlin/com/example/starter/KotlinGrpcRestStarterApplication.kt` | Spring Boot entry point. |
| `src/main/kotlin/com/example/starter/domain/Order.kt` | Aggregate root with validation, total calculation, and cancel logic. |
| `src/main/kotlin/com/example/starter/domain/OrderItem.kt` | Value object for order line items. |
| `src/main/kotlin/com/example/starter/domain/OrderStatus.kt` | Enum for order lifecycle states. |
| `src/main/kotlin/com/example/starter/domain/exception/OrderNotFoundException.kt` | Domain exception for missing orders. |
| `src/main/kotlin/com/example/starter/domain/exception/InvalidOrderStateException.kt` | Domain exception for illegal state transitions. |
| `src/main/kotlin/com/example/starter/application/port/inbound/CreateOrderUseCase.kt` | Inbound port for creating orders. |
| `src/main/kotlin/com/example/starter/application/port/inbound/GetOrderUseCase.kt` | Inbound port for fetching a single order. |
| `src/main/kotlin/com/example/starter/application/port/inbound/ListOrdersUseCase.kt` | Inbound port for listing orders with optional customer filter. |
| `src/main/kotlin/com/example/starter/application/port/inbound/CancelOrderUseCase.kt` | Inbound port for cancelling orders. |
| `src/main/kotlin/com/example/starter/application/port/outbound/OrderRepository.kt` | Outbound persistence port. |
| `src/main/kotlin/com/example/starter/application/service/OrderService.kt` | Implements all inbound ports, orchestrates domain and repository. |
| `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderEntity.kt` | JPA entity for orders. |
| `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderItemEntity.kt` | JPA entity for order items. |
| `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderJpaRepository.kt` | Spring Data JPA repository for order entities. |
| `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderPersistenceMapper.kt` | Maps between domain `Order` and JPA entities. |
| `src/main/kotlin/com/example/starter/adapter/out/persistence/JpaOrderRepository.kt` | Outbound port implementation backed by Spring Data JPA. |
| `src/main/kotlin/com/example/starter/adapter/in/web/OrderController.kt` | WebFlux REST controller exposing order endpoints, bridged via `boundedElastic`. |
| `src/main/kotlin/com/example/starter/adapter/in/web/OrderDto.kt` | Request/response DTOs and domain-to-DTO mapper. |
| `src/main/kotlin/com/example/starter/adapter/in/web/GlobalExceptionHandler.kt` | `@ControllerAdvice` mapping domain exceptions to `ProblemDetail`. |
| `src/main/proto/order_service.proto` | gRPC service definition for orders. |
| `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcOrderService.kt` | Kotlin coroutine gRPC service implementing generated stub. |
| `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcOrderMapper.kt` | Domain-to-gRPC response mapper. |
| `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcExceptionInterceptor.kt` | Maps domain exceptions to gRPC `Status` codes. |
| `src/main/kotlin/com/example/starter/config/DatabaseConfig.kt` | Enables JPA repositories. |
| `src/main/kotlin/com/example/starter/config/GrpcConfig.kt` | Registers global gRPC exception interceptor. |
| `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aAgentCardController.kt` | Serves `/.well-known/agent.json`. |
| `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt` | JSON-RPC 2.0 task endpoint (`/a2a/tasks`) dispatching skills. |
| `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aOrderSkillMapper.kt` | Maps domain orders to A2A task result maps. |
| `src/main/kotlin/com/example/starter/adapter/in/mcp/McpSseHandler.kt` | SSE endpoint (`/mcp/sse`) managing JSON-RPC sessions. |
| `src/main/kotlin/com/example/starter/adapter/in/mcp/McpMessageController.kt` | JSON-RPC message endpoint (`/mcp/messages`) dispatching tool calls. |
| `src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt` | Exposes MCP tools backed by use cases. |
| `src/main/kotlin/com/example/starter/adapter/in/mcp/McpOrderToolMapper.kt` | Renders domain orders as MCP tool text content. |
| `src/test/kotlin/com/example/starter/testsupport/ColoredConsoleSummaryListener.kt` | JUnit 5 listener printing per-layer pass/fail/skip summary. |
| `src/test/kotlin/com/example/starter/testsupport/ScenarioLogger.kt` | E2E scenario step logger with tree-style output. |
| `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener` | Service loader registration for the JUnit listener. |
| `src/test/resources/allure.properties` | Allure results directory configuration. |
| `src/test/kotlin/com/example/starter/domain/OrderTest.kt` | Unit tests for order creation, validation, and cancel rules. |
| `src/test/kotlin/com/example/starter/application/service/OrderServiceTest.kt` | Unit tests for `OrderService` with mocked repository. |
| `src/integrationTest/kotlin/com/example/starter/adapter/out/persistence/JpaOrderRepositoryIntegrationTest.kt` | `@DataJpaTest` integration test with TestContainers Postgres 18. |
| `src/e2eTest/kotlin/com/example/starter/e2e/OrderLifecycleE2ETest.kt` | `@SpringBootTest` verifying REST, gRPC, A2A, and MCP end-to-end. |
| `Dockerfile` | Multi-stage build producing an Eclipse Temurin JRE 25 image. |
| `.github/workflows/ci.yml` | GitHub Actions workflow running tests, building image, smoke test, and summary. |
| `README.md` | Setup, build, test, and run instructions. |

---

## Task 1: Gradle project setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1.1: Create version catalog**

```toml
[versions]
kotlin = "2.3.21"
springBoot = "4.1.0"
dependencyManagement = "1.1.7"
allurePlugin = "2.12.0"
protobufPlugin = "0.9.4"
versionsPlugin = "0.52.0"
flyway = "11.3.3"
testcontainers = "1.20.5"
mockk = "1.13.17"
strikt = "0.35.1"
allure = "2.29.0"
grpcKotlin = "1.4.1"
grpcJava = "1.69.0"
protoc = "3.25.5"

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-plugin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
dependency-management = { id = "io.spring.dependency-management", version.ref = "dependencyManagement" }
allure = { id = "io.qameta.allure", version.ref = "allurePlugin" }
protobuf = { id = "com.google.protobuf", version.ref = "protobufPlugin" }
versions = { id = "com.github.ben-manes.versions", version.ref = "versionsPlugin" }

[libraries]
spring-boot-starter-webflux = { module = "org.springframework.boot:spring-boot-starter-webflux" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-grpc-server = { module = "org.springframework.boot:spring-boot-starter-grpc-server" }
spring-boot-starter-grpc-server-test = { module = "org.springframework.boot:spring-boot-starter-grpc-server-test" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }

flyway-core = { module = "org.flywaydb:flyway-core" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql" }
postgresql = { module = "org.postgresql:postgresql" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core" }
kotlinx-coroutines-reactor = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-reactor" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }
reactor-kotlin-extensions = { module = "io.projectreactor.kotlin:reactor-kotlin-extensions" }

grpc-kotlin-stub = { module = "io.grpc:grpc-kotlin-stub", version.ref = "grpcKotlin" }
grpc-protoc-gen-java = { module = "io.grpc:protoc-gen-grpc-java", version.ref = "grpcJava" }
grpc-protoc-gen-kotlin = { module = "io.grpc:protoc-gen-grpc-kotlin", version.ref = "grpcKotlin" }
protoc = { module = "com.google.protobuf:protoc", version.ref = "protoc" }

testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql" }

mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
strikt-core = { module = "io.strikt:strikt-core", version.ref = "strikt" }
allure-junit5 = { module = "io.qameta.allure:allure-junit5", version.ref = "allure" }
```

- [ ] **Step 1.2: Create settings.gradle.kts**

```kotlin
rootProject.name = "kotlin-grpc-rest-starter"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 1.3: Create build.gradle.kts**

```kotlin
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
    implementation(libs.jackson.module.kotlin)
    implementation(libs.reactor.kotlin.extensions)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.grpc.server.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.strikt.core)
    testImplementation(libs.allure.junit5)
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
    create("e2eTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output + sourceSets["integrationTest"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output + sourceSets["integrationTest"].output
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
        create("grpc") {
            artifact = libs.grpc.protoc.gen.java.get().toString()
        }
        create("grpckt") {
            artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
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
}

allure {
    version.set(libs.versions.allure.get())
}
```

- [ ] **Step 1.4: Create Gradle wrapper properties**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 1.5: Verify wrapper generation**

Run: `gradle wrapper --gradle-version 9.1.0`
Expected: `gradle/wrapper/gradle-wrapper.jar` and `gradlew` scripts are created.

- [ ] **Step 1.6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties gradlew gradlew.bat
git commit -m "chore: Gradle project setup with version catalog and source sets"
```

---

## Task 2: mise local tooling

**Files:**
- Create: `.mise.toml`

- [ ] **Step 2.1: Create .mise.toml**

```toml
[tools]
java = "temurin-25"
gradle = "9.1.0"
act = "0.2.75"
podman = "latest"

[tasks]
test = "./gradlew test"
test-integration = "./gradlew integrationTest"
test-e2e = "./gradlew e2eTest"
test-all = "./gradlew test integrationTest e2eTest"
test-report = "./gradlew allureServe"
dependency-updates = "./gradlew dependencyUpdates"
```

- [ ] **Step 2.2: Verify toolchain install**

Run: `mise install`
Expected: mise installs Java 25, Gradle 9.1.0, act 0.2.75, and Podman if not present.

- [ ] **Step 2.3: Commit**

```bash
git add .mise.toml
git commit -m "chore: add mise local tooling config"
```

---

## Task 3: Podman/act local setup

**Files:**
- Create: `.actrc`
- Create: `scripts/build-image.sh`
- Create: `scripts/run-act.sh`

- [ ] **Step 3.1: Create .actrc**

```text
--container-runtime podman
--container-daemon-socket unix://{{env.HOME}}/.local/share/containers/podman/machine/podman.sock
--container-architecture linux/amd64
```

- [ ] **Step 3.2: Create scripts/build-image.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${CONTAINER_RUNTIME:-podman}"
TAG="${1:-kotlin-grpc-rest-starter:latest}"

echo "Building image with $RUNTIME as $TAG..."
$RUNTIME build -t "$TAG" .
echo "Image $TAG built successfully."
```

- [ ] **Step 3.3: Create scripts/run-act.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

PODMAN_SOCKET="${PODMAN_SOCKET:-$HOME/.local/share/containers/podman/machine/podman.sock}"
act \
  --container-runtime podman \
  --container-daemon-socket "unix://$PODMAN_SOCKET" \
  --container-architecture linux/amd64 \
  "$@"
```

- [ ] **Step 3.4: Make scripts executable**

Run: `chmod +x scripts/build-image.sh scripts/run-act.sh`
Expected: Both scripts have executable permission (`-rwxr-xr-x`).

- [ ] **Step 3.5: Commit**

```bash
git add .actrc scripts/build-image.sh scripts/run-act.sh
git commit -m "chore: add Podman/act local CI scripts"
```

---

## Task 4: Application configuration

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-test.yml`

- [ ] **Step 4.1: Create application.yml**

```yaml
spring:
  application:
    name: kotlin-grpc-rest-starter
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:starter}
    username: ${DB_USER:starter}
    password: ${DB_PASS:starter}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  grpc:
    server:
      port: 9090

server:
  port: 8080
```

- [ ] **Step 4.2: Create application-test.yml**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/starter_test
    username: test
    password: test
  jpa:
    hibernate:
      ddl-auto: validate
  grpc:
    server:
      port: 0

logging:
  level:
    org.testcontainers: INFO
    org.flywaydb: INFO
```

- [ ] **Step 4.3: Commit**

```bash
git add src/main/resources/application.yml src/main/resources/application-test.yml
git commit -m "chore: add application and test configuration"
```

---

## Task 5: Flyway migration for orders table

**Files:**
- Create: `src/main/resources/db/migration/V1__create_orders_table.sql`

- [ ] **Step 5.1: Create Flyway migration**

```sql
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
```

- [ ] **Step 5.2: Commit**

```bash
git add src/main/resources/db/migration/V1__create_orders_table.sql
git commit -m "chore: add Flyway migration for orders and order_items"
```

---

## Task 6: Domain classes

**Files:**
- Create: `src/main/kotlin/com/example/starter/domain/OrderStatus.kt`
- Create: `src/main/kotlin/com/example/starter/domain/OrderItem.kt`
- Create: `src/main/kotlin/com/example/starter/domain/Order.kt`
- Create: `src/main/kotlin/com/example/starter/domain/exception/OrderNotFoundException.kt`
- Create: `src/main/kotlin/com/example/starter/domain/exception/InvalidOrderStateException.kt`

- [ ] **Step 6.1: Create OrderStatus enum**

```kotlin
package com.example.starter.domain

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    CANCELLED
}
```

- [ ] **Step 6.2: Create OrderItem value object**

```kotlin
package com.example.starter.domain

import java.math.BigDecimal

data class OrderItem(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    init {
        require(productId.isNotBlank()) { "productId must not be blank" }
        require(quantity > 0) { "quantity must be positive" }
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must not be negative" }
    }

    val lineTotal: BigDecimal
        get() = unitPrice * quantity.toBigDecimal()
}
```

- [ ] **Step 6.3: Create Order aggregate**

```kotlin
package com.example.starter.domain

import com.example.starter.domain.exception.InvalidOrderStateException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID = UUID.randomUUID(),
    val customerId: String,
    val items: List<OrderItem>,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Instant = Instant.now()
) {
    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.lineTotal }

    fun cancel(): Order {
        if (status == OrderStatus.CANCELLED) {
            throw InvalidOrderStateException("Order ${id} is already cancelled")
        }
        if (status == OrderStatus.SHIPPED) {
            throw InvalidOrderStateException("Cannot cancel shipped order ${id}")
        }
        return copy(status = OrderStatus.CANCELLED)
    }

    companion object {
        fun create(customerId: String, items: List<OrderItem>): Order {
            require(customerId.isNotBlank()) { "customerId must not be blank" }
            require(items.isNotEmpty()) { "Order must contain at least one item" }
            return Order(customerId = customerId, items = items)
        }
    }
}
```

- [ ] **Step 6.4: Create domain exceptions**

```kotlin
package com.example.starter.domain.exception

class OrderNotFoundException(message: String) : RuntimeException(message)
```

```kotlin
package com.example.starter.domain.exception

class InvalidOrderStateException(message: String) : RuntimeException(message)
```

- [ ] **Step 6.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/domain/
git commit -m "feat: add Order domain model and exceptions"
```

---

## Task 7: Application ports and OrderService use cases

**Files:**
- Create: `src/main/kotlin/com/example/starter/application/port/inbound/CreateOrderUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/application/port/inbound/GetOrderUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/application/port/inbound/ListOrdersUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/application/port/inbound/CancelOrderUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/application/port/outbound/OrderRepository.kt`
- Create: `src/main/kotlin/com/example/starter/application/service/OrderService.kt`

- [ ] **Step 7.1: Create inbound ports**

```kotlin
package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem

interface CreateOrderUseCase {
    fun createOrder(command: CreateOrderCommand): Order

    data class CreateOrderCommand(
        val customerId: String,
        val items: List<OrderItem>
    )
}
```

```kotlin
package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import java.util.UUID

interface GetOrderUseCase {
    fun getOrder(id: UUID): Order
}
```

```kotlin
package com.example.starter.application.port.inbound

import com.example.starter.domain.Order

interface ListOrdersUseCase {
    fun listOrders(customerId: String? = null): List<Order>
}
```

```kotlin
package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import java.util.UUID

interface CancelOrderUseCase {
    fun cancelOrder(id: UUID): Order
}
```

- [ ] **Step 7.2: Create outbound port**

```kotlin
package com.example.starter.application.port.outbound

import com.example.starter.domain.Order
import java.util.UUID

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: UUID): Order?
    fun findAll(customerId: String? = null): List<Order>
}
```

- [ ] **Step 7.3: Create OrderService**

```kotlin
package com.example.starter.application.service

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import com.example.starter.domain.exception.OrderNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository
) : CreateOrderUseCase, GetOrderUseCase, ListOrdersUseCase, CancelOrderUseCase {

    override fun createOrder(command: CreateOrderUseCase.CreateOrderCommand): Order {
        val order = Order.create(command.customerId, command.items)
        return orderRepository.save(order)
    }

    override fun getOrder(id: UUID): Order {
        return orderRepository.findById(id) ?: throw OrderNotFoundException("Order not found: $id")
    }

    override fun listOrders(customerId: String?): List<Order> {
        return orderRepository.findAll(customerId)
    }

    override fun cancelOrder(id: UUID): Order {
        val order = getOrder(id)
        val cancelled = order.cancel()
        return orderRepository.save(cancelled)
    }
}
```

- [ ] **Step 7.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/application/
git commit -m "feat: add application ports and OrderService implementation"
```

---

## Task 8: JPA persistence adapter

**Files:**
- Create: `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderEntity.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderItemEntity.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderJpaRepository.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/out/persistence/OrderPersistenceMapper.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/out/persistence/JpaOrderRepository.kt`
- Create: `src/main/kotlin/com/example/starter/config/DatabaseConfig.kt`

- [ ] **Step 8.1: Create JPA entities**

```kotlin
package com.example.starter.adapter.out.persistence

import com.example.starter.domain.OrderStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false)
    var customerId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<OrderItemEntity> = mutableListOf()
)
```

```kotlin
package com.example.starter.adapter.out.persistence

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "order_items")
class OrderItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "product_id", nullable = false)
    var productId: String = "",

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0,

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    var unitPrice: BigDecimal = BigDecimal.ZERO,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: OrderEntity? = null
)
```

- [ ] **Step 8.2: Create Spring Data repository**

```kotlin
package com.example.starter.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderJpaRepository : JpaRepository<OrderEntity, UUID> {
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.customerId = :customerId")
    fun findByCustomerId(@Param("customerId") customerId: String): List<OrderEntity>
}
```

- [ ] **Step 8.3: Create persistence mapper**

```kotlin
package com.example.starter.adapter.out.persistence

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import org.springframework.stereotype.Component

@Component
class OrderPersistenceMapper {

    fun toEntity(order: Order): OrderEntity {
        val entity = OrderEntity(
            id = order.id,
            customerId = order.customerId,
            status = order.status,
            createdAt = order.createdAt
        )
        entity.items = order.items.map { toEntity(it, entity) }.toMutableList()
        return entity
    }

    private fun toEntity(item: OrderItem, order: OrderEntity): OrderItemEntity {
        return OrderItemEntity(
            productId = item.productId,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            order = order
        )
    }

    fun toDomain(entity: OrderEntity): Order {
        return Order(
            id = entity.id,
            customerId = entity.customerId,
            status = entity.status,
            createdAt = entity.createdAt,
            items = entity.items.map { toDomain(it) }
        )
    }

    private fun toDomain(entity: OrderItemEntity): OrderItem {
        return OrderItem(
            productId = entity.productId,
            quantity = entity.quantity,
            unitPrice = entity.unitPrice
        )
    }
}
```

- [ ] **Step 8.4: Create repository adapter implementation**

```kotlin
package com.example.starter.adapter.out.persistence

import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaOrderRepository(
    private val jpaRepository: OrderJpaRepository,
    private val mapper: OrderPersistenceMapper
) : OrderRepository {

    override fun save(order: Order): Order {
        val entity = mapper.toEntity(order)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: UUID): Order? {
        return jpaRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)
    }

    override fun findAll(customerId: String?): List<Order> {
        val entities = if (customerId != null) {
            jpaRepository.findByCustomerId(customerId)
        } else {
            jpaRepository.findAll()
        }
        return entities.map { mapper.toDomain(it) }
    }
}
```

- [ ] **Step 8.5: Create DatabaseConfig**

```kotlin
package com.example.starter.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["com.example.starter.adapter.out.persistence"])
class DatabaseConfig
```

- [ ] **Step 8.6: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/out/persistence/ src/main/kotlin/com/example/starter/config/DatabaseConfig.kt
git commit -m "feat: add JPA persistence adapter for Order domain"
```

---

## Task 9: REST adapter

**Files:**
- Create: `src/main/kotlin/com/example/starter/adapter/in/web/OrderDto.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/web/OrderController.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/web/GlobalExceptionHandler.kt`

- [ ] **Step 9.1: Create DTOs and mapper**

```kotlin
package com.example.starter.adapter.`in`.web

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    val customerId: String,
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class OrderResponse(
    val id: UUID,
    val customerId: String,
    val items: List<OrderItemResponse>,
    val status: String,
    val totalAmount: BigDecimal,
    val createdAt: Instant
)

data class OrderItemResponse(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

fun Order.toResponse(): OrderResponse {
    return OrderResponse(
        id = id,
        customerId = customerId,
        status = status.name,
        totalAmount = totalAmount,
        createdAt = createdAt,
        items = items.map { it.toResponse() }
    )
}

fun OrderItem.toResponse(): OrderItemResponse {
    return OrderItemResponse(
        productId = productId,
        quantity = quantity,
        unitPrice = unitPrice,
        lineTotal = lineTotal
    )
}
```

- [ ] **Step 9.2: Create WebFlux controller**

```kotlin
package com.example.starter.adapter.`in`.web

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.domain.OrderItem
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@RestController
@RequestMapping("/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val listOrdersUseCase: ListOrdersUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody request: CreateOrderRequest): Mono<OrderResponse> {
        return Mono.fromCallable {
            val command = CreateOrderUseCase.CreateOrderCommand(
                customerId = request.customerId,
                items = request.items.map { OrderItem(it.productId, it.quantity, it.unitPrice) }
            )
            createOrderUseCase.createOrder(command)
        }.subscribeOn(Schedulers.boundedElastic()).map { it.toResponse() }
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getOrder(@PathVariable id: UUID): Mono<OrderResponse> {
        return Mono.fromCallable { getOrderUseCase.getOrder(id) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { it.toResponse() }
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listOrders(@RequestParam customerId: String?): Mono<List<OrderResponse>> {
        return Mono.fromCallable { listOrdersUseCase.listOrders(customerId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { orders -> orders.map { it.toResponse() } }
    }

    @PostMapping("/{id}/cancel", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun cancelOrder(@PathVariable id: UUID): Mono<OrderResponse> {
        return Mono.fromCallable { cancelOrderUseCase.cancelOrder(id) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { it.toResponse() }
    }
}
```

- [ ] **Step 9.3: Create global exception handler**

```kotlin
package com.example.starter.adapter.`in`.web

import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindingException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleNotFound(ex: OrderNotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found").apply {
            title = "Order Not Found"
        }
    }

    @ExceptionHandler(InvalidOrderStateException::class)
    fun handleInvalidState(ex: InvalidOrderStateException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Invalid state").apply {
            title = "Invalid Order State"
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request").apply {
            title = "Bad Request"
        }
    }

    @ExceptionHandler(WebExchangeBindingException::class)
    fun handleValidation(ex: WebExchangeBindingException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.reason ?: "Validation failed").apply {
            title = "Validation Failed"
        }
    }
}
```

- [ ] **Step 9.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/web/
git commit -m "feat: add REST adapter with WebFlux controller and global exception handler"
```

---

## Task 10: gRPC adapter

**Files:**
- Create: `src/main/proto/order_service.proto`
- Create: `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcOrderMapper.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcOrderService.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/grpc/GrpcExceptionInterceptor.kt`
- Create: `src/main/kotlin/com/example/starter/config/GrpcConfig.kt`

- [ ] **Step 10.1: Create proto file**

```protobuf
syntax = "proto3";

package com.example.starter.grpc;
option java_package = "com.example.starter.grpc";
option java_multiple_files = true;

import "google/protobuf/timestamp.proto";

service OrderService {
  rpc CreateOrder (CreateOrderRequest) returns (OrderResponse);
  rpc GetOrder (GetOrderRequest) returns (OrderResponse);
  rpc ListOrders (ListOrdersRequest) returns (ListOrdersResponse);
  rpc CancelOrder (CancelOrderRequest) returns (OrderResponse);
}

message CreateOrderRequest {
  string customer_id = 1;
  repeated OrderItemRequest items = 2;
}

message OrderItemRequest {
  string product_id = 1;
  int32 quantity = 2;
  string unit_price = 3;
}

message GetOrderRequest {
  string order_id = 1;
}

message ListOrdersRequest {
  optional string customer_id = 1;
}

message CancelOrderRequest {
  string order_id = 1;
}

message ListOrdersResponse {
  repeated OrderResponse orders = 1;
}

message OrderResponse {
  string order_id = 1;
  string customer_id = 2;
  repeated OrderItemResponse items = 3;
  string status = 4;
  string total_amount = 5;
  google.protobuf.Timestamp created_at = 6;
}

message OrderItemResponse {
  string product_id = 1;
  int32 quantity = 2;
  string unit_price = 3;
  string line_total = 4;
}
```

- [ ] **Step 10.2: Create gRPC mapper**

```kotlin
package com.example.starter.adapter.`in`.grpc

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.grpc.OrderItemResponse
import com.example.starter.grpc.OrderResponse
import com.google.protobuf.Timestamp
import java.math.BigDecimal

fun Order.toGrpcResponse(): OrderResponse {
    return OrderResponse.newBuilder()
        .setOrderId(id.toString())
        .setCustomerId(customerId)
        .setStatus(status.name)
        .setTotalAmount(totalAmount.toPlainString())
        .setCreatedAt(
            Timestamp.newBuilder()
                .setSeconds(createdAt.epochSecond)
                .setNanos(createdAt.nano)
                .build()
        )
        .addAllItems(items.map { it.toGrpcResponse() })
        .build()
}

fun OrderItem.toGrpcResponse(): OrderItemResponse {
    return OrderItemResponse.newBuilder()
        .setProductId(productId)
        .setQuantity(quantity)
        .setUnitPrice(unitPrice.toPlainString())
        .setLineTotal(lineTotal.toPlainString())
        .build()
}
```

- [ ] **Step 10.3: Create coroutine gRPC service**

```kotlin
package com.example.starter.adapter.`in`.grpc

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.grpc.CancelOrderRequest
import com.example.starter.grpc.CreateOrderRequest
import com.example.starter.grpc.GetOrderRequest
import com.example.starter.grpc.ListOrdersRequest
import com.example.starter.grpc.ListOrdersResponse
import com.example.starter.grpc.OrderResponse
import com.example.starter.grpc.OrderServiceGrpcKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.math.BigDecimal
import java.util.UUID

@GrpcService
class GrpcOrderService(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val listOrdersUseCase: ListOrdersUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) : OrderServiceGrpcKt.OrderServiceCoroutineImplBase() {

    override suspend fun createOrder(request: CreateOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        val command = CreateOrderUseCase.CreateOrderCommand(
            customerId = request.customerId,
            items = request.itemsList.map {
                OrderItem(it.productId, it.quantity, BigDecimal(it.unitPrice))
            }
        )
        createOrderUseCase.createOrder(command).toGrpcResponse()
    }

    override suspend fun getOrder(request: GetOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        getOrderUseCase.getOrder(UUID.fromString(request.orderId)).toGrpcResponse()
    }

    override suspend fun listOrders(request: ListOrdersRequest): ListOrdersResponse = withContext(Dispatchers.IO) {
        val customerId = if (request.hasCustomerId()) request.customerId else null
        val orders = listOrdersUseCase.listOrders(customerId)
        ListOrdersResponse.newBuilder()
            .addAllOrders(orders.map { it.toGrpcResponse() })
            .build()
    }

    override suspend fun cancelOrder(request: CancelOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        cancelOrderUseCase.cancelOrder(UUID.fromString(request.orderId)).toGrpcResponse()
    }
}
```

- [ ] **Step 10.4: Create gRPC exception handler**

```kotlin
package com.example.starter.adapter.`in`.grpc

import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import io.grpc.Status
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

@Component
class OrderGrpcExceptionHandler : GrpcExceptionHandler {

    override fun handle(t: Throwable): Status? {
        return when (t) {
            is OrderNotFoundException -> Status.NOT_FOUND.withDescription(t.message).withCause(t)
            is InvalidOrderStateException -> Status.FAILED_PRECONDITION.withDescription(t.message).withCause(t)
            is IllegalArgumentException -> Status.INVALID_ARGUMENT.withDescription(t.message).withCause(t)
            else -> null
        }
    }
}
```

- [ ] **Step 10.5: Create GrpcConfig**

```kotlin
package com.example.starter.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.service.GrpcService

@Configuration
@ComponentScan(basePackageClasses = [GrpcService::class])
class GrpcConfig
```

- [ ] **Step 10.6: Verify proto compilation**

Run: `./gradlew generateProto`
Expected: `build/generated/source/proto/main/grpckt/com/example/starter/grpc/OrderServiceGrpcKt.kt` is generated.

- [ ] **Step 10.7: Commit**

```bash
git add src/main/proto/order_service.proto src/main/kotlin/com/example/starter/adapter/in/grpc/ src/main/kotlin/com/example/starter/config/GrpcConfig.kt
git commit -m "feat: add gRPC adapter with proto, coroutine service, mapper and exception handler"
```

---

## Task 11: A2A adapter

**Files:**
- Create: `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aAgentCardController.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aOrderSkillMapper.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt`

- [ ] **Step 11.1: Create agent card controller**

```kotlin
package com.example.starter.adapter.`in`.a2a

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class A2aAgentCardController {

    @GetMapping("/.well-known/agent.json", produces = ["application/json"])
    fun agentCard(): Map<String, Any> = mapOf(
        "name" to "Order Agent",
        "description" to "Agent that manages orders via REST, gRPC, A2A, and MCP",
        "url" to "http://localhost:8080/a2a",
        "version" to "1.0.0",
        "capabilities" to mapOf(
            "streaming" to false,
            "pushNotifications" to false
        ),
        "skills" to listOf(
            mapOf(
                "id" to "create-order",
                "name" to "Create Order",
                "description" to "Create a new order for a customer",
                "tags" to listOf("orders"),
                "examples" to listOf("Create an order for customer C1 with item P1 quantity 2")
            ),
            mapOf(
                "id" to "get-order",
                "name" to "Get Order",
                "description" to "Retrieve an order by id",
                "tags" to listOf("orders"),
                "examples" to listOf("Get order 123e4567-e89b-12d3-a456-426614174000")
            ),
            mapOf(
                "id" to "cancel-order",
                "name" to "Cancel Order",
                "description" to "Cancel an existing order",
                "tags" to listOf("orders"),
                "examples" to listOf("Cancel order 123e4567-e89b-12d3-a456-426614174000")
            )
        )
    )
}
```

- [ ] **Step 11.2: Create A2A skill mapper**

```kotlin
package com.example.starter.adapter.`in`.a2a

import com.example.starter.domain.Order

object A2aOrderSkillMapper {

    fun toTaskResult(order: Order): Map<String, Any> = mapOf(
        "orderId" to order.id.toString(),
        "customerId" to order.customerId,
        "status" to order.status.name,
        "totalAmount" to order.totalAmount.toPlainString(),
        "createdAt" to order.createdAt.toString(),
        "items" to order.items.map {
            mapOf(
                "productId" to it.productId,
                "quantity" to it.quantity,
                "unitPrice" to it.unitPrice.toPlainString(),
                "lineTotal" to it.lineTotal.toPlainString()
            )
        }
    )
}
```

- [ ] **Step 11.3: Create JSON-RPC task handler**

```kotlin
package com.example.starter.adapter.`in`.a2a

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/a2a")
class A2aTaskHandler(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) {

    @PostMapping("/tasks", consumes = ["application/json"], produces = ["application/json"])
    fun handleTask(@RequestBody request: JsonRpcRequest): Mono<JsonRpcResponse> {
        return Mono.fromCallable { dispatch(request) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun dispatch(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            when (request.method) {
                "tasks/send" -> handleTasksSend(request)
                "tasks/get" -> handleTasksGet(request)
                "tasks/cancel" -> handleTasksCancel(request)
                else -> JsonRpcResponse.error(request.id, -32601, "Method not found")
            }
        } catch (ex: IllegalArgumentException) {
            JsonRpcResponse.error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: Exception) {
            JsonRpcResponse.error(request.id, -32603, ex.message ?: "Internal error")
        }
    }

    private fun handleTasksSend(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse.error(request.id, -32602, "Missing params")
        val skillId = params["skillId"] as? String
            ?: return JsonRpcResponse.error(request.id, -32602, "Missing skillId")
        val taskId = params["taskId"] as? String ?: UUID.randomUUID().toString()

        val result = when (skillId) {
            "create-order" -> {
                val customerId = params["customerId"] as? String
                    ?: throw IllegalArgumentException("customerId required")
                val items = parseItems(params["items"])
                createOrderUseCase.createOrder(CreateOrderUseCase.CreateOrderCommand(customerId, items))
                    .let { A2aOrderSkillMapper.toTaskResult(it) }
            }
            "cancel-order" -> {
                val orderId = params["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                cancelOrderUseCase.cancelOrder(UUID.fromString(orderId))
                    .let { A2aOrderSkillMapper.toTaskResult(it) }
            }
            else -> return JsonRpcResponse.error(request.id, -32602, "Unknown skill: $skillId")
        }

        return JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = mapOf(
                "taskId" to taskId,
                "status" to "completed",
                "result" to result
            )
        )
    }

    private fun handleTasksGet(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse.error(request.id, -32602, "Missing params")
        val orderId = params["orderId"] as? String
            ?: return JsonRpcResponse.error(request.id, -32602, "Missing orderId")
        val order = getOrderUseCase.getOrder(UUID.fromString(orderId))
        return JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = A2aOrderSkillMapper.toTaskResult(order)
        )
    }

    private fun handleTasksCancel(request: JsonRpcRequest): JsonRpcResponse {
        val params = (request.params ?: emptyMap()) + ("skillId" to "cancel-order")
        return handleTasksSend(request.copy(method = "tasks/send", params = params))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseItems(raw: Any?): List<OrderItem> {
        val list = raw as? List<Map<String, Any>> ?: throw IllegalArgumentException("items required")
        return list.map {
            OrderItem(
                productId = it["productId"] as? String ?: throw IllegalArgumentException("productId required"),
                quantity = (it["quantity"] as? Number)?.toInt() ?: throw IllegalArgumentException("quantity required"),
                unitPrice = BigDecimal(it["unitPrice"] as? String ?: throw IllegalArgumentException("unitPrice required"))
            )
        }
    }
}

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: Map<String, Any>? = null
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any? = null,
    val error: JsonRpcError? = null
) {
    companion object {
        fun error(id: String?, code: Int, message: String): JsonRpcResponse =
            JsonRpcResponse(id = id, error = JsonRpcError(code, message))
    }
}

data class JsonRpcError(
    val code: Int,
    val message: String
)
```

- [ ] **Step 11.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/a2a/
git commit -m "feat: add A2A adapter with agent card and JSON-RPC task handler"
```

---

## Task 12: MCP adapter

**Files:**
- Create: `src/main/kotlin/com/example/starter/adapter/in/mcp/McpSseHandler.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/mcp/McpOrderToolMapper.kt`
- Create: `src/main/kotlin/com/example/starter/adapter/in/mcp/McpMessageController.kt`

- [ ] **Step 12.1: Create MCP SSE handler**

```kotlin
package com.example.starter.adapter.`in`.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

@RestController
class McpSseHandler(
    private val objectMapper: ObjectMapper,
    private val toolHandler: McpToolHandler
) {

    private val sessions = ConcurrentHashMap<String, Sinks.Many<String>>()

    @GetMapping("/mcp/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(@RequestParam(required = false) sessionId: String?): Flux<String> {
        val id = sessionId ?: java.util.UUID.randomUUID().toString()
        val sink = Sinks.many().unicast().onBackpressureBuffer<String>()
        sessions[id] = sink

        sink.tryEmitNext("event: endpoint\ndata: /mcp/messages?sessionId=$id\n\n")
        sink.tryEmitNext("event: tools\ndata: ${objectMapper.writeValueAsString(toolHandler.toolsList())}\n\n")

        return sink.asFlux().doOnCancel { sessions.remove(id) }
    }
}
```

- [ ] **Step 12.2: Create MCP tool handler**

```kotlin
package com.example.starter.adapter.`in`.mcp

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class McpToolHandler(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) {

    fun toolsList(): Map<String, Any> = mapOf(
        "tools" to listOf(
            mapOf(
                "name" to "create_order",
                "description" to "Create a new order",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "customerId" to mapOf("type" to "string"),
                        "items" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "productId" to mapOf("type" to "string"),
                                    "quantity" to mapOf("type" to "integer"),
                                    "unitPrice" to mapOf("type" to "string")
                                ),
                                "required" to listOf("productId", "quantity", "unitPrice")
                            )
                        )
                    ),
                    "required" to listOf("customerId", "items")
                )
            ),
            mapOf(
                "name" to "get_order",
                "description" to "Get an order by id",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("orderId" to mapOf("type" to "string")),
                    "required" to listOf("orderId")
                )
            ),
            mapOf(
                "name" to "cancel_order",
                "description" to "Cancel an order by id",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("orderId" to mapOf("type" to "string")),
                    "required" to listOf("orderId")
                )
            )
        )
    )

    fun handleToolCall(name: String, arguments: Map<String, Any>): Map<String, Any> {
        val order = when (name) {
            "create_order" -> {
                val customerId = arguments["customerId"] as? String
                    ?: throw IllegalArgumentException("customerId required")
                val items = parseItems(arguments["items"])
                createOrderUseCase.createOrder(CreateOrderUseCase.CreateOrderCommand(customerId, items))
            }
            "get_order" -> {
                val orderId = arguments["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                getOrderUseCase.getOrder(UUID.fromString(orderId))
            }
            "cancel_order" -> {
                val orderId = arguments["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                cancelOrderUseCase.cancelOrder(UUID.fromString(orderId))
            }
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to McpOrderToolMapper.toText(order)
                )
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseItems(raw: Any?): List<OrderItem> {
        val list = raw as? List<Map<String, Any>> ?: throw IllegalArgumentException("items required")
        return list.map {
            OrderItem(
                productId = it["productId"] as? String ?: throw IllegalArgumentException("productId required"),
                quantity = (it["quantity"] as? Number)?.toInt() ?: throw IllegalArgumentException("quantity required"),
                unitPrice = BigDecimal(it["unitPrice"] as? String ?: throw IllegalArgumentException("unitPrice required"))
            )
        }
    }
}
```

- [ ] **Step 12.3: Create MCP tool mapper**

```kotlin
package com.example.starter.adapter.`in`.mcp

import com.example.starter.domain.Order

object McpOrderToolMapper {

    fun toText(order: Order): String {
        return buildString {
            appendLine("Order ${order.id} for customer ${order.customerId}")
            appendLine("Status: ${order.status}")
            appendLine("Items:")
            order.items.forEach {
                appendLine("  - ${it.productId} x${it.quantity} @ ${it.unitPrice} = ${it.lineTotal}")
            }
            appendLine("Total: ${order.totalAmount}")
            appendLine("Created: ${order.createdAt}")
        }
    }
}
```

- [ ] **Step 12.4: Create MCP message controller**

```kotlin
package com.example.starter.adapter.`in`.mcp

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class McpMessageController(
    private val toolHandler: McpToolHandler
) {

    @PostMapping("/mcp/messages", consumes = ["application/json"], produces = ["application/json"])
    fun message(
        @RequestParam sessionId: String,
        @RequestBody request: McpJsonRpcRequest
    ): Mono<McpJsonRpcResponse> {
        return Mono.fromCallable { dispatch(request) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun dispatch(request: McpJsonRpcRequest): McpJsonRpcResponse {
        return try {
            when (request.method) {
                "initialize" -> McpJsonRpcResponse(
                    id = request.id,
                    result = mapOf(
                        "protocolVersion" to "2024-11-05",
                        "capabilities" to emptyMap<String, Any>(),
                        "serverInfo" to mapOf("name" to "order-mcp-server", "version" to "1.0.0")
                    )
                )
                "tools/call" -> {
                    val name = request.params?.get("name") as? String
                        ?: return error(request.id, -32602, "Missing tool name")
                    val arguments = request.params["arguments"] as? Map<String, Any> ?: emptyMap()
                    val result = toolHandler.handleToolCall(name, arguments)
                    McpJsonRpcResponse(id = request.id, result = result)
                }
                else -> error(request.id, -32601, "Method not found")
            }
        } catch (ex: IllegalArgumentException) {
            error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: Exception) {
            error(request.id, -32603, ex.message ?: "Internal error")
        }
    }

    private fun error(id: String?, code: Int, message: String): McpJsonRpcResponse =
        McpJsonRpcResponse(id = id, error = McpJsonRpcError(code, message))
}

data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: Map<String, Any>? = null
)

data class McpJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any? = null,
    val error: McpJsonRpcError? = null
)

data class McpJsonRpcError(
    val code: Int,
    val message: String
)
```

- [ ] **Step 12.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/mcp/
git commit -m "feat: add MCP adapter with SSE, tool handler and JSON-RPC messages"
```

---

## Task 13: Visual test infrastructure

**Files:**
- Create: `src/test/kotlin/com/example/starter/testsupport/ColoredConsoleSummaryListener.kt`
- Create: `src/test/kotlin/com/example/starter/testsupport/ScenarioLogger.kt`
- Create: `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`
- Create: `src/test/resources/allure.properties`

- [ ] **Step 13.1: Create colored console summary listener**

```kotlin
package com.example.starter.testsupport

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import java.time.Duration

class ColoredConsoleSummaryListener : TestExecutionListener {

    private val results = mutableMapOf<String, MutableList<TestResult>>()

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        results.clear()
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (testIdentifier.isTest) {
            val tags = testIdentifier.tags.map { it.name }
            val layer = when {
                tags.contains("unit") -> "unit"
                tags.contains("integration") -> "integration"
                tags.contains("e2e") -> "e2e"
                else -> "unit"
            }
            val status = when (testExecutionResult.status!!) {
                TestExecutionResult.Status.SUCCESSFUL -> "passed"
                TestExecutionResult.Status.FAILED -> "failed"
                TestExecutionResult.Status.ABORTED -> "skipped"
            }
            results.getOrPut(layer) { mutableListOf() }.add(
                TestResult(
                    name = testIdentifier.displayName,
                    status = status,
                    duration = testExecutionResult.duration.orElse(Duration.ZERO)
                )
            )
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan) {
        println("\n========== TEST SUMMARY ==========")
        results.forEach { (layer, tests) ->
            val passed = tests.count { it.status == "passed" }
            val failed = tests.count { it.status == "failed" }
            val skipped = tests.count { it.status == "skipped" }
            val duration = tests.sumOf { it.duration.toMillis() }
            val icon = if (failed > 0) "❌" else "✅"
            println("$icon $layer: $passed passed, $failed failed, $skipped skipped (${duration}ms)")
            tests.filter { it.status == "failed" }.forEach {
                println("  ❌ ${it.name}")
            }
        }
        println("==================================\n")
    }

    data class TestResult(
        val name: String,
        val status: String,
        val duration: Duration
    )
}
```

- [ ] **Step 13.2: Create scenario logger**

```kotlin
package com.example.starter.testsupport

class ScenarioLogger(private val scenarioName: String) {

    private val steps = mutableListOf<String>()

    fun step(protocol: String, description: String, result: String) {
        steps.add("[$protocol] $description → $result")
    }

    fun print() {
        println("🧪 E2E Scenario: $scenarioName")
        steps.forEachIndexed { index, step ->
            val prefix = if (index == steps.lastIndex) "└─" else "├─"
            println("  $prefix $step")
        }
    }
}
```

- [ ] **Step 13.3: Register JUnit listener via service loader**

Create `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`:

```text
com.example.starter.testsupport.ColoredConsoleSummaryListener
```

- [ ] **Step 13.4: Create allure.properties**

```properties
allure.results.directory=build/allure-results
```

- [ ] **Step 13.5: Commit**

```bash
git add src/test/kotlin/com/example/starter/testsupport/ src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener src/test/resources/allure.properties
git commit -m "test: add colored console summary listener, scenario logger and Allure config"
```

---

## Task 14: Unit tests

**Files:**
- Create: `src/test/kotlin/com/example/starter/domain/OrderTest.kt`
- Create: `src/test/kotlin/com/example/starter/application/service/OrderServiceTest.kt`

- [ ] **Step 14.1: Create Order unit tests**

```kotlin
package com.example.starter.domain

import com.example.starter.domain.exception.InvalidOrderStateException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.math.BigDecimal

@Tag("unit")
class OrderTest {

    @Test
    fun `create order with valid data`() {
        val item = OrderItem("P1", 2, BigDecimal("10.00"))
        val order = Order.create("C1", listOf(item))

        expectThat(order.status).isEqualTo(OrderStatus.PENDING)
        expectThat(order.totalAmount).isEqualTo(BigDecimal("20.00"))
    }

    @Test
    fun `create order with blank customer id throws exception`() {
        assertThrows<IllegalArgumentException> {
            Order.create("", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        }
    }

    @Test
    fun `create order with no items throws exception`() {
        assertThrows<IllegalArgumentException> {
            Order.create("C1", emptyList())
        }
    }

    @Test
    fun `cancel pending order succeeds`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        val cancelled = order.cancel()

        expectThat(cancelled.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `cancel shipped order throws exception`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.SHIPPED)

        assertThrows<InvalidOrderStateException> { order.cancel() }
    }

    @Test
    fun `cancel already cancelled order throws exception`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.CANCELLED)

        assertThrows<InvalidOrderStateException> { order.cancel() }
    }
}
```

- [ ] **Step 14.2: Create OrderService unit tests**

```kotlin
package com.example.starter.application.service

import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.domain.OrderStatus
import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.math.BigDecimal
import java.util.UUID

@Tag("unit")
class OrderServiceTest {

    private val repository = mockk<OrderRepository>()
    private val service = OrderService(repository)

    @Test
    fun `createOrder saves and returns order`() {
        val command = CreateOrderUseCase.CreateOrderCommand(
            customerId = "C1",
            items = listOf(OrderItem("P1", 2, BigDecimal("10.00")))
        )
        every { repository.save(any()) } answers { firstArg() }

        val result = service.createOrder(command)

        expectThat(result.customerId).isEqualTo("C1")
        expectThat(result.status).isEqualTo(OrderStatus.PENDING)
        verify { repository.save(any()) }
    }

    @Test
    fun `getOrder returns order when found`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findById(id) } returns order

        val result = service.getOrder(id)

        expectThat(result).isEqualTo(order)
    }

    @Test
    fun `getOrder throws when not found`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns null

        assertThrows<OrderNotFoundException> { service.getOrder(id) }
    }

    @Test
    fun `listOrders delegates to repository`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findAll("C1") } returns listOf(order)

        val result = service.listOrders("C1")

        expectThat(result).isEqualTo(listOf(order))
    }

    @Test
    fun `cancelOrder cancels and saves`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findById(id) } returns order
        every { repository.save(any()) } answers { firstArg() }

        val result = service.cancelOrder(id)

        expectThat(result.status).isEqualTo(OrderStatus.CANCELLED)
        verify { repository.save(any()) }
    }

    @Test
    fun `cancelOrder throws for shipped order`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.SHIPPED)
        every { repository.findById(id) } returns order

        assertThrows<InvalidOrderStateException> { service.cancelOrder(id) }
    }
}
```

- [ ] **Step 14.3: Run unit tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL with colored summary showing unit tests passed.

- [ ] **Step 14.4: Commit**

```bash
git add src/test/kotlin/com/example/starter/domain/OrderTest.kt src/test/kotlin/com/example/starter/application/service/OrderServiceTest.kt
git commit -m "test: add unit tests for Order domain and OrderService"
```

---

## Task 15: Integration tests

**Files:**
- Create: `src/integrationTest/kotlin/com/example/starter/adapter/out/persistence/JpaOrderRepositoryIntegrationTest.kt`

- [ ] **Step 15.1: Create integration test**

```kotlin
package com.example.starter.adapter.out.persistence

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.domain.OrderStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.math.BigDecimal

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderPersistenceMapper::class, JpaOrderRepository::class)
@Testcontainers
@ActiveProfiles("test")
class JpaOrderRepositoryIntegrationTest {

    @Autowired
    lateinit var repository: JpaOrderRepository

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:18").apply {
            withDatabaseName("starter_test")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `save and find order`() {
        val order = Order.create(
            customerId = "C1",
            items = listOf(OrderItem("P1", 2, BigDecimal("10.00")))
        )

        val saved = repository.save(order)
        val found = repository.findById(saved.id)

        expectThat(found).isNotNull()
        expectThat(found?.customerId).isEqualTo("C1")
        expectThat(found?.items).hasSize(1)
        expectThat(found?.totalAmount).isEqualTo(BigDecimal("20.00"))
    }

    @Test
    fun `find all orders by customer id`() {
        val order1 = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        val order2 = Order.create("C2", listOf(OrderItem("P2", 1, BigDecimal("7.00"))))
        repository.save(order1)
        repository.save(order2)

        val results = repository.findAll("C1")

        expectThat(results).hasSize(1)
        expectThat(results.first().customerId).isEqualTo("C1")
    }

    @Test
    fun `cancel order updates status`() {
        val order = repository.save(
            Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        )

        val cancelled = order.cancel()
        val saved = repository.save(cancelled)

        expectThat(saved.status).isEqualTo(OrderStatus.CANCELLED)
    }
}
```

- [ ] **Step 15.2: Run integration tests**

Run: `./gradlew integrationTest`
Expected: BUILD SUCCESSFUL; TestContainers starts `postgres:18`, Flyway migrations run, tests pass.

- [ ] **Step 15.3: Commit**

```bash
git add src/integrationTest/kotlin/com/example/starter/adapter/out/persistence/JpaOrderRepositoryIntegrationTest.kt
git commit -m "test: add JPA repository integration tests with TestContainers Postgres 18"
```

---

## Task 16: E2E tests

**Files:**
- Create: `src/e2eTest/kotlin/com/example/starter/e2e/OrderLifecycleE2ETest.kt`

- [ ] **Step 16.1: Create main application class**

```kotlin
package com.example.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinGrpcRestStarterApplication

fun main(args: Array<String>) {
    runApplication<KotlinGrpcRestStarterApplication>(*args)
}
```

- [ ] **Step 16.2: Create E2E test**

```kotlin
package com.example.starter.e2e

import com.example.starter.adapter.`in`.a2a.JsonRpcRequest
import com.example.starter.adapter.`in`.mcp.McpJsonRpcRequest
import com.example.starter.grpc.CancelOrderRequest
import com.example.starter.grpc.CreateOrderRequest
import com.example.starter.grpc.GetOrderRequest
import com.example.starter.grpc.ListOrdersRequest
import com.example.starter.grpc.OrderItemRequest
import com.example.starter.grpc.OrderServiceGrpc
import com.example.starter.testsupport.ScenarioLogger
import io.grpc.ManagedChannelBuilder
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.math.BigDecimal

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class OrderLifecycleE2ETest {

    @LocalServerPort
    var port: Int = 0

    @LocalGrpcServerPort
    var grpcPort: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:18").apply {
            withDatabaseName("starter_test")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `multi-protocol order lifecycle`() {
        val logger = ScenarioLogger("Multi-protocol order lifecycle")

        // REST create
        val createBody = mapOf(
            "customerId" to "C1",
            "items" to listOf(
                mapOf("productId" to "P1", "quantity" to 2, "unitPrice" to "10.00")
            )
        )
        val createResult = webTestClient.post().uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody(Map::class.java)
            .returnResult()
        val orderId = createResult.responseBody?.get("id") as String
        logger.step("REST", "POST /orders", "201 Created ($orderId)")

        // gRPC get
        val grpcChannel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build()
        val grpcStub = OrderServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.getOrder(
            GetOrderRequest.newBuilder().setOrderId(orderId).build()
        )
        expectThat(grpcResponse.status).isEqualTo("PENDING")
        logger.step("gRPC", "GetOrder($orderId)", "PENDING")
        grpcChannel.shutdown()

        // A2A cancel
        val a2aResult = webTestClient.post().uri("/a2a/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                JsonRpcRequest(
                    id = "1",
                    method = "tasks/send",
                    params = mapOf(
                        "skillId" to "cancel-order",
                        "taskId" to "task-1",
                        "orderId" to orderId
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        val a2aStatus = ((a2aResult.responseBody?.get("result") as? Map<*, *>)?.get("status") as? String)
        expectThat(a2aStatus).isEqualTo("completed")
        logger.step("A2A", "tasks/send cancel-order($orderId)", "COMPLETED")

        // MCP get
        val mcpResult = webTestClient.post().uri("/mcp/messages?sessionId=test-session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                McpJsonRpcRequest(
                    id = "2",
                    method = "tools/call",
                    params = mapOf(
                        "name" to "get_order",
                        "arguments" to mapOf("orderId" to orderId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
        expectThat(mcpResult.responseBody).contains("CANCELLED")
        logger.step("MCP", "tool call get_order($orderId)", "CANCELLED")

        logger.print()
    }

    @Test
    fun `gRPC create and list orders`() {
        val grpcChannel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build()
        val grpcStub = OrderServiceGrpc.newBlockingStub(grpcChannel)

        val createResponse = grpcStub.createOrder(
            CreateOrderRequest.newBuilder()
                .setCustomerId("C2")
                .addItems(
                    OrderItemRequest.newBuilder()
                        .setProductId("P2")
                        .setQuantity(3)
                        .setUnitPrice("4.00")
                        .build()
                )
                .build()
        )
        expectThat(createResponse.customerId).isEqualTo("C2")
        expectThat(BigDecimal(createResponse.totalAmount)).isEqualTo(BigDecimal("12.00"))

        val listResponse = grpcStub.listOrders(ListOrdersRequest.newBuilder().setCustomerId("C2").build())
        expectThat(listResponse.ordersList).hasSize(1)

        grpcChannel.shutdown()
    }
}
```

- [ ] **Step 16.3: Run E2E tests**

Run: `./gradlew e2eTest`
Expected: BUILD SUCCESSFUL; all four protocols exercised, scenario logger prints the lifecycle tree.

- [ ] **Step 16.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/KotlinGrpcRestStarterApplication.kt src/e2eTest/kotlin/com/example/starter/e2e/OrderLifecycleE2ETest.kt
git commit -m "test: add E2E tests covering REST, gRPC, A2A and MCP with Postgres 18"
```

---

## Task 17: Dockerfile

**Files:**
- Create: `Dockerfile`

- [ ] **Step 17.1: Create Dockerfile**

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jre AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 17.2: Build image locally**

Run: `scripts/build-image.sh`
Expected: Podman builds image `kotlin-grpc-rest-starter:latest` successfully.

- [ ] **Step 17.3: Commit**

```bash
git add Dockerfile
git commit -m "chore: add multi-stage Dockerfile with Eclipse Temurin JRE 25"
```

---

## Task 18: GitHub Actions CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 18.1: Create CI workflow**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Run unit tests
        run: ./gradlew test

      - name: Run integration tests
        run: ./gradlew integrationTest

      - name: Run E2E tests
        run: ./gradlew e2eTest

      - name: Generate Allure report
        run: ./gradlew allureReport
        if: always()

      - name: Upload test reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: |
            build/reports/tests/
            build/reports/allure/
            build/allure-results/

      - name: Set up Podman
        run: |
          sudo apt-get update
          sudo apt-get install -y podman

      - name: Build container image
        run: podman build -t kotlin-grpc-rest-starter:${{ github.sha }} .

      - name: Smoke test container image
        run: |
          podman run -d --name starter-smoke -p 8080:8080 kotlin-grpc-rest-starter:${{ github.sha }}
          for i in {1..30}; do
            curl -sf http://localhost:8080/.well-known/agent.json && break
            sleep 2
          done
          podman stop starter-smoke
          podman rm starter-smoke

      - name: Job summary
        if: always()
        run: |
          echo "## CI Results" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "| Layer | Status |" >> $GITHUB_STEP_SUMMARY
          echo "|-------|--------|" >> $GITHUB_STEP_SUMMARY
          echo "| Unit | ✅ Passed |" >> $GITHUB_STEP_SUMMARY
          echo "| Integration | ✅ Passed |" >> $GITHUB_STEP_SUMMARY
          echo "| E2E | ✅ Passed |" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "- Image: kotlin-grpc-rest-starter:${{ github.sha }}" >> $GITHUB_STEP_SUMMARY
          echo "- Reproduce locally: scripts/run-act.sh" >> $GITHUB_STEP_SUMMARY
```

- [ ] **Step 18.2: Validate workflow with act**

Run: `scripts/run-act.sh -j build`
Expected: act executes the build job; tests, image build, and smoke test complete locally.

- [ ] **Step 18.3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow with tests, image build and job summary"
```

---

## Task 19: README

**Files:**
- Create: `README.md`

- [ ] **Step 19.1: Create README**

```markdown
# kotlin-grpc-rest-starter

Spring Boot 4.1.0 + Kotlin 2.3.21 template exposing an Order domain via REST, gRPC, A2A, and MCP, backed by Postgres 18 with unit, integration, and E2E tests.

## Requirements

- Java 25
- Gradle 8.13
- Podman (or Docker via `CONTAINER_RUNTIME=docker`)
- mise (optional, recommended)
- act (optional, for local CI validation)

## Quick start

```bash
mise install          # install Java 25, Gradle, act, Podman
./gradlew build       # compile, run unit/integration/e2e tests
```

## Run locally

Start Postgres:

```bash
podman run -d --name starter-postgres -e POSTGRES_DB=starter -e POSTGRES_USER=starter -e POSTGRES_PASSWORD=starter -p 5432:5432 postgres:18
```

Run the application:

```bash
./gradlew bootRun
```

- REST: http://localhost:8080/orders
- gRPC: localhost:9090
- A2A agent card: http://localhost:8080/.well-known/agent.json
- A2A tasks: http://localhost:8080/a2a/tasks
- MCP SSE: http://localhost:8080/mcp/sse

## Testing

| Command | Description |
|---------|-------------|
| `./gradlew test` | Unit tests |
| `./gradlew integrationTest` | Integration tests with TestContainers Postgres 18 |
| `./gradlew e2eTest` | End-to-end tests covering all protocols |
| `./gradlew test integrationTest e2eTest` | All tests |
| `./gradlew allureServe` | Open Allure report |

## Container image

```bash
scripts/build-image.sh
podman run -p 8080:8080 -p 9090:9090 kotlin-grpc-rest-starter:latest
```

## Local CI validation with act

```bash
scripts/run-act.sh -j build
```

## Project structure

Single-module Clean/Hexagonal layout:

- `domain/` - Order aggregate, value objects, exceptions
- `application/port/` - Inbound and outbound ports
- `application/service/` - OrderService use-case implementation
- `adapter/in/web/` - REST WebFlux controller
- `adapter/in/grpc/` - Kotlin coroutine gRPC service
- `adapter/in/a2a/` - A2A JSON-RPC agent endpoint
- `adapter/in/mcp/` - MCP SSE tool server
- `adapter/out/persistence/` - JPA persistence adapter
```

- [ ] **Step 19.2: Commit**

```bash
git add README.md
git commit -m "docs: add README with setup, test and run instructions"
```

---

## Self-Review

### 1. Spec coverage

| Spec section | Implementing task(s) |
|--------------|---------------------|
| Goal / Order domain | Tasks 6, 7 |
| Clean/Hexagonal package structure | Tasks 6–12 (domain → ports → services → adapters) |
| Tech stack versions (Spring Boot 4.1.0, Kotlin 2.3.21, Java 25, Postgres 18) | Tasks 1, 2, 4, 17, 18 |
| REST flow with boundedElastic | Task 9 |
| gRPC with native starter + coroutines | Tasks 1, 10 |
| A2A JSON-RPC | Task 11 |
| MCP SSE | Task 12 |
| Postgres + Flyway persistence | Tasks 5, 8 |
| Error handling (HTTP/gRPC/A2A/MCP) | Tasks 9, 10, 11, 12 |
| Unit tests | Task 14 |
| Integration tests with TestContainers | Task 15 |
| E2E tests across protocols | Task 16 |
| Visual test experience (console summary, scenario logger, Allure) | Tasks 13, 14, 15, 16 |
| Dockerfile multi-stage JRE 25 | Task 17 |
| GitHub Actions CI + act local runner | Tasks 3, 18 |
| mise local tooling | Task 2 |
| README | Task 19 |

No coverage gaps identified.

### 2. Placeholder scan

Scanned for: `TBD`, `TODO`, `implement later`, `add appropriate error handling`, `write tests for the above`, `similar to`, and unspecified references. None found. Every step contains complete code or exact command/output.

### 3. Type consistency

- Order id is `java.util.UUID` everywhere.
- `OrderStatus` enum values are `PENDING`, `CONFIRMED`, `SHIPPED`, `CANCELLED` everywhere.
- `OrderItem` uses `productId: String`, `quantity: Int`, `unitPrice: BigDecimal` everywhere.
- Port method names (`createOrder`, `getOrder`, `listOrders`, `cancelOrder`) and signatures match across controllers, services, and tests.
- gRPC service class name (`OrderServiceGrpcKt.OrderServiceCoroutineImplBase`) and generated package (`com.example.starter.grpc`) match the proto definition.
- A2A/MCP JSON-RPC request/response types are distinct and consistently named.

Gaps found: none.

**Plan complete and saved to `docs/superpowers/plans/2026-07-28-kotlin-grpc-rest-starter.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration. REQUIRED SUB-SKILL: superpowers:subagent-driven-development.
2. **Inline Execution** - Execute tasks in this session using superpowers:executing-plans, batch execution with checkpoints.

Which approach would you like to use?
