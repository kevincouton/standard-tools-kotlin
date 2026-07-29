package com.example.starter.e2e

import com.example.starter.adapter.`in`.a2a.JsonRpcRequest
import com.example.starter.adapter.`in`.mcp.McpJsonRpcRequest
import com.example.starter.grpc.CancelOrderRequest
import com.example.starter.grpc.CreateOrderRequest
import com.example.starter.grpc.GetOrderRequest
import com.example.starter.grpc.ListOrdersRequest
import com.example.starter.grpc.OrderItemRequest
import com.example.starter.grpc.OrderServiceGrpc
import com.example.starter.testsupport.ScenarioLogger
import io.grpc.ManagedChannelBuilder
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.math.BigDecimal

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class OrderLifecycleE2ETest {

    @LocalServerPort
    var port: Int = 0

    @LocalGrpcServerPort
    var grpcPort: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:18").apply {
            withDatabaseName("starter_test")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `multi-protocol order lifecycle`() {
        val logger = ScenarioLogger("Multi-protocol order lifecycle")

        // REST create
        val createBody = mapOf(
            "customerId" to "C1",
            "items" to listOf(
                mapOf("productId" to "P1", "quantity" to 2, "unitPrice" to "10.00")
            )
        )
        val createResult = webTestClient.post().uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createBody)
            .exchange()
            .expectStatus().isCreated
            .expectBody(Map::class.java)
            .returnResult()
        val orderId = createResult.responseBody?.get("id") as String
        logger.step("REST", "POST /orders", "201 Created ($orderId)")

        // gRPC get
        val grpcChannel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build()
        val grpcStub = OrderServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.getOrder(
            GetOrderRequest.newBuilder().setOrderId(orderId).build()
        )
        expectThat(grpcResponse.status).isEqualTo("PENDING")
        logger.step("gRPC", "GetOrder($orderId)", "PENDING")
        grpcChannel.shutdown()

        // A2A cancel
        val a2aResult = webTestClient.post().uri("/a2a/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                JsonRpcRequest(
                    id = "1",
                    method = "tasks/send",
                    params = mapOf(
                        "skillId" to "cancel-order",
                        "taskId" to "task-1",
                        "orderId" to orderId
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        val a2aStatus = ((a2aResult.responseBody?.get("result") as? Map<*, *>)?.get("status") as? String)
        expectThat(a2aStatus).isEqualTo("completed")
        logger.step("A2A", "tasks/send cancel-order($orderId)", "COMPLETED")

        // MCP get
        val mcpResult = webTestClient.post().uri("/mcp/messages?sessionId=test-session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                McpJsonRpcRequest(
                    id = "2",
                    method = "tools/call",
                    params = mapOf(
                        "name" to "get_order",
                        "arguments" to mapOf("orderId" to orderId)
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
        expectThat(mcpResult.responseBody ?: "").contains("CANCELLED")
        logger.step("MCP", "tool call get_order($orderId)", "CANCELLED")

        logger.print()
    }

    @Test
    fun `gRPC create and list orders`() {
        val grpcChannel = ManagedChannelBuilder.forAddress("localhost", grpcPort).usePlaintext().build()
        val grpcStub = OrderServiceGrpc.newBlockingStub(grpcChannel)

        val createResponse = grpcStub.createOrder(
            CreateOrderRequest.newBuilder()
                .setCustomerId("C2")
                .addItems(
                    OrderItemRequest.newBuilder()
                        .setProductId("P2")
                        .setQuantity(3)
                        .setUnitPrice("4.00")
                        .build()
                )
                .build()
        )
        expectThat(createResponse.customerId).isEqualTo("C2")
        expectThat(BigDecimal(createResponse.totalAmount)).isEqualTo(BigDecimal("12.00"))

        val listResponse = grpcStub.listOrders(ListOrdersRequest.newBuilder().setCustomerId("C2").build())
        expectThat(listResponse.ordersList).hasSize(1)

        grpcChannel.shutdown()
    }
}
