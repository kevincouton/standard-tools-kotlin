package com.example.starter.screener.application.service

import com.example.starter.indicators.domain.IndicatorCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.application.port.outbound.FundamentalProvider
import com.example.starter.screener.domain.FundamentalData
import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenMatch
import com.example.starter.screener.domain.ScreenResult
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class ScreenerService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val fundamentalProvider: FundamentalProvider,
    private val indicatorCalculator: IndicatorCalculator
) : ScreenStocksUseCase {

    override fun screen(command: ScreenStocksUseCase.ScreenCommand): ScreenResult {
        val matches = mutableListOf<ScreenMatch>()
        val failed = mutableListOf<String>()
        command.tickers.forEach { ticker ->
            try {
                val fundamentals = fundamentalProvider.fetch(ticker)
                if (fundamentals == null) {
                    failed.add(ticker)
                    return@forEach
                }
                val series = fetchMarketDataUseCase.fetch(
                    FetchMarketDataUseCase.FetchMarketDataCommand(
                        ticker = Ticker(ticker),
                        range = command.range,
                        interval = command.interval,
                        provider = command.provider
                    )
                )
                val rsi = command.criteria.rsiMax?.let { _ ->
                    val result = indicatorCalculator.calculate("rsi", series, mapOf("period" to 14))
                    result.values.lastOrNull()?.value?.toDouble()
                }
                val sma = command.criteria.priceAboveSma?.let { period ->
                    val result = indicatorCalculator.calculate("sma", series, mapOf("period" to period))
                    result.values.lastOrNull()?.value?.toDouble()
                }
                val price = series.last().close.toDouble()
                val priceVsSma = sma?.let { (price - it) / it }
                if (passes(fundamentals, command.criteria, rsi, priceVsSma)) {
                    matches.add(ScreenMatch(ticker, fundamentals, rsi, priceVsSma))
                }
            } catch (ex: Exception) {
                failed.add(ticker)
            }
        }
        val sorted = when (command.sortBy) {
            "pe" -> matches.sortedBy { it.fundamentals.peRatio }
            "rsi" -> matches.sortedBy { it.rsi }
            else -> matches.sortedBy { it.ticker }
        }.let { if (command.ascending) it else it.reversed() }
        return ScreenResult(command.criteria, sorted, failed)
    }

    private fun passes(f: FundamentalData, c: ScreenCriteria, rsi: Double?, priceVsSma: Double?): Boolean {
        if (c.peRatioMax != null && (f.peRatio == null || f.peRatio > c.peRatioMax)) return false
        if (c.pbRatioMax != null && (f.pbRatio == null || f.pbRatio > c.pbRatioMax)) return false
        if (c.debtEquityMax != null && (f.debtEquity == null || f.debtEquity > c.debtEquityMax)) return false
        if (c.roeMin != null && (f.roe == null || f.roe < c.roeMin)) return false
        if (c.profitMarginMin != null && (f.profitMargin == null || f.profitMargin < c.profitMarginMin)) return false
        if (c.dividendYieldMin != null && (f.dividendYield == null || f.dividendYield < c.dividendYieldMin)) return false
        if (c.marketCapMin != null && (f.marketCap == null || f.marketCap < c.marketCapMin)) return false
        if (c.betaMax != null && (f.beta == null || f.beta > c.betaMax)) return false
        if (c.betaMin != null && (f.beta == null || f.beta < c.betaMin)) return false
        if (c.rsiMax != null && (rsi == null || rsi > c.rsiMax)) return false
        if (c.rsiMin != null && (rsi == null || rsi < c.rsiMin)) return false
        if (c.priceAboveSma != null && (priceVsSma == null || priceVsSma <= 0)) return false
        if (c.priceBelowSma != null && (priceVsSma == null || priceVsSma >= 0)) return false
        return true
    }
}
