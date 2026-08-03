package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.springframework.stereotype.Component

@Component
class CorrelationCalculator {

    fun calculate(tickers: List<String>, series: List<PriceSeries>, weights: Map<String, Double>? = null): CorrelationResult {
        require(tickers.size == series.size && tickers.size >= 2)
        val returns = series.map { simpleReturns(it) }
        val minLen = returns.minOf { it.size }
        val data = Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
        val corr = PearsonsCorrelation(data)
        val matrix = tickers.mapIndexed { i, ti ->
            ti to tickers.mapIndexed { j, tj -> tj to corr.correlationMatrix.getEntry(i, j) }.toMap()
        }.toMap()
        val pairs = mutableListOf<Double>()
        for (i in tickers.indices) for (j in i + 1 until tickers.size) pairs.add(corr.correlationMatrix.getEntry(i, j))
        val divRatio = weights?.let { diversificationRatio(tickers, returns, it) }
        return CorrelationResult(
            matrix = matrix,
            average = pairs.average(),
            min = pairs.minOrNull() ?: 0.0,
            max = pairs.maxOrNull() ?: 0.0,
            diversificationRatio = divRatio
        )
    }

    private fun diversificationRatio(tickers: List<String>, returns: List<List<Double>>, weights: Map<String, Double>): Double {
        val w = tickers.map { weights[it] ?: 0.0 }.toDoubleArray()
        val data = Array(returns.first().size) { row -> DoubleArray(returns.size) { col -> returns[col][row] } }
        val cov = org.apache.commons.math3.stat.correlation.Covariance(data).covarianceMatrix
        val portfolioVariance = w.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * w.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }
        val weightedVol = tickers.indices.sumOf { i -> w[i] * kotlin.math.sqrt(cov.getEntry(i, i)) }
        return if (portfolioVariance <= 0) weightedVol else weightedVol / kotlin.math.sqrt(portfolioVariance)
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
