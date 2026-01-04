package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.quality.QualityRunEntity
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Quality Run Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * QualityRun에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 *
 * v2.0 Enhancement:
 * - Uses WorkflowRunStatus instead of deprecated RunStatus
 * - Uses LocalDateTime instead of Instant
 * - Updated field names to match v2.0 entity
 */
interface QualityRunRepositoryJpa {
    // 기본 CRUD 작업
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
    fun findByTargetResource(targetResource: String): List<QualityRunEntity>

    fun findByTargetResourceOrderByStartedAtDesc(
        targetResource: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 실행자별 조회
    fun findByTriggeredBy(triggeredBy: String): List<QualityRunEntity>

    fun findByTriggeredByOrderByStartedAtDesc(
        triggeredBy: String,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 상태별 조회
    fun findByStatus(status: WorkflowRunStatus): List<QualityRunEntity>

    fun findByStatusOrderByStartedAtDesc(
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 시간 범위별 조회
    fun findByStartedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<QualityRunEntity>

    fun findByStartedAtBetweenOrderByStartedAtDesc(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    fun findByStartedAtAfter(startTime: LocalDateTime): List<QualityRunEntity>

    fun findByStartedAtBefore(endTime: LocalDateTime): List<QualityRunEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<QualityRunEntity>

    // 통계 및 집계
    fun countByStatus(status: WorkflowRunStatus): Long

    fun countByTriggeredBy(triggeredBy: String): Long

    fun countBySpecName(specName: String): Long

    fun countByTargetResource(targetResource: String): Long

    fun count(): Long

    // 실행 중인 작업 조회
    fun findByStatusAndStartedAtBefore(
        status: WorkflowRunStatus,
        threshold: LocalDateTime,
    ): List<QualityRunEntity>

    // 최근 완료된 실행 조회
    fun findByStatusInAndEndedAtIsNotNullOrderByEndedAtDesc(
        statuses: List<WorkflowRunStatus>,
        pageable: Pageable,
    ): Page<QualityRunEntity>

    // 장시간 실행 중인 작업 조회
    fun findLongRunningTasks(threshold: LocalDateTime): List<QualityRunEntity>

    // Airflow sync 대상 조회
    fun findByStatusInAndLastSyncedAtBefore(
        statuses: List<WorkflowRunStatus>,
        threshold: LocalDateTime,
    ): List<QualityRunEntity>

    // Quality Spec ID별 조회
    fun findByQualitySpecId(qualitySpecId: Long): List<QualityRunEntity>

    fun findByQualitySpecIdOrderByStartedAtDesc(
        qualitySpecId: Long,
        pageable: Pageable,
    ): Page<QualityRunEntity>
}
