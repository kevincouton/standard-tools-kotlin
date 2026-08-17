package com.example.starter.agent

import com.example.starter.audit.AuditWriter
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.backtest.domain.BacktestDiagnostics
import com.example.starter.backtest.domain.BacktestResult
import com.example.starter.backtest.domain.EquityCurvePoint
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.indicators.domain.IndicatorResult
import com.example.starter.indicators.domain.IndicatorValue
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.metrics.domain.RiskMetrics
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.InvalidCommandException
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.Ticker
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import java.math.BigDecimal
import java.time.LocalDate

@Tag("integration")
class AgentToolsIntegrationTest {

    private val fetchMarketDataUseCase = mockk<FetchMarketDataUseCase>()
    private val calculateIndicatorUseCase = mockk<CalculateIndicatorUseCase>()
    private val calculateMetricsUseCase = mockk<CalculateMetricsUseCase>()
    private val runBacktestUseCase = mockk<RunBacktestUseCase>()
    private val auditWriter = mockk<AuditWriter>(relaxed = true)

    private val toolRegistry = ToolRegistry()
    private val toolDispatcher = ToolDispatcher(
        createOrderUseCase = mockk(),
        getOrderUseCase = mockk(),
        cancelOrderUseCase = mockk(),
        fetchMarketDataUseCase = fetchMarketDataUseCase,
        calculateIndicatorUseCase = calculateIndicatorUseCase,
        calculateMetricsUseCase = calculateMetricsUseCase,
        runAnalysisUseCase = mockk(),
        runBacktestUseCase = runBacktestUseCase,
        optimizePortfolioUseCase = mockk(),
        screenStocksUseCase = mockk(),
        auditWriter = auditWriter
    )

    @Test
    fun `registry exposes at least 42 tools in OpenAI function format`() {
        expectThat(toolRegistry.tools.size).isGreaterThanOrEqualTo(42)
        val first = toolRegistry.tools.first()
        expectThat(first["type"]).isEqualTo("function")
        @Suppress("UNCHECKED_CAST")
        val function = first["function"] as Map<String, Any>
        expectThat(function["name"]).isNotNull()
        expectThat(function["description"]).isNotNull()
        expectThat(function["parameters"]).isNotNull()
    }

    @Test
    fun `registry includes expected core and advanced tools`() {
        val names = toolRegistry.definitions.map { it.name }
        expectThat(names).isEqualTo(
            listOf(
                "run_sma_backtest", "run_rsi_backtest", "run_macd_backtest", "run_bollinger_backtest",
                "run_buy_and_hold", "compare_strategies", "analyze_stock_risk", "get_technical_analysis",
                "get_portfolio_analysis", "run_screener", "run_factor_regression", "analysis_regression",
                "analysis_multi_factor", "run_cointegration_test", "run_pca_analysis", "run_hurst_analysis",
                "run_regime_adaptive_backtest", "scan_pairs", "run_walk_forward_backtest",
                "get_portfolio_risk_attribution", "get_position_size", "get_stock_fundamentals",
                "run_backtest_optimization", "get_advanced_indicators", "get_rolling_beta",
                "get_extended_risk_metrics", "run_custom_signal_backtest", "run_signal_panel_backtest",
                "run_regime_adaptive_walkforward_backtest", "get_backtest_diagnostics",
                "run_portfolio_simulation", "run_pair_trade_backtest", "get_robustness_diagnostics",
                "get_capacity_report", "get_data_quality_report", "run_backtest_compact",
                "run_portfolio_optimization", "portfolio_black_litterman", "get_option_pricing",
                "get_implied_volatility", "get_volatility_estimators", "get_correlation_analysis",
                "run_monte_carlo_simulation", "run_stress_test", "get_liquidity_metrics",
                "marketdata_fetch", "indicators_calculate", "metrics_risk", "metrics_return",
                "create_order", "get_order", "cancel_order", "screener_run"
            )
        )
    }

    @Test
    fun `dispatch marketdata_fetch returns bars`() {
        every { fetchMarketDataUseCase.fetch(any()) } returns listOf(
            OHLCV(
                ticker = Ticker("AAPL"),
                date = LocalDate.of(2024, 1, 2),
                open = BigDecimal("100"),
                high = BigDecimal("101"),
                low = BigDecimal("99"),
                close = BigDecimal("100.5"),
                volume = 1_000_000
            )
        )

        val result = toolDispatcher.dispatch(
            "marketdata_fetch",
            mapOf(
                "symbol" to "AAPL",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY"
            )
        )

        @Suppress("UNCHECKED_CAST")
        val bars = result["bars"] as List<Map<String, Any>>
        expectThat(bars).hasSize(1)
        expectThat(bars.first()["symbol"] ?: bars.first()["date"]).isEqualTo("2024-01-02")
    }

