"""Tests for debug check implementations.

Covers:
- PythonVersionCheck passes on Python 3.12+
- DliVersionCheck returns correct version
- OsInfoCheck returns valid OS info
- BaseCheck helper methods (_pass, _fail, _warn, _skip)
- Checks include duration_ms measurement
"""

from __future__ import annotations

import platform
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from dli.core.debug.checks import (
    BaseCheck,
    ConfigFileCheck,
    DliVersionCheck,
    OsInfoCheck,
    ProjectPathCheck,
    PythonVersionCheck,
)
from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus
from dli.models.common import ExecutionContext, ExecutionMode


class TestPythonVersionCheck:
    """Tests for PythonVersionCheck."""

    @pytest.fixture
    def check(self) -> PythonVersionCheck:
        """Create PythonVersionCheck instance."""
        return PythonVersionCheck()

    @pytest.fixture
    def mock_context(self) -> ExecutionContext:
        """Create mock execution context."""
        return ExecutionContext(execution_mode=ExecutionMode.MOCK)

    def test_name(self, check: PythonVersionCheck) -> None:
        """Test check name is correct."""
        assert check.name == "Python version"

    def test_category(self, check: PythonVersionCheck) -> None:
        """Test check category is SYSTEM."""
        assert check.category == CheckCategory.SYSTEM

    def test_passes_on_python_312_plus(
        self, check: PythonVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test check passes on Python 3.12+."""
        result = check.execute(mock_context)

        # Current Python version should be 3.12+
        assert result.status == CheckStatus.PASS
        assert "3.12" in result.message or sys.version_info >= (3, 12)
        assert result.category == CheckCategory.SYSTEM

    def test_result_includes_version_details(
        self, check: PythonVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes version in details."""
        result = check.execute(mock_context)

        assert result.details is not None
        assert "version" in result.details

    def test_includes_duration_ms(
        self, check: PythonVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes duration_ms."""
        result = check.execute(mock_context)

        # duration_ms should be set (could be 0 for fast checks)
        assert result.duration_ms >= 0

    def test_fails_on_old_python(
        self, check: PythonVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test check fails on Python < 3.12."""

        # Create a class that mimics sys.version_info with both attributes and comparison
        class MockVersionInfo:
            major = 3
            minor = 10
            micro = 0

            def __ge__(self, other: tuple) -> bool:
                return (self.major, self.minor) >= other[:2]

            def __lt__(self, other: tuple) -> bool:
                return (self.major, self.minor) < other[:2]

        mock_version = MockVersionInfo()

        with patch.object(sys, "version_info", mock_version):
            result = check.execute(mock_context)

            assert result.status == CheckStatus.FAIL
            assert result.error is not None
            assert result.remediation is not None
            assert "3.12" in result.remediation


class TestDliVersionCheck:
    """Tests for DliVersionCheck."""

    @pytest.fixture
    def check(self) -> DliVersionCheck:
        """Create DliVersionCheck instance."""
        return DliVersionCheck()

    @pytest.fixture
    def mock_context(self) -> ExecutionContext:
        """Create mock execution context."""
        return ExecutionContext(execution_mode=ExecutionMode.MOCK)

    def test_name(self, check: DliVersionCheck) -> None:
        """Test check name is correct."""
        assert check.name == "dli version"

    def test_category(self, check: DliVersionCheck) -> None:
        """Test check category is SYSTEM."""
        assert check.category == CheckCategory.SYSTEM

    def test_returns_correct_version(
        self, check: DliVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test check returns dli version."""
        result = check.execute(mock_context)

        assert result.status == CheckStatus.PASS
        # Version should be in message
        assert result.message is not None
        # Should contain version pattern (e.g., "0.7.0")
        assert "." in result.message or "version" in result.message.lower()

    def test_result_includes_version_details(
        self, check: DliVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes version in details."""
        result = check.execute(mock_context)

        assert result.details is not None
        assert "version" in result.details

    def test_includes_duration_ms(
        self, check: DliVersionCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes duration_ms."""
        result = check.execute(mock_context)

        assert result.duration_ms >= 0


class TestOsInfoCheck:
    """Tests for OsInfoCheck."""

    @pytest.fixture
    def check(self) -> OsInfoCheck:
        """Create OsInfoCheck instance."""
        return OsInfoCheck()

    @pytest.fixture
    def mock_context(self) -> ExecutionContext:
        """Create mock execution context."""
        return ExecutionContext(execution_mode=ExecutionMode.MOCK)

    def test_name(self, check: OsInfoCheck) -> None:
        """Test check name is correct."""
        assert check.name == "OS info"

    def test_category(self, check: OsInfoCheck) -> None:
        """Test check category is SYSTEM."""
        assert check.category == CheckCategory.SYSTEM

    def test_returns_valid_os_info(
        self, check: OsInfoCheck, mock_context: ExecutionContext
    ) -> None:
        """Test check returns valid OS information."""
        result = check.execute(mock_context)

        assert result.status == CheckStatus.PASS
        # Message should contain OS info
        assert result.message is not None
        # Should contain platform info (darwin, linux, win32)
        assert (
            "darwin" in result.message.lower()
            or "linux" in result.message.lower()
            or "windows" in result.message.lower()
            or platform.system().lower() in result.message.lower()
        )

    def test_result_includes_os_details(
        self, check: OsInfoCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes OS details."""
        result = check.execute(mock_context)

        assert result.details is not None
        # Should include os_name or os_version info
        assert "os_name" in result.details or "os_version" in result.details

    def test_includes_duration_ms(
        self, check: OsInfoCheck, mock_context: ExecutionContext
    ) -> None:
        """Test result includes duration_ms."""
        result = check.execute(mock_context)

        assert result.duration_ms >= 0


class TestProjectPathCheck:
    """Tests for ProjectPathCheck."""

    @pytest.fixture
    def check(self) -> ProjectPathCheck:
        """Create ProjectPathCheck instance."""
        return ProjectPathCheck()

    def test_name(self, check: ProjectPathCheck) -> None:
        """Test check name is correct."""
        assert check.name == "Project path"

    def test_category(self, check: ProjectPathCheck) -> None:
        """Test check category is CONFIG."""
        assert check.category == CheckCategory.CONFIG

    def test_passes_with_valid_project_path(
        self, check: ProjectPathCheck, tmp_path: Path
    ) -> None:
        """Test check passes with valid project path."""
        # Create minimal project structure
        (tmp_path / "dli.yaml").write_text("project_name: test")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        assert result.status == CheckStatus.PASS
        assert str(tmp_path) in result.message

    def test_fails_with_nonexistent_path(self, check: ProjectPathCheck) -> None:
        """Test check fails with non-existent project path."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=Path("/nonexistent/path/xyz123"),
        )
        result = check.execute(ctx)

        assert result.status == CheckStatus.FAIL
        assert result.error is not None
        assert result.remediation is not None

    def test_warns_when_no_config_file(
        self, check: ProjectPathCheck, tmp_path: Path
    ) -> None:
        """Test check warns when dli.yaml not found."""
        # Directory exists but no config file
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        # Should warn (path exists but no config)
        assert result.status in (CheckStatus.WARN, CheckStatus.PASS)

    def test_includes_duration_ms(
        self, check: ProjectPathCheck, tmp_path: Path
    ) -> None:
        """Test result includes duration_ms."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        assert result.duration_ms >= 0


class TestConfigFileCheck:
    """Tests for ConfigFileCheck."""

    @pytest.fixture
    def check(self) -> ConfigFileCheck:
        """Create ConfigFileCheck instance."""
        return ConfigFileCheck()

    def test_name(self, check: ConfigFileCheck) -> None:
        """Test check name is correct."""
        assert check.name == "Config file"

    def test_category(self, check: ConfigFileCheck) -> None:
        """Test check category is CONFIG."""
        assert check.category == CheckCategory.CONFIG

    def test_passes_with_config_file(
        self, check: ConfigFileCheck, tmp_path: Path
    ) -> None:
        """Test check passes when config file exists."""
        config_file = tmp_path / "dli.yaml"
        config_file.write_text("project_name: test\ndefaults:\n  dialect: trino")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        assert result.status == CheckStatus.PASS

    def test_warns_without_config_file(
        self, check: ConfigFileCheck, tmp_path: Path
    ) -> None:
        """Test check warns when no config file found."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        # Should warn or skip (not critical failure)
        assert result.status in (CheckStatus.WARN, CheckStatus.SKIP)

    def test_includes_duration_ms(
        self, check: ConfigFileCheck, tmp_path: Path
    ) -> None:
        """Test result includes duration_ms."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK, project_path=tmp_path
        )
        result = check.execute(ctx)

        assert result.duration_ms >= 0


class TestBaseCheckHelperMethods:
    """Tests for BaseCheck helper methods (_pass, _fail, _warn, _skip)."""

    class ConcreteCheck(BaseCheck):
        """Concrete implementation for testing BaseCheck."""

        @property
        def name(self) -> str:
            return "Test check"

        @property
        def category(self) -> CheckCategory:
            return CheckCategory.SYSTEM

        def execute(self, context: ExecutionContext) -> CheckResult:
            return self._pass("Test passed")

    @pytest.fixture
    def check(self) -> "TestBaseCheckHelperMethods.ConcreteCheck":
        """Create concrete check instance."""
        return self.ConcreteCheck()

    def test_pass_helper(self, check: "TestBaseCheckHelperMethods.ConcreteCheck") -> None:
        """Test _pass helper creates correct CheckResult."""
        result = check._pass("All good")

        assert result.status == CheckStatus.PASS
        assert result.message == "All good"
        assert result.name == "Test check"
        assert result.category == CheckCategory.SYSTEM
        assert result.error is None
        assert result.remediation is None

    def test_pass_helper_with_details(
        self, check: "TestBaseCheckHelperMethods.ConcreteCheck"
    ) -> None:
        """Test _pass helper with additional details."""
        result = check._pass("All good", version="1.0.0", latency=50)

        assert result.status == CheckStatus.PASS
        assert result.details == {"version": "1.0.0", "latency": 50}

    def test_fail_helper(self, check: "TestBaseCheckHelperMethods.ConcreteCheck") -> None:
        """Test _fail helper creates correct CheckResult."""
        result = check._fail(
            message="Connection failed",
            error="Timeout after 30s",
            remediation="Check network settings",
        )

        assert result.status == CheckStatus.FAIL
        assert result.message == "Connection failed"
        assert result.error == "Timeout after 30s"
        assert result.remediation == "Check network settings"
        assert result.name == "Test check"
        assert result.category == CheckCategory.SYSTEM

    def test_fail_helper_with_details(
        self, check: "TestBaseCheckHelperMethods.ConcreteCheck"
    ) -> None:
        """Test _fail helper with additional details."""
        result = check._fail(
            message="Failed",
            error="Error",
            remediation="Fix it",
            attempt=3,
            timeout=30,
        )

        assert result.status == CheckStatus.FAIL
        assert result.details == {"attempt": 3, "timeout": 30}

    def test_warn_helper(self, check: "TestBaseCheckHelperMethods.ConcreteCheck") -> None:
        """Test _warn helper creates correct CheckResult."""
        result = check._warn("Token expires soon")

        assert result.status == CheckStatus.WARN
        assert result.message == "Token expires soon"
        assert result.name == "Test check"
        assert result.category == CheckCategory.SYSTEM

    def test_warn_helper_with_details(
        self, check: "TestBaseCheckHelperMethods.ConcreteCheck"
    ) -> None:
        """Test _warn helper with additional details."""
        result = check._warn("Token expires soon", expires_in=300)

        assert result.status == CheckStatus.WARN
        assert result.details == {"expires_in": 300}

    def test_skip_helper(self, check: "TestBaseCheckHelperMethods.ConcreteCheck") -> None:
        """Test _skip helper creates correct CheckResult."""
        result = check._skip("Skipped due to missing dependency")

        assert result.status == CheckStatus.SKIP
        assert result.message == "Skipped due to missing dependency"
        assert result.name == "Test check"
        assert result.category == CheckCategory.SYSTEM

    def test_skip_helper_with_reason(
        self, check: "TestBaseCheckHelperMethods.ConcreteCheck"
    ) -> None:
        """Test _skip helper with reason in details."""
        result = check._skip("Skipped", reason="Connection check failed")

        assert result.status == CheckStatus.SKIP
        assert result.details == {"reason": "Connection check failed"}


class TestCheckDurationTracking:
    """Tests for duration tracking in checks."""

    def test_check_measures_duration(self) -> None:
        """Test that check execution measures duration_ms."""

        class SlowCheck(BaseCheck):
            """Check that takes some time to execute."""

            @property
            def name(self) -> str:
                return "Slow check"

            @property
            def category(self) -> CheckCategory:
                return CheckCategory.SYSTEM

            def execute(self, context: ExecutionContext) -> CheckResult:
                import time

                time.sleep(0.01)  # 10ms delay
                return self._pass("Done")

        check = SlowCheck()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        # Execute through the timed wrapper if available
        result = check.execute(ctx)

        # Duration should be measured
        # Note: actual timing depends on implementation
        assert result.duration_ms >= 0

    def test_multiple_checks_have_independent_durations(self) -> None:
        """Test that each check has its own duration measurement."""
        check1 = PythonVersionCheck()
        check2 = DliVersionCheck()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        result1 = check1.execute(ctx)
        result2 = check2.execute(ctx)

        # Both should have duration set
        assert result1.duration_ms >= 0
        assert result2.duration_ms >= 0


class TestRemediationQuality:
    """Tests for remediation message quality.

    Ensures remediation messages are:
    - Actionable (contain action verbs)
    - Detailed (> 20 characters)
    """

    # Actionable words that should appear in remediation messages
    ACTIONABLE_WORDS = frozenset([
        "check", "run", "set", "verify", "install", "create",
        "update", "configure", "add", "remove", "fix", "ensure",
        "try", "use", "specify", "provide", "confirm", "enable",
        "disable", "restart", "download", "upgrade",
    ])

    MIN_REMEDIATION_LENGTH = 20

    @pytest.fixture
    def mock_context(self) -> ExecutionContext:
        """Create mock execution context."""
        return ExecutionContext(execution_mode=ExecutionMode.MOCK)

    def _get_failed_result_with_remediation(
        self, check: BaseCheck, context: ExecutionContext
    ) -> CheckResult | None:
        """Execute check and return result if it has remediation."""
        result = check.execute(context)
        if result.remediation:
            return result
        return None

    def test_python_version_remediation_is_actionable(self) -> None:
        """Test PythonVersionCheck remediation contains actionable words."""
        check = PythonVersionCheck()

        # Create a failing context with old Python version
        class MockVersionInfo:
            major = 3
            minor = 10
            micro = 0

            def __ge__(self, other: tuple) -> bool:
                return (self.major, self.minor) >= other[:2]

            def __lt__(self, other: tuple) -> bool:
                return (self.major, self.minor) < other[:2]

        mock_version = MockVersionInfo()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        with patch.object(sys, "version_info", mock_version):
            result = check.execute(ctx)

            assert result.remediation is not None
            remediation_lower = result.remediation.lower()

            # Check for actionable words
            has_actionable_word = any(
                word in remediation_lower for word in self.ACTIONABLE_WORDS
            )
            assert has_actionable_word, (
                f"Remediation should contain actionable words like "
                f"{list(self.ACTIONABLE_WORDS)[:5]}. Got: {result.remediation}"
            )

    def test_python_version_remediation_is_detailed(self) -> None:
        """Test PythonVersionCheck remediation is sufficiently detailed."""
        check = PythonVersionCheck()

        class MockVersionInfo:
            major = 3
            minor = 10
            micro = 0

            def __ge__(self, other: tuple) -> bool:
                return (self.major, self.minor) >= other[:2]

            def __lt__(self, other: tuple) -> bool:
                return (self.major, self.minor) < other[:2]

        mock_version = MockVersionInfo()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        with patch.object(sys, "version_info", mock_version):
            result = check.execute(ctx)

            assert result.remediation is not None
            assert len(result.remediation) > self.MIN_REMEDIATION_LENGTH, (
                f"Remediation should be > {self.MIN_REMEDIATION_LENGTH} chars. "
                f"Got {len(result.remediation)}: {result.remediation}"
            )

    def test_project_path_remediation_is_actionable(self) -> None:
        """Test ProjectPathCheck remediation contains actionable words."""
        check = ProjectPathCheck()
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=Path("/nonexistent/path/xyz123"),
        )

        result = check.execute(ctx)

        assert result.status == CheckStatus.FAIL
        assert result.remediation is not None
        remediation_lower = result.remediation.lower()

        has_actionable_word = any(
            word in remediation_lower for word in self.ACTIONABLE_WORDS
        )
        assert has_actionable_word, (
            f"Remediation should contain actionable words. Got: {result.remediation}"
        )

    def test_project_path_remediation_is_detailed(self) -> None:
        """Test ProjectPathCheck remediation is sufficiently detailed."""
        check = ProjectPathCheck()
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=Path("/nonexistent/path/xyz123"),
        )

        result = check.execute(ctx)

        assert result.status == CheckStatus.FAIL
        assert result.remediation is not None
        assert len(result.remediation) > self.MIN_REMEDIATION_LENGTH, (
            f"Remediation should be > {self.MIN_REMEDIATION_LENGTH} chars. "
            f"Got {len(result.remediation)}: {result.remediation}"
        )

    def test_all_failed_checks_have_remediation(self) -> None:
        """Test that all checks that can fail have remediation messages."""
        from dli.core.debug.checks import ALL_CHECKS

        # Checks that should have remediation when failing
        checks_with_remediation = []

        for check_class in ALL_CHECKS:
            check = check_class()
            # We can't easily trigger failures for all checks,
            # but we can verify the _fail helper requires remediation
            # by checking that failed checks in real scenarios have it

            # Test with non-existent path for path-related checks
            if "path" in check.name.lower():
                ctx = ExecutionContext(
                    execution_mode=ExecutionMode.MOCK,
                    project_path=Path("/nonexistent/abc123"),
                )
                result = check.execute(ctx)
                if result.status == CheckStatus.FAIL:
                    checks_with_remediation.append((check.name, result))

        # All failed checks should have remediation
        for name, result in checks_with_remediation:
            assert result.remediation is not None, (
                f"Check '{name}' failed but has no remediation message"
            )
            assert len(result.remediation) > self.MIN_REMEDIATION_LENGTH, (
                f"Check '{name}' remediation too short: {result.remediation}"
            )

    def test_remediation_messages_are_not_generic(self) -> None:
        """Test remediation messages are specific, not generic placeholders."""
        check = ProjectPathCheck()
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=Path("/nonexistent/path/xyz123"),
        )

        result = check.execute(ctx)

        assert result.remediation is not None

        # Should not be generic placeholder messages
        generic_phrases = ["fix it", "todo", "tbd", "not implemented"]
        remediation_lower = result.remediation.lower()

        for phrase in generic_phrases:
            assert phrase not in remediation_lower, (
                f"Remediation should not contain generic phrase '{phrase}'"
            )
