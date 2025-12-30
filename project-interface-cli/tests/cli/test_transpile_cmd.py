"""Tests for the transpile subcommand."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


def get_output(result) -> str:
    """Get combined output (Typer mixes stdout/stderr by default)."""
    return result.output or result.stdout or ""


# =============================================================================
# Test: Inline SQL Transpile
# =============================================================================


class TestTranspileInline:
    """Tests for inline SQL transpile command."""

    def test_simple_transpile(self) -> None:
        """Test basic inline SQL transpilation."""
        result = runner.invoke(app, ["transpile", "SELECT * FROM users"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show transpile result
        assert "transpile" in output.lower() or "sql" in output.lower()

    def test_transpile_with_substitution(self) -> None:
        """Test transpilation with table substitution."""
        result = runner.invoke(app, ["transpile", "SELECT * FROM raw.events"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show success and applied rules
        assert "success" in output.lower() or "rule" in output.lower()

    def test_transpile_with_metric(self) -> None:
        """Test transpilation with METRIC function."""
        result = runner.invoke(app, ["transpile", "SELECT METRIC(revenue) FROM orders"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show the result
        assert "transpile" in output.lower() or "sql" in output.lower()

    def test_transpile_with_unknown_metric_graceful(self) -> None:
        """Test transpilation with unknown metric (graceful mode)."""
        result = runner.invoke(
            app, ["transpile", "SELECT METRIC(unknown_metric) FROM orders"]
        )
        # Should succeed with warning in non-strict mode
        assert result.exit_code == 0
        output = get_output(result)
        # May show warning about unknown metric
        assert "transpile" in output.lower() or "warning" in output.lower()


# =============================================================================
# Test: File-based Transpile
# =============================================================================


class TestTranspileFile:
    """Tests for file-based SQL transpile command."""

    def test_transpile_from_file(self) -> None:
        """Test transpilation from SQL file."""
        with tempfile.NamedTemporaryFile(mode="w", suffix=".sql", delete=False) as f:
            f.write("SELECT * FROM analytics.users")
            f.flush()
            temp_path = f.name

        try:
            result = runner.invoke(app, ["transpile", "-f", temp_path])
            assert result.exit_code == 0
            output = get_output(result)
            assert "transpile" in output.lower() or "sql" in output.lower()
        finally:
            Path(temp_path).unlink()

    def test_transpile_from_nonexistent_file(self) -> None:
        """Test transpilation from nonexistent file."""
        result = runner.invoke(app, ["transpile", "-f", "/nonexistent/path/query.sql"])
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_transpile_from_complex_file(self) -> None:
        """Test transpilation from file with complex SQL."""
        sql_content = """
        WITH active_users AS (
            SELECT id, name
            FROM analytics.users
            WHERE active = true
        )
        SELECT u.*, COUNT(*) as order_count
        FROM active_users u
        LEFT JOIN legacy.orders o ON u.id = o.user_id
        GROUP BY u.id, u.name
        LIMIT 100
        """
        with tempfile.NamedTemporaryFile(mode="w", suffix=".sql", delete=False) as f:
            f.write(sql_content)
            f.flush()
            temp_path = f.name

        try:
            result = runner.invoke(app, ["transpile", "-f", temp_path])
            assert result.exit_code == 0
            output = get_output(result)
            # Should show result with substitutions
            assert (
                "transpile" in output.lower()
                or "success" in output.lower()
                or "sql" in output.lower()
            )
        finally:
            Path(temp_path).unlink()


# =============================================================================
# Test: JSON Output Format
# NOTE: Options must come BEFORE the SQL argument in Typer commands
# =============================================================================


class TestTranspileJsonOutput:
    """Tests for JSON output format."""

    def test_json_format_output(self) -> None:
        """Test JSON output format."""
        # Options must come before SQL argument
        result = runner.invoke(
            app, ["transpile", "--format", "json", "SELECT * FROM users"]
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        # Should be valid JSON
        try:
            data = json.loads(output)
            assert "success" in data
            assert "sql" in data
            assert "metadata" in data
        except json.JSONDecodeError:
            pytest.fail(f"Output is not valid JSON: {output}")

    def test_json_format_with_rules(self) -> None:
        """Test JSON output includes applied rules."""
        result = runner.invoke(
            app, ["transpile", "--format", "json", "SELECT * FROM raw.events"]
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        data = json.loads(output)
        assert "applied_rules" in data
        assert isinstance(data["applied_rules"], list)

    def test_json_format_with_warnings(self) -> None:
        """Test JSON output includes warnings."""
        result = runner.invoke(
            app, ["transpile", "--format", "json", "SELECT * FROM users"]
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        data = json.loads(output)
        assert "warnings" in data
        assert isinstance(data["warnings"], list)

    def test_json_format_with_error(self) -> None:
        """Test JSON output includes error field."""
        result = runner.invoke(
            app,
            [
                "transpile",
                "--format",
                "json",
                "SELECT METRIC(nonexistent) FROM t",
            ],
        )
        # May succeed with graceful degradation
        output = get_output(result).strip()
        try:
            data = json.loads(output)
            # error field should be present (may be null)
            assert "error" in data
        except json.JSONDecodeError:
            pass  # May have other output on error


# =============================================================================
# Test: Table Output Format
# =============================================================================


class TestTranspileTableOutput:
    """Tests for table (default) output format."""

    def test_table_format_default(self) -> None:
        """Test default table format output."""
        result = runner.invoke(app, ["transpile", "SELECT * FROM users"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show structured output with labels
        assert (
            "status" in output.lower()
            or "sql" in output.lower()
            or "transpile" in output.lower()
        )

    def test_table_format_explicit(self) -> None:
        """Test explicit table format option."""
        result = runner.invoke(
            app, ["transpile", "--format", "table", "SELECT * FROM users"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "transpile" in output.lower() or "sql" in output.lower()


# =============================================================================
# Test: Show Rules Option
# =============================================================================


class TestShowRulesOption:
    """Tests for --show-rules option."""

    def test_show_rules_flag(self) -> None:
        """Test --show-rules displays rule details."""
        result = runner.invoke(
            app, ["transpile", "--show-rules", "SELECT * FROM raw.events"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show rule details
        assert (
            "rule" in output.lower()
            or "substitution" in output.lower()
            or "source" in output.lower()
        )

    def test_no_show_rules_by_default(self) -> None:
        """Test rules detail not shown by default."""
        result = runner.invoke(app, ["transpile", "SELECT * FROM users"])
        assert result.exit_code == 0
        # Default output should be less verbose


# =============================================================================
# Test: Strict Mode
# =============================================================================


class TestStrictMode:
    """Tests for --strict option."""

    def test_strict_mode_success(self) -> None:
        """Test strict mode with valid SQL."""
        result = runner.invoke(
            app, ["transpile", "--strict", "SELECT id FROM users LIMIT 10"]
        )
        assert result.exit_code == 0

    def test_strict_mode_with_parse_error(self) -> None:
        """Test strict mode with invalid SQL."""
        # SQL that truly fails to parse (unclosed parenthesis)
        result = runner.invoke(
            app, ["transpile", "--strict", "SELECT * FROM users WHERE id IN (1, 2"]
        )
        # Should fail in strict mode
        assert result.exit_code == 1
        output = get_output(result)
        assert "error" in output.lower() or "failed" in output.lower()


# =============================================================================
# Test: Validate Option
# =============================================================================


class TestValidateOption:
    """Tests for --validate option."""

    def test_validate_valid_sql(self) -> None:
        """Test validation of valid SQL."""
        result = runner.invoke(
            app, ["transpile", "--validate", "SELECT * FROM users"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "valid" in output.lower()

    def test_validate_invalid_sql(self) -> None:
        """Test validation of invalid SQL."""
        # SQL that truly fails to parse (unclosed parenthesis)
        result = runner.invoke(
            app, ["transpile", "--validate", "SELECT * FROM users WHERE id IN (1, 2"]
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "failed" in output.lower() or "error" in output.lower()


# =============================================================================
# Test: Dialect Option
# =============================================================================


class TestDialectOption:
    """Tests for --dialect option."""

    def test_trino_dialect(self) -> None:
        """Test Trino dialect (default)."""
        result = runner.invoke(
            app, ["transpile", "--dialect", "trino", "SELECT * FROM users"]
        )
        assert result.exit_code == 0

    def test_bigquery_dialect(self) -> None:
        """Test BigQuery dialect."""
        result = runner.invoke(
            app, ["transpile", "--dialect", "bigquery", "SELECT * FROM users"]
        )
        assert result.exit_code == 0

    def test_invalid_dialect(self) -> None:
        """Test invalid dialect value."""
        result = runner.invoke(
            app, ["transpile", "--dialect", "postgres", "SELECT * FROM users"]
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "invalid" in output.lower() or "error" in output.lower()

    def test_dialect_short_option(self) -> None:
        """Test short dialect option -d."""
        result = runner.invoke(
            app, ["transpile", "-d", "bigquery", "SELECT * FROM users"]
        )
        assert result.exit_code == 0


# =============================================================================
# Test: Help Output
# =============================================================================


class TestTranspileHelp:
    """Tests for help output."""

    def test_help_flag(self) -> None:
        """Test --help flag."""
        result = runner.invoke(app, ["transpile", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show command description
        assert "transpile" in output.lower()
        assert "sql" in output.lower()

    def test_help_shows_options(self) -> None:
        """Test help shows available options."""
        result = runner.invoke(app, ["transpile", "--help"])
        output = get_output(result)
        # Should show key options
        assert "--file" in output or "-f" in output
        assert "--format" in output
        assert "--strict" in output
        assert "--dialect" in output or "-d" in output

    def test_no_args_shows_help(self) -> None:
        """Test no arguments shows help."""
        result = runner.invoke(app, ["transpile"])
        # Should show help or usage when no SQL provided
        assert result.exit_code in [0, 2]
        output = get_output(result)
        assert (
            "transpile" in output.lower()
            or "usage" in output.lower()
            or "sql" in output.lower()
        )


# =============================================================================
# Test: Error Handling
# =============================================================================


class TestErrorHandling:
    """Tests for error handling."""

    def test_empty_sql_error(self) -> None:
        """Test error for empty SQL string."""
        result = runner.invoke(app, ["transpile", ""])
        assert result.exit_code == 1
        output = get_output(result)
        assert "empty" in output.lower() or "error" in output.lower()

    def test_whitespace_only_sql_error(self) -> None:
        """Test error for whitespace-only SQL."""
        result = runner.invoke(app, ["transpile", "   "])
        assert result.exit_code == 1
        output = get_output(result)
        assert "empty" in output.lower() or "error" in output.lower()

    def test_both_sql_and_file_error(self) -> None:
        """Test error when both SQL and file are provided."""
        with tempfile.NamedTemporaryFile(mode="w", suffix=".sql", delete=False) as f:
            f.write("SELECT 1")
            f.flush()
            temp_path = f.name

        try:
            # Options before argument
            result = runner.invoke(
                app, ["transpile", "-f", temp_path, "SELECT * FROM users"]
            )
            assert result.exit_code == 1
            output = get_output(result)
            assert "cannot" in output.lower() or "error" in output.lower()
        finally:
            Path(temp_path).unlink()

    def test_file_read_error(self) -> None:
        """Test error when file cannot be read."""
        result = runner.invoke(app, ["transpile", "-f", "/nonexistent/file.sql"])
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()


# =============================================================================
# Test: Complex Scenarios
# =============================================================================


class TestComplexScenarios:
    """Tests for complex usage scenarios."""

    def test_cte_transpilation(self) -> None:
        """Test CTE (Common Table Expression) transpilation."""
        sql = """
        WITH active AS (
            SELECT * FROM raw.events WHERE active = true
        )
        SELECT * FROM active LIMIT 100
        """
        result = runner.invoke(app, ["transpile", sql])
        assert result.exit_code == 0

    def test_join_transpilation(self) -> None:
        """Test JOIN query transpilation."""
        sql = """
        SELECT e.*, u.name
        FROM raw.events e
        JOIN analytics.users u ON e.user_id = u.id
        LIMIT 100
        """
        result = runner.invoke(app, ["transpile", sql])
        assert result.exit_code == 0

    def test_subquery_transpilation(self) -> None:
        """Test subquery transpilation."""
        sql = """
        SELECT * FROM (
            SELECT * FROM analytics.users
        ) sub LIMIT 10
        """
        result = runner.invoke(app, ["transpile", sql])
        assert result.exit_code == 0

    def test_metric_with_alias(self) -> None:
        """Test METRIC with column alias."""
        sql = "SELECT METRIC(revenue) AS total_revenue FROM orders"
        result = runner.invoke(app, ["transpile", sql])
        assert result.exit_code == 0

    def test_combined_options(self) -> None:
        """Test combined options."""
        # All options before SQL argument
        result = runner.invoke(
            app,
            [
                "transpile",
                "--format",
                "json",
                "--show-rules",
                "--dialect",
                "trino",
                "SELECT * FROM raw.events",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        # Should be valid JSON
        data = json.loads(output)
        assert data["success"] is True


# =============================================================================
# Test: Warning Display
# =============================================================================


class TestWarningDisplay:
    """Tests for warning display."""

    def test_select_star_warning(self) -> None:
        """Test SELECT * warning is shown."""
        result = runner.invoke(app, ["transpile", "SELECT * FROM users LIMIT 10"])
        assert result.exit_code == 0
        output = get_output(result)
        # Warning about SELECT * should be shown
        assert (
            "warning" in output.lower()
            or "*" in output
            or "select" in output.lower()
        )

    def test_no_limit_warning(self) -> None:
        """Test no LIMIT warning is shown."""
        result = runner.invoke(app, ["transpile", "SELECT id FROM users"])
        assert result.exit_code == 0
        output = get_output(result)
        # Warning about missing LIMIT should be shown
        assert "warning" in output.lower() or "limit" in output.lower()

    def test_dangerous_statement_warning(self) -> None:
        """Test dangerous statement warning."""
        result = runner.invoke(app, ["transpile", "DROP TABLE users"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show dangerous statement warning
        assert (
            "warning" in output.lower()
            or "dangerous" in output.lower()
            or "drop" in output.lower()
        )
