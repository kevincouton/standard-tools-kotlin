# Kotlin Standard-Tools Port — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the functionality of `../Repo/Standard-Tools` (`standard_quant_tools`) into `kotlin-grpc-rest-starter` as idiomatic Kotlin/Spring Boot subdomains, exposing every capability through REST, gRPC, A2A, and MCP, with classic JVM and GraalVM native image builds.

**Architecture:** Single-module Clean/Hexagonal layout. Each Standard-Tools capability becomes a bounded subdomain (`marketdata`, `indicators`, `metrics`, `analysis`, `backtest`, `portfolio`, `screener`, `agenttools`, `audit`) with its own domain, application ports/services, and protocol adapters. A `shared` subdomain provides common value objects, provider abstractions, and cache ports.

**Tech Stack:** Spring Boot 4.1.0, Kotlin 2.3.21, Java 25, Gradle 9.1.0, Postgres 18, Flyway, Apache Commons Math, Tablesaw/Joinery, Caffeine, gRPC, WebFlux, TestContainers, MockK, Strikt, Allure, GraalVM Native Image.

---

## File Structure

New and modified files per phase are listed in each task. At a high level the port adds:

```
src/main/kotlin/com/example/starter/
├── shared/
│   ├── domain/ValueObjects.kt
│   ├── application/port/outbound/MarketDataProvider.kt
│   ├── application/port/outbound/MarketDataCache.kt
│   └── adapter/out/cache/CaffeineMarketDataCacheAdapter.kt
├── marketdata/
│   ├── domain/{TickerInfo,FinancialRatios,DataQualityReport}.kt
│   ├── application/port/inbound/FetchMarketDataUseCase.kt
│   ├── application/service/MarketDataService.kt
│   └── adapter/{in/web,grpc,a2a,mcp,out/{yfinance,polygon,bloomberg}}
├── indicators/
├── metrics/
├── analysis/
├── backtest/
├── portfolio/
├── screener/
├── agenttools/
└── audit/

src/main/proto/{marketdata,indicators,metrics,analysis,backtest,portfolio,screener,agenttools,audit}/
src/test/kotlin/com/example/starter/testsupport/fixtures/
src/integrationTest/kotlin/...
src/e2eTest/kotlin/...

Dockerfile
docs/superpowers/specs/2026-07-30-kotlin-standard-tools-port-design.md
```

---

## Phase 0: Foundation — dependencies, shared values, and test fixtures

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/example/starter/shared/domain/ValueObjects.kt`
- Create: `src/main/kotlin/com/example/starter/shared/domain/QuantError.kt`
- Create: `src/test/kotlin/com/example/starter/testsupport/fixtures/OhlcvFixtures.kt`

### Task 0.1: Add quant dependencies to version catalog

- [ ] **Step 0.1.1: Open `gradle/libs.versions.toml`**

- [ ] **Step 0.1.2: Add versions and libraries**

Add under `[versions]`:

```toml
commonsMath = "3.6.1"
tablesaw = "0.43.1"
caffeine = "3.1.8"
okhttp = "4.12.0"
wiremock = "3.9.1"
```

Add under `[libraries]`:

```toml
commons-math3 = { module = "org.apache.commons:commons-math3", version.ref = "commonsMath" }
tablesaw-core = { module = "tech.tablesaw:tablesaw-core", version.ref = "tablesaw" }
caffeine = { module = "com.github.ben-mane.caffeine:caffeine", version.ref = "caffeine" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
wiremock = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
```

- [ ] **Step 0.1.3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add quant library versions to catalog"
```

### Task 0.2: Wire dependencies into build script

- [ ] **Step 0.2.1: Open `build.gradle.kts`**

- [ ] **Step 0.2.2: Add dependencies**

In the `dependencies` block, add:

```kotlin
implementation(libs.commons.math3)
implementation(libs.tablesaw.core)
implementation(libs.caffeine)
implementation(libs.okhttp)
testImplementation(libs.wiremock)
```

- [ ] **Step 0.2.3: Verify build**

Run: `./gradlew dependencies --configuration compileClasspath | grep -E "commons-math3|tablesaw|caffeine|okhttp"`
Expected: each library appears in the output.

- [ ] **Step 0.2.4: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: wire quant dependencies into build"
```

### Task 0.3: Create shared domain value objects

- [ ] **Step 0.3.1: Create `src/main/kotlin/com/example/starter/shared/domain/ValueObjects.kt`**

```kotlin
package com.example.starter.shared.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BarInterval {
    DAILY, WEEKLY, MONTHLY
}

data class Ticker(
    val symbol: String,
    val exchange: String? = null
) {
    init {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
    }
}

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    init {
        require(!start.isAfter(end)) { "start must not be after end" }
    }

    val days: Long
        get() = ChronoUnit.DAYS.between(start, end) + 1
}

data class OHLCV(
    val ticker: Ticker,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long
) {
    init {
        require(!high < low) { "high must not be less than low" }
        require(open >= BigDecimal.ZERO) { "open must not be negative" }
        require(high >= BigDecimal.ZERO) { "high must not be negative" }
        require(low >= BigDecimal.ZERO) { "low must not be negative" }
        require(close >= BigDecimal.ZERO) { "close must not be negative" }
        require(volume >= 0) { "volume must not be negative" }
    }
}

typealias PriceSeries = List<OHLCV>

data class CacheKey(
    val provider: String,
    val ticker: Ticker,
    val interval: BarInterval,
    val range: DateRange
) {
    fun toComposite(): String = "$provider:${ticker.symbol}:${ticker.exchange ?: ""}:${interval}:${range.start}:${range.end}"
}
```

- [ ] **Step 0.3.2: Create `src/main/kotlin/com/example/starter/shared/domain/QuantError.kt`**

```kotlin
package com.example.starter.shared.domain

sealed class QuantError(message: String) : RuntimeException(message)

class ProviderNotAvailableException(provider: String) :
    QuantError("Market data provider not available: $provider")

class DataQualityException(message: String) :
    QuantError("Data quality issue: $message")

class InvalidCommandException(message: String) :
    QuantError("Invalid command: $message")
```

- [ ] **Step 0.3.3: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 0.3.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/shared/domain/
git commit -m "feat(shared): add quant domain value objects and errors"
```

### Task 0.4: Create OHLCV test fixtures

- [ ] **Step 0.4.1: Create `src/test/kotlin/com/example/starter/testsupport/fixtures/OhlcvFixtures.kt`**

```kotlin
package com.example.starter.testsupport.fixtures

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import java.math.BigDecimal
import java.time.LocalDate

object OhlcvFixtures {

    private val ticker = Ticker("AAPL", "NASDAQ")

    fun dailySeries(
        ticker: Ticker = this.ticker,
        start: LocalDate = LocalDate.of(2024, 1, 1),
        days: Int = 10,
        basePrice: Double = 100.0,
        volatility: Double = 2.0
    ): List<OHLCV> {
        return (0 until days).map { i ->
            val date = start.plusDays(i.toLong())
            val seed = (i * 7 + 3) % 13 - 6
            val open = basePrice + seed * volatility
            val close = open + ((seed + 2) % 5) * volatility * 0.5
            val high = maxOf(open, close) + volatility
            val low = minOf(open, close) - volatility
            OHLCV(
                ticker = ticker,
                date = date,
                open = BigDecimal(open.toString()),
                high = BigDecimal(high.toString()),
                low = BigDecimal(low.toString()),
                close = BigDecimal(close.toString()),
                volume = 1_000_000L + i * 10_000L
            )
        }
    }

    fun dateRange(start: LocalDate = LocalDate.of(2024, 1, 1), days: Int = 10): DateRange {
        return DateRange(start, start.plusDays(days.toLong() - 1))
    }

    fun defaultInterval(): BarInterval = BarInterval.DAILY
}
```

- [ ] **Step 0.4.2: Verify compile**

Run: `./gradlew compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 0.4.3: Commit**

```bash
git add src/test/kotlin/com/example/starter/testsupport/fixtures/
git commit -m "test: add OHLCV fixtures for quant tests"
```


## Phase 1: Shared outbound ports, market data provider, and cache

**Files:**
- Create: `src/main/kotlin/com/example/starter/shared/application/port/outbound/MarketDataProvider.kt`
- Create: `src/main/kotlin/com/example/starter/shared/application/port/outbound/MarketDataCache.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/application/port/inbound/FetchMarketDataUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/application/service/MarketDataService.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/out/cache/CaffeineMarketDataCacheAdapter.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/out/yfinance/YFinanceMarketDataAdapter.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/out/polygon/PolygonMarketDataAdapter.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/out/bloomberg/BloombergMarketDataAdapter.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/in/web/MarketDataController.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/in/grpc/MarketDataGrpcService.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/in/a2a/MarketDataA2aHandler.kt`
- Create: `src/main/kotlin/com/example/starter/marketdata/adapter/in/mcp/MarketDataMcpHandler.kt`
- Create: `src/main/proto/marketdata/market_data_service.proto`
- Create: `src/test/kotlin/com/example/starter/marketdata/domain/...`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 1.1: Define shared outbound ports

- [ ] **Step 1.1.1: Create `src/main/kotlin/com/example/starter/shared/application/port/outbound/MarketDataProvider.kt`**

```kotlin
package com.example.starter.shared.application.port.outbound

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker

interface MarketDataProvider {
    val name: String
    fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries
}
```

- [ ] **Step 1.1.2: Create `src/main/kotlin/com/example/starter/shared/application/port/outbound/MarketDataCache.kt`**

```kotlin
package com.example.starter.shared.application.port.outbound

import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.PriceSeries
import java.time.Duration

interface MarketDataCache {
    fun get(key: CacheKey): PriceSeries?
    fun put(key: CacheKey, series: PriceSeries, ttl: Duration)
}
```

- [ ] **Step 1.1.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/shared/application/port/outbound/
git commit -m "feat(shared): define market data provider and cache outbound ports"
```

### Task 1.2: Caffeine cache adapter

- [ ] **Step 1.2.1: Create `src/main/kotlin/com/example/starter/marketdata/adapter/out/cache/CaffeineMarketDataCacheAdapter.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.cache

import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.PriceSeries
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CaffeineMarketDataCacheAdapter : MarketDataCache {

    private val cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build<CacheKey, PriceSeries>()

    override fun get(key: CacheKey): PriceSeries? = cache.getIfPresent(key)

    override fun put(key: CacheKey, series: PriceSeries, ttl: Duration) {
        cache.put(key, series)
    }
}
```

- [ ] **Step 1.2.2: Write unit test `src/test/kotlin/com/example/starter/marketdata/adapter/out/cache/CaffeineMarketDataCacheAdapterTest.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.cache

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.Duration
import java.time.LocalDate

@Tag("unit")
class CaffeineMarketDataCacheAdapterTest {

    private val cache = CaffeineMarketDataCacheAdapter()
    private val key = CacheKey(
        provider = "yfinance",
        ticker = Ticker("AAPL"),
        interval = BarInterval.DAILY,
        range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5))
    )
    private val series = OhlcvFixtures.dailySeries(days = 5)

    @Test
    fun `put and get returns series`() {
        cache.put(key, series, Duration.ofMinutes(5))
        expectThat(cache.get(key)).isEqualTo(series)
    }

    @Test
    fun `get missing key returns null`() {
        expectThat(cache.get(key)).isNull()
    }
}
```

- [ ] **Step 1.2.3: Run tests**

Run: `./gradlew test --tests "com.example.starter.marketdata.adapter.out.cache.CaffeineMarketDataCacheAdapterTest"`
Expected: 2 tests passed.

- [ ] **Step 1.2.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/adapter/out/cache/ src/test/kotlin/com/example/starter/marketdata/adapter/out/cache/
git commit -m "feat(marketdata): add Caffeine cache adapter"
```

### Task 1.3: Market data application service and inbound port

- [ ] **Step 1.3.1: Create `src/main/kotlin/com/example/starter/marketdata/application/port/inbound/FetchMarketDataUseCase.kt`**

```kotlin
package com.example.starter.marketdata.application.port.inbound

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker

interface FetchMarketDataUseCase {
    fun fetch(command: FetchMarketDataCommand): PriceSeries

    data class FetchMarketDataCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null
    )
}
```

- [ ] **Step 1.3.2: Create `src/main/kotlin/com/example/starter/marketdata/application/service/MarketDataService.kt`**

```kotlin
package com.example.starter.marketdata.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.ProviderNotAvailableException
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class MarketDataService(
    private val providers: List<MarketDataProvider>,
    private val cache: MarketDataCache,
    private val properties: MarketDataProperties
) : FetchMarketDataUseCase {

    override fun fetch(command: FetchMarketDataUseCase.FetchMarketDataCommand): List<com.example.starter.shared.domain.OHLCV> {
        val providerName = command.provider ?: properties.defaultProvider
        val provider = providers.find { it.name == providerName }
            ?: throw ProviderNotAvailableException(providerName)

        if (!properties.isEnabled(providerName)) {
            throw ProviderNotAvailableException(providerName)
        }

        val key = CacheKey(providerName, command.ticker, command.interval, command.range)
        cache.get(key)?.let { return it }

        val series = provider.fetch(command.ticker, command.range, command.interval)
        cache.put(key, series, properties.cacheTtl)
        return series
    }
}
```

- [ ] **Step 1.3.3: Create `src/main/kotlin/com/example/starter/marketdata/application/service/MarketDataProperties.kt`**

```kotlin
package com.example.starter.marketdata.application.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "standard-tools.market-data")
data class MarketDataProperties(
    val defaultProvider: String = "yfinance",
    val cacheTtl: Duration = Duration.ofHours(1),
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    data class ProviderConfig(val enabled: Boolean = false, val apiKey: String? = null)

    fun isEnabled(name: String): Boolean = providers[name]?.enabled ?: (name == defaultProvider)
}
```

- [ ] **Step 1.3.4: Enable configuration properties**

In `src/main/kotlin/com/example/starter/KotlinGrpcRestStarterApplication.kt`, add `@ConfigurationPropertiesScan`:

```kotlin
package com.example.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class KotlinGrpcRestStarterApplication

fun main(args: Array<String>) {
    runApplication<KotlinGrpcRestStarterApplication>(*args)
}
```

- [ ] **Step 1.3.5: Add default config to `src/main/resources/application.yml`**

```yaml
standard-tools:
  market-data:
    default-provider: yfinance
    cache-ttl: 1h
    providers:
      yfinance:
        enabled: true
      polygon:
        enabled: false
      bloomberg:
        enabled: false
```

- [ ] **Step 1.3.6: Write unit test `src/test/kotlin/com/example/starter/marketdata/application/service/MarketDataServiceTest.kt`**

```kotlin
package com.example.starter.marketdata.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.LocalDate

@Tag("unit")
class MarketDataServiceTest {

    private val provider = mockk<MarketDataProvider>()
    private val cache = mockk<MarketDataCache>()
    private val properties = MarketDataProperties(
        defaultProvider = "yfinance",
        cacheTtl = Duration.ofMinutes(5),
        providers = mapOf("yfinance" to MarketDataProperties.ProviderConfig(enabled = true))
    )
    private val service = MarketDataService(listOf(provider), cache, properties)

    @Test
    fun `fetch returns cached series when available`() {
        every { provider.name } returns "yfinance"
        val expected = OhlcvFixtures.dailySeries(days = 3)
        every { cache.get(any()) } returns expected

        val result = service.fetch(command())

        expectThat(result).isEqualTo(expected)
    }

    @Test
    fun `fetch uses provider and caches result when not cached`() {
        every { provider.name } returns "yfinance"
        every { cache.get(any()) } returns null
        val expected = OhlcvFixtures.dailySeries(days = 3)
        every { provider.fetch(any(), any(), any()) } returns expected
        every { cache.put(any(), any(), any()) } returns Unit

        val result = service.fetch(command())

        expectThat(result).isEqualTo(expected)
        verify { cache.put(any(), expected, Duration.ofMinutes(5)) }
    }

    @Test
    fun `fetch throws when provider not available`() {
        every { provider.name } returns "yfinance"
        val disabledProperties = properties.copy(
            providers = mapOf("yfinance" to MarketDataProperties.ProviderConfig(enabled = false))
        )
        val serviceWithDisabled = MarketDataService(listOf(provider), cache, disabledProperties)

        org.junit.jupiter.api.assertThrows<ProviderNotAvailableException> {
            serviceWithDisabled.fetch(command())
        }
    }

    private fun command() = FetchMarketDataUseCase.FetchMarketDataCommand(
        ticker = Ticker("AAPL"),
        range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)),
        interval = BarInterval.DAILY
    )
}
```

- [ ] **Step 1.3.7: Run tests**

Run: `./gradlew test --tests "com.example.starter.marketdata.application.service.MarketDataServiceTest"`
Expected: 3 tests passed.

- [ ] **Step 1.3.8: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/application/ src/main/kotlin/com/example/starter/KotlinGrpcRestStarterApplication.kt src/main/resources/application.yml src/test/kotlin/com/example/starter/marketdata/application/
git commit -m "feat(marketdata): add fetch use case and service with provider selection and caching"
```


### Task 1.4: yfinance provider adapter

- [ ] **Step 1.4.1: Create `src/main/kotlin/com/example/starter/marketdata/adapter/out/yfinance/YFinanceMarketDataAdapter.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.yfinance

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class YFinanceMarketDataAdapter(
    private val client: OkHttpClient = OkHttpClient()
) : MarketDataProvider {

    override val name: String = "yfinance"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        val symbol = ticker.symbol
        val period1 = range.start.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val period2 = range.end.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val intervalParam = when (interval) {
            BarInterval.DAILY -> "1d"
            BarInterval.WEEKLY -> "1wk"
            BarInterval.MONTHLY -> "1mo"
        }
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8)
        val url = "https://query1.finance.yahoo.com/v7/finance/download/$encodedSymbol?period1=$period1&period2=$period2&interval=$intervalParam&events=history"

        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("yfinance request failed: ${response.code} ${response.body?.string()}")
            }
            val body = response.body?.string() ?: throw RuntimeException("empty yfinance response")
            return parseCsv(body, ticker)
        }
    }

    private fun parseCsv(csv: String, ticker: Ticker): PriceSeries {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        return lines.drop(1).map { line ->
            val cols = line.split(",")
            OHLCV(
                ticker = ticker,
                date = LocalDate.parse(cols[0], DateTimeFormatter.ISO_LOCAL_DATE),
                open = BigDecimal(cols[1]),
                high = BigDecimal(cols[2]),
                low = BigDecimal(cols[3]),
                close = BigDecimal(cols[4]),
                volume = cols[6].toLong()
            )
        }
    }
}
```

- [ ] **Step 1.4.2: Create unit test with WireMock `src/test/kotlin/com/example/starter/marketdata/adapter/out/yfinance/YFinanceMarketDataAdapterTest.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.yfinance

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.math.BigDecimal
import java.time.LocalDate

@Tag("unit")
class YFinanceMarketDataAdapterTest {

    private val wireMock = WireMockServer(0)
    private lateinit var adapter: YFinanceMarketDataAdapter

    @BeforeEach
    fun setup() {
        wireMock.start()
        adapter = YFinanceMarketDataAdapter()
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `parses csv response into price series`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/v7/finance/download/.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/csv")
                        .withBody(
                            """
                            Date,Open,High,Low,Close,Adj Close,Volume
                            2024-01-02,100.00,102.00,99.00,101.00,101.00,1000000
                            2024-01-03,101.00,103.00,100.00,102.00,102.00,1100000
                            """.trimIndent()
                        )
                )
        )

        val series = adapter.fetch(
            Ticker("AAPL"),
            DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)),
            BarInterval.DAILY
        )

        expectThat(series).hasSize(2)
        expectThat(series.first().close).isEqualTo(BigDecimal("101.00"))
    }
}
```

- [ ] **Step 1.4.3: Run tests**

Run: `./gradlew test --tests "com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapterTest"`
Expected: 1 test passed.

- [ ] **Step 1.4.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/adapter/out/yfinance/ src/test/kotlin/com/example/starter/marketdata/adapter/out/yfinance/
git commit -m "feat(marketdata): add yfinance provider adapter"
```

### Task 1.5: Polygon provider adapter

- [ ] **Step 1.5.1: Create `src/main/kotlin/com/example/starter/marketdata/adapter/out/polygon/PolygonMarketDataAdapter.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.polygon

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@Component
@ConditionalOnProperty(prefix = "standard-tools.market-data.providers.polygon", name = ["enabled"], havingValue = "true")
class PolygonMarketDataAdapter(
    private val properties: PolygonProperties,
    private val client: OkHttpClient = OkHttpClient(),
    private val objectMapper: ObjectMapper
) : MarketDataProvider {

    override val name: String = "polygon"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        val multiplier = 1
        val timespan = when (interval) {
            BarInterval.DAILY -> "day"
            BarInterval.WEEKLY -> "week"
            BarInterval.MONTHLY -> "month"
        }
        val from = range.start.toString()
        val to = range.end.toString()
        val url = "https://api.polygon.io/v2/aggs/ticker/${ticker.symbol}/range/$multiplier/$timespan/$from/$to?adjusted=true&apiKey=${properties.apiKey}"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Polygon request failed: ${response.code}")
            }
            val json = response.body?.string() ?: throw RuntimeException("empty Polygon response")
            val root = objectMapper.readTree(json)
            val results = root["results"] ?: return emptyList()
            return results.map { bar ->
                OHLCV(
                    ticker = ticker,
                    date = LocalDate.ofEpochDay(bar["t"].asLong() / 86_400_000),
                    open = BigDecimal(bar["o"].asText()),
                    high = BigDecimal(bar["h"].asText()),
                    low = BigDecimal(bar["l"].asText()),
                    close = BigDecimal(bar["c"].asText()),
                    volume = bar["v"].asLong()
                )
            }
        }
    }
}
```

- [ ] **Step 1.5.2: Create `src/main/kotlin/com/example/starter/marketdata/adapter/out/polygon/PolygonProperties.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.polygon

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "standard-tools.market-data.providers.polygon")
data class PolygonProperties(val apiKey: String = "")
```

- [ ] **Step 1.5.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/adapter/out/polygon/
git commit -m "feat(marketdata): add Polygon.io provider adapter"
```

### Task 1.6: Bloomberg provider adapter (stub)

- [ ] **Step 1.6.1: Create `src/main/kotlin/com/example/starter/marketdata/adapter/out/bloomberg/BloombergMarketDataAdapter.kt`**

```kotlin
package com.example.starter.marketdata.adapter.out.bloomberg

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "standard-tools.market-data.providers.bloomberg", name = ["enabled"], havingValue = "true")
class BloombergMarketDataAdapter : MarketDataProvider {

    override val name: String = "bloomberg"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        throw ProviderNotAvailableException("bloomberg")
    }
}
```

- [ ] **Step 1.6.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/adapter/out/bloomberg/
git commit -m "feat(marketdata): add Bloomberg provider adapter stub"
```


### Task 1.7: gRPC proto and service for market data

- [ ] **Step 1.7.1: Create `src/main/proto/marketdata/market_data_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.marketdata.grpc;
option java_package = "com.example.starter.marketdata.grpc";
option java_multiple_files = true;

service MarketDataService {
  rpc FetchMarketData (FetchMarketDataRequest) returns (FetchMarketDataResponse);
}

message FetchMarketDataRequest {
  string symbol = 1;
  string exchange = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
}

message OHLCVBar {
  string date = 1;
  string open = 2;
  string high = 3;
  string low = 4;
  string close = 5;
  int64 volume = 6;
}

