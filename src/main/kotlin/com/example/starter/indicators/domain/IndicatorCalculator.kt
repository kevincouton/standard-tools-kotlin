package com.example.starter.indicators.domain

import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class IndicatorCalculator {

    fun calculate(name: String, series: PriceSeries, parameters: Map<String, Any>): IndicatorResult {
        return when (name.lowercase()) {
            "sma" -> sma(series, parameters.intParam("period", 20))
            "ema" -> ema(series, parameters.intParam("period", 20))
            "rsi" -> rsi(series, parameters.intParam("period", 14))
            "macd" -> macd(series, parameters.intParam("fast", 12), parameters.intParam("slow", 26), parameters.intParam("signal", 9))
            "bollinger_bands" -> bollingerBands(series, parameters.intParam("period", 20), parameters.intParam("stdDev", 2))
            "atr" -> atr(series, parameters.intParam("period", 14))
            "obv" -> obv(series)
            "vwap" -> vwap(series)
            else -> throw IllegalArgumentException("Unknown indicator: $name")
        }
    }

    private fun sma(series: PriceSeries, period: Int): IndicatorResult {
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx + 1 < period) null
            else series.subList(idx + 1 - period, idx + 1).map { it.close }.average()
            IndicatorValue(bar.date, value)
        }
        return IndicatorResult("sma", mapOf("period" to period), values)
    }

    private fun ema(series: PriceSeries, period: Int): IndicatorResult {
        val multiplier = 2.0 / (period + 1)
        val values = mutableListOf<IndicatorValue>()
        var ema = series.first().close.toDouble()
        series.forEachIndexed { idx, bar ->
            if (idx == 0) {
                values.add(IndicatorValue(bar.date, bar.close))
            } else {
                ema = (bar.close.toDouble() - ema) * multiplier + ema
                values.add(IndicatorValue(bar.date, BigDecimal(ema).setScale(4, RoundingMode.HALF_UP)))
            }
        }
        return IndicatorResult("ema", mapOf("period" to period), values)
    }

    private fun rsi(series: PriceSeries, period: Int): IndicatorResult {
        val closes = series.map { it.close.toDouble() }
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            gains.add(if (change > 0) change else 0.0)
            losses.add(if (change < 0) -change else 0.0)
        }
        val values = (0 until period).map { IndicatorValue(series[it].date, null) } +
            (period until closes.size).map { idx ->
                val avgGain = gains.subList(idx - period, idx).average()
                val avgLoss = losses.subList(idx - period, idx).average()
                val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
                val rsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1 + rs))
                IndicatorValue(series[idx].date, BigDecimal(rsi).setScale(4, RoundingMode.HALF_UP))
            }
        return IndicatorResult("rsi", mapOf("period" to period), values)
    }

    private fun macd(series: PriceSeries, fast: Int, slow: Int, signal: Int): IndicatorResult {
        val fastEma = emaValues(series, fast)
        val slowEma = emaValues(series, slow)
        val macdLine = fastEma.zip(slowEma).map { (f, s) -> f - s }
        val signalLine = emaOfList(macdLine, signal)
        val histogram = macdLine.zip(signalLine).map { (m, s) -> m - s }
        val values = series.indices.map { i ->
            IndicatorValue(series[i].date, BigDecimal(macdLine[i]).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("macd", mapOf("fast" to fast, "slow" to slow, "signal" to signal), values)
    }

    private fun bollingerBands(series: PriceSeries, period: Int, stdDev: Int): IndicatorResult {
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx + 1 < period) null
            else {
                val window = series.subList(idx + 1 - period, idx + 1).map { it.close.toDouble() }
                val stats = DescriptiveStatistics(window.toDoubleArray())
                val middle = stats.mean
                val upper = middle + stdDev * stats.standardDeviation
                BigDecimal(upper).setScale(4, RoundingMode.HALF_UP)
            }
            IndicatorValue(bar.date, value)
        }
        return IndicatorResult("bollinger_bands_upper", mapOf("period" to period, "stdDev" to stdDev), values)
    }

    private fun atr(series: PriceSeries, period: Int): IndicatorResult {
        val trs = series.mapIndexed { idx, bar ->
            if (idx == 0) bar.high.toDouble() - bar.low.toDouble()
            else {
                val prevClose = series[idx - 1].close.toDouble()
                listOf(
                    bar.high.toDouble() - bar.low.toDouble(),
                    kotlin.math.abs(bar.high.toDouble() - prevClose),
                    kotlin.math.abs(bar.low.toDouble() - prevClose)
                ).maxOrNull()!!
            }
        }
        val values = series.mapIndexed { idx, bar ->
            val value = if (idx < period) null
            else trs.subList(idx - period + 1, idx + 1).average()
            IndicatorValue(bar.date, value?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) })
        }
        return IndicatorResult("atr", mapOf("period" to period), values)
    }

    private fun obv(series: PriceSeries): IndicatorResult {
        var obv = 0.0
        val values = series.mapIndexed { idx, bar ->
            if (idx > 0) {
                val prevClose = series[idx - 1].close.toDouble()
                val change = bar.close.toDouble() - prevClose
                obv += when {
                    change > 0 -> bar.volume.toDouble()
                    change < 0 -> -bar.volume.toDouble()
                    else -> 0.0
                }
            } else {
                obv = 0.0
            }
            IndicatorValue(bar.date, BigDecimal(obv).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("obv", emptyMap(), values)
    }

    private fun vwap(series: PriceSeries): IndicatorResult {
        var cumulativeTypicalVolume = 0.0
        var cumulativeVolume = 0.0
        val values = series.map { bar ->
            val typical = (bar.high.toDouble() + bar.low.toDouble() + bar.close.toDouble()) / 3.0
            cumulativeTypicalVolume += typical * bar.volume
            cumulativeVolume += bar.volume.toDouble()
            val vwap = if (cumulativeVolume == 0.0) 0.0 else cumulativeTypicalVolume / cumulativeVolume
            IndicatorValue(bar.date, BigDecimal(vwap).setScale(4, RoundingMode.HALF_UP))
        }
        return IndicatorResult("vwap", emptyMap(), values)
    }

    private fun emaValues(series: PriceSeries, period: Int): List<Double> {
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = series.first().close.toDouble()
        series.forEachIndexed { idx, bar ->
            if (idx == 0) result.add(ema)
            else {
                ema = (bar.close.toDouble() - ema) * multiplier + ema
                result.add(ema)
            }
        }
        return result
    }

    private fun emaOfList(values: List<Double>, period: Int): List<Double> {
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

    private fun Map<String, Any>.intParam(key: String, default: Int): Int {
        return when (val v = get(key)) {
            is Int -> v
            is Number -> v.toInt()
            is String -> v.toInt()
            else -> default
        }
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return fold(BigDecimal.ZERO) { sum, v -> sum + v }.divide(BigDecimal(size), 4, RoundingMode.HALF_UP)
    }
}
