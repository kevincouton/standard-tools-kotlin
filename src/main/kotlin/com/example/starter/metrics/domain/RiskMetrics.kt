package com.example.starter.metrics.domain

import java.math.BigDecimal
import java.time.LocalDate

data class RiskMetrics(
    val sharpeRatio: BigDecimal?,
    val sortinoRatio: BigDecimal?,
    val maxDrawdown: BigDecimal,
    val calmarRatio: BigDecimal?,
    val var95: BigDecimal,
    val cvar95: BigDecimal,
    val volatility: BigDecimal
)

data class ReturnMetrics(
    val cumulativeReturn: BigDecimal,
    val cagr: BigDecimal?,
    val annualizedVolatility: BigDecimal
)
