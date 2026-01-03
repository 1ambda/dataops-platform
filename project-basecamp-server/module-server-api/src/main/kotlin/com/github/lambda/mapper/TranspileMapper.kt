package com.github.lambda.mapper

import com.github.lambda.api.dto.transpile.*
import com.github.lambda.domain.service.*
import org.springframework.stereotype.Component

/**
 * Transpile Mapper
 *
 * Handles conversions between API DTOs and Domain service results.
 * - Domain service results -> Response DTOs
 */
@Component
class TranspileMapper {
    /**
     * Convert TranspileRulesResult to TranspileRulesDto
     *
     * Used for GET /api/v1/transpile/rules
     */
    fun toTranspileRulesDto(result: TranspileRulesResult): TranspileRulesDto =
        TranspileRulesDto(
            version = result.version,
            rules =
                result.rules.map { rule ->
                    TranspileRuleDto(
                        name = rule.name,
                        fromDialect = rule.fromDialect.name.lowercase(),
                        toDialect = rule.toDialect.name.lowercase(),
                        pattern = rule.pattern,
                        replacement = rule.replacement,
                        priority = rule.priority,
                        enabled = rule.enabled,
                        description = rule.description,
                    )
                },
            metadata =
                TranspileMetadataDto(
                    createdAt = result.metadata.createdAt,
                    createdBy = result.metadata.createdBy,
                    totalRules = result.metadata.totalRules,
                    cacheTtlSeconds = result.metadata.cacheTtlSeconds,
                ),
        )

    /**
     * Convert MetricTranspileResult to TranspileResultDto
     *
     * Used for GET /api/v1/transpile/metrics/{name}
     */
    fun toTranspileResultDto(result: MetricTranspileResult): TranspileResultDto =
        TranspileResultDto(
            metricName = result.metricName,
            sourceDialect = result.sourceDialect,
            targetDialect = result.targetDialect,
            originalSql = result.originalSql,
            transpiledSql = result.transpiledSql,
            appliedRules =
                result.appliedRules.map { rule ->
                    AppliedRuleDto(
                        name = rule.name,
                        source = rule.source,
                        target = rule.target,
                    )
                },
            warnings =
                result.warnings.map { warning ->
                    TranspileWarningDto(
                        type = warning.type,
                        message = warning.message,
                        line = warning.line,
                        column = warning.column,
                    )
                },
            transpiledAt = result.transpiledAt,
            durationMs = result.durationMs,
        )

    /**
     * Convert DatasetTranspileResult to TranspileResultDto
     *
     * Used for GET /api/v1/transpile/datasets/{name}
     */
    fun toTranspileResultDto(result: DatasetTranspileResult): TranspileResultDto =
        TranspileResultDto(
            datasetName = result.datasetName,
            sourceDialect = result.sourceDialect,
            targetDialect = result.targetDialect,
            originalSql = result.originalSql,
            transpiledSql = result.transpiledSql,
            appliedRules =
                result.appliedRules.map { rule ->
                    AppliedRuleDto(
                        name = rule.name,
                        source = rule.source,
                        target = rule.target,
                    )
                },
            warnings =
                result.warnings.map { warning ->
                    TranspileWarningDto(
                        type = warning.type,
                        message = warning.message,
                        line = warning.line,
                        column = warning.column,
                    )
                },
            transpiledAt = result.transpiledAt,
            durationMs = result.durationMs,
        )
}
