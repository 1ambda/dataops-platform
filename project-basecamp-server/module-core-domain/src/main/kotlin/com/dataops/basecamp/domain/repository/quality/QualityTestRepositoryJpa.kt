package com.dataops.basecamp.domain.repository.quality

import com.dataops.basecamp.common.enums.Severity
import com.dataops.basecamp.common.enums.TestType
import com.dataops.basecamp.domain.entity.quality.QualityTestEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Quality Test Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * QualityTest에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface QualityTestRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 충돌하지 않는 메서드들)
    fun save(qualityTest: QualityTestEntity): QualityTestEntity

    // 도메인 특화 조회 메서드 - ID 기반
    fun findByIdOrNull(id: Long): QualityTestEntity?

    fun existsById(id: Long): Boolean

    fun deleteById(id: Long)

    // Spec별 테스트 조회
    fun findBySpecName(specName: String): List<QualityTestEntity>

    fun findBySpecNameOrderByName(specName: String): List<QualityTestEntity>

    fun findBySpecNameAndEnabled(
        specName: String,
        enabled: Boolean,
    ): List<QualityTestEntity>

    // 이름 기반 조회
    fun findByName(name: String): List<QualityTestEntity>

    fun findByNameContainingIgnoreCase(namePattern: String): List<QualityTestEntity>

    // 테스트 타입별 조회
    fun findByTestType(testType: TestType): List<QualityTestEntity>

    fun findByTestTypeOrderByName(
        testType: TestType,
        pageable: Pageable,
    ): Page<QualityTestEntity>

    // 컬럼 기반 조회
    fun findByColumn(column: String): List<QualityTestEntity>

    fun findByColumnsContaining(column: String): List<QualityTestEntity>

    // 심각도별 조회
    fun findBySeverity(severity: Severity): List<QualityTestEntity>

    fun findBySeverityOrderByName(
        severity: Severity,
        pageable: Pageable,
    ): Page<QualityTestEntity>

    // 활성화 상태별 조회
    fun findByEnabled(enabled: Boolean): List<QualityTestEntity>

    fun findByEnabledOrderByName(
        enabled: Boolean,
        pageable: Pageable,
    ): Page<QualityTestEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByName(pageable: Pageable): Page<QualityTestEntity>

    // 통계 및 집계
    fun countByTestType(testType: TestType): Long

    fun countBySeverity(severity: Severity): Long

    fun countByEnabled(enabled: Boolean): Long

    fun countBySpecName(specName: String): Long

    fun count(): Long

    // 설명 패턴 검색
    fun findByDescriptionContainingIgnoreCase(descriptionPattern: String): List<QualityTestEntity>

    // 복합 조건 조회
    fun findBySpecNameAndTestType(
        specName: String,
        testType: TestType,
    ): List<QualityTestEntity>

    fun findBySpecNameAndSeverity(
        specName: String,
        severity: Severity,
    ): List<QualityTestEntity>

    fun findByTestTypeAndEnabled(
        testType: TestType,
        enabled: Boolean,
    ): List<QualityTestEntity>

    fun findByTestTypeAndSeverity(
        testType: TestType,
        severity: Severity,
    ): List<QualityTestEntity>

    // 특정 Spec의 활성화된 테스트만 조회
    fun findBySpecNameAndEnabledOrderByName(specName: String): List<QualityTestEntity>

    // 특정 컬럼에 대한 모든 테스트 조회
    fun findByColumnOrColumnsContaining(column: String): List<QualityTestEntity>

    // SpecId 기반 조회
    fun findBySpecId(specId: Long): List<QualityTestEntity>

    fun findBySpecIdAndEnabled(
        specId: Long,
        enabled: Boolean,
    ): List<QualityTestEntity>

    fun deleteBySpecId(specId: Long)
}
