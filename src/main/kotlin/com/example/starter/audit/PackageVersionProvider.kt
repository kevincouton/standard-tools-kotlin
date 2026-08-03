package com.example.starter.audit

import java.util.Properties

object PackageVersionProvider {

    private const val FALLBACK = "0.0.1"

    fun version(): String {
        val manifestVersion = AuditWriter::class.java.`package`?.implementationVersion
        if (!manifestVersion.isNullOrBlank()) {
            return manifestVersion
        }

        val buildInfo = AuditWriter::class.java.classLoader.getResourceAsStream("META-INF/build-info.properties")?.use {
            val props = Properties()
            props.load(it)
            props.getProperty("build.version")
        }
        if (!buildInfo.isNullOrBlank()) {
            return buildInfo
        }

        return FALLBACK
    }
}
