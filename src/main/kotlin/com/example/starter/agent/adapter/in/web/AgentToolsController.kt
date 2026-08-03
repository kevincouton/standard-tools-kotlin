package com.example.starter.agent.adapter.`in`.web

import com.example.starter.agent.ToolDispatcher
import com.example.starter.agent.ToolRegistry
import com.example.starter.shared.domain.InvalidCommandException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/agent")
class AgentToolsController(
    private val toolRegistry: ToolRegistry,
    private val toolDispatcher: ToolDispatcher
) {

    @GetMapping("/tools")
    fun listTools(): Map<String, Any> = mapOf("tools" to toolRegistry.tools)

    @PostMapping("/dispatch")
    fun dispatch(@RequestBody request: DispatchRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val result = toolDispatcher.dispatch(request.tool, request.arguments)
            ResponseEntity.ok(mapOf("tool" to request.tool, "result" to result))
        } catch (ex: InvalidCommandException) {
            ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Invalid command")))
        } catch (ex: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Invalid arguments")))
        } catch (ex: NotImplementedError) {
            ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Not implemented")))
        } catch (ex: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to (ex.message ?: "Internal error")))
        }
    }

    data class DispatchRequest(
        val tool: String,
        val arguments: Map<String, Any> = emptyMap()
    )
}
