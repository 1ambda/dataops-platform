package com.dataops.basecamp.domain.command.execution

import com.dataops.basecamp.common.enums.SqlDialect

/**
 * Dataset 실행 파라미터
 */
data class DatasetRunParams(
    val parameters: Map<String, Any> = emptyMap(),
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
    val dryRun: Boolean = false,
    val limit: Int? = null,
    val reason: String? = null,
    val userId: Long,
)

/**
 * Quality 테스트 파라미터
 */
data class QualityTestParams(
    val parameters: Map<String, Any> = emptyMap(),
    val testFilter: List<String>? = null,
    val failFast: Boolean = false,
    val reason: String? = null,
    val userId: Long,
)

/**
 * Raw SQL 실행 파라미터
 */
data class SqlExecutionParams(
    val sql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
    val dryRun: Boolean = false,
    val reason: String? = null,
    val userId: Long,
)
