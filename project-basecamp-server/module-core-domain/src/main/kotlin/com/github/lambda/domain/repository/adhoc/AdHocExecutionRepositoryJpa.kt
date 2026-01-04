package com.github.lambda.domain.repository.adhoc

import com.github.lambda.common.enums.ExecutionStatus
import com.github.lambda.domain.entity.adhoc.AdHocExecutionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Ad-Hoc Execution Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Ad-Hoc 쿼리 실행 기록에 대한 기본 CRUD 작업과 도메인 특화 쿼리를 정의합니다.
 */
interface AdHocExecutionRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository에서 상속)
    fun save(execution: AdHocExecutionEntity): AdHocExecutionEntity

    // 커스텀 조회
    fun findByQueryId(queryId: String): AdHocExecutionEntity?

    fun existsByQueryId(queryId: String): Boolean

    fun deleteByQueryId(queryId: String): Long

    // 사용자별 조회
    fun findByUserId(userId: String): List<AdHocExecutionEntity>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: String,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity>

    // 상태별 조회
    fun findByStatus(status: ExecutionStatus): List<AdHocExecutionEntity>

    fun findByUserIdAndStatus(
        userId: String,
        status: ExecutionStatus,
    ): List<AdHocExecutionEntity>

    // 엔진별 조회
    fun findByEngine(engine: String): List<AdHocExecutionEntity>

    fun findByUserIdAndEngine(
        userId: String,
        engine: String,
    ): List<AdHocExecutionEntity>

    // 만료된 결과 조회 (정리용)
    fun findByExpiresAtBeforeAndStatus(
        expiresAt: LocalDateTime,
        status: ExecutionStatus,
    ): List<AdHocExecutionEntity>

    // 통계
    fun countByUserId(userId: String): Long

    fun countByUserIdAndStatus(
        userId: String,
        status: ExecutionStatus,
    ): Long

    fun countByUserIdAndCreatedAtAfter(
        userId: String,
        createdAt: LocalDateTime,
    ): Long

    // 전체 목록 (페이지네이션)
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdHocExecutionEntity>

    // 최근 실행 내역
    fun findByCreatedAtAfterOrderByCreatedAtDesc(
        createdAt: LocalDateTime,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity>
}
