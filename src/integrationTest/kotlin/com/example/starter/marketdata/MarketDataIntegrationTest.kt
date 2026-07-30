package com.example.starter.marketdata

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.marketdata.application.service.MarketDataProperties
import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.PostgresTestContainer
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Duration
import java.time.LocalDate

@Tag("integration")
@SpringBootTest
@Import(MarketDataIntegrationTest.StubProviderConfig::class)
@Testcontainers
@ActiveProfiles("test")
class MarketDataIntegrationTest {

    @Autowired
    lateinit var fetchMarketDataUseCase: FetchMarketDataUseCase

    @Autowired
    lateinit var cache: MarketDataCache

    @Autowired
    lateinit var stubProvider: StubMarketDataProvider

    @Autowired
    lateinit var properties: MarketDataProperties

    @TestConfiguration
    class StubProviderConfig {

        @Bean
        fun stubMarketDataProvider(): MarketDataProvider = StubMarketDataProvider()
    }

    companion object {
        @Container
        val postgres = PostgresTestContainer.instance

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `cache miss fetches from provider and stores result`() {
        stubProvider.reset()
        val series = OhlcvFixtures.dailySeries(days = 3)
        stubProvider.returns(series)
        val command = command(symbol = "AAPL", startDay = 1)

        val result = fetchMarketDataUseCase.fetch(command)

        expectThat(result).isEqualTo(series)
        expectThat(stubProvider.callCount).isEqualTo(1)
        expectThat(cache.get(command.toCacheKey())).isEqualTo(series)
    }

    @Test
    fun `cache hit returns cached series without calling provider`() {
        val series = OhlcvFixtures.dailySeries(days = 3)
        val command = command(symbol = "MSFT", startDay = 10)
        cache.put(command.toCacheKey(), series, Duration.ofMinutes(5))
        stubProvider.reset()

        val result = fetchMarketDataUseCase.fetch(command)

        expectThat(result).isEqualTo(series)
        expectThat(stubProvider.callCount).isEqualTo(0)
    }

    @Test
    fun `provider selection falls back to default when not specified`() {
        expectThat(properties.defaultProvider).isEqualTo("stub")
    }

    @Test
    fun `disabled provider is not available`() {
        org.junit.jupiter.api.assertThrows<ProviderNotAvailableException> {
            fetchMarketDataUseCase.fetch(command(symbol = "TSLA", startDay = 20).copy(provider = "yfinance"))
        }
    }

    private fun command(symbol: String, startDay: Int) = FetchMarketDataUseCase.FetchMarketDataCommand(
        ticker = Ticker(symbol),
        range = DateRange(LocalDate.of(2024, 1, startDay), LocalDate.of(2024, 1, startDay + 2)),
        interval = BarInterval.DAILY
    )

    private fun FetchMarketDataUseCase.FetchMarketDataCommand.toCacheKey() =
        com.example.starter.shared.domain.CacheKey(
            provider = provider ?: properties.defaultProvider,
            ticker = ticker,
            interval = interval,
            range = range
        )

    class StubMarketDataProvider : MarketDataProvider {
        override val name: String = "stub"
        var callCount: Int = 0
            private set
        private var nextResponse: PriceSeries = emptyList()

        fun returns(series: PriceSeries) {
            nextResponse = series
        }

        fun reset() {
            callCount = 0
            nextResponse = emptyList()
        }

        override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
            callCount++
            return nextResponse
        }
    }
}
