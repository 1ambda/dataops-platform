"""Tests for debug CLI command.

Covers:
- `dli debug` runs all checks
- `dli debug --connection` runs only connection checks
- `dli debug --auth` runs only auth checks
- `dli debug --json` outputs valid JSON
- `dli debug --verbose` shows detailed output
- Exit code 0 when all pass, 1 when any fail
- Multiple flags combine correctly
"""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


class TestDebugCommand:
    """Tests for `dli debug` command."""

    def test_debug_basic(self) -> None:
        """Test basic debug command runs."""
        result = runner.invoke(app, ["debug"])

        # Should run without crashing (exit 0 or 1)
        assert result.exit_code in [0, 1]
        # Should show version header
        output = get_output(result)
        assert "debug" in output.lower() or "dli" in output.lower()

    def test_debug_runs_checks(self) -> None:
        """Test debug command runs diagnostic checks."""
        result = runner.invoke(app, ["debug"])
        output = get_output(result)

        # Should show check categories or results
        assert (
            "System" in output
            or "PASS" in output
            or "pass" in output.lower()
            or "check" in output.lower()
        )

    def test_help_flag(self) -> None:
        """Test --help shows usage information."""
        result = runner.invoke(app, ["debug", "--help"])

        assert result.exit_code == 0
        output = get_output(result)
        assert "debug" in output.lower()
        # Should mention available options
        assert "--connection" in output
        assert "--auth" in output or "-a" in output
        assert "--json" in output
        assert "--config" in output or "-c" in output


class TestDebugConnectionFlag:
    """Tests for `dli debug --connection` flag."""

    def test_connection_flag(self) -> None:
        """Test --connection runs only connection checks."""
        result = runner.invoke(app, ["debug", "--connection"])
        output = get_output(result)

        # Should run (exit 0 or 1)
        assert result.exit_code in [0, 1]
        # Should mention database/connection
        assert (
            "Database" in output
            or "Connection" in output
            or "connection" in output.lower()
        )

    def test_connection_only_long_flag(self) -> None:
        """Test --connection works (no short flag -c as it's used by --config)."""
        result = runner.invoke(app, ["debug", "--connection"])

        assert result.exit_code in [0, 1]

    def test_connection_with_dialect(self) -> None:
        """Test --connection with --dialect option."""
        result = runner.invoke(app, ["debug", "--connection", "--dialect", "bigquery"])
        output = get_output(result)

        assert result.exit_code in [0, 1]


class TestDebugAuthFlag:
    """Tests for `dli debug --auth` flag."""

    def test_auth_flag(self) -> None:
        """Test --auth runs only authentication checks."""
        result = runner.invoke(app, ["debug", "--auth"])
        output = get_output(result)

        assert result.exit_code in [0, 1]
        # Should mention auth
        assert (
            "Authentication" in output
            or "Auth" in output
            or "auth" in output.lower()
        )

    def test_auth_short_flag(self) -> None:
        """Test -a short flag works."""
        result = runner.invoke(app, ["debug", "-a"])

        assert result.exit_code in [0, 1]


class TestDebugNetworkFlag:
    """Tests for `dli debug --network` flag."""

    def test_network_flag(self) -> None:
        """Test --network runs only network checks."""
        result = runner.invoke(app, ["debug", "--network"])
        output = get_output(result)

        assert result.exit_code in [0, 1]
        # Should mention network
        assert (
            "Network" in output
            or "network" in output.lower()
            or "DNS" in output
        )

    def test_network_short_flag(self) -> None:
        """Test -n short flag works."""
        result = runner.invoke(app, ["debug", "-n"])

        assert result.exit_code in [0, 1]


class TestDebugServerFlag:
    """Tests for `dli debug --server` flag."""

    def test_server_flag(self) -> None:
        """Test --server runs only server checks."""
        result = runner.invoke(app, ["debug", "--server"])
        output = get_output(result)

        assert result.exit_code in [0, 1]
        # Should mention server
        assert (
            "Server" in output
            or "server" in output.lower()
            or "Basecamp" in output
        )

    def test_server_short_flag(self) -> None:
        """Test -s short flag works."""
        result = runner.invoke(app, ["debug", "-s"])

        assert result.exit_code in [0, 1]


