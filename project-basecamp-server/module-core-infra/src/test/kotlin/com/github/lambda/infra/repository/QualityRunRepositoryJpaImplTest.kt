package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.quality.QualityRunEntity
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
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
import java.time.LocalDateTime
import java.util.UUID

/**
 * QualityRunRepositoryJpaImpl Integration Tests
 *
 * Tests CRUD operations, status queries, time-based queries,
 * and quality spec relationship queries.
 *
 * v2.0 Update: Uses WorkflowRunStatus and new entity structure
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

    private val now: LocalDateTime = LocalDateTime.now()
    private val yesterday: LocalDateTime = now.minusDays(1)
    private val lastWeek: LocalDateTime = now.minusDays(7)

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

        // Create test runs with v2.0 structure
        run1 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                qualitySpecId = spec.id!!,
                specName = spec.name,
                targetResource = "dataset.sales",
                targetResourceType = ResourceType.DATASET,
                status = WorkflowRunStatus.SUCCESS,
                runType = WorkflowRunType.MANUAL,
                triggeredBy = "user1@example.com",
                startedAt = now,
                endedAt = now.plusSeconds(60),
                totalTests = 5,
                passedTests = 5,
                failedTests = 0,
            )

        run2 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                qualitySpecId = spec.id!!,
                specName = spec.name,
                targetResource = "dataset.sales",
                targetResourceType = ResourceType.DATASET,
                status = WorkflowRunStatus.FAILED,
                runType = WorkflowRunType.SCHEDULED,
                triggeredBy = "user2@example.com",
                startedAt = yesterday,
                endedAt = yesterday.plusSeconds(120),
                totalTests = 5,
                passedTests = 3,
                failedTests = 2,
            )

        run3 =
            QualityRunEntity(
                runId = "run-${UUID.randomUUID()}",
                qualitySpecId = spec.id!!,
                specName = spec.name,
                targetResource = "dataset.orders",
                targetResourceType = ResourceType.DATASET,
                status = WorkflowRunStatus.RUNNING,
                runType = WorkflowRunType.MANUAL,
                triggeredBy = "user1@example.com",
                startedAt = lastWeek,
                totalTests = 0,
                passedTests = 0,
                failedTests = 0,
            )

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
                    qualitySpecId = spec.id!!,
                    specName = spec.name,
                    targetResource = "dataset.new",
                    targetResourceType = ResourceType.DATASET,
                    status = WorkflowRunStatus.PENDING,
                    runType = WorkflowRunType.MANUAL,
                    triggeredBy = "new@example.com",
                )

            // When
            val saved = repository.save(newRun)
            testEntityManager.flush()

            // Then
            assertThat(saved.id).isNotNull()
            assertThat(saved.targetResource).isEqualTo("dataset.new")
        }

        @Test
        @DisplayName("should find by runId")
        fun `should return entity when runId exists`() {
            // When
            val found = repository.findByRunId(run1.runId)

            // Then
            assertThat(found).isNotNull
            assertThat(found!!.runId).isEqualTo(run1.runId)
            assertThat(found.targetResource).isEqualTo("dataset.sales")
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
            val success = repository.findByStatus(WorkflowRunStatus.SUCCESS)
            val running = repository.findByStatus(WorkflowRunStatus.RUNNING)
            val failed = repository.findByStatus(WorkflowRunStatus.FAILED)

            // Then
            assertThat(success).hasSize(1)
            assertThat(running).hasSize(1)
            assertThat(failed).hasSize(1)
            assertThat(running[0].runId).isEqualTo(run3.runId)
        }

        @Test
        @DisplayName("should count by status")
        fun `should count runs by status`() {
            // When & Then
            assertThat(repository.countByStatus(WorkflowRunStatus.SUCCESS)).isEqualTo(1)
            assertThat(repository.countByStatus(WorkflowRunStatus.RUNNING)).isEqualTo(1)
            assertThat(repository.countByStatus(WorkflowRunStatus.FAILED)).isEqualTo(1)
            assertThat(repository.countByStatus(WorkflowRunStatus.PENDING)).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Time-based Queries")
    inner class TimeQueries {
        @Test
        @DisplayName("should find runs between dates")
        fun `should find runs between time range`() {
            // When
            val twoDaysAgo = now.minusDays(2)
            val result = repository.findByStartedAtBetween(twoDaysAgo, now.plusSeconds(1))

            // Then
            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("should find runs after date")
        fun `should find runs started after date`() {
            // When
            val twoDaysAgo = now.minusDays(2)
            val result = repository.findByStartedAtAfter(twoDaysAgo)

            // Then
            assertThat(result).hasSize(2) // run1 and run2
        }

        @Test
        @DisplayName("should find runs before date")
        fun `should find runs started before date`() {
            // When
            val twoDaysAgo = now.minusDays(2)
            val result = repository.findByStartedAtBefore(twoDaysAgo)

            // Then
            assertThat(result).hasSize(1) // run3
        }
    }

    @Nested
    @DisplayName("Target Resource Queries")
    inner class TargetResourceQueries {
        @Test
        @DisplayName("should find by target resource")
        fun `should find runs by target resource`() {
            // When
            val salesRuns = repository.findByTargetResource("dataset.sales")
            val ordersRuns = repository.findByTargetResource("dataset.orders")

            // Then
            assertThat(salesRuns).hasSize(2)
            assertThat(ordersRuns).hasSize(1)
        }

        @Test
        @DisplayName("should count by target resource")
        fun `should count runs by target resource`() {
            // When & Then
            assertThat(repository.countByTargetResource("dataset.sales")).isEqualTo(2)
            assertThat(repository.countByTargetResource("dataset.orders")).isEqualTo(1)
            assertThat(repository.countByTargetResource("nonexistent")).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Triggered By Queries")
    inner class TriggeredByQueries {
        @Test
        @DisplayName("should find by triggeredBy")
        fun `should find runs by triggeredBy`() {
            // When
            val user1Runs = repository.findByTriggeredBy("user1@example.com")
            val user2Runs = repository.findByTriggeredBy("user2@example.com")

            // Then
            assertThat(user1Runs).hasSize(2)
            assertThat(user2Runs).hasSize(1)
        }

        @Test
        @DisplayName("should count by triggeredBy")
        fun `should count runs by triggeredBy`() {
            // When & Then
            assertThat(repository.countByTriggeredBy("user1@example.com")).isEqualTo(2)
            assertThat(repository.countByTriggeredBy("user2@example.com")).isEqualTo(1)
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
            val page = repository.findByStatusOrderByStartedAtDesc(WorkflowRunStatus.SUCCESS, pageable)

            // Then
            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(1)
        }

        @Test
        @DisplayName("should return target resource filtered runs with pagination")
        fun `should return target resource filtered runs paginated`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val page = repository.findByTargetResourceOrderByStartedAtDesc("dataset.sales", pageable)

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

        @Test
        @DisplayName("should find by quality spec ID")
        fun `should find runs by quality spec id`() {
            // When
            val result = repository.findByQualitySpecId(spec.id!!)

            // Then
            assertThat(result).hasSize(3)
        }
    }
}
