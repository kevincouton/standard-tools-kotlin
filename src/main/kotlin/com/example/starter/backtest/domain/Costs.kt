package com.example.starter.backtest.domain

object Costs {

    fun percentageCommission(notional: Double, pct: Double): Double = notional * pct

    fun perShareCommission(shares: Double, costPerShare: Double): Double = kotlin.math.abs(shares) * costPerShare

    fun fixedBpsSpread(mid: Double, bps: Double): Double = mid * bps / 10_000.0

    fun sqrtImpactBps(notional: Double, dailyVolume: Double, baseBps: Double = 10.0): Double {
        if (dailyVolume <= 0) return 0.0
        return baseBps * kotlin.math.sqrt(notional / dailyVolume)
    }
}
