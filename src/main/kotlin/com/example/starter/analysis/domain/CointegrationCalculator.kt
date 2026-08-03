package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.ln
import kotlin.math.sqrt

class CointegrationCalculator {

    fun calculate(a: PriceSeries, b: PriceSeries, zScoreWindow: Int = 30): CointegrationResult {
        val aligned = alignByDate(a, b)
        val logA = aligned.first.map { ln(it.close.toDouble()) }
        val logB = aligned.second.map { ln(it.close.toDouble()) }
        val ols = SimpleRegression()
        logB.zip(logA).forEach { (x, y) -> ols.addData(x, y) }
        val hedgeRatio = ols.slope
        val intercept = ols.intercept
        val spread = logB.zip(logA).map { (x, y) -> y - intercept - hedgeRatio * x }
        val hl = halfLife(spread)
        val (adfStat, pApprox) = adfApproximation(spread)
        val currentZ = if (spread.size >= zScoreWindow) zScore(spread.takeLast(zScoreWindow)) else null
        return CointegrationResult(
            hedgeRatio = hedgeRatio,
            adfStatistic = adfStat,
            pValueApprox = pApprox,
            halfLife = hl,
            currentZScore = currentZ
        )
    }

    private fun alignByDate(a: PriceSeries, b: PriceSeries): Pair<PriceSeries, PriceSeries> {
        val dates = a.map { it.date }.intersect(b.map { it.date }.toSet()).sorted()
        val byDateA = a.associateBy { it.date }
        val byDateB = b.associateBy { it.date }
        return dates.map { byDateA.getValue(it) } to dates.map { byDateB.getValue(it) }
    }

    private fun halfLife(spread: List<Double>): Double {
        if (spread.size < 2) return Double.NaN
        val delta = spread.zipWithNext { prev, curr -> curr - prev }
        val lag = spread.dropLast(1)
        val ols = SimpleRegression()
        lag.zip(delta).forEach { (x, y) -> ols.addData(x, y) }
        val lambda = ols.slope
        return if (lambda < 0) -ln(2.0) / lambda else Double.POSITIVE_INFINITY
    }

    private fun adfApproximation(spread: List<Double>): Pair<Double, Double> {
        if (spread.size < 3) return 0.0 to 1.0
        val diff = spread.zipWithNext { prev, curr -> curr - prev }
        val lag = spread.dropLast(1)
        val ols = SimpleRegression()
        lag.zip(diff).forEach { (x, y) -> ols.addData(x, y) }
        val tStat = ols.slope / if (ols.slopeStdErr == 0.0) 1.0 else ols.slopeStdErr
        val pApprox = 1.0 / (1.0 + kotlin.math.exp(2.0 * tStat + 1.0))
        return tStat to pApprox
    }

    private fun zScore(window: List<Double>): Double {
        val mean = window.average()
        val variance = window.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        return if (std == 0.0) 0.0 else (window.last() - mean) / std
    }
}
