package com.github.lambda.domain.repository

import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.TestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

/**
 * Quality Run Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * QualityRun에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface QualityRunRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 충돌하지 않는 메서드들)
    fun save(qualityRun: QualityRunEntity): QualityRunEntity

    // 도메인 특화 조회 메서드 - runId 기반
    fun findByRunId(runId: String): QualityRunEntity?

    fun existsByRunId(runId: String): Boolean

    fun deleteByRunId(runId: String): Long

    // Spec별 실행 이력 조회
    fun findBySpecName(specName: String): List<QualityRunEntity>

    fun findBySpecNameOrderByStartedAtDesc(
        specName: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 리소스별 실행 이력 조회
    fun findByResourceName(resourceName: String): List<QualityRunEntity>

    fun findByResourceNameOrderByStartedAtDesc(
        resourceName: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행자별 조회
    fun findByExecutedBy(executedBy: String): List<QualityRunEntity>

    fun findByExecutedByOrderByStartedAtDesc(
        executedBy: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 상태별 조회
    fun findByStatus(status: RunStatus): List<QualityRunEntity>

    fun findByStatusOrderByStartedAtDesc(
        status: RunStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    fun findByOverallStatus(overallStatus: TestStatus): List<QualityRunEntity>

    fun findByOverallStatusOrderByStartedAtDesc(
        overallStatus: TestStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 시간 범위별 조회
    fun findByStartedAtBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<QualityRunEntity>

    fun findByStartedAtBetweenOrderByStartedAtDesc(
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    fun findByStartedAtAfter(startTime: Instant): List<QualityRunEntity>

    fun findByStartedAtBefore(endTime: Instant): List<QualityRunEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<QualityRunEntity>

    // 통계 및 집계
    fun countByStatus(status: RunStatus): Long

    fun countByOverallStatus(overallStatus: TestStatus): Long

    fun countByExecutedBy(executedBy: String): Long

    fun countBySpecName(specName: String): Long

    fun countByResourceName(resourceName: String): Long

    fun count(): Long

    // 실행 중인 작업 조회
    fun findByStatusAndStartedAtBefore(
        status: RunStatus,
        threshold: Instant,
    ): List<QualityRunEntity>

    // 최근 완료된 실행 조회
    fun findByStatusInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
        statuses: List<RunStatus>,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행 시간 기반 조회
    fun findByDurationSecondsGreaterThan(durationSeconds: Double): List<QualityRunEntity>

    fun findByDurationSecondsBetween(
        minDuration: Double,
        maxDuration: Double,
    ): List<QualityRunEntity>

    // 커스텀 업데이트 쿼리
    fun updateStatusByRunId(
        runId: String,
        status: RunStatus,
        completedAt: Instant,
    ): Int

    // 복잡한 검색 쿼리
    fun findByComplexFilters(
        resourceName: String?,
        status: RunStatus?,
        overallStatus: TestStatus?,
        executedBy: String?,
        startTime: Instant?,
        endTime: Instant?,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    fun countByComplexFilters(
        resourceName: String?,
        status: RunStatus?,
        overallStatus: TestStatus?,
        executedBy: String?,
        startTime: Instant?,
        endTime: Instant?,
    ): Long

    // 장시간 실행 중인 작업 조회
    fun findLongRunningTasks(threshold: Instant): List<QualityRunEntity>

    // 실행 통계 조회
    fun getRunStatistics(since: Instant): List<Map<String, Any>>
}
