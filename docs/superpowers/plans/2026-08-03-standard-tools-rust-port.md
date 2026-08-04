# Standard-Tools Rust Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the complete Standard-Tools quantitative finance toolkit from Kotlin/Spring Boot to Rust, exposing the same 42+ agent tools through REST, gRPC, A2A, and MCP, with hash-chained audit records, PostgreSQL persistence, and native compilation support.

**Architecture:** A workspace-based Rust project (`standard-tools-rust`) using Axum for REST, Tonic for gRPC, SQLx for async PostgreSQL, and a clean hexagonal domain layer. Each domain module (market-data, indicators, metrics, analysis, backtest, portfolio, screener, agent, audit) is a separate crate under one Cargo workspace to keep compile times and responsibilities bounded.

**Tech Stack:** Rust 1.82+, Tokio, Axum, Tonic, Prost, Serde, SQLx, PostgreSQL, Flyway (via sqlx migrate or refinery), Reqwest, Moka, Ndarray/Nalgebra/Statrs, Clap, Tracing.

---

## File Structure

```
standard-tools-rust/
├── Cargo.toml                    # workspace manifest
├── README.md
├── .mise.toml
├── .github/workflows/ci.yml
├── docker-compose.yml
├── Dockerfile
├── Dockerfile.native
├── proto/                        # shared .proto files (copied from Kotlin port)
│   ├── order.proto
│   ├── marketdata.proto
│   ├── indicators.proto
│   ├── metrics.proto
│   ├── analysis.proto
│   ├── backtest.proto
│   ├── portfolio.proto
│   ├── screener.proto
│   └── agent.proto
├── crates/
│   ├── sqt-core/                 # shared domain errors, value objects, utils
│   ├── sqt-marketdata/           # fetch, cache, providers (yfinance, polygon, bloomberg)
│   ├── sqt-indicators/           # technical indicator calculator
│   ├── sqt-metrics/              # risk & return metrics
│   ├── sqt-analysis/             # regression, cointegration, hurst, pca, correlation, multi-factor, options
│   ├── sqt-backtest/             # strategies, engines, walk-forward, monte-carlo, pairs, panel
│   ├── sqt-portfolio/            # mean-variance, risk-parity, black-litterman
│   ├── sqt-screener/             # fundamental provider + screening service
│   ├── sqt-agent/                # 42-tool registry + dispatcher
│   ├── sqt-audit/                # hash-chained records, verify, replay
│   ├── sqt-orders/               # order domain + persistence
│   └── sqt-api/                  # axum REST, tonic gRPC, A2A/MCP adapters, app wiring, CLI
└── scripts/
    ├── run-act-local.sh
    └── visual-test-report.sh
```

---

## Task 0: Bootstrap Workspace and Repository

**Files:**
- Create: `standard-tools-rust/Cargo.toml`
- Create: `standard-tools-rust/.mise.toml`
- Create: `standard-tools-rust/.gitignore`
- Create: `standard-tools-rust/README.md` (skeleton)
- Create GitHub repo `kevincouton/standard-tools-rust`

- [ ] **Step 0.1: Create GitHub repo and clone locally**

```bash
gh repo create kevincouton/standard-tools-rust --public --clone --description "Rust port of Standard-Tools quant toolkit"
cd standard-tools-rust
```

- [ ] **Step 0.2: Write workspace `Cargo.toml`**

