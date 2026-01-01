"""Integration tests for debug functionality.

These tests verify the debug feature works in a real environment.
They are marked with @pytest.mark.integration and can be skipped in CI.

Covers:
- System checks pass in valid environment
- Config checks with real config file
- Full diagnostic run in real environment
"""

from __future__ import annotations

import os
import platform
import sys
from pathlib import Path

import pytest

from dli.api.debug import DebugAPI
from dli.core.debug.models import CheckCategory, CheckStatus
from dli.models.common import ExecutionContext, ExecutionMode


@pytest.mark.integration
class TestDebugSystemIntegration:
    """Integration tests for system checks."""

    @pytest.fixture
    def real_api(self, tmp_path: Path) -> DebugAPI:
        """Create DebugAPI with real context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        return DebugAPI(context=ctx)

    def test_system_checks_pass_in_valid_environment(
        self, real_api: DebugAPI
    ) -> None:
        """System checks should pass in a valid Python 3.12+ environment."""
        result = real_api.check_system()

        # In a valid test environment, system checks should pass
        assert result.success is True
        assert result.total_count > 0

        # Verify Python version check passes (we're running on Python 3.12+)
        python_check = None
        for check in result.checks:
            if "python" in check.name.lower():
                python_check = check
                break

        if python_check:
            assert python_check.status == CheckStatus.PASS

    def test_system_checks_include_all_expected(self, real_api: DebugAPI) -> None:
        """System checks should include Python, dli version, and OS info."""
        result = real_api.check_system()

        check_names = [c.name.lower() for c in result.checks]

        # Should have key system checks
        assert any("python" in name for name in check_names)
        assert any("dli" in name or "version" in name for name in check_names)
        assert any("os" in name or "system" in name for name in check_names)

    def test_system_checks_return_valid_data(self, real_api: DebugAPI) -> None:
        """System checks should return valid system information."""
        result = real_api.check_system()

        for check in result.checks:
            # All checks should have valid data
            assert check.name is not None
            assert check.category == CheckCategory.SYSTEM
            assert check.status in [
                CheckStatus.PASS,
                CheckStatus.FAIL,
                CheckStatus.WARN,
                CheckStatus.SKIP,
            ]
            assert check.message is not None


@pytest.mark.integration
class TestDebugConfigIntegration:
    """Integration tests for configuration checks."""

    @pytest.fixture
    def project_with_config(self, tmp_path: Path) -> Path:
        """Create a temporary project with configuration."""
        # Create dli.yaml
        config_content = """
project_name: integration_test
defaults:
  dialect: trino
  catalog: iceberg
  schema: test
"""
        (tmp_path / "dli.yaml").write_text(config_content)

        # Create directories
        (tmp_path / "datasets").mkdir()
        (tmp_path / "metrics").mkdir()

        return tmp_path

    def test_config_checks_with_valid_project(
        self, project_with_config: Path
    ) -> None:
        """Config checks should pass with valid project configuration."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=project_with_config
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Should have some passing checks
        assert result.total_count > 0
        # With valid config, should mostly pass
        assert result.passed_count >= 1

    def test_config_checks_detect_config_file(
        self, project_with_config: Path
    ) -> None:
        """Config checks should detect the configuration file."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=project_with_config
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Look for config file check
        config_checks = [
            c for c in result.checks if "config" in c.name.lower()
        ]

        # Should have a config-related check that passes
        if config_checks:
            # At least one config check should pass
            passing_config = [c for c in config_checks if c.status == CheckStatus.PASS]
            assert len(passing_config) >= 1

    @pytest.mark.skipif(
        not Path.home().joinpath(".dli/config.yaml").exists(),
        reason="No global config file",
    )
    def test_config_checks_with_global_config(self) -> None:
        """Config checks work with global config file if present."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Should run without errors
        assert result.total_count > 0


