package com.example.starter.native

import com.example.starter.testsupport.PostgresTestContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotNull

@Tag("native")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class NativeImageSmokeTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

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
    fun `actuator health is up`() {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `agent tools endpoint returns functions`() {
        val result = webTestClient.get().uri("/api/v1/agent/tools")
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()

        @Suppress("UNCHECKED_CAST")
        val tools = result.responseBody?.get("tools") as? List<Map<String, Any>>
        expectThat(tools).isNotNull()
        expectThat(tools!!.size).isGreaterThanOrEqualTo(42)

        val first = tools.first()
        expectThat(first["type"]).isEqualTo("function")
    }
}
