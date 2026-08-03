package com.example.starter.screener

import com.example.starter.indicators.domain.IndicatorCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.screener.adapter.out.reference.HardcodedFundamentalAdapter
import com.example.starter.screener.application.service.ScreenerService
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.LocalDate

@Tag("integration")
class ScreenerIntegrationTest {

    private val fetch = mockk<FetchMarketDataUseCase>()
    private val service = ScreenerService(
        fetchMarketDataUseCase = fetch,
        fundamentalProvider = HardcodedFundamentalAdapter(),
        indicatorCalculator = IndicatorCalculator()
    )

    @Test
    fun `screen filters by peRatioMax`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 30)

        val result = service.screen(
            com.example.starter.screener.application.port.inbound.ScreenStocksUseCase.ScreenCommand(
                tickers = listOf("AAPL", "MSFT", "TSLA"),
                criteria = com.example.starter.screener.domain.ScreenCriteria(peRatioMax = 35.0),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 30)),
                interval = BarInterval.DAILY
            )
        )

        expectThat(result.matches.map { it.ticker }).contains("AAPL", "MSFT").doesNotContain("TSLA")
        expectThat(result.failedTickers).isEmpty()
    }

    @Test
    fun `screen filters by roeMin`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 30)

        val result = service.screen(
            com.example.starter.screener.application.port.inbound.ScreenStocksUseCase.ScreenCommand(
                tickers = listOf("AAPL", "MSFT", "TSLA"),
                criteria = com.example.starter.screener.domain.ScreenCriteria(roeMin = 0.20),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 30)),
                interval = BarInterval.DAILY
            )
        )

        expectThat(result.matches.map { it.ticker }).contains("AAPL", "MSFT").doesNotContain("TSLA")
    }

    @Test
    fun `screen sorts by pe ascending`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 30)

        val result = service.screen(
            com.example.starter.screener.application.port.inbound.ScreenStocksUseCase.ScreenCommand(
                tickers = listOf("AAPL", "MSFT", "TSLA"),
                criteria = com.example.starter.screener.domain.ScreenCriteria(),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 30)),
                interval = BarInterval.DAILY,
                sortBy = "pe"
            )
        )

        expectThat(result.matches.first().ticker).isEqualTo("AAPL")
    }
}
