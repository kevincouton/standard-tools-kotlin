package com.example.starter.audit

import com.example.starter.testsupport.PostgresTestContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AuditWriterConcurrencyTest {

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
    fun `concurrent writes are serialized into a single intact chain`() {
        val writers = 16
        val ready = CountDownLatch(writers)
        val start = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(writers)

        val futures = (1..writers).map { i ->
            pool.submit<AuditRecord> {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                try {
                    auditWriter.write(
                        toolName = "concurrent-tool-$i",
                        input = mapOf("i" to i),
                        durationMs = 1,
                        output = mapOf("result" to "ok-$i"),
                        status = "ok"
                    )
                } catch (ex: Exception) {
                    errors.incrementAndGet()
                    throw ex
                }
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        start.countDown()
        val records = futures.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        expectThat(errors.get()).isEqualTo(0)
        expectThat(records.map { it.recordHash }.distinct()).hasSize(writers)
        // Every record must point at a distinct predecessor: no two records
        // may share the same prev_record_hash (fork in the chain).
        expectThat(records.map { it.prevRecordHash }.distinct()).hasSize(writers)
        // The whole chain (including records from other tests) must verify.
        expectThat(auditVerifier.verify()).isEmpty()
    }
}
