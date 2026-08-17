package com.example.starter.backtest.domain

import com.example.starter.metrics.domain.RiskReturnCalculator
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import java.time.LocalDate

class BacktestEngine(private val riskReturnCalculator: RiskReturnCalculator = RiskReturnCalculator()) {

    companion object {
        const val MAX_BACKTEST_BARS = 50_000
    }

    fun run(
        series: PriceSeries,
        signals: List<Double>,
        initialCapital: Double = 10_000.0,
        commissionPct: Double = 0.001,
        slippagePct: Double = 0.0005,
        strategyName: String = "custom"
    ): BacktestResult {
        require(series.size == signals.size) { "series and signals must align" }
        require(series.size <= MAX_BACKTEST_BARS) {
            "backtest series exceeds maximum of $MAX_BACKTEST_BARS bars"
        }
        var cash = initialCapital
        var position = 0.0
        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<EquityCurvePoint>()
        var peak = initialCapital
        var openTrade: TradeBuilder? = null
        val drawdownEpisodes = mutableListOf<DrawdownEpisode>()
        var currentEpisode: DrawdownEpisodeBuilder? = null

        series.forEachIndexed { idx, bar ->
            val signal = signals[idx].coerceIn(-1.0, 1.0)
            val price = bar.close.toDouble()
            val targetPosition = if (idx == 0) 0.0 else signal * initialCapital / price
            val delta = targetPosition - position
            if (kotlin.math.abs(delta) > 1e-9 && idx > 0) {
                val tradePrice = price * (1.0 + slippagePct * kotlin.math.sign(delta))
                val notional = kotlin.math.abs(delta) * tradePrice
                val commission = notional * commissionPct
                cash -= delta * tradePrice + commission
                position = targetPosition
                val directionSign = if (openTrade?.direction == "long") 1.0 else -1.0
                if (openTrade != null && kotlin.math.sign(directionSign * targetPosition) <= 0) {
                    trades.add(openTrade.close(bar.date, tradePrice))
                    openTrade = null
                }
                if (targetPosition != 0.0 && openTrade == null) {
                    openTrade = TradeBuilder(bar.date, if (targetPosition > 0) "long" else "short", tradePrice, targetPosition)
                }
            }
            val equity = cash + position * price
            val drawdown = (peak - equity) / peak
            if (equity > peak) {
                currentEpisode?.end(bar.date)?.let { drawdownEpisodes.add(it) }
                currentEpisode = null
                peak = equity
            } else if (drawdown > 0) {
                if (currentEpisode == null) currentEpisode = DrawdownEpisodeBuilder(bar.date, bar.date, drawdown)
                else if (drawdown > currentEpisode.depth) currentEpisode = currentEpisode.copy(troughDate = bar.date, depth = drawdown)
            }
            equityCurve.add(EquityCurvePoint(bar.date, equity, drawdown))
        }
        currentEpisode?.let { drawdownEpisodes.add(DrawdownEpisode(it.startDate, it.troughDate, null, it.depth)) }
        val finalEquity = equityCurve.lastOrNull()?.equity ?: initialCapital
        val metrics = if (equityCurve.size >= 2) riskReturnCalculator.riskMetrics(seriesFromEquity(equityCurve)) else null
        val winningTrades = trades.filter { it.pnl > 0 }
        val losingTrades = trades.filter { it.pnl <= 0 }
        val avgWin = if (winningTrades.isEmpty()) 0.0 else winningTrades.map { it.pnl }.average()
        val avgLoss = if (losingTrades.isEmpty()) 0.0 else losingTrades.map { it.pnl }.average()
        val winRateValue = if (trades.isEmpty()) 0.0 else winningTrades.size.toDouble() / trades.size
        val expectancy = winRateValue * avgWin + (1.0 - winRateValue) * avgLoss

        val years = equityCurve.size.toDouble() / 252.0
        val grossTraded = trades.sumOf { kotlin.math.abs(it.size * it.entryPrice) } * 2.0
        val avgCapital = (initialCapital + finalEquity) / 2.0
        val annualizedTurnover = if (years > 0 && avgCapital > 0) grossTraded / avgCapital / years else 0.0

        val diagnostics = BacktestDiagnostics(
            numberOfTrades = trades.size,
            winRate = winRateValue,
            averageTradeReturn = if (trades.isEmpty()) 0.0 else trades.map { it.pnl }.average(),
            expectancy = expectancy,
            maxExposure = trades.maxOfOrNull { kotlin.math.abs(it.size * it.entryPrice) } ?: 0.0,
            annualizedTurnover = annualizedTurnover
        )
        return BacktestResult(
            strategyName = strategyName,
            initialCapital = initialCapital,
            finalEquity = finalEquity,
            totalReturn = (finalEquity - initialCapital) / initialCapital,
            metrics = metrics,
            trades = trades,
            equityCurve = equityCurve,
            drawdownEpisodes = drawdownEpisodes,
            diagnostics = diagnostics
        )
    }

    private fun seriesFromEquity(equityCurve: List<EquityCurvePoint>): PriceSeries {
        return equityCurve.map { point ->
            OHLCV(
                ticker = com.example.starter.shared.domain.Ticker("BACKTEST"),
                date = point.date,
                open = java.math.BigDecimal(point.equity.toString()),
                high = java.math.BigDecimal(point.equity.toString()),
                low = java.math.BigDecimal(point.equity.toString()),
                close = java.math.BigDecimal(point.equity.toString()),
                volume = 0L
            )
        }
    }

    private data class TradeBuilder(
        val entryDate: LocalDate,
        val direction: String,
        val entryPrice: Double,
        val size: Double
    ) {
        fun close(exitDate: LocalDate, exitPrice: Double): Trade {
            val multiplier = if (direction == "long") 1.0 else -1.0
            val pnl = multiplier * size * (exitPrice - entryPrice)
            return Trade(entryDate, exitDate, direction, entryPrice, exitPrice, size, pnl, null, null)
        }
    }

    private data class DrawdownEpisodeBuilder(
        val startDate: LocalDate,
        var troughDate: LocalDate,
        var depth: Double
    ) {
        fun end(endDate: LocalDate): DrawdownEpisode = DrawdownEpisode(startDate, troughDate, endDate, depth)
    }
}
