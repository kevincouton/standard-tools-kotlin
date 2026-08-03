package com.example.starter.backtest.domain

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

class MonteCarloEngine {

    fun simulateForwardPaths(
        returns: List<Double>,
        horizonDays: Int = 252,
        nSimulations: Int = 1_000,
        blockSize: Int = 20,
        initialCapital: Double = 10_000.0,
        seed: Long? = null
    ): Map<String, Any> {
        val random = seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default
        val terminals = DoubleArray(nSimulations)
        repeat(nSimulations) { sim ->
            var equity = initialCapital
            repeat(horizonDays) { day ->
                val start = random.nextInt(0, (returns.size - blockSize).coerceAtLeast(1) + 1)
                val blockReturn = returns.subList(start, kotlin.math.min(start + blockSize, returns.size)).average()
                equity *= (1.0 + blockReturn)
            }
            terminals[sim] = equity
        }
        val stats = DescriptiveStatistics(terminals)
        return mapOf(
            "mean_terminal_equity" to stats.mean,
            "median_terminal_equity" to stats.getPercentile(50.0),
            "percentile_5" to stats.getPercentile(5.0),
            "percentile_95" to stats.getPercentile(95.0),
            "initial_capital" to initialCapital
        )
    }
}
