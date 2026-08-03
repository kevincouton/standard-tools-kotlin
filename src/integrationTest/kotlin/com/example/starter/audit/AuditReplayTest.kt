package com.example.starter.audit

import com.example.starter.agent.ToolDispatcher
import com.example.starter.testsupport.PostgresTestContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.isTrue
import java.util.UUID

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AuditReplayTest {

    @Autowired
    lateinit var auditWriter: AuditWriter

    @Autowired
    lateinit var auditReplay: AuditReplay

    @Autowired
    lateinit var toolDispatcher: ToolDispatcher

    companion object {
        @Container
        val postgres = PostgresTestContainer.instance

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `replay get_option_pricing and output matches`() {
        val input = mapOf(
            "spot" to 100.0,
            "strike" to 100.0,
            "timeToExpiry" to 1.0,
            "riskFreeRate" to 0.05,
            "volatility" to 0.2,
            "optionType" to "call"
        )

        val result = toolDispatcher.dispatch("get_option_pricing", input)
        val requestId = UUID.randomUUID()
        auditWriter.write(
            requestId = requestId,
            toolName = "get_option_pricing",
            input = input,
            durationMs = 5,
            output = result,
            status = "ok"
        )

        val replayResult = auditReplay.replay(requestId)

        expectThat(replayResult.outputMatch).isTrue()
    }
}
