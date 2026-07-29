package com.example.starter.adapter.out.persistence

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.domain.OrderStatus
import com.example.starter.testsupport.PostgresTestContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import java.math.BigDecimal

@Tag("integration")
@SpringBootTest
@Import(OrderPersistenceMapper::class, JpaOrderRepository::class)
@Testcontainers
@ActiveProfiles("test")
class JpaOrderRepositoryIntegrationTest {

    @Autowired
    lateinit var repository: JpaOrderRepository

    companion object {
        @Container
        val postgres = PostgresTestContainer.instance

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `save and find order`() {
        val order = Order.create(
            customerId = "C1",
            items = listOf(OrderItem("P1", 2, BigDecimal("10.00")))
        )

        val saved = repository.save(order)
        val found = repository.findById(saved.id)

        expectThat(found).isNotNull().and {
            get { customerId }.isEqualTo("C1")
            get { items }.hasSize(1)
            get { totalAmount.compareTo(BigDecimal("20.00")) }.isEqualTo(0)
        }
    }

    @Test
    fun `find all orders by customer id`() {
        val order1 = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        val order2 = Order.create("C2", listOf(OrderItem("P2", 1, BigDecimal("7.00"))))
        repository.save(order1)
        repository.save(order2)

        val results = repository.findAll("C1")

        expectThat(results).hasSize(1)
        expectThat(results.first().customerId).isEqualTo("C1")
    }

    @Test
    fun `cancel order updates status`() {
        val order = repository.save(
            Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        )

        val cancelled = order.cancel()
        val saved = repository.save(cancelled)

        expectThat(saved.status).isEqualTo(OrderStatus.CANCELLED)
    }
}
