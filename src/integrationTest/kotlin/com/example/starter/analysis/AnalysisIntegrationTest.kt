package com.example.starter.analysis

import com.example.starter.analysis.application.service.AnalysisService
import com.example.starter.analysis.domain.CointegrationCalculator
import com.example.starter.analysis.domain.CorrelationCalculator
import com.example.starter.analysis.domain.HurstCalculator
import com.example.starter.analysis.domain.MultiFactorCalculator
import com.example.starter.analysis.domain.OptionsCalculator
import com.example.starter.analysis.domain.PcaCalculator
import com.example.starter.analysis.domain.RegressionCalculator
import com.example.starter.marketdata.application.port.inbound.FetchMarketDataUseCase
import com.example.starter.shared.domain.BarInterval
import com.example.starter.shared.domain.DateRange
import com.example.starter.shared.domain.Ticker
import com.example.starter.testsupport.fixtures.OhlcvFixtures
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import java.time.LocalDate

@Tag("integration")
class AnalysisIntegrationTest {

    private val fetch = mockk<FetchMarketDataUseCase>()
    private val service = AnalysisService(
        fetchMarketDataUseCase = fetch,
        regressionCalculator = RegressionCalculator(),
        cointegrationCalculator = CointegrationCalculator(),
        hurstCalculator = HurstCalculator(),
        pcaCalculator = PcaCalculator(),
        correlationCalculator = CorrelationCalculator(),
        multiFactorCalculator = MultiFactorCalculator(),
        optionsCalculator = OptionsCalculator()
    )

    @Test
    fun `regression for identical series has beta near one`() {
        every { fetch.fetch(any()) } returns OhlcvFixtures.dailySeries(days = 60)
        val result = service.execute(
            com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase.RegressionCommand(
                asset = Ticker("A"),
                benchmark = Ticker("A"),
                range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)),
                interval = BarInterval.DAILY
            )
        ) as com.example.starter.analysis.domain.RegressionResult
        expectThat(result.beta).isGreaterThan(0.95)
    }
}
