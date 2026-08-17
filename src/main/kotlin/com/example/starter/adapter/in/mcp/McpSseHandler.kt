package com.example.starter.adapter.`in`.mcp

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
class McpSseHandler(
    private val sessionManager: McpSessionManager,
    private val toolHandler: McpToolHandler
) {

    @GetMapping("/mcp/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sse(@RequestParam(required = false) sessionId: String?): Flux<String> {
        val id = sessionId ?: UUID.randomUUID().toString()
        return sessionManager.register(id, toolHandler)
    }
}
