package com.dataops.basecamp.domain.projection.workflow

import com.dataops.basecamp.common.enums.SpecSyncErrorType
import java.time.Instant

/**
 * Run Sync Result (Phase 5)
 *
 * Airflow Run 동기화 결과를 나타내는 데이터 클래스
 */
data class RunSyncProjection(
    /** 동기화된 클러스터 수 */
    val totalClusters: Int,
    /** 클러스터별 동기화 결과 */
    val clusterResults: List<ClusterSyncProjection>,
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
        fun empty(): RunSyncProjection =
            RunSyncProjection(
                totalClusters = 0,
                clusterResults = emptyList(),
                syncedAt = Instant.now(),
            )

        /**
         * 단일 클러스터 동기화 결과 생성
         */
        fun single(result: ClusterSyncProjection): RunSyncProjection =
            RunSyncProjection(
                totalClusters = 1,
                clusterResults = listOf(result),
                syncedAt = Instant.now(),
            )
    }
}

/**
 * Cluster Sync Projection
 *
 * 개별 Airflow 클러스터의 동기화 결과
 */
data class ClusterSyncProjection(
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
        ): ClusterSyncProjection =
            ClusterSyncProjection(
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
        ): ClusterSyncProjection =
            ClusterSyncProjection(
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
data class TaskProgressProjection(
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
        fun empty(): TaskProgressProjection =
            TaskProgressProjection(
                total = 0,
                completed = 0,
                failed = 0,
                running = 0,
            )

        /**
         * Airflow Task Instance 목록으로부터 진행 상황 생성
         */
        fun fromTaskStates(taskStates: Map<String, String>): TaskProgressProjection {
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

            return TaskProgressProjection(
                total = total,
                completed = completed,
                failed = failed,
                running = running,
            )
        }
    }
}

/**
 * S3에서 Workflow Spec을 동기화한 결과
 */
data class SpecSyncProjection(
    val totalProcessed: Int,
    val created: Int,
    val updated: Int,
    val failed: Int,
    val errors: List<SpecSyncErrorProjection>,
    val syncedAt: Instant,
) {
    /**
     * 동기화 성공 여부 (에러가 없으면 성공)
     */
    fun isSuccess(): Boolean = errors.isEmpty()

    /**
     * 동기화 결과 요약 문자열
     */
    fun summary(): String =
        buildString {
            append("SpecSync completed at $syncedAt: ")
            append("processed=$totalProcessed, created=$created, updated=$updated, failed=$failed")
            if (errors.isNotEmpty()) {
                append(", errors=${errors.size}")
            }
        }

    companion object {
        /**
         * 빈 결과 생성 (동기화할 Spec이 없는 경우)
         */
        fun empty(syncedAt: Instant = Instant.now()): SpecSyncProjection =
            SpecSyncProjection(
                totalProcessed = 0,
                created = 0,
                updated = 0,
                failed = 0,
                errors = emptyList(),
                syncedAt = syncedAt,
            )
    }
}

/**
 * Spec 동기화 에러 정보
 */
data class SpecSyncErrorProjection(
    val specPath: String,
    val message: String,
    val errorType: SpecSyncErrorType = SpecSyncErrorType.UNKNOWN,
) {
    override fun toString(): String = "[$errorType] $specPath: $message"
}