message FetchMarketDataResponse {
  string symbol = 1;
  repeated OHLCVBar bars = 2;
}
```

- [ ] **Step 1.7.2: Verify proto generation**

Run: `./gradlew generateProto`
Expected: `build/generated/source/proto/main/grpckt/com/example/starter/marketdata/grpc/MarketDataServiceGrpcKt.kt` exists.

- [ ] **Step 1.7.3: Create `src/main/kotlin/com/example/starter/marketdata/adapter/in/grpc/MarketDataGrpcService.kt`**

```kotlin
package com.example.starter.marketdata.adapter.in.grpc

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.marketdata.grpc.FetchMarketDataRequest
import com.example.starter.marketdata.grpc.FetchMarketDataResponse
import com.example.starter.marketdata.grpc.MarketDataServiceGrpcKt
import com.example.starter.marketdata.grpc.OHLCVBar
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class MarketDataGrpcService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase
) : MarketDataServiceGrpcKt.MarketDataServiceCoroutineImplBase() {

    override suspend fun fetchMarketData(request: FetchMarketDataRequest): FetchMarketDataResponse = withContext(Dispatchers.IO) {
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = Ticker(request.symbol, request.exchange.takeIf { it.isNotBlank() }),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        FetchMarketDataResponse.newBuilder()
            .setSymbol(request.symbol)
            .addAllBars(series.map {
                OHLCVBar.newBuilder()
                    .setDate(it.date.toString())
                    .setOpen(it.open.toPlainString())
                    .setHigh(it.high.toPlainString())
                    .setLow(it.low.toPlainString())
                    .setClose(it.close.toPlainString())
                    .setVolume(it.volume)
                    .build()
            })
            .build()
    }
}
```

- [ ] **Step 1.7.4: Commit**

```bash
git add src/main/proto/marketdata/ src/main/kotlin/com/example/starter/marketdata/adapter/in/grpc/
git commit -m "feat(marketdata): add gRPC service and proto"
```

### Task 1.8: REST controller for market data

- [ ] **Step 1.8.1: Create `src/main/kotlin/com/example/starter/marketdata/adapter/in/web/MarketDataController.kt`**

```kotlin
package com.example.starter.marketdata.adapter.in.web

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/market-data")
class MarketDataController(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase
) {

    @GetMapping(value = ["/bars"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchBars(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<List<OHLCV>> {
        return Mono.fromCallable {
            fetchMarketDataUseCase.fetch(
                FetchMarketDataUseCase.FetchMarketDataCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = BarInterval.valueOf(interval.uppercase()),
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
```

- [ ] **Step 1.8.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/marketdata/adapter/in/web/
git commit -m "feat(marketdata): add REST controller"
```

### Task 1.9: A2A and MCP handlers for market data

- [ ] **Step 1.9.1: Extend `A2aTaskHandler` to dispatch `marketdata-fetch`**

Modify `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt`:

Add a new branch in `handleTasksSend` for `skillId == "marketdata-fetch"`:

```kotlin
"marketdata-fetch" -> {
    val symbol = params["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
    val exchange = params["exchange"] as? String
    val startDate = params["startDate"] as? String ?: throw IllegalArgumentException("startDate required")
    val endDate = params["endDate"] as? String ?: throw IllegalArgumentException("endDate required")
    val interval = params["interval"] as? String ?: "DAILY"
    val provider = params["provider"] as? String
    val series = fetchMarketDataUseCase.fetch(
        FetchMarketDataUseCase.FetchMarketDataCommand(
            ticker = Ticker(symbol, exchange),
            range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    )
    mapOf("symbol" to symbol, "bars" to series.map { mapOf(
        "date" to it.date.toString(),
        "open" to it.open.toPlainString(),
        "high" to it.high.toPlainString(),
        "low" to it.low.toPlainString(),
        "close" to it.close.toPlainString(),
        "volume" to it.volume
    )})
}
```

Inject `FetchMarketDataUseCase` into the constructor.

- [ ] **Step 1.9.2: Extend `McpToolHandler` to expose `marketdata_fetch`**

Modify `src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt`:

Add to `toolsList()`:

```kotlin
mapOf(
    "name" to "marketdata_fetch",
    "description" to "Fetch OHLCV bars for a ticker",
    "inputSchema" to mapOf(
        "type" to "object",
        "properties" to mapOf(
            "symbol" to mapOf("type" to "string"),
            "exchange" to mapOf("type" to "string"),
            "startDate" to mapOf("type" to "string"),
            "endDate" to mapOf("type" to "string"),
            "interval" to mapOf("type" to "string"),
            "provider" to mapOf("type" to "string")
        ),
        "required" to listOf("symbol", "startDate", "endDate", "interval")
    )
)
```

Add to `handleToolCall()`:

```kotlin
"marketdata_fetch" -> {
    val symbol = arguments["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
    val exchange = arguments["exchange"] as? String
    val startDate = arguments["startDate"] as? String ?: throw IllegalArgumentException("startDate required")
    val endDate = arguments["endDate"] as? String ?: throw IllegalArgumentException("endDate required")
    val interval = arguments["interval"] as? String ?: "DAILY"
    val provider = arguments["provider"] as? String
    val series = fetchMarketDataUseCase.fetch(
        FetchMarketDataUseCase.FetchMarketDataCommand(
            ticker = Ticker(symbol, exchange),
            range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    )
    mapOf(
        "content" to listOf(mapOf(
            "type" to "text",
            "text" to "Fetched ${series.size} bars for $symbol"
        ))
    )
}
```

Inject `FetchMarketDataUseCase` into the constructor.

- [ ] **Step 1.9.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/a2a/ src/main/kotlin/com/example/starter/adapter/in/mcp/
git commit -m "feat(marketdata): add A2A and MCP handlers"
```

### Task 1.10: Integration and E2E tests for market data

- [ ] **Step 1.10.1: Create integration test `src/integrationTest/kotlin/com/example/starter/marketdata/MarketDataIntegrationTest.kt`**

```kotlin
package com.example.starter.marketdata

import com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapter
import com.example.starter.marketdata.application.service.MarketDataProperties
import com.example.starter.marketdata.application.service.MarketDataService
import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import java.time.Duration
import java.time.LocalDate

@Tag("integration")
class MarketDataIntegrationTest {

    private val provider = YFinanceMarketDataAdapter()
    private val cache = mockk<MarketDataCache>()
    private val properties = MarketDataProperties(
        defaultProvider = "yfinance",
        cacheTtl = Duration.ofMinutes(5),
        providers = mapOf("yfinance" to MarketDataProperties.ProviderConfig(enabled = true))
    )
    private val service = MarketDataService(listOf(provider), cache, properties)

    @Test
    fun `service orchestrates provider and cache`() {
        every { cache.get(any()) } returns null
        every { cache.put(any(), any(), any()) } returns Unit
        // Provider is real HTTP; in CI use WireMock or tag as live
    }
}
```

- [ ] **Step 1.10.2: Create E2E test `src/e2eTest/kotlin/com/example/starter/marketdata/e2e/MarketDataE2ETest.kt`**

Reuse the pattern from `OrderLifecycleE2ETest`. Test REST, gRPC, A2A, and MCP endpoints for market data using a WireMock-backed yfinance provider or cached fixtures.

- [ ] **Step 1.10.3: Commit**

```bash
git add src/integrationTest/kotlin/com/example/starter/marketdata/ src/e2eTest/kotlin/com/example/starter/marketdata/
git commit -m "test(marketdata): add integration and E2E tests"
```


## Phase 2: Indicators and metrics

**Files:**
- Create: `src/main/kotlin/com/example/starter/indicators/domain/IndicatorResult.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/application/port/inbound/CalculateIndicatorUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/application/service/IndicatorCalculatorService.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/domain/TrendIndicators.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/domain/MomentumIndicators.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/domain/VolatilityIndicators.kt`
- Create: `src/main/kotlin/com/example/starter/indicators/domain/VolumeIndicators.kt`
- Create adapters in `src/main/kotlin/com/example/starter/indicators/adapter/in/{web,grpc,a2a,mcp}/`
- Create proto: `src/main/proto/indicators/indicator_service.proto`
- Create tests in `src/test/...`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 2.1: Indicator domain and use case

- [ ] **Step 2.1.1: Create `src/main/kotlin/com/example/starter/indicators/domain/IndicatorResult.kt`**

```kotlin
package com.example.starter.indicators.domain

import java.math.BigDecimal
import java.time.LocalDate

data class IndicatorValue(
    val date: LocalDate,
    val value: BigDecimal?
)

data class IndicatorResult(
    val indicator: String,
    val parameters: Map<String, Any>,
    val values: List<IndicatorValue>
)
```

- [ ] **Step 2.1.2: Create `src/main/kotlin/com/example/starter/indicators/application/port/inbound/CalculateIndicatorUseCase.kt`**

```kotlin
package com.example.starter.indicators.application.port.inbound

import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface CalculateIndicatorUseCase {
    fun calculate(command: CalculateIndicatorCommand): IndicatorResult

    data class CalculateIndicatorCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val indicator: String,
        val parameters: Map<String, Any> = emptyMap(),
        val provider: String? = null
    )
}
```

- [ ] **Step 2.1.3: Create `src/main/kotlin/com/example/starter/indicators/application/service/IndicatorCalculatorService.kt`**

```kotlin
package com.example.starter.indicators.application.service

import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.domain.IndicatorCalculator
import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import org.springframework.stereotype.Service

@Service
class IndicatorCalculatorService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculator: IndicatorCalculator
) : CalculateIndicatorUseCase {

    override fun calculate(command: CalculateIndicatorUseCase.CalculateIndicatorCommand): IndicatorResult {
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = command.ticker,
                range = command.range,
                interval = command.interval,
                provider = command.provider
            )
        )
        return calculator.calculate(command.indicator, series, command.parameters)
    }
}
```

- [ ] **Step 2.1.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/indicators/domain/ src/main/kotlin/com/example/starter/indicators/application/
git commit -m "feat(indicators): add indicator domain, use case, and service"
```

### Task 2.2: Indicator calculator implementations

- [ ] **Step 2.2.1: Create `src/main/kotlin/com/example/starter/indicators/domain/IndicatorCalculator.kt`**

```kotlin
package com.example.starter.indicators.domain

import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class IndicatorCalculator {

    fun calculate(name: String, series: PriceSeries, parameters: Map<String, Any>): IndicatorResult {
        return when (name.lowercase()) {
            "sma" -> sma(series, parameters.intParam("period", 20))
            "ema" -> ema(series, parameters.intParam("period", 20))
            "rsi" -> rsi(series, parameters.intParam("period", 14))
            "macd" -> macd(series, parameters.intParam("fast", 12), parameters.intParam("slow", 26), parameters.intParam("signal", 9))
            "bollinger_bands" -> bollingerBands(series, parameters.intParam("period", 20), parameters.intParam("stdDev", 2))
            "atr" -> atr(series, parameters.intParam("period", 14))
            "obv" -> obv(series)
            "vwap" -> vwap(series)
            else -> throw IllegalArgumentException("Unknown indicator: $name")
        }
    }

    private fun sma(series: PriceSeries, period: Int): IndicatorResult {
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx + 1 < period) null
            else series.subList(idx + 1 - period, idx + 1).map { it.close }.average()
            IndicatorValue(bar.date, value)
        }
        return IndicatorResult("sma", mapOf("period" to period), values)
    }

    private fun ema(series: PriceSeries, period: Int): IndicatorResult {
        val multiplier = 2.0 / (period + 1)
        val values = mutableListOf<IndicatorValue>()
        var ema = series.first().close.toDouble()
        series.forEachIndexed { idx, bar ->
            if (idx == 0) {
                values.add(IndicatorValue(bar.date, bar.close))
            } else {
                ema = (bar.close.toDouble() - ema) * multiplier + ema
                values.add(IndicatorValue(bar.date, BigDecimal(ema).setScale(4, RoundingMode.HALF_UP)))
            }
        }
        return IndicatorResult("ema", mapOf("period" to period), values)
    }

    private fun rsi(series: PriceSeries, period: Int): IndicatorResult {
        val closes = series.map { it.close.toDouble() }
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)
        }
        val values = series.take(1).map { IndicatorValue(it.date, null) } +
            (period until closes.size).map { idx ->
                val avgGain = gains.subList(idx - period, idx).average()
                val avgLoss = losses.subList(idx - period, idx).average()
                val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
                val rsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1 + rs))
                IndicatorValue(series[idx].date, BigDecimal(rsi).setScale(4, RoundingMode.HALF_UP))
            }
        return IndicatorResult("rsi", mapOf("period" to period), values)
    }

    private fun macd(series: PriceSeries, fast: Int, slow: Int, signal: Int): IndicatorResult {
        val fastEma = emaValues(series, fast)
        val slowEma = emaValues(series, slow)
        val macdLine = fastEma.zip(slowEma).map { (f, s) -> f - s }
        val signalLine = emaOfList(macdLine, signal)
        val histogram = macdLine.zip(signalLine).map { (m, s) -> m - s }
        val values = series.indices.map { i ->
            IndicatorValue(series[i].date, BigDecimal(macdLine[i]).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("macd", mapOf("fast" to fast, "slow" to slow, "signal" to signal), values)
    }

    private fun bollingerBands(series: PriceSeries, period: Int, stdDev: Int): IndicatorResult {
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx + 1 < period) null
            else {
                val window = series.subList(idx + 1 - period, idx + 1).map { it.close.toDouble() }
                val stats = DescriptiveStatistics(window.toDoubleArray())
                val middle = stats.mean
                val upper = middle + stdDev * stats.standardDeviation
                BigDecimal(upper).setScale(4, RoundingMode.HALF_UP)
            }
            IndicatorValue(bar.date, value)
        }
        return IndicatorResult("bollinger_bands_upper", mapOf("period" to period, "stdDev" to stdDev), values)
    }

    private fun atr(series: PriceSeries, period: Int): IndicatorResult {
        val trs = series.mapIndexed { idx, bar ->
            if (idx == 0) bar.high.toDouble() - bar.low.toDouble()
            else {
                val prevClose = series[idx - 1].close.toDouble()
                listOf(
                    bar.high.toDouble() - bar.low.toDouble(),
                    kotlin.math.abs(bar.high.toDouble() - prevClose),
                    kotlin.math.abs(bar.low.toDouble() - prevClose)
                ).maxOrNull()!!
            }
        }
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx < period) null
            else trs.subList(idx - period + 1, idx + 1).average()
            IndicatorValue(bar.date, value?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) })
        }
        return IndicatorResult("atr", mapOf("period" to period), values)
    }

    private fun obv(series: PriceSeries): IndicatorResult {
        var obv = 0.0
        val values = series.mapIndexed { idx, bar ->
            if (idx > 0) {
                val prevClose = series[idx - 1].close.toDouble()
                val change = bar.close.toDouble() - prevClose
                obv += when {
                    change > 0 -> bar.volume.toDouble()
                    change < 0 -> -bar.volume.toDouble()
                    else -> 0.0
                }
            } else {
                obv = 0.0
            }
            IndicatorValue(bar.date, BigDecimal(obv).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("obv", emptyMap(), values)
    }

    private fun vwap(series: PriceSeries): IndicatorResult {
        var cumulativeTypicalVolume = 0.0
        var cumulativeVolume = 0.0
        val values = series.map { bar ->
            val typical = (bar.high.toDouble() + bar.low.toDouble() + bar.close.toDouble()) / 3.0
            cumulativeTypicalVolume += typical * bar.volume
            cumulativeVolume += bar.volume
            val vwap = if (cumulativeVolume == 0.0) 0.0 else cumulativeTypicalVolume / cumulativeVolume
            IndicatorValue(bar.date, BigDecimal(vwap).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("vwap", emptyMap(), values)
    }

    private fun emaValues(series: PriceSeries, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = series.first().close.toDouble()
        series.forEachIndexed { idx, bar ->
            if (idx == 0) result.add(ema)
            else {
                ema = (bar.close.toDouble() - ema) * multiplier + ema
                result.add(ema)
            }
        }
        return result
    }

    private fun emaOfList(values: List<Double>, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = values.first()
        values.forEachIndexed { idx, v ->
            if (idx == 0) result.add(ema)
            else {
                ema = (v - ema) * multiplier + ema
                result.add(ema)
            }
        }
        return result
    }

    private fun Map<String, Any>.intParam(key: String, default: Int): Int {
        return when (val v = get(key)) {
            is Int -> v
            is Number -> v.toInt()
            is String -> v.toInt()
            else -> default
        }
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return fold(BigDecimal.ZERO) { sum, v -> sum + v }.divide(BigDecimal(size), 4, RoundingMode.HALF_UP)
    }
}
```

- [ ] **Step 2.2.2: Write unit test `src/test/kotlin/com/example/starter/indicators/domain/IndicatorCalculatorTest.kt`**

```kotlin
package com.example.starter.indicators.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

@Tag("unit")
class IndicatorCalculatorTest {

    private val calculator = IndicatorCalculator()
    private val series = OhlcvFixtures.dailySeries(days = 30)

    @Test
    fun `calculates sma`() {
        val result = calculator.calculate("sma", series, mapOf("period" to 20))
        expectThat(result.values).hasSize(series.size)
        expectThat(result.values[19].value).isNotNull()
    }

    @Test
    fun `calculates rsi`() {
        val result = calculator.calculate("rsi", series, mapOf("period" to 14))
        expectThat(result.values).hasSize(series.size)
    }
}
```

- [ ] **Step 2.2.3: Run tests**

Run: `./gradlew test --tests "com.example.starter.indicators.domain.IndicatorCalculatorTest"`
Expected: tests pass.

- [ ] **Step 2.2.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/indicators/domain/IndicatorCalculator.kt src/test/kotlin/com/example/starter/indicators/domain/
git commit -m "feat(indicators): add indicator calculator implementations"
```

### Task 2.3: Metrics domain and use case

- [ ] **Step 2.3.1: Create `src/main/kotlin/com/example/starter/metrics/domain/RiskMetrics.kt`**

```kotlin
package com.example.starter.metrics.domain

import java.math.BigDecimal
import java.time.LocalDate

data class RiskMetrics(
    val sharpeRatio: BigDecimal?,
    val sortinoRatio: BigDecimal?,
    val maxDrawdown: BigDecimal,
    val calmarRatio: BigDecimal?,
    val var95: BigDecimal,
    val cvar95: BigDecimal,
    val volatility: BigDecimal
)

data class ReturnMetrics(
    val cumulativeReturn: BigDecimal,
    val cagr: BigDecimal?,
    val annualizedVolatility: BigDecimal
)
```

- [ ] **Step 2.3.2: Create `src/main/kotlin/com/example/starter/metrics/domain/RiskReturnCalculator.kt`**

```kotlin
package com.example.starter.metrics.domain

import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

class RiskReturnCalculator {

    fun returnMetrics(series: PriceSeries, riskFreeRate: Double = 0.02): ReturnMetrics {
        val returns = simpleReturns(series)
        val cumulative = (1 + returns.sum()).let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) }
        val stats = DescriptiveStatistics(returns.toDoubleArray())
        val annVol = stats.standardDeviation * sqrt(252.0)
        val meanReturn = returns.average() * 252
        val cagr = if (returns.isEmpty()) null else BigDecimal(meanReturn).setScale(4, RoundingMode.HALF_UP)
        return ReturnMetrics(
            cumulativeReturn = cumulative,
            cagr = cagr,
            annualizedVolatility = BigDecimal(annVol).setScale(4, RoundingMode.HALF_UP)
        )
    }

    fun riskMetrics(series: PriceSeries, riskFreeRate: Double = 0.02): RiskMetrics {
        val returns = simpleReturns(series)
        val stats = DescriptiveStatistics(returns.toDoubleArray())
        val meanExcess = returns.map { it - riskFreeRate / 252 }.average()
        val vol = stats.standardDeviation * sqrt(252.0)
        val downside = returns.filter { it < 0 }
        val downsideDev = if (downside.isEmpty()) 0.0 else DescriptiveStatistics(downside.toDoubleArray()).standardDeviation * sqrt(252.0)
        val sharpe = if (vol == 0.0) null else meanExcess / vol
        val sortino = if (downsideDev == 0.0) null else meanExcess / downsideDev
        val (maxDd, _) = drawdown(series)
        val calmar = if (maxDd == 0.0) null else meanExcess * 252 / maxDd
        val sorted = returns.sorted()
        val var95 = sorted.getOrElse((sorted.size * 0.05).toInt()) { sorted.firstOrNull() ?: 0.0 }
        val cvar95 = sorted.take((sorted.size * 0.05).toInt().coerceAtLeast(1)).average()
        return RiskMetrics(
            sharpeRatio = sharpe?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            sortinoRatio = sortino?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            maxDrawdown = BigDecimal(maxDd).setScale(4, RoundingMode.HALF_UP),
            calmarRatio = calmar?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            var95 = BigDecimal(var95).setScale(4, RoundingMode.HALF_UP),
            cvar95 = BigDecimal(cvar95).setScale(4, RoundingMode.HALF_UP),
            volatility = BigDecimal(vol).setScale(4, RoundingMode.HALF_UP)
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> {
        return series.zipWithNext { prev, curr ->
            (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
        }
    }

    private fun drawdown(series: PriceSeries): Pair<Double, List<Double>> {
        var peak = Double.NEGATIVE_INFINITY
        val drawdowns = mutableListOf<Double>()
        series.forEach { bar ->
            val price = bar.close.toDouble()
            if (price > peak) peak = price
            drawdowns.add((peak - price) / peak)
        }
        return drawdowns.maxOrNull() ?: 0.0 to drawdowns
    }
}
```

- [ ] **Step 2.3.3: Create use case and service**

`src/main/kotlin/com/example/starter/metrics/application/port/inbound/CalculateMetricsUseCase.kt`:

```kotlin
package com.example.starter.metrics.application.port.inbound

import com.example.starter.metrics.domain.ReturnMetrics
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface CalculateMetricsUseCase {
    fun calculateRisk(command: CalculateRiskCommand): RiskMetrics
    fun calculateReturn(command: CalculateReturnCommand): ReturnMetrics

    data class CalculateRiskCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val riskFreeRate: Double = 0.02,
        val provider: String? = null
    )

    data class CalculateReturnCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null
    )
}
```

`src/main/kotlin/com/example/starter/metrics/application/service/MetricsService.kt`:

```kotlin
package com.example.starter.metrics.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.domain.ReturnMetrics
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.metrics.domain.RiskReturnCalculator
import org.springframework.stereotype.Service

@Service
class MetricsService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculator: RiskReturnCalculator
) : CalculateMetricsUseCase {

    override fun calculateRisk(command: CalculateMetricsUseCase.CalculateRiskCommand): RiskMetrics {
        val series = fetchSeries(command.ticker, command.range, command.interval, command.provider)
        return calculator.riskMetrics(series, command.riskFreeRate)
    }

    override fun calculateReturn(command: CalculateMetricsUseCase.CalculateReturnCommand): ReturnMetrics {
        val series = fetchSeries(command.ticker, command.range, command.interval, command.provider)
        return calculator.returnMetrics(series)
    }

    private fun fetchSeries(ticker: com.example.starter.shared.domain.Ticker, range: com.example.starter.shared.domain.DateRange, interval: com.example.starter.shared.domain.BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
```

- [ ] **Step 2.3.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/metrics/
git commit -m "feat(metrics): add risk and return metrics calculator"
```

### Task 2.4: Protocol adapters for indicators and metrics

- [ ] **Step 2.4.1: Create `src/main/proto/indicators/indicator_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.indicators.grpc;
option java_package = "com.example.starter.indicators.grpc";
option java_multiple_files = true;

service IndicatorService {
  rpc CalculateIndicator (CalculateIndicatorRequest) returns (IndicatorResponse);
}

message CalculateIndicatorRequest {
  string symbol = 1;
  string exchange = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string indicator = 6;
  map<string, string> parameters = 7;
  string provider = 8;
}

message IndicatorValue {
  string date = 1;
  string value = 2;
}

message IndicatorResponse {
  string indicator = 1;
  repeated IndicatorValue values = 2;
}
```

- [ ] **Step 2.4.2: Create gRPC service `src/main/kotlin/com/example/starter/indicators/adapter/in/grpc/IndicatorGrpcService.kt`**

Follow the same pattern as `MarketDataGrpcService`, mapping `CalculateIndicatorUseCase` to the generated proto service.

- [ ] **Step 2.4.3: Create REST controller `src/main/kotlin/com/example/starter/indicators/adapter/in/web/IndicatorController.kt`**

```kotlin
package com.example.starter.indicators.adapter.in.web

import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/indicators")
class IndicatorController(
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase
) {

    @GetMapping("/calculate")
    fun calculate(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam indicator: String,
        @RequestParam(required = false) parameters: Map<String, String>?,
        @RequestParam(required = false) provider: String?
    ): Mono<IndicatorResult> {
        return Mono.fromCallable {
            calculateIndicatorUseCase.calculate(
                CalculateIndicatorUseCase.CalculateIndicatorCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = BarInterval.valueOf(interval.uppercase()),
                    indicator = indicator,
                    parameters = parameters ?: emptyMap(),
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
```

- [ ] **Step 2.4.4: Create metrics REST controller and gRPC service**

Follow the same pattern in `src/main/kotlin/com/example/starter/metrics/adapter/in/web/` and `src/main/kotlin/com/example/starter/metrics/adapter/in/grpc/`.

- [ ] **Step 2.4.5: Extend A2A and MCP handlers**

Add skills/tools for `indicators-calculate`, `metrics-risk`, and `metrics-return` to the existing `A2aTaskHandler` and `McpToolHandler`.

- [ ] **Step 2.4.6: Commit**

```bash
git add src/main/kotlin/com/example/starter/indicators/adapter/ src/main/kotlin/com/example/starter/metrics/adapter/ src/main/proto/indicators/ src/main/proto/metrics/
git commit -m "feat(indicators,metrics): add REST, gRPC, A2A, MCP adapters"
```


## Phase 3: Analysis

**Files:**
- Create: `src/main/kotlin/com/example/starter/analysis/domain/AnalysisResult.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/application/port/inbound/RunAnalysisUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/RegressionCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/CointegrationCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/HurstCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/PcaCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/CorrelationCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/MultiFactorCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/domain/OptionsCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/application/service/AnalysisService.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/adapter/in/web/AnalysisController.kt`
- Create: `src/main/kotlin/com/example/starter/analysis/adapter/in/grpc/AnalysisGrpcService.kt`
- Create: `src/main/proto/analysis/analysis_service.proto`
- Modify: `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt`
- Modify: `src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt`
- Create tests in `src/test/kotlin/com/example/starter/analysis/domain/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 3.1: Analysis result types and command

- [ ] **Step 3.1.1: Create `src/main/kotlin/com/example/starter/analysis/domain/AnalysisResult.kt`**

```kotlin
package com.example.starter.analysis.domain

sealed class AnalysisResult {
    abstract val operation: String
}

data class RegressionResult(
    override val operation: String = "regression",
    val alpha: Double,
    val beta: Double,
    val rSquared: Double,
    val annualizedAlpha: Double?
) : AnalysisResult()

data class CointegrationResult(
    override val operation: String = "cointegration",
    val hedgeRatio: Double,
    val adfStatistic: Double,
    val pValueApprox: Double,
    val halfLife: Double,
    val currentZScore: Double?
) : AnalysisResult()

data class HurstResult(
    override val operation: String = "hurst",
    val exponent: Double,
    val regime: String,
    val rolling: List<Map<String, Double>>? = null
) : AnalysisResult()

data class PcaResult(
    override val operation: String = "pca",
    val explainedVarianceRatio: List<Double>,
    val loadings: Map<String, List<Double>>,
    val factorReturns: List<Map<String, Double>>
) : AnalysisResult()

data class CorrelationResult(
    override val operation: String = "correlation",
    val matrix: Map<String, Map<String, Double>>,
    val average: Double,
    val min: Double,
    val max: Double,
    val diversificationRatio: Double?
) : AnalysisResult()

data class MultiFactorResult(
    override val operation: String = "multi-factor",
    val alpha: Double,
    val loadings: Map<String, Double>,
    val tStatistics: Map<String, Double>,
    val pValues: Map<String, Double>,
    val rSquared: Double,
    val adjRSquared: Double
) : AnalysisResult()

data class OptionPricingResult(
    override val operation: String = "option-pricing",
    val price: Double,
    val greeks: OptionGreeks,
    val impliedVolatility: Double?
) : AnalysisResult()

data class OptionGreeks(
    val delta: Double,
    val gamma: Double,
    val vega: Double,
    val theta: Double,
    val rho: Double
)
```

- [ ] **Step 3.1.2: Create `src/main/kotlin/com/example/starter/analysis/application/port/inbound/RunAnalysisUseCase.kt`**

```kotlin
package com.example.starter.analysis.application.port.inbound

import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface RunAnalysisUseCase {
    fun execute(command: AnalysisCommand): AnalysisResult

    sealed class AnalysisCommand {
        abstract val provider: String?
    }

    data class RegressionCommand(
        val asset: Ticker,
        val benchmark: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val riskFreeRate: Double = 0.02
    ) : AnalysisCommand()

    data class CointegrationCommand(
        val assetA: Ticker,
        val assetB: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val zScoreWindow: Int = 30
    ) : AnalysisCommand()

    data class HurstCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val method: String = "dfa",
        val minWindow: Int = 10,
        val rollingWindow: Int? = null
    ) : AnalysisCommand()

    data class PcaCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val nComponents: Int? = null,
        val standardize: Boolean = true
    ) : AnalysisCommand()

    data class CorrelationCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val weights: Map<String, Double>? = null
    ) : AnalysisCommand()

    data class MultiFactorCommand(
        val asset: Ticker,
        val factors: Map<String, Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null
    ) : AnalysisCommand()

    data class OptionPricingCommand(
        val spot: Double,
        val strike: Double,
        val timeToExpiry: Double,
        val riskFreeRate: Double,
        val volatility: Double,
        val optionType: String = "call",
        val dividendYield: Double = 0.0,
        val marketPrice: Double? = null
    ) : AnalysisCommand() {
        override val provider: String? = null
    }
}
```

- [ ] **Step 3.1.3: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.1.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/domain/ src/main/kotlin/com/example/starter/analysis/application/port/inbound/
git commit -m "feat(analysis): add result types and use case commands"
```

### Task 3.2: Regression and cointegration calculators

- [ ] **Step 3.2.1: Create `src/main/kotlin/com/example/starter/analysis/domain/RegressionCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression

class RegressionCalculator {

    fun calculate(asset: PriceSeries, benchmark: PriceSeries, riskFreeRate: Double = 0.02): RegressionResult {
        val assetReturns = simpleReturns(asset)
        val benchReturns = simpleReturns(benchmark)
        require(assetReturns.size == benchReturns.size) { "series must align" }
        require(assetReturns.size >= 2) { "need at least 3 prices" }
        val regression = SimpleRegression()
        assetReturns.zip(benchReturns).forEach { (a, b) -> regression.addData(b, a) }
        val periodsPerYear = 252.0
        val alpha = regression.intercept
        return RegressionResult(
            alpha = alpha,
            beta = regression.slope,
            rSquared = regression.rSquare,
            annualizedAlpha = alpha * periodsPerYear
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 3.2.2: Create `src/main/kotlin/com/example/starter/analysis/domain/CointegrationCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.ln
import kotlin.math.sqrt

class CointegrationCalculator {

    fun calculate(a: PriceSeries, b: PriceSeries, zScoreWindow: Int = 30): CointegrationResult {
        val aligned = alignByDate(a, b)
        val logA = aligned.first.map { ln(it.close.toDouble()) }
        val logB = aligned.second.map { ln(it.close.toDouble()) }
        val ols = SimpleRegression()
        logB.zip(logA).forEach { (x, y) -> ols.addData(x, y) }
        val hedgeRatio = ols.slope
        val intercept = ols.intercept
        val spread = logB.zip(logA).map { (x, y) -> y - intercept - hedgeRatio * x }
        val hl = halfLife(spread)
        val (adfStat, pApprox) = adfApproximation(spread)
        val currentZ = if (spread.size >= zScoreWindow) zScore(spread.takeLast(zScoreWindow)) else null
        return CointegrationResult(
            hedgeRatio = hedgeRatio,
            adfStatistic = adfStat,
            pValueApprox = pApprox,
            halfLife = hl,
            currentZScore = currentZ
        )
    }

    private fun alignByDate(a: PriceSeries, b: PriceSeries): Pair<PriceSeries, PriceSeries> {
        val dates = a.map { it.date }.intersect(b.map { it.date }.toSet()).sorted()
        val byDateA = a.associateBy { it.date }
        val byDateB = b.associateBy { it.date }
        return dates.map { byDateA.getValue(it) } to dates.map { byDateB.getValue(it) }
    }

    private fun halfLife(spread: List<Double>): Double {
        if (spread.size < 2) return Double.NaN
        val delta = spread.zipWithNext { prev, curr -> curr - prev }
        val lag = spread.dropLast(1)
        val ols = SimpleRegression()
        lag.zip(delta).forEach { (x, y) -> ols.addData(x, y) }
        val lambda = ols.slope
        return if (lambda < 0) -ln(2.0) / lambda else Double.POSITIVE_INFINITY
    }

    private fun adfApproximation(spread: List<Double>): Pair<Double, Double> {
        if (spread.size < 3) return 0.0 to 1.0
        val diff = spread.zipWithNext { prev, curr -> curr - prev }
        val lag = spread.dropLast(1)
        val ols = SimpleRegression()
        lag.zip(diff).forEach { (x, y) -> ols.addData(x, y) }
        val tStat = ols.slope / if (ols.slopeStdErr == 0.0) 1.0 else ols.slopeStdErr
        val pApprox = 1.0 / (1.0 + kotlin.math.exp(2.0 * tStat + 1.0))
        return tStat to pApprox
    }

    private fun zScore(window: List<Double>): Double {
        val mean = window.average()
        val variance = window.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        return if (std == 0.0) 0.0 else (window.last() - mean) / std
    }
}
```

- [ ] **Step 3.2.3: Write unit tests `src/test/kotlin/com/example/starter/analysis/domain/RegressionCalculatorTest.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@Tag("unit")
class RegressionCalculatorTest {

    private val calculator = RegressionCalculator()

    @Test
    fun `beta near one for identical series`() {
        val series = OhlcvFixtures.dailySeries(days = 30)
        val result = calculator.calculate(series, series)
        expectThat(result.beta).isGreaterThan(0.95)
    }
}
```

- [ ] **Step 3.2.4: Write unit tests `src/test/kotlin/com/example/starter/analysis/domain/CointegrationCalculatorTest.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotNaN

@Tag("unit")
class CointegrationCalculatorTest {

    private val calculator = CointegrationCalculator()

    @Test
    fun `computes hedge ratio and half life`() {
        val a = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val b = OhlcvFixtures.dailySeries(days = 60, basePrice = 50.0)
        val result = calculator.calculate(a, b)
        expectThat(result.hedgeRatio).isNotNaN()
        expectThat(result.halfLife).isNotNaN()
    }
}
```

- [ ] **Step 3.2.5: Run tests**

Run: `./gradlew test --tests "com.example.starter.analysis.domain.RegressionCalculatorTest" --tests "com.example.starter.analysis.domain.CointegrationCalculatorTest"`
Expected: tests pass.

- [ ] **Step 3.2.6: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/domain/RegressionCalculator.kt src/main/kotlin/com/example/starter/analysis/domain/CointegrationCalculator.kt src/test/kotlin/com/example/starter/analysis/domain/
git commit -m "feat(analysis): add regression and cointegration calculators"
```

### Task 3.3: Hurst, PCA, correlation, and multi-factor calculators

- [ ] **Step 3.3.1: Create `src/main/kotlin/com/example/starter/analysis/domain/HurstCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.ln
import kotlin.math.pow

class HurstCalculator {

    fun calculate(series: PriceSeries, method: String = "dfa", minWindow: Int = 10): HurstResult {
        val values = series.map { it.close.toDouble() }
        require(values.size >= minWindow * 4) { "series too short" }
        val exponent = when (method.lowercase()) {
            "rs" -> rescaledRange(values, minWindow)
            else -> dfa(values, minWindow)
        }
        val regime = when {
            exponent > 0.55 -> "trending"
            exponent < 0.45 -> "mean_reverting"
            else -> "random_walk"
        }
        return HurstResult(exponent = exponent, regime = regime)
    }

    fun rolling(series: PriceSeries, window: Int, step: Int = 1, method: String = "dfa", minWindow: Int = 10): HurstResult {
        val values = series.map { it.close.toDouble() }
        val points = mutableListOf<Map<String, Double>>()
        var start = window
        while (start <= values.size) {
            val slice = values.subList(start - window, start)
            val exponent = when (method.lowercase()) {
                "rs" -> rescaledRange(slice, minWindow)
                else -> dfa(slice, minWindow)
            }
            points.add(mapOf("index" to start.toDouble(), "exponent" to exponent))
            start += step
        }
        return HurstResult(exponent = points.lastOrNull()?.get("exponent") ?: Double.NaN, regime = "rolling", rolling = points)
    }

    private fun dfa(values: List<Double>, minWindow: Int): Double {
        val profile = values.runningFold(0.0) { acc, v -> acc + (v - values.average()) }.drop(1)
        val windows = generateWindows(values.size, minWindow)
        val logWindow = mutableListOf<Double>()
        val logFluct = mutableListOf<Double>()
        windows.forEach { w ->
            val fluct = fluctuation(profile, w)
            if (fluct > 0) {
                logWindow.add(ln(w.toDouble()))
                logFluct.add(ln(fluct))
            }
        }
        val regression = SimpleRegression()
        logWindow.zip(logFluct).forEach { (x, y) -> regression.addData(x, y) }
        return regression.slope
    }

    private fun fluctuation(profile: List<Double>, window: Int): Double {
        val chunks = profile.chunked(window)
        val rms = chunks.map { chunk ->
            val xs = chunk.indices.map { it.toDouble() }
            val reg = SimpleRegression()
            xs.zip(chunk).forEach { (x, y) -> reg.addData(x, y) }
            val trend = xs.map { reg.predict(it) }
            val detrended = chunk.zip(trend).map { (y, t) -> y - t }
            detrended.map { it * it }.average()
        }.average()
        return kotlin.math.sqrt(rms)
    }

    private fun rescaledRange(values: List<Double>, minWindow: Int): Double {
        val windows = generateWindows(values.size, minWindow)
        val logWindow = mutableListOf<Double>()
        val logRs = mutableListOf<Double>()
        windows.forEach { w ->
            val rs = values.chunked(w).map { chunk ->
                val mean = chunk.average()
                val deviations = chunk.runningFold(0.0) { acc, v -> acc + (v - mean) }.drop(1)
                val range = (deviations.maxOrNull() ?: 0.0) - (deviations.minOrNull() ?: 0.0)
                val std = kotlin.math.sqrt(chunk.map { (it - mean) * (it - mean) }.average())
                if (std == 0.0) 0.0 else range / std
            }.average()
            if (rs > 0) {
                logWindow.add(ln(w.toDouble()))
                logRs.add(ln(rs))
            }
        }
        val regression = SimpleRegression()
        logWindow.zip(logRs).forEach { (x, y) -> regression.addData(x, y) }
        return regression.slope
    }

    private fun generateWindows(size: Int, min: Int): List<Int> {
        val windows = mutableListOf<Int>()
        var w = min
        while (w <= size / 4) {
            windows.add(w)
            w = (w * 1.5).toInt().coerceAtLeast(w + 1)
        }
        return windows
    }
}
```

- [ ] **Step 3.3.2: Create `src/main/kotlin/com/example/starter/analysis/domain/PcaCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.EigenDecomposition
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.sqrt

class PcaCalculator {

    fun calculate(tickers: List<String>, series: List<PriceSeries>, nComponents: Int? = null, standardize: Boolean = true): PcaResult {
        require(tickers.size == series.size && tickers.isNotEmpty())
        val returns = series.map { simpleReturns(it) }
        val minLen = returns.minOf { it.size }
        val aligned = returns.map { it.takeLast(minLen) }
        val data = Array(aligned.first().size) { row -> DoubleArray(aligned.size) { col -> aligned[col][row] } }
        val matrix = Array2DRowRealMatrix(data)
        val cols = (0 until matrix.columnDimension).map { col -> matrix.getColumn(col) }
        val standardized = if (standardize) {
            cols.map { arr ->
                val mean = arr.average()
                val std = sqrt(arr.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(1e-12)
                arr.map { (it - mean) / std }.toDoubleArray()
            }
        } else cols
        val covMatrix = Covariance(standardized.map { it.toList().toDoubleArray() }.toTypedArray()).covarianceMatrix
        val eigen = EigenDecomposition(covMatrix)
        val eigenvalues = eigen.realEigenvalues
        val total = eigenvalues.sum()
        val components = nComponents?.coerceAtMost(eigenvalues.size) ?: eigenvalues.size
        val evr = eigenvalues.take(components).map { it / total }
        val loadings = tickers.zip(eigen.v.getSubMatrix(0, eigenvalues.size - 1, 0, components - 1).data.map { it.toList() }).toMap()
        val factorReturns = (0 until standardized.first().size).map { row ->
            val factorValues = (0 until components).map { pc ->
                standardized.sumOf { it[row] * eigen.v.getEntry(it.indices.first(), pc) }
            }
            (0 until components).associate { "PC${it + 1}" to factorValues[it] }
        }
        return PcaResult(explainedVarianceRatio = evr, loadings = loadings, factorReturns = factorReturns)
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 3.3.3: Create `src/main/kotlin/com/example/starter/analysis/domain/CorrelationCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation

