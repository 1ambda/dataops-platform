package com.github.lambda.domain.model.pipeline

/**
 * 파이프라인 통계 도메인 객체
 * 헥사고날 아키텍처 원칙에 따라 도메인 레이어에서 정의된 값 객체입니다.
 */
data class PipelineStatistics(
    val totalPipelines: Long,
    val activePipelines: Long,
    val runningPipelines: Long,
    val pausedPipelines: Long,
    val failedPipelines: Long,
    val totalJobs: Long,
    val averageJobsPerPipeline: Double,
    val statusBreakdown: Map<PipelineStatus, Long>,
    val ownerBreakdown: List<OwnerStatistics>,
)

/**
 * 소유자별 통계 도메인 객체
 */
data class OwnerStatistics(
    val owner: String,
    val pipelineCount: Long,
    val activeCount: Long,
    val inactiveCount: Long,
)
