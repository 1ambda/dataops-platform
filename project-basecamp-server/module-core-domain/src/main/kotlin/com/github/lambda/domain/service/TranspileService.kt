package com.github.lambda.domain.service

import com.github.lambda.common.exception.BusinessException
import com.github.lambda.common.exception.DatasetNotFoundException
import com.github.lambda.common.exception.MetricNotFoundException
import com.github.lambda.domain.external.BasecampParserClient
import com.github.lambda.domain.external.TranspileRule
import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.model.transpile.TranspileRuleEntity
import com.github.lambda.domain.repository.TranspileRuleRepositoryDsl
import com.github.lambda.domain.repository.TranspileRuleRepositoryJpa
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
     * @return TranspileRulesResult with rules and metadata
     */
    fun getTranspileRules(
        version: String? = null,
        fromDialect: SqlDialect? = null,
        toDialect: SqlDialect? = null,
    ): TranspileRulesResult {
        logger.debug("Getting transpile rules - version: {}, from: {}, to: {}", version, fromDialect, toDialect)

        val rules =
            transpileRuleRepositoryDsl.findByDialectsAndEnabled(
                fromDialect = fromDialect,
                toDialect = toDialect,
                enabled = true,
            )

        val currentVersion = generateVersion()

        return TranspileRulesResult(
            version = currentVersion,
            rules = rules,
            metadata =
                TranspileMetadata(
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
    ): MetricTranspileResult {
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
                    TranspileRule(
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
                throw TranspileException("Failed to transpile metric SQL: ${parserResult.errorMessage}")
            }

            return MetricTranspileResult(
                metricName = metricName,
                sourceDialect = detectedSourceDialect.name.lowercase(),
                targetDialect = targetDialect.name.lowercase(),
                originalSql = metric.sql,
                transpiledSql = parserResult.transpiledSql,
                appliedRules =
                    parserResult.appliedTransformations.map { transform ->
                        AppliedRule(
                            name = transform.name ?: transform.type,
                            source = transform.from ?: "",
                            target = transform.to ?: "",
                        )
                    },
                warnings =
                    parserResult.warnings.map { warning ->
                        TranspileWarning(
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
    ): DatasetTranspileResult {
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
                    TranspileRule(
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
                throw TranspileException("Failed to transpile dataset SQL: ${parserResult.errorMessage}")
            }

            return DatasetTranspileResult(
                datasetName = datasetName,
                sourceDialect = detectedSourceDialect.name.lowercase(),
                targetDialect = targetDialect.name.lowercase(),
                originalSql = dataset.sql,
                transpiledSql = parserResult.transpiledSql,
                appliedRules =
                    parserResult.appliedTransformations.map { transform ->
                        AppliedRule(
                            name = transform.name ?: transform.type,
                            source = transform.from ?: "",
                            target = transform.to ?: "",
                        )
                    },
                warnings =
                    parserResult.warnings.map { warning ->
                        TranspileWarning(
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

// === Result Classes ===

/**
 * Result class for transpile rules query
 */
data class TranspileRulesResult(
    val version: String,
    val rules: List<TranspileRuleEntity>,
    val metadata: TranspileMetadata,
)

/**
 * Metadata for transpile rules
 */
data class TranspileMetadata(
    val createdAt: Instant,
    val createdBy: String,
    val totalRules: Int,
    val cacheTtlSeconds: Int,
)

/**
 * Base transpile result interface
 */
interface TranspileResult {
    val sourceDialect: String
    val targetDialect: String
    val originalSql: String
    val transpiledSql: String
    val appliedRules: List<AppliedRule>
    val warnings: List<TranspileWarning>
    val transpiledAt: Instant
    val durationMs: Long
}

/**
 * Metric transpile result
 */
data class MetricTranspileResult(
    val metricName: String,
    override val sourceDialect: String,
    override val targetDialect: String,
    override val originalSql: String,
    override val transpiledSql: String,
    override val appliedRules: List<AppliedRule>,
    override val warnings: List<TranspileWarning>,
    override val transpiledAt: Instant,
    override val durationMs: Long,
) : TranspileResult

/**
 * Dataset transpile result
 */
data class DatasetTranspileResult(
    val datasetName: String,
    override val sourceDialect: String,
    override val targetDialect: String,
    override val originalSql: String,
    override val transpiledSql: String,
    override val appliedRules: List<AppliedRule>,
    override val warnings: List<TranspileWarning>,
    override val transpiledAt: Instant,
    override val durationMs: Long,
) : TranspileResult

/**
 * Applied rule information
 */
data class AppliedRule(
    val name: String,
    val source: String,
    val target: String,
)

/**
 * Transpile warning
 */
data class TranspileWarning(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)

/**
 * Transpile exception for SQL transpilation errors
 */
class TranspileException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
        message = message,
        errorCode = "TRANSPILE_ERROR",
        cause = cause,
    )
