package com.example.starter.application.port.outbound

import com.example.starter.domain.Order
import java.util.UUID

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: UUID): Order?
    fun findAll(customerId: String? = null): List<Order>
}
