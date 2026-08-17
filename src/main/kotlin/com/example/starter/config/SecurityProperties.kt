package com.example.starter.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@ConfigurationProperties(prefix = "standard-tools.security")
data class SecurityProperties(
    val authEnabled: Boolean = true,
    val apiKey: String = ""
) {
    /**
     * Returns true when the provided key authorizes the request.
     * Fails closed: when auth is enabled but no key is configured, nothing is authorized.
     */
    fun isAuthorized(providedKey: String?): Boolean {
        if (!authEnabled) return true
        if (apiKey.isBlank() || providedKey == null) return false
        return MessageDigest.isEqual(
            providedKey.toByteArray(StandardCharsets.UTF_8),
            apiKey.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
