# Kotlin Standard-Tools Port — Design Spec

> **Goal:** Port the functionality of `../Repo/Standard-Tools` (`standard_quant_tools`) into the `kotlin-grpc-rest-starter` Spring Boot 4.1 + Kotlin project, preserving the existing Clean/Hexagonal architecture and exposing every capability through REST, gRPC, A2A, and MCP.

## 1. Context

### 1.1 Source project: Standard-Tools

`standard_quant_tools` is a Python 3.10+ quantitative finance library built around:

- **Data providers:** yfinance, Bloomberg Desktop API, Polygon.io REST
- **Technical indicators:** trend, momentum, volatility, volume
- **Risk/return metrics:** Sharpe, Sortino, max drawdown, VaR/CVaR, Calmar, etc.
- **Analysis:** regression, cointegration, Hurst exponent, PCA, correlation, multi-factor, options pricing/Greeks
- **Backtesting:** vectorized strategies, portfolio simulation, pair trading, walk-forward, robustness, Monte Carlo
- **Portfolio construction:** mean-variance, risk parity, Black-Litterman
- **Screening:** fundamental + technical filters
- **Agent tools:** 42 LLM-callable Pydantic-based tools
- **Audit trail:** append-only, hash-chained decision records with optional Ed25519 signing and CLI (`sqt`)
- **Performance:** optional C++17/pybind11 extension + Numba fallback

### 1.2 Target project: kotlin-grpc-rest-starter

A Spring Boot 4.1.0 + Kotlin 2.3.21 template with:

- Clean/Hexagonal single-module layout
- Domain-driven `Order` aggregate exposed via REST, gRPC, A2A, MCP
- Postgres 18 + Flyway persistence
- TestContainers-based integration/E2E tests
- Visual test reporting (colored console summary, scenario logger, Allure)
- mise/Podman/act local tooling and GitHub Actions CI

## 2. Porting Principles

1. **Idiomatic Kotlin re-design, not literal translation.** We keep behavioral parity with Standard-Tools where it matters, but re-express concepts in Spring Boot / JVM idioms: immutable value objects, domain services, outbound/inbound ports, reactive adapters.
2. **Subdomain-per-package.** Each major capability from Standard-Tools becomes a bounded subdomain with its own `domain`, `application/port`, `application/service`, and adapters.
3. **Protocol-agnostic core.** Every subdomain's use cases are exposed uniformly through REST, gRPC, A2A, and MCP by adding thin adapters, not by changing core logic.
4. **JVM-first performance.** Hot paths use JVM-optimized Kotlin and established Java/Kotlin math libraries. Native acceleration is explicitly out of scope for the first version.
5. **Test parity.** Each subdomain ships unit, integration, and E2E tests following the existing visual reporting style.
6. **Audit by default.** Every quant operation that mutates state or produces a decision records a tamper-evident `DecisionRecord`.

## 3. Subdomain Decomposition

The port adds these subdomains under `com.example.starter`:

| Subdomain | Responsibility | Maps to Standard-Tools |
|-----------|----------------|------------------------|
| `shared` | Common value objects, providers, cache, time-series utilities, exceptions | `data/base.py`, `data/metadata.py`, `_retry.py`, shared math |
| `marketdata` | OHLCV bars, price series, ticker info, data-provider abstraction, caching | `data/`, `data/quality.py` |
| `indicators` | Technical indicators (trend, momentum, volatility, volume) | `indicators/` |
| `metrics` | Risk/return metrics and diagnostics | `metrics/` |
| `analysis` | Regression, cointegration, Hurst, PCA, correlation, multi-factor, options | `analysis/` |
| `backtest` | Vectorized backtesting, strategies, portfolio simulation, pairs, walk-forward | `backtest/` |
| `portfolio` | Portfolio construction and optimization | `portfolio/` |
| `screener` | Stock screening with fundamental + technical filters | `screener/` |
| `agenttools` | LLM-callable tool dispatch, input/output schemas, routing | `agent/tools.py`, `agent/models.py` |
| `audit` | Decision records, hash chain, verification, replay, CLI | `audit/` |

