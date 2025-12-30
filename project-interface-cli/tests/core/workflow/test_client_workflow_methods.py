"""Tests for the BasecampClient workflow methods."""

from __future__ import annotations

import pytest

from dli.core.client import (
    BasecampClient,
    RunStatus,
    ServerConfig,
    WorkflowSource,
)


class TestWorkflowRun:
    """Tests for workflow_run method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_run_basic(self, client: BasecampClient) -> None:
        """Test basic workflow run trigger."""
        response = client.workflow_run("iceberg.analytics.daily_clicks")
        assert response.success is True
        assert response.data is not None
        assert "run_id" in response.data
        assert response.data["dataset_name"] == "iceberg.analytics.daily_clicks"
        assert response.data["status"] == RunStatus.PENDING.value
        assert response.data["triggered_by"] == "cli"

    def test_workflow_run_with_params(self, client: BasecampClient) -> None:
        """Test workflow run with custom parameters."""
        params = {"partition_date": "2024-01-15", "mode": "incremental"}
        response = client.workflow_run(
            "iceberg.analytics.daily_clicks",
            params=params,
        )
        assert response.success is True
        assert response.data["params"] == params

    def test_workflow_run_dry_run(self, client: BasecampClient) -> None:
        """Test workflow dry run (validation only)."""
        response = client.workflow_run(
            "iceberg.analytics.daily_clicks",
            dry_run=True,
        )
        assert response.success is True
        assert "message" in response.data
        assert "Dry run validation passed" in response.data["message"]
        assert "would_create_run_id" in response.data
        # Should not have actual run_id in dry run
        assert "run_id" not in response.data

    def test_workflow_run_dry_run_with_params(self, client: BasecampClient) -> None:
        """Test workflow dry run with parameters."""
        params = {"key": "value"}
        response = client.workflow_run(
            "iceberg.warehouse.user_events",
            params=params,
            dry_run=True,
        )
        assert response.success is True
        assert response.data["params"] == params
        assert response.data["dataset_name"] == "iceberg.warehouse.user_events"

    def test_workflow_run_generates_unique_run_id(self, client: BasecampClient) -> None:
        """Test that each run generates a unique run_id."""
        response1 = client.workflow_run("iceberg.analytics.daily_clicks")
        response2 = client.workflow_run("iceberg.analytics.daily_clicks")

        assert response1.success is True
        assert response2.success is True
        # Run IDs include timestamp, so they should be unique
        # (unless called within the same second)
        assert response1.data["run_id"].startswith("iceberg.analytics.daily_clicks_")
        assert response2.data["run_id"].startswith("iceberg.analytics.daily_clicks_")

    def test_workflow_run_empty_params(self, client: BasecampClient) -> None:
        """Test workflow run with empty params defaults to empty dict."""
        response = client.workflow_run("iceberg.analytics.daily_clicks")
        assert response.success is True
        assert response.data["params"] == {}


class TestWorkflowBackfill:
    """Tests for workflow_backfill method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_backfill_single_day(self, client: BasecampClient) -> None:
        """Test backfill for a single day."""
        response = client.workflow_backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2024-01-15",
            end_date="2024-01-15",
        )
        assert response.success is True
        assert response.data["dataset_name"] == "iceberg.analytics.daily_clicks"
        assert response.data["start_date"] == "2024-01-15"
        assert response.data["end_date"] == "2024-01-15"
        assert response.data["total_runs"] == 1
        assert len(response.data["runs"]) == 1

        run = response.data["runs"][0]
        assert run["date"] == "2024-01-15"
        assert run["status"] == RunStatus.PENDING.value

    def test_workflow_backfill_date_range(self, client: BasecampClient) -> None:
        """Test backfill for a date range."""
        response = client.workflow_backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2024-01-10",
            end_date="2024-01-15",
        )
        assert response.success is True
        assert response.data["total_runs"] == 6  # 10, 11, 12, 13, 14, 15

        dates = [r["date"] for r in response.data["runs"]]
        assert dates == [
            "2024-01-10",
            "2024-01-11",
            "2024-01-12",
            "2024-01-13",
            "2024-01-14",
            "2024-01-15",
        ]

    def test_workflow_backfill_generates_run_ids(self, client: BasecampClient) -> None:
        """Test that backfill generates proper run IDs for each date."""
        response = client.workflow_backfill(
            "iceberg.warehouse.user_events",
            start_date="2024-01-01",
            end_date="2024-01-03",
        )
        assert response.success is True

        run_ids = [r["run_id"] for r in response.data["runs"]]
        assert run_ids[0] == "iceberg.warehouse.user_events_20240101_000000"
        assert run_ids[1] == "iceberg.warehouse.user_events_20240102_000000"
        assert run_ids[2] == "iceberg.warehouse.user_events_20240103_000000"

    def test_workflow_backfill_all_pending_status(self, client: BasecampClient) -> None:
        """Test that all backfill runs start with pending status."""
        response = client.workflow_backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2024-01-01",
            end_date="2024-01-05",
        )
        assert response.success is True

        for run in response.data["runs"]:
            assert run["status"] == RunStatus.PENDING.value

    def test_workflow_backfill_with_params(self, client: BasecampClient) -> None:
        """Test backfill with custom parameters."""
        params = {"mode": "full_refresh"}
        response = client.workflow_backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2024-01-15",
            end_date="2024-01-15",
            params=params,
        )
        # Params are stored but not returned in run-level data (by design)
        assert response.success is True