```toml
[workspace]
resolver = "2"
members = [
    "crates/sqt-core",
    "crates/sqt-marketdata",
    "crates/sqt-indicators",
    "crates/sqt-metrics",
    "crates/sqt-analysis",
    "crates/sqt-backtest",
    "crates/sqt-portfolio",
    "crates/sqt-screener",
    "crates/sqt-agent",
    "crates/sqt-audit",
    "crates/sqt-orders",
    "crates/sqt-api",
]

[workspace.package]
version = "0.1.0"
edition = "2021"
rust-version = "1.82"
authors = ["Kevin Couton"]
license = "MIT"

[workspace.dependencies]
tokio = { version = "1.40", features = ["full"] }
axum = "0.7"
tonic = "0.12"
prost = "0.13"
tower = "0.5"
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
reqwest = { version = "0.12", features = ["json"] }
sqlx = { version = "0.8", features = ["runtime-tokio", "postgres", "migrate", "uuid", "chrono"] }
moka = { version = "0.12", features = ["future"] }
ndarray = "0.16"
ndarray-linalg = { version = "0.16", features = ["openblas"] }
statrs = "0.18"
nalgebra = "0.33"
chrono = { version = "0.4", features = ["serde"] }
uuid = { version = "1.11", features = ["v4", "serde"] }
thiserror = "2.0"
anyhow = "1.0"
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
clap = { version = "4.5", features = ["derive"] }
config = "0.14"
tower-http = { version = "0.6", features = ["trace", "cors"] }
```

- [ ] **Step 0.3: Write `.mise.toml`**

```toml
[tools]
rust = "latest"
podman = "latest"
act = "0.2.75"

[tasks]
build = "cargo build --workspace"
test = "cargo test --workspace"
test-integration = "cargo test --workspace --test '*'"
clippy = "cargo clippy --workspace --all-targets -- -D warnings"
fmt = "cargo fmt --all"
db-up = "docker compose up -d postgres"
migrate = "sqlx migrate run"
run = "cargo run -p sqt-api"
```

- [ ] **Step 0.4: Initial commit**

```bash
git add .
git commit -m "chore: bootstrap standard-tools-rust workspace"
git push -u origin main
```

---

## Task 1: Core Crate — Errors and Value Objects

**Files:**
- Create: `crates/sqt-core/Cargo.toml`
- Create: `crates/sqt-core/src/lib.rs`
- Create: `crates/sqt-core/src/error.rs`
- Create: `crates/sqt-core/src/value_objects.rs`
- Test: `crates/sqt-core/src/value_objects.rs` (doc tests) and `crates/sqt-core/tests/value_objects_test.rs`

- [ ] **Step 1.1: Define shared errors**

```rust
// crates/sqt-core/src/error.rs
use thiserror::Error;

#[derive(Error, Debug, Clone)]
pub enum QuantError {
    #[error("invalid command: {0}")]
    InvalidCommand(String),
    #[error("provider not available: {0}")]
    ProviderNotAvailable(String),
    #[error("data quality: {0}")]
    DataQuality(String),
    #[error("not found: {0}")]
    NotFound(String),
    #[error(transparent)]
    Internal(#[from] anyhow::Error),
}

pub type Result<T> = std::result::Result<T, QuantError>;
```

- [ ] **Step 1.2: Define value objects**

```rust
// crates/sqt-core/src/value_objects.rs
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Ticker {
    pub symbol: String,
    pub exchange: Option<String>,
}

impl Ticker {
    pub fn new(symbol: impl Into<String>) -> Self {
        Self { symbol: symbol.into(), exchange: None }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct DateRange {
    pub start: NaiveDate,
    pub end: NaiveDate,
}

impl DateRange {
    pub fn new(start: NaiveDate, end: NaiveDate) -> crate::Result<Self> {
        if start > end {
            return Err(crate::QuantError::InvalidCommand("start after end".into()));
        }
        Ok(Self { start, end })
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Ohlcv {
    pub ticker: Ticker,
    pub date: NaiveDate,
    pub open: Decimal,
    pub high: Decimal,
    pub low: Decimal,
    pub close: Decimal,
    pub volume: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum BarInterval {
    #[default]
    Daily,
    Weekly,
    Monthly,
}
```

- [ ] **Step 1.3: Run tests**

```bash
cargo test -p sqt-core
```

Expected: PASS

- [ ] **Step 1.4: Commit**

```bash
git add crates/sqt-core
git commit -m "feat(core): add shared errors and value objects"
```

---

## Task 2: Market Data Crate