    @Test
    fun `dispatch indicators_calculate returns indicator values`() {
        every { calculateIndicatorUseCase.calculate(any()) } returns IndicatorResult(
            indicator = "sma",
            parameters = emptyMap(),
            values = listOf(IndicatorValue(date = LocalDate.of(2024, 1, 2), value = BigDecimal("100.5")))
        )

        val result = toolDispatcher.dispatch(
            "indicators_calculate",
            mapOf(
                "symbol" to "AAPL",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY",
                "indicator" to "sma"
            )
        )

        expectThat(result["indicator"]).isEqualTo("sma")
        @Suppress("UNCHECKED_CAST")
        val values = result["values"] as List<Map<String, Any>>
        expectThat(values).hasSize(1)
    }

    @Test
    fun `dispatch metrics_risk returns risk metrics`() {
        every { calculateMetricsUseCase.calculateRisk(any()) } returns RiskMetrics(
            sharpeRatio = BigDecimal("1.2"),
            sortinoRatio = BigDecimal("1.5"),
            maxDrawdown = BigDecimal("0.1"),
            calmarRatio = BigDecimal("2.0"),
            var95 = BigDecimal("0.02"),
            cvar95 = BigDecimal("0.03"),
            volatility = BigDecimal("0.15")
        )

        val result = toolDispatcher.dispatch(
            "metrics_risk",
            mapOf(
                "symbol" to "AAPL",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY"
            )
        )

        expectThat(result["sharpeRatio"]).isEqualTo("1.2")
        expectThat(result["volatility"]).isEqualTo("0.15")
    }

    @Test
    fun `dispatch run_buy_and_hold returns backtest summary`() {
        every { runBacktestUseCase.execute(any()) } returns BacktestResult(
            strategyName = "buy_and_hold",
            initialCapital = 10_000.0,
            finalEquity = 11_000.0,
            totalReturn = 0.10,
            metrics = null,
            trades = emptyList(),
            equityCurve = listOf(
                EquityCurvePoint(LocalDate.of(2024, 1, 2), 10_000.0, 0.0),
                EquityCurvePoint(LocalDate.of(2024, 1, 3), 11_000.0, 0.0)
            ),
            drawdownEpisodes = emptyList(),
            diagnostics = BacktestDiagnostics(
                numberOfTrades = 1,
                winRate = 1.0,
                averageTradeReturn = 0.10,
                expectancy = 0.10,
                maxExposure = 1.0,
                annualizedTurnover = 0.5
            )
        )

        val result = toolDispatcher.dispatch(
            "run_buy_and_hold",
            mapOf(
                "symbol" to "AAPL",
                "strategy" to "buy_and_hold",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY"
            )
        )

        expectThat(result["strategyName"]).isEqualTo("buy_and_hold")
        expectThat(result["totalReturn"]).isEqualTo(0.10)
    }

    @Test
    fun `dispatch run_sma_backtest maps to sma_crossover strategy`() {
        every { runBacktestUseCase.execute(any()) } returns BacktestResult(
            strategyName = "sma_crossover",
            initialCapital = 10_000.0,
            finalEquity = 10_500.0,
            totalReturn = 0.05,
            metrics = null,
            trades = emptyList(),
            equityCurve = emptyList(),
            drawdownEpisodes = emptyList(),
            diagnostics = null
        )

        toolDispatcher.dispatch(
            "run_sma_backtest",
            mapOf(
                "symbol" to "AAPL",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY"
            )
        )

        val command = com.example.starter.backtest.application.port.inbound.RunBacktestUseCase.SingleAssetCommand(
            ticker = Ticker("AAPL"),
            strategy = "sma_crossover",
            parameters = mapOf("fast" to 10, "slow" to 30),
            range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)),
            interval = BarInterval.DAILY
        )
        expectThat(command.strategy).isEqualTo("sma_crossover")
    }

    @Test
    fun `dispatch unknown tool throws InvalidCommandException`() {
        val thrown = org.junit.jupiter.api.assertThrows<InvalidCommandException> {
            toolDispatcher.dispatch("unknown_tool", emptyMap())
        }
        expectThat(thrown.message).isEqualTo("Invalid command: Unknown tool: unknown_tool")
    }

    @Test
    fun `dispatch capacity report returns capacity estimate`() {
        every { fetchMarketDataUseCase.fetch(any()) } returns listOf(
            OHLCV(
                ticker = Ticker("AAPL"),
                date = LocalDate.of(2024, 1, 2),
                open = BigDecimal("100"),
                high = BigDecimal("101"),
                low = BigDecimal("99"),
                close = BigDecimal("100.5"),
                volume = 1_000_000
            )
        )

        val result = toolDispatcher.dispatch(
            "get_capacity_report",
            mapOf(
                "symbol" to "AAPL",
                "startDate" to "2024-01-01",
                "endDate" to "2024-01-31",
                "interval" to "DAILY"
            )
        )
        expectThat(result["symbol"]).isEqualTo("AAPL")
        expectThat(result["estimatedCapacity"]).isNotNull()
    }
}
