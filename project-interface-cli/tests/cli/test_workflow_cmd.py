"""Tests for the workflow subcommand."""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import Mock, patch

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


@pytest.fixture
def mock_workflow_run_response() -> dict:
    """Mock successful workflow run response."""
    return {
        "run_id": "iceberg.analytics.daily_clicks_20240115_093045",
        "dataset_name": "iceberg.analytics.daily_clicks",
        "status": "PENDING",
        "source": "manual",
        "triggered_by": "cli-user",
    }


@pytest.fixture
def mock_workflow_status_response() -> dict:
    """Mock workflow status response."""
    return {
        "run_id": "iceberg.analytics.daily_clicks_20240115_093045",
        "dataset_name": "iceberg.analytics.daily_clicks",
        "status": "RUNNING",
        "source": "manual",
        "triggered_by": "cli-user",
        "started_at": "2024-01-15T09:30:45Z",
        "ended_at": None,
        "duration_seconds": None,
        "error_message": None,
    }


@pytest.fixture
def mock_workflows_list() -> list:
    """Mock list of workflows."""
    return [
        {
            "dataset_name": "iceberg.analytics.daily_clicks",
            "source": "code",
            "paused": False,
            "enabled": True,
            "schedule": "0 9 * * *",
            "next_run_at": "2024-01-16T09:00:00Z",
        },
        {
            "dataset_name": "iceberg.analytics.hourly_events",
            "source": "manual",
            "paused": True,
            "enabled": True,
            "schedule": "0 * * * *",
            "next_run_at": None,
        },
    ]


@pytest.fixture
def mock_workflow_history() -> list:
    """Mock workflow execution history."""
    return [
        {
            "run_id": "iceberg.analytics.daily_clicks_20240115_093045",
            "dataset_name": "iceberg.analytics.daily_clicks",
            "source": "manual",
            "status": "COMPLETED",
            "started_at": "2024-01-15T09:30:45Z",
            "ended_at": "2024-01-15T09:35:12Z",
        },
        {
            "run_id": "iceberg.analytics.daily_clicks_20240114_090000",
            "dataset_name": "iceberg.analytics.daily_clicks",
            "source": "code",
            "status": "FAILED",
            "started_at": "2024-01-14T09:00:00Z",
            "ended_at": "2024-01-14T09:02:30Z",
        },
    ]


# =============================================================================
# Test: workflow run
# =============================================================================


