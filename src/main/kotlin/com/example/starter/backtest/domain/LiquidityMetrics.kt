package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

object LiquidityMetrics {

    fun amihudIlliquidity(returns: List<Double>, dollarVolumes: List<Double>, window: Int = 20): List<Double?> {
        return returns.mapIndexed { idx, ret ->
            if (idx < window - 1) null
            else {
                val windowed = (0 until window).map { i -> kotlin.math.abs(returns[idx - i]) / dollarVolumes[idx - i] }
                windowed.average()
            }
        }
    }

    fun corwinSchultzSpread(highs: List<Double>, lows: List<Double>, window: Int = 1): List<Double?> {
        return highs.mapIndexed { idx, _ ->
            if (idx < window) null
            else {
                val beta = (0 until window).sumOf { i -> kotlin.math.ln(highs[idx - i] / lows[idx - i]).let { it * it } }
                val highMax = highs.subList(idx - window + 1, idx + 1).maxOrNull() ?: highs[idx]
                val lowMin = lows.subList(idx - window + 1, idx + 1).minOrNull() ?: lows[idx]
                val gamma = kotlin.math.ln(highMax / lowMin.coerceAtLeast(1e-12))
                val alpha = (kotlin.math.sqrt(2.0 * beta) - kotlin.math.sqrt(beta)) / (3.0 - 2.0 * kotlin.math.sqrt(2.0)) - kotlin.math.sqrt(gamma / (3.0 - 2.0 * kotlin.math.sqrt(2.0)))
                maxOf(alpha, 0.0)
            }
        }
    }
}
