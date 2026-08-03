package com.example.starter.backtest.domain

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

class RobustnessEngine {

    fun blockBootstrapCi(
        returns: List<Double>,
        metricFn: (List<Double>) -> Double,
        nIterations: Int = 1_000,
        blockSize: Int = 20,
        confidence: Double = 0.95,
        seed: Long? = null
    ): Map<String, Double> {
        val random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
        val stats = DescriptiveStatistics()
        repeat(nIterations) {
            val sample = blockBootstrap(returns, blockSize, random)
            stats.addValue(metricFn(sample))
        }
        val alpha = 1.0 - confidence
        val lower = stats.getPercentile(alpha * 50.0)
        val upper = stats.getPercentile(100.0 - alpha * 50.0)
        return mapOf("mean" to stats.mean, "median" to stats.getPercentile(50.0), "lower" to lower, "upper" to upper)
    }

    private fun blockBootstrap(returns: List<Double>, blockSize: Int, random: kotlin.random.Random): List<Double> {
        if (returns.isEmpty()) return emptyList()
        if (returns.size <= blockSize) return returns.shuffled(random)
        val result = mutableListOf<Double>()
        while (result.size < returns.size) {
            val start = random.nextInt(0, returns.size - blockSize + 1)
            result.addAll(returns.subList(start, kotlin.math.min(start + blockSize, returns.size)))
        }
        return result.take(returns.size)
    }

    fun parameterSensitivity(gridResults: List<Map<String, Any>>, metricCol: String = "sharpe_ratio"): Map<String, Double> {
        val sorted = gridResults.sortedByDescending { (it[metricCol] as? Number)?.toDouble() ?: 0.0 }
        val best = (sorted.firstOrNull()?.get(metricCol) as? Number)?.toDouble() ?: 0.0
        val median = sorted.getOrNull(sorted.size / 2).let { (it?.get(metricCol) as? Number)?.toDouble() ?: 0.0 }
        val rank2 = (sorted.getOrNull(1)?.get(metricCol) as? Number)?.toDouble() ?: 0.0
        return mapOf("best" to best, "median" to median, "rank2" to rank2, "best_minus_median" to best - median)
    }

    fun deflatedSharpeRatio(
        observedSharpe: Double,
        sharpeTrialsStd: Double,
        nTrials: Int,
        nObs: Int,
        skew: Double = 0.0,
        kurtosis: Double = 3.0
    ): Map<String, Double> {
        val variance = sharpeTrialsStd * sharpeTrialsStd
        val expectedMax = sharpeTrialsStd * ((1.0 - 0.5772) * kotlin.math.ln(nTrials.toDouble()) + 0.5772 * kotlin.math.ln(nTrials.toDouble()))
        val adj = 1.0 + (observedSharpe * skew / 6.0) * observedSharpe - ((kurtosis - 3.0) / 24.0) * observedSharpe * observedSharpe
        val dsr = (observedSharpe - expectedMax) / sharpeTrialsStd * adj
        return mapOf("deflated_sharpe" to dsr, "expected_max_sharpe" to expectedMax)
    }
}
