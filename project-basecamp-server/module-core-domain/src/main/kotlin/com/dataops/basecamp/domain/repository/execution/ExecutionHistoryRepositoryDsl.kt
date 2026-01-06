package com.dataops.basecamp.domain.repository.execution

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.enums.ExecutionType
import com.dataops.basecamp.domain.entity.execution.ExecutionHistoryEntity
import java.time.LocalDateTime

/**
 * Execution History Repository DSL Interface
 *
 * 복잡한 쿼리 및 필터링 작업을 정의합니다.
 */
interface ExecutionHistoryRepositoryDsl {
    /**
     * 사용자 ID로 실행 이력 조회 (최근순)
     */
    fun findByUserId(
        userId: Long,
        limit: Int = 50,
    ): List<ExecutionHistoryEntity>

    /**
     * 실행 타입과 상태로 조회
     */
    fun findByTypeAndStatus(
        type: ExecutionType,
        status: ExecutionStatus,
    ): List<ExecutionHistoryEntity>

    /**
     * 리소스 이름으로 조회 (최근순)
     */
    fun findByResourceName(
        resourceName: String,
        limit: Int = 50,
    ): List<ExecutionHistoryEntity>

    /**
     * 시간 범위로 조회
     */
    fun findByTimeRange(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<ExecutionHistoryEntity>
}
