# standard-tools-kotlin

Spring Boot 4.1.0 + Kotlin 2.3.21 template exposing an Order domain via REST, gRPC, A2A, and MCP, backed by PostgreSQL 18 with unit, integration, and E2E tests. Also includes a GraalVM native image build path.

## Requirements

- Java 25
- Gradle 9.1.0
- Podman (or Docker via `CONTAINER_RUNTIME=docker`)
- mise (optional, recommended)
- act (optional, for local CI validation)

## Quick start with mise

```bash
mise install                              # install Java 25, Gradle, act, Podman
mise run test-all                         # compile and run unit, integration, and E2E tests
mise run build                            # build the JVM bootJar
mise run build-native                     # build the GraalVM native executable
```

Available tasks are defined in `.mise.toml`:

| Task | Command |
|------|---------|
| `build` | `./gradlew bootJar` |
| `build-native` | `./gradlew nativeCompile` |
| `run` | `./gradlew bootRun` |
| `test` | `./gradlew test` |
| `test-integration` | `./gradlew integrationTest` |
| `test-e2e` | `./gradlew e2eTest` |
| `test-all` | `./gradlew test integrationTest e2eTest` |
| `test-visual` | `./gradlew e2eTest --info \| ./scripts/visual-test-report.sh` |
| `dependency-updates` | `./gradlew dependencyUpdates` |
| `act-ci` | `./scripts/run-act-local.sh -j build-jvm` |
| `compose-up` | `podman-compose up -d --build` |
| `compose-down` | `podman-compose down` |

## Run locally

### With Podman Compose

Start the JVM stack (app + PostgreSQL):

```bash
podman-compose -f docker-compose.yml up -d --build
```

Start the native image stack:

```bash
podman-compose -f docker-compose.native.yml up -d --build
```

Stop either stack:

```bash
podman-compose -f docker-compose.yml down
```

### Manually with a local database

Start PostgreSQL:

```bash
podman run -d --name starter-postgres \
  -e POSTGRES_DB=starter \
  -e POSTGRES_USER=starter \
  -e POSTGRES_PASSWORD=starter \
  -p 5432:5432 postgres:18
```

Run the application:

```bash
./gradlew bootRun
```

The datasource can be customized with environment variables:

| Variable | Default |
|----------|---------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `starter` |
| `DB_USER` | `starter` |
| `DB_PASS` | `starter` |
| `STANDARD_TOOLS_AUDIT_ENABLED` | `true` |

## Endpoints

- **REST**
  - `POST /orders` — create an order
  - `GET /orders` — list orders (optional `customerId` query param)
  - `GET /orders/{id}` — get an order
  - `POST /orders/{id}/cancel` — cancel an order
- **gRPC** — `localhost:9090`
- **A2A agent card** — `http://localhost:8080/.well-known/agent.json`
- **A2A tasks** — `POST http://localhost:8080/a2a/tasks`
- **MCP SSE** — `http://localhost:8080/mcp/sse`
- **MCP messages** — `POST http://localhost:8080/mcp/messages?sessionId={sessionId}`

### Agent tools / A2A / MCP / audit

- **Agent tools list** — `GET http://localhost:8080/api/v1/agent/tools`
  - Returns OpenAI-style function definitions for the registered tool catalog.
- **Agent dispatch** — `POST http://localhost:8080/api/v1/agent/dispatch`
  - Runs a tool by name (e.g. `get_option_pricing`, `marketdata_fetch`, `backtest_single`).
- **A2A tasks** — `POST http://localhost:8080/a2a/tasks`
  - JSON-RPC endpoint for A2A `tasks/send` requests.
- **MCP SSE** — `http://localhost:8080/mcp/sse`
  - Server-Sent Events stream for Model Context Protocol sessions.
- **MCP messages** — `POST http://localhost:8080/mcp/messages?sessionId={sessionId}`
  - JSON-RPC message endpoint for MCP tool calls.
