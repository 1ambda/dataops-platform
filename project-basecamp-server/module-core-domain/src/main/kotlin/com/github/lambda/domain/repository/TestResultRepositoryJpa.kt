package com.github.lambda.domain.repository

import com.github.lambda.domain.model.quality.TestResultEntity
import com.github.lambda.domain.model.quality.TestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Test Result Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * TestResult에 대한 기본 CRUD 작업과 도메인 특화 쿼리 작업을 정의합니다.
 * JpaRepository와 동일한 시그니처를 사용하여 충돌을 방지합니다.
 */
interface TestResultRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository와 충돌하지 않는 메서드들)
    fun save(testResult: TestResultEntity): TestResultEntity

    // 도메인 특화 조회 메서드 - ID 기반
    fun findByIdOrNull(id: Long): TestResultEntity?

    fun existsById(id: Long): Boolean

    fun deleteById(id: Long)

    // Run별 결과 조회
    fun findByRunRunId(runId: String): List<TestResultEntity>

    fun findByRunRunIdOrderByExecutedAtDesc(
        runId: String,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // Test별 결과 조회
    fun findByTestName(testName: String): List<TestResultEntity>

    fun findByTestNameOrderByExecutedAtDesc(
        testName: String,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // 상태별 조회
    fun findByStatus(status: TestStatus): List<TestResultEntity>

    fun findByStatusOrderByExecutedAtDesc(
        status: TestStatus,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // Run과 Test의 복합 조회
    fun findByRunRunIdAndTestName(
        runId: String,
        testName: String,
    ): List<TestResultEntity>

    fun findByRunRunIdAndStatus(
        runId: String,
        status: TestStatus,
    ): List<TestResultEntity>

    // 전체 목록 조회 (페이지네이션)
    fun findAllByOrderByExecutedAtDesc(pageable: Pageable): Page<TestResultEntity>

    // 통계 및 집계
    fun countByStatus(status: TestStatus): Long

    fun countByRunRunId(runId: String): Long

    fun countByTestName(testName: String): Long

    fun count(): Long

    // 실행 시간 기반 조회
    fun findByDurationSecondsGreaterThan(durationSeconds: Double): List<TestResultEntity>

    fun findByDurationSecondsBetween(
        minDuration: Double,
        maxDuration: Double,
    ): List<TestResultEntity>

    // 에러 메시지 기반 검색
    fun findByErrorMessageContainingIgnoreCase(errorPattern: String): List<TestResultEntity>

    fun findByErrorMessageIsNotNull(): List<TestResultEntity>

    fun findByErrorMessageIsNull(): List<TestResultEntity>

    // 상세 정보 검색
    fun findByDetailsContainingIgnoreCase(detailsPattern: String): List<TestResultEntity>

    // 최근 실행 결과 조회
    fun findTop10ByOrderByExecutedAtDesc(): List<TestResultEntity>

    // 특정 Run의 실패한 테스트 결과들
    fun findByRunRunIdAndStatusIn(
        runId: String,
        statuses: List<TestStatus>,
    ): List<TestResultEntity>

    // 특정 Run의 성공/실패 개수 조회
    fun countByRunRunIdAndStatus(
        runId: String,
        status: TestStatus,
    ): Long
}
