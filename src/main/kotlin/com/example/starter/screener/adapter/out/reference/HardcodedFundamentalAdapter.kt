package com.example.starter.screener.adapter.out.reference

import com.example.starter.screener.application.port.outbound.FundamentalProvider
import com.example.starter.screener.domain.FundamentalData
import org.springframework.stereotype.Component

@Component
class HardcodedFundamentalAdapter : FundamentalProvider {

    private val table = mapOf(
        "AAPL" to FundamentalData("AAPL", peRatio = 28.0, pbRatio = 45.0, debtEquity = 1.5, roe = 0.25, profitMargin = 0.22, dividendYield = 0.005, marketCap = 2.8e12, beta = 1.2),
        "MSFT" to FundamentalData("MSFT", peRatio = 32.0, pbRatio = 12.0, debtEquity = 0.4, roe = 0.30, profitMargin = 0.35, dividendYield = 0.007, marketCap = 3.0e12, beta = 0.9),
        "TSLA" to FundamentalData("TSLA", peRatio = 75.0, pbRatio = 15.0, debtEquity = 0.2, roe = 0.10, profitMargin = 0.08, dividendYield = 0.0, marketCap = 8.0e11, beta = 2.0),
        "JNJ" to FundamentalData("JNJ", peRatio = 18.0, pbRatio = 5.0, debtEquity = 0.5, roe = 0.18, profitMargin = 0.16, dividendYield = 0.025, marketCap = 4.5e11, beta = 0.6)
    )

    override fun fetch(ticker: String): FundamentalData? = table[ticker.uppercase()]
}
