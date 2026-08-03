package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression
import org.springframework.stereotype.Component

@Component
class MultiFactorCalculator {

    fun calculate(asset: PriceSeries, factors: Map<String, PriceSeries>): MultiFactorResult {
        val assetReturns = simpleReturns(asset)
        val aligned = factors.map { (name, series) -> name to simpleReturns(series).takeLast(assetReturns.size) }.toMap()
        val minLen = aligned.values.minOfOrNull { it.size }?.coerceAtMost(assetReturns.size) ?: assetReturns.size
        val y = assetReturns.takeLast(minLen).toDoubleArray()
        val x = aligned.keys.toList().map { name -> aligned.getValue(name).takeLast(minLen).toDoubleArray() }.toTypedArray()
        val design = Array(minLen) { row -> DoubleArray(x.size) { col -> x[col][row] } }
        val regression = OLSMultipleLinearRegression()
        regression.newSampleData(y, design)
        val params = regression.estimateRegressionParameters()
        val stdErrs = regression.estimateRegressionParametersStandardErrors()
        val r2 = regression.calculateRSquared()
        val adjR2 = regression.calculateAdjustedRSquared()
        val names = listOf("alpha") + aligned.keys
        val loadings = names.zip(params.toList()).toMap()
        val tStats = names.zip(params.zip(stdErrs).map { (p, e) -> if (e == 0.0) 0.0 else p / e }).toMap()
        val pValues = tStats.mapValues { (_, t) -> 2.0 * (1.0 - org.apache.commons.math3.distribution.TDistribution((minLen - names.size).toDouble()).cumulativeProbability(kotlin.math.abs(t))) }
        return MultiFactorResult(
            alpha = params[0],
            loadings = loadings.minus("alpha"),
            tStatistics = tStats,
            pValues = pValues,
            rSquared = r2,
            adjRSquared = adjR2
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
