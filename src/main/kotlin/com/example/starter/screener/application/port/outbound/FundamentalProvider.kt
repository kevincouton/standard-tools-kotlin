package com.example.starter.screener.application.port.outbound

import com.example.starter.screener.domain.FundamentalData

interface FundamentalProvider {
    fun fetch(ticker: String): FundamentalData?
}
