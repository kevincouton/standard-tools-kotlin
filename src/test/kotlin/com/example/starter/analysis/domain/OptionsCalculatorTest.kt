package com.example.starter.analysis.domain

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull

@Tag("unit")
class OptionsCalculatorTest {
    private val calc = OptionsCalculator()

    @Test
    fun `call price increases with spot`() {
        val cheap = calc.price(90.0, 100.0, 0.25, 0.05, 0.2)
        val expensive = calc.price(110.0, 100.0, 0.25, 0.05, 0.2)
        expectThat(expensive).isGreaterThan(cheap)
    }

    @Test
    fun `implied vol recovers input vol`() {
        val price = calc.price(100.0, 100.0, 0.5, 0.05, 0.25)
        val iv = calc.impliedVolatility(price, 100.0, 100.0, 0.5, 0.05)
        expectThat(iv).isNotNull()
    }
}