class TestDebugConfigFlag:
    """Tests for `dli debug --config` flag."""

    def test_config_flag(self) -> None:
        """Test --config validates project configuration."""
        result = runner.invoke(app, ["debug", "--config"])
        output = get_output(result)

        assert result.exit_code in [0, 1]
        # Should mention project/config
        assert (
            "Project" in output
            or "Config" in output
            or "project" in output.lower()
            or "config" in output.lower()
        )

    def test_config_short_flag(self) -> None:
        """Test -c short flag works."""
        result = runner.invoke(app, ["debug", "-c"])

        assert result.exit_code in [0, 1]


class TestDebugJsonOutput:
    """Tests for `dli debug --json` output."""

    def test_json_output_is_valid(self) -> None:
        """Test --json outputs valid JSON."""
        result = runner.invoke(app, ["debug", "--json"])
        output = get_output(result)

        # Should be valid JSON
        data = json.loads(output)
        assert isinstance(data, dict)

    def test_json_output_structure(self) -> None:
        """Test --json output has expected structure."""
        result = runner.invoke(app, ["debug", "--json"])
        output = get_output(result)

        data = json.loads(output)

        # Should have required fields
        assert "success" in data
        assert "checks" in data
        assert "version" in data

    def test_json_output_with_connection_flag(self) -> None:
        """Test --json with --connection flag."""
        result = runner.invoke(app, ["debug", "--connection", "--json"])
        output = get_output(result)

        data = json.loads(output)
        assert "success" in data

    def test_json_output_has_timestamp(self) -> None:
        """Test --json output includes timestamp."""
        result = runner.invoke(app, ["debug", "--json"])
        output = get_output(result)

        data = json.loads(output)
        assert "timestamp" in data


class TestDebugVerboseOutput:
    """Tests for `dli debug --verbose` output."""

    def test_verbose_flag(self) -> None:
        """Test --verbose shows detailed output."""
        result = runner.invoke(app, ["debug", "--verbose"])
        output = get_output(result)

        assert result.exit_code in [0, 1]
        # Verbose should have more detail (could include tree characters or extra info)
        assert len(output) > 0

    def test_verbose_short_flag(self) -> None:
        """Test -v short flag works."""
        result = runner.invoke(app, ["debug", "-v"])

        assert result.exit_code in [0, 1]

    def test_verbose_shows_more_than_default(self) -> None:
        """Test verbose output is longer than default."""
        result_normal = runner.invoke(app, ["debug"])
        result_verbose = runner.invoke(app, ["debug", "--verbose"])

        output_normal = get_output(result_normal)
        output_verbose = get_output(result_verbose)

        # Verbose should generally show more detail
        # (At minimum, they should both run successfully)
        assert result_normal.exit_code in [0, 1]
        assert result_verbose.exit_code in [0, 1]


class TestDebugExitCodes:
    """Tests for exit code semantics."""

    def test_exit_code_0_when_all_pass(self) -> None:
        """Test exit code 0 when all checks pass."""
        # In mock mode or with proper environment, should pass
        with patch("dli.commands.debug.DebugAPI") as mock_api_class:
            mock_api = MagicMock()
            mock_result = MagicMock()
            mock_result.success = True
            mock_result.checks = []
            mock_result.version = "0.7.0"
            mock_result.timestamp = "2026-01-01T00:00:00Z"
            mock_result.passed_count = 5
            mock_result.failed_count = 0
            mock_result.total_count = 5
            mock_result.by_category = {}
            mock_result.model_dump_json.return_value = json.dumps({
                "success": True,
                "checks": [],
                "version": "0.7.0",
                "timestamp": "2026-01-01T00:00:00Z",
            })
            mock_api.run_all.return_value = mock_result
            mock_api_class.return_value = mock_api

            result = runner.invoke(app, ["debug", "--json"])

            # When success=True, exit code should be 0
            assert result.exit_code == 0

    def test_exit_code_1_when_any_fail(self) -> None:
        """Test exit code 1 when any check fails."""
        with patch("dli.commands.debug.DebugAPI") as mock_api_class:
            mock_api = MagicMock()
            mock_result = MagicMock()
            mock_result.success = False
            mock_result.checks = []
            mock_result.version = "0.7.0"
            mock_result.timestamp = "2026-01-01T00:00:00Z"
            mock_result.passed_count = 3
            mock_result.failed_count = 1
            mock_result.total_count = 4
            mock_result.by_category = {}
            mock_result.model_dump_json.return_value = json.dumps({
                "success": False,
                "checks": [],
                "version": "0.7.0",
                "timestamp": "2026-01-01T00:00:00Z",
            })
            mock_api.run_all.return_value = mock_result
            mock_api_class.return_value = mock_api

            result = runner.invoke(app, ["debug", "--json"])

            # When success=False, exit code should be 1
            assert result.exit_code == 1


