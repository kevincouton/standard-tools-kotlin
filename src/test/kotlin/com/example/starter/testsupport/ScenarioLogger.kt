package com.example.starter.testsupport

class ScenarioLogger(private val scenarioName: String) {

    private val steps = mutableListOf<String>()

    fun step(protocol: String, description: String, result: String) {
        steps.add("[$protocol] $description → $result")
    }

    fun print() {
        println("🧪 E2E Scenario: $scenarioName")
        steps.forEachIndexed { index, step ->
            val prefix = if (index == steps.lastIndex) "└─" else "├─"
            println("  $prefix $step")
        }
    }
}