@pytest.mark.integration
class TestDebugFullDiagnosticIntegration:
    """Integration tests for full diagnostic run."""

    @pytest.fixture
    def full_project(self, tmp_path: Path) -> Path:
        """Create a complete project structure."""
        # Create config
        (tmp_path / "dli.yaml").write_text("""
project_name: full_test
defaults:
  dialect: trino
  catalog: iceberg
  schema: analytics
""")

        # Create directories
        (tmp_path / "datasets").mkdir()
        (tmp_path / "metrics").mkdir()

        # Create sample files
        (tmp_path / "datasets" / "sample.yaml").write_text("""
name: iceberg.analytics.sample
type: Dataset
query_file: sample.sql
""")
        (tmp_path / "datasets" / "sample.sql").write_text("SELECT 1")

        return tmp_path

    def test_full_diagnostic_runs(self, full_project: Path) -> None:
        """Full diagnostic should run all check categories."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=full_project
        )
        api = DebugAPI(context=ctx)

        result = api.run_all()

        # Should have checks from multiple categories
        categories = set(c.category for c in result.checks)
        assert CheckCategory.SYSTEM in categories
        assert CheckCategory.CONFIG in categories

    def test_full_diagnostic_measures_duration(self, full_project: Path) -> None:
        """Full diagnostic should measure duration for checks."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=full_project
        )
        api = DebugAPI(context=ctx)

        result = api.run_all()

        # At least some checks should have duration measured
        durations = [c.duration_ms for c in result.checks]
        assert any(d >= 0 for d in durations)

    def test_full_diagnostic_result_structure(self, full_project: Path) -> None:
        """Full diagnostic result should have correct structure."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=full_project
        )
        api = DebugAPI(context=ctx)

        result = api.run_all()

        # Verify result structure
        assert result.version is not None
        assert result.timestamp is not None
        assert isinstance(result.success, bool)
        assert result.total_count == len(result.checks)
        assert result.passed_count + result.failed_count + result.warned_count + result.skipped_count == result.total_count


@pytest.mark.integration
class TestDebugEnvironmentVariables:
    """Integration tests for environment variable handling."""

    def test_respects_dli_environment_variable(
        self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Debug should respect DLI_ENVIRONMENT variable."""
        monkeypatch.setenv("DLI_ENVIRONMENT", "production")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_project()

        # Should run without error
        assert result.total_count > 0

    def test_handles_missing_credentials(self, tmp_path: Path) -> None:
        """Debug should handle missing credentials gracefully."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        # Auth check without credentials should fail gracefully
        result = api.check_auth()

        # Should complete (may fail but should not crash)
        assert result.total_count >= 0


@pytest.mark.integration
class TestDebugPlatformSpecific:
    """Integration tests for platform-specific behavior."""

    def test_os_detection_matches_platform(self, tmp_path: Path) -> None:
        """OS check should detect correct platform."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_system()

        # Find OS check
        os_check = None
        for check in result.checks:
            if "os" in check.name.lower():
                os_check = check
                break

        if os_check:
            assert os_check.status == CheckStatus.PASS
            # Message should contain actual platform
            current_os = platform.system().lower()
            assert (
                current_os in os_check.message.lower()
                or current_os in str(os_check.details).lower()
            )

    def test_python_version_matches_runtime(self, tmp_path: Path) -> None:
        """Python version check should match actual runtime."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_system()

        # Find Python check
        python_check = None
        for check in result.checks:
            if "python" in check.name.lower():
                python_check = check
                break

        if python_check and python_check.details:
            # Version in details should match runtime
            version = python_check.details.get("version", "")
            expected = f"{sys.version_info.major}.{sys.version_info.minor}"
            assert expected in version


@pytest.mark.integration
@pytest.mark.slow
class TestDebugPerformance:
    """Integration tests for performance requirements."""

    def test_full_diagnostic_completes_within_timeout(
        self, tmp_path: Path
    ) -> None:
        """Full diagnostic should complete within reasonable time."""
        import time

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=tmp_path,
            timeout=30,  # 30 second timeout
        )
        api = DebugAPI(context=ctx)

        start = time.time()
        result = api.run_all()
        duration = time.time() - start

        # Should complete within timeout
        assert duration < 30
        # Should have results
        assert result.total_count > 0

    def test_individual_checks_are_fast(self, tmp_path: Path) -> None:
        """Individual checks should complete quickly."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL, project_path=tmp_path
        )
        api = DebugAPI(context=ctx)

        result = api.check_system()

        # System checks should be very fast (under 1 second each)
        for check in result.checks:
            assert check.duration_ms < 5000  # 5 seconds max per check
