package com.example.starter.backtest.application.port.inbound

import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface RunBacktestUseCase {
    fun execute(command: BacktestCommand): BacktestResult

    sealed class BacktestCommand {
        abstract val provider: String?
    }

    data class SingleAssetCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameters: Map<String, Any> = emptyMap(),
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        val slippagePct: Double = 0.0005,
        override val provider: String? = null
    ) : BacktestCommand()

    data class PortfolioSimulationCommand(
        val tickers: List<Ticker>,
        val weights: Map<String, Double>,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        val slippagePct: Double = 0.0005,
        val maxGrossLeverage: Double = 1.0,
        override val provider: String? = null
    ) : BacktestCommand()

    data class PairTradeCommand(
        val symbolA: String,
        val symbolB: String,
        val entryZ: Double = 2.0,
        val exitZ: Double = 0.5,
        val zScoreWindow: Int = 30,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        override val provider: String? = null
    ) : BacktestCommand()

    data class SignalPanelCommand(
        val signals: Map<String, List<Double>>,
        val tickers: List<Ticker>,
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        val commissionPct: Double = 0.001,
        override val provider: String? = null
    ) : BacktestCommand()

    data class WalkForwardCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameterGrid: Map<String, List<Any>>,
        val trainSize: Int = 252,
        val testSize: Int = 63,
        val metric: String = "sharpe_ratio",
        val range: DateRange,
        val interval: BarInterval,
        val initialCapital: Double = 10_000.0,
        override val provider: String? = null
    ) : BacktestCommand()

    data class MonteCarloCommand(
        val ticker: Ticker,
        val strategy: String,
        val parameters: Map<String, Any> = emptyMap(),
        val horizonDays: Int = 252,
        val nSimulations: Int = 1_000,
        val blockSize: Int = 20,
        val initialCapital: Double = 10_000.0,
        val range: DateRange,
        val interval: BarInterval,
        override val provider: String? = null
    ) : BacktestCommand()
}
