package com.dataops.basecamp.domain.repository.workflow

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.domain.entity.workflow.WorkflowEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Workflow Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Workflow에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface WorkflowRepositoryJpa {
    // 기본 CRUD 작업
    fun save(workflow: WorkflowEntity): WorkflowEntity

    // 도메인 특화 조회 메서드 - Dataset Name 기반
    fun findByDatasetName(datasetName: String): WorkflowEntity?

    fun existsByDatasetName(datasetName: String): Boolean

    fun deleteByDatasetName(datasetName: String): Long

    // 소유자 기반 조회
    fun findByOwner(owner: String): List<WorkflowEntity>

    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 소스 타입 기반 조회
    fun findBySourceType(sourceType: WorkflowSourceType): List<WorkflowEntity>

    fun findBySourceTypeOrderByUpdatedAtDesc(
        sourceType: WorkflowSourceType,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 상태 기반 조회
    fun findByStatus(status: WorkflowStatus): List<WorkflowEntity>

    fun findByStatusOrderByUpdatedAtDesc(
        status: WorkflowStatus,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 팀별 조회
    fun findByTeam(team: String): List<WorkflowEntity>

    fun findByTeamIsNull(): List<WorkflowEntity>

    fun findByTeamOrderByUpdatedAtDesc(
        team: String,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<WorkflowEntity>

    // 통계 및 집계
    fun countByOwner(owner: String): Long

    fun countByStatus(status: WorkflowStatus): Long

    fun countBySourceType(sourceType: WorkflowSourceType): Long

    fun countByTeam(team: String): Long

    fun count(): Long

    // Dataset name 패턴 검색 (단순 LIKE 검색)
    fun findByDatasetNameContainingIgnoreCase(namePattern: String): List<WorkflowEntity>

    fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<WorkflowEntity>

    // Airflow DAG ID 기반 조회
    fun findByAirflowDagId(airflowDagId: String): WorkflowEntity?

    fun findByAirflowDagIdContainingIgnoreCase(dagIdPattern: String): List<WorkflowEntity>

    // 업데이트 시간 기반 조회
    fun findByUpdatedAtAfter(
        updatedAt: LocalDateTime,
        pageable: Pageable,
    ): Page<WorkflowEntity>

    // 스케줄이 설정된 워크플로우 조회
    fun findByScheduleCronIsNotNull(): List<WorkflowEntity>

    fun findByScheduleCronIsNull(): List<WorkflowEntity>

    // 활성 상태이면서 스케줄이 설정된 워크플로우 조회
    fun findByStatusAndScheduleCronIsNotNull(status: WorkflowStatus): List<WorkflowEntity>
}
