package com.example.starter.metrics.adapter.`in`.grpc

import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.grpc.CalculateReturnRequest
import com.example.starter.metrics.grpc.CalculateRiskRequest
import com.example.starter.metrics.grpc.MetricsServiceGrpcKt
import com.example.starter.metrics.grpc.ReturnMetricsResponse
import com.example.starter.metrics.grpc.RiskMetricsResponse
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
class MetricsGrpcService(
    private val calculateMetricsUseCase: CalculateMetricsUseCase
) : MetricsServiceGrpcKt.MetricsServiceCoroutineImplBase() {

    override suspend fun calculateRisk(request: CalculateRiskRequest): RiskMetricsResponse = withContext(Dispatchers.IO) {
        validateMetricsInput(request.symbol, request.interval)
        val result = calculateMetricsUseCase.calculateRisk(
            CalculateMetricsUseCase.CalculateRiskCommand(
                ticker = Ticker(request.symbol, request.exchange.takeIf { it.isNotBlank() }),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = parseInterval(request.interval),
                riskFreeRate = request.riskFreeRate,
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        RiskMetricsResponse.newBuilder()
            .setSharpeRatio(result.sharpeRatio?.toPlainString() ?: "")
            .setSortinoRatio(result.sortinoRatio?.toPlainString() ?: "")
            .setMaxDrawdown(result.maxDrawdown.toPlainString())
            .setCalmarRatio(result.calmarRatio?.toPlainString() ?: "")
            .setVar95(result.var95.toPlainString())
            .setCvar95(result.cvar95.toPlainString())
            .setVolatility(result.volatility.toPlainString())
            .build()
    }

    override suspend fun calculateReturn(request: CalculateReturnRequest): ReturnMetricsResponse = withContext(Dispatchers.IO) {
        validateMetricsInput(request.symbol, request.interval)
        val result = calculateMetricsUseCase.calculateReturn(
            CalculateMetricsUseCase.CalculateReturnCommand(
                ticker = Ticker(request.symbol, request.exchange.takeIf { it.isNotBlank() }),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = parseInterval(request.interval),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        ReturnMetricsResponse.newBuilder()
            .setCumulativeReturn(result.cumulativeReturn.toPlainString())
            .setCagr(result.cagr?.toPlainString() ?: "")
            .setAnnualizedVolatility(result.annualizedVolatility.toPlainString())
            .build()
    }

    private fun validateMetricsInput(symbol: String, interval: String) {
        if (symbol.isBlank()) {
            throw InvalidCommandException("symbol must not be blank")
        }
        parseInterval(interval)
    }

    private fun parseInterval(interval: String): BarInterval = interval.toBarInterval()
}
