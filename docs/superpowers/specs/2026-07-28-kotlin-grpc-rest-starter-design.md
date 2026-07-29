# kotlin-grpc-rest-starter — Design Spec

## 1. Goal
Create a new, self-contained Spring Boot Kotlin template repository that exposes a small **Order management** domain through both **REST** and **gRPC** endpoints, backed by **Postgres**, and demonstrates battle-tested testing at unit, integration, and e2e levels.

## 2. Decisions made during brainstorming
| Decision | Choice |
|----------|--------|
| Repo name | `kotlin-grpc-rest-starter` |
| Sample domain | Order / book management |
| Persistence | Postgres + TestContainers |
| REST stack | Spring Boot WebFlux (reactive) |
| gRPC style | `grpc-spring-boot-starter` with Kotlin coroutines |
| E2E approach | Spring Boot Test + TestContainers |
| DevOps | GitHub Actions CI + Dockerfile + nektos/act local runner |
| Project structure | Single-module Clean/Hexagonal packages |

## 3. Architecture
A single Gradle module with Clean/Hexagonal package boundaries:

```
src/main/kotlin/com/example/starter/
├── domain/
│   ├── Order.kt
│   ├── OrderItem.kt
│   ├── OrderStatus.kt
│   └── exception/
│       ├── OrderNotFoundException.kt
│       └── InvalidOrderStateException.kt
├── application/port/
│   ├── inbound/
│   │   ├── CreateOrderUseCase.kt
│   │   ├── GetOrderUseCase.kt
│   │   └── CancelOrderUseCase.kt
│   └── outbound/
│       └── OrderRepository.kt
├── application/service/
│   └── OrderService.kt
├── adapter/in/web/
│   ├── OrderController.kt
│   └── OrderDto.kt
├── adapter/in/grpc/
│   ├── GrpcOrderService.kt
│   └── GrpcOrderMapper.kt
├── adapter/out/persistence/
│   ├── R2dbcOrderRepository.kt
│   ├── OrderEntity.kt
│   └── OrderPersistenceMapper.kt
└── config/
    ├── DatabaseConfig.kt
    └── GrpcConfig.kt
```

## 4. Technology Stack
- **Spring Boot** 3.3+
- **Kotlin** 2.0
- **Gradle Kotlin DSL** with `gradle/libs.versions.toml` version catalog
- **Spring WebFlux** + **Spring Data R2DBC** (reactive end-to-end)
- **grpc-spring-boot-starter** with Kotlin coroutine service stubs
- **Postgres** 16 + **Flyway** migrations
- **TestContainers** for Postgres in integration and e2e tests
- **JUnit 5** + **MockK** + **Strikt** for assertions
- **GitHub Actions** CI workflow
- **Dockerfile** (multi-stage, Eclipse Temurin JRE 21)

## 5. Domain & Data Flow
Domain entity: `Order` with `id`, `customerId`, `items`, `status`, `createdAt`.
Supported operations:
- `createOrder(customerId, items)` → `Order`
- `getOrder(id)` → `Order`
- `listOrders(customerId?)` → `List<Order>`
- `cancelOrder(id)` → `Order`

`OrderStatus`: `PENDING`, `CONFIRMED`, `SHIPPED`, `CANCELLED`.

### 5.1 REST flow
`POST /orders` → `OrderController` → `CreateOrderUseCase` → `OrderService` → `OrderRepository` port → `R2dbcOrderRepository` → Postgres.

### 5.2 gRPC flow
`CreateOrder` RPC → `GrpcOrderService` (coroutine) → same use case → same repository adapter → Postgres.

## 6. Error Handling
- Domain exceptions are thrown in domain/use-case layers:
  - `OrderNotFoundException` → HTTP 404 / gRPC `NOT_FOUND`
  - `InvalidOrderStateException` → HTTP 409 / gRPC `FAILED_PRECONDITION`
  - Invalid input → HTTP 400 / gRPC `INVALID_ARGUMENT`
- WebFlux: global `@ControllerAdvice` maps domain exceptions to `ProblemDetail` responses.
- gRPC: a `CoroutineExceptionInterceptor` maps throwables to the appropriate `Status`.

## 7. Testing Strategy
| Level | Scope | Tools | Notes |
|-------|-------|-------|-------|
| Unit | Domain rules + use cases | JUnit 5, MockK, Strikt | No Spring context; ports mocked |
| Integration | R2DBC repository adapter + Flyway migrations | `@DataR2dbcTest`, TestContainers Postgres | Real DB, no HTTP/gRPC |
| E2E | Full app via REST and gRPC | `@SpringBootTest`, TestContainers Postgres, `WebTestClient`, in-process gRPC channel | Verifies both entry points end-to-end |

## 8. CI/CD & Containerization
- `.github/workflows/ci.yml`: Gradle build, run all test suites, build Docker image, run smoke test against the image.
- `Dockerfile`: multi-stage build compiling with Gradle, then copying the layered JAR into an Eclipse Temurin JRE 21 image.
- **Local CI validation with [nektos/act](https://github.com/nektos/act)**: include an `.actrc` or documented command so the GitHub Actions workflow can be run locally before pushing. The workflow should be act-compatible (e.g., no secrets required for the basic build/test path, explicit runner images).

## 9. Out of Scope
- Authentication / authorization
- Pagination and advanced query parameters
- Metrics / observability tooling
- Kubernetes manifests
- API documentation generation (OpenAPI) — can be added later

## 10. Success Criteria
- `./gradlew build` passes all unit, integration, and e2e tests.
- REST endpoints respond correctly via `WebTestClient`.
- gRPC endpoints respond correctly via an in-process channel.
- Postgres migrations run successfully with TestContainers.
- Docker image builds and starts without errors.
- `act` can run `.github/workflows/ci.yml` locally without requiring repository secrets.