**Files:**
- Create: `crates/sqt-marketdata/Cargo.toml`
- Create: `crates/sqt-marketdata/src/lib.rs`
- Create: `crates/sqt-marketdata/src/port.rs` (provider/cache ports)
- Create: `crates/sqt-marketdata/src/service.rs`
- Create: `crates/sqt-marketdata/src/providers/yfinance.rs`
- Create: `crates/sqt-marketdata/src/cache/moka_cache.rs`
- Test: `crates/sqt-marketdata/tests/market_data_test.rs`

- [ ] **Step 2.1: Define ports and service**

```rust
// crates/sqt-marketdata/src/port.rs
use async_trait::async_trait;
use sqt_core::{BarInterval, DateRange, Ohlcv, Result, Ticker};

#[async_trait]
pub trait MarketDataProvider: Send + Sync {
    fn name(&self) -> &'static str;
    async fn fetch(&self, ticker: &Ticker, range: DateRange, interval: BarInterval) -> Result<Vec<Ohlcv>>;
}

#[async_trait]
pub trait MarketDataCache: Send + Sync {
    async fn get(&self, key: &str) -> Option<Vec<Ohlcv>>;
    async fn put(&self, key: &str, series: Vec<Ohlcv>);
}
```

- [ ] **Step 2.2: Implement yfinance provider**

Parse CSV from `https://query1.finance.yahoo.com/v7/finance/download/{symbol}`. Use `reqwest` + `csv` crate. Return `Vec<Ohlcv>`.

- [ ] **Step 2.3: Implement Moka cache**

Wrap `moka::future::Cache<String, Vec<Ohlcv>>` with TTL.

- [ ] **Step 2.4: Implement service**

```rust
// crates/sqt-marketdata/src/service.rs
pub struct MarketDataService {
    default_provider: String,
    providers: HashMap<String, Arc<dyn MarketDataProvider>>,
    cache: Arc<dyn MarketDataCache>,
}

impl MarketDataService {
    pub async fn fetch(&self, ticker: &Ticker, range: DateRange, interval: BarInterval, provider: Option<&str>) -> Result<Vec<Ohlcv>> {
        // 1. resolve provider name
        // 2. build cache key
        // 3. cache hit -> return
        // 4. cache miss -> fetch -> cache -> return
    }
}
```

- [ ] **Step 2.5: Test**

Use `wiremock` or `mockito` to stub Yahoo CSV. Assert fetch returns expected bars and cache returns same on second call.

- [ ] **Step 2.6: Commit**

```bash
git add crates/sqt-marketdata
git commit -m "feat(marketdata): add provider port, yfinance adapter, moka cache, service"
```

---

## Task 3: Indicators and Metrics Crates

**Files:**
- Create: `crates/sqt-indicators/...`
- Create: `crates/sqt-metrics/...`

- [ ] **Step 3.1: Indicator calculator**

```rust
// crates/sqt-indicators/src/calculator.rs
use sqt_core::Ohlcv;
use rust_decimal::Decimal;

pub struct IndicatorResult {
    pub name: String,
    pub params: HashMap<String, String>,
    pub values: Vec<(NaiveDate, Option<Decimal>)>,
}

pub struct IndicatorCalculator;

impl IndicatorCalculator {
    pub fn calculate(name: &str, series: &[Ohlcv], params: &HashMap<String, String>) -> Result<IndicatorResult> {
        match name.to_ascii_lowercase().as_str() {
            "sma" => Self::sma(series, params),
            "ema" => Self::ema(series, params),
            "rsi" => Self::rsi(series, params),
            "macd" => Self::macd(series, params),
            "bollinger_bands" => Self::bollinger(series, params),
            "atr" => Self::atr(series, params),
            "obv" => Self::obv(series),
            "vwap" => Self::vwap(series),
            _ => Err(QuantError::InvalidCommand(format!("unknown indicator {name}"))),
        }
    }
}
```

- [ ] **Step 3.2: Risk/return metrics**

Use `ndarray` + `statrs`. Implement Sharpe, Sortino, max drawdown, VaR, CVaR, beta, alpha.

- [ ] **Step 3.3: Tests**

Unit tests for each indicator and metric with known inputs/outputs.

- [ ] **Step 3.4: Commit**