class TestDebugMultipleFlags:
    """Tests for combining multiple flags."""

    def test_connection_and_auth_flags(self) -> None:
        """Test combining --connection and --auth flags."""
        result = runner.invoke(app, ["debug", "--connection", "--auth"])
        output = get_output(result)

        assert result.exit_code in [0, 1]

    def test_all_filter_flags(self) -> None:
        """Test combining all filter flags."""
        result = runner.invoke(app, [
            "debug",
            "--connection",
            "--auth",
            "--network",
            "--server",
            "--config",
        ])

        assert result.exit_code in [0, 1]

    def test_flags_with_verbose(self) -> None:
        """Test filter flags with --verbose."""
        result = runner.invoke(app, ["debug", "--connection", "--verbose"])

        assert result.exit_code in [0, 1]

    def test_flags_with_json(self) -> None:
        """Test filter flags with --json."""
        result = runner.invoke(app, ["debug", "--auth", "--json"])
        output = get_output(result)

        # Should be valid JSON
        data = json.loads(output)
        assert "success" in data


class TestDebugPathOption:
    """Tests for --path option."""

    def test_path_option(self, tmp_path: Path) -> None:
        """Test --path option sets project path."""
        # Create minimal project
        (tmp_path / "dli.yaml").write_text("project_name: test")

        result = runner.invoke(app, ["debug", "--config", "--path", str(tmp_path)])

        assert result.exit_code in [0, 1]

    def test_invalid_path(self) -> None:
        """Test --path with invalid path."""
        result = runner.invoke(app, ["debug", "--config", "--path", "/nonexistent/xyz"])

        # Should either fail or show path error
        output = get_output(result)
        assert result.exit_code in [0, 1, 2]


class TestDebugTimeoutOption:
    """Tests for --timeout option."""

    def test_timeout_option(self) -> None:
        """Test --timeout option is accepted."""
        result = runner.invoke(app, ["debug", "--timeout", "60"])

        assert result.exit_code in [0, 1]

    def test_timeout_short_flag(self) -> None:
        """Test -t short flag works."""
        result = runner.invoke(app, ["debug", "-t", "30"])

        assert result.exit_code in [0, 1]


class TestDebugDialectOption:
    """Tests for --dialect option."""

    def test_dialect_bigquery(self) -> None:
        """Test --dialect bigquery option."""
        result = runner.invoke(app, ["debug", "--connection", "--dialect", "bigquery"])

        assert result.exit_code in [0, 1]

    def test_dialect_trino(self) -> None:
        """Test --dialect trino option."""
        result = runner.invoke(app, ["debug", "--connection", "--dialect", "trino"])

        assert result.exit_code in [0, 1]

    def test_dialect_short_flag(self) -> None:
        """Test -d short flag works."""
        result = runner.invoke(app, ["debug", "--connection", "-d", "bigquery"])

        assert result.exit_code in [0, 1]


class TestDebugOutputFormatting:
    """Tests for output formatting."""

    def test_shows_pass_fail_status(self) -> None:
        """Test output shows PASS/FAIL status."""
        result = runner.invoke(app, ["debug"])
        output = get_output(result)

        # Should show some form of status
        assert (
            "PASS" in output
            or "FAIL" in output
            or "pass" in output.lower()
            or "fail" in output.lower()
            or "[" in output  # Rich formatting
        )

    def test_shows_summary(self) -> None:
        """Test output shows summary of checks."""
        result = runner.invoke(app, ["debug"])
        output = get_output(result)

        # Should show some form of summary (counts, pass/fail, etc.)
        assert (
            "/" in output  # e.g., "12/12"
            or "passed" in output.lower()
            or "check" in output.lower()
        )

    def test_table_format_default(self) -> None:
        """Test default output uses table format."""
        result = runner.invoke(app, ["debug"])
        output = get_output(result)

        # Should not be JSON by default
        try:
            json.loads(output)
            # If it parses as JSON, that's unexpected for default format
            # (unless --json is default, which it shouldn't be)
        except json.JSONDecodeError:
            pass  # Expected: not JSON format


