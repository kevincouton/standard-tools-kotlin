package com.example.starter.screener.domain

data class ScreenResult(
    val criteria: ScreenCriteria,
    val matches: List<ScreenMatch>,
    val failedTickers: List<String>
)

data class ScreenMatch(
    val ticker: String,
    val fundamentals: FundamentalData,
    val rsi: Double? = null,
    val priceVsSma: Double? = null
)
