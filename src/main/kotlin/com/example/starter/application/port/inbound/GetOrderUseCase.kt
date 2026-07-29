package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import java.util.UUID

interface GetOrderUseCase {
    fun getOrder(id: UUID): Order
}
