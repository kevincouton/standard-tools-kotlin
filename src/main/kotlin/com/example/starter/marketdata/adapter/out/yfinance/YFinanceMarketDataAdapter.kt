package com.example.starter.marketdata.adapter.out.yfinance

import com.example.starter.shared.application.port.outbound.MarketDataProvider
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DataQualityException
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.OHLCV
import com.example.starter.shared.domain.PriceSeries
import com.example.starter.shared.domain.ProviderNotAvailableException
import com.example.starter.shared.domain.Ticker
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@ConditionalOnProperty(
    prefix = "standard-tools.market-data.providers.yfinance",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class YFinanceMarketDataAdapter(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://query1.finance.yahoo.com"
) : MarketDataProvider {

    override val name: String = "yfinance"

    override fun fetch(ticker: Ticker, range: DateRange, interval: BarInterval): PriceSeries {
        val symbol = ticker.symbol
        val period1 = range.start.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val period2 = range.end.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val intervalParam = when (interval) {
            BarInterval.DAILY -> "1d"
            BarInterval.WEEKLY -> "1wk"
            BarInterval.MONTHLY -> "1mo"
        }
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8)
        val url = "$baseUrl/v7/finance/download/$encodedSymbol?period1=$period1&period2=$period2&interval=$intervalParam&events=history"

        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ProviderNotAvailableException("yfinance")
                }
                val body = response.body?.string() ?: throw ProviderNotAvailableException("yfinance")
                return parseCsv(body, ticker)
            }
        } catch (e: IOException) {
            throw ProviderNotAvailableException("yfinance")
        }
    }

    private fun parseCsv(csv: String, ticker: Ticker): PriceSeries {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        return lines.drop(1).mapIndexed { index, line ->
            val cols = line.split(",")
            if (cols.size < 7) {
                throw DataQualityException("yfinance CSV row ${index + 2} has ${cols.size} columns, expected at least 7")
            }
            try {
                OHLCV(
                    ticker = ticker,
                    date = LocalDate.parse(cols[0], DateTimeFormatter.ISO_LOCAL_DATE),
                    open = BigDecimal(cols[1]),
                    high = BigDecimal(cols[2]),
                    low = BigDecimal(cols[3]),
                    close = BigDecimal(cols[4]),
                    volume = cols[6].toLong()
                )
            } catch (e: Exception) {
                throw DataQualityException("yfinance CSV row ${index + 2} parse error: ${e.message}")
            }
        }
    }
}
