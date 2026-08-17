package com.example.starter.portfolio.domain

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.stat.correlation.Covariance

class BlackLittermanOptimizer {

    companion object {
        const val MAX_PORTFOLIO_ASSETS = 100
    }

    fun optimize(
        returns: List<List<Double>>,
        tickers: List<String>,
        marketWeights: DoubleArray,
        pMatrix: Array<DoubleArray>,
        qVector: DoubleArray,
        riskAversion: Double = 2.5,
        tau: Double = 0.05,
        omega: Array<DoubleArray>? = null
    ): Portfolio {
        require(tickers.isNotEmpty() && returns.size == tickers.size)
        require(tickers.size <= MAX_PORTFOLIO_ASSETS) {
            "portfolio optimization supports at most $MAX_PORTFOLIO_ASSETS assets"
        }
        val aligned = align(returns)
        val cov = Covariance(aligned).covarianceMatrix
        val pi = cov.operate(MatrixUtils.createRealVector(marketWeights)).mapMultiply(riskAversion)
        val p = Array2DRowRealMatrix(pMatrix)
        val q = MatrixUtils.createRealVector(qVector)
        val omegaMatrix = omega?.let { Array2DRowRealMatrix(it) }
            ?: p.multiply(cov.scalarMultiply(tau)).multiply(p.transpose()).let { matrix ->
                val data = Array(matrix.rowDimension) { i -> DoubleArray(matrix.columnDimension) { j -> if (i == j) matrix.getEntry(i, j) else 0.0 } }
                Array2DRowRealMatrix(data)
            }
        val tauCov = cov.scalarMultiply(tau)
        val middle = LUDecomposition(p.multiply(tauCov).multiply(p.transpose()).add(omegaMatrix)).solver.inverse
        val posteriorReturn = tauCov.multiply(p.transpose()).multiply(middle).operate(q.subtract(p.operate(pi))).add(pi)
        val posteriorCov = cov.add(tauCov).subtract(tauCov.multiply(p.transpose()).multiply(middle).multiply(p).multiply(tauCov))
        val invCov = LUDecomposition(posteriorCov).solver.inverse
        val impliedWeights = invCov.operate(posteriorReturn).mapDivide(invCov.operate(posteriorReturn).toArray().sum())
        val portReturn = posteriorReturn.dotProduct(impliedWeights)
        val portVar = impliedWeights.dotProduct(posteriorCov.operate(impliedWeights))
        return Portfolio(
            objective = "black_litterman",
            tickers = tickers,
            weights = tickers.zip(impliedWeights.toArray().toList()).toMap(),
            expectedReturn = portReturn * 252,
            volatility = kotlin.math.sqrt(portVar) * kotlin.math.sqrt(252.0),
            sharpeRatio = null
        )
    }

    private fun align(returns: List<List<Double>>): Array<DoubleArray> {
        val minLen = returns.minOf { it.size }
        return Array(minLen) { row -> DoubleArray(returns.size) { col -> returns[col][returns[col].size - minLen + row] } }
    }
}
