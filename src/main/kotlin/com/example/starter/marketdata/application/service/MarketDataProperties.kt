package com.example.starter.marketdata.application.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "standard-tools.market-data")
data class MarketDataProperties(
    val defaultProvider: String = "yfinance",
    val cacheTtl: Duration = Duration.ofHours(1),
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    data class ProviderConfig(val enabled: Boolean = false, val apiKey: String? = null)

    fun isEnabled(name: String): Boolean = providers[name]?.enabled ?: (name == defaultProvider)
}
