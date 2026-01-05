package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.TestType
import com.dataops.basecamp.domain.external.quality.GenerateSqlRequest
import com.dataops.basecamp.domain.external.quality.GenerateSqlResponse
import com.dataops.basecamp.domain.external.quality.QualityRuleEngineClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Quality Rule Engine Service
 *
 * Orchestrates quality test SQL generation by delegating to external rule engine service.
 * Provides convenient methods for generating SQL for different quality test types.
 */
@Service
@Transactional(readOnly = true)
class QualityRuleEngineService(
    private val qualityRuleEngineClient: QualityRuleEngineClient,
) {
    private val log = LoggerFactory.getLogger(QualityRuleEngineService::class.java)

    /**
     * Generate SQL for a NOT_NULL test
     *
     * @param resourceName Fully qualified resource name (e.g., "iceberg.analytics.users")
     * @param column Column name to test
     * @return Generated SQL response
     */
    fun generateNotNullTestSql(
        resourceName: String,
        column: String,
    ): GenerateSqlResponse {
        val request =
            GenerateSqlRequest(
                testType = TestType.NOT_NULL,
                resourceName = resourceName,
                column = column,
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for a UNIQUE test
     *
     * @param resourceName Fully qualified resource name
     * @param column Column name to test
     * @return Generated SQL response
     */
    fun generateUniqueTestSql(
        resourceName: String,
        column: String,
    ): GenerateSqlResponse {
        val request =
            GenerateSqlRequest(
                testType = TestType.UNIQUE,
                resourceName = resourceName,
                column = column,
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for an ACCEPTED_VALUES test
     *
     * @param resourceName Fully qualified resource name
     * @param column Column name to test
     * @param acceptedValues List of accepted values
     * @return Generated SQL response
     */
    fun generateAcceptedValuesTestSql(
        resourceName: String,
        column: String,
        acceptedValues: List<String>,
    ): GenerateSqlResponse {
        val request =
            GenerateSqlRequest(
                testType = TestType.ACCEPTED_VALUES,
                resourceName = resourceName,
                column = column,
                config = mapOf("values" to acceptedValues),
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for a RELATIONSHIPS test
     *
     * @param resourceName Fully qualified resource name
     * @param column Source column name to test
     * @param toTable Target table name
     * @param toColumn Target column name
     * @return Generated SQL response
     */
    fun generateRelationshipsTestSql(
        resourceName: String,
        column: String,
        toTable: String,
        toColumn: String,
    ): GenerateSqlResponse {
        val request =
            GenerateSqlRequest(
                testType = TestType.RELATIONSHIPS,
                resourceName = resourceName,
                column = column,
                config =
                    mapOf(
                        "to_table" to toTable,
                        "to_column" to toColumn,
                    ),
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for an EXPRESSION test
     *
     * @param resourceName Fully qualified resource name
     * @param expression SQL expression to evaluate
     * @param description Optional test description
     * @return Generated SQL response
     */
    fun generateExpressionTestSql(
        resourceName: String,
        expression: String,
        description: String? = null,
    ): GenerateSqlResponse {
        val config =
            buildMap {
                put("expression", expression)
                description?.let { put("description", it) }
            }

        val request =
            GenerateSqlRequest(
                testType = TestType.EXPRESSION,
                resourceName = resourceName,
                config = config,
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for a ROW_COUNT test
     *
     * @param resourceName Fully qualified resource name
     * @param minRows Minimum expected row count (optional)
     * @param maxRows Maximum expected row count (optional)
     * @return Generated SQL response
     */
    fun generateRowCountTestSql(
        resourceName: String,
        minRows: Long? = null,
        maxRows: Long? = null,
    ): GenerateSqlResponse {
        val config =
            buildMap {
                minRows?.let { put("min", it) }
                maxRows?.let { put("max", it) }
            }

        val request =
            GenerateSqlRequest(
                testType = TestType.ROW_COUNT,
                resourceName = resourceName,
                config = config,
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for a SINGULAR test
     *
     * @param resourceName Fully qualified resource name
     * @return Generated SQL response
     */
    fun generateSingularTestSql(resourceName: String): GenerateSqlResponse {
        val request =
            GenerateSqlRequest(
                testType = TestType.SINGULAR,
                resourceName = resourceName,
            )

        return executeWithLogging(request)
    }

    /**
     * Generate SQL for any quality test type with custom configuration
     *
     * @param request Custom SQL generation request
     * @return Generated SQL response
     */
    fun generateCustomTestSql(request: GenerateSqlRequest): GenerateSqlResponse = executeWithLogging(request)

    /**
     * Check if the external rule engine service is available
     *
     * @return true if service is available and responding
     */
    fun isRuleEngineAvailable(): Boolean =
        try {
            qualityRuleEngineClient.isAvailable()
        } catch (ex: Exception) {
            log.warn("Failed to check rule engine availability", ex)
            false
        }

    private fun executeWithLogging(request: GenerateSqlRequest): GenerateSqlResponse {
        log.debug(
            "Generating SQL for test type: {}, resource: {}, column: {}",
            request.testType,
            request.resourceName,
            request.column,
        )

        return try {
            val response = qualityRuleEngineClient.generateSql(request)
            log.debug("Successfully generated SQL for test type: {}", request.testType)
            response
        } catch (ex: Exception) {
            log.error(
                "Failed to generate SQL for test type: {}, resource: {}, error: {}",
                request.testType,
                request.resourceName,
                ex.message,
                ex,
            )
            throw ex
        }
    }
}
