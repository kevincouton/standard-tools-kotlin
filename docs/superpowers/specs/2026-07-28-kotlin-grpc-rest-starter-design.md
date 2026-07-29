# kotlin-grpc-rest-starter — Design Spec

## 1. Goal
Create a new, self-contained Spring Boot Kotlin template repository that exposes a small **Order management** domain through both **REST** and **gRPC** endpoints, backed by **Postgres**, and demonstrates battle-tested testing at unit, integration, and e2e levels.

## 2. Decisions made during brainstorming
| Decision | Choice |
|----------|--------|
| Repo name | `kotlin-grpc-rest-starter` |
| Sample domain | Order / book management |
| Persistence | Postgres + Spring Data JPA + TestContainers |
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
├── adapter/in/a2a/
│   ├── A2aAgentCardController.kt
│   ├── A2aTaskHandler.kt
│   └── A2aOrderSkillMapper.kt
├── adapter/in/mcp/
│   ├── McpSseHandler.kt
│   ├── McpToolHandler.kt
│   └── McpOrderToolMapper.kt
├── adapter/out/persistence/
│   ├── JpaOrderRepository.kt
│   ├── OrderEntity.kt
│   └── OrderPersistenceMapper.kt
└── config/
    ├── DatabaseConfig.kt
    └── GrpcConfig.kt
```

## 4. Technology Stack
All libraries use the **latest stable compatible versions** as of the implementation date, pinned in `gradle/libs.versions.toml`. Local tooling is managed by **[mise](https://mise.jdx.dev/)** and the container runtime is **[Podman](https://podman.io/)** (Docker-compatible where needed).
- **mise** for local tool management (Java, Gradle, Node if needed, act, etc.)
- **Spring Boot** 3.4+ (latest stable)
- **Kotlin** 2.1+ (latest stable, compatible with Spring Boot)
- **Gradle Kotlin DSL** with `gradle/libs.versions.toml` version catalog
- **Spring WebFlux** + **Spring Data JPA** (blocking persistence bridged to reactive via `Schedulers.boundedElastic` / `Dispatchers.IO`)
- **grpc-spring-boot-starter** with Kotlin coroutine service stubs (latest stable compatible with Spring Boot)
- **A2A (Agent-to-Agent)** JSON-RPC 2.0 agent endpoint (`/.well-known/agent.json`, `/a2a/tasks`)
- **MCP (Model Context Protocol)** SSE endpoint (`/mcp/sse`) exposing tools
- **Postgres** 17 + **Flyway** 10+ migrations
- **TestContainers** 1.20+ for Postgres in integration and e2e tests
- **JUnit 5** + **MockK** + **Strikt** for assertions
- **GitHub Actions** CI workflow
- **Dockerfile** (multi-stage, Eclipse Temurin JRE 21)

**Version policy:** versions are pinned explicitly in the catalog. A Gradle dependency-updates task (e.g., `com.github.ben-manes.versions`) is included so the template can be checked for newer compatible versions easily.

## 4.1 Local Development Environment
- **mise**: `.mise.toml` pins Java, Gradle, and any other CLI tools required to build/test the project. Contributors run `mise install` to get the exact toolchain.
- **Podman**: all containerized workflows (image build, TestContainers, `act`, smoke tests) target Podman. Scripts and docs default to `podman` commands; a `DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` configuration is provided so TestContainers and `act` use the local Podman socket.
- **act**: configured to use Podman as the container runtime (e.g., `act --container-daemon-socket ...` or `.actrc`) so the GitHub Actions workflow can be validated locally without Docker Desktop.

## 5. Domain & Data Flow
Domain entity: `Order` with `id`, `customerId`, `items`, `status`, `createdAt`.
Supported operations:
- `createOrder(customerId, items)` → `Order`
- `getOrder(id)` → `Order`
- `listOrders(customerId?)` → `List<Order>`
- `cancelOrder(id)` → `Order`

`OrderStatus`: `PENDING`, `CONFIRMED`, `SHIPPED`, `CANCELLED`.

### 5.1 REST flow
`POST /orders` → `OrderController` → `CreateOrderUseCase` → `OrderService` → `OrderRepository` port → `JpaOrderRepository` (wrapped in `Mono.fromCallable` / `boundedElastic`) → Postgres.

### 5.2 gRPC flow
`CreateOrder` RPC → `GrpcOrderService` (coroutine) → same use case → same repository adapter (wrapped in `withContext(Dispatchers.IO)`) → Postgres.

### 5.3 A2A flow
Agent Card served at `/.well-known/agent.json`. JSON-RPC 2.0 task endpoint at `/a2a/tasks` supporting `tasks/send`, `tasks/get`, and `tasks/cancel`. Each A2A skill maps to a use case (e.g., "create-order" → `CreateOrderUseCase`).

### 5.4 MCP flow
Server-Sent Events (SSE) endpoint at `/mcp/sse` with JSON-RPC session management. Exposes tools such as `create_order`, `get_order`, and `cancel_order`, backed by the same use cases.

## 6. Error Handling
- Domain exceptions are thrown in domain/use-case layers:
  - `OrderNotFoundException` → HTTP 404 / gRPC `NOT_FOUND`
  - `InvalidOrderStateException` → HTTP 409 / gRPC `FAILED_PRECONDITION`
  - Invalid input → HTTP 400 / gRPC `INVALID_ARGUMENT`
- WebFlux: global `@ControllerAdvice` maps domain exceptions to `ProblemDetail` responses.
- gRPC: a `CoroutineExceptionInterceptor` maps throwables to the appropriate `Status`.
- A2A: JSON-RPC error objects with standard codes (-32602 invalid params, -32603 internal error, application-specific codes for domain errors).
- MCP: JSON-RPC error responses returned through the SSE channel with appropriate error codes.

## 7. Testing Strategy
| Level | Scope | Tools | Notes |
|-------|-------|-------|-------|
| Unit | Domain rules + use cases | JUnit 5, MockK, Strikt | No Spring context; ports mocked |
| Integration | JPA repository adapter + Flyway migrations | `@DataJpaTest`, TestContainers Postgres | Real DB, no HTTP/gRPC |
| E2E | Full app via REST, gRPC, A2A, and MCP | `@SpringBootTest`, TestContainers Postgres, `WebTestClient`, in-process gRPC channel, A2A JSON-RPC client, MCP SSE client | Verifies all entry points end-to-end |

## 8. CI/CD & Containerization
- `.github/workflows/ci.yml`: Gradle build, run all test suites, build container image, run smoke test against the image.
- `Dockerfile`: multi-stage build compiling with Gradle, then copying the layered JAR into an Eclipse Temurin JRE 21 image.
- **Local CI validation with [nektos/act](https://github.com/nektos/act)**: include an `.actrc` and documented command so the GitHub Actions workflow can be run locally before pushing. The workflow is act-compatible and configured to run with Podman as the container runtime.
- **Podman-first scripts**: build and smoke-test scripts use `podman` by default; `docker` is supported via aliases or environment variables.
- **TestContainers Podman support**: configuration sets the Docker socket and ryuk settings so TestContainers starts Postgres through Podman.

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
- A2A endpoints return valid JSON-RPC responses and the agent card.
- MCP SSE endpoint accepts tool calls and returns correct results.
- Postgres migrations run successfully with TestContainers.
- Docker image builds and starts without errors.
- `act` can run `.github/workflows/ci.yml` locally with Podman without requiring repository secrets.
- `mise install` provides the exact Java/Gradle toolchain.
- `podman build -t kotlin-grpc-rest-starter .` produces a working image.
