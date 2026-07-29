package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem

interface CreateOrderUseCase {
    fun createOrder(command: CreateOrderCommand): Order

    data class CreateOrderCommand(
        val customerId: String,
        val items: List<OrderItem>
    )
}