Each subdomain follows the existing package structure:

```
com.example.starter.<subdomain>/
├── domain/
├── application/
│   ├── port/inbound/
│   ├── port/outbound/
│   └── service/
└── adapter/
    ├── in/web/          # REST controllers + DTOs
    ├── in/grpc/         # gRPC services + mappers
    ├── in/a2a/          # A2A JSON-RPC handlers
    ├── in/mcp/          # MCP tool handlers
    └── out/...          # JPA, REST client, cache adapters
```

## 4. Domain Layer Design

### 4.1 Shared domain values

```kotlin
package com.example.starter.shared.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class Ticker(val symbol: String, val exchange: String? = null)

data class OHLCV(
    val ticker: Ticker,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long
)

typealias PriceSeries = List<OHLCV>
```

### 4.2 Per-subdomain aggregates

| Subdomain | Key aggregates / value objects |
|-----------|-------------------------------|
| `marketdata` | `OHLCV`, `PriceSeries`, `TickerInfo`, `FinancialRatios`, `DataQualityReport` |
| `indicators` | `IndicatorResult`, `SignalSeries`, `IndicatorParameters` |
| `metrics` | `ReturnMetrics`, `RiskMetrics`, `DrawdownPeriod`, `TradeDiagnostics` |
| `analysis` | `RegressionResult`, `CointegrationResult`, `HurstResult`, `PcaResult`, `OptionGreeks` |
| `backtest` | `BacktestInput`, `BacktestResult`, `Strategy`, `Trade`, `EquityCurve` |
| `portfolio` | `Portfolio`, `AssetWeight`, `OptimizationConstraint` |
| `screener` | `ScreenCriteria`, `ScreenResult` |
| `agenttools` | `ToolCall`, `ToolResult`, `ToolDefinition` |
| `audit` | `DecisionRecord`, `AuditChain`, `AuditCheckpoint` |

### 4.3 Domain services

Pure Kotlin functions/classes with no framework dependencies:

- `IndicatorCalculator` — SMA, EMA, RSI, MACD, Bollinger Bands, etc.
- `ReturnCalculator`, `RiskCalculator` — CAGR, volatility, Sharpe, Sortino, max drawdown
- `BacktestEngine` — vectorized signal → trade → equity curve
- `PortfolioOptimizer` — mean-variance, risk parity, Black-Litterman
- `AuditHasher` — hash-chain computation

## 5. Application Layer Design

### 5.1 Inbound ports (use cases)

One or more per subdomain:

```kotlin
// marketdata
interface FetchMarketDataUseCase {
    fun fetch(request: FetchMarketDataCommand): PriceSeries
}

// indicators
interface CalculateIndicatorUseCase {
    fun calculate(command: CalculateIndicatorCommand): IndicatorResult
}

// backtest
interface RunBacktestUseCase {
    fun run(command: RunBacktestCommand): BacktestResult
}

// portfolio
interface OptimizePortfolioUseCase {
    fun optimize(command: OptimizePortfolioCommand): Portfolio
}

// agenttools
interface DispatchAgentToolUseCase {
    fun dispatch(call: ToolCall): ToolResult
}

// audit
interface RecordDecisionUseCase {
    fun record(record: DecisionRecord): DecisionRecord
}
```

### 5.2 Outbound ports

```kotlin
// marketdata
interface MarketDataProvider {
    fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries
}
interface MarketDataCache {
    fun get(key: CacheKey): PriceSeries?
    fun put(key: CacheKey, series: PriceSeries, ttl: Duration)
}

// audit
interface AuditRepository {
    fun save(record: DecisionRecord): DecisionRecord
    fun findById(id: UUID): DecisionRecord?
    fun findChain(): List<DecisionRecord>
}
```

