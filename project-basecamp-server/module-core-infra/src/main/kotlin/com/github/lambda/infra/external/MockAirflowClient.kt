package com.github.lambda.infra.external

import com.github.lambda.common.exception.AirflowConnectionException
import com.github.lambda.domain.external.airflow.AirflowClient
import com.github.lambda.domain.external.airflow.AirflowDAGRunState
import com.github.lambda.domain.external.airflow.AirflowDAGRunStatusResponse
import com.github.lambda.domain.external.airflow.AirflowDagRunResponse
import com.github.lambda.domain.external.airflow.AirflowDagStatusResponse
import com.github.lambda.domain.external.airflow.AirflowTaskInstanceResponse
import com.github.lambda.domain.external.airflow.AirflowTaskState
import com.github.lambda.domain.external.airflow.BackfillCreateResponse
import com.github.lambda.domain.external.airflow.BackfillState
import com.github.lambda.domain.external.airflow.BackfillStatusResponse
import com.github.lambda.domain.model.workflow.ScheduleInfo
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Mock Airflow Client Implementation
 *
 * Provides mock Airflow responses for development and testing while the actual
 * Airflow integration is not yet implemented.
 *
 * @param deterministicMode When true, disables random failures for deterministic testing.
 *                          Default is false for realistic simulation in development.
 */
