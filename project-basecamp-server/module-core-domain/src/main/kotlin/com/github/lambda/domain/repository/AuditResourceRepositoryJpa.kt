package com.github.lambda.domain.repository

import com.github.lambda.domain.model.audit.AuditResourceEntity
import java.time.LocalDateTime
import java.util.*

/**
 * 리소스 감사 Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface AuditResourceRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 다른 시그니처로 충돌 방지)
    fun save(auditResource: AuditResourceEntity): AuditResourceEntity

    // findById는 JpaRepository에서 제공하므로 제거
    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean

    fun findAll(): List<AuditResourceEntity>

    // 도메인 특화 조회 메서드
    fun findByResourceId(resourceId: Long): List<AuditResourceEntity>

    fun findByUserId(userId: Long): List<AuditResourceEntity>

    fun findByResourceIdAndUserId(
        resourceId: Long,
        userId: Long,
    ): List<AuditResourceEntity>

    // 시간 기반 조회
    fun findByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditResourceEntity>

    fun findByResourceIdAndCreatedAtBetween(
        resourceId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): List<AuditResourceEntity>

    // 페이지네이션 조회
    fun findByResourceIdWithPagination(
        resourceId: Long,
        page: Int,
        size: Int,
    ): List<AuditResourceEntity>

    fun findByUserIdWithPagination(
        userId: Long,
        page: Int,
        size: Int,
    ): List<AuditResourceEntity>

    // 통계 및 집계
    fun countByResourceId(resourceId: Long): Long

    fun countByUserId(userId: Long): Long

    fun countByCreatedAtBetween(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): Long
}
