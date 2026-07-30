package com.example.starter.marketdata.adapter.out.polygon

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DataQualityException
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import okhttp3.OkHttpClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import okhttp3.Request
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Component
@ConditionalOnProperty(prefix = "standard-tools.market-data.providers.polygon", name = ["enabled"], havingValue = "true")
class PolygonMarketDataAdapter(
    private val properties: PolygonProperties,
    private val client: OkHttpClient,
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
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ProviderNotAvailableException("polygon")
                }
                val json = response.body?.string() ?: throw ProviderNotAvailableException("polygon")
                val root = objectMapper.readTree(json)
                val results = root["results"] ?: return emptyList()
                return (0 until results.size()).map { index ->
                    val bar = results[index]
                    validateBarFields(bar, index)
                    OHLCV(
                        ticker = ticker,
                        date = Instant.ofEpochMilli(bar["t"].asLong()).atZone(ZoneOffset.UTC).toLocalDate(),
                        open = BigDecimal(bar["o"].asText()),
                        high = BigDecimal(bar["h"].asText()),
                        low = BigDecimal(bar["l"].asText()),
                        close = BigDecimal(bar["c"].asText()),
                        volume = bar["v"].asLong()
                    )
                }
            }
        } catch (e: IOException) {
            throw ProviderNotAvailableException("polygon")
        }
    }

    private fun validateBarFields(bar: JsonNode, index: Int) {
        if (bar["t"]?.isNull != false ||
            bar["o"]?.isNull != false ||
            bar["h"]?.isNull != false ||
            bar["l"]?.isNull != false ||
            bar["c"]?.isNull != false ||
            bar["v"]?.isNull != false
        ) {
            throw DataQualityException("Polygon bar at index $index is missing required fields")
        }
    }
}
