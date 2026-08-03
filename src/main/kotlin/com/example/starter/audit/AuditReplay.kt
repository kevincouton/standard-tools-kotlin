package com.example.starter.audit

import com.example.starter.agent.ToolDispatcher
import org.springframework.stereotype.Service
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Service
class AuditReplay(
    private val repository: AuditRecordRepository,
    private val toolDispatcher: ToolDispatcher,
    private val objectMapper: ObjectMapper
) {

    data class ReplayResult(
        val requestId: UUID,
        val toolName: String,
        val originalOutputHash: String,
        val replayOutputHash: String,
        val outputMatch: Boolean,
        val error: String? = null
    )

    fun replay(requestId: UUID): ReplayResult {
        val record = repository.findByRequestId(requestId)
            ?: throw IllegalArgumentException("Audit record not found for requestId=$requestId")

        @Suppress("UNCHECKED_CAST")
        val input = objectMapper.readValue(record.inputJson, Map::class.java) as Map<String, Any>

        val result = try {
            toolDispatcher.dispatch(record.toolName, input)
        } catch (ex: Throwable) {
            return ReplayResult(
                requestId = requestId,
                toolName = record.toolName,
                originalOutputHash = record.outputHash,
                replayOutputHash = "",
                outputMatch = false,
                error = ex.message
            )
        }

        val replayHash = HashUtils.sha256Truncated16(canonicalJson(result))
        return ReplayResult(
            requestId = requestId,
            toolName = record.toolName,
            originalOutputHash = record.outputHash,
            replayOutputHash = replayHash,
            outputMatch = record.outputHash == replayHash
        )
    }

    private fun canonicalJson(value: Any): String {
        val mapper = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .build()
        return mapper.writeValueAsString(value)
    }
}
