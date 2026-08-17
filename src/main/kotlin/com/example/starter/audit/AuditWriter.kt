package com.example.starter.audit

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

@Service
class AuditWriter(
    private val repository: AuditRecordRepository,
    private val chainHeadRepository: AuditChainHeadRepository
) {

    private val canonicalMapper: ObjectMapper = JsonMapper.builder()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .build()

    @Transactional
    fun write(
        requestId: UUID = UUID.randomUUID(),
        toolName: String,
        input: Map<String, Any>,
        dataSources: Map<String, Any> = emptyMap(),
        durationMs: Long,
        output: Map<String, Any>,
        status: String,
        errorType: String? = null,
        errorMessage: String? = null
    ): AuditRecord = write(
        requestId = requestId,
        toolName = toolName,
        input = input,
        dataSources = dataSources,
        durationMs = durationMs,
        outputHash = hashOf(output),
        status = status,
        errorType = errorType,
        errorMessage = errorMessage
    )

    @Transactional
    fun write(
        requestId: UUID = UUID.randomUUID(),
        toolName: String,
        input: Map<String, Any>,
        dataSources: Map<String, Any> = emptyMap(),
        durationMs: Long,
        outputHash: String,
        status: String,
        errorType: String? = null,
        errorMessage: String? = null
    ): AuditRecord {
        // Lock the singleton chain-head row (SELECT ... FOR UPDATE) so concurrent
        // writers are serialized at the database level instead of racing on
        // read-then-write of the latest record.
        val head = chainHeadRepository.findByIdForUpdate()
            ?: chainHeadRepository.save(AuditChainHead())
        val prevRecordHash = head.headHash
        val record = AuditRecord(
            requestId = requestId,
            toolName = toolName,
            inputJson = canonicalJson(input),
            dataSourcesJson = dataSources.takeIf { it.isNotEmpty() }?.let { canonicalJson(it) },
            durationMs = durationMs,
            outputHash = outputHash,
            status = status,
            errorType = errorType,
            errorMessage = errorMessage,
            gitCommitSha = GitInfoProvider.sha(),
            packageVersion = PackageVersionProvider.version(),
            prevRecordHash = prevRecordHash
        )
        // Persist first so the database-assigned timestamp matches the one used
        // in the hash; PostgreSQL TIMESTAMPTZ truncates nanoseconds to microseconds,
        // which would otherwise make the verifier's recomputed hash differ.
        val saved = repository.save(record)
        saved.recordHash = computeRecordHash(saved)
        val verified = repository.save(saved)
        head.headHash = verified.recordHash
        chainHeadRepository.save(head)
        return verified
    }

    fun hashOf(data: Any): String = HashUtils.sha256Truncated16(canonicalJson(data))

    private fun computeRecordHash(record: AuditRecord): String {
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
        return HashUtils.sha256Truncated16(canonicalJson(payload))
    }

    private fun canonicalJson(value: Any): String = canonicalMapper.writeValueAsString(value)

    companion object {
        const val GENESIS_HASH = "0000000000000000"
    }
}