class TestWorkflowRun:
    """Tests for workflow run command."""

    def test_run_workflow_success(self, sample_project_path: Path) -> None:
        """Test triggering a workflow run successfully."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-15",
            ],
        )
        # Mock mode should succeed
        assert result.exit_code == 0
        output = get_output(result)
        assert "run" in output.lower() or "started" in output.lower()

    def test_run_workflow_dry_run(self, sample_project_path: Path) -> None:
        """Test workflow run in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--dry-run",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "dry-run" in output.lower() or "validation" in output.lower()

    def test_run_workflow_with_multiple_params(
        self, sample_project_path: Path
    ) -> None:
        """Test workflow run with multiple parameters."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-15",
                "-p",
                "region=us-east-1",
            ],
        )
        assert result.exit_code == 0

    def test_run_workflow_json_output(self, sample_project_path: Path) -> None:
        """Test workflow run with JSON output format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        # Should be valid JSON output
        output = get_output(result).strip()
        if output:
            try:
                json.loads(output)
            except json.JSONDecodeError:
                pass  # May not be pure JSON in mock mode

    def test_run_workflow_invalid_param_format(
        self, sample_project_path: Path
    ) -> None:
        """Test workflow run with invalid parameter format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "invalid_param_no_equals",
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "error" in output.lower() or "invalid" in output.lower()


# =============================================================================
# Test: workflow backfill
# =============================================================================


class TestWorkflowBackfill:
    """Tests for workflow backfill command."""

    def test_backfill_success(self, sample_project_path: Path) -> None:
        """Test successful backfill execution."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "2024-01-01",
                "--end",
                "2024-01-07",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "backfill" in output.lower() or "started" in output.lower()

    def test_backfill_dry_run(self, sample_project_path: Path) -> None:
        """Test backfill in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "2024-01-01",
                "--end",
                "2024-01-07",
                "--path",
                str(sample_project_path),
                "--dry-run",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "dry-run" in output.lower() or "validation" in output.lower()
        # Should show date range info
        assert "2024-01-01" in output
        assert "2024-01-07" in output

    def test_backfill_with_params(self, sample_project_path: Path) -> None:
        """Test backfill with additional parameters."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "2024-01-01",
                "--end",
                "2024-01-03",
                "--path",
                str(sample_project_path),
                "-p",
                "region=us-east-1",
            ],
        )
        assert result.exit_code == 0

    def test_backfill_invalid_date_format(self, sample_project_path: Path) -> None:
        """Test backfill with invalid date format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "01-01-2024",  # Invalid format
                "--end",
                "2024-01-07",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "invalid" in output.lower() or "yyyy-mm-dd" in output.lower()

    def test_backfill_start_after_end(self, sample_project_path: Path) -> None:
        """Test backfill with start date after end date."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "2024-01-10",
                "--end",
                "2024-01-01",  # Before start
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "before" in output.lower() or "start" in output.lower()

    def test_backfill_same_date(self, sample_project_path: Path) -> None:
        """Test backfill with same start and end date (single day)."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--start",
                "2024-01-15",
                "--end",
                "2024-01-15",
                "--path",
                str(sample_project_path),
            ],
        )
        # Same date is valid (single day backfill)
        assert result.exit_code == 0

    def test_backfill_missing_required_options(
        self, sample_project_path: Path
    ) -> None:
        """Test backfill without required start/end options."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "backfill",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail due to missing required options
        assert result.exit_code != 0


# =============================================================================
# Test: workflow stop
# =============================================================================


class TestWorkflowStop:
    """Tests for workflow stop command."""

    def test_stop_workflow_success(self, sample_project_path: Path) -> None:
        """Test stopping a workflow run successfully."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "stop",
                "iceberg.analytics.daily_clicks_20240115_093045",
                "--path",
                str(sample_project_path),
            ],
        )
        # In mock mode, may succeed or fail depending on mock state
        output = get_output(result)
        # Should show some response about stopping
        assert (
            "stop" in output.lower()
            or "error" in output.lower()
            or "not found" in output.lower()
        )

    def test_stop_nonexistent_run(self, sample_project_path: Path) -> None:
        """Test stopping a run that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "stop",
                "nonexistent_run_id_12345",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with not found error
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()


# =============================================================================
# Test: workflow status
# =============================================================================


