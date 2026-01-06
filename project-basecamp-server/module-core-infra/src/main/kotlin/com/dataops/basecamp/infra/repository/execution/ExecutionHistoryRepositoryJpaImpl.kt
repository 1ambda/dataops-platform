package com.dataops.basecamp.infra.repository.execution

import com.dataops.basecamp.domain.entity.execution.ExecutionHistoryEntity
import com.dataops.basecamp.domain.repository.execution.ExecutionHistoryRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Execution History Repository JPA Implementation
 *
 * Spring Data JPA를 사용하여 ExecutionHistoryRepositoryJpa 인터페이스를 구현합니다.
 * 인터페이스가 JpaRepository를 확장하여 자동으로 구현됩니다.
 */
@Repository("executionHistoryRepositoryJpa")
interface ExecutionHistoryRepositoryJpaImpl :
    ExecutionHistoryRepositoryJpa,
    JpaRepository<ExecutionHistoryEntity, Long> {
    /**
     * ID로 조회 (null-safe)
     */
    @Query("SELECT e FROM ExecutionHistoryEntity e WHERE e.id = :id")
    override fun findByIdOrNull(
        @Param("id") id: Long,
    ): ExecutionHistoryEntity?

    /**
     * Execution ID로 조회
     */
    override fun findByExecutionId(executionId: String): ExecutionHistoryEntity?

    /**
     * Execution ID로 존재 여부 확인
     */
    override fun existsByExecutionId(executionId: String): Boolean

    /**
     * ID로 존재 여부 확인
     */
    override fun existsById(id: Long): Boolean
}
