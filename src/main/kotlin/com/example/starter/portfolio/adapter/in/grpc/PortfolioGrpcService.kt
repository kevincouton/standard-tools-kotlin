package com.example.starter.portfolio.adapter.`in`.grpc

import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.grpc.BlackLittermanRequest
import com.example.starter.portfolio.grpc.OptimizeRequest
import com.example.starter.portfolio.grpc.PortfolioResponse
import com.example.starter.portfolio.grpc.PortfolioServiceGrpcKt
import com.example.starter.portfolio.grpc.RiskParityRequest
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class PortfolioGrpcService(
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase
) : PortfolioServiceGrpcKt.PortfolioServiceCoroutineImplBase() {

    override suspend fun optimize(request: OptimizeRequest): PortfolioResponse = withContext(Dispatchers.IO) {
        val result = optimizePortfolioUseCase.optimize(
            OptimizePortfolioUseCase.OptimizeCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                objective = request.objective.takeIf { it.isNotBlank() } ?: "max_sharpe",
                riskFreeRate = request.riskFreeRate.takeIf { it > 0 } ?: 0.02,
                targetReturn = request.targetReturn.takeIf { it > 0 },
                targetVolatility = request.targetVolatility.takeIf { it > 0 },
                allowShort = request.allowShort,
                maxWeight = request.maxWeight.takeIf { it > 0 }
            )
        )
        toResponse(result)
    }

    override suspend fun riskParity(request: RiskParityRequest): PortfolioResponse = withContext(Dispatchers.IO) {
        val result = optimizePortfolioUseCase.riskParity(
            OptimizePortfolioUseCase.RiskParityCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                riskBudget = request.riskBudgetMap
            )
        )
        toResponse(result)
    }

    override suspend fun blackLitterman(request: BlackLittermanRequest): PortfolioResponse = withContext(Dispatchers.IO) {
        val result = optimizePortfolioUseCase.blackLitterman(
            OptimizePortfolioUseCase.BlackLittermanCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                marketWeights = request.marketWeightsMap,
                views = OptimizePortfolioUseCase.BlackLittermanViewsInput(
                    assets = request.symbolsList,
                    views = request.viewsList.map {
                        OptimizePortfolioUseCase.BlackLittermanViewsInput.View(
                            asset = it.asset.takeIf { a -> a.isNotBlank() },
                            relativeAsset = it.relativeAsset.takeIf { a -> a.isNotBlank() },
                            returnView = it.returnView
                        )
                    }
                ),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                riskAversion = request.riskAversion.takeIf { it > 0 } ?: 2.5,
                tau = request.tau.takeIf { it > 0 } ?: 0.05
            )
        )
        toResponse(result)
    }

    private fun toResponse(result: com.example.starter.portfolio.domain.Portfolio): PortfolioResponse {
        return PortfolioResponse.newBuilder()
            .setObjective(result.objective)
            .putAllWeights(result.weights)
            .setExpectedReturn(result.expectedReturn)
            .setVolatility(result.volatility)
            .setSharpeRatio(result.sharpeRatio ?: 0.0)
            .build()
    }
}
