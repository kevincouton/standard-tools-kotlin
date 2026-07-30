package com.example.starter.adapter.`in`.grpc

import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import com.example.starter.shared.domain.QuantError
import io.grpc.Status
import io.grpc.StatusException
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

@Component
class OrderGrpcExceptionHandler : GrpcExceptionHandler {

    override fun handleException(t: Throwable): StatusException? {
        return when (t) {
            is OrderNotFoundException -> Status.NOT_FOUND.withDescription(t.message).withCause(t).asException()
            is InvalidOrderStateException -> Status.FAILED_PRECONDITION.withDescription(t.message).withCause(t).asException()
            is IllegalArgumentException -> Status.INVALID_ARGUMENT.withDescription(t.message).withCause(t).asException()
            is QuantError -> Status.INVALID_ARGUMENT.withDescription(t.message).withCause(t).asException()
            else -> null
        }
    }
}
