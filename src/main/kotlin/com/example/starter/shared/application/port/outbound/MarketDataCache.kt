package com.example.starter.shared.application.port.outbound

import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.PriceSeries
import java.time.Duration

interface MarketDataCache {
    fun get(key: CacheKey): PriceSeries?
    fun put(key: CacheKey, series: PriceSeries, ttl: Duration)
}
