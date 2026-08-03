package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty

@Tag("unit")
class PcaCalculatorTest {
    private val calculator = PcaCalculator()

    @Test
    fun `pca returns explained variance`() {
        val a = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val b = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val result = calculator.calculate(listOf("A", "B"), listOf(a, b), nComponents = 2)
        expectThat(result.explainedVarianceRatio.sum()).isGreaterThan(0.99)
        expectThat(result.loadings).isNotEmpty()
    }
}
