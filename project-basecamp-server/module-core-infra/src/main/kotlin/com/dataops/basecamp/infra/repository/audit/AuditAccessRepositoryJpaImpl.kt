package com.dataops.basecamp.infra.repository.audit

import com.dataops.basecamp.domain.entity.audit.AuditAccessEntity
import com.dataops.basecamp.domain.repository.audit.AuditAccessRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * 접근 감사 JPA Repository 구현 인터페이스
 *
 * Domain AuditAccessRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 직접 상속합니다.
 * AuditAccessRepositoryJpaSpringData와 AuditAccessRepositoryJpaImpl을 하나의 인터페이스로 통합했습니다.
 */
@Repository("auditAccessRepositoryJpa")
interface AuditAccessRepositoryJpaImpl :
    AuditAccessRepositoryJpa,
    JpaRepository<AuditAccessEntity, Long> {
    // Spring Data JPA 커스텀 쿼리 메서드들 (기존 SpringData에서 이동)
    // 사용자별 조회
    override fun findByUserId(userId: Long): List<AuditAccessEntity>

    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<AuditAccessEntity>

    // 접근 타입별 조회
    override fun findByAccessType(accessType: String): List<AuditAccessEntity>

    // 복합 조회
    override fun findByUserIdAndAccessType(
        userId: Long,
        accessType: String,
    ): List<AuditAccessEntity>

    // 시간 기반 조회
    override fun findByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditAccessEntity>

    override fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditAccessEntity>

    // 통계 및 집계
    override fun countByUserId(userId: Long): Long

    override fun countByAccessType(accessType: String): Long

    override fun countByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): Long

    // AuditAccessRepositoryJpa 인터페이스의 도메인 특화 메서드들을 구현

    // findById는 JpaRepository에서 제공되므로 별도 구현 불필요

    override fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<AuditAccessEntity> = findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size)).content

    override fun findByAccessTypeWithPagination(
        accessType: String,
        page: Int,
        size: Int,
    ): List<AuditAccessEntity> = findByAccessType(accessType).drop(page * size).take(size)
}
