package com.example.starter.analysis.adapter.`in`.web

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisController(
    private val runAnalysisUseCase: RunAnalysisUseCase
) {

    @GetMapping("/regression")
    fun regression(
        @RequestParam asset: String,
        @RequestParam benchmark: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run(
        RunAnalysisUseCase.RegressionCommand(
            asset = Ticker(asset),
            benchmark = Ticker(benchmark),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    )

    @GetMapping("/cointegration")
    fun cointegration(
        @RequestParam symbolA: String,
        @RequestParam symbolB: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false, defaultValue = "30") zScoreWindow: Int,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run(
        RunAnalysisUseCase.CointegrationCommand(
            assetA = Ticker(symbolA),
            assetB = Ticker(symbolB),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            zScoreWindow = zScoreWindow,
            provider = provider
        )
    )

    @GetMapping("/hurst")
    fun hurst(
        @RequestParam symbol: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false, defaultValue = "dfa") method: String,
        @RequestParam(required = false) rollingWindow: Int?,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run(
        RunAnalysisUseCase.HurstCommand(
            ticker = Ticker(symbol),
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            method = method,
            rollingWindow = rollingWindow,
            provider = provider
        )
    )

    @GetMapping("/pca")
    fun pca(
        @RequestParam symbols: List<String>,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) nComponents: Int?,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run(
        RunAnalysisUseCase.PcaCommand(
            tickers = symbols.map { Ticker(it) },
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            nComponents = nComponents,
            provider = provider
        )
    )

    @GetMapping("/correlation")
    fun correlation(
        @RequestParam symbols: List<String>,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<AnalysisResult> = run(
        RunAnalysisUseCase.CorrelationCommand(
            tickers = symbols.map { Ticker(it) },
            range = DateRange(startDate, endDate),
            interval = BarInterval.valueOf(interval.uppercase()),
            provider = provider
        )
    )

    @PostMapping("/multi-factor")
    fun multiFactor(@RequestBody request: MultiFactorRequestDto): Mono<AnalysisResult> = Mono.fromCallable {
        runAnalysisUseCase.execute(
            RunAnalysisUseCase.MultiFactorCommand(
                asset = Ticker(request.asset),
                factors = request.factors.mapValues { Ticker(it.value) },
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/option")
    fun option(@RequestBody request: OptionRequestDto): Mono<AnalysisResult> = Mono.fromCallable {
        runAnalysisUseCase.execute(
            RunAnalysisUseCase.OptionPricingCommand(
                spot = request.spot,
                strike = request.strike,
                timeToExpiry = request.timeToExpiry,
                riskFreeRate = request.riskFreeRate,
                volatility = request.volatility,
                optionType = request.optionType,
                dividendYield = request.dividendYield,
                marketPrice = request.marketPrice
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())

    private fun run(command: RunAnalysisUseCase.AnalysisCommand): Mono<AnalysisResult> =
        Mono.fromCallable { runAnalysisUseCase.execute(command) }.subscribeOn(Schedulers.boundedElastic())
}

data class MultiFactorRequestDto(
    val asset: String,
    val factors: Map<String, String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null
)

data class OptionRequestDto(
    val spot: Double,
    val strike: Double,
    val timeToExpiry: Double,
    val riskFreeRate: Double,
    val volatility: Double,
    val optionType: String = "call",
    val dividendYield: Double = 0.0,
    val marketPrice: Double? = null
)
