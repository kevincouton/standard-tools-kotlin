package com.example.starter.adapter.`in`.mcp

import com.example.starter.domain.Order

object McpOrderToolMapper {

    fun toText(order: Order): String {
        return buildString {
            appendLine("Order ${order.id} for customer ${order.customerId}")
            appendLine("Status: ${order.status}")
            appendLine("Items:")
            order.items.forEach {
                appendLine("  - ${it.productId} x${it.quantity} @ ${it.unitPrice} = ${it.lineTotal}")
            }
            appendLine("Total: ${order.totalAmount}")
            appendLine("Created: ${order.createdAt}")
        }
    }
}
