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
