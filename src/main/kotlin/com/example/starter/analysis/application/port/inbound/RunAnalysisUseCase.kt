package com.example.starter.analysis.application.port.inbound

import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface RunAnalysisUseCase {
    fun execute(command: AnalysisCommand): AnalysisResult

    sealed class AnalysisCommand {
        abstract val provider: String?
    }

    data class RegressionCommand(
        val asset: Ticker,
        val benchmark: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val riskFreeRate: Double = 0.02
    ) : AnalysisCommand()

    data class CointegrationCommand(
        val assetA: Ticker,
        val assetB: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val zScoreWindow: Int = 30
    ) : AnalysisCommand()

    data class HurstCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val method: String = "dfa",
        val minWindow: Int = 10,
        val rollingWindow: Int? = null
    ) : AnalysisCommand()

    data class PcaCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val nComponents: Int? = null,
        val standardize: Boolean = true
    ) : AnalysisCommand()

    data class CorrelationCommand(
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null,
        val weights: Map<String, Double>? = null
    ) : AnalysisCommand()

    data class MultiFactorCommand(
        val asset: Ticker,
        val factors: Map<String, Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null
    ) : AnalysisCommand()

    data class OptionPricingCommand(
        val spot: Double,
        val strike: Double,
        val timeToExpiry: Double,
        val riskFreeRate: Double,
        val volatility: Double,
        val optionType: String = "call",
        val dividendYield: Double = 0.0,
        val marketPrice: Double? = null
    ) : AnalysisCommand() {
        override val provider: String? = null
    }
}
