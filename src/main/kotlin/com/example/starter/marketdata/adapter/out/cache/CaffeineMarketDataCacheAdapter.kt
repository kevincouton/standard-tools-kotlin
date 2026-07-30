package com.example.starter.marketdata.adapter.out.cache

import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.PriceSeries
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CaffeineMarketDataCacheAdapter : MarketDataCache {

    private val cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build<CacheKey, PriceSeries>()

    override fun get(key: CacheKey): PriceSeries? = cache.getIfPresent(key)

    override fun put(key: CacheKey, series: PriceSeries, ttl: Duration) {
        cache.put(key, series)
    }
}
