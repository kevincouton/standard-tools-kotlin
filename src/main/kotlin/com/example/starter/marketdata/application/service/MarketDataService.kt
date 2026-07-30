package com.example.starter.marketdata.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.ProviderNotAvailableException
import org.springframework.stereotype.Service

@Service
class MarketDataService(
    private val providers: List<MarketDataProvider>,
    private val cache: MarketDataCache,
    private val properties: MarketDataProperties
) : FetchMarketDataUseCase {

    override fun fetch(command: FetchMarketDataUseCase.FetchMarketDataCommand): List<OHLCV> {
        val providerName = command.provider ?: properties.defaultProvider
        val provider = providers.find { it.name == providerName }
            ?: throw ProviderNotAvailableException(providerName)

        if (!properties.isEnabled(providerName)) {
            throw ProviderNotAvailableException(providerName)
        }

        val key = CacheKey(providerName, command.ticker, command.interval, command.range)
        cache.get(key)?.let { return it }

        val series = provider.fetch(command.ticker, command.range, command.interval)
        cache.put(key, series, properties.cacheTtl)
        return series
    }
}
