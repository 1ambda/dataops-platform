package com.dataops.basecamp.domain.projection.transpile

import com.dataops.basecamp.domain.entity.transpile.TranspileRuleEntity
import java.time.Instant

/**
 * Result projection for transpile rules query
 */
data class TranspileRulesProjection(
    val version: String,
    val rules: List<TranspileRuleEntity>,
    val metadata: TranspileMetadataProjection,
)

/**
 * Metadata for transpile rules
 */
data class TranspileMetadataProjection(
    val createdAt: Instant,
    val createdBy: String,
    val totalRules: Int,
    val cacheTtlSeconds: Int,
)

/**
 * Base transpile result interface for projections
 */
interface TranspileProjection {
    val sourceDialect: String
    val targetDialect: String
    val originalSql: String
    val transpiledSql: String
    val appliedRules: List<AppliedRuleProjection>
    val warnings: List<TranspileWarningProjection>
    val transpiledAt: Instant
    val durationMs: Long
}

/**
 * Metric transpile result projection
 */
data class MetricTranspileProjection(
    val metricName: String,
    override val sourceDialect: String,
    override val targetDialect: String,
    override val originalSql: String,
    override val transpiledSql: String,
    override val appliedRules: List<AppliedRuleProjection>,
    override val warnings: List<TranspileWarningProjection>,
    override val transpiledAt: Instant,
    override val durationMs: Long,
) : TranspileProjection

/**
 * Dataset transpile result projection
 */
data class DatasetTranspileProjection(
    val datasetName: String,
    override val sourceDialect: String,
    override val targetDialect: String,
    override val originalSql: String,
    override val transpiledSql: String,
    override val appliedRules: List<AppliedRuleProjection>,
    override val warnings: List<TranspileWarningProjection>,
    override val transpiledAt: Instant,
    override val durationMs: Long,
) : TranspileProjection

/**
 * Applied rule information projection
 */
data class AppliedRuleProjection(
    val name: String,
    val source: String,
    val target: String,
)

/**
 * Transpile warning projection
 */
data class TranspileWarningProjection(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)
