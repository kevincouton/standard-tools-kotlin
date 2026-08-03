package com.example.starter.backtest.domain

import com.example.starter.shared.domain.PriceSeries

fun interface Strategy {
    fun generate(series: PriceSeries, parameters: Map<String, Any>): List<Double>
    val name: String get() = "custom"
}
