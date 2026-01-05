package com.dataops.basecamp.infra.repository.workflow

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.domain.entity.workflow.WorkflowEntity
import com.dataops.basecamp.domain.repository.workflow.WorkflowRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Workflow JPA Repository 구현 인터페이스
 *
 * Domain WorkflowRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("workflowRepositoryJpa")
interface WorkflowRepositoryJpaImpl :
    WorkflowRepositoryJpa,
    JpaRepository<WorkflowEntity, String> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByDatasetName(datasetName: String): WorkflowEntity?

    override fun existsByDatasetName(datasetName: String): Boolean

    override fun deleteByDatasetName(datasetName: String): Long

    // 소유자 기반 조회
    override fun findByOwner(owner: String): List<WorkflowEntity>

    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 소스 타입 기반 조회
    override fun findBySourceType(sourceType: WorkflowSourceType): List<WorkflowEntity>

    override fun findBySourceTypeOrderByUpdatedAtDesc(
        sourceType: WorkflowSourceType,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 상태 기반 조회
    override fun findByStatus(status: WorkflowStatus): List<WorkflowEntity>

    override fun findByStatusOrderByUpdatedAtDesc(
        status: WorkflowStatus,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 팀별 조회
    override fun findByTeam(team: String): List<WorkflowEntity>

    override fun findByTeamIsNull(): List<WorkflowEntity>

    override fun findByTeamOrderByUpdatedAtDesc(
        team: String,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 전체 목록 조회
    override fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<WorkflowEntity>

    // 통계 및 집계
    override fun countByOwner(owner: String): Long

    override fun countByStatus(status: WorkflowStatus): Long

    override fun countBySourceType(sourceType: WorkflowSourceType): Long

    override fun countByTeam(team: String): Long

    // 패턴 검색
    override fun findByDatasetNameContainingIgnoreCase(namePattern: String): List<WorkflowEntity>

    override fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<WorkflowEntity>

    // Airflow DAG ID 기반 조회
    override fun findByAirflowDagId(airflowDagId: String): WorkflowEntity?

    override fun findByAirflowDagIdContainingIgnoreCase(dagIdPattern: String): List<WorkflowEntity>

    // 업데이트 시간 기반 조회
    override fun findByUpdatedAtAfter(
        updatedAt: LocalDateTime,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 스케줄 관련 조회
    override fun findByScheduleCronIsNotNull(): List<WorkflowEntity>

    override fun findByScheduleCronIsNull(): List<WorkflowEntity>

    override fun findByStatusAndScheduleCronIsNotNull(status: WorkflowStatus): List<WorkflowEntity>

    // 커스텀 쿼리들
    @Query(
        """
        SELECT w FROM WorkflowEntity w
        WHERE (:sourceType IS NULL OR w.sourceType = :sourceType)
        AND (:status IS NULL OR w.status = :status)
        AND (:owner IS NULL OR LOWER(w.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:team IS NULL OR w.team = :team)
        AND (:search IS NULL OR
             LOWER(w.datasetName) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY w.updatedAt DESC
        """,
    )
    fun findByComplexFilters(
        @Param("sourceType") sourceType: WorkflowSourceType?,
        @Param("status") status: WorkflowStatus?,
        @Param("owner") owner: String?,
        @Param("team") team: String?,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    @Query(
        """
        SELECT COUNT(w) FROM WorkflowEntity w
        WHERE (:sourceType IS NULL OR w.sourceType = :sourceType)
        AND (:status IS NULL OR w.status = :status)
        AND (:owner IS NULL OR LOWER(w.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:team IS NULL OR w.team = :team)
        AND (:search IS NULL OR
             LOWER(w.datasetName) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(w.description) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    fun countByComplexFilters(
        @Param("sourceType") sourceType: WorkflowSourceType?,
        @Param("status") status: WorkflowStatus?,
        @Param("owner") owner: String?,
        @Param("team") team: String?,
        @Param("search") search: String?,
    ): Long

    @Query(
        """
        SELECT w FROM WorkflowEntity w
        WHERE w.updatedAt >= :since
        ORDER BY w.updatedAt DESC
        """,
    )
    fun findRecentlyUpdated(
        @Param("since") since: LocalDateTime,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    @Query(
        """
        SELECT w FROM WorkflowEntity w
        WHERE w.status = :status
        AND w.schedule.cron IS NOT NULL
        AND (:cronPattern IS NULL OR w.schedule.cron LIKE CONCAT('%', :cronPattern, '%'))
        ORDER BY w.updatedAt DESC
        """,
    )
    fun findActiveScheduledWorkflows(
        @Param("status") status: WorkflowStatus = WorkflowStatus.ACTIVE,
        @Param("cronPattern") cronPattern: String?,
    ): List<WorkflowEntity>

    @Query(
        """
        SELECT w FROM WorkflowEntity w
        WHERE w.airflowDagId LIKE CONCAT('%', :dagIdPattern, '%')
        AND (:includeDisabled = true OR w.status = 'ACTIVE')
        ORDER BY w.updatedAt DESC
        """,
    )
    fun findWorkflowsByAirflowDagIdPattern(
        @Param("dagIdPattern") dagIdPattern: String,
        @Param("includeDisabled") includeDisabled: Boolean,
    ): List<WorkflowEntity>

    @Modifying
    @Query("UPDATE WorkflowEntity w SET w.updatedAt = :updatedAt WHERE w.datasetName = :datasetName")
    fun updateLastAccessedByDatasetName(
        @Param("datasetName") datasetName: String,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int
}
