package com.github.lambda.domain.repository.quality

import com.github.lambda.common.enums.ResourceType
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Quality Spec Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * QualitySpec에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface QualitySpecRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 충돌하지 않는 메서드들)
    fun save(qualitySpec: QualitySpecEntity): QualitySpecEntity

    // 도메인 특화 조회 메서드 - 이름 기반
    fun findByName(name: String): QualitySpecEntity?

    fun existsByName(name: String): Boolean

    fun deleteByName(name: String): Long

    // 리소스 기반 조회
    fun findByResourceName(resourceName: String): List<QualitySpecEntity>

    fun findByResourceType(resourceType: ResourceType): List<QualitySpecEntity>

    fun findByResourceNameAndResourceType(
        resourceName: String,
        resourceType: ResourceType,
    ): List<QualitySpecEntity>

    // 소유자 기반 조회
    fun findByOwner(owner: String): List<QualitySpecEntity>

    fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    // 태그 기반 조회 (기본적인 단일 태그 포함 검사)
    fun findByTagsContaining(tag: String): List<QualitySpecEntity>

    // 활성화 상태 기반 조회
    fun findByEnabled(enabled: Boolean): List<QualitySpecEntity>

    fun findByEnabledOrderByUpdatedAtDesc(
        enabled: Boolean,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<QualitySpecEntity>

    // 통계 및 집계
    fun countByOwner(owner: String): Long

    fun countByResourceType(resourceType: ResourceType): Long

    fun countByEnabled(enabled: Boolean): Long

    fun count(): Long

    // 이름 패턴 검색 (단순 LIKE 검색)
    fun findByNameContainingIgnoreCase(namePattern: String): List<QualitySpecEntity>

    fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<QualitySpecEntity>

    // 스케줄이 설정된 품질 스펙 조회
    fun findByScheduleCronIsNotNull(): List<QualitySpecEntity>

    fun findByScheduleCronIsNull(): List<QualitySpecEntity>

    // 팀별 조회
    fun findByTeam(team: String): List<QualitySpecEntity>

    fun findByTeamIsNull(): List<QualitySpecEntity>

    // 커스텀 업데이트 쿼리
    fun updateLastAccessedByName(
        name: String,
        updatedAt: java.time.LocalDateTime,
    ): Int

    // 복잡한 검색 쿼리
    fun findByComplexFilters(
        resourceType: ResourceType?,
        resourceName: String?,
        owner: String?,
        team: String?,
        tag: String?,
        enabled: Boolean?,
        search: String?,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    fun countByComplexFilters(
        resourceType: ResourceType?,
        resourceName: String?,
        owner: String?,
        team: String?,
        tag: String?,
        enabled: Boolean?,
        search: String?,
    ): Long

    // 최근 수정된 스펙 조회
    fun findRecentlyUpdated(
        since: java.time.LocalDateTime,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    // 활성 스케줄 스펙 조회
    fun findActiveScheduledQualitySpecs(cronPattern: String?): List<QualitySpecEntity>
}
