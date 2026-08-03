package com.example.starter.backtest.domain

import kotlin.math.abs

object Sizing {

    fun rankWeighted(scores: Map<String, Double>, grossLeverage: Double = 1.0): Map<String, Double> {
        val ranked = scores.entries.sortedByDescending { it.value }.mapIndexed { idx, entry -> entry.key to (scores.size - idx).toDouble() }.toMap()
        val sum = ranked.values.sum()
        return if (sum == 0.0) scores.mapValues { 0.0 } else ranked.mapValues { grossLeverage * it.value / sum * scores.size }
    }

    fun equalWeightTopBottom(scores: Map<String, Double>, nLong: Int, nShort: Int, grossLeverage: Double = 1.0): Map<String, Double> {
        val sorted = scores.entries.sortedByDescending { it.value }
        val longs = sorted.take(nLong).associate { it.key to 1.0 / nLong }
        val shorts = sorted.takeLast(nShort).associate { it.key to -1.0 / nShort }
        val weights = (longs + shorts).mapValues { it.value * grossLeverage }
        return scores.keys.associateWith { weights[it] ?: 0.0 }
    }

    fun zScoreNormalized(scores: Map<String, Double>, grossLeverage: Double = 1.0): Map<String, Double> {
        val mean = scores.values.average()
        val std = kotlin.math.sqrt(scores.values.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(1e-12)
        val z = scores.mapValues { (it.value - mean) / std }
        val sumAbs = z.values.sumOf { abs(it) }
        return if (sumAbs == 0.0) scores.mapValues { 0.0 } else z.mapValues { grossLeverage * it.value / sumAbs }
    }
}
