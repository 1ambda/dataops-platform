package com.dataops.basecamp.infra.repository.quality

import com.dataops.basecamp.common.enums.WorkflowRunStatus
import com.dataops.basecamp.domain.entity.quality.QualityRunEntity
import com.dataops.basecamp.domain.repository.quality.QualityRunRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Quality Run JPA Repository Implementation
 *
 * Implements the domain QualityRunRepositoryJpa interface by extending JpaRepository directly.
 *
 * v2.0 Enhancement:
 * - Uses WorkflowRunStatus instead of deprecated RunStatus
 * - Uses LocalDateTime instead of Instant
 * - Updated field names to match v2.0 entity (specName, targetResource, triggeredBy)
 */
@Repository("qualityRunRepositoryJpa")
interface QualityRunRepositoryJpaImpl :
    QualityRunRepositoryJpa,
    JpaRepository<QualityRunEntity, Long> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByRunId(runId: String): QualityRunEntity?

    override fun existsByRunId(runId: String): Boolean

    override fun deleteByRunId(runId: String): Long

    // Spec별 실행 이력 조회 (specName is now a direct field)
    override fun findBySpecName(specName: String): List<QualityRunEntity>

    override fun findBySpecNameOrderByStartedAtDesc(
        specName: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 리소스별 실행 이력 조회
    override fun findByTargetResource(targetResource: String): List<QualityRunEntity>

    override fun findByTargetResourceOrderByStartedAtDesc(
        targetResource: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행자별 조회
    override fun findByTriggeredBy(triggeredBy: String): List<QualityRunEntity>

    override fun findByTriggeredByOrderByStartedAtDesc(
        triggeredBy: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 상태별 조회
    override fun findByStatus(status: WorkflowRunStatus): List<QualityRunEntity>

    override fun findByStatusOrderByStartedAtDesc(
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 시간 범위별 조회
    override fun findByStartedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<QualityRunEntity>

    override fun findByStartedAtBetweenOrderByStartedAtDesc(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    override fun findByStartedAtAfter(startTime: LocalDateTime): List<QualityRunEntity>

    override fun findByStartedAtBefore(endTime: LocalDateTime): List<QualityRunEntity>

    // 전체 목록 조회
    override fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<QualityRunEntity>

    // 통계 및 집계
    override fun countByStatus(status: WorkflowRunStatus): Long

    override fun countByTriggeredBy(triggeredBy: String): Long

    override fun countBySpecName(specName: String): Long

    override fun countByTargetResource(targetResource: String): Long

    // 실행 중인 작업 조회
    override fun findByStatusAndStartedAtBefore(
        status: WorkflowRunStatus,
        threshold: LocalDateTime,
    ): List<QualityRunEntity>

    // 최근 완료된 실행 조회
    override fun findByStatusInAndEndedAtIsNotNullOrderByEndedAtDesc(
        statuses: List<WorkflowRunStatus>,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 장시간 실행 중인 작업 조회
    @Query(
        """
        SELECT qr FROM QualityRunEntity qr
        WHERE qr.status = 'RUNNING'
        AND qr.startedAt < :threshold
        ORDER BY qr.startedAt ASC
        """,
    )
    override fun findLongRunningTasks(
        @Param("threshold") threshold: LocalDateTime,
    ): List<QualityRunEntity>

    // Airflow sync 대상 조회
    @Query(
        """
        SELECT qr FROM QualityRunEntity qr
        WHERE qr.status IN :statuses
        AND (qr.lastSyncedAt IS NULL OR qr.lastSyncedAt < :threshold)
        ORDER BY qr.lastSyncedAt ASC
        """,
    )
    override fun findByStatusInAndLastSyncedAtBefore(
        @Param("statuses") statuses: List<WorkflowRunStatus>,
        @Param("threshold") threshold: LocalDateTime,
    ): List<QualityRunEntity>

    // Quality Spec ID별 조회
    override fun findByQualitySpecId(qualitySpecId: Long): List<QualityRunEntity>

    override fun findByQualitySpecIdOrderByStartedAtDesc(
        qualitySpecId: Long,
        pageable: Pageable,
    ): Page<QualityRunEntity>
}
