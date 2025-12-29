"""Tests for the quality subcommand.

This module tests the quality subcommand for managing and executing
data quality tests. Tests cover:
- list: List quality tests for a resource or all resources
- run: Execute quality tests locally or on server
- show: Show details of a specific test definition
"""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from typer.testing import CliRunner

from tests.conftest import strip_ansi

from dli.core.quality import (
    DqSeverity,
    DqStatus,
    DqTestDefinition,
    DqTestResult,
    DqTestType,
    QualityReport,
)
from dli.main import app

runner = CliRunner()


def get_output(result) -> str:
    """Get combined output (Typer mixes stdout/stderr by default)."""
    return result.output or result.stdout or ""


@pytest.fixture
def sample_project_path() -> Path:
    """Return path to sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


@pytest.fixture
def mock_test_definitions() -> list[DqTestDefinition]:
    """Create mock test definitions for testing."""
    return [
        DqTestDefinition(
            name="not_null_user_id",
            test_type=DqTestType.NOT_NULL,
            resource_name="iceberg.analytics.daily_clicks",
            columns=["user_id"],
            severity=DqSeverity.ERROR,
            enabled=True,
        ),
        DqTestDefinition(
            name="unique_user_dt",
            test_type=DqTestType.UNIQUE,
            resource_name="iceberg.analytics.daily_clicks",
            columns=["user_id", "dt"],
            severity=DqSeverity.ERROR,
            enabled=True,
        ),
        DqTestDefinition(
            name="accepted_device_type",
            test_type=DqTestType.ACCEPTED_VALUES,
            resource_name="iceberg.analytics.daily_clicks",
            columns=["device_type"],
            params={"values": ["mobile", "desktop", "tablet"]},
            severity=DqSeverity.WARN,
            enabled=True,
        ),
    ]


@pytest.fixture
def mock_quality_report_pass() -> QualityReport:
    """Create a mock quality report with passing tests."""
    results = [
        DqTestResult(
            test_name="not_null_user_id",
            resource_name="iceberg.analytics.daily_clicks",
            status=DqStatus.PASS,
            failed_rows=0,
            execution_time_ms=100,
        ),
        DqTestResult(
            test_name="unique_user_dt",
            resource_name="iceberg.analytics.daily_clicks",
            status=DqStatus.PASS,
            failed_rows=0,
            execution_time_ms=150,
        ),
    ]

    return QualityReport.from_results(
        resource_name="iceberg.analytics.daily_clicks",
        results=results,
        executed_on="local",
    )


@pytest.fixture
def mock_quality_report_fail() -> QualityReport:
    """Create a mock quality report with failing tests."""
    results = [
        DqTestResult(
            test_name="not_null_user_id",
            resource_name="iceberg.analytics.daily_clicks",
            status=DqStatus.FAIL,
            failed_rows=5,
            failed_samples=[
                {"user_id": None, "click_count": 10},
                {"user_id": None, "click_count": 5},
            ],
            execution_time_ms=100,
        ),
        DqTestResult(
            test_name="unique_user_dt",
            resource_name="iceberg.analytics.daily_clicks",
            status=DqStatus.PASS,
            failed_rows=0,
            execution_time_ms=150,
        ),
    ]

    return QualityReport.from_results(
        resource_name="iceberg.analytics.daily_clicks",
        results=results,
        executed_on="local",
    )


class TestQualityHelp:
    """Tests for quality command help."""

    def test_quality_help(self) -> None:
        """Test 'dli quality --help' shows command help."""
        result = runner.invoke(app, ["quality", "--help"])
        assert result.exit_code == 0
        assert "quality" in result.stdout.lower()
        assert "list" in result.stdout
        assert "run" in result.stdout
        assert "show" in result.stdout

    def test_quality_list_help(self) -> None:
        """Test 'dli quality list --help' shows command help."""
        result = runner.invoke(app, ["quality", "list", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--resource" in output
        assert "--format" in output

    def test_quality_run_help(self) -> None:
        """Test 'dli quality run --help' shows command help."""
        result = runner.invoke(app, ["quality", "run", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--all" in output
        assert "--server" in output
        assert "--fail-fast" in output

    def test_quality_show_help(self) -> None:
        """Test 'dli quality show --help' shows command help."""
        result = runner.invoke(app, ["quality", "show", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--test" in output
        assert "--format" in output


class TestQualityList:
    """Tests for quality list command."""

    def test_quality_list_all_tests(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test listing all quality tests."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                ["quality", "list", "--path", str(sample_project_path)],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # Should display test names in table
            assert "not_null" in output.lower() or "Test" in output

    def test_quality_list_by_resource(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test listing tests filtered by resource."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "list",
                    "--resource",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            # Verify resource filter was passed
            mock_registry.get_tests.assert_called_once_with(
                resource_name="iceberg.analytics.daily_clicks"
            )

    def test_quality_list_json_format(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test listing tests in JSON format."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "list",
                    "--format",
                    "json",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # JSON output should contain test data
            assert "not_null" in output.lower()

    def test_quality_list_no_tests_found(self, sample_project_path: Path) -> None:
        """Test listing when no tests are found."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = []
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                ["quality", "list", "--path", str(sample_project_path)],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "no tests" in output.lower()

    def test_quality_list_resource_not_found(self, sample_project_path: Path) -> None:
        """Test listing tests for nonexistent resource."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = []
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "list",
                    "--resource",
                    "nonexistent.resource",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "no tests" in output.lower()


class TestQualityRun:
    """Tests for quality run command."""

    def test_quality_run_resource(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_pass: QualityReport,
    ) -> None:
        """Test running quality tests for a resource."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_pass
            mock_executor_cls.return_value = mock_executor

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "passed" in output.lower() or "PASS" in output

    def test_quality_run_all(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_pass: QualityReport,
    ) -> None:
        """Test running all quality tests."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_pass
            mock_executor_cls.return_value = mock_executor

            result = runner.invoke(
                app,
                ["quality", "run", "--all", "--path", str(sample_project_path)],
            )

            assert result.exit_code == 0

    def test_quality_run_fails_on_test_failure(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_fail: QualityReport,
    ) -> None:
        """Test that run exits with error when tests fail."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_fail
            mock_executor_cls.return_value = mock_executor

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 1
            output = get_output(result)
            assert "fail" in output.lower()

    def test_quality_run_json_format(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_pass: QualityReport,
    ) -> None:
        """Test running tests with JSON output format."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_pass
            mock_executor_cls.return_value = mock_executor

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--format",
                    "json",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # Should contain JSON fields
            assert "passed" in output.lower() or "success" in output.lower()

    def test_quality_run_specific_test(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_pass: QualityReport,
    ) -> None:
        """Test running a specific test by name."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls:
            mock_registry = MagicMock()
            # Return only the specific test
            mock_registry.get_tests.return_value = [mock_test_definitions[0]]
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_pass
            mock_executor_cls.return_value = mock_executor

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--test",
                    "not_null_user_id",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            # Verify test filter was passed
            mock_registry.get_tests.assert_called_once_with(
                resource_name="iceberg.analytics.daily_clicks",
                test_name="not_null_user_id",
            )

    def test_quality_run_no_resource_no_all_flag(
        self, sample_project_path: Path
    ) -> None:
        """Test running without resource and without --all flag fails."""
        result = runner.invoke(
            app,
            ["quality", "run", "--path", str(sample_project_path)],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "resource" in output.lower() or "--all" in output

    def test_quality_run_no_tests_found(self, sample_project_path: Path) -> None:
        """Test running when no tests are found."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = []
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "no tests" in output.lower()

    def test_quality_run_server_mode(
        self,
        sample_project_path: Path,
        mock_test_definitions: list[TestDefinition],
        mock_quality_report_pass: QualityReport,
    ) -> None:
        """Test running tests on server."""
        with patch(
            "dli.commands.quality.QualityRegistry"
        ) as mock_registry_cls, patch(
            "dli.commands.quality.QualityExecutor"
        ) as mock_executor_cls, patch(
            "dli.commands.quality.get_client"
        ) as mock_get_client:
            mock_registry = MagicMock()
            mock_registry.get_tests.return_value = mock_test_definitions
            mock_registry_cls.return_value = mock_registry

            mock_executor = MagicMock()
            mock_executor.run_all.return_value = mock_quality_report_pass
            mock_executor_cls.return_value = mock_executor

            mock_client = MagicMock()
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--server",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            # Verify client was created and passed to executor
            mock_get_client.assert_called_once()
            mock_executor.run_all.assert_called_once()
            call_args = mock_executor.run_all.call_args
            assert call_args[1]["on_server"] is True


class TestQualityShow:
    """Tests for quality show command."""

    def test_quality_show_test_details(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test showing details of a specific test."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_test.return_value = mock_test_definitions[0]
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--test",
                    "not_null_user_id",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "not_null" in output.lower()

    def test_quality_show_json_format(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test showing test details in JSON format."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_test.return_value = mock_test_definitions[0]
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--test",
                    "not_null_user_id",
                    "--format",
                    "json",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # JSON output should contain test fields
            assert "not_null" in output.lower()
            assert "resource" in output.lower() or "type" in output.lower()

    def test_quality_show_test_not_found(self, sample_project_path: Path) -> None:
        """Test showing a test that doesn't exist."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_test.return_value = None
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--test",
                    "nonexistent_test",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 1
            output = get_output(result)
            assert "not found" in output.lower()

    def test_quality_show_with_params(
        self, sample_project_path: Path, mock_test_definitions: list[TestDefinition]
    ) -> None:
        """Test showing test details that have parameters."""
        # Get the accepted_values test which has params
        test_with_params = mock_test_definitions[2]

        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.get_test.return_value = test_with_params
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--test",
                    "accepted_device_type",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "accepted_values" in output.lower()


class TestQualityNoArgsIsHelp:
    """Tests for quality command with no args."""

    def test_quality_no_args_shows_help(self) -> None:
        """Test running 'dli quality' with no args shows help."""
        result = runner.invoke(app, ["quality"])
        # no_args_is_help=True should show help
        assert result.exit_code in [0, 2]
        output = get_output(result)
        assert "list" in output or "run" in output or "Usage" in output


class TestQualityRegistryErrors:
    """Tests for quality registry error handling."""

    def test_quality_list_registry_load_error(self, sample_project_path: Path) -> None:
        """Test handling registry load errors."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.load_all.side_effect = Exception("Failed to load config")
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                ["quality", "list", "--path", str(sample_project_path)],
            )

            assert result.exit_code == 1
            output = get_output(result)
            assert "failed" in output.lower() or "error" in output.lower()

    def test_quality_run_registry_load_error(self, sample_project_path: Path) -> None:
        """Test handling registry load errors during run."""
        with patch("dli.commands.quality.QualityRegistry") as mock_registry_cls:
            mock_registry = MagicMock()
            mock_registry.load_all.side_effect = Exception("Configuration error")
            mock_registry_cls.return_value = mock_registry

            result = runner.invoke(
                app,
                [
                    "quality",
                    "run",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 1
            output = get_output(result)
            assert "failed" in output.lower() or "error" in output.lower()
