package com.example.starter.adapter.out.persistence

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import org.springframework.stereotype.Component

@Component
class OrderPersistenceMapper {

    fun toEntity(order: Order): OrderEntity {
        val entity = OrderEntity(
            id = order.id,
            customerId = order.customerId,
            status = order.status,
            createdAt = order.createdAt
        )
        entity.items = order.items.map { toEntity(it, entity) }.toMutableList()
        return entity
    }

    private fun toEntity(item: OrderItem, order: OrderEntity): OrderItemEntity {
        return OrderItemEntity(
            productId = item.productId,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            order = order
        )
    }

    fun toDomain(entity: OrderEntity): Order {
        return Order(
            id = entity.id,
            customerId = entity.customerId,
            status = entity.status,
            createdAt = entity.createdAt,
            items = entity.items.map { toDomain(it) }
        )
    }

    private fun toDomain(entity: OrderItemEntity): OrderItem {
        return OrderItem(
            productId = entity.productId,
            quantity = entity.quantity,
            unitPrice = entity.unitPrice
        )
    }
}
