package com.example.starter.portfolio.adapter.`in`.web

import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import com.example.starter.shared.domain.toBarInterval
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
        validateOptimizeRequest(request)
        optimizePortfolioUseCase.optimize(
            OptimizePortfolioUseCase.OptimizeCommand(
                tickers = request.symbols.map { Ticker(it) },
                range = DateRange(request.startDate, request.endDate),
                interval = request.interval.toBarInterval(),
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

    private fun validateOptimizeRequest(request: OptimizeRequestDto) {
        val supportedObjectives = setOf("max_sharpe", "min_volatility", "target_return", "target_volatility")
        if (request.objective !in supportedObjectives) {
            throw InvalidCommandException("unsupported objective '${request.objective}'; must be one of ${supportedObjectives.joinToString()}")
        }
        if (request.objective == "target_return" && request.targetReturn == null) {
            throw InvalidCommandException("target_return objective requires targetReturn")
        }
        if (request.objective == "target_volatility" && request.targetVolatility == null) {
            throw InvalidCommandException("target_volatility objective requires targetVolatility")
        }
    }
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
