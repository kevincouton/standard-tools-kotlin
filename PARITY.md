# Port Parity Matrix

This document compares the `standard-tools-kotlin` port against the other Standard-Tools language implementations. It is accurate as of the latest commit on `main`.

## Legend

- ✅ Implemented / available
- ⚠️ Partial, stub, or minimal implementation
- ❌ Not implemented
- N/A Not applicable for this transport/stack

## Transport & protocol support

| Feature | Kotlin | C# | Go | Rust | C++ |
|---|---|---|---|---|---|
| REST | ✅ | ✅ | ✅ | ✅ | ✅ |
| gRPC | ✅ | ❌ | ⚠️ health only | ⚠️ health + agent | ⚠️ health only |
| A2A | ⚠️ tasks/send, no streaming | ❌ | ⚠️ minimal | ⚠️ partial (get/cancel placeholders) | ⚠️ skeleton |
| MCP | ✅ SSE | ❌ | ⚠️ HTTP-only | ⚠️ HTTP-only | ⚠️ HTTP-only |
| SSE | ⚠️ MCP transport only | ❌ | ❌ | ❌ | ❌ |
| Docker / container image | ✅ | ❌ | ✅ | ✅ | ✅ |
| CLI | ⚠️ audit commands only | ❌ | ✅ | ⚠️ server + audit placeholders | ✅ |
| Container health checks | ⚠️ actuator only | ⚠️ HTTP only | ✅ | ❌ | ✅ |

## Domain modules

| Feature | Kotlin | C# | Go | Rust | C++ |
|---|---|---|---|---|---|
| Market data provider port | ✅ YF, Polygon, Bloomberg stub | ⚠️ interface / stub | ✅ synthetic, YF, Polygon | ✅ YF + Moka cache | ⚠️ synthetic only |
| Indicators | ✅ | ✅ | ✅ | ✅ | ✅ |
| Risk / return metrics | ✅ | ✅ | ✅ | ✅ | ✅ |
| Analysis (regression, cointegration, Hurst, PCA, correlation, multi-factor, options) | ✅ | ✅ library; ⚠️ only regression + options exposed | ⚠️ no multi-factor | ✅ | ⚠️ no multi-factor |
| Backtesting engine | ✅ | ✅ | ✅ | ✅ | ✅ |
| Walk-forward optimization | ✅ | ✅ | ✅ | ✅ | ✅ |
| Monte Carlo simulation | ✅ | ✅ | ✅ | ✅ | ✅ |
| Robustness / stress testing | ✅ | ❌ | ❌ | ✅ | ❌ |
| Portfolio mean-variance | ✅ | ✅ | ✅ | ✅ | ✅ |
| Portfolio risk parity | ✅ equal-risk-contribution | ⚠️ inverse-vol | ✅ equal-risk-contribution | ✅ equal-risk-contribution | ✅ equal-risk-contribution |
| Black-Litterman | ✅ | ✅ | ✅ | ✅ | ✅ |
| Screener | ⚠️ hardcoded provider | ⚠️ hardcoded provider | ⚠️ hardcoded provider | ⚠️ hardcoded provider | ⚠️ hardcoded provider |
| Hash-chained audit | ✅ | ✅ | ✅ | ✅ | ✅ |
| Agent tool dispatcher | ✅ | ✅ | ✅ (19 tools) | ✅ (42 tools) | ✅ (11 tools) |

## Security & audit

| Feature | Kotlin | C# | Go | Rust | C++ |
|---|---|---|---|---|---|
| API-key auth on REST | ✅ fail-closed | ✅ fail-closed | ✅ fail-closed | ✅ fail-closed | ✅ fail-closed |
| API-key auth on gRPC | ✅ | N/A | ✅ | ✅ | ❌ |
| TLS termination | ❌ | ❌ | ❌ | ❌ | ❌ |
| Audit provenance (git commit / version / seed) | ⚠️ commit + version | ⚠️ schema only | ✅ all three | ❌ none recorded | ✅ all three |
| Replay read-only / side-effect blocklist | ✅ blocklist | ❌ not implemented | ❌ re-executes | ⚠️ blocklist, CLI placeholder | ⚠️ read-only fetch, no re-execution |
| Persistent audit storage | ✅ PostgreSQL | ✅ SQLite + memory | ✅ PostgreSQL + memory | ✅ PostgreSQL + memory | ✅ PostgreSQL + memory |

