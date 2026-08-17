package com.example.starter.adapter.`in`.web

import com.example.starter.config.SecurityProperties
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue
import java.util.concurrent.atomic.AtomicBoolean

@Tag("unit")
class ApiKeyAuthFilterTest {

    private fun chainInvoked(flag: AtomicBoolean): WebFilterChain = WebFilterChain {
        flag.set(true)
        Mono.empty()
    }

    private fun exchange(path: String, apiKey: String? = null): MockServerWebExchange {
        val request = MockServerHttpRequest.get(path)
        apiKey?.let { request.header(ApiKeyAuthFilter.API_KEY_HEADER, it) }
        return MockServerWebExchange.from(request)
    }

    @Test
    fun `request without api key is rejected with 401`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/api/orders")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        expectThat(invoked.get()).isFalse()
    }

    @Test
    fun `request with wrong api key is rejected with 401`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/api/orders", apiKey = "wrong")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        expectThat(invoked.get()).isFalse()
    }

    @Test
    fun `request with correct api key proceeds`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/api/orders", apiKey = "secret")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(ex.response.statusCode).isNull()
        expectThat(invoked.get()).isTrue()
    }

    @Test
    fun `actuator health endpoint is exempt from auth`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/actuator/health")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(invoked.get()).isTrue()
        expectThat(ex.response.statusCode).isNull()
    }

    @Test
    fun `auth disabled lets every request through`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = false, apiKey = ""))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/api/orders")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(invoked.get()).isTrue()
        expectThat(ex.response.statusCode).isNull()
    }

    @Test
    fun `auth enabled without configured key fails closed`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = ""))
        val invoked = AtomicBoolean(false)
        val ex = exchange("/api/orders", apiKey = "anything")

        filter.filter(ex, chainInvoked(invoked)).block()

        expectThat(ex.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        expectThat(invoked.get()).isFalse()
    }

    @Test
    fun `mcp and a2a endpoints require api key`() {
        val filter = ApiKeyAuthFilter(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val mcpInvoked = AtomicBoolean(false)
        val a2aInvoked = AtomicBoolean(false)
        val mcp = exchange("/mcp/messages")
        val a2a = exchange("/a2a/tasks")

        filter.filter(mcp, chainInvoked(mcpInvoked)).block()
        filter.filter(a2a, chainInvoked(a2aInvoked)).block()

        expectThat(mcp.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        expectThat(a2a.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        expectThat(mcpInvoked.get()).isFalse()
        expectThat(a2aInvoked.get()).isFalse()
    }
}
