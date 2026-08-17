package com.example.starter.adapter.`in`.grpc

import com.example.starter.config.SecurityProperties
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.stereotype.Component

/**
 * API-key authentication for the gRPC surface. Clients must send the key in
 * the `x-api-key` metadata header. Fails closed when auth is enabled but no
 * key is configured.
 */
@Component
@GlobalServerInterceptor
class ApiKeyAuthInterceptor(
    private val securityProperties: SecurityProperties
) : ServerInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        if (!securityProperties.authEnabled) {
            return next.startCall(call, headers)
        }
        if (securityProperties.apiKey.isBlank()) {
            logger.error("API-key auth is enabled but no key is configured (SQT_API_KEY); rejecting call (fail closed)")
        }
        val providedKey = headers.get(API_KEY_METADATA_KEY)
        if (!securityProperties.isAuthorized(providedKey)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid API key"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }

    companion object {
        val API_KEY_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER)
    }
}
