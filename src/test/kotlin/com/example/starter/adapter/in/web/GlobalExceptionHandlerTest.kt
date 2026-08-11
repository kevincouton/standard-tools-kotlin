package com.example.starter.adapter.`in`.web

import com.example.starter.shared.domain.InvalidCommandException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("unit")
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `InvalidCommandException maps to HTTP 400`() {
        val problemDetail = handler.handleInvalidCommand(InvalidCommandException("bad request"))

        expectThat(problemDetail.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        expectThat(problemDetail.title).isEqualTo("Invalid Command")
    }
}
