package com.example.starter.indicators.domain

import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@Tag("unit")
class IndicatorCalculatorTest {

    private val calculator = IndicatorCalculator()
    private val series = OhlcvFixtures.dailySeries(days = 30)

    @Test
    fun `calculates sma`() {
        val result = calculator.calculate("sma", series, mapOf("period" to 20))
        expectThat(result.values).hasSize(series.size)
        expectThat(result.values[19].value).isNotNull()
    }

    @Test
    fun `calculates rsi`() {
        val result = calculator.calculate("rsi", series, mapOf("period" to 14))
        expectThat(result.values).hasSize(series.size)
    }

    @Test
    fun `rejects unknown indicator`() {
        val exception = assertThrows<InvalidCommandException> {
            calculator.calculate("unknown", series, emptyMap())
        }
        expectThat(exception.message).isEqualTo("Invalid command: Unknown indicator: unknown")
    }
}
