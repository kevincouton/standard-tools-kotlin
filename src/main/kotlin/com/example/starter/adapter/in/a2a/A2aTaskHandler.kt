package com.example.starter.adapter.`in`.a2a

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.Ticker
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/a2a")
class A2aTaskHandler(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val fetchMarketDataUseCase: FetchMarketDataUseCase,
    private val calculateIndicatorUseCase: CalculateIndicatorUseCase,
    private val calculateMetricsUseCase: CalculateMetricsUseCase,
    private val runAnalysisUseCase: RunAnalysisUseCase,
    private val runBacktestUseCase: RunBacktestUseCase,
    private val optimizePortfolioUseCase: OptimizePortfolioUseCase
) {

    @PostMapping("/tasks", consumes = ["application/json"], produces = ["application/json"])
    fun handleTask(@RequestBody request: JsonRpcRequest): Mono<JsonRpcResponse> {
        return Mono.fromCallable { dispatch(request) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun dispatch(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            when (request.method) {
                "tasks/send" -> handleTasksSend(request)
                "tasks/get" -> handleTasksGet(request)
                "tasks/cancel" -> handleTasksCancel(request)
                else -> JsonRpcResponse.error(request.id, -32601, "Method not found")
            }
        } catch (ex: InvalidCommandException) {
            JsonRpcResponse.error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: IllegalArgumentException) {
            JsonRpcResponse.error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: Exception) {
            JsonRpcResponse.error(request.id, -32603, ex.message ?: "Internal error")
        }
    }

    private fun handleTasksSend(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse.error(request.id, -32602, "Missing params")
        val skillId = params["skillId"] as? String
            ?: return JsonRpcResponse.error(request.id, -32602, "Missing skillId")
        val taskId = params["taskId"] as? String ?: UUID.randomUUID().toString()

        val result = when (skillId) {
            "create-order" -> {
                val customerId = params["customerId"] as? String
                    ?: throw IllegalArgumentException("customerId required")
                val items = parseItems(params["items"])
                createOrderUseCase.createOrder(CreateOrderUseCase.CreateOrderCommand(customerId, items))
                    .let { A2aOrderSkillMapper.toTaskResult(it) }
            }
            "cancel-order" -> {
                val orderId = params["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                cancelOrderUseCase.cancelOrder(UUID.fromString(orderId))
                    .let { A2aOrderSkillMapper.toTaskResult(it) }
            }
            "marketdata-fetch" -> {
                val symbol = params["symbol"] as? String ?: throw InvalidCommandException("symbol required")
                if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
                val exchange = params["exchange"] as? String
                val startDate = params["startDate"] as? String ?: throw InvalidCommandException("startDate required")
                val endDate = params["endDate"] as? String ?: throw InvalidCommandException("endDate required")
                val interval = params["interval"] as? String ?: "DAILY"
                val provider = params["provider"] as? String
                val series = fetchMarketDataUseCase.fetch(
                    FetchMarketDataUseCase.FetchMarketDataCommand(
                        ticker = Ticker(symbol, exchange),
                        range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                        interval = parseInterval(interval),
                        provider = provider
                    )
                )
                mapOf(
                    "symbol" to symbol,
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
            }
            "indicators-calculate" -> {
                val symbol = params["symbol"] as? String ?: throw InvalidCommandException("symbol required")
                if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
                val exchange = params["exchange"] as? String
                val startDate = params["startDate"] as? String ?: throw InvalidCommandException("startDate required")
                val endDate = params["endDate"] as? String ?: throw InvalidCommandException("endDate required")
                val interval = params["interval"] as? String ?: "DAILY"
                val indicator = params["indicator"] as? String ?: throw InvalidCommandException("indicator required")
                val parameters = (params["parameters"] as? Map<String, Any>) ?: emptyMap()
                val provider = params["provider"] as? String
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
                mapOf(
                    "indicator" to result.indicator,
                    "values" to result.values.map {
                        mapOf(
                            "date" to it.date.toString(),
                            "value" to (it.value?.toPlainString() ?: "")
                        )
                    }
                )
            }
            "metrics-risk" -> {
                val symbol = params["symbol"] as? String ?: throw InvalidCommandException("symbol required")
                if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
                val exchange = params["exchange"] as? String
                val startDate = params["startDate"] as? String ?: throw InvalidCommandException("startDate required")
                val endDate = params["endDate"] as? String ?: throw InvalidCommandException("endDate required")
                val interval = params["interval"] as? String ?: "DAILY"
                val riskFreeRate = (params["riskFreeRate"] as? Number)?.toDouble() ?: 0.02
                val provider = params["provider"] as? String
                val result = calculateMetricsUseCase.calculateRisk(
                    CalculateMetricsUseCase.CalculateRiskCommand(
                        ticker = Ticker(symbol, exchange),
                        range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                        interval = parseInterval(interval),
                        riskFreeRate = riskFreeRate,
                        provider = provider
                    )
                )
                mapOf(
                    "sharpeRatio" to (result.sharpeRatio?.toPlainString() ?: ""),
                    "sortinoRatio" to (result.sortinoRatio?.toPlainString() ?: ""),
                    "maxDrawdown" to result.maxDrawdown.toPlainString(),
                    "calmarRatio" to (result.calmarRatio?.toPlainString() ?: ""),
                    "var95" to result.var95.toPlainString(),
                    "cvar95" to result.cvar95.toPlainString(),
                    "volatility" to result.volatility.toPlainString()
                )
            }
            "metrics-return" -> {
                val symbol = params["symbol"] as? String ?: throw InvalidCommandException("symbol required")
                if (symbol.isBlank()) throw InvalidCommandException("symbol must not be blank")
                val exchange = params["exchange"] as? String
                val startDate = params["startDate"] as? String ?: throw InvalidCommandException("startDate required")
                val endDate = params["endDate"] as? String ?: throw InvalidCommandException("endDate required")
                val interval = params["interval"] as? String ?: "DAILY"
                val provider = params["provider"] as? String
                val result = calculateMetricsUseCase.calculateReturn(
                    CalculateMetricsUseCase.CalculateReturnCommand(
                        ticker = Ticker(symbol, exchange),
                        range = DateRange(LocalDate.parse(startDate), LocalDate.parse(endDate)),
                        interval = parseInterval(interval),
                        provider = provider
                    )
                )
                mapOf(
                    "cumulativeReturn" to result.cumulativeReturn.toPlainString(),
                    "cagr" to (result.cagr?.toPlainString() ?: ""),
                    "annualizedVolatility" to result.annualizedVolatility.toPlainString()
                )
            }
            "analysis-regression" -> {
                val asset = params["asset"] as? String ?: throw IllegalArgumentException("asset required")
                val benchmark = params["benchmark"] as? String ?: throw IllegalArgumentException("benchmark required")
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.RegressionCommand(
                        asset = Ticker(asset),
                        benchmark = Ticker(benchmark),
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String
                    )
                )
            }
            "analysis-cointegration" -> {
                val assetA = params["assetA"] as? String ?: throw IllegalArgumentException("assetA required")
                val assetB = params["assetB"] as? String ?: throw IllegalArgumentException("assetB required")
                val zScoreWindow = (params["zScoreWindow"] as? Number)?.toInt() ?: 30
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CointegrationCommand(
                        assetA = Ticker(assetA),
                        assetB = Ticker(assetB),
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        zScoreWindow = zScoreWindow
                    )
                )
            }
            "analysis-hurst" -> {
                val symbol = params["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
                val method = params["method"] as? String ?: "dfa"
                val rollingWindow = (params["rollingWindow"] as? Number)?.toInt()
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.HurstCommand(
                        ticker = Ticker(symbol),
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        method = method,
                        rollingWindow = rollingWindow
                    )
                )
            }
            "analysis-pca" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                val nComponents = (params["nComponents"] as? Number)?.toInt()
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.PcaCommand(
                        tickers = symbols.map { Ticker(it) },
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        nComponents = nComponents
                    )
                )
            }
            "analysis-correlation" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.CorrelationCommand(
                        tickers = symbols.map { Ticker(it) },
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String
                    )
                )
            }
            "analysis-multi-factor" -> {
                val asset = params["asset"] as? String ?: throw IllegalArgumentException("asset required")
                val factors = params["factors"] as? Map<String, String> ?: throw IllegalArgumentException("factors required")
                runAnalysisUseCase.execute(
                    RunAnalysisUseCase.MultiFactorCommand(
                        asset = Ticker(asset),
                        factors = factors.mapValues { Ticker(it.value) },
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String
                    )
                )
            }
            "analysis-option" -> {
                val spot = (params["spot"] as? Number)?.toDouble() ?: throw IllegalArgumentException("spot required")
                val strike = (params["strike"] as? Number)?.toDouble() ?: throw IllegalArgumentException("strike required")
                val timeToExpiry = (params["timeToExpiry"] as? Number)?.toDouble() ?: throw IllegalArgumentException("timeToExpiry required")
                val riskFreeRate = (params["riskFreeRate"] as? Number)?.toDouble() ?: 0.05
                val volatility = (params["volatility"] as? Number)?.toDouble() ?: throw IllegalArgumentException("volatility required")
                val optionType = params["optionType"] as? String ?: "call"
                val dividendYield = (params["dividendYield"] as? Number)?.toDouble() ?: 0.0
                val marketPrice = (params["marketPrice"] as? Number)?.toDouble()
                runAnalysisUseCase.execute(
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
            }
            "backtest-single" -> {
                val symbol = params["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
                val strategy = params["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
                val parameters = (params["parameters"] as? Map<String, Any>) ?: emptyMap()
                val result = runBacktestUseCase.execute(
                    RunBacktestUseCase.SingleAssetCommand(
                        ticker = Ticker(symbol),
                        strategy = strategy,
                        parameters = parameters,
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        initialCapital = (params["initialCapital"] as? Number)?.toDouble() ?: 10_000.0,
                        commissionPct = (params["commissionPct"] as? Number)?.toDouble() ?: 0.001,
                        slippagePct = (params["slippagePct"] as? Number)?.toDouble() ?: 0.0005
                    )
                )
                backtestSummary(result)
            }
            "backtest-portfolio" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                @Suppress("UNCHECKED_CAST")
                val weights = (params["weights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() } ?: emptyMap()
                val result = runBacktestUseCase.execute(
                    RunBacktestUseCase.PortfolioSimulationCommand(
                        tickers = symbols.map { Ticker(it) },
                        weights = weights,
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        initialCapital = (params["initialCapital"] as? Number)?.toDouble() ?: 10_000.0,
                        commissionPct = (params["commissionPct"] as? Number)?.toDouble() ?: 0.001,
                        maxGrossLeverage = (params["maxGrossLeverage"] as? Number)?.toDouble() ?: 1.0
                    )
                )
                backtestSummary(result)
            }
            "backtest-pair" -> {
                val symbolA = params["symbolA"] as? String ?: throw IllegalArgumentException("symbolA required")
                val symbolB = params["symbolB"] as? String ?: throw IllegalArgumentException("symbolB required")
                val result = runBacktestUseCase.execute(
                    RunBacktestUseCase.PairTradeCommand(
                        symbolA = symbolA,
                        symbolB = symbolB,
                        entryZ = (params["entryZ"] as? Number)?.toDouble() ?: 2.0,
                        exitZ = (params["exitZ"] as? Number)?.toDouble() ?: 0.5,
                        zScoreWindow = (params["zScoreWindow"] as? Number)?.toInt() ?: 30,
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        initialCapital = (params["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
                    )
                )
                backtestSummary(result)
            }
            "backtest-walk-forward" -> {
                val symbol = params["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
                val strategy = params["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
                @Suppress("UNCHECKED_CAST")
                val parameterGrid = (params["parameterGrid"] as? Map<String, List<Any>>) ?: emptyMap()
                val result = runBacktestUseCase.execute(
                    RunBacktestUseCase.WalkForwardCommand(
                        ticker = Ticker(symbol),
                        strategy = strategy,
                        parameterGrid = parameterGrid,
                        trainSize = (params["trainSize"] as? Number)?.toInt() ?: 252,
                        testSize = (params["testSize"] as? Number)?.toInt() ?: 63,
                        metric = params["metric"] as? String ?: "sharpe_ratio",
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        initialCapital = (params["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
                    )
                )
                backtestSummary(result)
            }
            "backtest-monte-carlo" -> {
                val symbol = params["symbol"] as? String ?: throw IllegalArgumentException("symbol required")
                val strategy = params["strategy"] as? String ?: throw IllegalArgumentException("strategy required")
                val parameters = (params["parameters"] as? Map<String, Any>) ?: emptyMap()
                val result = runBacktestUseCase.execute(
                    RunBacktestUseCase.MonteCarloCommand(
                        ticker = Ticker(symbol),
                        strategy = strategy,
                        parameters = parameters,
                        horizonDays = (params["horizonDays"] as? Number)?.toInt() ?: 252,
                        nSimulations = (params["nSimulations"] as? Number)?.toInt() ?: 1_000,
                        blockSize = (params["blockSize"] as? Number)?.toInt() ?: 20,
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        initialCapital = (params["initialCapital"] as? Number)?.toDouble() ?: 10_000.0
                    )
                )
                backtestSummary(result)
            }
            "portfolio-optimize" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                val objective = params["objective"] as? String ?: "max_sharpe"
                val result = optimizePortfolioUseCase.optimize(
                    OptimizePortfolioUseCase.OptimizeCommand(
                        tickers = symbols.map { Ticker(it) },
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        objective = objective,
                        riskFreeRate = (params["riskFreeRate"] as? Number)?.toDouble() ?: 0.02,
                        targetReturn = (params["targetReturn"] as? Number)?.toDouble(),
                        targetVolatility = (params["targetVolatility"] as? Number)?.toDouble(),
                        allowShort = (params["allowShort"] as? Boolean) ?: false,
                        maxWeight = (params["maxWeight"] as? Number)?.toDouble()
                    )
                )
                portfolioSummary(result)
            }
            "portfolio-risk-parity" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                @Suppress("UNCHECKED_CAST")
                val riskBudget = (params["riskBudget"] as? Map<String, Number>)?.mapValues { it.value.toDouble() }
                val result = optimizePortfolioUseCase.riskParity(
                    OptimizePortfolioUseCase.RiskParityCommand(
                        tickers = symbols.map { Ticker(it) },
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        riskBudget = riskBudget
                    )
                )
                portfolioSummary(result)
            }
            "portfolio-black-litterman" -> {
                val symbols = params["symbols"] as? List<String> ?: throw IllegalArgumentException("symbols required")
                @Suppress("UNCHECKED_CAST")
                val marketWeights = (params["marketWeights"] as? Map<String, Number>)?.mapValues { it.value.toDouble() }
                    ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val views = (params["views"] as? List<Map<String, Any>>)?.map {
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
                        range = parseRange(params),
                        interval = parseInterval(params),
                        provider = params["provider"] as? String,
                        riskAversion = (params["riskAversion"] as? Number)?.toDouble() ?: 2.5,
                        tau = (params["tau"] as? Number)?.toDouble() ?: 0.05
                    )
                )
                portfolioSummary(result)
            }
            else -> return JsonRpcResponse.error(request.id, -32602, "Unknown skill: $skillId")
        }

        return JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = mapOf(
                "taskId" to taskId,
                "status" to "completed",
                "result" to result
            )
        )
    }

    private fun handleTasksGet(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse.error(request.id, -32602, "Missing params")
        val orderId = params["orderId"] as? String
            ?: return JsonRpcResponse.error(request.id, -32602, "Missing orderId")
        val order = getOrderUseCase.getOrder(UUID.fromString(orderId))
        return JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = A2aOrderSkillMapper.toTaskResult(order)
        )
    }

    private fun handleTasksCancel(request: JsonRpcRequest): JsonRpcResponse {
        val params = (request.params ?: emptyMap()) + ("skillId" to "cancel-order")
        return handleTasksSend(request.copy(method = "tasks/send", params = params))
    }

    private fun parseInterval(interval: String): BarInterval {
        return BarInterval.entries.find { it.name.equals(interval.trim(), ignoreCase = true) }
            ?: throw InvalidCommandException(
                "interval must be one of ${BarInterval.entries.joinToString { it.name }}"
            )
    }

    private fun parseRange(params: Map<String, Any>): DateRange {
        val start = params["startDate"] as? String ?: throw IllegalArgumentException("startDate required")
        val end = params["endDate"] as? String ?: throw IllegalArgumentException("endDate required")
        return DateRange(LocalDate.parse(start), LocalDate.parse(end))
    }

    private fun parseInterval(params: Map<String, Any>): BarInterval {
        val interval = params["interval"] as? String ?: "DAILY"
        return parseInterval(interval)
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

    private fun portfolioSummary(result: com.example.starter.portfolio.domain.Portfolio): Map<String, Any> = mapOf(
        "objective" to result.objective,
        "weights" to result.weights,
        "expectedReturn" to result.expectedReturn,
        "volatility" to result.volatility,
        "sharpeRatio" to (result.sharpeRatio ?: Double.NaN)
    )
}

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: Map<String, Any>? = null
)

data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any? = null,
    val error: JsonRpcError? = null
) {
    companion object {
        fun error(id: String?, code: Int, message: String): JsonRpcResponse =
            JsonRpcResponse(id = id, error = JsonRpcError(code, message))
    }
}

data class JsonRpcError(
    val code: Int,
    val message: String
)
