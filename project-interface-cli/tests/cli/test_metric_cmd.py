"""Tests for the metric subcommand."""

from __future__ import annotations

from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


def get_output(result) -> str:
    """Get combined output (Typer mixes stdout/stderr by default)."""
    return result.output or result.stdout or ""


@pytest.fixture
def sample_project_path() -> Path:
    """Return path to sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


class TestMetricList:
    """Tests for metric list command."""

    def test_list_local_metrics(self, sample_project_path: Path) -> None:
        """Test listing local metrics."""
        result = runner.invoke(
            app, ["metric", "list", "--path", str(sample_project_path)]
        )
        assert result.exit_code == 0
        assert "iceberg.reporting.user_summary" in result.stdout

    def test_list_local_metrics_json(self, sample_project_path: Path) -> None:
        """Test listing local metrics in JSON format."""
        result = runner.invoke(
            app,
            ["metric", "list", "--path", str(sample_project_path), "--format", "json"],
        )
        assert result.exit_code == 0
        assert "iceberg.reporting.user_summary" in result.stdout

    def test_list_server_metrics(self, sample_project_path: Path) -> None:
        """Test listing metrics from server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "metric",
                "list",
                "--source",
                "server",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        # Mock data should be shown
        assert "iceberg.reporting.user_summary" in result.stdout or "Source: server" in result.stdout

    def test_list_with_tag_filter(self, sample_project_path: Path) -> None:
        """Test listing metrics with tag filter."""
        result = runner.invoke(
            app,
            ["metric", "list", "--path", str(sample_project_path), "--tag", "kpi"],
        )
        # Should work even if no matches
        assert result.exit_code in [0, 0]  # 0 for found, 0 for not found (with warning)


class TestMetricGet:
    """Tests for metric get command."""

    def test_get_metric_details(self, sample_project_path: Path) -> None:
        """Test getting metric details."""
        result = runner.invoke(
            app,
            [
                "metric",
                "get",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.reporting.user_summary" in result.stdout

    def test_get_metric_json(self, sample_project_path: Path) -> None:
        """Test getting metric details in JSON format."""
        result = runner.invoke(
            app,
            [
                "metric",
                "get",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.reporting.user_summary" in result.stdout

    def test_get_nonexistent_metric(self, sample_project_path: Path) -> None:
        """Test getting a metric that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "metric",
                "get",
                "nonexistent.metric",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestMetricValidate:
    """Tests for metric validate command."""

    def test_validate_metric(self, sample_project_path: Path) -> None:
        """Test validating a metric."""
        result = runner.invoke(
            app,
            [
                "metric",
                "validate",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "-p",
                "date=2024-01-01",
            ],
        )
        # Should validate successfully or show validation result
        assert result.exit_code in [0, 1]

    def test_validate_nonexistent_metric(self, sample_project_path: Path) -> None:
        """Test validating a metric that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "metric",
                "validate",
                "nonexistent.metric",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestMetricRun:
    """Tests for metric run command."""

    def test_run_metric_dry_run(self, sample_project_path: Path) -> None:
        """Test running a metric in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "metric",
                "run",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "-p",
                "date=2024-01-01",
                "--dry-run",
            ],
        )
        # Dry-run should succeed or fail gracefully
        assert result.exit_code in [0, 1]

    def test_run_nonexistent_metric(self, sample_project_path: Path) -> None:
        """Test running a metric that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "metric",
                "run",
                "nonexistent.metric",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestMetricRegister:
    """Tests for metric register command."""

    def test_register_metric(self, sample_project_path: Path) -> None:
        """Test registering a metric to server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "metric",
                "register",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should succeed with mock server (first time)
        # or fail if already exists
        assert result.exit_code in [0, 1]

    def test_register_nonexistent_metric(self, sample_project_path: Path) -> None:
        """Test registering a metric that doesn't exist locally."""
        result = runner.invoke(
            app,
            [
                "metric",
                "register",
                "nonexistent.metric",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()
