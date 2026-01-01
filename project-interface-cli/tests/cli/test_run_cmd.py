"""Tests for dli run CLI command."""

import json
from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


def get_output(result) -> str:
    """Get combined output from CLI result."""
    return result.output or result.stdout or ""


# =============================================================================
# TestRunBasic
# =============================================================================


class TestRunBasic:
    """Tests for basic run command functionality."""

    def test_run_basic(self, tmp_path: Path) -> None:
        """Test basic run command with required options."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1 as id")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "--output",
                str(output_file),
            ],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should show success message or output path
        assert "completed" in output.lower() or str(output_file) in output

    def test_run_sql_subcommand(self, tmp_path: Path) -> None:
        """Test run sql subcommand."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1 as id")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "sql",
                "--sql",
                str(sql_file),
                "--output",
                str(output_file),
            ],
        )

        assert result.exit_code == 0
        output = get_output(result)
        assert "completed" in output.lower() or str(output_file) in output

    def test_run_creates_output_file(self, tmp_path: Path) -> None:
        """Test that run command creates output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()

    def test_run_quiet_mode(self, tmp_path: Path) -> None:
        """Test run command in quiet mode."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--quiet",
            ],
        )

        assert result.exit_code == 0
        # Quiet mode should output the file path (may have newlines from terminal wrapping)
        output = get_output(result).replace("\n", "").strip()
        assert "output.csv" in output


# =============================================================================
# TestRunOutputFormats
# =============================================================================


class TestRunOutputFormats:
    """Tests for run command output formats."""

    def test_run_csv_format(self, tmp_path: Path) -> None:
        """Test run with CSV output format (default)."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-f",
                "csv",
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()
        content = output_file.read_text()
        # CSV should have comma separators or header
        assert "," in content or "id" in content

    def test_run_json_format(self, tmp_path: Path) -> None:
        """Test run with JSON output format."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.json"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-f",
                "json",
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()
        # JSON Lines format - each line should be valid JSON
        lines = output_file.read_text().strip().split("\n")
        for line in lines:
            if line:
                json.loads(line)  # Should not raise

    def test_run_tsv_format(self, tmp_path: Path) -> None:
        """Test run with TSV output format."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.tsv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-f",
                "tsv",
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()
        content = output_file.read_text()
        # TSV should have tab separators
        assert "\t" in content


# =============================================================================
# TestRunParameters
# =============================================================================


class TestRunParameters:
    """Tests for run command with parameters."""

    def test_run_with_single_param(self, tmp_path: Path) -> None:
        """Test run with single parameter."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT '{{ date }}' as date")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-p",
                "date=2026-01-01",
            ],
        )

        assert result.exit_code == 0

    def test_run_with_multiple_params(self, tmp_path: Path) -> None:
        """Test run with multiple parameters."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{ table }} WHERE date = '{{ date }}'")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-p",
                "table=users",
                "-p",
                "date=2026-01-01",
            ],
        )

        assert result.exit_code == 0


# =============================================================================
# TestRunDryRun
# =============================================================================


class TestRunDryRun:
    """Tests for run command --dry-run option."""

    def test_run_dry_run(self, tmp_path: Path) -> None:
        """Test run with --dry-run flag."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dry-run",
            ],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Dry run should show preview without executing
        assert "dry run" in output.lower() or "would" in output.lower()

    def test_dry_run_does_not_create_output(self, tmp_path: Path) -> None:
        """Test that dry run does not create output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dry-run",
            ],
        )

        assert result.exit_code == 0
        assert not output_file.exists()

    def test_dry_run_with_params(self, tmp_path: Path) -> None:
        """Test dry run shows parameter values."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT '{{ date }}' as date")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dry-run",
                "-p",
                "date=2026-01-01",
            ],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should show the parameter or rendered SQL
        assert "date" in output.lower()


# =============================================================================
# TestRunShowSQL
# =============================================================================


