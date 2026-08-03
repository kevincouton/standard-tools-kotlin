package com.example.starter.audit

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
import strikt.assertions.isEmpty
import strikt.assertions.isNotEmpty

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AuditRecordWriteAndVerifyTest {

    @Autowired
    lateinit var auditWriter: AuditWriter

    @Autowired
    lateinit var auditVerifier: AuditVerifier

    @Autowired
    lateinit var auditRecordRepository: AuditRecordRepository

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
    fun `write two records and verify chain is intact`() {
        auditWriter.write(
            toolName = "tool1",
            input = mapOf("a" to 1),
            durationMs = 10,
            output = mapOf("result" to "ok"),
            status = "ok"
        )
        auditWriter.write(
            toolName = "tool2",
            input = mapOf("b" to 2),
            durationMs = 20,
            output = mapOf("result" to "ok2"),
            status = "ok"
        )

        val problems = auditVerifier.verify()

        expectThat(problems).isEmpty()
    }

    @Test
    fun `tampering a record is detected`() {
        val first = auditWriter.write(
            toolName = "tool1",
            input = mapOf("a" to 1),
            durationMs = 10,
            output = mapOf("result" to "ok"),
            status = "ok"
        )
        auditWriter.write(
            toolName = "tool2",
            input = mapOf("b" to 2),
            durationMs = 20,
            output = mapOf("result" to "ok2"),
            status = "ok"
        )

        first.inputJson = """{"a":2}"""
        auditRecordRepository.save(first)

        val problems = auditVerifier.verify()

        expectThat(problems).isNotEmpty()
    }
}
