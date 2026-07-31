package com.example.starter.adapter.`in`.mcp

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Component
class McpToolHandler(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase,
    private val calculateMetricsUseCase: CalculateMetricsUseCase
) {

    fun toolsList(): Map<String, Any> = mapOf(
        "tools" to listOf(
            mapOf(
                "name" to "create_order",
                "description" to "Create a new order",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "customerId" to mapOf("type" to "string"),
                        "items" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "productId" to mapOf("type" to "string"),
                                    "quantity" to mapOf("type" to "integer"),
                                    "unitPrice" to mapOf("type" to "string")
                                ),
                                "required" to listOf("productId", "quantity", "unitPrice")
                            )
                        )
                    ),
                    "required" to listOf("customerId", "items")
                )
            ),
            mapOf(
                "name" to "get_order",
                "description" to "Get an order by id",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("orderId" to mapOf("type" to "string")),
                    "required" to listOf("orderId")
                )
            ),
            mapOf(
                "name" to "cancel_order",
                "description" to "Cancel an order by id",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("orderId" to mapOf("type" to "string")),
                    "required" to listOf("orderId")
                )
            ),
            mapOf(
                "name" to "marketdata_fetch",
                "description" to "Fetch OHLCV bars for a ticker",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "exchange" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbol", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "indicators_calculate",
                "description" to "Calculate a technical indicator for a ticker",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "exchange" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "indicator" to mapOf("type" to "string"),
                        "parameters" to mapOf(
                            "type" to "object",
                            "additionalProperties" to mapOf("type" to "string")
                        ),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbol", "startDate", "endDate", "interval", "indicator")
                )
            ),
            mapOf(
                "name" to "metrics_risk",
                "description" to "Calculate risk metrics for a ticker",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "exchange" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "riskFreeRate" to mapOf("type" to "number"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbol", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "metrics_return",
                "description" to "Calculate return metrics for a ticker",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "exchange" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbol", "startDate", "endDate", "interval")
                )
            )
        )
    )

    fun handleToolCall(name: String, arguments: Map<String, Any>): Map<String, Any> {
        return when (name) {
            "create_order" -> {
                val customerId = arguments["customerId"] as? String
                    ?: throw IllegalArgumentException("customerId required")
                val items = parseItems(arguments["items"])
                val order = createOrderUseCase.createOrder(CreateOrderUseCase.CreateOrderCommand(customerId, items))
                toolResult(McpOrderToolMapper.toText(order))
            }
            "get_order" -> {
                val orderId = arguments["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                val order = getOrderUseCase.getOrder(UUID.fromString(orderId))
                toolResult(McpOrderToolMapper.toText(order))
            }
            "cancel_order" -> {
                val orderId = arguments["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                val order = cancelOrderUseCase.cancelOrder(UUID.fromString(orderId))
                toolResult(McpOrderToolMapper.toText(order))
            }
            "marketdata_fetch" -> handleMarketDataFetch(arguments)
            "indicators_calculate" -> handleIndicatorsCalculate(arguments)
            "metrics_risk" -> handleMetricsRisk(arguments)
            "metrics_return" -> handleMetricsReturn(arguments)
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
    }

    private fun toolResult(text: String): Map<String, Any> = mapOf(
        "content" to listOf(
            mapOf(
                "type" to "text",
                "text" to text
            )
        )
    )

    private fun handleMarketDataFetch(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw InvalidCommandException("symbol required")
        if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
        val exchange = arguments["exchange"] as? String
        val startDate = arguments["startDate"] as? String ?: throw InvalidCommandException("startDate required")
        val endDate = arguments["endDate"] as? String ?: throw InvalidCommandException("endDate required")
        val interval = arguments["interval"] as? String ?: "DAILY"
        val provider = arguments["provider"] as? String
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = Ticker(symbol, exchange),
                range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                interval = parseInterval(interval),
                provider = provider
            )
        )
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to "Fetched ${series.size} bars for $symbol"
                )
            )
        )
    }

    private fun handleIndicatorsCalculate(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw InvalidCommandException("symbol required")
        if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
        val exchange = arguments["exchange"] as? String
        val startDate = arguments["startDate"] as? String ?: throw InvalidCommandException("startDate required")
        val endDate = arguments["endDate"] as? String ?: throw InvalidCommandException("endDate required")
        val interval = arguments["interval"] as? String ?: "DAILY"
        val indicator = arguments["indicator"] as? String ?: throw InvalidCommandException("indicator required")
        val parameters = (arguments["parameters"] as? Map<String, Any>) ?: emptyMap()
        val provider = arguments["provider"] as? String
        val result = calculateIndicatorUseCase.calculate(
            CalculateIndicatorUseCase.CalculateIndicatorCommand(
                ticker = Ticker(symbol, exchange),
                range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                interval = parseInterval(interval),
                indicator = indicator,
                parameters = parameters,
                provider = provider
            )
        )
        return toolResult("Calculated ${result.indicator}: ${result.values.size} values")
    }

    private fun handleMetricsRisk(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw InvalidCommandException("symbol required")
        if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
        val exchange = arguments["exchange"] as? String
        val startDate = arguments["startDate"] as? String ?: throw InvalidCommandException("startDate required")
        val endDate = arguments["endDate"] as? String ?: throw InvalidCommandException("endDate required")
        val interval = arguments["interval"] as? String ?: "DAILY"
        val riskFreeRate = (arguments["riskFreeRate"] as? Number)?.toDouble() ?: 0.02
        val provider = arguments["provider"] as? String
        val result = calculateMetricsUseCase.calculateRisk(
            CalculateMetricsUseCase.CalculateRiskCommand(
                ticker = Ticker(symbol, exchange),
                range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                interval = parseInterval(interval),
                riskFreeRate = riskFreeRate,
                provider = provider
            )
        )
        return toolResult(
            "Risk metrics for $symbol: max drawdown ${result.maxDrawdown}, volatility ${result.volatility}, " +
                "Sharpe ${result.sharpeRatio ?: "n/a"}, Sortino ${result.sortinoRatio ?: "n/a"}"
        )
    }

    private fun handleMetricsReturn(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw InvalidCommandException("symbol required")
        if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
        val exchange = arguments["exchange"] as? String
        val startDate = arguments["startDate"] as? String ?: throw InvalidCommandException("startDate required")
        val endDate = arguments["endDate"] as? String ?: throw InvalidCommandException("endDate required")
        val interval = arguments["interval"] as? String ?: "DAILY"
        val provider = arguments["provider"] as? String
        val result = calculateMetricsUseCase.calculateReturn(
            CalculateMetricsUseCase.CalculateReturnCommand(
                ticker = Ticker(symbol, exchange),
                range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                interval = parseInterval(interval),
                provider = provider
            )
        )
        return toolResult(
            "Return metrics for $symbol: cumulative ${result.cumulativeReturn}, " +
                "CAGR ${result.cagr ?: "n/a"}, ann. volatility ${result.annualizedVolatility}"
        )
    }

    private fun parseInterval(interval: String): BarInterval {
        return BarInterval.entries.find { it.name.equals(interval.trim(), ignoreCase = true) }
            ?: throw InvalidCommandException(
                "interval must be one of ${BarInterval.entries.joinToString { it.name }}"
            )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseItems(raw: Any?): List<OrderItem> {
        val list = raw as? List<Map<String, Any>> ?: throw IllegalArgumentException("items required")
        return list.map {
            OrderItem(
                productId = it["productId"] as? String ?: throw IllegalArgumentException("productId required"),
                quantity = (it["quantity"] as? Number)?.toInt() ?: throw IllegalArgumentException("quantity required"),
                unitPrice = BigDecimal(it["unitPrice"] as? String ?: throw IllegalArgumentException("unitPrice required"))
            )
        }
    }
}
