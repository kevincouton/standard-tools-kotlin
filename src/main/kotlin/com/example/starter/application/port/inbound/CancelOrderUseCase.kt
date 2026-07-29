package com.example.starter.application.port.inbound

import com.example.starter.domain.Order
import java.util.UUID

interface CancelOrderUseCase {
    fun cancelOrder(id: UUID): Order
}
