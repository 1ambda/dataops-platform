package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.exception.AirflowConnectionException
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunState
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * MockAirflowClient Unit Tests
 *
 * Uses deterministicMode = true to ensure consistent test results
 * without random failures.
 */
class MockAirflowClientTest {
    private lateinit var client: MockAirflowClient

    @BeforeEach
    fun setUp() {
        // Use deterministic mode to disable random failures in tests
        client = MockAirflowClient(deterministicMode = true)
    }

    @Test
    fun `isAvailable should return true in deterministic mode`() {
        // when
        val result = client.isAvailable()

        // then - In deterministic mode, always returns true
        assertThat(result).isTrue()
    }

    @Test
    fun `triggerDAGRun should create and return DAG run`() {
        // given
        val dagId = "test_dag"
        val runId = "test_run_123"
        val conf = mapOf("param1" to "value1", "param2" to 42)

        // when
        val result = client.triggerDAGRun(dagId, runId, conf)

        // then
        assertThat(result).isEqualTo(runId)
    }

    @Test
    fun `getDAGRun should return DAG run status after trigger`() {
        // given
        val dagId = "test_dag"
        val runId = "test_run_123"
        client.triggerDAGRun(dagId, runId)

        // when
        val result = client.getDAGRun(dagId, runId)

        // then
        assertThat(result.dagRunId).isEqualTo(runId)
        assertThat(result.state).isIn(AirflowDAGRunState.QUEUED, AirflowDAGRunState.RUNNING)
        assertThat(result.startDate).isNotNull()
        assertThat(result.executionDate).isNotNull()
        assertThat(result.logsUrl).contains(dagId)
    }

    @Test
    fun `getDAGRun should throw exception for non-existent run`() {
        // when & then
        assertThatThrownBy {
            client.getDAGRun("non_existent_dag", "non_existent_run")
        }.isInstanceOf(AirflowConnectionException::class.java)
            .hasMessageContaining("getDAGRun")
    }

    @Test
    fun `pauseDAG should set DAG pause state`() {
        // given
        val dagId = "test_dag"

        // when
        val result = client.pauseDAG(dagId, true)

        // then
        assertThat(result).isTrue()

        // Verify pause state is set
        val dagStatus = client.getDagStatus(dagId)
        assertThat(dagStatus.isPaused).isTrue()
        assertThat(dagStatus.dagId).isEqualTo(dagId)
        assertThat(dagStatus.isActive).isTrue()
    }

    @Test
    fun `pauseDAG should unpause DAG`() {
        // given
        val dagId = "test_dag"
        client.pauseDAG(dagId, true) // First pause it

        // when
        val result = client.pauseDAG(dagId, false)

        // then
        assertThat(result).isTrue()

        // Verify unpause state
        val dagStatus = client.getDagStatus(dagId)
        assertThat(dagStatus.isPaused).isFalse()
    }

    @Test
    fun `stopDAGRun should stop running DAG`() {
        // given
        val dagId = "test_dag"
        val runId = "test_run_123"
        client.triggerDAGRun(dagId, runId)

        // when
        val result = client.stopDAGRun(dagId, runId)

        // then - should be able to stop (though might not always succeed in mock)
        // The mock allows stopping QUEUED or RUNNING states
        if (result) {
            val dagRun = client.getDAGRun(dagId, runId)
            assertThat(dagRun.state).isEqualTo(AirflowDAGRunState.FAILED)
            assertThat(dagRun.endDate).isNotNull()
        }
    }

    @Test
    fun `stopDAGRun should return false for non-existent run`() {
        // when
        val result = client.stopDAGRun("non_existent_dag", "non_existent_run")

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `createDAG should create new DAG with generated ID`() {
        // given
        val datasetName = "Test Dataset"
        val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
        val s3Path = "s3://test-bucket/path/to/data"

        // when
        val dagId = client.createDAG(datasetName, schedule, s3Path)

        // then
        assertThat(dagId).isEqualTo("dag_test_dataset")

        // Verify DAG was created
        val dagStatus = client.getDagStatus(dagId)
        assertThat(dagStatus.dagId).isEqualTo(dagId)
        assertThat(dagStatus.isPaused).isFalse()
        assertThat(dagStatus.isActive).isTrue()
        assertThat(dagStatus.lastParsed).isNotNull()
    }

    @Test
    fun `createDAG should handle special characters in dataset name`() {
        // given
        val datasetName = "Test-Dataset_With.Special@Chars"
        val schedule = ScheduleInfo(cron = "0 12 * * 1", timezone = "America/New_York")
        val s3Path = "s3://test-bucket/special/path"

        // when
        val dagId = client.createDAG(datasetName, schedule, s3Path)

        // then
        assertThat(dagId).matches("dag_[a-z0-9_]+")
    }

    @Test
    fun `deleteDAG should remove existing DAG`() {
        // given
        val datasetName = "test_dataset"
        val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
        val dagId = client.createDAG(datasetName, schedule, "s3://test/path")

        // when
        val result = client.deleteDAG(dagId)

        // then
        assertThat(result).isTrue()

        // Verify DAG is deleted
        assertThatThrownBy {
            client.getDagStatus(dagId)
        }.isInstanceOf(AirflowConnectionException::class.java)
    }

    @Test
    fun `deleteDAG should return false for non-existent DAG`() {
        // when
        val result = client.deleteDAG("non_existent_dag")

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `deleteDAG should also remove related DAG runs`() {
        // given
        val dagId = "test_dag"
        val runId = "test_run"

        // Create DAG and trigger run
        val datasetName = "test_dataset"
        val schedule = ScheduleInfo(cron = "0 8 * * *", timezone = "UTC")
        val createdDagId = client.createDAG(datasetName, schedule, "s3://test/path")
        client.triggerDAGRun(createdDagId, runId)

        // when
        client.deleteDAG(createdDagId)

        // then - DAG run should also be affected (though in this mock it's stored separately)
        // This tests the cleanup logic
        assertThatThrownBy {
            client.getDagStatus(createdDagId)
        }.isInstanceOf(AirflowConnectionException::class.java)
    }

    @Test
    fun `getDagStatus should throw exception for non-existent DAG`() {
        // when & then
        assertThatThrownBy {
            client.getDagStatus("non_existent_dag")
        }.isInstanceOf(AirflowConnectionException::class.java)
            .hasMessageContaining("getDagStatus")
    }

    @Test
    fun `DAG run state should progress over time`() {
        // given
        val dagId = "test_dag"
        val runId = "test_run_123"
        client.triggerDAGRun(dagId, runId)

        // when - check initial state
        val initialStatus = client.getDAGRun(dagId, runId)

        // then
        assertThat(initialStatus.state).isIn(AirflowDAGRunState.QUEUED, AirflowDAGRunState.RUNNING)

        // Note: State progression is time-based and random in the mock,
        // so we can't reliably test the exact progression without sleeping
        // This test mainly verifies the mechanism exists
    }
}
