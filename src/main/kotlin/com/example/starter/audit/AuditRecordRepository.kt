package com.example.starter.audit

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditRecordRepository : JpaRepository<AuditRecord, UUID> {
    fun findTopByOrderByTimestampDesc(): AuditRecord?
    fun findByRequestId(requestId: UUID): AuditRecord?
    fun findAllByOrderByTimestampDesc(): List<AuditRecord>
    fun findAllByOrderByTimestampAsc(): List<AuditRecord>
}
