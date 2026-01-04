package com.github.lambda.domain.entity.query

import com.github.lambda.common.enums.QueryEngine
import com.github.lambda.common.enums.QueryStatus
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Query Execution Entity for tracking query metadata
 *
 * Follows the specification in QUERY_FEATURE.md for Query Metadata API.
 * This entity represents query execution metadata for BigQuery, Trino, and other engines.
 */
@Entity
@Table(
    name = "query_executions",
    indexes = [
        Index(name = "idx_query_submitted_by", columnList = "submitted_by"),
        Index(name = "idx_query_status", columnList = "status"),
        Index(name = "idx_query_submitted_at", columnList = "submitted_at"),
        Index(name = "idx_query_is_system_query", columnList = "is_system_query"),
    ],
)
class QueryExecutionEntity(
    @Id
    @Column(name = "query_id")
    val queryId: String,
    @field:NotBlank(message = "SQL expression is required")
    @Column(name = "sql", columnDefinition = "TEXT", nullable = false)
    val sql: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: QueryStatus,
    @field:NotBlank(message = "Submitted by email is required")
    @Column(name = "submitted_by", nullable = false)
    val submittedBy: String,
    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "duration_seconds")
    var durationSeconds: Double? = null,
    @Column(name = "rows_returned")
    var rowsReturned: Long? = null,
    @Column(name = "bytes_scanned")
    var bytesScanned: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "engine", nullable = false)
    val engine: QueryEngine,
    @Column(name = "cost_usd")
    var costUsd: Double? = null,
    @Column(name = "execution_details", columnDefinition = "JSON")
    var executionDetails: String? = null,
    @Column(name = "error_details", columnDefinition = "JSON")
    var errorDetails: String? = null,
    @Column(name = "cancelled_by")
    var cancelledBy: String? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null,
    @Column(name = "is_system_query")
    val isSystemQuery: Boolean = false,
) {
    /**
     * Check if query can be cancelled
     */
    fun isCancellable(): Boolean = status in listOf(QueryStatus.PENDING, QueryStatus.RUNNING)

    /**
     * Check if query is in a terminal state
     */
    fun isTerminal(): Boolean = status in listOf(QueryStatus.COMPLETED, QueryStatus.FAILED, QueryStatus.CANCELLED)

    /**
     * Check if query is currently running
     */
    fun isRunning(): Boolean = status == QueryStatus.RUNNING

    /**
     * Get execution duration from timestamps if available
     */
    fun getCalculatedDurationSeconds(): Double? =
        if (startedAt != null && completedAt != null) {
            (completedAt!!.epochSecond - startedAt!!.epochSecond).toDouble() +
                (completedAt!!.nano - startedAt!!.nano) / 1_000_000_000.0
        } else {
            durationSeconds
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueryExecutionEntity) return false
        return queryId == other.queryId
    }

    override fun hashCode(): Int = queryId.hashCode()
}

// Enums moved to module-core-common/src/main/kotlin/com/github/lambda/common/enums/QueryEnums.kt
