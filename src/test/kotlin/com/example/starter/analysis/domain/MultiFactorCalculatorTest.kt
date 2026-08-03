package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotEmpty

@Tag("unit")
class MultiFactorCalculatorTest {
    private val calculator = MultiFactorCalculator()

    @Test
    fun `multi factor produces loadings`() {
        val asset = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val factor = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val result = calculator.calculate(asset, mapOf("MKT" to factor))
        expectThat(result.loadings).isNotEmpty()
    }
}
