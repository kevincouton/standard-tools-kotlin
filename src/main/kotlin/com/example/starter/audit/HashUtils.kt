package com.example.starter.audit

import java.security.MessageDigest

object HashUtils {

    private const val HEX = "0123456789abcdef"

    fun sha256Truncated16(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte ->
            val b = byte.toInt()
            "${HEX[(b shr 4) and 0xF]}${HEX[b and 0xF]}"
        }
    }
}
