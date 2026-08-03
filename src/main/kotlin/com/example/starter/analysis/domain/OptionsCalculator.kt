package com.example.starter.analysis.domain

import com.example.starter.analysis.application.port.inbound.RunAnalysisUseCase
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class OptionsCalculator {

    private fun d1(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, div: Double): Double {
        return (ln(spot / strike) + (rate - div + 0.5 * vol * vol) * time) / (vol * sqrt(time))
    }

    private fun d2(d1: Double, vol: Double, time: Double): Double = d1 - vol * sqrt(time)

    private fun normCdf(x: Double): Double {
        return 0.5 * (1.0 + org.apache.commons.math3.special.Erf.erf(x / sqrt(2.0)))
    }

    fun price(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, optionType: String = "call", div: Double = 0.0): Double {
        require(time > 0 && vol > 0)
        val d1v = d1(spot, strike, time, rate, vol, div)
        val d2v = d2(d1v, vol, time)
        val discount = exp(-rate * time)
        val divDiscount = exp(-div * time)
        return when (optionType.lowercase()) {
            "put" -> strike * discount * normCdf(-d2v) - spot * divDiscount * normCdf(-d1v)
            else -> spot * divDiscount * normCdf(d1v) - strike * discount * normCdf(d2v)
        }
    }

    fun greeks(spot: Double, strike: Double, time: Double, rate: Double, vol: Double, optionType: String = "call", div: Double = 0.0): OptionGreeks {
        val d1v = d1(spot, strike, time, rate, vol, div)
        val d2v = d2(d1v, vol, time)
        val nd1 = normCdf(d1v)
        val pdfD1 = exp(-0.5 * d1v * d1v) / sqrt(2.0 * PI)
        val discount = exp(-rate * time)
        val divDiscount = exp(-div * time)
        val delta = when (optionType.lowercase()) {
            "put" -> divDiscount * (nd1 - 1.0)
            else -> divDiscount * nd1
        }
        val gamma = divDiscount * pdfD1 / (spot * vol * sqrt(time))
        val vega = spot * divDiscount * pdfD1 * sqrt(time) / 100.0
        val theta = when (optionType.lowercase()) {
            "put" -> (-spot * divDiscount * pdfD1 * vol / (2.0 * sqrt(time)) + rate * strike * discount * normCdf(-d2v) - div * spot * divDiscount * normCdf(-d1v)) / 365.0
            else -> (-spot * divDiscount * pdfD1 * vol / (2.0 * sqrt(time)) - rate * strike * discount * normCdf(d2v) + div * spot * divDiscount * normCdf(d1v)) / 365.0
        }
        val rho = when (optionType.lowercase()) {
            "put" -> -strike * time * discount * normCdf(-d2v) / 100.0
            else -> strike * time * discount * normCdf(d2v) / 100.0
        }
        return OptionGreeks(delta = delta, gamma = gamma, vega = vega, theta = theta, rho = rho)
    }

    fun impliedVolatility(marketPrice: Double, spot: Double, strike: Double, time: Double, rate: Double, optionType: String = "call", div: Double = 0.0, initialGuess: Double = 0.2, tol: Double = 1e-6, maxIter: Int = 100): Double? {
        var vol = initialGuess.coerceAtLeast(0.001)
        repeat(maxIter) {
            val p = price(spot, strike, time, rate, vol, optionType, div)
            val g = greeks(spot, strike, time, rate, vol, optionType, div)
            val diff = p - marketPrice
            if (kotlin.math.abs(diff) < tol) return vol
            if (g.vega == 0.0) return null
            vol -= diff / (g.vega * 100.0)
            if (vol <= 0) vol = 0.001
        }
        return null
    }

    fun calculate(command: RunAnalysisUseCase.OptionPricingCommand): OptionPricingResult {
        val price = price(command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.volatility, command.optionType, command.dividendYield)
        val greeks = greeks(command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.volatility, command.optionType, command.dividendYield)
        val iv = command.marketPrice?.let { impliedVolatility(it, command.spot, command.strike, command.timeToExpiry, command.riskFreeRate, command.optionType, command.dividendYield) }
        return OptionPricingResult(price = price, greeks = greeks, impliedVolatility = iv)
    }
}
