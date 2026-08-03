package com.example.starter.agent.e2e

import com.example.starter.testsupport.PostgresTestContainer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
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
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull

@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class AgentToolsE2ETest {

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
    fun `GET agent tools returns OpenAI function list`() {
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
        @Suppress("UNCHECKED_CAST")
        val function = first["function"] as Map<String, Any>
        expectThat(function["name"]).isNotNull()
        expectThat(function["description"]).isNotNull()
        expectThat(function["parameters"]).isNotNull()
    }

    @Test
    fun `POST agent dispatch runs option pricing tool`() {
        val result = webTestClient.post().uri("/api/v1/agent/dispatch")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "tool" to "get_option_pricing",
                    "arguments" to mapOf(
                        "spot" to 100.0,
                        "strike" to 100.0,
                        "timeToExpiry" to 1.0,
                        "riskFreeRate" to 0.05,
                        "volatility" to 0.20,
                        "optionType" to "call"
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()

        @Suppress("UNCHECKED_CAST")
        val body = result.responseBody ?: emptyMap<String, Any>()
        println("Dispatch response body: $body")
        expectThat(body["tool"]).isEqualTo("get_option_pricing")
        @Suppress("UNCHECKED_CAST")
        val toolResult = body["result"] as? Map<String, Any>
        expectThat(toolResult).isNotNull()
        expectThat(toolResult?.get("operation")).isEqualTo("option-pricing")
        expectThat(toolResult?.get("price")).isNotNull()
    }
}
