package com.dataops.basecamp.domain.repository.workflow

import com.dataops.basecamp.common.enums.WorkflowRunStatus
import com.dataops.basecamp.common.enums.WorkflowRunType
import com.dataops.basecamp.domain.entity.workflow.WorkflowRunEntity
import com.dataops.basecamp.domain.projection.workflow.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Workflow Run Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * WorkflowRun에 대한 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface WorkflowRunRepositoryDsl {
    /**
     * 복합 필터 조건으로 workflow run 검색
     *
     * @param datasetName 데이터셋 이름 필터 (정확히 일치)
     * @param status 상태 필터 (정확히 일치)
     * @param runType 실행 타입 필터 (정확히 일치)
     * @param triggeredBy 실행자 필터 (부분 일치)
     * @param startDate 시작일 (이후)
     * @param endDate 종료일 (이전)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 workflow run 목록
     */
    fun findRunsByFilters(
        datasetName: String? = null,
        status: WorkflowRunStatus? = null,
        runType: WorkflowRunType? = null,
        triggeredBy: String? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
        pageable: Pageable,
    ): Page<WorkflowRunEntity>

    /**
     * 필터 조건에 맞는 workflow run 개수 조회
     *
     * @param datasetName 데이터셋 이름 필터 (정확히 일치)
     * @param status 상태 필터 (정확히 일치)
     * @param runType 실행 타입 필터 (정확히 일치)
     * @param triggeredBy 실행자 필터 (부분 일치)
     * @param startDate 시작일 (이후)
     * @param endDate 종료일 (이전)
     * @return 조건에 맞는 workflow run 개수
     */
    fun countRunsByFilters(
        datasetName: String? = null,
        status: WorkflowRunStatus? = null,
        runType: WorkflowRunType? = null,
        triggeredBy: String? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
    ): Long

    /**
     * Workflow Run 통계 정보 조회
     *
     * @param datasetName 특정 데이터셋으로 제한 (null이면 전체)
     * @param triggeredBy 특정 실행자로 제한 (null이면 전체)
     * @return 통계 정보 (총 실행 수, 상태별 개수, 실행 타입별 개수 등)
     */
    fun getRunStatistics(
        datasetName: String? = null,
        triggeredBy: String? = null,
    ): WorkflowRunStatisticsProjection

    /**
     * 상태별 workflow run 개수 조회
     *
     * @return 상태별 workflow run 개수 맵
     */
    fun getRunCountByStatus(): List<WorkflowRunCountByStatusProjection>

    /**
     * 실행 타입별 workflow run 개수 조회
     *
     * @return 실행 타입별 workflow run 개수 맵
     */
    fun getRunCountByRunType(): List<WorkflowRunCountByTypeProjection>

    /**
     * 실행자별 workflow run 개수 조회
     *
     * @return 실행자별 workflow run 개수 맵
     */
    fun getRunCountByTriggeredBy(): List<WorkflowRunCountByTriggeredByProjection>

    /**
     * 데이터셋별 workflow run 개수 조회
     *
     * @return 데이터셋별 workflow run 개수 맵
     */
    fun getRunCountByDatasetName(): List<WorkflowRunCountByDatasetProjection>

    /**
     * 실행 시간 통계 조회
     *
     * @param datasetName 특정 데이터셋으로 제한 (null이면 전체)
     * @param daysSince 몇 일 전부터 (기본값: 30일)
     * @return 실행 시간 통계 (평균, 최소, 최대 등)
     */
    fun getDurationStatistics(
        datasetName: String? = null,
        daysSince: Int = 30,
    ): WorkflowDurationStatisticsProjection

    /**
     * 특정 데이터셋의 실행 이력 조회
     *
     * @param datasetName 데이터셋 이름
     * @param limit 조회할 개수
     * @return 최근 실행 이력 목록
     */
    fun findExecutionHistory(
        datasetName: String,
        limit: Int = 50,
    ): List<WorkflowRunEntity>

    /**
     * 실행 시간이 긴 workflow run 조회
     *
     * @param minDurationSeconds 최소 실행 시간 (초)
     * @param limit 조회할 개수
     * @return 실행 시간이 긴 workflow run 목록
     */
    fun findLongRunningWorkflows(
        minDurationSeconds: Double = 3600.0, // 1시간
        limit: Int = 10,
    ): List<WorkflowRunEntity>

    /**
     * 실패한 workflow run 조회
     *
     * @param datasetName 특정 데이터셋으로 제한 (null이면 전체)
     * @param daysSince 몇 일 전부터
     * @return 실패한 workflow run 목록
     */
    fun findFailedRuns(
        datasetName: String? = null,
        daysSince: Int = 7,
    ): List<WorkflowRunEntity>

    /**
     * 성공률 통계 조회
     *
     * @param datasetName 특정 데이터셋으로 제한 (null이면 전체)
     * @param daysSince 몇 일 전부터
     * @return 성공률 통계 정보
     */
    fun getSuccessRateStatistics(
        datasetName: String? = null,
        daysSince: Int = 30,
    ): WorkflowSuccessRateProjection

    /**
     * 현재 실행 중인 workflow run 조회
     *
     * @return 실행 중인 workflow run 목록
     */
    fun findCurrentlyRunningRuns(): List<WorkflowRunEntity>

    /**
     * 일별 실행 통계 조회
     *
     * @param datasetName 특정 데이터셋으로 제한 (null이면 전체)
     * @param daysSince 몇 일 전부터
     * @return 일별 실행 통계 맵
     */
    fun getDailyRunStatistics(
        datasetName: String? = null,
        daysSince: Int = 30,
    ): List<WorkflowDailyRunStatisticsProjection>

    /**
     * 중지된 workflow run 조회
     *
     * @param stoppedBy 특정 중지자로 제한 (null이면 전체)
     * @param daysSince 몇 일 전부터
     * @return 중지된 workflow run 목록
     */
    fun findStoppedRuns(
        stoppedBy: String? = null,
        daysSince: Int = 7,
    ): List<WorkflowRunEntity>

    /**
     * 최근 완료된 실행들의 평균 실행 시간 조회
     *
     * @param datasetName 데이터셋 이름
     * @param limit 최근 N개 실행 기준
     * @return 평균 실행 시간 (초)
     */
    fun getAverageExecutionTime(
        datasetName: String,
        limit: Int = 10,
    ): Double?

    // === Airflow 동기화 관련 쿼리 (Phase 5) ===

    /**
     * 특정 클러스터에서 진행 중인 Run 조회 (동기화용)
     *
     * @param clusterId Airflow 클러스터 ID
     * @param since 조회 시작 시간
     * @return 진행 중인 workflow run 목록
     */
    fun findPendingRunsByCluster(
        clusterId: Long,
        since: LocalDateTime,
    ): List<WorkflowRunEntity>

    /**
     * 동기화가 오래된 실행 조회 (stale 상태)
     *
     * @param staleThreshold 마지막 동기화 이후 경과 시간 기준
     * @param statuses 조회할 상태 목록 (기본: PENDING, RUNNING)
     * @return stale 상태의 workflow run 목록
     */
    fun findStaleRuns(
        staleThreshold: LocalDateTime,
        statuses: List<WorkflowRunStatus> =
            listOf(
                WorkflowRunStatus.PENDING,
                WorkflowRunStatus.RUNNING,
            ),
    ): List<WorkflowRunEntity>

    /**
     * 클러스터별 동기화 통계 조회
     *
     * @param clusterId Airflow 클러스터 ID (null이면 전체)
     * @return 동기화 통계 (총 실행 수, 동기화된 수, 동기화 필요한 수 등)
     */
    fun getSyncStatistics(clusterId: Long? = null): WorkflowSyncStatisticsProjection
}
