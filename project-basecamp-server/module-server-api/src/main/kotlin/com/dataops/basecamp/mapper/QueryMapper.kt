package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.entity.query.QueryExecutionEntity
import com.dataops.basecamp.dto.query.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * Query Mapper
 *
 * Converts between API DTOs and Domain Entities for query execution metadata.
 * - Domain Entity → Response DTO
 * - JSON string parsing for execution details and error details
 */
@Component
object QueryMapper {
    private val objectMapper = ObjectMapper()

    // === Domain Entity → Response DTO Conversions ===

    /**
     * Convert QueryExecution entity to list item DTO for query listing
     */
    fun toListItemDto(entity: QueryExecutionEntity): QueryListItemDto =
        QueryListItemDto(
            queryId = entity.queryId,
            sql = entity.sql,
            status = entity.status,
            submittedBy = entity.submittedBy,
            submittedAt = entity.submittedAt,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            durationSeconds = entity.getCalculatedDurationSeconds(),
            rowsReturned = entity.rowsReturned,
            bytesScanned = entity.bytesScanned,
            engine = entity.engine,
            costUsd = entity.costUsd,
        )

    /**
     * Convert QueryExecution entity to detailed DTO for query details
     */
    fun toDetailDto(entity: QueryExecutionEntity): QueryDetailDto =
        QueryDetailDto(
            queryId = entity.queryId,
            sql = entity.sql,
            status = entity.status,
            submittedBy = entity.submittedBy,
            submittedAt = entity.submittedAt,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            durationSeconds = entity.getCalculatedDurationSeconds(),
            rowsReturned = entity.rowsReturned,
            bytesScanned = entity.bytesScanned,
            engine = entity.engine,
            costUsd = entity.costUsd,
            executionDetails = parseExecutionDetails(entity.executionDetails),
            error = parseErrorDetails(entity.errorDetails),
        )

    /**
     * Convert QueryExecution entity to cancellation response DTO
     */
    fun toCancelQueryResponseDto(entity: QueryExecutionEntity): CancelQueryResponseDto =
        CancelQueryResponseDto(
            queryId = entity.queryId,
            status = entity.status,
            cancelledBy =
                entity.cancelledBy
                    ?: throw IllegalStateException("Cancelled query must have cancelledBy field"),
            cancelledAt =
                entity.cancelledAt
                    ?: throw IllegalStateException("Cancelled query must have cancelledAt field"),
            reason = entity.cancellationReason,
        )

    /**
     * Convert list of QueryExecution entities to list of DTOs
     */
    fun toListItemDtoList(entities: List<QueryExecutionEntity>): List<QueryListItemDto> =
        entities.map { toListItemDto(it) }

    // === JSON Parsing Helpers ===

    /**
     * Parse execution details JSON string to DTO
     */
    private fun parseExecutionDetails(jsonString: String?): ExecutionDetailsDto? {
        if (jsonString.isNullOrBlank()) return null

        return try {
            val json = objectMapper.readValue(jsonString, Map::class.java)
            ExecutionDetailsDto(
                jobId = json["job_id"] as? String,
                queryPlan = parseQueryPlan(json["query_plan"]),
                tablesAccessed = parseTablesAccessed(json["tables_accessed"]),
            )
        } catch (e: Exception) {
            // Log error and return null for malformed JSON
            null
        }
    }

    /**
     * Parse query plan from JSON object
     */
    private fun parseQueryPlan(queryPlanObj: Any?): List<QueryPlanStageDto>? {
        if (queryPlanObj !is List<*>) return null

        return try {
            queryPlanObj.filterIsInstance<Map<String, Any>>().map { stage ->
                QueryPlanStageDto(
                    stage = stage["stage"] as? String ?: "Unknown",
                    operation = stage["operation"] as? String ?: "Unknown",
                    inputRows = (stage["input_rows"] as? Number)?.toLong(),
                    outputRows = (stage["output_rows"] as? Number)?.toLong(),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse tables accessed from JSON object
     */
    private fun parseTablesAccessed(tablesObj: Any?): List<String>? {
        if (tablesObj !is List<*>) return null

        return try {
            tablesObj.filterIsInstance<String>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse error details JSON string to DTO
     */
    private fun parseErrorDetails(jsonString: String?): QueryErrorDto? {
        if (jsonString.isNullOrBlank()) return null

        return try {
            val typeRef = object : TypeReference<Map<String, Any>>() {}
            val json = objectMapper.readValue(jsonString, typeRef)
            QueryErrorDto(
                code = json["code"] as? String ?: "UNKNOWN_ERROR",
                message = json["message"] as? String ?: "Unknown error occurred",
                details = json["details"] as? Map<String, Any>,
            )
        } catch (e: Exception) {
            // Log error and return null for malformed JSON
            null
        }
    }

    // === Error Response Helpers ===

    /**
     * Create query not found error response
     */
    fun toQueryNotFoundError(queryId: String): Map<String, Any> =
        mapOf(
            "error" to
                mapOf(
                    "code" to "QUERY_NOT_FOUND",
                    "message" to "Query $queryId does not exist",
                    "details" to mapOf("query_id" to queryId),
                ),
        )

    /**
     * Create query not cancellable error response
     */
    fun toQueryNotCancellableError(
        queryId: String,
        currentStatus: String,
    ): Map<String, Any> =
        mapOf(
            "error" to
                mapOf(
                    "code" to "QUERY_NOT_CANCELLABLE",
                    "message" to "Query $queryId is already $currentStatus",
                    "details" to
                        mapOf(
                            "query_id" to queryId,
                            "current_status" to currentStatus,
                        ),
                ),
        )

    /**
     * Create access denied error response
     */
    fun toAccessDeniedError(message: String): Map<String, Any> =
        mapOf(
            "error" to
                mapOf(
                    "code" to "FORBIDDEN",
                    "message" to message,
                ),
        )

    /**
     * Create invalid date range error response
     */
    fun toInvalidDateRangeError(reason: String): Map<String, Any> =
        mapOf(
            "error" to
                mapOf(
                    "code" to "INVALID_DATE_RANGE",
                    "message" to "Invalid date format or range: $reason",
                ),
        )
}
