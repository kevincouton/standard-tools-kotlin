package com.example.starter.audit.adapter.`in`.shell

import com.example.starter.audit.AuditRecordRepository
import com.example.starter.audit.AuditReplay
import com.example.starter.audit.AuditVerifier
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import java.util.UUID

@ShellComponent
class AuditShellCommands(
    private val repository: AuditRecordRepository,
    private val verifier: AuditVerifier,
    private val replay: AuditReplay
) {

    @ShellMethod(key = ["audit:report"], value = "Show a single audit record by requestId")
    fun report(requestId: String): String {
        val record = repository.findByRequestId(UUID.fromString(requestId))
            ?: return "No audit record found for requestId=$requestId"
        return """
            requestId: ${record.requestId}
            toolName: ${record.toolName}
            timestamp: ${record.timestamp}
            status: ${record.status}
            durationMs: ${record.durationMs}
            outputHash: ${record.outputHash}
            prevRecordHash: ${record.prevRecordHash}
            recordHash: ${record.recordHash}
        """.trimIndent()
    }

    @ShellMethod(key = ["audit:replay"], value = "Replay an audited tool call by requestId")
    fun replay(requestId: String): String {
        val result = replay.replay(UUID.fromString(requestId))
        return "Replayed ${result.toolName}: outputMatch=${result.outputMatch}, " +
                "original=${result.originalOutputHash}, replay=${result.replayOutputHash}" +
                (result.error?.let { ", error=$it" } ?: "")
    }

    @ShellMethod(key = ["audit:verify"], value = "Verify the audit hash chain")
    fun verify(): String {
        val problems = verifier.verify()
        return if (problems.isEmpty()) "Audit chain is valid." else problems.joinToString("\n")
    }
}
