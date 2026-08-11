package com.example.starter.screener.adapter.`in`.grpc

import com.example.starter.screener.application.port.inbound.ScreenStocksUseCase
import com.example.starter.screener.domain.ScreenCriteria
import com.example.starter.screener.domain.ScreenMatch
import com.example.starter.screener.grpc.ScreenRequest
import com.example.starter.screener.grpc.ScreenResponse
import com.example.starter.screener.grpc.ScreenerServiceGrpcKt
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.shared.domain.toBarInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.grpc.server.service.GrpcService
import java.time.LocalDate

@GrpcService
class ScreenerGrpcService(
    private val screenStocksUseCase: ScreenStocksUseCase
) : ScreenerServiceGrpcKt.ScreenerServiceCoroutineImplBase() {

    override suspend fun screen(request: ScreenRequest): ScreenResponse = withContext(Dispatchers.IO) {
        val result = screenStocksUseCase.screen(
            ScreenStocksUseCase.ScreenCommand(
                tickers = request.tickersList,
                criteria = ScreenCriteria(
                    peRatioMax = request.peRatioMax.takeIf { it > 0 },
                    pbRatioMax = request.pbRatioMax.takeIf { it > 0 },
                    roeMin = request.roeMin.takeIf { it > 0 },
                    rsiMax = request.rsiMax.takeIf { it > 0 }
                ),
                range = DateRange(LocalDate.parse(request.startDate), LocalDate.parse(request.endDate)),
                interval = request.interval.toBarInterval(),
                provider = request.provider.takeIf { it.isNotBlank() },
                sortBy = request.sortBy.takeIf { it.isNotBlank() },
                ascending = request.ascending
            )
        )
        toResponse(result)
    }

    private fun toResponse(result: com.example.starter.screener.domain.ScreenResult): ScreenResponse {
        return ScreenResponse.newBuilder()
            .addAllMatches(result.matches.map { toMatch(it) })
            .addAllFailedTickers(result.failedTickers)
            .build()
    }

    private fun toMatch(match: ScreenMatch): com.example.starter.screener.grpc.ScreenMatch {
        return com.example.starter.screener.grpc.ScreenMatch.newBuilder()
            .setTicker(match.ticker)
            .setPeRatio(match.fundamentals.peRatio ?: 0.0)
            .setPbRatio(match.fundamentals.pbRatio ?: 0.0)
            .setRsi(match.rsi ?: 0.0)
            .build()
    }
}
