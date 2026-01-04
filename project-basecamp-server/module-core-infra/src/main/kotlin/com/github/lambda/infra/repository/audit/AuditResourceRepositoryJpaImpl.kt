package com.github.lambda.infra.repository.audit

import com.github.lambda.domain.entity.audit.AuditResourceEntity
import com.github.lambda.domain.repository.audit.AuditResourceRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 리소스 감사 JPA Repository 구현 인터페이스
 *
 * Domain AuditResourceRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * AuditResourceRepositoryJpaSpringData와 AuditResourceRepositoryJpaImpl을 하나의 인터페이스로 통합했습니다.
 */
@Repository("auditResourceRepositoryJpa")
interface AuditResourceRepositoryJpaImpl :
    AuditResourceRepositoryJpa,
    JpaRepository<AuditResourceEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들 (기존 SpringData에서 이동)
    // 리소스별 조회
    override fun findByResourceId(resourceId: Long): List<AuditResourceEntity>

    fun findByResourceIdOrderByCreatedAtDesc(
        resourceId: Long,
        pageable: Pageable,
    ): Page<AuditResourceEntity>

    // 사용자별 조회
    override fun findByUserId(userId: Long): List<AuditResourceEntity>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<AuditResourceEntity>

    // 복합 조회
    override fun findByResourceIdAndUserId(
        resourceId: Long,
        userId: Long,
    ): List<AuditResourceEntity>

    // 시간 기반 조회
    override fun findByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditResourceEntity>

    override fun findByResourceIdAndCreatedAtBetween(
        resourceId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditResourceEntity>

    // 통계 및 집계
    override fun countByResourceId(resourceId: Long): Long

    override fun countByUserId(userId: Long): Long

    override fun countByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): Long

    // AuditResourceRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun findByResourceIdWithPagination(
        resourceId: Long,
        page: Int,
        size: Int,
    ): List<AuditResourceEntity> = findByResourceIdOrderByCreatedAtDesc(resourceId, PageRequest.of(page, size)).content

    override fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<AuditResourceEntity> = findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).content
}
