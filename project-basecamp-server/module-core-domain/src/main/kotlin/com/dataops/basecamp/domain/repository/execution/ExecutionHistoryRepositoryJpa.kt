package com.dataops.basecamp.domain.repository.execution

import com.dataops.basecamp.domain.entity.execution.ExecutionHistoryEntity

/**
 * Execution History Repository JPA Interface
 *
 * 실행 이력에 대한 기본 CRUD 작업을 정의합니다.
 */
interface ExecutionHistoryRepositoryJpa {
    /**
     * 실행 이력 저장
     */
    fun save(entity: ExecutionHistoryEntity): ExecutionHistoryEntity

    /**
     * ID로 실행 이력 조회 (null-safe)
     */
    fun findByIdOrNull(id: Long): ExecutionHistoryEntity?

    /**
     * Execution ID로 실행 이력 조회
     */
    fun findByExecutionId(executionId: String): ExecutionHistoryEntity?

    /**
     * Execution ID로 존재 여부 확인
     */
    fun existsByExecutionId(executionId: String): Boolean

    /**
     * ID로 존재 여부 확인
     */
    fun existsById(id: Long): Boolean

    /**
     * ID로 삭제
     */
    fun deleteById(id: Long)
}
