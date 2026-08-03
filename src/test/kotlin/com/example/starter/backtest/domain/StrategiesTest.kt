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
