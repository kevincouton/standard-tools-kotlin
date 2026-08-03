package com.example.starter.audit

import org.springframework.stereotype.Service
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper

@Service
class AuditVerifier(
    private val repository: AuditRecordRepository
) {

    private val canonicalMapper: ObjectMapper = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .build()

    fun verify(): List<String> {
        val records = repository.findAllByOrderByTimestampAsc()
        val problems = mutableListOf<String>()
        var previousHash = AuditWriter.GENESIS_HASH

        for ((index, record) in records.withIndex()) {
            if (record.prevRecordHash != previousHash) {
                problems.add(
                    "Record $index (${record.id}) prev_record_hash mismatch: expected $previousHash, got ${record.prevRecordHash}"
                )
            }
            val expectedHash = computeExpectedHash(record)
            if (record.recordHash != expectedHash) {
                problems.add(
                    "Record $index (${record.id}) record_hash mismatch: expected $expectedHash, got ${record.recordHash}"
                )
            }
            previousHash = record.recordHash
        }

        return problems
    }

    private fun computeExpectedHash(record: AuditRecord): String {
        val payload = mapOf(
            "id" to record.id,
            "requestId" to record.requestId,
            "timestamp" to record.timestamp.toString(),
            "toolName" to record.toolName,
            "inputJson" to record.inputJson,
            "dataSourcesJson" to record.dataSourcesJson,
            "durationMs" to record.durationMs,
            "outputHash" to record.outputHash,
            "status" to record.status,
            "errorType" to record.errorType,
            "errorMessage" to record.errorMessage,
            "gitCommitSha" to record.gitCommitSha,
            "packageVersion" to record.packageVersion,
            "prevRecordHash" to record.prevRecordHash
        )
        return HashUtils.sha256Truncated16(canonicalMapper.writeValueAsString(payload))
    }
}
