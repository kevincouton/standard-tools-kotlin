package com.example.starter.metrics.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

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
}
