package com.example.starter.backtest.domain

import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.shared.domain.PriceSeries

class PairBacktestEngine(
    private val cointegrationCalculator: CointegrationCalculator = CointegrationCalculator(),
    private val portfolioEngine: PortfolioEngine = PortfolioEngine()
) {

    fun runPairBacktest(
        seriesA: PriceSeries,
        seriesB: PriceSeries,
        entryZ: Double = 2.0,
        exitZ: Double = 0.5,
        zScoreWindow: Int = 30,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001
    ): BacktestResult {
        val coint = cointegrationCalculator.calculate(seriesA, seriesB, zScoreWindow)
        val hedge = coint.hedgeRatio
        val aligned = alignByDate(seriesA, seriesB)
        val spread = aligned.first.zip(aligned.second).map { (a, b) ->
            kotlin.math.ln(a.close.toDouble()) - hedge * kotlin.math.ln(b.close.toDouble())
        }
        val signals = spread.mapIndexed { idx, _ ->
            if (idx < zScoreWindow) 0.0 else {
                val window = spread.subList(idx - zScoreWindow, idx)
                val mean = window.average()
                val std = kotlin.math.sqrt(window.map { (it - mean) * (it - mean) }.average())
                val z = if (std == 0.0) 0.0 else (spread[idx] - mean) / std
                when {
                    z > entryZ -> -1.0
                    z < -entryZ -> 1.0
                    kotlin.math.abs(z) < exitZ -> 0.0
                    else -> Double.NaN
                }
            }
        }
        val cleanSignals = signals.map { if (it.isNaN()) 0.0 else it }
        val weights = aligned.first.zip(aligned.second).zip(cleanSignals).map { (pair, signal) ->
            val (a, b) = pair
            val weightA = if (signal > 0) 0.5 else if (signal < 0) -0.5 else 0.0
            val weightB = if (signal > 0) -0.5 * hedge else if (signal < 0) 0.5 * hedge else 0.0
            mapOf(a.ticker.symbol to weightA, b.ticker.symbol to weightB)
        }
        val priceData = mapOf(seriesA.first().ticker.symbol to aligned.first, seriesB.first().ticker.symbol to aligned.second)
        val result = portfolioEngine.runPortfolioSimulation(
            priceData = priceData,
            targetWeights = weights.first(),
            initialCapital = initialCapital,
            commissionPct = commissionPct
        )
        return result.copy(strategyName = "pair_trade")
    }

    private fun alignByDate(a: PriceSeries, b: PriceSeries): Pair<PriceSeries, PriceSeries> {
        val dates = a.map { it.date }.intersect(b.map { it.date }.toSet()).sorted()
        val byA = a.associateBy { it.date }
        val byB = b.associateBy { it.date }
        return dates.map { byA.getValue(it) } to dates.map { byB.getValue(it) }
    }
}
