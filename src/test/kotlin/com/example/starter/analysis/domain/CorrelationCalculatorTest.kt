package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotEmpty

@Tag("unit")
class CorrelationCalculatorTest {
    private val calculator = CorrelationCalculator()

    @Test
    fun `correlation matrix is non empty`() {
        val a = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val b = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val result = calculator.calculate(listOf("A", "B"), listOf(a, b))
        expectThat(result.matrix).isNotEmpty()
    }
}