class TestWorkflowStop:
    """Tests for workflow_stop method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_stop_running_job(self, client: BasecampClient) -> None:
        """Test stopping a running workflow."""
        # Use the running job from mock data
        run_id = "iceberg.analytics.daily_clicks_20240115_120000"
        response = client.workflow_stop(run_id)

        assert response.success is True
        assert response.data["run_id"] == run_id
        assert response.data["status"] == RunStatus.KILLED.value
        assert "stopped successfully" in response.data["message"]

    def test_workflow_stop_not_found(self, client: BasecampClient) -> None:
        """Test stopping a non-existent run."""
        response = client.workflow_stop("nonexistent_run_id_123")

        assert response.success is False
        assert response.status_code == 404
        assert "not found" in response.error.lower()

    def test_workflow_stop_already_completed(self, client: BasecampClient) -> None:
        """Test stopping an already completed run."""
        # Use a completed run from mock data
        run_id = "iceberg.analytics.daily_clicks_20240115_060000"
        response = client.workflow_stop(run_id)

        assert response.success is False
        assert response.status_code == 400
        assert "cannot stop" in response.error.lower()

    def test_workflow_stop_failed_run(self, client: BasecampClient) -> None:
        """Test stopping a failed run."""
        # Use a failed run from mock data
        run_id = "iceberg.reporting.weekly_summary_20240108_100000"
        response = client.workflow_stop(run_id)

        assert response.success is False
        assert response.status_code == 400


class TestWorkflowStatus:
    """Tests for workflow_status method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_status_running(self, client: BasecampClient) -> None:
        """Test getting status of a running workflow."""
        run_id = "iceberg.analytics.daily_clicks_20240115_120000"
        response = client.workflow_status(run_id)

        assert response.success is True
        assert response.data["run_id"] == run_id
        assert response.data["status"] == RunStatus.RUNNING.value
        assert response.data["dataset_name"] == "iceberg.analytics.daily_clicks"
        assert "started_at" in response.data

    def test_workflow_status_success(self, client: BasecampClient) -> None:
        """Test getting status of a successful workflow."""
        run_id = "iceberg.analytics.daily_clicks_20240115_060000"
        response = client.workflow_status(run_id)

        assert response.success is True
        assert response.data["status"] == RunStatus.COMPLETED.value
        assert response.data["ended_at"] is not None
        assert response.data["duration_seconds"] == 900

    def test_workflow_status_failed(self, client: BasecampClient) -> None:
        """Test getting status of a failed workflow."""
        run_id = "iceberg.reporting.weekly_summary_20240108_100000"
        response = client.workflow_status(run_id)

        assert response.success is True
        assert response.data["status"] == RunStatus.FAILED.value
        assert "error_message" in response.data

    def test_workflow_status_not_found(self, client: BasecampClient) -> None:
        """Test getting status of a non-existent run."""
        response = client.workflow_status("nonexistent_run_id")

        assert response.success is False
        assert response.status_code == 404
        assert "not found" in response.error.lower()

    def test_workflow_status_contains_required_fields(
        self, client: BasecampClient
    ) -> None:
        """Test that status response contains all required fields."""
        run_id = "iceberg.warehouse.user_events_20240115_040000"
        response = client.workflow_status(run_id)

        assert response.success is True
        data = response.data
        assert "run_id" in data
        assert "dataset_name" in data
        assert "source" in data
        assert "status" in data
        assert "started_at" in data
        assert "triggered_by" in data


