package com.example.starter.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderJpaRepository : JpaRepository<OrderEntity, UUID> {
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.customerId = :customerId")
    fun findByCustomerId(@Param("customerId") customerId: String): List<OrderEntity>
}
