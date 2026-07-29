package com.example.starter.adapter.`in`.a2a

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class A2aAgentCardController {

    @GetMapping("/.well-known/agent.json", produces = ["application/json"])
    fun agentCard(): Map<String, Any> = mapOf(
        "name" to "Order Agent",
        "description" to "Agent that manages orders via REST, gRPC, A2A, and MCP",
        "url" to "http://localhost:8080/a2a",
        "version" to "1.0.0",
        "capabilities" to mapOf(
            "streaming" to false,
            "pushNotifications" to false
        ),
        "skills" to listOf(
            mapOf(
                "id" to "create-order",
                "name" to "Create Order",
                "description" to "Create a new order for a customer",
                "tags" to listOf("orders"),
                "examples" to listOf("Create an order for customer C1 with item P1 quantity 2")
            ),
            mapOf(
                "id" to "get-order",
                "name" to "Get Order",
                "description" to "Retrieve an order by id",
                "tags" to listOf("orders"),
                "examples" to listOf("Get order 123e4567-e89b-12d3-a456-426614174000")
            ),
            mapOf(
                "id" to "cancel-order",
                "name" to "Cancel Order",
                "description" to "Cancel an existing order",
                "tags" to listOf("orders"),
                "examples" to listOf("Cancel order 123e4567-e89b-12d3-a456-426614174000")
            )
        )
    )
}
