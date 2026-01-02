package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.QualityTestEntity
import com.github.lambda.domain.model.quality.Severity
import com.github.lambda.domain.model.quality.TestType
import com.github.lambda.domain.repository.QualityTestRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Quality Test JPA Repository Implementation Interface
 *
 * Implements the domain QualityTestRepositoryJpa interface by extending JpaRepository directly.
 * This simplified pattern combines domain interface and Spring Data JPA into one interface.
 * Follows Pure Hexagonal Architecture pattern (same as MetricRepositoryJpaImpl).
 */
@Repository("qualityTestRepositoryJpa")
interface QualityTestRepositoryJpaImpl :
    QualityTestRepositoryJpa,
    JpaRepository<QualityTestEntity, Long> {

    // Domain-specific queries (Spring Data JPA auto-implements these)
    override fun findByIdOrNull(id: Long): QualityTestEntity?
    override fun existsById(id: Long): Boolean
    override fun deleteById(id: Long)

    // Spec별 테스트 조회
    override fun findBySpecName(specName: String): List<QualityTestEntity>
    override fun findBySpecNameOrderByName(specName: String): List<QualityTestEntity>
    override fun findBySpecNameAndEnabled(specName: String, enabled: Boolean): List<QualityTestEntity>

    // 이름 기반 조회
    override fun findByName(name: String): List<QualityTestEntity>
    override fun findByNameContainingIgnoreCase(namePattern: String): List<QualityTestEntity>

    // 테스트 타입별 조회
    override fun findByTestType(testType: TestType): List<QualityTestEntity>
    override fun findByTestTypeOrderByName(testType: TestType, pageable: Pageable): Page<QualityTestEntity>

    // 컬럼 기반 조회 (Spring Data JPA로 auto-implement하기 위해 메서드명 조정)
    @Query("SELECT qt FROM QualityTestEntity qt WHERE :column MEMBER OF qt.targetColumns")
    override fun findByColumn(@Param("column") column: String): List<QualityTestEntity>
    
    @Query("SELECT qt FROM QualityTestEntity qt WHERE :column MEMBER OF qt.targetColumns")
    override fun findByColumnsContaining(@Param("column") column: String): List<QualityTestEntity>

    // 심각도별 조회
    override fun findBySeverity(severity: Severity): List<QualityTestEntity>
    override fun findBySeverityOrderByName(severity: Severity, pageable: Pageable): Page<QualityTestEntity>

    // 활성화 상태별 조회
    override fun findByEnabled(enabled: Boolean): List<QualityTestEntity>
    override fun findByEnabledOrderByName(enabled: Boolean, pageable: Pageable): Page<QualityTestEntity>

    // 전체 목록 조회
    override fun findAllByOrderByName(pageable: Pageable): Page<QualityTestEntity>

    // 통계 및 집계
    override fun countByTestType(testType: TestType): Long
    override fun countBySeverity(severity: Severity): Long
    override fun countByEnabled(enabled: Boolean): Long
    override fun countBySpecName(specName: String): Long

    // 설명 패턴 검색
    override fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<QualityTestEntity>

    // 복합 조건 조회
    override fun findBySpecNameAndTestType(specName: String, testType: TestType): List<QualityTestEntity>
    override fun findBySpecNameAndSeverity(specName: String, severity: Severity): List<QualityTestEntity>
    override fun findByTestTypeAndEnabled(testType: TestType, enabled: Boolean): List<QualityTestEntity>
    override fun findByTestTypeAndSeverity(testType: TestType, severity: Severity): List<QualityTestEntity>

    // 특정 Spec의 활성화된 테스트만 조회
    @Query("SELECT qt FROM QualityTestEntity qt WHERE qt.spec.name = :specName AND qt.enabled = true ORDER BY qt.name")
    override fun findBySpecNameAndEnabledOrderByName(@Param("specName") specName: String): List<QualityTestEntity>

    // 특정 컬럼에 대한 모든 테스트 조회
    @Query("SELECT qt FROM QualityTestEntity qt WHERE :column MEMBER OF qt.targetColumns")
    override fun findByColumnOrColumnsContaining(@Param("column") column: String): List<QualityTestEntity>

    // 커스텀 업데이트 쿼리
    @Modifying
    @Query("UPDATE QualityTestEntity qt SET qt.enabled = :enabled WHERE qt.spec.name = :specName")
    fun updateEnabledBySpecName(@Param("specName") specName: String, @Param("enabled") enabled: Boolean): Int

    @Modifying
    @Query("UPDATE QualityTestEntity qt SET qt.enabled = :enabled WHERE qt.testType = :testType")
    fun updateEnabledByTestType(@Param("testType") testType: TestType, @Param("enabled") enabled: Boolean): Int

    // 복잡한 검색 쿼리
    @Query("""
        SELECT qt FROM QualityTestEntity qt
        WHERE (:specName IS NULL OR qt.spec.name = :specName)
        AND (:testType IS NULL OR qt.testType = :testType)
        AND (:severity IS NULL OR qt.severity = :severity)
        AND (:enabled IS NULL OR qt.enabled = :enabled)
        AND (:column IS NULL OR :column MEMBER OF qt.targetColumns)
        AND (:search IS NULL OR
             LOWER(qt.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(qt.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY qt.name
        """)
    fun findByComplexFilters(
        @Param("specName") specName: String?,
        @Param("testType") testType: TestType?,
        @Param("severity") severity: Severity?,
        @Param("enabled") enabled: Boolean?,
        @Param("column") column: String?,
        @Param("search") search: String?,
        pageable: Pageable,
    ): Page<QualityTestEntity>

    @Query("""
        SELECT COUNT(qt) FROM QualityTestEntity qt
        WHERE (:specName IS NULL OR qt.spec.name = :specName)
        AND (:testType IS NULL OR qt.testType = :testType)
        AND (:severity IS NULL OR qt.severity = :severity)
        AND (:enabled IS NULL OR qt.enabled = :enabled)
        AND (:column IS NULL OR :column MEMBER OF qt.targetColumns)
        AND (:search IS NULL OR
             LOWER(qt.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(qt.description) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    fun countByComplexFilters(
        @Param("specName") specName: String?,
        @Param("testType") testType: TestType?,
        @Param("severity") severity: Severity?,
        @Param("enabled") enabled: Boolean?,
        @Param("column") column: String?,
        @Param("search") search: String?,
    ): Long

    // 테스트 통계 조회
    @Query("""
        SELECT
            qt.testType as testType,
            qt.severity as severity,
            COUNT(*) as count,
            SUM(CASE WHEN qt.enabled = true THEN 1 ELSE 0 END) as enabledCount
        FROM QualityTestEntity qt
        WHERE qt.spec.name = :specName
        GROUP BY qt.testType, qt.severity
        """)
    fun getTestStatisticsBySpec(@Param("specName") specName: String): List<Map<String, Any>>

    // 컬럼별 테스트 개수 조회
    @Query("""
        SELECT
            col as columnName,
            COUNT(*) as testCount
        FROM QualityTestEntity qt
        JOIN qt.targetColumns col
        WHERE qt.spec.name = :specName
        GROUP BY col
        ORDER BY testCount DESC
        """)
    fun getTestCountByColumn(@Param("specName") specName: String): List<Map<String, Any>>
}
