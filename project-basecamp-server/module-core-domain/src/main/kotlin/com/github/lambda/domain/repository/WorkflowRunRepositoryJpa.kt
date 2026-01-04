package com.github.lambda.domain.repository

import com.github.lambda.domain.model.workflow.WorkflowRunEntity
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Workflow Run Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * WorkflowRun에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface WorkflowRunRepositoryJpa {
    // 기본 CRUD 작업
    fun save(workflowRun: WorkflowRunEntity): WorkflowRunEntity

    // 도메인 특화 조회 메서드 - Run ID 기반
    fun findByRunId(runId: String): WorkflowRunEntity?

    fun existsByRunId(runId: String): Boolean

    fun deleteByRunId(runId: String): Long

    // Dataset Name 기반 조회
    fun findByDatasetName(datasetName: String): List<WorkflowRunEntity>

    fun findByDatasetNameOrderByStartedAtDesc(
        datasetName: String,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 상태 기반 조회
    fun findByStatus(status: WorkflowRunStatus): List<WorkflowRunEntity>

    fun findByStatusOrderByStartedAtDesc(
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // Dataset Name과 상태 조합 조회
    fun findByDatasetNameAndStatus(
        datasetName: String,
        status: WorkflowRunStatus,
    ): List<WorkflowRunEntity>

    fun findByDatasetNameAndStatusOrderByStartedAtDesc(
        datasetName: String,
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 실행 중인 워크플로우 조회
    fun findRunningWorkflows(): List<WorkflowRunEntity>

    fun findByStatusIn(statuses: List<WorkflowRunStatus>): List<WorkflowRunEntity>

    // 최근 실행 이력 조회 (Dataset별)
    fun findTop10ByDatasetNameOrderByStartedAtDesc(datasetName: String): List<WorkflowRunEntity>

    fun findTop5ByDatasetNameOrderByStartedAtDesc(datasetName: String): List<WorkflowRunEntity>

    // 실행 타입 기반 조회
    fun findByRunType(runType: WorkflowRunType): List<WorkflowRunEntity>

    fun findByRunTypeOrderByStartedAtDesc(
        runType: WorkflowRunType,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 트리거한 사용자 기반 조회
    fun findByTriggeredBy(triggeredBy: String): List<WorkflowRunEntity>

    fun findByTriggeredByOrderByStartedAtDesc(
        triggeredBy: String,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 시간 기반 조회
    fun findByStartedAtAfter(startedAt: LocalDateTime): List<WorkflowRunEntity>

    fun findByStartedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): List<WorkflowRunEntity>

    fun findByEndedAtAfter(endedAt: LocalDateTime): List<WorkflowRunEntity>

    fun findByEndedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): List<WorkflowRunEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<WorkflowRunEntity>

    // 통계 및 집계
    fun countByStatus(status: WorkflowRunStatus): Long

    fun countByDatasetName(datasetName: String): Long

    fun countByRunType(runType: WorkflowRunType): Long

    fun countByTriggeredBy(triggeredBy: String): Long

    fun count(): Long

    // 완료된 실행 조회
    fun findByEndedAtIsNotNull(): List<WorkflowRunEntity>

    fun findByEndedAtIsNull(): List<WorkflowRunEntity>

    // 중지된 실행 조회
    fun findByStoppedByIsNotNull(): List<WorkflowRunEntity>

    fun findByStoppedBy(stoppedBy: String): List<WorkflowRunEntity>

    // 로그가 있는 실행 조회
    fun findByLogsUrlIsNotNull(): List<WorkflowRunEntity>

    fun findByLogsUrlIsNull(): List<WorkflowRunEntity>

    // === Airflow 동기화 관련 조회 (Phase 5) ===

    /**
     * Airflow DAG Run ID로 조회
     */
    fun findByAirflowDagRunId(dagRunId: String): WorkflowRunEntity?

    /**
     * 특정 Airflow 클러스터의 실행 목록 조회
     */
    fun findByAirflowClusterId(clusterId: Long): List<WorkflowRunEntity>

    /**
     * 특정 Airflow 클러스터의 특정 상태 실행 조회
     */
    fun findByAirflowClusterIdAndStatus(
        clusterId: Long,
        status: WorkflowRunStatus,
    ): List<WorkflowRunEntity>

    /**
     * 동기화가 필요한 (진행 중인) 실행 조회
     */
    fun findByStatusInAndAirflowDagRunIdIsNotNull(statuses: List<WorkflowRunStatus>): List<WorkflowRunEntity>
}
