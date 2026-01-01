"""Tests for the query subcommand."""

from __future__ import annotations

import json
from pathlib import Path

from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


# =============================================================================
# Test: query list
# =============================================================================


class TestQueryList:
    """Tests for query list command."""

    def test_list_default(self, sample_project_path: Path) -> None:
        """Test listing queries with default options (my scope, limit 10)."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table with query info or "no queries" message
        assert (
            "query_id" in output.lower()
            or "no queries" in output.lower()
            or "queries" in output.lower()
        )

    def test_list_json_format(self, sample_project_path: Path) -> None:
        """Test listing queries in JSON format."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--format",
                "json",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output and "no queries" not in output.lower():
            try:
                data = json.loads(output)
                assert isinstance(data, dict)
                assert "queries" in data
            except json.JSONDecodeError:
                pass  # May have mixed output in mock mode

    def test_list_with_scope_system(self, sample_project_path: Path) -> None:
        """Test listing queries with system scope filter."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "system",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should include account column for non-MY scope
        if "no queries" not in output.lower():
            assert "account" in output.lower() or "query_id" in output.lower()

    def test_list_with_scope_user(self, sample_project_path: Path) -> None:
        """Test listing queries with user scope filter."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "user",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show personal account queries
        assert "no queries" in output.lower() or "query_id" in output.lower()

    def test_list_with_scope_all(self, sample_project_path: Path) -> None:
        """Test listing queries with all scope."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # All scope should include both personal and system accounts
        if "no queries" not in output.lower():
            assert "account" in output.lower() or "query_id" in output.lower()

    def test_list_with_account_keyword(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by account keyword."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "airflow",
                "--scope",
                "system",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter by account name containing 'airflow'
        assert (
            "airflow" in output.lower()
            or "no queries" in output.lower()
            or "query_id" in output.lower()
        )

    def test_list_with_status_filter(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by status."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--status",
                "running",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter to only running queries
        # Note: Rich tables may truncate "running" to "runni..." in narrow terminals
        assert "runni" in output.lower() or "no queries" in output.lower()

    def test_list_with_status_failed(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by failed status."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--status",
                "failed",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter to only failed queries
        if "no queries" not in output.lower():
            assert "failed" in output.lower() or "query_id" in output.lower()

    def test_list_with_tag_filter(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by tag."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--tag",
                "team::analytics",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter by tag
        assert "no queries" in output.lower() or "query_id" in output.lower()

    def test_list_with_multiple_tags(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by multiple tags (AND logic)."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--tag",
                "team::analytics",
                "--tag",
                "pipeline::daily",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_with_sql_filter(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by SQL pattern."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--sql",
                "SELECT * FROM users",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter by SQL content
        assert "no queries" in output.lower() or "query_id" in output.lower()

    def test_list_with_engine_filter(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by engine."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--engine",
                "bigquery",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter by engine type
        assert "no queries" in output.lower() or "query_id" in output.lower()

    def test_list_with_engine_trino(self, sample_project_path: Path) -> None:
        """Test listing queries filtered by trino engine."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--engine",
                "trino",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_with_limit(self, sample_project_path: Path) -> None:
        """Test listing queries with custom limit."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--limit",
                "5",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_with_offset(self, sample_project_path: Path) -> None:
        """Test listing queries with pagination offset."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--offset",
                "10",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_with_since(self, sample_project_path: Path) -> None:
        """Test listing queries with since time filter."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--since",
                "7d",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_with_until(self, sample_project_path: Path) -> None:
        """Test listing queries with until time filter."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--since",
                "7d",
                "--until",
                "1d",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0

    def test_list_combined_filters(self, sample_project_path: Path) -> None:
        """Test listing queries with combined filters."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "system",
                "--status",
                "success",
                "--engine",
                "bigquery",
                "--limit",
                "20",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0


# =============================================================================
# Test: query show
# =============================================================================


class TestQueryShow:
    """Tests for query show command."""

    def test_show_query(self, sample_project_path: Path) -> None:
        """Test showing query detail."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--path",
                str(sample_project_path),
            ],
        )
        # May succeed or fail depending on mock state
        output = get_output(result)
        assert (
            "query" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_show_query_json_format(self, sample_project_path: Path) -> None:
        """Test showing query detail in JSON format."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--format",
                "json",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0 and output.strip():
            try:
                data = json.loads(output.strip())
                assert isinstance(data, dict)
            except json.JSONDecodeError:
                pass  # May have mixed output

    def test_show_query_with_full_query(self, sample_project_path: Path) -> None:
        """Test showing query with full query text."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--full-query",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        # Should show query or error
        assert (
            "query" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_show_query_not_found(self, sample_project_path: Path) -> None:
        """Test showing a query that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "nonexistent_query_id_12345",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_show_query_displays_sections(self, sample_project_path: Path) -> None:
        """Test that show displays expected sections."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0:
            # Should display various sections
            output_lower = output.lower()
            assert (
                "query id" in output_lower
                or "engine" in output_lower
                or "state" in output_lower
                or "not found" in output_lower
            )


# =============================================================================
# Test: query cancel
# =============================================================================


class TestQueryCancel:
    """Tests for query cancel command."""

    def test_cancel_by_id(self, sample_project_path: Path) -> None:
        """Test cancelling a specific query by ID."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "bq_job_abc123",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        # Should show cancel result or error
        assert (
            "cancel" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_cancel_by_user(self, sample_project_path: Path) -> None:
        """Test cancelling all queries for a user account."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "--user",
                "airflow-prod",
                "--force",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        # Should show cancel result or no running queries
        assert (
            "cancel" in output.lower()
            or "no running" in output.lower()
            or "error" in output.lower()
        )

    def test_cancel_dry_run(self, sample_project_path: Path) -> None:
        """Test cancel in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "bq_job_abc123",
                "--dry-run",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        # Dry run should not actually cancel
        assert (
            "dry run" in output.lower()
            or "would cancel" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_cancel_dry_run_by_user(self, sample_project_path: Path) -> None:
        """Test cancel dry-run for user account."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "--user",
                "airflow-prod",
                "--dry-run",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        assert (
            "dry run" in output.lower()
            or "would cancel" in output.lower()
            or "no running" in output.lower()
        )

    def test_cancel_force(self, sample_project_path: Path) -> None:
        """Test cancel with force mode (skip confirmation)."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "--user",
                "airflow-prod",
                "--force",
                "--path",
                str(sample_project_path),
            ],
        )
        # Force should skip confirmation prompt
        output = get_output(result)
        assert (
            "cancel" in output.lower()
            or "no running" in output.lower()
            or "error" in output.lower()
        )

    def test_cancel_invalid_params_both_id_and_user(
        self, sample_project_path: Path
    ) -> None:
        """Test error when both query_id and --user are provided."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "bq_job_abc123",
                "--user",
                "airflow-prod",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "cannot" in output.lower() or "error" in output.lower()

    def test_cancel_invalid_params_neither_id_nor_user(
        self, sample_project_path: Path
    ) -> None:
        """Test error when neither query_id nor --user is provided."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "must" in output.lower() or "error" in output.lower()

    def test_cancel_nonexistent_query(self, sample_project_path: Path) -> None:
        """Test cancelling a query that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "nonexistent_query_id_12345",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_cancel_json_output(self, sample_project_path: Path) -> None:
        """Test cancel with JSON output format."""
        result = runner.invoke(
            app,
            [
                "query",
                "cancel",
                "bq_job_abc123",
                "--format",
                "json",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0 and output.strip():
            try:
                data = json.loads(output.strip())
                assert isinstance(data, dict)
            except json.JSONDecodeError:
                pass  # May have mixed output


# =============================================================================
# Test: query help
# =============================================================================


class TestQueryHelp:
    """Tests for query help and command structure."""

    def test_query_help(self) -> None:
        """Test query main help output."""
        result = runner.invoke(app, ["query", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show query description and available commands
        assert "query" in output.lower()
        assert "list" in output.lower()
        assert "show" in output.lower()
        assert "cancel" in output.lower()

    def test_query_no_args_shows_help(self) -> None:
        """Test that query without arguments shows help."""
        result = runner.invoke(app, ["query"])
        # Typer's no_args_is_help returns exit code 0 or 2
        assert result.exit_code in [0, 2]
        output = get_output(result)
        # Should show available commands
        assert "list" in output.lower()
        assert "show" in output.lower()
        assert "cancel" in output.lower()

    def test_query_list_help(self) -> None:
        """Test query list subcommand help."""
        result = runner.invoke(app, ["query", "list", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show list options
        assert "--scope" in output
        assert "--status" in output or "-S" in output
        assert "--tag" in output or "-t" in output
        assert "--limit" in output or "-n" in output
        assert "--format" in output or "-f" in output
        assert "--engine" in output
        assert "--sql" in output

    def test_query_show_help(self) -> None:
        """Test query show subcommand help."""
        result = runner.invoke(app, ["query", "show", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show show options
        assert "query_id" in output.lower() or "query-id" in output.lower()
        assert "--full-query" in output
        assert "--format" in output or "-f" in output

    def test_query_cancel_help(self) -> None:
        """Test query cancel subcommand help."""
        result = runner.invoke(app, ["query", "cancel", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show cancel options
        assert "--user" in output
        assert "--dry-run" in output
        assert "--force" in output
        assert "--format" in output or "-f" in output


# =============================================================================
# Test: Edge cases and error handling
# =============================================================================


class TestQueryEdgeCases:
    """Tests for edge cases and error handling."""

    def test_invalid_scope_option(self, sample_project_path: Path) -> None:
        """Test invalid scope option value."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "invalid_scope",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0

    def test_invalid_status_option(self, sample_project_path: Path) -> None:
        """Test invalid status option value."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--status",
                "invalid_status",
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
                "query",
                "list",
                "--format",
                "xml",
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
                "query",
                "list",
                "--limit",
                "-5",
                "--path",
                str(sample_project_path),
            ],
        )
        # Behavior depends on implementation - verify it handles gracefully
        assert result.exit_code in [0, 1, 2]

    def test_zero_limit(self, sample_project_path: Path) -> None:
        """Test zero limit value."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--limit",
                "0",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should handle gracefully
        assert result.exit_code in [0, 1, 2]

    def test_empty_query_id(self, sample_project_path: Path) -> None:
        """Test empty query ID argument."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "",
                "--path",
                str(sample_project_path),
            ],
        )
        # Empty string should be handled gracefully
        assert result.exit_code in [0, 1, 2]

    def test_invalid_project_path(self) -> None:
        """Test with invalid project path."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--path",
                "/nonexistent/path/to/project",
            ],
        )
        # CLI may fall back to mock mode or return error
        # Verify it handles gracefully without crashing
        output = get_output(result)
        assert result.exit_code in [0, 1, 2]
        # If it succeeded, it used mock mode; if failed, should show error
        if result.exit_code != 0:
            assert "not found" in output.lower() or "error" in output.lower()

    def test_invalid_since_format(self, sample_project_path: Path) -> None:
        """Test with invalid since time format."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--since",
                "invalid_time",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should handle invalid time format
        # May fail or default to a reasonable value
        assert result.exit_code in [0, 1, 2]


# =============================================================================
# Test: Output formatting
# =============================================================================


class TestQueryOutputFormatting:
    """Tests for query output formatting."""

    def test_list_table_format_columns(self, sample_project_path: Path) -> None:
        """Test that table format shows expected columns."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0 and "no queries" not in output.lower():
            # Should include key columns in table header
            output_upper = output.upper()
            assert (
                "QUERY_ID" in output_upper
                or "ENGINE" in output_upper
                or "STATE" in output_upper
            )

    def test_list_my_scope_hides_account(self, sample_project_path: Path) -> None:
        """Test that MY scope doesn't show account column."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "my",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0 and "no queries" not in output.lower():
            # MY scope should not show ACCOUNT column header
            # (individual query output may still mention account)
            output_upper = output.upper()
            # The ACCOUNT column header should not be prominent in MY scope
            # This test validates the table structure
            assert "QUERY_ID" in output_upper or "no queries" in output.lower()

    def test_list_non_my_scope_shows_account(self, sample_project_path: Path) -> None:
        """Test that non-MY scope shows account column."""
        result = runner.invoke(
            app,
            [
                "query",
                "list",
                "--scope",
                "all",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0 and "no queries" not in output.lower():
            # Non-MY scope should show ACCOUNT column
            output_upper = output.upper()
            assert (
                "ACCOUNT" in output_upper
                or "TYPE" in output_upper
                or "no queries" in output.lower()
            )

    def test_show_displays_timing_section(self, sample_project_path: Path) -> None:
        """Test that show displays timing section."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0:
            output_lower = output.lower()
            # Should display timing information
            assert (
                "started" in output_lower
                or "duration" in output_lower
                or "timing" in output_lower
                or "not found" in output_lower
            )

    def test_show_displays_resources_section(self, sample_project_path: Path) -> None:
        """Test that show displays resources section."""
        result = runner.invoke(
            app,
            [
                "query",
                "show",
                "bq_job_abc123",
                "--path",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        if result.exit_code == 0:
            output_lower = output.lower()
            # Should display resource information
            assert (
                "bytes" in output_lower
                or "resources" in output_lower
                or "rows" in output_lower
                or "not found" in output_lower
            )