### 5.3 Application services

- Implement inbound ports.
- Orchestrate domain services and outbound ports.
- Use `@Transactional` when writing to Postgres.
- Convert cross-subdomain calls through inbound ports (e.g., backtest service calls `FetchMarketDataUseCase` rather than reaching directly into `marketdata` adapter).

## 6. Adapter Layer Design

### 6.1 Outbound adapters

| Port | Adapters |
|------|----------|
| `MarketDataProvider` | `YFinanceMarketDataAdapter`, `PolygonMarketDataAdapter`, `BloombergMarketDataAdapter` |
| `MarketDataCache` | `CaffeineMarketDataCacheAdapter` |
| `AuditRepository` | `JpaAuditRepository` |
| `ReferenceDataRepository` | `JpaReferenceDataRepository` (ticker metadata, fundamentals) |

Provider selection is configured via `application.yml`:

```yaml
standard-tools:
  market-data:
    default-provider: yfinance
    providers:
      yfinance:
        enabled: true
        cache-ttl: 1h
      polygon:
        enabled: false
        api-key: ${POLYGON_API_KEY}
      bloomberg:
        enabled: false
```

### 6.2 Inbound adapters

Each subdomain exposes the same use cases through four protocols:

| Protocol | Adapter | Example path |
|----------|---------|--------------|
| REST | `BacktestController` | `POST /api/v1/backtest` |
| gRPC | `BacktestGrpcService` | `BacktestService/RunBacktest` |
| A2A | `BacktestA2aHandler` | `POST /a2a/tasks` with `skillId=run-backtest` |
| MCP | `BacktestMcpHandler` | `POST /mcp/messages` with `tools/call` |

The existing `/.well-known/agent.json` and `/mcp/sse` endpoints are extended to include all new tools/skills.

## 7. Data Providers and Cache

### 7.1 Provider abstraction

`MarketDataProvider` is the outbound port. Implementations:

- **yfinance:** HTTP client to Yahoo Finance endpoints; parses CSV/JSON into `PriceSeries`.
- **Polygon.io:** REST client (`/v2/aggs/ticker/{ticker}/range/...`).
- **Bloomberg:** Thin wrapper around `blpapi` (optional dependency, enabled via profile).

### 7.2 Caching

- `CaffeineMarketDataCacheAdapter` for in-memory TTL caching of `PriceSeries`.
- Cache key: `provider:ticker:interval:dateRange`.
- No persistent OHLCV store in Postgres for the first version; add TimescaleDB later if needed.

### 7.3 Data quality

`DataQualityReport` domain service checks:

- Missing bars
- Stale prices
- Price jumps / outliers
- Survivorship bias warnings (metadata-driven)

## 8. Protocol Exposure Details

### 8.1 REST

- Base path: `/api/v1/{subdomain}`
- Request/response DTOs per subdomain under `adapter/in/web/`
- `GlobalExceptionHandler` extended to map `QuantError` hierarchy to `ProblemDetail`

### 8.2 gRPC

- One proto file per subdomain under `src/main/proto/{subdomain}/`
- Kotlin coroutine service implementations extend generated `*CoroutineImplBase`
- Exception handler maps domain errors to gRPC `Status` codes

### 8.3 A2A

- Agent card at `/.well-known/agent.json` lists all skills per subdomain.
- `A2aTaskHandler` dispatches `tasks/send` to the appropriate subdomain service.
- Skill IDs follow pattern `{subdomain}-{action}`, e.g., `marketdata-fetch`, `backtest-run`, `portfolio-optimize`.

### 8.4 MCP

- `/mcp/sse` advertises all tools.
- `/mcp/messages` handles `tools/call` for each subdomain.
- Tool names follow pattern `{subdomain}_{action}`, e.g., `marketdata_fetch`, `backtest_run`.

## 9. Audit Trail

The audit subdomain is a first-class citizen:

