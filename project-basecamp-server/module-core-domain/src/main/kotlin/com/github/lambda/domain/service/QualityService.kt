package com.github.lambda.domain.service

import com.github.lambda.common.enums.ResourceType
import com.github.lambda.common.enums.TestStatus
import com.github.lambda.common.enums.TestType
import com.github.lambda.common.enums.WorkflowRunStatus
import com.github.lambda.common.enums.WorkflowRunType
import com.github.lambda.common.exception.QualityRunNotFoundException
import com.github.lambda.common.exception.QualitySpecAlreadyExistsException
import com.github.lambda.common.exception.QualitySpecNotFoundException
import com.github.lambda.domain.entity.quality.QualityRunEntity
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import com.github.lambda.domain.entity.quality.QualityTestEntity
import com.github.lambda.domain.projection.quality.MockTestResultProjection
import com.github.lambda.domain.repository.quality.QualityRunRepositoryJpa
import com.github.lambda.domain.repository.quality.QualitySpecRepositoryDsl
import com.github.lambda.domain.repository.quality.QualitySpecRepositoryJpa
import com.github.lambda.domain.repository.quality.QualityTestRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * Quality Service
 *
 * Handles Quality Spec CRUD operations and quality test execution.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 *
 * v2.0 Enhancement:
 * - Uses WorkflowRunStatus instead of deprecated RunStatus
 * - Updated QualityRunEntity structure with workflow integration fields
 */