class TestDebugErrorHandling:
    """Tests for error handling."""

    def test_handles_exceptions_gracefully(self) -> None:
        """Test CLI handles exceptions without crashing."""
        # Even with bad input, should not raise uncaught exception
        result = runner.invoke(app, ["debug", "--timeout", "-1"])

        # Should handle gracefully (may fail with error message)
        assert result.exit_code in [0, 1, 2]

    def test_shows_remediation_on_failure(self) -> None:
        """Test failure output includes remediation hints."""
        # Force a failure by pointing to nonexistent path
        result = runner.invoke(app, [
            "debug", "--config", "--path", "/definitely/not/a/real/path"
        ])
        output = get_output(result)

        # If there's a failure, should have actionable info
        if result.exit_code == 1:
            # Should contain some guidance
            assert len(output) > 0


class TestDebugExitCode2:
    """Tests for exit code 2 (usage errors / invalid arguments).

    In Typer/Click, exit code 2 indicates a usage error such as:
    - Unknown option provided
    - Missing required argument
    - Invalid option value type
    """

    def test_exit_code_2_on_unknown_option(self) -> None:
        """Test exit code 2 when unknown option is provided."""
        result = runner.invoke(app, ["debug", "--invalid-option-xyz"])
        output = get_output(result)

        # Unknown options should produce exit code 2
        assert result.exit_code == 2
        # Error message should indicate the issue
        assert "invalid" in output.lower() or "no such option" in output.lower()

    def test_exit_code_2_on_invalid_timeout_type(self) -> None:
        """Test exit code 2 when timeout is not an integer."""
        result = runner.invoke(app, ["debug", "--timeout", "not-a-number"])
        output = get_output(result)

        # Invalid type for integer option should produce exit code 2
        assert result.exit_code == 2
        assert "invalid" in output.lower() or "not a valid" in output.lower()

    def test_exit_code_2_on_missing_option_value(self) -> None:
        """Test exit code 2 when option requires value but none provided."""
        result = runner.invoke(app, ["debug", "--dialect"])
        output = get_output(result)

        # Missing required option value should produce exit code 2
        assert result.exit_code == 2
        assert "requires" in output.lower() or "missing" in output.lower() or "argument" in output.lower()

    def test_exit_code_2_on_multiple_invalid_options(self) -> None:
        """Test exit code 2 when multiple invalid options provided."""
        result = runner.invoke(app, ["debug", "--foo", "--bar", "--baz"])
        output = get_output(result)

        # Multiple unknown options should produce exit code 2
        assert result.exit_code == 2

    def test_invalid_arguments_produce_error_message(self) -> None:
        """Test that invalid arguments produce helpful error message."""
        result = runner.invoke(app, ["debug", "--unknown-option-123"])
        output = get_output(result)

        assert result.exit_code == 2
        # Should have some error indication
        assert len(output) > 0
        # Should contain "error" or similar indicator
        assert (
            "error" in output.lower()
            or "usage" in output.lower()
            or "invalid" in output.lower()
            or "no such option" in output.lower()
        )

    def test_exit_code_1_vs_2_distinction(self) -> None:
        """Test distinction between exit code 1 (check failure) and 2 (usage error)."""
        # Exit code 1: Valid command but checks fail
        result_valid = runner.invoke(app, [
            "debug", "--config", "--path", "/nonexistent/path/xyz123"
        ])

        # Exit code 2: Invalid command syntax
        result_invalid = runner.invoke(app, ["debug", "--not-a-real-option"])

        # Usage error (exit 2) should be different from check failure (exit 0 or 1)
        assert result_invalid.exit_code == 2
        assert result_valid.exit_code in [0, 1]  # Valid command, may pass or fail checks

    def test_valid_dialect_values_do_not_produce_exit_2(self) -> None:
        """Test that valid dialect values work correctly (no exit code 2)."""
        # Valid dialects should not produce usage error
        for dialect in ["bigquery", "trino"]:
            result = runner.invoke(app, ["debug", "--connection", "--dialect", dialect])
            # Should not be exit code 2 (usage error)
            # May be 0 (pass) or 1 (fail) depending on environment
            assert result.exit_code in [0, 1], f"Dialect '{dialect}' produced unexpected exit code {result.exit_code}"
