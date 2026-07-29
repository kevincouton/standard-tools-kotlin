package com.example.starter.application.service

import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.outbound.OrderRepository
import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.domain.OrderStatus
import com.example.starter.domain.exception.InvalidOrderStateException
import com.example.starter.domain.exception.OrderNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.math.BigDecimal
import java.util.UUID

@Tag("unit")
class OrderServiceTest {

    private val repository = mockk<OrderRepository>()
    private val service = OrderService(repository)

    @Test
    fun `createOrder saves and returns order`() {
        val command = CreateOrderUseCase.CreateOrderCommand(
            customerId = "C1",
            items = listOf(OrderItem("P1", 2, BigDecimal("10.00")))
        )
        every { repository.save(any()) } answers { firstArg() }

        val result = service.createOrder(command)

        expectThat(result.customerId).isEqualTo("C1")
        expectThat(result.status).isEqualTo(OrderStatus.PENDING)
        verify { repository.save(any()) }
    }

    @Test
    fun `getOrder returns order when found`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findById(id) } returns order

        val result = service.getOrder(id)

        expectThat(result).isEqualTo(order)
    }

    @Test
    fun `getOrder throws when not found`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns null

        assertThrows<OrderNotFoundException> { service.getOrder(id) }
    }

    @Test
    fun `listOrders delegates to repository`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findAll("C1") } returns listOf(order)

        val result = service.listOrders("C1")

        expectThat(result).isEqualTo(listOf(order))
    }

    @Test
    fun `cancelOrder cancels and saves`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        every { repository.findById(id) } returns order
        every { repository.save(any()) } answers { firstArg() }

        val result = service.cancelOrder(id)

        expectThat(result.status).isEqualTo(OrderStatus.CANCELLED)
        verify { repository.save(any()) }
    }

    @Test
    fun `cancelOrder throws for shipped order`() {
        val id = UUID.randomUUID()
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.SHIPPED)
        every { repository.findById(id) } returns order

        assertThrows<InvalidOrderStateException> { service.cancelOrder(id) }
    }
}
