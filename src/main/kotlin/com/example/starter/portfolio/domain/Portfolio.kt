package com.example.starter.portfolio.domain

data class Portfolio(
    val objective: String,
    val tickers: List<String>,
    val weights: Map<String, Double>,
    val expectedReturn: Double,
    val volatility: Double,
    val sharpeRatio: Double?
)

data class BlackLittermanViews(
    val pMatrix: List<List<Double>>,
    val qVector: List<Double>,
    val omegaMatrix: List<List<Double>>? = null
)
