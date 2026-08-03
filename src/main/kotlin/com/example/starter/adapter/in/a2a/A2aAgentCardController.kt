package com.example.starter.adapter.`in`.a2a

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class A2aAgentCardController {

    @GetMapping("/.well-known/agent.json", produces = ["application/json"])
    fun agentCard(): Map<String, Any> = mapOf(
        "name" to "Quant Agent",
        "description" to "Agent that exposes order management, market data, indicators, metrics, analysis, backtest, portfolio, and screening tools via REST, gRPC, A2A, and MCP",
        "url" to "http://localhost:8080/a2a",
        "version" to "2.0.0",
        "capabilities" to mapOf(
            "streaming" to false,
            "pushNotifications" to false
        ),
        "skills" to listOf(
            mapOf(
                "id" to "create-order",
                "name" to "Create Order",
                "description" to "Create a new order for a customer",
                "tags" to listOf("orders"),
                "examples" to listOf("Create an order for customer C1 with item P1 quantity 2")
            ),
            mapOf(
                "id" to "get-order",
                "name" to "Get Order",
                "description" to "Retrieve an order by id",
                "tags" to listOf("orders"),
                "examples" to listOf("Get order 123e4567-e89b-12d3-a456-426614174000")
            ),
            mapOf(
                "id" to "cancel-order",
                "name" to "Cancel Order",
                "description" to "Cancel an existing order",
                "tags" to listOf("orders"),
                "examples" to listOf("Cancel order 123e4567-e89b-12d3-a456-426614174000")
            ),
            mapOf(
                "id" to "marketdata-fetch",
                "name" to "Fetch Market Data",
                "description" to "Fetch OHLCV bars for a ticker",
                "tags" to listOf("market-data")
            ),
            mapOf(
                "id" to "indicators-calculate",
                "name" to "Calculate Indicator",
                "description" to "Calculate a technical indicator for a ticker",
                "tags" to listOf("indicators")
            ),
            mapOf(
                "id" to "metrics-risk",
                "name" to "Calculate Risk Metrics",
                "description" to "Calculate risk metrics for a ticker",
                "tags" to listOf("metrics")
            ),
            mapOf(
                "id" to "metrics-return",
                "name" to "Calculate Return Metrics",
                "description" to "Calculate return metrics for a ticker",
                "tags" to listOf("metrics")
            ),
            mapOf(
                "id" to "analysis-regression",
                "name" to "Regression Analysis",
                "description" to "Run regression analysis for an asset against a benchmark",
                "tags" to listOf("analysis")
            ),
            mapOf(
                "id" to "analysis-cointegration",
                "name" to "Cointegration Test",
                "description" to "Run cointegration analysis between two assets",
                "tags" to listOf("analysis")
            ),
            mapOf(
                "id" to "backtest-single",
                "name" to "Run Backtest",
                "description" to "Run a single-asset strategy backtest",
                "tags" to listOf("backtest")
            ),
            mapOf(
                "id" to "portfolio-optimize",
                "name" to "Optimize Portfolio",
                "description" to "Run mean-variance portfolio optimization",
                "tags" to listOf("portfolio")
            ),
            mapOf(
                "id" to "screener-run",
                "name" to "Run Screener",
                "description" to "Run a stock screen over a universe of tickers",
                "tags" to listOf("screener")
            )
        )
    )
}
