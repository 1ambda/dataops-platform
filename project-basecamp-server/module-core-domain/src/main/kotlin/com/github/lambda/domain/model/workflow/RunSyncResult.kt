package com.github.lambda.domain.model.workflow

import java.time.Instant

/**
 * Run Sync Result (Phase 5)
 *
 * Airflow Run 동기화 결과를 나타내는 데이터 클래스
 */
data class RunSyncResult(
    /** 동기화된 클러스터 수 */
    val totalClusters: Int,
    /** 클러스터별 동기화 결과 */
    val clusterResults: List<ClusterSyncResult>,
    /** 동기화 시각 */
    val syncedAt: Instant,
) {
    /** 전체 업데이트된 실행 수 */
    val totalUpdated: Int
        get() = clusterResults.sumOf { it.updatedCount }

    /** 전체 생성된 실행 수 */
    val totalCreated: Int
        get() = clusterResults.sumOf { it.createdCount }

    /** 오류가 발생한 클러스터 수 */
    val failedClusters: Int
        get() = clusterResults.count { it.error != null }

    /** 전체 동기화 성공 여부 */
    val isSuccess: Boolean
        get() = failedClusters == 0

    companion object {
        /**
         * 빈 동기화 결과 생성
         */
        fun empty(): RunSyncResult =
            RunSyncResult(
                totalClusters = 0,
                clusterResults = emptyList(),
                syncedAt = Instant.now(),
            )

        /**
         * 단일 클러스터 동기화 결과 생성
         */
        fun single(result: ClusterSyncResult): RunSyncResult =
            RunSyncResult(
                totalClusters = 1,
                clusterResults = listOf(result),
                syncedAt = Instant.now(),
            )
    }
}

/**
 * Cluster Sync Result
 *
 * 개별 Airflow 클러스터의 동기화 결과
 */
data class ClusterSyncResult(
    /** 클러스터 ID */
    val clusterId: Long,
    /** 클러스터 이름 */
    val clusterName: String,
    /** 업데이트된 실행 수 */
    val updatedCount: Int,
    /** 새로 생성된 실행 수 */
    val createdCount: Int,
    /** 동기화 중 발생한 오류 (null이면 성공) */
    val error: String? = null,
) {
    /** 동기화 성공 여부 */
    val isSuccess: Boolean
        get() = error == null

    /** 총 처리된 실행 수 */
    val totalProcessed: Int
        get() = updatedCount + createdCount

    companion object {
        /**
         * 성공 결과 생성
         */
        fun success(
            clusterId: Long,
            clusterName: String,
            updatedCount: Int,
            createdCount: Int,
        ): ClusterSyncResult =
            ClusterSyncResult(
                clusterId = clusterId,
                clusterName = clusterName,
                updatedCount = updatedCount,
                createdCount = createdCount,
                error = null,
            )

        /**
         * 실패 결과 생성
         */
        fun failure(
            clusterId: Long,
            clusterName: String,
            error: String,
        ): ClusterSyncResult =
            ClusterSyncResult(
                clusterId = clusterId,
                clusterName = clusterName,
                updatedCount = 0,
                createdCount = 0,
                error = error,
            )
    }
}

/**
 * Task Progress
 *
 * Airflow DAG Run의 Task 진행 상황
 */
data class TaskProgress(
    /** 전체 Task 수 */
    val total: Int,
    /** 완료된 Task 수 */
    val completed: Int,
    /** 실패한 Task 수 */
    val failed: Int,
    /** 실행 중인 Task 수 */
    val running: Int,
    /** 대기 중인 Task 수 */
    val pending: Int = total - completed - failed - running,
) {
    /** 완료율 (0-100) */
    val completionPercentage: Int
        get() = if (total > 0) (completed * 100) / total else 0

    /** 전체 완료 여부 */
    val isComplete: Boolean
        get() = completed + failed == total

    /** 실패 존재 여부 */
    val hasFailed: Boolean
        get() = failed > 0

    companion object {
        /**
         * 빈 진행 상황 생성
         */
        fun empty(): TaskProgress =
            TaskProgress(
                total = 0,
                completed = 0,
                failed = 0,
                running = 0,
            )

        /**
         * Airflow Task Instance 목록으로부터 진행 상황 생성
         */
        fun fromTaskStates(taskStates: Map<String, String>): TaskProgress {
            val total = taskStates.size
            var completed = 0
            var failed = 0
            var running = 0

            taskStates.values.forEach { state ->
                when (state.uppercase()) {
                    "SUCCESS" -> completed++
                    "FAILED", "UPSTREAM_FAILED" -> failed++
                    "RUNNING" -> running++
                }
            }

            return TaskProgress(
                total = total,
                completed = completed,
                failed = failed,
                running = running,
            )
        }
    }
}
