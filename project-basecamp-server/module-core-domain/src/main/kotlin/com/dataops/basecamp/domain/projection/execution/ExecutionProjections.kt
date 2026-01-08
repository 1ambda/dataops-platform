package com.dataops.basecamp.domain.projection.execution

import com.dataops.basecamp.common.enums.ExecutionStatus

/**
 * Query 실행 결과 (Dataset/Raw SQL)
 */
data class QueryExecutionResult(
    val executionId: String,
    val status: ExecutionStatus,
    val rows: List<Map<String, Any?>>?,
    val rowCount: Int?,
    val schema: List<ColumnInfo>?,
    val executionTimeMs: Long,
    val transpiledSql: String?,
    val error: ExecutionError?,
)

/**
 * Quality 실행 결과
 */
data class QualityExecutionResult(
    val executionId: String,
    val status: ExecutionStatus,
    val passed: Boolean?,
    val totalTests: Int?,
    val passedTests: Int?,
    val failedTests: Int?,
    val failures: List<TestFailure>?,
    val executionTimeMs: Long,
    val error: ExecutionError?,
)

/**
 * 실행 에러 정보
 */
data class ExecutionError(
    val code: String,
    val message: String,
    val details: Map<String, Any>?,
    val cause: String?,
)

/**
 * 컬럼 정보
 */
data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean = true,
)

/**
 * 테스트 실패 정보
 */
data class TestFailure(
    val testName: String,
    val column: String?,
    val message: String,
    val rowsAffected: Int?,
)

// === CLI-Rendered Execution Projections ===

/**
 * CLI 렌더링된 실행 결과 (Dataset/Metric/SQL)
 */
data class RenderedExecutionResultProjection(
    val executionId: String,
    val status: ExecutionStatus,
    val rows: List<Map<String, Any?>>?,
    val rowCount: Int?,
    val durationSeconds: Double?,
    val renderedSql: String,
    val error: String?,
)

/**
 * CLI 렌더링된 Quality 실행 결과
 */
data class RenderedQualityExecutionResultProjection(
    val executionId: String,
    val status: ExecutionStatus,
    val results: List<QualityTestResultProjection>,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val totalDurationMs: Long,
)

/**
 * Quality 테스트 결과 Projection
 */
data class QualityTestResultProjection(
    val testName: String,
    val passed: Boolean,
    val failedCount: Int,
    val failedRows: List<Map<String, Any>>?,
    val durationMs: Long,
)