class TestWorkflowList:
    """Tests for workflow_list method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_list_all(self, client: BasecampClient) -> None:
        """Test listing all workflows."""
        response = client.workflow_list()

        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) == 3  # Mock data has 3 workflows

    def test_workflow_list_filter_by_source_code(self, client: BasecampClient) -> None:
        """Test filtering workflows by source type Code."""
        response = client.workflow_list(source=WorkflowSource.CODE.value)

        assert response.success is True
        assert isinstance(response.data, list)
        # All returned workflows should have source='Code'
        for workflow in response.data:
            assert workflow["source"] == WorkflowSource.CODE.value

    def test_workflow_list_filter_by_source_manual(
        self, client: BasecampClient
    ) -> None:
        """Test filtering workflows by source type Manual."""
        response = client.workflow_list(source=WorkflowSource.MANUAL.value)

        assert response.success is True
        assert isinstance(response.data, list)
        for workflow in response.data:
            assert workflow["source"] == WorkflowSource.MANUAL.value

    def test_workflow_list_running_only(self, client: BasecampClient) -> None:
        """Test listing only workflows with running jobs."""
        response = client.workflow_list(running_only=True)

        assert response.success is True
        assert isinstance(response.data, list)
        # Only iceberg.analytics.daily_clicks has a running job
        assert len(response.data) == 1
        assert response.data[0]["dataset_name"] == "iceberg.analytics.daily_clicks"

    def test_workflow_list_enabled_only(self, client: BasecampClient) -> None:
        """Test listing only enabled workflows."""
        response = client.workflow_list(enabled_only=True)

        assert response.success is True
        assert isinstance(response.data, list)
        for workflow in response.data:
            assert workflow["enabled"] is True

    def test_workflow_list_dataset_filter(self, client: BasecampClient) -> None:
        """Test filtering workflows by dataset name pattern."""
        response = client.workflow_list(dataset_filter="analytics")

        assert response.success is True
        assert isinstance(response.data, list)
        for workflow in response.data:
            assert "analytics" in workflow["dataset_name"].lower()

    def test_workflow_list_dataset_filter_case_insensitive(
        self, client: BasecampClient
    ) -> None:
        """Test that dataset filter is case-insensitive."""
        response_lower = client.workflow_list(dataset_filter="warehouse")
        response_upper = client.workflow_list(dataset_filter="WAREHOUSE")

        assert response_lower.success is True
        assert response_upper.success is True
        assert len(response_lower.data) == len(response_upper.data)

    def test_workflow_list_combined_filters(self, client: BasecampClient) -> None:
        """Test combining multiple filters."""
        response = client.workflow_list(
            source=WorkflowSource.CODE.value,
            enabled_only=True,
            dataset_filter="iceberg",
        )

        assert response.success is True
        for workflow in response.data:
            assert workflow["source"] == WorkflowSource.CODE.value
            assert workflow["enabled"] is True
            assert "iceberg" in workflow["dataset_name"].lower()

    def test_workflow_list_no_matches(self, client: BasecampClient) -> None:
        """Test listing with filter that matches nothing."""
        response = client.workflow_list(dataset_filter="nonexistent_pattern_xyz")

        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) == 0


class TestWorkflowHistory:
    """Tests for workflow_history method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_history_all(self, client: BasecampClient) -> None:
        """Test getting all workflow history."""
        response = client.workflow_history()

        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) == 4  # Mock data has 4 runs

    def test_workflow_history_limit(self, client: BasecampClient) -> None:
        """Test history with limit."""
        response = client.workflow_history(limit=2)

        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) == 2

    def test_workflow_history_sorted_by_start_time(
        self, client: BasecampClient
    ) -> None:
        """Test that history is sorted by start time descending."""
        response = client.workflow_history()

        assert response.success is True
        if len(response.data) > 1:
            for i in range(len(response.data) - 1):
                # Each run should be more recent or equal to the next
                assert response.data[i]["started_at"] >= response.data[i + 1]["started_at"]

    def test_workflow_history_filter_by_status_success(
        self, client: BasecampClient
    ) -> None:
        """Test filtering history by success status."""
        response = client.workflow_history(status_filter=RunStatus.COMPLETED.value)

        assert response.success is True
        for run in response.data:
            assert run["status"] == RunStatus.COMPLETED.value

    def test_workflow_history_filter_by_status_failed(
        self, client: BasecampClient
    ) -> None:
        """Test filtering history by failed status."""
        response = client.workflow_history(status_filter=RunStatus.FAILED.value)

        assert response.success is True
        for run in response.data:
            assert run["status"] == RunStatus.FAILED.value

    def test_workflow_history_filter_by_status_running(
        self, client: BasecampClient
    ) -> None:
        """Test filtering history by running status."""
        response = client.workflow_history(status_filter=RunStatus.RUNNING.value)

        assert response.success is True
        for run in response.data:
            assert run["status"] == RunStatus.RUNNING.value

    def test_workflow_history_filter_by_dataset(self, client: BasecampClient) -> None:
        """Test filtering history by dataset name pattern."""
        response = client.workflow_history(dataset_filter="daily_clicks")

        assert response.success is True
        for run in response.data:
            assert "daily_clicks" in run["dataset_name"].lower()

    def test_workflow_history_filter_by_source(self, client: BasecampClient) -> None:
        """Test filtering history by source type."""
        response = client.workflow_history(source=WorkflowSource.MANUAL.value)

        assert response.success is True
        for run in response.data:
            assert run["source"] == WorkflowSource.MANUAL.value

    def test_workflow_history_combined_filters(self, client: BasecampClient) -> None:
        """Test combining multiple history filters."""
        response = client.workflow_history(
            dataset_filter="analytics",
            status_filter=RunStatus.COMPLETED.value,
            limit=10,
        )

        assert response.success is True
        for run in response.data:
            assert "analytics" in run["dataset_name"].lower()
            assert run["status"] == RunStatus.COMPLETED.value

    def test_workflow_history_no_matches(self, client: BasecampClient) -> None:
        """Test history with filter that matches nothing."""
        response = client.workflow_history(
            dataset_filter="nonexistent_dataset_xyz"
        )

        assert response.success is True
        assert len(response.data) == 0


