package com.example.starter.metrics.domain

import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.pow

@Tag("unit")
class RiskReturnCalculatorTest {

    private val calculator = RiskReturnCalculator()
    private val series = OhlcvFixtures.dailySeries(days = 30)

    @Test
    fun `calculates return metrics`() {
        val result = calculator.returnMetrics(series)
        expectThat(result.annualizedVolatility).isGreaterThan(java.math.BigDecimal.ZERO)
    }

    @Test
    fun `calculates risk metrics`() {
        val result = calculator.riskMetrics(series)
        expectThat(result.maxDrawdown).isGreaterThan(java.math.BigDecimal.valueOf(-1))
        expectThat(result.volatility).isGreaterThan(java.math.BigDecimal.ZERO)
    }

    @Test
    fun `cumulative return is geometric product of simple returns`() {
        val ticker = Ticker("TEST")
        val explicitSeries = listOf(
            ohlcv(ticker, LocalDate.of(2024, 1, 1), 100.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 2), 110.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 3), 99.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 4), 108.0)
        )
        val result = calculator.returnMetrics(explicitSeries)
        // (108 - 100) / 100 = 0.08 exactly
        expectThat(result.cumulativeReturn).isEqualTo(BigDecimal("0.0800"))
    }

    @Test
    fun `sharpe ratio annualizes mean excess return`() {
        val ticker = Ticker("TEST")
        val explicitSeries = listOf(
            ohlcv(ticker, LocalDate.of(2024, 1, 1), 100.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 2), 110.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 3), 99.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 4), 108.0)
        )
        val result = calculator.riskMetrics(explicitSeries, riskFreeRate = 0.02)

        // Daily returns: 0.10, -0.10, 0.09090909...
        // meanExcess = mean(returns) - 0.02/252
        // annualizedSharpe = meanExcess * sqrt(252) / sampleStdDaily
        val returns = listOf(0.10, -0.10, 1.0 / 11.0)
        val mean = returns.average()
        val meanExcess = mean - 0.02 / 252
        val sampleVariance = returns.map { (it - mean) * (it - mean) }.sum() / (returns.size - 1)
        val stdDaily = kotlin.math.sqrt(sampleVariance)
        val expected = meanExcess * kotlin.math.sqrt(252.0) / stdDaily

        expectThat(result.sharpeRatio).isEqualTo(BigDecimal(expected).setScale(4, java.math.RoundingMode.HALF_UP))
    }

    @Test
    fun `cagr uses geometric annualization`() {
        val ticker = Ticker("TEST")
        val explicitSeries = listOf(
            ohlcv(ticker, LocalDate.of(2024, 1, 1), 100.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 2), 110.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 3), 99.0),
            ohlcv(ticker, LocalDate.of(2024, 1, 4), 108.0)
        )
        val result = calculator.returnMetrics(explicitSeries)

        // CAGR = (end/start)^(252/n) - 1
        val start = 100.0
        val end = 108.0
        val n = 3
        val expected = (end / start).pow(252.0 / n) - 1

        expectThat(result.cagr).isEqualTo(BigDecimal(expected).setScale(4, java.math.RoundingMode.HALF_UP))
    }

    private fun ohlcv(ticker: Ticker, date: LocalDate, close: Double): OHLCV {
        return OHLCV(
            ticker = ticker,
            date = date,
            open = BigDecimal(close.toString()),
            high = BigDecimal(close.toString()),
            low = BigDecimal(close.toString()),
            close = BigDecimal(close.toString()),
            volume = 1_000L
        )
    }
}
