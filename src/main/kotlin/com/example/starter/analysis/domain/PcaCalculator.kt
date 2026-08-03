package com.example.starter.analysis.domain

import com.example.starter.shared.domain.PriceSeries
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.EigenDecomposition
import org.apache.commons.math3.stat.correlation.Covariance
import kotlin.math.sqrt

class PcaCalculator {

    fun calculate(tickers: List<String>, series: List<PriceSeries>, nComponents: Int? = null, standardize: Boolean = true): PcaResult {
        require(tickers.size == series.size && tickers.isNotEmpty())
        val returns = series.map { simpleReturns(it) }
        val minLen = returns.minOf { it.size }
        val aligned = returns.map { it.takeLast(minLen) }
        val data = Array(aligned.first().size) { row -> DoubleArray(aligned.size) { col -> aligned[col][row] } }
        val matrix = Array2DRowRealMatrix(data)
        val cols = (0 until matrix.columnDimension).map { col -> matrix.getColumn(col) }
        val standardized = if (standardize) {
            cols.map { arr ->
                val mean = arr.average()
                val std = sqrt(arr.map { (it - mean) * (it - mean) }.average()).coerceAtLeast(1e-12)
                arr.map { (it - mean) / std }.toDoubleArray()
            }
        } else cols
        val covMatrix = Covariance(standardized.map { it.toList().toDoubleArray() }.toTypedArray()).covarianceMatrix
        val eigen = EigenDecomposition(covMatrix)
        val eigenvalues = eigen.realEigenvalues
        val total = eigenvalues.sum()
        val components = nComponents?.coerceAtMost(eigenvalues.size) ?: eigenvalues.size
        val evr = eigenvalues.take(components).map { it / total }
        val loadings = tickers.zip(eigen.v.getSubMatrix(0, eigenvalues.size - 1, 0, components - 1).data.map { it.toList() }).toMap()
        val factorReturns = (0 until standardized.first().size).map { row ->
            val factorValues = (0 until components).map { pc ->
                standardized.sumOf { it[row] * eigen.v.getEntry(it.indices.first(), pc) }
            }
            (0 until components).associate { "PC${it + 1}" to factorValues[it] }
        }
        return PcaResult(explainedVarianceRatio = evr, loadings = loadings, factorReturns = factorReturns)
    }

    private fun simpleReturns(series: PriceSeries): List<Double> = series.zipWithNext { prev, curr ->
        (curr.close.toDouble() - prev.close.toDouble()) / prev.close.toDouble()
    }
}
