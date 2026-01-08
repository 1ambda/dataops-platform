"""Tests for the quality subcommand.

This module tests the quality subcommand for managing and executing
data quality tests. Tests cover:
- list: List quality tests registered on server
- get: Get details of a specific quality from server
- run: Execute quality tests from a Quality Spec (LOCAL/SERVER)
- validate: Validate a Quality Spec YML file
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output
from tests.conftest import strip_ansi

runner = CliRunner()


# Fixtures path
FIXTURES_PATH = Path(__file__).parent.parent / "fixtures" / "sample_project"


class TestQualityHelp:
    """Tests for quality command help."""

    def test_quality_help(self) -> None:
        """Test 'dli quality --help' shows command help."""
        result = runner.invoke(app, ["quality", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "quality" in output.lower()
        assert "list" in output
        assert "get" in output
        assert "run" in output
        assert "validate" in output

    def test_quality_list_help(self) -> None:
        """Test 'dli quality list --help' shows command help."""
        result = runner.invoke(app, ["quality", "list", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--target-type" in output
        assert "--target" in output
        assert "--format" in output

    def test_quality_get_help(self) -> None:
        """Test 'dli quality get --help' shows command help."""
        result = runner.invoke(app, ["quality", "get", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "QUALITY_NAME" in output
        assert "--format" in output

    def test_quality_run_help(self) -> None:
        """Test 'dli quality run --help' shows command help."""
        result = runner.invoke(app, ["quality", "run", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--local" in output
        assert "--server" in output
        assert "--remote" in output
        assert "--test" in output
        assert "--fail-fast" in output
        assert "--param" in output

    def test_quality_validate_help(self) -> None:
        """Test 'dli quality validate --help' shows command help."""
        result = runner.invoke(app, ["quality", "validate", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--strict" in output
        assert "--test" in output
        assert "--format" in output


class TestQualityList:
    """Tests for quality list command."""

    def test_quality_list_all(self) -> None:
        """Test listing all qualities (mock mode)."""
        result = runner.invoke(app, ["quality", "list"])

        assert result.exit_code == 0
        output = get_output(result)
        # Should show mock data
        assert "Quality Tests" in output or "pk_unique" in output

    def test_quality_list_filter_by_target_type(self) -> None:
        """Test listing qualities filtered by target type."""
        result = runner.invoke(
            app,
            ["quality", "list", "--target-type", "dataset"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should only show dataset qualities
        assert "dataset" in output.lower()

    def test_quality_list_filter_by_target_name(self) -> None:
        """Test listing qualities filtered by target name."""
        result = runner.invoke(
            app,
            ["quality", "list", "--target", "daily_clicks"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Check for the target URN which contains daily_clicks
        # Table may truncate, so check for partial match or URN
        assert "iceberg.analytics.daily_clicks" in output or "daily" in output.lower()

    def test_quality_list_json_format(self) -> None:
        """Test listing qualities in JSON format."""
        result = runner.invoke(
            app,
            ["quality", "list", "--format", "json"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should be valid JSON
        data = json.loads(output)
        assert isinstance(data, list)
        assert len(data) > 0
        assert "name" in data[0]
        assert "target_urn" in data[0]


class TestQualityGet:
    """Tests for quality get command."""

    def test_quality_get_existing(self) -> None:
        """Test getting an existing quality."""
        result = runner.invoke(app, ["quality", "get", "pk_unique"])

        assert result.exit_code == 0
        output = get_output(result)
        assert "pk_unique" in output

    def test_quality_get_nonexistent(self) -> None:
        """Test getting a nonexistent quality."""
        result = runner.invoke(app, ["quality", "get", "nonexistent_quality"])

        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower()

    def test_quality_get_json_format(self) -> None:
        """Test getting quality in JSON format."""
        result = runner.invoke(
            app,
            ["quality", "get", "pk_unique", "--format", "json"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should be valid JSON
        data = json.loads(output)
        assert data["name"] == "pk_unique"
        assert "target_urn" in data


class TestQualityRun:
    """Tests for quality run command."""

    def test_quality_run_valid_spec(self) -> None:
        """Test running a valid Quality Spec."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        # Use server mode since LOCAL requires an actual executor
        result = runner.invoke(
            app,
            ["quality", "run", spec_path, "--server"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "Quality Test Report" in output or "passed" in output.lower()

    def test_quality_run_with_server_flag(self) -> None:
        """Test running with --server flag."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "run", spec_path, "--server"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "SERVER" in output or "server" in output.lower()

    def test_quality_run_specific_tests(self) -> None:
        """Test running specific tests only."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        # Use server mode since LOCAL requires an actual executor
        result = runner.invoke(
            app,
            ["quality", "run", spec_path, "--test", "pk_unique", "--server"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "pk_unique" in output or "passed" in output.lower()

    def test_quality_run_json_format(self) -> None:
        """Test running with JSON output format."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        # Use server mode since LOCAL requires an actual executor
        result = runner.invoke(
            app,
            ["quality", "run", spec_path, "--format", "json", "--server"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should be valid JSON
        data = json.loads(output)
        assert "target_urn" in data
        assert "status" in data
        assert "test_results" in data

    def test_quality_run_nonexistent_spec(self) -> None:
        """Test running a nonexistent spec."""
        result = runner.invoke(
            app,
            ["quality", "run", "nonexistent.yaml", "--path", str(FIXTURES_PATH)],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower()


class TestQualityValidate:
    """Tests for quality validate command."""

    def test_quality_validate_valid_spec(self) -> None:
        """Test validating a valid Quality Spec."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "validate", spec_path],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "Validation passed" in output or "valid" in output.lower()

    def test_quality_validate_shows_spec_info(self) -> None:
        """Test that validate shows spec information."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "validate", spec_path],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should show spec details
        assert "iceberg.analytics.daily_clicks" in output

    def test_quality_validate_specific_test(self) -> None:
        """Test validating with specific test details."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "validate", spec_path, "--test", "pk_unique"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "pk_unique" in output

    def test_quality_validate_nonexistent_test(self) -> None:
        """Test validating with a nonexistent test name."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "validate", spec_path, "--test", "nonexistent_test"],
        )

        # Should show warning that test not found
        output = get_output(result)
        assert "not found" in output.lower() or "nonexistent_test" in output

    def test_quality_validate_json_format(self) -> None:
        """Test validating with JSON output format."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        result = runner.invoke(
            app,
            ["quality", "validate", spec_path, "--format", "json"],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should be valid JSON
        data = json.loads(output)
        assert "valid" in data
        assert "errors" in data
        assert "warnings" in data

    def test_quality_validate_nonexistent_spec(self) -> None:
        """Test validating a nonexistent spec."""
        result = runner.invoke(
            app,
            ["quality", "validate", "nonexistent.yaml", "--path", str(FIXTURES_PATH)],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower()


class TestQualityNoArgsIsHelp:
    """Tests for quality command with no args."""

    def test_quality_no_args_shows_help(self) -> None:
        """Test running 'dli quality' with no args shows help."""
        result = runner.invoke(app, ["quality"])
        # no_args_is_help=True should show help
        assert result.exit_code in [0, 2]
        output = get_output(result)
        assert "list" in output or "run" in output or "Usage" in output


class TestQualityOutputFormats:
    """Tests for output format consistency."""

    def test_list_table_format(self) -> None:
        """Test list command with table format."""
        result = runner.invoke(app, ["quality", "list", "--format", "table"])
        assert result.exit_code == 0
        output = get_output(result)
        # Table should have structure
        assert "Name" in output or "Target" in output or "Quality" in output

    def test_get_table_format(self) -> None:
        """Test get command with table format."""
        result = runner.invoke(
            app, ["quality", "get", "pk_unique", "--format", "table"]
        )
        assert result.exit_code == 0


class TestQualityEdgeCases:
    """Tests for edge cases and error handling."""

    def test_run_with_parameters(self) -> None:
        """Test running with custom parameters."""
        spec_path = str(FIXTURES_PATH / "quality.iceberg.analytics.daily_clicks.yaml")

        # Use --server flag since LOCAL requires an actual executor
        result = runner.invoke(
            app,
            [
                "quality",
                "run",
                spec_path,
                "--param",
                "date=2025-01-01",
                "--server",
            ],
        )

        assert result.exit_code == 0

    def test_validate_multiple_specs(self) -> None:
        """Test validating different specs."""
        for spec_name in [
            "quality.iceberg.analytics.daily_clicks.yaml",
            "quality.iceberg.analytics.user_sessions.yaml",
            "quality.iceberg.core.users.yaml",
        ]:
            spec_path = str(FIXTURES_PATH / spec_name)
            result = runner.invoke(app, ["quality", "validate", spec_path])
            assert result.exit_code == 0, f"Failed for {spec_name}"
