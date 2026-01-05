package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.DatasetNotFoundException
import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.common.exception.TranspileException
import com.dataops.basecamp.domain.external.parser.BasecampParserClient
import com.dataops.basecamp.domain.external.parser.TranspileRuleRequest
import com.dataops.basecamp.domain.projection.transpile.*
import com.dataops.basecamp.domain.repository.transpile.TranspileRuleRepositoryDsl
import com.dataops.basecamp.domain.repository.transpile.TranspileRuleRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/**
 * Transpile Service
 *
 * Handles SQL transpilation operations between different dialects.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class TranspileService(
    private val transpileRuleRepositoryJpa: TranspileRuleRepositoryJpa,
    private val transpileRuleRepositoryDsl: TranspileRuleRepositoryDsl,
    private val metricService: MetricService,
    private val datasetService: DatasetService,
    private val basecampParserClient: BasecampParserClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get all transpile rules with optional filtering
     *
     * @param version Version identifier (not used in mock implementation)
     * @param fromDialect Filter by source dialect
     * @param toDialect Filter by target dialect
     * @return TranspileRulesProjection with rules and metadata
     */
    fun getTranspileRules(
        version: String? = null,
        fromDialect: SqlDialect? = null,
        toDialect: SqlDialect? = null,
    ): TranspileRulesProjection {
        logger.debug("Getting transpile rules - version: {}, from: {}, to: {}", version, fromDialect, toDialect)

        val rules =
            transpileRuleRepositoryDsl.findByDialectsAndEnabled(
                fromDialect = fromDialect,
                toDialect = toDialect,
                enabled = true,
            )

        val currentVersion = generateVersion()

        return TranspileRulesProjection(
            version = currentVersion,
            rules = rules,
            metadata =
                TranspileMetadataProjection(
                    createdAt = Instant.now(),
                    createdBy = "system",
                    totalRules = rules.size,
                    cacheTtlSeconds = 3600,
                ),
        )
    }

    /**
     * Transpile metric SQL to target dialect
     *
     * @param metricName Fully qualified metric name
     * @param targetDialect Target SQL dialect
     * @param sourceDialect Source dialect (auto-detected if null)
     * @param parameters Parameter values for substitution (not implemented in mock)
     * @return TranspileResult with converted SQL
     * @throws MetricNotFoundException if metric not found
     * @throws TranspileException if transpilation fails
     */
    fun transpileMetric(
        metricName: String,
        targetDialect: SqlDialect,
        sourceDialect: SqlDialect? = null,
        parameters: Map<String, Any> = emptyMap(),
    ): MetricTranspileProjection {
        logger.debug("Transpiling metric: {} from {} to {}", metricName, sourceDialect, targetDialect)

        val startTime = System.currentTimeMillis()

        try {
            // Get metric
            val metric =
                metricService.getMetric(metricName)
                    ?: throw MetricNotFoundException(metricName)

            // Detect source dialect if not provided
            val detectedSourceDialect = sourceDialect ?: detectDialect(metric.sql)

            // Get applicable rules
            val rules =
                transpileRuleRepositoryDsl.findApplicableRules(
                    fromDialect = detectedSourceDialect,
                    toDialect = targetDialect,
                    orderByPriority = true,
                )

            // Convert domain rules to parser rules
            val parserRules =
                rules.map { rule ->
                    TranspileRuleRequest(
                        name = rule.name,
                        pattern = rule.pattern,
                        replacement = rule.replacement,
                    )
                }

            // Call parser service
            val parserResult =
                basecampParserClient.transpileSQL(
                    sql = metric.sql,
                    sourceDialect = detectedSourceDialect.name.lowercase(),
                    targetDialect = targetDialect.name.lowercase(),
                    rules = parserRules,
                )

            val duration = System.currentTimeMillis() - startTime

            if (!parserResult.success) {
                throw TranspileException(parserResult.errorMessage ?: "Unknown transpilation error")
            }

            return MetricTranspileProjection(
                metricName = metricName,
                sourceDialect = detectedSourceDialect.name.lowercase(),
                targetDialect = targetDialect.name.lowercase(),
                originalSql = metric.sql,
                transpiledSql = parserResult.transpiledSql,
                appliedRules =
                    parserResult.appliedTransformations.map { transform ->
                        AppliedRuleProjection(
                            name = transform.name ?: transform.type,
                            source = transform.from ?: "",
                            target = transform.to ?: "",
                        )
                    },
                warnings =
                    parserResult.warnings.map { warning ->
                        TranspileWarningProjection(
                            type = warning.type,
                            message = warning.message,
                            line = warning.line,
                            column = warning.column,
                        )
                    },
                transpiledAt = Instant.now(),
                durationMs = duration,
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Failed to transpile metric {}: {}", metricName, e.message, e)
            throw TranspileException("Failed to transpile metric $metricName: ${e.message}", e)
        }
    }

    /**
     * Transpile dataset SQL to target dialect
     *
     * @param datasetName Fully qualified dataset name
     * @param targetDialect Target SQL dialect
     * @param sourceDialect Source dialect (auto-detected if null)
     * @param parameters Parameter values for substitution (not implemented in mock)
     * @return TranspileResult with converted SQL
     * @throws DatasetNotFoundException if dataset not found
     * @throws TranspileException if transpilation fails
     */
    fun transpileDataset(
        datasetName: String,
        targetDialect: SqlDialect,
        sourceDialect: SqlDialect? = null,
        parameters: Map<String, Any> = emptyMap(),
    ): DatasetTranspileProjection {
        logger.debug("Transpiling dataset: {} from {} to {}", datasetName, sourceDialect, targetDialect)

        val startTime = System.currentTimeMillis()

        try {
            // Get dataset
            val dataset =
                datasetService.getDataset(datasetName)
                    ?: throw DatasetNotFoundException(datasetName)

            // Detect source dialect if not provided
            val detectedSourceDialect = sourceDialect ?: detectDialect(dataset.sql)

            // Get applicable rules
            val rules =
                transpileRuleRepositoryDsl.findApplicableRules(
                    fromDialect = detectedSourceDialect,
                    toDialect = targetDialect,
                    orderByPriority = true,
                )

            // Convert domain rules to parser rules
            val parserRules =
                rules.map { rule ->
                    TranspileRuleRequest(
                        name = rule.name,
                        pattern = rule.pattern,
                        replacement = rule.replacement,
                    )
                }

            // Call parser service
            val parserResult =
                basecampParserClient.transpileSQL(
                    sql = dataset.sql,
                    sourceDialect = detectedSourceDialect.name.lowercase(),
                    targetDialect = targetDialect.name.lowercase(),
                    rules = parserRules,
                )

            val duration = System.currentTimeMillis() - startTime

            if (!parserResult.success) {
                throw TranspileException(parserResult.errorMessage ?: "Unknown transpilation error")
            }

            return DatasetTranspileProjection(
                datasetName = datasetName,
                sourceDialect = detectedSourceDialect.name.lowercase(),
                targetDialect = targetDialect.name.lowercase(),
                originalSql = dataset.sql,
                transpiledSql = parserResult.transpiledSql,
                appliedRules =
                    parserResult.appliedTransformations.map { transform ->
                        AppliedRuleProjection(
                            name = transform.name ?: transform.type,
                            source = transform.from ?: "",
                            target = transform.to ?: "",
                        )
                    },
                warnings =
                    parserResult.warnings.map { warning ->
                        TranspileWarningProjection(
                            type = warning.type,
                            message = warning.message,
                            line = warning.line,
                            column = warning.column,
                        )
                    },
                transpiledAt = Instant.now(),
                durationMs = duration,
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Failed to transpile dataset {}: {}", datasetName, e.message, e)
            throw TranspileException("Failed to transpile dataset $datasetName: ${e.message}", e)
        }
    }

    /**
     * Detect SQL dialect from SQL content using simple heuristics
     */
    private fun detectDialect(sql: String): SqlDialect =
        when {
            // BigQuery patterns
            sql.contains("`") && (sql.contains("DATE_SUB") || sql.contains("SAFE_DIVIDE")) -> SqlDialect.BIGQUERY
            sql.contains("STRUCT(") || sql.contains("ARRAY_AGG(") && sql.contains("IGNORE NULLS") -> SqlDialect.BIGQUERY

            // Trino patterns
            sql.contains("\"") && (sql.contains("date_add") || sql.contains("TRY_CAST")) -> SqlDialect.TRINO
            sql.contains("FILTER (WHERE") -> SqlDialect.TRINO

            // Default to TRINO for unknown patterns
            else -> SqlDialect.TRINO
        }

    /**
     * Generate version string for caching
     */
    private fun generateVersion(): String {
        val now = Instant.now()
        val date = now.atZone(ZoneId.of("UTC")).toLocalDate()
        val sequence = (now.toEpochMilli() % 1000).toString().padStart(3, '0')
        return "$date-$sequence"
    }
}
