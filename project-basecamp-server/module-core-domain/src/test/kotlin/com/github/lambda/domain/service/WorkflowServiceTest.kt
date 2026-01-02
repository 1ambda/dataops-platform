package com.github.lambda.domain.service

import com.github.lambda.common.exception.WorkflowAlreadyExistsException
import com.github.lambda.common.exception.WorkflowNotFoundException
import com.github.lambda.common.exception.WorkflowRunNotFoundException
import com.github.lambda.domain.external.AirflowClient
import com.github.lambda.domain.external.AirflowDAGRunState
import com.github.lambda.domain.external.AirflowDAGRunStatus
import com.github.lambda.domain.external.WorkflowStorage
import com.github.lambda.domain.model.workflow.ScheduleInfo
import com.github.lambda.domain.model.workflow.WorkflowEntity
import com.github.lambda.domain.model.workflow.WorkflowRunEntity
import com.github.lambda.domain.model.workflow.WorkflowRunStatus
import com.github.lambda.domain.model.workflow.WorkflowRunType
import com.github.lambda.domain.model.workflow.WorkflowSourceType
import com.github.lambda.domain.model.workflow.WorkflowStatus
import com.github.lambda.domain.repository.WorkflowRepositoryDsl
import com.github.lambda.domain.repository.WorkflowRepositoryJpa
import com.github.lambda.domain.repository.WorkflowRunRepositoryDsl
import com.github.lambda.domain.repository.WorkflowRunRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * WorkflowService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("WorkflowService Unit Tests")
class WorkflowServiceTest {
    private val workflowRepositoryJpa: WorkflowRepositoryJpa = mockk()
    private val workflowRepositoryDsl: WorkflowRepositoryDsl = mockk()
    private val workflowRunRepositoryJpa: WorkflowRunRepositoryJpa = mockk()
    private val workflowRunRepositoryDsl: WorkflowRunRepositoryDsl = mockk()
    private val airflowClient: AirflowClient = mockk()
    private val workflowStorage: WorkflowStorage = mockk()

    private val workflowService =
        WorkflowService(
            workflowRepositoryJpa,
            workflowRepositoryDsl,
            workflowRunRepositoryJpa,
            workflowRunRepositoryDsl,
            airflowClient,
            workflowStorage,
        )

    private lateinit var testWorkflow: WorkflowEntity
    private lateinit var testWorkflowRun: WorkflowRunEntity
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

