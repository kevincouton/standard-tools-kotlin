package com.example.starter.adapter.`in`.web

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderRequest(
    val customerId: String,
    val items: List<OrderItemRequest>
)

data class OrderItemRequest(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class OrderResponse(
    val id: UUID,
    val customerId: String,
    val items: List<OrderItemResponse>,
    val status: String,
    val totalAmount: BigDecimal,
    val createdAt: Instant
)

data class OrderItemResponse(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

fun Order.toResponse(): OrderResponse {
    return OrderResponse(
        id = id,
        customerId = customerId,
        status = status.name,
        totalAmount = totalAmount,
        createdAt = createdAt,
        items = items.map { it.toResponse() }
    )
}

fun OrderItem.toResponse(): OrderItemResponse {
    return OrderItemResponse(
        productId = productId,
        quantity = quantity,
        unitPrice = unitPrice,
        lineTotal = lineTotal
    )
}
