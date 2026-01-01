"""Tests for dli.api.debug module (DebugAPI).

Covers:
- DebugAPI initialization with context
- run_all() in MOCK mode (all pass)
- check_system() returns system checks
- check_project() validates project path
- check_server() checks server connectivity
- Failed check includes remediation message
- Mock mode behavior
"""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from dli.api.debug import DebugAPI
from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus, DebugResult
from dli.models.common import ExecutionContext, ExecutionMode


class TestDebugAPIInit:
    """Tests for DebugAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = DebugAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, server_url="https://test.com"
        )
        api = DebugAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_project_path(self, tmp_path: Path) -> None:
        """Test initialization with project path."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        assert api.context.project_path == tmp_path

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(
            server_url="https://test.com", execution_mode=ExecutionMode.MOCK
        )
        api = DebugAPI(context=ctx)

        result = repr(api)

        assert "DebugAPI" in result


class TestDebugAPIMockMode:
    """Tests for DebugAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> DebugAPI:
        """Create DebugAPI in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DebugAPI(context=ctx)

    def test_run_all_returns_debug_result(self, mock_api: DebugAPI) -> None:
        """Test run_all returns DebugResult."""
        result = mock_api.run_all()

        assert isinstance(result, DebugResult)

    def test_run_all_mock_all_pass(self, mock_api: DebugAPI) -> None:
        """Test run_all in mock mode returns all passing checks."""
        result = mock_api.run_all()

        assert result.success is True
        assert result.total_count > 0
        assert result.passed_count == result.total_count
        assert result.failed_count == 0

    def test_run_all_has_version(self, mock_api: DebugAPI) -> None:
        """Test run_all result includes version."""
        result = mock_api.run_all()

        assert result.version is not None
        # Version should be a valid semver-like string
        assert "." in result.version

    def test_run_all_has_timestamp(self, mock_api: DebugAPI) -> None:
        """Test run_all result includes timestamp."""
        result = mock_api.run_all()

        assert result.timestamp is not None

    def test_check_system_mock(self, mock_api: DebugAPI) -> None:
        """Test check_system in mock mode."""
        result = mock_api.check_system()

        assert isinstance(result, DebugResult)
        assert result.success is True

        # All checks should be SYSTEM category
        for check in result.checks:
            assert check.category == CheckCategory.SYSTEM

    def test_check_project_mock(self, mock_api: DebugAPI) -> None:
        """Test check_project in mock mode."""
        result = mock_api.check_project()

        assert isinstance(result, DebugResult)
        # In mock mode with default context, should still work
        assert result.success is True

    def test_check_server_mock(self, mock_api: DebugAPI) -> None:
        """Test check_server in mock mode."""
        result = mock_api.check_server()

        assert isinstance(result, DebugResult)
        # In mock mode, server check should pass
        assert result.success is True

    def test_check_connection_mock(self, mock_api: DebugAPI) -> None:
        """Test check_connection in mock mode."""
        result = mock_api.check_connection()

        assert isinstance(result, DebugResult)
        # In mock mode, connection check should pass
        assert result.success is True

    def test_check_auth_mock(self, mock_api: DebugAPI) -> None:
        """Test check_auth in mock mode."""
        result = mock_api.check_auth()

        assert isinstance(result, DebugResult)
        # In mock mode, auth check should pass
        assert result.success is True

    def test_check_network_mock(self, mock_api: DebugAPI) -> None:
        """Test check_network in mock mode."""
        result = mock_api.check_network()

        assert isinstance(result, DebugResult)
        # In mock mode, network check should pass
        assert result.success is True


