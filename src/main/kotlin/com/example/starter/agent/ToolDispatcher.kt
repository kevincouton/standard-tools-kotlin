package com.example.starter.agent

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.audit.AuditWriter
import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.domain.OrderItem
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Component
class ToolDispatcher(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase,
    private val calculateMetricsUseCase: CalculateMetricsUseCase,
    private val runAnalysisUseCase: RunAnalysisUseCase,
    private val runBacktestUseCase: RunBacktestUseCase,
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase,
    private val screenStocksUseCase: ScreenStocksUseCase,
    private val auditWriter: AuditWriter
) {

    fun dispatch(name: String, arguments: Map<String, Any>): Map<String, Any> {
        val requestId = UUID.randomUUID()
        val start = System.currentTimeMillis()
        val result = try {
            dispatchInternal(name, arguments)
        } catch (ex: Throwable) {
            val durationMs = System.currentTimeMillis() - start
            auditWriter.write(
                requestId = requestId,
                toolName = name,
                input = arguments,
                durationMs = durationMs,
                outputHash = "",
                status = "error",
                errorType = ex::class.simpleName,
                errorMessage = ex.message
            )
            throw ex
        }
        val durationMs = System.currentTimeMillis() - start
        auditWriter.write(
            requestId = requestId,
            toolName = name,
            input = arguments,
            durationMs = durationMs,
            output = result,
            status = "ok"
        )
        return result
    }

    private fun dispatchInternal(name: String, arguments: Map<String, Any>): Map<String, Any> {
        return when (name) {
            "run_sma_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = "sma_crossover",
                        parameters = mapOf(
                            "fast" to intArg(arguments, "fast", 10),
                            "slow" to intArg(arguments, "slow", 30)
                        ),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_rsi_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = "rsi_mean_reversion",
                        parameters = mapOf(
                            "period" to intArg(arguments, "period", 14),
                            "oversold" to doubleArg(arguments, "oversold", 30.0),
                            "overbought" to doubleArg(arguments, "overbought", 70.0)
                        ),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_macd_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = "macd_crossover",
                        parameters = mapOf(
                            "fast" to intArg(arguments, "fast", 12),
                            "slow" to intArg(arguments, "slow", 26),
                            "signal" to intArg(arguments, "signal", 9)
                        ),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_bollinger_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = "bollinger_reversion",
                        parameters = mapOf(
                            "period" to intArg(arguments, "period", 20),
                            "stdDev" to doubleArg(arguments, "stdDev", 2.0)
                        ),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_buy_and_hold" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = "buy_and_hold",
                        parameters = emptyMap(),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "compare_strategies" -> compareStrategies(arguments)
            "analyze_stock_risk" -> riskMetrics(calculateMetricsUseCase.calculateRisk(riskCommand(arguments)))
            "get_technical_analysis" -> indicatorResult(
                calculateIndicatorUseCase.calculate(
                    CalculateIndicatorUseCase.CalculateIndicatorCommand(
                        ticker = ticker(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        indicator = stringArg(arguments, "indicator"),
                        parameters = objectArg(arguments, "parameters"),
                        provider = provider(arguments)
                    )
                )
            )
            "get_portfolio_analysis" -> portfolioAnalysis(arguments)
            "run_screener" -> screenerResult(screenStocksUseCase.screen(screenCommand(arguments)))
            "run_factor_regression" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.RegressionCommand(
                        asset = ticker(arguments, "asset"),
                        benchmark = ticker(arguments, "benchmark"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02)
                    )
                )
            )
            "analysis_regression" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.RegressionCommand(
                        asset = ticker(arguments, "asset"),
                        benchmark = ticker(arguments, "benchmark"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02)
                    )
                )
            )
            "analysis_multi_factor" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.MultiFactorCommand(
                        asset = ticker(arguments, "asset"),
                        factors = parseFactors(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments)
                    )
                )
            )
            "run_cointegration_test" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CointegrationCommand(
                        assetA = ticker(arguments, "assetA"),
                        assetB = ticker(arguments, "assetB"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        zScoreWindow = intArg(arguments, "zScoreWindow", 30)
                    )
                )
            )
            "run_pca_analysis" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.PcaCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        nComponents = optionalInt(arguments, "nComponents"),
                        standardize = booleanArg(arguments, "standardize", true)
                    )
                )
            )
            "run_hurst_analysis" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.HurstCommand(
                        ticker = ticker(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        method = stringArg(arguments, "method", "dfa"),
                        rollingWindow = optionalInt(arguments, "rollingWindow")
                    )
                )
            )
            "run_regime_adaptive_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameters = objectArg(arguments, "parameters"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "scan_pairs" -> scanPairs(arguments)
            "run_walk_forward_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.WalkForwardCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameterGrid = parameterGrid(arguments),
                        trainSize = intArg(arguments, "trainSize", 252),
                        testSize = intArg(arguments, "testSize", 63),
                        metric = stringArg(arguments, "metric", "sharpe_ratio"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0)
                    )
                )
            )
            "get_portfolio_risk_attribution" -> portfolioSummary(
                optimizePortfolioUseCase.riskParity(
                    OptimizePortfolioUseCase.RiskParityCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        riskBudget = weightsArg(arguments, "riskBudget")
                    )
                )
            )
            "get_position_size" -> positionSize(arguments)
            "get_stock_fundamentals" -> screenerResult(
                screenStocksUseCase.screen(
                    ScreenStocksUseCase.ScreenCommand(
                        tickers = listOf(stringArg(arguments, "ticker")),
                        criteria = ScreenCriteria(),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments)
                    )
                )
            )
            "run_backtest_optimization" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.WalkForwardCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameterGrid = parameterGrid(arguments),
                        trainSize = intArg(arguments, "trainSize", 252),
                        testSize = intArg(arguments, "testSize", 63),
                        metric = stringArg(arguments, "metric", "sharpe_ratio"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0)
                    )
                )
            )
            "get_advanced_indicators" -> indicatorResult(
                calculateIndicatorUseCase.calculate(
                    CalculateIndicatorUseCase.CalculateIndicatorCommand(
                        ticker = ticker(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        indicator = stringArg(arguments, "indicator"),
                        parameters = objectArg(arguments, "parameters"),
                        provider = provider(arguments)
                    )
                )
            )
            "get_rolling_beta" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.RegressionCommand(
                        asset = ticker(arguments, "asset"),
                        benchmark = ticker(arguments, "benchmark"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02)
                    )
                )
            )
            "get_extended_risk_metrics" -> riskMetrics(calculateMetricsUseCase.calculateRisk(riskCommand(arguments)))
            "run_custom_signal_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SignalPanelCommand(
                        signals = parseSignals(arguments),
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001)
                    )
                )
            )
            "run_signal_panel_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SignalPanelCommand(
                        signals = parseSignals(arguments),
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001)
                    )
                )
            )
            "run_regime_adaptive_walkforward_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.WalkForwardCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameterGrid = parameterGrid(arguments),
                        trainSize = intArg(arguments, "trainSize", 252),
                        testSize = intArg(arguments, "testSize", 63),
                        metric = stringArg(arguments, "metric", "sharpe_ratio"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0)
                    )
                )
            )
            "get_backtest_diagnostics" -> backtestDiagnostics(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameters = objectArg(arguments, "parameters"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_portfolio_simulation" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.PortfolioSimulationCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        weights = weightsArg(arguments, "weights") ?: emptyMap(),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        maxGrossLeverage = doubleArg(arguments, "maxGrossLeverage", 1.0)
                    )
                )
            )
            "run_pair_trade_backtest" -> backtestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.PairTradeCommand(
                        symbolA = stringArg(arguments, "symbolA"),
                        symbolB = stringArg(arguments, "symbolB"),
                        entryZ = doubleArg(arguments, "entryZ", 2.0),
                        exitZ = doubleArg(arguments, "exitZ", 0.5),
                        zScoreWindow = intArg(arguments, "zScoreWindow", 30),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0)
                    )
                )
            )
            "get_robustness_diagnostics" -> robustnessDiagnostics(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.MonteCarloCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameters = objectArg(arguments, "parameters"),
                        horizonDays = intArg(arguments, "horizonDays", 252),
                        nSimulations = intArg(arguments, "nSimulations", 1_000),
                        blockSize = intArg(arguments, "blockSize", 20),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments)
                    )
                )
            )
            "get_capacity_report" -> throw NotImplementedError("Capacity report is not yet implemented")
            "get_data_quality_report" -> dataQualityReport(arguments)
            "run_backtest_compact" -> compactBacktestSummary(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameters = objectArg(arguments, "parameters"),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                        slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                    )
                )
            )
            "run_portfolio_optimization" -> portfolioSummary(
                optimizePortfolioUseCase.optimize(
                    OptimizePortfolioUseCase.OptimizeCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        objective = stringArg(arguments, "objective", "max_sharpe"),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02),
                        targetReturn = optionalDouble(arguments, "targetReturn"),
                        targetVolatility = optionalDouble(arguments, "targetVolatility"),
                        allowShort = booleanArg(arguments, "allowShort", false),
                        maxWeight = optionalDouble(arguments, "maxWeight")
                    )
                )
            )
            "portfolio_black_litterman" -> portfolioSummary(
                optimizePortfolioUseCase.blackLitterman(
                    OptimizePortfolioUseCase.BlackLittermanCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        marketWeights = weightsArg(arguments, "marketWeights") ?: emptyMap(),
                        views = parseBlackLittermanViews(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        riskAversion = doubleArg(arguments, "riskAversion", 2.5),
                        tau = doubleArg(arguments, "tau", 0.05)
                    )
                )
            )
            "get_option_pricing" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.OptionPricingCommand(
                        spot = doubleArg(arguments, "spot"),
                        strike = doubleArg(arguments, "strike"),
                        timeToExpiry = doubleArg(arguments, "timeToExpiry"),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.05),
                        volatility = doubleArg(arguments, "volatility"),
                        optionType = stringArg(arguments, "optionType", "call"),
                        dividendYield = doubleArg(arguments, "dividendYield", 0.0),
                        marketPrice = optionalDouble(arguments, "marketPrice")
                    )
                )
            )
            "get_implied_volatility" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.OptionPricingCommand(
                        spot = doubleArg(arguments, "spot"),
                        strike = doubleArg(arguments, "strike"),
                        timeToExpiry = doubleArg(arguments, "timeToExpiry"),
                        riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.05),
                        volatility = doubleArg(arguments, "volatility"),
                        optionType = stringArg(arguments, "optionType", "call"),
                        dividendYield = doubleArg(arguments, "dividendYield", 0.0),
                        marketPrice = doubleArg(arguments, "marketPrice")
                    )
                )
            )
            "get_volatility_estimators" -> riskMetrics(calculateMetricsUseCase.calculateRisk(riskCommand(arguments)))
            "get_correlation_analysis" -> analysisResult(
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CorrelationCommand(
                        tickers = symbols(arguments).map { Ticker(it) },
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments),
                        weights = weightsArg(arguments, "weights")
                    )
                )
            )
            "run_monte_carlo_simulation" -> robustnessDiagnostics(
                runBacktestUseCase.execute(
                    RunBacktestUseCase.MonteCarloCommand(
                        ticker = ticker(arguments),
                        strategy = stringArg(arguments, "strategy"),
                        parameters = objectArg(arguments, "parameters"),
                        horizonDays = intArg(arguments, "horizonDays", 252),
                        nSimulations = intArg(arguments, "nSimulations", 1_000),
                        blockSize = intArg(arguments, "blockSize", 20),
                        initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments)
                    )
                )
            )
            "run_stress_test" -> throw NotImplementedError("Stress test is not yet implemented")
            "get_liquidity_metrics" -> throw NotImplementedError("Liquidity metrics are not yet implemented")
            "marketdata_fetch" -> marketDataResult(
                fetchMarketDataUseCase.fetch(
                    FetchMarketDataUseCase.FetchMarketDataCommand(
                        ticker = ticker(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        provider = provider(arguments)
                    )
                )
            )
            "indicators_calculate" -> indicatorResult(
                calculateIndicatorUseCase.calculate(
                    CalculateIndicatorUseCase.CalculateIndicatorCommand(
                        ticker = ticker(arguments),
                        range = range(arguments),
                        interval = interval(arguments),
                        indicator = stringArg(arguments, "indicator"),
                        parameters = objectArg(arguments, "parameters"),
                        provider = provider(arguments)
                    )
                )
            )
            "metrics_risk" -> riskMetrics(calculateMetricsUseCase.calculateRisk(riskCommand(arguments)))
            "metrics_return" -> returnMetrics(calculateMetricsUseCase.calculateReturn(returnCommand(arguments)))
            "create_order" -> mapOf("order" to orderMap(createOrderUseCase.createOrder(createOrderCommand(arguments))))
            "get_order" -> mapOf("order" to orderMap(getOrderUseCase.getOrder(UUID.fromString(stringArg(arguments, "orderId")))))
            "cancel_order" -> mapOf("order" to orderMap(cancelOrderUseCase.cancelOrder(UUID.fromString(stringArg(arguments, "orderId")))))
            "screener_run" -> screenerResult(screenStocksUseCase.screen(screenCommand(arguments)))
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
    }

    private fun ticker(arguments: Map<String, Any>): Ticker =
        Ticker(stringArg(arguments, "symbol"), arguments["exchange"] as? String)

    private fun ticker(arguments: Map<String, Any>, key: String): Ticker =
        Ticker(stringArg(arguments, key), arguments["exchange"] as? String)

    private fun range(arguments: Map<String, Any>): DateRange {
        val start = stringArg(arguments, "startDate")
        val end = stringArg(arguments, "endDate")
        return DateRange(LocalDate.parse(start), LocalDate.parse(end))
    }

    private fun interval(arguments: Map<String, Any>): BarInterval {
        val value = arguments["interval"] as? String ?: "DAILY"
        return BarInterval.entries.find { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw InvalidCommandException(
                "interval must be one of ${BarInterval.entries.joinToString { it.name }}"
            )
    }

    private fun provider(arguments: Map<String, Any>): String? = arguments["provider"] as? String

    private fun stringArg(arguments: Map<String, Any>, key: String): String =
        arguments[key] as? String ?: throw IllegalArgumentException("$key required")

    private fun stringArg(arguments: Map<String, Any>, key: String, default: String): String =
        arguments[key] as? String ?: default

    private fun intArg(arguments: Map<String, Any>, key: String, default: Int): Int =
        (arguments[key] as? Number)?.toInt() ?: default

    private fun optionalInt(arguments: Map<String, Any>, key: String): Int? =
        (arguments[key] as? Number)?.toInt()

    private fun doubleArg(arguments: Map<String, Any>, key: String): Double =
        (arguments[key] as? Number)?.toDouble() ?: throw IllegalArgumentException("$key required")

    private fun doubleArg(arguments: Map<String, Any>, key: String, default: Double): Double =
        (arguments[key] as? Number)?.toDouble() ?: default

    private fun optionalDouble(arguments: Map<String, Any>, key: String): Double? =
        (arguments[key] as? Number)?.toDouble()

    private fun booleanArg(arguments: Map<String, Any>, key: String, default: Boolean): Boolean =
        (arguments[key] as? Boolean) ?: default

    @Suppress("UNCHECKED_CAST")
    private fun objectArg(arguments: Map<String, Any>, key: String): Map<String, Any> =
        (arguments[key] as? Map<String, Any>) ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun symbols(arguments: Map<String, Any>): List<String> =
        arguments["symbols"] as? List<String>
            ?: arguments["tickers"] as? List<String>
            ?: throw IllegalArgumentException("symbols or tickers required")

    @Suppress("UNCHECKED_CAST")
    private fun weightsArg(arguments: Map<String, Any>, key: String): Map<String, Double>? =
        (arguments[key] as? Map<String, Number>)?.mapValues { it.value.toDouble() }

    @Suppress("UNCHECKED_CAST")
    private fun parameterGrid(arguments: Map<String, Any>): Map<String, List<Any>> =
        (arguments["parameterGrid"] as? Map<String, List<Any>>) ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun parseSignals(arguments: Map<String, Any>): Map<String, List<Double>> {
        val raw = arguments["signals"] as? Map<String, List<Any>>
            ?: throw IllegalArgumentException("signals required")
        return raw.mapValues { (_, values) ->
            values.map {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDouble()
                    else -> throw IllegalArgumentException("Signal values must be numbers")
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFactors(arguments: Map<String, Any>): Map<String, Ticker> {
        val raw = arguments["factors"] as? Map<String, String>
            ?: throw IllegalArgumentException("factors required")
        return raw.mapValues { Ticker(it.value) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBlackLittermanViews(arguments: Map<String, Any>): OptimizePortfolioUseCase.BlackLittermanViewsInput {
        val symbols = symbols(arguments)
        val views = (arguments["views"] as? List<Map<String, Any>>)?.map {
            OptimizePortfolioUseCase.BlackLittermanViewsInput.View(
                asset = it["asset"] as? String,
                relativeAsset = it["relativeAsset"] as? String,
                returnView = (it["returnView"] as? Number)?.toDouble()
                    ?: throw IllegalArgumentException("returnView required")
            )
        } ?: emptyList()
        return OptimizePortfolioUseCase.BlackLittermanViewsInput(assets = symbols, views = views)
    }

    private fun riskCommand(arguments: Map<String, Any>): CalculateMetricsUseCase.CalculateRiskCommand =
        CalculateMetricsUseCase.CalculateRiskCommand(
            ticker = ticker(arguments),
            range = range(arguments),
            interval = interval(arguments),
            riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02),
            provider = provider(arguments)
        )

    private fun returnCommand(arguments: Map<String, Any>): CalculateMetricsUseCase.CalculateReturnCommand =
        CalculateMetricsUseCase.CalculateReturnCommand(
            ticker = ticker(arguments),
            range = range(arguments),
            interval = interval(arguments),
            provider = provider(arguments)
        )

    private fun screenCommand(arguments: Map<String, Any>): ScreenStocksUseCase.ScreenCommand =
        ScreenStocksUseCase.ScreenCommand(
            tickers = (arguments["tickers"] as? List<String>) ?: symbols(arguments),
            criteria = ScreenCriteria(
                peRatioMax = optionalDouble(arguments, "peRatioMax"),
                pbRatioMax = optionalDouble(arguments, "pbRatioMax"),
                debtEquityMax = optionalDouble(arguments, "debtEquityMax"),
                roeMin = optionalDouble(arguments, "roeMin"),
                profitMarginMin = optionalDouble(arguments, "profitMarginMin"),
                dividendYieldMin = optionalDouble(arguments, "dividendYieldMin"),
                marketCapMin = optionalDouble(arguments, "marketCapMin"),
                rsiMax = optionalDouble(arguments, "rsiMax"),
                rsiMin = optionalDouble(arguments, "rsiMin"),
                priceAboveSma = optionalInt(arguments, "priceAboveSma"),
                priceBelowSma = optionalInt(arguments, "priceBelowSma"),
                betaMax = optionalDouble(arguments, "betaMax"),
                betaMin = optionalDouble(arguments, "betaMin")
            ),
            range = range(arguments),
            interval = interval(arguments),
            provider = provider(arguments),
            sortBy = arguments["sortBy"] as? String,
            ascending = booleanArg(arguments, "ascending", true)
        )

    private fun createOrderCommand(arguments: Map<String, Any>): CreateOrderUseCase.CreateOrderCommand {
        @Suppress("UNCHECKED_CAST")
        val items = arguments["items"] as? List<Map<String, Any>> ?: throw IllegalArgumentException("items required")
        return CreateOrderUseCase.CreateOrderCommand(
            customerId = stringArg(arguments, "customerId"),
            items = items.map {
                OrderItem(
                    productId = it["productId"] as? String ?: throw IllegalArgumentException("productId required"),
                    quantity = (it["quantity"] as? Number)?.toInt() ?: throw IllegalArgumentException("quantity required"),
                    unitPrice = BigDecimal(it["unitPrice"] as? String ?: throw IllegalArgumentException("unitPrice required"))
                )
            }
        )
    }

    private fun compareStrategies(arguments: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val strategies = arguments["strategies"] as? List<String> ?: throw IllegalArgumentException("strategies required")
        val results = strategies.map { strategy ->
            val result = runBacktestUseCase.execute(
                RunBacktestUseCase.SingleAssetCommand(
                    ticker = ticker(arguments),
                    strategy = strategy,
                    parameters = objectArg(arguments, "parameters"),
                    range = range(arguments),
                    interval = interval(arguments),
                    provider = provider(arguments),
                    initialCapital = doubleArg(arguments, "initialCapital", 10_000.0),
                    commissionPct = doubleArg(arguments, "commissionPct", 0.001),
                    slippagePct = doubleArg(arguments, "slippagePct", 0.0005)
                )
            )
            strategy to compactBacktestSummary(result)
        }
        return mapOf(
            "symbol" to stringArg(arguments, "symbol"),
            "comparisons" to results.map { mapOf("strategy" to it.first, "summary" to it.second) }
        )
    }

    private fun portfolioAnalysis(arguments: Map<String, Any>): Map<String, Any> {
        val syms = symbols(arguments)
        val weights = weightsArg(arguments, "weights") ?: syms.associateWith { 1.0 / syms.size }
        val riskResults = syms.map { symbol ->
            symbol to calculateMetricsUseCase.calculateRisk(
                CalculateMetricsUseCase.CalculateRiskCommand(
                    ticker = Ticker(symbol),
                    range = range(arguments),
                    interval = interval(arguments),
                    riskFreeRate = doubleArg(arguments, "riskFreeRate", 0.02),
                    provider = provider(arguments)
                )
            )
        }
        val weightedVolatility = riskResults.sumOf { (symbol, result) ->
            (weights[symbol] ?: 0.0) * result.volatility.toDouble()
        }
        return mapOf(
            "symbols" to syms,
            "weights" to weights,
            "weightedVolatility" to weightedVolatility,
            "assetRisk" to riskResults.map { (symbol, result) ->
                mapOf(
                    "symbol" to symbol,
                    "volatility" to result.volatility.toDouble(),
                    "sharpeRatio" to (result.sharpeRatio?.toDouble() ?: Double.NaN),
                    "maxDrawdown" to result.maxDrawdown.toDouble()
                )
            }
        )
    }

    private fun scanPairs(arguments: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val tickers = arguments["tickers"] as? List<String> ?: throw IllegalArgumentException("tickers required")
        val topN = intArg(arguments, "topN", 5)
        val pairs = tickers.flatMapIndexed { i, a ->
            tickers.subList(i + 1, tickers.size).map { b -> a to b }
        }
        val results = pairs.map { (a, b) ->
            @Suppress("UNCHECKED_CAST")
            val result = runAnalysisUseCase.execute(
                RunAnalysisUseCase.CointegrationCommand(
                    assetA = Ticker(a),
                    assetB = Ticker(b),
                    range = range(arguments),
                    interval = interval(arguments),
                    provider = provider(arguments),
                    zScoreWindow = intArg(arguments, "zScoreWindow", 30)
                )
            ) as com.example.starter.analysis.domain.CointegrationResult
            mapOf(
                "pair" to "$a-$b",
                "adfStatistic" to result.adfStatistic,
                "pValue" to result.pValueApprox,
                "hedgeRatio" to result.hedgeRatio,
                "halfLife" to result.halfLife
            )
        }
        return mapOf(
            "pairs" to results.sortedBy { it["pValue"] as? Double ?: Double.MAX_VALUE }.take(topN)
        )
    }

    private fun positionSize(arguments: Map<String, Any>): Map<String, Any> {
        val result = calculateMetricsUseCase.calculateRisk(riskCommand(arguments))
        val volatility = result.volatility.toDouble()
        val targetVol = doubleArg(arguments, "targetVolatility", 0.10)
        val capital = doubleArg(arguments, "capital", 100_000.0)
        val allocation = if (volatility > 0) (targetVol / volatility).coerceAtMost(1.0) else 0.0
        return mapOf(
            "symbol" to stringArg(arguments, "symbol"),
            "capital" to capital,
            "allocationFraction" to allocation,
            "notionalExposure" to capital * allocation,
            "annualizedVolatility" to volatility
        )
    }

    private fun dataQualityReport(arguments: Map<String, Any>): Map<String, Any> {
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = ticker(arguments),
                range = range(arguments),
                interval = interval(arguments),
                provider = provider(arguments)
            )
        )
        val missingVolume = series.count { it.volume <= 0 }
        val zeroPrices = series.count { it.close == BigDecimal.ZERO }
        val ohclcViolations = series.count { it.high < it.low || it.high < it.close || it.low > it.close }
        return mapOf(
            "symbol" to stringArg(arguments, "symbol"),
            "totalBars" to series.size,
            "missingVolume" to missingVolume,
            "zeroPrices" to zeroPrices,
            "ohlcViolations" to ohclcViolations,
            "firstDate" to (series.firstOrNull()?.date?.toString() ?: ""),
            "lastDate" to (series.lastOrNull()?.date?.toString() ?: "")
        )
    }

    private fun marketDataResult(series: com.example.starter.shared.domain.PriceSeries): Map<String, Any> =
        mapOf(
            "symbol" to (series.firstOrNull()?.ticker?.symbol ?: ""),
            "bars" to series.map {
                mapOf(
                    "date" to it.date.toString(),
                    "open" to it.open.toPlainString(),
                    "high" to it.high.toPlainString(),
                    "low" to it.low.toPlainString(),
                    "close" to it.close.toPlainString(),
                    "volume" to it.volume
                )
            }
        )

    private fun indicatorResult(result: com.example.starter.indicators.domain.IndicatorResult): Map<String, Any> =
        mapOf(
            "indicator" to result.indicator,
            "values" to result.values.map {
                mapOf(
                    "date" to it.date.toString(),
                    "value" to (it.value?.toPlainString() ?: "")
                )
            }
        )

    private fun riskMetrics(result: com.example.starter.metrics.domain.RiskMetrics): Map<String, Any> =
        mapOf(
            "sharpeRatio" to (result.sharpeRatio?.toPlainString() ?: ""),
            "sortinoRatio" to (result.sortinoRatio?.toPlainString() ?: ""),
            "maxDrawdown" to result.maxDrawdown.toPlainString(),
            "calmarRatio" to (result.calmarRatio?.toPlainString() ?: ""),
            "var95" to result.var95.toPlainString(),
            "cvar95" to result.cvar95.toPlainString(),
            "volatility" to result.volatility.toPlainString()
        )

    private fun returnMetrics(result: com.example.starter.metrics.domain.ReturnMetrics): Map<String, Any> =
        mapOf(
            "cumulativeReturn" to result.cumulativeReturn.toPlainString(),
            "cagr" to (result.cagr?.toPlainString() ?: ""),
            "annualizedVolatility" to result.annualizedVolatility.toPlainString()
        )

    private fun analysisResult(result: com.example.starter.analysis.domain.AnalysisResult): Map<String, Any> =
        when (result) {
            is com.example.starter.analysis.domain.RegressionResult -> mapOf(
                "operation" to result.operation,
                "alpha" to result.alpha,
                "beta" to result.beta,
                "rSquared" to result.rSquared,
                "annualizedAlpha" to (result.annualizedAlpha ?: Double.NaN)
            )
            is com.example.starter.analysis.domain.CointegrationResult -> mapOf(
                "operation" to result.operation,
                "hedgeRatio" to result.hedgeRatio,
                "adfStatistic" to result.adfStatistic,
                "pValue" to result.pValueApprox,
                "halfLife" to result.halfLife,
                "currentZScore" to (result.currentZScore ?: Double.NaN)
            )
            is com.example.starter.analysis.domain.HurstResult -> mapOf(
                "operation" to result.operation,
                "exponent" to result.exponent,
                "regime" to result.regime,
                "rolling" to (result.rolling ?: emptyList())
            )
            is com.example.starter.analysis.domain.PcaResult -> mapOf(
                "operation" to result.operation,
                "explainedVarianceRatio" to result.explainedVarianceRatio,
                "loadings" to result.loadings,
                "factorReturns" to result.factorReturns
            )
            is com.example.starter.analysis.domain.CorrelationResult -> mapOf(
                "operation" to result.operation,
                "matrix" to result.matrix,
                "average" to result.average,
                "min" to result.min,
                "max" to result.max,
                "diversificationRatio" to (result.diversificationRatio ?: Double.NaN)
            )
            is com.example.starter.analysis.domain.MultiFactorResult -> mapOf(
                "operation" to result.operation,
                "alpha" to result.alpha,
                "loadings" to result.loadings,
                "tStatistics" to result.tStatistics,
                "pValues" to result.pValues,
                "rSquared" to result.rSquared,
                "adjRSquared" to result.adjRSquared
            )
            is com.example.starter.analysis.domain.OptionPricingResult -> mapOf(
                "operation" to result.operation,
                "price" to result.price,
                "greeks" to mapOf(
                    "delta" to result.greeks.delta,
                    "gamma" to result.greeks.gamma,
                    "vega" to result.greeks.vega,
                    "theta" to result.greeks.theta,
                    "rho" to result.greeks.rho
                ),
                "impliedVolatility" to (result.impliedVolatility ?: Double.NaN)
            )
        }

    private fun backtestSummary(result: BacktestResult): Map<String, Any> = mapOf(
        "strategyName" to result.strategyName,
        "initialCapital" to result.initialCapital,
        "finalEquity" to result.finalEquity,
        "totalReturn" to result.totalReturn,
        "trades" to result.trades.size,
        "equityCurvePoints" to result.equityCurve.size,
        "maxDrawdown" to (result.metrics?.maxDrawdown?.toDouble() ?: 0.0),
        "sharpeRatio" to (result.metrics?.sharpeRatio?.toDouble() ?: Double.NaN)
    )

    private fun compactBacktestSummary(result: BacktestResult): Map<String, Any> = mapOf(
        "strategyName" to result.strategyName,
        "totalReturn" to result.totalReturn,
        "trades" to result.trades.size,
        "maxDrawdown" to (result.metrics?.maxDrawdown?.toDouble() ?: 0.0),
        "sharpeRatio" to (result.metrics?.sharpeRatio?.toDouble() ?: Double.NaN)
    )

    private fun backtestDiagnostics(result: BacktestResult): Map<String, Any> {
        val diagnostics = result.diagnostics
        return if (diagnostics != null) {
            mapOf(
                "strategyName" to result.strategyName,
                "numberOfTrades" to diagnostics.numberOfTrades,
                "winRate" to diagnostics.winRate,
                "averageTradeReturn" to diagnostics.averageTradeReturn,
                "expectancy" to diagnostics.expectancy,
                "maxExposure" to diagnostics.maxExposure,
                "annualizedTurnover" to diagnostics.annualizedTurnover
            )
        } else {
            mapOf(
                "strategyName" to result.strategyName,
                "numberOfTrades" to result.trades.size,
                "message" to "Diagnostics not available"
            )
        }
    }

    private fun robustnessDiagnostics(result: BacktestResult): Map<String, Any> =
        mapOf(
            "strategyName" to result.strategyName,
            "simulationPercentiles" to (result.parameterGrid ?: emptyMap<String, Any>())
        )

    private fun portfolioSummary(result: com.example.starter.portfolio.domain.Portfolio): Map<String, Any> =
        mapOf(
            "objective" to result.objective,
            "weights" to result.weights,
            "expectedReturn" to result.expectedReturn,
            "volatility" to result.volatility,
            "sharpeRatio" to (result.sharpeRatio ?: Double.NaN)
        )

    private fun screenerResult(result: com.example.starter.screener.domain.ScreenResult): Map<String, Any> =
        mapOf(
            "matches" to result.matches.map {
                @Suppress("UNCHECKED_CAST")
                mapOf(
                    "ticker" to it.ticker,
                    "peRatio" to (it.fundamentals.peRatio ?: "")
                ) as Map<String, Any>
            },
            "failedTickers" to result.failedTickers
        )

    private fun orderMap(order: com.example.starter.domain.Order): Map<String, Any> = mapOf(
        "orderId" to order.id.toString(),
        "customerId" to order.customerId,
        "status" to order.status.name,
        "totalAmount" to order.totalAmount.toPlainString(),
        "createdAt" to order.createdAt.toString(),
        "items" to order.items.map {
            mapOf(
                "productId" to it.productId,
                "quantity" to it.quantity,
                "unitPrice" to it.unitPrice.toPlainString(),
                "lineTotal" to it.lineTotal.toPlainString()
            )
        }
    )
}
