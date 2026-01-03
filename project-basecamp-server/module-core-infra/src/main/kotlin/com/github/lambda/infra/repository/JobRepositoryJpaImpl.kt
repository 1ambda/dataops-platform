package com.github.lambda.infra.repository

import com.github.lambda.domain.model.pipeline.JobEntity
import com.github.lambda.domain.model.pipeline.JobStatus
import com.github.lambda.domain.repository.JobRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 작업 JPA Repository 구현 인터페이스
 *
 * Domain JobRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * JobRepositoryJpaSpringData와 JobRepositoryJpaImpl을 하나의 인터페이스로 통합했습니다.
 */
@Repository("jobRepositoryJpa")
interface JobRepositoryJpaImpl :
    JobRepositoryJpa,
    JpaRepository<JobEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들 (기존 SpringData에서 이동)
    // 파이프라인별 작업 조회 (FK field 사용)
    @Query("SELECT j FROM JobEntity j WHERE j.pipelineId = :pipelineId ORDER BY j.createdAt DESC")
    fun findByPipelineIdOrderByCreatedAtDesc(
        @Param("pipelineId") pipelineId: Long,
    ): List<JobEntity>

    @Query("SELECT j FROM JobEntity j WHERE j.pipelineId = :pipelineId ORDER BY j.createdAt DESC")
    fun findByPipelineIdOrderByCreatedAtDesc(
        @Param("pipelineId") pipelineId: Long,
        pageable: Pageable,
    ): Page<JobEntity>

    // 상태별 작업 조회
    fun findByStatusOrderByCreatedAtDesc(status: JobStatus): List<JobEntity>

    fun findByStatusOrderByCreatedAtDesc(
        status: JobStatus,
        pageable: Pageable,
    ): Page<JobEntity>

    // 복합 조건 조회 (FK field 사용)
    @Query(
        "SELECT j FROM JobEntity j WHERE j.pipelineId = :pipelineId AND j.status = :status ORDER BY j.createdAt DESC",
    )
    override fun findByPipelineIdAndStatus(
        @Param("pipelineId") pipelineId: Long,
        @Param("status") status: JobStatus,
    ): List<JobEntity>

    // 최신 작업 조회 (FK field 사용)
    @Query("SELECT j FROM JobEntity j WHERE j.pipelineId = :pipelineId ORDER BY j.createdAt DESC LIMIT 1")
    fun findTopByPipelineIdOrderByCreatedAtDesc(
        @Param("pipelineId") pipelineId: Long,
    ): JobEntity?

    // 통계 및 집계 (FK field 사용)
    @Query("SELECT COUNT(j) FROM JobEntity j WHERE j.pipelineId = :pipelineId")
    override fun countByPipelineId(
        @Param("pipelineId") pipelineId: Long,
    ): Long

    override fun countByStatus(status: JobStatus): Long

    // 상태 업데이트
    @Modifying
    @Query("UPDATE JobEntity j SET j.status = :status, j.updatedAt = :updatedAt WHERE j.id = :id")
    fun updateStatusById(
        @Param("id") id: Long,
        @Param("status") status: JobStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    // 종료 시간 업데이트
    @Modifying
    @Query("UPDATE JobEntity j SET j.finishedAt = :finishedAt, j.updatedAt = :updatedAt WHERE j.id = :id")
    fun updateEndTimeById(
        @Param("id") id: Long,
        @Param("finishedAt") finishedAt: LocalDateTime,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    // JobRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun findByPipelineId(pipelineId: Long): List<JobEntity> = findByPipelineIdOrderByCreatedAtDesc(pipelineId)

    override fun findByStatus(status: JobStatus): List<JobEntity> = findByStatusOrderByCreatedAtDesc(status)

    override fun findByPipelineIdWithPagination(
        pipelineId: Long,
        page: Int,
        size: Int,
    ): List<JobEntity> = findByPipelineIdOrderByCreatedAtDesc(pipelineId, PageRequest.of(page, size)).content

    override fun findByStatusWithPagination(
        status: JobStatus,
        page: Int,
        size: Int,
    ): List<JobEntity> = findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size)).content

    override fun updateStatus(
        id: Long,
        status: JobStatus,
    ): Boolean = updateStatusById(id, status, LocalDateTime.now()) > 0

    override fun updateJobEndTime(
        id: Long,
        endTime: LocalDateTime,
    ): Boolean = updateEndTimeById(id, endTime, LocalDateTime.now()) > 0
}
