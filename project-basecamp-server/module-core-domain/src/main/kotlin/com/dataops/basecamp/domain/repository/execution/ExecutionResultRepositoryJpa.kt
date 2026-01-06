package com.dataops.basecamp.domain.repository.execution

import com.dataops.basecamp.domain.entity.execution.ExecutionResultEntity

/**
 * Execution Result Repository JPA Interface
 *
 * 실행 결과에 대한 기본 CRUD 작업을 정의합니다.
 */
interface ExecutionResultRepositoryJpa {
    /**
     * 실행 결과 저장
     */
    fun save(entity: ExecutionResultEntity): ExecutionResultEntity

    /**
     * ID로 실행 결과 조회 (null-safe)
     */
    fun findByIdOrNull(id: Long): ExecutionResultEntity?

    /**
     * Execution ID로 실행 결과 조회
     */
    fun findByExecutionId(executionId: String): ExecutionResultEntity?

    /**
     * Execution ID로 존재 여부 확인
     */
    fun existsByExecutionId(executionId: String): Boolean

    /**
     * ID로 존재 여부 확인
     */
    fun existsById(id: Long): Boolean

    /**
     * Execution ID로 삭제
     */
    fun deleteByExecutionId(executionId: String)

    /**
     * ID로 삭제
     */
    fun deleteById(id: Long)
}
