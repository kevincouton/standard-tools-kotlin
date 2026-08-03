package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import kotlin.math.max

object Strategies {

    val REGISTRY: Map<String, Strategy> = mapOf(
        "sma_crossover" to Strategy { series, params -> smaCrossover(series, intParam(params, "fast", 10), intParam(params, "slow", 30)) },
        "rsi_mean_reversion" to Strategy { series, params -> rsiMeanReversion(series, intParam(params, "period", 14), dblParam(params, "oversold", 30.0), dblParam(params, "overbought", 70.0)) },
        "macd_crossover" to Strategy { series, params -> macdCrossover(series, intParam(params, "fast", 12), intParam(params, "slow", 26), intParam(params, "signal", 9)) },
        "bollinger_reversion" to Strategy { series, params -> bollingerReversion(series, intParam(params, "period", 20), dblParam(params, "stdDev", 2.0)) },
        "donchian_breakout" to Strategy { series, params -> donchianBreakout(series, intParam(params, "period", 20)) },
        "momentum_timeseries" to Strategy { series, params -> momentum(series, intParam(params, "period", 20)) },
        "vwap_reversion" to Strategy { series, params -> vwapReversion(series) },
        "buy_and_hold" to Strategy { series, params -> List(series.size) { 1.0 } }
    )

    private fun smaCrossover(series: PriceSeries, fast: Int, slow: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, _ ->
            if (idx < slow) 0.0
            else {
                val fastSma = closes.subList(idx - fast + 1, idx + 1).average()
                val slowSma = closes.subList(idx - slow + 1, idx + 1).average()
                when {
                    fastSma > slowSma -> 1.0
                    fastSma < slowSma -> -1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun rsiMeanReversion(series: PriceSeries, period: Int, oversold: Double, overbought: Double): List<Double> {
        val closes = series.map { it.close.toDouble() }
        val rsi = rsi(closes, period)
        return rsi.map { when { it < oversold -> 1.0; it > overbought -> -1.0; else -> 0.0 } }
    }

    private fun rsi(closes: List<Double>, period: Int): List<Double> {
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            gains.add(max(change, 0.0))
            losses.add(max(-change, 0.0))
        }
        return closes.take(1).map { 50.0 } + (period until closes.size).map { idx ->
            val avgGain = gains.subList(idx - period, idx).average()
            val avgLoss = losses.subList(idx - period, idx).average()
            if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
    }

    private fun macdCrossover(series: PriceSeries, fast: Int, slow: Int, signal: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        val fastEma = ema(closes, fast)
        val slowEma = ema(closes, slow)
        val macdLine = fastEma.zip(slowEma).map { (f, s) -> f - s }
        val signalLine = ema(macdLine, signal)
        return macdLine.zip(signalLine).map { (m, s) -> when { m > s -> 1.0; m < s -> -1.0; else -> 0.0 } }
    }

    private fun bollingerReversion(series: PriceSeries, period: Int, stdDev: Double): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, close ->
            if (idx + 1 < period) 0.0
            else {
                val window = closes.subList(idx + 1 - period, idx + 1)
                val stats = DescriptiveStatistics(window.toDoubleArray())
                val upper = stats.mean + stdDev * stats.standardDeviation
                val lower = stats.mean - stdDev * stats.standardDeviation
                when {
                    close > upper -> -1.0
                    close < lower -> 1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun donchianBreakout(series: PriceSeries, period: Int): List<Double> {
        val highs = series.map { it.high.toDouble() }
        val lows = series.map { it.low.toDouble() }
        return series.mapIndexed { idx, bar ->
            if (idx < period) 0.0
            else {
                val upper = highs.subList(idx - period, idx).maxOrNull() ?: bar.high.toDouble()
                val lower = lows.subList(idx - period, idx).minOrNull() ?: bar.low.toDouble()
                when {
                    bar.close.toDouble() > upper -> 1.0
                    bar.close.toDouble() < lower -> -1.0
                    else -> 0.0
                }
            }
        }
    }

    private fun momentum(series: PriceSeries, period: Int): List<Double> {
        val closes = series.map { it.close.toDouble() }
        return closes.mapIndexed { idx, close ->
            if (idx < period) 0.0
            else if (close > closes[idx - period]) 1.0 else -1.0
        }
    }

    private fun vwapReversion(series: PriceSeries): List<Double> {
        var cumTypVol = 0.0
        var cumVol = 0.0
        return series.map { bar ->
            val typical = (bar.high.toDouble() + bar.low.toDouble() + bar.close.toDouble()) / 3.0
            cumTypVol += typical * bar.volume
            cumVol += bar.volume
            val vwap = if (cumVol == 0.0) typical else cumTypVol / cumVol
            when {
                bar.close.toDouble() > vwap -> -1.0
                bar.close.toDouble() < vwap -> 1.0
                else -> 0.0
            }
        }
    }

    private fun ema(values: List<Double>, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = values.first()
        values.forEachIndexed { idx, v ->
            if (idx == 0) result.add(ema)
            else {
                ema = (v - ema) * multiplier + ema
                result.add(ema)
            }
        }
        return result
    }

    private fun intParam(params: Map<String, Any>, key: String, default: Int): Int = when (val v = params[key]) {
        is Int -> v
        is Number -> v.toInt()
        is String -> v.toInt()
        else -> default
    }

    private fun dblParam(params: Map<String, Any>, key: String, default: Double): Double = when (val v = params[key]) {
        is Double -> v
        is Number -> v.toDouble()
        is String -> v.toDouble()
        else -> default
    }
}
