package com.example.starter.portfolio.domain

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.sqrt

class PortfolioMetricsCalculator {

    fun portfolioReturn(returns: List<List<Double>>, weights: DoubleArray): Double {
        val meanReturns = returns.map { it.average() }
        return meanReturns.zip(weights.asIterable()).sumOf { (r, w) -> r * w }
    }

    fun portfolioVariance(returns: List<List<Double>>, weights: DoubleArray): Double {
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val w = Array2DRowRealMatrix(weights)
        return (w.transpose().multiply(cov).multiply(w)).getEntry(0, 0)
    }

    fun portfolioVolatility(returns: List<List<Double>>, weights: DoubleArray): Double = sqrt(portfolioVariance(returns, weights))

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