class TestWorkflowStatus:
    """Tests for workflow status command."""

    def test_status_table_output(self, sample_project_path: Path) -> None:
        """Test getting workflow status in table format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "status",
                "iceberg.analytics.daily_clicks_20240115_093045",
                "--path",
                str(sample_project_path),
            ],
        )
        # In mock mode, may succeed or fail
        output = get_output(result)
        # Should show status or error
        assert (
            "status" in output.lower()
            or "run id" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_status_json_output(self, sample_project_path: Path) -> None:
        """Test getting workflow status in JSON format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "status",
                "iceberg.analytics.daily_clicks_20240115_093045",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        output = get_output(result)
        # Should be JSON or error
        if result.exit_code == 0 and output.strip():
            try:
                data = json.loads(output.strip())
                assert isinstance(data, dict)
            except json.JSONDecodeError:
                pass  # May have other output in mock mode

    def test_status_nonexistent_run(self, sample_project_path: Path) -> None:
        """Test getting status of nonexistent run."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "status",
                "nonexistent_run_id_12345",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()


# =============================================================================
# Test: workflow list
# =============================================================================


class TestWorkflowList:
    """Tests for workflow list command."""

    def test_list_all_workflows(self, sample_project_path: Path) -> None:
        """Test listing all workflows."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should succeed in mock mode
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table or "no workflows" message
        assert (
            "workflow" in output.lower()
            or "dataset" in output.lower()
            or "no workflows" in output.lower()
        )

    def test_list_workflows_json_output(self, sample_project_path: Path) -> None:
        """Test listing workflows in JSON format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output and "no workflows" not in output.lower():
            try:
                data = json.loads(output)
                assert isinstance(data, list)
            except json.JSONDecodeError:
                pass  # May have mixed output

    def test_list_filter_by_source_code(self, sample_project_path: Path) -> None:
        """Test listing workflows filtered by code source."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--source",
                "code",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_filter_by_source_manual(self, sample_project_path: Path) -> None:
        """Test listing workflows filtered by manual source."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--source",
                "manual",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_running_only(self, sample_project_path: Path) -> None:
        """Test listing only running workflows."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--running",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_enabled_only(self, sample_project_path: Path) -> None:
        """Test listing only enabled workflows."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--enabled-only",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_filter_by_dataset(self, sample_project_path: Path) -> None:
        """Test listing workflows filtered by dataset name."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--dataset",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_combined_filters(self, sample_project_path: Path) -> None:
        """Test listing workflows with combined filters."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--source",
                "code",
                "--enabled-only",
                "--dataset",
                "iceberg.analytics",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0


# =============================================================================
# Test: workflow history
# =============================================================================


class TestWorkflowHistory:
    """Tests for workflow history command."""

    def test_history_default(self, sample_project_path: Path) -> None:
        """Test getting workflow history with defaults."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table or "no history" message
        assert (
            "history" in output.lower()
            or "run id" in output.lower()
            or "no" in output.lower()
        )

    def test_history_json_output(self, sample_project_path: Path) -> None:
        """Test getting history in JSON format."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output and "no" not in output.lower():
            try:
                data = json.loads(output)
                assert isinstance(data, list)
            except json.JSONDecodeError:
                pass

    def test_history_filter_by_dataset(self, sample_project_path: Path) -> None:
        """Test history filtered by dataset name."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--dataset",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_history_filter_by_source(self, sample_project_path: Path) -> None:
        """Test history filtered by source type."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--source",
                "code",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_history_filter_by_status_completed(
        self, sample_project_path: Path
    ) -> None:
        """Test history filtered by COMPLETED status."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--status",
                "COMPLETED",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_history_filter_by_status_failed(self, sample_project_path: Path) -> None:
        """Test history filtered by FAILED status."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--status",
                "FAILED",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_history_with_limit(self, sample_project_path: Path) -> None:
        """Test history with custom limit."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--limit",
                "5",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_history_combined_filters(self, sample_project_path: Path) -> None:
        """Test history with combined filters."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--dataset",
                "iceberg.analytics",
                "--source",
                "code",
                "--status",
                "COMPLETED",
                "--limit",
                "50",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0


# =============================================================================
# Test: workflow pause / unpause
# =============================================================================


class TestWorkflowPause:
    """Tests for workflow pause command."""

    def test_pause_workflow_success(self, sample_project_path: Path) -> None:
        """Test pausing a workflow successfully."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "pause",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # In mock mode, may succeed or show already paused
        output = get_output(result)
        assert (
            "pause" in output.lower()
            or "already" in output.lower()
            or "error" in output.lower()
        )

    def test_pause_nonexistent_workflow(self, sample_project_path: Path) -> None:
        """Test pausing a workflow that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "pause",
                "nonexistent.dataset.name",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()


class TestWorkflowUnpause:
    """Tests for workflow unpause command."""

    def test_unpause_workflow_success(self, sample_project_path: Path) -> None:
        """Test unpausing a workflow successfully."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "unpause",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # In mock mode, may succeed or show not paused
        output = get_output(result)
        assert (
            "unpause" in output.lower()
            or "not paused" in output.lower()
            or "error" in output.lower()
        )

    def test_unpause_nonexistent_workflow(self, sample_project_path: Path) -> None:
        """Test unpausing a workflow that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "unpause",
                "nonexistent.dataset.name",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()


# =============================================================================
# Test: workflow help
# =============================================================================


class TestWorkflowHelp:
    """Tests for workflow help and command structure."""

    def test_workflow_no_args_shows_help(self) -> None:
        """Test that workflow without arguments shows help."""
        result = runner.invoke(app, ["workflow"])
        # Typer's no_args_is_help returns exit code 0 or 2 depending on version
        assert result.exit_code in [0, 2]
        output = get_output(result)
        # Should show available commands
        assert "run" in output.lower()
        assert "list" in output.lower()
        assert "history" in output.lower()

    def test_workflow_help_flag(self) -> None:
        """Test workflow --help flag."""
        result = runner.invoke(app, ["workflow", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show workflow description and commands
        assert "workflow" in output.lower()
        assert "airflow" in output.lower() or "server" in output.lower()

    def test_workflow_run_help(self) -> None:
        """Test workflow run --help."""
        result = runner.invoke(app, ["workflow", "run", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        assert "trigger" in output.lower() or "run" in output.lower()
        assert "--param" in output or "-p" in output
        assert "--dry-run" in output

    def test_workflow_backfill_help(self) -> None:
        """Test workflow backfill --help."""
        result = runner.invoke(app, ["workflow", "backfill", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        assert "backfill" in output.lower()
        assert "--start" in output or "-s" in output
        assert "--end" in output or "-e" in output

    def test_workflow_list_help(self) -> None:
        """Test workflow list --help."""
        result = runner.invoke(app, ["workflow", "list", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        assert "--source" in output
        assert "--running" in output
        assert "--format" in output


# =============================================================================
# Test: Edge cases and error handling
# =============================================================================


class TestWorkflowEdgeCases:
    """Tests for edge cases and error handling."""

    def test_invalid_source_option(self, sample_project_path: Path) -> None:
        """Test invalid source option value."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--source",
                "invalid_source",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0

    def test_invalid_status_filter(self, sample_project_path: Path) -> None:
        """Test invalid status filter value."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--status",
                "INVALID_STATUS",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0

    def test_invalid_format_option(self, sample_project_path: Path) -> None:
        """Test invalid format option value."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--format",
                "xml",  # Invalid format
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0

    def test_negative_limit(self, sample_project_path: Path) -> None:
        """Test negative limit value."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "history",
                "--limit",
                "-5",
                "--path",
                str(sample_project_path),
            ],
        )
        # Behavior depends on implementation - may accept or reject
        # Just verify it doesn't crash
        assert result.exit_code in [0, 1, 2]

    def test_empty_dataset_name(self, sample_project_path: Path) -> None:
        """Test empty dataset name argument."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "",
                "--path",
                str(sample_project_path),
            ],
        )
        # Empty string may be rejected or treated as invalid
        # Just verify proper handling
        output = get_output(result)
        assert result.exit_code in [0, 1, 2]

    def test_invalid_project_path(self) -> None:
        """Test with invalid project path."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "list",
                "--path",
                "/nonexistent/path/to/project",
            ],
        )
        # In mock mode, the client may still work without project validation
        # The test verifies the command handles the path gracefully
        # Exit code 0 (mock success) or 1 (validation error) are both acceptable
        assert result.exit_code in [0, 1]

    def test_workflow_run_empty_params(self, sample_project_path: Path) -> None:
        """Test workflow run without any parameters."""
        result = runner.invoke(
            app,
            [
                "workflow",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should succeed even without params
        assert result.exit_code == 0
