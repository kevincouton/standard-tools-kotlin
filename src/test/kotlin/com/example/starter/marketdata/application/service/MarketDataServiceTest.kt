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
