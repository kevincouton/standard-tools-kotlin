package com.example.starter.analysis.application.service

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.analysis.domain.AnalysisResult
import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.analysis.domain.CorrelationCalculator
import com.example.starter.analysis.domain.HurstCalculator
import com.example.starter.analysis.domain.MultiFactorCalculator
import com.example.starter.analysis.domain.OptionsCalculator
import com.example.starter.analysis.domain.PcaCalculator
import com.example.starter.analysis.domain.RegressionCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Service

@Service
class AnalysisService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val regressionCalculator: RegressionCalculator,
    private val cointegrationCalculator: CointegrationCalculator,
    private val hurstCalculator: HurstCalculator,
    private val pcaCalculator: PcaCalculator,
    private val correlationCalculator: CorrelationCalculator,
    private val multiFactorCalculator: MultiFactorCalculator,
    private val optionsCalculator: OptionsCalculator
) : RunAnalysisUseCase {

    override fun execute(command: RunAnalysisUseCase.AnalysisCommand): AnalysisResult = when (command) {
        is RunAnalysisUseCase.RegressionCommand -> {
            val asset = fetch(command.asset, command.range, command.interval, command.provider)
            val benchmark = fetch(command.benchmark, command.range, command.interval, command.provider)
            regressionCalculator.calculate(asset, benchmark, command.riskFreeRate)
        }
        is RunAnalysisUseCase.CointegrationCommand -> {
            val a = fetch(command.assetA, command.range, command.interval, command.provider)
            val b = fetch(command.assetB, command.range, command.interval, command.provider)
            cointegrationCalculator.calculate(a, b, command.zScoreWindow)
        }
        is RunAnalysisUseCase.HurstCommand -> {
            val series = fetch(command.ticker, command.range, command.interval, command.provider)
            command.rollingWindow?.let {
                hurstCalculator.rolling(series, it, method = command.method, minWindow = command.minWindow)
            } ?: hurstCalculator.calculate(series, command.method, command.minWindow)
        }
        is RunAnalysisUseCase.PcaCommand -> {
            val series = command.tickers.map { fetch(it, command.range, command.interval, command.provider) }
            pcaCalculator.calculate(command.tickers.map { it.symbol }, series, command.nComponents, command.standardize)
        }
        is RunAnalysisUseCase.CorrelationCommand -> {
            val series = command.tickers.map { fetch(it, command.range, command.interval, command.provider) }
            correlationCalculator.calculate(command.tickers.map { it.symbol }, series, command.weights)
        }
        is RunAnalysisUseCase.MultiFactorCommand -> {
            val asset = fetch(command.asset, command.range, command.interval, command.provider)
            val factors = command.factors.mapValues { fetch(it.value, command.range, command.interval, command.provider) }
            multiFactorCalculator.calculate(asset, factors)
        }
        is RunAnalysisUseCase.OptionPricingCommand -> optionsCalculator.calculate(command)
    }

    private fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
