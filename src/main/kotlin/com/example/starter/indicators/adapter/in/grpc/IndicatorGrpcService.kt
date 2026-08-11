package com.example.starter.indicators.adapter.`in`.grpc

import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.grpc.CalculateIndicatorRequest
import com.example.starter.indicators.grpc.IndicatorResponse
import com.example.starter.indicators.grpc.IndicatorServiceGrpcKt
import com.example.starter.indicators.grpc.IndicatorValue
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import com.example.starter.shared.domain.toBarInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class IndicatorGrpcService(
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase
) : IndicatorServiceGrpcKt.IndicatorServiceCoroutineImplBase() {

    override suspend fun calculateIndicator(request: CalculateIndicatorRequest): IndicatorResponse = withContext(Dispatchers.IO) {
        validateIndicatorInput(request.symbol, request.interval)
        val result = calculateIndicatorUseCase.calculate(
            CalculateIndicatorUseCase.CalculateIndicatorCommand(
                ticker = Ticker(request.symbol, request.exchange.takeIf { it.isNotBlank() }),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = parseInterval(request.interval),
                indicator = request.indicator,
                parameters = request.parametersMap,
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        IndicatorResponse.newBuilder()
            .setIndicator(result.indicator)
            .addAllValues(
                result.values.map { v ->
                    IndicatorValue.newBuilder()
                        .setDate(v.date.toString())
                        .setValue(v.value?.toPlainString() ?: "")
                        .build()
                }
            )
            .build()
    }

    private fun validateIndicatorInput(symbol: String, interval: String) {
        if (symbol.isBlank()) {
            throw InvalidCommandException("symbol must not be blank")
        }
        parseInterval(interval)
    }

    private fun parseInterval(interval: String): BarInterval = interval.toBarInterval()
}
