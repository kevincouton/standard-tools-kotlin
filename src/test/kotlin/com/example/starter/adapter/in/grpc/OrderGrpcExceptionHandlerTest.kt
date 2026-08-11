package com.example.starter.adapter.`in`.grpc

import com.example.starter.shared.domain.InvalidCommandException
import io.grpc.Status
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

@Tag("unit")
class OrderGrpcExceptionHandlerTest {

    private val handler = OrderGrpcExceptionHandler()

    @Test
    fun `InvalidCommandException maps to gRPC INVALID_ARGUMENT`() {
        val statusException = handler.handleException(InvalidCommandException("bad argument"))

        expectThat(statusException).isNotNull()
        expectThat(statusException!!.status.code).isEqualTo(Status.INVALID_ARGUMENT.code)
    }
}
