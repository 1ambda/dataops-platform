package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * 파이프라인 Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface PipelineRepositoryDsl {
    // 복합 조회 조건 (Page 반환)
    fun searchPipelinesWithComplexConditions(
        owner: String? = null,
        status: PipelineStatus? = null,
        isActive: Boolean? = true,
        pageable: Pageable,
    ): Page<PipelineEntity>

    // 통계 및 집계 (복잡한 쿼리)
    fun getPipelineStatisticsWithJobCounts(owner: String? = null): Map<String, Any>

    fun getPipelineCountByOwner(): List<Map<String, Any>>

    // 추가 조회 메서드
    fun findRecentlyActivePipelines(
        limit: Int,
        daysSince: Int,
    ): List<PipelineEntity>

    fun findPipelinesBySchedulePattern(
        schedulePattern: String,
        statusList: List<PipelineStatus>,
        isActive: Boolean,
    ): List<PipelineEntity>
}
