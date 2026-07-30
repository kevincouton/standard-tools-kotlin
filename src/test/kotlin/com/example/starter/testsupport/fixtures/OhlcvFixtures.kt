package com.example.starter.testsupport.fixtures

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import java.math.BigDecimal
import java.time.LocalDate

object OhlcvFixtures {

    private val ticker = Ticker("AAPL", "NASDAQ")

    fun dailySeries(
        ticker: Ticker = this.ticker,
        start: LocalDate = LocalDate.of(2024, 1, 1),
        days: Int = 10,
        basePrice: Double = 100.0,
        volatility: Double = 2.0
    ): List<OHLCV> {
        return (0 until days).map { i ->
            val date = start.plusDays(i.toLong())
            val seed = (i * 7 + 3) % 13 - 6
            val open = basePrice + seed * volatility
            val close = open + ((seed + 2) % 5) * volatility * 0.5
            val high = maxOf(open, close) + volatility
            val low = minOf(open, close) - volatility
            OHLCV(
                ticker = ticker,
                date = date,
                open = BigDecimal(open.toString()),
                high = BigDecimal(high.toString()),
                low = BigDecimal(low.toString()),
                close = BigDecimal(close.toString()),
                volume = 1_000_000L + i * 10_000L
            )
        }
    }

    fun dateRange(start: LocalDate = LocalDate.of(2024, 1, 1), days: Int = 10): DateRange {
        return DateRange(start, start.plusDays(days.toLong() - 1))
    }

    val DEFAULT_INTERVAL: BarInterval = BarInterval.DAILY
}
