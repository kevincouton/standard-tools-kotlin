package com.example.starter.metrics.application.service

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.domain.ReturnMetrics
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.metrics.domain.RiskReturnCalculator
import org.springframework.stereotype.Service

@Service
class MetricsService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculator: RiskReturnCalculator
) : CalculateMetricsUseCase {

    override fun calculateRisk(command: CalculateMetricsUseCase.CalculateRiskCommand): RiskMetrics {
        val series = fetchSeries(command.ticker, command.range, command.interval, command.provider)
        return calculator.riskMetrics(series, command.riskFreeRate)
    }

    override fun calculateReturn(command: CalculateMetricsUseCase.CalculateReturnCommand): ReturnMetrics {
        val series = fetchSeries(command.ticker, command.range, command.interval, command.provider)
        return calculator.returnMetrics(series)
    }

    private fun fetchSeries(ticker: com.example.starter.shared.domain.Ticker, range: com.example.starter.shared.domain.DateRange, interval: com.example.starter.shared.domain.BarInterval, provider: String?) =
        fetchMarketDataUseCase.fetch(FetchMarketDataUseCase.FetchMarketDataCommand(ticker, range, interval, provider))
}
