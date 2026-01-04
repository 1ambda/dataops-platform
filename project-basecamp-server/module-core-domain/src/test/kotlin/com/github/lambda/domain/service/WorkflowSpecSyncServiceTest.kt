package com.github.lambda.domain.service

import com.github.lambda.common.enums.SpecSyncErrorType
import com.github.lambda.common.enums.WorkflowSourceType
import com.github.lambda.common.enums.WorkflowStatus
import com.github.lambda.domain.entity.workflow.WorkflowEntity
import com.github.lambda.domain.external.WorkflowStorage
import com.github.lambda.domain.model.workflow.ScheduleInfo
import com.github.lambda.domain.model.workflow.WorkflowParseResult
import com.github.lambda.domain.model.workflow.WorkflowScheduleSpec
import com.github.lambda.domain.model.workflow.WorkflowSpec
import com.github.lambda.domain.repository.workflow.WorkflowRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * WorkflowSpecSyncService Unit Tests
 *
 * Tests for S3 workflow spec synchronization service.
 */
@DisplayName("WorkflowSpecSyncService Unit Tests")
class WorkflowSpecSyncServiceTest {
    private val workflowStorage: WorkflowStorage = mockk()
    private val workflowRepositoryJpa: WorkflowRepositoryJpa = mockk()
    private val yamlParser: WorkflowYamlParser = mockk()
    private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    private lateinit var service: WorkflowSpecSyncService

    private val validYamlContent =
        """
        name: catalog.schema.dataset
        owner: user@example.com
        team: data-platform
        description: Test workflow
        schedule:
          cron: "0 8 * * *"
          timezone: UTC
        """.trimIndent()

    private val validSpec =
        WorkflowSpec(
            name = "catalog.schema.dataset",
            owner = "user@example.com",
            team = "data-platform",
            description = "Test workflow",
            schedule =
                WorkflowScheduleSpec(
                    cron = "0 8 * * *",
                    timezone = "UTC",
                ),
        )

    @BeforeEach
    fun setUp() {
        service =
            WorkflowSpecSyncService(
                workflowStorage = workflowStorage,
                workflowRepositoryJpa = workflowRepositoryJpa,
                yamlParser = yamlParser,
                clock = fixedClock,
            )
    }