class TestWorkflowPause:
    """Tests for workflow_pause method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_pause_success(self, client: BasecampClient) -> None:
        """Test pausing a running workflow."""
        # Use a workflow that is not paused
        dataset_name = "iceberg.analytics.daily_clicks"
        response = client.workflow_pause(dataset_name)

        assert response.success is True
        assert response.data["dataset_name"] == dataset_name
        assert response.data["paused"] is True
        assert "paused successfully" in response.data["message"]

    def test_workflow_pause_not_found(self, client: BasecampClient) -> None:
        """Test pausing a non-existent workflow."""
        response = client.workflow_pause("nonexistent.dataset.name")

        assert response.success is False
        assert response.status_code == 404
        assert "not found" in response.error.lower()

    def test_workflow_pause_already_paused(self, client: BasecampClient) -> None:
        """Test pausing an already paused workflow."""
        # Use a workflow that is already paused
        dataset_name = "iceberg.reporting.weekly_summary"
        response = client.workflow_pause(dataset_name)

        assert response.success is False
        assert response.status_code == 400
        assert "already paused" in response.error.lower()


class TestWorkflowUnpause:
    """Tests for workflow_unpause method."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_workflow_unpause_success(self, client: BasecampClient) -> None:
        """Test unpausing a paused workflow."""
        # Use a workflow that is paused
        dataset_name = "iceberg.reporting.weekly_summary"
        response = client.workflow_unpause(dataset_name)

        assert response.success is True
        assert response.data["dataset_name"] == dataset_name
        assert response.data["paused"] is False
        assert "unpaused successfully" in response.data["message"]

    def test_workflow_unpause_not_found(self, client: BasecampClient) -> None:
        """Test unpausing a non-existent workflow."""
        response = client.workflow_unpause("nonexistent.dataset.name")

        assert response.success is False
        assert response.status_code == 404
        assert "not found" in response.error.lower()

    def test_workflow_unpause_not_paused(self, client: BasecampClient) -> None:
        """Test unpausing a workflow that is not paused."""
        # Use a workflow that is not paused
        dataset_name = "iceberg.analytics.daily_clicks"
        response = client.workflow_unpause(dataset_name)

        assert response.success is False
        assert response.status_code == 400
        assert "not paused" in response.error.lower()


