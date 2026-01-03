package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.TestStatus
import com.github.lambda.infra.config.JpaTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * QualityRunRepositoryJpaImpl Integration Tests
 *
 * Tests CRUD operations, status queries, time-based queries,
 * and quality spec relationship queries.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaTestConfig::class)
@DisplayName("QualityRunRepositoryJpaImpl Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class QualityRunRepositoryJpaImplTest {
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var repository: QualityRunRepositoryJpaImpl

    private lateinit var spec: QualitySpecEntity
    private lateinit var run1: QualityRunEntity
    private lateinit var run2: QualityRunEntity
    private lateinit var run3: QualityRunEntity

    private val now = Instant.now()
    private val yesterday = now.minus(1, ChronoUnit.DAYS)
    private val lastWeek = now.minus(7, ChronoUnit.DAYS)

    @BeforeEach
    fun setUp() {
        // Clean up any existing data from parallel test runs
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_runs").executeUpdate()
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_spec_tags").executeUpdate()
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_specs").executeUpdate()
        testEntityManager.flush()
        testEntityManager.clear()

        // Create a spec first (required for runs)
        spec =
            QualitySpecEntity(
                name = "test-quality-spec",
                resourceName = "dataset.sales",
                resourceType = ResourceType.DATASET,
                owner = "owner@example.com",
            )
        testEntityManager.persistAndFlush(spec)

        // Create test runs
        run1 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                resourceName = "dataset.sales",
                status = RunStatus.COMPLETED,
                overallStatus = TestStatus.PASSED,
                startedAt = now,
                completedAt = now.plusSeconds(60),
                durationSeconds = 60.0,
                passedTests = 5,
                failedTests = 0,
                executedBy = "user1@example.com",
            ).also { it.spec = spec }

        run2 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                resourceName = "dataset.sales",
                status = RunStatus.COMPLETED,
                overallStatus = TestStatus.FAILED,
                startedAt = yesterday,
                completedAt = yesterday.plusSeconds(120),
                durationSeconds = 120.0,
                passedTests = 3,
                failedTests = 2,
                executedBy = "user2@example.com",
            ).also { it.spec = spec }

        run3 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                resourceName = "dataset.orders",
                status = RunStatus.RUNNING,
                overallStatus = null,
                startedAt = lastWeek,
                completedAt = null,
                durationSeconds = null,
                passedTests = 0,
                failedTests = 0,
                executedBy = "user1@example.com",
            ).also { it.spec = spec }

        testEntityManager.persistAndFlush(run1)
        testEntityManager.persistAndFlush(run2)
        testEntityManager.persistAndFlush(run3)
        testEntityManager.clear()
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class CrudOperations {
        @Test
        @DisplayName("should save and generate ID")
        fun `should save entity with generated id`() {
            // Given
            val newRun =
                QualityRunEntity(
                    runId = "run-${UUID.randomUUID()}",
                    resourceName = "dataset.new",
                    status = RunStatus.RUNNING,
                    startedAt = Instant.now(),
                    executedBy = "new@example.com",
                ).also { it.spec = spec }

            // When
            val saved = repository.save(newRun)
            testEntityManager.flush()

            // Then
            assertThat(saved.id).isNotNull()
            assertThat(saved.resourceName).isEqualTo("dataset.new")
        }

        @Test
        @DisplayName("should find by runId")
        fun `should return entity when runId exists`() {
            // When
            val found = repository.findByRunId(run1.runId)

            // Then
            assertThat(found).isNotNull
            assertThat(found!!.runId).isEqualTo(run1.runId)
            assertThat(found.resourceName).isEqualTo("dataset.sales")
        }

        @Test
        @DisplayName("should return null when runId not found")
        fun `should return null when runId not found`() {
            // When
            val found = repository.findByRunId("nonexistent-run-id")

            // Then
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("should check existence by runId")
        fun `should return true when runId exists`() {
            // When & Then
            assertThat(repository.existsByRunId(run1.runId)).isTrue()
            assertThat(repository.existsByRunId("nonexistent")).isFalse()
        }

        @Test
        @DisplayName("should delete by runId")
        fun `should delete entity by runId`() {
            // When
            val deletedCount = repository.deleteByRunId(run1.runId)
            testEntityManager.flush()

            // Then
            assertThat(deletedCount).isEqualTo(1)
            assertThat(repository.findByRunId(run1.runId)).isNull()
        }
    }

    @Nested
    @DisplayName("Status Queries")
    inner class StatusQueries {
        @Test
        @DisplayName("should find by run status")
        fun `should find runs by status`() {
            // When
            val completed = repository.findByStatus(RunStatus.COMPLETED)
            val running = repository.findByStatus(RunStatus.RUNNING)

            // Then
            assertThat(completed).hasSize(2)
            assertThat(running).hasSize(1)
            assertThat(running[0].runId).isEqualTo(run3.runId)
        }

        @Test
        @DisplayName("should find by overall test status")
        fun `should find runs by overall status`() {
            // When
            val passed = repository.findByOverallStatus(TestStatus.PASSED)
            val failed = repository.findByOverallStatus(TestStatus.FAILED)

            // Then
            assertThat(passed).hasSize(1)
            assertThat(passed[0].runId).isEqualTo(run1.runId)
            assertThat(failed).hasSize(1)
            assertThat(failed[0].runId).isEqualTo(run2.runId)
        }

        @Test
        @DisplayName("should count by status")
        fun `should count runs by status`() {
            // When & Then
            assertThat(repository.countByStatus(RunStatus.COMPLETED)).isEqualTo(2)
            assertThat(repository.countByStatus(RunStatus.RUNNING)).isEqualTo(1)
            assertThat(repository.countByStatus(RunStatus.FAILED)).isEqualTo(0)
        }

        @Test
        @DisplayName("should count by overall status")
        fun `should count runs by overall status`() {
            // When & Then
            assertThat(repository.countByOverallStatus(TestStatus.PASSED)).isEqualTo(1)
            assertThat(repository.countByOverallStatus(TestStatus.FAILED)).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Time-based Queries")
    inner class TimeQueries {
        @Test
        @DisplayName("should find runs between dates")
        fun `should find runs between time range`() {
            // When
            val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)
            val result = repository.findByStartedAtBetween(twoDaysAgo, now.plusSeconds(1))

            // Then
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("should find runs after date")
        fun `should find runs started after date`() {
            // When
            val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)
            val result = repository.findByStartedAtAfter(twoDaysAgo)

            // Then
            assertThat(result).hasSize(2) // run1 and run2
        }

        @Test
        @DisplayName("should find runs before date")
        fun `should find runs started before date`() {
            // When
            val twoDaysAgo = now.minus(2, ChronoUnit.DAYS)
            val result = repository.findByStartedAtBefore(twoDaysAgo)

            // Then
            assertThat(result).hasSize(1) // run3
        }
    }

    @Nested
    @DisplayName("Resource Queries")
    inner class ResourceQueries {
        @Test
        @DisplayName("should find by resource name")
        fun `should find runs by resource name`() {
            // When
            val salesRuns = repository.findByResourceName("dataset.sales")
            val ordersRuns = repository.findByResourceName("dataset.orders")

            // Then
            assertThat(salesRuns).hasSize(2)
            assertThat(ordersRuns).hasSize(1)
        }

        @Test
        @DisplayName("should count by resource name")
        fun `should count runs by resource name`() {
            // When & Then
            assertThat(repository.countByResourceName("dataset.sales")).isEqualTo(2)
            assertThat(repository.countByResourceName("dataset.orders")).isEqualTo(1)
            assertThat(repository.countByResourceName("nonexistent")).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Executor Queries")
    inner class ExecutorQueries {
        @Test
        @DisplayName("should find by executedBy")
        fun `should find runs by executor`() {
            // When
            val user1Runs = repository.findByExecutedBy("user1@example.com")
            val user2Runs = repository.findByExecutedBy("user2@example.com")

            // Then
            assertThat(user1Runs).hasSize(2)
            assertThat(user2Runs).hasSize(1)
        }

        @Test
        @DisplayName("should count by executedBy")
        fun `should count runs by executor`() {
            // When & Then
            assertThat(repository.countByExecutedBy("user1@example.com")).isEqualTo(2)
            assertThat(repository.countByExecutedBy("user2@example.com")).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Duration Queries")
    inner class DurationQueries {
        @Test
        @DisplayName("should find by duration greater than")
        fun `should find runs with duration greater than threshold`() {
            // When
            val longRuns = repository.findByDurationSecondsGreaterThan(90.0)

            // Then
            assertThat(longRuns).hasSize(1)
            assertThat(longRuns[0].runId).isEqualTo(run2.runId)
        }

        @Test
        @DisplayName("should find by duration between range")
        fun `should find runs with duration in range`() {
            // When
            val result = repository.findByDurationSecondsBetween(50.0, 130.0)

            // Then
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    @DisplayName("Pagination Queries")
    inner class PaginationQueries {
        @Test
        @DisplayName("should return paginated results ordered by startedAt desc")
        fun `should return paginated results`() {
            // Given
            val pageable = PageRequest.of(0, 2)

            // When
            val page = repository.findAllByOrderByStartedAtDesc(pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(3)
            assertThat(page.totalPages).isEqualTo(2)
            // Most recent first
            assertThat(page.content[0].runId).isEqualTo(run1.runId)
        }

        @Test
        @DisplayName("should return status filtered runs with pagination")
        fun `should return status filtered runs paginated`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val page = repository.findByStatusOrderByStartedAtDesc(RunStatus.COMPLETED, pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(2)
        }

        @Test
        @DisplayName("should return resource filtered runs with pagination")
        fun `should return resource filtered runs paginated`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val page = repository.findByResourceNameOrderByStartedAtDesc("dataset.sales", pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Spec Relationship Queries")
    inner class SpecQueries {
        @Test
        @DisplayName("should find by spec name")
        fun `should find runs by spec name`() {
            // When
            val result = repository.findBySpecName("test-quality-spec")

            // Then
            assertThat(result).hasSize(3)
        }

        @Test
        @DisplayName("should count by spec name")
        fun `should count runs by spec name`() {
            // When & Then
            assertThat(repository.countBySpecName("test-quality-spec")).isEqualTo(3)
            assertThat(repository.countBySpecName("nonexistent")).isEqualTo(0)
        }
    }
}
