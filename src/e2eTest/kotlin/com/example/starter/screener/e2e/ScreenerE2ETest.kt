package com.example.starter.screener.e2e

import com.example.starter.marketdata.adapter.out.yfinance.YFinanceMarketDataAdapter
import com.example.starter.screener.grpc.ScreenRequest
import com.example.starter.screener.grpc.ScreenerServiceGrpc
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
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
@Import(ScreenerE2ETest.YFinanceStubConfig::class)
class ScreenerE2ETest {

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
                            2024-01-05,103.00,105.00,102.00,104.00,104.00,1300000
                            2024-01-08,104.00,106.00,103.00,105.00,105.00,1400000
                            2024-01-09,105.00,107.00,104.00,106.00,106.00,1500000
                            2024-01-10,106.00,108.00,105.00,107.00,107.00,1600000
                            2024-01-11,107.00,109.00,106.00,108.00,108.00,1700000
                            2024-01-12,108.00,110.00,107.00,109.00,109.00,1800000
                            2024-01-16,109.00,111.00,108.00,110.00,110.00,1900000
                            2024-01-17,110.00,112.00,109.00,111.00,111.00,2000000
                            2024-01-18,111.00,113.00,110.00,112.00,112.00,2100000
                            2024-01-19,112.00,114.00,111.00,113.00,113.00,2200000
                            2024-01-22,113.00,115.00,112.00,114.00,114.00,2300000
                            2024-01-23,114.00,116.00,113.00,115.00,115.00,2400000
                            2024-01-24,115.00,117.00,114.00,116.00,116.00,2500000
                            2024-01-25,116.00,118.00,115.00,117.00,117.00,2600000
                            2024-01-26,117.00,119.00,116.00,118.00,118.00,2700000
                            2024-01-29,118.00,120.00,117.00,119.00,119.00,2800000
                            2024-01-30,119.00,121.00,118.00,120.00,120.00,2900000
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
    fun `stock screen across REST and gRPC`() {
        val logger = ScenarioLogger("Screener multi-protocol")

        val restResult = webTestClient.get().uri { builder ->
            builder.path("/api/v1/screener/screen")
                .queryParam("tickers", "AAPL", "MSFT", "TSLA")
                .queryParam("startDate", "2024-01-01")
                .queryParam("endDate", "2024-01-30")
                .queryParam("interval", "DAILY")
                .queryParam("peRatioMax", "35")
                .build()
        }
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
        @Suppress("UNCHECKED_CAST")
        val body = restResult.responseBody as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val matches = body["matches"] as List<Map<String, Any>>
        val tickers = matches.map { it["ticker"] as String }
        expectThat(tickers).contains("AAPL", "MSFT").doesNotContain("TSLA")
        logger.step("REST", "GET /api/v1/screener/screen", "matches=$tickers")

        val grpcStub = ScreenerServiceGrpc.newBlockingStub(grpcChannel)
        val grpcResponse = grpcStub.screen(
            ScreenRequest.newBuilder()
                .addAllTickers(listOf("AAPL", "MSFT", "TSLA"))
                .setStartDate("2024-01-01")
                .setEndDate("2024-01-30")
                .setInterval("DAILY")
                .setPeRatioMax(35.0)
                .build()
        )
        val grpcTickers = grpcResponse.matchesList.map { it.ticker }
        expectThat(grpcTickers).contains("AAPL", "MSFT").doesNotContain("TSLA")
        logger.step("gRPC", "ScreenerService/Screen", "matches=$grpcTickers")

        logger.print()
    }
}
