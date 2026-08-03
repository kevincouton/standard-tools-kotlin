package com.example.starter.audit.adapter.`in`.web

import com.example.starter.audit.AuditRecord
import com.example.starter.audit.AuditRecordRepository
import com.example.starter.audit.AuditReplay
import com.example.starter.audit.AuditVerifier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val repository: AuditRecordRepository,
    private val verifier: AuditVerifier,
    private val replay: AuditReplay
) {

    @GetMapping("/records")
    fun listRecords(): List<AuditRecord> = repository.findAllByOrderByTimestampDesc()

    @GetMapping("/records/{requestId}")
    fun getRecord(@PathVariable requestId: UUID): ResponseEntity<AuditRecord> =
        repository.findByRequestId(requestId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping("/verify")
    fun verify(): VerifyResponse {
        val problems = verifier.verify()
        return VerifyResponse(problems = problems, valid = problems.isEmpty())
    }

    @PostMapping("/replay/{requestId}")
    fun replay(@PathVariable requestId: UUID): ResponseEntity<AuditReplay.ReplayResult> =
        ResponseEntity.ok(replay.replay(requestId))

    data class VerifyResponse(
        val problems: List<String>,
        val valid: Boolean = problems.isEmpty()
    )
}
