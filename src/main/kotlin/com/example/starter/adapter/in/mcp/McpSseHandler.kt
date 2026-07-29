package com.example.starter.adapter.`in`.mcp

import tools.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

@RestController
class McpSseHandler(
    private val objectMapper: ObjectMapper,
    private val toolHandler: McpToolHandler
) {

    private val sessions = ConcurrentHashMap<String, Sinks.Many<String>>()

    @GetMapping("/mcp/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(@RequestParam(required = false) sessionId: String?): Flux<String> {
        val id = sessionId ?: java.util.UUID.randomUUID().toString()
        val sink = Sinks.many().unicast().onBackpressureBuffer<String>()
        sessions[id] = sink

        sink.tryEmitNext("event: endpoint\ndata: /mcp/messages?sessionId=$id\n\n")
        sink.tryEmitNext("event: tools\ndata: ${objectMapper.writeValueAsString(toolHandler.toolsList())}\n\n")

        return sink.asFlux().doOnCancel { sessions.remove(id) }
    }
}
