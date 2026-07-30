# kotlin-grpc-rest-starter

Spring Boot 4.1.0 + Kotlin 2.3.21 template exposing an Order domain via REST, gRPC, A2A, and MCP, backed by PostgreSQL 18 with unit, integration, and E2E tests.

## Requirements

- Java 25
- Gradle 9.1.0
- Podman (or Docker via `CONTAINER_RUNTIME=docker`)
- mise (optional, recommended)
- act (optional, for local CI validation)

## Quick start

```bash
mise install                              # install Java 25, Gradle, act, Podman
./gradlew test integrationTest e2eTest    # compile and run all tests
```

## Run locally

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

### Endpoints

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

## Testing

| Command | Description |
|---------|-------------|
| `./gradlew test` | Unit tests |
| `./gradlew integrationTest` | Integration tests with TestContainers PostgreSQL 18 |
| `./gradlew e2eTest` | End-to-end tests covering all protocols |
| `./gradlew test integrationTest e2eTest` | All tests |
| `./gradlew allureServe` | Open Allure report |

## Container image

```bash
scripts/build-image.sh
podman run -p 8080:8080 -p 9090:9090 \
  -e DB_HOST=host.containers.internal \
  kotlin-grpc-rest-starter:latest
```

## Local CI validation with act

```bash
scripts/run-act.sh -j build
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
- `config/` — Spring configuration (`DatabaseConfig`, `GrpcConfig`)
