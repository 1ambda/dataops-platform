package com.github.lambda.dto

import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import java.time.LocalDateTime

/**
 * 파이프라인 API DTO
 * API 레이어에서 도메인과 프레젠테이션 계층 간의 데이터 변환을 위한 DTO입니다.
 * 헥사고날 아키텍처 원칙에 따라 도메인 엔터티에서 API 응답 DTO로의 중간 변환 객체입니다.
 */
data class PipelineDto(
    val id: Long?,
    val name: String,
    val description: String?,
    val status: PipelineStatus,
    val owner: String,
    val scheduleExpression: String?,
    val isActive: Boolean,
    val jobCount: Int = 0,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        /**
         * Create PipelineDto from entity.
         * Note: jobCount must be provided separately since jobs relationship was removed.
         */
        fun from(
            pipeline: PipelineEntity,
            jobCount: Int = 0,
        ): PipelineDto =
            PipelineDto(
                id = pipeline.id,
                name = pipeline.name,
                description = pipeline.description,
                status = pipeline.status,
                owner = pipeline.owner,
                scheduleExpression = pipeline.scheduleExpression,
                isActive = pipeline.isActive,
                jobCount = jobCount,
                createdAt = pipeline.createdAt,
                updatedAt = pipeline.updatedAt,
            )
    }
}

/**
 * 파이프라인 실행 결과 DTO
 */
data class PipelineExecutionDto(
    val executionId: String,
    val pipelineId: Long,
    val pipelineName: String,
    val status: String,
    val startedAt: LocalDateTime,
    val message: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
)

/**
 * 파이프라인 통계 DTO
 */
data class PipelineStatisticsDto(
    val totalPipelines: Long,
    val activePipelines: Long,
    val runningPipelines: Long,
    val pausedPipelines: Long,
    val failedPipelines: Long,
    val totalJobs: Long,
    val averageJobsPerPipeline: Double,
    val statusBreakdown: Map<PipelineStatus, Long>,
    val ownerBreakdown: List<OwnerStatDto>,
)

/**
 * 소유자별 통계 DTO
 */
data class OwnerStatDto(
    val owner: String,
    val pipelineCount: Long,
    val activeCount: Long,
    val inactiveCount: Long,
)
