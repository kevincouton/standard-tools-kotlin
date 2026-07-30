package com.example.starter.marketdata.adapter.`in`.grpc

import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.marketdata.grpc.FetchMarketDataRequest
import com.example.starter.marketdata.grpc.FetchMarketDataResponse
import com.example.starter.marketdata.grpc.MarketDataServiceGrpcKt
import com.example.starter.marketdata.grpc.OHLCVBar
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class MarketDataGrpcService(
    private val fetchMarketDataUseCase: FetchMarketDataUseCase
) : MarketDataServiceGrpcKt.MarketDataServiceCoroutineImplBase() {

    override suspend fun fetchMarketData(request: FetchMarketDataRequest): FetchMarketDataResponse = withContext(Dispatchers.IO) {
        val series = fetchMarketDataUseCase.fetch(
            FetchMarketDataUseCase.FetchMarketDataCommand(
                ticker = Ticker(request.symbol, request.exchange.takeIf { it.isNotBlank() }),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = BarInterval.valueOf(request.interval.uppercase()),
                provider = request.provider.takeIf { it.isNotBlank() }
            )
        )
        FetchMarketDataResponse.newBuilder()
            .setSymbol(request.symbol)
            .addAllBars(series.map {
                OHLCVBar.newBuilder()
                    .setDate(it.date.toString())
                    .setOpen(it.open.toPlainString())
                    .setHigh(it.high.toPlainString())
                    .setLow(it.low.toPlainString())
                    .setClose(it.close.toPlainString())
                    .setVolume(it.volume)
                    .build()
            })
            .build()
    }
}
