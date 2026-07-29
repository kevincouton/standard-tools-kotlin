package com.example.starter.adapter.`in`.grpc

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.domain.OrderItem
import com.example.starter.grpc.CancelOrderRequest
import com.example.starter.grpc.CreateOrderRequest
import com.example.starter.grpc.GetOrderRequest
import com.example.starter.grpc.ListOrdersRequest
import com.example.starter.grpc.ListOrdersResponse
import com.example.starter.grpc.OrderResponse
import com.example.starter.grpc.OrderServiceGrpcKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.math.BigDecimal
import java.util.UUID

@GrpcService
class GrpcOrderService(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val listOrdersUseCase: ListOrdersUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) : OrderServiceGrpcKt.OrderServiceCoroutineImplBase() {

    override suspend fun createOrder(request: CreateOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        val command = CreateOrderUseCase.CreateOrderCommand(
            customerId = request.customerId,
            items = request.itemsList.map {
                OrderItem(it.productId, it.quantity, BigDecimal(it.unitPrice))
            }
        )
        createOrderUseCase.createOrder(command).toGrpcResponse()
    }

    override suspend fun getOrder(request: GetOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        getOrderUseCase.getOrder(UUID.fromString(request.orderId)).toGrpcResponse()
    }

    override suspend fun listOrders(request: ListOrdersRequest): ListOrdersResponse = withContext(Dispatchers.IO) {
        val customerId = if (request.hasCustomerId()) request.customerId else null
        val orders = listOrdersUseCase.listOrders(customerId)
        ListOrdersResponse.newBuilder()
            .addAllOrders(orders.map { it.toGrpcResponse() })
            .build()
    }

    override suspend fun cancelOrder(request: CancelOrderRequest): OrderResponse = withContext(Dispatchers.IO) {
        cancelOrderUseCase.cancelOrder(UUID.fromString(request.orderId)).toGrpcResponse()
    }
}
