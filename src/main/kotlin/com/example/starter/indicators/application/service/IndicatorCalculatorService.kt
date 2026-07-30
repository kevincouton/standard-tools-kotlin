package com.example.starter.indicators.application.service

import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.domain.IndicatorCalculator
import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import org.springframework.stereotype.Service

@Service
class IndicatorCalculatorService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculator: IndicatorCalculator
) : CalculateIndicatorUseCase {

    override fun calculate(command: CalculateIndicatorUseCase.CalculateIndicatorCommand): IndicatorResult {
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = command.ticker,
                range = command.range,
                interval = command.interval,
                provider = command.provider
            )
        )
        return calculator.calculate(command.indicator, series, command.parameters)
    }
}