- `DecisionRecord` aggregate: id, timestamp, operation, input hash, output hash, previous record hash, signature (optional).
- `AuditWriter` domain service computes hash chain.
- `RecordDecisionUseCase` invoked by other application services after every mutating/decision operation.
- `JpaAuditRepository` persists records append-only.
- Verification endpoint: `POST /api/v1/audit/verify`.
- Optional CLI exposed as a Gradle task or shell script.

## 10. Persistence Strategy

### 10.1 Postgres

- Reference data: `tickers`, `financial_ratios`
- Audit: `decision_records`
- Configuration: `provider_configs`, `screen_criteria`
- No OHLCV time-series tables in v1.

### 10.2 Migrations

Flyway migrations under `src/main/resources/db/migration/`:

- `V2__create_marketdata_tables.sql`
- `V3__create_audit_tables.sql`
- `V4__create_reference_data_tables.sql`
- etc.

## 11. Performance Strategy

- Hot paths in pure Kotlin with `DoubleArray` / `List<Double>` where precision allows.
- Libraries:
  - `org.apache.commons:commons-math3` for linear algebra, optimization, statistics
  - `tech.tablesaw:tablesaw-core` or `joinery` for dataframe-style operations
  - `org.nield:kotlin-statistics` for descriptive stats
- Lazy evaluation and coroutine `Flow` for large series.
- Caching for provider calls.
- No C++/JNI/Kotlin/Native acceleration in v1.
- **Dual build artifacts:** The project produces both a classic JVM image and a GraalVM native image.
  - **Classic JVM:** Default, highest compatibility, easier debugging, used for development and CI.
  - **Native image:** Optimized startup and memory footprint, built in Phase 10 using Spring AOT + GraalVM Native Image. Requires explicit reflection/proxy hints for gRPC, JPA entities, provider adapters, and protobuf generated classes.

## 12. Testing Strategy

Each phase follows the existing pattern:

| Layer | Scope | Tech |
|-------|-------|------|
| Unit | Pure domain + application service with mocked ports | JUnit 5, MockK, Strikt |
| Integration | Outbound adapters (Postgres, cache, mock HTTP providers) | TestContainers, WireMock |
| E2E | Full `@SpringBootTest` exercising REST → gRPC → A2A → MCP | WebTestClient, gRPC stubs |

Synthetic OHLCV fixtures live in `src/test/kotlin/com/example/starter/testsupport/fixtures/`.

## 13. Phasing Plan

1. **Phase 1 — Shared + Market data + cache:** `shared` value objects, `MarketDataProvider` port, yfinance/Polygon/Bloomberg adapters, Caffeine cache, REST/gRPC/A2A/MCP handlers.
2. **Phase 2 — Indicators + metrics:** technical indicators, return/risk metrics, diagnostics.
3. **Phase 3 — Analysis:** regression, cointegration, Hurst, PCA, correlation, multi-factor, options pricing/Greeks.
4. **Phase 4 — Backtesting engine:** strategies, portfolio simulation, pair trading, signal panel, walk-forward, robustness, Monte Carlo.
5. **Phase 5 — Portfolio optimization:** mean-variance, risk parity, Black-Litterman.
6. **Phase 6 — Screener:** sync + async screening, fundamental + technical filters.
7. **Phase 7 — Agent tools:** tool definitions, dispatch, Pydantic-equivalent input/output models, all 42 tools mapped to subdomain use cases.
8. **Phase 8 — Audit trail:** `DecisionRecord`, hash chain, JPA repository, verify/replay endpoints, CLI.
9. **Phase 9 — Cross-cutting:** Docker/CI updates, README, end-to-end performance smoke tests, documentation.
10. **Phase 10 — Native image build:** Add GraalVM Native Image support, Spring AOT hints for gRPC/JPA/providers, `Dockerfile.native`, and a CI job that builds both the classic JVM image and the native image.

## 14. File Structure (target)

