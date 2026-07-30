package com.example.starter.marketdata.adapter.`in`.web

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
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
@RequestMapping("/api/v1/market-data")
class MarketDataController(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase
) {

    @GetMapping(value = ["/bars"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchBars(
        @RequestParam symbol: String,
        @RequestParam(required = false) exchange: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam interval: String,
        @RequestParam(required = false) provider: String?
    ): Mono<List<OHLCV>> {
        return Mono.fromCallable {
            fetchMarketDataUseCase.fetch(
                FetchMarketDataUseCase.FetchMarketDataCommand(
                    ticker = Ticker(symbol, exchange),
                    range = DateRange(startDate, endDate),
                    interval = BarInterval.valueOf(interval.uppercase()),
                    provider = provider
                )
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
