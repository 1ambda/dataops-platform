package com.github.lambda.domain.query.pipeline

import com.github.lambda.domain.model.pipeline.PipelineStatus
import org.springframework.data.domain.Pageable

/**
 * 파이프라인 조회 쿼리
 */
data class GetPipelineQuery(
    val id: Long,
    val includeJobs: Boolean = false,
    val includeDeleted: Boolean = false,
)

/**
 * 파이프라인 목록 조회 쿼리
 */
data class GetPipelinesQuery(
    val owner: String? = null,
    val status: PipelineStatus? = null,
    val isActive: Boolean? = null,
    val includeDeleted: Boolean = false,
    val pageable: Pageable,
)

/**
 * 소유자별 파이프라인 조회 쿼리
 */
data class GetPipelinesByOwnerQuery(
    val owner: String,
    val includeInactive: Boolean = false,
    val pageable: Pageable,
)

/**
 * 상태별 파이프라인 조회 쿼리
 */
data class GetPipelinesByStatusQuery(
    val status: PipelineStatus,
    val includeInactive: Boolean = false,
    val pageable: Pageable,
)

/**
 * 파이프라인 통계 조회 쿼리
 */
data class GetPipelineStatisticsQuery(
    val owner: String? = null,
    val includeJobCounts: Boolean = true,
    val includeStatusCounts: Boolean = true,
)

/**
 * 소유자별 파이프라인 수 조회 쿼리
 */
data class GetPipelineCountByOwnerQuery(
    val includeInactive: Boolean = false,
)
