package com.example.starter.indicators.domain

import java.math.BigDecimal
import java.time.LocalDate

data class IndicatorValue(
    val date: LocalDate,
    val value: BigDecimal?
)

data class IndicatorResult(
    val indicator: String,
    val parameters: Map<String, Any>,
    val values: List<IndicatorValue>
)
