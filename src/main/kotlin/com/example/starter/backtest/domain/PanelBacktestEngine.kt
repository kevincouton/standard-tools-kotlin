package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

class PanelBacktestEngine(private val engine: BacktestEngine = BacktestEngine()) {

    fun runSignalPanelBacktest(
        priceData: Map<String, PriceSeries>,
        signalPanel: Map<String, List<Double>>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001
    ): BacktestResult {
        val perTicker = priceData.map { (symbol, series) ->
            val signals = signalPanel[symbol] ?: List(series.size) { 0.0 }
            symbol to engine.run(series, signals, initialCapital = initialCapital / priceData.size, commissionPct = commissionPct, strategyName = "panel_$symbol")
        }.toMap()
        val dates = priceData.values.first().map { it.date }
        val equityCurve = dates.mapIndexed { idx, date ->
            val equity = perTicker.values.sumOf { it.equityCurve[idx].equity }
            val drawdown = perTicker.values.maxOfOrNull { it.equityCurve[idx].drawdown } ?: 0.0
            EquityCurvePoint(date, equity, drawdown)
        }
        val trades = perTicker.values.flatMap { it.trades }
        return BacktestResult(
            strategyName = "signal_panel",
            initialCapital = initialCapital,
            finalEquity = equityCurve.last().equity,
            totalReturn = (equityCurve.last().equity - initialCapital) / initialCapital,
            metrics = null,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = emptyList(),
            diagnostics = null
        )
    }
}
