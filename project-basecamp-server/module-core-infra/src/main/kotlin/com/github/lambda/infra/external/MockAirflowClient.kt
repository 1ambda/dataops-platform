package com.github.lambda.infra.external

import com.github.lambda.domain.external.AirflowClient
import com.github.lambda.domain.external.AirflowConnectionException
import com.github.lambda.domain.external.AirflowDAGRunState
import com.github.lambda.domain.external.AirflowDAGRunStatus
import com.github.lambda.domain.external.AirflowDagStatus
import com.github.lambda.domain.model.workflow.ScheduleInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Mock Airflow Client Implementation
 *
 * Provides mock Airflow responses for development and testing while the actual
 * Airflow integration is not yet implemented.
 */
@Repository("airflowClient")
class MockAirflowClient : AirflowClient {
    private val log = LoggerFactory.getLogger(MockAirflowClient::class.java)

    // Mock storage for DAG states and run statuses
    private val dagStates = ConcurrentHashMap<String, AirflowDagStatus>()
    private val dagRuns = ConcurrentHashMap<String, AirflowDAGRunStatus>()

    override fun triggerDAGRun(
        dagId: String,
        runId: String,
        conf: Map<String, Any>,
    ): String {
        log.info("Mock Airflow: Triggering DAG run - dagId: {}, runId: {}, conf: {}", dagId, runId, conf)

        // Simulate potential random failure (5% chance)
        if (Random.nextInt(100) < 5) {
            throw AirflowConnectionException("triggerDAGRun", RuntimeException("Mock network error"))
        }

        // Create and store DAG run status
        val dagRunStatus =
            AirflowDAGRunStatus(
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
                AirflowDagStatus(
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
    ): AirflowDAGRunStatus {
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

        // Simulate potential error (2% chance)
        if (Random.nextInt(100) < 2) {
            throw AirflowConnectionException("pauseDAG", RuntimeException("Mock DAG not found"))
        }

        val currentStatus =
            dagStates[dagId] ?: AirflowDagStatus(
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

        // Simulate potential validation error (3% chance)
        if (Random.nextInt(100) < 3) {
            throw AirflowConnectionException("createDAG", RuntimeException("Invalid DAG configuration"))
        }

        val dagStatus =
            AirflowDagStatus(
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

    override fun getDagStatus(dagId: String): AirflowDagStatus {
        log.info("Mock Airflow: Getting DAG status - dagId: {}", dagId)

        val status =
            dagStates[dagId]
                ?: throw AirflowConnectionException("getDagStatus", RuntimeException("DAG not found: $dagId"))

        simulateDelay(50, 150)

        return status
    }

    override fun isAvailable(): Boolean {
        log.debug("Mock Airflow: Checking availability")

        // Simulate occasional unavailability (1% chance)
        return Random.nextInt(100) >= 1
    }

    // === Helper Methods ===

    /**
     * Simulate realistic state progression for DAG runs
     */
    private fun simulateStateProgression(currentStatus: AirflowDAGRunStatus): AirflowDAGRunStatus {
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
}