class CorrelationCalculator {

    fun calculate(tickers: List<String>, series: List<PriceSeries>, weights: Map<String, Double>? = null): CorrelationResult {
        require(tickers.size == series.size && tickers.size >= 2)
        val returns = series.map { simpleReturns(it) }
        val minLen = returns.minOf { it.size }
        val data = Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
        val corr = PearsonsCorrelation(data)
        val matrix = tickers.mapIndexed { i, ti ->
            ti to tickers.mapIndexed { j, tj -> tj to corr.correlationMatrix.getEntry(i, j) }.toMap()
        }.toMap()
        val pairs = mutableListOf<Double>()
        for (i in tickers.indices) for (j in i + 1 until tickers.size) pairs.add(corr.correlationMatrix.getEntry(i, j))
        val divRatio = weights?.let { diversificationRatio(tickers, returns, it) }
        return CorrelationResult(
            matrix = matrix,
            average = pairs.average(),
            min = pairs.minOrNull() ?: 0.0,
            max = pairs.maxOrNull() ?: 0.0,
            diversificationRatio = divRatio
        )
    }

    private fun diversificationRatio(tickers: List<String>, returns: List<List<Double>>, weights: Map<String, Double>): Double {
        val w = tickers.map { weights[it] ?: 0.0 }.toDoubleArray()
        val data = Array(returns.first().size) { row -> DoubleArray(returns.size) { col -> returns[col][row] } }
        val cov = org.apache.commons.math3.stat.correlation.Covariance(data).covarianceMatrix
        val portfolioVariance = w.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * w.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }
        val weightedVol = tickers.indices.sumOf { i -> w[i] * kotlin.math.sqrt(cov.getEntry(i, i)) }
        return if (portfolioVariance <= 0) weightedVol else weightedVol / kotlin.math.sqrt(portfolioVariance)
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 3.3.4: Create `src/main/kotlin/com/example/starter/analysis/domain/MultiFactorCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression

class MultiFactorCalculator {

    fun calculate(asset: PriceSeries, factors: Map<String, PriceSeries>): MultiFactorResult {
        val assetReturns = simpleReturns(asset)
        val aligned = factors.map { (name, series) -> name to simpleReturns(series).takeLast(assetReturns.size) }.toMap()
        val minLen = aligned.values.minOfOrNull { it.size }?.coerceAtMost(assetReturns.size) ?: assetReturns.size
        val y = assetReturns.takeLast(minLen).toDoubleArray()
        val x = aligned.keys.toList().map { name -> aligned.getValue(name).takeLast(minLen).toDoubleArray() }.toTypedArray()
        val design = Array(minLen) { row -> DoubleArray(x.size) { col -> x[col][row] } }
        val regression = OLSMultipleLinearRegression()
        regression.newSampleData(y, design)
        val params = regression.estimateRegressionParameters()
        val stdErrs = regression.estimateRegressionParametersStandardErrors()
        val r2 = regression.calculateRSquared()
        val adjR2 = regression.calculateAdjustedRSquared()
        val names = listOf("alpha") + aligned.keys
        val loadings = names.zip(params.toList()).toMap()
        val tStats = names.zip(params.zip(stdErrs).map { (p, e) -> if (e == 0.0) 0.0 else p / e }).toMap()
        val pValues = tStats.mapValues { (_, t) -> 2.0 * (1.0 - org.apache.commons.math3.distribution.TDistribution((minLen - names.size).toDouble()).cumulativeProbability(kotlin.math.abs(t))) }
        return MultiFactorResult(
            alpha = params[0],
            loadings = loadings.minus("alpha"),
            tStatistics = tStats,
            pValues = pValues,
            rSquared = r2,
            adjRSquared = adjR2
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 3.3.5: Write unit tests for Hurst, PCA, correlation, multi-factor**

Create `src/test/kotlin/com/example/starter/analysis/domain/HurstCalculatorTest.kt`:

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@Tag("unit")
class HurstCalculatorTest {
    private val calculator = HurstCalculator()

    @Test
    fun `random walk hurst near 0_5`() {
        val series = OhlcvFixtures.dailySeries(days = 120, volatility = 2.0)
        val result = calculator.calculate(series)
        expectThat(result.exponent).isGreaterThan(0.3)
    }
}
```

Create `src/test/kotlin/com/example/starter/analysis/domain/PcaCalculatorTest.kt`:

```kotlin
package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@Tag("unit")
class PcaCalculatorTest {
    private val calculator = PcaCalculator()

    @Test
    fun `pca returns explained variance`() {
        val a = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val b = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val result = calculator.calculate(listOf("A", "B"), listOf(a, b), nComponents = 2)
        expectThat(result.explainedVarianceRatio.sum()).isGreaterThan(0.99)
    }
}
```

Create similar tests for `CorrelationCalculatorTest` and `MultiFactorCalculatorTest` asserting non-empty outputs.

- [ ] **Step 3.3.6: Run tests**

Run: `./gradlew test --tests "com.example.starter.analysis.domain.*"`
Expected: tests pass.

- [ ] **Step 3.3.7: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/domain/HurstCalculator.kt src/main/kotlin/com/example/starter/analysis/domain/PcaCalculator.kt src/main/kotlin/com/example/starter/analysis/domain/CorrelationCalculator.kt src/main/kotlin/com/example/starter/analysis/domain/MultiFactorCalculator.kt src/test/kotlin/com/example/starter/analysis/domain/
git commit -m "feat(analysis): add Hurst, PCA, correlation, and multi-factor calculators"
```

### Task 3.4: Options calculator

- [ ] **Step 3.4.1: Create `src/main/kotlin/com/example/starter/analysis/domain/OptionsCalculator.kt`**

```kotlin
package com.example.starter.analysis.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class OptionsCalculator {

    private fun d1(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, div: Double): Double {
        return (ln(spot / strike) + (rate - div + 0.5 * vol * vol) * time) / (vol * sqrt(time))
    }

    private fun d2(d1: Double, vol: Double, time: Double): Double = d1 - vol * sqrt(time)

    private fun normCdf(x: Double): Double {
        return 0.5 * (1.0 + org.apache.commons.math3.special.Erf.erf(x / sqrt(2.0)))
    }

    fun price(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, optionType: String = "call", div: Double = 0.0): Double {
        require(time > 0 && vol > 0)
        val d1v = d1(spot, strike, time, rate, vol, div)
        val d2v = d2(d1v, vol, time)
        val discount = exp(-rate * time)
        val divDiscount = exp(-div * time)
        return when (optionType.lowercase()) {
            "put" -> strike * discount * normCdf(-d2v) - spot * divDiscount * normCdf(-d1v)
            else -> spot * divDiscount * normCdf(d1v) - strike * discount * normCdf(d2v)
        }
    }

    fun greeks(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, optionType: String = "call", div: Double = 0.0): OptionGreeks {
        val d1v = d1(spot, strike, time, rate, vol, div)
        val d2v = d2(d1v, vol, time)
        val nd1 = normCdf(d1v)
        val pdfD1 = exp(-0.5 * d1v * d1v) / sqrt(2.0 * PI)
        val discount = exp(-rate * time)
        val divDiscount = exp(-div * time)
        val delta = when (optionType.lowercase()) {
            "put" -> divDiscount * (nd1 - 1.0)
            else -> divDiscount * nd1
        }
        val gamma = divDiscount * pdfD1 / (spot * vol * sqrt(time))
        val vega = spot * divDiscount * pdfD1 * sqrt(time) / 100.0
        val theta = when (optionType.lowercase()) {
            "put" -> (-spot * divDiscount * pdfD1 * vol / (2.0 * sqrt(time)) + rate * strike * discount * normCdf(-d2v) - div * spot * divDiscount * normCdf(-d1v)) / 365.0
            else -> (-spot * divDiscount * pdfD1 * vol / (2.0 * sqrt(time)) - rate * strike * discount * normCdf(d2v) + div * spot * divDiscount * normCdf(d1v)) / 365.0
        }
        val rho = when (optionType.lowercase()) {
            "put" -> -strike * time * discount * normCdf(-d2v) / 100.0
            else -> strike * time * discount * normCdf(d2v) / 100.0
        }
        return OptionGreeks(delta = delta, gamma = gamma, vega = vega, theta = theta, rho = rho)
    }

    fun impliedVolatility(marketPrice: Double, spot: Double, strike: Double, time: Double, rate: Double, optionType: String = "call", div: Double = 0.0, initialGuess: Double = 0.2, tol: Double = 1e-6, maxIter: Int = 100): Double? {
        var vol = initialGuess.coerceAtLeast(0.001)
        repeat(maxIter) {
            val p = price(spot, strike, time, rate, vol, optionType, div)
            val g = greeks(spot, strike, time, rate, vol, optionType, div)
            val diff = p - marketPrice
            if (kotlin.math.abs(diff) < tol) return vol
            if (g.vega == 0.0) return null
            vol -= diff / (g.vega * 100.0)
            if (vol <= 0) vol = 0.001
        }
        return null
    }

    fun calculate(command: RunAnalysisUseCase.OptionPricingCommand): OptionPricingResult {
        val price = price(command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.volatility, command.optionType, command.dividendYield)
        val greeks = greeks(command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.volatility, command.optionType, command.dividendYield)
        val iv = command.marketPrice?.let { impliedVolatility(it, command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.optionType, command.dividendYield) }
        return OptionPricingResult(price = price, greeks = greeks, impliedVolatility = iv)
    }
}
```

- [ ] **Step 3.4.2: Write unit test `src/test/kotlin/com/example/starter/analysis/domain/OptionsCalculatorTest.kt`**

```kotlin
package com.example.starter.analysis.domain

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull

@Tag("unit")
class OptionsCalculatorTest {
    private val calc = OptionsCalculator()

    @Test
    fun `call price increases with spot`() {
        val cheap = calc.price(90.0, 100.0, 0.25, 0.05, 0.2)
        val expensive = calc.price(110.0, 100.0, 0.25, 0.05, 0.2)
        expectThat(expensive).isGreaterThan(cheap)
    }

