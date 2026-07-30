package com.example.starter.indicators.application.port.inbound

import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface CalculateIndicatorUseCase {
    fun calculate(command: CalculateIndicatorCommand): IndicatorResult

    data class CalculateIndicatorCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val indicator: String,
        val parameters: Map<String, Any> = emptyMap(),
        val provider: String? = null
    )
}