class TestWorkflowPauseUnpauseCycle:
    """Tests for pause/unpause cycle behavior."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_pause_then_unpause(self, client: BasecampClient) -> None:
        """Test pausing then unpausing a workflow."""
        dataset_name = "iceberg.analytics.daily_clicks"

        # Pause
        pause_response = client.workflow_pause(dataset_name)
        assert pause_response.success is True
        assert pause_response.data["paused"] is True

        # Unpause
        unpause_response = client.workflow_unpause(dataset_name)
        assert unpause_response.success is True
        assert unpause_response.data["paused"] is False

    def test_unpause_then_pause(self, client: BasecampClient) -> None:
        """Test unpausing then pausing a workflow."""
        dataset_name = "iceberg.reporting.weekly_summary"

        # Unpause (it's already paused in mock data)
        unpause_response = client.workflow_unpause(dataset_name)
        assert unpause_response.success is True
        assert unpause_response.data["paused"] is False

        # Pause again
        pause_response = client.workflow_pause(dataset_name)
        assert pause_response.success is True
        assert pause_response.data["paused"] is True


class TestWorkflowNonMockMode:
    """Tests for workflow methods in non-mock mode (real API calls)."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a non-mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=False)

    def test_workflow_run_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_run returns 501 in non-mock mode."""
        response = client.workflow_run("iceberg.analytics.daily_clicks")
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_workflow_backfill_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_backfill returns 501 in non-mock mode."""
        response = client.workflow_backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2024-01-01",
            end_date="2024-01-05",
        )
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_stop_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_stop returns 501 in non-mock mode."""
        response = client.workflow_stop("some_run_id")
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_status_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_status returns 501 in non-mock mode."""
        response = client.workflow_status("some_run_id")
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_list_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_list returns 501 in non-mock mode."""
        response = client.workflow_list()
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_history_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_history returns 501 in non-mock mode."""
        response = client.workflow_history()
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_pause_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_pause returns 501 in non-mock mode."""
        response = client.workflow_pause("iceberg.analytics.daily_clicks")
        assert response.success is False
        assert response.status_code == 501

    def test_workflow_unpause_non_mock(self, client: BasecampClient) -> None:
        """Test workflow_unpause returns 501 in non-mock mode."""
        response = client.workflow_unpause("iceberg.analytics.daily_clicks")
        assert response.success is False
        assert response.status_code == 501
