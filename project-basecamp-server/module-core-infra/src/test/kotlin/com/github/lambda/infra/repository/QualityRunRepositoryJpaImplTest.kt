package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.TestStatus
import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.repository.QualityRunRepositoryJpa
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * QualityRunRepositoryJpaImpl Integration Tests
 *
 * Uses @DataJpaTest for database layer testing with H2 in-memory database.
 */
@DataJpaTest
@Import(QualityRunRepositoryJpaImpl::class)
@ComponentScan(basePackages = ["com.github.lambda.infra.repository"])
@ActiveProfiles("test")
@DisplayName("QualityRunRepositoryJpaImpl Integration Tests")
class QualityRunRepositoryJpaImplTest {

    @Autowired
    private lateinit var qualityRunRepository: QualityRunRepositoryJpa

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private lateinit var testQualitySpec: QualitySpecEntity
    private lateinit var testQualityRun1: QualityRunEntity
    private lateinit var testQualityRun2: QualityRunEntity
    private lateinit var testQualityRun3: QualityRunEntity
    private lateinit var testQualityRun4: QualityRunEntity

    @BeforeEach
    fun setUp() {
        // Create a test quality spec first
        testQualitySpec = QualitySpecEntity(
            name = "test_quality_spec",
            resourceName = "iceberg.analytics.users",
            resourceType = ResourceType.DATASET,
            owner = "test@example.com",
            team = "data-team",
            description = "Test quality spec",
            enabled = true
        )

        val now = Instant.now()

        testQualityRun1 = QualityRunEntity(
            runId = "run_20250102_120000_001",
            resourceName = "iceberg.analytics.users",
            status = RunStatus.COMPLETED,
            overallStatus = TestStatus.PASSED,
            startedAt = now.minus(5, ChronoUnit.HOURS),
            completedAt = now.minus(4, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
            durationSeconds = 1800.0, // 30 minutes
            passedTests = 3,
            failedTests = 0,
            executedBy = "alice@example.com"
        )

        testQualityRun2 = QualityRunEntity(
            runId = "run_20250102_130000_002",
            resourceName = "iceberg.analytics.orders",
            status = RunStatus.COMPLETED,
            overallStatus = TestStatus.FAILED,
            startedAt = now.minus(3, ChronoUnit.HOURS),
            completedAt = now.minus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES),
            durationSeconds = 2700.0, // 45 minutes
            passedTests = 2,
            failedTests = 1,
            executedBy = "bob@example.com"
        )

        testQualityRun3 = QualityRunEntity(
            runId = "run_20250102_140000_003",
            resourceName = "iceberg.analytics.users",
            status = RunStatus.RUNNING,
            overallStatus = null,
            startedAt = now.minus(1, ChronoUnit.HOURS),
            completedAt = null,
            durationSeconds = null,
            passedTests = 0,
            failedTests = 0,
            executedBy = "alice@example.com"
        )

        testQualityRun4 = QualityRunEntity(
            runId = "run_20250102_150000_004",
            resourceName = "iceberg.analytics.products",
            status = RunStatus.TIMEOUT,
            overallStatus = TestStatus.ERROR,
            startedAt = now.minus(2, ChronoUnit.HOURS),
            completedAt = now.minus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES),
            durationSeconds = 1800.0, // 30 minutes
            passedTests = 0,
            failedTests = 0,
            executedBy = "charlie@example.com"
        )
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class BasicCrudOperations {

        @Test
        @DisplayName("should save quality run successfully")
        fun `should save quality run successfully`() {
            // Given
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec

            // When
            val saved = qualityRunRepository.save(testQualityRun1)
            entityManager.flush()

            // Then
            assertThat(saved).isNotNull
            assertThat(saved.id).isNotNull
            assertThat(saved.runId).isEqualTo(testQualityRun1.runId)
            assertThat(saved.resourceName).isEqualTo(testQualityRun1.resourceName)
            assertThat(saved.status).isEqualTo(testQualityRun1.status)
            assertThat(saved.executedBy).isEqualTo(testQualityRun1.executedBy)
        }

        @Test
        @DisplayName("should find quality run by run ID")
        fun `should find quality run by run ID`() {
            // Given
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            entityManager.persistAndFlush(testQualityRun1)

            // When
            val found = qualityRunRepository.findByRunId(testQualityRun1.runId)

            // Then
            assertThat(found).isNotNull
            assertThat(found?.runId).isEqualTo(testQualityRun1.runId)
            assertThat(found?.resourceName).isEqualTo(testQualityRun1.resourceName)
        }

        @Test
        @DisplayName("should return null when quality run not found by run ID")
        fun `should return null when quality run not found by run ID`() {
            // When
            val found = qualityRunRepository.findByRunId("nonexistent_run_id")

            // Then
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("should check if quality run exists by run ID")
        fun `should check if quality run exists by run ID`() {
            // Given
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            entityManager.persistAndFlush(testQualityRun1)

            // When
            val exists = qualityRunRepository.existsByRunId(testQualityRun1.runId)
            val notExists = qualityRunRepository.existsByRunId("nonexistent_run_id")

            // Then
            assertThat(exists).isTrue
            assertThat(notExists).isFalse
        }

        @Test
        @DisplayName("should delete quality run by run ID")
        fun `should delete quality run by run ID`() {
            // Given
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            entityManager.persistAndFlush(testQualityRun1)
            val initialCount = qualityRunRepository.count()

            // When
            val deletedCount = qualityRunRepository.deleteByRunId(testQualityRun1.runId)

            // Then
            assertThat(deletedCount).isEqualTo(1L)
            assertThat(qualityRunRepository.count()).isEqualTo(initialCount - 1)
            assertThat(qualityRunRepository.findByRunId(testQualityRun1.runId)).isNull()
        }
    }

    @Nested
    @DisplayName("Spec-Based Queries")
    inner class SpecBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by spec name")
        fun `should find quality runs by spec name`() {
            // When
            val found = qualityRunRepository.findBySpecName(testQualitySpec.name)

            // Then
            assertThat(found).hasSize(4)
            assertThat(found.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun2.runId,
                testQualityRun3.runId,
                testQualityRun4.runId
            )
        }

        @Test
        @DisplayName("should find quality runs by spec name with pagination ordered by started at desc")
        fun `should find quality runs by spec name with pagination ordered by started at desc`() {
            // When
            val page = qualityRunRepository.findBySpecNameOrderByStartedAtDesc(
                testQualitySpec.name,
                PageRequest.of(0, 2)
            )

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(4)
            assertThat(page.totalPages).isEqualTo(2)
            // Should be ordered by startedAt DESC (most recent first)
            assertThat(page.content[0].startedAt).isAfter(page.content[1].startedAt)
        }

        @Test
        @DisplayName("should count quality runs by spec name")
        fun `should count quality runs by spec name`() {
            // When
            val count = qualityRunRepository.countBySpecName(testQualitySpec.name)

            // Then
            assertThat(count).isEqualTo(4)
        }
    }