```
src/main/kotlin/com/example/starter/
├── KotlinGrpcRestStarterApplication.kt
├── order/                          # existing
├── shared/
│   ├── domain/
│   ├── application/
│   └── adapter/out/
├── marketdata/
│   ├── domain/
│   ├── application/port/inbound/
│   ├── application/port/outbound/
│   ├── application/service/
│   └── adapter/{in/web,grpc,a2a,mcp,out/...}
├── indicators/
├── metrics/
├── analysis/
├── backtest/
├── portfolio/
├── screener/
├── agenttools/
└── audit/

src/main/proto/
├── order_service.proto             # existing
├── marketdata/
├── indicators/
├── metrics/
├── analysis/
├── backtest/
├── portfolio/
├── screener/
├── agenttools/
└── audit/

src/test/kotlin/com/example/starter/testsupport/
├── fixtures/                       # synthetic OHLCV, returns, equity curves
├── PostgresTestContainer.kt
├── ColoredConsoleSummaryListener.kt
└── ScenarioLogger.kt
```

Build artifacts:

```
Dockerfile              # classic JVM image via eclipse-temurin:25-jdk/jre
Dockerfile.native       # GraalVM native image build
scripts/build-image.sh  # default: classic JVM image
scripts/build-native-image.sh
```

## 15. Dependency Additions

Additions to `gradle/libs.versions.toml` and `build.gradle.kts`:

- `commons-math3` — math/stats/optimization
- `tablesaw-core` or `joinery` — dataframe ops
- `kotlin-statistics` — descriptive stats
- `caffeine` — caching
- `jackson-module-kotlin` (already present)
- `okhttp` or `spring-webflux` (WebClient) — HTTP provider clients
- `wiremock` — integration test mocking
- `blpapi` (optional, `bloomberg` profile) — Bloomberg
- `org.graalvm.buildtools.native` Gradle plugin — GraalVM Native Image
- `spring-boot-graalvm` / Spring AOT processing — native hints
- Paketo native buildpacks or GraalVM JDK 25 — native image builder

## 16. Out of Scope (v1)

- C++/Numba/native-code acceleration of numerical algorithms
- Persistent OHLCV time-series database (TimescaleDB/InfluxDB)
- Real-time streaming data/WebSocket providers
- Options backtesting / multi-leg strategies
- Distributed backtesting (Spark/Dask equivalent)
- UI/dashboards
- Authentication/authorization

> **Note:** GraalVM Native Image is in scope as Phase 10, but the native-image smoke tests and AOT hints are added only after the classic JVM image is stable.

## 17. Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Standard-Tools has 19k+ lines of tests; porting all is huge | Domain-driven phases; each phase independently testable; stub where needed |
| yfinance/Polygon rate limits in CI | WireMock fixtures for unit/integration tests; live provider tests marked `@Tag("live")` |
| Bloomberg blpapi hard to test locally | Optional dependency + profile; integration tests skipped if not available |
| JVM numeric performance vs Python/NumPy | Use primitive arrays, cache, coroutines; benchmark before optimizing |
| Agent-tool model explosion | Centralize shared input/output models in `shared` and `agenttools` |
| Audit hash chain correctness | Unit-test chain against known-good hashes; verify endpoint in E2E |
| GraalVM native image build failures | Add AOT hints iteratively; keep native build in separate CI job; fallback to JVM image |
| Long native-image build times | Cache native-image build layers; run native job only on main/release branches |

## 18. Success Criteria

- All 10 phases implemented and committed.
- Each phase has passing unit, integration, and E2E tests with visual summaries.
- Every subdomain exposes at least one endpoint/skill/tool per protocol (REST, gRPC, A2A, MCP).
- `scripts/build-image.sh` (classic JVM) and GitHub Actions CI pass.
- `scripts/build-native-image.sh` (GraalVM native) and its CI job pass.
- README documents all new endpoints, example requests, and both image build options.