        testWorkflowRun =
            WorkflowRunEntity(
                runId = "iceberg_analytics_users_manual_20250102_120000",
                datasetName = "iceberg.analytics.users",
                status = WorkflowRunStatus.PENDING,
                triggeredBy = "test@example.com",
                runType = WorkflowRunType.MANUAL,
                startedAt = LocalDateTime.now(),
            )
    }

    @Nested
    @DisplayName("getWorkflows")
    inner class GetWorkflows {
        @Test
        @DisplayName("should return workflows without filters")
        fun `should return workflows without filters`() {
            // Given
            val pageable = PageRequest.of(0, 50)
            val workflows = listOf(testWorkflow)
            val page = PageImpl(workflows, pageable, 1)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].datasetName).isEqualTo(testWorkflow.datasetName)
            verify(exactly = 1) {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return workflows with status filter")
        fun `should return workflows with status filter`() {
            // Given
            val status = "ACTIVE"
            val pageable = PageRequest.of(0, 50)
            val workflows = listOf(testWorkflow)
            val page = PageImpl(workflows, pageable, 1)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = WorkflowStatus.ACTIVE,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows(status = status)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].status).isEqualTo(WorkflowStatus.ACTIVE)
            verify(exactly = 1) {
                workflowRepositoryDsl.findByFilters(
                    status = WorkflowStatus.ACTIVE,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return workflows with sourceType filter")
        fun `should return workflows with sourceType filter`() {
            // Given
            val sourceType = "MANUAL"
            val pageable = PageRequest.of(0, 50)
            val workflows = listOf(testWorkflow)
            val page = PageImpl(workflows, pageable, 1)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = WorkflowSourceType.MANUAL,
                    owner = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows(sourceType = sourceType)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].sourceType).isEqualTo(WorkflowSourceType.MANUAL)
        }

        @Test
        @DisplayName("should return workflows with owner filter")
        fun `should return workflows with owner filter`() {
            // Given
            val owner = "test@example.com"
            val pageable = PageRequest.of(0, 50)
            val workflows = listOf(testWorkflow)
            val page = PageImpl(workflows, pageable, 1)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = null,
                    owner = owner,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows(owner = owner)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].owner).isEqualTo(owner)
        }

        @Test
        @DisplayName("should apply limit and offset correctly")
        fun `should apply limit and offset correctly`() {
            // Given
            val limit = 10
            val offset = 5
            val pageable = PageRequest.of(0, 10) // offset/limit = 5/10 = 0
            val workflows = listOf(testWorkflow)
            val page = PageImpl(workflows, pageable, 1)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows(limit = limit, offset = offset)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                workflowRepositoryDsl.findByFilters(
                    status = null,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return empty list when no workflows match filters")
        fun `should return empty list when no workflows match filters`() {
            // Given
            val status = "DISABLED"
            val pageable = PageRequest.of(0, 50)
            val page = PageImpl<WorkflowEntity>(emptyList(), pageable, 0)

            every {
                workflowRepositoryDsl.findByFilters(
                    status = WorkflowStatus.DISABLED,
                    sourceType = null,
                    owner = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflows(status = status)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getWorkflow")
    inner class GetWorkflow {
        @Test
        @DisplayName("should return workflow when found by dataset name")
        fun `should return workflow when found by dataset name`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow

            // When
            val result = workflowService.getWorkflow(datasetName)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.datasetName).isEqualTo(datasetName)
            assertThat(result?.owner).isEqualTo("test@example.com")
            verify(exactly = 1) { workflowRepositoryJpa.findByDatasetName(datasetName) }
        }

        @Test
        @DisplayName("should return null when workflow not found")
        fun `should return null when workflow not found`() {
            // Given
            val datasetName = "nonexistent.dataset.name"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns null

            // When
            val result = workflowService.getWorkflow(datasetName)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { workflowRepositoryJpa.findByDatasetName(datasetName) }
        }

        @Test
        @DisplayName("should return null for soft-deleted workflow")
        fun `should return null for soft-deleted workflow`() {
            // Given
            val datasetName = "deleted.workflow.name"
            val deletedWorkflow =
                testWorkflow.apply {
                    deletedAt = LocalDateTime.now()
                }
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns deletedWorkflow

            // When
            val result = workflowService.getWorkflow(datasetName)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getWorkflowOrThrow")
    inner class GetWorkflowOrThrow {
        @Test
        @DisplayName("should return workflow when found")
        fun `should return workflow when found`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow

            // When
            val result = workflowService.getWorkflowOrThrow(datasetName)

            // Then
            assertThat(result.datasetName).isEqualTo(datasetName)
        }

        @Test
        @DisplayName("should throw WorkflowNotFoundException when not found")
        fun `should throw WorkflowNotFoundException when not found`() {
            // Given
            val datasetName = "nonexistent.workflow.name"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns null

            // When & Then
            val exception =
                assertThrows<WorkflowNotFoundException> {
                    workflowService.getWorkflowOrThrow(datasetName)
                }

            assertThat(exception.message).contains(datasetName)
        }
    }

    @Nested
    @DisplayName("getWorkflowRun")
    inner class GetWorkflowRun {
        @Test
        @DisplayName("should return workflow run when found by run ID")
        fun `should return workflow run when found by run ID`() {
            // Given
            val runId = "iceberg_analytics_users_manual_20250102_120000"
            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun

            // When
            val result = workflowService.getWorkflowRun(runId)

            // Then
            assertThat(result).isNotNull()
            assertThat(result?.runId).isEqualTo(runId)
            assertThat(result?.triggeredBy).isEqualTo("test@example.com")
            verify(exactly = 1) { workflowRunRepositoryJpa.findByRunId(runId) }
        }

        @Test
        @DisplayName("should return null when workflow run not found")
        fun `should return null when workflow run not found`() {
            // Given
            val runId = "nonexistent_run"
            every { workflowRunRepositoryJpa.findByRunId(runId) } returns null

            // When
            val result = workflowService.getWorkflowRun(runId)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { workflowRunRepositoryJpa.findByRunId(runId) }
        }
    }

    @Nested
    @DisplayName("getWorkflowRunOrThrow")
    inner class GetWorkflowRunOrThrow {
        @Test
        @DisplayName("should return workflow run when found")
        fun `should return workflow run when found`() {
            // Given
            val runId = "iceberg_analytics_users_manual_20250102_120000"
            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun

            // When
            val result = workflowService.getWorkflowRunOrThrow(runId)

            // Then
            assertThat(result.runId).isEqualTo(runId)
        }

        @Test
        @DisplayName("should throw WorkflowRunNotFoundException when not found")
        fun `should throw WorkflowRunNotFoundException when not found`() {
            // Given
            val runId = "nonexistent_run"
            every { workflowRunRepositoryJpa.findByRunId(runId) } returns null

            // When & Then
            val exception =
                assertThrows<WorkflowRunNotFoundException> {
                    workflowService.getWorkflowRunOrThrow(runId)
                }

            assertThat(exception.message).contains(runId)
        }
    }

    @Nested
    @DisplayName("getWorkflowRunWithSync")
    inner class GetWorkflowRunWithSync {
        @Test
        @DisplayName("should sync status with Airflow when run is not finished")
        fun `should sync status with Airflow when run is not finished`() {
            // Given
            val runId = "test_run_123"
            testWorkflowRun.status = WorkflowRunStatus.RUNNING
            val airflowStatus =
                AirflowDAGRunStatus(
                    dagRunId = runId,
                    state = AirflowDAGRunState.SUCCESS,
                    startDate = LocalDateTime.now().minusMinutes(5),
                    endDate = LocalDateTime.now(),
                    executionDate = LocalDateTime.of(2025, 1, 2, 12, 0, 0),
                    logsUrl = "https://airflow.example.com/logs/123",
                )

            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun
            every { workflowRepositoryJpa.findByDatasetName(testWorkflowRun.datasetName) } returns testWorkflow
            every { airflowClient.getDAGRun(testWorkflow.airflowDagId, runId) } returns airflowStatus
            every { workflowRunRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.getWorkflowRunWithSync(runId)

            // Then
            assertThat(result.status).isEqualTo(WorkflowRunStatus.SUCCESS)
            verify(exactly = 1) { airflowClient.getDAGRun(testWorkflow.airflowDagId, runId) }
            verify(exactly = 1) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should not sync status when run is already finished")
        fun `should not sync status when run is already finished`() {
            // Given
            val runId = "test_run_123"
            testWorkflowRun.status = WorkflowRunStatus.SUCCESS

            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun

            // When
            val result = workflowService.getWorkflowRunWithSync(runId)

            // Then
            assertThat(result.status).isEqualTo(WorkflowRunStatus.SUCCESS)
            verify(exactly = 0) { airflowClient.getDAGRun(any(), any()) }
            verify(exactly = 0) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should handle Airflow client exception gracefully")
        fun `should handle Airflow client exception gracefully`() {
            // Given
            val runId = "test_run_123"
            testWorkflowRun.status = WorkflowRunStatus.RUNNING

            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun
            every { workflowRepositoryJpa.findByDatasetName(testWorkflowRun.datasetName) } returns testWorkflow
            every { airflowClient.getDAGRun(testWorkflow.airflowDagId, runId) } throws RuntimeException("Airflow error")

            // When
            val result = workflowService.getWorkflowRunWithSync(runId)

            // Then
            assertThat(result.status).isEqualTo(WorkflowRunStatus.RUNNING) // Status unchanged
            verify(exactly = 1) { airflowClient.getDAGRun(testWorkflow.airflowDagId, runId) }
            verify(exactly = 0) { workflowRunRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("getWorkflowHistory")
    inner class GetWorkflowHistory {
        @Test
        @DisplayName("should return workflow history without filters")
        fun `should return workflow history without filters`() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val runs = listOf(testWorkflowRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                workflowRunRepositoryDsl.findRunsByFilters(
                    datasetName = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflowHistory()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].runId).isEqualTo(testWorkflowRun.runId)
        }

        @Test
        @DisplayName("should return workflow history with dataset name filter")
        fun `should return workflow history with dataset name filter`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val pageable = PageRequest.of(0, 20)
            val runs = listOf(testWorkflowRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                workflowRunRepositoryDsl.findRunsByFilters(
                    datasetName = datasetName,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflowHistory(datasetName = datasetName)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].datasetName).isEqualTo(datasetName)
        }

        @Test
        @DisplayName("should return workflow history with date range filters")
        fun `should return workflow history with date range filters`() {
            // Given
            val startDate = "2025-01-01"
            val endDate = "2025-01-02"
            val startDateTime = LocalDate.parse(startDate).atStartOfDay()
            val endDateTime = LocalDate.parse(endDate).plusDays(1).atStartOfDay()
            val pageable = PageRequest.of(0, 20)
            val runs = listOf(testWorkflowRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                workflowRunRepositoryDsl.findRunsByFilters(
                    datasetName = null,
                    startDate = startDateTime,
                    endDate = endDateTime,
                    pageable = pageable,
                )
            } returns page

            // When
            val result =
                workflowService.getWorkflowHistory(
                    startDate = startDate,
                    endDate = endDate,
                )

            // Then
            assertThat(result).hasSize(1)
        }

        @Test
        @DisplayName("should apply limit correctly")
        fun `should apply limit correctly`() {
            // Given
            val limit = 10
            val pageable = PageRequest.of(0, 10)
            val runs = listOf(testWorkflowRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                workflowRunRepositoryDsl.findRunsByFilters(
                    datasetName = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = workflowService.getWorkflowHistory(limit = limit)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                workflowRunRepositoryDsl.findRunsByFilters(
                    datasetName = null,
                    startDate = null,
                    endDate = null,
                    pageable = pageable,
                )
            }
        }
    }

    @Nested
    @DisplayName("registerWorkflow")
    inner class RegisterWorkflow {
        @Test
        @DisplayName("should register workflow successfully when name does not exist")
        fun `should register workflow successfully when name does not exist`() {
            // Given
            val datasetName = "new.dataset.name"
            val sourceType = WorkflowSourceType.MANUAL
            val schedule = testScheduleInfo
            val owner = "owner@example.com"
            val team = "data-team"
            val description = "New workflow"
            val yamlContent = "workflow:\n  tasks:\n    - name: test"
            val s3Path = "s3://bucket/workflows/new.dataset.name.yaml"
            val dagId = "new_dataset_name"

            every { workflowRepositoryJpa.existsByDatasetName(datasetName) } returns false
            every { workflowStorage.saveWorkflowYaml(datasetName, sourceType, yamlContent) } returns s3Path
            every { airflowClient.createDAG(datasetName, schedule, s3Path) } returns dagId

            val savedWorkflowSlot = slot<WorkflowEntity>()
            every { workflowRepositoryJpa.save(capture(savedWorkflowSlot)) } answers { savedWorkflowSlot.captured }

            // When
            val result =
                workflowService.registerWorkflow(
                    datasetName = datasetName,
                    sourceType = sourceType,
                    schedule = schedule,
                    owner = owner,
                    team = team,
                    description = description,
                    yamlContent = yamlContent,
                )

            // Then
            assertThat(result.datasetName).isEqualTo(datasetName)
            assertThat(result.sourceType).isEqualTo(sourceType)
            assertThat(result.owner).isEqualTo(owner)
            assertThat(result.team).isEqualTo(team)
            assertThat(result.description).isEqualTo(description)
            assertThat(result.s3Path).isEqualTo(s3Path)
            assertThat(result.airflowDagId).isEqualTo(dagId)
            assertThat(result.status).isEqualTo(WorkflowStatus.ACTIVE)

            verify(exactly = 1) { workflowRepositoryJpa.existsByDatasetName(datasetName) }
            verify(exactly = 1) { workflowStorage.saveWorkflowYaml(datasetName, sourceType, yamlContent) }
            verify(exactly = 1) { airflowClient.createDAG(datasetName, schedule, s3Path) }
            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorkflowAlreadyExistsException when workflow already exists")
        fun `should throw WorkflowAlreadyExistsException when workflow already exists`() {
            // Given
            val datasetName = "existing.workflow.name"
            every { workflowRepositoryJpa.existsByDatasetName(datasetName) } returns true

            // When & Then
            val exception =
                assertThrows<WorkflowAlreadyExistsException> {
                    workflowService.registerWorkflow(
                        datasetName = datasetName,
                        sourceType = WorkflowSourceType.MANUAL,
                        schedule = testScheduleInfo,
                        owner = "test@example.com",
                        yamlContent = "test: yaml",
                    )
                }

            assertThat(exception.message).contains(datasetName)
            verify(exactly = 1) { workflowRepositoryJpa.existsByDatasetName(datasetName) }
            verify(exactly = 0) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should handle storage failure during registration")
        fun `should handle storage failure during registration`() {
            // Given
            val datasetName = "test.dataset.name"
            every { workflowRepositoryJpa.existsByDatasetName(datasetName) } returns false
            every { workflowStorage.saveWorkflowYaml(any(), any(), any()) } throws RuntimeException("Storage error")

            // When & Then
            assertThrows<RuntimeException> {
                workflowService.registerWorkflow(
                    datasetName = datasetName,
                    sourceType = WorkflowSourceType.MANUAL,
                    schedule = testScheduleInfo,
                    owner = "test@example.com",
                    yamlContent = "test: yaml",
                )
            }

            verify(exactly = 0) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should validate cron expression and throw exception for invalid cron")
        fun `should validate cron expression and throw exception for invalid cron`() {
            // Given
            val datasetName = "test.dataset.name"
            val invalidSchedule =
                ScheduleInfo(
                    cron = "invalid cron",
                    timezone = "UTC",
                )
            every { workflowRepositoryJpa.existsByDatasetName(datasetName) } returns false

            // When & Then
            assertThrows<IllegalArgumentException> {
                workflowService.registerWorkflow(
                    datasetName = datasetName,
                    sourceType = WorkflowSourceType.MANUAL,
                    schedule = invalidSchedule,
                    owner = "test@example.com",
                    yamlContent = "test: yaml",
                )
            }

            verify(exactly = 0) { workflowRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("triggerWorkflowRun")
    inner class TriggerWorkflowRun {
        @Test
        @DisplayName("should trigger workflow run successfully")
        fun `should trigger workflow run successfully`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val params = mapOf("date" to "2025-01-02", "force" to true)
            val dryRun = false
            val triggeredBy = "user@example.com"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.triggerDAGRun(any(), any(), any()) } returns "triggered_successfully"

            val savedRunSlot = slot<WorkflowRunEntity>()
            every { workflowRunRepositoryJpa.save(capture(savedRunSlot)) } answers { savedRunSlot.captured }

            // When
            val result =
                workflowService.triggerWorkflowRun(
                    datasetName = datasetName,
                    params = params,
                    dryRun = dryRun,
                    triggeredBy = triggeredBy,
                )

            // Then
            assertThat(result.datasetName).isEqualTo(datasetName)
            assertThat(result.triggeredBy).isEqualTo(triggeredBy)
            assertThat(result.runType).isEqualTo(WorkflowRunType.MANUAL)
            assertThat(result.status).isEqualTo(WorkflowRunStatus.PENDING)
            assertThat(result.params).contains("date")
            assertThat(result.params).contains("force")

            verify(exactly = 1) { workflowRepositoryJpa.findByDatasetName(datasetName) }
            verify(exactly = 1) { airflowClient.triggerDAGRun(any(), any(), any()) }
            verify(exactly = 1) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorkflowNotFoundException when workflow not found")
        fun `should throw WorkflowNotFoundException when workflow not found`() {
            // Given
            val datasetName = "nonexistent.dataset.name"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns null

            // When & Then
            assertThrows<WorkflowNotFoundException> {
                workflowService.triggerWorkflowRun(datasetName = datasetName)
            }

            verify(exactly = 0) { airflowClient.triggerDAGRun(any(), any(), any()) }
            verify(exactly = 0) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw IllegalStateException when workflow cannot run")
        fun `should throw IllegalStateException when workflow cannot run`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val pausedWorkflow = testWorkflow.apply { status = WorkflowStatus.PAUSED }
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns pausedWorkflow

            // When & Then
            val exception =
                assertThrows<IllegalStateException> {
                    workflowService.triggerWorkflowRun(datasetName = datasetName)
                }

            assertThat(exception.message).contains("Cannot run workflow")
            assertThat(exception.message).contains("PAUSED")
            verify(exactly = 0) { airflowClient.triggerDAGRun(any(), any(), any()) }
        }

        @Test
        @DisplayName("should handle empty params correctly")
        fun `should handle empty params correctly`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.triggerDAGRun(any(), any(), any()) } returns "triggered_successfully"

            val savedRunSlot = slot<WorkflowRunEntity>()
            every { workflowRunRepositoryJpa.save(capture(savedRunSlot)) } answers { savedRunSlot.captured }

            // When
            val result = workflowService.triggerWorkflowRun(datasetName = datasetName)

            // Then
            assertThat(result.params).isNull()
            verify(exactly = 1) { workflowRunRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("triggerBackfill")
    inner class TriggerBackfill {
        @Test
        @DisplayName("should trigger backfill successfully for date range")
        fun `should trigger backfill successfully for date range`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val startDate = "2025-01-01"
            val endDate = "2025-01-03"
            val params = mapOf("test" to "value")
            val parallel = false
            val triggeredBy = "admin@example.com"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.triggerDAGRun(any(), any(), any()) } returns "triggered_successfully"

            val savedRunsSlots = mutableListOf<WorkflowRunEntity>()
            every { workflowRunRepositoryJpa.save(capture(savedRunsSlots)) } answers { firstArg() }

            // When
            val result =
                workflowService.triggerBackfill(
                    datasetName = datasetName,
                    startDate = startDate,
                    endDate = endDate,
                    params = params,
                    parallel = parallel,
                    triggeredBy = triggeredBy,
                )

            // Then
            assertThat(result).hasSize(3) // 2025-01-01, 2025-01-02, 2025-01-03
            result.forEach { run ->
                assertThat(run.datasetName).isEqualTo(datasetName)
                assertThat(run.triggeredBy).isEqualTo(triggeredBy)
                assertThat(run.runType).isEqualTo(WorkflowRunType.BACKFILL)
                assertThat(run.status).isEqualTo(WorkflowRunStatus.PENDING)
                assertThat(run.params).contains("date")
                assertThat(run.params).contains("backfill")
            }

            verify(exactly = 1) { workflowRepositoryJpa.findByDatasetName(datasetName) }
            verify(exactly = 3) { airflowClient.triggerDAGRun(any(), any(), any()) }
            verify(exactly = 3) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorkflowNotFoundException when workflow not found")
        fun `should throw WorkflowNotFoundException when workflow not found`() {
            // Given
            val datasetName = "nonexistent.dataset.name"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns null

            // When & Then
            assertThrows<WorkflowNotFoundException> {
                workflowService.triggerBackfill(
                    datasetName = datasetName,
                    startDate = "2025-01-01",
                    endDate = "2025-01-02",
                )
            }

            verify(exactly = 0) { airflowClient.triggerDAGRun(any(), any(), any()) }
        }

        @Test
        @DisplayName("should handle single date backfill")
        fun `should handle single date backfill`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val startDate = "2025-01-01"
            val endDate = "2025-01-01"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.triggerDAGRun(any(), any(), any()) } returns "triggered_successfully"

            val savedRunSlot = slot<WorkflowRunEntity>()
            every { workflowRunRepositoryJpa.save(capture(savedRunSlot)) } answers { savedRunSlot.captured }

            // When
            val result =
                workflowService.triggerBackfill(
                    datasetName = datasetName,
                    startDate = startDate,
                    endDate = endDate,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].params).contains("2025-01-01")
        }
    }

    @Nested
    @DisplayName("stopWorkflowRun")
    inner class StopWorkflowRun {
        @Test
        @DisplayName("should stop workflow run successfully")
        fun `should stop workflow run successfully`() {
            // Given
            val runId = "test_run_123"
            val reason = "User requested"
            val stoppedBy = "admin@example.com"
            testWorkflowRun.status = WorkflowRunStatus.RUNNING

            every { workflowRunRepositoryJpa.findByRunId(runId) } returns testWorkflowRun
            every { workflowRepositoryJpa.findByDatasetName(testWorkflowRun.datasetName) } returns testWorkflow
            every { airflowClient.stopDAGRun(testWorkflow.airflowDagId, runId) } returns true
            every { workflowRunRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.stopWorkflowRun(runId, reason, stoppedBy)

            // Then
            assertThat(result.status).isEqualTo(WorkflowRunStatus.STOPPING)
            assertThat(result.stopReason).isEqualTo(reason)
            assertThat(result.stoppedBy).isEqualTo(stoppedBy)
            assertThat(result.stoppedAt).isNotNull()

            verify(exactly = 1) { airflowClient.stopDAGRun(testWorkflow.airflowDagId, runId) }
            verify(exactly = 1) { workflowRunRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorkflowRunNotFoundException when run not found")
        fun `should throw WorkflowRunNotFoundException when run not found`() {
            // Given
            val runId = "nonexistent_run"
            every { workflowRunRepositoryJpa.findByRunId(runId) } returns null

            // When & Then
            assertThrows<WorkflowRunNotFoundException> {
                workflowService.stopWorkflowRun(runId)
            }

            verify(exactly = 0) { airflowClient.stopDAGRun(any(), any()) }
        }

        @Test
        @DisplayName("should throw IllegalStateException when run is already finished")
        fun `should throw IllegalStateException when run is already finished`() {
            // Given
            val runId = "finished_run"
            val finishedRun =
                WorkflowRunEntity(
                    runId = runId,
                    datasetName = "iceberg.analytics.users",
                    status = WorkflowRunStatus.SUCCESS,
                    triggeredBy = "test@example.com",
                    runType = WorkflowRunType.MANUAL,
                    startedAt = LocalDateTime.now(),
                )

            every { workflowRunRepositoryJpa.findByRunId(runId) } returns finishedRun
            every { workflowRepositoryJpa.findByDatasetName("iceberg.analytics.users") } returns testWorkflow

            // When & Then
            val exception =
                assertThrows<IllegalStateException> {
                    workflowService.stopWorkflowRun(runId)
                }

            assertThat(exception.message).contains("Cannot stop run")
            assertThat(exception.message).contains("SUCCESS")
            verify(exactly = 0) { airflowClient.stopDAGRun(any(), any()) }
        }
    }

    @Nested
    @DisplayName("pauseWorkflow")
    inner class PauseWorkflow {
        @Test
        @DisplayName("should pause workflow successfully")
        fun `should pause workflow successfully`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val reason = "Maintenance"
            val pausedBy = "admin@example.com"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.pauseDAG(testWorkflow.airflowDagId, true) } returns true
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.pauseWorkflow(datasetName, reason, pausedBy)

            // Then
            assertThat(result.status).isEqualTo(WorkflowStatus.PAUSED)
            assertThat(result.isPaused()).isTrue()
            assertThat(result.canRun()).isFalse()

            verify(exactly = 1) { airflowClient.pauseDAG(testWorkflow.airflowDagId, true) }
            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw WorkflowNotFoundException when workflow not found")
        fun `should throw WorkflowNotFoundException when workflow not found`() {
            // Given
            val datasetName = "nonexistent.workflow.name"
            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns null

            // When & Then
            assertThrows<WorkflowNotFoundException> {
                workflowService.pauseWorkflow(datasetName)
            }

            verify(exactly = 0) { airflowClient.pauseDAG(any(), any()) }
        }

        @Test
        @DisplayName("should throw IllegalStateException when workflow is not active")
        fun `should throw IllegalStateException when workflow is not active`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val pausedWorkflow = testWorkflow.apply { status = WorkflowStatus.PAUSED }

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns pausedWorkflow

            // When & Then
            val exception =
                assertThrows<IllegalStateException> {
                    workflowService.pauseWorkflow(datasetName)
                }

            assertThat(exception.message).contains("Cannot pause workflow")
            assertThat(exception.message).contains("PAUSED")
            verify(exactly = 0) { airflowClient.pauseDAG(any(), any()) }
        }
    }

    @Nested
    @DisplayName("unpauseWorkflow")
    inner class UnpauseWorkflow {
        @Test
        @DisplayName("should unpause workflow successfully")
        fun `should unpause workflow successfully`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val unpausedBy = "admin@example.com"
            val pausedWorkflow = testWorkflow.apply { status = WorkflowStatus.PAUSED }

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns pausedWorkflow
            every { airflowClient.pauseDAG(testWorkflow.airflowDagId, false) } returns true
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.unpauseWorkflow(datasetName, unpausedBy)

            // Then
            assertThat(result.status).isEqualTo(WorkflowStatus.ACTIVE)
            assertThat(result.isActive()).isTrue()
            assertThat(result.canRun()).isTrue()

            verify(exactly = 1) { airflowClient.pauseDAG(testWorkflow.airflowDagId, false) }
            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw IllegalStateException when workflow is not paused")
        fun `should throw IllegalStateException when workflow is not paused`() {
            // Given
            val datasetName = "iceberg.analytics.users"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow

            // When & Then
            val exception =
                assertThrows<IllegalStateException> {
                    workflowService.unpauseWorkflow(datasetName)
                }

            assertThat(exception.message).contains("Cannot unpause workflow")
            assertThat(exception.message).contains("ACTIVE")
            verify(exactly = 0) { airflowClient.pauseDAG(any(), any()) }
        }
    }

    @Nested
    @DisplayName("unregisterWorkflow")
    inner class UnregisterWorkflow {
        @Test
        @DisplayName("should unregister workflow successfully when no active runs")
        fun `should unregister workflow successfully when no active runs`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val unregisteredBy = "admin@example.com"

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every {
                workflowRunRepositoryJpa.findByDatasetNameAndStatus(
                    datasetName,
                    WorkflowRunStatus.RUNNING,
                )
            } returns
                emptyList()
            every { airflowClient.deleteDAG(testWorkflow.airflowDagId) } returns true
            every { workflowStorage.deleteWorkflowYaml(testWorkflow.s3Path) } returns true
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.unregisterWorkflow(datasetName, false, unregisteredBy)

            // Then
            assertThat(result.status).isEqualTo(WorkflowStatus.DISABLED)
            assertThat(result.deletedAt).isNotNull()

            verify(
                exactly = 1,
            ) { workflowRunRepositoryJpa.findByDatasetNameAndStatus(datasetName, WorkflowRunStatus.RUNNING) }
            verify(exactly = 1) { airflowClient.deleteDAG(testWorkflow.airflowDagId) }
            verify(exactly = 1) { workflowStorage.deleteWorkflowYaml(testWorkflow.s3Path) }
            verify(exactly = 1) { workflowRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw IllegalStateException when active runs exist and not forced")
        fun `should throw IllegalStateException when active runs exist and not forced`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val activeRuns = listOf(testWorkflowRun.apply { status = WorkflowRunStatus.RUNNING })

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every {
                workflowRunRepositoryJpa.findByDatasetNameAndStatus(
                    datasetName,
                    WorkflowRunStatus.RUNNING,
                )
            } returns
                activeRuns

            // When & Then
            val exception =
                assertThrows<IllegalStateException> {
                    workflowService.unregisterWorkflow(datasetName, force = false)
                }

            assertThat(exception.message).contains("Cannot unregister workflow with active runs")
            verify(exactly = 0) { airflowClient.deleteDAG(any()) }
        }

        @Test
        @DisplayName("should unregister workflow with force even when active runs exist")
        fun `should unregister workflow with force even when active runs exist`() {
            // Given
            val datasetName = "iceberg.analytics.users"
            val activeRuns = listOf(testWorkflowRun.apply { status = WorkflowRunStatus.RUNNING })

            every { workflowRepositoryJpa.findByDatasetName(datasetName) } returns testWorkflow
            every { airflowClient.deleteDAG(testWorkflow.airflowDagId) } returns true
            every { workflowStorage.deleteWorkflowYaml(testWorkflow.s3Path) } returns true
            every { workflowRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = workflowService.unregisterWorkflow(datasetName, force = true)

            // Then
            assertThat(result.status).isEqualTo(WorkflowStatus.DISABLED)
            assertThat(result.deletedAt).isNotNull()

            // Should not check for active runs when force = true
            verify(exactly = 0) { workflowRunRepositoryJpa.findByDatasetNameAndStatus(any(), any()) }
            verify(exactly = 1) { airflowClient.deleteDAG(testWorkflow.airflowDagId) }
        }
    }
}
