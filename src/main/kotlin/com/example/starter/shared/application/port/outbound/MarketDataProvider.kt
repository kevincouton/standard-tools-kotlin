package com.example.starter.shared.application.port.outbound

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker

interface MarketDataProvider {
    val name: String
    fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries
}
