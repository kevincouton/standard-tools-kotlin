package com.example.starter.marketdata.adapter.out.yfinance

import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.math.BigDecimal
import java.time.LocalDate

@Tag("unit")
class YFinanceMarketDataAdapterTest {

    private val wireMock = WireMockServer(0)
    private lateinit var adapter: YFinanceMarketDataAdapter

    @BeforeEach
    fun setup() {
        wireMock.start()
        adapter = YFinanceMarketDataAdapter(baseUrl = "http://localhost:${wireMock.port()}")
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `parses csv response into price series`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/v7/finance/download/.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/csv")
                        .withBody(
                            """
                            Date,Open,High,Low,Close,Adj Close,Volume
                            2024-01-02,100.00,102.00,99.00,101.00,101.00,1000000
                            2024-01-03,101.00,103.00,100.00,102.00,102.00,1100000
                            """.trimIndent()
                        )
                )
        )

        val series = adapter.fetch(
            Ticker("AAPL"),
            DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)),
            BarInterval.DAILY
        )

        expectThat(series).hasSize(2)
        expectThat(series.first().close).isEqualTo(BigDecimal("101.00"))
    }
}
