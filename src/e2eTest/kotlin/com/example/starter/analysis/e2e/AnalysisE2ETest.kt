package com.example.starter.analysis.e2e

import com.example.starter.adapter.`in`.a2a.JsonRpcRequest
import com.example.starter.analysis.grpc.AnalysisServiceGrpc
import com.example.starter.analysis.grpc.OptionPricingRequest
import com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapter
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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
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
@Import(AnalysisE2ETest.YFinanceStubConfig::class)
class AnalysisE2ETest {

    @LocalGrpcServerPort
    var grpcPort: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

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
    fun `analysis across REST gRPC and A2A`() {
        val logger = ScenarioLogger("Analysis multi-protocol")

        // REST regression
        val restResult = webTestClient.get().uri { builder ->
            builder.path("/api/v1/analysis/regression")
                .queryParam("asset", "AAPL")
                .queryParam("benchmark", "AAPL")
                .queryParam("startDate", "2024-01-01")
                .queryParam("endDate", "2024-01-04")
                .queryParam("interval", "DAILY")
                .build()
        }
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        @Suppress("UNCHECKED_CAST")
        val body = restResult.responseBody as Map<String, Any>
        expectThat(body["beta"] as Double).isGreaterThan(0.95)
        logger.step("REST", "GET /api/v1/analysis/regression", "beta=${body["beta"]}")

        // REST option
        val optionResult = webTestClient.post().uri("/api/v1/analysis/option")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "spot" to 100.0,
                    "strike" to 100.0,
                    "timeToExpiry" to 0.5,
                    "riskFreeRate" to 0.05,
                    "volatility" to 0.25,
                    "optionType" to "call",
                    "dividendYield" to 0.0,
                    "marketPrice" to null
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        @Suppress("UNCHECKED_CAST")
        val optionBody = optionResult.responseBody as Map<String, Any>
        expectThat(optionBody["price"] as Double).isGreaterThan(0.0)
        logger.step("REST", "POST /api/v1/analysis/option", "price=${optionBody["price"]}")

        // gRPC price option
        val grpcStub = AnalysisServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.priceOption(
            OptionPricingRequest.newBuilder()
                .setSpot(100.0)
                .setStrike(100.0)
                .setTimeToExpiry(0.5)
                .setRiskFreeRate(0.05)
                .setVolatility(0.25)
                .setOptionType("call")
                .build()
        )
        expectThat(grpcResponse.price).isGreaterThan(0.0)
        logger.step("gRPC", "AnalysisService/PriceOption", "price=${grpcResponse.price}")

        // A2A analysis option
        val a2aResult = webTestClient.post().uri("/a2a/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                JsonRpcRequest(
                    id = "1",
                    method = "tasks/send",
                    params = mapOf(
                        "skillId" to "analysis-option",
                        "taskId" to "task-analysis-1",
                        "spot" to 100.0,
                        "strike" to 100.0,
                        "timeToExpiry" to 0.5,
                        "riskFreeRate" to 0.05,
                        "volatility" to 0.25,
                        "optionType" to "call"
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        val a2aStatus = ((a2aResult.responseBody?.get("result") as? Map<*, *>)?.get("status") as? String)
        expectThat(a2aStatus).isEqualTo("completed")
        logger.step("A2A", "tasks/send analysis-option", a2aStatus ?: "")

        logger.print()
    }
}
