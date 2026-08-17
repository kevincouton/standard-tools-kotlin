package com.example.starter.adapter.`in`.grpc

import com.example.starter.config.SecurityProperties
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.Status
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@Tag("unit")
class ApiKeyAuthInterceptorTest {

    @Suppress("UNCHECKED_CAST")
    private fun callMock(): ServerCall<String, String> = mockk(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    private fun handlerMock(): ServerCallHandler<String, String> = mockk {
        every { startCall(any(), any()) } returns mockk<ServerCall.Listener<String>>()
    }

    @Test
    fun `call without api key metadata is closed with UNAUTHENTICATED`() {
        val interceptor = ApiKeyAuthInterceptor(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val call = callMock()

        interceptor.interceptCall(call, Metadata(), handlerMock())

        verify {
            call.close(
                withArg { expectThat(it.code).isEqualTo(Status.Code.UNAUTHENTICATED) },
                any()
            )
        }
    }

    @Test
    fun `call with wrong api key is closed with UNAUTHENTICATED`() {
        val interceptor = ApiKeyAuthInterceptor(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val call = callMock()
        val headers = Metadata().apply {
            put(ApiKeyAuthInterceptor.API_KEY_METADATA_KEY, "wrong")
        }

        interceptor.interceptCall(call, headers, handlerMock())

        verify {
            call.close(
                withArg { expectThat(it.code).isEqualTo(Status.Code.UNAUTHENTICATED) },
                any()
            )
        }
    }

    @Test
    fun `call with correct api key proceeds`() {
        val interceptor = ApiKeyAuthInterceptor(SecurityProperties(authEnabled = true, apiKey = "secret"))
        val call = callMock()
        val handler = handlerMock()
        val headers = Metadata().apply {
            put(ApiKeyAuthInterceptor.API_KEY_METADATA_KEY, "secret")
        }

        interceptor.interceptCall(call, headers, handler)

        verify { handler.startCall(call, headers) }
        verify(exactly = 0) { call.close(any(), any()) }
    }

    @Test
    fun `auth disabled lets every call through`() {
        val interceptor = ApiKeyAuthInterceptor(SecurityProperties(authEnabled = false, apiKey = ""))
        val call = callMock()
        val handler = handlerMock()

        interceptor.interceptCall(call, Metadata(), handler)

        verify { handler.startCall(call, any()) }
    }

    @Test
    fun `auth enabled without configured key fails closed`() {
        val interceptor = ApiKeyAuthInterceptor(SecurityProperties(authEnabled = true, apiKey = ""))
        val call = callMock()
        val headers = Metadata().apply {
            put(ApiKeyAuthInterceptor.API_KEY_METADATA_KEY, "anything")
        }

        interceptor.interceptCall(call, headers, handlerMock())

        verify {
            call.close(
                withArg { expectThat(it.code).isEqualTo(Status.Code.UNAUTHENTICATED) },
                any()
            )
        }
    }
}
