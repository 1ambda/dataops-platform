package com.dataops.basecamp.infra.repository.workflow

import com.dataops.basecamp.common.enums.WorkflowRunStatus
import com.dataops.basecamp.common.enums.WorkflowRunType
import com.dataops.basecamp.domain.entity.workflow.WorkflowRunEntity
import com.dataops.basecamp.domain.repository.workflow.WorkflowRunRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Workflow Run JPA Repository 구현 인터페이스
 *
 * Domain WorkflowRunRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("workflowRunRepositoryJpa")
interface WorkflowRunRepositoryJpaImpl :
    WorkflowRunRepositoryJpa,
    JpaRepository<WorkflowRunEntity, Long> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByRunId(runId: String): WorkflowRunEntity?

    override fun existsByRunId(runId: String): Boolean

    override fun deleteByRunId(runId: String): Long

    // Dataset Name 기반 조회
    override fun findByDatasetName(datasetName: String): List<WorkflowRunEntity>

    override fun findByDatasetNameOrderByStartedAtDesc(
        datasetName: String,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 상태 기반 조회
    override fun findByStatus(status: WorkflowRunStatus): List<WorkflowRunEntity>

    override fun findByStatusOrderByStartedAtDesc(
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // Dataset Name과 상태 조합 조회
    override fun findByDatasetNameAndStatus(
        datasetName: String,
        status: WorkflowRunStatus,
    ): List<WorkflowRunEntity>

    override fun findByDatasetNameAndStatusOrderByStartedAtDesc(
        datasetName: String,
        status: WorkflowRunStatus,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 특정 상태들 조회
    override fun findByStatusIn(statuses: List<WorkflowRunStatus>): List<WorkflowRunEntity>

    // 최근 실행 이력 조회 (Dataset별)
    override fun findTop10ByDatasetNameOrderByStartedAtDesc(datasetName: String): List<WorkflowRunEntity>

    override fun findTop5ByDatasetNameOrderByStartedAtDesc(datasetName: String): List<WorkflowRunEntity>

    // 실행 타입 기반 조회
    override fun findByRunType(runType: WorkflowRunType): List<WorkflowRunEntity>

    override fun findByRunTypeOrderByStartedAtDesc(
        runType: WorkflowRunType,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 트리거한 사용자 기반 조회
    override fun findByTriggeredBy(triggeredBy: String): List<WorkflowRunEntity>

    override fun findByTriggeredByOrderByStartedAtDesc(
        triggeredBy: String,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    // 시간 기반 조회
    override fun findByStartedAtAfter(startedAt: LocalDateTime): List<WorkflowRunEntity>

    override fun findByStartedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): List<WorkflowRunEntity>

    override fun findByEndedAtAfter(endedAt: LocalDateTime): List<WorkflowRunEntity>

    override fun findByEndedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
    ): List<WorkflowRunEntity>

    // 전체 목록 조회
    override fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<WorkflowRunEntity>

    // 통계 및 집계
    override fun countByStatus(status: WorkflowRunStatus): Long

    override fun countByDatasetName(datasetName: String): Long

    override fun countByRunType(runType: WorkflowRunType): Long

    override fun countByTriggeredBy(triggeredBy: String): Long

    // 완료 관련 조회
    override fun findByEndedAtIsNotNull(): List<WorkflowRunEntity>

    override fun findByEndedAtIsNull(): List<WorkflowRunEntity>

    // 중지 관련 조회
    override fun findByStoppedByIsNotNull(): List<WorkflowRunEntity>

    override fun findByStoppedBy(stoppedBy: String): List<WorkflowRunEntity>

    // 로그 관련 조회
    override fun findByLogsUrlIsNotNull(): List<WorkflowRunEntity>

    override fun findByLogsUrlIsNull(): List<WorkflowRunEntity>

    // 실행 중인 워크플로우 조회 (커스텀 쿼리)
    @Query(
        """
        SELECT wr FROM WorkflowRunEntity wr
        WHERE wr.status IN ('RUNNING', 'PENDING', 'STOPPING')
        ORDER BY wr.startedAt DESC
        """,
    )
    override fun findRunningWorkflows(): List<WorkflowRunEntity>

    // 복잡한 필터링 쿼리
    @Query(
        """
        SELECT wr FROM WorkflowRunEntity wr
        WHERE (:datasetName IS NULL OR wr.datasetName = :datasetName)
        AND (:status IS NULL OR wr.status = :status)
        AND (:runType IS NULL OR wr.runType = :runType)
        AND (:triggeredBy IS NULL OR LOWER(wr.triggeredBy) LIKE LOWER(CONCAT('%', :triggeredBy, '%')))
        AND (:startDate IS NULL OR wr.startedAt >= :startDate)
        AND (:endDate IS NULL OR wr.startedAt <= :endDate)
        ORDER BY wr.startedAt DESC
        """,
    )
    fun findByComplexFilters(
        @Param("datasetName") datasetName: String?,
        @Param("status") status: WorkflowRunStatus?,
        @Param("runType") runType: WorkflowRunType?,
        @Param("triggeredBy") triggeredBy: String?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    @Query(
        """
        SELECT COUNT(wr) FROM WorkflowRunEntity wr
        WHERE (:datasetName IS NULL OR wr.datasetName = :datasetName)
        AND (:status IS NULL OR wr.status = :status)
        AND (:runType IS NULL OR wr.runType = :runType)
        AND (:triggeredBy IS NULL OR LOWER(wr.triggeredBy) LIKE LOWER(CONCAT('%', :triggeredBy, '%')))
        AND (:startDate IS NULL OR wr.startedAt >= :startDate)
        AND (:endDate IS NULL OR wr.startedAt <= :endDate)
        """,
    )
    fun countByComplexFilters(
        @Param("datasetName") datasetName: String?,
        @Param("status") status: WorkflowRunStatus?,
        @Param("runType") runType: WorkflowRunType?,
        @Param("triggeredBy") triggeredBy: String?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?,
    ): Long

    @Query(
        """
        SELECT wr FROM WorkflowRunEntity wr
        WHERE wr.startedAt >= :since
        ORDER BY wr.startedAt DESC
        """,
    )
    fun findRecentRuns(
        @Param("since") since: LocalDateTime,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    @Query(
        """
        SELECT wr FROM WorkflowRunEntity wr
        WHERE wr.status = 'FAILED'
        AND (:datasetName IS NULL OR wr.datasetName = :datasetName)
        AND wr.startedAt >= :since
        ORDER BY wr.startedAt DESC
        """,
    )
    fun findFailedRuns(
        @Param("datasetName") datasetName: String?,
        @Param("since") since: LocalDateTime,
    ): List<WorkflowRunEntity>

    @Modifying
    @Query(
        """
        UPDATE WorkflowRunEntity wr
        SET wr.status = :status, wr.endedAt = :endedAt
        WHERE wr.runId = :runId
        """,
    )
    fun updateRunStatus(
        @Param("runId") runId: String,
        @Param("status") status: WorkflowRunStatus,
        @Param("endedAt") endedAt: LocalDateTime?,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE WorkflowRunEntity wr
        SET wr.logsUrl = :logsUrl
        WHERE wr.runId = :runId
        """,
    )
    fun updateLogsUrl(
        @Param("runId") runId: String,
        @Param("logsUrl") logsUrl: String,
    ): Int

    // === Airflow 동기화 관련 조회 (Phase 5) ===

    /**
     * Airflow DAG Run ID로 조회
     */
    override fun findByAirflowDagRunId(dagRunId: String): WorkflowRunEntity?

    /**
     * 특정 Airflow 클러스터의 실행 목록 조회
     */
    override fun findByAirflowClusterId(clusterId: Long): List<WorkflowRunEntity>

    /**
     * 특정 Airflow 클러스터의 특정 상태 실행 조회
     */
    override fun findByAirflowClusterIdAndStatus(
        clusterId: Long,
        status: WorkflowRunStatus,
    ): List<WorkflowRunEntity>

    /**
     * 동기화가 필요한 (진행 중인) 실행 조회
     */
    @Query(
        """
        SELECT wr FROM WorkflowRunEntity wr
        WHERE wr.status IN :statuses
        AND wr.airflowDagRunId IS NOT NULL
        ORDER BY wr.startedAt DESC
        """,
    )
    override fun findByStatusInAndAirflowDagRunIdIsNotNull(
        @Param("statuses") statuses: List<WorkflowRunStatus>,
    ): List<WorkflowRunEntity>
}
