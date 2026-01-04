package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.repository.PipelineRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 파이프라인 JPA Repository 구현 인터페이스
 *
 * Domain PipelineRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("pipelineRepositoryJpa")
interface PipelineRepositoryJpaImpl :
    PipelineRepositoryJpa,
    JpaRepository<PipelineEntity, Long> {
    // 도메인 특화 조회 메서드 (Spring Data JPA auto-implements)
    override fun findByIdAndIsActiveTrue(id: Long): PipelineEntity?

    override fun findAllByIsActive(isActive: Boolean): List<PipelineEntity>

    override fun findByOwner(owner: String): List<PipelineEntity>

    override fun findByStatus(status: PipelineStatus): List<PipelineEntity>

    // 페이지네이션 조회
    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    override fun findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    override fun findByStatusOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    override fun findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    // 상태 업데이트 (커스텀 쿼리) - 원시 메소드만 제공, 비즈니스 로직은 service layer에서
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
    override fun countByStatus(status: PipelineStatus): Long

    override fun countByOwner(owner: String): Long
}