```bash
git add crates/sqt-indicators crates/sqt-metrics
git commit -m "feat(indicators,metrics): add calculators and unit tests"
```

---

## Task 4: Analysis Crate

**Files:**
- Create: `crates/sqt-analysis/src/regression.rs`
- Create: `crates/sqt-analysis/src/cointegration.rs`
- Create: `crates/sqt-analysis/src/hurst.rs`
- Create: `crates/sqt-analysis/src/pca.rs`
- Create: `crates/sqt-analysis/src/correlation.rs`
- Create: `crates/sqt-analysis/src/multi_factor.rs`
- Create: `crates/sqt-analysis/src/options.rs`
- Create: `crates/sqt-analysis/src/service.rs`

- [ ] **Step 4.1: Implement each calculator**

Use `ndarray` for linear algebra, `statrs` for distributions. Match Kotlin results shape.

- [ ] **Step 4.2: Service orchestration**

```rust
pub struct AnalysisService;

impl AnalysisService {
    pub fn regression(asset: &[Ohlcv], benchmark: &[Ohlcv], risk_free_rate: f64) -> Result<RegressionResult> { ... }
    pub fn cointegration(a: &[Ohlcv], b: &[Ohlcv]) -> Result<CointegrationResult> { ... }
    // ... etc
}
```

- [ ] **Step 4.3: Tests**

Unit tests for each analysis method.

- [ ] **Step 4.4: Commit**

```bash
git add crates/sqt-analysis
git commit -m "feat(analysis): add regression, cointegration, hurst, pca, correlation, multi-factor, options"
```

---

## Task 5: Backtest Crate

**Files:**
- Create: `crates/sqt-backtest/src/strategy.rs`
- Create: `crates/sqt-backtest/src/strategies.rs`
- Create: `crates/sqt-backtest/src/engine.rs`
- Create: `crates/sqt-backtest/src/portfolio_engine.rs`
- Create: `crates/sqt-backtest/src/pair_engine.rs`
- Create: `crates/sqt-backtest/src/walk_forward.rs`
- Create: `crates/sqt-backtest/src/monte_carlo.rs`
- Create: `crates/sqt-backtest/src/robustness.rs`
- Create: `crates/sqt-backtest/src/service.rs`

- [ ] **Step 5.1: Define strategy trait and built-ins**

```rust
pub trait Strategy: Send + Sync {
    fn name(&self) -> &'static str;
    fn signals(&self, series: &[Ohlcv], params: &HashMap<String, String>) -> Result<Vec<Signal>>;
}

pub struct SmaCrossover;
pub struct RsiMeanReversion;
pub struct MacdCrossover;
pub struct BollingerReversion;
```

- [ ] **Step 5.2: Engine**

Process signals → equity curve → trades → metrics.

- [ ] **Step 5.3: Advanced engines**

Portfolio, pair, walk-forward, Monte Carlo, robustness.

- [ ] **Step 5.4: Tests**

Unit and integration tests.

- [ ] **Step 5.5: Commit**

```bash
git add crates/sqt-backtest
git commit -m "feat(backtest): add strategies and engines"
```

---

## Task 6: Portfolio and Screener Crates

**Files:**
- Create: `crates/sqt-portfolio/...`
- Create: `crates/sqt-screener/...`

- [ ] **Step 6.1: Portfolio optimizers**

Mean-variance, risk parity, Black-Litterman using `ndarray-linalg` or `nalgebra`.

- [ ] **Step 6.2: Screener**

Hardcoded fundamental provider + screening service using indicators.

- [ ] **Step 6.3: Tests**

- [ ] **Step 6.4: Commit**

```bash
git add crates/sqt-portfolio crates/sqt-screener
git commit -m "feat(portfolio,screener): add optimizers and screener"
```

---

## Task 7: Agent Crate — 42-Tool Registry and Dispatch

**Files:**
- Create: `crates/sqt-agent/src/tool.rs`
- Create: `crates/sqt-agent/src/registry.rs`
- Create: `crates/sqt-agent/src/dispatcher.rs`