    @Nested
    @DisplayName("syncFromStorage")
    inner class SyncFromStorage {
        @Test
        @DisplayName("should create new workflow when spec does not exist in DB")
        fun `should create new workflow when spec does not exist in DB`() {
            // Given
            val specPath = "s3://bucket/workflows/code/catalog.schema.dataset.yaml"
            every { workflowStorage.listAllSpecs() } returns listOf(specPath)
            every { workflowStorage.getWorkflowYaml(specPath) } returns validYamlContent
            every { yamlParser.parse(validYamlContent) } returns WorkflowParseResult.Success(validSpec)
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns null

            val savedWorkflowSlot = slot<WorkflowEntity>()
            every { workflowRepositoryJpa.save(capture(savedWorkflowSlot)) } answers { savedWorkflowSlot.captured }

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(1)
            assertThat(result.created).isEqualTo(1)
            assertThat(result.updated).isEqualTo(0)
            assertThat(result.failed).isEqualTo(0)
            assertThat(result.errors).isEmpty()
            assertThat(result.isSuccess()).isTrue()
            assertThat(result.syncedAt).isEqualTo(fixedInstant)

            // Verify saved workflow
            assertThat(savedWorkflowSlot.captured.datasetName).isEqualTo("catalog.schema.dataset")
            assertThat(savedWorkflowSlot.captured.owner).isEqualTo("user@example.com")
            assertThat(savedWorkflowSlot.captured.team).isEqualTo("data-platform")
            assertThat(savedWorkflowSlot.captured.sourceType).isEqualTo(WorkflowSourceType.CODE)
            assertThat(savedWorkflowSlot.captured.status).isEqualTo(WorkflowStatus.ACTIVE)

            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update existing workflow with S3 spec")
        fun `should update existing workflow with S3 spec`() {
            // Given
            val specPath = "s3://bucket/workflows/code/catalog.schema.dataset.yaml"
            val existingWorkflow =
                WorkflowEntity(
                    datasetName = "catalog.schema.dataset",
                    sourceType = WorkflowSourceType.MANUAL,
                    status = WorkflowStatus.ACTIVE,
                    owner = "old-owner@example.com",
                    team = "old-team",
                    description = "Old description",
                    s3Path = "old-path",
                    airflowDagId = "dag_catalog_schema_dataset",
                    schedule = ScheduleInfo(cron = "0 0 * * *", timezone = "UTC"),
                )

            every { workflowStorage.listAllSpecs() } returns listOf(specPath)
            every { workflowStorage.getWorkflowYaml(specPath) } returns validYamlContent
            every { yamlParser.parse(validYamlContent) } returns WorkflowParseResult.Success(validSpec)
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns existingWorkflow
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(1)
            assertThat(result.created).isEqualTo(0)
            assertThat(result.updated).isEqualTo(1)
            assertThat(result.failed).isEqualTo(0)
            assertThat(result.isSuccess()).isTrue()

            // Verify workflow was updated with new spec values (S3-first policy)
            assertThat(existingWorkflow.owner).isEqualTo("user@example.com")
            assertThat(existingWorkflow.team).isEqualTo("data-platform")
            assertThat(existingWorkflow.description).isEqualTo("Test workflow")
            assertThat(existingWorkflow.sourceType).isEqualTo(WorkflowSourceType.CODE)

            verify(exactly = 2) { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") }
            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should handle parse errors gracefully without failing entire sync")
        fun `should handle parse errors gracefully without failing entire sync`() {
            // Given
            val validSpecPath = "s3://bucket/workflows/code/valid.yaml"
            val invalidSpecPath = "s3://bucket/workflows/code/invalid.yaml"

            every { workflowStorage.listAllSpecs() } returns listOf(validSpecPath, invalidSpecPath)
            every { workflowStorage.getWorkflowYaml(validSpecPath) } returns validYamlContent
            every { workflowStorage.getWorkflowYaml(invalidSpecPath) } returns "invalid: yaml content"
            every { yamlParser.parse(validYamlContent) } returns
                WorkflowParseResult.Success(validSpec)
            every { yamlParser.parse("invalid: yaml content") } returns
                WorkflowParseResult.Failure(listOf("Parse error"))
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns null

            val savedWorkflowSlot = slot<WorkflowEntity>()
            every { workflowRepositoryJpa.save(capture(savedWorkflowSlot)) } answers { savedWorkflowSlot.captured }

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(2)
            assertThat(result.created).isEqualTo(1)
            assertThat(result.updated).isEqualTo(0)
            assertThat(result.failed).isEqualTo(1)
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].specPath).isEqualTo(invalidSpecPath)
            assertThat(result.errors[0].errorType).isEqualTo(SpecSyncErrorType.PARSE_ERROR)
            assertThat(result.isSuccess()).isFalse()
        }

        @Test
        @DisplayName("should return empty result when no specs found")
        fun `should return empty result when no specs found`() {
            // Given
            every { workflowStorage.listAllSpecs() } returns emptyList()

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(0)
            assertThat(result.created).isEqualTo(0)
            assertThat(result.updated).isEqualTo(0)
            assertThat(result.failed).isEqualTo(0)
            assertThat(result.errors).isEmpty()
            assertThat(result.isSuccess()).isTrue()
            assertThat(result.syncedAt).isEqualTo(fixedInstant)
        }

        @Test
        @DisplayName("should handle storage listing error")
        fun `should handle storage listing error`() {
            // Given
            every { workflowStorage.listAllSpecs() } throws RuntimeException("S3 connection error")

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(0)
            assertThat(result.created).isEqualTo(0)
            assertThat(result.updated).isEqualTo(0)
            assertThat(result.failed).isEqualTo(1)
            assertThat(result.errors).hasSize(1)
            assertThat(result.errors[0].specPath).isEqualTo("*")
            assertThat(result.errors[0].message).contains("S3 connection error")
            assertThat(result.errors[0].errorType).isEqualTo(SpecSyncErrorType.STORAGE_ERROR)
        }

        @Test
        @DisplayName("should preserve disabled status when updating workflow")
        fun `should preserve disabled status when updating workflow`() {
            // Given
            val specPath = "s3://bucket/workflows/code/catalog.schema.dataset.yaml"
            val disabledWorkflow =
                WorkflowEntity(
                    datasetName = "catalog.schema.dataset",
                    sourceType = WorkflowSourceType.MANUAL,
                    status = WorkflowStatus.DISABLED,
                    owner = "old-owner@example.com",
                    team = "old-team",
                    description = "Old description",
                    s3Path = "old-path",
                    airflowDagId = "dag_catalog_schema_dataset",
                    schedule = ScheduleInfo(cron = "0 0 * * *", timezone = "UTC"),
                )

            every { workflowStorage.listAllSpecs() } returns listOf(specPath)
            every { workflowStorage.getWorkflowYaml(specPath) } returns validYamlContent
            every { yamlParser.parse(validYamlContent) } returns WorkflowParseResult.Success(validSpec)
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns disabledWorkflow
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.updated).isEqualTo(1)
            // Disabled status should be preserved
            assertThat(disabledWorkflow.status).isEqualTo(WorkflowStatus.DISABLED)
        }

        @Test
        @DisplayName("should collect multiple errors without failing entire sync")
        fun `should collect multiple errors without failing entire sync`() {
            // Given
            val specPath1 = "s3://bucket/workflows/code/spec1.yaml"
            val specPath2 = "s3://bucket/workflows/code/spec2.yaml"
            val specPath3 = "s3://bucket/workflows/code/spec3.yaml"

            every { workflowStorage.listAllSpecs() } returns listOf(specPath1, specPath2, specPath3)
            every { workflowStorage.getWorkflowYaml(specPath1) } throws RuntimeException("Storage error 1")
            every { workflowStorage.getWorkflowYaml(specPath2) } throws RuntimeException("Storage error 2")
            every { workflowStorage.getWorkflowYaml(specPath3) } returns validYamlContent
            every { yamlParser.parse(validYamlContent) } returns WorkflowParseResult.Success(validSpec)
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns null

            val savedWorkflowSlot = slot<WorkflowEntity>()
            every { workflowRepositoryJpa.save(capture(savedWorkflowSlot)) } answers { savedWorkflowSlot.captured }

            // When
            val result = service.syncFromStorage()

            // Then
            assertThat(result.totalProcessed).isEqualTo(3)
            assertThat(result.created).isEqualTo(1)
            assertThat(result.failed).isEqualTo(2)
            assertThat(result.errors).hasSize(2)
            assertThat(result.isSuccess()).isFalse()
        }

        @Test
        @DisplayName("should return correct sync statistics in summary")
        fun `should return correct sync statistics in summary`() {
            // Given
            val specPath = "s3://bucket/workflows/code/catalog.schema.dataset.yaml"
            every { workflowStorage.listAllSpecs() } returns listOf(specPath)
            every { workflowStorage.getWorkflowYaml(specPath) } returns validYamlContent
            every { yamlParser.parse(validYamlContent) } returns WorkflowParseResult.Success(validSpec)
            every { workflowRepositoryJpa.findByDatasetName("catalog.schema.dataset") } returns null

            val savedWorkflowSlot = slot<WorkflowEntity>()
            every { workflowRepositoryJpa.save(capture(savedWorkflowSlot)) } answers { savedWorkflowSlot.captured }

            // When
            val result = service.syncFromStorage()
            val summary = result.summary()

            // Then
            assertThat(summary).contains("SpecSync completed")
            assertThat(summary).contains("processed=1")
            assertThat(summary).contains("created=1")
            assertThat(summary).contains("updated=0")
            assertThat(summary).contains("failed=0")
        }
    }
}
