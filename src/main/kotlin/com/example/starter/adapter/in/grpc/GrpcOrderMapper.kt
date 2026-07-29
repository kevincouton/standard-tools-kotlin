package com.example.starter.adapter.`in`.grpc

import com.example.starter.domain.Order
import com.example.starter.domain.OrderItem
import com.example.starter.grpc.OrderItemResponse
import com.example.starter.grpc.OrderResponse
import com.google.protobuf.Timestamp
import java.math.BigDecimal

fun Order.toGrpcResponse(): OrderResponse {
    return OrderResponse.newBuilder()
        .setOrderId(id.toString())
        .setCustomerId(customerId)
        .setStatus(status.name)
        .setTotalAmount(totalAmount.toPlainString())
        .setCreatedAt(
            Timestamp.newBuilder()
                .setSeconds(createdAt.epochSecond)
                .setNanos(createdAt.nano)
                .build()
        )
        .addAllItems(items.map { it.toGrpcResponse() })
        .build()
}

fun OrderItem.toGrpcResponse(): OrderItemResponse {
    return OrderItemResponse.newBuilder()
        .setProductId(productId)
        .setQuantity(quantity)
        .setUnitPrice(unitPrice.toPlainString())
        .setLineTotal(lineTotal.toPlainString())
        .build()
}
