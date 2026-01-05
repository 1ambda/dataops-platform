package com.dataops.basecamp.dto.query

import com.dataops.basecamp.common.enums.QueryEngine
import com.dataops.basecamp.common.enums.QueryStatus
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Query List Item DTO for query listing API
 */
@Schema(description = "Query execution summary for list view")
data class QueryListItemDto(
    @Schema(description = "Unique query identifier", example = "query_20260101_100000_abc123")
    @JsonProperty("query_id")
    val queryId: String,
    @Schema(description = "SQL query text", example = "SELECT user_id, COUNT(*) FROM users GROUP BY 1")
    val sql: String,
    @Schema(description = "Current execution status")
    val status: QueryStatus,
    @Schema(description = "User who submitted the query", example = "analyst@example.com")
    @JsonProperty("submitted_by")
    val submittedBy: String,
    @Schema(description = "Submission timestamp")
    @JsonProperty("submitted_at")
    val submittedAt: Instant,
    @Schema(description = "Query start timestamp")
    @JsonProperty("started_at")
    val startedAt: Instant?,
    @Schema(description = "Query completion timestamp")
    @JsonProperty("completed_at")
    val completedAt: Instant?,
    @Schema(description = "Execution duration in seconds", example = "10.5")
    @JsonProperty("duration_seconds")
    val durationSeconds: Double?,
    @Schema(description = "Number of rows returned", example = "1500000")
    @JsonProperty("rows_returned")
    val rowsReturned: Long?,
    @Schema(description = "Amount of data scanned", example = "1.2 GB")
    @JsonProperty("bytes_scanned")
    val bytesScanned: String?,
    @Schema(description = "Query engine used")
    val engine: QueryEngine,
    @Schema(description = "Query execution cost in USD", example = "0.006")
    @JsonProperty("cost_usd")
    val costUsd: Double?,
)

/**
 * Query Detail DTO for detailed query information
 */
@Schema(description = "Detailed query execution information")
data class QueryDetailDto(
    @Schema(description = "Unique query identifier", example = "query_20260101_100000_abc123")
    @JsonProperty("query_id")
    val queryId: String,
    @Schema(description = "SQL query text")
    val sql: String,
    @Schema(description = "Current execution status")
    val status: QueryStatus,
    @Schema(description = "User who submitted the query", example = "analyst@example.com")
    @JsonProperty("submitted_by")
    val submittedBy: String,
    @Schema(description = "Submission timestamp")
    @JsonProperty("submitted_at")
    val submittedAt: Instant,
    @Schema(description = "Query start timestamp")
    @JsonProperty("started_at")
    val startedAt: Instant?,
    @Schema(description = "Query completion timestamp")
    @JsonProperty("completed_at")
    val completedAt: Instant?,
    @Schema(description = "Execution duration in seconds", example = "10.5")
    @JsonProperty("duration_seconds")
    val durationSeconds: Double?,
    @Schema(description = "Number of rows returned", example = "1500000")
    @JsonProperty("rows_returned")
    val rowsReturned: Long?,
    @Schema(description = "Amount of data scanned", example = "1.2 GB")
    @JsonProperty("bytes_scanned")
    val bytesScanned: String?,
    @Schema(description = "Query engine used")
    val engine: QueryEngine,
    @Schema(description = "Query execution cost in USD", example = "0.006")
    @JsonProperty("cost_usd")
    val costUsd: Double?,
    @Schema(description = "Detailed execution information")
    @JsonProperty("execution_details")
    val executionDetails: ExecutionDetailsDto?,
    @Schema(description = "Error information if query failed")
    val error: QueryErrorDto?,
)

/**
 * Execution details DTO
 */
@Schema(description = "Query execution plan and metadata")
data class ExecutionDetailsDto(
    @Schema(description = "Engine-specific job ID", example = "bqjob_r1234567890_000001_project")
    @JsonProperty("job_id")
    val jobId: String?,
    @Schema(description = "Query execution plan stages")
    @JsonProperty("query_plan")
    val queryPlan: List<QueryPlanStageDto>?,
    @Schema(description = "Tables accessed during execution")
    @JsonProperty("tables_accessed")
    val tablesAccessed: List<String>?,
)

/**
 * Query plan stage DTO
 */
@Schema(description = "Single stage in query execution plan")
data class QueryPlanStageDto(
    @Schema(description = "Stage name", example = "Stage 1")
    val stage: String,
    @Schema(description = "Operation type", example = "Scan")
    val operation: String,
    @Schema(description = "Number of input rows", example = "1500000")
    @JsonProperty("input_rows")
    val inputRows: Long?,
    @Schema(description = "Number of output rows", example = "450000")
    @JsonProperty("output_rows")
    val outputRows: Long?,
)

/**
 * Query error DTO
 */
@Schema(description = "Query execution error information")
data class QueryErrorDto(
    @Schema(description = "Error code", example = "TABLE_NOT_FOUND")
    val code: String,
    @Schema(description = "Error message")
    val message: String,
    @Schema(description = "Additional error details")
    val details: Map<String, Any>?,
)

/**
 * Cancel query response DTO
 */
@Schema(description = "Query cancellation response")
data class CancelQueryResponseDto(
    @Schema(description = "Query identifier", example = "query_20260101_100000_abc123")
    @JsonProperty("query_id")
    val queryId: String,
    @Schema(description = "Updated status after cancellation")
    val status: QueryStatus,
    @Schema(description = "User who cancelled the query", example = "analyst@example.com")
    @JsonProperty("cancelled_by")
    val cancelledBy: String,
    @Schema(description = "Cancellation timestamp")
    @JsonProperty("cancelled_at")
    val cancelledAt: Instant,
    @Schema(description = "Cancellation reason")
    val reason: String?,
)

/**
 * Cancel query request DTO
 */
@Schema(description = "Query cancellation request")
data class CancelQueryRequestDto(
    @Schema(description = "Cancellation reason", example = "User requested cancellation")
    val reason: String?,
)
