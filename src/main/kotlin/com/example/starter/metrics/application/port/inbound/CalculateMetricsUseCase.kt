package com.example.starter.metrics.application.port.inbound

import com.example.starter.metrics.domain.ReturnMetrics
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker

interface CalculateMetricsUseCase {
    fun calculateRisk(command: CalculateRiskCommand): RiskMetrics
    fun calculateReturn(command: CalculateReturnCommand): ReturnMetrics

    data class CalculateRiskCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val riskFreeRate: Double = 0.02,
        val provider: String? = null
    )

    data class CalculateReturnCommand(
        val ticker: Ticker,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null
    )
}
