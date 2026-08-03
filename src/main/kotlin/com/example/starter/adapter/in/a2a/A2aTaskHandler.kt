package com.example.starter.adapter.`in`.a2a

import com.example.starter.agent.ToolDispatcher
import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.shared.domain.InvalidCommandException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/a2a")
class A2aTaskHandler(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val toolDispatcher: ToolDispatcher
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
        } catch (ex: NotImplementedError) {
            JsonRpcResponse.error(request.id, -32602, ex.message ?: "Not implemented")
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
                A2aOrderSkillMapper.toTaskResult(
                    createOrderUseCase.createOrder(CreateOrderUseCase.CreateOrderCommand(customerId, items))
                )
            }
            "cancel-order" -> {
                val orderId = params["orderId"] as? String
                    ?: throw IllegalArgumentException("orderId required")
                A2aOrderSkillMapper.toTaskResult(cancelOrderUseCase.cancelOrder(UUID.fromString(orderId)))
            }
            else -> {
                val toolName = SKILL_TO_TOOL[skillId]
                    ?: return JsonRpcResponse.error(request.id, -32602, "Unknown skill: $skillId")
                toolDispatcher.dispatch(toolName, params)
            }
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

    companion object {
        private val SKILL_TO_TOOL = mapOf(
            "marketdata-fetch" to "marketdata_fetch",
            "indicators-calculate" to "indicators_calculate",
            "metrics-risk" to "metrics_risk",
            "metrics-return" to "metrics_return",
            "analysis-regression" to "analysis_regression",
            "analysis-cointegration" to "run_cointegration_test",
            "analysis-hurst" to "run_hurst_analysis",
            "analysis-pca" to "run_pca_analysis",
            "analysis-correlation" to "get_correlation_analysis",
            "analysis-multi-factor" to "analysis_multi_factor",
            "analysis-option" to "get_option_pricing",
            "backtest-single" to "run_backtest_compact",
            "backtest-portfolio" to "run_portfolio_simulation",
            "backtest-pair" to "run_pair_trade_backtest",
            "backtest-walk-forward" to "run_walk_forward_backtest",
            "backtest-monte-carlo" to "run_monte_carlo_simulation",
            "portfolio-optimize" to "run_portfolio_optimization",
            "portfolio-risk-parity" to "get_portfolio_risk_attribution",
            "portfolio-black-litterman" to "portfolio_black_litterman",
            "screener-run" to "run_screener"
        )
    }
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
