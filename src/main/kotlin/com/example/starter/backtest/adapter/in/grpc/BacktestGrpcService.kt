package com.example.starter.backtest.adapter.`in`.grpc

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.grpc.BacktestResponse
import com.example.starter.backtest.grpc.BacktestServiceGrpcKt
import com.example.starter.backtest.grpc.EquityCurvePoint
import com.example.starter.backtest.grpc.MonteCarloRequest
import com.example.starter.backtest.grpc.PairTradeRequest
import com.example.starter.backtest.grpc.PortfolioSimulationRequest
import com.example.starter.backtest.grpc.SingleAssetBacktestRequest
import com.example.starter.backtest.grpc.Trade
import com.example.starter.backtest.grpc.WalkForwardRequest
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.shared.domain.toBarInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class BacktestGrpcService(
    private val runBacktestUseCase: RunBacktestUseCase
) : BacktestServiceGrpcKt.BacktestServiceCoroutineImplBase() {

    override suspend fun runSingleAsset(request: SingleAssetBacktestRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parametersMap.mapValues { it.value.toDoubleOrString() },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital.takeIf { it > 0 } ?: 10_000.0,
                commissionPct = request.commissionPct.takeIf { it > 0 } ?: 0.001,
                slippagePct = request.slippagePct.takeIf { it > 0 } ?: 0.0005
            )
        )
        toResponse(result)
    }

    override suspend fun runPortfolioSimulation(request: PortfolioSimulationRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                weights = request.weightsMap,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital.takeIf { it > 0 } ?: 10_000.0,
                commissionPct = request.commissionPct.takeIf { it > 0 } ?: 0.001,
                maxGrossLeverage = request.maxGrossLeverage.takeIf { it > 0 } ?: 1.0
            )
        )
        toResponse(result)
    }

    override suspend fun runPairTrade(request: PairTradeRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PairTradeCommand(
                symbolA = request.symbolA,
                symbolB = request.symbolB,
                entryZ = request.entryZ.takeIf { it != 0.0 } ?: 2.0,
                exitZ = request.exitZ.takeIf { it != 0.0 } ?: 0.5,
                zScoreWindow = request.zScoreWindow.takeIf { it > 0 } ?: 30,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() },
                initialCapital = request.initialCapital.takeIf { it > 0 } ?: 10_000.0
            )
        )
        toResponse(result)
    }

    override suspend fun runWalkForward(request: WalkForwardRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val grid = request.parameterGridMap.mapValues { (_, list) -> list.valuesList }
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.WalkForwardCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameterGrid = grid,
                trainSize = request.trainSize.takeIf { it > 0 } ?: 252,
                testSize = request.testSize.takeIf { it > 0 } ?: 63,
                metric = request.metric.takeIf { it.isNotBlank() } ?: "sharpe_ratio",
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        toResponse(result)
    }

    override suspend fun runMonteCarlo(request: MonteCarloRequest): BacktestResponse = withContext(Dispatchers.IO) {
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.MonteCarloCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parametersMap.mapValues { it.value.toDoubleOrString() },
                horizonDays = request.horizonDays.takeIf { it > 0 } ?: 252,
                nSimulations = request.nSimulations.takeIf { it > 0 } ?: 1_000,
                blockSize = request.blockSize.takeIf { it > 0 } ?: 20,
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        toResponse(result)
    }

    private fun toResponse(result: com.example.starter.backtest.domain.BacktestResult): BacktestResponse {
        return BacktestResponse.newBuilder()
            .setStrategyName(result.strategyName)
            .setInitialCapital(result.initialCapital)
            .setFinalEquity(result.finalEquity)
            .setTotalReturn(result.totalReturn)
            .addAllTrades(result.trades.map {
                Trade.newBuilder()
                    .setEntryDate(it.entryDate.toString())
                    .setExitDate(it.exitDate?.toString() ?: "")
                    .setDirection(it.direction)
                    .setEntryPrice(it.entryPrice)
                    .setExitPrice(it.exitPrice ?: 0.0)
                    .setSize(it.size)
                    .setPnl(it.pnl)
                    .build()
            })
            .addAllEquityCurve(result.equityCurve.map {
                EquityCurvePoint.newBuilder()
                    .setDate(it.date.toString())
                    .setEquity(it.equity)
                    .setDrawdown(it.drawdown)
                    .build()
            })
            .putAllMetrics(mapOf(
                "sharpe_ratio" to (result.metrics?.sharpeRatio?.toDouble() ?: 0.0),
                "max_drawdown" to (result.drawdownEpisodes.maxOfOrNull { it.depth } ?: 0.0)
            ))
            .putAllMetadata(result.parameterGrid?.mapValues { it.value.toString() } ?: emptyMap())
            .build()
    }

    private fun String.toDoubleOrString(): Any = this.toDoubleOrNull() ?: this
}