- **Audit records** — `GET http://localhost:8080/api/v1/audit/records`
- **Audit verify** — `POST http://localhost:8080/api/v1/audit/verify`
- **Audit replay** — `POST http://localhost:8080/api/v1/audit/replay/{requestId}`

Audit records are written on every agent-tool dispatch. The `STANDARD_TOOLS_AUDIT_ENABLED` variable in `.mise.toml` is reserved for future use and is not currently consumed by the application.

### Example requests

Create an order via REST:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C1","items":[{"productId":"P1","quantity":2,"unitPrice":"10.00"}]}'
```

Cancel an order via A2A:

```bash
curl -X POST http://localhost:8080/a2a/tasks \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tasks/send","params":{"skillId":"cancel-order","orderId":"<order-id>"}}'
```

List agent tools:

```bash
curl http://localhost:8080/api/v1/agent/tools
```

Dispatch an agent tool:

```bash
curl -X POST http://localhost:8080/api/v1/agent/dispatch \
  -H "Content-Type: application/json" \
  -d '{"tool":"get_option_pricing","arguments":{"spot":100,"strike":100,"timeToExpiry":1,"riskFreeRate":0.05,"volatility":0.2,"optionType":"call"}}'
```

## Testing

| Command | Description |
|---------|-------------|
| `./gradlew test` | Unit tests |
| `./gradlew integrationTest` | Integration tests with TestContainers PostgreSQL 18 |
| `./gradlew e2eTest` | End-to-end tests covering all protocols |
| `./gradlew test integrationTest e2eTest` | All tests |
| `./gradlew allureServe` | Open Allure report |

E2E tests use `ScenarioLogger` to print a visual scenario tree to the console:

```bash
./gradlew e2eTest --info | ./scripts/visual-test-report.sh
```

## Container images

### Classic JVM image

```bash
scripts/build-image.sh
podman run -p 8080:8080 -p 9090:9090 \
  -e DB_HOST=host.containers.internal \
  standard-tools-kotlin:latest
```

### Native image

Build the native executable:

```bash
./gradlew nativeCompile
```

Build and run the native container:

```bash
podman build -f Dockerfile.native -t standard-tools-kotlin:native .
podman run -p 8080:8080 -p 9090:9090 \
  -e DB_HOST=host.containers.internal \
  standard-tools-kotlin:native
```

Native image AOT hints are registered in `src/main/kotlin/com/example/starter/config/NativeImageHints.kt`, covering gRPC service classes, proto-generated classes, JPA entities, and the Jackson Kotlin module.

> **Note:** `nativeCompile` requires a GraalVM JDK in the toolchain. If your local toolchain cannot resolve GraalVM, the Docker-based build in `Dockerfile.native` uses `ghcr.io/graalvm/native-image-community:25`. The `build-native` CI job validates the native build path.

## Local CI validation with act

Run the full CI pipeline or a single job locally using Podman:

```bash
mise run act-ci
# or
scripts/run-act-local.sh -j unit-tests
scripts/run-act-local.sh -j integration-tests
scripts/run-act-local.sh -j e2e-tests
scripts/run-act-local.sh -j build-native
```

## Project structure

Single-module Clean/Hexagonal layout:

- `domain/` — Order aggregate, value objects, exceptions
- `application/port/` — Inbound and outbound ports
- `application/service/` — OrderService use-case implementation
- `adapter/in/web/` — REST WebFlux controller
- `adapter/in/grpc/` — Kotlin coroutine gRPC service
- `adapter/in/a2a/` — A2A JSON-RPC agent endpoint
- `adapter/in/mcp/` — MCP SSE tool server
- `adapter/out/persistence/` — JPA persistence adapter
- `config/` — Spring configuration (`DatabaseConfig`, `GrpcConfig`, `NativeImageHints`)
- `scripts/` — Local helpers (`build-image.sh`, `run-act.sh`, `run-act-local.sh`, `visual-test-report.sh`)
