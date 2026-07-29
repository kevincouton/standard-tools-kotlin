package com.example.starter.testsupport

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import java.time.Duration

class ColoredConsoleSummaryListener : TestExecutionListener {

    private val results = mutableMapOf<String, MutableList<TestResult>>()
    private val startTimes = mutableMapOf<String, Long>()

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        results.clear()
        startTimes.clear()
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) {
            startTimes[testIdentifier.uniqueId] = System.nanoTime()
        }
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (testIdentifier.isTest) {
            val tags = testIdentifier.tags.map { it.name }
            val layer = when {
                tags.contains("unit") -> "unit"
                tags.contains("integration") -> "integration"
                tags.contains("e2e") -> "e2e"
                else -> "unit"
            }
            val status = when (testExecutionResult.status) {
                TestExecutionResult.Status.SUCCESSFUL -> "passed"
                TestExecutionResult.Status.FAILED -> "failed"
                TestExecutionResult.Status.ABORTED -> "skipped"
            }
            val startNanos = startTimes[testIdentifier.uniqueId]
            val duration = if (startNanos != null) {
                Duration.ofNanos(System.nanoTime() - startNanos)
            } else {
                Duration.ZERO
            }
            results.getOrPut(layer) { mutableListOf() }.add(
                TestResult(
                    name = testIdentifier.displayName,
                    status = status,
                    duration = duration
                )
            )
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan) {
        println("\n========== TEST SUMMARY ==========")
        results.forEach { (layer, tests) ->
            val passed = tests.count { it.status == "passed" }
            val failed = tests.count { it.status == "failed" }
            val skipped = tests.count { it.status == "skipped" }
            val duration = tests.sumOf { it.duration.toMillis() }
            val icon = if (failed > 0) "❌" else "✅"
            println("$icon $layer: $passed passed, $failed failed, $skipped skipped (${duration}ms)")
            tests.filter { it.status == "failed" }.forEach {
                println("  ❌ ${it.name}")
            }
        }
        println("==================================\n")
    }

    data class TestResult(
        val name: String,
        val status: String,
        val duration: Duration
    )
}
