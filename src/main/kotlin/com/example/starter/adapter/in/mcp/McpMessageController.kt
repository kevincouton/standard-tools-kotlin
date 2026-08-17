package com.example.starter.adapter.`in`.mcp

import com.example.starter.shared.domain.InvalidCommandException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class McpMessageController(
    private val toolHandler: McpToolHandler,
    private val sessionManager: McpSessionManager
) {

    @PostMapping("/mcp/messages", consumes = ["application/json"], produces = ["application/json"])
    fun message(
        @RequestParam sessionId: String,
        @RequestBody request: McpJsonRpcRequest
    ): Mono<ResponseEntity<Any>> {
        return Mono.fromCallable { dispatch(request) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { response ->
                if (sessionManager.hasSession(sessionId)) {
                    sessionManager.send(sessionId, response)
                    ResponseEntity.accepted().build<Any>()
                } else {
                    ResponseEntity.ok(response)
                }
            }
    }

    private fun dispatch(request: McpJsonRpcRequest): McpJsonRpcResponse {
        return try {
            when (request.method) {
                "initialize" -> McpJsonRpcResponse(
                    id = request.id,
                    result = mapOf(
                        "protocolVersion" to "2024-11-05",
                        "capabilities" to emptyMap<String, Any>(),
                        "serverInfo" to mapOf("name" to "standard-tools-mcp", "version" to "1.0.0")
                    )
                )
                "tools/call" -> {
                    val name = request.params?.get("name") as? String
                        ?: return error(request.id, -32602, "Missing tool name")
                    val arguments = request.params["arguments"] as? Map<String, Any> ?: emptyMap()
                    val result = toolHandler.handleToolCall(name, arguments)
                    McpJsonRpcResponse(id = request.id, result = result)
                }
                else -> error(request.id, -32601, "Method not found")
            }
        } catch (ex: InvalidCommandException) {
            error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: IllegalArgumentException) {
            error(request.id, -32602, ex.message ?: "Invalid params")
        } catch (ex: Exception) {
            error(request.id, -32603, ex.message ?: "Internal error")
        }
    }

    private fun error(id: String?, code: Int, message: String): McpJsonRpcResponse =
        McpJsonRpcResponse(id = id, error = McpJsonRpcError(code, message))
}

data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: Map<String, Any>? = null
)

data class McpJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: Any? = null,
    val error: McpJsonRpcError? = null
)

data class McpJsonRpcError(
    val code: Int,
    val message: String
)
