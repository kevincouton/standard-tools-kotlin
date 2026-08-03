package com.example.starter.adapter.`in`.mcp

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
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
    private val calculateMetricsUseCase: CalculateMetricsUseCase,
    private val runAnalysisUseCase: RunAnalysisUseCase
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
            ),
            mapOf(
                "name" to "analysis_regression",
                "description" to "Run regression analysis for an asset against a benchmark",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "asset" to mapOf("type" to "string"),
                        "benchmark" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("asset", "benchmark", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_cointegration",
                "description" to "Run cointegration analysis between two assets",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "assetA" to mapOf("type" to "string"),
                        "assetB" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "zScoreWindow" to mapOf("type" to "integer"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("assetA", "assetB", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_hurst",
                "description" to "Estimate Hurst exponent for a price series",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "method" to mapOf("type" to "string"),
                        "rollingWindow" to mapOf("type" to "integer"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbol", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_pca",
                "description" to "Run principal component analysis on a list of assets",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "nComponents" to mapOf("type" to "integer"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbols", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_correlation",
                "description" to "Compute correlation matrix for a list of assets",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbols", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_multi_factor",
                "description" to "Run multi-factor regression for an asset",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "asset" to mapOf("type" to "string"),
                        "factors" to mapOf("type" to "object", "additionalProperties" to mapOf("type" to "string")),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("asset", "factors", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "analysis_option",
                "description" to "Price an option using Black-Scholes and compute Greeks",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "spot" to mapOf("type" to "number"),
                        "strike" to mapOf("type" to "number"),
                        "timeToExpiry" to mapOf("type" to "number"),
                        "riskFreeRate" to mapOf("type" to "number"),
                        "volatility" to mapOf("type" to "number"),
                        "optionType" to mapOf("type" to "string"),
                        "dividendYield" to mapOf("type" to "number"),
                        "marketPrice" to mapOf("type" to "number")
                    ),
                    "required" to listOf("spot", "strike", "timeToExpiry", "riskFreeRate", "volatility")
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
            "analysis_regression" -> handleAnalysisRegression(arguments)
            "analysis_cointegration" -> handleAnalysisCointegration(arguments)
            "analysis_hurst" -> handleAnalysisHurst(arguments)
            "analysis_pca" -> handleAnalysisPca(arguments)
            "analysis_correlation" -> handleAnalysisCorrelation(arguments)
            "analysis_multi_factor" -> handleAnalysisMultiFactor(arguments)
            "analysis_option" -> handleAnalysisOption(arguments)
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

    private fun handleAnalysisRegression(arguments: Map<String, Any>): Map<String, Any> {
        val asset = arguments["asset"] as? String ?: throw IllegalArgumentException("asset required")
        val benchmark = arguments["benchmark"] as? String ?: throw IllegalArgumentException("benchmark required")
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.RegressionCommand(
                asset = Ticker(asset),
                benchmark = Ticker(benchmark),
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisCointegration(arguments: Map<String, Any>): Map<String, Any> {
        val assetA = arguments["assetA"] as? String ?: throw IllegalArgumentException("assetA required")
        val assetB = arguments["assetB"] as? String ?: throw IllegalArgumentException("assetB required")
        val zScoreWindow = (arguments["zScoreWindow"] as? Number)?.toInt() ?: 30
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CointegrationCommand(
                assetA = Ticker(assetA),
                assetB = Ticker(assetB),
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                zScoreWindow = zScoreWindow
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisHurst(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
        val method = arguments["method"] as? String ?: "dfa"
        val rollingWindow = (arguments["rollingWindow"] as? Number)?.toInt()
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.HurstCommand(
                ticker = Ticker(symbol),
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                method = method,
                rollingWindow = rollingWindow
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisPca(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        val nComponents = (arguments["nComponents"] as? Number)?.toInt()
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.PcaCommand(
                tickers = symbols.map { Ticker(it) },
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                nComponents = nComponents
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisCorrelation(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.CorrelationCommand(
                tickers = symbols.map { Ticker(it) },
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisMultiFactor(arguments: Map<String, Any>): Map<String, Any> {
        val asset = arguments["asset"] as? String ?: throw IllegalArgumentException("asset required")
        val factors = arguments["factors"] as? Map<String, String> ?: throw IllegalArgumentException("factors required")
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.MultiFactorCommand(
                asset = Ticker(asset),
                factors = factors.mapValues { Ticker(it.value) },
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String
            )
        )
        return toolResult(result.toString())
    }

    private fun handleAnalysisOption(arguments: Map<String, Any>): Map<String, Any> {
        val spot = (arguments["spot"] as? Number)?.toDouble() ?: throw IllegalArgumentException("spot required")
        val strike = (arguments["strike"] as? Number)?.toDouble() ?: throw IllegalArgumentException("strike required")
        val timeToExpiry = (arguments["timeToExpiry"] as? Number)?.toDouble() ?: throw IllegalArgumentException("timeToExpiry required")
        val riskFreeRate = (arguments["riskFreeRate"] as? Number)?.toDouble() ?: 0.05
        val volatility = (arguments["volatility"] as? Number)?.toDouble() ?: throw IllegalArgumentException("volatility required")
        val optionType = arguments["optionType"] as? String ?: "call"
        val dividendYield = (arguments["dividendYield"] as? Number)?.toDouble() ?: 0.0
        val marketPrice = (arguments["marketPrice"] as? Number)?.toDouble()
        val result = runAnalysisUseCase.execute(
            RunAnalysisUseCase.OptionPricingCommand(
                spot = spot,
                strike = strike,
                timeToExpiry = timeToExpiry,
                riskFreeRate = riskFreeRate,
                volatility = volatility,
                optionType = optionType,
                dividendYield = dividendYield,
                marketPrice = marketPrice
            )
        )
        return toolResult(result.toString())
    }

    private fun parseRange(arguments: Map<String, Any>): DateRange {
        val start = arguments["startDate"] as? String ?: throw IllegalArgumentException("startDate required")
        val end = arguments["endDate"] as? String ?: throw IllegalArgumentException("endDate required")
        return DateRange(LocalDate.parse(start), LocalDate.parse(end))
    }

    private fun parseInterval(arguments: Map<String, Any>): BarInterval {
        val interval = arguments["interval"] as? String ?: "DAILY"
        return parseInterval(interval)
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
