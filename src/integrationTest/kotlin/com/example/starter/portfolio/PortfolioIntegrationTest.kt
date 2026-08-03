package com.example.starter.portfolio

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.portfolio.application.service.PortfolioService
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotEmpty
import java.time.LocalDate

@Tag("integration")
class PortfolioIntegrationTest {

    private val fetch = mockk<FetchMarketDataUseCase>()
    private val service = PortfolioService(fetchMarketDataUseCase = fetch)

    @Test
    fun `mean variance optimization produces valid portfolio`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)

        val result = service.optimize(
            com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase.OptimizeCommand(
                tickers = listOf(Ticker("AAPL"), Ticker("MSFT")),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY,
                objective = "max_sharpe"
            )
        )

        expectThat(result.weights).isNotEmpty()
        expectThat(result.weights.values.sum()).isGreaterThanOrEqualTo(0.99)
        expectThat(result.volatility).isGreaterThanOrEqualTo(0.0)
    }

    @Test
    fun `risk parity optimization produces valid portfolio`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)

        val result = service.riskParity(
            com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase.RiskParityCommand(
                tickers = listOf(Ticker("AAPL"), Ticker("MSFT")),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY
            )
        )

        expectThat(result.weights).isNotEmpty()
        expectThat(result.weights.values.sum()).isGreaterThanOrEqualTo(0.99)
        expectThat(result.volatility).isGreaterThanOrEqualTo(0.0)
    }
}
