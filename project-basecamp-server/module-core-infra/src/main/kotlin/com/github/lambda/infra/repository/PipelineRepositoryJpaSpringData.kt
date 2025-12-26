package com.github.lambda.infra.repository

import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 파이프라인 Spring Data JPA Repository 인터페이스
 *
 * Spring Data JPA 자동 구현을 위한 인터페이스
 */
interface PipelineRepositoryJpaSpringData : JpaRepository<PipelineEntity, Long> {
    // 활성상태별 조회
    fun findByIdAndIsActiveTrue(id: Long): PipelineEntity?

    fun findAllByIsActive(isActive: Boolean): List<PipelineEntity>

    // 소유자별 조회
    fun findByOwner(owner: String): List<PipelineEntity>

    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    fun findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    // 상태별 조회
    fun findByStatus(status: PipelineStatus): List<PipelineEntity>

    fun findByStatusOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    fun findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    // 상태 업데이트 (커스텀 쿼리)
    @Modifying
    @Query("UPDATE PipelineEntity p SET p.status = :status, p.updatedAt = :updatedAt WHERE p.id = :id")
    fun updateStatusById(
        @Param("id") id: Long,
        @Param("status") status: PipelineStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query("UPDATE PipelineEntity p SET p.isActive = :isActive, p.updatedAt = :updatedAt WHERE p.id = :id")
    fun updateIsActiveById(
        @Param("id") id: Long,
        @Param("isActive") isActive: Boolean,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    // 통계 및 집계
    fun countByStatus(status: PipelineStatus): Long

    fun countByOwner(owner: String): Long
}
