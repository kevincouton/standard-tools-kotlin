package com.example.starter.adapter.`in`.mcp

import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared session manager for the MCP SSE transport.
 *
 * Clients connect via GET /mcp/sse and POST requests to /mcp/messages.
 * Replies must flow back through the SSE stream, so this component keeps
 * the per-session sink and emits JSON-RPC messages as `event: message`.
 */
@Component
class McpSessionManager(private val objectMapper: ObjectMapper) {

    private val sessions = ConcurrentHashMap<String, Sinks.Many<String>>()

    /**
     * Registers a new session and returns a Flux that emits the endpoint
     * announcement, the initial tools list, and any messages sent to this
     * session, plus periodic keep-alive comments.
     */
    fun register(
        sessionId: String,
        toolHandler: McpToolHandler
    ): Flux<String> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<String>()
        val previous = sessions.put(sessionId, sink)
        previous?.tryEmitComplete()

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "tools/list",
                "params" to toolHandler.toolsList()
            )
        )

        sink.tryEmitNext("event: endpoint\ndata: /mcp/messages?sessionId=$sessionId\n\n")
        sink.tryEmitNext("event: message\ndata: $payload\n\n")

        val heartbeat = Flux.interval(Duration.ofSeconds(15))
            .map { ": heartbeat\n\n" }

        return Flux.merge(sink.asFlux(), heartbeat)
            .doOnCancel { sessions.remove(sessionId) }
            .doOnComplete { sessions.remove(sessionId) }
            .doOnError { sessions.remove(sessionId) }
    }

    /**
     * Sends a JSON-RPC message to the session's SSE stream.
     */
    fun send(sessionId: String, message: McpJsonRpcResponse): Boolean {
        val sink = sessions[sessionId] ?: return false
        val payload = objectMapper.writeValueAsString(message)
        val emitted = sink.tryEmitNext("event: message\ndata: $payload\n\n")
        return emitted.isSuccess
    }

    fun hasSession(sessionId: String): Boolean = sessions.containsKey(sessionId)
}
