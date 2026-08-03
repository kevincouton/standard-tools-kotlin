package com.example.starter.screener.application.port.inbound

import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenResult
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange

interface ScreenStocksUseCase {
    fun screen(command: ScreenCommand): ScreenResult

    data class ScreenCommand(
        val tickers: List<String>,
        val criteria: ScreenCriteria,
        val range: DateRange,
        val interval: BarInterval,
        val provider: String? = null,
        val sortBy: String? = null,
        val ascending: Boolean = true
    )
}
