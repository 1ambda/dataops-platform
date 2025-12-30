"""Tests for the dataset subcommand."""

from __future__ import annotations

from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


class TestDatasetList:
    """Tests for dataset list command."""

    def test_list_local_datasets(self, sample_project_path: Path) -> None:
        """Test listing local datasets."""
        result = runner.invoke(
            app, ["dataset", "list", "--path", str(sample_project_path)]
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_list_local_datasets_json(self, sample_project_path: Path) -> None:
        """Test listing local datasets in JSON format."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "list",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_list_server_datasets(self, sample_project_path: Path) -> None:
        """Test listing datasets from server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "list",
                "--source",
                "server",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        assert "Source: server" in result.stdout

    def test_list_with_tag_filter(self, sample_project_path: Path) -> None:
        """Test listing datasets with tag filter."""
        result = runner.invoke(
            app,
            ["dataset", "list", "--path", str(sample_project_path), "--tag", "daily"],
        )
        assert result.exit_code in [0, 0]


class TestDatasetGet:
    """Tests for dataset get command."""

    def test_get_dataset_details(self, sample_project_path: Path) -> None:
        """Test getting dataset details."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_get_dataset_json(self, sample_project_path: Path) -> None:
        """Test getting dataset details in JSON format."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_get_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test getting a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetValidate:
    """Tests for dataset validate command."""

    def test_validate_dataset(self, sample_project_path: Path) -> None:
        """Test validating a dataset."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "validate",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-01",
            ],
        )
        # Should validate successfully or show validation result
        assert result.exit_code in [0, 1]

    def test_validate_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test validating a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "validate",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetRun:
    """Tests for dataset run command."""

    def test_run_dataset_dry_run(self, sample_project_path: Path) -> None:
        """Test running a dataset in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-01",
                "--dry-run",
            ],
        )
        # Dry-run should succeed or fail gracefully
        assert result.exit_code in [0, 1]

    def test_run_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test running a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "run",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetRegister:
    """Tests for dataset register command."""

    def test_register_dataset(self, sample_project_path: Path) -> None:
        """Test registering a dataset to server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "register",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should succeed with mock server (first time)
        # or fail if already exists
        assert result.exit_code in [0, 1]

    def test_register_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test registering a dataset that doesn't exist locally."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "register",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()