    @Nested
    @DisplayName("Resource-Based Queries")
    inner class ResourceBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by resource name")
        fun `should find quality runs by resource name`() {
            // When
            val usersRuns = qualityRunRepository.findByResourceName("iceberg.analytics.users")
            val ordersRuns = qualityRunRepository.findByResourceName("iceberg.analytics.orders")

            // Then
            assertThat(usersRuns).hasSize(2)
            assertThat(usersRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun3.runId
            )

            assertThat(ordersRuns).hasSize(1)
            assertThat(ordersRuns[0].runId).isEqualTo(testQualityRun2.runId)
        }

        @Test
        @DisplayName("should find quality runs by resource name with pagination")
        fun `should find quality runs by resource name with pagination`() {
            // When
            val page = qualityRunRepository.findByResourceNameOrderByStartedAtDesc(
                "iceberg.analytics.users",
                PageRequest.of(0, 1)
            )

            // Then
            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(2)
        }

        @Test
        @DisplayName("should count quality runs by resource name")
        fun `should count quality runs by resource name`() {
            // When
            val usersCount = qualityRunRepository.countByResourceName("iceberg.analytics.users")
            val ordersCount = qualityRunRepository.countByResourceName("iceberg.analytics.orders")

            // Then
            assertThat(usersCount).isEqualTo(2)
            assertThat(ordersCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Executor-Based Queries")
    inner class ExecutorBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by executed by")
        fun `should find quality runs by executed by`() {
            // When
            val aliceRuns = qualityRunRepository.findByExecutedBy("alice@example.com")
            val bobRuns = qualityRunRepository.findByExecutedBy("bob@example.com")

            // Then
            assertThat(aliceRuns).hasSize(2)
            assertThat(aliceRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun3.runId
            )

            assertThat(bobRuns).hasSize(1)
            assertThat(bobRuns[0].runId).isEqualTo(testQualityRun2.runId)
        }

        @Test
        @DisplayName("should find quality runs by executed by with pagination")
        fun `should find quality runs by executed by with pagination`() {
            // When
            val page = qualityRunRepository.findByExecutedByOrderByStartedAtDesc(
                "alice@example.com",
                PageRequest.of(0, 1)
            )

            // Then
            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(2)
        }

        @Test
        @DisplayName("should count quality runs by executed by")
        fun `should count quality runs by executed by`() {
            // When
            val aliceCount = qualityRunRepository.countByExecutedBy("alice@example.com")
            val bobCount = qualityRunRepository.countByExecutedBy("bob@example.com")

            // Then
            assertThat(aliceCount).isEqualTo(2)
            assertThat(bobCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Status-Based Queries")
    inner class StatusBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by run status")
        fun `should find quality runs by run status`() {
            // When
            val completedRuns = qualityRunRepository.findByStatus(RunStatus.COMPLETED)
            val runningRuns = qualityRunRepository.findByStatus(RunStatus.RUNNING)
            val timeoutRuns = qualityRunRepository.findByStatus(RunStatus.TIMEOUT)

            // Then
            assertThat(completedRuns).hasSize(2)
            assertThat(completedRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun2.runId
            )

            assertThat(runningRuns).hasSize(1)
            assertThat(runningRuns[0].runId).isEqualTo(testQualityRun3.runId)

            assertThat(timeoutRuns).hasSize(1)
            assertThat(timeoutRuns[0].runId).isEqualTo(testQualityRun4.runId)
        }

        @Test
        @DisplayName("should find quality runs by overall status")
        fun `should find quality runs by overall status`() {
            // When
            val passedRuns = qualityRunRepository.findByOverallStatus(TestStatus.PASSED)
            val failedRuns = qualityRunRepository.findByOverallStatus(TestStatus.FAILED)
            val errorRuns = qualityRunRepository.findByOverallStatus(TestStatus.ERROR)

            // Then
            assertThat(passedRuns).hasSize(1)
            assertThat(passedRuns[0].runId).isEqualTo(testQualityRun1.runId)

            assertThat(failedRuns).hasSize(1)
            assertThat(failedRuns[0].runId).isEqualTo(testQualityRun2.runId)

            assertThat(errorRuns).hasSize(1)
            assertThat(errorRuns[0].runId).isEqualTo(testQualityRun4.runId)
        }

        @Test
        @DisplayName("should count quality runs by status")
        fun `should count quality runs by status`() {
            // When
            val completedCount = qualityRunRepository.countByStatus(RunStatus.COMPLETED)
            val runningCount = qualityRunRepository.countByStatus(RunStatus.RUNNING)
            val passedCount = qualityRunRepository.countByOverallStatus(TestStatus.PASSED)

            // Then
            assertThat(completedCount).isEqualTo(2)
            assertThat(runningCount).isEqualTo(1)
            assertThat(passedCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Time-Based Queries")
    inner class TimeBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by started at range")
        fun `should find quality runs by started at range`() {
            // Given
            val now = Instant.now()
            val startTime = now.minus(4, ChronoUnit.HOURS)
            val endTime = now.minus(1, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES)

            // When
            val runsInRange = qualityRunRepository.findByStartedAtBetween(startTime, endTime)

            // Then - Should include run2, run3, and run4
            assertThat(runsInRange).hasSize(3)
            assertThat(runsInRange.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun2.runId,
                testQualityRun3.runId,
                testQualityRun4.runId
            )
        }

        @Test
        @DisplayName("should find quality runs started after time")
        fun `should find quality runs started after time`() {
            // Given
            val cutoffTime = Instant.now().minus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES)

            // When
            val recentRuns = qualityRunRepository.findByStartedAtAfter(cutoffTime)

            // Then - Should include run3 and run4
            assertThat(recentRuns).hasSize(2)
            assertThat(recentRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun3.runId,
                testQualityRun4.runId
            )
        }

        @Test
        @DisplayName("should find quality runs started before time")
        fun `should find quality runs started before time`() {
            // Given
            val cutoffTime = Instant.now().minus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES)

            // When
            val oldRuns = qualityRunRepository.findByStartedAtBefore(cutoffTime)

            // Then - Should include run1 and run2
            assertThat(oldRuns).hasSize(2)
            assertThat(oldRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun2.runId
            )
        }
    }

    @Nested
    @DisplayName("Duration-Based Queries")
    inner class DurationBasedQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find quality runs by duration greater than threshold")
        fun `should find quality runs by duration greater than threshold`() {
            // When
            val longRuns = qualityRunRepository.findByDurationSecondsGreaterThan(2000.0)

            // Then - Should include run2 (2700 seconds)
            assertThat(longRuns).hasSize(1)
            assertThat(longRuns[0].runId).isEqualTo(testQualityRun2.runId)
        }

        @Test
        @DisplayName("should find quality runs by duration range")
        fun `should find quality runs by duration range`() {
            // When
            val mediumRuns = qualityRunRepository.findByDurationSecondsBetween(1500.0, 2000.0)

            // Then - Should include run1 and run4 (both 1800 seconds)
            assertThat(mediumRuns).hasSize(2)
            assertThat(mediumRuns.map { it.runId }).containsExactlyInAnyOrder(
                testQualityRun1.runId,
                testQualityRun4.runId
            )
        }
    }

    @Nested
    @DisplayName("Pagination and Ordering")
    inner class PaginationAndOrdering {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should return all quality runs with pagination ordered by started at desc")
        fun `should return all quality runs with pagination ordered by started at desc`() {
            // When
            val page = qualityRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 2))

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(4)
            assertThat(page.totalPages).isEqualTo(2)
            assertThat(page.hasNext()).isTrue

            // Should be ordered by startedAt DESC (most recent first)
            assertThat(page.content[0].startedAt).isAfter(page.content[1].startedAt)
        }

        @Test
        @DisplayName("should find completed runs ordered by completed at desc")
        fun `should find completed runs ordered by completed at desc`() {
            // When
            val page = qualityRunRepository.findByStatusInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                listOf(RunStatus.COMPLETED, RunStatus.TIMEOUT),
                PageRequest.of(0, 3)
            )

            // Then
            assertThat(page.content).hasSize(3)
            assertThat(page.content.map { it.runId }).containsExactly(
                testQualityRun4.runId, // Most recent completion
                testQualityRun2.runId,
                testQualityRun1.runId
            )
        }
    }

    @Nested
    @DisplayName("Running Tasks Queries")
    inner class RunningTasksQueries {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should find long-running tasks that might be stuck")
        fun `should find long-running tasks that might be stuck`() {
            // Given - Tasks started before threshold but still running
            val threshold = Instant.now().minus(30, ChronoUnit.MINUTES)

            // When
            val stuckRuns = qualityRunRepository.findByStatusAndStartedAtBefore(
                RunStatus.RUNNING,
                threshold
            )

            // Then - Should include run3 which is still running and started over 30 minutes ago
            assertThat(stuckRuns).hasSize(1)
            assertThat(stuckRuns[0].runId).isEqualTo(testQualityRun3.runId)
        }
    }

    @Nested
    @DisplayName("Total Count")
    inner class TotalCount {

        @BeforeEach
        fun setUpData() {
            val savedSpec = entityManager.persistAndFlush(testQualitySpec)
            testQualityRun1.spec = savedSpec
            testQualityRun2.spec = savedSpec
            testQualityRun3.spec = savedSpec
            testQualityRun4.spec = savedSpec

            entityManager.persistAndFlush(testQualityRun1)
            entityManager.persistAndFlush(testQualityRun2)
            entityManager.persistAndFlush(testQualityRun3)
            entityManager.persistAndFlush(testQualityRun4)
        }

        @Test
        @DisplayName("should count total quality runs")
        fun `should count total quality runs`() {
            // When
            val totalCount = qualityRunRepository.count()

            // Then
            assertThat(totalCount).isEqualTo(4)
        }
    }
}