package com.example.starter.shared.domain

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.math.BigDecimal
import java.time.LocalDate

@Tag("unit")
class ValueObjectsTest {

    @Test
    fun `Ticker rejects blank symbol`() {
        val exception = assertThrows<IllegalArgumentException> {
            Ticker(symbol = "   ")
        }
        expectThat(exception.message).isEqualTo("symbol must not be blank")
    }

    @Test
    fun `DateRange rejects start after end`() {
        val start = LocalDate.of(2024, 1, 10)
        val end = LocalDate.of(2024, 1, 1)

        val exception = assertThrows<IllegalArgumentException> {
            DateRange(start = start, end = end)
        }
        expectThat(exception.message).isEqualTo("start must not be after end")
    }

    @Test
    fun `OHLCV rejects high less than low`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(high = BigDecimal("90.00"), low = BigDecimal("100.00"))
        }
    }

    @Test
    fun `OHLCV rejects negative open`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(open = BigDecimal("-1.00"))
        }
    }

    @Test
    fun `OHLCV rejects negative high`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(high = BigDecimal("-1.00"))
        }
    }

    @Test
    fun `OHLCV rejects negative low`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(low = BigDecimal("-1.00"))
        }
    }

    @Test
    fun `OHLCV rejects negative close`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(close = BigDecimal("-1.00"))
        }
    }

    @Test
    fun `OHLCV rejects negative volume`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(volume = -1L)
        }
    }

    @Test
    fun `OHLCV rejects high less than max of open and close`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(open = BigDecimal("110.00"), high = BigDecimal("105.00"), close = BigDecimal("100.00"))
        }
    }

    @Test
    fun `OHLCV rejects low greater than min of open and close`() {
        assertThrows<IllegalArgumentException> {
            validOhlcv().copy(open = BigDecimal("90.00"), low = BigDecimal("95.00"), close = BigDecimal("100.00"))
        }
    }

    @Test
    fun `toBarInterval parses uppercase value`() {
        expectThat("DAILY".toBarInterval()).isEqualTo(BarInterval.DAILY)
    }

    @Test
    fun `toBarInterval parses lowercase value`() {
        expectThat("weekly".toBarInterval()).isEqualTo(BarInterval.WEEKLY)
    }

    @Test
    fun `toBarInterval parses mixed case value`() {
        expectThat("Monthly".toBarInterval()).isEqualTo(BarInterval.MONTHLY)
    }

    @Test
    fun `toBarInterval trims whitespace`() {
        expectThat("  daily  ".toBarInterval()).isEqualTo(BarInterval.DAILY)
    }

    @Test
    fun `toBarInterval rejects invalid value`() {
        val exception = assertThrows<InvalidCommandException> {
            "hourly".toBarInterval()
        }
        expectThat(exception.message).isEqualTo(
            "Invalid command: interval must be one of ${BarInterval.entries.joinToString { it.name }}"
        )
    }

    @Test
    fun `CacheKey toComposite produces expected string`() {
        val key = CacheKey(
            provider = "yahoo",
            ticker = Ticker(symbol = "AAPL", exchange = "NASDAQ"),
            interval = BarInterval.DAILY,
            range = DateRange(
                start = LocalDate.of(2024, 1, 1),
                end = LocalDate.of(2024, 1, 10)
            )
        )

        expectThat(key.toComposite()).isEqualTo("yahoo:AAPL:NASDAQ:DAILY:2024-01-01:2024-01-10")
    }

    @Test
    fun `CacheKey toComposite handles null exchange`() {
        val key = CacheKey(
            provider = "yahoo",
            ticker = Ticker(symbol = "AAPL", exchange = null),
            interval = BarInterval.DAILY,
            range = DateRange(
                start = LocalDate.of(2024, 1, 1),
                end = LocalDate.of(2024, 1, 10)
            )
        )

        expectThat(key.toComposite()).isEqualTo("yahoo:AAPL::DAILY:2024-01-01:2024-01-10")
    }

    private fun validOhlcv(): OHLCV = OHLCV(
        ticker = Ticker(symbol = "AAPL"),
        date = LocalDate.of(2024, 1, 1),
        open = BigDecimal("100.00"),
        high = BigDecimal("105.00"),
        low = BigDecimal("95.00"),
        close = BigDecimal("102.00"),
        volume = 1_000_000L
    )
}
