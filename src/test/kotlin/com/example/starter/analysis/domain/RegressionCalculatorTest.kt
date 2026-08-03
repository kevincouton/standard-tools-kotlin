package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan

@Tag("unit")
class RegressionCalculatorTest {

    private val calculator = RegressionCalculator()

    @Test
    fun `beta near one for identical series`() {
        val series = OhlcvFixtures.dailySeries(days = 30)
        val result = calculator.calculate(series, series)
        expectThat(result.beta).isGreaterThan(0.95)
    }
}
