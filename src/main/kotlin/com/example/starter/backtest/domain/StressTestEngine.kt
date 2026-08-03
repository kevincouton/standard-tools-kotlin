package com.example.starter.backtest.domain

object StressTestEngine {

    private val SCENARIOS = mapOf(
        "covid_crash_2020" to Pair("2020-02-19", "2020-03-23"),
        "gfc_2008" to Pair("2008-10-01", "2008-12-01"),
        "dot_com_2002" to Pair("2002-03-01", "2002-07-01"),
        "black_monday_1987" to Pair("1987-10-14", "1987-10-26")
    )

    fun listScenarios(): List<String> = SCENARIOS.keys.toList()

    fun scenarioDates(scenario: String): Pair<String, String>? = SCENARIOS[scenario]
}
