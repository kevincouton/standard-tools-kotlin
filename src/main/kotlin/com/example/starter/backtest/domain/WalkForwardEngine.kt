package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

class WalkForwardEngine(
    private val engine: BacktestEngine = BacktestEngine(),
    private val strategies: Map<String, Strategy> = Strategies.REGISTRY
) {

    fun run(
        series: PriceSeries,
        strategyName: String,
        parameterGrid: Map<String, List<Any>>,
        trainSize: Int,
        testSize: Int,
        metric: String = "sharpe_ratio",
        initialCapital: Double = 10_000.0
    ): BacktestResult {
        val combinations = cartesianProduct(parameterGrid)
        val outOfSampleReturns = mutableListOf<Double>()
        val windowParams = mutableListOf<Map<String, Any>>()
        var start = 0
        while (start + trainSize + testSize <= series.size) {
            val train = series.subList(start, start + trainSize)
            val test = series.subList(start + trainSize, start + trainSize + testSize)
            val best = combinations.maxByOrNull { params ->
                val signals = strategies.getValue(strategyName).generate(train, params)
                val result = engine.run(train, signals, initialCapital = initialCapital, strategyName = strategyName)
                metricValue(result, metric)
            } ?: combinations.first()
            val testSignals = strategies.getValue(strategyName).generate(test, best)
            val testResult = engine.run(test, testSignals, initialCapital = initialCapital, strategyName = strategyName)
            val dailyReturns = testResult.equityCurve.zipWithNext { prev, curr -> (curr.equity - prev.equity) / prev.equity }
            outOfSampleReturns.addAll(dailyReturns)
            windowParams.add(best)
            start += testSize
        }
        val equity = outOfSampleReturns.runningFold(initialCapital) { acc, r -> acc * (1 + r) }
        val curve = equity.mapIndexed { idx, e -> EquityCurvePoint(series[trainSize + idx].date, e, 0.0) }
        return BacktestResult(
            strategyName = "walk_forward_$strategyName",
            initialCapital = initialCapital,
            finalEquity = equity.last(),
            totalReturn = (equity.last() - initialCapital) / initialCapital,
            metrics = null,
            trades = emptyList(),
            equityCurve = curve,
            drawdownEpisodes = emptyList(),
            diagnostics = null,
            parameterGrid = mapOf("window_count" to windowParams.size)
        )
    }

    private fun metricValue(result: BacktestResult, metric: String): Double = when (metric) {
        "sharpe_ratio" -> result.metrics?.sharpeRatio?.toDouble() ?: 0.0
        "total_return" -> result.totalReturn
        "max_drawdown" -> -(result.drawdownEpisodes.maxOfOrNull { it.depth } ?: 0.0)
        else -> result.totalReturn
    }

    private fun cartesianProduct(grid: Map<String, List<Any>>): List<Map<String, Any>> {
        if (grid.isEmpty()) return listOf(emptyMap())
        val keys = grid.keys.toList()
        val values = keys.map { grid.getValue(it) }
        return values.fold(listOf(emptyList<Any>())) { acc, list ->
            acc.flatMap { prefix -> list.map { prefix + it } }
        }.map { combo -> keys.zip(combo).toMap() }
    }
}
