package com.example.starter.analysis.domain

import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse

@Tag("unit")
class CointegrationCalculatorTest {

    private val calculator = CointegrationCalculator()

    @Test
    fun `computes hedge ratio and half life`() {
        val a = OhlcvFixtures.dailySeries(days = 60, basePrice = 100.0)
        val b = OhlcvFixtures.dailySeries(days = 60, basePrice = 50.0)
        val result = calculator.calculate(a, b)
        expectThat(result.hedgeRatio.isNaN()).isFalse()
        expectThat(result.halfLife.isNaN()).isFalse()
    }
}
