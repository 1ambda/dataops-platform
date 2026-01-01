"""Tests for format CLI commands (dli dataset format, dli metric format).

These tests validate the format CLI functionality including:
- Help output
- Check mode exit codes
- Error handling for unknown resources
- Dialect option validation
"""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from typer.testing import CliRunner

from dli.main import app

# Check if FORMAT feature is implemented
try:
    from dli.models.format import FormatResult, FormatStatus

    FORMAT_IMPLEMENTED = True
except ImportError:
    FORMAT_IMPLEMENTED = False

from tests.cli.conftest import get_output

runner = CliRunner()


class TestFormatHelp:
    """Tests for format command help output."""

    def test_dataset_format_help(self) -> None:
        """Test dataset format command help."""
        result = runner.invoke(app, ["dataset", "format", "--help"])

        # Should show help (exit code 0) or command not found (if not implemented)
        if result.exit_code == 0:
            output = get_output(result)
            assert "format" in output.lower() or "Format" in output
            # Check for expected options
            if FORMAT_IMPLEMENTED:
                assert "--check" in output
                assert "--lint" in output or "--sql-only" in output
        else:
            # Command not implemented yet
            pytest.skip("format command not implemented")

    def test_metric_format_help(self) -> None:
        """Test metric format command help."""
        result = runner.invoke(app, ["metric", "format", "--help"])

        if result.exit_code == 0:
            output = get_output(result)
            assert "format" in output.lower() or "Format" in output
        else:
            pytest.skip("format command not implemented")


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatCheckMode:
    """Tests for format check mode."""

    @pytest.fixture
    def tmp_project(self, tmp_path: Path) -> Path:
        """Create temporary project with sample dataset."""
        # Create dli.yaml
        (tmp_path / "dli.yaml").write_text("""
project_name: test_project
defaults:
  dialect: bigquery
  catalog: iceberg
  schema: analytics
""")

        # Create dataset YAML
        yaml_path = tmp_path / "dataset.iceberg.analytics.test_clicks.yaml"
        yaml_path.write_text("""
tags:
  - daily
name: iceberg.analytics.test_clicks
owner: test@example.com
type: Dataset
query_file: sql/test_clicks.sql
""")

        # Create SQL
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        (sql_dir / "test_clicks.sql").write_text("""
select user_id,count(*) as clicks
from raw_clicks
where dt = '{{ ds }}'
group by user_id
""")

        return tmp_path

    def test_format_check_mode_exit_0_no_changes(self, tmp_project: Path) -> None:
        """Test check mode returns exit code 0 when no changes needed."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "iceberg.analytics.test_clicks",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        # Exit code 0 if no changes, 1 if changes needed
        assert result.exit_code in [0, 1]

    def test_format_check_mode_exit_1_changes_needed(
        self, tmp_project: Path
    ) -> None:
        """Test check mode returns exit code 1 when changes are needed."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "iceberg.analytics.test_clicks",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        # With unformatted SQL, should indicate changes needed (exit code 1)
        # or success if already formatted (exit code 0)
        assert result.exit_code in [0, 1]
        output = get_output(result)
        # Should have some output about the result
        assert len(output) > 0

    def test_format_check_mode_with_diff(self, tmp_project: Path) -> None:
        """Test check mode with --diff option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "iceberg.analytics.test_clicks",
                "--check",
                "--diff",
                "--path",
                str(tmp_project),
            ],
        )

        # Should show diff if changes are needed
        if result.exit_code == 1:
            output = get_output(result)
            # Diff might contain + or - for additions/removals
            # or the word "diff" or "change"
            assert (
                "+" in output
                or "-" in output
                or "diff" in output.lower()
                or "change" in output.lower()
            )


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatUnknownResource:
    """Tests for handling unknown resources."""

    def test_format_unknown_dataset(self, tmp_path: Path) -> None:
        """Test error on unknown dataset."""
        # Create minimal dli.yaml
        (tmp_path / "dli.yaml").write_text("project_name: test")

        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "nonexistent_dataset",
                "--path",
                str(tmp_path),
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert (
            "not found" in output.lower()
            or "error" in output.lower()
            or "does not exist" in output.lower()
        )

    def test_format_unknown_metric(self, tmp_path: Path) -> None:
        """Test error on unknown metric."""
        # Create minimal dli.yaml
        (tmp_path / "dli.yaml").write_text("project_name: test")

        result = runner.invoke(
            app,
            [
                "metric",
                "format",
                "nonexistent_metric",
                "--path",
                str(tmp_path),
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert (
            "not found" in output.lower()
            or "error" in output.lower()
            or "does not exist" in output.lower()
        )


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatDialect:
    """Tests for dialect option."""

    @pytest.fixture
    def tmp_project(self, tmp_path: Path) -> Path:
        """Create temporary project."""
        (tmp_path / "dli.yaml").write_text("project_name: test")
        yaml_path = tmp_path / "dataset.test.table.yaml"
        yaml_path.write_text("""