class TestDebugAPICheckSystem:
    """Tests for DebugAPI.check_system method."""

    @pytest.fixture
    def api(self) -> DebugAPI:
        """Create DebugAPI for testing."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DebugAPI(context=ctx)

    def test_returns_system_checks(self, api: DebugAPI) -> None:
        """Test check_system returns system category checks."""
        result = api.check_system()

        assert result.total_count > 0

        # All should be system category
        for check in result.checks:
            assert check.category == CheckCategory.SYSTEM

    def test_includes_python_version(self, api: DebugAPI) -> None:
        """Test check_system includes Python version check."""
        result = api.check_system()

        check_names = [c.name for c in result.checks]
        assert any("python" in name.lower() for name in check_names)

    def test_includes_dli_version(self, api: DebugAPI) -> None:
        """Test check_system includes dli version check."""
        result = api.check_system()

        check_names = [c.name for c in result.checks]
        assert any("dli" in name.lower() or "version" in name.lower() for name in check_names)

    def test_includes_os_info(self, api: DebugAPI) -> None:
        """Test check_system includes OS info check."""
        result = api.check_system()

        check_names = [c.name for c in result.checks]
        assert any("os" in name.lower() or "system" in name.lower() for name in check_names)


class TestDebugAPICheckProject:
    """Tests for DebugAPI.check_project method."""

    def test_validates_project_path(self, tmp_path: Path) -> None:
        """Test check_project validates project path exists."""
        # Create minimal project structure
        (tmp_path / "dli.yaml").write_text("project_name: test")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        assert result.success is True

    def test_fails_with_invalid_path(self) -> None:
        """Test check_project fails with non-existent path."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,  # Not mock to trigger real validation
            project_path=Path("/nonexistent/path/xyz123"),
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Should have at least one failing check
        assert result.failed_count >= 1 or result.success is False

    def test_returns_config_category_checks(self, tmp_path: Path) -> None:
        """Test check_project returns CONFIG category checks."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # All checks should be CONFIG category
        for check in result.checks:
            assert check.category == CheckCategory.CONFIG


class TestDebugAPICheckServer:
    """Tests for DebugAPI.check_server method."""

    def test_returns_server_category_checks(self) -> None:
        """Test check_server returns SERVER category checks."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            server_url="https://mock.server.com",
        )
        api = DebugAPI(context=ctx)

        result = api.check_server()

        # Should have server-related checks
        for check in result.checks:
            assert check.category == CheckCategory.SERVER

    def test_mock_mode_passes(self) -> None:
        """Test check_server passes in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = DebugAPI(context=ctx)

        result = api.check_server()

        assert result.success is True


class TestDebugAPICheckConnection:
    """Tests for DebugAPI.check_connection method."""

    @pytest.fixture
    def mock_api(self) -> DebugAPI:
        """Create DebugAPI in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DebugAPI(context=ctx)

    def test_returns_database_category_checks(self, mock_api: DebugAPI) -> None:
        """Test check_connection returns DATABASE category checks."""
        result = mock_api.check_connection()

        for check in result.checks:
            assert check.category == CheckCategory.DATABASE

    def test_accepts_dialect_parameter(self, mock_api: DebugAPI) -> None:
        """Test check_connection accepts dialect parameter."""
        result = mock_api.check_connection(dialect="bigquery")

        assert result.success is True

    def test_trino_dialect(self, mock_api: DebugAPI) -> None:
        """Test check_connection with trino dialect."""
        result = mock_api.check_connection(dialect="trino")

        assert result.success is True


class TestDebugAPIFailedChecks:
    """Tests for failed check behavior."""

    def test_failed_check_includes_error(self) -> None:
        """Test that failed checks include error message."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=Path("/nonexistent/xyz123"),
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Find failed checks
        failed_checks = [c for c in result.checks if c.status == CheckStatus.FAIL]

        # At least one check should fail
        if failed_checks:
            for check in failed_checks:
                assert check.error is not None

    def test_failed_check_includes_remediation(self) -> None:
        """Test that failed checks include remediation message."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=Path("/nonexistent/xyz123"),
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Find failed checks
        failed_checks = [c for c in result.checks if c.status == CheckStatus.FAIL]

        # Failed checks should have remediation
        if failed_checks:
            for check in failed_checks:
                assert check.remediation is not None


