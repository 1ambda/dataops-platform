package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.TestStatus
import com.github.lambda.domain.repository.QualityRunRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Quality Run JPA Repository Implementation Interface
 *
 * Implements the domain QualityRunRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern (same as MetricRepositoryJpaImpl).
 */
@Repository("qualityRunRepositoryJpa")
interface QualityRunRepositoryJpaImpl :
    QualityRunRepositoryJpa,
    JpaRepository<QualityRunEntity, String> {
    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByRunId(runId: String): QualityRunEntity?

    override fun existsByRunId(runId: String): Boolean

    override fun deleteByRunId(runId: String): Long

    // Spec별 실행 이력 조회
    @Query("SELECT qr FROM QualityRunEntity qr WHERE qr.spec.name = :specName")
    override fun findBySpecName(
        @Param("specName") specName: String,
    ): List<QualityRunEntity>

    @Query("SELECT qr FROM QualityRunEntity qr WHERE qr.spec.name = :specName ORDER BY qr.startedAt DESC")
    override fun findBySpecNameOrderByStartedAtDesc(
        @Param("specName") specName: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 리소스별 실행 이력 조회
    override fun findByResourceName(resourceName: String): List<QualityRunEntity>

    override fun findByResourceNameOrderByStartedAtDesc(
        resourceName: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행자별 조회
    override fun findByExecutedBy(executedBy: String): List<QualityRunEntity>

    override fun findByExecutedByOrderByStartedAtDesc(
        executedBy: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 상태별 조회
    override fun findByStatus(status: RunStatus): List<QualityRunEntity>

    override fun findByStatusOrderByStartedAtDesc(
        status: RunStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    override fun findByOverallStatus(overallStatus: TestStatus): List<QualityRunEntity>

    override fun findByOverallStatusOrderByStartedAtDesc(
        overallStatus: TestStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 시간 범위별 조회
    override fun findByStartedAtBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<QualityRunEntity>

    override fun findByStartedAtBetweenOrderByStartedAtDesc(
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    override fun findByStartedAtAfter(startTime: Instant): List<QualityRunEntity>

    override fun findByStartedAtBefore(endTime: Instant): List<QualityRunEntity>

    // 전체 목록 조회
    override fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<QualityRunEntity>

    // 통계 및 집계
    override fun countByStatus(status: RunStatus): Long

    override fun countByOverallStatus(overallStatus: TestStatus): Long

    override fun countByExecutedBy(executedBy: String): Long

    @Query("SELECT COUNT(qr) FROM QualityRunEntity qr WHERE qr.spec.name = :specName")
    override fun countBySpecName(
        @Param("specName") specName: String,
    ): Long

    override fun countByResourceName(resourceName: String): Long

    // 실행 중인 작업 조회
    override fun findByStatusAndStartedAtBefore(
        status: RunStatus,
        threshold: Instant,
    ): List<QualityRunEntity>

    // 최근 완료된 실행 조회
    override fun findByStatusInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
        statuses: List<RunStatus>,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행 시간 기반 조회
    override fun findByDurationSecondsGreaterThan(durationSeconds: Double): List<QualityRunEntity>

    override fun findByDurationSecondsBetween(
        minDuration: Double,
        maxDuration: Double,
    ): List<QualityRunEntity>

    // 커스텀 업데이트 쿼리
    @Modifying
    @Query("UPDATE QualityRunEntity qr SET qr.status = :status, qr.completedAt = :completedAt WHERE qr.runId = :runId")
    override fun updateStatusByRunId(
        @Param("runId") runId: String,
        @Param("status") status: RunStatus,
        @Param("completedAt") completedAt: Instant,
    ): Int

    // 복잡한 검색 쿼리
    @Query(
        """
        SELECT qr FROM QualityRunEntity qr
        WHERE (:resourceName IS NULL OR qr.resourceName = :resourceName)
        AND (:status IS NULL OR qr.status = :status)
        AND (:overallStatus IS NULL OR qr.overallStatus = :overallStatus)
        AND (:executedBy IS NULL OR LOWER(qr.executedBy) LIKE LOWER(CONCAT('%', :executedBy, '%')))
        AND (:startTime IS NULL OR qr.startedAt >= :startTime)
        AND (:endTime IS NULL OR qr.startedAt <= :endTime)
        ORDER BY qr.startedAt DESC
        """,
    )
    override fun findByComplexFilters(
        @Param("resourceName") resourceName: String?,
        @Param("status") status: RunStatus?,
        @Param("overallStatus") overallStatus: TestStatus?,
        @Param("executedBy") executedBy: String?,
        @Param("startTime") startTime: Instant?,
        @Param("endTime") endTime: Instant?,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    @Query(
        """
        SELECT COUNT(qr) FROM QualityRunEntity qr
        WHERE (:resourceName IS NULL OR qr.resourceName = :resourceName)
        AND (:status IS NULL OR qr.status = :status)
        AND (:overallStatus IS NULL OR qr.overallStatus = :overallStatus)
        AND (:executedBy IS NULL OR LOWER(qr.executedBy) LIKE LOWER(CONCAT('%', :executedBy, '%')))
        AND (:startTime IS NULL OR qr.startedAt >= :startTime)
        AND (:endTime IS NULL OR qr.startedAt <= :endTime)
        """,
    )
    override fun countByComplexFilters(
        @Param("resourceName") resourceName: String?,
        @Param("status") status: RunStatus?,
        @Param("overallStatus") overallStatus: TestStatus?,
        @Param("executedBy") executedBy: String?,
        @Param("startTime") startTime: Instant?,
        @Param("endTime") endTime: Instant?,
    ): Long

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
        @Param("threshold") threshold: Instant,
    ): List<QualityRunEntity>

    // 실행 통계 조회
    @Query(
        """
        SELECT
            qr.status as status,
            COUNT(*) as count,
            AVG(qr.durationSeconds) as avgDuration
        FROM QualityRunEntity qr
        WHERE qr.startedAt >= :since
        GROUP BY qr.status
        """,
    )
    override fun getRunStatistics(
        @Param("since") since: Instant,
    ): List<Map<String, Any>>
}
