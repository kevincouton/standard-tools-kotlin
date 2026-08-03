package com.example.starter.portfolio.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.portfolio.domain.BlackLittermanOptimizer
import com.example.starter.portfolio.domain.MeanVarianceOptimizer
import com.example.starter.portfolio.domain.Portfolio
import com.example.starter.portfolio.domain.RiskParityOptimizer
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class PortfolioService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val meanVarianceOptimizer: MeanVarianceOptimizer = MeanVarianceOptimizer(),
    private val riskParityOptimizer: RiskParityOptimizer = RiskParityOptimizer(),
    private val blackLittermanOptimizer: BlackLittermanOptimizer = BlackLittermanOptimizer()
) : OptimizePortfolioUseCase {

    override fun optimize(command: OptimizePortfolioUseCase.OptimizeCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        return meanVarianceOptimizer.optimize(
            returns = returns,
            tickers = command.tickers.map { it.symbol },
            objective = command.objective,
            riskFreeRate = command.riskFreeRate,
            targetReturn = command.targetReturn,
            targetVolatility = command.targetVolatility,
            allowShort = command.allowShort,
            maxWeight = command.maxWeight
        )
    }

    override fun riskParity(command: OptimizePortfolioUseCase.RiskParityCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        return riskParityOptimizer.optimize(returns, command.tickers.map { it.symbol }, command.riskBudget)
    }

    override fun blackLitterman(command: OptimizePortfolioUseCase.BlackLittermanCommand): Portfolio {
        val returns = command.tickers.map { simpleReturns(fetch(it, command.range, command.interval, command.provider)) }
        val marketWeights = command.tickers.map { command.marketWeights[it.symbol] ?: 0.0 }.toDoubleArray()
        val pMatrix = command.views.views.map { view ->
            command.tickers.map { ticker ->
                when {
                    view.asset == ticker.symbol && view.relativeAsset == null -> 1.0
                    view.asset == ticker.symbol && view.relativeAsset != null -> 1.0
                    view.relativeAsset == ticker.symbol -> -1.0
                    else -> 0.0
                }
            }.toDoubleArray()
        }.toTypedArray()
        val qVector = command.views.views.map { it.returnView }.toDoubleArray()
        return blackLittermanOptimizer.optimize(
            returns = returns,
            tickers = command.tickers.map { it.symbol },
            marketWeights = marketWeights,
            pMatrix = pMatrix,
            qVector = qVector,
            riskAversion = command.riskAversion,
            tau = command.tau
        )
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))

    private fun simpleReturns(series: List<OHLCV>): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
