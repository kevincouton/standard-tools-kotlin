package com.example.starter.backtest

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.application.service.BacktestService
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
import strikt.assertions.isNotEmpty
import java.time.LocalDate

@Tag("integration")
class BacktestIntegrationTest {

    private val fetch = mockk<FetchMarketDataUseCase>()
    private val service = BacktestService(fetchMarketDataUseCase = fetch)

    @Test
    fun `single-asset buy and hold produces non-empty equity curve`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)

        val result = service.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker("AAPL"),
                strategy = "buy_and_hold",
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY
            )
        )

        expectThat(result.equityCurve).isNotEmpty()
        expectThat(result.equityCurve.size).isGreaterThan(1)
    }

    @Test
    fun `portfolio simulation produces non-empty equity curve`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)

        val result = service.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = listOf(Ticker("AAPL"), Ticker("MSFT")),
                weights = mapOf("AAPL" to 0.6, "MSFT" to 0.4),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY
            )
        )

        expectThat(result.equityCurve).isNotEmpty()
    }
}
