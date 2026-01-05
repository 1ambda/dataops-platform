package com.dataops.basecamp.domain.service

import com.dataops.basecamp.domain.entity.workflow.AirflowClusterEntity
import com.dataops.basecamp.domain.entity.workflow.WorkflowRunEntity
import com.dataops.basecamp.domain.external.airflow.AirflowClient
import com.dataops.basecamp.domain.external.airflow.AirflowDagRunResponse
import com.dataops.basecamp.domain.projection.workflow.ClusterSyncProjection
import com.dataops.basecamp.domain.projection.workflow.RunSyncProjection
import com.dataops.basecamp.domain.projection.workflow.TaskProgressProjection
import com.dataops.basecamp.domain.projection.workflow.WorkflowSyncStatisticsProjection
import com.dataops.basecamp.domain.repository.airflow.AirflowClusterRepositoryJpa
import com.dataops.basecamp.domain.repository.workflow.WorkflowRunRepositoryDsl
import com.dataops.basecamp.domain.repository.workflow.WorkflowRunRepositoryJpa
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime

/**
 * Airflow Run Sync Service (Phase 5)
 *
 * Airflow 클러스터에서 DAG Run 상태를 동기화하는 서비스
 */
@Service
@Transactional(readOnly = true)
class AirflowRunSyncService(
    private val airflowClient: AirflowClient,
    private val clusterRepository: AirflowClusterRepositoryJpa,
    private val workflowRunRepositoryJpa: WorkflowRunRepositoryJpa,
    private val workflowRunRepositoryDsl: WorkflowRunRepositoryDsl,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(AirflowRunSyncService::class.java)

    /**
     * 모든 활성 클러스터에서 Run 동기화 실행
     *
     * @param lookbackHours 동기화 대상 시간 범위 (기본: 24시간)
     * @param batchSize 클러스터당 최대 동기화 Run 수 (기본: 100)
     * @return 전체 동기화 결과
     */
    @Transactional
    fun syncAllClusters(
        lookbackHours: Long = 24,
        batchSize: Int = 100,
    ): RunSyncProjection {
        logger.info(
            "Starting Airflow run sync for all active clusters (lookback: {}h, batch: {})",
            lookbackHours,
            batchSize,
        )

        val activeClusters = clusterRepository.findAllActive()
        if (activeClusters.isEmpty()) {
            logger.info("No active Airflow clusters found, skipping sync")
            return RunSyncProjection.empty()
        }

        val clusterResults =
            activeClusters.map { cluster ->
                syncClusterSafe(cluster, lookbackHours, batchSize)
            }

        val result =
            RunSyncProjection(
                totalClusters = activeClusters.size,
                clusterResults = clusterResults,
                syncedAt = Instant.now(),
            )

        logger.info(
            "Completed Airflow run sync: {} clusters, {} updated, {} created, {} failures",
            result.totalClusters,
            result.totalUpdated,
            result.totalCreated,
            result.failedClusters,
        )

        return result
    }

    /**
     * 특정 클러스터에서 Run 동기화 실행
     *
     * @param clusterId 클러스터 ID
     * @param lookbackHours 동기화 대상 시간 범위 (기본: 24시간)
     * @param batchSize 최대 동기화 Run 수 (기본: 100)
     * @return 클러스터 동기화 결과
     */
    @Transactional
    fun syncCluster(
        clusterId: Long,
        lookbackHours: Long = 24,
        batchSize: Int = 100,
    ): ClusterSyncProjection {
        logger.info(
            "Starting Airflow run sync for cluster {} (lookback: {}h, batch: {})",
            clusterId,
            lookbackHours,
            batchSize,
        )

        val cluster =
            clusterRepository.findById(clusterId)
                ?: return ClusterSyncProjection.failure(
                    clusterId = clusterId,
                    clusterName = "unknown",
                    error = "Cluster not found: $clusterId",
                )

        return syncClusterSafe(cluster, lookbackHours, batchSize)
    }

    /**
     * Stale 상태의 Run 동기화 실행
     *
     * @param staleThresholdHours stale로 간주하는 시간 (기본: 1시간)
     * @return 동기화 결과
     */
    @Transactional
    fun syncStaleRuns(staleThresholdHours: Long = 1): RunSyncProjection {
        logger.info("Starting sync for stale runs (threshold: {}h)", staleThresholdHours)

        val staleThreshold = LocalDateTime.now().minusHours(staleThresholdHours)
        val staleRuns = workflowRunRepositoryDsl.findStaleRuns(staleThreshold)

        if (staleRuns.isEmpty()) {
            logger.info("No stale runs found")
            return RunSyncProjection.empty()
        }

        var updatedCount = 0
        val errors = mutableListOf<String>()

        staleRuns.forEach { run ->
            try {
                if (syncSingleRun(run)) {
                    updatedCount++
                }
            } catch (e: Exception) {
                logger.error("Failed to sync stale run {}: {}", run.runId, e.message)
                errors.add("Run ${run.runId}: ${e.message}")
            }
        }

        val result =
            ClusterSyncProjection(
                clusterId = 0,
                clusterName = "stale-sync",
                updatedCount = updatedCount,
                createdCount = 0,
                error = if (errors.isNotEmpty()) errors.joinToString("; ") else null,
            )

        return RunSyncProjection.single(result)
    }

    /**
     * 동기화 통계 조회
     *
     * @param clusterId 클러스터 ID (null이면 전체)
     * @return 동기화 통계
     */
    fun getSyncStatistics(clusterId: Long? = null): WorkflowSyncStatisticsProjection =
        workflowRunRepositoryDsl.getSyncStatistics(clusterId)

    // === Private Helper Methods ===

    private fun syncClusterSafe(
        cluster: AirflowClusterEntity,
        lookbackHours: Long,
        batchSize: Int,
    ): ClusterSyncProjection =
        try {
            doSyncCluster(cluster, lookbackHours, batchSize)
        } catch (e: Exception) {
            logger.error("Failed to sync cluster {}: {}", cluster.id, e.message, e)
            ClusterSyncProjection.failure(
                clusterId = cluster.id!!,
                clusterName = cluster.team,
                error = e.message ?: "Unknown error",
            )
        }

    private fun doSyncCluster(
        cluster: AirflowClusterEntity,
        lookbackHours: Long,
        batchSize: Int,
    ): ClusterSyncProjection {
        val since = LocalDateTime.now().minusHours(lookbackHours)

        // 1. Airflow에서 최근 DAG Run 목록 조회
        val airflowRuns = airflowClient.listRecentDagRuns(since, batchSize)
        logger.debug("Fetched {} DAG runs from Airflow cluster {}", airflowRuns.size, cluster.team)

        // 2. 우리 DB에서 진행 중인 Run 조회
        val pendingRuns = workflowRunRepositoryDsl.findPendingRunsByCluster(cluster.id!!, since)
        logger.debug("Found {} pending runs in DB for cluster {}", pendingRuns.size, cluster.team)

        var updatedCount = 0
        var createdCount = 0

        // 3. Airflow Run을 기준으로 동기화
        airflowRuns.forEach { airflowRun ->
            val result = syncAirflowRun(airflowRun, cluster)
            when (result) {
                SyncAction.UPDATED -> updatedCount++
                SyncAction.CREATED -> createdCount++
                SyncAction.SKIPPED -> {}
            }
        }

        // 4. 우리 DB에만 있고 Airflow에서 조회되지 않은 Run 처리 (stale 처리)
        val airflowDagRunIds = airflowRuns.map { it.dagRunId }.toSet()
        pendingRuns
            .filter { it.airflowDagRunId != null && it.airflowDagRunId !in airflowDagRunIds }
            .forEach { run ->
                // Airflow에서 개별 조회 시도
                if (syncSingleRun(run)) {
                    updatedCount++
                }
            }

        return ClusterSyncProjection.success(
            clusterId = cluster.id!!,
            clusterName = cluster.team,
            updatedCount = updatedCount,
            createdCount = createdCount,
        )
    }

    private fun syncAirflowRun(
        airflowRun: AirflowDagRunResponse,
        cluster: AirflowClusterEntity,
    ): SyncAction {
        // 기존 Run 조회
        val existingRun = workflowRunRepositoryJpa.findByAirflowDagRunId(airflowRun.dagRunId)

        return if (existingRun != null) {
            // 기존 Run 업데이트
            updateRunFromAirflow(existingRun, airflowRun, cluster)
            SyncAction.UPDATED
        } else {
            // 새로운 Run은 생성하지 않음 (우리가 트리거하지 않은 Run)
            // 필요시 여기서 새 Run 생성 로직 추가 가능
            SyncAction.SKIPPED
        }
    }

    private fun updateRunFromAirflow(
        run: WorkflowRunEntity,
        airflowRun: AirflowDagRunResponse,
        cluster: AirflowClusterEntity,
    ) {
        // Task 진행 상황 조회
        val taskProgress = fetchTaskProgress(airflowRun.dagId, airflowRun.dagRunId)

        // Run 업데이트
        run.updateFromAirflow(
            airflowState = airflowRun.state.name,
            airflowUrl = buildAirflowUrl(cluster, airflowRun),
            taskProgress = serializeTaskProgress(taskProgress),
            startedAt = airflowRun.startDate,
            endedAt = airflowRun.endDate,
        )

        workflowRunRepositoryJpa.save(run)
        logger.debug("Updated run {} with Airflow state {}", run.runId, airflowRun.state)
    }

    private fun syncSingleRun(run: WorkflowRunEntity): Boolean {
        if (run.airflowDagRunId == null || run.workflowId.isBlank()) {
            return false
        }

        return try {
            val airflowRun = airflowClient.getDAGRun(run.workflowId, run.airflowDagRunId!!)

            // 클러스터 정보 조회
            val cluster =
                run.airflowClusterId?.let { clusterRepository.findById(it) }
                    ?: return false

            // Task 진행 상황 조회
            val taskProgress = fetchTaskProgress(run.workflowId, run.airflowDagRunId!!)

            // Run 업데이트
            run.updateFromAirflow(
                airflowState = airflowRun.state.name,
                airflowUrl = run.airflowUrl, // 기존 URL 유지
                taskProgress = serializeTaskProgress(taskProgress),
                startedAt = airflowRun.startDate,
                endedAt = airflowRun.endDate,
            )

            workflowRunRepositoryJpa.save(run)
            logger.debug("Synced single run {} with state {}", run.runId, airflowRun.state)
            true
        } catch (e: Exception) {
            logger.warn("Failed to sync single run {}: {}", run.runId, e.message)
            false
        }
    }

    private fun fetchTaskProgress(
        dagId: String,
        dagRunId: String,
    ): TaskProgressProjection =
        try {
            val taskInstances = airflowClient.getTaskInstances(dagId, dagRunId)
            val taskStates =
                taskInstances.associate {
                    it.taskId to it.state.name
                }
            TaskProgressProjection.fromTaskStates(taskStates)
        } catch (e: Exception) {
            logger.debug("Failed to fetch task progress for {}/{}: {}", dagId, dagRunId, e.message)
            TaskProgressProjection.empty()
        }

    private fun buildAirflowUrl(
        cluster: AirflowClusterEntity,
        airflowRun: AirflowDagRunResponse,
    ): String = "${cluster.airflowUrl}/dags/${airflowRun.dagId}/grid?dag_run_id=${airflowRun.dagRunId}"

    private fun serializeTaskProgress(taskProgress: TaskProgressProjection): String? =
        try {
            objectMapper.writeValueAsString(taskProgress)
        } catch (e: Exception) {
            logger.warn("Failed to serialize task progress: {}", e.message)
            null
        }

    private enum class SyncAction {
        UPDATED,
        CREATED,
        SKIPPED,
    }
}