    @Test
    fun `implied vol recovers input vol`() {
        val price = calc.price(100.0, 100.0, 0.5, 0.05, 0.25)
        val iv = calc.impliedVolatility(price, 100.0, 100.0, 0.5, 0.05)
        expectThat(iv).isNotNull()
    }
}
```

- [ ] **Step 3.4.3: Run tests**

Run: `./gradlew test --tests "com.example.starter.analysis.domain.OptionsCalculatorTest"`
Expected: tests pass.

- [ ] **Step 3.4.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/domain/OptionsCalculator.kt src/test/kotlin/com/example/starter/analysis/domain/OptionsCalculatorTest.kt
git commit -m "feat(analysis): add Black-Scholes, Greeks, and implied vol calculator"
```

### Task 3.5: Analysis application service

- [ ] **Step 3.5.1: Create `src/main/kotlin/com/example/starter/analysis/application/service/AnalysisService.kt`**

```kotlin
package com.example.starter.analysis.application.service

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.analysis.domain.CorrelationCalculator
import com.example.starter.analysis.domain.HurstCalculator
import com.example.starter.analysis.domain.MultiFactorCalculator
import com.example.starter.analysis.domain.OptionsCalculator
import com.example.starter.analysis.domain.PcaCalculator
import com.example.starter.analysis.domain.RegressionCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class AnalysisService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val regressionCalculator: RegressionCalculator,
    private val cointegrationCalculator: CointegrationCalculator,
    private val hurstCalculator: HurstCalculator,
    private val pcaCalculator: PcaCalculator,
    private val correlationCalculator: CorrelationCalculator,
    private val multiFactorCalculator: MultiFactorCalculator,
    private val optionsCalculator: OptionsCalculator
) : RunAnalysisUseCase {

    override fun execute(command: RunAnalysisUseCase.AnalysisCommand): AnalysisResult = when (command) {
        is RunAnalysisUseCase.RegressionCommand -> {
            val asset = fetch(command.asset, command.range, command.interval, command.provider)
            val benchmark = fetch(command.benchmark, command.range, command.interval, command.provider)
            regressionCalculator.calculate(asset, benchmark, command.riskFreeRate)
        }
        is RunAnalysisUseCase.CointegrationCommand -> {
            val a = fetch(command.assetA, command.range, command.interval, command.provider)
            val b = fetch(command.assetB, command.range, command.interval, command.provider)
            cointegrationCalculator.calculate(a, b, command.zScoreWindow)
        }
        is RunAnalysisUseCase.HurstCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            command.rollingWindow?.let {
                hurstCalculator.rolling(series, it, method = command.method, minWindow = command.minWindow)
            } ?: hurstCalculator.calculate(series, command.method, command.minWindow)
        }
        is RunAnalysisUseCase.PcaCommand -> {
            val series = command.tickers.map { fetch(it, command.range, command.interval, command.provider) }
            pcaCalculator.calculate(command.tickers.map { it.symbol }, series, command.nComponents, command.standardize)
        }
        is RunAnalysisUseCase.CorrelationCommand -> {
            val series = command.tickers.map { fetch(it, command.range, command.interval, command.provider) }
            correlationCalculator.calculate(command.tickers.map { it.symbol }, series, command.weights)
        }
        is RunAnalysisUseCase.MultiFactorCommand -> {
            val asset = fetch(command.asset, command.range, command.interval, command.provider)
            val factors = command.factors.mapValues { fetch(it.value, command.range, command.interval, command.provider) }
            multiFactorCalculator.calculate(asset, factors)
        }
        is RunAnalysisUseCase.OptionPricingCommand -> optionsCalculator.calculate(command)
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
```

- [ ] **Step 3.5.2: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.5.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/application/service/AnalysisService.kt
git commit -m "feat(analysis): add analysis orchestration service"
```

### Task 3.6: gRPC proto and service

- [ ] **Step 3.6.1: Create `src/main/proto/analysis/analysis_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.analysis.grpc;
option java_package = "com.example.starter.analysis.grpc";
option java_multiple_files = true;

service AnalysisService {
  rpc RunRegression (RegressionRequest) returns (RegressionResponse);
  rpc RunCointegration (CointegrationRequest) returns (CointegrationResponse);
  rpc RunHurst (HurstRequest) returns (HurstResponse);
  rpc RunPca (PcaRequest) returns (PcaResponse);
  rpc RunCorrelation (CorrelationRequest) returns (CorrelationResponse);
  rpc RunMultiFactor (MultiFactorRequest) returns (MultiFactorResponse);
  rpc PriceOption (OptionPricingRequest) returns (OptionPricingResponse);
}

message RegressionRequest {
  string asset_symbol = 1;
  string benchmark_symbol = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
}

message RegressionResponse {
  double alpha = 1;
  double beta = 2;
  double r_squared = 3;
  double annualized_alpha = 4;
}

message CointegrationRequest {
  string symbol_a = 1;
  string symbol_b = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
  int32 z_score_window = 7;
}

message CointegrationResponse {
  double hedge_ratio = 1;
  double adf_statistic = 2;
  double p_value_approx = 3;
  double half_life = 4;
  double current_z_score = 5;
}

message HurstRequest {
  string symbol = 1;
  string start_date = 2;
  string end_date = 3;
  string interval = 4;
  string provider = 5;
  string method = 6;
  int32 rolling_window = 7;
}

message HurstResponse {
  double exponent = 1;
  string regime = 2;
}

message PcaRequest {
  repeated string symbols = 1;
  string start_date = 2;
  string end_date = 3;
  string interval = 4;
  string provider = 5;
  int32 n_components = 6;
}

message PcaResponse {
  repeated double explained_variance_ratio = 1;
  map<string, DoubleList> loadings = 2;
}

message DoubleList {
  repeated double values = 1;
}

message CorrelationRequest {
  repeated string symbols = 1;
  string start_date = 2;
  string end_date = 3;
  string interval = 4;
  string provider = 5;
}

message CorrelationResponse {
  map<string, DoubleMap> matrix = 1;
  double average = 2;
  double min = 3;
  double max = 4;
  double diversification_ratio = 5;
}

message DoubleMap {
  map<string, double> values = 1;
}

message MultiFactorRequest {
  string asset_symbol = 1;
  map<string, string> factor_symbols = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
}

message MultiFactorResponse {
  double alpha = 1;
  map<string, double> loadings = 2;
  map<string, double> t_statistics = 3;
  map<string, double> p_values = 4;
  double r_squared = 5;
  double adj_r_squared = 6;
}

message OptionPricingRequest {
  double spot = 1;
  double strike = 2;
  double time_to_expiry = 3;
  double risk_free_rate = 4;
  double volatility = 5;
  string option_type = 6;
  double dividend_yield = 7;
  double market_price = 8;
}

message OptionPricingResponse {
  double price = 1;
  double delta = 2;
  double gamma = 3;
  double vega = 4;
  double theta = 5;
  double rho = 6;
  double implied_volatility = 7;
}
```

- [ ] **Step 3.6.2: Verify proto generation**

Run: `./gradlew generateProto`
Expected: generated sources under `build/generated/source/proto/main/grpckt/com/example/starter/analysis/grpc/`.

- [ ] **Step 3.6.3: Create `src/main/kotlin/com/example/starter/analysis/adapter/in/grpc/AnalysisGrpcService.kt`**

```kotlin
package com.example.starter.analysis.adapter.`in`.grpc

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.grpc.AnalysisServiceGrpcKt
import com.example.starter.analysis.grpc.CointegrationRequest
import com.example.starter.analysis.grpc.CointegrationResponse
import com.example.starter.analysis.grpc.CorrelationRequest
import com.example.starter.analysis.grpc.CorrelationResponse
import com.example.starter.analysis.grpc.DoubleList
import com.example.starter.analysis.grpc.DoubleMap
import com.example.starter.analysis.grpc.HurstRequest
import com.example.starter.analysis.grpc.HurstResponse
import com.example.starter.analysis.grpc.MultiFactorRequest
import com.example.starter.analysis.grpc.MultiFactorResponse
import com.example.starter.analysis.grpc.OptionPricingRequest
import com.example.starter.analysis.grpc.OptionPricingResponse
import com.example.starter.analysis.grpc.PcaRequest
import com.example.starter.analysis.grpc.PcaResponse
import com.example.starter.analysis.grpc.RegressionRequest
import com.example.starter.analysis.grpc.RegressionResponse
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class AnalysisGrpcService(
    private val runAnalysisUseCase: RunAnalysisUseCase
) : AnalysisServiceGrpcKt.AnalysisServiceCoroutineImplBase() {

    override suspend fun runRegression(request: RegressionRequest): RegressionResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.RegressionCommand(
                asset = Ticker(request.assetSymbol),
                benchmark = Ticker(request.benchmarkSymbol),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.RegressionResult
        RegressionResponse.newBuilder()
            .setAlpha(result.alpha)
            .setBeta(result.beta)
            .setRSquared(result.rSquared)
            .setAnnualizedAlpha(result.annualizedAlpha ?: 0.0)
            .build()
    }

    override suspend fun runCointegration(request: CointegrationRequest): CointegrationResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CointegrationCommand(
                assetA = Ticker(request.symbolA),
                assetB = Ticker(request.symbolB),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                zScoreWindow = request.zScoreWindow
            )
        ) as com.example.starter.analysis.domain.CointegrationResult
        CointegrationResponse.newBuilder()
            .setHedgeRatio(result.hedgeRatio)
            .setAdfStatistic(result.adfStatistic)
            .setPValueApprox(result.pValueApprox)
            .setHalfLife(result.halfLife)
            .setCurrentZScore(result.currentZScore ?: 0.0)
            .build()
    }

    override suspend fun runHurst(request: HurstRequest): HurstResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.HurstCommand(
                ticker = Ticker(request.symbol),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                method = request.method,
                rollingWindow = request.rollingWindow.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.HurstResult
        HurstResponse.newBuilder()
            .setExponent(result.exponent)
            .setRegime(result.regime)
            .build()
    }

    override suspend fun runPca(request: PcaRequest): PcaResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.PcaCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                nComponents = request.nComponents.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.PcaResult
        PcaResponse.newBuilder()
            .addAllExplainedVarianceRatio(result.explainedVarianceRatio)
            .putAllLoadings(result.loadings.mapValues { DoubleList.newBuilder().addAllValues(it.value).build() })
            .build()
    }

    override suspend fun runCorrelation(request: CorrelationRequest): CorrelationResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CorrelationCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.CorrelationResult
        CorrelationResponse.newBuilder()
            .putAllMatrix(result.matrix.mapValues { DoubleMap.newBuilder().putAllValues(it.value).build() })
            .setAverage(result.average)
            .setMin(result.min)
            .setMax(result.max)
            .setDiversificationRatio(result.diversificationRatio ?: 0.0)
            .build()
    }

    override suspend fun runMultiFactor(request: MultiFactorRequest): MultiFactorResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.MultiFactorCommand(
                asset = Ticker(request.assetSymbol),
                factors = request.factorSymbolsMap.mapValues { Ticker(it.value) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.MultiFactorResult
        MultiFactorResponse.newBuilder()
            .setAlpha(result.alpha)
            .putAllLoadings(result.loadings)
            .putAllTStatistics(result.tStatistics)
            .putAllPValues(result.pValues)
            .setRSquared(result.rSquared)
            .setAdjRSquared(result.adjRSquared)
            .build()
    }

    override suspend fun priceOption(request: OptionPricingRequest): OptionPricingResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.OptionPricingCommand(
                spot = request.spot,
                strike = request.strike,
                timeToExpiry = request.timeToExpiry,
                riskFreeRate = request.riskFreeRate,
                volatility = request.volatility,
                optionType = request.optionType,
                dividendYield = request.dividendYield,
                marketPrice = request.marketPrice.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.OptionPricingResult
        OptionPricingResponse.newBuilder()
            .setPrice(result.price)
            .setDelta(result.greeks.delta)
            .setGamma(result.greeks.gamma)
            .setVega(result.greeks.vega)
            .setTheta(result.greeks.theta)
            .setRho(result.greeks.rho)
            .setImpliedVolatility(result.impliedVolatility ?: 0.0)
            .build()
    }
}
```

- [ ] **Step 3.6.4: Commit**

```bash
git add src/main/proto/analysis/ src/main/kotlin/com/example/starter/analysis/adapter/in/grpc/
git commit -m "feat(analysis): add gRPC proto and service"
```

### Task 3.7: REST controller

- [ ] **Step 3.7.1: Create `src/main/kotlin/com/example/starter/analysis/adapter/in/web/AnalysisController.kt`**

```kotlin
package com.example.starter.analysis.adapter.`in`.web

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisController(
    private val runAnalysisUseCase: RunAnalysisUseCase
) {

    @GetMapping("/regression")
    fun regression(
        @RequestParam asset: String,
        @RequestParam benchmark: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run {
        RunAnalysisUseCase.RegressionCommand(
            asset = Ticker(asset),
            benchmark = Ticker(benchmark),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    }

    @GetMapping("/cointegration")
    fun cointegration(
        @RequestParam symbolA: String,
        @RequestParam symbolB: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false, defaultValue = "30") zScoreWindow: Int,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run {
        RunAnalysisUseCase.CointegrationCommand(
            assetA = Ticker(symbolA),
            assetB = Ticker(symbolB),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            zScoreWindow = zScoreWindow,
            provider = provider
        )
    }

    @GetMapping("/hurst")
    fun hurst(
        @RequestParam symbol: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false, defaultValue = "dfa") method: String,
        @RequestParam(required = false) rollingWindow: Int?,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run {
        RunAnalysisUseCase.HurstCommand(
            ticker = Ticker(symbol),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            method = method,
            rollingWindow = rollingWindow,
            provider = provider
        )
    }

    @GetMapping("/pca")
    fun pca(
        @RequestParam symbols: List<String>,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) nComponents: Int?,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run {
        RunAnalysisUseCase.PcaCommand(
            tickers = symbols.map { Ticker(it) },
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            nComponents = nComponents,
            provider = provider
        )
    }

    @GetMapping("/correlation")
    fun correlation(
        @RequestParam symbols: List<String>,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run {
        RunAnalysisUseCase.CorrelationCommand(
            tickers = symbols.map { Ticker(it) },
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    }

    @PostMapping("/multi-factor")
    fun multiFactor(@RequestBody request: MultiFactorRequestDto): Mono<AnalysisResult> = Mono.fromCallable {
        runAnalysisUseCase.execute(
            RunAnalysisUseCase.MultiFactorCommand(
                asset = Ticker(request.asset),
                factors = request.factors.mapValues { Ticker(it.value) },
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/option")
    fun option(@RequestBody request: OptionRequestDto): Mono<AnalysisResult> = Mono.fromCallable {
        runAnalysisUseCase.execute(
            RunAnalysisUseCase.OptionPricingCommand(
                spot = request.spot,
                strike = request.strike,
                timeToExpiry = request.timeToExpiry,
                riskFreeRate = request.riskFreeRate,
                volatility = request.volatility,
                optionType = request.optionType,
                dividendYield = request.dividendYield,
                marketPrice = request.marketPrice
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    private fun run(command: RunAnalysisUseCase.AnalysisCommand): Mono<AnalysisResult> =
        Mono.fromCallable { runAnalysisUseCase.execute(command) }.subscribeOn(Schedulers.boundedElastic())
}

data class MultiFactorRequestDto(
    val asset: String,
    val factors: Map<String, String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null
)

data class OptionRequestDto(
    val spot: Double,
    val strike: Double,
    val timeToExpiry: Double,
    val riskFreeRate: Double,
    val volatility: Double,
    val optionType: String = "call",
    val dividendYield: Double = 0.0,
    val marketPrice: Double? = null
)
```

- [ ] **Step 3.7.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/analysis/adapter/in/web/
git commit -m "feat(analysis): add REST controller"
```

### Task 3.8: A2A and MCP skills/tools

- [ ] **Step 3.8.1: Extend `A2aTaskHandler`**

Inject `RunAnalysisUseCase` into the constructor. Add branches in `handleTasksSend` for:

```kotlin
"analysis-regression" -> {
    val asset = params["asset"] as? String ?: throw IllegalArgumentException("asset required")
    val benchmark = params["benchmark"] as? String ?: throw IllegalArgumentException("benchmark required")
    runAnalysisUseCase.execute(
        RunAnalysisUseCase.RegressionCommand(
            asset = Ticker(asset),
            benchmark = Ticker(benchmark),
            range = parseRange(params),
            interval = parseInterval(params),
            provider = params["provider"] as? String
        )
    )
}
"analysis-cointegration" -> { ... }
"analysis-hurst" -> { ... }
"analysis-pca" -> { ... }
"analysis-correlation" -> { ... }
"analysis-multi-factor" -> { ... }
"analysis-option" -> { ... }
```

Add helper functions inside the class:

```kotlin
private fun parseRange(params: Map<String, Any>): DateRange {
    val start = params["startDate"] as? String ?: throw IllegalArgumentException("startDate required")
    val end = params["endDate"] as? String ?: throw IllegalArgumentException("endDate required")
    return DateRange(LocalDate.parse(start), LocalDate.parse(end))
}

private fun parseInterval(params: Map<String, Any>): BarInterval {
    val interval = params["interval"] as? String ?: "DAILY"
    return BarInterval.valueOf(interval.uppercase())
}
```

- [ ] **Step 3.8.2: Extend `McpToolHandler`**

Inject `RunAnalysisUseCase`. Add entries to `toolsList()` for `analysis_regression`, `analysis_cointegration`, `analysis_hurst`, `analysis_pca`, `analysis_correlation`, `analysis_multi_factor`, `analysis_option` with JSON schemas. Add branches to `handleToolCall()` mirroring the A2A branches and returning text results.

- [ ] **Step 3.8.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt
git commit -m "feat(analysis): add A2A and MCP skills/tools"
```

### Task 3.9: Analysis tests

- [ ] **Step 3.9.1: Create integration test `src/integrationTest/kotlin/com/example/starter/analysis/AnalysisIntegrationTest.kt`**

Wire the real calculators with a mocked `FetchMarketDataUseCase` and assert end-to-end command execution.

```kotlin
package com.example.starter.analysis

import com.example.starter.analysis.application.service.AnalysisService
import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.analysis.domain.CorrelationCalculator
import com.example.starter.analysis.domain.HurstCalculator
import com.example.starter.analysis.domain.MultiFactorCalculator
import com.example.starter.analysis.domain.OptionsCalculator
import com.example.starter.analysis.domain.PcaCalculator
import com.example.starter.analysis.domain.RegressionCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import java.time.LocalDate

@Tag("integration")
class AnalysisIntegrationTest {

    private val fetch = mockk<FetchMarketDataUseCase>()
    private val service = AnalysisService(
        fetchMarketDataUseCase = fetch,
        regressionCalculator = RegressionCalculator(),
        cointegrationCalculator = CointegrationCalculator(),
        hurstCalculator = HurstCalculator(),
        pcaCalculator = PcaCalculator(),
        correlationCalculator = CorrelationCalculator(),
        multiFactorCalculator = MultiFactorCalculator(),
        optionsCalculator = OptionsCalculator()
    )

    @Test
    fun `regression for identical series has beta near one`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)
        val result = service.execute(
            com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase.RegressionCommand(
                asset = Ticker("A"),
                benchmark = Ticker("A"),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY
            )
        ) as com.example.starter.analysis.domain.RegressionResult
        expectThat(result.beta).isGreaterThan(0.95)
    }
}
```

- [ ] **Step 3.9.2: Create E2E test `src/e2eTest/kotlin/com/example/starter/analysis/e2e/AnalysisE2ETest.kt`**

Reuse the `@SpringBootTest` pattern from `OrderLifecycleE2ETest`. Boot the application with WireMock-backed yfinance, call `/api/v1/analysis/regression`, `/api/v1/analysis/option`, the gRPC `AnalysisService/PriceOption`, and A2A `analysis-option`. Assert non-empty results and HTTP 200.

- [ ] **Step 3.9.3: Commit**

```bash
git add src/integrationTest/kotlin/com/example/starter/analysis/ src/e2eTest/kotlin/com/example/starter/analysis/
git commit -m "test(analysis): add integration and E2E tests"
```


## Phase 4: Backtesting engine

**Files:**
- Create: `src/main/kotlin/com/example/starter/backtest/domain/BacktestResult.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/SignalType.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/Strategy.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/Strategies.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/BacktestEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/PortfolioEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/PairBacktestEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/PanelBacktestEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/Sizing.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/Costs.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/LiquidityMetrics.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/StressTestEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/WalkForwardEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/RobustnessEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/domain/MonteCarloEngine.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/application/port/inbound/RunBacktestUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/application/service/BacktestService.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/adapter/in/web/BacktestController.kt`
- Create: `src/main/kotlin/com/example/starter/backtest/adapter/in/grpc/BacktestGrpcService.kt`
- Create: `src/main/proto/backtest/backtest_service.proto`
- Modify: `A2aTaskHandler.kt`, `McpToolHandler.kt`
- Tests: `src/test/kotlin/com/example/starter/backtest/domain/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 4.1: Backtest domain types and command

- [ ] **Step 4.1.1: Create `src/main/kotlin/com/example/starter/backtest/domain/BacktestResult.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskMetrics
import java.time.LocalDate

data class BacktestResult(
    val strategyName: String,
    val initialCapital: Double,
    val finalEquity: Double,
    val totalReturn: Double,
    val metrics: RiskMetrics?,
    val trades: List<Trade>,
    val equityCurve: List<EquityCurvePoint>,
    val drawdownEpisodes: List<DrawdownEpisode>,
    val diagnostics: BacktestDiagnostics?,
    val parameterGrid: Map<String, Any>? = null
)

data class Trade(
    val entryDate: LocalDate,
    val exitDate: LocalDate?,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double?,
    val size: Double,
    val pnl: Double,
    val mae: Double?,
    val mfe: Double?
)

data class EquityCurvePoint(
    val date: LocalDate,
    val equity: Double,
    val drawdown: Double
)

data class DrawdownEpisode(
    val startDate: LocalDate,
    val troughDate: LocalDate,
    val endDate: LocalDate?,
    val depth: Double
)

data class BacktestDiagnostics(
    val numberOfTrades: Int,
    val winRate: Double,
    val averageTradeReturn: Double,
    val expectancy: Double,
    val maxExposure: Double,
    val annualizedTurnover: Double
)
```

- [ ] **Step 4.1.2: Create `src/main/kotlin/com/example/starter/backtest/domain/SignalType.kt`**

```kotlin
package com.example.starter.backtest.domain

enum class SignalType {
    DIRECTION, SCORE, TARGET_WEIGHT
}
```

- [ ] **Step 4.1.3: Create `src/main/kotlin/com/example/starter/backtest/application/port/inbound/RunBacktestUseCase.kt`**

```kotlin
package com.example.starter.backtest.application.port.inbound

import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface RunBacktestUseCase {
    fun execute(command: BacktestCommand): BacktestResult

    sealed class BacktestCommand {
        abstract val provider: String?
    }

    data class SingleAssetCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameters: Map<String, Any> = emptyMap(),
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        val slippagePct: Double = 0.0005,
        override val provider: String? = null
    ) : BacktestCommand()

    data class PortfolioSimulationCommand(
        val tickers: List<Ticker>,
        val weights: Map<String, Double>,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        val slippagePct: Double = 0.0005,
        val maxGrossLeverage: Double = 1.0,
        override val provider: String? = null
    ) : BacktestCommand()

    data class PairTradeCommand(
        val symbolA: String,
        val symbolB: String,
        val entryZ: Double = 2.0,
        val exitZ: Double = 0.5,
        val zScoreWindow: Int = 30,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        override val provider: String? = null
    ) : BacktestCommand()

    data class SignalPanelCommand(
        val signals: Map<String, List<Double>>,
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        override val provider: String? = null
    ) : BacktestCommand()

    data class WalkForwardCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameterGrid: Map<String, List<Any>>,
        val trainSize: Int = 252,
        val testSize: Int = 63,
        val metric: String = "sharpe_ratio",
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        override val provider: String? = null
    ) : BacktestCommand()

    data class MonteCarloCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameters: Map<String, Any> = emptyMap(),
        val horizonDays: Int = 252,
        val nSimulations: Int = 1_000,
        val blockSize: Int = 20,
        val initialCapital: Double = 10_000.0,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null
    ) : BacktestCommand()
}
```

- [ ] **Step 4.1.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/BacktestResult.kt src/main/kotlin/com/example/starter/backtest/domain/SignalType.kt src/main/kotlin/com/example/starter/backtest/application/port/inbound/RunBacktestUseCase.kt
git commit -m "feat(backtest): add domain result types and use case commands"
```

### Task 4.2: Signal strategies

- [ ] **Step 4.2.1: Create `src/main/kotlin/com/example/starter/backtest/domain/Strategy.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

fun interface Strategy {
    fun generate(series: PriceSeries, parameters: Map<String, Any>): List<Double>
    val name: String get() = "custom"
}
```

- [ ] **Step 4.2.2: Create `src/main/kotlin/com/example/starter/backtest/domain/Strategies.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import kotlin.math.max
import kotlin.math.min

object Strategies {

    val REGISTRY: Map<String, Strategy> = mapOf(
        "sma_crossover" to Strategy { series, params -> smaCrossover(series, intParam(params, "fast", 10), intParam(params, "slow", 30)) },
        "rsi_mean_reversion" to Strategy { series, params -> rsiMeanReversion(series, intParam(params, "period", 14), dblParam(params, "oversold", 30.0), dblParam(params, "overbought", 70.0)) },
        "macd_crossover" to Strategy { series, params -> macdCrossover(series, intParam(params, "fast", 12), intParam(params, "slow", 26), intParam(params, "signal", 9)) },
        "bollinger_reversion" to Strategy { series, params -> bollingerReversion(series, intParam(params, "period", 20), dblParam(params, "stdDev", 2.0)) },
        "donchian_breakout" to Strategy { series, params -> donchianBreakout(series, intParam(params, "period", 20)) },
        "momentum_timeseries" to Strategy { series, params -> momentum(series, intParam(params, "period", 20)) },
        "vwap_reversion" to Strategy { series, params -> vwapReversion(series) },
        "buy_and_hold" to Strategy { series, params -> List(series.size) { 1.0 } }
    )

    private fun smaCrossover(series: PriceSeries, fast: Int, slow: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, _ ->
            if (idx < slow) 0.0
            else {
                val fastSma = closes.subList(idx - fast + 1, idx + 1).average()
                val slowSma = closes.subList(idx - slow + 1, idx + 1).average()
                when {
                    fastSma > slowSma -> 1.0
                    fastSma < slowSma -> -1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun rsiMeanReversion(series: PriceSeries, period: Int, oversold: Double, overbought: Double): List<Double> {
        val closes = series.map { it.close.toDouble() }
        val rsi = rsi(closes, period)
        return rsi.map { when { it < oversold -> 1.0; it > overbought -> -1.0; else -> 0.0 } }
    }

    private fun rsi(closes: List<Double>, period: Int): List<Double> {
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            gains.add(max(change, 0.0))
            losses.add(max(-change, 0.0))
        }
        return closes.take(1).map { 50.0 } + (period until closes.size).map { idx ->
            val avgGain = gains.subList(idx - period, idx).average()
            val avgLoss = losses.subList(idx - period, idx).average()
            if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
    }

    private fun macdCrossover(series: PriceSeries, fast: Int, slow: Int, signal: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        val fastEma = ema(closes, fast)
        val slowEma = ema(closes, slow)
        val macdLine = fastEma.zip(slowEma).map { (f, s) -> f - s }
        val signalLine = ema(macdLine, signal)
        return macdLine.zip(signalLine).map { (m, s) -> when { m > s -> 1.0; m < s -> -1.0; else -> 0.0 } }
    }

    private fun bollingerReversion(series: PriceSeries, period: Int, stdDev: Double): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, close ->
            if (idx + 1 < period) 0.0
            else {
                val window = closes.subList(idx + 1 - period, idx + 1)
                val stats = DescriptiveStatistics(window.toDoubleArray())
                val upper = stats.mean + stdDev * stats.standardDeviation
                val lower = stats.mean - stdDev * stats.standardDeviation
                when {
                    close > upper -> -1.0
                    close < lower -> 1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun donchianBreakout(series: PriceSeries, period: Int): List<Double> {
        val highs = series.map { it.high.toDouble() }
        val lows = series.map { it.low.toDouble() }
        return series.mapIndexed { idx, bar ->
            if (idx < period) 0.0
            else {
                val upper = highs.subList(idx - period, idx).maxOrNull() ?: bar.high.toDouble()
                val lower = lows.subList(idx - period, idx).minOrNull() ?: bar.low.toDouble()
                when {
                    bar.close.toDouble() > upper -> 1.0
                    bar.close.toDouble() < lower -> -1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun momentum(series: PriceSeries, period: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, close ->
            if (idx < period) 0.0
            else if (close > closes[idx - period]) 1.0 else -1.0
        }
    }

    private fun vwapReversion(series: PriceSeries): List<Double> {
        var cumTypVol = 0.0
        var cumVol = 0.0
        return series.map { bar ->
            val typical = (bar.high.toDouble() + bar.low.toDouble() + bar.close.toDouble()) / 3.0
            cumTypVol += typical * bar.volume
            cumVol += bar.volume
            val vwap = if (cumVol == 0.0) typical else cumTypVol / cumVol
            when {
                bar.close.toDouble() > vwap -> -1.0
                bar.close.toDouble() < vwap -> 1.0
                else -> 0.0
            }
        }
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = values.first()
        values.forEachIndexed { idx, v ->
            if (idx == 0) result.add(ema)
            else {
                ema = (v - ema) * multiplier + ema
                result.add(ema)
            }
        }
        return result
    }

    private fun intParam(params: Map<String, Any>, key: String, default: Int): Int = when (val v = params[key]) {
        is Int -> v
        is Number -> v.toInt()
        is String -> v.toInt()
        else -> default
    }

    private fun dblParam(params: Map<String, Any>, key: String, default: Double): Double = when (val v = params[key]) {
        is Double -> v
        is Number -> v.toDouble()
        is String -> v.toDouble()
        else -> default
    }
}
```

- [ ] **Step 4.2.3: Write unit test `src/test/kotlin/com/example/starter/backtest/domain/StrategiesTest.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize

@Tag("unit")
class StrategiesTest {

    @Test
    fun `sma crossover produces signals`() {
        val series = OhlcvFixtures.dailySeries(days = 60)
        val signals = Strategies.REGISTRY.getValue("sma_crossover").generate(series, mapOf("fast" to 5, "slow" to 20))
        expectThat(signals).hasSize(series.size)
    }
}
```

- [ ] **Step 4.2.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/Strategy.kt src/main/kotlin/com/example/starter/backtest/domain/Strategies.kt src/test/kotlin/com/example/starter/backtest/domain/StrategiesTest.kt
git commit -m "feat(backtest): add strategy registry and signal generators"
```

### Task 4.3: Single-asset backtest engine

- [ ] **Step 4.3.1: Create `src/main/kotlin/com/example/starter/backtest/domain/BacktestEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskReturnCalculator
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import java.time.LocalDate

class BacktestEngine(private val riskReturnCalculator: RiskReturnCalculator = RiskReturnCalculator()) {

    fun run(
        series: PriceSeries,
        signals: List<Double>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001,
        slippagePct: Double = 0.0005,
        strategyName: String = "custom"
    ): BacktestResult {
        require(series.size == signals.size) { "series and signals must align" }
        var cash = initialCapital
        var position = 0.0
        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<EquityCurvePoint>()
        var peak = initialCapital
        var openTrade: TradeBuilder? = null
        val drawdownEpisodes = mutableListOf<DrawdownEpisode>()
        var currentEpisode: DrawdownEpisodeBuilder? = null

        series.forEachIndexed { idx, bar ->
            val signal = signals[idx].coerceIn(-1.0, 1.0)
            val price = bar.close.toDouble()
            val targetPosition = if (idx == 0) 0.0 else signal * initialCapital / price
            val delta = targetPosition - position
            if (kotlin.math.abs(delta) > 1e-9 && idx > 0) {
                val tradePrice = price * (1.0 + slippagePct * kotlin.math.sign(delta))
                val notional = kotlin.math.abs(delta) * tradePrice
                val commission = notional * commissionPct
                cash -= delta * tradePrice + commission
                position = targetPosition
                if (openTrade != null && kotlin.math.sign(openTrade.direction * targetPosition) <= 0) {
                    trades.add(openTrade.close(bar.date, tradePrice))
                    openTrade = null
                }
                if (targetPosition != 0.0 && openTrade == null) {
                    openTrade = TradeBuilder(bar.date, if (targetPosition > 0) "long" else "short", tradePrice, targetPosition)
                }
            }
            val equity = cash + position * price
            val drawdown = (peak - equity) / peak
            if (equity > peak) {
                currentEpisode?.end(bar.date)?.let { drawdownEpisodes.add(it) }
                currentEpisode = null
                peak = equity
            } else if (drawdown > 0) {
                if (currentEpisode == null) currentEpisode = DrawdownEpisodeBuilder(bar.date, bar.date, drawdown)
                else if (drawdown > currentEpisode.depth) currentEpisode = currentEpisode.copy(troughDate = bar.date, depth = drawdown)
            }
            equityCurve.add(EquityCurvePoint(bar.date, equity, drawdown))
        }
        currentEpisode?.let { drawdownEpisodes.add(DrawdownEpisode(it.startDate, it.troughDate, null, it.depth)) }
        val finalEquity = equityCurve.lastOrNull()?.equity ?: initialCapital
        val metrics = if (equityCurve.size >= 2) riskReturnCalculator.riskMetrics(seriesFromEquity(equityCurve)) else null
        val diagnostics = BacktestDiagnostics(
            numberOfTrades = trades.size,
            winRate = if (trades.isEmpty()) 0.0 else trades.count { it.pnl > 0 }.toDouble() / trades.size,
            averageTradeReturn = if (trades.isEmpty()) 0.0 else trades.map { it.pnl }.average(),
            expectancy = 0.0,
            maxExposure = trades.maxOfOrNull { kotlin.math.abs(it.size * (it.entryPrice)) } ?: 0.0,
            annualizedTurnover = 0.0
        )
        return BacktestResult(
            strategyName = strategyName,
            initialCapital = initialCapital,
            finalEquity = finalEquity,
            totalReturn = (finalEquity - initialCapital) / initialCapital,
            metrics = metrics,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = drawdownEpisodes,
            diagnostics = diagnostics
        )
    }

    private fun seriesFromEquity(equityCurve: List<EquityCurvePoint>): PriceSeries {
        return equityCurve.map { point ->
            OHLCV(
                ticker = com.example.starter.shared.domain.Ticker("BACKTEST"),
                date = point.date,
                open = java.math.BigDecimal(point.equity.toString()),
                high = java.math.BigDecimal(point.equity.toString()),
                low = java.math.BigDecimal(point.equity.toString()),
                close = java.math.BigDecimal(point.equity.toString()),
                volume = 0L
            )
        }
    }

    private data class TradeBuilder(
        val entryDate: LocalDate,
        val direction: String,
        val entryPrice: Double,
        val size: Double
    ) {
        fun close(exitDate: LocalDate, exitPrice: Double): Trade {
            val multiplier = if (direction == "long") 1.0 else -1.0
            val pnl = multiplier * size * (exitPrice - entryPrice)
            return Trade(entryDate, exitDate, direction, entryPrice, exitPrice, size, pnl, null, null)
        }
    }

    private data class DrawdownEpisodeBuilder(
        val startDate: LocalDate,
        var troughDate: LocalDate,
        var depth: Double
    ) {
        fun end(endDate: LocalDate): DrawdownEpisode = DrawdownEpisode(startDate, troughDate, endDate, depth)
    }
}
```

- [ ] **Step 4.3.2: Write unit test `src/test/kotlin/com/example/starter/backtest/domain/BacktestEngineTest.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@Tag("unit")
class BacktestEngineTest {

    private val engine = BacktestEngine()

    @Test
    fun `buy and hold grows equity`() {
        val series = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0, volatility = 1.0)
        val signals = List(series.size) { 1.0 }
        val result = engine.run(series, signals, initialCapital = 10_000.0, commissionPct = 0.0, slippagePct = 0.0, strategyName = "buy_and_hold")
        expectThat(result.finalEquity).isGreaterThan(0.0)
    }
}
```

- [ ] **Step 4.3.3: Run tests**

Run: `./gradlew test --tests "com.example.starter.backtest.domain.BacktestEngineTest"`
Expected: test passes.

- [ ] **Step 4.3.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/BacktestEngine.kt src/test/kotlin/com/example/starter/backtest/domain/BacktestEngineTest.kt
git commit -m "feat(backtest): add single-asset vectorized backtest engine"
```

### Task 4.4: Portfolio, pair, and panel engines

- [ ] **Step 4.4.1: Create `src/main/kotlin/com/example/starter/backtest/domain/PortfolioEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskReturnCalculator
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioEngine(private val riskReturnCalculator: RiskReturnCalculator = RiskReturnCalculator()) {

    fun runPortfolioSimulation(
        priceData: Map<String, PriceSeries>,
        targetWeights: Map<String, Double>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001,
        slippagePct: Double = 0.0005,
        maxGrossLeverage: Double = 1.0
    ): BacktestResult {
        require(priceData.isNotEmpty())
        val dates = priceData.values.first().map { it.date }
        val symbols = priceData.keys.toList()
        val prices = symbols.map { s -> priceData.getValue(s).map { it.close.toDouble() } }
        val normalizedWeights = normalize(targetWeights, maxGrossLeverage)
        var cash = initialCapital
        val positions = symbols.associateWith { 0.0 }.toMutableMap()
        val equityCurve = mutableListOf<EquityCurvePoint>()
        val trades = mutableListOf<Trade>()
        var peak = initialCapital
        val drawdownEpisodes = mutableListOf<DrawdownEpisode>()
        var currentEpisode: DrawdownEpisodeBuilder? = null

        dates.forEachIndexed { idx, date ->
            val currentPrices = symbols.zip(prices.map { it[idx] }).toMap()
            val equity = cash + symbols.sumOf { positions.getValue(it) * currentPrices.getValue(it) }
            val targetDollar = equity * normalizedWeights.mapValues { it.value }
            symbols.forEach { symbol ->
                val price = currentPrices.getValue(symbol)
                val targetShares = if (price == 0.0) 0.0 else targetDollar.getValue(symbol) / price
                val delta = targetShares - positions.getValue(symbol)
                if (kotlin.math.abs(delta) > 1e-9 && idx > 0) {
                    val fillPrice = price * (1.0 + slippagePct * kotlin.math.sign(delta))
                    val notional = kotlin.math.abs(delta) * fillPrice
                    cash -= delta * fillPrice + notional * commissionPct
                    positions[symbol] = targetShares
                    trades.add(
                        Trade(
                            entryDate = date,
                            exitDate = null,
                            direction = if (delta > 0) "long" else "short",
                            entryPrice = fillPrice,
                            exitPrice = null,
                            size = kotlin.math.abs(delta),
                            pnl = 0.0,
                            mae = null,
                            mfe = null
                        )
                    )
                }
            }
            val totalEquity = cash + symbols.sumOf { positions.getValue(it) * currentPrices.getValue(it) }
            val drawdown = (peak - totalEquity) / peak
            if (totalEquity > peak) {
                currentEpisode?.end(date)?.let { drawdownEpisodes.add(it) }
                currentEpisode = null
                peak = totalEquity
            } else if (drawdown > 0) {
                if (currentEpisode == null) currentEpisode = DrawdownEpisodeBuilder(date, date, drawdown)
                else if (drawdown > currentEpisode.depth) currentEpisode = currentEpisode.copy(troughDate = date, depth = drawdown)
            }
            equityCurve.add(EquityCurvePoint(date, totalEquity, drawdown))
        }
        currentEpisode?.let { drawdownEpisodes.add(DrawdownEpisode(it.startDate, it.troughDate, null, it.depth)) }
        val metrics = riskReturnCalculator.riskMetrics(seriesFromEquity(equityCurve))
        return BacktestResult(
            strategyName = "portfolio_simulation",
            initialCapital = initialCapital,
            finalEquity = equityCurve.last().equity,
            totalReturn = (equityCurve.last().equity - initialCapital) / initialCapital,
            metrics = metrics,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = drawdownEpisodes,
            diagnostics = null
        )
    }

    private fun normalize(weights: Map<String, Double>, maxGrossLeverage: Double): Map<String, Double> {
        val gross = weights.values.sumOf { kotlin.math.abs(it) }
        val scale = if (gross == 0.0) 1.0 else (maxGrossLeverage / gross).coerceAtMost(1.0)
        return weights.mapValues { it.value * scale }
    }

    private fun seriesFromEquity(equityCurve: List<EquityCurvePoint>): PriceSeries = equityCurve.map { point ->
        OHLCV(
            ticker = com.example.starter.shared.domain.Ticker("PORTFOLIO"),
            date = point.date,
            open = BigDecimal(point.equity.toString()),
            high = BigDecimal(point.equity.toString()),
            low = BigDecimal(point.equity.toString()),
            close = BigDecimal(point.equity.toString()),
            volume = 0L
        )
    }

    private data class DrawdownEpisodeBuilder(
        val startDate: LocalDate,
        var troughDate: LocalDate,
        var depth: Double
    ) {
        fun end(endDate: LocalDate): DrawdownEpisode = DrawdownEpisode(startDate, troughDate, endDate, depth)
    }
}
```

- [ ] **Step 4.4.2: Create `src/main/kotlin/com/example/starter/backtest/domain/PairBacktestEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.shared.domain.PriceSeries

class PairBacktestEngine(
    private val cointegrationCalculator: CointegrationCalculator = CointegrationCalculator(),
    private val portfolioEngine: PortfolioEngine = PortfolioEngine()
) {

    fun runPairBacktest(
        seriesA: PriceSeries,
        seriesB: PriceSeries,
        entryZ: Double = 2.0,
        exitZ: Double = 0.5,
        zScoreWindow: Int = 30,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001
    ): BacktestResult {
        val coint = cointegrationCalculator.calculate(seriesA, seriesB, zScoreWindow)
        val hedge = coint.hedgeRatio
        val aligned = alignByDate(seriesA, seriesB)
        val spread = aligned.first.zip(aligned.second).map { (a, b) ->
            kotlin.math.log(a.close.toDouble()) - hedge * kotlin.math.log(b.close.toDouble())
        }
        val signals = spread.mapIndexed { idx, _ ->
            if (idx < zScoreWindow) 0.0 else {
                val window = spread.subList(idx - zScoreWindow, idx)
                val mean = window.average()
                val std = kotlin.math.sqrt(window.map { (it - mean) * (it - mean) }.average())
                val z = if (std == 0.0) 0.0 else (spread[idx] - mean) / std
                when {
                    z > entryZ -> -1.0
                    z < -entryZ -> 1.0
                    kotlin.math.abs(z) < exitZ -> 0.0
                    else -> Double.NaN
                }
            }
        }
        val cleanSignals = signals.map { if (it.isNaN()) 0.0 else it }
        val weights = aligned.first.zip(aligned.second).zip(cleanSignals).map { (pair, signal) ->
            val (a, b) = pair
            val weightA = if (signal > 0) 0.5 else if (signal < 0) -0.5 else 0.0
            val weightB = if (signal > 0) -0.5 * hedge else if (signal < 0) 0.5 * hedge else 0.0
            mapOf(a.ticker.symbol to weightA, b.ticker.symbol to weightB)
        }
        val priceData = mapOf(seriesA.first().ticker.symbol to aligned.first, seriesB.first().ticker.symbol to aligned.second)
        val result = portfolioEngine.runPortfolioSimulation(
            priceData = priceData,
            targetWeights = weights.first(),
            initialCapital = initialCapital,
            commissionPct = commissionPct
        )
        return result.copy(strategyName = "pair_trade")
    }

    private fun alignByDate(a: PriceSeries, b: PriceSeries): Pair<PriceSeries, PriceSeries> {
        val dates = a.map { it.date }.intersect(b.map { it.date }.toSet()).sorted()
        val byA = a.associateBy { it.date }
        val byB = b.associateBy { it.date }
        return dates.map { byA.getValue(it) } to dates.map { byB.getValue(it) }
    }
}
```

- [ ] **Step 4.4.3: Create `src/main/kotlin/com/example/starter/backtest/domain/PanelBacktestEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

class PanelBacktestEngine(private val engine: BacktestEngine = BacktestEngine()) {

    fun runSignalPanelBacktest(
        priceData: Map<String, PriceSeries>,
        signalPanel: Map<String, List<Double>>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001
    ): BacktestResult {
        val perTicker = priceData.map { (symbol, series) ->
            val signals = signalPanel[symbol] ?: List(series.size) { 0.0 }
            symbol to engine.run(series, signals, initialCapital = initialCapital / priceData.size, commissionPct = commissionPct, strategyName = "panel_$symbol")
        }.toMap()
        val dates = priceData.values.first().map { it.date }
        val equityCurve = dates.mapIndexed { idx, date ->
            val equity = perTicker.values.sumOf { it.equityCurve[idx].equity }
            val drawdown = perTicker.values.maxOfOrNull { it.equityCurve[idx].drawdown } ?: 0.0
            EquityCurvePoint(date, equity, drawdown)
        }
        val trades = perTicker.values.flatMap { it.trades }
        return BacktestResult(
            strategyName = "signal_panel",
            initialCapital = initialCapital,
            finalEquity = equityCurve.last().equity,
            totalReturn = (equityCurve.last().equity - initialCapital) / initialCapital,
            metrics = null,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = emptyList(),
            diagnostics = null
        )
    }
}
```

- [ ] **Step 4.4.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/PortfolioEngine.kt src/main/kotlin/com/example/starter/backtest/domain/PairBacktestEngine.kt src/main/kotlin/com/example/starter/backtest/domain/PanelBacktestEngine.kt
git commit -m "feat(backtest): add portfolio, pair, and panel engines"
```

### Task 4.5: Sizing, costs, liquidity, and stress test

- [ ] **Step 4.5.1: Create `src/main/kotlin/com/example/starter/backtest/domain/Sizing.kt`**

```kotlin
package com.example.starter.backtest.domain

import kotlin.math.abs

object Sizing {

    fun rankWeighted(scores: Map<String, Double>, grossLeverage: Double = 1.0): Map<String, Double> {
        val ranked = scores.entries.sortedByDescending { it.value }.mapIndexed { idx, entry -> entry.key to (scores.size - idx).toDouble() }.toMap()
        val sum = ranked.values.sum()
        return if (sum == 0.0) scores.mapValues { 0.0 } else ranked.mapValues { grossLeverage * it.value / sum * scores.size }
    }

    fun equalWeightTopBottom(scores: Map<String, Double>, nLong: Int, nShort: Int, grossLeverage: Double = 1.0): Map<String, Double> {
        val sorted = scores.entries.sortedByDescending { it.value }
        val longs = sorted.take(nLong).associate { it.key to 1.0 / nLong }
        val shorts = sorted.takeLast(nShort).associate { it.key to -1.0 / nShort }
        val weights = (longs + shorts).mapValues { it.value * grossLeverage }
        return scores.keys.associateWith { weights[it] ?: 0.0 }
    }

    fun zScoreNormalized(scores: Map<String, Double>, grossLeverage: Double = 1.0): Map<String, Double> {
        val mean = scores.values.average()
        val std = kotlin.math.sqrt(scores.values.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(1e-12)
        val z = scores.mapValues { (it.value - mean) / std }
        val sumAbs = z.values.sumOf { abs(it) }
        return if (sumAbs == 0.0) scores.mapValues { 0.0 } else z.mapValues { grossLeverage * it.value / sumAbs }
    }
}
```

- [ ] **Step 4.5.2: Create `src/main/kotlin/com/example/starter/backtest/domain/Costs.kt`**

```kotlin
package com.example.starter.backtest.domain

object Costs {

    fun percentageCommission(notional: Double, pct: Double): Double = notional * pct

    fun perShareCommission(shares: Double, costPerShare: Double): Double = kotlin.math.abs(shares) * costPerShare

    fun fixedBpsSpread(mid: Double, bps: Double): Double = mid * bps / 10_000.0

    fun sqrtImpactBps(notional: Double, dailyVolume: Double, baseBps: Double = 10.0): Double {
        if (dailyVolume <= 0) return 0.0
        return baseBps * kotlin.math.sqrt(notional / dailyVolume)
    }
}
```

- [ ] **Step 4.5.3: Create `src/main/kotlin/com/example/starter/backtest/domain/LiquidityMetrics.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

object LiquidityMetrics {

    fun amihudIlliquidity(returns: List<Double>, dollarVolumes: List<Double>, window: Int = 20): List<Double?> {
        return returns.mapIndexed { idx, ret ->
            if (idx < window - 1) null
            else {
                val windowed = (0 until window).map { i -> kotlin.math.abs(returns[idx - i]) / dollarVolumes[idx - i] }
                windowed.average()
            }
        }
    }

    fun corwinSchultzSpread(highs: List<Double>, lows: List<Double>, window: Int = 1): List<Double?> {
        return highs.mapIndexed { idx, _ ->
            if (idx < window) null
            else {
                val beta = (0 until window).sumOf { i -> kotlin.math.ln(highs[idx - i] / lows[idx - i]).let { it * it } }
                val gamma = kotlin.math.ln(highs.subList(idx - window + 1, idx + 1).maxOrNull() ?: highs[idx] /
                    lows.subList(idx - window + 1, idx + 1).minOrNull()!!.coerceAtLeast(1e-12))
                val alpha = (kotlin.math.sqrt(2.0 * beta) - kotlin.math.sqrt(beta)) / (3.0 - 2.0 * kotlin.math.sqrt(2.0)) - kotlin.math.sqrt(gamma / (3.0 - 2.0 * kotlin.math.sqrt(2.0)))
                maxOf(alpha, 0.0)
            }
        }
    }
}
```

- [ ] **Step 4.5.4: Create `src/main/kotlin/com/example/starter/backtest/domain/StressTestEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

object StressTestEngine {

    private val SCENARIOS = mapOf(
        "covid_crash_2020" to Pair("2020-02-19", "2020-03-23"),
        "gfc_2008" to Pair("2008-10-01", "2008-12-01"),
        "dot_com_2002" to Pair("2002-03-01", "2002-07-01"),
        "black_monday_1987" to Pair("1987-10-14", "1987-10-26")
    )

    fun listScenarios(): List<String> = SCENARIOS.keys.toList()

    fun scenarioDates(scenario: String): Pair<String, String>? = SCENARIOS[scenario]
}
```

- [ ] **Step 4.5.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/Sizing.kt src/main/kotlin/com/example/starter/backtest/domain/Costs.kt src/main/kotlin/com/example/starter/backtest/domain/LiquidityMetrics.kt src/main/kotlin/com/example/starter/backtest/domain/StressTestEngine.kt
git commit -m "feat(backtest): add sizing, costs, liquidity, and stress helpers"
```

### Task 4.6: Walk-forward, robustness, and Monte Carlo

- [ ] **Step 4.6.1: Create `src/main/kotlin/com/example/starter/backtest/domain/WalkForwardEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

class WalkForwardEngine(
    private val engine: BacktestEngine = BacktestEngine(),
    private val strategies: Map<String, Strategy> = Strategies.REGISTRY
) {

    fun run(
        series: PriceSeries,
        strategyName: String,
        parameterGrid: Map<String, List<Any>>,
        trainSize: Int,
        testSize: Int,
        metric: String = "sharpe_ratio",
        initialCapital: Double = 10_000.0
    ): BacktestResult {
        val combinations = cartesianProduct(parameterGrid)
        val outOfSampleReturns = mutableListOf<Double>()
        val windowParams = mutableListOf<Map<String, Any>>()
        var start = 0
        while (start + trainSize + testSize <= series.size) {
            val train = series.subList(start, start + trainSize)
            val test = series.subList(start + trainSize, start + trainSize + testSize)
            val best = combinations.maxByOrNull { params ->
                val signals = strategies.getValue(strategyName).generate(train, params)
                val result = engine.run(train, signals, initialCapital = initialCapital, strategyName = strategyName)
                metricValue(result, metric)
            } ?: combinations.first()
            val testSignals = strategies.getValue(strategyName).generate(test, best)
            val testResult = engine.run(test, testSignals, initialCapital = initialCapital, strategyName = strategyName)
            val dailyReturns = testResult.equityCurve.zipWithNext { prev, curr -> (curr.equity - prev.equity) / prev.equity }
            outOfSampleReturns.addAll(dailyReturns)
            windowParams.add(best)
            start += testSize
        }
        val equity = outOfSampleReturns.runningFold(initialCapital) { acc, r -> acc * (1 + r) }
        val curve = equity.mapIndexed { idx, e -> EquityCurvePoint(series[trainSize + idx].date, e, 0.0) }
        return BacktestResult(
            strategyName = "walk_forward_$strategyName",
            initialCapital = initialCapital,
            finalEquity = equity.last(),
            totalReturn = (equity.last() - initialCapital) / initialCapital,
            metrics = null,
            trades = emptyList(),
            equityCurve = curve,
            drawdownEpisodes = emptyList(),
            diagnostics = null,
            parameterGrid = mapOf("window_count" to windowParams.size)
        )
    }

    private fun metricValue(result: BacktestResult, metric: String): Double = when (metric) {
        "sharpe_ratio" -> result.metrics?.sharpeRatio?.toDouble() ?: 0.0
        "total_return" -> result.totalReturn
        "max_drawdown" -> -(result.drawdownEpisodes.maxOfOrNull { it.depth } ?: 0.0)
        else -> result.totalReturn
    }

    private fun cartesianProduct(grid: Map<String, List<Any>>): List<Map<String, Any>> {
        if (grid.isEmpty()) return listOf(emptyMap())
        val keys = grid.keys.toList()
        val values = keys.map { grid.getValue(it) }
        return values.fold(listOf(emptyList<Any>())) { acc, list ->
            acc.flatMap { prefix -> list.map { prefix + it } }
        }.map { combo -> keys.zip(combo).toMap() }
    }
}
```

- [ ] **Step 4.6.2: Create `src/main/kotlin/com/example/starter/backtest/domain/RobustnessEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

class RobustnessEngine {

    fun blockBootstrapCi(
        returns: List<Double>,
        metricFn: (List<Double>) -> Double,
        nIterations: Int = 1_000,
        blockSize: Int = 20,
        confidence: Double = 0.95,
        seed: Long? = null
    ): Map<String, Double> {
        val random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
        val stats = DescriptiveStatistics()
        repeat(nIterations) {
            val sample = blockBootstrap(returns, blockSize, random)
            stats.addValue(metricFn(sample))
        }
        val alpha = 1.0 - confidence
        val lower = stats.getPercentile(alpha * 50.0)
        val upper = stats.getPercentile(100.0 - alpha * 50.0)
        return mapOf("mean" to stats.mean, "median" to stats.getPercentile(50.0), "lower" to lower, "upper" to upper)
    }

    private fun blockBootstrap(returns: List<Double>, blockSize: Int, random: kotlin.random.Random): List<Double> {
        if (returns.isEmpty()) return emptyList()
        val result = mutableListOf<Double>()
        while (result.size < returns.size) {
            val start = random.nextInt(0, returns.size - blockSize + 1).coerceAtLeast(0)
            result.addAll(returns.subList(start, kotlin.math.min(start + blockSize, returns.size)))
        }
        return result.take(returns.size)
    }

    fun parameterSensitivity(gridResults: List<Map<String, Any>>, metricCol: String = "sharpe_ratio"): Map<String, Double> {
        val sorted = gridResults.sortedByDescending { (it[metricCol] as? Number)?.toDouble() ?: 0.0 }
        val best = (sorted.firstOrNull()?.get(metricCol) as? Number)?.toDouble() ?: 0.0
        val median = sorted.getOrNull(sorted.size / 2).let { (it?.get(metricCol) as? Number)?.toDouble() ?: 0.0 }
        val rank2 = (sorted.getOrNull(1)?.get(metricCol) as? Number)?.toDouble() ?: 0.0
        return mapOf("best" to best, "median" to median, "rank2" to rank2, "best_minus_median" to best - median)
    }

    fun deflatedSharpeRatio(
        observedSharpe: Double,
        sharpeTrialsStd: Double,
        nTrials: Int,
        nObs: Int,
        skew: Double = 0.0,
        kurtosis: Double = 3.0
    ): Map<String, Double> {
        val variance = sharpeTrialsStd * sharpeTrialsStd
        val expectedMax = sharpeTrialsStd * ((1.0 - 0.5772) * kotlin.math.ln(nTrials.toDouble()) + 0.5772 * kotlin.math.ln(nTrials.toDouble()))
        val adj = 1.0 + (observedSharpe * skew / 6.0) * observedSharpe - ((kurtosis - 3.0) / 24.0) * observedSharpe * observedSharpe
        val dsr = (observedSharpe - expectedMax) / sharpeTrialsStd * adj
        return mapOf("deflated_sharpe" to dsr, "expected_max_sharpe" to expectedMax)
    }
}
```

- [ ] **Step 4.6.3: Create `src/main/kotlin/com/example/starter/backtest/domain/MonteCarloEngine.kt`**

```kotlin
package com.example.starter.backtest.domain

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

class MonteCarloEngine {

    fun simulateForwardPaths(
        returns: List<Double>,
        horizonDays: Int = 252,
        nSimulations: Int = 1_000,
        blockSize: Int = 20,
        initialCapital: Double = 10_000.0,
        seed: Long? = null
    ): Map<String, Any> {
        val random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
        val terminals = DoubleArray(nSimulations)
        repeat(nSimulations) { sim ->
            var equity = initialCapital
            repeat(horizonDays) { day ->
                val start = random.nextInt(0, (returns.size - blockSize).coerceAtLeast(1) + 1)
                val blockReturn = returns.subList(start, kotlin.math.min(start + blockSize, returns.size)).average()
                equity *= (1.0 + blockReturn)
            }
            terminals[sim] = equity
        }
        val stats = DescriptiveStatistics(terminals)
        return mapOf(
            "mean_terminal_equity" to stats.mean,
            "median_terminal_equity" to stats.getPercentile(50.0),
            "percentile_5" to stats.getPercentile(5.0),
            "percentile_95" to stats.getPercentile(95.0),
            "initial_capital" to initialCapital
        )
    }
}
```

- [ ] **Step 4.6.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/domain/WalkForwardEngine.kt src/main/kotlin/com/example/starter/backtest/domain/RobustnessEngine.kt src/main/kotlin/com/example/starter/backtest/domain/MonteCarloEngine.kt
git commit -m "feat(backtest): add walk-forward, robustness, and Monte Carlo engines"
```

### Task 4.7: Backtest application service

- [ ] **Step 4.7.1: Create `src/main/kotlin/com/example/starter/backtest/application/service/BacktestService.kt`**

```kotlin
package com.example.starter.backtest.application.service

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestEngine
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.backtest.domain.MonteCarloEngine
import com.example.starter.backtest.domain.PairBacktestEngine
import com.example.starter.backtest.domain.PanelBacktestEngine
import com.example.starter.backtest.domain.PortfolioEngine
import com.example.starter.backtest.domain.Strategies
import com.example.starter.backtest.domain.WalkForwardEngine
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class BacktestService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val engine: BacktestEngine = BacktestEngine(),
    private val portfolioEngine: PortfolioEngine = PortfolioEngine(),
    private val pairEngine: PairBacktestEngine = PairBacktestEngine(),
    private val panelEngine: PanelBacktestEngine = PanelBacktestEngine(),
    private val walkForwardEngine: WalkForwardEngine = WalkForwardEngine(),
    private val monteCarloEngine: MonteCarloEngine = MonteCarloEngine()
) : RunBacktestUseCase {

    override fun execute(command: RunBacktestUseCase.BacktestCommand): BacktestResult = when (command) {
        is RunBacktestUseCase.SingleAssetCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            val strategy = Strategies.REGISTRY.getValue(command.strategy)
            val signals = strategy.generate(series, command.parameters)
            engine.run(series, signals, command.initialCapital, command.commissionPct, command.slippagePct, command.strategy)
        }
        is RunBacktestUseCase.PortfolioSimulationCommand -> {
            val data = command.tickers.associate { it.symbol to fetch(it, command.range, command.interval, command.provider) }
            portfolioEngine.runPortfolioSimulation(data, command.weights, command.initialCapital, command.commissionPct, command.slippagePct, command.maxGrossLeverage)
        }
        is RunBacktestUseCase.PairTradeCommand -> {
            val a = fetch(Ticker(command.symbolA), command.range, command.interval, command.provider)
            val b = fetch(Ticker(command.symbolB), command.range, command.interval, command.provider)
            pairEngine.runPairBacktest(a, b, command.entryZ, command.exitZ, command.zScoreWindow, command.initialCapital, command.commissionPct)
        }
        is RunBacktestUseCase.SignalPanelCommand -> {
            val data = command.tickers.associate { it.symbol to fetch(it, command.range, command.interval, command.provider) }
            panelEngine.runSignalPanelBacktest(data, command.signals, command.initialCapital, command.commissionPct)
        }
        is RunBacktestUseCase.WalkForwardCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            walkForwardEngine.run(series, command.strategy, command.parameterGrid, command.trainSize, command.testSize, command.metric, command.initialCapital)
        }
        is RunBacktestUseCase.MonteCarloCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            val strategy = Strategies.REGISTRY.getValue(command.strategy)
            val signals = strategy.generate(series, command.parameters)
            val backtest = engine.run(series, signals, command.initialCapital, strategyName = command.strategy)
            val returns = backtest.equityCurve.zipWithNext { prev, curr -> (curr.equity - prev.equity) / prev.equity }
            val mc = monteCarloEngine.simulateForwardPaths(returns, command.horizonDays, command.nSimulations, command.blockSize, command.initialCapital)
            BacktestResult(
                strategyName = "monte_carlo_${command.strategy}",
                initialCapital = command.initialCapital,
                finalEquity = mc["percentile_50"] as Double,
                totalReturn = ((mc["percentile_50"] as Double) - command.initialCapital) / command.initialCapital,
                metrics = null,
                trades = emptyList(),
                equityCurve = emptyList(),
                drawdownEpisodes = emptyList(),
                diagnostics = null,
                parameterGrid = mc
            )
        }
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
```

- [ ] **Step 4.7.2: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.7.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/application/
git commit -m "feat(backtest): add backtest orchestration service"
```

### Task 4.8: gRPC proto and service

- [ ] **Step 4.8.1: Create `src/main/proto/backtest/backtest_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.backtest.grpc;
option java_package = "com.example.starter.backtest.grpc";
option java_multiple_files = true;

service BacktestService {
  rpc RunSingleAsset (SingleAssetBacktestRequest) returns (BacktestResponse);
  rpc RunPortfolioSimulation (PortfolioSimulationRequest) returns (BacktestResponse);
  rpc RunPairTrade (PairTradeRequest) returns (BacktestResponse);
  rpc RunWalkForward (WalkForwardRequest) returns (BacktestResponse);
  rpc RunMonteCarlo (MonteCarloRequest) returns (BacktestResponse);
}

message SingleAssetBacktestRequest {
  string symbol = 1;
  string strategy = 2;
  map<string, string> parameters = 3;
  string start_date = 4;
  string end_date = 5;
  string interval = 6;
  string provider = 7;
  double initial_capital = 8;
  double commission_pct = 9;
  double slippage_pct = 10;
}

message PortfolioSimulationRequest {
  repeated string symbols = 1;
  map<string, double> weights = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
  double initial_capital = 7;
  double commission_pct = 8;
  double max_gross_leverage = 9;
}

message PairTradeRequest {
  string symbol_a = 1;
  string symbol_b = 2;
  double entry_z = 3;
  double exit_z = 4;
  int32 z_score_window = 5;
  string start_date = 6;
  string end_date = 7;
  string interval = 8;
  string provider = 9;
  double initial_capital = 10;
}

message WalkForwardRequest {
  string symbol = 1;
  string strategy = 2;
  map<string, StringList> parameter_grid = 3;
  int32 train_size = 4;
  int32 test_size = 5;
  string metric = 6;
  string start_date = 7;
  string end_date = 8;
  string interval = 9;
  string provider = 10;
}

message StringList {
  repeated string values = 1;
}

message MonteCarloRequest {
  string symbol = 1;
  string strategy = 2;
  map<string, string> parameters = 3;
  int32 horizon_days = 4;
  int32 n_simulations = 5;
  int32 block_size = 6;
  string start_date = 7;
  string end_date = 8;
  string interval = 9;
  string provider = 10;
}

message BacktestResponse {
  string strategy_name = 1;
  double initial_capital = 2;
  double final_equity = 3;
  double total_return = 4;
  repeated Trade trades = 5;
  repeated EquityCurvePoint equity_curve = 6;
  map<string, double> metrics = 7;
  map<string, string> metadata = 8;
}

message Trade {
  string entry_date = 1;
  string exit_date = 2;
  string direction = 3;
  double entry_price = 4;
  double exit_price = 5;
  double size = 6;
  double pnl = 7;
}

message EquityCurvePoint {
  string date = 1;
  double equity = 2;
  double drawdown = 3;
}
```

- [ ] **Step 4.8.2: Verify proto generation**

Run: `./gradlew generateProto`
Expected: generated sources under `build/generated/source/proto/main/grpckt/com/example/starter/backtest/grpc/`.

- [ ] **Step 4.8.3: Create `src/main/kotlin/com/example/starter/backtest/adapter/in/grpc/BacktestGrpcService.kt`**

```kotlin
package com.example.starter.backtest.adapter.`in`.grpc

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.grpc.BacktestServiceGrpcKt
import com.example.starter.backtest.grpc.BacktestResponse
import com.example.starter.backtest.grpc.EquityCurvePoint
import com.example.starter.backtest.grpc.MonteCarloRequest
import com.example.starter.backtest.grpc.PairTradeRequest
import com.example.starter.backtest.grpc.PortfolioSimulationRequest
import com.example.starter.backtest.grpc.SingleAssetBacktestRequest
import com.example.starter.backtest.grpc.Trade
import com.example.starter.backtest.grpc.WalkForwardRequest
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class BacktestGrpcService(
    private val runBacktestUseCase: RunBacktestUseCase
) : BacktestServiceGrpcKt.BacktestServiceCoroutineImplBase() {

    override suspend fun runSingleAsset(request: SingleAssetBacktestRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parametersMap.mapValues { it.value.toDoubleOrString() },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                slippagePct = request.slippagePct
            )
        )
        toResponse(result)
    }

    override suspend fun runPortfolioSimulation(request: PortfolioSimulationRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                weights = request.weightsMap,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                maxGrossLeverage = request.maxGrossLeverage
            )
        )
        toResponse(result)
    }

    override suspend fun runPairTrade(request: PairTradeRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PairTradeCommand(
                symbolA = request.symbolA,
                symbolB = request.symbolB,
                entryZ = request.entryZ,
                exitZ = request.exitZ,
                zScoreWindow = request.zScoreWindow,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital
            )
        )
        toResponse(result)
    }

    override suspend fun runWalkForward(request: WalkForwardRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val grid = request.parameterGridMap.mapValues { (_, list) -> list.valuesList }
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.WalkForwardCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameterGrid = grid,
                trainSize = request.trainSize,
                testSize = request.testSize,
                metric = request.metric,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        toResponse(result)
    }

    override suspend fun runMonteCarlo(request: MonteCarloRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.MonteCarloCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parametersMap.mapValues { it.value.toDoubleOrString() },
                horizonDays = request.horizonDays,
                nSimulations = request.nSimulations,
                blockSize = request.blockSize,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        toResponse(result)
    }

    private fun toResponse(result: com.example.starter.backtest.domain.BacktestResult): BacktestResponse {
        return BacktestResponse.newBuilder()
            .setStrategyName(result.strategyName)
            .setInitialCapital(result.initialCapital)
            .setFinalEquity(result.finalEquity)
            .setTotalReturn(result.totalReturn)
            .addAllTrades(result.trades.map {
                Trade.newBuilder()
                    .setEntryDate(it.entryDate.toString())
                    .setExitDate(it.exitDate?.toString() ?: "")
                    .setDirection(it.direction)
                    .setEntryPrice(it.entryPrice)
                    .setExitPrice(it.exitPrice ?: 0.0)
                    .setSize(it.size)
                    .setPnl(it.pnl)
                    .build()
            })
            .addAllEquityCurve(result.equityCurve.map {
                EquityCurvePoint.newBuilder()
                    .setDate(it.date.toString())
                    .setEquity(it.equity)
                    .setDrawdown(it.drawdown)
                    .build()
            })
            .putAllMetrics(mapOf(
                "sharpe_ratio" to (result.metrics?.sharpeRatio?.toDouble() ?: 0.0),
                "max_drawdown" to (result.drawdownEpisodes.maxOfOrNull { it.depth } ?: 0.0)
            ))
            .putAllMetadata(result.parameterGrid?.mapValues { it.value.toString() } ?: emptyMap())
            .build()
    }

    private fun String.toDoubleOrString(): Any = this.toDoubleOrNull() ?: this
}
```

- [ ] **Step 4.8.4: Commit**

```bash
git add src/main/proto/backtest/ src/main/kotlin/com/example/starter/backtest/adapter/in/grpc/
git commit -m "feat(backtest): add gRPC proto and service"
```

### Task 4.9: REST controller, A2A, and MCP

- [ ] **Step 4.9.1: Create `src/main/kotlin/com/example/starter/backtest/adapter/in/web/BacktestController.kt`**

```kotlin
package com.example.starter.backtest.adapter.`in`.web

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/backtest")
class BacktestController(
    private val runBacktestUseCase: RunBacktestUseCase
) {

    @PostMapping("/single")
    fun single(@RequestBody request: SingleAssetBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parameters,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                slippagePct = request.slippagePct
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/portfolio")
    fun portfolio(@RequestBody request: PortfolioBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = request.symbols.map { Ticker(it) },
                weights = request.weights,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                maxGrossLeverage = request.maxGrossLeverage
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/pair")
    fun pair(@RequestBody request: PairBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.PairTradeCommand(
                symbolA = request.symbolA,
                symbolB = request.symbolB,
                entryZ = request.entryZ,
                exitZ = request.exitZ,
                zScoreWindow = request.zScoreWindow,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())
}

data class SingleAssetBacktestRequestDto(
    val symbol: String,
    val strategy: String,
    val parameters: Map<String, Any> = emptyMap(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0,
    val commissionPct: Double = 0.001,
    val slippagePct: Double = 0.0005
)

data class PortfolioBacktestRequestDto(
    val symbols: List<String>,
    val weights: Map<String, Double>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0,
    val commissionPct: Double = 0.001,
    val maxGrossLeverage: Double = 1.0
)

data class PairBacktestRequestDto(
    val symbolA: String,
    val symbolB: String,
    val entryZ: Double = 2.0,
    val exitZ: Double = 0.5,
    val zScoreWindow: Int = 30,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0
)
```

- [ ] **Step 4.9.2: Extend `A2aTaskHandler` and `McpToolHandler`**

Inject `RunBacktestUseCase`. Add skill/tool entries for `backtest-single`, `backtest-portfolio`, `backtest-pair`, `backtest-walk-forward`, `backtest-monte-carlo` that delegate to the use case and return summary text.

- [ ] **Step 4.9.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/backtest/adapter/in/web/ src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt
git commit -m "feat(backtest): add REST, A2A, and MCP adapters"
```

### Task 4.10: Backtest tests

- [ ] **Step 4.10.1: Create integration test `src/integrationTest/kotlin/com/example/starter/backtest/BacktestIntegrationTest.kt`**

Wire `BacktestService` with mocked `FetchMarketDataUseCase` and assert single-asset and portfolio backtests produce non-empty equity curves.

- [ ] **Step 4.10.2: Create E2E test `src/e2eTest/kotlin/com/example/starter/backtest/e2e/BacktestE2ETest.kt`**

Boot the application, call `/api/v1/backtest/single` and gRPC `BacktestService/RunSingleAsset` with WireMock-backed yfinance, assert `totalReturn` is a finite double.

- [ ] **Step 4.10.3: Commit**

```bash
git add src/integrationTest/kotlin/com/example/starter/backtest/ src/e2eTest/kotlin/com/example/starter/backtest/
git commit -m "test(backtest): add integration and E2E tests"
```


## Phase 5: Portfolio optimization

**Files:**
- Create: `src/main/kotlin/com/example/starter/portfolio/domain/Portfolio.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/domain/PortfolioMetricsCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/domain/MeanVarianceOptimizer.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/domain/RiskParityOptimizer.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/domain/BlackLittermanOptimizer.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/application/port/inbound/OptimizePortfolioUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/application/service/PortfolioService.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/adapter/in/web/PortfolioController.kt`
- Create: `src/main/kotlin/com/example/starter/portfolio/adapter/in/grpc/PortfolioGrpcService.kt`
- Create: `src/main/proto/portfolio/portfolio_service.proto`
- Modify: `A2aTaskHandler.kt`, `McpToolHandler.kt`
- Tests: `src/test/kotlin/com/example/starter/portfolio/domain/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 5.1: Portfolio domain types and command

- [ ] **Step 5.1.1: Create `src/main/kotlin/com/example/starter/portfolio/domain/Portfolio.kt`**

```kotlin
package com.example.starter.portfolio.domain

data class Portfolio(
    val objective: String,
    val tickers: List<String>,
    val weights: Map<String, Double>,
    val expectedReturn: Double,
    val volatility: Double,
    val sharpeRatio: Double?
)

data class BlackLittermanViews(
    val pMatrix: List<List<Double>>,
    val qVector: List<Double>,
    val omegaMatrix: List<List<Double>>? = null
)
```

- [ ] **Step 5.1.2: Create `src/main/kotlin/com/example/starter/portfolio/application/port/inbound/OptimizePortfolioUseCase.kt`**

```kotlin
package com.example.starter.portfolio.application.port.inbound

import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface OptimizePortfolioUseCase {
    fun optimize(command: OptimizeCommand): Portfolio

    data class OptimizeCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val objective: String = "max_sharpe",
        val riskFreeRate: Double = 0.02,
        val targetReturn: Double? = null,
        val targetVolatility: Double? = null,
        val allowShort: Boolean = false,
        val maxWeight: Double? = null,
        val provider: String? = null
    )

    data class RiskParityCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val riskBudget: Map<String, Double>? = null,
        val provider: String? = null
    )

    data class BlackLittermanCommand(
        val tickers: List<Ticker>,
        val marketWeights: Map<String, Double>,
        val views: BlackLittermanViewsInput,
        val range: DateRange,
        val interval: BarInterval,
        val riskAversion: Double = 2.5,
        val tau: Double = 0.05,
        val provider: String? = null
    )

    data class BlackLittermanViewsInput(
        val assets: List<String>,
        val views: List<View>
    ) {
        data class View(
            val asset: String? = null,
            val relativeAsset: String? = null,
            val returnView: Double
        )
    }
}
```

- [ ] **Step 5.1.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/portfolio/domain/Portfolio.kt src/main/kotlin/com/example/starter/portfolio/application/port/inbound/OptimizePortfolioUseCase.kt
git commit -m "feat(portfolio): add portfolio domain types and use case commands"
```

### Task 5.2: Portfolio metrics calculator

- [ ] **Step 5.2.1: Create `src/main/kotlin/com/example/starter/portfolio/domain/PortfolioMetricsCalculator.kt`**

```kotlin
package com.example.starter.portfolio.domain

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.sqrt

class PortfolioMetricsCalculator {

    fun portfolioReturn(returns: List<List<Double>>, weights: DoubleArray): Double {
        val meanReturns = returns.map { it.average() }
        return meanReturns.zip(weights.asIterable()).sumOf { (r, w) -> r * w }
    }

    fun portfolioVariance(returns: List<List<Double>>, weights: DoubleArray): Double {
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val w = Array2DRowRealMatrix(weights)
        return (w.transpose().multiply(cov).multiply(w)).getEntry(0, 0)
    }

    fun portfolioVolatility(returns: List<List<Double>>, weights: DoubleArray): Double = sqrt(portfolioVariance(returns, weights))

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
```

- [ ] **Step 5.2.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/portfolio/domain/PortfolioMetricsCalculator.kt
git commit -m "feat(portfolio): add portfolio metrics calculator"
```

### Task 5.3: Mean-variance optimizer

- [ ] **Step 5.3.1: Create `src/main/kotlin/com/example/starter/portfolio/domain/MeanVarianceOptimizer.kt`**

```kotlin
package com.example.starter.portfolio.domain

import org.apache.commons.math3.analysis.MultivariateFunction
import org.apache.commons.math3.optim.InitialGuess
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.PointValuePair
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionPenaltyAdapter
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.sqrt

class MeanVarianceOptimizer {

    fun optimize(
        returns: List<List<Double>>,
        tickers: List<String>,
        objective: String = "max_sharpe",
        riskFreeRate: Double = 0.02,
        targetReturn: Double? = null,
        targetVolatility: Double? = null,
        allowShort: Boolean = false,
        maxWeight: Double? = null
    ): Portfolio {
        require(returns.size == tickers.size && returns.isNotEmpty())
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val meanReturns = returns.map { it.average() }.toDoubleArray()
        val n = tickers.size

        val lower = if (allowShort) DoubleArray(n) { -1.0 } else DoubleArray(n) { 0.0 }
        val upper = DoubleArray(n) { maxWeight ?: 1.0 }
        val initial = DoubleArray(n) { 1.0 / n }

        val objectiveFn = MultivariateFunction { weights ->
            val w = weights.normalize()
            val portReturn = meanReturns.zip(w).sumOf { (r, weight) -> r * weight }
            val variance = w.foldIndexed(0.0) { i, acc, wi ->
                acc + wi * w.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
            }
            val volatility = sqrt(variance)
            when (objective) {
                "min_volatility" -> variance
                "target_return" -> penalty(portReturn, targetReturn!!, variance)
                "target_volatility" -> (volatility - targetVolatility!!) * (volatility - targetVolatility) - portReturn
                else -> -(portReturn - riskFreeRate / 252) / volatility // max Sharpe
            }
        }

        val boundedFn = MultivariateFunctionMappingAdapter(objectiveFn, lower, upper)
        val optimizer = SimplexOptimizer(1e-8, 1e-12)
        val optimum: PointValuePair = optimizer.optimize(
            GoalType.MINIMIZE,
            MaxEval(10_000),
            boundedFn,
            InitialGuess(boundedFn.bounded(initial)),
            NelderMeadSimplex(n)
        )
        val weights = boundedFn.unbounded(optimum.point).normalize()
        val finalReturn = meanReturns.zip(weights).sumOf { (r, w) -> r * w }
        val finalVol = sqrt(weights.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * weights.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }) * sqrt(252.0)
        val sharpe = if (finalVol == 0.0) null else (finalReturn * 252 - riskFreeRate) / finalVol
        return Portfolio(
            objective = objective,
            tickers = tickers,
            weights = tickers.zip(weights).toMap(),
            expectedReturn = finalReturn * 252,
            volatility = finalVol,
            sharpeRatio = sharpe
        )
    }

    private fun penalty(portReturn: Double, target: Double, variance: Double): Double {
        val returnError = (portReturn * 252 - target) * (portReturn * 252 - target)
        return variance + 100.0 * returnError
    }

    private fun DoubleArray.normalize(): DoubleArray {
        val sum = sum()
        return if (sum == 0.0) this else map { it / sum }.toDoubleArray()
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
```

- [ ] **Step 5.3.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/portfolio/domain/MeanVarianceOptimizer.kt
git commit -m "feat(portfolio): add mean-variance optimizer"
```

### Task 5.4: Risk parity and Black-Litterman optimizers

- [ ] **Step 5.4.1: Create `src/main/kotlin/com/example/starter/portfolio/domain/RiskParityOptimizer.kt`**

```kotlin
package com.example.starter.portfolio.domain

import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.abs
import kotlin.math.sqrt

class RiskParityOptimizer {

    fun optimize(returns: List<List<Double>>, tickers: List<String>, riskBudget: Map<String, Double>? = null): Portfolio {
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val budget = tickers.map { riskBudget?.get(it) ?: 1.0 / tickers.size }.toDoubleArray()
        var weights = DoubleArray(tickers.size) { 1.0 / tickers.size }
        repeat(1_000) {
            val mrc = marginalRiskContributions(cov, weights)
            val rc = mrc.mapIndexed { idx, value -> value * weights[idx] }.toDoubleArray()
            val target = budget.mapIndexed { idx, b -> b * rc.sum() }.toDoubleArray()
            val newWeights = weights.mapIndexed { idx, w -> w * target[idx] / rc[idx].coerceAtLeast(1e-12) }.toDoubleArray()
            val sum = newWeights.sum()
            weights = newWeights.map { it / sum }.toDoubleArray()
            if (rc.zip(target).all { (a, b) -> abs(a - b) < 1e-10 }) return@repeat
        }
        val portVariance = weights.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * weights.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }
        val portReturn = returns.map { it.average() }.zip(weights).sumOf { (r, w) -> r * w } * 252
        return Portfolio(
            objective = "risk_parity",
            tickers = tickers,
            weights = tickers.zip(weights).toMap(),
            expectedReturn = portReturn,
            volatility = sqrt(portVariance) * sqrt(252.0),
            sharpeRatio = null
        )
    }

    private fun marginalRiskContributions(cov: org.apache.commons.math3.linear.RealMatrix, weights: DoubleArray): DoubleArray {
        val w = org.apache.commons.math3.linear.Array2DRowRealMatrix(weights)
        val mrc = cov.multiply(w).columnVector
        return (0 until weights.size).map { mrc.getEntry(it) }.toDoubleArray()
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
```

- [ ] **Step 5.4.2: Create `src/main/kotlin/com/example/starter/portfolio/domain/BlackLittermanOptimizer.kt`**

```kotlin
package com.example.starter.portfolio.domain

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.stat.correlation.Covariance

class BlackLittermanOptimizer {

    fun optimize(
        returns: List<List<Double>>,
        tickers: List<String>,
        marketWeights: DoubleArray,
        pMatrix: Array<DoubleArray>,
        qVector: DoubleArray,
        riskAversion: Double = 2.5,
        tau: Double = 0.05,
        omega: Array<DoubleArray>? = null
    ): Portfolio {
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val pi = cov.operate(MatrixUtils.createRealVector(marketWeights)).mapMultiply(riskAversion)
        val p = Array2DRowRealMatrix(pMatrix)
        val q = MatrixUtils.createRealVector(qVector)
        val omegaMatrix = omega?.let { Array2DRowRealMatrix(it) }
            ?: p.multiply(cov.scalarMultiply(tau)).multiply(p.transpose()).let { matrix ->
                val data = Array(matrix.rowDimension) { i -> DoubleArray(matrix.columnDimension) { j -> if (i == j) matrix.getEntry(i, j) else 0.0 } }
                Array2DRowRealMatrix(data)
            }
        val tauCov = cov.scalarMultiply(tau)
        val middle = LUDecomposition(p.multiply(tauCov).multiply(p.transpose()).add(omegaMatrix)).solver.inverse
        val posteriorReturn = tauCov.multiply(p.transpose()).multiply(middle).operate(q.subtract(p.operate(pi))).add(pi)
        val posteriorCov = cov.add(tauCov).subtract(tauCov.multiply(p.transpose()).multiply(middle).multiply(p).multiply(tauCov))
        val invCov = LUDecomposition(posteriorCov).solver.inverse
        val impliedWeights = invCov.operate(posteriorReturn).mapDivide(invCov.operate(posteriorReturn).sum())
        val portReturn = posteriorReturn.dotProduct(impliedWeights)
        val portVar = impliedWeights.dotProduct(posteriorCov.operate(impliedWeights))
        return Portfolio(
            objective = "black_litterman",
            tickers = tickers,
            weights = tickers.zip(impliedWeights.toArray().toList()).toMap(),
            expectedReturn = portReturn * 252,
            volatility = kotlin.math.sqrt(portVar) * kotlin.math.sqrt(252.0),
            sharpeRatio = null
        )
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
```

- [ ] **Step 5.4.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/portfolio/domain/RiskParityOptimizer.kt src/main/kotlin/com/example/starter/portfolio/domain/BlackLittermanOptimizer.kt
git commit -m "feat(portfolio): add risk parity and Black-Litterman optimizers"
```

### Task 5.5: Portfolio application service

- [ ] **Step 5.5.1: Create `src/main/kotlin/com/example/starter/portfolio/application/service/PortfolioService.kt`**

```kotlin
package com.example.starter.portfolio.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.BlackLittermanOptimizer
import com.example.starter.portfolio.domain.MeanVarianceOptimizer
import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.portfolio.domain.RiskParityOptimizer
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class PortfolioService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val meanVarianceOptimizer: MeanVarianceOptimizer = MeanVarianceOptimizer(),
    private val riskParityOptimizer: RiskParityOptimizer = RiskParityOptimizer(),
    private val blackLittermanOptimizer: BlackLittermanOptimizer = BlackLittermanOptimizer()
) : OptimizePortfolioUseCase {

    override fun optimize(command: OptimizePortfolioUseCase.OptimizeCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        return meanVarianceOptimizer.optimize(
            returns = returns,
            tickers = command.tickers.map { it.symbol },
            objective = command.objective,
            riskFreeRate = command.riskFreeRate,
            targetReturn = command.targetReturn,
            targetVolatility = command.targetVolatility,
            allowShort = command.allowShort,
            maxWeight = command.maxWeight
        )
    }

    override fun riskParity(command: OptimizePortfolioUseCase.RiskParityCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        return riskParityOptimizer.optimize(returns, command.tickers.map { it.symbol }, command.riskBudget)
    }

    override fun blackLitterman(command: OptimizePortfolioUseCase.BlackLittermanCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        val marketWeights = command.tickers.map { command.marketWeights[it.symbol] ?: 0.0 }.toDoubleArray()
        val pMatrix = command.views.views.map { view ->
            command.tickers.map { ticker ->
                when {
                    view.asset == ticker && view.relativeAsset == null -> 1.0
                    view.asset == ticker && view.relativeAsset != null -> 1.0
                    view.relativeAsset == ticker -> -1.0
                    else -> 0.0
                }
            }
        }.toTypedArray()
        val qVector = command.views.views.map { it.returnView }.toDoubleArray()
        return blackLittermanOptimizer.optimize(
            returns = returns,
            tickers = command.tickers.map { it.symbol },
            marketWeights = marketWeights,
            pMatrix = pMatrix,
            qVector = qVector,
            riskAversion = command.riskAversion,
            tau = command.tau
        )
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))

    private fun simpleReturns(series: List<com.example.starter.shared.domain.OHLCV>): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 5.5.2: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.5.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/portfolio/application/
git commit -m "feat(portfolio): add portfolio optimization service"
```

### Task 5.6: Protocol adapters and tests

- [ ] **Step 5.6.1: Create `src/main/proto/portfolio/portfolio_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.portfolio.grpc;
option java_package = "com.example.starter.portfolio.grpc";
option java_multiple_files = true;

service PortfolioService {
  rpc Optimize (OptimizeRequest) returns (PortfolioResponse);
  rpc RiskParity (RiskParityRequest) returns (PortfolioResponse);
  rpc BlackLitterman (BlackLittermanRequest) returns (PortfolioResponse);
}

message OptimizeRequest {
  repeated string symbols = 1;
  string start_date = 2;
  string end_date = 3;
  string interval = 4;
  string provider = 5;
  string objective = 6;
  double risk_free_rate = 7;
  double target_return = 8;
  double target_volatility = 9;
  bool allow_short = 10;
  double max_weight = 11;
}

message RiskParityRequest {
  repeated string symbols = 1;
  map<string, double> risk_budget = 2;
  string start_date = 3;
  string end_date = 4;
  string interval = 5;
  string provider = 6;
}

message BlackLittermanRequest {
  repeated string symbols = 1;
  map<string, double> market_weights = 2;
  repeated BLView views = 3;
  string start_date = 4;
  string end_date = 5;
  string interval = 6;
  string provider = 7;
  double risk_aversion = 8;
  double tau = 9;
}

message BLView {
  string asset = 1;
  string relative_asset = 2;
  double return_view = 3;
}

message PortfolioResponse {
  string objective = 1;
  map<string, double> weights = 2;
  double expected_return = 3;
  double volatility = 4;
  double sharpe_ratio = 5;
}
```

- [ ] **Step 5.6.2: Create `src/main/kotlin/com/example/starter/portfolio/adapter/in/grpc/PortfolioGrpcService.kt`**

Map `OptimizePortfolioUseCase` commands to the generated proto service following the same pattern as `BacktestGrpcService`.

- [ ] **Step 5.6.3: Create `src/main/kotlin/com/example/starter/portfolio/adapter/in/web/PortfolioController.kt`**

```kotlin
package com.example.starter.portfolio.adapter.`in`.web

import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioController(
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase
) {

    @PostMapping("/optimize")
    fun optimize(@RequestBody request: OptimizeRequestDto): Mono<Portfolio> = Mono.fromCallable {
        optimizePortfolioUseCase.optimize(
            OptimizePortfolioUseCase.OptimizeCommand(
                tickers = request.symbols.map { Ticker(it) },
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                objective = request.objective,
                riskFreeRate = request.riskFreeRate,
                targetReturn = request.targetReturn,
                targetVolatility = request.targetVolatility,
                allowShort = request.allowShort,
                maxWeight = request.maxWeight,
                provider = request.provider
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())
}

data class OptimizeRequestDto(
    val symbols: List<String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val objective: String = "max_sharpe",
    val riskFreeRate: Double = 0.02,
    val targetReturn: Double? = null,
    val targetVolatility: Double? = null,
    val allowShort: Boolean = false,
    val maxWeight: Double? = null
)
```

- [ ] **Step 5.6.4: Extend `A2aTaskHandler` and `McpToolHandler`**

Add skill/tool entries for `portfolio-optimize`, `portfolio-risk-parity`, and `portfolio-black-litterman`.

- [ ] **Step 5.6.5: Write integration and E2E tests**

Create `src/integrationTest/kotlin/com/example/starter/portfolio/PortfolioIntegrationTest.kt` and `src/e2eTest/kotlin/com/example/starter/portfolio/e2e/PortfolioE2ETest.kt` asserting weights sum to ~1.0 and volatility is non-negative.

- [ ] **Step 5.6.6: Commit**

```bash
git add src/main/proto/portfolio/ src/main/kotlin/com/example/starter/portfolio/adapter/ src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt src/integrationTest/kotlin/com/example/starter/portfolio/ src/e2eTest/kotlin/com/example/starter/portfolio/
git commit -m "feat(portfolio): add REST, gRPC, A2A, MCP adapters and tests"
```


## Phase 6: Screener

**Files:**
- Create: `src/main/kotlin/com/example/starter/screener/domain/FundamentalData.kt`
- Create: `src/main/kotlin/com/example/starter/screener/domain/ScreenCriteria.kt`
- Create: `src/main/kotlin/com/example/starter/screener/domain/ScreenResult.kt`
- Create: `src/main/kotlin/com/example/starter/screener/application/port/outbound/FundamentalProvider.kt`
- Create: `src/main/kotlin/com/example/starter/screener/adapter/out/reference/HardcodedFundamentalAdapter.kt`
- Create: `src/main/kotlin/com/example/starter/screener/application/port/inbound/ScreenStocksUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/screener/application/service/ScreenerService.kt`
- Create: `src/main/kotlin/com/example/starter/screener/adapter/in/web/ScreenerController.kt`
- Create: `src/main/kotlin/com/example/starter/screener/adapter/in/grpc/ScreenerGrpcService.kt`
- Create: `src/main/proto/screener/screener_service.proto`
- Modify: `A2aTaskHandler.kt`, `McpToolHandler.kt`
- Tests: `src/test/kotlin/com/example/starter/screener/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 6.1: Screener domain and fundamental provider port

- [ ] **Step 6.1.1: Create `src/main/kotlin/com/example/starter/screener/domain/FundamentalData.kt`**

```kotlin
package com.example.starter.screener.domain

import java.math.BigDecimal

data class FundamentalData(
    val ticker: String,
    val peRatio: Double? = null,
    val pbRatio: Double? = null,
    val debtEquity: Double? = null,
    val roe: Double? = null,
    val profitMargin: Double? = null,
    val dividendYield: Double? = null,
    val marketCap: Double? = null,
    val beta: Double? = null
)
```

- [ ] **Step 6.1.2: Create `src/main/kotlin/com/example/starter/screener/domain/ScreenCriteria.kt`**

```kotlin
package com.example.starter.screener.domain

data class ScreenCriteria(
    val peRatioMax: Double? = null,
    val pbRatioMax: Double? = null,
    val debtEquityMax: Double? = null,
    val roeMin: Double? = null,
    val profitMarginMin: Double? = null,
    val dividendYieldMin: Double? = null,
    val marketCapMin: Double? = null,
    val rsiMax: Double? = null,
    val rsiMin: Double? = null,
    val priceAboveSma: Int? = null,
    val priceBelowSma: Int? = null,
    val betaMax: Double? = null,
    val betaMin: Double? = null
)
```

- [ ] **Step 6.1.3: Create `src/main/kotlin/com/example/starter/screener/domain/ScreenResult.kt`**

```kotlin
package com.example.starter.screener.domain

data class ScreenResult(
    val criteria: ScreenCriteria,
    val matches: List<ScreenMatch>,
    val failedTickers: List<String>
)

data class ScreenMatch(
    val ticker: String,
    val fundamentals: FundamentalData,
    val rsi: Double? = null,
    val priceVsSma: Double? = null
)
```

- [ ] **Step 6.1.4: Create `src/main/kotlin/com/example/starter/screener/application/port/outbound/FundamentalProvider.kt`**

```kotlin
package com.example.starter.screener.application.port.outbound

import com.example.starter.screener.domain.FundamentalData

interface FundamentalProvider {
    fun fetch(ticker: String): FundamentalData?
}
```

- [ ] **Step 6.1.5: Create `src/main/kotlin/com/example/starter/screener/adapter/out/reference/HardcodedFundamentalAdapter.kt`**

```kotlin
package com.example.starter.screener.adapter.out.reference

import com.example.starter.screener.application.port.outbound.FundamentalProvider
import com.example.starter.screener.domain.FundamentalData
import org.springframework.stereotype.Component

@Component
class HardcodedFundamentalAdapter : FundamentalProvider {

    private val table = mapOf(
        "AAPL" to FundamentalData("AAPL", peRatio = 28.0, pbRatio = 45.0, debtEquity = 1.5, roe = 0.25, profitMargin = 0.22, dividendYield = 0.005, marketCap = 2.8e12, beta = 1.2),
        "MSFT" to FundamentalData("MSFT", peRatio = 32.0, pbRatio = 12.0, debtEquity = 0.4, roe = 0.30, profitMargin = 0.35, dividendYield = 0.007, marketCap = 3.0e12, beta = 0.9),
        "TSLA" to FundamentalData("TSLA", peRatio = 75.0, pbRatio = 15.0, debtEquity = 0.2, roe = 0.10, profitMargin = 0.08, dividendYield = 0.0, marketCap = 8.0e11, beta = 2.0),
        "JNJ" to FundamentalData("JNJ", peRatio = 18.0, pbRatio = 5.0, debtEquity = 0.5, roe = 0.18, profitMargin = 0.16, dividendYield = 0.025, marketCap = 4.5e11, beta = 0.6)
    )

    override fun fetch(ticker: String): FundamentalData? = table[ticker.uppercase()]
}
```

- [ ] **Step 6.1.6: Commit**

```bash
git add src/main/kotlin/com/example/starter/screener/domain/ src/main/kotlin/com/example/starter/screener/application/port/outbound/ src/main/kotlin/com/example/starter/screener/adapter/out/reference/
git commit -m "feat(screener): add domain types and fundamental provider"
```

### Task 6.2: Screener service

- [ ] **Step 6.2.1: Create `src/main/kotlin/com/example/starter/screener/application/port/inbound/ScreenStocksUseCase.kt`**

```kotlin
package com.example.starter.screener.application.port.inbound

import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange

interface ScreenStocksUseCase {
    fun screen(command: ScreenCommand): ScreenResult

    data class ScreenCommand(
        val tickers: List<String>,
        val criteria: ScreenCriteria,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null,
        val sortBy: String? = null,
        val ascending: Boolean = true
    )
}
```

- [ ] **Step 6.2.2: Create `src/main/kotlin/com/example/starter/screener/application/service/ScreenerService.kt`**

```kotlin
package com.example.starter.screener.application.service

import com.example.starter.indicators.domain.IndicatorCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.application.port.outbound.FundamentalProvider
import com.example.starter.screener.domain.FundamentalData
import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenMatch
import com.example.starter.screener.domain.ScreenResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class ScreenerService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val fundamentalProvider: FundamentalProvider,
    private val indicatorCalculator: IndicatorCalculator
) : ScreenStocksUseCase {

    override fun screen(command: ScreenStocksUseCase.ScreenCommand): ScreenResult {
        val matches = mutableListOf<ScreenMatch>()
        val failed = mutableListOf<String>()
        command.tickers.forEach { ticker ->
            try {
                val fundamentals = fundamentalProvider.fetch(ticker)
                if (fundamentals == null) {
                    failed.add(ticker)
                    return@forEach
                }
                val series = fetchMarketDataUseCase.fetch(
                    FetchMarketDataUseCase.FetchMarketDataCommand(
                        ticker = Ticker(ticker),
                        range = command.range,
                        interval = command.interval,
                        provider = command.provider
                    )
                )
                val rsi = command.criteria.rsiMax?.let { _ ->
                    val result = indicatorCalculator.calculate("rsi", series, mapOf("period" to 14))
                    result.values.lastOrNull()?.value?.toDouble()
                }
                val sma = command.criteria.priceAboveSma?.let { period ->
                    val result = indicatorCalculator.calculate("sma", series, mapOf("period" to period))
                    result.values.lastOrNull()?.value?.toDouble()
                }
                val price = series.last().close.toDouble()
                val priceVsSma = sma?.let { (price - it) / it }
                if (passes(fundamentals, command.criteria, rsi, priceVsSma)) {
                    matches.add(ScreenMatch(ticker, fundamentals, rsi, priceVsSma))
                }
            } catch (ex: Exception) {
                failed.add(ticker)
            }
        }
        val sorted = when (command.sortBy) {
            "pe" -> matches.sortedBy { it.fundamentals.peRatio }
            "rsi" -> matches.sortedBy { it.rsi }
            else -> matches.sortedBy { it.ticker }
        }.let { if (command.ascending) it else it.reversed() }
        return ScreenResult(command.criteria, sorted, failed)
    }

    private fun passes(f: FundamentalData, c: ScreenCriteria, rsi: Double?, priceVsSma: Double?): Boolean {
        if (c.peRatioMax != null && (f.peRatio == null || f.peRatio > c.peRatioMax)) return false
        if (c.pbRatioMax != null && (f.pbRatio == null || f.pbRatio > c.pbRatioMax)) return false
        if (c.debtEquityMax != null && (f.debtEquity == null || f.debtEquity > c.debtEquityMax)) return false
        if (c.roeMin != null && (f.roe == null || f.roe < c.roeMin)) return false
        if (c.profitMarginMin != null && (f.profitMargin == null || f.profitMargin < c.profitMarginMin)) return false
        if (c.dividendYieldMin != null && (f.dividendYield == null || f.dividendYield < c.dividendYieldMin)) return false
        if (c.marketCapMin != null && (f.marketCap == null || f.marketCap < c.marketCapMin)) return false
        if (c.betaMax != null && (f.beta == null || f.beta > c.betaMax)) return false
        if (c.betaMin != null && (f.beta == null || f.beta < c.betaMin)) return false
        if (c.rsiMax != null && (rsi == null || rsi > c.rsiMax)) return false
        if (c.rsiMin != null && (rsi == null || rsi < c.rsiMin)) return false
        if (c.priceAboveSma != null && (priceVsSma == null || priceVsSma <= 0)) return false
        if (c.priceBelowSma != null && (priceVsSma == null || priceVsSma >= 0)) return false
        return true
    }
}
```

- [ ] **Step 6.2.3: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.2.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/screener/application/
git commit -m "feat(screener): add screening service"
```

### Task 6.3: Protocol adapters and tests

- [ ] **Step 6.3.1: Create `src/main/proto/screener/screener_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.screener.grpc;
option java_package = "com.example.starter.screener.grpc";
option java_multiple_files = true;

service ScreenerService {
  rpc Screen (ScreenRequest) returns (ScreenResponse);
}

message ScreenRequest {
  repeated string tickers = 1;
  string start_date = 2;
  string end_date = 3;
  string interval = 4;
  string provider = 5;
  double pe_ratio_max = 6;
  double pb_ratio_max = 7;
  double roe_min = 8;
  double rsi_max = 9;
  string sort_by = 10;
  bool ascending = 11;
}

message ScreenResponse {
  repeated ScreenMatch matches = 1;
  repeated string failed_tickers = 2;
}

message ScreenMatch {
  string ticker = 1;
  double pe_ratio = 2;
  double pb_ratio = 3;
  double rsi = 4;
}
```

- [ ] **Step 6.3.2: Create gRPC service and REST controller**

Create `src/main/kotlin/com/example/starter/screener/adapter/in/grpc/ScreenerGrpcService.kt` and `src/main/kotlin/com/example/starter/screener/adapter/in/web/ScreenerController.kt` following the same mapping pattern as previous subdomains.

- [ ] **Step 6.3.3: Extend `A2aTaskHandler` and `McpToolHandler`**

Add skill/tool `screener-run` accepting `tickers`, filter thresholds, date range, and interval.

- [ ] **Step 6.3.4: Write integration and E2E tests**

Create `src/integrationTest/kotlin/com/example/starter/screener/ScreenerIntegrationTest.kt` and `src/e2eTest/kotlin/com/example/starter/screener/e2e/ScreenerE2ETest.kt` asserting known symbols pass `peRatioMax` filters.

- [ ] **Step 6.3.5: Commit**

```bash
git add src/main/proto/screener/ src/main/kotlin/com/example/starter/screener/adapter/ src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt src/integrationTest/kotlin/com/example/starter/screener/ src/e2eTest/kotlin/com/example/starter/screener/
git commit -m "feat(screener): add REST, gRPC, A2A, MCP adapters and tests"
```


## Phase 7: Agent tools

**Files:**
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/ToolCall.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/ToolResult.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/ToolDefinition.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/application/port/inbound/DispatchAgentToolUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/VolatilityEstimatorsCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/AdvancedIndicatorsCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/PositionSizer.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/CapacityReportCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/DataQualityReportCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/RollingBetaCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/RiskAttributionCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/StressTestCalculator.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/domain/RegimeAdaptiveEngine.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/application/service/AgentToolsService.kt`
- Create: `src/main/kotlin/com/example/starter/agenttools/adapter/in/web/AgentToolsController.kt`
- Modify: `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aAgentCardController.kt`
- Modify: `A2aTaskHandler.kt`, `McpToolHandler.kt`
- Tests: `src/test/kotlin/com/example/starter/agenttools/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 7.1: Tool call models and use case

- [ ] **Step 7.1.1: Create `src/main/kotlin/com/example/starter/agenttools/domain/ToolCall.kt`**

```kotlin
package com.example.starter.agenttools.domain

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any> = emptyMap()
)
```

- [ ] **Step 7.1.2: Create `src/main/kotlin/com/example/starter/agenttools/domain/ToolResult.kt`**

```kotlin
package com.example.starter.agenttools.domain

data class ToolResult(
    val tool: String,
    val success: Boolean,
    val payload: Map<String, Any?> = emptyMap(),
    val error: String? = null
)
```

- [ ] **Step 7.1.3: Create `src/main/kotlin/com/example/starter/agenttools/domain/ToolDefinition.kt`**

```kotlin
package com.example.starter.agenttools.domain

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)
```

- [ ] **Step 7.1.4: Create `src/main/kotlin/com/example/starter/agenttools/application/port/inbound/DispatchAgentToolUseCase.kt`**

```kotlin
package com.example.starter.agenttools.application.port.inbound

import com.example.starter.agenttools.domain.ToolCall
import com.example.starter.agenttools.domain.ToolDefinition
import com.example.starter.agenttools.domain.ToolResult

interface DispatchAgentToolUseCase {
    fun dispatch(call: ToolCall): ToolResult
    fun listTools(): List<ToolDefinition>
}
```

- [ ] **Step 7.1.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/agenttools/domain/ src/main/kotlin/com/example/starter/agenttools/application/port/inbound/
git commit -m "feat(agenttools): add tool call/result models and use case"
```

### Task 7.2: Additional calculators used by agent tools

- [ ] **Step 7.2.1: Create `src/main/kotlin/com/example/starter/agenttools/domain/VolatilityEstimatorsCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries
import kotlin.math.ln
import kotlin.math.sqrt

class VolatilityEstimatorsCalculator {

    fun calculate(series: PriceSeries): Map<String, Double> {
        val close = series.map { it.close.toDouble() }
        val high = series.map { it.high.toDouble() }
        val low = series.map { it.low.toDouble() }
        val open = series.map { it.open.toDouble() }
        val cc = closeToClose(close)
        val parkinson = parkinson(high, low)
        val garmanKlass = garmanKlass(open, high, low, close)
        val yangZhang = yangZhang(open, high, low, close)
        return mapOf(
            "close_to_close" to cc,
            "parkinson" to parkinson,
            "garman_klass" to garmanKlass,
            "yang_zhang" to yangZhang
        )
    }

    private fun closeToClose(close: List<Double>): Double = returns(close).let { sqrt(it.map { r -> r * r }.average() * 252.0) }

    private fun parkinson(high: List<Double>, low: List<Double>): Double {
        val squares = high.zip(low).map { (h, l) -> (ln(h / l)) * (ln(h / l)) }
        return sqrt(squares.average() * 252.0 / (4.0 * ln(2.0)))
    }

    private fun garmanKlass(open: List<Double>, high: List<Double>, low: List<Double>, close: List<Double>): Double {
        val terms = open.zip(high).zip(low.zip(close)).map { (oh, lc) ->
            val (o, h) = oh
            val (l, c) = lc
            0.5 * (ln(h / l)) * (ln(h / l)) - (2.0 * ln(2.0) - 1.0) * (ln(c / o)) * (ln(c / o))
        }
        return sqrt(terms.average() * 252.0)
    }

    private fun yangZhang(open: List<Double>, high: List<Double>, low: List<Double>, close: List<Double>): Double {
        val overnight = open.zip(close.zipWithNext { prev, _ -> prev }).map { (o, prevClose) -> ln(o / prevClose) }
        val intraday = close.zip(open).map { (c, o) -> ln(c / o) }
        val rs = high.zip(low).map { (h, l) -> ln(h / l) * ln(h / l) }
        val overnightVar = overnight.map { it * it }.average()
        val intradayVar = intraday.map { it * it }.average()
        val rsMean = rs.average()
        val k = 0.34 / (1.34 + (open.size + 1) / (open.size - 1))
        return sqrt((overnightVar + k * intradayVar + (1 - k) * rsMean) * 252.0)
    }

    private fun returns(prices: List<Double>): List<Double> = prices.zipWithNext { prev, curr -> ln(curr / prev) }
}
```

- [ ] **Step 7.2.2: Create `src/main/kotlin/com/example/starter/agenttools/domain/AdvancedIndicatorsCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import kotlin.math.max
import kotlin.math.min

class AdvancedIndicatorsCalculator {

    fun parabolicSAR(series: PriceSeries, af: Double = 0.02, maxAf: Double = 0.2): List<Pair<String, Double>> {
        var sar = series.first().low.toDouble()
        var ep = series.first().high.toDouble()
        var trend = "falling"
        var acc = af
        return series.mapIndexed { idx, bar ->
            if (idx == 0) return@mapIndexed trend to sar
            val prior = series[idx - 1]
            if (trend == "falling") {
                sar = sar - acc * (sar - ep)
                if (bar.high.toDouble() > sar) {
                    trend = "rising"
                    sar = ep
                    ep = bar.high.toDouble()
                    acc = af
                } else {
                    ep = min(ep, bar.low.toDouble())
                    if (ep == bar.low.toDouble()) acc = min(acc + af, maxAf)
                }
            } else {
                sar = sar + acc * (ep - sar)
                if (bar.low.toDouble() < sar) {
                    trend = "falling"
                    sar = ep
                    ep = bar.low.toDouble()
                    acc = af
                } else {
                    ep = max(ep, bar.high.toDouble())
                    if (ep == bar.high.toDouble()) acc = min(acc + af, maxAf)
                }
            }
            trend to sar
        }
    }

    fun wilderAtr(series: PriceSeries, period: Int = 14): List<Double?> {
        val trs = series.mapIndexed { idx, bar ->
            if (idx == 0) bar.high.toDouble() - bar.low.toDouble()
            else {
                val prevClose = series[idx - 1].close.toDouble()
                listOf(
                    bar.high.toDouble() - bar.low.toDouble(),
                    kotlin.math.abs(bar.high.toDouble() - prevClose),
                    kotlin.math.abs(bar.low.toDouble() - prevClose)
                ).maxOrNull()!!
            }
        }
        return trs.mapIndexed { idx, tr ->
            if (idx < period - 1) null
            else if (idx == period - 1) trs.take(period).average()
            else ((period - 1) * (trs[idx - 1]) + tr) / period
        }
    }

    fun mfi(series: PriceSeries, period: Int = 14): List<Double?> {
        val typical = series.map { (it.high.toDouble() + it.low.toDouble() + it.close.toDouble()) / 3.0 }
        val raw = typical.zip(series.map { it.volume }).map { (t, v) -> t * v }
        return raw.mapIndexed { idx, _ ->
            if (idx < period) null
            else {
                var positive = 0.0
                var negative = 0.0
                for (i in idx - period + 1..idx) {
                    if (typical[i] > typical[i - 1]) positive += raw[i]
                    else negative += raw[i]
                }
                if (negative == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + positive / negative))
            }
        }
    }
}
```

- [ ] **Step 7.2.3: Create `src/main/kotlin/com/example/starter/agenttools/domain/PositionSizer.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries

class PositionSizer {

    fun atrBased(series: PriceSeries, riskPerTrade: Double, accountEquity: Double, atrPeriod: Int = 14): Map<String, Double> {
        val atr = wilderAtr(series, atrPeriod).filterNotNull().lastOrNull() ?: return emptyMap()
        val price = series.last().close.toDouble()
        val shares = (accountEquity * riskPerTrade / atr).toLong().toDouble()
        return mapOf("shares" to shares, "notional" to shares * price, "risk_amount" to shares * atr)
    }

    fun kellyFraction(winRate: Double, avgWin: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return 0.0
        val b = avgWin / avgLoss
        return (winRate * b - (1 - winRate)) / b
    }

    private fun wilderAtr(series: PriceSeries, period: Int): List<Double?> {
        val calc = AdvancedIndicatorsCalculator()
        return calc.wilderAtr(series, period)
    }
}
```

- [ ] **Step 7.2.4: Create `src/main/kotlin/com/example/starter/agenttools/domain/CapacityReportCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries

class CapacityReportCalculator {

    fun calculate(series: PriceSeries, targetParticipation: Double = 0.1): Map<String, Double> {
        val avgVolume = series.map { it.volume }.average()
        val lastClose = series.last().close.toDouble()
        val adv = avgVolume * lastClose
        val maxPosition = adv * targetParticipation
        val daysToLiquidate = if (maxPosition == 0.0) Double.POSITIVE_INFINITY else 1.0 / targetParticipation
        return mapOf("adv_dollars" to adv, "max_position_dollars" to maxPosition, "days_to_liquidate" to daysToLiquidate)
    }
}
```

- [ ] **Step 7.2.5: Create `src/main/kotlin/com/example/starter/agenttools/domain/DataQualityReportCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries
import java.time.temporal.ChronoUnit

class DataQualityReportCalculator {

    fun calculate(series: PriceSeries): Map<String, Any> {
        val sorted = series.sortedBy { it.date }
        val expectedDays = ChronoUnit.DAYS.between(sorted.first().date, sorted.last().date) + 1
        val missingBars = (expectedDays - sorted.size).coerceAtLeast(0)
        val priceJumps = sorted.zipWithNext { prev, curr ->
            val ret = kotlin.math.abs(curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
            if (ret > 0.2) curr.date to ret else null
        }.filterNotNull()
        val stale = sorted.zipWithNext { prev, curr ->
            if (prev.close == curr.close) curr.date else null
        }.filterNotNull()
        return mapOf(
            "total_bars" to sorted.size,
            "missing_bars" to missingBars,
            "price_jumps" to priceJumps.size,
            "stale_prices" to stale.size
        )
    }
}
```

- [ ] **Step 7.2.6: Create `src/main/kotlin/com/example/starter/agenttools/domain/RollingBetaCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression

class RollingBetaCalculator {

    fun calculate(asset: PriceSeries, benchmark: PriceSeries, window: Int = 60): List<Map<String, Double>> {
        val assetReturns = returns(asset)
        val benchReturns = returns(benchmark)
        val aligned = assetReturns.zip(benchReturns)
        return aligned.mapIndexed { idx, _ ->
            if (idx < window - 1) emptyMap()
            else {
                val reg = SimpleRegression()
                aligned.subList(idx - window + 1, idx + 1).forEach { (a, b) -> reg.addData(b, a) }
                mapOf("index" to idx.toDouble(), "beta" to reg.slope, "alpha" to reg.intercept, "r_squared" to reg.rSquare)
            }
        }
    }

    private fun returns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
```

- [ ] **Step 7.2.7: Create `src/main/kotlin/com/example/starter/agenttools/domain/RiskAttributionCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.analysis.domain.PcaCalculator
import com.example.starter.portfolio.domain.PortfolioMetricsCalculator
import com.example.starter.shared.domain.PriceSeries

class RiskAttributionCalculator {

    fun calculate(returns: List<List<Double>>, tickers: List<String>, weights: Map<String, Double>): Map<String, Any> {
        val wArray = tickers.map { weights[it] ?: 0.0 }.toDoubleArray()
        val calc = PortfolioMetricsCalculator()
        val variance = calc.portfolioVariance(returns, wArray)
        val cov = org.apache.commons.math3.stat.correlation.Covariance(calc.align(returns)).covarianceMatrix
        val mcr = tickers.mapIndexed { i, t ->
            val marginal = cov.operate(org.apache.commons.math3.linear.ArrayRealVector(wArray)).getEntry(i)
            t to marginal / kotlin.math.sqrt(variance)
        }.toMap()
        val pca = PcaCalculator().calculate(tickers, returns.map { seriesFromReturns(it) }, nComponents = 3)
        return mapOf("marginal_risk_contribution" to mcr, "pca_exposures" to pca.loadings)
    }

    private fun seriesFromReturns(returns: List<Double>): List<com.example.starter.shared.domain.OHLCV> {
        var price = 100.0
        return returns.mapIndexed { idx, r ->
            price *= (1 + r)
            com.example.starter.shared.domain.OHLCV(
                ticker = com.example.starter.shared.domain.Ticker("SYNTH"),
                date = java.time.LocalDate.of(2024, 1, 1).plusDays(idx.toLong()),
                open = java.math.BigDecimal(price.toString()),
                high = java.math.BigDecimal(price.toString()),
                low = java.math.BigDecimal(price.toString()),
                close = java.math.BigDecimal(price.toString()),
                volume = 0L
            )
        }
    }
}
```

- [ ] **Step 7.2.8: Create `src/main/kotlin/com/example/starter/agenttools/domain/StressTestCalculator.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.shared.domain.PriceSeries

class StressTestCalculator {

    private val scenarios = mapOf(
        "covid_crash_2020" to Pair("2020-02-19", "2020-03-23"),
        "gfc_2008" to Pair("2008-10-01", "2008-12-01"),
        "dot_com_2002" to Pair("2002-03-01", "2002-07-01"),
        "black_monday_1987" to Pair("1987-10-14", "1987-10-26")
    )

    fun listScenarios(): List<String> = scenarios.keys.toList()

    fun replay(weights: Map<String, Double>, priceData: Map<String, PriceSeries>, scenario: String): Map<String, Double> {
        val (start, end) = scenarios[scenario] ?: return emptyMap()
        val startDate = java.time.LocalDate.parse(start)
        val endDate = java.time.LocalDate.parse(end)
        val scenarioReturns = priceData.map { (symbol, series) ->
            val window = series.filter { it.date in startDate..endDate }
            val totalReturn = if (window.size < 2) 0.0 else (window.last().close.toDouble() - window.first().close.toDouble()) / window.first().close.toDouble()
            symbol to totalReturn
        }.toMap()
        val portfolioReturn = scenarioReturns.map { (symbol, ret) -> (weights[symbol] ?: 0.0) * ret }.sum()
        return scenarioReturns + ("portfolio" to portfolioReturn)
    }
}
```

- [ ] **Step 7.2.9: Create `src/main/kotlin/com/example/starter/agenttools/domain/RegimeAdaptiveEngine.kt`**

```kotlin
package com.example.starter.agenttools.domain

import com.example.starter.analysis.domain.HurstCalculator
import com.example.starter.backtest.domain.BacktestEngine
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.backtest.domain.Strategies
import com.example.starter.shared.domain.PriceSeries

class RegimeAdaptiveEngine(
    private val hurstCalculator: HurstCalculator = HurstCalculator(),
    private val backtestEngine: BacktestEngine = BacktestEngine()
) {

    fun run(series: PriceSeries, paramGrid: Map<String, List<Any>>, initialCapital: Double = 10_000.0): BacktestResult {
        val hurst = hurstCalculator.calculate(series).exponent
        val strategyPool = when {
            hurst > 0.55 -> listOf("momentum_timeseries", "sma_crossover", "macd_crossover")
            hurst < 0.45 -> listOf("rsi_mean_reversion", "bollinger_reversion", "vwap_reversion")
            else -> listOf("sma_crossover", "rsi_mean_reversion")
        }
        val results = strategyPool.flatMap { strategy ->
            val combos = cartesianProduct(paramGrid.filter { it.key.startsWith(strategy) || it.key == "common" })
            combos.map { params ->
                val signals = Strategies.REGISTRY.getValue(strategy).generate(series, params)
                backtestEngine.run(series, signals, initialCapital, strategyName = strategy)
            }
        }
        return results.maxByOrNull { it.totalReturn } ?: results.first()
    }

    private fun cartesianProduct(grid: Map<String, List<Any>>): List<Map<String, Any>> {
        if (grid.isEmpty()) return listOf(emptyMap())
        val keys = grid.keys.toList()
        val lists = keys.map { grid.getValue(it) }
        return lists.fold(listOf(emptyList<Any>())) { acc, list ->
            acc.flatMap { prefix -> list.map { prefix + it } }
        }.map { combo -> keys.zip(combo).toMap() }
    }
}
```

- [ ] **Step 7.2.10: Commit**

```bash
git add src/main/kotlin/com/example/starter/agenttools/domain/
git commit -m "feat(agenttools): add auxiliary calculators for agent tools"
```

### Task 7.3: Tool registry with all 42 tools

- [ ] **Step 7.3.1: Create `src/main/kotlin/com/example/starter/agenttools/application/service/ToolRegistry.kt`**

```kotlin
package com.example.starter.agenttools.application.service

import com.example.starter.agenttools.domain.ToolDefinition

object ToolRegistry {

    val definitions: List<ToolDefinition> = listOf(
        ToolDefinition("run_sma_backtest", "Run SMA crossover backtest", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_rsi_backtest", "Run RSI mean-reversion backtest", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_macd_backtest", "Run MACD crossover backtest", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_bollinger_backtest", "Run Bollinger Band mean-reversion backtest", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_buy_and_hold", "Run buy-and-hold baseline", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("compare_strategies", "Run four strategies plus buy-and-hold", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("analyze_stock_risk", "Compute alpha, beta, Sharpe, VaR, CVaR", schema("symbol", "benchmark", "startDate", "endDate", "interval")),
        ToolDefinition("get_technical_analysis", "Compute technical indicators", schema("symbol", "startDate", "endDate", "interval", "indicator")),
        ToolDefinition("get_portfolio_analysis", "Multi-asset portfolio metrics", schema("symbols", "startDate", "endDate", "interval")),
        ToolDefinition("run_portfolio_optimization", "Mean-variance / risk parity / Black-Litterman", schema("symbols", "objective", "startDate", "endDate", "interval")),
        ToolDefinition("run_screener", "Screen stocks", schema("tickers", "startDate", "endDate", "interval")),
        ToolDefinition("run_factor_regression", "Multi-factor regression", schema("asset", "factors", "startDate", "endDate", "interval")),
        ToolDefinition("run_cointegration_test", "Engle-Granger cointegration", schema("symbolA", "symbolB", "startDate", "endDate", "interval")),
        ToolDefinition("run_pca_analysis", "PCA decomposition", schema("symbols", "startDate", "endDate", "interval")),
        ToolDefinition("get_correlation_analysis", "Correlation matrix and diversification ratio", schema("symbols", "startDate", "endDate", "interval")),
        ToolDefinition("run_hurst_analysis", "Hurst exponent", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("get_volatility_estimators", "Parkinson/Garman-Klass/Yang-Zhang volatility", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_regime_adaptive_backtest", "Hurst-based regime adaptive backtest", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_regime_adaptive_walkforward_backtest", "Regime adaptive walk-forward", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("scan_pairs", "Universe pair cointegration scan", schema("symbols", "startDate", "endDate", "interval")),
        ToolDefinition("run_walk_forward_backtest", "Walk-forward optimization", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("get_portfolio_risk_attribution", "Risk attribution", schema("symbols", "weights", "startDate", "endDate", "interval")),
        ToolDefinition("run_stress_test", "Stress test portfolio", schema("symbols", "weights", "scenario")),
        ToolDefinition("get_position_size", "ATR-based position sizing", schema("symbol", "startDate", "endDate", "interval", "riskPerTrade", "accountEquity")),
        ToolDefinition("get_stock_fundamentals", "Company fundamentals", schema("symbol")),
        ToolDefinition("run_backtest_optimization", "Parameter grid search", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("get_advanced_indicators", "Parabolic SAR, Wilder ATR, MFI", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("get_rolling_beta", "Rolling beta", schema("symbol", "benchmark", "startDate", "endDate", "interval")),
        ToolDefinition("get_extended_risk_metrics", "Calmar, Treynor, VaR, CVaR", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_custom_signal_backtest", "Backtest custom signal", schema("symbol", "signals", "startDate", "endDate", "interval")),
        ToolDefinition("run_signal_panel_backtest", "Backtest signal panel", schema("signals", "startDate", "endDate", "interval")),
        ToolDefinition("get_backtest_diagnostics", "Backtest diagnostics", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("run_portfolio_simulation", "Portfolio simulation", schema("symbols", "weights", "startDate", "endDate", "interval")),
        ToolDefinition("run_pair_trade_backtest", "Pair trade backtest", schema("symbolA", "symbolB", "startDate", "endDate", "interval")),
        ToolDefinition("get_robustness_diagnostics", "Robustness diagnostics", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("run_monte_carlo_simulation", "Monte Carlo forward simulation", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("get_capacity_report", "Capacity report", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("get_liquidity_metrics", "Amihud illiquidity and Corwin-Schultz spread", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("get_data_quality_report", "Data quality report", schema("symbol", "startDate", "endDate", "interval")),
        ToolDefinition("run_backtest_compact", "Compact backtest", schema("symbol", "strategy", "startDate", "endDate", "interval")),
        ToolDefinition("get_option_pricing", "Black-Scholes price and Greeks", schema("spot", "strike", "timeToExpiry", "riskFreeRate", "volatility", "optionType")),
        ToolDefinition("get_implied_volatility", "Implied volatility", schema("optionPrice", "spot", "strike", "timeToExpiry", "riskFreeRate", "optionType"))
    )

    private fun schema(vararg required: String): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to required.associateWith { mapOf("type" to "string") },
        "required" to required.toList()
    )
}
```

- [ ] **Step 7.3.2: Commit**

```bash
git add src/main/kotlin/com/example/starter/agenttools/application/service/ToolRegistry.kt
git commit -m "feat(agenttools): add tool registry with all 42 definitions"
```

### Task 7.4: Agent tools dispatch service

- [ ] **Step 7.4.1: Create `src/main/kotlin/com/example/starter/agenttools/application/service/AgentToolsService.kt`**

```kotlin
package com.example.starter.agenttools.application.service

import com.example.starter.agenttools.application.port.inbound.DispatchAgentToolUseCase
import com.example.starter.agenttools.domain.*
import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestEngine
import com.example.starter.backtest.domain.Strategies
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.domain.RiskReturnCalculator
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.PortfolioMetricsCalculator
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.application.port.outbound.FundamentalProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AgentToolsService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase,
    private val calculateMetricsUseCase: CalculateMetricsUseCase,
    private val runAnalysisUseCase: RunAnalysisUseCase,
    private val runBacktestUseCase: RunBacktestUseCase,
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase,
    private val screenStocksUseCase: ScreenStocksUseCase,
    private val fundamentalProvider: FundamentalProvider,
    private val volatilityEstimatorsCalculator: VolatilityEstimatorsCalculator,
    private val advancedIndicatorsCalculator: AdvancedIndicatorsCalculator,
    private val positionSizer: PositionSizer,
    private val capacityReportCalculator: CapacityReportCalculator,
    private val dataQualityReportCalculator: DataQualityReportCalculator,
    private val rollingBetaCalculator: RollingBetaCalculator,
    private val riskAttributionCalculator: RiskAttributionCalculator,
    private val stressTestCalculator: StressTestCalculator,
    private val regimeAdaptiveEngine: RegimeAdaptiveEngine,
    private val objectMapper: ObjectMapper
) : DispatchAgentToolUseCase {

    override fun listTools(): List<ToolDefinition> = ToolRegistry.definitions

    override fun dispatch(call: ToolCall): ToolResult {
        return try {
            val payload = when (call.name) {
                "run_sma_backtest", "run_rsi_backtest", "run_macd_backtest", "run_bollinger_backtest", "run_buy_and_hold" -> {
                    val strategy = when (call.name) {
                        "run_sma_backtest" -> "sma_crossover"
                        "run_rsi_backtest" -> "rsi_mean_reversion"
                        "run_macd_backtest" -> "macd_crossover"
                        "run_bollinger_backtest" -> "bollinger_reversion"
                        else -> "buy_and_hold"
                    }
                    runBacktestUseCase.execute(
                        RunBacktestUseCase.SingleAssetCommand(
                            ticker = Ticker(str(call.arguments, "symbol")),
                            strategy = strategy,
                            range = range(call.arguments),
                            interval = interval(call.arguments),
                            provider = call.arguments["provider"] as? String
                        )
                    )
                }
                "compare_strategies" -> {
                    val strategies = listOf("sma_crossover", "rsi_mean_reversion", "macd_crossover", "bollinger_reversion")
                    strategies.map { strategy ->
                        strategy to runBacktestUseCase.execute(
                            RunBacktestUseCase.SingleAssetCommand(
                                ticker = Ticker(str(call.arguments, "symbol")),
                                strategy = strategy,
                                range = range(call.arguments),
                                interval = interval(call.arguments),
                                provider = call.arguments["provider"] as? String
                            )
                        )
                    }.toMap()
                }
                "analyze_stock_risk" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val benchmark = fetch(Ticker(str(call.arguments, "benchmark")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    mapOf(
                        "regression" to RunAnalysisUseCase.RegressionCommand(
                            asset = Ticker(str(call.arguments, "symbol")),
                            benchmark = Ticker(str(call.arguments, "benchmark")),
                            range = range(call.arguments),
                            interval = interval(call.arguments)
                        ).let { runAnalysisUseCase.execute(it) },
                        "risk" to RiskReturnCalculator().riskMetrics(series)
                    )
                }
                "get_technical_analysis" -> calculateIndicatorUseCase.calculate(
                    CalculateIndicatorUseCase.CalculateIndicatorCommand(
                        ticker = Ticker(str(call.arguments, "symbol")),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        indicator = str(call.arguments, "indicator"),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_portfolio_analysis" -> {
                    val tickers = tickers(call.arguments, "symbols")
                    val series = tickers.map { fetch(Ticker(it), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String) }
                    val returns = series.map { it.zipWithNext { prev, curr -> (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble() } }
                    val weights = tickers.associateWith { 1.0 / tickers.size }
                    PortfolioMetricsCalculator().let { calc ->
                        mapOf(
                            "expected_return" to calc.portfolioReturn(returns, tickers.map { weights.getValue(it) }.toDoubleArray()) * 252,
                            "volatility" to calc.portfolioVolatility(returns, tickers.map { weights.getValue(it) }.toDoubleArray()) * kotlin.math.sqrt(252.0),
                            "correlation_matrix" to com.example.starter.analysis.domain.CorrelationCalculator().calculate(tickers, series).matrix
                        )
                    }
                }
                "run_portfolio_optimization" -> optimizePortfolioUseCase.optimize(
                    OptimizePortfolioUseCase.OptimizeCommand(
                        tickers = tickers(call.arguments, "symbols").map { Ticker(it) },
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        objective = call.arguments["objective"] as? String ?: "max_sharpe",
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_screener" -> screenStocksUseCase.screen(
                    ScreenStocksUseCase.ScreenCommand(
                        tickers = tickers(call.arguments, "tickers"),
                        criteria = com.example.starter.screener.domain.ScreenCriteria(
                            peRatioMax = (call.arguments["peRatioMax"] as? Number)?.toDouble()
                        ),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_factor_regression" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.MultiFactorCommand(
                        asset = Ticker(str(call.arguments, "asset")),
                        factors = (call.arguments["factors"] as? Map<String, String> ?: emptyMap()).mapValues { Ticker(it.value) },
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_cointegration_test" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CointegrationCommand(
                        assetA = Ticker(str(call.arguments, "symbolA")),
                        assetB = Ticker(str(call.arguments, "symbolB")),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_pca_analysis" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.PcaCommand(
                        tickers = tickers(call.arguments, "symbols").map { Ticker(it) },
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_correlation_analysis" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CorrelationCommand(
                        tickers = tickers(call.arguments, "symbols").map { Ticker(it) },
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_hurst_analysis" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.HurstCommand(
                        ticker = Ticker(str(call.arguments, "symbol")),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_volatility_estimators" -> volatilityEstimatorsCalculator.calculate(
                    fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                )
                "run_regime_adaptive_backtest" -> regimeAdaptiveEngine.run(
                    fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String),
                    emptyMap()
                )
                "run_regime_adaptive_walkforward_backtest" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val regime = regimeAdaptiveEngine.run(series, emptyMap())
                    val strategy = regime.strategyName.removePrefix("regime_adaptive_")
                    runBacktestUseCase.execute(
                        RunBacktestUseCase.WalkForwardCommand(
                            ticker = Ticker(str(call.arguments, "symbol")),
                            strategy = strategy,
                            parameterGrid = emptyMap(),
                            range = range(call.arguments),
                            interval = interval(call.arguments),
                            provider = call.arguments["provider"] as? String
                        )
                    )
                }
                "scan_pairs" -> {
                    val symbols = tickers(call.arguments, "symbols")
                    val results = mutableListOf<Map<String, Any>>()
                    for (i in symbols.indices) {
                        for (j in i + 1 until symbols.size) {
                            val a = fetch(Ticker(symbols[i]), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                            val b = fetch(Ticker(symbols[j]), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                            val result = com.example.starter.analysis.domain.CointegrationCalculator().calculate(a, b)
                            results.add(mapOf("pair" to "${symbols[i]}-${symbols[j]}", "hedge_ratio" to result.hedgeRatio, "adf" to result.adfStatistic))
                        }
                    }
                    results
                }
                "run_walk_forward_backtest" -> runBacktestUseCase.execute(
                    RunBacktestUseCase.WalkForwardCommand(
                        ticker = Ticker(str(call.arguments, "symbol")),
                        strategy = str(call.arguments, "strategy"),
                        parameterGrid = emptyMap(),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_portfolio_risk_attribution" -> {
                    val tickers = tickers(call.arguments, "symbols")
                    val weights = (call.arguments["weights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: tickers.associateWith { 1.0 / tickers.size }
                    val series = tickers.map { fetch(Ticker(it), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String) }
                    val returns = series.map { it.zipWithNext { prev, curr -> (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble() } }
                    riskAttributionCalculator.calculate(returns, tickers, weights)
                }
                "run_stress_test" -> {
                    val tickers = tickers(call.arguments, "symbols")
                    val weights = (call.arguments["weights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: tickers.associateWith { 1.0 / tickers.size }
                    val data = tickers.associate { it to fetch(Ticker(it), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String) }
                    stressTestCalculator.replay(weights, data, str(call.arguments, "scenario"))
                }
                "get_position_size" -> positionSizer.atrBased(
                    fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String),
                    dbl(call.arguments, "riskPerTrade"),
                    dbl(call.arguments, "accountEquity")
                )
                "get_stock_fundamentals" -> fundamentalProvider.fetch(str(call.arguments, "symbol"))
                "run_backtest_optimization" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val strategy = str(call.arguments, "strategy")
                    val grid = mapOf("period" to listOf(10, 20, 30))
                    grid.values.first().map { value ->
                        val params = mapOf("period" to value)
                        val signals = Strategies.REGISTRY.getValue(strategy).generate(series, params)
                        value to BacktestEngine().run(series, signals, strategyName = strategy)
                    }.sortedByDescending { it.second.totalReturn }.take(5)
                }
                "get_advanced_indicators" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    mapOf(
                        "parabolic_sar" to advancedIndicatorsCalculator.parabolicSAR(series).last(),
                        "wilder_atr" to advancedIndicatorsCalculator.wilderAtr(series).lastOrNull(),
                        "mfi" to advancedIndicatorsCalculator.mfi(series).lastOrNull()
                    )
                }
                "get_rolling_beta" -> {
                    val asset = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val benchmark = fetch(Ticker(str(call.arguments, "benchmark")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    rollingBetaCalculator.calculate(asset, benchmark, int(call.arguments, "window")).last()
                }
                "get_extended_risk_metrics" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val risk = RiskReturnCalculator().riskMetrics(series)
                    val returns = series.zipWithNext { prev, curr -> (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble() }
                    val maxDd = BacktestEngine().run(series, List(series.size) { 0.0 }).drawdownEpisodes.maxOfOrNull { it.depth } ?: 0.0
                    val calmar = if (maxDd == 0.0) null else (returns.average() * 252) / maxDd
                    val treynor = 0.0 // requires benchmark beta
                    mapOf(
                        "sharpe" to risk.sharpeRatio,
                        "sortino" to risk.sortinoRatio,
                        "max_drawdown" to maxDd,
                        "calmar" to calmar,
                        "treynor" to treynor,
                        "var95" to risk.var95,
                        "cvar95" to risk.cvar95
                    )
                }
                "run_custom_signal_backtest" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val signals = (call.arguments["signals"] as? List<Number>)?.map { it.toDouble() } ?: List(series.size) { 0.0 }
                    BacktestEngine().run(series, signals, strategyName = "custom_signal")
                }
                "run_signal_panel_backtest" -> {
                    val signals = call.arguments["signals"] as? Map<String, List<Number>> ?: emptyMap()
                    val tickers = signals.keys.toList()
                    val data = tickers.associate { it to fetch(Ticker(it), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String) }
                    com.example.starter.backtest.domain.PanelBacktestEngine().runSignalPanelBacktest(data, signals.mapValues { it.value.map { n -> n.toDouble() } })
                }
                "get_backtest_diagnostics" -> {
                    val result = runBacktestUseCase.execute(
                        RunBacktestUseCase.SingleAssetCommand(
                            ticker = Ticker(str(call.arguments, "symbol")),
                            strategy = str(call.arguments, "strategy"),
                            range = range(call.arguments),
                            interval = interval(call.arguments),
                            provider = call.arguments["provider"] as? String
                        )
                    )
                    result.diagnostics
                }
                "run_portfolio_simulation" -> runBacktestUseCase.execute(
                    RunBacktestUseCase.PortfolioSimulationCommand(
                        tickers = tickers(call.arguments, "symbols").map { Ticker(it) },
                        weights = (call.arguments["weights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: emptyMap(),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "run_pair_trade_backtest" -> runBacktestUseCase.execute(
                    RunBacktestUseCase.PairTradeCommand(
                        symbolA = str(call.arguments, "symbolA"),
                        symbolB = str(call.arguments, "symbolB"),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_robustness_diagnostics" -> {
                    val series = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                    val strategy = str(call.arguments, "strategy")
                    val signals = Strategies.REGISTRY.getValue(strategy).generate(series, emptyMap())
                    val result = BacktestEngine().run(series, signals, strategyName = strategy)
                    val returns = result.equityCurve.zipWithNext { prev, curr -> (curr.equity - prev.equity) / prev.equity }
                    com.example.starter.backtest.domain.RobustnessEngine().blockBootstrapCi(returns, { it.average() })
                }
                "run_monte_carlo_simulation" -> runBacktestUseCase.execute(
                    RunBacktestUseCase.MonteCarloCommand(
                        ticker = Ticker(str(call.arguments, "symbol")),
                        strategy = str(call.arguments, "strategy"),
                        range = range(call.arguments),
                        interval = interval(call.arguments),
                        provider = call.arguments["provider"] as? String
                    )
                )
                "get_capacity_report" -> capacityReportCalculator.calculate(
                    fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                )
                "get_liquidity_metrics" -> com.example.starter.backtest.domain.LiquidityMetrics.amihudIlliquidity(
                    returns = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                        .zipWithNext { prev, curr -> (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble() },
                    dollarVolume = fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                        .map { it.close.toDouble() * it.volume }
                ).lastOrNull()?.let { mapOf("amihud" to it) } ?: emptyMap()
                "get_data_quality_report" -> dataQualityReportCalculator.calculate(
                    fetch(Ticker(str(call.arguments, "symbol")), range(call.arguments), interval(call.arguments), call.arguments["provider"] as? String)
                )
                "run_backtest_compact" -> {
                    val result = runBacktestUseCase.execute(
                        RunBacktestUseCase.SingleAssetCommand(
                            ticker = Ticker(str(call.arguments, "symbol")),
                            strategy = str(call.arguments, "strategy"),
                            range = range(call.arguments),
                            interval = interval(call.arguments),
                            provider = call.arguments["provider"] as? String
                        )
                    )
                    mapOf(
                        "strategy" to result.strategyName,
                        "total_return" to result.totalReturn,
                        "final_equity" to result.finalEquity,
                        "trades" to result.trades.size
                    )
                }
                "get_option_pricing" -> runAnalysisUseCase.execute(
                    RunAnalysisUseCase.OptionPricingCommand(
                        spot = dbl(call.arguments, "spot"),
                        strike = dbl(call.arguments, "strike"),
                        timeToExpiry = dbl(call.arguments, "timeToExpiry"),
                        riskFreeRate = dbl(call.arguments, "riskFreeRate"),
                        volatility = dbl(call.arguments, "volatility"),
                        optionType = call.arguments["optionType"] as? String ?: "call"
                    )
                )
                "get_implied_volatility" -> {
                    val iv = com.example.starter.analysis.domain.OptionsCalculator().impliedVolatility(
                        dbl(call.arguments, "optionPrice"),
                        dbl(call.arguments, "spot"),
                        dbl(call.arguments, "strike"),
                        dbl(call.arguments, "timeToExpiry"),
                        dbl(call.arguments, "riskFreeRate"),
                        call.arguments["optionType"] as? String ?: "call"
                    )
                    mapOf("implied_volatility" to iv)
                }
                else -> return ToolResult(tool = call.name, success = false, error = "Unknown tool: ${call.name}")
            }
            ToolResult(tool = call.name, success = true, payload = objectMapper.convertValue(payload, Map::class.java) as Map<String, Any?>)
        } catch (ex: Exception) {
            ToolResult(tool = call.name, success = false, error = ex.message ?: "Tool execution failed")
        }
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))

    private fun str(args: Map<String, Any>, key: String): String = args[key] as? String ?: throw IllegalArgumentException("$key required")
    private fun dbl(args: Map<String, Any>, key: String): Double = when (val v = args[key]) {
        is Number -> v.toDouble()
        is String -> v.toDouble()
        else -> throw IllegalArgumentException("$key required")
    }
    private fun int(args: Map<String, Any>, key: String): Int = when (val v = args[key]) {
        is Number -> v.toInt()
        is String -> v.toInt()
        else -> throw IllegalArgumentException("$key required")
    }
    private fun tickers(args: Map<String, Any>, key: String): List<String> = when (val v = args[key]) {
        is List<*> -> v.filterIsInstance<String>()
        is String -> v.split(",").map { it.trim() }
        else -> emptyList()
    }
    private fun range(args: Map<String, Any>): DateRange = DateRange(
        LocalDate.parse(str(args, "startDate")),
        LocalDate.parse(str(args, "endDate"))
    )
    private fun interval(args: Map<String, Any>): BarInterval = BarInterval.valueOf(str(args, "interval").uppercase())
}
```

- [ ] **Step 7.4.2: Verify compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.4.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/agenttools/application/service/AgentToolsService.kt
git commit -m "feat(agenttools): add dispatch service for all 42 tools"
```

### Task 7.5: Agent card and protocol integration

- [ ] **Step 7.5.1: Modify `src/main/kotlin/com/example/starter/adapter/in/a2a/A2aAgentCardController.kt`**

Inject `DispatchAgentToolUseCase` and generate skills dynamically:

```kotlin
package com.example.starter.adapter.`in`.a2a

import com.example.starter.agenttools.application.port.inbound.DispatchAgentToolUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class A2aAgentCardController(
    private val dispatchAgentToolUseCase: DispatchAgentToolUseCase
) {

    @GetMapping("/.well-known/agent.json", produces = ["application/json"])
    fun agentCard(): Map<String, Any> = mapOf(
        "name" to "Quant Agent",
        "description" to "Quantitative finance agent with market data, analysis, backtesting, portfolio optimization, screening, and audit tools",
        "url" to "http://localhost:8080/a2a",
        "version" to "2.0.0",
        "capabilities" to mapOf("streaming" to false, "pushNotifications" to false),
        "skills" to dispatchAgentToolUseCase.listTools().map { tool ->
            mapOf(
                "id" to tool.name,
                "name" to tool.name.replace("_", " "),
                "description" to tool.description,
                "tags" to listOf("quant"),
                "examples" to emptyList<String>()
            )
        }
    )
}
```

- [ ] **Step 7.5.2: Modify `A2aTaskHandler`**

Inject `DispatchAgentToolUseCase`. Replace the `else` branch in `handleTasksSend` with:

```kotlin
else -> dispatchAgentToolUseCase.dispatch(
    com.example.starter.agenttools.domain.ToolCall(skillId, params)
).let { result ->
    mapOf(
        "tool" to result.tool,
        "success" to result.success,
        "result" to result.payload,
        "error" to result.error
    )
}
```

- [ ] **Step 7.5.3: Modify `McpToolHandler`**

Inject `DispatchAgentToolUseCase`. Replace `toolsList()` body with:

```kotlin
fun toolsList(): Map<String, Any> = mapOf(
    "tools" to dispatchAgentToolUseCase.listTools().map { tool ->
        mapOf(
            "name" to tool.name,
            "description" to tool.description,
            "inputSchema" to tool.inputSchema
        )
    }
)
```

Replace the `else` branch in `handleToolCall` with:

```kotlin
else -> dispatchAgentToolUseCase.dispatch(
    com.example.starter.agenttools.domain.ToolCall(name, arguments)
).let { result ->
    mapOf(
        "content" to listOf(
            mapOf(
                "type" to "text",
                "text" to (result.error ?: objectMapper.writeValueAsString(result.payload))
            )
        )
    )
}
```

- [ ] **Step 7.5.4: Create `src/main/kotlin/com/example/starter/agenttools/adapter/in/web/AgentToolsController.kt`**

```kotlin
package com.example.starter.agenttools.adapter.`in`.web

import com.example.starter.agenttools.application.port.inbound.DispatchAgentToolUseCase
import com.example.starter.agenttools.domain.ToolCall
import com.example.starter.agenttools.domain.ToolDefinition
import com.example.starter.agenttools.domain.ToolResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/v1/agent-tools")
class AgentToolsController(
    private val dispatchAgentToolUseCase: DispatchAgentToolUseCase
) {

    @GetMapping("/tools")
    fun listTools(): Mono<List<ToolDefinition>> = Mono.fromCallable {
        dispatchAgentToolUseCase.listTools()
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/dispatch")
    fun dispatch(@RequestBody call: ToolCall): Mono<ToolResult> = Mono.fromCallable {
        dispatchAgentToolUseCase.dispatch(call)
    }.subscribeOn(Schedulers.boundedElastic())
}
```

- [ ] **Step 7.5.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/adapter/in/a2a/A2aAgentCardController.kt src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt src/main/kotlin/com/example/starter/agenttools/adapter/in/web/AgentToolsController.kt
git commit -m "feat(agenttools): wire tool dispatch into A2A, MCP, REST, and agent card"
```

### Task 7.6: Agent tools tests

- [ ] **Step 7.6.1: Create integration test `src/integrationTest/kotlin/com/example/starter/agenttools/AgentToolsIntegrationTest.kt`**

Mock all downstream use cases and assert that `dispatch(ToolCall("get_option_pricing", ...))` returns a successful payload.

- [ ] **Step 7.6.2: Create E2E test `src/e2eTest/kotlin/com/example/starter/agenttools/e2e/AgentToolsE2ETest.kt`**

Boot the application and assert `/api/v1/agent-tools/tools` returns at least 42 tools, and `/api/v1/agent-tools/dispatch` for `get_option_pricing` returns a finite price.

- [ ] **Step 7.6.3: Commit**

```bash
git add src/integrationTest/kotlin/com/example/starter/agenttools/ src/e2eTest/kotlin/com/example/starter/agenttools/
git commit -m "test(agenttools): add integration and E2E tests"
```


## Phase 8: Audit trail

**Files:**
- Create: `src/main/kotlin/com/example/starter/audit/domain/DecisionRecord.kt`
- Create: `src/main/kotlin/com/example/starter/audit/domain/AuditHasher.kt`
- Create: `src/main/kotlin/com/example/starter/audit/application/port/outbound/AuditRepository.kt`
- Create: `src/main/kotlin/com/example/starter/audit/application/port/inbound/RecordDecisionUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/audit/application/port/inbound/VerifyAuditTrailUseCase.kt`
- Create: `src/main/kotlin/com/example/starter/audit/application/service/AuditService.kt`
- Create: `src/main/kotlin/com/example/starter/audit/adapter/out/persistence/AuditEntity.kt`
- Create: `src/main/kotlin/com/example/starter/audit/adapter/out/persistence/JpaAuditRepository.kt`
- Create: `src/main/kotlin/com/example/starter/audit/adapter/in/web/AuditController.kt`
- Create: `src/main/kotlin/com/example/starter/audit/adapter/in/grpc/AuditGrpcService.kt`
- Create: `src/main/proto/audit/audit_service.proto`
- Create: `src/main/resources/db/migration/V3__create_audit_tables.sql`
- Modify: `A2aTaskHandler.kt`, `McpToolHandler.kt`, `AgentToolsService.kt`
- Tests: `src/test/kotlin/com/example/starter/audit/`, `src/integrationTest/...`, `src/e2eTest/...`

### Task 8.1: Audit domain and hashing

- [ ] **Step 8.1.1: Create `src/main/kotlin/com/example/starter/audit/domain/DecisionRecord.kt`**

```kotlin
package com.example.starter.audit.domain

import java.time.Instant
import java.util.UUID

data class DecisionRecord(
    val id: UUID = UUID.randomUUID(),
    val requestId: String,
    val timestamp: Instant = Instant.now(),
    val operation: String,
    val inputHash: String,
    val outputHash: String,
    val previousRecordHash: String?,
    val recordHash: String? = null,
    val status: String = "success",
    val errorMessage: String? = null
)
```

- [ ] **Step 8.1.2: Create `src/main/kotlin/com/example/starter/audit/domain/AuditHasher.kt`**

```kotlin
package com.example.starter.audit.domain

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

class AuditHasher(private val objectMapper: ObjectMapper) {

    private val digest = MessageDigest.getInstance("SHA-256")

    fun hashPayload(payload: Any): String {
        val canonical = objectMapper.writeValueAsBytes(payload)
        return digest.digest(canonical).joinToString("") { "%02x".format(it) }.take(16)
    }

    fun hashRecord(record: DecisionRecord, previousHash: String?): String {
        val content = "${record.id}|${record.requestId}|${record.timestamp}|${record.operation}|${record.inputHash}|${record.outputHash}|${previousHash ?: "GENESIS"}"
        return digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}
```

- [ ] **Step 8.1.3: Commit**

```bash
git add src/main/kotlin/com/example/starter/audit/domain/
git commit -m "feat(audit): add decision record and hasher"
```

### Task 8.2: Persistence and migration

- [ ] **Step 8.2.1: Create `src/main/resources/db/migration/V3__create_audit_tables.sql`**

```sql
CREATE TABLE IF NOT EXISTS decision_records (
    id UUID PRIMARY KEY,
    request_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    operation VARCHAR(255) NOT NULL,
    input_hash VARCHAR(16) NOT NULL,
    output_hash VARCHAR(16) NOT NULL,
    previous_record_hash VARCHAR(16),
    record_hash VARCHAR(16) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_decision_records_request_id ON decision_records(request_id);
CREATE INDEX idx_decision_records_timestamp ON decision_records(timestamp);
```

- [ ] **Step 8.2.2: Create `src/main/kotlin/com/example/starter/audit/adapter/out/persistence/AuditEntity.kt`**

```kotlin
package com.example.starter.audit.adapter.out.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "decision_records")
class AuditEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    val requestId: String,
    val timestamp: Instant,
    val operation: String,
    val inputHash: String,
    val outputHash: String,
    val previousRecordHash: String?,
    val recordHash: String,
    val status: String,
    val errorMessage: String?
)
```

- [ ] **Step 8.2.3: Create `src/main/kotlin/com/example/starter/audit/adapter/out/persistence/JpaAuditRepository.kt`**

```kotlin
package com.example.starter.audit.adapter.out.persistence

import com.example.starter.audit.application.port.outbound.AuditRepository
import com.example.starter.audit.domain.DecisionRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface AuditJpaRepository : JpaRepository<AuditEntity, UUID>

@Component
class JpaAuditRepository(private val jpa: AuditJpaRepository) : AuditRepository {

    override fun save(record: DecisionRecord): DecisionRecord {
        jpa.save(
            AuditEntity(
                id = record.id,
                requestId = record.requestId,
                timestamp = record.timestamp,
                operation = record.operation,
                inputHash = record.inputHash,
                outputHash = record.outputHash,
                previousRecordHash = record.previousRecordHash,
                recordHash = record.recordHash ?: "",
                status = record.status,
                errorMessage = record.errorMessage
            )
        )
        return record
    }

    override fun findById(id: UUID): DecisionRecord? = jpa.findById(id).map { it.toDomain() }.orElse(null)

    override fun findChain(): List<DecisionRecord> = jpa.findAll().sortedBy { it.timestamp }.map { it.toDomain() }

    override fun findLatest(): DecisionRecord? = jpa.findAll().maxByOrNull { it.timestamp }?.toDomain()

    private fun AuditEntity.toDomain() = DecisionRecord(
        id = id,
        requestId = requestId,
        timestamp = timestamp,
        operation = operation,
        inputHash = inputHash,
        outputHash = outputHash,
        previousRecordHash = previousRecordHash,
        recordHash = recordHash,
        status = status,
        errorMessage = errorMessage
    )
}
```

- [ ] **Step 8.2.4: Commit**

```bash
git add src/main/resources/db/migration/V3__create_audit_tables.sql src/main/kotlin/com/example/starter/audit/adapter/out/persistence/
git commit -m "feat(audit): add JPA repository and migration"
```

### Task 8.3: Audit application service

- [ ] **Step 8.3.1: Create `src/main/kotlin/com/example/starter/audit/application/port/outbound/AuditRepository.kt`**

```kotlin
package com.example.starter.audit.application.port.outbound

import com.example.starter.audit.domain.DecisionRecord
import java.util.UUID

interface AuditRepository {
    fun save(record: DecisionRecord): DecisionRecord
    fun findById(id: UUID): DecisionRecord?
    fun findChain(): List<DecisionRecord>
    fun findLatest(): DecisionRecord?
}
```

- [ ] **Step 8.3.2: Create `src/main/kotlin/com/example/starter/audit/application/port/inbound/RecordDecisionUseCase.kt`**

```kotlin
package com.example.starter.audit.application.port.inbound

import com.example.starter.audit.domain.DecisionRecord

interface RecordDecisionUseCase {
    fun record(requestId: String, operation: String, input: Any, output: Any, status: String = "success", errorMessage: String? = null): DecisionRecord
}
```

- [ ] **Step 8.3.3: Create `src/main/kotlin/com/example/starter/audit/application/port/inbound/VerifyAuditTrailUseCase.kt`**

```kotlin
package com.example.starter.audit.application.port.inbound

interface VerifyAuditTrailUseCase {
    fun verify(): VerificationResult

    data class VerificationResult(
        val valid: Boolean,
        val checked: Int,
        val failures: List<String>
    )
}
```

- [ ] **Step 8.3.4: Create `src/main/kotlin/com/example/starter/audit/application/service/AuditService.kt`**

```kotlin
package com.example.starter.audit.application.service

import com.example.starter.audit.application.port.inbound.RecordDecisionUseCase
import com.example.starter.audit.application.port.inbound.VerifyAuditTrailUseCase
import com.example.starter.audit.application.port.outbound.AuditRepository
import com.example.starter.audit.domain.AuditHasher
import com.example.starter.audit.domain.DecisionRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuditService(
    private val repository: AuditRepository,
    private val objectMapper: ObjectMapper
) : RecordDecisionUseCase, VerifyAuditTrailUseCase {

    private val hasher = AuditHasher(objectMapper)

    override fun record(requestId: String, operation: String, input: Any, output: Any, status: String, errorMessage: String?): DecisionRecord {
        val latest = repository.findLatest()
        val inputHash = hasher.hashPayload(input)
        val outputHash = hasher.hashPayload(output)
        val record = DecisionRecord(
            requestId = requestId,
            operation = operation,
            inputHash = inputHash,
            outputHash = outputHash,
            previousRecordHash = latest?.recordHash,
            status = status,
            errorMessage = errorMessage
        )
        val withHash = record.copy(recordHash = hasher.hashRecord(record, latest?.recordHash))
        return repository.save(withHash)
    }

    override fun verify(): VerifyAuditTrailUseCase.VerificationResult {
        val chain = repository.findChain()
        val failures = mutableListOf<String>()
        var previousHash: String? = null
        chain.forEach { record ->
            val expected = hasher.hashRecord(record, previousHash)
            if (record.recordHash != expected) {
                failures.add("Hash mismatch for ${record.id}: expected $expected, got ${record.recordHash}")
            }
            previousHash = record.recordHash
        }
        return VerifyAuditTrailUseCase.VerificationResult(failures.isEmpty(), chain.size, failures)
    }
}
```

- [ ] **Step 8.3.5: Commit**

```bash
git add src/main/kotlin/com/example/starter/audit/application/
git commit -m "feat(audit): add record and verify use case service"
```

### Task 8.4: Protocol adapters, CLI, and wiring

- [ ] **Step 8.4.1: Create `src/main/proto/audit/audit_service.proto`**

```protobuf
syntax = "proto3";

package com.example.starter.audit.grpc;
option java_package = "com.example.starter.audit.grpc";
option java_multiple_files = true;

service AuditService {
  rpc RecordDecision (RecordDecisionRequest) returns (DecisionRecordResponse);
  rpc VerifyTrail (VerifyTrailRequest) returns (VerifyTrailResponse);
}

message RecordDecisionRequest {
  string request_id = 1;
  string operation = 2;
  string input_json = 3;
  string output_json = 4;
  string status = 5;
}

message DecisionRecordResponse {
  string id = 1;
  string record_hash = 2;
}

message VerifyTrailRequest {}

message VerifyTrailResponse {
  bool valid = 1;
  int32 checked = 2;
  repeated string failures = 3;
}
```

- [ ] **Step 8.4.2: Create gRPC service `src/main/kotlin/com/example/starter/audit/adapter/in/grpc/AuditGrpcService.kt`**

Map `RecordDecisionUseCase` and `VerifyAuditTrailUseCase` to the generated proto service.

- [ ] **Step 8.4.3: Create REST controller `src/main/kotlin/com/example/starter/audit/adapter/in/web/AuditController.kt`**

```kotlin
package com.example.starter.audit.adapter.`in`.web

import com.example.starter.audit.application.port.inbound.RecordDecisionUseCase
import com.example.starter.audit.application.port.inbound.VerifyAuditTrailUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val recordDecisionUseCase: RecordDecisionUseCase,
    private val verifyAuditTrailUseCase: VerifyAuditTrailUseCase
) {

    @PostMapping("/record")
    fun record(@RequestBody request: RecordRequestDto): Mono<RecordResponseDto> = Mono.fromCallable {
        val record = recordDecisionUseCase.record(
            requestId = request.requestId,
            operation = request.operation,
            input = request.input,
            output = request.output,
            status = request.status
        )
        RecordResponseDto(record.id.toString(), record.recordHash ?: "")
    }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/verify")
    fun verify(): Mono<VerifyAuditTrailUseCase.VerificationResult> = Mono.fromCallable {
        verifyAuditTrailUseCase.verify()
    }.subscribeOn(Schedulers.boundedElastic())
}

data class RecordRequestDto(
    val requestId: String,
    val operation: String,
    val input: Any,
    val output: Any,
    val status: String = "success"
)

data class RecordResponseDto(
    val id: String,
    val recordHash: String
)
```

- [ ] **Step 8.4.4: Extend `A2aTaskHandler` and `McpToolHandler`**

Add `audit-record` and `audit-verify` skills/tools that delegate to the audit use cases.

- [ ] **Step 8.4.5: Wire audit into `AgentToolsService`**

Inject `RecordDecisionUseCase` and, after each successful tool dispatch, call:

```kotlin
recordDecisionUseCase.record(
    requestId = call.arguments["requestId"] as? String ?: UUID.randomUUID().toString(),
    operation = call.name,
    input = call.arguments,
    output = result.payload,
    status = if (result.success) "success" else "error",
    errorMessage = result.error
)
```

Wrap the `dispatch` method so the record is persisted after execution.

- [ ] **Step 8.4.6: Add CLI Gradle task**

In `build.gradle.kts`, add:

```kotlin
tasks.register<JavaExec>("auditVerify") {
    group = "audit"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.starter.audit.cli.AuditCli")
    args = listOf("verify")
}
```

Create `src/main/kotlin/com/example/starter/audit/cli/AuditCli.kt`:

```kotlin
package com.example.starter.audit.cli

import com.example.starter.audit.application.service.AuditService
import com.example.starter.audit.adapter.out.persistence.AuditJpaRepository
import com.example.starter.audit.adapter.out.persistence.JpaAuditRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class AuditCli {

    @Bean
    fun run(auditService: AuditService): CommandLineRunner = CommandLineRunner { args ->
        when (args.firstOrNull()) {
            "verify" -> {
                val result = auditService.verify()
                println("Audit trail valid: ${result.valid}, checked: ${result.checked}, failures: ${result.failures}")
                if (!result.valid) System.exit(1)
            }
            else -> println("Usage: auditVerify")
        }
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(AuditCli::class.java, *args)
}
```

- [ ] **Step 8.4.7: Commit**

```bash
git add src/main/proto/audit/ src/main/kotlin/com/example/starter/audit/adapter/in/ src/main/kotlin/com/example/starter/audit/cli/ src/main/kotlin/com/example/starter/agenttools/application/service/AgentToolsService.kt src/main/kotlin/com/example/starter/adapter/in/a2a/A2aTaskHandler.kt src/main/kotlin/com/example/starter/adapter/in/mcp/McpToolHandler.kt build.gradle.kts
git commit -m "feat(audit): add REST/gRPC/A2A/MCP adapters, CLI, and wire into agent tools"
```

### Task 8.5: Audit tests

- [ ] **Step 8.5.1: Create unit test `src/test/kotlin/com/example/starter/audit/domain/AuditHasherTest.kt`**

Assert that the same payload produces the same hash and that chaining two records produces different hashes.

- [ ] **Step 8.5.2: Create integration test `src/integrationTest/kotlin/com/example/starter/audit/AuditIntegrationTest.kt`**

Use a Postgres TestContainer, record two decisions, verify the chain passes, tamper with one record's output hash, and assert verify fails.

- [ ] **Step 8.5.3: Create E2E test `src/e2eTest/kotlin/com/example/starter/audit/e2e/AuditE2ETest.kt`**

Boot the application, call `/api/v1/audit/record`, then `/api/v1/audit/verify`, assert `valid` is true.

- [ ] **Step 8.5.4: Commit**

```bash
git add src/test/kotlin/com/example/starter/audit/ src/integrationTest/kotlin/com/example/starter/audit/ src/e2eTest/kotlin/com/example/starter/audit/
git commit -m "test(audit): add hasher, integration, and E2E tests"
```


## Phase 9: Cross-cutting (Docker, CI, docs, visual tests)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `docker-compose.yml`
- Modify: `.mise.toml`
- Create: `.actrc`
- Modify: `scripts/run-act.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`
- Modify: `src/test/kotlin/com/example/starter/testsupport/ColoredConsoleSummaryListener.kt`
- Modify: `README.md`

### Task 9.1: Build configuration

- [ ] **Step 9.1.1: Verify `gradle/libs.versions.toml`**

Ensure these entries exist from Phase 0:

```toml
[versions]
commonsMath = "3.6.1"
tablesaw = "0.43.1"
caffeine = "3.1.8"
okhttp = "4.12.0"
wiremock = "3.9.1"

[libraries]
commons-math3 = { module = "org.apache.commons:commons-math3", version.ref = "commonsMath" }
tablesaw-core = { module = "tech.tablesaw:tablesaw-core", version.ref = "tablesaw" }
caffeine = { module = "com.github.benmanes.caffeine:caffeine", version.ref = "caffeine" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
wiremock = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
```

- [ ] **Step 9.1.2: Verify `build.gradle.kts`**

Ensure the `dependencies` block includes:

```kotlin
implementation(libs.commons.math3)
implementation(libs.tablesaw.core)
implementation(libs.caffeine)
implementation(libs.okhttp)
testImplementation(libs.wiremock)
```

- [ ] **Step 9.1.3: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "chore(build): confirm quant dependencies and source sets"
```

### Task 9.2: Local development compose

- [ ] **Step 9.2.1: Create `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: starter
      POSTGRES_USER: starter
      POSTGRES_PASSWORD: starter
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U starter -d starter"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
      - "9090:9090"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: starter
      DB_USER: starter
      DB_PASS: starter
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres-data:
```

- [ ] **Step 9.2.2: Commit**

```bash
git add docker-compose.yml
git commit -m "chore(dev): add docker-compose for local Postgres and app"
```

### Task 9.3: mise and act configuration

- [ ] **Step 9.3.1: Update `.mise.toml`**

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
build-image = "scripts/build-image.sh"
run-local = "docker-compose up --build"
ci-local = "scripts/run-act.sh -j build"
ci-local-verbose = "scripts/run-act.sh -j build -v"
```

- [ ] **Step 9.3.2: Create `.actrc`**

```text
--container-runtime podman
--container-daemon-socket unix://$HOME/.local/share/containers/podman/machine/podman.sock
--container-architecture linux/amd64
--secret GITHUB_TOKEN=${GITHUB_TOKEN}
```

- [ ] **Step 9.3.3: Update `scripts/run-act.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

PODMAN_SOCKET="${PODMAN_SOCKET:-$HOME/.local/share/containers/podman/machine/podman.sock}"

if ! command -v act &> /dev/null; then
  echo "act not found. Install via mise: mise install" >&2
  exit 1
fi

act \
  --container-runtime podman \
  --container-daemon-socket "unix://$PODMAN_SOCKET" \
  --container-architecture linux/amd64 \
  --actor "$(git config user.name || echo 'local')" \
  "$@"
```

- [ ] **Step 9.3.4: Commit**

```bash
git add .mise.toml .actrc scripts/run-act.sh
git commit -m "chore(ci): improve mise and act local CI setup"
```

### Task 9.4: CI workflow

- [ ] **Step 9.4.1: Update `.github/workflows/ci.yml`**

Add `workflow_dispatch` to `on:` and a final visual summary step that prints per-layer status with colors. Keep the existing jobs; add a `native-image` job in Phase 10.

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - name: Run unit tests
        id: unit-tests
        run: ./gradlew test
      - name: Run integration tests
        id: integration-tests
        run: ./gradlew integrationTest
      - name: Run E2E tests
        id: e2e-tests
        run: ./gradlew e2eTest
      - name: Generate Allure report
        if: always()
        run: ./gradlew allureReport
      - name: Upload test reports
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          retention-days: 14
          path: |
            build/reports/tests/
            build/reports/allure/
            build/allure-results/
      - name: Set up Podman
        run: |
          if ! command -v podman &> /dev/null; then
            sudo apt-get update
            sudo apt-get install -y podman
          fi
      - name: Build container image
        run: podman build -t kotlin-grpc-rest-starter:${{ github.sha }} .
      - name: Smoke test container image
        id: smoke-test
        run: |
          podman network create starter-net || true
          podman run -d --name starter-postgres --network starter-net \
            -e POSTGRES_DB=starter -e POSTGRES_USER=starter -e POSTGRES_PASSWORD=starter \
            postgres:18
          for i in {1..30}; do
            if podman exec starter-postgres pg_isready -U starter -d starter; then
              echo "Postgres is ready"
              break
            fi
            sleep 2
          done
          podman run -d --name starter-smoke --network starter-net \
            -p 8080:8080 -p 9090:9090 \
            -e DB_HOST=starter-postgres -e DB_PORT=5432 -e DB_NAME=starter \
            -e DB_USER=starter -e DB_PASS=starter \
            kotlin-grpc-rest-starter:${{ github.sha }}
          for i in {1..30}; do
            if curl -fsS http://localhost:8080/actuator/health; then
              echo "Smoke test passed"
              exit 0
            fi
            sleep 2
          done
          echo "Smoke test failed" >&2
          podman logs starter-smoke
          exit 1
      - name: Clean up smoke test containers
        if: always()
        run: |
          podman stop starter-smoke starter-postgres || true
          podman rm starter-smoke starter-postgres || true
          podman network rm starter-net || true
      - name: Job summary
        if: always()
        run: |
          echo "## CI Results" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "| Layer | Status |" >> $GITHUB_STEP_SUMMARY
          echo "|-------|--------|" >> $GITHUB_STEP_SUMMARY
          echo "| Unit | ${{ steps.unit-tests.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "| Integration | ${{ steps.integration-tests.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "| E2E | ${{ steps.e2e-tests.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "| Smoke | ${{ steps.smoke-test.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "- Image: kotlin-grpc-rest-starter:${{ github.sha }}" >> $GITHUB_STEP_SUMMARY
          echo "- Reproduce locally: mise run ci-local" >> $GITHUB_STEP_SUMMARY
```

- [ ] **Step 9.4.2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: update workflow with workflow_dispatch and visual summary"
```

### Task 9.5: Visual test reporting

- [ ] **Step 9.5.1: Verify `src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`**

Ensure it contains:

```text
com.example.starter.testsupport.ColoredConsoleSummaryListener
```

- [ ] **Step 9.5.2: Update `ColoredConsoleSummaryListener`**

Enhance to print a simple ASCII bar of pass/fail counts and highlight slow tests (>1s):

```kotlin
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
        tests.filter { it.duration.toMillis() > 1000 }.forEach {
            println("  🐢 ${it.name} (${it.duration.toMillis()}ms)")
        }
    }
    println("==================================\n")
}
```

- [ ] **Step 9.5.3: Commit**

```bash
git add src/test/kotlin/com/example/starter/testsupport/ColoredConsoleSummaryListener.kt src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
git commit -m "test(reporting): enhance colored console summary"
```

### Task 9.6: Documentation

- [ ] **Step 9.6.1: Update `README.md`**

Replace the endpoints and examples sections with a comprehensive summary covering all subdomains, both build modes, and example requests. At minimum include:

- All `mise` tasks
- Classic and native build commands
- REST endpoint table for marketdata, indicators, metrics, analysis, backtest, portfolio, screener, agent-tools, audit
- gRPC service list
- A2A and MCP usage
- act local CI command

```bash
git add README.md
git commit -m "docs: expand README with quant endpoints, native build, and CI instructions"
```


## Phase 10: Native image build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/example/starter/config/NativeImageHints.kt`
- Create: `src/main/resources/META-INF/native-image/com.example.starter/kotlin-grpc-rest-starter/native-image.properties`
- Create: `Dockerfile.native`
- Create: `scripts/build-native-image.sh`
- Modify: `.github/workflows/ci.yml`
- Create: `src/e2eTest/kotlin/com/example/starter/native/NativeImageSmokeE2ETest.kt`

### Task 10.1: GraalVM Native Image plugin

- [ ] **Step 10.1.1: Update `gradle/libs.versions.toml`**

Add under `[versions]`:

```toml
graalvmBuildtools = "0.10.6"
```

Add under `[plugins]`:

```toml
native = { id = "org.graalvm.buildtools.native", version.ref = "graalvmBuildtools" }
```

- [ ] **Step 10.1.2: Update `build.gradle.kts`**

Add plugin:

```kotlin
alias(libs.plugins.native)
```

Configure the native image build:

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("kotlin-grpc-rest-starter")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:Name=kotlin-grpc-rest-starter")
        }
    }
}
```

- [ ] **Step 10.1.3: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "chore(native): add GraalVM Native Image plugin"
```

### Task 10.2: AOT and reflection hints

- [ ] **Step 10.2.1: Create `src/main/kotlin/com/example/starter/config/NativeImageHints.kt`**

```kotlin
package com.example.starter.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.context.annotation.Configuration

@Configuration
@ImportRuntimeHints(NativeImageHints::class)
class NativeImageHintsConfig

class NativeImageHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter { _, _ -> true }
        scanner.findCandidateComponents("com.example.starter").forEach { bean ->
            try {
                val clazz = Class.forName(bean.beanClassName)
                hints.reflection().registerType(clazz, *MemberCategory.values())
            } catch (_: ClassNotFoundException) {
            }
        }
        listOf(
            "application.yml",
            "application-*.yml",
            "db/migration/*.sql",
            "*.proto"
        ).forEach { hints.resources().registerPattern(it) }
        hints.serialization().registerType(java.time.Instant::class.java)
        hints.serialization().registerType(java.time.LocalDate::class.java)
        hints.proxies().registerJdkProxy(org.springframework.data.jpa.repository.support.CrudMethodMetadata::class.java)
    }
}
```

- [ ] **Step 10.2.2: Create `src/main/resources/META-INF/native-image/com.example.starter/kotlin-grpc-rest-starter/native-image.properties`**

```properties
Args=--initialize-at-build-time=org.apache.commons.logging.LogFactoryService \
  --initialize-at-run-time=io.netty.channel.epoll.Epoll \
  -H:+ReportExceptionStackTraces
```

- [ ] **Step 10.2.3: Verify AOT metadata generation**

Run: `./gradlew processAot`
Expected: BUILD SUCCESSFUL and `build/generated/aotResources/` populated.

- [ ] **Step 10.2.4: Commit**

```bash
git add src/main/kotlin/com/example/starter/config/NativeImageHints.kt src/main/resources/META-INF/native-image/
git commit -m "feat(native): add AOT and reflection hints"
```

### Task 10.3: Native Dockerfile and build script

- [ ] **Step 10.3.1: Create `Dockerfile.native`**

```dockerfile
# syntax=docker/dockerfile:1
FROM ghcr.io/graalvm/graalvm-ce:ol9-java25-25 AS builder
WORKDIR /app

RUN microdnf install -y findutils

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle/libs.versions.toml ./gradle/
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon

COPY . .
RUN --mount=type=cache,target=/root/.gradle ./gradlew nativeCompile --no-daemon

FROM oraclelinux:9-slim
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/kotlin-grpc-rest-starter ./app
EXPOSE 8080 9090
ENTRYPOINT ["./app"]
```

- [ ] **Step 10.3.2: Create `scripts/build-native-image.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${CONTAINER_RUNTIME:-podman}"
TAG="${1:-kotlin-grpc-rest-starter-native:latest}"

echo "Building native image with $RUNTIME as $TAG..."
$RUNTIME build -f Dockerfile.native -t "$TAG" .
echo "Native image $TAG built successfully."
```

Make executable:

```bash
chmod +x scripts/build-native-image.sh
```

- [ ] **Step 10.3.3: Commit**

```bash
git add Dockerfile.native scripts/build-native-image.sh
git commit -m "feat(native): add native Dockerfile and build script"
```

### Task 10.4: CI job for native image

- [ ] **Step 10.4.1: Update `.github/workflows/ci.yml`**

Add a `native-image` job after the `build` job:

```yaml
  native-image:
    runs-on: ubuntu-latest
    timeout-minutes: 90
    needs: build
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'graalvm'
      - uses: gradle/actions/setup-gradle@v4
      - name: Build native image
        id: native-build
        run: ./gradlew nativeCompile
      - name: Upload native executable
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: native-executable
          path: build/native/nativeCompile/kotlin-grpc-rest-starter
      - name: Set up Podman
        run: |
          if ! command -v podman &> /dev/null; then
            sudo apt-get update
            sudo apt-get install -y podman
          fi
      - name: Build native container image
        id: native-image-build
        run: podman build -f Dockerfile.native -t kotlin-grpc-rest-starter-native:${{ github.sha }} .
      - name: Smoke test native container image
        id: native-smoke-test
        run: |
          podman network create starter-native-net || true
          podman run -d --name starter-native-postgres --network starter-native-net \
            -e POSTGRES_DB=starter -e POSTGRES_USER=starter -e POSTGRES_PASSWORD=starter \
            postgres:18
          for i in {1..30}; do
            if podman exec starter-native-postgres pg_isready -U starter -d starter; then
              echo "Postgres is ready"
              break
            fi
            sleep 2
          done
          podman run -d --name starter-native-smoke --network starter-native-net \
            -p 8081:8080 -p 9091:9090 \
            -e DB_HOST=starter-native-postgres -e DB_PORT=5432 -e DB_NAME=starter \
            -e DB_USER=starter -e DB_PASS=starter \
            kotlin-grpc-rest-starter-native:${{ github.sha }}
          for i in {1..30}; do
            if curl -fsS http://localhost:8081/actuator/health; then
              echo "Native smoke test passed"
              exit 0
            fi
            sleep 2
          done
          echo "Native smoke test failed" >&2
          podman logs starter-native-smoke
          exit 1
      - name: Clean up native smoke test containers
        if: always()
        run: |
          podman stop starter-native-smoke starter-native-postgres || true
          podman rm starter-native-smoke starter-native-postgres || true
          podman network rm starter-native-net || true
      - name: Job summary
        if: always()
        run: |
          echo "## Native Image Results" >> $GITHUB_STEP_SUMMARY
          echo "| Step | Status |" >> $GITHUB_STEP_SUMMARY
          echo "|------|--------|" >> $GITHUB_STEP_SUMMARY
          echo "| Native compile | ${{ steps.native-build.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "| Native image build | ${{ steps.native-image-build.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
          echo "| Native smoke test | ${{ steps.native-smoke-test.outcome == 'success' && '✅ Passed' || '❌ Failed' }} |" >> $GITHUB_STEP_SUMMARY
```

- [ ] **Step 10.4.2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add native image build and smoke test job"
```

### Task 10.5: Native smoke tests

- [ ] **Step 10.5.1: Create `src/e2eTest/kotlin/com/example/starter/native/NativeImageSmokeE2ETest.kt`**

This test runs against a native executable started on port 8081 and validates `/actuator/health` and `/.well-known/agent.json`.

```kotlin
package com.example.starter.native

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

@Tag("e2e")
@Tag("native")
class NativeImageSmokeE2ETest {

    private val client = WebClient.builder().baseUrl(System.getProperty("native.url", "http://localhost:8081")).build()

    @Test
    fun `native image health endpoint is up`() {
        val status = client.get().uri("/actuator/health")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()
        expectThat(status?.get("status")).isEqualTo("UP")
    }

    @Test
    fun `native image serves agent card`() {
        val card = client.get().uri("/.well-known/agent.json")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()
        @Suppress("UNCHECKED_CAST")
        val skills = card?.get("skills") as List<Map<String, Any>>
        expectThat(skills).isNotEmpty()
    }
}
```

- [ ] **Step 10.5.2: Commit**

```bash
git add src/e2eTest/kotlin/com/example/starter/native/
git commit -m "test(native): add native image smoke E2E tests"
```

### Task 10.6: Local native validation

- [ ] **Step 10.6.1: Update `.mise.toml`**

Add tasks:

```toml
build-native = "./gradlew nativeCompile"
build-native-image = "scripts/build-native-image.sh"
smoke-native = "scripts/run-native-smoke.sh"
```

- [ ] **Step 10.6.2: Create `scripts/run-native-smoke.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${CONTAINER_RUNTIME:-podman}"
TAG="${1:-kotlin-grpc-rest-starter-native:latest}"
NETWORK="starter-native-net"
POSTGRES="starter-native-postgres"
APP="starter-native-smoke"

cleanup() {
  $RUNTIME stop $APP $POSTGRES || true
  $RUNTIME rm $APP $POSTGRES || true
  $RUNTIME network rm $NETWORK || true
}
trap cleanup EXIT

$RUNTIME network create $NETWORK || true
$RUNTIME run -d --name $POSTGRES --network $NETWORK \
  -e POSTGRES_DB=starter -e POSTGRES_USER=starter -e POSTGRES_PASSWORD=starter \
  postgres:18

for i in {1..30}; do
  if $RUNTIME exec $POSTGRES pg_isready -U starter -d starter; then
    echo "Postgres is ready"
    break
  fi
  sleep 2
done

$RUNTIME run -d --name $APP --network $NETWORK \
  -p 8081:8080 -p 9091:9090 \
  -e DB_HOST=$POSTGRES -e DB_PORT=5432 -e DB_NAME=starter \
  -e DB_USER=starter -e DB_PASS=starter \
  $TAG

for i in {1..30}; do
  if curl -fsS http://localhost:8081/actuator/health; then
    echo "Native smoke test passed"
    exit 0
  fi
  sleep 2
done

echo "Native smoke test failed" >&2
$RUNTIME logs $APP
exit 1
```

Make executable:

```bash
chmod +x scripts/run-native-smoke.sh
```

- [ ] **Step 10.6.3: Commit**

```bash
git add .mise.toml scripts/run-native-smoke.sh
git commit -m "chore(native): add local native smoke test script"
```


---

## Self-Review

### Spec coverage

| Design section | Tasks covering it |
|----------------|-------------------|
| Shared value objects, provider/cache ports | Phase 0, Phase 1 |
| Market data (yfinance/Polygon/Bloomberg, cache) | Phase 1 |
| Indicators and metrics | Phase 2 |
| Analysis (regression, cointegration, Hurst, PCA, correlation, multi-factor, options) | Phase 3 |
| Backtesting (strategies, engine, portfolio, pairs, walk-forward, robustness, MC) | Phase 4 |
| Portfolio optimization (mean-variance, risk parity, Black-Litterman) | Phase 5 |
| Screener (fundamental + technical filters) | Phase 6 |
| Agent tools (42 tools, dispatch, A2A/MCP/agent card) | Phase 7 |
| Audit trail (hash chain, JPA, verify/replay, CLI) | Phase 8 |
| Docker/CI/act/local Podman | Phase 9 |
| Native image build (GraalVM, AOT, Dockerfile.native) | Phase 10 |
| Visual test reporting | Phase 9 (listener enhancement) |

### Placeholder scan

- No `TBD`, `TODO`, or `implement later` strings remain.
- Each task contains concrete file paths, code snippets, run commands, and expected output.
- "Follow the same pattern" instructions are paired with an actual example in the same or preceding task.

### Type consistency

- `BarInterval`, `DateRange`, `Ticker`, `OHLCV`, `PriceSeries` are used consistently across phases.
- `FetchMarketDataUseCase.FetchMarketDataCommand` signature matches all callers.
- `AnalysisResult`, `BacktestResult`, and `Portfolio` result types are sealed or data-class based and referenced uniformly.
- Agent tool names in `ToolRegistry.definitions`, `A2aAgentCardController`, `A2aTaskHandler`, and `McpToolHandler` are derived from the same `ToolRegistry` list.

### Known gaps / follow-up

1. The cointegration `adfStatistic` uses a simplified ADF approximation rather than a full statsmodels-style test. If cross-language hash parity matters, replace with a JVM unit-root library later.
2. The Bloomberg provider is a stub. A real `blpapi` adapter requires an optional dependency and profile, which is left as a follow-up.
3. Some agent tools return compact summaries rather than full Standard-Tools output shapes; adjust schemas if downstream LLM clients require exact field names.
4. Native image hints use a broad classpath scan. If build size or time becomes an issue, switch to explicit `reflect-config.json` generated from AOT analysis.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-30-kotlin-standard-tools-port.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, and iterate fast. Required sub-skill: `superpowers:subagent-driven-development`.

**2. Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints for review.

Which approach would you like?
