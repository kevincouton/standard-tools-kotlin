package com.example.starter.application.port.inbound

import com.example.starter.domain.Order

interface ListOrdersUseCase {
    fun listOrders(customerId: String? = null): List<Order>
}
