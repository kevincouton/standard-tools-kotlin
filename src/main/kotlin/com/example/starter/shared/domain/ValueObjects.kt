package com.example.starter.shared.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BarInterval {
    DAILY, WEEKLY, MONTHLY
}

data class Ticker(
    val symbol: String,
    val exchange: String? = null
) {
    init {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
    }
}

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    init {
        require(!start.isAfter(end)) { "start must not be after end" }
    }

    val days: Long
        get() = ChronoUnit.DAYS.between(start, end) + 1
}

data class OHLCV(
    val ticker: Ticker,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long
) {
    init {
        require(high >= low) { "high must not be less than low" }
        require(open >= BigDecimal.ZERO) { "open must not be negative" }
        require(high >= BigDecimal.ZERO) { "high must not be negative" }
        require(low >= BigDecimal.ZERO) { "low must not be negative" }
        require(close >= BigDecimal.ZERO) { "close must not be negative" }
        require(volume >= 0) { "volume must not be negative" }
        require(high >= maxOf(open, close)) { "high must not be less than max(open, close)" }
        require(low <= minOf(open, close)) { "low must not be greater than min(open, close)" }
    }
}

typealias PriceSeries = List<OHLCV>

data class CacheKey(
    val provider: String,
    val ticker: Ticker,
    val interval: BarInterval,
    val range: DateRange
) {
    fun toComposite(): String = "$provider:${ticker.symbol}:${ticker.exchange ?: ""}:${interval}:${range.start}:${range.end}"
}