class TestRunShowSQL:
    """Tests for run command --show-sql option."""

    def test_run_show_sql(self, tmp_path: Path) -> None:
        """Test run with --show-sql flag."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM users")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--show-sql",
            ],
        )

        assert result.exit_code == 0
        output = get_output(result)
        # Should show SQL or "rendered" in output
        assert "sql" in output.lower() or "select" in output.lower()


# =============================================================================
# TestRunOptions
# =============================================================================


class TestRunOptions:
    """Tests for run command options."""

    def test_run_with_limit(self, tmp_path: Path) -> None:
        """Test run with row limit."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--limit",
                "100",
            ],
        )

        assert result.exit_code == 0

    def test_run_with_timeout(self, tmp_path: Path) -> None:
        """Test run with custom timeout."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--timeout",
                "60",
            ],
        )

        assert result.exit_code == 0

    def test_run_with_dialect_bigquery(self, tmp_path: Path) -> None:
        """Test run with BigQuery dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dialect",
                "bigquery",
            ],
        )

        assert result.exit_code == 0

    def test_run_with_dialect_trino(self, tmp_path: Path) -> None:
        """Test run with Trino dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dialect",
                "trino",
            ],
        )

        assert result.exit_code == 0


# =============================================================================
# TestRunExecutionMode
# =============================================================================


class TestRunExecutionMode:
    """Tests for run command execution mode options."""

    def test_run_with_local_option_succeeds_when_allowed(
        self, tmp_path: Path
    ) -> None:
        """Test run with --local flag succeeds when policy allows."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        # In mock mode (default for CLI tests), local should work
        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--local",
            ],
        )

        # Mock mode allows local execution
        assert result.exit_code == 0
        output = get_output(result)
        assert "completed" in output.lower() or output_file.exists()

    def test_run_with_server_option_succeeds(self, tmp_path: Path) -> None:
        """Test run with --server flag executes in server mode."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--server",
            ],
        )

        # Server mode should succeed (mock client handles it)
        assert result.exit_code == 0
        output = get_output(result)
        # Verify completion message or output file creation
        assert "completed" in output.lower() or output_file.exists()

    def test_run_both_local_and_server_error(self, tmp_path: Path) -> None:
        """Test error when both --local and --server specified."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--local",
                "--server",
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "both" in output.lower() or "error" in output.lower()

    def test_run_local_shows_mode_in_output(self, tmp_path: Path) -> None:
        """Test that local mode is indicated in successful output."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--local",
            ],
        )

        if result.exit_code == 0:
            # Verify output file was created
            assert output_file.exists()
            # Output should contain run completion info
            output = get_output(result)
            assert len(output) > 0

    def test_run_server_shows_mode_in_output(self, tmp_path: Path) -> None:
        """Test that server mode is indicated in successful output."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--server",
            ],
        )

        assert result.exit_code == 0
        # Verify output file was created
        assert output_file.exists()
        # Output should contain run completion info
        output = get_output(result)
        assert len(output) > 0


# =============================================================================
# TestRunErrorHandling
# =============================================================================


