package com.github.lambda.infra.repository.dataset

import com.github.lambda.domain.entity.dataset.DatasetEntity
import com.github.lambda.domain.repository.dataset.DatasetRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * Dataset JPA Repository 구현 인터페이스
 *
 * Domain DatasetRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("datasetRepositoryJpa")
interface DatasetRepositoryJpaImpl :
    DatasetRepositoryJpa,
    JpaRepository<DatasetEntity, String> {
    // 기본 CRUD 작업 (save는 JpaRepository와 시그니처가 동일하므로 자동으로 맞춰짐)
    // override fun save(dataset: DatasetEntity): DatasetEntity - JpaRepository에서 자동 제공

    // 도메인 특화 조회 메서드 (Spring Data JPA auto-implements)
    override fun findByName(name: String): DatasetEntity?

    override fun existsByName(name: String): Boolean

    override fun deleteByName(name: String): Long

    override fun findByOwner(owner: String): List<DatasetEntity>

    // 페이지네이션 조회
    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<DatasetEntity>

    // 태그 기반 조회 (기본적인 단일 태그 포함 검사)
    override fun findByTagsContaining(tag: String): List<DatasetEntity>

    // 전체 목록 조회 (페이지네이션)
    override fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<DatasetEntity>

    // 이름 패턴 검색 (단순 LIKE 검색)
    override fun findByNameContainingIgnoreCase(namePattern: String): List<DatasetEntity>

    override fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<DatasetEntity>

    // 통계 및 집계
    override fun countByOwner(owner: String): Long

    // 커스텀 업데이트 쿼리 (원시 메소드만 제공, 비즈니스 로직은 service layer에서)
    @Modifying
    @Query("UPDATE DatasetEntity d SET d.updatedAt = :updatedAt WHERE d.name = :name")
    fun updateLastAccessedByName(
        @Param("name") name: String,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int

    // 복잡한 검색 쿼리
    @Query(
        """
        SELECT d FROM DatasetEntity d
        WHERE (:owner IS NULL OR LOWER(d.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:tag IS NULL OR :tag MEMBER OF d.tags)
        AND (:search IS NULL OR
             LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(d.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY d.updatedAt DESC
    """,
    )
    fun findByComplexFilters(
        @Param("owner") owner: String?,
        @Param("tag") tag: String?,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<DatasetEntity>

    @Query(
        """
        SELECT COUNT(d) FROM DatasetEntity d
        WHERE (:owner IS NULL OR LOWER(d.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:tag IS NULL OR :tag MEMBER OF d.tags)
        AND (:search IS NULL OR
             LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(d.description) LIKE LOWER(CONCAT('%', :search, '%')))
    """,
    )
    fun countByComplexFilters(
        @Param("owner") owner: String?,
        @Param("tag") tag: String?,
        @Param("search") search: String?,
    ): Long
}
