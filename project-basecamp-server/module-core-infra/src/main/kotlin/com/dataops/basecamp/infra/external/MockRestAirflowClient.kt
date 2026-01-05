package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.exception.AirflowConnectionException
import com.dataops.basecamp.domain.entity.workflow.AirflowClusterEntity
import com.dataops.basecamp.domain.external.airflow.AirflowClient
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunState
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunStatusResponse
import com.dataops.basecamp.domain.external.airflow.AirflowDagRunResponse
import com.dataops.basecamp.domain.external.airflow.AirflowDagStatusResponse
import com.dataops.basecamp.domain.external.airflow.AirflowTaskInstanceResponse
import com.dataops.basecamp.domain.external.airflow.AirflowTaskState
import com.dataops.basecamp.domain.external.airflow.BackfillCreateResponse
import com.dataops.basecamp.domain.external.airflow.BackfillState
import com.dataops.basecamp.domain.external.airflow.BackfillStatusResponse
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import com.dataops.basecamp.domain.repository.airflow.AirflowClusterRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock REST Airflow Client Implementation (Cluster-Aware)
 *
 * A more sophisticated mock that simulates Airflow 3 REST API patterns
 * with cluster-aware operations. Uses AirflowClusterRepository to route
 * requests to the appropriate cluster based on team/DAG ID.
 *
 * This client is suitable for integration testing scenarios where
 * realistic multi-cluster behavior is needed.
 */
