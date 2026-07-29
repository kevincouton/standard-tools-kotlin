package com.example.starter.domain

import com.example.starter.domain.exception.InvalidOrderStateException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID = UUID.randomUUID(),
    val customerId: String,
    val items: List<OrderItem>,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Instant = Instant.now()
) {
    val totalAmount: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { sum, item -> sum + item.lineTotal }

    fun cancel(): Order {
        if (status == OrderStatus.CANCELLED) {
            throw InvalidOrderStateException("Order ${id} is already cancelled")
        }
        if (status == OrderStatus.SHIPPED) {
            throw InvalidOrderStateException("Cannot cancel shipped order ${id}")
        }
        return copy(status = OrderStatus.CANCELLED)
    }

    companion object {
        fun create(customerId: String, items: List<OrderItem>): Order {
            require(customerId.isNotBlank()) { "customerId must not be blank" }
            require(items.isNotEmpty()) { "Order must contain at least one item" }
            return Order(customerId = customerId, items = items)
        }
    }
}
