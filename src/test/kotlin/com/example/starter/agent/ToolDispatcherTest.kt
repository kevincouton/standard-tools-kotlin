package com.example.starter.agent

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.audit.AuditWriter
import com.example.starter.backtest.application.port.inbound.RunBacktestUseCase
import com.example.starter.indicators.application.port.inbound.CalculateIndicatorUseCase
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.metrics.application.port.inbound.CalculateMetricsUseCase
import com.example.starter.portfolio.application.port.inbound.OptimizePortfolioUseCase
import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.shared.domain.InvalidCommandException
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("unit")
class ToolDispatcherTest {

    private val createOrderUseCase = mockk<CreateOrderUseCase>()
    private val getOrderUseCase = mockk<GetOrderUseCase>()
    private val cancelOrderUseCase = mockk<CancelOrderUseCase>()
    private val fetchMarketDataUseCase = mockk<FetchMarketDataUseCase>()
    private val calculateIndicatorUseCase = mockk<CalculateIndicatorUseCase>()
    private val calculateMetricsUseCase = mockk<CalculateMetricsUseCase>()
    private val runAnalysisUseCase = mockk<RunAnalysisUseCase>()
    private val runBacktestUseCase = mockk<RunBacktestUseCase>()
    private val optimizePortfolioUseCase = mockk<OptimizePortfolioUseCase>()
    private val screenStocksUseCase = mockk<ScreenStocksUseCase>()
    private val auditWriter = mockk<AuditWriter>(relaxed = true)

    private val dispatcher = ToolDispatcher(
        createOrderUseCase = createOrderUseCase,
        getOrderUseCase = getOrderUseCase,
        cancelOrderUseCase = cancelOrderUseCase,
        fetchMarketDataUseCase = fetchMarketDataUseCase,
        calculateIndicatorUseCase = calculateIndicatorUseCase,
        calculateMetricsUseCase = calculateMetricsUseCase,
        runAnalysisUseCase = runAnalysisUseCase,
        runBacktestUseCase = runBacktestUseCase,
        optimizePortfolioUseCase = optimizePortfolioUseCase,
        screenStocksUseCase = screenStocksUseCase,
        auditWriter = auditWriter
    )

    @Test
    fun `unknown tool throws InvalidCommandException`() {
        val exception = assertThrows<InvalidCommandException> {
            dispatcher.dispatch("unknown_tool", emptyMap())
        }

        expectThat(exception.message).isEqualTo("Invalid command: Unknown tool: unknown_tool")
        verify(exactly = 1) { auditWriter.write(any(), "unknown_tool", any(), any(), any(), "", "error", any(), any()) }
    }
}
