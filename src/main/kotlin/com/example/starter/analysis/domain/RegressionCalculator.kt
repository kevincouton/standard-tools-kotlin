package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.springframework.stereotype.Component

@Component
class RegressionCalculator {

    fun calculate(asset: PriceSeries, benchmark: PriceSeries, riskFreeRate: Double = 0.02): RegressionResult {
        val assetReturns = simpleReturns(asset)
        val benchReturns = simpleReturns(benchmark)
        require(assetReturns.size == benchReturns.size) { "series must align" }
        require(assetReturns.size >= 2) { "need at least 3 prices" }
        val regression = SimpleRegression()
        assetReturns.zip(benchReturns).forEach { (a, b) -> regression.addData(b, a) }
        val periodsPerYear = 252.0
        val alpha = regression.intercept
        return RegressionResult(
            alpha = alpha,
            beta = regression.slope,
            rSquared = regression.rSquare,
            annualizedAlpha = alpha * periodsPerYear
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
