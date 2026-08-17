package com.example.starter.agent

import org.springframework.stereotype.Component

@Component
class ToolRegistry {

    val definitions: List<ToolDefinition> = buildDefinitions()

    val tools: List<Map<String, Any>> = definitions.map {
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to it.name,
                "description" to it.description,
                "parameters" to it.parameters
            )
        )
    }

    fun find(name: String): ToolDefinition? = definitions.find { it.name == name }

    private fun buildDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "run_sma_backtest",
            description = "Run an SMA crossover backtest for a single asset",
            parameters = singleAssetBacktestSchema(
                extra = mapOf(
                    "fast" to integerParam("Fast SMA period", 10),
                    "slow" to integerParam("Slow SMA period", 30)
                )
            )
        ),
        ToolDefinition(
            name = "run_rsi_backtest",
            description = "Run an RSI mean-reversion backtest for a single asset",
            parameters = singleAssetBacktestSchema(
                extra = mapOf(
                    "period" to integerParam("RSI period", 14),
                    "oversold" to numberParam("Oversold threshold", 30.0),
                    "overbought" to numberParam("Overbought threshold", 70.0)
                )
            )
        ),
        ToolDefinition(
            name = "run_macd_backtest",
            description = "Run a MACD crossover backtest for a single asset",
            parameters = singleAssetBacktestSchema(
                extra = mapOf(
                    "fast" to integerParam("Fast EMA period", 12),
                    "slow" to integerParam("Slow EMA period", 26),
                    "signal" to integerParam("Signal EMA period", 9)
                )
            )
        ),
        ToolDefinition(
            name = "run_bollinger_backtest",
            description = "Run a Bollinger Bands mean-reversion backtest for a single asset",
            parameters = singleAssetBacktestSchema(
                extra = mapOf(
                    "period" to integerParam("Rolling window period", 20),
                    "stdDev" to numberParam("Number of standard deviations", 2.0)
                )
            )
        ),
        ToolDefinition(
            name = "run_buy_and_hold",
            description = "Run a buy-and-hold backtest for a single asset",
            parameters = singleAssetBacktestSchema()
        ),
        ToolDefinition(
            name = "compare_strategies",
            description = "Compare multiple strategies for a single asset by running each backtest",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategies", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategies" to arrayParam("List of strategy names to compare", stringParam("Strategy name")),
                    "exchange" to stringParam("Exchange code"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0),
                    "commissionPct" to numberParam("Commission percentage", 0.001),
                    "slippagePct" to numberParam("Slippage percentage", 0.0005)
                )
            )
        ),
        ToolDefinition(
            name = "analyze_stock_risk",
            description = "Calculate risk metrics (Sharpe, Sortino, max drawdown, VaR, CVaR, volatility) for a stock",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "get_technical_analysis",
            description = "Calculate a technical indicator for a stock",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval", "indicator"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "exchange" to stringParam("Exchange code"),
                    "indicator" to stringParam("Indicator name (e.g. sma, rsi, macd, bollinger)"),
                    "parameters" to objectParam("Indicator-specific parameters"),
                    "provider" to stringParam("Market data provider")
                )
            )
        ),
        ToolDefinition(
            name = "get_portfolio_analysis",
            description = "Calculate weighted risk and return metrics for a portfolio of assets",
            parameters = dateRangeSchema(
                required = listOf("symbols", "weights", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "weights" to objectParam("Asset weights (sum to 1.0)"),
                    "provider" to stringParam("Market data provider"),
                    "riskFreeRate" to numberParam("Annual risk-free rate", 0.02)
                )
            )
        ),
        ToolDefinition(
            name = "run_screener",
            description = "Run a fundamental/technical stock screen over a universe of tickers",
            parameters = dateRangeSchema(
                required = listOf("tickers", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "tickers" to arrayParam("Universe of tickers", stringParam("Ticker symbol")),
                    "provider" to stringParam("Market data provider"),
                    "peRatioMax" to numberParam("Maximum P/E ratio"),
                    "pbRatioMax" to numberParam("Maximum P/B ratio"),
                    "debtEquityMax" to numberParam("Maximum debt-to-equity ratio"),
                    "roeMin" to numberParam("Minimum ROE"),
                    "profitMarginMin" to numberParam("Minimum profit margin"),
                    "dividendYieldMin" to numberParam("Minimum dividend yield"),
                    "marketCapMin" to numberParam("Minimum market capitalization"),
                    "rsiMax" to numberParam("Maximum RSI"),
                    "rsiMin" to numberParam("Minimum RSI"),
                    "priceAboveSma" to integerParam("Require price above this SMA period"),
                    "priceBelowSma" to integerParam("Require price below this SMA period"),
                    "betaMax" to numberParam("Maximum beta"),
                    "betaMin" to numberParam("Minimum beta"),
                    "sortBy" to stringParam("Metric to sort by"),
                    "ascending" to booleanParam("Sort ascending", true)
                )
            )
        ),
        ToolDefinition(
            name = "run_factor_regression",
            description = "Run a factor regression for an asset against a benchmark",
            parameters = dateRangeSchema(
                required = listOf("asset", "benchmark", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "asset" to stringParam("Asset symbol"),
                    "benchmark" to stringParam("Benchmark symbol"),
                    "provider" to stringParam("Market data provider"),
                    "riskFreeRate" to numberParam("Annual risk-free rate", 0.02)
                )
            )
        ),
        ToolDefinition(
            name = "analysis_regression",
            description = "Run regression analysis for an asset against a benchmark",
            parameters = dateRangeSchema(
                required = listOf("asset", "benchmark", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "asset" to stringParam("Asset symbol"),
                    "benchmark" to stringParam("Benchmark symbol"),
                    "provider" to stringParam("Market data provider"),
                    "riskFreeRate" to numberParam("Annual risk-free rate", 0.02)
                )
            )
        ),
        ToolDefinition(
            name = "analysis_multi_factor",
            description = "Run multi-factor regression for an asset",
            parameters = dateRangeSchema(
                required = listOf("asset", "factors", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "asset" to stringParam("Asset symbol"),
                    "factors" to objectParam("Factor symbols keyed by factor name"),
                    "provider" to stringParam("Market data provider")
                )
            )
        ),
        ToolDefinition(
            name = "run_cointegration_test",
            description = "Run an Engle-Granger cointegration test between two assets",
            parameters = dateRangeSchema(
                required = listOf("assetA", "assetB", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "assetA" to stringParam("First asset symbol"),
                    "assetB" to stringParam("Second asset symbol"),
                    "provider" to stringParam("Market data provider"),
                    "zScoreWindow" to integerParam("Z-score rolling window", 30)
                )
            )
        ),
        ToolDefinition(
            name = "run_pca_analysis",
            description = "Run principal component analysis on a list of assets",
            parameters = dateRangeSchema(
                required = listOf("symbols", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "provider" to stringParam("Market data provider"),
                    "nComponents" to integerParam("Number of components to retain"),
                    "standardize" to booleanParam("Standardize returns", true)
                )
            )
        ),
        ToolDefinition(
            name = "run_hurst_analysis",
            description = "Estimate the Hurst exponent for a price series",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "provider" to stringParam("Market data provider"),
                    "method" to stringParam("Estimation method (dfa, rs, variance)", "dfa"),
                    "rollingWindow" to integerParam("Optional rolling window for time-varying estimate")
                )
            )
        ),
        ToolDefinition(
            name = "run_regime_adaptive_backtest",
            description = "Run a regime-adaptive single-asset backtest",
            parameters = singleAssetBacktestSchema()
        ),
        ToolDefinition(
            name = "scan_pairs",
            description = "Scan a universe of tickers for cointegrated pairs",
            parameters = dateRangeSchema(
                required = listOf("tickers", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "tickers" to arrayParam("Universe of tickers", stringParam("Ticker symbol")),
                    "provider" to stringParam("Market data provider"),
                    "zScoreWindow" to integerParam("Z-score rolling window", 30),
                    "topN" to integerParam("Number of top pairs to return", 5)
                )
            )
        ),
        ToolDefinition(
            name = "run_walk_forward_backtest",
            description = "Run a walk-forward optimization backtest for a single asset",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategy" to stringParam("Strategy name"),
                    "parameters" to objectParam("Strategy parameters"),
                    "parameterGrid" to objectParam("Parameter grid for optimization"),
                    "trainSize" to integerParam("In-sample training size", 252),
                    "testSize" to integerParam("Out-of-sample test size", 63),
                    "metric" to stringParam("Optimization metric", "sharpe_ratio"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_portfolio_risk_attribution",
            description = "Compute risk parity weights or risk attribution for a portfolio",
            parameters = dateRangeSchema(
                required = listOf("symbols", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "provider" to stringParam("Market data provider"),
                    "riskBudget" to objectParam("Risk budget per asset")
                )
            )
        ),
        ToolDefinition(
            name = "get_position_size",
            description = "Suggest a volatility-targeted position size for an asset",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "provider" to stringParam("Market data provider"),
                    "targetVolatility" to numberParam("Target annual volatility", 0.10),
                    "capital" to numberParam("Capital to allocate", 100_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_stock_fundamentals",
            description = "Fetch fundamental data for a single stock",
            parameters = dateRangeSchema(
                required = listOf("ticker", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "ticker" to stringParam("Ticker symbol"),
                    "provider" to stringParam("Market data provider")
                )
            )
        ),
        ToolDefinition(
            name = "run_backtest_optimization",
            description = "Optimize strategy parameters using walk-forward analysis",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategy" to stringParam("Strategy name"),
                    "parameterGrid" to objectParam("Parameter grid"),
                    "trainSize" to integerParam("Training window size", 252),
                    "testSize" to integerParam("Test window size", 63),
                    "metric" to stringParam("Optimization metric", "sharpe_ratio"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_advanced_indicators",
            description = "Calculate an advanced technical indicator for a stock",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval", "indicator"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "exchange" to stringParam("Exchange code"),
                    "indicator" to stringParam("Indicator name"),
                    "parameters" to objectParam("Indicator parameters"),
                    "provider" to stringParam("Market data provider")
                )
            )
        ),
        ToolDefinition(
            name = "get_rolling_beta",
            description = "Compute rolling beta of an asset relative to a benchmark",
            parameters = dateRangeSchema(
                required = listOf("asset", "benchmark", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "asset" to stringParam("Asset symbol"),
                    "benchmark" to stringParam("Benchmark symbol"),
                    "provider" to stringParam("Market data provider"),
                    "window" to integerParam("Rolling window", 63)
                )
            )
        ),
        ToolDefinition(
            name = "get_extended_risk_metrics",
            description = "Calculate extended risk metrics for an asset",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "run_custom_signal_backtest",
            description = "Run a backtest using a custom signal series",
            parameters = dateRangeSchema(
                required = listOf("symbols", "signals", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "signals" to objectParam("Signal values keyed by symbol"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0),
                    "commissionPct" to numberParam("Commission percentage", 0.001)
                )
            )
        ),
        ToolDefinition(
            name = "run_signal_panel_backtest",
            description = "Run a panel backtest using cross-sectional signals",
            parameters = dateRangeSchema(
                required = listOf("symbols", "signals", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "signals" to objectParam("Signal values keyed by symbol"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0),
                    "commissionPct" to numberParam("Commission percentage", 0.001)
                )
            )
        ),
        ToolDefinition(
            name = "run_regime_adaptive_walkforward_backtest",
            description = "Run a walk-forward backtest with regime adaptation",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategy" to stringParam("Strategy name"),
                    "parameterGrid" to objectParam("Parameter grid"),
                    "trainSize" to integerParam("Training window size", 252),
                    "testSize" to integerParam("Test window size", 63),
                    "metric" to stringParam("Optimization metric", "sharpe_ratio"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_backtest_diagnostics",
            description = "Run a single-asset backtest and return detailed diagnostics",
            parameters = singleAssetBacktestSchema()
        ),
        ToolDefinition(
            name = "run_portfolio_simulation",
            description = "Run a buy-and-hold portfolio simulation with fixed weights",
            parameters = dateRangeSchema(
                required = listOf("symbols", "weights", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "weights" to objectParam("Asset weights"),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0),
                    "commissionPct" to numberParam("Commission percentage", 0.001),
                    "maxGrossLeverage" to numberParam("Maximum gross leverage", 1.0)
                )
            )
        ),
        ToolDefinition(
            name = "run_pair_trade_backtest",
            description = "Run a mean-reversion pair-trade backtest",
            parameters = dateRangeSchema(
                required = listOf("symbolA", "symbolB", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbolA" to stringParam("First asset symbol"),
                    "symbolB" to stringParam("Second asset symbol"),
                    "entryZ" to numberParam("Z-score entry threshold", 2.0),
                    "exitZ" to numberParam("Z-score exit threshold", 0.5),
                    "zScoreWindow" to integerParam("Z-score rolling window", 30),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_robustness_diagnostics",
            description = "Run Monte Carlo simulations to assess strategy robustness",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategy" to stringParam("Strategy name"),
                    "parameters" to objectParam("Strategy parameters"),
                    "horizonDays" to integerParam("Simulation horizon in days", 252),
                    "nSimulations" to integerParam("Number of simulation paths", 1_000),
                    "blockSize" to integerParam("Bootstrap block size", 20),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "get_capacity_report",
            description = "Estimate strategy capacity and market impact from historical volume",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "targetAum" to numberParam("Target assets under management", 1_000_000.0),
                    "participationRate" to numberParam("Expected participation rate", 0.10)
                )
            )
        ),
        ToolDefinition(
            name = "get_data_quality_report",
            description = "Fetch market data and report basic data-quality statistics",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "run_backtest_compact",
            description = "Run a single-asset backtest and return a compact summary",
            parameters = singleAssetBacktestSchema()
        ),
        ToolDefinition(
            name = "run_portfolio_optimization",
            description = "Run mean-variance portfolio optimization",
            parameters = dateRangeSchema(
                required = listOf("symbols", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "provider" to stringParam("Market data provider"),
                    "objective" to stringParam("Optimization objective", "max_sharpe"),
                    "riskFreeRate" to numberParam("Annual risk-free rate", 0.02),
                    "targetReturn" to numberParam("Target return"),
                    "targetVolatility" to numberParam("Target volatility"),
                    "allowShort" to booleanParam("Allow short positions", false),
                    "maxWeight" to numberParam("Maximum weight per asset")
                )
            )
        ),
        ToolDefinition(
            name = "portfolio_black_litterman",
            description = "Run Black-Litterman portfolio optimization",
            parameters = dateRangeSchema(
                required = listOf("symbols", "marketWeights", "views", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "marketWeights" to objectParam("Market capitalization weights"),
                    "views" to arrayParam(
                        "List of return views",
                        mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "asset" to stringParam("Asset symbol"),
                                "relativeAsset" to stringParam("Relative asset symbol"),
                                "returnView" to numberParam("Return view")
                            ),
                            "required" to listOf("returnView")
                        )
                    ),
                    "provider" to stringParam("Market data provider"),
                    "riskAversion" to numberParam("Risk aversion", 2.5),
                    "tau" to numberParam("Uncertainty scaling", 0.05)
                )
            )
        ),
        ToolDefinition(
            name = "get_option_pricing",
            description = "Price an option using Black-Scholes and compute Greeks",
            parameters = optionPricingSchema()
        ),
        ToolDefinition(
            name = "get_implied_volatility",
            description = "Compute implied volatility from an option market price",
            parameters = optionPricingSchema()
        ),
        ToolDefinition(
            name = "get_volatility_estimators",
            description = "Estimate realized volatility and related estimators for an asset",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "get_correlation_analysis",
            description = "Compute the correlation matrix for a list of assets",
            parameters = dateRangeSchema(
                required = listOf("symbols", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbols" to arrayParam("List of asset symbols", stringParam("Asset symbol")),
                    "provider" to stringParam("Market data provider"),
                    "weights" to objectParam("Optional asset weights")
                )
            )
        ),
        ToolDefinition(
            name = "run_monte_carlo_simulation",
            description = "Run a Monte Carlo forward-path simulation for a strategy",
            parameters = dateRangeSchema(
                required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "strategy" to stringParam("Strategy name"),
                    "parameters" to objectParam("Strategy parameters"),
                    "horizonDays" to integerParam("Simulation horizon", 252),
                    "nSimulations" to integerParam("Number of paths", 1_000),
                    "blockSize" to integerParam("Bootstrap block size", 20),
                    "provider" to stringParam("Market data provider"),
                    "initialCapital" to numberParam("Initial capital", 10_000.0)
                )
            )
        ),
        ToolDefinition(
            name = "run_stress_test",
            description = "Run a stress-test scenario on a strategy",
            parameters = singleAssetBacktestSchema()
        ),
        ToolDefinition(
            name = "get_liquidity_metrics",
            description = "Compute basic liquidity metrics for an asset",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "marketdata_fetch",
            description = "Fetch OHLCV bars for a ticker",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "indicators_calculate",
            description = "Calculate a technical indicator for a ticker",
            parameters = dateRangeSchema(
                required = listOf("symbol", "startDate", "endDate", "interval", "indicator"),
                extra = mapOf(
                    "symbol" to stringParam("Asset symbol"),
                    "exchange" to stringParam("Exchange code"),
                    "indicator" to stringParam("Indicator name"),
                    "parameters" to objectParam("Indicator parameters"),
                    "provider" to stringParam("Market data provider")
                )
            )
        ),
        ToolDefinition(
            name = "metrics_risk",
            description = "Calculate risk metrics for a ticker",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "metrics_return",
            description = "Calculate return metrics for a ticker",
            parameters = singleAssetMarketDataSchema()
        ),
        ToolDefinition(
            name = "create_order",
            description = "Create a new order",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "customerId" to stringParam("Customer identifier"),
                    "items" to arrayParam(
                        "Order line items",
                        mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "productId" to stringParam("Product identifier"),
                                "quantity" to integerParam("Quantity", 1),
                                "unitPrice" to stringParam("Unit price as decimal string")
                            ),
                            "required" to listOf("productId", "quantity", "unitPrice")
                        )
                    )
                ),
                "required" to listOf("customerId", "items")
            )
        ),
        ToolDefinition(
            name = "get_order",
            description = "Get an order by id",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("orderId" to stringParam("Order UUID")),
                "required" to listOf("orderId")
            )
        ),
        ToolDefinition(
            name = "cancel_order",
            description = "Cancel an order by id",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("orderId" to stringParam("Order UUID")),
                "required" to listOf("orderId")
            )
        ),
        ToolDefinition(
            name = "screener_run",
            description = "Run a fundamental/technical stock screen over a universe of tickers",
            parameters = dateRangeSchema(
                required = listOf("tickers", "startDate", "endDate", "interval"),
                extra = mapOf(
                    "tickers" to arrayParam("Universe of tickers", stringParam("Ticker symbol")),
                    "provider" to stringParam("Market data provider"),
                    "peRatioMax" to numberParam("Maximum P/E ratio"),
                    "pbRatioMax" to numberParam("Maximum P/B ratio"),
                    "debtEquityMax" to numberParam("Maximum debt-to-equity ratio"),
                    "roeMin" to numberParam("Minimum ROE"),
                    "profitMarginMin" to numberParam("Minimum profit margin"),
                    "dividendYieldMin" to numberParam("Minimum dividend yield"),
                    "marketCapMin" to numberParam("Minimum market capitalization"),
                    "rsiMax" to numberParam("Maximum RSI"),
                    "rsiMin" to numberParam("Minimum RSI"),
                    "priceAboveSma" to integerParam("Require price above this SMA period"),
                    "priceBelowSma" to integerParam("Require price below this SMA period"),
                    "betaMax" to numberParam("Maximum beta"),
                    "betaMin" to numberParam("Minimum beta"),
                    "sortBy" to stringParam("Metric to sort by"),
                    "ascending" to booleanParam("Sort ascending", true)
                )
            )
        )
    )

    private fun singleAssetBacktestSchema(extra: Map<String, Map<String, Any>> = emptyMap()): Map<String, Any> =
        dateRangeSchema(
            required = listOf("symbol", "strategy", "startDate", "endDate", "interval"),
            extra = buildMap {
                put("symbol", stringParam("Asset symbol"))
                put("exchange", stringParam("Exchange code"))
                put("strategy", stringParam("Strategy name"))
                put("parameters", objectParam("Strategy parameters"))
                put("provider", stringParam("Market data provider"))
                put("initialCapital", numberParam("Initial capital", 10_000.0))
                put("commissionPct", numberParam("Commission percentage", 0.001))
                put("slippagePct", numberParam("Slippage percentage", 0.0005))
                putAll(extra)
            }
        )

    private fun singleAssetMarketDataSchema(): Map<String, Any> = dateRangeSchema(
        required = listOf("symbol", "startDate", "endDate", "interval"),
        extra = mapOf(
            "symbol" to stringParam("Asset symbol"),
            "exchange" to stringParam("Exchange code"),
            "provider" to stringParam("Market data provider"),
            "riskFreeRate" to numberParam("Annual risk-free rate", 0.02)
        )
    )

    private fun optionPricingSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "spot" to numberParam("Current underlying price"),
            "strike" to numberParam("Option strike price"),
            "timeToExpiry" to numberParam("Time to expiry in years"),
            "riskFreeRate" to numberParam("Annual risk-free rate", 0.05),
            "volatility" to numberParam("Annual volatility"),
            "optionType" to stringParam("Option type (call or put)", "call"),
            "dividendYield" to numberParam("Continuous dividend yield", 0.0),
            "marketPrice" to numberParam("Market price for implied volatility calculation")
        ),
        "required" to listOf("spot", "strike", "timeToExpiry", "riskFreeRate", "volatility")
    )

    private fun dateRangeSchema(
        required: List<String>,
        extra: Map<String, Map<String, Any>>
    ): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to buildMap {
            put("startDate", stringParam("Start date (ISO-8601)"))
            put("endDate", stringParam("End date (ISO-8601)"))
            put("interval", stringParam("Bar interval (DAILY, WEEKLY, MONTHLY)", "DAILY"))
            putAll(extra)
        },
        "required" to required
    )

    private fun stringParam(description: String, default: String? = null): Map<String, Any> = buildMap {
        put("type", "string")
        put("description", description)
        default?.let { put("default", it) }
    }

    private fun integerParam(description: String, default: Int? = null): Map<String, Any> = buildMap {
        put("type", "integer")
        put("description", description)
        default?.let { put("default", it) }
    }

    private fun numberParam(description: String, default: Double? = null): Map<String, Any> = buildMap {
        put("type", "number")
        put("description", description)
        default?.let { put("default", it) }
    }

    private fun booleanParam(description: String, default: Boolean? = null): Map<String, Any> = buildMap {
        put("type", "boolean")
        put("description", description)
        default?.let { put("default", it) }
    }

    private fun objectParam(description: String): Map<String, Any> = mapOf(
        "type" to "object",
        "description" to description,
        "additionalProperties" to true
    )

    private fun arrayParam(description: String, items: Map<String, Any>): Map<String, Any> = mapOf(
        "type" to "array",
        "description" to description,
        "items" to items
    )
}
