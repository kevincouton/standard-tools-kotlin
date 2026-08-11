package com.example.starter.metrics.domain

import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt

@Component
class RiskReturnCalculator {

    fun returnMetrics(series: PriceSeries, riskFreeRate: Double = 0.02): ReturnMetrics {
        val returns = simpleReturns(series)
        val cumulative = returns
            .fold(1.0) { acc, r -> acc * (1 + r) }
            .let { BigDecimal(it - 1).setScale(4, RoundingMode.HALF_UP) }
        val stats = DescriptiveStatistics(returns.toDoubleArray())
        val annVol = stats.standardDeviation * sqrt(252.0)
        val meanReturn = returns.average() * 252
        val cagr = if (returns.isEmpty()) null else BigDecimal(meanReturn).setScale(4, RoundingMode.HALF_UP)
        return ReturnMetrics(
            cumulativeReturn = cumulative,
            cagr = cagr,
            annualizedVolatility = BigDecimal(annVol).setScale(4, RoundingMode.HALF_UP)
        )
    }

    fun riskMetrics(series: PriceSeries, riskFreeRate: Double = 0.02): RiskMetrics {
        val returns = simpleReturns(series)
        val stats = DescriptiveStatistics(returns.toDoubleArray())
        val meanExcess = returns.map { it - riskFreeRate / 252 }.average()
        val vol = stats.standardDeviation * sqrt(252.0)
        val downside = returns.filter { it < 0 }
        val downsideDev = if (downside.isEmpty()) 0.0 else DescriptiveStatistics(downside.toDoubleArray()).standardDeviation * sqrt(252.0)
        val sharpe = if (vol == 0.0) null else meanExcess / vol
        val sortino = if (downsideDev == 0.0) null else meanExcess / downsideDev
        val (maxDd, _) = drawdown(series)
        val calmar = if (maxDd == 0.0) null else meanExcess * 252 / maxDd
        val sorted = returns.sorted()
        val var95 = sorted.getOrElse((sorted.size * 0.05).toInt()) { sorted.firstOrNull() ?: 0.0 }
        val cvar95 = sorted.take((sorted.size * 0.05).toInt().coerceAtLeast(1)).average()
        return RiskMetrics(
            sharpeRatio = sharpe?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            sortinoRatio = sortino?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            maxDrawdown = BigDecimal(maxDd).setScale(4, RoundingMode.HALF_UP),
            calmarRatio = calmar?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            var95 = BigDecimal(var95).setScale(4, RoundingMode.HALF_UP),
            cvar95 = BigDecimal(cvar95).setScale(4, RoundingMode.HALF_UP),
            volatility = BigDecimal(vol).setScale(4, RoundingMode.HALF_UP)
        )
    }

    private fun simpleReturns(series: PriceSeries): List<Double> {
        return series.zipWithNext { prev, curr ->
            (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
        }
    }

    private fun drawdown(series: PriceSeries): Pair<Double, List<Double>> {
        var peak = Double.NEGATIVE_INFINITY
        val drawdowns = mutableListOf<Double>()
        series.forEach { bar ->
            val price = bar.close.toDouble()
            if (price > peak) peak = price
            drawdowns.add((peak - price) / peak)
        }
        return (drawdowns.maxOrNull() ?: 0.0) to drawdowns
    }
}
