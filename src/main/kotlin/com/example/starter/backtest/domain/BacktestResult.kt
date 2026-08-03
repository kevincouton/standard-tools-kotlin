package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskMetrics
import java.time.LocalDate

data class BacktestResult(
    val strategyName: String,
    val initialCapital: Double,
    val finalEquity: Double,
    val totalReturn: Double,
    val metrics: RiskMetrics?,
    val trades: List<Trade>,
    val equityCurve: List<EquityCurvePoint>,
    val drawdownEpisodes: List<DrawdownEpisode>,
    val diagnostics: BacktestDiagnostics?,
    val parameterGrid: Map<String, Any>? = null
)

data class Trade(
    val entryDate: LocalDate,
    val exitDate: LocalDate?,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double?,
    val size: Double,
    val pnl: Double,
    val mae: Double?,
    val mfe: Double?
)

data class EquityCurvePoint(
    val date: LocalDate,
    val equity: Double,
    val drawdown: Double
)

data class DrawdownEpisode(
    val startDate: LocalDate,
    val troughDate: LocalDate,
    val endDate: LocalDate?,
    val depth: Double
)

data class BacktestDiagnostics(
    val numberOfTrades: Int,
    val winRate: Double,
    val averageTradeReturn: Double,
    val expectancy: Double,
    val maxExposure: Double,
    val annualizedTurnover: Double
)
