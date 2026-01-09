"""Tests for trace-related utilities in dli.commands.utils module.

This module tests print_error, display_error, and get_effective_trace_mode functions.
"""

from __future__ import annotations

import re
from io import StringIO
from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import pytest
from rich.console import Console

from dli.commands import utils as utils_module
from dli.core.trace import TraceContext
from dli.exceptions import DLIError, ErrorCode
from dli.models.common import TraceMode

if TYPE_CHECKING:
    from collections.abc import Generator


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def trace_context() -> TraceContext:
    """Create test trace context with fixed values."""
    return TraceContext(
        trace_id="550e8400-e29b-41d4-a716-446655440000",
        command="workflow backfill",
        cli_version="0.9.0",
        os_name="darwin",
        python_version="3.12.1",
    )


@pytest.fixture
def capture_stderr() -> Generator[StringIO, None, None]:
    """Capture stderr output from Rich console."""
    output = StringIO()
    # Replace the error_console with one that writes to our StringIO
    with patch.object(utils_module, "error_console", Console(file=output, force_terminal=False)):
        yield output


@pytest.fixture
def capture_stderr_with_trace(
    trace_context: TraceContext,
) -> Generator[tuple[StringIO, TraceContext], None, None]:
    """Capture stderr output and patch get_current_trace to return trace context."""
    output = StringIO()
    with (
        patch.object(utils_module, "error_console", Console(file=output, force_terminal=False)),
        patch.object(utils_module, "get_current_trace", return_value=trace_context),
    ):
        yield output, trace_context


def strip_ansi(text: str) -> str:
    """Remove ANSI escape codes from text."""
    ansi_pattern = re.compile(r"\x1b\[[0-9;]*m")
    return ansi_pattern.sub("", text)


# =============================================================================
# print_error Tests
# =============================================================================


class TestPrintErrorBasic:
    """Tests for basic print_error functionality."""

    def test_print_error_basic(self, capture_stderr: StringIO) -> None:
        """print_error should print error message without trace context."""
        TraceContext.clear_current()

        utils_module.print_error("Connection failed")

        output = strip_ansi(capture_stderr.getvalue())
        assert "Error:" in output
        assert "Connection failed" in output

    def test_print_error_message_format(self, capture_stderr: StringIO) -> None:
        """print_error should format message with x prefix."""
        TraceContext.clear_current()

        utils_module.print_error("Something went wrong")

        output = strip_ansi(capture_stderr.getvalue())
        assert "x Error:" in output
        assert "Something went wrong" in output


class TestPrintErrorWithErrorCode:
    """Tests for print_error with error codes."""

    def test_print_error_with_error_code(self, capture_stderr: StringIO) -> None:
        """print_error should include error code in brackets."""
        TraceContext.clear_current()

        utils_module.print_error("Server unreachable", error_code="DLI-501")

        output = strip_ansi(capture_stderr.getvalue())
        assert "[DLI-501]" in output
        assert "Server unreachable" in output

    def test_print_error_without_error_code(self, capture_stderr: StringIO) -> None:
        """print_error should work without error code."""
        TraceContext.clear_current()

        utils_module.print_error("Generic error")

        output = strip_ansi(capture_stderr.getvalue())
        assert "DLI-" not in output
        assert "Generic error" in output


