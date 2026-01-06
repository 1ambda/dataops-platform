package com.dataops.basecamp.infra.repository.execution

import com.dataops.basecamp.domain.entity.execution.ExecutionResultEntity
import com.dataops.basecamp.domain.repository.execution.ExecutionResultRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Execution Result Repository JPA Implementation
 *
 * Spring Data JPA를 사용하여 ExecutionResultRepositoryJpa 인터페이스를 구현합니다.
 * 인터페이스가 JpaRepository를 확장하여 자동으로 구현됩니다.
 */
@Repository("executionResultRepositoryJpa")
interface ExecutionResultRepositoryJpaImpl :
    ExecutionResultRepositoryJpa,
    JpaRepository<ExecutionResultEntity, Long> {
    /**
     * ID로 조회 (null-safe)
     */
    @Query("SELECT e FROM ExecutionResultEntity e WHERE e.id = :id")
    override fun findByIdOrNull(
        @Param("id") id: Long,
    ): ExecutionResultEntity?

    /**
     * Execution ID로 조회
     */
    override fun findByExecutionId(executionId: String): ExecutionResultEntity?

    /**
     * Execution ID로 존재 여부 확인
     */
    override fun existsByExecutionId(executionId: String): Boolean

    /**
     * ID로 존재 여부 확인
     */
    override fun existsById(id: Long): Boolean

    /**
     * Execution ID로 삭제
     */
    @Modifying
    @Query("DELETE FROM ExecutionResultEntity e WHERE e.executionId = :executionId")
    override fun deleteByExecutionId(
        @Param("executionId") executionId: String,
    )
}
