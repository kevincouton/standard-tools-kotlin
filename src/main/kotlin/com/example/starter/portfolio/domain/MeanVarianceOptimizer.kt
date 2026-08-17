package com.example.starter.portfolio.domain

import org.apache.commons.math3.analysis.MultivariateFunction
import org.apache.commons.math3.optim.InitialGuess
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.PointValuePair
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer
import org.apache.commons.math3.stat.correlation.Covariance
import com.example.starter.shared.domain.InvalidCommandException
import kotlin.math.sqrt

class MeanVarianceOptimizer {

    companion object {
        const val MAX_PORTFOLIO_ASSETS = 100
    }

    fun optimize(
        returns: List<List<Double>>,
        tickers: List<String>,
        objective: String = "max_sharpe",
        riskFreeRate: Double = 0.02,
        targetReturn: Double? = null,
        targetVolatility: Double? = null,
        allowShort: Boolean = false,
        maxWeight: Double? = null
    ): Portfolio {
        require(returns.size == tickers.size && returns.isNotEmpty())
        require(tickers.size <= MAX_PORTFOLIO_ASSETS) {
            "portfolio optimization supports at most $MAX_PORTFOLIO_ASSETS assets"
        }
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val meanReturns = returns.map { it.average() }.toDoubleArray()
        val n = tickers.size

        val lower = if (allowShort) DoubleArray(n) { -1.0 } else DoubleArray(n) { 0.0 }
        val upper = DoubleArray(n) { maxWeight ?: 1.0 }
        val initial = DoubleArray(n) { 1.0 / n }

        if (objective == "target_return") {
            requireCommand(targetReturn != null) { "target_return objective requires targetReturn" }
        }
        if (objective == "target_volatility") {
            requireCommand(targetVolatility != null) { "target_volatility objective requires targetVolatility" }
        }

        val objectiveFn = MultivariateFunction { weights ->
            val w = weights.normalize()
            val portReturn = meanReturns.zip(w).sumOf { (r, weight) -> r * weight }
            val variance = w.foldIndexed(0.0) { i, acc, wi ->
                acc + wi * w.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
            }
            val volatility = sqrt(variance)
            when (objective) {
                "min_volatility" -> variance
                "target_return" -> penalty(
                    portReturn,
                    targetReturn ?: throw InvalidCommandException("target_return objective requires targetReturn"),
                    variance
                )
                "target_volatility" -> {
                    val target = targetVolatility
                        ?: throw InvalidCommandException("target_volatility objective requires targetVolatility")
                    (volatility - target) * (volatility - target) - portReturn
                }
                else -> -(portReturn - riskFreeRate / 252) / volatility
            }
        }

        val boundedFn = MultivariateFunctionMappingAdapter(objectiveFn, lower, upper)
        val optimizer = SimplexOptimizer(1e-8, 1e-12)
        val optimum: PointValuePair = optimizer.optimize(
            ObjectiveFunction(boundedFn),
            GoalType.MINIMIZE,
            MaxEval(10_000),
            InitialGuess(boundedFn.boundedToUnbounded(initial)),
            NelderMeadSimplex(n)
        )
        val weights = boundedFn.unboundedToBounded(optimum.point).normalize()
        val finalReturn = meanReturns.zip(weights).sumOf { (r, w) -> r * w }
        val finalVol = sqrt(weights.foldIndexed(0.0) { i, acc, wi ->
            acc + wi * weights.foldIndexed(0.0) { j, sum, wj -> sum + wj * cov.getEntry(i, j) }
        }) * sqrt(252.0)
        val sharpe = if (finalVol == 0.0) null else (finalReturn * 252 - riskFreeRate) / finalVol
        return Portfolio(
            objective = objective,
            tickers = tickers,
            weights = tickers.zip(weights.asIterable()).toMap(),
            expectedReturn = finalReturn * 252,
            volatility = finalVol,
            sharpeRatio = sharpe
        )
    }

    private fun penalty(portReturn: Double, target: Double, variance: Double): Double {
        val returnError = (portReturn * 252 - target) * (portReturn * 252 - target)
        return variance + 100.0 * returnError
    }

    private fun DoubleArray.normalize(): DoubleArray {
        val sum = sum()
        return if (sum == 0.0) this else map { it / sum }.toDoubleArray()
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }

    @OptIn(kotlin.contracts.ExperimentalContracts::class)
    private inline fun requireCommand(value: Boolean, lazyMessage: () -> String) {
        kotlin.contracts.contract {
            callsInPlace(lazyMessage, kotlin.contracts.InvocationKind.AT_MOST_ONCE)
            returns() implies value
        }
        if (!value) throw InvalidCommandException(lazyMessage())
    }
}
