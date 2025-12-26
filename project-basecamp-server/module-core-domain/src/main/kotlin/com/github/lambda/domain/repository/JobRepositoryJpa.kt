package com.github.lambda.domain.repository

import com.github.lambda.domain.model.pipeline.JobEntity
import com.github.lambda.domain.model.pipeline.JobStatus
import java.util.*

/**
 * 작업 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface JobRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(job: JobEntity): JobEntity

    // findById는 JpaRepository에서 제공하므로 제거
    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<JobEntity>

    // 도메인 특화 조회 메서드
    fun findByPipelineId(pipelineId: Long): List<JobEntity>

    fun findByStatus(status: JobStatus): List<JobEntity>

    fun findByPipelineIdAndStatus(
        pipelineId: Long,
        status: JobStatus,
    ): List<JobEntity>

    // 페이지네이션 조회
    fun findByPipelineIdWithPagination(
        pipelineId: Long,
        page: Int,
        size: Int,
    ): List<JobEntity>

    fun findByStatusWithPagination(
        status: JobStatus,
        page: Int,
        size: Int,
    ): List<JobEntity>

    // 상태 업데이트
    fun updateStatus(
        id: Long,
        status: JobStatus,
    ): Boolean

    fun updateJobEndTime(
        id: Long,
        endTime: java.time.LocalDateTime,
    ): Boolean

    // 통계 및 집계
    fun countByStatus(status: JobStatus): Long

    fun countByPipelineId(pipelineId: Long): Long
}
