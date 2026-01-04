package com.github.lambda.infra.repository.adhoc

import com.github.lambda.common.enums.ExecutionStatus
import com.github.lambda.domain.entity.adhoc.AdHocExecutionEntity
import com.github.lambda.domain.repository.adhoc.AdHocExecutionRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Ad-Hoc Execution JPA Repository 구현
 *
 * Domain AdHocExecutionRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("adHocExecutionRepositoryJpa")
interface AdHocExecutionRepositoryJpaImpl :
    AdHocExecutionRepositoryJpa,
    JpaRepository<AdHocExecutionEntity, String> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByQueryId(queryId: String): AdHocExecutionEntity?

    override fun existsByQueryId(queryId: String): Boolean

    @Modifying
    @Query("DELETE FROM AdHocExecutionEntity e WHERE e.queryId = :queryId")
    override fun deleteByQueryId(
        @Param("queryId") queryId: String,
    ): Long

    // 사용자별 조회
    override fun findByUserId(userId: String): List<AdHocExecutionEntity>

    override fun findByUserIdOrderByCreatedAtDesc(
        userId: String,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity>

    // 상태별 조회
    override fun findByStatus(status: ExecutionStatus): List<AdHocExecutionEntity>

    override fun findByUserIdAndStatus(
        userId: String,
        status: ExecutionStatus,
    ): List<AdHocExecutionEntity>

    // 엔진별 조회
    override fun findByEngine(engine: String): List<AdHocExecutionEntity>

    override fun findByUserIdAndEngine(
        userId: String,
        engine: String,
    ): List<AdHocExecutionEntity>

    // 만료된 결과 조회 (정리용)
    override fun findByExpiresAtBeforeAndStatus(
        expiresAt: LocalDateTime,
        status: ExecutionStatus,
    ): List<AdHocExecutionEntity>

    // 통계
    override fun countByUserId(userId: String): Long

    override fun countByUserIdAndStatus(
        userId: String,
        status: ExecutionStatus,
    ): Long

    @Query(
        """
        SELECT COUNT(e) FROM AdHocExecutionEntity e
        WHERE e.userId = :userId AND e.createdAt > :createdAt
        """,
    )
    override fun countByUserIdAndCreatedAtAfter(
        @Param("userId") userId: String,
        @Param("createdAt") createdAt: LocalDateTime,
    ): Long

    // 전체 목록 (페이지네이션)
    override fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdHocExecutionEntity>

    // 최근 실행 내역
    @Query(
        """
        SELECT e FROM AdHocExecutionEntity e
        WHERE e.createdAt > :createdAt
        ORDER BY e.createdAt DESC
        """,
    )
    override fun findByCreatedAtAfterOrderByCreatedAtDesc(
        @Param("createdAt") createdAt: LocalDateTime,
        pageable: Pageable,
    ): Page<AdHocExecutionEntity>
}
