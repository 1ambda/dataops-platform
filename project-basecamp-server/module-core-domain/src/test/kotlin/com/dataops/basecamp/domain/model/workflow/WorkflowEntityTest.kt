package com.dataops.basecamp.domain.model.workflow

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.domain.entity.workflow.WorkflowEntity
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * WorkflowEntity Unit Tests
 *
 * Tests business logic and validation rules for WorkflowEntity.
 */
@DisplayName("WorkflowEntity Unit Tests")
class WorkflowEntityTest {
    private lateinit var testWorkflow: WorkflowEntity
    private lateinit var testScheduleInfo: ScheduleInfo

    @BeforeEach
    fun setUp() {
        testScheduleInfo =
            ScheduleInfo(
                cron = "0 8 * * *",
                timezone = "UTC",
            )

        testWorkflow =
            WorkflowEntity(
                datasetName = "iceberg.analytics.users",
                sourceType = WorkflowSourceType.MANUAL,
                status = WorkflowStatus.ACTIVE,
                owner = "test@example.com",
                team = "data-team",
                description = "Test workflow for users dataset",
                s3Path = "s3://bucket/workflows/iceberg.analytics.users.yaml",
                airflowDagId = "iceberg_analytics_users",
                schedule = testScheduleInfo,
            )
    }

    @Nested
    @DisplayName("Status checks")
    inner class StatusChecks {
        @Test
        @DisplayName("should return true for isActive when status is ACTIVE")
        fun `should return true for isActive when status is ACTIVE`() {
            // Given
            testWorkflow.status = WorkflowStatus.ACTIVE

            // When & Then
            assertThat(testWorkflow.isActive()).isTrue()
            assertThat(testWorkflow.isPaused()).isFalse()
            assertThat(testWorkflow.isDisabled()).isFalse()
        }

        @Test
        @DisplayName("should return true for isPaused when status is PAUSED")
        fun `should return true for isPaused when status is PAUSED`() {
            // Given
            testWorkflow.status = WorkflowStatus.PAUSED

            // When & Then
            assertThat(testWorkflow.isPaused()).isTrue()
            assertThat(testWorkflow.isActive()).isFalse()
            assertThat(testWorkflow.isDisabled()).isFalse()
        }

        @Test
        @DisplayName("should return true for isDisabled when status is DISABLED")
        fun `should return true for isDisabled when status is DISABLED`() {
            // Given
            testWorkflow.status = WorkflowStatus.DISABLED

            // When & Then
            assertThat(testWorkflow.isDisabled()).isTrue()
            assertThat(testWorkflow.isActive()).isFalse()
            assertThat(testWorkflow.isPaused()).isFalse()
        }

        @Test
        @DisplayName("should return true for canRun only when status is ACTIVE")
        fun `should return true for canRun only when status is ACTIVE`() {
            // When status is ACTIVE
            testWorkflow.status = WorkflowStatus.ACTIVE
            assertThat(testWorkflow.canRun()).isTrue()

            // When status is PAUSED
            testWorkflow.status = WorkflowStatus.PAUSED
            assertThat(testWorkflow.canRun()).isFalse()

            // When status is DISABLED
            testWorkflow.status = WorkflowStatus.DISABLED
            assertThat(testWorkflow.canRun()).isFalse()
        }
    }

    @Nested
    @DisplayName("Source type checks")
    inner class SourceTypeChecks {
        @Test
        @DisplayName("should return true for isManualSource when sourceType is MANUAL")
        fun `should return true for isManualSource when sourceType is MANUAL`() {
            // Given
            testWorkflow.sourceType = WorkflowSourceType.MANUAL

            // When & Then
            assertThat(testWorkflow.isManualSource()).isTrue()
            assertThat(testWorkflow.isCodeSource()).isFalse()
        }

        @Test
        @DisplayName("should return true for isCodeSource when sourceType is CODE")
        fun `should return true for isCodeSource when sourceType is CODE`() {
            // Given
            testWorkflow.sourceType = WorkflowSourceType.CODE

            // When & Then
            assertThat(testWorkflow.isCodeSource()).isTrue()
            assertThat(testWorkflow.isManualSource()).isFalse()
        }
    }

