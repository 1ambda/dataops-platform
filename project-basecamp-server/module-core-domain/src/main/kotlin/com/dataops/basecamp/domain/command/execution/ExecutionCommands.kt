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

// === CLI-Rendered Execution Params ===

/**
 * CLI에서 렌더링된 Dataset SQL 실행 파라미터
 */
data class RenderedDatasetExecutionParams(
    val renderedSql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val executionTimeout: Int = 300,
    val executionLimit: Int? = null,
    val transpileSourceDialect: String? = null,
    val transpileTargetDialect: String? = null,
    val transpileUsedServerPolicy: Boolean = false,
    val resourceName: String? = null,
    val originalSpec: Map<String, Any>? = null,
    val userId: Long,
)

/**
 * CLI에서 렌더링된 Metric SQL 실행 파라미터
 */
data class RenderedMetricExecutionParams(
    val renderedSql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val executionTimeout: Int = 300,
    val executionLimit: Int? = null,
    val transpileSourceDialect: String? = null,
    val transpileTargetDialect: String? = null,
    val transpileUsedServerPolicy: Boolean = false,
    val resourceName: String? = null,
    val originalSpec: Map<String, Any>? = null,
    val userId: Long,
)

/**
 * CLI에서 렌더링된 Quality 테스트 실행 파라미터
 */
data class RenderedQualityExecutionParams(
    val resourceName: String,
    val tests: List<RenderedQualityTestItem>,
    val executionTimeout: Int = 300,
    val transpileSourceDialect: String? = null,
    val transpileTargetDialect: String? = null,
    val userId: Long,
)

/**
 * 렌더링된 Quality 테스트 항목
 */
data class RenderedQualityTestItem(
    val name: String,
    val type: String,
    val renderedSql: String,
)

/**
 * Ad-hoc SQL 실행 파라미터 (렌더링된 SQL 버전)
 */
data class RenderedSqlExecutionParams(
    val sql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val executionTimeout: Int = 300,
    val executionLimit: Int? = null,
    val targetDialect: String? = null,
    val userId: Long,
)
