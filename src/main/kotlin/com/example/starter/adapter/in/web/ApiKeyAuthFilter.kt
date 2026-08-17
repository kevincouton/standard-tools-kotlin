package com.example.starter.adapter.`in`.web

import com.example.starter.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * API-key authentication for every HTTP surface (REST, A2A, MCP).
 * Only the actuator health endpoint is exempt. When auth is enabled but no
 * key is configured, all requests are rejected (fail closed).
 */
@Component
class ApiKeyAuthFilter(
    private val securityProperties: SecurityProperties
) : WebFilter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!securityProperties.authEnabled || isExempt(exchange.request.path.value())) {
            return chain.filter(exchange)
        }
        if (securityProperties.authEnabled && securityProperties.apiKey.isBlank()) {
            logger.error("API-key auth is enabled but no key is configured (SQT_API_KEY); rejecting request (fail closed)")
        }
        val providedKey = exchange.request.headers.getFirst(API_KEY_HEADER)
        if (!securityProperties.isAuthorized(providedKey)) {
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }
        return chain.filter(exchange)
    }

    private fun isExempt(path: String): Boolean =
        path == "/actuator/health" || path.startsWith("/actuator/health/")

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }
}
