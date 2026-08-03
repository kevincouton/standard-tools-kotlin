package com.example.starter.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_records")
class AuditRecord(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "request_id", nullable = false)
    var requestId: UUID = UUID.randomUUID(),

    @Column(name = "timestamp", nullable = false)
    var timestamp: Instant = Instant.now(),

    @Column(name = "tool_name", nullable = false, length = 255)
    var toolName: String = "",

    @Column(name = "input_json", nullable = false, columnDefinition = "TEXT")
    var inputJson: String = "",

    @Column(name = "data_sources_json", columnDefinition = "TEXT")
    var dataSourcesJson: String? = null,

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0L,

    @Column(name = "output_hash", nullable = false, length = 16)
    var outputHash: String = "",

    @Column(name = "status", nullable = false, length = 20)
    var status: String = "",

    @Column(name = "error_type", length = 255)
    var errorType: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "git_commit_sha", length = 40)
    var gitCommitSha: String? = null,

    @Column(name = "package_version", length = 50)
    var packageVersion: String? = null,

    @Column(name = "prev_record_hash", nullable = false, length = 16)
    var prevRecordHash: String = "",

    @Column(name = "record_hash", nullable = false, length = 16)
    var recordHash: String = ""
)
