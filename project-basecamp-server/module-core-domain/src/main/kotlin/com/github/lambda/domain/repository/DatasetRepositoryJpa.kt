package com.github.lambda.domain.repository

import com.github.lambda.domain.model.dataset.DataFormat
import com.github.lambda.domain.model.dataset.Dataset
import com.github.lambda.domain.model.dataset.DatasetType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 데이터셋 리포지토리
 */
@Repository
interface DatasetRepositoryJpa : JpaRepository<Dataset, Long> {
    /**
     * 이름으로 데이터셋 조회
     */
    fun findByName(name: String): Dataset?

    /**
     * 소유자로 데이터셋 목록 조회
     */
    fun findByOwnerAndIsActiveTrue(
        owner: String,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 데이터셋 유형으로 조회
     */
    fun findByTypeAndIsActiveTrue(
        type: DatasetType,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 데이터 형식으로 조회
     */
    fun findByFormatAndIsActiveTrue(
        format: DataFormat,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 소유자와 유형으로 조회
     */
    fun findByOwnerAndTypeAndIsActiveTrue(
        owner: String,
        type: DatasetType,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 활성화된 데이터셋 목록 조회
     */
    fun findByIsActiveTrue(pageable: Pageable): Page<Dataset>

    /**
     * 데이터셋 이름 존재 여부 확인
     */
    fun existsByNameAndIsActiveTrue(name: String): Boolean

    /**
     * 소유자별 데이터셋 개수 조회
     */
    @Query("SELECT COUNT(d) FROM Dataset d WHERE d.owner = :owner AND d.isActive = true")
    fun countByOwnerAndIsActiveTrue(
        @Param("owner") owner: String,
    ): Long

    /**
     * 유형별 데이터셋 개수 조회
     */
    @Query(
        """
        SELECT d.type, COUNT(d)
        FROM Dataset d
        WHERE d.isActive = true
        GROUP BY d.type
    """,
    )
    fun countByTypeAndIsActiveTrue(): List<Array<Any>>

    /**
     * 태그로 데이터셋 검색 (JSON 필드 검색)
     */
    @Query(
        """
        SELECT * FROM datasets
        WHERE is_active = true
        AND JSON_CONTAINS(tags, JSON_QUOTE(:tag))
    """,
        nativeQuery = true,
    )
    fun findByTagsContaining(
        @Param("tag") tag: String,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 키워드로 데이터셋 검색 (이름, 설명에서 검색)
     */
    @Query(
        """
        SELECT d FROM Dataset d
        WHERE d.isActive = true
        AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """,
    )
    fun searchByKeyword(
        @Param("keyword") keyword: String,
        pageable: Pageable,
    ): Page<Dataset>

    /**
     * 데이터셋 활성화 상태 업데이트
     */
    @Modifying
    @Query("UPDATE Dataset d SET d.isActive = :isActive WHERE d.id = :id")
    fun updateActiveStatus(
        @Param("id") id: Long,
        @Param("isActive") isActive: Boolean,
    ): Int

    /**
     * 스키마 정의 업데이트
     */
    @Modifying
    @Query("UPDATE Dataset d SET d.schemaDefinition = :schemaDefinition WHERE d.id = :id")
    fun updateSchemaDefinition(
        @Param("id") id: Long,
        @Param("schemaDefinition") schemaDefinition: String,
    ): Int
}
