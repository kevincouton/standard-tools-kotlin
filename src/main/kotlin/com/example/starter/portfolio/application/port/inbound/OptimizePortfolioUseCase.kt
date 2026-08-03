package com.example.starter.portfolio.application.port.inbound

import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface OptimizePortfolioUseCase {
    fun optimize(command: OptimizeCommand): Portfolio
    fun riskParity(command: RiskParityCommand): Portfolio
    fun blackLitterman(command: BlackLittermanCommand): Portfolio

    data class OptimizeCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val objective: String = "max_sharpe",
        val riskFreeRate: Double = 0.02,
        val targetReturn: Double? = null,
        val targetVolatility: Double? = null,
        val allowShort: Boolean = false,
        val maxWeight: Double? = null,
        val provider: String? = null
    )

    data class RiskParityCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val riskBudget: Map<String, Double>? = null,
        val provider: String? = null
    )

    data class BlackLittermanCommand(
        val tickers: List<Ticker>,
        val marketWeights: Map<String, Double>,
        val views: BlackLittermanViewsInput,
        val range: DateRange,
        val interval: BarInterval,
        val riskAversion: Double = 2.5,
        val tau: Double = 0.05,
        val provider: String? = null
    )

    data class BlackLittermanViewsInput(
        val assets: List<String>,
        val views: List<View>
    ) {
        data class View(
            val asset: String? = null,
            val relativeAsset: String? = null,
            val returnView: Double
        )
    }
}
