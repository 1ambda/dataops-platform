package com.github.lambda.domain.model.workflow

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * WorkflowRunEntity Unit Tests
 *
 * Tests business logic and validation rules for WorkflowRunEntity.
 */
@DisplayName("WorkflowRunEntity Unit Tests")
class WorkflowRunEntityTest {
    private lateinit var testWorkflowRun: WorkflowRunEntity

    @BeforeEach
    fun setUp() {
        testWorkflowRun =
            WorkflowRunEntity(
                runId = "test_run_20250102_120000",
                datasetName = "iceberg.analytics.users",
                status = WorkflowRunStatus.PENDING,
                triggeredBy = "test@example.com",
                runType = WorkflowRunType.MANUAL,
            )
    }

    @Nested
    @DisplayName("Status checks")
    inner class StatusChecks {
        @Test
        @DisplayName("should return correct status checks for PENDING")
        fun `should return correct status checks for PENDING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.PENDING

            // When & Then
            assertThat(testWorkflowRun.isPending()).isTrue()
            assertThat(testWorkflowRun.isRunning()).isFalse()
            assertThat(testWorkflowRun.isCompleted()).isFalse()
            assertThat(testWorkflowRun.isFailed()).isFalse()
            assertThat(testWorkflowRun.isStopped()).isFalse()
            assertThat(testWorkflowRun.isStopping()).isFalse()
            assertThat(testWorkflowRun.isFinished()).isFalse()
        }

        @Test
        @DisplayName("should return correct status checks for RUNNING")
        fun `should return correct status checks for RUNNING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING

            // When & Then
            assertThat(testWorkflowRun.isRunning()).isTrue()
            assertThat(testWorkflowRun.isPending()).isFalse()
            assertThat(testWorkflowRun.isCompleted()).isFalse()
            assertThat(testWorkflowRun.isFailed()).isFalse()
            assertThat(testWorkflowRun.isStopped()).isFalse()
            assertThat(testWorkflowRun.isStopping()).isFalse()
            assertThat(testWorkflowRun.isFinished()).isFalse()
        }

        @Test
        @DisplayName("should return correct status checks for SUCCESS")
        fun `should return correct status checks for SUCCESS`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.SUCCESS

