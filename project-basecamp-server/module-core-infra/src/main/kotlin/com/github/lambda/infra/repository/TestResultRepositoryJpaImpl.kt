package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.quality.TestResultEntity
import com.github.lambda.domain.model.quality.TestStatus
import com.github.lambda.domain.repository.TestResultRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Test Result JPA Repository 구현 인터페이스
 *
 * Domain TestResultRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("testResultRepositoryJpa")
interface TestResultRepositoryJpaImpl :
    TestResultRepositoryJpa,
    JpaRepository<TestResultEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository와 시그니처가 동일하므로 자동으로 맞춰짐)
    // override fun save(testResult: TestResultEntity): TestResultEntity - JpaRepository에서 자동 제공

    // 도메인 특화 조회 메서드 (Spring Data JPA auto-implements)
    override fun findByIdOrNull(id: Long): TestResultEntity? = findById(id).orElse(null)

    override fun existsById(id: Long): Boolean

    override fun deleteById(id: Long)

    // Run별 결과 조회 (explicit JOIN with QualityRunEntity)
    @Query("SELECT tr FROM TestResultEntity tr JOIN QualityRunEntity r ON tr.runId = r.id WHERE r.runId = :runId")
    override fun findByRunRunId(
        @Param("runId") runId: String,
    ): List<TestResultEntity>

    @Query(
        """
        SELECT tr FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId
        ORDER BY tr.createdAt DESC
        """,
    )
    override fun findByRunRunIdOrderByCreatedAtDesc(
        @Param("runId") runId: String,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // Test별 결과 조회 (use testName field directly)
    @Query("SELECT tr FROM TestResultEntity tr WHERE tr.testName = :testName")
    override fun findByTestName(
        @Param("testName") testName: String,
    ): List<TestResultEntity>

    @Query("SELECT tr FROM TestResultEntity tr WHERE tr.testName = :testName ORDER BY tr.createdAt DESC")
    override fun findByTestNameOrderByCreatedAtDesc(
        @Param("testName") testName: String,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // 상태별 조회
    override fun findByStatus(status: TestStatus): List<TestResultEntity>

    override fun findByStatusOrderByCreatedAtDesc(
        status: TestStatus,
        pageable: Pageable,
    ): Page<TestResultEntity>

    // Run과 Test의 복합 조회 (explicit JOIN with QualityRunEntity, use testName field)
    @Query(
        """
        SELECT tr FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId AND tr.testName = :testName
        """,
    )
    override fun findByRunRunIdAndTestName(
        @Param("runId") runId: String,
        @Param("testName") testName: String,
    ): List<TestResultEntity>

    @Query(
        """
        SELECT tr FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId AND tr.status = :status
        """,
    )
    override fun findByRunRunIdAndStatus(
        @Param("runId") runId: String,
        @Param("status") status: TestStatus,
    ): List<TestResultEntity>

    // 전체 목록 조회 (페이지네이션)
    override fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<TestResultEntity>

    // 통계 및 집계
    override fun countByStatus(status: TestStatus): Long

    @Query(
        """
        SELECT COUNT(tr) FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId
        """,
    )
    override fun countByRunRunId(
        @Param("runId") runId: String,
    ): Long

    @Query("SELECT COUNT(tr) FROM TestResultEntity tr WHERE tr.testName = :testName")
    override fun countByTestName(
        @Param("testName") testName: String,
    ): Long

    // 실행 시간 기반 조회
    @Query("SELECT tr FROM TestResultEntity tr WHERE tr.executionTimeSeconds > :durationSeconds")
    override fun findByDurationSecondsGreaterThan(
        @Param("durationSeconds") durationSeconds: Double,
    ): List<TestResultEntity>

    @Query("SELECT tr FROM TestResultEntity tr WHERE tr.executionTimeSeconds BETWEEN :minDuration AND :maxDuration")
    override fun findByDurationSecondsBetween(
        @Param("minDuration") minDuration: Double,
        @Param("maxDuration") maxDuration: Double,
    ): List<TestResultEntity>

    // 에러 메시지 기반 검색
    override fun findByErrorMessageContainingIgnoreCase(errorPattern: String): List<TestResultEntity>

    override fun findByErrorMessageIsNotNull(): List<TestResultEntity>

    override fun findByErrorMessageIsNull(): List<TestResultEntity>

    // 상세 정보 검색 (sampleFailures 필드 기반 - native query for LOB support)
    @Query(
        value = "SELECT * FROM quality_test_results WHERE LOWER(sample_failures) LIKE LOWER(:detailsPattern)",
        nativeQuery = true,
    )
    override fun findByDetailsContainingIgnoreCase(
        @Param("detailsPattern") detailsPattern: String,
    ): List<TestResultEntity>

    // 최근 실행 결과 조회
    override fun findTop10ByOrderByCreatedAtDesc(): List<TestResultEntity>

    // 특정 Run의 실패한 테스트 결과들 (explicit JOIN with QualityRunEntity)
    @Query(
        """
        SELECT tr FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId AND tr.status IN :statuses
        """,
    )
    override fun findByRunRunIdAndStatusIn(
        @Param("runId") runId: String,
        @Param("statuses") statuses: List<TestStatus>,
    ): List<TestResultEntity>

    // 특정 Run의 성공/실패 개수 조회 (explicit JOIN with QualityRunEntity)
    @Query(
        """
        SELECT COUNT(tr) FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId AND tr.status = :status
        """,
    )
    override fun countByRunRunIdAndStatus(
        @Param("runId") runId: String,
        @Param("status") status: TestStatus,
    ): Long

    // 커스텀 업데이트 쿼리 (subquery for runId lookup)
    @Modifying
    @Query(
        """
        UPDATE TestResultEntity tr SET tr.status = :status
        WHERE tr.runId IN (SELECT r.id FROM QualityRunEntity r WHERE r.runId = :runId)
        """,
    )
    fun updateStatusByRunId(
        @Param("runId") runId: String,
        @Param("status") status: TestStatus,
    ): Int

    // 복잡한 검색 쿼리 (explicit JOIN with QualityRunEntity)
    @Query(
        """
        SELECT tr FROM TestResultEntity tr
        LEFT JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE (:runId IS NULL OR r.runId = :runId)
        AND (:testName IS NULL OR tr.testName = :testName)
        AND (:status IS NULL OR tr.status = :status)
        AND (:hasError IS NULL OR
             (:hasError = true AND tr.errorMessage IS NOT NULL) OR
             (:hasError = false AND tr.errorMessage IS NULL))
        AND (:minDuration IS NULL OR tr.executionTimeSeconds >= :minDuration)
        AND (:maxDuration IS NULL OR tr.executionTimeSeconds <= :maxDuration)
        ORDER BY tr.createdAt DESC
        """,
    )
    fun findByComplexFilters(
        @Param("runId") runId: String?,
        @Param("testName") testName: String?,
        @Param("status") status: TestStatus?,
        @Param("hasError") hasError: Boolean?,
        @Param("minDuration") minDuration: Double?,
        @Param("maxDuration") maxDuration: Double?,
        pageable: Pageable,
    ): Page<TestResultEntity>

    @Query(
        """
        SELECT COUNT(tr) FROM TestResultEntity tr
        LEFT JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE (:runId IS NULL OR r.runId = :runId)
        AND (:testName IS NULL OR tr.testName = :testName)
        AND (:status IS NULL OR tr.status = :status)
        AND (:hasError IS NULL OR
             (:hasError = true AND tr.errorMessage IS NOT NULL) OR
             (:hasError = false AND tr.errorMessage IS NULL))
        AND (:minDuration IS NULL OR tr.executionTimeSeconds >= :minDuration)
        AND (:maxDuration IS NULL OR tr.executionTimeSeconds <= :maxDuration)
        """,
    )
    fun countByComplexFilters(
        @Param("runId") runId: String?,
        @Param("testName") testName: String?,
        @Param("status") status: TestStatus?,
        @Param("hasError") hasError: Boolean?,
        @Param("minDuration") minDuration: Double?,
        @Param("maxDuration") maxDuration: Double?,
    ): Long

    // 테스트 결과 통계 조회 (explicit JOIN with QualityRunEntity)
    @Query(
        """
        SELECT
            tr.status as status,
            COUNT(*) as count,
            AVG(tr.executionTimeSeconds) as avgDuration,
            MIN(tr.executionTimeSeconds) as minDuration,
            MAX(tr.executionTimeSeconds) as maxDuration
        FROM TestResultEntity tr
        JOIN QualityRunEntity r ON tr.runId = r.id
        WHERE r.runId = :runId
        GROUP BY tr.status
        ORDER BY tr.status
        """,
    )
    fun getResultStatisticsByRun(
        @Param("runId") runId: String,
    ): List<Map<String, Any>>

    // 테스트별 성공률 조회 (use testName field directly)
    @Query(
        """
        SELECT
            tr.testName as testName,
            COUNT(*) as totalRuns,
            SUM(CASE WHEN tr.status = 'PASSED' THEN 1 ELSE 0 END) as passedRuns,
            ROUND(SUM(CASE WHEN tr.status = 'PASSED' THEN 1.0 ELSE 0 END) / COUNT(*) * 100, 2) as successRate
        FROM TestResultEntity tr
        GROUP BY tr.testName
        HAVING COUNT(*) >= :minRuns
        ORDER BY successRate DESC, totalRuns DESC
        """,
    )
    fun getTestSuccessRates(
        @Param("minRuns") minRuns: Int = 5,
    ): List<Map<String, Any>>
}
