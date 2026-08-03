package com.example.starter.audit

import java.io.File
import java.util.Properties

object GitInfoProvider {

    fun sha(): String? {
        val gitProperties = AuditWriter::class.java.classLoader.getResourceAsStream("git.properties")?.use {
            val props = Properties()
            props.load(it)
            props.getProperty("git.commit.id") ?: props.getProperty("git.commit.id.abbrev")
        }
        if (!gitProperties.isNullOrBlank()) {
            return gitProperties
        }

        return try {
            ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(File("."))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim().takeIf { sha -> sha.length >= 7 } }
        } catch (_: Exception) {
            null
        }
    }
}