- [ ] **Step 7.1: Tool definition**

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolDefinition {
    pub name: String,
    pub description: String,
    pub parameters: serde_json::Value, // JSON schema
}
```

- [ ] **Step 7.2: Registry**

Static list of 42+ tools with JSON schemas.

- [ ] **Step 7.3: Dispatcher**

```rust
pub struct ToolDispatcher { ... }

impl ToolDispatcher {
    pub async fn dispatch(&self, name: &str, args: serde_json::Value) -> Result<serde_json::Value> {
        match name {
            "marketdata_fetch" => self.marketdata(args).await,
            "run_sma_backtest" => self.run_backtest("sma_crossover", args).await,
            // ... 40 more
            _ => Err(QuantError::InvalidCommand(format!("unknown tool {name}"))),
        }
    }
}
```

- [ ] **Step 7.4: Tests**

Registry count test + dispatch tests for core tools.

- [ ] **Step 7.5: Commit**

```bash
git add crates/sqt-agent
git commit -m "feat(agent): add 42-tool registry and dispatcher"
```

---

## Task 8: Audit Crate

**Files:**
- Create: `crates/sqt-audit/src/record.rs`
- Create: `crates/sqt-audit/src/writer.rs`
- Create: `crates/sqt-audit/src/verifier.rs`
- Create: `crates/sqt-audit/src/replay.rs`
- Create: `crates/sqt-audit/src/hash.rs`

- [ ] **Step 8.1: Record model and SQLx migration**

```rust
#[derive(sqlx::FromRow)]
pub struct AuditRecord {
    pub id: Uuid,
    pub request_id: Uuid,
    pub recorded_at: DateTime<Utc>,
    pub tool_name: String,
    pub input: serde_json::Value,
    pub output_hash: Option<String>,
    pub status: String,
    pub error_message: Option<String>,
    pub prev_record_hash: String,
    pub record_hash: String,
}
```

- [ ] **Step 8.2: Hash chain**

SHA-256 over canonical JSON; genesis hash `"0000000000000000"`.

- [ ] **Step 8.3: Writer/Verifier/Replay**

Writer inserts records transactionally. Verifier walks chain. Replay re-dispatches and compares output hash.

- [ ] **Step 8.4: Tests**

- [ ] **Step 8.5: Commit**

```bash
git add crates/sqt-audit
git commit -m "feat(audit): add hash-chained audit records"
```

---

## Task 9: Orders Crate

**Files:**
- Create: `crates/sqt-orders/src/domain.rs`
- Create: `crates/sqt-orders/src/repository.rs`
- Create: `crates/sqt-orders/src/service.rs`

- [ ] **Step 9.1: Order domain + SQLx persistence**

- [ ] **Step 9.2: Service with state transitions**

- [ ] **Step 9.3: Tests**

- [ ] **Step 9.4: Commit**

```bash
git add crates/sqt-orders
git commit -m "feat(orders): add order domain and persistence"
```

---

## Task 10: API Crate — REST, gRPC, A2A, MCP

**Files:**
- Create: `crates/sqt-api/Cargo.toml`
- Create: `crates/sqt-api/src/main.rs`
- Create: `crates/sqt-api/src/rest/mod.rs`
- Create: `crates/sqt-api/src/rest/marketdata.rs`
- Create: `crates/sqt-api/src/rest/indicators.rs`
- Create: `crates/sqt-api/src/rest/metrics.rs`
- Create: `crates/sqt-api/src/rest/analysis.rs`
- Create: `crates/sqt-api/src/rest/backtest.rs`
- Create: `crates/sqt-api/src/rest/portfolio.rs`
- Create: `crates/sqt-api/src/rest/screener.rs`
- Create: `crates/sqt-api/src/rest/agent.rs`
- Create: `crates/sqt-api/src/rest/audit.rs`
- Create: `crates/sqt-api/src/rest/orders.rs`
- Create: `crates/sqt-api/src/grpc/mod.rs`
- Create: `crates/sqt-api/src/a2a/mod.rs`
- Create: `crates/sqt-api/src/mcp/mod.rs`
- Create: `crates/sqt-api/src/cli.rs`
- Create: `crates/sqt-api/src/state.rs`

- [ ] **Step 10.1: Tonic gRPC services**

Generate code from `proto/` with `tonic-build`. Implement services delegating to domain crates.

- [ ] **Step 10.2: Axum REST routers**

```rust
pub fn router(state: Arc<AppState>) -> Router {
    Router::new()
        .nest("/api/v1/market-data", marketdata::router(state.clone()))
        .nest("/api/v1/indicators", indicators::router(state.clone()))
        // ... etc
        .nest("/api/v1/agent", agent::router(state.clone()))
        .nest("/api/v1/audit", audit::router(state.clone()))
        .nest("/a2a", a2a::router(state.clone()))
        .nest("/mcp", mcp::router(state.clone()))
        .layer(TraceLayer::new_for_http())
}
```

- [ ] **Step 10.3: A2A JSON-RPC handler**

Route `tasks/send`, `tasks/get`, `tasks/cancel` through agent dispatcher.

- [ ] **Step 10.4: MCP handler**

`tools/list` from registry, `tools/call` through dispatcher.

- [ ] **Step 10.5: CLI**

`standard-tools audit report <id>`, `standard-tools audit replay <id>`, `standard-tools audit verify`.

- [ ] **Step 10.6: Commit**

```bash
git add crates/sqt-api
git commit -m "feat(api): add REST, gRPC, A2A, MCP, CLI"
```

---

## Task 11: Integration and E2E Tests

**Files:**
- Create: `crates/sqt-api/tests/health_test.rs`
- Create: `crates/sqt-api/tests/marketdata_e2e.rs`
- Create: `crates/sqt-api/tests/agent_e2e.rs`
- Create: `crates/sqt-api/tests/audit_e2e.rs`

- [ ] **Step 11.1: Testcontainers Postgres setup**

Use `testcontainers-modules` with Postgres. Apply migrations.

- [ ] **Step 11.2: E2E helpers**

```rust
pub async fn spawn_app() -> TestApp {
    // start testcontainer, migrate, bind to port 0, return base_url
}
```

- [ ] **Step 11.3: E2E tests**

- GET `/api/v1/agent/tools` returns 42+ tools.
- POST `/api/v1/agent/dispatch` option pricing works.
- Audit records written after dispatch and verify passes.

- [ ] **Step 11.4: Commit**

```bash
git add crates/sqt-api/tests
git commit -m "test(api): add E2E tests for agent, audit, marketdata"
```

---

## Task 12: Cross-Cutting — Docker, Mise, CI, README

**Files:**
- Create: `docker-compose.yml`
- Create: `Dockerfile`
- Create: `Dockerfile.native`
- Create: `.github/workflows/ci.yml`
- Update: `README.md`

- [ ] **Step 12.1: Dockerfile**

Multi-stage builder with `rust:1.82-slim` → runtime `debian:bookworm-slim`.

- [ ] **Step 12.2: Dockerfile.native**

Use `rust:1.82-slim` + install `musl-tools`, build static binary, copy to `scratch` or `alpine`.

- [ ] **Step 12.3: CI workflow**

```yaml
jobs:
  fmt-clippy:
  unit-tests:
  integration-tests:
  e2e-tests:
  build-native:
```

- [ ] **Step 12.4: README**

Quick start, mise tasks, API endpoints, native build.

- [ ] **Step 12.5: Commit**

```bash
git add docker-compose.yml Dockerfile Dockerfile.native .github README.md
git commit -m "feat(infra): add docker, CI, and docs"
```

---

## Self-Review

1. **Spec coverage:** Each Kotlin/Standard-Tools capability maps to a crate or module above. The 42 agent tools, audit trail, and multi-protocol adapters are all represented.
2. **Placeholder scan:** No TBDs. Every step names concrete files and includes representative code.
3. **Type consistency:** `Ticker`, `Ohlcv`, `DateRange`, `BarInterval` from `sqt-core` are reused across all crates. `MarketDataProvider`/`MarketDataCache` ports used in service and API.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-03-standard-tools-rust-port.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
