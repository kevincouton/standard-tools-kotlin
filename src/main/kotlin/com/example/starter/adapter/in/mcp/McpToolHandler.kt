package com.example.starter.adapter.`in`.mcp

import com.example.starter.agent.ToolDispatcher
import com.example.starter.agent.ToolRegistry
import org.springframework.stereotype.Component

@Component
class McpToolHandler(
    private val toolRegistry: ToolRegistry,
    private val toolDispatcher: ToolDispatcher
) {

    fun toolsList(): Map<String, Any> = mapOf(
        "tools" to toolRegistry.definitions.map {
            mapOf(
                "name" to it.name,
                "description" to it.description,
                "inputSchema" to it.parameters
            )
        }
    )

    fun handleToolCall(name: String, arguments: Map<String, Any>): Map<String, Any> {
        val result = toolDispatcher.dispatch(name, arguments)
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to renderText(name, result)
                )
            )
        )
    }

    private fun renderText(name: String, result: Map<String, Any>): String {
        return when (name) {
            "marketdata_fetch" -> {
                @Suppress("UNCHECKED_CAST")
                val bars = result["bars"] as? List<Map<String, Any>> ?: emptyList()
                val symbol = result["symbol"] as? String ?: ""
                "Fetched ${bars.size} bars for $symbol"
            }
            "indicators_calculate", "get_technical_analysis", "get_advanced_indicators" -> {
                val indicator = result["indicator"] as? String ?: ""
                @Suppress("UNCHECKED_CAST")
                val values = result["values"] as? List<Any> ?: emptyList()
                "Calculated $indicator: ${values.size} values"
            }
            "metrics_risk", "analyze_stock_risk", "get_extended_risk_metrics", "get_volatility_estimators" -> {
                val symbol = result["symbol"] as? String ?: ""
                "Risk metrics for $symbol: max drawdown ${result["maxDrawdown"]}, volatility ${result["volatility"]}, Sharpe ${result["sharpeRatio"] ?: "n/a"}, Sortino ${result["sortinoRatio"] ?: "n/a"}"
            }
            "metrics_return" -> {
                val symbol = result["symbol"] as? String ?: ""
                "Return metrics for $symbol: cumulative ${result["cumulativeReturn"]}, CAGR ${result["cagr"] ?: "n/a"}, ann. volatility ${result["annualizedVolatility"]}"
            }
            "create_order", "get_order", "cancel_order" -> {
                @Suppress("UNCHECKED_CAST")
                val order = result["order"] as? Map<String, Any> ?: result
                "Order ${order["orderId"]}: status ${order["status"]}, total ${order["totalAmount"]}"
            }
            "screener_run", "run_screener", "get_stock_fundamentals" -> {
                @Suppress("UNCHECKED_CAST")
                val matches = result["matches"] as? List<Any> ?: emptyList()
                val failed = result["failedTickers"] as? List<Any> ?: emptyList()
                "Screen matched ${matches.size} ticker(s)" +
                    (if (failed.isNotEmpty()) "; failed: $failed" else "")
            }
            "run_portfolio_optimization", "get_portfolio_risk_attribution", "run_portfolio_simulation" -> {
                "Portfolio ${result["objective"]}: weights ${result["weights"]}, expected return ${result["expectedReturn"]}, volatility ${result["volatility"]}, Sharpe ${result["sharpeRatio"] ?: "n/a"}"
            }
            "run_buy_and_hold", "run_sma_backtest", "run_rsi_backtest", "run_macd_backtest",
            "run_bollinger_backtest", "run_regime_adaptive_backtest", "run_backtest_compact" -> {
                "Backtest ${result["strategyName"]}: final equity ${result["finalEquity"]}, total return ${result["totalReturn"]}, trades ${result["trades"]}, max drawdown ${result["maxDrawdown"]}, Sharpe ${result["sharpeRatio"] ?: "n/a"}"
            }
            "run_walk_forward_backtest", "run_regime_adaptive_walkforward_backtest", "run_backtest_optimization" -> {
                "Walk-forward ${result["strategyName"]}: final equity ${result["finalEquity"]}, total return ${result["totalReturn"]}, trades ${result["trades"]}, max drawdown ${result["maxDrawdown"]}, Sharpe ${result["sharpeRatio"] ?: "n/a"}"
            }
            "run_pair_trade_backtest" -> {
                "Pair backtest ${result["strategyName"]}: final equity ${result["finalEquity"]}, total return ${result["totalReturn"]}, trades ${result["trades"]}, max drawdown ${result["maxDrawdown"]}, Sharpe ${result["sharpeRatio"] ?: "n/a"}"
            }
            "run_monte_carlo_simulation", "get_robustness_diagnostics" -> {
                "Monte Carlo ${result["strategyName"]}: percentiles ${result["simulationPercentiles"]}"
            }
            "get_option_pricing", "get_implied_volatility" -> {
                "Option result: $result"
            }
            else -> result.toString()
        }
    }
}
