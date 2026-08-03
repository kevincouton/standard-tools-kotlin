package com.example.starter.portfolio.adapter.`in`.web

import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.Portfolio
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
@RequestMapping("/api/v1/portfolio")
class PortfolioController(
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase
) {

    @PostMapping("/optimize")
    fun optimize(@RequestBody request: OptimizeRequestDto): Mono<Portfolio> = Mono.fromCallable {
        optimizePortfolioUseCase.optimize(
            OptimizePortfolioUseCase.OptimizeCommand(
                tickers = request.symbols.map { Ticker(it) },
                range = DateRange(request.startDate, request.endDate),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                objective = request.objective,
                riskFreeRate = request.riskFreeRate,
                targetReturn = request.targetReturn,
                targetVolatility = request.targetVolatility,
                allowShort = request.allowShort,
                maxWeight = request.maxWeight,
                provider = request.provider
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())
}

data class OptimizeRequestDto(
    val symbols: List<String>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val interval: String,
    val provider: String? = null,
    val objective: String = "max_sharpe",
    val riskFreeRate: Double = 0.02,
    val targetReturn: Double? = null,
    val targetVolatility: Double? = null,
    val allowShort: Boolean = false,
    val maxWeight: Double? = null
)
