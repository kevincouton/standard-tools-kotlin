package com.example.starter.adapter.`in`.a2a

import com.example.starter.domain.Order

object A2aOrderSkillMapper {

    fun toTaskResult(order: Order): Map<String, Any> = mapOf(
        "orderId" to order.id.toString(),
        "customerId" to order.customerId,
        "status" to order.status.name,
        "totalAmount" to order.totalAmount.toPlainString(),
        "createdAt" to order.createdAt.toString(),
        "items" to order.items.map {
            mapOf(
                "productId" to it.productId,
                "quantity" to it.quantity,
                "unitPrice" to it.unitPrice.toPlainString(),
                "lineTotal" to it.lineTotal.toPlainString()
            )
        }
    )
}
