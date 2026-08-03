package com.example.starter.analysis.adapter.`in`.grpc

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.grpc.AnalysisServiceGrpcKt
import com.example.starter.analysis.grpc.CointegrationRequest
import com.example.starter.analysis.grpc.CointegrationResponse
import com.example.starter.analysis.grpc.CorrelationRequest
import com.example.starter.analysis.grpc.CorrelationResponse
import com.example.starter.analysis.grpc.DoubleList
import com.example.starter.analysis.grpc.DoubleMap
import com.example.starter.analysis.grpc.HurstRequest
import com.example.starter.analysis.grpc.HurstResponse
import com.example.starter.analysis.grpc.MultiFactorRequest
import com.example.starter.analysis.grpc.MultiFactorResponse
import com.example.starter.analysis.grpc.OptionPricingRequest
import com.example.starter.analysis.grpc.OptionPricingResponse
import com.example.starter.analysis.grpc.PcaRequest
import com.example.starter.analysis.grpc.PcaResponse
import com.example.starter.analysis.grpc.RegressionRequest
import com.example.starter.analysis.grpc.RegressionResponse
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class AnalysisGrpcService(
    private val runAnalysisUseCase: RunAnalysisUseCase
) : AnalysisServiceGrpcKt.AnalysisServiceCoroutineImplBase() {

    override suspend fun runRegression(request: RegressionRequest): RegressionResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.RegressionCommand(
                asset = Ticker(request.assetSymbol),
                benchmark = Ticker(request.benchmarkSymbol),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.RegressionResult
        RegressionResponse.newBuilder()
            .setAlpha(result.alpha)
            .setBeta(result.beta)
            .setRSquared(result.rSquared)
            .setAnnualizedAlpha(result.annualizedAlpha ?: 0.0)
            .build()
    }

    override suspend fun runCointegration(request: CointegrationRequest): CointegrationResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CointegrationCommand(
                assetA = Ticker(request.symbolA),
                assetB = Ticker(request.symbolB),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                zScoreWindow = request.zScoreWindow
            )
        ) as com.example.starter.analysis.domain.CointegrationResult
        CointegrationResponse.newBuilder()
            .setHedgeRatio(result.hedgeRatio)
            .setAdfStatistic(result.adfStatistic)
            .setPValueApprox(result.pValueApprox)
            .setHalfLife(result.halfLife)
            .setCurrentZScore(result.currentZScore ?: 0.0)
            .build()
    }

    override suspend fun runHurst(request: HurstRequest): HurstResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.HurstCommand(
                ticker = Ticker(request.symbol),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                method = request.method,
                rollingWindow = request.rollingWindow.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.HurstResult
        HurstResponse.newBuilder()
            .setExponent(result.exponent)
            .setRegime(result.regime)
            .build()
    }

    override suspend fun runPca(request: PcaRequest): PcaResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.PcaCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() },
                nComponents = request.nComponents.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.PcaResult
        PcaResponse.newBuilder()
            .addAllExplainedVarianceRatio(result.explainedVarianceRatio)
            .putAllLoadings(result.loadings.mapValues { DoubleList.newBuilder().addAllValues(it.value).build() })
            .build()
    }

    override suspend fun runCorrelation(request: CorrelationRequest): CorrelationResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CorrelationCommand(
                tickers = request.symbolsList.map { Ticker(it) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.CorrelationResult
        CorrelationResponse.newBuilder()
            .putAllMatrix(result.matrix.mapValues { DoubleMap.newBuilder().putAllValues(it.value).build() })
            .setAverage(result.average)
            .setMin(result.min)
            .setMax(result.max)
            .setDiversificationRatio(result.diversificationRatio ?: 0.0)
            .build()
    }

    override suspend fun runMultiFactor(request: MultiFactorRequest): MultiFactorResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.MultiFactorCommand(
                asset = Ticker(request.assetSymbol),
                factors = request.factorSymbolsMap.mapValues { Ticker(it.value) },
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        ) as com.example.starter.analysis.domain.MultiFactorResult
        MultiFactorResponse.newBuilder()
            .setAlpha(result.alpha)
            .putAllLoadings(result.loadings)
            .putAllTStatistics(result.tStatistics)
            .putAllPValues(result.pValues)
            .setRSquared(result.rSquared)
            .setAdjRSquared(result.adjRSquared)
            .build()
    }

    override suspend fun priceOption(request: OptionPricingRequest): OptionPricingResponse = withContext(Dispatchers.IO) {
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.OptionPricingCommand(
                spot = request.spot,
                strike = request.strike,
                timeToExpiry = request.timeToExpiry,
                riskFreeRate = request.riskFreeRate,
                volatility = request.volatility,
                optionType = request.optionType,
                dividendYield = request.dividendYield,
                marketPrice = request.marketPrice.takeIf { it > 0 }
            )
        ) as com.example.starter.analysis.domain.OptionPricingResult
        OptionPricingResponse.newBuilder()
            .setPrice(result.price)
            .setDelta(result.greeks.delta)
            .setGamma(result.greeks.gamma)
            .setVega(result.greeks.vega)
            .setTheta(result.greeks.theta)
            .setRho(result.greeks.rho)
            .setImpliedVolatility(result.impliedVolatility ?: 0.0)
            .build()
    }
}