class TestPrintErrorWithTraceAlways:
    """Tests for print_error with TraceMode.ALWAYS."""

    def test_print_error_with_trace_always(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """print_error should include trace ID when mode is ALWAYS."""
        output, _ = capture_stderr_with_trace
        utils_module.print_error("Test error", trace_mode=TraceMode.ALWAYS)

        result = strip_ansi(output.getvalue())
        assert "[trace:550e8400]" in result
        assert "Test error" in result

    def test_print_error_with_trace_always_and_error_code(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """print_error should include both error code and trace ID."""
        output, _ = capture_stderr_with_trace
        utils_module.print_error("Connection timeout", error_code="DLI-501", trace_mode=TraceMode.ALWAYS)

        result = strip_ansi(output.getvalue())
        assert "[DLI-501]" in result
        assert "[trace:550e8400]" in result
        assert "Connection timeout" in result


class TestPrintErrorWithTraceNever:
    """Tests for print_error with TraceMode.NEVER."""

    def test_print_error_with_trace_never(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """print_error should exclude trace ID when mode is NEVER."""
        output, _ = capture_stderr_with_trace
        utils_module.print_error("Test error", trace_mode=TraceMode.NEVER)

        result = strip_ansi(output.getvalue())
        assert "trace:" not in result
        assert "550e8400" not in result
        assert "Test error" in result

    def test_print_error_with_trace_never_but_no_context(
        self, capture_stderr: StringIO
    ) -> None:
        """print_error with NEVER mode should work without trace context."""
        TraceContext.clear_current()

        utils_module.print_error("No context error", trace_mode=TraceMode.NEVER)

        output = strip_ansi(capture_stderr.getvalue())
        assert "trace:" not in output
        assert "No context error" in output


class TestPrintErrorWithTraceErrorOnly:
    """Tests for print_error with TraceMode.ERROR_ONLY (default)."""

    def test_print_error_with_trace_error_only_shows_trace(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """print_error with ERROR_ONLY (default) should show trace on errors."""
        output, _ = capture_stderr_with_trace
        # ERROR_ONLY is the default
        utils_module.print_error("Error occurred", trace_mode=TraceMode.ERROR_ONLY)

        result = strip_ansi(output.getvalue())
        assert "[trace:550e8400]" in result
        assert "Error occurred" in result

    def test_print_error_default_trace_mode(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """print_error default trace_mode should be ERROR_ONLY."""
        output, _ = capture_stderr_with_trace
        # Don't pass trace_mode, use default
        utils_module.print_error("Default mode error")

        result = strip_ansi(output.getvalue())
        # ERROR_ONLY shows trace on errors
        assert "[trace:550e8400]" in result


class TestPrintErrorNoTraceContext:
    """Tests for print_error without trace context set."""

    def test_print_error_no_context_always(self, capture_stderr: StringIO) -> None:
        """print_error with ALWAYS mode but no context should not crash."""
        TraceContext.clear_current()

        utils_module.print_error("No context", trace_mode=TraceMode.ALWAYS)

        output = strip_ansi(capture_stderr.getvalue())
        assert "trace:" not in output  # No trace context, no trace ID
        assert "No context" in output


# =============================================================================
# display_error Tests
# =============================================================================


class TestDisplayError:
    """Tests for display_error function."""

    def test_display_error_with_dli_error(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """display_error should format DLIError with code and trace."""
        output, _ = capture_stderr_with_trace
        error = DLIError(message="Connection timeout", code=ErrorCode.SERVER_UNREACHABLE)
        utils_module.display_error(error, trace_mode=TraceMode.ALWAYS)

        result = strip_ansi(output.getvalue())
        assert "[DLI-501]" in result
        assert "[trace:550e8400]" in result
        assert "Connection timeout" in result

    def test_display_error_respects_trace_mode_never(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """display_error should honor trace_mode parameter."""
        output, _ = capture_stderr_with_trace
        error = DLIError(message="Test error", code=ErrorCode.EXECUTION_FAILED)
        utils_module.display_error(error, trace_mode=TraceMode.NEVER)

        result = strip_ansi(output.getvalue())
        assert "[DLI-401]" in result  # Error code should still show
        assert "trace:" not in result  # But trace ID should not
        assert "Test error" in result

    def test_display_error_default_trace_mode(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """display_error should use ERROR_ONLY as default trace mode."""
        output, _ = capture_stderr_with_trace
        error = DLIError(message="Default mode", code=ErrorCode.VALIDATION_FAILED)
        # Don't pass trace_mode, use default
        utils_module.display_error(error)

        result = strip_ansi(output.getvalue())
        assert "[DLI-201]" in result
        # ERROR_ONLY shows trace on errors
        assert "[trace:550e8400]" in result

    def test_display_error_with_various_error_codes(
        self, capture_stderr: StringIO
    ) -> None:
        """display_error should work with various error codes."""
        TraceContext.clear_current()

        test_cases = [
            (ErrorCode.CONFIG_NOT_FOUND, "DLI-002"),
            (ErrorCode.DATASET_NOT_FOUND, "DLI-101"),
            (ErrorCode.TRANSPILE_FAILED, "DLI-301"),
        ]

        for error_code, expected_code in test_cases:
            # Clear the buffer
            capture_stderr.truncate(0)
            capture_stderr.seek(0)

            error = DLIError(message="Test", code=error_code)
            utils_module.display_error(error, trace_mode=TraceMode.NEVER)

            output = strip_ansi(capture_stderr.getvalue())
            assert f"[{expected_code}]" in output, f"Expected {expected_code} in output"


# =============================================================================
# get_effective_trace_mode Tests
# =============================================================================


class TestGetEffectiveTraceMode:
    """Tests for get_effective_trace_mode function."""

    def test_get_effective_trace_mode_true_flag(self) -> None:
        """get_effective_trace_mode should return ALWAYS when flag is True."""
        result = utils_module.get_effective_trace_mode(True)
        assert result == TraceMode.ALWAYS

    def test_get_effective_trace_mode_false_flag(self) -> None:
        """get_effective_trace_mode should return NEVER when flag is False."""
        result = utils_module.get_effective_trace_mode(False)
        assert result == TraceMode.NEVER

    def test_get_effective_trace_mode_none_flag_uses_config(self) -> None:
        """get_effective_trace_mode should use config when flag is None."""
        mock_config_api = MagicMock()
        mock_config_api.get_trace_mode.return_value = TraceMode.ERROR_ONLY

        # Patch at dli.api.config.ConfigAPI since it's imported dynamically inside the function
        with patch("dli.api.config.ConfigAPI", return_value=mock_config_api):
            result = utils_module.get_effective_trace_mode(None)

        assert result == TraceMode.ERROR_ONLY
        mock_config_api.get_trace_mode.assert_called_once()

    def test_get_effective_trace_mode_config_returns_always(self) -> None:
        """get_effective_trace_mode should return config's ALWAYS setting."""
        mock_config_api = MagicMock()
        mock_config_api.get_trace_mode.return_value = TraceMode.ALWAYS

        with patch("dli.api.config.ConfigAPI", return_value=mock_config_api):
            result = utils_module.get_effective_trace_mode(None)

        assert result == TraceMode.ALWAYS

    def test_get_effective_trace_mode_config_returns_never(self) -> None:
        """get_effective_trace_mode should return config's NEVER setting."""
        mock_config_api = MagicMock()
        mock_config_api.get_trace_mode.return_value = TraceMode.NEVER

        with patch("dli.api.config.ConfigAPI", return_value=mock_config_api):
            result = utils_module.get_effective_trace_mode(None)

        assert result == TraceMode.NEVER


class TestGetEffectiveTraceModeEdgeCases:
    """Edge case tests for get_effective_trace_mode."""

    def test_get_effective_trace_mode_explicit_true_overrides_config(self) -> None:
        """Explicit True should override any config setting."""
        # Even if config would return NEVER, True flag should win
        result = utils_module.get_effective_trace_mode(True)
        assert result == TraceMode.ALWAYS

    def test_get_effective_trace_mode_explicit_false_overrides_config(self) -> None:
        """Explicit False should override any config setting."""
        # Even if config would return ALWAYS, False flag should win
        result = utils_module.get_effective_trace_mode(False)
        assert result == TraceMode.NEVER


# =============================================================================
# Integration Tests
# =============================================================================


class TestTraceUtilsIntegration:
    """Integration tests combining multiple trace utilities."""

    def test_full_error_flow_with_trace(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """Test complete error flow with trace context."""
        output, _ = capture_stderr_with_trace
        error = DLIError(
            message="Query execution failed",
            code=ErrorCode.EXECUTION_FAILED,
        )

        # Simulate CLI error handling
        trace_mode = utils_module.get_effective_trace_mode(True)  # --trace flag
        utils_module.display_error(error, trace_mode=trace_mode)

        result = strip_ansi(output.getvalue())

        # Should have all components
        assert "x Error:" in result
        assert "[DLI-401]" in result
        assert "[trace:550e8400]" in result
        assert "Query execution failed" in result

    def test_full_error_flow_without_trace(
        self, capture_stderr_with_trace: tuple[StringIO, TraceContext]
    ) -> None:
        """Test error flow with trace disabled."""
        output, _ = capture_stderr_with_trace
        error = DLIError(
            message="Validation failed",
            code=ErrorCode.VALIDATION_FAILED,
        )

        # Simulate CLI error handling with --no-trace
        trace_mode = utils_module.get_effective_trace_mode(False)
        utils_module.display_error(error, trace_mode=trace_mode)

        result = strip_ansi(output.getvalue())

        # Should have error but no trace
        assert "x Error:" in result
        assert "[DLI-201]" in result
        assert "trace:" not in result
        assert "Validation failed" in result
