package com.dataops.basecamp.domain.repository.adhoc

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.domain.entity.adhoc.AdHocExecutionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Ad-Hoc Execution Repository DSL 인터페이스 (복잡한 쿼리용)
 *
 * QueryDSL을 사용한 복잡한 쿼리를 정의합니다.
 */
interface AdHocExecutionRepositoryDsl {
    /**
     * 복합 필터로 실행 기록 조회
     */
    fun findByFilters(
        userId: String? = null,
        status: ExecutionStatus? = null,
        engine: String? = null,
        search: String? = null,
        fromDate: LocalDateTime? = null,
        toDate: LocalDateTime? = null,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity>

    /**
     * 사용자별 실행 통계 조회
     */
    fun getExecutionStatistics(userId: String? = null): Map<String, Any>

    /**
     * 일정 기간 내 사용자별 실행 횟수 조회
     */
    fun countExecutionsByUserIdSince(
        userId: String,
        since: LocalDateTime,
    ): Long

    /**
     * 최근 N일간 실행된 쿼리 조회
     */
    fun findRecentExecutions(
        limit: Int,
        daysSince: Int,
    ): List<AdHocExecutionEntity>

    /**
     * 엔진별 실행 통계
     */
    fun getEngineStatistics(userId: String? = null): Map<String, Long>

    /**
     * 만료된 결과 정리 (bulk delete)
     */
    fun deleteExpiredResults(before: LocalDateTime): Long
}
