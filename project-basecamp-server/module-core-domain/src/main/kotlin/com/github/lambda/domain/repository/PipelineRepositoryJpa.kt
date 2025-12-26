package com.github.lambda.domain.repository

import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.*

/**
 * 파이프라인 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface PipelineRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(pipeline: PipelineEntity): PipelineEntity

    fun findById(id: Long): PipelineEntity?

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    // 도메인 특화 조회 메서드
    fun findByIdAndIsActiveTrue(id: Long): PipelineEntity?

    fun findAllByIsActive(isActive: Boolean): List<PipelineEntity>

    fun findByOwner(owner: String): List<PipelineEntity>

    fun findByStatus(status: PipelineStatus): List<PipelineEntity>

    // 페이지네이션 조회 (Spring Data Page 지원)
    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    fun findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity>

    fun findByStatusOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    fun findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity>

    // 상태 업데이트
    fun updateStatus(
        id: Long,
        status: PipelineStatus,
    ): Boolean

    fun toggleActive(id: Long): Boolean

    // 통계 및 집계
    fun countByStatus(status: PipelineStatus): Long

    fun countByOwner(owner: String): Long
}