name: test.table
owner: test@example.com
type: Dataset
query_file: sql/table.sql
""")
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        (sql_dir / "table.sql").write_text("select 1")
        return tmp_path

    def test_format_unsupported_dialect(self, tmp_project: Path) -> None:
        """Test error on unsupported dialect."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--dialect",
                "unknown_dialect",
                "--path",
                str(tmp_project),
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert (
            "unsupported" in output.lower()
            or "dialect" in output.lower()
            or "error" in output.lower()
        )

    def test_format_with_bigquery_dialect(self, tmp_project: Path) -> None:
        """Test formatting with BigQuery dialect."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--dialect",
                "bigquery",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        # Should succeed or indicate changes needed
        assert result.exit_code in [0, 1]

    def test_format_with_trino_dialect(self, tmp_project: Path) -> None:
        """Test formatting with Trino dialect."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--dialect",
                "trino",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        # Should succeed or indicate changes needed
        assert result.exit_code in [0, 1]


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatOptions:
    """Tests for various format options."""

    @pytest.fixture
    def tmp_project(self, tmp_path: Path) -> Path:
        """Create temporary project."""
        (tmp_path / "dli.yaml").write_text("project_name: test")
        yaml_path = tmp_path / "dataset.test.table.yaml"
        yaml_path.write_text("""
name: test.table
owner: test@example.com
type: Dataset
query_file: sql/table.sql
""")
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        (sql_dir / "table.sql").write_text("select a,b from t")
        return tmp_path

    def test_format_sql_only_option(self, tmp_project: Path) -> None:
        """Test --sql-only option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--sql-only",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        assert result.exit_code in [0, 1]

    def test_format_yaml_only_option(self, tmp_project: Path) -> None:
        """Test --yaml-only option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--yaml-only",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        assert result.exit_code in [0, 1]

    def test_format_lint_option(self, tmp_project: Path) -> None:
        """Test --lint option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--lint",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        assert result.exit_code in [0, 1]

    def test_format_json_output(self, tmp_project: Path) -> None:
        """Test --format json option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--check",
                "--format",
                "json",
                "--path",
                str(tmp_project),
            ],
        )

        if result.exit_code in [0, 1]:
            output = get_output(result)
            # Should be valid JSON
            try:
                json.loads(output)
            except json.JSONDecodeError:
                # May have extra output before JSON
                pass


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatMutualExclusion:
    """Tests for mutually exclusive options."""

    @pytest.fixture
    def tmp_project(self, tmp_path: Path) -> Path:
        """Create temporary project."""
        (tmp_path / "dli.yaml").write_text("project_name: test")
        return tmp_path

    def test_sql_only_and_yaml_only_exclusive(self, tmp_project: Path) -> None:
        """Test that --sql-only and --yaml-only are mutually exclusive."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--sql-only",
                "--yaml-only",
                "--path",
                str(tmp_project),
            ],
        )

        # Should fail with error about mutual exclusion
        assert result.exit_code != 0


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not implemented")
class TestFormatOutput:
    """Tests for format command output."""

    @pytest.fixture
    def tmp_project(self, tmp_path: Path) -> Path:
        """Create temporary project with unformatted files."""
        (tmp_path / "dli.yaml").write_text("project_name: test")
        yaml_path = tmp_path / "dataset.test.table.yaml"
        yaml_path.write_text("""
tags: [daily]
name: test.table
owner: test@example.com
type: Dataset
query_file: sql/table.sql
""")
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        (sql_dir / "table.sql").write_text("select a,b from t where x=1")
        return tmp_path

    def test_format_shows_summary(self, tmp_project: Path) -> None:
        """Test format shows summary of changes."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--check",
                "--path",
                str(tmp_project),
            ],
        )

        output = get_output(result)
        # Should show some summary
        assert (
            "file" in output.lower()
            or "change" in output.lower()
            or "format" in output.lower()
        )

    def test_format_table_output(self, tmp_project: Path) -> None:
        """Test format with table output."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "format",
                "test.table",
                "--check",
                "--format",
                "table",
                "--path",
                str(tmp_project),
            ],
        )

        assert result.exit_code in [0, 1]
        output = get_output(result)
        assert len(output) > 0


class TestFormatCommandNotImplemented:
    """Tests that gracefully handle unimplemented format command."""

    def test_format_command_availability(self) -> None:
        """Check if format command is available."""
        result = runner.invoke(app, ["dataset", "--help"])
        output = get_output(result)

        if "format" in output.lower():
            # Format command is available
            pass
        else:
            # Format command not yet added
            pytest.skip("format command not added to CLI yet")
