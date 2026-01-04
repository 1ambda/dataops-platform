package com.github.lambda.infra.repository.quality

import com.github.lambda.common.enums.ResourceType
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import com.github.lambda.domain.repository.quality.QualitySpecRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Quality Spec JPA Repository 구현 인터페이스
 *
 * Domain QualitySpecRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("qualitySpecRepositoryJpa")
interface QualitySpecRepositoryJpaImpl :
    QualitySpecRepositoryJpa,
    JpaRepository<QualitySpecEntity, String> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByName(name: String): QualitySpecEntity?

    override fun existsByName(name: String): Boolean

    override fun deleteByName(name: String): Long

    // 리소스 기반 조회
    override fun findByResourceName(resourceName: String): List<QualitySpecEntity>

    override fun findByResourceType(resourceType: ResourceType): List<QualitySpecEntity>

    override fun findByResourceNameAndResourceType(
        resourceName: String,
        resourceType: ResourceType,
    ): List<QualitySpecEntity>

    // 소유자 기반 조회
    override fun findByOwner(owner: String): List<QualitySpecEntity>

    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    // 태그 기반 조회
    override fun findByTagsContaining(tag: String): List<QualitySpecEntity>

    // 활성화 상태 기반 조회
    override fun findByEnabled(enabled: Boolean): List<QualitySpecEntity>

    override fun findByEnabledOrderByUpdatedAtDesc(
        enabled: Boolean,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    // 전체 목록 조회
    override fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<QualitySpecEntity>

    // 통계 및 집계
    override fun countByOwner(owner: String): Long

    override fun countByResourceType(resourceType: ResourceType): Long

    override fun countByEnabled(enabled: Boolean): Long

    // 이름 패턴 검색
    override fun findByNameContainingIgnoreCase(namePattern: String): List<QualitySpecEntity>

    override fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<QualitySpecEntity>

    // 스케줄 관련 조회
    override fun findByScheduleCronIsNotNull(): List<QualitySpecEntity>

    override fun findByScheduleCronIsNull(): List<QualitySpecEntity>

    // 팀별 조회
    override fun findByTeam(team: String): List<QualitySpecEntity>

    override fun findByTeamIsNull(): List<QualitySpecEntity>

    // 커스텀 업데이트 쿼리
    @Modifying
    @Query("UPDATE QualitySpecEntity qs SET qs.updatedAt = :updatedAt WHERE qs.name = :name")
    override fun updateLastAccessedByName(
        @Param("name") name: String,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    // 복잡한 검색 쿼리
    @Query(
        """
        SELECT qs FROM QualitySpecEntity qs
        WHERE (:resourceType IS NULL OR qs.resourceType = :resourceType)
        AND (:resourceName IS NULL OR qs.resourceName = :resourceName)
        AND (:owner IS NULL OR LOWER(qs.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:team IS NULL OR qs.team = :team)
        AND (:tag IS NULL OR :tag MEMBER OF qs.tags)
        AND (:enabled IS NULL OR qs.enabled = :enabled)
        AND (:search IS NULL OR
             LOWER(qs.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(qs.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY qs.updatedAt DESC
        """,
    )
    override fun findByComplexFilters(
        @Param("resourceType") resourceType: ResourceType?,
        @Param("resourceName") resourceName: String?,
        @Param("owner") owner: String?,
        @Param("team") team: String?,
        @Param("tag") tag: String?,
        @Param("enabled") enabled: Boolean?,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    @Query(
        """
        SELECT COUNT(qs) FROM QualitySpecEntity qs
        WHERE (:resourceType IS NULL OR qs.resourceType = :resourceType)
        AND (:resourceName IS NULL OR qs.resourceName = :resourceName)
        AND (:owner IS NULL OR LOWER(qs.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:team IS NULL OR qs.team = :team)
        AND (:tag IS NULL OR :tag MEMBER OF qs.tags)
        AND (:enabled IS NULL OR qs.enabled = :enabled)
        AND (:search IS NULL OR
             LOWER(qs.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(qs.description) LIKE LOWER(CONCAT('%', :search, '%')))
        """,
    )
    override fun countByComplexFilters(
        @Param("resourceType") resourceType: ResourceType?,
        @Param("resourceName") resourceName: String?,
        @Param("owner") owner: String?,
        @Param("team") team: String?,
        @Param("tag") tag: String?,
        @Param("enabled") enabled: Boolean?,
        @Param("search") search: String?,
    ): Long

    @Query(
        """
        SELECT qs FROM QualitySpecEntity qs
        WHERE qs.updatedAt >= :since
        ORDER BY qs.updatedAt DESC
        """,
    )
    override fun findRecentlyUpdated(
        @Param("since") since: LocalDateTime,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    @Query(
        """
        SELECT qs FROM QualitySpecEntity qs
        WHERE qs.enabled = true
        AND qs.scheduleCron IS NOT NULL
        AND (:cronPattern IS NULL OR qs.scheduleCron LIKE CONCAT('%', :cronPattern, '%'))
        ORDER BY qs.updatedAt DESC
        """,
    )
    override fun findActiveScheduledQualitySpecs(
        @Param("cronPattern") cronPattern: String?,
    ): List<QualitySpecEntity>
}
