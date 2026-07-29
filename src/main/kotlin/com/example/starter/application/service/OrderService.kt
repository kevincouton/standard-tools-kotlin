package com.example.starter.application.service

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import com.example.starter.domain.exception.OrderNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository
) : CreateOrderUseCase, GetOrderUseCase, ListOrdersUseCase, CancelOrderUseCase {

    override fun createOrder(command: CreateOrderUseCase.CreateOrderCommand): Order {
        val order = Order.create(command.customerId, command.items)
        return orderRepository.save(order)
    }

    override fun getOrder(id: UUID): Order {
        return orderRepository.findById(id) ?: throw OrderNotFoundException("Order not found: $id")
    }

    override fun listOrders(customerId: String?): List<Order> {
        return orderRepository.findAll(customerId)
    }

    override fun cancelOrder(id: UUID): Order {
        val order = getOrder(id)
        val cancelled = order.cancel()
        return orderRepository.save(cancelled)
    }
}
