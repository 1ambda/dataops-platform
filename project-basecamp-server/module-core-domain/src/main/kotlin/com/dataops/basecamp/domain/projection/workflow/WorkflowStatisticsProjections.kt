package com.dataops.basecamp.domain.projection.workflow

import com.dataops.basecamp.common.enums.WorkflowRunStatus
import com.dataops.basecamp.common.enums.WorkflowRunType

/**
 * Workflow Run 통계 정보 Projection
 * Repository DSL getRunStatistics 메소드의 리턴값으로 사용
 */
data class WorkflowRunStatisticsProjection(
    val totalRuns: Long,
    val totalRunsByStatus: Map<WorkflowRunStatus, Long>,
    val totalRunsByType: Map<WorkflowRunType, Long>,
    val totalRunsByTriggeredBy: Map<String, Long>,
    val totalRunsByDatasetName: Map<String, Long>,
    val averageDurationSeconds: Double?,
)

/**
 * 상태별 workflow run 개수 Projection
 * Repository DSL getRunCountByStatus 메소드의 리턴값으로 사용
 */
data class WorkflowRunCountByStatusProjection(
    val status: WorkflowRunStatus,
    val count: Long,
)

/**
 * 실행 타입별 workflow run 개수 Projection
 * Repository DSL getRunCountByRunType 메소드의 리턴값으로 사용
 */
data class WorkflowRunCountByTypeProjection(
    val runType: WorkflowRunType,
    val count: Long,
)

/**
 * 실행자별 workflow run 개수 Projection
 * Repository DSL getRunCountByTriggeredBy 메소드의 리턴값으로 사용
 */
data class WorkflowRunCountByTriggeredByProjection(
    val triggeredBy: String,
    val count: Long,
)

/**
 * 데이터셋별 workflow run 개수 Projection
 * Repository DSL getRunCountByDatasetName 메소드의 리턴값으로 사용
 */
data class WorkflowRunCountByDatasetProjection(
    val datasetName: String,
    val count: Long,
)

/**
 * 실행 시간 통계 Projection
 * Repository DSL getDurationStatistics 메소드의 리턴값으로 사용
 */
data class WorkflowDurationStatisticsProjection(
    val averageDurationSeconds: Double,
    val minDurationSeconds: Double,
    val maxDurationSeconds: Double,
    val medianDurationSeconds: Double?,
    val totalRuns: Long,
)

/**
 * 성공률 통계 Projection
 * Repository DSL getSuccessRateStatistics 메소드의 리턴값으로 사용
 */
data class WorkflowSuccessRateProjection(
    val totalRuns: Long,
    val successfulRuns: Long,
    val failedRuns: Long,
    val successRate: Double, // 0.0 to 1.0
)

/**
 * 일별 실행 통계 Projection
 * Repository DSL getDailyRunStatistics 메소드의 리턴값으로 사용
 */
data class WorkflowDailyRunStatisticsProjection(
    val date: String, // yyyy-MM-dd format
    val totalRuns: Long,
    val successfulRuns: Long,
    val failedRuns: Long,
    val averageDurationSeconds: Double?,
)

/**
 * 클러스터별 동기화 통계 Projection
 * Repository DSL getSyncStatistics 메소드의 리턴값으로 사용
 */
data class WorkflowSyncStatisticsProjection(
    val clusterId: Long?,
    val totalRuns: Long,
    val syncedRuns: Long,
    val pendingSyncRuns: Long,
    val staleRuns: Long,
    val lastSyncedAt: String?, // ISO timestamp
)