@Service
@Transactional(readOnly = true)
class QualityService(
    private val qualitySpecRepositoryJpa: QualitySpecRepositoryJpa,
    private val qualitySpecRepositoryDsl: QualitySpecRepositoryDsl,
    private val qualityRunRepositoryJpa: QualityRunRepositoryJpa,
    private val qualityTestRepositoryJpa: QualityTestRepositoryJpa,
    private val qualityRuleEngineService: QualityRuleEngineService,
) {
    private val log = LoggerFactory.getLogger(QualityService::class.java)

    /**
     * Get quality specifications with filters
     *
     * @param resourceType Filter by resource type (Dataset, Metric)
     * @param tag Filter by tag (exact match)
     * @param limit Maximum results (1-500)
     * @param offset Pagination offset
     * @return List of matching quality specs
     */
    fun getQualitySpecs(
        resourceType: String? = null,
        tag: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<QualitySpecEntity> {
        val resourceTypeEnum = resourceType?.let { ResourceType.valueOf(it.uppercase()) }
        val pageable = PageRequest.of(offset / limit, limit)
        return qualitySpecRepositoryDsl
            .findByFilters(
                resourceType = resourceTypeEnum,
                tag = tag,
                pageable = pageable,
            ).content
    }

    /**
     * Get quality spec by name
     *
     * @param name Quality spec name
     * @return Quality spec entity or null if not found
     */
    fun getQualitySpec(name: String): QualitySpecEntity? =
        qualitySpecRepositoryJpa.findByName(name)?.takeIf { !it.isDeleted }

    /**
     * Get quality spec by name (throws exception if not found)
     *
     * @param name Quality spec name
     * @return Quality spec entity
     * @throws QualitySpecNotFoundException if quality spec not found
     */
    fun getQualitySpecOrThrow(name: String): QualitySpecEntity =
        getQualitySpec(name) ?: throw QualitySpecNotFoundException(name)

    /**
     * Get quality tests for a spec by name
     *
     * @param specName Quality spec name
     * @return List of quality test entities
     */
    fun getQualityTests(specName: String): List<QualityTestEntity> = qualityTestRepositoryJpa.findBySpecName(specName)

    /**
     * Create a new quality spec
     *
     * @param spec Quality spec entity to create
     * @return Created quality spec entity
     * @throws QualitySpecAlreadyExistsException if spec with same name exists
     */
    @Transactional
    fun createQualitySpec(spec: QualitySpecEntity): QualitySpecEntity {
        // Check for duplicates
        if (qualitySpecRepositoryJpa.existsByName(spec.name)) {
            throw QualitySpecAlreadyExistsException(spec.name)
        }

        val savedSpec = qualitySpecRepositoryJpa.save(spec)

        return savedSpec
    }

    /**
     * Update quality spec
     *
     * @param name Quality spec name
     * @param description New description (optional)
     * @param enabled New enabled status (optional)
     * @param tests New tests list (optional)
     * @return Updated quality spec entity
     * @throws QualitySpecNotFoundException if quality spec not found
     */
    @Transactional
    fun updateQualitySpec(
        name: String,
        description: String? = null,
        enabled: Boolean? = null,
        tests: List<QualityTestEntity>? = null,
    ): QualitySpecEntity {
        val entity = getQualitySpecOrThrow(name)

        description?.let { entity.description = it }
        enabled?.let { entity.enabled = it }

        tests?.let { newTests ->
            // Delete existing tests for this spec
            qualityTestRepositoryJpa.deleteBySpecId(entity.id!!)

            // Save new tests with specId set
            newTests.forEach { test ->
                test.specId = entity.id!!
                qualityTestRepositoryJpa.save(test)
            }
        }

        return qualitySpecRepositoryJpa.save(entity)
    }

    /**
     * Delete quality spec (soft delete)
     *
     * @param name Quality spec name
     * @return true if deleted successfully
     * @throws QualitySpecNotFoundException if quality spec not found
     */
    @Transactional
    fun deleteQualitySpec(name: String): Boolean {
        val entity = getQualitySpecOrThrow(name)
        entity.deletedAt = LocalDateTime.now()
        qualitySpecRepositoryJpa.save(entity)
        return true
    }

    /**
     * Execute quality tests for a resource
     *
     * @param resourceName Fully qualified resource name (e.g., "iceberg.analytics.users")
     * @param qualitySpecName Specific quality spec to run (optional)
     * @param testNames Specific test names to run (optional)
     * @param timeout Timeout in seconds (optional, default 300)
     * @param executedBy User who executed the tests
     * @return Quality run result
     */
    @Transactional
    fun executeQualityTests(
        resourceName: String,
        qualitySpecName: String? = null,
        testNames: List<String>? = null,
        timeout: Int = 300,
        executedBy: String = "system",
    ): QualityRunEntity {
        log.info(
            "Starting quality test execution for resource: {}, spec: {}, tests: {}",
            resourceName,
            qualitySpecName,
            testNames,
        )

        // Find quality specs for the resource
        val specs =
            if (qualitySpecName != null) {
                listOf(getQualitySpecOrThrow(qualitySpecName))
            } else {
                qualitySpecRepositoryDsl.findQualitySpecsByResource(resourceName, ResourceType.DATASET)
            }

        if (specs.isEmpty()) {
            throw QualitySpecNotFoundException("No quality specs found for resource: $resourceName")
        }

        // For simplicity, run the first spec (in real implementation, might run all)
        val spec = specs.first()
        val runId = generateRunId(spec.name)

        // Create quality run entity with v2.0 structure
        val qualityRun =
            QualityRunEntity(
                runId = runId,
                qualitySpecId = spec.id!!,
                specName = spec.name,
                targetResource = resourceName,
                targetResourceType = spec.resourceType,
                status = WorkflowRunStatus.PENDING,
                runType = WorkflowRunType.MANUAL,
                triggeredBy = executedBy,
                totalTests = 0,
                passedTests = 0,
                failedTests = 0,
            )

        // Start the run
        qualityRun.start()
        val savedRun = qualityRunRepositoryJpa.save(qualityRun)

        try {
            // Get tests to execute
            val testsToRun =
                if (testNames != null) {
                    qualityTestRepositoryJpa
                        .findBySpecName(spec.name)
                        .filter { it.enabled && testNames.contains(it.name) }
                } else {
                    qualityTestRepositoryJpa.findBySpecNameAndEnabled(spec.name, true)
                }

            log.info("Executing {} tests for spec: {}", testsToRun.size, spec.name)

            var passedCount = 0
            var failedCount = 0

            // Execute each test
            testsToRun.forEach { test ->
                try {
                    val result = executeIndividualTest(resourceName, test, savedRun.id!!)

                    if (result.status == TestStatus.PASSED) {
                        passedCount++
                    } else {
                        failedCount++
                    }
                } catch (ex: Exception) {
                    log.error("Failed to execute test: {}", test.name, ex)
                    failedCount++
                }
            }

            // Complete the run with results
            savedRun.complete(passedCount, failedCount, testsToRun.size)

            log.info(
                "Quality test execution completed for resource: {}, passed: {}, failed: {}",
                resourceName,
                passedCount,
                failedCount,
            )
        } catch (ex: Exception) {
            log.error("Quality test execution failed for resource: {}", resourceName, ex)
            savedRun.fail()
        }

        return qualityRunRepositoryJpa.save(savedRun)
    }

    /**
     * Get quality runs for a spec
     *
     * @param specName Quality spec name
     * @param limit Maximum results
     * @param offset Pagination offset
     * @return List of quality runs
     */
    fun getQualityRuns(
        specName: String,
        limit: Int = 50,
        offset: Int = 0,
    ): List<QualityRunEntity> {
        val pageable = PageRequest.of(offset / limit, limit)
        return qualityRunRepositoryJpa
            .findBySpecNameOrderByStartedAtDesc(
                specName = specName,
                pageable = pageable,
            ).content
    }

    /**
     * Get quality run by run ID
     *
     * @param runId Quality run ID
     * @return Quality run entity or null if not found
     */
    fun getQualityRun(runId: String): QualityRunEntity? = qualityRunRepositoryJpa.findByRunId(runId)

    /**
     * Get quality run by run ID (throws exception if not found)
     *
     * @param runId Quality run ID
     * @return Quality run entity
     * @throws QualityRunNotFoundException if quality run not found
     */
    fun getQualityRunOrThrow(runId: String): QualityRunEntity =
        getQualityRun(runId) ?: throw QualityRunNotFoundException(runId)

    /**
     * Execute an individual quality test
     *
     * @param resourceName Resource name
     * @param test Quality test entity
     * @param runId Quality run ID
     * @return Test result entity (mock implementation for now)
     */
    private fun executeIndividualTest(
        resourceName: String,
        test: QualityTestEntity,
        runId: Long,
    ): MockTestResultProjection {
        log.debug("Executing test: {} for resource: {}", test.name, resourceName)

        try {
            // Generate SQL using QualityRuleEngineService
            val sqlResponse =
                when (test.testType) {
                    TestType.NOT_NULL ->
                        qualityRuleEngineService.generateNotNullTestSql(resourceName, test.getPrimaryColumn()!!)
                    TestType.UNIQUE ->
                        qualityRuleEngineService.generateUniqueTestSql(resourceName, test.getPrimaryColumn()!!)
                    TestType.ACCEPTED_VALUES -> {
                        val values = test.config?.get("values")?.map { it.asText() } ?: emptyList()
                        qualityRuleEngineService.generateAcceptedValuesTestSql(
                            resourceName,
                            test.getPrimaryColumn()!!,
                            values,
                        )
                    }
                    TestType.RELATIONSHIPS -> {
                        val toTable = test.config?.get("to_table")?.asText() ?: ""
                        val toColumn = test.config?.get("to_column")?.asText() ?: ""
                        qualityRuleEngineService.generateRelationshipsTestSql(
                            resourceName,
                            test.getPrimaryColumn()!!,
                            toTable,
                            toColumn,
                        )
                    }
                    TestType.EXPRESSION -> {
                        val expression = test.config?.get("expression")?.asText() ?: ""
                        qualityRuleEngineService.generateExpressionTestSql(resourceName, expression)
                    }
                    TestType.ROW_COUNT -> {
                        val minRows = test.config?.get("min")?.asLong()
                        val maxRows = test.config?.get("max")?.asLong()
                        qualityRuleEngineService.generateRowCountTestSql(resourceName, minRows, maxRows)
                    }
                    TestType.SINGULAR ->
                        qualityRuleEngineService.generateSingularTestSql(resourceName)
                }

            val generatedSql = sqlResponse.sql

            // Mock query execution (in real implementation, would use QueryEngineClient)
            val mockResult = executeMockQuery(generatedSql, test)

            log.debug(
                "Test {} executed: failed_rows={}, total_rows={}, status={}",
                test.name,
                mockResult.failedRows,
                mockResult.totalRows,
                mockResult.status,
            )

            return mockResult
        } catch (ex: Exception) {
            log.error("Failed to execute test: {}", test.name, ex)
            return MockTestResultProjection(
                status = TestStatus.FAILED,
                failedRows = 0,
                totalRows = 0,
                executionTimeSeconds = 0.0,
                errorMessage = ex.message,
            )
        }
    }

    /**
     * Mock query execution (replace with real QueryEngineClient in production)
     */
    private fun executeMockQuery(
        sql: String,
        test: QualityTestEntity,
    ): MockTestResultProjection {
        // Mock implementation - returns random results for demonstration
        val totalRows = 1000000L
        val failedRows =
            when (test.testType) {
                TestType.NOT_NULL -> 0L
                TestType.UNIQUE -> 50L
                TestType.ACCEPTED_VALUES -> 25L
                else -> 0L
            }

        val status = if (failedRows == 0L) TestStatus.PASSED else TestStatus.FAILED

        return MockTestResultProjection(
            status = status,
            failedRows = failedRows,
            totalRows = totalRows,
            executionTimeSeconds = kotlin.random.Random.nextDouble(1.0, 10.0),
            errorMessage = if (status == TestStatus.FAILED) "Test failed with $failedRows violations" else null,
        )
    }

    /**
     * Generate unique run ID
     */
    private fun generateRunId(specName: String): String {
        val timestamp = LocalDateTime.now()
        val formattedTime =
            timestamp
                .toString()
                .replace("-", "")
                .replace(":", "")
                .replace(".", "")
                .replace("T", "_")
        return "${specName}_${formattedTime}_${UUID.randomUUID().toString().take(8)}"
    }

    // ===== Quality Workflow v2.0 Methods =====

    /**
     * Execute quality workflow (v2.0)
     *
     * Enhanced version of executeQualityTests for workflow-based execution.
     * Includes Airflow integration and extended parameter support.
     */
    @Transactional
    fun executeQualityWorkflow(
        specName: String,
        parameters: Map<String, Any>? = null,
        executedBy: String,
    ): QualityRunEntity {
        log.info("Executing quality workflow: $specName by $executedBy")

        val spec = getQualitySpecOrThrow(specName)
        val runId = generateRunId(specName)

        val run =
            QualityRunEntity(
                runId = runId,
                qualitySpecId = spec.id!!,
                specName = specName,
                targetResource = spec.resourceName,
                targetResourceType = spec.resourceType,
                status = WorkflowRunStatus.PENDING,
                runType = WorkflowRunType.MANUAL,
                triggeredBy = executedBy,
                params = parameters?.toString(), // Convert to JSON string
            )

        return qualityRunRepositoryJpa.save(run)
    }

    /**
     * Get quality run by ID with exception if not found (v2.0)
     */
    fun getQualityRunByIdOrThrow(runId: String): QualityRunEntity = getQualityRunOrThrow(runId)

    /**
     * Get quality test results for a run (v2.0)
     */
    fun getQualityTestResults(runId: String): List<QualityTestEntity> {
        // TODO: Implement with QualityTestResultEntity when available
        // For now return empty list using QualityTestEntity
        return emptyList()
    }

    /**
     * Get quality run history with extended filtering (v2.0)
     */
    fun getQualityRunHistory(
        specName: String? = null,
        status: String? = null,
        executedBy: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<QualityRunEntity> {
        // Create pageable for repository query
        val pageable = PageRequest.of(offset / limit, limit)

        // Enhanced version of getQualityRuns with more filters
        return if (specName != null) {
            qualityRunRepositoryJpa
                .findBySpecNameOrderByStartedAtDesc(specName, pageable)
                .content
                .filter { run ->
                    (status == null || run.status.name == status) &&
                        (executedBy == null || run.triggeredBy == executedBy)
                }
        } else {
            qualityRunRepositoryJpa
                .findAllByOrderByStartedAtDesc(pageable)
                .content
                .filter { run ->
                    (status == null || run.status.name == status) &&
                        (executedBy == null || run.triggeredBy == executedBy)
                }
        }
    }

    /**
     * Stop quality run (v2.0)
     */
    @Transactional
    fun stopQualityRun(
        runId: String,
        stoppedBy: String,
        reason: String? = null,
    ): QualityRunEntity {
        log.info("Stopping quality run: $runId by $stoppedBy")

        val run = getQualityRunOrThrow(runId)

        require(run.canBeStopped()) {
            "Quality run $runId cannot be stopped. Current status: ${run.status}"
        }

        run.stop(stoppedBy, reason)
        return qualityRunRepositoryJpa.save(run)
    }

    /**
     * Pause quality specification (v2.0)
     */
    @Transactional
    fun pauseQualitySpec(
        specName: String,
        pausedBy: String,
        reason: String? = null,
    ): QualitySpecEntity {
        log.info("Pausing quality spec: $specName by $pausedBy")

        val spec = getQualitySpecOrThrow(specName)

        require(spec.canBePaused()) {
            "Quality spec $specName cannot be paused. Current status: ${spec.status}"
        }

        spec.pause(pausedBy, reason)
        return qualitySpecRepositoryJpa.save(spec)
    }

    /**
     * Unpause quality specification (v2.0)
     */
    @Transactional
    fun unpauseQualitySpec(
        specName: String,
        unpausedBy: String,
    ): QualitySpecEntity {
        log.info("Unpausing quality spec: $specName by $unpausedBy")

        val spec = getQualitySpecOrThrow(specName)

        require(spec.canBeUnpaused()) {
            "Quality spec $specName cannot be unpaused. Current status: ${spec.status}"
        }

        spec.unpause()
        return qualitySpecRepositoryJpa.save(spec)
    }

    /**
     * Register quality specification from YAML (v2.0)
     */
    @Transactional
    fun registerQualitySpec(
        yamlContent: String,
        registeredBy: String,
        sourceType: com.github.lambda.common.enums.WorkflowSourceType =
            com.github.lambda.common.enums.WorkflowSourceType.MANUAL,
        s3Path: String? = null,
    ): QualitySpecEntity {
        // TODO: Implement YAML parsing and QualitySpec creation
        // For now, this is a placeholder that would parse YAML and create QualitySpecEntity
        log.info("Registering quality spec from YAML by $registeredBy")

        throw NotImplementedError("YAML parsing and quality spec registration not yet implemented")
    }

    /**
     * Unregister quality specification (v2.0)
     */
    @Transactional
    fun unregisterQualitySpec(
        specName: String,
        unregisteredBy: String,
    ): Boolean {
        log.info("Unregistering quality spec: $specName by $unregisteredBy")

        val spec = getQualitySpecOrThrow(specName)

        // Check if there are any running executions
        val runningRuns =
            qualityRunRepositoryJpa
                .findBySpecName(specName)
                .filter { it.status == WorkflowRunStatus.RUNNING || it.status == WorkflowRunStatus.PENDING }

        require(runningRuns.isEmpty()) {
            "Cannot unregister quality spec $specName: ${runningRuns.size} executions are still running"
        }

        return deleteQualitySpec(specName)
    }
}