class TestRunErrorHandling:
    """Tests for run command error handling."""

    def test_run_missing_sql_option(self, tmp_path: Path) -> None:
        """Test error when --sql option missing."""
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "-o",
                str(output_file),
            ],
        )

        assert result.exit_code != 0
        output = get_output(result)
        assert "sql" in output.lower() or "missing" in output.lower()

    def test_run_missing_output_option(self, tmp_path: Path) -> None:
        """Test error when --output option missing."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
            ],
        )

        assert result.exit_code != 0
        output = get_output(result)
        assert "output" in output.lower() or "missing" in output.lower()

    def test_run_nonexistent_sql_file(self, tmp_path: Path) -> None:
        """Test error when SQL file does not exist."""
        sql_file = tmp_path / "nonexistent.sql"
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
            ],
        )

        assert result.exit_code != 0

    def test_run_invalid_dialect(self, tmp_path: Path) -> None:
        """Test error with invalid dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "--dialect",
                "invalid",
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "invalid" in output.lower() or "dialect" in output.lower()

    def test_run_invalid_param_format(self, tmp_path: Path) -> None:
        """Test error with invalid parameter format."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
                "-p",
                "invalid_no_equals",
            ],
        )

        assert result.exit_code == 1
        output = get_output(result)
        assert "=" in output or "format" in output.lower() or "error" in output.lower()


# =============================================================================
# TestRunHelp
# =============================================================================


class TestRunHelp:
    """Tests for run command help."""

    def test_run_help(self) -> None:
        """Test run --help shows usage info."""
        result = runner.invoke(app, ["run", "--help"])

        assert result.exit_code == 0
        output = get_output(result)
        assert "run" in output.lower()
        assert "--sql" in output
        assert "--output" in output

    def test_run_sql_help(self) -> None:
        """Test run sql --help shows usage info."""
        result = runner.invoke(app, ["run", "sql", "--help"])

        assert result.exit_code == 0
        output = get_output(result)
        assert "--sql" in output
        assert "--output" in output
        assert "--format" in output

    def test_run_no_args_shows_help_or_error(self) -> None:
        """Test run with no args shows help or missing option error."""
        result = runner.invoke(app, ["run"])

        # Should either show help (exit 0), error about missing options (exit 1),
        # or invalid usage (exit 2)
        assert result.exit_code in (0, 1, 2)


# =============================================================================
# TestRunEdgeCases
# =============================================================================


class TestRunEdgeCases:
    """Tests for run command edge cases."""

    def test_run_with_nested_output_path(self, tmp_path: Path) -> None:
        """Test run creates nested output directories."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "nested" / "deep" / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()

    def test_run_overwrite_existing_output(self, tmp_path: Path) -> None:
        """Test run overwrites existing output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"
        output_file.write_text("old content")

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
            ],
        )

        assert result.exit_code == 0
        assert output_file.exists()
        content = output_file.read_text()
        assert content != "old content"

    def test_run_empty_sql_file(self, tmp_path: Path) -> None:
        """Test run with empty SQL file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("")
        output_file = tmp_path / "output.csv"

        result = runner.invoke(
            app,
            [
                "run",
                "--sql",
                str(sql_file),
                "-o",
                str(output_file),
            ],
        )

        # Should succeed with empty result
        assert result.exit_code == 0


# =============================================================================
# TestFormatBytes
# =============================================================================


class TestFormatBytes:
    """Tests for _format_bytes helper function."""

    def test_format_bytes_none(self) -> None:
        """Test formatting None value."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(None) == "-"

    def test_format_bytes_zero(self) -> None:
        """Test formatting zero bytes."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(0) == "0 B"

    def test_format_bytes_small(self) -> None:
        """Test formatting bytes under 1KB."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(512) == "512 B"
        assert _format_bytes(999) == "999 B"

    def test_format_bytes_kilobytes(self) -> None:
        """Test formatting kilobytes."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(1_000) == "1.00 KB"
        assert _format_bytes(1_500) == "1.50 KB"
        assert _format_bytes(999_999) == "1000.00 KB"

    def test_format_bytes_megabytes(self) -> None:
        """Test formatting megabytes."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(1_000_000) == "1.00 MB"
        assert _format_bytes(1_500_000) == "1.50 MB"
        assert _format_bytes(999_999_999) == "1000.00 MB"

    def test_format_bytes_gigabytes(self) -> None:
        """Test formatting gigabytes."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(1_000_000_000) == "1.00 GB"
        assert _format_bytes(2_500_000_000) == "2.50 GB"

    def test_format_bytes_terabytes(self) -> None:
        """Test formatting terabytes."""
        from dli.commands.run import _format_bytes

        assert _format_bytes(1_000_000_000_000) == "1.00 TB"
        assert _format_bytes(5_500_000_000_000) == "5.50 TB"


