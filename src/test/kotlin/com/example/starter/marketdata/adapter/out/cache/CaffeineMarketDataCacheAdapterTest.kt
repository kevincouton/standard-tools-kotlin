package com.example.starter.marketdata.adapter.out.cache

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.CacheKey
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.Duration
import java.time.LocalDate

@Tag("unit")
class CaffeineMarketDataCacheAdapterTest {

    private val cache = CaffeineMarketDataCacheAdapter()
    private val key = CacheKey(
        provider = "yfinance",
        ticker = Ticker("AAPL"),
        interval = BarInterval.DAILY,
        range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 5))
    )
    private val series = OhlcvFixtures.dailySeries(days = 5)

    @Test
    fun `put and get returns series`() {
        cache.put(key, series, Duration.ofMinutes(5))
        expectThat(cache.get(key)).isEqualTo(series)
    }

    @Test
    fun `get missing key returns null`() {
        expectThat(cache.get(key)).isNull()
    }

    @Test
    fun `entry expires after ttl`() {
        cache.put(key, series, Duration.ofMillis(100))
        expectThat(cache.get(key)).isEqualTo(series)
        Thread.sleep(150)
        expectThat(cache.get(key)).isNull()
    }
}
