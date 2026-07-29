package com.example.starter.adapter.out.persistence

import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaOrderRepository(
    private val jpaRepository: OrderJpaRepository,
    private val mapper: OrderPersistenceMapper
) : OrderRepository {

    override fun save(order: Order): Order {
        val entity = mapper.toEntity(order)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: UUID): Order? {
        return jpaRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)
    }

    override fun findAll(customerId: String?): List<Order> {
        val entities = if (customerId != null) {
            jpaRepository.findByCustomerId(customerId)
        } else {
            jpaRepository.findAll()
        }
        return entities.map { mapper.toDomain(it) }
    }
}
