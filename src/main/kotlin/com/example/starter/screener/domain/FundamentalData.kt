package com.example.starter.screener.domain

data class FundamentalData(
    val ticker: String,
    val peRatio: Double? = null,
    val pbRatio: Double? = null,
    val debtEquity: Double? = null,
    val roe: Double? = null,
    val profitMargin: Double? = null,
    val dividendYield: Double? = null,
    val marketCap: Double? = null,
    val beta: Double? = null
)
