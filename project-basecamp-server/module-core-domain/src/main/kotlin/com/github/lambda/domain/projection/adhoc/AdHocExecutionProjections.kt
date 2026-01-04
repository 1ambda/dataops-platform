package com.github.lambda.domain.projection.adhoc

import com.github.lambda.common.enums.ExecutionStatus
import java.time.LocalDateTime

/**
 * Ad-hoc execution result projection
 * Used for service-to-controller responses for SQL execution
 */
data class AdHocExecutionResultProjection(
    val queryId: String?,
    val status: ExecutionStatus,
    val executionTimeSeconds: Double,
    val rowsReturned: Int,
    val bytesScanned: Long?,
    val costUsd: java.math.BigDecimal?,
    val rows: List<Map<String, Any?>>,
    val expiresAt: LocalDateTime?,
    val renderedSql: String,
    val downloadFormat: String?,
)
