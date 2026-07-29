package com.example.starter.adapter.`in`.web

import com.example.starter.application.port.inbound.CancelOrderUseCase
import com.example.starter.application.port.inbound.CreateOrderUseCase
import com.example.starter.application.port.inbound.GetOrderUseCase
import com.example.starter.application.port.inbound.ListOrdersUseCase
import com.example.starter.domain.OrderItem
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@RestController
@RequestMapping("/orders")
class OrderController(
    private val createOrderUseCase: CreateOrderUseCase,
    private val getOrderUseCase: GetOrderUseCase,
    private val listOrdersUseCase: ListOrdersUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody request: CreateOrderRequest): Mono<OrderResponse> {
        return Mono.fromCallable {
            val command = CreateOrderUseCase.CreateOrderCommand(
                customerId = request.customerId,
                items = request.items.map { OrderItem(it.productId, it.quantity, it.unitPrice) }
            )
            createOrderUseCase.createOrder(command)
        }.subscribeOn(Schedulers.boundedElastic()).map { it.toResponse() }
    }

    @GetMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getOrder(@PathVariable id: UUID): Mono<OrderResponse> {
        return Mono.fromCallable { getOrderUseCase.getOrder(id) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { it.toResponse() }
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listOrders(@RequestParam customerId: String?): Mono<List<OrderResponse>> {
        return Mono.fromCallable { listOrdersUseCase.listOrders(customerId) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { orders -> orders.map { it.toResponse() } }
    }

    @PostMapping("/{id}/cancel", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun cancelOrder(@PathVariable id: UUID): Mono<OrderResponse> {
        return Mono.fromCallable { cancelOrderUseCase.cancelOrder(id) }
            .subscribeOn(Schedulers.boundedElastic())
            .map { it.toResponse() }
    }
}
