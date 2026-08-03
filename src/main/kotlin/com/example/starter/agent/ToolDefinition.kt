package com.example.starter.agent

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)
