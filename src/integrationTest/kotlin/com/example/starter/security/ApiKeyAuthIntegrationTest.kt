package com.example.starter.security

import com.example.starter.adapter.`in`.grpc.ApiKeyAuthInterceptor
import com.example.starter.adapter.`in`.web.ApiKeyAuthFilter
import com.example.starter.grpc.ListOrdersRequest
import com.example.starter.grpc.OrderServiceGrpc
import com.example.starter.testsupport.PostgresTestContainer
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.MetadataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.concurrent.TimeUnit

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "standard-tools.security.auth-enabled=true",
        "standard-tools.security.api-key=integration-test-key"
    ]
)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class ApiKeyAuthIntegrationTest {

    @LocalGrpcServerPort
    var grpcPort: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    private lateinit var grpcChannel: ManagedChannel

    @BeforeEach
    fun setupChannel() {
        grpcChannel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun tearDownChannel() {
        grpcChannel.shutdownNow()
        grpcChannel.awaitTermination(5, TimeUnit.SECONDS)
    }

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
    fun `REST request without api key is rejected with 401`() {
        webTestClient.get().uri("/orders")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `REST request with correct api key proceeds`() {
        webTestClient.get().uri("/orders")
            .header(ApiKeyAuthFilter.API_KEY_HEADER, "integration-test-key")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `actuator health is reachable without api key`() {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `MCP endpoint without api key is rejected with 401`() {
        webTestClient.get().uri("/mcp/sse")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `gRPC call without api key metadata fails with UNAUTHENTICATED`() {
        val stub = OrderServiceGrpc.newBlockingStub(grpcChannel)

        val ex = assertThrows<StatusRuntimeException> {
            stub.listOrders(ListOrdersRequest.newBuilder().build())
        }

        expectThat(ex.status.code).isEqualTo(Status.Code.UNAUTHENTICATED)
    }

    @Test
    fun `gRPC call with correct api key metadata proceeds`() {
        val metadata = Metadata().apply {
            put(ApiKeyAuthInterceptor.API_KEY_METADATA_KEY, "integration-test-key")
        }
        val stub = OrderServiceGrpc.newBlockingStub(grpcChannel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))

        val response = stub.listOrders(ListOrdersRequest.newBuilder().build())

        expectThat(response.ordersCount).isEqualTo(0)
    }
}
