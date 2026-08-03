package com.example.starter.adapter.`in`.mcp

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.domain.ScreenCriteria
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
    private val runAnalysisUseCase: RunAnalysisUseCase,
    private val runBacktestUseCase: RunBacktestUseCase,
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase,
    private val screenStocksUseCase: ScreenStocksUseCase
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
            ),
            mapOf(
                "name" to "backtest_single",
                "description" to "Run a single-asset strategy backtest",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "strategy" to mapOf("type" to "string"),
                        "parameters" to mapOf("type" to "object"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "initialCapital" to mapOf("type" to "number"),
                        "commissionPct" to mapOf("type" to "number"),
                        "slippagePct" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbol", "strategy", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "backtest_portfolio",
                "description" to "Run a buy-and-hold portfolio backtest",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "weights" to mapOf("type" to "object"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "initialCapital" to mapOf("type" to "number"),
                        "commissionPct" to mapOf("type" to "number"),
                        "maxGrossLeverage" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbols", "weights", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "backtest_pair",
                "description" to "Run a mean-reversion pair-trade backtest",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbolA" to mapOf("type" to "string"),
                        "symbolB" to mapOf("type" to "string"),
                        "entryZ" to mapOf("type" to "number"),
                        "exitZ" to mapOf("type" to "number"),
                        "zScoreWindow" to mapOf("type" to "integer"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "initialCapital" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbolA", "symbolB", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "backtest_walk_forward",
                "description" to "Run a walk-forward optimization backtest",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "strategy" to mapOf("type" to "string"),
                        "parameterGrid" to mapOf("type" to "object"),
                        "trainSize" to mapOf("type" to "integer"),
                        "testSize" to mapOf("type" to "integer"),
                        "metric" to mapOf("type" to "string"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "initialCapital" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbol", "strategy", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "backtest_monte_carlo",
                "description" to "Run a Monte Carlo robustness backtest",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbol" to mapOf("type" to "string"),
                        "strategy" to mapOf("type" to "string"),
                        "parameters" to mapOf("type" to "object"),
                        "horizonDays" to mapOf("type" to "integer"),
                        "nSimulations" to mapOf("type" to "integer"),
                        "blockSize" to mapOf("type" to "integer"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "initialCapital" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbol", "strategy", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "portfolio_optimize",
                "description" to "Run mean-variance portfolio optimization",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "objective" to mapOf("type" to "string"),
                        "riskFreeRate" to mapOf("type" to "number"),
                        "targetReturn" to mapOf("type" to "number"),
                        "targetVolatility" to mapOf("type" to "number"),
                        "allowShort" to mapOf("type" to "boolean"),
                        "maxWeight" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbols", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "portfolio_risk_parity",
                "description" to "Run risk parity portfolio optimization",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "riskBudget" to mapOf("type" to "object"),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string")
                    ),
                    "required" to listOf("symbols", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "portfolio_black_litterman",
                "description" to "Run Black-Litterman portfolio optimization",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "symbols" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "marketWeights" to mapOf("type" to "object"),
                        "views" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "asset" to mapOf("type" to "string"),
                                    "relativeAsset" to mapOf("type" to "string"),
                                    "returnView" to mapOf("type" to "number")
                                ),
                                "required" to listOf("returnView")
                            )
                        ),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "riskAversion" to mapOf("type" to "number"),
                        "tau" to mapOf("type" to "number")
                    ),
                    "required" to listOf("symbols", "marketWeights", "views", "startDate", "endDate", "interval")
                )
            ),
            mapOf(
                "name" to "screener_run",
                "description" to "Run a fundamental/technical stock screen over a universe of tickers",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "tickers" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "startDate" to mapOf("type" to "string"),
                        "endDate" to mapOf("type" to "string"),
                        "interval" to mapOf("type" to "string"),
                        "provider" to mapOf("type" to "string"),
                        "peRatioMax" to mapOf("type" to "number"),
                        "pbRatioMax" to mapOf("type" to "number"),
                        "debtEquityMax" to mapOf("type" to "number"),
                        "roeMin" to mapOf("type" to "number"),
                        "profitMarginMin" to mapOf("type" to "number"),
                        "dividendYieldMin" to mapOf("type" to "number"),
                        "marketCapMin" to mapOf("type" to "number"),
                        "rsiMax" to mapOf("type" to "number"),
                        "rsiMin" to mapOf("type" to "number"),
                        "priceAboveSma" to mapOf("type" to "integer"),
                        "priceBelowSma" to mapOf("type" to "integer"),
                        "betaMax" to mapOf("type" to "number"),
                        "betaMin" to mapOf("type" to "number"),
                        "sortBy" to mapOf("type" to "string"),
                        "ascending" to mapOf("type" to "boolean")
                    ),
                    "required" to listOf("tickers", "startDate", "endDate", "interval")
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
            "backtest_single" -> handleBacktestSingle(arguments)
            "backtest_portfolio" -> handleBacktestPortfolio(arguments)
            "backtest_pair" -> handleBacktestPair(arguments)
            "backtest_walk_forward" -> handleBacktestWalkForward(arguments)
            "backtest_monte_carlo" -> handleBacktestMonteCarlo(arguments)
            "portfolio_optimize" -> handlePortfolioOptimize(arguments)
            "portfolio_risk_parity" -> handlePortfolioRiskParity(arguments)
            "portfolio_black_litterman" -> handlePortfolioBlackLitterman(arguments)
            "screener_run" -> handleScreenerRun(arguments)
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

    private fun handleBacktestSingle(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
        val strategy = arguments["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
        val parameters = (arguments["parameters"] as? Map<String, Any>) ?: emptyMap()
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.SingleAssetCommand(
                ticker = Ticker(symbol),
                strategy = strategy,
                parameters = parameters,
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                initialCapital = (arguments["initialCapital"] as? Number)?.toDouble() ?: 10_000.0,
                commissionPct = (arguments["commissionPct"] as? Number)?.toDouble() ?: 0.001,
                slippagePct = (arguments["slippagePct"] as? Number)?.toDouble() ?: 0.0005
            )
        )
        return toolResult(backtestSummary(result))
    }

    private fun handleBacktestPortfolio(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        @Suppress("UNCHECKED_CAST")
        val weights = (arguments["weights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: emptyMap()
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PortfolioSimulationCommand(
                tickers = symbols.map { Ticker(it) },
                weights = weights,
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                initialCapital = (arguments["initialCapital"] as? Number)?.toDouble() ?: 10_000.0,
                commissionPct = (arguments["commissionPct"] as? Number)?.toDouble() ?: 0.001,
                maxGrossLeverage = (arguments["maxGrossLeverage"] as? Number)?.toDouble() ?: 1.0
            )
        )
        return toolResult(backtestSummary(result))
    }

    private fun handleBacktestPair(arguments: Map<String, Any>): Map<String, Any> {
        val symbolA = arguments["symbolA"] as? String ?: throw IllegalArgumentException("symbolA required")
        val symbolB = arguments["symbolB"] as? String ?: throw IllegalArgumentException("symbolB required")
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.PairTradeCommand(
                symbolA = symbolA,
                symbolB = symbolB,
                entryZ = (arguments["entryZ"] as? Number)?.toDouble() ?: 2.0,
                exitZ = (arguments["exitZ"] as? Number)?.toDouble() ?: 0.5,
                zScoreWindow = (arguments["zScoreWindow"] as? Number)?.toInt() ?: 30,
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                initialCapital = (arguments["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
            )
        )
        return toolResult(backtestSummary(result))
    }

    private fun handleBacktestWalkForward(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
        val strategy = arguments["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
        @Suppress("UNCHECKED_CAST")
        val parameterGrid = (arguments["parameterGrid"] as? Map<String, List<Any>>) ?: emptyMap()
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.WalkForwardCommand(
                ticker = Ticker(symbol),
                strategy = strategy,
                parameterGrid = parameterGrid,
                trainSize = (arguments["trainSize"] as? Number)?.toInt() ?: 252,
                testSize = (arguments["testSize"] as? Number)?.toInt() ?: 63,
                metric = arguments["metric"] as? String ?: "sharpe_ratio",
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                initialCapital = (arguments["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
            )
        )
        return toolResult(backtestSummary(result))
    }

    private fun handleBacktestMonteCarlo(arguments: Map<String, Any>): Map<String, Any> {
        val symbol = arguments["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
        val strategy = arguments["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
        val parameters = (arguments["parameters"] as? Map<String, Any>) ?: emptyMap()
        val result = runBacktestUseCase.execute(
            RunBacktestUseCase.MonteCarloCommand(
                ticker = Ticker(symbol),
                strategy = strategy,
                parameters = parameters,
                horizonDays = (arguments["horizonDays"] as? Number)?.toInt() ?: 252,
                nSimulations = (arguments["nSimulations"] as? Number)?.toInt() ?: 1_000,
                blockSize = (arguments["blockSize"] as? Number)?.toInt() ?: 20,
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                initialCapital = (arguments["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
            )
        )
        return toolResult(backtestSummary(result))
    }

    private fun backtestSummary(result: BacktestResult): String =
        "Backtest ${result.strategyName}: final equity ${result.finalEquity}, total return ${result.totalReturn}, " +
            "trades ${result.trades.size}, max drawdown ${result.metrics?.maxDrawdown ?: "n/a"}, " +
            "Sharpe ${result.metrics?.sharpeRatio ?: "n/a"}"

    private fun handlePortfolioOptimize(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        val objective = arguments["objective"] as? String ?: "max_sharpe"
        val result = optimizePortfolioUseCase.optimize(
            OptimizePortfolioUseCase.OptimizeCommand(
                tickers = symbols.map { Ticker(it) },
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                objective = objective,
                riskFreeRate = (arguments["riskFreeRate"] as? Number)?.toDouble() ?: 0.02,
                targetReturn = (arguments["targetReturn"] as? Number)?.toDouble(),
                targetVolatility = (arguments["targetVolatility"] as? Number)?.toDouble(),
                allowShort = (arguments["allowShort"] as? Boolean) ?: false,
                maxWeight = (arguments["maxWeight"] as? Number)?.toDouble()
            )
        )
        return toolResult(portfolioSummary(result))
    }

    private fun handlePortfolioRiskParity(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        @Suppress("UNCHECKED_CAST")
        val riskBudget = (arguments["riskBudget"] as? Map<String, Number>)?.mapValues { it.value.toDouble() }
        val result = optimizePortfolioUseCase.riskParity(
            OptimizePortfolioUseCase.RiskParityCommand(
                tickers = symbols.map { Ticker(it) },
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                riskBudget = riskBudget
            )
        )
        return toolResult(portfolioSummary(result))
    }

    private fun handlePortfolioBlackLitterman(arguments: Map<String, Any>): Map<String, Any> {
        val symbols = arguments["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
        @Suppress("UNCHECKED_CAST")
        val marketWeights = (arguments["marketWeights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() }
            ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val views = (arguments["views"] as? List<Map<String, Any>>)?.map {
            OptimizePortfolioUseCase.BlackLittermanViewsInput.View(
                asset = it["asset"] as? String,
                relativeAsset = it["relativeAsset"] as? String,
                returnView = (it["returnView"] as? Number)?.toDouble()
                    ?: throw IllegalArgumentException("returnView required")
            )
        } ?: emptyList()
        val result = optimizePortfolioUseCase.blackLitterman(
            OptimizePortfolioUseCase.BlackLittermanCommand(
                tickers = symbols.map { Ticker(it) },
                marketWeights = marketWeights,
                views = OptimizePortfolioUseCase.BlackLittermanViewsInput(assets = symbols, views = views),
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                riskAversion = (arguments["riskAversion"] as? Number)?.toDouble() ?: 2.5,
                tau = (arguments["tau"] as? Number)?.toDouble() ?: 0.05
            )
        )
        return toolResult(portfolioSummary(result))
    }

    private fun portfolioSummary(result: com.example.starter.portfolio.domain.Portfolio): String =
        "Portfolio ${result.objective}: weights ${result.weights}, expected return ${result.expectedReturn}, " +
            "volatility ${result.volatility}, Sharpe ${result.sharpeRatio ?: "n/a"}"

    private fun handleScreenerRun(arguments: Map<String, Any>): Map<String, Any> {
        val tickers = arguments["tickers"] as? List<String>
            ?: throw IllegalArgumentException("tickers required")
        val criteria = ScreenCriteria(
            peRatioMax = (arguments["peRatioMax"] as? Number)?.toDouble(),
            pbRatioMax = (arguments["pbRatioMax"] as? Number)?.toDouble(),
            debtEquityMax = (arguments["debtEquityMax"] as? Number)?.toDouble(),
            roeMin = (arguments["roeMin"] as? Number)?.toDouble(),
            profitMarginMin = (arguments["profitMarginMin"] as? Number)?.toDouble(),
            dividendYieldMin = (arguments["dividendYieldMin"] as? Number)?.toDouble(),
            marketCapMin = (arguments["marketCapMin"] as? Number)?.toDouble(),
            rsiMax = (arguments["rsiMax"] as? Number)?.toDouble(),
            rsiMin = (arguments["rsiMin"] as? Number)?.toDouble(),
            priceAboveSma = (arguments["priceAboveSma"] as? Number)?.toInt(),
            priceBelowSma = (arguments["priceBelowSma"] as? Number)?.toInt(),
            betaMax = (arguments["betaMax"] as? Number)?.toDouble(),
            betaMin = (arguments["betaMin"] as? Number)?.toDouble()
        )
        val result = screenStocksUseCase.screen(
            ScreenStocksUseCase.ScreenCommand(
                tickers = tickers,
                criteria = criteria,
                range = parseRange(arguments),
                interval = parseInterval(arguments),
                provider = arguments["provider"] as? String,
                sortBy = arguments["sortBy"] as? String,
                ascending = (arguments["ascending"] as? Boolean) ?: true
            )
        )
        val summary = "Screen matched ${result.matches.size} of ${tickers.size} tickers" +
            (if (result.failedTickers.isNotEmpty()) "; failed: ${result.failedTickers}" else "") +
            ": ${result.matches.map { it.ticker }}"
        return toolResult(summary)
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