    @Nested
    @DisplayName("Status management")
    inner class StatusManagement {
        @Test
        @DisplayName("should pause workflow when status is ACTIVE")
        fun `should pause workflow when status is ACTIVE`() {
            // Given
            testWorkflow.status = WorkflowStatus.ACTIVE

            // When
            testWorkflow.pause()

            // Then
            assertThat(testWorkflow.status).isEqualTo(WorkflowStatus.PAUSED)
            assertThat(testWorkflow.isPaused()).isTrue()
            assertThat(testWorkflow.canRun()).isFalse()
        }

        @Test
        @DisplayName("should throw exception when pausing non-active workflow")
        fun `should throw exception when pausing non-active workflow`() {
            // Given
            testWorkflow.status = WorkflowStatus.PAUSED

            // When & Then
            assertThatThrownBy { testWorkflow.pause() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot pause workflow. Current status: PAUSED")
        }

        @Test
        @DisplayName("should unpause workflow when status is PAUSED")
        fun `should unpause workflow when status is PAUSED`() {
            // Given
            testWorkflow.status = WorkflowStatus.PAUSED

            // When
            testWorkflow.unpause()

            // Then
            assertThat(testWorkflow.status).isEqualTo(WorkflowStatus.ACTIVE)
            assertThat(testWorkflow.isActive()).isTrue()
            assertThat(testWorkflow.canRun()).isTrue()
        }

        @Test
        @DisplayName("should throw exception when unpausing non-paused workflow")
        fun `should throw exception when unpausing non-paused workflow`() {
            // Given
            testWorkflow.status = WorkflowStatus.ACTIVE

            // When & Then
            assertThatThrownBy { testWorkflow.unpause() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot unpause workflow. Current status: ACTIVE")
        }

        @Test
        @DisplayName("should disable workflow from any status")
        fun `should disable workflow from any status`() {
            // Test disabling from ACTIVE
            testWorkflow.status = WorkflowStatus.ACTIVE
            testWorkflow.disable()
            assertThat(testWorkflow.status).isEqualTo(WorkflowStatus.DISABLED)

            // Test disabling from PAUSED
            testWorkflow.status = WorkflowStatus.PAUSED
            testWorkflow.disable()
            assertThat(testWorkflow.status).isEqualTo(WorkflowStatus.DISABLED)
        }
    }

    @Nested
    @DisplayName("Dataset name parsing")
    inner class DatasetNameParsing {
        @Test
        @DisplayName("should extract catalog correctly")
        fun `should extract catalog correctly`() {
            // Given
            testWorkflow.datasetName = "iceberg.analytics.users"

            // When & Then
            assertThat(testWorkflow.getCatalog()).isEqualTo("iceberg")
        }

        @Test
        @DisplayName("should extract schema correctly")
        fun `should extract schema correctly`() {
            // Given
            testWorkflow.datasetName = "iceberg.analytics.users"

            // When & Then
            assertThat(testWorkflow.getSchema()).isEqualTo("analytics")
        }

        @Test
        @DisplayName("should extract dataset short name correctly")
        fun `should extract dataset short name correctly`() {
            // Given
            testWorkflow.datasetName = "iceberg.analytics.users"

            // When & Then
            assertThat(testWorkflow.getDatasetShortName()).isEqualTo("users")
        }

        @Test
        @DisplayName("should handle invalid dataset name gracefully")
        fun `should handle invalid dataset name gracefully`() {
            // Given
            testWorkflow.datasetName = "invalid_name"

            // When & Then
            assertThat(testWorkflow.getCatalog()).isEmpty()
            assertThat(testWorkflow.getSchema()).isEmpty()
            assertThat(testWorkflow.getDatasetShortName()).isEqualTo("invalid_name")
        }

        @Test
        @DisplayName("should handle incomplete dataset name")
        fun `should handle incomplete dataset name`() {
            // Given
            testWorkflow.datasetName = "catalog.schema"

            // When & Then
            assertThat(testWorkflow.getCatalog()).isEqualTo("catalog")
            assertThat(testWorkflow.getSchema()).isEqualTo("schema")
            assertThat(testWorkflow.getDatasetShortName()).isEqualTo("catalog.schema")
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    inner class ConstructorValidation {
        @Test
        @DisplayName("should create workflow with all required fields")
        fun `should create workflow with all required fields`() {
            // When
            val workflow =
                WorkflowEntity(
                    datasetName = "test.catalog.dataset",
                    sourceType = WorkflowSourceType.MANUAL,
                    status = WorkflowStatus.ACTIVE,
                    owner = "owner@example.com",
                    team = "team",
                    description = "Test description",
                    s3Path = "s3://bucket/path",
                    airflowDagId = "test_dag_id",
                    schedule = ScheduleInfo(),
                )

            // Then
            assertThat(workflow.datasetName).isEqualTo("test.catalog.dataset")
            assertThat(workflow.sourceType).isEqualTo(WorkflowSourceType.MANUAL)
            assertThat(workflow.status).isEqualTo(WorkflowStatus.ACTIVE)
            assertThat(workflow.owner).isEqualTo("owner@example.com")
            assertThat(workflow.team).isEqualTo("team")
            assertThat(workflow.description).isEqualTo("Test description")
            assertThat(workflow.s3Path).isEqualTo("s3://bucket/path")
            assertThat(workflow.airflowDagId).isEqualTo("test_dag_id")
            assertThat(workflow.schedule).isNotNull()
        }

        @Test
        @DisplayName("should create workflow with minimal required fields")
        fun `should create workflow with minimal required fields`() {
            // When
            val workflow =
                WorkflowEntity(
                    datasetName = "catalog.schema.dataset",
                    owner = "owner@example.com",
                    s3Path = "s3://bucket/path",
                    airflowDagId = "test_dag_id",
                )

            // Then
            assertThat(workflow.datasetName).isEqualTo("catalog.schema.dataset")
            assertThat(workflow.sourceType).isEqualTo(WorkflowSourceType.MANUAL) // default
            assertThat(workflow.status).isEqualTo(WorkflowStatus.ACTIVE) // default
            assertThat(workflow.owner).isEqualTo("owner@example.com")
            assertThat(workflow.team).isNull()
            assertThat(workflow.description).isNull()
            assertThat(workflow.s3Path).isEqualTo("s3://bucket/path")
            assertThat(workflow.airflowDagId).isEqualTo("test_dag_id")
            assertThat(workflow.schedule).isNotNull() // has default constructor
        }
    }
}
