package com.example.starter.metrics.adapter.`in`.web

import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.domain.ReturnMetrics
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/metrics")
class MetricsController(
    private val calculateMetricsUseCase: CalculateMetricsUseCase
) {

    @GetMapping(value = ["/risk"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun calculateRisk(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false, defaultValue = "0.02") riskFreeRate: Double,
        @RequestParam(required = false) provider: String?
    ): Mono<RiskMetrics> {
        validateMetricsInput(symbol, interval)
        return Mono.fromCallable {
            calculateMetricsUseCase.calculateRisk(
                CalculateMetricsUseCase.CalculateRiskCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = parseInterval(interval),
                    riskFreeRate = riskFreeRate,
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    @GetMapping(value = ["/return"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun calculateReturn(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<ReturnMetrics> {
        validateMetricsInput(symbol, interval)
        return Mono.fromCallable {
            calculateMetricsUseCase.calculateReturn(
                CalculateMetricsUseCase.CalculateReturnCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = parseInterval(interval),
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun validateMetricsInput(symbol: String, interval: String) {
        if (symbol.isBlank()) {
            throw InvalidCommandException("symbol must not be blank")
        }
        parseInterval(interval)
    }

    private fun parseInterval(interval: String): BarInterval {
        return BarInterval.entries.find { it.name.equals(interval.trim(), ignoreCase = true) }
            ?: throw InvalidCommandException(
                "interval must be one of ${BarInterval.entries.joinToString { it.name }}"
            )
    }
}
