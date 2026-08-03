package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.springframework.stereotype.Component
import kotlin.math.ln
import kotlin.math.pow

@Component
class HurstCalculator {

    fun calculate(series: PriceSeries, method: String = "dfa", minWindow: Int = 10): HurstResult {
        val values = series.map { it.close.toDouble() }
        require(values.size >= minWindow * 4) { "series too short" }
        val exponent = when (method.lowercase()) {
            "rs" -> rescaledRange(values, minWindow)
            else -> dfa(values, minWindow)
        }
        val regime = when {
            exponent > 0.55 -> "trending"
            exponent < 0.45 -> "mean_reverting"
            else -> "random_walk"
        }
        return HurstResult(exponent = exponent, regime = regime)
    }

    fun rolling(series: PriceSeries, window: Int, step: Int = 1, method: String = "dfa", minWindow: Int = 10): HurstResult {
        val values = series.map { it.close.toDouble() }
        val points = mutableListOf<Map<String, Double>>()
        var start = window
        while (start <= values.size) {
            val slice = values.subList(start - window, start)
            val exponent = when (method.lowercase()) {
                "rs" -> rescaledRange(slice, minWindow)
                else -> dfa(slice, minWindow)
            }
            points.add(mapOf("index" to start.toDouble(), "exponent" to exponent))
            start += step
        }
        return HurstResult(exponent = points.lastOrNull()?.get("exponent") ?: Double.NaN, regime = "rolling", rolling = points)
    }

    private fun dfa(values: List<Double>, minWindow: Int): Double {
        val profile = values.runningFold(0.0) { acc, v -> acc + (v - values.average()) }.drop(1)
        val windows = generateWindows(values.size, minWindow)
        val logWindow = mutableListOf<Double>()
        val logFluct = mutableListOf<Double>()
        windows.forEach { w ->
            val fluct = fluctuation(profile, w)
            if (fluct > 0) {
                logWindow.add(ln(w.toDouble()))
                logFluct.add(ln(fluct))
            }
        }
        val regression = SimpleRegression()
        logWindow.zip(logFluct).forEach { (x, y) -> regression.addData(x, y) }
        return regression.slope
    }

    private fun fluctuation(profile: List<Double>, window: Int): Double {
        val chunks = profile.chunked(window)
        val rms = chunks.map { chunk ->
            val xs = chunk.indices.map { it.toDouble() }
            val reg = SimpleRegression()
            xs.zip(chunk).forEach { (x, y) -> reg.addData(x, y) }
            val trend = xs.map { reg.predict(it) }
            val detrended = chunk.zip(trend).map { (y, t) -> y - t }
            detrended.map { it * it }.average()
        }.average()
        return kotlin.math.sqrt(rms)
    }

    private fun rescaledRange(values: List<Double>, minWindow: Int): Double {
        val windows = generateWindows(values.size, minWindow)
        val logWindow = mutableListOf<Double>()
        val logRs = mutableListOf<Double>()
        windows.forEach { w ->
            val rs = values.chunked(w).map { chunk ->
                val mean = chunk.average()
                val deviations = chunk.runningFold(0.0) { acc, v -> acc + (v - mean) }.drop(1)
                val range = (deviations.maxOrNull() ?: 0.0) - (deviations.minOrNull() ?: 0.0)
                val std = kotlin.math.sqrt(chunk.map { (it - mean) * (it - mean) }.average())
                if (std == 0.0) 0.0 else range / std
            }.average()
            if (rs > 0) {
                logWindow.add(ln(w.toDouble()))
                logRs.add(ln(rs))
            }
        }
        val regression = SimpleRegression()
        logWindow.zip(logRs).forEach { (x, y) -> regression.addData(x, y) }
        return regression.slope
    }

    private fun generateWindows(size: Int, min: Int): List<Int> {
        val windows = mutableListOf<Int>()
        var w = min
        while (w <= size / 4) {
            windows.add(w)
            w = (w * 1.5).toInt().coerceAtLeast(w + 1)
        }
        return windows
    }
}