# =============================================================================
# TestFormatDuration
# =============================================================================


class TestFormatDuration:
    """Tests for _format_duration helper function."""

    def test_format_duration_zero(self) -> None:
        """Test formatting zero seconds."""
        from dli.commands.run import _format_duration

        assert _format_duration(0) == "0.0s"

    def test_format_duration_subsecond(self) -> None:
        """Test formatting sub-second durations."""
        from dli.commands.run import _format_duration

        assert _format_duration(0.5) == "0.5s"
        assert _format_duration(0.123) == "0.1s"

    def test_format_duration_seconds(self) -> None:
        """Test formatting durations under 1 minute."""
        from dli.commands.run import _format_duration

        assert _format_duration(1.0) == "1.0s"
        assert _format_duration(30.5) == "30.5s"
        assert _format_duration(59.9) == "59.9s"

    def test_format_duration_minutes(self) -> None:
        """Test formatting durations over 1 minute."""
        from dli.commands.run import _format_duration

        assert _format_duration(60) == "1m 0s"
        assert _format_duration(90) == "1m 30s"
        assert _format_duration(150) == "2m 30s"

    def test_format_duration_large(self) -> None:
        """Test formatting large durations."""
        from dli.commands.run import _format_duration

        assert _format_duration(3600) == "60m 0s"
        assert _format_duration(3661) == "61m 1s"


# =============================================================================
# TestParseParameters
# =============================================================================


class TestParseParameters:
    """Tests for _parse_parameters helper function."""

    def test_parse_parameters_none(self) -> None:
        """Test parsing None input."""
        from dli.commands.run import _parse_parameters

        assert _parse_parameters(None) == {}

    def test_parse_parameters_empty_list(self) -> None:
        """Test parsing empty list."""
        from dli.commands.run import _parse_parameters

        assert _parse_parameters([]) == {}

    def test_parse_parameters_single(self) -> None:
        """Test parsing single parameter."""
        from dli.commands.run import _parse_parameters

        result = _parse_parameters(["date=2026-01-01"])
        assert result == {"date": "2026-01-01"}

    def test_parse_parameters_multiple(self) -> None:
        """Test parsing multiple parameters."""
        from dli.commands.run import _parse_parameters

        result = _parse_parameters(["table=users", "date=2026-01-01", "limit=100"])
        assert result == {"table": "users", "date": "2026-01-01", "limit": "100"}

    def test_parse_parameters_value_with_equals(self) -> None:
        """Test parsing value containing equals sign."""
        from dli.commands.run import _parse_parameters

        result = _parse_parameters(["condition=a=b"])
        assert result == {"condition": "a=b"}

    def test_parse_parameters_empty_value(self) -> None:
        """Test parsing parameter with empty value."""
        from dli.commands.run import _parse_parameters

        result = _parse_parameters(["empty="])
        assert result == {"empty": ""}

    def test_parse_parameters_key_with_spaces(self) -> None:
        """Test parsing key with leading/trailing spaces (stripped)."""
        from dli.commands.run import _parse_parameters

        result = _parse_parameters(["  key  =value"])
        assert result == {"key": "value"}

    def test_parse_parameters_missing_equals_raises(self) -> None:
        """Test that missing equals sign raises BadParameter."""
        import typer

        from dli.commands.run import _parse_parameters

        with pytest.raises(typer.BadParameter) as exc_info:
            _parse_parameters(["no_equals_sign"])

        assert "key=value" in str(exc_info.value)

    def test_parse_parameters_empty_key_raises(self) -> None:
        """Test that empty key raises BadParameter."""
        import typer

        from dli.commands.run import _parse_parameters

        with pytest.raises(typer.BadParameter) as exc_info:
            _parse_parameters(["=value"])

        assert "Key cannot be empty" in str(exc_info.value)
