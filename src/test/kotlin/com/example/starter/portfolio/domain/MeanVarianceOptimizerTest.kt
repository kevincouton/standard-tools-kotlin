package com.example.starter.portfolio.domain

import com.example.starter.shared.domain.InvalidCommandException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("unit")
class MeanVarianceOptimizerTest {

    private val optimizer = MeanVarianceOptimizer()
    private val tickers = listOf("A", "B")
    private val returns = listOf(
        listOf(0.01, -0.01, 0.02, -0.005),
        listOf(-0.005, 0.015, -0.01, 0.01)
    )

    @Test
    fun `target_return objective without targetReturn throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            optimizer.optimize(returns = returns, tickers = tickers, objective = "target_return")
        }

        expectThat(error.message).isEqualTo("Invalid command: target_return objective requires targetReturn")
    }

    @Test
    fun `target_volatility objective without targetVolatility throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            optimizer.optimize(returns = returns, tickers = tickers, objective = "target_volatility")
        }

        expectThat(error.message).isEqualTo("Invalid command: target_volatility objective requires targetVolatility")
    }
}
