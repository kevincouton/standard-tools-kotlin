package com.example.starter.indicators.adapter.`in`.web

import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import com.example.starter.shared.domain.toBarInterval
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
@RequestMapping("/api/v1/indicators")
class IndicatorController(
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase
) {

    @GetMapping(value = ["/calculate"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun calculate(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam indicator: String,
        @RequestParam(required = false) parameters: Map<String, String>?,
        @RequestParam(required = false) provider: String?
    ): Mono<IndicatorResult> {
        validateIndicatorInput(symbol, interval)
        return Mono.fromCallable {
            calculateIndicatorUseCase.calculate(
                CalculateIndicatorUseCase.CalculateIndicatorCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = parseInterval(interval),
                    indicator = indicator,
                    parameters = parameters ?: emptyMap(),
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun validateIndicatorInput(symbol: String, interval: String) {
        if (symbol.isBlank()) {
            throw InvalidCommandException("symbol must not be blank")
        }
        parseInterval(interval)
    }

    private fun parseInterval(interval: String): BarInterval = interval.toBarInterval()
}
