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
