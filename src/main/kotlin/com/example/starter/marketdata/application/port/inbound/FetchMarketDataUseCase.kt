package com.example.starter.marketdata.application.port.inbound

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker

interface FetchMarketDataUseCase {
    fun fetch(command: FetchMarketDataCommand): PriceSeries

    data class FetchMarketDataCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null
    )
}
