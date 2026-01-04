package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.workflow.WorkflowEntity
import com.github.lambda.domain.model.workflow.WorkflowSourceType
import com.github.lambda.domain.model.workflow.WorkflowStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Workflow Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * Workflow에 대한 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface WorkflowRepositoryDsl {
    /**
     * 복합 필터 조건으로 workflow 검색
     *
     * @param sourceType 소스 타입 필터 (정확히 일치)
     * @param status 상태 필터 (정확히 일치)
     * @param owner 소유자 필터 (부분 일치)
     * @param team 팀 필터 (정확히 일치)
     * @param search 데이터셋 이름 및 설명 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 workflow 목록
     */
    fun findByFilters(
        sourceType: WorkflowSourceType? = null,
        status: WorkflowStatus? = null,
        owner: String? = null,
        team: String? = null,
        search: String? = null,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    /**
     * 필터 조건에 맞는 workflow 개수 조회
     *
     * @param sourceType 소스 타입 필터 (정확히 일치)
     * @param status 상태 필터 (정확히 일치)
     * @param owner 소유자 필터 (부분 일치)
     * @param team 팀 필터 (정확히 일치)
     * @param search 데이터셋 이름 및 설명 검색 (부분 일치)
     * @return 조건에 맞는 workflow 개수
     */
    fun countByFilters(
        sourceType: WorkflowSourceType? = null,
        status: WorkflowStatus? = null,
        owner: String? = null,
        team: String? = null,
        search: String? = null,
    ): Long

    /**
     * Workflow 통계 정보 조회
     *
     * @param sourceType 특정 소스 타입으로 제한 (null이면 전체)
     * @param owner 특정 소유자로 제한 (null이면 전체)
     * @return 통계 정보 (총 개수, 상태별 개수, 소스타입별 개수 등)
     */
    fun getWorkflowStatistics(
        sourceType: WorkflowSourceType? = null,
        owner: String? = null,
    ): Map<String, Any>

    /**
     * 상태별 workflow 개수 조회
     *
     * @return 상태별 workflow 개수 맵
     */
    fun getWorkflowCountByStatus(): List<Map<String, Any>>

    /**
     * 소유자별 workflow 개수 조회
     *
     * @return 소유자별 workflow 개수 맵
     */
    fun getWorkflowCountByOwner(): List<Map<String, Any>>

    /**
     * 소스타입별 workflow 개수 조회
     *
     * @return 소스타입별 workflow 개수 맵
     */
    fun getWorkflowCountBySourceType(): List<Map<String, Any>>

    /**
     * 팀별 workflow 개수 조회
     *
     * @return 팀별 workflow 개수 맵
     */
    fun getWorkflowCountByTeam(): List<Map<String, Any>>

    /**
     * 최근에 업데이트된 workflow 조회
     *
     * @param limit 조회할 개수
     * @param daysSince 몇 일 전부터
     * @return 최근 업데이트된 workflow 목록
     */
    fun findRecentlyUpdated(
        limit: Int,
        daysSince: Int,
    ): List<WorkflowEntity>

    /**
     * 활성화된 스케줄 workflow들 조회
     *
     * @return 활성 상태이면서 스케줄이 설정된 workflow 목록
     */
    fun findActiveScheduledWorkflows(): List<WorkflowEntity>

    /**
     * 특정 스케줄 패턴을 가진 workflow들 조회
     *
     * @param cronPattern cron 패턴 (부분 일치)
     * @return 스케줄 패턴에 맞는 workflow 목록
     */
    fun findWorkflowsBySchedule(cronPattern: String? = null): List<WorkflowEntity>

    /**
     * 특정 Airflow DAG ID 패턴을 가진 workflow들 조회
     *
     * @param dagIdPattern DAG ID 패턴 (부분 일치)
     * @param includeDisabled 비활성화된 workflow도 포함할지 여부
     * @return DAG ID 패턴에 맞는 workflow 목록
     */
    fun findWorkflowsByAirflowDagId(
        dagIdPattern: String,
        includeDisabled: Boolean = false,
    ): List<WorkflowEntity>

    /**
     * 활성 workflow 중 특정 조건에 맞는 것들 조회
     *
     * @param hasSchedule 스케줄이 설정된 것만 조회할지 여부
     * @param sourceType 소스 타입 필터
     * @return 조건에 맞는 활성화된 workflow 목록
     */
    fun findActiveWorkflows(
        hasSchedule: Boolean? = null,
        sourceType: WorkflowSourceType? = null,
    ): List<WorkflowEntity>

    /**
     * 데이터셋 이름으로 그룹화된 workflow 통계
     *
     * @return 데이터셋별 workflow 개수 맵 (catalog, schema, table 레벨)
     */
    fun getWorkflowCountByDatasetLevel(): Map<String, Any>

    /**
     * 실행 이력이 있는 workflow 목록 조회
     *
     * @param hasRecentRuns 최근 실행 이력이 있는지 여부 (7일 기준)
     * @return 실행 이력 조건에 맞는 workflow 목록
     */
    fun findWorkflowsWithRuns(hasRecentRuns: Boolean? = null): List<WorkflowEntity>
}
