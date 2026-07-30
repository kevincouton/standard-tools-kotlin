package com.example.starter.marketdata.adapter.out.bloomberg

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "standard-tools.market-data.providers.bloomberg", name = ["enabled"], havingValue = "true")
class BloombergMarketDataAdapter : MarketDataProvider {

    override val name: String = "bloomberg"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        throw ProviderNotAvailableException("bloomberg")
    }
}