@Repository("airflowClient")
@ConditionalOnProperty(
    name = ["basecamp.workflow.client.type"],
    havingValue = "mock",
    matchIfMissing = true,
)
class MockAirflowClient(
    private val deterministicMode: Boolean = false,
) : AirflowClient {
    private val log = LoggerFactory.getLogger(MockAirflowClient::class.java)

    // Mock storage for DAG states and run statuses
    private val dagStates = ConcurrentHashMap<String, AirflowDagStatusResponse>()
    private val dagRuns = ConcurrentHashMap<String, AirflowDAGRunStatusResponse>()
    private val backfills = ConcurrentHashMap<String, BackfillStatusResponse>()
    private val taskInstances = ConcurrentHashMap<String, List<AirflowTaskInstanceResponse>>()

    override fun triggerDAGRun(
        dagId: String,
        runId: String,
        conf: Map<String, Any>,
    ): String {
        log.info("Mock Airflow: Triggering DAG run - dagId: {}, runId: {}, conf: {}", dagId, runId, conf)

        // Simulate potential random failure (5% chance) - disabled in deterministic mode
        if (!deterministicMode && Random.nextInt(100) < 5) {
            throw AirflowConnectionException("triggerDAGRun", RuntimeException("Mock network error"))
        }

        // Create and store DAG run status
        val dagRunStatus =
            AirflowDAGRunStatusResponse(
                dagRunId = runId,
                state = AirflowDAGRunState.QUEUED,
                startDate = LocalDateTime.now(),
                endDate = null,
                executionDate = LocalDateTime.now(),
                logsUrl = "http://mock-airflow:8080/log?dag_id=$dagId&task_id=start",
            )

        dagRuns["${dagId}_$runId"] = dagRunStatus

        // Ensure DAG exists in state storage
        if (!dagStates.containsKey(dagId)) {
            dagStates[dagId] =
                AirflowDagStatusResponse(
                    dagId = dagId,
                    isPaused = false,
                    lastParsed = LocalDateTime.now(),
                    isActive = true,
                )
        }

        // Simulate processing delay
        simulateDelay(100, 500)

        return runId
    }

    override fun getDAGRun(
        dagId: String,
        runId: String,
    ): AirflowDAGRunStatusResponse {
        log.info("Mock Airflow: Getting DAG run status - dagId: {}, runId: {}", dagId, runId)

        val key = "${dagId}_$runId"
        val currentStatus =
            dagRuns[key]
                ?: throw AirflowConnectionException("getDAGRun", RuntimeException("DAG run not found: $key"))

        // Simulate state progression
        val updatedStatus = simulateStateProgression(currentStatus)
        dagRuns[key] = updatedStatus

        simulateDelay(50, 200)

        return updatedStatus
    }

    override fun stopDAGRun(
        dagId: String,
        runId: String,
    ): Boolean {
        log.info("Mock Airflow: Stopping DAG run - dagId: {}, runId: {}", dagId, runId)

        val key = "${dagId}_$runId"
        val currentStatus = dagRuns[key] ?: return false

        // Only allow stopping QUEUED or RUNNING states
        if (currentStatus.state in listOf(AirflowDAGRunState.QUEUED, AirflowDAGRunState.RUNNING)) {
            val stoppedStatus =
                currentStatus.copy(
                    state = AirflowDAGRunState.FAILED,
                    endDate = LocalDateTime.now(),
                )
            dagRuns[key] = stoppedStatus

            simulateDelay(100, 300)
            return true
        }

        return false
    }

    override fun pauseDAG(
        dagId: String,
        isPaused: Boolean,
    ): Boolean {
        log.info("Mock Airflow: Setting DAG pause state - dagId: {}, isPaused: {}", dagId, isPaused)

        // Simulate potential error (2% chance) - disabled in deterministic mode
        if (!deterministicMode && Random.nextInt(100) < 2) {
            throw AirflowConnectionException("pauseDAG", RuntimeException("Mock DAG not found"))
        }

        val currentStatus =
            dagStates[dagId] ?: AirflowDagStatusResponse(
                dagId = dagId,
                isPaused = false,
                lastParsed = LocalDateTime.now(),
                isActive = true,
            )

        val updatedStatus = currentStatus.copy(isPaused = isPaused)
        dagStates[dagId] = updatedStatus

        simulateDelay(50, 150)

        return true
    }

    override fun createDAG(
        datasetName: String,
        schedule: ScheduleInfo,
        s3Path: String,
    ): String {
        val dagId = "dag_${datasetName.lowercase().replace("[^a-z0-9]".toRegex(), "_")}"
        log.info("Mock Airflow: Creating DAG - dagId: {}, datasetName: {}, s3Path: {}", dagId, datasetName, s3Path)

        // Simulate potential validation error (3% chance) - disabled in deterministic mode
        if (!deterministicMode && Random.nextInt(100) < 3) {
            throw AirflowConnectionException("createDAG", RuntimeException("Invalid DAG configuration"))
        }

        val dagStatus =
            AirflowDagStatusResponse(
                dagId = dagId,
                isPaused = false,
                lastParsed = LocalDateTime.now(),
                isActive = true,
            )

        dagStates[dagId] = dagStatus

        simulateDelay(200, 800)

        return dagId
    }

    override fun deleteDAG(dagId: String): Boolean {
        log.info("Mock Airflow: Deleting DAG - dagId: {}", dagId)

        val removed = dagStates.remove(dagId) != null

        // Also remove any related DAG runs
        val runKeysToRemove = dagRuns.keys.filter { it.startsWith("${dagId}_") }
        runKeysToRemove.forEach { dagRuns.remove(it) }

        simulateDelay(100, 400)

        return removed
    }

    override fun getDagStatus(dagId: String): AirflowDagStatusResponse {
        log.info("Mock Airflow: Getting DAG status - dagId: {}", dagId)

        val status =
            dagStates[dagId]
                ?: throw AirflowConnectionException("getDagStatus", RuntimeException("DAG not found: $dagId"))

        simulateDelay(50, 150)

        return status
    }

    override fun isAvailable(): Boolean {
        log.debug("Mock Airflow: Checking availability")

        // Simulate occasional unavailability (1% chance) - disabled in deterministic mode
        return deterministicMode || Random.nextInt(100) >= 1
    }

    // ============ Phase 4: Backfill & Run Sync Methods ============

    override fun createBackfill(
        dagId: String,
        fromDate: String,
        toDate: String,
    ): BackfillCreateResponse {
        log.info("Mock Airflow: Creating backfill - dagId: {}, fromDate: {}, toDate: {}", dagId, fromDate, toDate)

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

        simulateDelay(100, 300)

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
        log.info("Mock Airflow: Getting backfill status - backfillId: {}", backfillId)

        val status =
            backfills[backfillId]
                ?: throw AirflowConnectionException(
                    "getBackfillStatus",
                    RuntimeException("Backfill not found: $backfillId"),
                )

        // Simulate state progression for backfills
        val updatedStatus = simulateBackfillProgression(status)
        backfills[backfillId] = updatedStatus

        simulateDelay(50, 150)

        return updatedStatus
    }

    override fun pauseBackfill(backfillId: String): BackfillStatusResponse {
        log.info("Mock Airflow: Pausing backfill - backfillId: {}", backfillId)

        val status =
            backfills[backfillId]
                ?: throw AirflowConnectionException(
                    "pauseBackfill",
                    RuntimeException("Backfill not found: $backfillId"),
                )

        if (status.state == BackfillState.RUNNING) {
            val pausedStatus = status.copy(isPaused = true)
            backfills[backfillId] = pausedStatus
            simulateDelay(50, 150)
            return pausedStatus
        }

        return status
    }

    override fun unpauseBackfill(backfillId: String): BackfillStatusResponse {
        log.info("Mock Airflow: Unpausing backfill - backfillId: {}", backfillId)

        val status =
            backfills[backfillId]
                ?: throw AirflowConnectionException(
                    "unpauseBackfill",
                    RuntimeException("Backfill not found: $backfillId"),
                )

        if (status.isPaused) {
            val unpausedStatus = status.copy(isPaused = false)
            backfills[backfillId] = unpausedStatus
            simulateDelay(50, 150)
            return unpausedStatus
        }

        return status
    }

    override fun cancelBackfill(backfillId: String): Boolean {
        log.info("Mock Airflow: Cancelling backfill - backfillId: {}", backfillId)

        val status = backfills[backfillId] ?: return false

        if (status.state in listOf(BackfillState.RUNNING, BackfillState.QUEUED)) {
            val cancelledStatus =
                status.copy(
                    state = BackfillState.CANCELLED,
                    completedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
            backfills[backfillId] = cancelledStatus
            simulateDelay(50, 150)
            return true
        }

        return false
    }

    override fun listRecentDagRuns(
        since: LocalDateTime,
        limit: Int,
    ): List<AirflowDagRunResponse> {
        log.info("Mock Airflow: Listing recent DAG runs - since: {}, limit: {}", since, limit)

        simulateDelay(50, 200)

        return dagRuns.values
            .filter { run ->
                val startDate = run.startDate ?: run.executionDate
                startDate.isAfter(since)
            }.sortedByDescending { it.startDate ?: it.executionDate }
            .take(limit)
            .map { status ->
                AirflowDagRunResponse(
                    dagId = extractDagId(status.dagRunId),
                    dagRunId = status.dagRunId,
                    state = status.state,
                    logicalDate = status.executionDate,
                    startDate = status.startDate,
                    endDate = status.endDate,
                    conf = null,
                )
            }
    }

    override fun listDagRuns(
        dagId: String,
        limit: Int,
    ): List<AirflowDagRunResponse> {
        log.info("Mock Airflow: Listing DAG runs - dagId: {}, limit: {}", dagId, limit)

        simulateDelay(50, 200)

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
        log.info("Mock Airflow: Getting task instances - dagId: {}, dagRunId: {}", dagId, dagRunId)

        val key = "${dagId}_$dagRunId"

        // Return stored task instances or generate mock ones
        val instances =
            taskInstances.getOrPut(key) {
                generateMockTaskInstances(dagId, dagRunId)
            }

        simulateDelay(50, 150)

        return instances
    }

    // === Helper Methods ===

    /**
     * Simulate realistic state progression for DAG runs
     */
    private fun simulateStateProgression(currentStatus: AirflowDAGRunStatusResponse): AirflowDAGRunStatusResponse {
        val now = LocalDateTime.now()
        val startTime = currentStatus.startDate ?: now
        val timeSinceStart =
            java.time.Duration
                .between(startTime, now)
                .seconds

        return when (currentStatus.state) {
            AirflowDAGRunState.QUEUED -> {
                // After 10 seconds in queue, move to running
                if (timeSinceStart > 10) {
                    currentStatus.copy(
                        state = AirflowDAGRunState.RUNNING,
                        startDate = now,
                    )
                } else {
                    currentStatus
                }
            }
            AirflowDAGRunState.RUNNING -> {
                // After 30-120 seconds of running, complete
                val runDuration = Random.nextLong(30, 120)
                if (timeSinceStart > runDuration) {
                    val isSuccess = Random.nextInt(100) < 85 // 85% success rate
                    currentStatus.copy(
                        state = if (isSuccess) AirflowDAGRunState.SUCCESS else AirflowDAGRunState.FAILED,
                        endDate = now,
                    )
                } else {
                    currentStatus
                }
            }
            AirflowDAGRunState.FAILED -> {
                // Sometimes retry failed jobs (20% chance)
                if (timeSinceStart > 60 && Random.nextInt(100) < 20) {
                    currentStatus.copy(
                        state = AirflowDAGRunState.UP_FOR_RETRY,
                        startDate = now,
                        endDate = null,
                    )
                } else {
                    currentStatus
                }
            }
            AirflowDAGRunState.UP_FOR_RETRY -> {
                // After 10 seconds, retry
                if (timeSinceStart > 10) {
                    currentStatus.copy(
                        state = AirflowDAGRunState.RUNNING,
                        startDate = now,
                    )
                } else {
                    currentStatus
                }
            }
            else -> currentStatus // SUCCESS, UPSTREAM_FAILED, SKIPPED don't change
        }
    }

    /**
     * Simulate network delay for realistic behavior
     */
    private fun simulateDelay(
        minMs: Long,
        maxMs: Long,
    ) {
        val delay = Random.nextLong(minMs, maxMs)
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Simulate realistic state progression for backfills
     */
    private fun simulateBackfillProgression(currentStatus: BackfillStatusResponse): BackfillStatusResponse {
        if (currentStatus.isPaused || currentStatus.state !in listOf(BackfillState.QUEUED, BackfillState.RUNNING)) {
            return currentStatus
        }

        // Random chance to complete (10% per check)
        return if (Random.nextInt(100) < 10) {
            currentStatus.copy(
                state = BackfillState.COMPLETED,
                completedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        } else {
            currentStatus
        }
    }

    /**
     * Generate mock task instances for a DAG run
     */
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

    /**
     * Extract DAG ID from the dag run key (format: "{dagId}_{runId}")
     */
    private fun extractDagId(dagRunKey: String): String = dagRunKey.substringBefore("_")
}
