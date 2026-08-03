package com.example.starter.portfolio.domain

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.abs
import kotlin.math.sqrt

class RiskParityOptimizer {

    fun optimize(returns: List<List<Double>>, tickers: List<String>, riskBudget: Map<String, Double>? = null): Portfolio {
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val budget = tickers.map { riskBudget?.get(it) ?: 1.0 / tickers.size }.toDoubleArray()
        var weights = DoubleArray(tickers.size) { 1.0 / tickers.size }
        repeat(1_000) {
            val mrc = marginalRiskContributions(cov, weights)
            val rc = mrc.mapIndexed { idx, value -> value * weights[idx] }.toDoubleArray()
            val target = budget.mapIndexed { idx, b -> b * rc.sum() }.toDoubleArray()
            val newWeights = weights.mapIndexed { idx, w -> w * target[idx] / rc[idx].coerceAtLeast(1e-12) }.toDoubleArray()
            val sum = newWeights.sum()
            weights = newWeights.map { it / sum }.toDoubleArray()
            if (rc.zip(target).all { (a, b) -> abs(a - b) < 1e-10 }) return@repeat
        }
        val portVariance = weights.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * weights.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }
        val portReturn = returns.map { it.average() }.zip(weights.asIterable()).sumOf { (r, w) -> r * w } * 252
        return Portfolio(
            objective = "risk_parity",
            tickers = tickers,
            weights = tickers.zip(weights.asIterable()).toMap(),
            expectedReturn = portReturn,
            volatility = sqrt(portVariance) * sqrt(252.0),
            sharpeRatio = null
        )
    }

    private fun marginalRiskContributions(cov: org.apache.commons.math3.linear.RealMatrix, weights: DoubleArray): DoubleArray {
        val w = Array2DRowRealMatrix(weights)
        val mrc = cov.multiply(w).getColumnVector(0)
        return (0 until weights.size).map { mrc.getEntry(it) }.toDoubleArray()
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
