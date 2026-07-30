package com.example.starter.marketdata.adapter.out.polygon

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.Ticker
import okhttp3.OkHttpClient
import tools.jackson.databind.ObjectMapper
import okhttp3.Request
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
@ConditionalOnProperty(prefix = "standard-tools.market-data.providers.polygon", name = ["enabled"], havingValue = "true")
class PolygonMarketDataAdapter(
    private val properties: PolygonProperties,
    private val client: OkHttpClient = OkHttpClient(),
    private val objectMapper: ObjectMapper
) : MarketDataProvider {

    override val name: String = "polygon"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        val multiplier = 1
        val timespan = when (interval) {
            BarInterval.DAILY -> "day"
            BarInterval.WEEKLY -> "week"
            BarInterval.MONTHLY -> "month"
        }
        val from = range.start.toString()
        val to = range.end.toString()
        val url = "https://api.polygon.io/v2/aggs/ticker/${ticker.symbol}/range/$multiplier/$timespan/$from/$to?adjusted=true&apiKey=${properties.apiKey}"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Polygon request failed: ${response.code}")
            }
            val json = response.body?.string() ?: throw RuntimeException("empty Polygon response")
            val root = objectMapper.readTree(json)
            val results = root["results"] ?: return emptyList()
            return (0 until results.size()).map { index ->
                val bar = results[index]
                OHLCV(
                    ticker = ticker,
                    date = LocalDate.ofEpochDay(bar["t"].asLong() / 86_400_000),
                    open = BigDecimal(bar["o"].asText()),
                    high = BigDecimal(bar["h"].asText()),
                    low = BigDecimal(bar["l"].asText()),
                    close = BigDecimal(bar["c"].asText()),
                    volume = bar["v"].asLong()
                )
            }
        }
    }
}
