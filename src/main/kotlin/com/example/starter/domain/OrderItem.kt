package com.example.starter.domain

import java.math.BigDecimal

data class OrderItem(
    val productId: String,
    val quantity: Int,
    val unitPrice: BigDecimal
) {
    init {
        require(productId.isNotBlank()) { "productId must not be blank" }
        require(quantity > 0) { "quantity must be positive" }
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must not be negative" }
    }

    val lineTotal: BigDecimal
        get() = unitPrice * quantity.toBigDecimal()
}