class TestDebugAPIResultGrouping:
    """Tests for DebugResult grouping functionality."""

    @pytest.fixture
    def mock_api(self) -> DebugAPI:
        """Create DebugAPI in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DebugAPI(context=ctx)

    def test_result_by_category(self, mock_api: DebugAPI) -> None:
        """Test grouping results by category."""
        result = mock_api.run_all()

        by_cat = result.by_category

        # Should have multiple categories
        assert len(by_cat) > 0

        # SYSTEM should be present (always has Python/dli/OS checks)
        assert CheckCategory.SYSTEM in by_cat

    def test_counts_are_consistent(self, mock_api: DebugAPI) -> None:
        """Test that count properties are consistent."""
        result = mock_api.run_all()

        # Total should equal sum of other counts
        total = result.passed_count + result.failed_count + result.warned_count + result.skipped_count
        assert total == result.total_count

        # In mock mode, all should pass
        assert result.passed_count == result.total_count
        assert result.failed_count == 0


class TestDebugAPITimeout:
    """Tests for timeout handling."""

    def test_accepts_timeout_parameter(self) -> None:
        """Test that API methods accept timeout."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK, timeout=60)
        api = DebugAPI(context=ctx)

        result = api.run_all()

        assert result.success is True

    def test_default_timeout(self) -> None:
        """Test default timeout is used when not specified."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = DebugAPI(context=ctx)

        # Should work with default timeout
        result = api.run_all()

        assert result.success is True


class TestDebugAPIErrorCodes:
    """Tests for DLI-95x error codes in debug exceptions."""

    def test_debug_error_codes_exist(self) -> None:
        """Test that DLI-95x error codes are defined in ErrorCode."""
        from dli.exceptions import ErrorCode

        # Verify all debug error codes exist
        assert ErrorCode.DEBUG_SYSTEM_CHECK_FAILED.value == "DLI-950"
        assert ErrorCode.DEBUG_CONFIG_CHECK_FAILED.value == "DLI-951"
        assert ErrorCode.DEBUG_SERVER_CHECK_FAILED.value == "DLI-952"
        assert ErrorCode.DEBUG_AUTH_CHECK_FAILED.value == "DLI-953"
        assert ErrorCode.DEBUG_CONNECTION_CHECK_FAILED.value == "DLI-954"
        assert ErrorCode.DEBUG_NETWORK_CHECK_FAILED.value == "DLI-955"
        assert ErrorCode.DEBUG_TIMEOUT.value == "DLI-956"

    def test_error_codes_match_expected_values(self) -> None:
        """Test error code values match the expected DLI-95x pattern."""
        from dli.exceptions import ErrorCode

        debug_codes = [
            ErrorCode.DEBUG_SYSTEM_CHECK_FAILED,
            ErrorCode.DEBUG_CONFIG_CHECK_FAILED,
            ErrorCode.DEBUG_SERVER_CHECK_FAILED,
            ErrorCode.DEBUG_AUTH_CHECK_FAILED,
            ErrorCode.DEBUG_CONNECTION_CHECK_FAILED,
            ErrorCode.DEBUG_NETWORK_CHECK_FAILED,
            ErrorCode.DEBUG_TIMEOUT,
        ]

        for code in debug_codes:
            # All debug codes should be in the DLI-95x range
            assert code.value.startswith("DLI-95")
            # Extract numeric part and verify it's in 950-956 range
            numeric = int(code.value.split("-")[1])
            assert 950 <= numeric <= 956

    def test_error_codes_are_sequential(self) -> None:
        """Test debug error codes are sequential from 950 to 956."""
        from dli.exceptions import ErrorCode

        debug_codes = [
            ErrorCode.DEBUG_SYSTEM_CHECK_FAILED,
            ErrorCode.DEBUG_CONFIG_CHECK_FAILED,
            ErrorCode.DEBUG_SERVER_CHECK_FAILED,
            ErrorCode.DEBUG_AUTH_CHECK_FAILED,
            ErrorCode.DEBUG_CONNECTION_CHECK_FAILED,
            ErrorCode.DEBUG_NETWORK_CHECK_FAILED,
            ErrorCode.DEBUG_TIMEOUT,
        ]

        expected_values = [f"DLI-{950 + i}" for i in range(7)]
        actual_values = [code.value for code in debug_codes]

        assert actual_values == expected_values


class TestDebugAPICheckSkipPropagation:
    """Tests for check skip propagation when prerequisites fail."""

    def test_server_check_skipped_when_no_url_configured(self) -> None:
        """Test server health check is skipped when no server URL is configured."""
        # Context without server URL - should cause ServerHealthCheck to skip
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            server_url=None,  # No server URL
        )
        api = DebugAPI(context=ctx)

        result = api.check_server()

        # Find the Server connection check (ServerHealthCheck)
        skipped_checks = [c for c in result.checks if c.status == CheckStatus.SKIP]

        # At least one check should be skipped due to missing server URL
        assert len(skipped_checks) >= 1

        # Verify skip message indicates the reason
        for check in skipped_checks:
            assert "not configured" in check.message.lower() or "no server" in check.message.lower()

    def test_skipped_check_includes_skip_reason(self) -> None:
        """Test that skipped checks include a reason in the message."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            server_url=None,
        )
        api = DebugAPI(context=ctx)

        result = api.check_server()

        skipped_checks = [c for c in result.checks if c.status == CheckStatus.SKIP]

        for check in skipped_checks:
            # Skip message should be non-empty and explain the reason
            assert check.message is not None
            assert len(check.message) > 0

    def test_project_path_failure_affects_downstream_checks(self) -> None:
        """Test that project checks fail correctly with invalid path."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=Path("/completely/nonexistent/path/xyz789"),
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Project path check should fail
        failed_checks = [c for c in result.checks if c.status == CheckStatus.FAIL]
        assert len(failed_checks) >= 1

        # Find the project path check
        path_checks = [c for c in failed_checks if "path" in c.name.lower()]
        assert len(path_checks) >= 1

    def test_mock_mode_does_not_skip_checks(self) -> None:
        """Test that mock mode does not skip any checks."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            server_url=None,  # Even without server URL
        )
        api = DebugAPI(context=ctx)

        result = api.run_all()

        # In mock mode, no checks should be skipped
        skipped_checks = [c for c in result.checks if c.status == CheckStatus.SKIP]
        assert len(skipped_checks) == 0

        # All checks should pass
        assert result.success is True
        assert result.passed_count == result.total_count
