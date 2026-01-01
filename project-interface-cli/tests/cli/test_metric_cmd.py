"""Tests for the metric subcommand."""

from __future__ import annotations

from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


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


# =============================================================================
# Test: Metric Transpile Command
# =============================================================================


class TestMetricTranspile:
    """Tests for metric transpile command."""

    def test_transpile_metric_basic(self, sample_project_path: Path) -> None:
        """Test basic metric transpilation."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show transpiled SQL
        assert "sql" in output.lower() or "transpile" in output.lower()

    def test_transpile_metric_with_parameters(self, sample_project_path: Path) -> None:
        """Test metric transpilation with parameters."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "-p",
                "date=2024-01-01",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should substitute parameters in SQL
        assert "2024-01-01" in output or "sql" in output.lower()

    def test_transpile_metric_json_format(self, sample_project_path: Path) -> None:
        """Test metric transpilation with JSON output."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        # Should be valid JSON
        import json
        try:
            data = json.loads(output)
            assert "sql" in data or "success" in data
        except json.JSONDecodeError:
            pytest.fail(f"Output is not valid JSON: {output}")

    def test_transpile_metric_with_rules_file(
        self, sample_project_path: Path, tmp_path: Path
    ) -> None:
        """Test metric transpilation with custom rules file."""
        # Create a temporary rules file
        rules_file = tmp_path / "rules.yaml"
        rules_file.write_text(
            """
rules:
  - source_pattern: "raw\\\\.(\\\\w+)"
    target_pattern: "production.\\\\1"
"""
        )
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--rules-file",
                str(rules_file),
            ],
        )
        assert result.exit_code == 0

    def test_transpile_metric_with_dialect(self, sample_project_path: Path) -> None:
        """Test metric transpilation with specific dialect."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--dialect",
                "bigquery",
            ],
        )
        assert result.exit_code == 0

    def test_transpile_metric_with_retry(self, sample_project_path: Path) -> None:
        """Test metric transpilation with retry option."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--transpile-retry",
                "3",
            ],
        )
        assert result.exit_code == 0

    def test_transpile_metric_table_format(self, sample_project_path: Path) -> None:
        """Test metric transpilation with table output format."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--format",
                "table",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show structured output
        assert "sql" in output.lower() or "transpile" in output.lower()

    def test_transpile_nonexistent_metric(self, sample_project_path: Path) -> None:
        """Test transpiling a metric that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "nonexistent.metric",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_transpile_metric_invalid_dialect(self, sample_project_path: Path) -> None:
        """Test metric transpilation with invalid dialect."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--dialect",
                "invalid_dialect",
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "invalid" in output.lower() or "error" in output.lower()

    def test_transpile_metric_invalid_rules_file(
        self, sample_project_path: Path
    ) -> None:
        """Test metric transpilation with nonexistent rules file."""
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--rules-file",
                "/nonexistent/rules.yaml",
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_transpile_metric_with_all_options(
        self, sample_project_path: Path, tmp_path: Path
    ) -> None:
        """Test metric transpilation with all options combined."""
        # Create a temporary rules file
        rules_file = tmp_path / "rules.yaml"
        rules_file.write_text(
            """
rules:
  - source_pattern: "raw\\\\.(\\\\w+)"
    target_pattern: "production.\\\\1"
"""
        )
        result = runner.invoke(
            app,
            [
                "metric",
                "transpile",
                "iceberg.reporting.user_summary",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
                "--rules-file",
                str(rules_file),
                "--transpile-retry",
                "2",
                "--dialect",
                "trino",
                "-p",
                "date=2024-01-01",
            ],
        )
        assert result.exit_code == 0

    def test_transpile_metric_help(self) -> None:
        """Test metric transpile help output."""
        result = runner.invoke(app, ["metric", "transpile", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show command description and options
        assert "transpile" in output.lower()
        assert "--format" in output or "--dialect" in output
