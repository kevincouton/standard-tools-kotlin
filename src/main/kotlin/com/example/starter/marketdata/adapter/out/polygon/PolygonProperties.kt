package com.example.starter.marketdata.adapter.out.polygon

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "standard-tools.market-data.providers.polygon")
data class PolygonProperties(val apiKey: String = "")