@Repository("airflowClient")
@ConditionalOnProperty(
    name = ["basecamp.workflow.client.type"],
    havingValue = "rest-mock",
)
class MockRestAirflowClient(
    private val clusterRepository: AirflowClusterRepositoryJpa,
) : AirflowClient {
    private val log = LoggerFactory.getLogger(MockRestAirflowClient::class.java)

    // Mock storage per cluster (clusterId -> storage)
    private val clusterDagStates = ConcurrentHashMap<Long, ConcurrentHashMap<String, AirflowDagStatusResponse>>()
    private val clusterDagRuns = ConcurrentHashMap<Long, ConcurrentHashMap<String, AirflowDAGRunStatusResponse>>()
    private val clusterBackfills = ConcurrentHashMap<Long, ConcurrentHashMap<String, BackfillStatusResponse>>()
    private val clusterTaskInstances =
        ConcurrentHashMap<Long, ConcurrentHashMap<String, List<AirflowTaskInstanceResponse>>>()

    override fun triggerDAGRun(
        dagId: String,
        runId: String,
        conf: Map<String, Any>,
    ): String {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Triggering DAG run - cluster: {}, dagId: {}, runId: {}",
            cluster.team,
            dagId,
            runId,
        )

        val dagRuns = getOrCreateDagRuns(cluster.id!!)
        val dagStates = getOrCreateDagStates(cluster.id!!)

        val dagRunStatus =
            AirflowDAGRunStatusResponse(
                dagRunId = runId,
                state = AirflowDAGRunState.QUEUED,
                startDate = LocalDateTime.now(),
                endDate = null,
                executionDate = LocalDateTime.now(),
                logsUrl = "${cluster.airflowUrl}/log?dag_id=$dagId&task_id=start",
            )

        dagRuns["${dagId}_$runId"] = dagRunStatus

        if (!dagStates.containsKey(dagId)) {
            dagStates[dagId] =
                AirflowDagStatusResponse(
                    dagId = dagId,
                    isPaused = false,
                    lastParsed = LocalDateTime.now(),
                    isActive = true,
                )
        }

        return runId
    }

    override fun getDAGRun(
        dagId: String,
        runId: String,
    ): AirflowDAGRunStatusResponse {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Getting DAG run status - cluster: {}, dagId: {}, runId: {}",
            cluster.team,
            dagId,
            runId,
        )

        val dagRuns = getOrCreateDagRuns(cluster.id!!)
        val key = "${dagId}_$runId"

        val currentStatus =
            dagRuns[key]
                ?: throw AirflowConnectionException(
                    "getDAGRun",
                    RuntimeException("DAG run not found: $key on cluster ${cluster.team}"),
                )

        val updatedStatus = simulateStateProgression(currentStatus)
        dagRuns[key] = updatedStatus

        return updatedStatus
    }

    override fun stopDAGRun(
        dagId: String,
        runId: String,
    ): Boolean {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Stopping DAG run - cluster: {}, dagId: {}, runId: {}",
            cluster.team,
            dagId,
            runId,
        )

        val dagRuns = getOrCreateDagRuns(cluster.id!!)
        val key = "${dagId}_$runId"
        val currentStatus = dagRuns[key] ?: return false

        if (currentStatus.state in listOf(AirflowDAGRunState.QUEUED, AirflowDAGRunState.RUNNING)) {
            val stoppedStatus =
                currentStatus.copy(
                    state = AirflowDAGRunState.FAILED,
                    endDate = LocalDateTime.now(),
                )
            dagRuns[key] = stoppedStatus
            return true
        }

        return false
    }

    override fun pauseDAG(
        dagId: String,
        isPaused: Boolean,
    ): Boolean {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Setting DAG pause state - cluster: {}, dagId: {}, isPaused: {}",
            cluster.team,
            dagId,
            isPaused,
        )

        val dagStates = getOrCreateDagStates(cluster.id!!)

        val currentStatus =
            dagStates[dagId] ?: AirflowDagStatusResponse(
                dagId = dagId,
                isPaused = false,
                lastParsed = LocalDateTime.now(),
                isActive = true,
            )

        val updatedStatus = currentStatus.copy(isPaused = isPaused)
        dagStates[dagId] = updatedStatus

        return true
    }

    override fun createDAG(
        datasetName: String,
        schedule: ScheduleInfo,
        s3Path: String,
    ): String {
        // Extract team from dataset name (format: team__dataset_name)
        val team = extractTeamFromDatasetName(datasetName)
        val cluster = resolveClusterForTeam(team)
        val dagId = "dag_${datasetName.lowercase().replace("[^a-z0-9]".toRegex(), "_")}"

        log.info(
            "MockRestAirflow: Creating DAG - cluster: {}, dagId: {}, datasetName: {}",
            cluster.team,
            dagId,
            datasetName,
        )

        val dagStates = getOrCreateDagStates(cluster.id!!)

        val dagStatus =
            AirflowDagStatusResponse(
                dagId = dagId,
                isPaused = false,
                lastParsed = LocalDateTime.now(),
                isActive = true,
            )

        dagStates[dagId] = dagStatus

        return dagId
    }

    override fun deleteDAG(dagId: String): Boolean {
        val cluster = resolveClusterForDag(dagId)
        log.info("MockRestAirflow: Deleting DAG - cluster: {}, dagId: {}", cluster.team, dagId)

        val dagStates = getOrCreateDagStates(cluster.id!!)
        val dagRuns = getOrCreateDagRuns(cluster.id!!)

        val removed = dagStates.remove(dagId) != null

        // Also remove related DAG runs
        val runKeysToRemove = dagRuns.keys.filter { it.startsWith("${dagId}_") }
        runKeysToRemove.forEach { dagRuns.remove(it) }

        return removed
    }

    override fun getDagStatus(dagId: String): AirflowDagStatusResponse {
        val cluster = resolveClusterForDag(dagId)
        log.info("MockRestAirflow: Getting DAG status - cluster: {}, dagId: {}", cluster.team, dagId)

        val dagStates = getOrCreateDagStates(cluster.id!!)

        return dagStates[dagId]
            ?: throw AirflowConnectionException(
                "getDagStatus",
                RuntimeException("DAG not found: $dagId on cluster ${cluster.team}"),
            )
    }

    override fun isAvailable(): Boolean {
        val clusters = clusterRepository.findAllActive()
        return clusters.isNotEmpty()
    }

    // ============ Phase 4: Backfill & Run Sync Methods ============

    override fun createBackfill(
        dagId: String,
        fromDate: String,
        toDate: String,
    ): BackfillCreateResponse {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Creating backfill - cluster: {}, dagId: {}, fromDate: {}, toDate: {}",
            cluster.team,
            dagId,
            fromDate,
            toDate,
        )

        val backfills = getOrCreateBackfills(cluster.id!!)
        val backfillId = UUID.randomUUID().toString()
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val status =
            BackfillStatusResponse(
                id = backfillId,
                dagId = dagId,
                fromDate = fromDate,
                toDate = toDate,
                isPaused = false,
                completedAt = null,
                state = BackfillState.RUNNING,
            )
        backfills[backfillId] = status

        return BackfillCreateResponse(
            id = backfillId,
            dagId = dagId,
            fromDate = fromDate,
            toDate = toDate,
            isPaused = false,
            createdAt = now,
        )
    }

    override fun getBackfillStatus(backfillId: String): BackfillStatusResponse {
        log.info("MockRestAirflow: Getting backfill status - backfillId: {}", backfillId)

        // Search across all clusters for the backfill
        for ((clusterId, backfills) in clusterBackfills) {
            val status = backfills[backfillId]
            if (status != null) {
                val updatedStatus = simulateBackfillProgression(status)
                backfills[backfillId] = updatedStatus
                return updatedStatus
            }
        }

        throw AirflowConnectionException(
            "getBackfillStatus",
            RuntimeException("Backfill not found: $backfillId"),
        )
    }

    override fun pauseBackfill(backfillId: String): BackfillStatusResponse {
        log.info("MockRestAirflow: Pausing backfill - backfillId: {}", backfillId)

        for ((_, backfills) in clusterBackfills) {
            val status = backfills[backfillId]
            if (status != null && status.state == BackfillState.RUNNING) {
                val pausedStatus = status.copy(isPaused = true)
                backfills[backfillId] = pausedStatus
                return pausedStatus
            }
        }

        throw AirflowConnectionException(
            "pauseBackfill",
            RuntimeException("Backfill not found or not running: $backfillId"),
        )
    }

    override fun unpauseBackfill(backfillId: String): BackfillStatusResponse {
        log.info("MockRestAirflow: Unpausing backfill - backfillId: {}", backfillId)

        for ((_, backfills) in clusterBackfills) {
            val status = backfills[backfillId]
            if (status != null && status.isPaused) {
                val unpausedStatus = status.copy(isPaused = false)
                backfills[backfillId] = unpausedStatus
                return unpausedStatus
            }
        }

        throw AirflowConnectionException(
            "unpauseBackfill",
            RuntimeException("Backfill not found or not paused: $backfillId"),
        )
    }

    override fun cancelBackfill(backfillId: String): Boolean {
        log.info("MockRestAirflow: Cancelling backfill - backfillId: {}", backfillId)

        for ((_, backfills) in clusterBackfills) {
            val status = backfills[backfillId]
            if (status != null && status.state in listOf(BackfillState.RUNNING, BackfillState.QUEUED)) {
                val cancelledStatus =
                    status.copy(
                        state = BackfillState.CANCELLED,
                        completedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    )
                backfills[backfillId] = cancelledStatus
                return true
            }
        }

        return false
    }

    override fun listRecentDagRuns(
        since: LocalDateTime,
        limit: Int,
    ): List<AirflowDagRunResponse> {
        log.info("MockRestAirflow: Listing recent DAG runs across all clusters - since: {}, limit: {}", since, limit)

        val allRuns = mutableListOf<AirflowDagRunResponse>()

        for ((_, dagRuns) in clusterDagRuns) {
            for ((key, status) in dagRuns) {
                val startDate = status.startDate ?: status.executionDate
                if (startDate.isAfter(since)) {
                    val dagId = key.substringBefore("_")
                    allRuns.add(
                        AirflowDagRunResponse(
                            dagId = dagId,
                            dagRunId = status.dagRunId,
                            state = status.state,
                            logicalDate = status.executionDate,
                            startDate = status.startDate,
                            endDate = status.endDate,
                            conf = null,
                        ),
                    )
                }
            }
        }

        return allRuns
            .sortedByDescending { it.startDate ?: it.logicalDate }
            .take(limit)
    }

    override fun listDagRuns(
        dagId: String,
        limit: Int,
    ): List<AirflowDagRunResponse> {
        val cluster = resolveClusterForDag(dagId)
        log.info("MockRestAirflow: Listing DAG runs - cluster: {}, dagId: {}, limit: {}", cluster.team, dagId, limit)

        val dagRuns = getOrCreateDagRuns(cluster.id!!)

        return dagRuns.entries
            .filter { (key, _) -> key.startsWith("${dagId}_") }
            .map { (_, status) ->
                AirflowDagRunResponse(
                    dagId = dagId,
                    dagRunId = status.dagRunId,
                    state = status.state,
                    logicalDate = status.executionDate,
                    startDate = status.startDate,
                    endDate = status.endDate,
                    conf = null,
                )
            }.sortedByDescending { it.startDate ?: it.logicalDate }
            .take(limit)
    }

    override fun getTaskInstances(
        dagId: String,
        dagRunId: String,
    ): List<AirflowTaskInstanceResponse> {
        val cluster = resolveClusterForDag(dagId)
        log.info(
            "MockRestAirflow: Getting task instances - cluster: {}, dagId: {}, dagRunId: {}",
            cluster.team,
            dagId,
            dagRunId,
        )

        val taskInstances = getOrCreateTaskInstances(cluster.id!!)
        val key = "${dagId}_$dagRunId"

        return taskInstances.getOrPut(key) {
            generateMockTaskInstances(dagId, dagRunId)
        }
    }

    // === Cluster Resolution Methods ===

    /**
     * Resolve cluster for a DAG based on its ID pattern.
     * DAG ID format: dag_{team}__{name} -> extract team
     */
    private fun resolveClusterForDag(dagId: String): AirflowClusterEntity {
        val team = extractTeamFromDagId(dagId)
        return resolveClusterForTeam(team)
    }

    /**
     * Resolve cluster for a team name
     */
    private fun resolveClusterForTeam(team: String): AirflowClusterEntity =
        clusterRepository.findByTeam(team)
            ?: clusterRepository.findAllActive().firstOrNull()
            ?: throw AirflowConnectionException(
                "resolveCluster",
                RuntimeException("No active Airflow cluster found for team: $team"),
            )

    /**
     * Extract team from DAG ID (format: dag_{team}__{name})
     */
    private fun extractTeamFromDagId(dagId: String): String {
        val parts = dagId.removePrefix("dag_").split("__")
        return if (parts.size >= 2) parts[0] else "default"
    }

    /**
     * Extract team from dataset name (format: {team}__{name})
     */
    private fun extractTeamFromDatasetName(datasetName: String): String {
        val parts = datasetName.split("__")
        return if (parts.size >= 2) parts[0] else "default"
    }

    // === Storage Access Methods ===

    private fun getOrCreateDagStates(clusterId: Long): ConcurrentHashMap<String, AirflowDagStatusResponse> =
        clusterDagStates.getOrPut(clusterId) { ConcurrentHashMap() }

    private fun getOrCreateDagRuns(clusterId: Long): ConcurrentHashMap<String, AirflowDAGRunStatusResponse> =
        clusterDagRuns.getOrPut(clusterId) { ConcurrentHashMap() }

    private fun getOrCreateBackfills(clusterId: Long): ConcurrentHashMap<String, BackfillStatusResponse> =
        clusterBackfills.getOrPut(clusterId) { ConcurrentHashMap() }

    private fun getOrCreateTaskInstances(
        clusterId: Long,
    ): ConcurrentHashMap<String, List<AirflowTaskInstanceResponse>> =
        clusterTaskInstances.getOrPut(clusterId) { ConcurrentHashMap() }

    // === Helper Methods ===

    private fun simulateStateProgression(currentStatus: AirflowDAGRunStatusResponse): AirflowDAGRunStatusResponse {
        val now = LocalDateTime.now()
        val startTime = currentStatus.startDate ?: now
        val timeSinceStart =
            java.time.Duration
                .between(startTime, now)
                .seconds

        return when (currentStatus.state) {
            AirflowDAGRunState.QUEUED -> {
                if (timeSinceStart > 5) {
                    currentStatus.copy(
                        state = AirflowDAGRunState.RUNNING,
                        startDate = now,
                    )
                } else {
                    currentStatus
                }
            }
            AirflowDAGRunState.RUNNING -> {
                if (timeSinceStart > 30) {
                    currentStatus.copy(
                        state = AirflowDAGRunState.SUCCESS,
                        endDate = now,
                    )
                } else {
                    currentStatus
                }
            }
            else -> currentStatus
        }
    }

    private fun simulateBackfillProgression(currentStatus: BackfillStatusResponse): BackfillStatusResponse {
        if (currentStatus.isPaused || currentStatus.state !in listOf(BackfillState.QUEUED, BackfillState.RUNNING)) {
            return currentStatus
        }

        return currentStatus
    }

    private fun generateMockTaskInstances(
        dagId: String,
        dagRunId: String,
    ): List<AirflowTaskInstanceResponse> {
        val taskNames = listOf("start", "extract", "transform", "load", "validate", "end")
        val now = LocalDateTime.now()

        return taskNames.mapIndexed { index, taskName ->
            val taskState =
                when {
                    index < taskNames.size - 2 -> AirflowTaskState.SUCCESS
                    index == taskNames.size - 2 -> AirflowTaskState.RUNNING
                    else -> AirflowTaskState.SCHEDULED
                }

            val startDate =
                if (taskState != AirflowTaskState.SCHEDULED) {
                    now.minusMinutes((taskNames.size - index - 1) * 5L)
                } else {
                    null
                }

            val endDate =
                if (taskState == AirflowTaskState.SUCCESS) {
                    startDate?.plusMinutes(3)
                } else {
                    null
                }

            AirflowTaskInstanceResponse(
                taskId = taskName,
                dagId = dagId,
                dagRunId = dagRunId,
                state = taskState,
                startDate = startDate,
                endDate = endDate,
                tryNumber = 1,
                duration = endDate?.let { 180.0 },
            )
        }
    }
}
