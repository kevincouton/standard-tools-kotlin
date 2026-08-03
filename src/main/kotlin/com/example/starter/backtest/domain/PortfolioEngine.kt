package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskReturnCalculator
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import java.math.BigDecimal
import java.time.LocalDate

class PortfolioEngine(private val riskReturnCalculator: RiskReturnCalculator = RiskReturnCalculator()) {

    fun runPortfolioSimulation(
        priceData: Map<String, PriceSeries>,
        targetWeights: Map<String, Double>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001,
        slippagePct: Double = 0.0005,
        maxGrossLeverage: Double = 1.0
    ): BacktestResult {
        require(priceData.isNotEmpty())
        val dates = priceData.values.first().map { it.date }
        val symbols = priceData.keys.toList()
        val prices = symbols.map { s -> priceData.getValue(s).map { it.close.toDouble() } }
        val normalizedWeights = normalize(targetWeights, maxGrossLeverage)
        var cash = initialCapital
        val positions = symbols.associateWith { 0.0 }.toMutableMap()
        val equityCurve = mutableListOf<EquityCurvePoint>()
        val trades = mutableListOf<Trade>()
        var peak = initialCapital
        val drawdownEpisodes = mutableListOf<DrawdownEpisode>()
        var currentEpisode: DrawdownEpisodeBuilder? = null

        dates.forEachIndexed { idx, date ->
            val currentPrices = symbols.zip(prices.map { it[idx] }).toMap()
            val equity = cash + symbols.sumOf { positions.getValue(it) * currentPrices.getValue(it) }
            val targetDollar = normalizedWeights.mapValues { equity * it.value }
            symbols.forEach { symbol ->
                val price = currentPrices.getValue(symbol)
                val targetShares = if (price == 0.0) 0.0 else targetDollar.getValue(symbol) / price
                val delta = targetShares - positions.getValue(symbol)
                if (kotlin.math.abs(delta) > 1e-9 && idx > 0) {
                    val fillPrice = price * (1.0 + slippagePct * kotlin.math.sign(delta))
                    val notional = kotlin.math.abs(delta) * fillPrice
                    cash -= delta * fillPrice + notional * commissionPct
                    positions[symbol] = targetShares
                    trades.add(
                        Trade(
                            entryDate = date,
                            exitDate = null,
                            direction = if (delta > 0) "long" else "short",
                            entryPrice = fillPrice,
                            exitPrice = null,
                            size = kotlin.math.abs(delta),
                            pnl = 0.0,
                            mae = null,
                            mfe = null
                        )
                    )
                }
            }
            val totalEquity = cash + symbols.sumOf { positions.getValue(it) * currentPrices.getValue(it) }
            val drawdown = (peak - totalEquity) / peak
            if (totalEquity > peak) {
                currentEpisode?.end(date)?.let { drawdownEpisodes.add(it) }
                currentEpisode = null
                peak = totalEquity
            } else if (drawdown > 0) {
                if (currentEpisode == null) currentEpisode = DrawdownEpisodeBuilder(date, date, drawdown)
                else if (drawdown > currentEpisode.depth) currentEpisode = currentEpisode.copy(troughDate = date, depth = drawdown)
            }
            equityCurve.add(EquityCurvePoint(date, totalEquity, drawdown))
        }
        currentEpisode?.let { drawdownEpisodes.add(DrawdownEpisode(it.startDate, it.troughDate, null, it.depth)) }
        val metrics = riskReturnCalculator.riskMetrics(seriesFromEquity(equityCurve))
        return BacktestResult(
            strategyName = "portfolio_simulation",
            initialCapital = initialCapital,
            finalEquity = equityCurve.last().equity,
            totalReturn = (equityCurve.last().equity - initialCapital) / initialCapital,
            metrics = metrics,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = drawdownEpisodes,
            diagnostics = null
        )
    }

    private fun normalize(weights: Map<String, Double>, maxGrossLeverage: Double): Map<String, Double> {
        val gross = weights.values.sumOf { kotlin.math.abs(it) }
        val scale = if (gross == 0.0) 1.0 else (maxGrossLeverage / gross).coerceAtMost(1.0)
        return weights.mapValues { it.value * scale }
    }

    private fun seriesFromEquity(equityCurve: List<EquityCurvePoint>): PriceSeries = equityCurve.map { point ->
        OHLCV(
            ticker = com.example.starter.shared.domain.Ticker("PORTFOLIO"),
            date = point.date,
            open = BigDecimal(point.equity.toString()),
            high = BigDecimal(point.equity.toString()),
            low = BigDecimal(point.equity.toString()),
            close = BigDecimal(point.equity.toString()),
            volume = 0L
        )
    }

    private data class DrawdownEpisodeBuilder(
        val startDate: LocalDate,
        var troughDate: LocalDate,
        var depth: Double
    ) {
        fun end(endDate: LocalDate): DrawdownEpisode = DrawdownEpisode(startDate, troughDate, endDate, depth)
    }
}
