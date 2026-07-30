package com.example.starter.adapter.`in`.web

import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import com.example.starter.shared.domain.QuantError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleNotFound(ex: OrderNotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found").apply {
            title = "Order Not Found"
        }
    }

    @ExceptionHandler(InvalidOrderStateException::class)
    fun handleInvalidState(ex: InvalidOrderStateException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Invalid state").apply {
            title = "Invalid Order State"
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request").apply {
            title = "Bad Request"
        }
    }

    @ExceptionHandler(QuantError::class)
    fun handleQuantError(ex: QuantError): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Quant error").apply {
            title = "Quant Error"
        }
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(ex: WebExchangeBindException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.reason ?: "Validation failed").apply {
            title = "Validation Failed"
        }
    }
}
