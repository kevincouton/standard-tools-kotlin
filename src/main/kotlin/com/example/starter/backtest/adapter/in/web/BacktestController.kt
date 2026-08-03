package com.example.starter.backtest.adapter.`in`.web

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/backtest")
class BacktestController(
    private val runBacktestUseCase: RunBacktestUseCase
) {

    @PostMapping("/single")
    fun single(@RequestBody request: SingleAssetBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker(request.symbol),
                strategy = request.strategy,
                parameters = request.parameters,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                slippagePct = request.slippagePct
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/portfolio")
    fun portfolio(@RequestBody request: PortfolioBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = request.symbols.map { Ticker(it) },
                weights = request.weights,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital,
                commissionPct = request.commissionPct,
                maxGrossLeverage = request.maxGrossLeverage
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/pair")
    fun pair(@RequestBody request: PairBacktestRequestDto): Mono<BacktestResult> = Mono.fromCallable {
        runBacktestUseCase.execute(
            RunBacktestUseCase.PairTradeCommand(
                symbolA = request.symbolA,
                symbolB = request.symbolB,
                entryZ = request.entryZ,
                exitZ = request.exitZ,
                zScoreWindow = request.zScoreWindow,
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider,
                initialCapital = request.initialCapital
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())
}

data class SingleAssetBacktestRequestDto(
    val symbol: String,
    val strategy: String,
    val parameters: Map<String, Any> = emptyMap(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0,
    val commissionPct: Double = 0.001,
    val slippagePct: Double = 0.0005
)

data class PortfolioBacktestRequestDto(
    val symbols: List<String>,
    val weights: Map<String, Double>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0,
    val commissionPct: Double = 0.001,
    val maxGrossLeverage: Double = 1.0
)

data class PairBacktestRequestDto(
    val symbolA: String,
    val symbolB: String,
    val entryZ: Double = 2.0,
    val exitZ: Double = 0.5,
    val zScoreWindow: Int = 30,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val initialCapital: Double = 10_000.0
)
