package com.example.starter.backtest.application.service

import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestEngine
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.backtest.domain.MonteCarloEngine
import com.example.starter.backtest.domain.PairBacktestEngine
import com.example.starter.backtest.domain.PanelBacktestEngine
import com.example.starter.backtest.domain.PortfolioEngine
import com.example.starter.backtest.domain.Strategies
import com.example.starter.backtest.domain.WalkForwardEngine
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class BacktestService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase
) : RunBacktestUseCase {

    private val engine = BacktestEngine()
    private val portfolioEngine = PortfolioEngine()
    private val pairEngine = PairBacktestEngine()
    private val panelEngine = PanelBacktestEngine()
    private val walkForwardEngine = WalkForwardEngine()
    private val monteCarloEngine = MonteCarloEngine()

    override fun execute(command: RunBacktestUseCase.BacktestCommand): BacktestResult = when (command) {
        is RunBacktestUseCase.SingleAssetCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            val strategy = Strategies.REGISTRY.getValue(command.strategy)
            val signals = strategy.generate(series, command.parameters)
            engine.run(series, signals, command.initialCapital, command.commissionPct, command.slippagePct, command.strategy)
        }
        is RunBacktestUseCase.PortfolioSimulationCommand -> {
            val data = command.tickers.associate { it.symbol to fetch(it, command.range, command.interval, command.provider) }
            portfolioEngine.runPortfolioSimulation(data, command.weights, command.initialCapital, command.commissionPct, command.slippagePct, command.maxGrossLeverage)
        }
        is RunBacktestUseCase.PairTradeCommand -> {
            val a = fetch(Ticker(command.symbolA), command.range, command.interval, command.provider)
            val b = fetch(Ticker(command.symbolB), command.range, command.interval, command.provider)
            pairEngine.runPairBacktest(a, b, command.entryZ, command.exitZ, command.zScoreWindow, command.initialCapital, command.commissionPct)
        }
        is RunBacktestUseCase.SignalPanelCommand -> {
            val data = command.tickers.associate { it.symbol to fetch(it, command.range, command.interval, command.provider) }
            panelEngine.runSignalPanelBacktest(data, command.signals, command.initialCapital, command.commissionPct)
        }
        is RunBacktestUseCase.WalkForwardCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            walkForwardEngine.run(series, command.strategy, command.parameterGrid, command.trainSize, command.testSize, command.metric, command.initialCapital)
        }
        is RunBacktestUseCase.MonteCarloCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            val strategy = Strategies.REGISTRY.getValue(command.strategy)
            val signals = strategy.generate(series, command.parameters)
            val backtest = engine.run(series, signals, command.initialCapital, strategyName = command.strategy)
            val returns = backtest.equityCurve.zipWithNext { prev, curr -> (curr.equity - prev.equity) / prev.equity }
            val mc = monteCarloEngine.simulateForwardPaths(returns, command.horizonDays, command.nSimulations, command.blockSize, command.initialCapital)
            BacktestResult(
                strategyName = "monte_carlo_${command.strategy}",
                initialCapital = command.initialCapital,
                finalEquity = mc["percentile_50"] as Double,
                totalReturn = ((mc["percentile_50"] as Double) - command.initialCapital) / command.initialCapital,
                metrics = null,
                trades = emptyList(),
                equityCurve = emptyList(),
                drawdownEpisodes = emptyList(),
                diagnostics = null,
                parameterGrid = mc
            )
        }
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
