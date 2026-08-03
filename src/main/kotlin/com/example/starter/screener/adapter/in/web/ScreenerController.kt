package com.example.starter.screener.adapter.`in`.web

import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/screener")
class ScreenerController(
    private val screenStocksUseCase: ScreenStocksUseCase
) {

    @GetMapping("/screen")
    fun screen(
        @RequestParam tickers: List<String>,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) peRatioMax: Double?,
        @RequestParam(required = false) pbRatioMax: Double?,
        @RequestParam(required = false) roeMin: Double?,
        @RequestParam(required = false) rsiMax: Double?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false, defaultValue = "true") ascending: Boolean
    ): Mono<ScreenResult> = Mono.fromCallable {
        screenStocksUseCase.screen(
            ScreenStocksUseCase.ScreenCommand(
                tickers = tickers,
                criteria = ScreenCriteria(
                    peRatioMax = peRatioMax,
                    pbRatioMax = pbRatioMax,
                    roeMin = roeMin,
                    rsiMax = rsiMax
                ),
                range = DateRange(startDate, endDate),
                interval = BarInterval.valueOf(interval.uppercase()),
                provider = provider,
                sortBy = sortBy,
                ascending = ascending
            )
        )
    }.subscribeOn(Schedulers.boundedElastic())
}
