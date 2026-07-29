package com.example.starter.domain

import com.example.starter.domain.exception.InvalidOrderStateException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.math.BigDecimal

@Tag("unit")
class OrderTest {

    @Test
    fun `create order with valid data`() {
        val item = OrderItem("P1", 2, BigDecimal("10.00"))
        val order = Order.create("C1", listOf(item))

        expectThat(order.status).isEqualTo(OrderStatus.PENDING)
        expectThat(order.totalAmount).isEqualTo(BigDecimal("20.00"))
    }

    @Test
    fun `create order with blank customer id throws exception`() {
        assertThrows<IllegalArgumentException> {
            Order.create("", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        }
    }

    @Test
    fun `create order with no items throws exception`() {
        assertThrows<IllegalArgumentException> {
            Order.create("C1", emptyList())
        }
    }

    @Test
    fun `cancel pending order succeeds`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
        val cancelled = order.cancel()

        expectThat(cancelled.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `cancel shipped order throws exception`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.SHIPPED)

        assertThrows<InvalidOrderStateException> { order.cancel() }
    }

    @Test
    fun `cancel already cancelled order throws exception`() {
        val order = Order.create("C1", listOf(OrderItem("P1", 1, BigDecimal("5.00"))))
            .copy(status = OrderStatus.CANCELLED)

        assertThrows<InvalidOrderStateException> { order.cancel() }
    }
}
