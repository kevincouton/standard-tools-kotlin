package com.example.starter.shared.domain

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("unit")
class QuantErrorTest {

    @Test
    fun `ProviderNotAvailableException carries expected message`() {
        val error = ProviderNotAvailableException(provider = "yahoo")

        expectThat(error.message).isEqualTo("Market data provider not available: yahoo")
    }

    @Test
    fun `DataQualityException carries expected message`() {
        val error = DataQualityException(message = "missing close price")

        expectThat(error.message).isEqualTo("Data quality issue: missing close price")
    }

    @Test
    fun `InvalidCommandException carries expected message`() {
        val error = InvalidCommandException(message = "unknown indicator")

        expectThat(error.message).isEqualTo("Invalid command: unknown indicator")
    }
}
