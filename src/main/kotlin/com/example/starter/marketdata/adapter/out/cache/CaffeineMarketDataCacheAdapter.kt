package com.example.starter.marketdata.adapter.out.cache

import com.example.starter.shared.application.port.outbound.MarketDataCache
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.PriceSeries
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CaffeineMarketDataCacheAdapter : MarketDataCache {

    private data class CacheEntry(val series: PriceSeries, val expiresAt: Instant)

    private val cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfter(object : Expiry<CacheKey, CacheEntry> {
            override fun expireAfterCreate(key: CacheKey, value: CacheEntry, currentTime: Long): Long {
                return Duration.between(Instant.now(), value.expiresAt).toNanos().coerceAtLeast(0)
            }

            override fun expireAfterUpdate(key: CacheKey, value: CacheEntry, currentTime: Long, currentDuration: Long): Long {
                return Duration.between(Instant.now(), value.expiresAt).toNanos().coerceAtLeast(0)
            }

            override fun expireAfterRead(key: CacheKey, value: CacheEntry, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }
        })
        .build<CacheKey, CacheEntry>()

    override fun get(key: CacheKey): PriceSeries? = cache.getIfPresent(key)?.series

    override fun put(key: CacheKey, series: PriceSeries, ttl: Duration) {
        cache.put(key, CacheEntry(series, Instant.now().plus(ttl)))
    }
}