            // When & Then
            assertThat(testWorkflowRun.isCompleted()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.isRunning()).isFalse()
            assertThat(testWorkflowRun.isPending()).isFalse()
            assertThat(testWorkflowRun.isFailed()).isFalse()
            assertThat(testWorkflowRun.isStopped()).isFalse()
            assertThat(testWorkflowRun.isStopping()).isFalse()
        }

        @Test
        @DisplayName("should return correct status checks for FAILED")
        fun `should return correct status checks for FAILED`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.FAILED

            // When & Then
            assertThat(testWorkflowRun.isFailed()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.isRunning()).isFalse()
            assertThat(testWorkflowRun.isPending()).isFalse()
            assertThat(testWorkflowRun.isCompleted()).isFalse()
            assertThat(testWorkflowRun.isStopped()).isFalse()
            assertThat(testWorkflowRun.isStopping()).isFalse()
        }

        @Test
        @DisplayName("should return correct status checks for STOPPED")
        fun `should return correct status checks for STOPPED`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.STOPPED

            // When & Then
            assertThat(testWorkflowRun.isStopped()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.isRunning()).isFalse()
            assertThat(testWorkflowRun.isPending()).isFalse()
            assertThat(testWorkflowRun.isCompleted()).isFalse()
            assertThat(testWorkflowRun.isFailed()).isFalse()
            assertThat(testWorkflowRun.isStopping()).isFalse()
        }

        @Test
        @DisplayName("should return correct status checks for STOPPING")
        fun `should return correct status checks for STOPPING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.STOPPING

            // When & Then
            assertThat(testWorkflowRun.isStopping()).isTrue()
            assertThat(testWorkflowRun.isRunning()).isFalse()
            assertThat(testWorkflowRun.isPending()).isFalse()
            assertThat(testWorkflowRun.isCompleted()).isFalse()
            assertThat(testWorkflowRun.isFailed()).isFalse()
            assertThat(testWorkflowRun.isStopped()).isFalse()
            assertThat(testWorkflowRun.isFinished()).isFalse()
        }
    }

    @Nested
    @DisplayName("Run type checks")
    inner class RunTypeChecks {
        @Test
        @DisplayName("should return true for isManual when runType is MANUAL")
        fun `should return true for isManual when runType is MANUAL`() {
            // Given
            testWorkflowRun.runType = WorkflowRunType.MANUAL

            // When & Then
            assertThat(testWorkflowRun.isManual()).isTrue()
            assertThat(testWorkflowRun.isScheduled()).isFalse()
            assertThat(testWorkflowRun.isBackfill()).isFalse()
        }

        @Test
        @DisplayName("should return true for isScheduled when runType is SCHEDULED")
        fun `should return true for isScheduled when runType is SCHEDULED`() {
            // Given
            testWorkflowRun.runType = WorkflowRunType.SCHEDULED

            // When & Then
            assertThat(testWorkflowRun.isScheduled()).isTrue()
            assertThat(testWorkflowRun.isManual()).isFalse()
            assertThat(testWorkflowRun.isBackfill()).isFalse()
        }

        @Test
        @DisplayName("should return true for isBackfill when runType is BACKFILL")
        fun `should return true for isBackfill when runType is BACKFILL`() {
            // Given
            testWorkflowRun.runType = WorkflowRunType.BACKFILL

            // When & Then
            assertThat(testWorkflowRun.isBackfill()).isTrue()
            assertThat(testWorkflowRun.isManual()).isFalse()
            assertThat(testWorkflowRun.isScheduled()).isFalse()
        }
    }

    @Nested
    @DisplayName("Status management")
    inner class StatusManagement {
        @Test
        @DisplayName("should start run when status is PENDING")
        fun `should start run when status is PENDING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.PENDING
            val beforeStart = LocalDateTime.now()

            // When
            testWorkflowRun.start()

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.RUNNING)
            assertThat(testWorkflowRun.isRunning()).isTrue()
            assertThat(testWorkflowRun.startedAt).isNotNull()
            assertThat(testWorkflowRun.startedAt).isAfterOrEqualTo(beforeStart)
        }

        @Test
        @DisplayName("should throw exception when starting non-pending run")
        fun `should throw exception when starting non-pending run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING

            // When & Then
            assertThatThrownBy { testWorkflowRun.start() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot start run. Current status: RUNNING")
        }

        @Test
        @DisplayName("should complete run when status is RUNNING")
        fun `should complete run when status is RUNNING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING
            val beforeComplete = LocalDateTime.now()

            // When
            testWorkflowRun.complete()

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.SUCCESS)
            assertThat(testWorkflowRun.isCompleted()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.endedAt).isNotNull()
            assertThat(testWorkflowRun.endedAt).isAfterOrEqualTo(beforeComplete)
        }

        @Test
        @DisplayName("should throw exception when completing non-running run")
        fun `should throw exception when completing non-running run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.PENDING

            // When & Then
            assertThatThrownBy { testWorkflowRun.complete() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot complete run. Current status: PENDING")
        }

        @Test
        @DisplayName("should fail run when status allows it")
        fun `should fail run when status allows it`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING
            val beforeFail = LocalDateTime.now()

            // When
            testWorkflowRun.fail()

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.FAILED)
            assertThat(testWorkflowRun.isFailed()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.endedAt).isNotNull()
            assertThat(testWorkflowRun.endedAt).isAfterOrEqualTo(beforeFail)
        }

        @Test
        @DisplayName("should fail run from PENDING status")
        fun `should fail run from PENDING status`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.PENDING

            // When
            testWorkflowRun.fail()

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.FAILED)
            assertThat(testWorkflowRun.isFailed()).isTrue()
        }

        @Test
        @DisplayName("should throw exception when failing already successful run")
        fun `should throw exception when failing already successful run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.SUCCESS

            // When & Then
            assertThatThrownBy { testWorkflowRun.fail() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot fail run. Current status: SUCCESS")
        }

        @Test
        @DisplayName("should throw exception when failing already stopped run")
        fun `should throw exception when failing already stopped run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.STOPPED

            // When & Then
            assertThatThrownBy { testWorkflowRun.fail() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot fail run. Current status: STOPPED")
        }

        @Test
        @DisplayName("should request stop when status allows it")
        fun `should request stop when status allows it`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING
            val stoppedBy = "admin@example.com"
            val reason = "Requested by user"
            val beforeStop = LocalDateTime.now()

            // When
            testWorkflowRun.requestStop(stoppedBy, reason)

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.STOPPING)
            assertThat(testWorkflowRun.isStopping()).isTrue()
            assertThat(testWorkflowRun.stoppedBy).isEqualTo(stoppedBy)
            assertThat(testWorkflowRun.stopReason).isEqualTo(reason)
            assertThat(testWorkflowRun.stoppedAt).isNotNull()
            assertThat(testWorkflowRun.stoppedAt).isAfterOrEqualTo(beforeStop)
        }

        @Test
        @DisplayName("should request stop from PENDING status")
        fun `should request stop from PENDING status`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.PENDING
            val stoppedBy = "admin@example.com"

            // When
            testWorkflowRun.requestStop(stoppedBy)

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.STOPPING)
            assertThat(testWorkflowRun.stoppedBy).isEqualTo(stoppedBy)
            assertThat(testWorkflowRun.stopReason).isNull()
        }

        @Test
        @DisplayName("should throw exception when requesting stop on finished run")
        fun `should throw exception when requesting stop on finished run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.SUCCESS
            val stoppedBy = "admin@example.com"

            // When & Then
            assertThatThrownBy { testWorkflowRun.requestStop(stoppedBy) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot stop run. Current status: SUCCESS")
        }

        @Test
        @DisplayName("should complete stop when status is STOPPING")
        fun `should complete stop when status is STOPPING`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.STOPPING
            val beforeCompleteStop = LocalDateTime.now()

            // When
            testWorkflowRun.completeStop()

            // Then
            assertThat(testWorkflowRun.status).isEqualTo(WorkflowRunStatus.STOPPED)
            assertThat(testWorkflowRun.isStopped()).isTrue()
            assertThat(testWorkflowRun.isFinished()).isTrue()
            assertThat(testWorkflowRun.endedAt).isNotNull()
            assertThat(testWorkflowRun.endedAt).isAfterOrEqualTo(beforeCompleteStop)
        }

        @Test
        @DisplayName("should throw exception when completing stop on non-stopping run")
        fun `should throw exception when completing stop on non-stopping run`() {
            // Given
            testWorkflowRun.status = WorkflowRunStatus.RUNNING

            // When & Then
            assertThatThrownBy { testWorkflowRun.completeStop() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Cannot complete stop. Current status: RUNNING")
        }
    }

    @Nested
    @DisplayName("Duration calculation")
    inner class DurationCalculation {
        @Test
        @DisplayName("should return null duration when startedAt is null")
        fun `should return null duration when startedAt is null`() {
            // Given
            testWorkflowRun.startedAt = null
            testWorkflowRun.endedAt = null

            // When & Then
            assertThat(testWorkflowRun.getDurationSeconds()).isNull()
        }

        @Test
        @DisplayName("should calculate duration using current time when endedAt is null")
        fun `should calculate duration using current time when endedAt is null`() {
            // Given
            val startTime = LocalDateTime.now().minusMinutes(5)
            testWorkflowRun.startedAt = startTime
            testWorkflowRun.endedAt = null

            // When
            val duration = testWorkflowRun.getDurationSeconds()

            // Then
            assertThat(duration).isNotNull()
            assertThat(duration).isGreaterThan(290.0) // At least 4 minutes 50 seconds
            assertThat(duration).isLessThan(310.0) // At most 5 minutes 10 seconds
        }

        @Test
        @DisplayName("should calculate duration correctly when both times are set")
        fun `should calculate duration correctly when both times are set`() {
            // Given
            val startTime = LocalDateTime.of(2025, 1, 2, 12, 0, 0)
            val endTime = LocalDateTime.of(2025, 1, 2, 12, 5, 30) // 5 minutes 30 seconds later
            testWorkflowRun.startedAt = startTime
            testWorkflowRun.endedAt = endTime

            // When
            val duration = testWorkflowRun.getDurationSeconds()

            // Then
            assertThat(duration).isNotNull()
            assertThat(duration).isEqualTo(330.0) // 5 * 60 + 30 = 330 seconds
        }

        @Test
        @DisplayName("should return zero duration when start and end times are same")
        fun `should return zero duration when start and end times are same`() {
            // Given
            val time = LocalDateTime.of(2025, 1, 2, 12, 0, 0)
            testWorkflowRun.startedAt = time
            testWorkflowRun.endedAt = time

            // When
            val duration = testWorkflowRun.getDurationSeconds()

            // Then
            assertThat(duration).isNotNull()
            assertThat(duration).isEqualTo(0.0)
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    inner class ConstructorValidation {
        @Test
        @DisplayName("should create workflow run with all required fields")
        fun `should create workflow run with all required fields`() {
            // When
            val workflowRun =
                WorkflowRunEntity(
                    runId = "custom_run_id",
                    datasetName = "test.catalog.dataset",
                    status = WorkflowRunStatus.RUNNING,
                    triggeredBy = "user@example.com",
                    runType = WorkflowRunType.SCHEDULED,
                    startedAt = LocalDateTime.now(),
                    endedAt = null,
                    params = """{"key": "value"}""",
                    logsUrl = "https://airflow.example.com/logs/123",
                    stopReason = null,
                    stoppedBy = null,
                    stoppedAt = null,
                )

            // Then
            assertThat(workflowRun.runId).isEqualTo("custom_run_id")
            assertThat(workflowRun.datasetName).isEqualTo("test.catalog.dataset")
            assertThat(workflowRun.status).isEqualTo(WorkflowRunStatus.RUNNING)
            assertThat(workflowRun.triggeredBy).isEqualTo("user@example.com")
            assertThat(workflowRun.runType).isEqualTo(WorkflowRunType.SCHEDULED)
            assertThat(workflowRun.startedAt).isNotNull()
            assertThat(workflowRun.endedAt).isNull()
            assertThat(workflowRun.params).isEqualTo("""{"key": "value"}""")
            assertThat(workflowRun.logsUrl).isEqualTo("https://airflow.example.com/logs/123")
            assertThat(workflowRun.stopReason).isNull()
            assertThat(workflowRun.stoppedBy).isNull()
            assertThat(workflowRun.stoppedAt).isNull()
            assertThat(workflowRun.workflow).isNull()
        }

        @Test
        @DisplayName("should create workflow run with minimal required fields")
        fun `should create workflow run with minimal required fields`() {
            // When
            val workflowRun =
                WorkflowRunEntity(
                    runId = "minimal_run_id",
                    datasetName = "catalog.schema.dataset",
                    triggeredBy = "system",
                )

            // Then
            assertThat(workflowRun.runId).isEqualTo("minimal_run_id")
            assertThat(workflowRun.datasetName).isEqualTo("catalog.schema.dataset")
            assertThat(workflowRun.triggeredBy).isEqualTo("system")
            assertThat(workflowRun.status).isEqualTo(WorkflowRunStatus.PENDING) // default
            assertThat(workflowRun.runType).isEqualTo(WorkflowRunType.MANUAL) // default
            assertThat(workflowRun.startedAt).isNull()
            assertThat(workflowRun.endedAt).isNull()
            assertThat(workflowRun.params).isNull()
            assertThat(workflowRun.logsUrl).isNull()
            assertThat(workflowRun.stopReason).isNull()
            assertThat(workflowRun.stoppedBy).isNull()
            assertThat(workflowRun.stoppedAt).isNull()
            assertThat(workflowRun.workflow).isNull()
        }
    }
}
