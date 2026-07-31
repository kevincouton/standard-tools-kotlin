package com.example.starter.analysis.domain

sealed class AnalysisResult {
    abstract val operation: String
}

data class RegressionResult(
    override val operation: String = "regression",
    val alpha: Double,
    val beta: Double,
    val rSquared: Double,
    val annualizedAlpha: Double?
) : AnalysisResult()

data class CointegrationResult(
    override val operation: String = "cointegration",
    val hedgeRatio: Double,
    val adfStatistic: Double,
    val pValueApprox: Double,
    val halfLife: Double,
    val currentZScore: Double?
) : AnalysisResult()

data class HurstResult(
    override val operation: String = "hurst",
    val exponent: Double,
    val regime: String,
    val rolling: List<Map<String, Double>>? = null
) : AnalysisResult()

data class PcaResult(
    override val operation: String = "pca",
    val explainedVarianceRatio: List<Double>,
    val loadings: Map<String, List<Double>>,
    val factorReturns: List<Map<String, Double>>
) : AnalysisResult()

data class CorrelationResult(
    override val operation: String = "correlation",
    val matrix: Map<String, Map<String, Double>>,
    val average: Double,
    val min: Double,
    val max: Double,
    val diversificationRatio: Double?
) : AnalysisResult()

data class MultiFactorResult(
    override val operation: String = "multi-factor",
    val alpha: Double,
    val loadings: Map<String, Double>,
    val tStatistics: Map<String, Double>,
    val pValues: Map<String, Double>,
    val rSquared: Double,
    val adjRSquared: Double
) : AnalysisResult()

data class OptionPricingResult(
    override val operation: String = "option-pricing",
    val price: Double,
    val greeks: OptionGreeks,
    val impliedVolatility: Double?
) : AnalysisResult()

data class OptionGreeks(
    val delta: Double,
    val gamma: Double,
    val vega: Double,
    val theta: Double,
    val rho: Double
)
