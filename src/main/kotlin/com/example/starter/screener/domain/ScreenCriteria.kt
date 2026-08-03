package com.example.starter.screener.domain

data class ScreenCriteria(
    val peRatioMax: Double? = null,
    val pbRatioMax: Double? = null,
    val debtEquityMax: Double? = null,
    val roeMin: Double? = null,
    val profitMarginMin: Double? = null,
    val dividendYieldMin: Double? = null,
    val marketCapMin: Double? = null,
    val rsiMax: Double? = null,
    val rsiMin: Double? = null,
    val priceAboveSma: Int? = null,
    val priceBelowSma: Int? = null,
    val betaMax: Double? = null,
    val betaMin: Double? = null
)