## Operational hardening

| Feature | Kotlin | C# | Go | Rust | C++ |
|---|---|---|---|---|---|
| Request body limit | 16 MB + 4 MB gRPC | 16 MiB | 16 MiB | 16 MiB | 16 MiB |
| HTTP/gRPC request timeout | 30 s netty | configured | configured | 60 s | ❌ |
| Backtest bar cap | 50 000 | 50 000 | 50 000 | 50 000 | 50 000 |
| Monte Carlo simulation cap | 100 000 / 2 520 horizon | 100 000 | 100 000 | 10 000 | 100 000 |
| Walk-forward window cap | 10 000 | 10 000 | 10 000 | 10 000 | 10 000 |
| Walk-forward combination cap | 10 000 | 10 000 | 10 000 | 10 000 | 10 000 |
| Portfolio asset cap | 100 | 100 | 100 | 100 | 100 |
| Screener ticker cap | 500 | 500 | 500 | 100 | 500 |
| Structured logging / request tracing | ❌ | ❌ | ❌ | ❌ | ❌ |
| Metrics / Prometheus endpoint | ✅ | ❌ | ❌ | ❌ | ❌ |

## CI status

Validation below was performed locally with `nektos/act` on `linux/arm64` (Podman) using the workflow job(s) that exercise the core build and tests.

| Port | Status | Notes |
|---|---|---|
| Kotlin | ✅ green | `act push --job unit-tests` passes (unit / integration / e2e); native build not validated locally |
| C# | ✅ green | `act push --job build-and-test` passes (`dotnet test` 88 tests) |
| Go | ✅ green | `act push --job quality` passes (`go test ./...`, `gofmt`, `go vet`) |
| Rust | ✅ green | `act push --job test` passes; artifact upload skipped under `env.ACT` |
| C++ | ✅ green | `act push --job quality` passes (build + ctest)

## Known limitations relevant to this port

- TLS termination is not implemented; terminate TLS at the reverse proxy.
- Audit records do not capture a per-request random seed.
- `STANDARD_TOOLS_AUDIT_ENABLED` in `.mise.toml` is not consumed by the application; audit writes are unconditional.
- The `lint` mise task references `ktlintCheck`, which is not configured in the build.
- The GraalVM native-image build depends on a toolchain that may not be available locally.
- Some analysis outputs (e.g., PCA factor returns, deflated Sharpe) have known quant caveats documented in the source.

## Outstanding P0/P1 gaps (deferred)

The following items were identified in the staff-engine audit and are explicitly documented rather than hidden behind false claims:

1. **TLS termination** — not implemented in any port. Deploy behind a reverse proxy that terminates TLS.
2. **Structured logging / request tracing** — no request-id propagation or structured log output; observability is limited to console logging.
3. **Full A2A/MCP semantics** — A2A is `tasks/send` only (no streaming, `tasks/get`, or `tasks/cancel`). MCP is SSE-based but session lifecycle and protocol compliance are incomplete.
4. **Native-image CI** — the GraalVM `build-native` job requires a toolchain not validated under `act`; treat as experimental until green locally.
5. **Dependency scanning** — no `cargo-audit`, `govulncheck`, or Dependabot integration yet.

## Recommendations before a release tag

1. Wire `STANDARD_TOOLS_AUDIT_ENABLED` or remove it from configuration.
2. Record a per-request random seed in audit provenance.
3. Add a container `HEALTHCHECK` and non-root runtime validation.
4. Fix or remove the `lint` mise task.
5. Stabilize the native-image CI job or mark it as experimental.
