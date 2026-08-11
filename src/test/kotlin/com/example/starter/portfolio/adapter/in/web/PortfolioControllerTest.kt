package com.example.starter.portfolio.adapter.`in`.web

import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.shared.domain.InvalidCommandException
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.time.LocalDate

@Tag("unit")
class PortfolioControllerTest {

    private val useCase = mockk<OptimizePortfolioUseCase>()
    private val controller = PortfolioController(useCase)

    private val baseRequest = OptimizeRequestDto(
        symbols = listOf("A", "B"),
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        interval = "1d"
    )

    @Test
    fun `invalid objective throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            controller.optimize(baseRequest.copy(objective = "max_return")).block()
        }

        expectThat(error.message)
            .isEqualTo("Invalid command: unsupported objective 'max_return'; must be one of max_sharpe, min_volatility, target_return, target_volatility")
    }

    @Test
    fun `target_return objective without targetReturn throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            controller.optimize(baseRequest.copy(objective = "target_return")).block()
        }

        expectThat(error.message).isEqualTo("Invalid command: target_return objective requires targetReturn")
    }

    @Test
    fun `target_volatility objective without targetVolatility throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            controller.optimize(baseRequest.copy(objective = "target_volatility")).block()
        }

        expectThat(error.message).isEqualTo("Invalid command: target_volatility objective requires targetVolatility")
    }

    @Test
    fun `invalid interval throws InvalidCommandException`() {
        val error = assertThrows<InvalidCommandException> {
            controller.optimize(baseRequest.copy(interval = "1d")).block()
        }

        expectThat(error.message).contains("interval must be one of")
    }
}
