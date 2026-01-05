package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.WorkflowRunStatus
import com.dataops.basecamp.common.enums.WorkflowRunType
import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.common.exception.WorkflowAlreadyExistsException
import com.dataops.basecamp.common.exception.WorkflowNotFoundException
import com.dataops.basecamp.common.exception.WorkflowRunNotFoundException
import com.dataops.basecamp.domain.entity.workflow.AirflowClusterEntity
import com.dataops.basecamp.domain.entity.workflow.WorkflowEntity
import com.dataops.basecamp.domain.entity.workflow.WorkflowRunEntity
import com.dataops.basecamp.domain.external.airflow.AirflowClient
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunState
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunStatusResponse
import com.dataops.basecamp.domain.external.storage.WorkflowStorage
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import com.dataops.basecamp.domain.repository.airflow.AirflowClusterRepositoryJpa
import com.dataops.basecamp.domain.repository.workflow.WorkflowRepositoryDsl
import com.dataops.basecamp.domain.repository.workflow.WorkflowRepositoryJpa
import com.dataops.basecamp.domain.repository.workflow.WorkflowRunRepositoryDsl
import com.dataops.basecamp.domain.repository.workflow.WorkflowRunRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Workflow Service
 *
 * Handles workflow orchestration via Airflow integration.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class WorkflowService(
    private val workflowRepositoryJpa: WorkflowRepositoryJpa,
    private val workflowRepositoryDsl: WorkflowRepositoryDsl,
    private val workflowRunRepositoryJpa: WorkflowRunRepositoryJpa,
    private val workflowRunRepositoryDsl: WorkflowRunRepositoryDsl,
    private val airflowClient: AirflowClient,
    private val workflowStorage: WorkflowStorage,
    private val clusterRepository: AirflowClusterRepositoryJpa? = null,
) {
    private val log = LoggerFactory.getLogger(WorkflowService::class.java)

    /**
     * Get workflows with filters
     *
     * @param status Filter by workflow status (ACTIVE, PAUSED, DISABLED)
     * @param sourceType Filter by source type (MANUAL, CODE)
     * @param owner Filter by owner email
     * @param limit Maximum results (1-500)
     * @param offset Pagination offset
     * @return List of matching workflows
     */
    fun getWorkflows(
        status: String? = null,
        sourceType: String? = null,
        owner: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<WorkflowEntity> {
        val statusEnum = status?.let { WorkflowStatus.valueOf(it.uppercase()) }
        val sourceTypeEnum = sourceType?.let { WorkflowSourceType.valueOf(it.uppercase()) }
        val pageable = PageRequest.of(offset / limit, limit)

        return workflowRepositoryDsl
            .findByFilters(
                status = statusEnum,
                sourceType = sourceTypeEnum,
                owner = owner,
                pageable = pageable,
            ).content
    }

    /**
     * Get workflow by dataset name
     *
     * @param datasetName Workflow dataset name
     * @return Workflow entity or null if not found
     */
    fun getWorkflow(datasetName: String): WorkflowEntity? =
        workflowRepositoryJpa.findByDatasetName(datasetName)?.takeIf { !it.isDeleted }

    /**
     * Get workflow by dataset name (throws exception if not found)
     *
     * @param datasetName Workflow dataset name
     * @return Workflow entity
     * @throws WorkflowNotFoundException if workflow not found
     */
    fun getWorkflowOrThrow(datasetName: String): WorkflowEntity =
        getWorkflow(datasetName) ?: throw WorkflowNotFoundException(datasetName)

    /**
     * Get workflow run by run ID
     *
     * @param runId Workflow run ID
     * @return Workflow run entity or null if not found
     */
    fun getWorkflowRun(runId: String): WorkflowRunEntity? = workflowRunRepositoryJpa.findByRunId(runId)

    /**
     * Get workflow run by run ID with Airflow status sync
     *
     * When the run has been synced from Airflow and is not stale (within 1 hour),
     * the synced data is used directly without making additional Airflow API calls.
     *
     * @param runId Workflow run ID
     * @return Workflow run entity with updated status
     * @throws WorkflowRunNotFoundException if workflow run not found
     */
    fun getWorkflowRunWithSync(runId: String): WorkflowRunEntity {
        val workflowRun = getWorkflowRunOrThrow(runId)

        // Phase 6: Prefer synced data when available and not stale
        if (workflowRun.isSyncedFromAirflow() && !workflowRun.isSyncStale(SYNC_STALE_THRESHOLD_MINUTES)) {
            log.debug("Using synced data for run {}, last synced at {}", runId, workflowRun.lastSyncedAt)
            return workflowRun
        }

        // Sync with Airflow status if run is not finished
        if (!workflowRun.isFinished()) {
            try {
                val workflow = getWorkflowOrThrow(workflowRun.datasetName)
                val airflowStatus = airflowClient.getDAGRun(workflow.airflowDagId, runId)

                // Update status based on Airflow response
                val newStatus = mapAirflowStatusToWorkflowStatus(airflowStatus)
                if (newStatus != workflowRun.status) {
                    workflowRun.status = newStatus

                    // Update end time if finished
                    if (workflowRun.isFinished() && workflowRun.endedAt == null) {
                        workflowRun.endedAt = LocalDateTime.now()
                    }

                    workflowRunRepositoryJpa.save(workflowRun)
                    log.info("Synchronized workflow run {} status from {} to {}", runId, workflowRun.status, newStatus)
                }
            } catch (ex: Exception) {
                log.warn("Failed to sync workflow run {} with Airflow: {}", runId, ex.message)
            }
        }

        return workflowRun
    }

    /**
     * Get workflow run by run ID (throws exception if not found)
     *
     * @param runId Workflow run ID
     * @return Workflow run entity
     * @throws WorkflowRunNotFoundException if workflow run not found
     */
    fun getWorkflowRunOrThrow(runId: String): WorkflowRunEntity =
        getWorkflowRun(runId) ?: throw WorkflowRunNotFoundException(runId)

    /**
     * Get workflow history with filters
     *
     * @param datasetName Filter by dataset name
     * @param startDate Start date filter (YYYY-MM-DD)
     * @param endDate End date filter (YYYY-MM-DD)
     * @param limit Maximum results
     * @return List of workflow runs
     */
    fun getWorkflowHistory(
        datasetName: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int = 20,
    ): List<WorkflowRunEntity> {
        val startDateTime = startDate?.let { LocalDate.parse(it).atStartOfDay() }
        val endDateTime = endDate?.let { LocalDate.parse(it).plusDays(1).atStartOfDay() }

        val pageable = PageRequest.of(0, limit)
        return workflowRunRepositoryDsl
            .findRunsByFilters(
                datasetName = datasetName,
                startDate = startDateTime,
                endDate = endDateTime,
                pageable = pageable,
            ).content
    }

    /**
     * Register a new workflow
     *
     * @param datasetName Dataset name
     * @param sourceType Source type (MANUAL, CODE)
     * @param schedule Schedule information
     * @param owner Owner email
     * @param team Team name (optional)
     * @param description Workflow description (optional)
     * @param yamlContent Workflow YAML content
     * @return Created workflow entity
     * @throws WorkflowAlreadyExistsException if workflow with same name exists
     */
    @Transactional
    fun registerWorkflow(
        datasetName: String,
        sourceType: WorkflowSourceType,
        schedule: ScheduleInfo,
        owner: String,
        team: String? = null,
        description: String? = null,
        yamlContent: String,
    ): WorkflowEntity {
        log.info("Registering workflow: {}", datasetName)

        // Check for duplicates
        if (workflowRepositoryJpa.existsByDatasetName(datasetName)) {
            throw WorkflowAlreadyExistsException(datasetName)
        }

        // Validate cron expression
        schedule.cron?.let { validateCronExpression(it) }

        try {
            // Save YAML to storage
            val s3Path = workflowStorage.saveWorkflowYaml(datasetName, sourceType, yamlContent)
            log.debug("Saved workflow YAML to: {}", s3Path)

            // Generate Airflow DAG ID
            val airflowDagId = generateDagId(datasetName)

            // Create DAG in Airflow
            val createdDagId = airflowClient.createDAG(datasetName, schedule, s3Path)
            log.debug("Created Airflow DAG: {}", createdDagId)

            // Create workflow entity
            val entity =
                WorkflowEntity(
                    datasetName = datasetName,
                    sourceType = sourceType,
                    status = WorkflowStatus.ACTIVE,
                    owner = owner,
                    team = team,
                    description = description,
                    s3Path = s3Path,
                    airflowDagId = airflowDagId,
                    schedule = schedule,
                )

            val savedWorkflow = workflowRepositoryJpa.save(entity)
            log.info("Successfully registered workflow: {}", datasetName)

            return savedWorkflow
        } catch (ex: Exception) {
            log.error("Failed to register workflow: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Trigger workflow run
     *
     * @param datasetName Dataset name
     * @param params Execution parameters
     * @param dryRun Dry run flag
     * @param triggeredBy User who triggered the run
     * @return Created workflow run entity
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional
    fun triggerWorkflowRun(
        datasetName: String,
        params: Map<String, Any> = emptyMap(),
        dryRun: Boolean = false,
        triggeredBy: String = "system",
    ): WorkflowRunEntity {
        log.info("Triggering workflow run for dataset: {}, dryRun: {}", datasetName, dryRun)

        val workflow = getWorkflowOrThrow(datasetName)

        // Check if workflow can run
        if (!workflow.canRun()) {
            throw IllegalStateException("Cannot run workflow. Current status: ${workflow.status}")
        }

        // Generate run ID
        val runId = generateRunId(datasetName, WorkflowRunType.MANUAL)

        // Resolve cluster for team (Phase 6)
        val cluster = workflow.team?.let { resolveClusterForTeam(it) }

        try {
            // Trigger DAG in Airflow
            val airflowConf =
                params.toMutableMap().apply {
                    put("dry_run", dryRun)
                    put("dataset_name", datasetName)
                    put("triggered_by", triggeredBy)
                }

            airflowClient.triggerDAGRun(workflow.airflowDagId, runId, airflowConf)
            log.debug("Triggered Airflow DAG run: {}", runId)

            // Create workflow run entity with cluster ID
            val workflowRun =
                WorkflowRunEntity(
                    runId = runId,
                    datasetName = datasetName,
                    status = WorkflowRunStatus.PENDING,
                    triggeredBy = triggeredBy,
                    runType = WorkflowRunType.MANUAL,
                    startedAt = LocalDateTime.now(),
                    params = if (params.isNotEmpty()) serializeParams(params) else null,
                    workflowId = workflow.datasetName,
                    airflowClusterId = cluster?.id,
                    airflowDagRunId = runId,
                )

            val savedRun = workflowRunRepositoryJpa.save(workflowRun)
            log.info("Successfully triggered workflow run: {} on cluster: {}", runId, cluster?.team ?: "default")

            return savedRun
        } catch (ex: Exception) {
            log.error("Failed to trigger workflow run for dataset: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Trigger backfill
     *
     * @param datasetName Dataset name
     * @param startDate Start date (YYYY-MM-DD)
     * @param endDate End date (YYYY-MM-DD)
     * @param params Additional parameters
     * @param parallel Run dates in parallel
     * @param triggeredBy User who triggered the backfill
     * @return List of created workflow run entities
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional
    fun triggerBackfill(
        datasetName: String,
        startDate: String,
        endDate: String,
        params: Map<String, Any> = emptyMap(),
        parallel: Boolean = false,
        triggeredBy: String = "system",
    ): List<WorkflowRunEntity> {
        log.info(
            "Triggering backfill for dataset: {} from {} to {}, parallel: {}",
            datasetName,
            startDate,
            endDate,
            parallel,
        )

        val workflow = getWorkflowOrThrow(datasetName)

        // Check if workflow can run
        if (!workflow.canRun()) {
            throw IllegalStateException("Cannot run workflow. Current status: ${workflow.status}")
        }

        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val dates = generateDateRange(start, end)

        log.info("Generated {} dates for backfill: {}", dates.size, dates)

        // Resolve cluster for team (Phase 6)
        val cluster = workflow.team?.let { resolveClusterForTeam(it) }

        val runs = mutableListOf<WorkflowRunEntity>()

        try {
            dates.forEach { date ->
                val runId = generateRunId(datasetName, WorkflowRunType.BACKFILL, date)
                val dateParams =
                    params.toMutableMap().apply {
                        put("date", date.toString())
                        put("dataset_name", datasetName)
                        put("triggered_by", triggeredBy)
                        put("backfill", true)
                    }

                // Trigger DAG in Airflow
                airflowClient.triggerDAGRun(workflow.airflowDagId, runId, dateParams)
                log.debug("Triggered Airflow DAG run for date {}: {}", date, runId)

                // Create workflow run entity with cluster ID
                val workflowRun =
                    WorkflowRunEntity(
                        runId = runId,
                        datasetName = datasetName,
                        status = WorkflowRunStatus.PENDING,
                        triggeredBy = triggeredBy,
                        runType = WorkflowRunType.BACKFILL,
                        startedAt = LocalDateTime.now(),
                        params = serializeParams(dateParams),
                        workflowId = workflow.datasetName,
                        airflowClusterId = cluster?.id,
                        airflowDagRunId = runId,
                    )

                runs.add(workflowRunRepositoryJpa.save(workflowRun))
            }

            log.info(
                "Successfully triggered {} backfill runs for dataset: {} on cluster: {}",
                runs.size,
                datasetName,
                cluster?.team ?: "default",
            )
            return runs
        } catch (ex: Exception) {
            log.error("Failed to trigger backfill for dataset: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Stop workflow run
     *
     * @param runId Run ID
     * @param reason Stop reason
     * @param stoppedBy User who stopped the run
     * @return Updated workflow run entity
     * @throws WorkflowRunNotFoundException if workflow run not found
     */
    @Transactional
    fun stopWorkflowRun(
        runId: String,
        reason: String? = null,
        stoppedBy: String = "system",
    ): WorkflowRunEntity {
        log.info("Stopping workflow run: {}, reason: {}", runId, reason)

        val workflowRun = getWorkflowRunOrThrow(runId)
        val workflow = getWorkflowOrThrow(workflowRun.datasetName)

        // Check if run can be stopped
        if (workflowRun.isFinished()) {
            throw IllegalStateException("Cannot stop run. Current status: ${workflowRun.status}")
        }

        try {
            // Stop DAG run in Airflow
            airflowClient.stopDAGRun(workflow.airflowDagId, runId)
            log.debug("Stopped Airflow DAG run: {}", runId)

            // Update workflow run entity
            workflowRun.requestStop(stoppedBy, reason)

            val savedRun = workflowRunRepositoryJpa.save(workflowRun)
            log.info("Successfully stopped workflow run: {}", runId)

            return savedRun
        } catch (ex: Exception) {
            log.error("Failed to stop workflow run: {}", runId, ex)
            throw ex
        }
    }

    /**
     * Pause workflow
     *
     * @param datasetName Dataset name
     * @param reason Pause reason
     * @param pausedBy User who paused the workflow
     * @return Updated workflow entity
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional
    fun pauseWorkflow(
        datasetName: String,
        reason: String? = null,
        pausedBy: String = "system",
    ): WorkflowEntity {
        log.info("Pausing workflow: {}, reason: {}", datasetName, reason)

        val workflow = getWorkflowOrThrow(datasetName)

        // Check if workflow can be paused
        if (!workflow.isActive()) {
            throw IllegalStateException("Cannot pause workflow. Current status: ${workflow.status}")
        }

        try {
            // Pause DAG in Airflow
            airflowClient.pauseDAG(workflow.airflowDagId, true)
            log.debug("Paused Airflow DAG: {}", workflow.airflowDagId)

            // Update workflow entity
            workflow.pause()

            val savedWorkflow = workflowRepositoryJpa.save(workflow)
            log.info("Successfully paused workflow: {}", datasetName)

            return savedWorkflow
        } catch (ex: Exception) {
            log.error("Failed to pause workflow: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Unpause workflow
     *
     * @param datasetName Dataset name
     * @param unpausedBy User who unpaused the workflow
     * @return Updated workflow entity
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional
    fun unpauseWorkflow(
        datasetName: String,
        unpausedBy: String = "system",
    ): WorkflowEntity {
        log.info("Unpausing workflow: {}", datasetName)

        val workflow = getWorkflowOrThrow(datasetName)

        // Check if workflow can be unpaused
        if (!workflow.isPaused()) {
            throw IllegalStateException("Cannot unpause workflow. Current status: ${workflow.status}")
        }

        try {
            // Unpause DAG in Airflow
            airflowClient.pauseDAG(workflow.airflowDagId, false)
            log.debug("Unpaused Airflow DAG: {}", workflow.airflowDagId)

            // Update workflow entity
            workflow.unpause()

            val savedWorkflow = workflowRepositoryJpa.save(workflow)
            log.info("Successfully unpaused workflow: {}", datasetName)

            return savedWorkflow
        } catch (ex: Exception) {
            log.error("Failed to unpause workflow: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Unregister workflow
     *
     * @param datasetName Dataset name
     * @param force Force delete even if runs are active
     * @param unregisteredBy User who unregistered the workflow
     * @return Updated workflow entity
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional
    fun unregisterWorkflow(
        datasetName: String,
        force: Boolean = false,
        unregisteredBy: String = "system",
    ): WorkflowEntity {
        log.info("Unregistering workflow: {}, force: {}", datasetName, force)

        val workflow = getWorkflowOrThrow(datasetName)

        // Check for active runs if not forcing
        if (!force) {
            val activeRuns =
                workflowRunRepositoryJpa.findByDatasetNameAndStatus(
                    datasetName,
                    WorkflowRunStatus.RUNNING,
                )
            if (activeRuns.isNotEmpty()) {
                throw IllegalStateException(
                    "Cannot unregister workflow with active runs. Use force=true to override.",
                )
            }
        }

        try {
            // Delete DAG in Airflow
            airflowClient.deleteDAG(workflow.airflowDagId)
            log.debug("Deleted Airflow DAG: {}", workflow.airflowDagId)

            // Delete YAML from storage
            workflowStorage.deleteWorkflowYaml(workflow.s3Path)
            log.debug("Deleted workflow YAML: {}", workflow.s3Path)

            // Soft delete workflow entity
            workflow.disable()
            workflow.deletedAt = LocalDateTime.now()

            val savedWorkflow = workflowRepositoryJpa.save(workflow)
            log.info("Successfully unregistered workflow: {}", datasetName)

            return savedWorkflow
        } catch (ex: Exception) {
            log.error("Failed to unregister workflow: {}", datasetName, ex)
            throw ex
        }
    }

    /**
     * Generate run ID
     */
    private fun generateRunId(
        datasetName: String,
        runType: WorkflowRunType = WorkflowRunType.MANUAL,
        date: LocalDate? = null,
    ): String {
        val timestamp = date?.toString() ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "${datasetName}_${runType.name.lowercase()}_$timestamp"
    }

    /**
     * Generate Airflow DAG ID from dataset name
     */
    private fun generateDagId(datasetName: String): String = datasetName.replace(".", "_").replace("-", "_")

    /**
     * Validate cron expression
     */
    private fun validateCronExpression(cron: String) {
        // Simple validation - could use a proper cron validator library
        val cronParts = cron.trim().split("\\s+".toRegex())
        if (cronParts.size != 5) {
            throw IllegalArgumentException("Invalid cron expression: $cron. Expected 5 parts.")
        }
    }

    /**
     * Generate date range between start and end dates (inclusive)
     */
    private fun generateDateRange(
        start: LocalDate,
        end: LocalDate,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = start
        while (!current.isAfter(end)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    /**
     * Serialize parameters to JSON string
     */
    private fun serializeParams(params: Map<String, Any>): String {
        // Simple JSON serialization - could use Jackson ObjectMapper
        return params.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"$key\":\"$value\""
        }
    }

    /**
     * Map Airflow DAG run status to WorkflowRunStatus
     */
    private fun mapAirflowStatusToWorkflowStatus(airflowStatus: AirflowDAGRunStatusResponse): WorkflowRunStatus =
        when (airflowStatus.state) {
            AirflowDAGRunState.QUEUED -> WorkflowRunStatus.PENDING
            AirflowDAGRunState.RUNNING -> WorkflowRunStatus.RUNNING
            AirflowDAGRunState.SUCCESS -> WorkflowRunStatus.SUCCESS
            AirflowDAGRunState.FAILED, AirflowDAGRunState.UPSTREAM_FAILED -> WorkflowRunStatus.FAILED
            AirflowDAGRunState.UP_FOR_RETRY -> WorkflowRunStatus.RUNNING
            AirflowDAGRunState.SKIPPED -> WorkflowRunStatus.FAILED
        }

    // === Phase 6: Cluster Routing ===

    /**
     * Resolve the Airflow cluster for a given team
     *
     * @param team Team name
     * @return AirflowClusterEntity if found and active, null otherwise
     */
    private fun resolveClusterForTeam(team: String): AirflowClusterEntity? {
        if (clusterRepository == null) {
            log.debug("Cluster repository not available, returning null for team: {}", team)
            return null
        }

        return try {
            clusterRepository.findByTeam(team)?.also { cluster ->
                log.debug("Resolved cluster {} for team: {}", cluster.id, team)
            }
        } catch (ex: Exception) {
            log.warn("Failed to resolve cluster for team {}: {}", team, ex.message)
            null
        }
    }

    companion object {
        /** Threshold in minutes for considering synced data as stale */
        private const val SYNC_STALE_THRESHOLD_MINUTES: Long = 60
    }
}
