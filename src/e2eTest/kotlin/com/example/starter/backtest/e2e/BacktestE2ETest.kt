package com.example.starter.backtest.e2e

import com.example.starter.backtest.grpc.BacktestServiceGrpc
import com.example.starter.backtest.grpc.SingleAssetBacktestRequest
import com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapter
import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
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
import strikt.assertions.isGreaterThan
import strikt.assertions.isTrue
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
@Import(BacktestE2ETest.YFinanceStubConfig::class)
class BacktestE2ETest {

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

        override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): com.example.starter.shared.domain.PriceSeries =
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
    fun `backtest across REST and gRPC`() {
        val logger = ScenarioLogger("Backtest multi-protocol")

        val restResult = webTestClient.post().uri("/api/v1/backtest/single")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "symbol" to "AAPL",
                    "strategy" to "buy_and_hold",
                    "parameters" to emptyMap<String, Any>(),
                    "startDate" to "2024-01-01",
                    "endDate" to "2024-01-04",
                    "interval" to "DAILY"
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        @Suppress("UNCHECKED_CAST")
        val body = restResult.responseBody as Map<String, Any>
        val restTotalReturn = (body["totalReturn"] as Number).toDouble()
        expectThat(restTotalReturn.isFinite()).isTrue()
        logger.step("REST", "POST /api/v1/backtest/single", "totalReturn=$restTotalReturn")

        val grpcStub = BacktestServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.runSingleAsset(
            SingleAssetBacktestRequest.newBuilder()
                .setSymbol("AAPL")
                .setStrategy("buy_and_hold")
                .setStartDate("2024-01-01")
                .setEndDate("2024-01-04")
                .setInterval("DAILY")
                .build()
        )
        expectThat(grpcResponse.totalReturn.isFinite()).isTrue()
        logger.step("gRPC", "BacktestService/RunSingleAsset", "totalReturn=${grpcResponse.totalReturn}")

        logger.print()
    }
}
