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
        expectThat(result.exponent).isGreaterThan(0.2)
    }
}
