package com.example.starter.marketdata.e2e

import com.example.starter.adapter.`in`.a2a.JsonRpcRequest
import com.example.starter.adapter.`in`.mcp.McpJsonRpcRequest
import com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapter
import com.example.starter.marketdata.grpc.MarketDataServiceGrpc
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.PostgresTestContainer
import com.example.starter.testsupport.ScenarioLogger
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.util.concurrent.TimeUnit

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "standard-tools.market-data.default-provider=wiremock",
        "standard-tools.market-data.providers.wiremock.enabled=true"
    ]
)
@AutoConfigureWebTestClient
@Import(MarketDataE2ETest.YFinanceStubConfig::class)
class MarketDataE2ETest {

    @LocalGrpcServerPort
    var grpcPort: Int = 0

    @Autowired
    lateinit var webTestClient: org.springframework.test.web.reactive.server.WebTestClient

    @Autowired
    lateinit var wireMockServer: WireMockServer

    private lateinit var grpcChannel: ManagedChannel

    @BeforeEach
    fun setup() {
        wireMockServer.resetAll()
        wireMockServer.stubFor(
            get(urlMatching("/v7/finance/download/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/csv")
                        .withBody(
                            """
                            Date,Open,High,Low,Close,Adj Close,Volume
                            2024-01-02,100.00,102.00,99.00,101.00,101.00,1000000
                            2024-01-03,101.00,103.00,100.00,102.00,102.00,1100000
                            2024-01-04,102.00,104.00,101.00,103.00,103.00,1200000
                            """.trimIndent()
                        )
                )
        )
        grpcChannel = ManagedChannelBuilder.forAddress(LOCALHOST, grpcPort)
            .usePlaintext()
            .build()
    }

    @AfterEach
    fun tearDown() {
        grpcChannel.shutdownNow()
        grpcChannel.awaitTermination(5, TimeUnit.SECONDS)
    }

    @TestConfiguration
    class YFinanceStubConfig {

        @Bean(initMethod = "start", destroyMethod = "stop")
        fun wireMockServer(): WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @Bean
        fun wiremockMarketDataProvider(client: OkHttpClient, wireMockServer: WireMockServer): MarketDataProvider =
            WireMockYFinanceProvider(client, wireMockServer.baseUrl())
    }

    class WireMockYFinanceProvider(
        client: OkHttpClient,
        baseUrl: String
    ) : MarketDataProvider {
        override val name: String = "wiremock"
        private val delegate = YFinanceMarketDataAdapter(client = client, baseUrl = baseUrl)

        override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries =
            delegate.fetch(ticker, range, interval)
    }

    companion object {
        const val LOCALHOST = "localhost"

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
    fun `market data across REST gRPC A2A and MCP`() {
        val logger = ScenarioLogger("Market data multi-protocol")

        // REST
        val restResult = webTestClient.get().uri { builder ->
            builder.path("/api/v1/market-data/bars")
                .queryParam("symbol", "AAPL")
                .queryParam("startDate", "2024-01-01")
                .queryParam("endDate", "2024-01-03")
                .queryParam("interval", "DAILY")
                .build()
        }
            .exchange()
            .expectStatus().isOk
            .expectBody(List::class.java)
            .returnResult()
        @Suppress("UNCHECKED_CAST")
        val bars = restResult.responseBody as List<Map<String, Any>>
        expectThat(bars).hasSize(3)
        logger.step("REST", "GET /api/v1/market-data/bars", "${bars.size} bars")

        // gRPC
        val grpcStub = MarketDataServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.fetchMarketData(
            com.example.starter.marketdata.grpc.FetchMarketDataRequest.newBuilder()
                .setSymbol("AAPL")
                .setStartDate("2024-01-01")
                .setEndDate("2024-01-03")
                .setInterval("DAILY")
                .build()
        )
        expectThat(grpcResponse.barsList).hasSize(3)
        logger.step("gRPC", "FetchMarketData(AAPL)", "${grpcResponse.barsList.size} bars")

        // A2A
        val a2aResult = webTestClient.post().uri("/a2a/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                JsonRpcRequest(
                    id = "1",
                    method = "tasks/send",
                    params = mapOf(
                        "skillId" to "marketdata-fetch",
                        "taskId" to "task-md-1",
                        "symbol" to "AAPL",
                        "startDate" to "2024-01-01",
                        "endDate" to "2024-01-03",
                        "interval" to "DAILY"
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        val a2aBars = (((a2aResult.responseBody?.get("result") as? Map<*, *>)?.get("result") as? Map<*, *>)?.get("bars") as? List<*>)?.size
        expectThat(a2aBars).isEqualTo(3)
        logger.step("A2A", "tasks/send marketdata-fetch(AAPL)", "$a2aBars bars")

        // MCP
        val mcpResult = webTestClient.post().uri("/mcp/messages?sessionId=test-md-session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                McpJsonRpcRequest(
                    id = "2",
                    method = "tools/call",
                    params = mapOf(
                        "name" to "marketdata_fetch",
                        "arguments" to mapOf(
                            "symbol" to "AAPL",
                            "startDate" to "2024-01-01",
                            "endDate" to "2024-01-03",
                            "interval" to "DAILY"
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
        expectThat(mcpResult.responseBody ?: "").contains("Fetched 3 bars for AAPL")
        logger.step("MCP", "tools/call marketdata_fetch(AAPL)", "success")

        logger.print()
    }
}
