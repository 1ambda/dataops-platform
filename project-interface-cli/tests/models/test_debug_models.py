"""Tests for debug models (CheckStatus, CheckCategory, CheckResult, DebugResult).

Covers:
- CheckStatus enum values and behavior
- CheckCategory enum values
- CheckResult creation with all field combinations
- DebugResult computed properties (passed_count, failed_count, total_count, by_category)
- DebugResult.success reflects actual check statuses
"""

from __future__ import annotations

from datetime import UTC, datetime

import pytest
from pydantic import ValidationError

from dli.core.debug.models import (
    CheckCategory,
    CheckResult,
    CheckStatus,
    DebugResult,
)


class TestCheckStatus:
    """Tests for CheckStatus enum."""

    def test_enum_values(self) -> None:
        """Test CheckStatus enum has expected values."""
        assert CheckStatus.PASS.value == "pass"
        assert CheckStatus.FAIL.value == "fail"
        assert CheckStatus.WARN.value == "warn"
        assert CheckStatus.SKIP.value == "skip"

    def test_all_values_present(self) -> None:
        """Test all CheckStatus values are present."""
        values = [e.value for e in CheckStatus]
        assert len(values) == 4
        assert "pass" in values
        assert "fail" in values
        assert "warn" in values
        assert "skip" in values

    def test_string_inheritance(self) -> None:
        """Test CheckStatus inherits from str for easy comparison."""
        assert CheckStatus.PASS == "pass"
        assert CheckStatus.FAIL == "fail"

    def test_from_string(self) -> None:
        """Test creating CheckStatus from string value."""
        assert CheckStatus("pass") == CheckStatus.PASS
        assert CheckStatus("fail") == CheckStatus.FAIL
        assert CheckStatus("warn") == CheckStatus.WARN
        assert CheckStatus("skip") == CheckStatus.SKIP

    def test_invalid_value_raises_error(self) -> None:
        """Test invalid CheckStatus value raises ValueError."""
        with pytest.raises(ValueError):
            CheckStatus("invalid")


class TestCheckCategory:
    """Tests for CheckCategory enum."""

    def test_enum_values(self) -> None:
        """Test CheckCategory enum has expected values."""
        assert CheckCategory.SYSTEM.value == "system"
        assert CheckCategory.CONFIG.value == "config"
        assert CheckCategory.SERVER.value == "server"
        assert CheckCategory.AUTH.value == "auth"
        assert CheckCategory.DATABASE.value == "database"
        assert CheckCategory.NETWORK.value == "network"

    def test_all_values_present(self) -> None:
        """Test all CheckCategory values are present."""
        values = [e.value for e in CheckCategory]
        assert len(values) == 6
        assert "system" in values
        assert "config" in values
        assert "server" in values
        assert "auth" in values
        assert "database" in values
        assert "network" in values

    def test_string_inheritance(self) -> None:
        """Test CheckCategory inherits from str."""
        assert CheckCategory.SYSTEM == "system"
        assert CheckCategory.DATABASE == "database"

    def test_from_string(self) -> None:
        """Test creating CheckCategory from string value."""
        assert CheckCategory("system") == CheckCategory.SYSTEM
        assert CheckCategory("database") == CheckCategory.DATABASE


class TestCheckResult:
    """Tests for CheckResult model."""

    def test_create_minimal(self) -> None:
        """Test creating CheckResult with required fields only."""
        result = CheckResult(
            name="Python version",
            category=CheckCategory.SYSTEM,
            status=CheckStatus.PASS,
            message="Python 3.12.1",
        )

        assert result.name == "Python version"
        assert result.category == CheckCategory.SYSTEM
        assert result.status == CheckStatus.PASS
        assert result.message == "Python 3.12.1"
        assert result.details is None
        assert result.error is None
        assert result.remediation is None
        assert result.duration_ms == 0

    def test_create_with_all_fields(self) -> None:
        """Test creating CheckResult with all fields specified."""
        result = CheckResult(
            name="BigQuery connection",
            category=CheckCategory.DATABASE,
            status=CheckStatus.FAIL,
            message="Connection failed",
            details={"project": "my-project", "latency_ms": 120},
            error="Connection timeout after 30s",
            remediation="Check network connectivity and credentials",
            duration_ms=30000,
        )

        assert result.name == "BigQuery connection"
        assert result.category == CheckCategory.DATABASE
        assert result.status == CheckStatus.FAIL
        assert result.message == "Connection failed"
        assert result.details == {"project": "my-project", "latency_ms": 120}
        assert result.error == "Connection timeout after 30s"
        assert result.remediation == "Check network connectivity and credentials"
        assert result.duration_ms == 30000

    def test_create_pass_result(self) -> None:
        """Test creating a passing CheckResult."""
        result = CheckResult(
            name="dli version",
            category=CheckCategory.SYSTEM,
            status=CheckStatus.PASS,
            message="0.7.0",
            details={"version": "0.7.0"},
            duration_ms=5,
        )

        assert result.status == CheckStatus.PASS
        assert result.error is None
        assert result.remediation is None

    def test_create_warn_result(self) -> None:
        """Test creating a warning CheckResult."""
        result = CheckResult(
            name="Token expiration",
            category=CheckCategory.AUTH,
            status=CheckStatus.WARN,
            message="Token expires in 5 minutes",
            details={"expires_in": 300},
        )

        assert result.status == CheckStatus.WARN

    def test_create_skip_result(self) -> None:
        """Test creating a skipped CheckResult."""
        result = CheckResult(
            name="Test query",
            category=CheckCategory.DATABASE,
            status=CheckStatus.SKIP,
            message="Skipped (connection failed)",
        )

        assert result.status == CheckStatus.SKIP

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = CheckResult(
            name="Test check",
            category=CheckCategory.SYSTEM,
            status=CheckStatus.PASS,
            message="All good",
            details={"key": "value"},
            duration_ms=100,
        )

        json_str = original.model_dump_json()
        restored = CheckResult.model_validate_json(json_str)

        assert restored.name == original.name
        assert restored.category == original.category
        assert restored.status == original.status
        assert restored.message == original.message
        assert restored.details == original.details
        assert restored.duration_ms == original.duration_ms

    def test_required_fields_validation(self) -> None:
        """Test that required fields are validated."""
        with pytest.raises(ValidationError):
            CheckResult()  # type: ignore[call-arg]

        with pytest.raises(ValidationError):
            CheckResult(name="test")  # type: ignore[call-arg]

    def test_duration_ms_default(self) -> None:
        """Test duration_ms defaults to 0."""
        result = CheckResult(
            name="Test",
            category=CheckCategory.SYSTEM,
            status=CheckStatus.PASS,
            message="OK",
        )
        assert result.duration_ms == 0


class TestDebugResult:
    """Tests for DebugResult model."""

    @pytest.fixture
    def sample_checks(self) -> list[CheckResult]:
        """Create sample check results for testing."""
        return [
            CheckResult(
                name="Python version",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="Python 3.12.1",
            ),
            CheckResult(
                name="dli version",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="0.7.0",
            ),
            CheckResult(
                name="Config file",
                category=CheckCategory.CONFIG,
                status=CheckStatus.PASS,
                message="Found ~/.dli/config.yaml",
            ),
            CheckResult(
                name="Server connection",
                category=CheckCategory.SERVER,
                status=CheckStatus.FAIL,
                message="Connection failed",
                error="Timeout",
                remediation="Check server URL",
            ),
            CheckResult(
                name="Test query",
                category=CheckCategory.DATABASE,
                status=CheckStatus.SKIP,
                message="Skipped",
            ),
        ]

    def test_create_minimal(self) -> None:
        """Test creating DebugResult with minimal fields."""
        result = DebugResult(
            version="0.7.0",
            success=True,
        )

        assert result.version == "0.7.0"
        assert result.success is True
        assert result.checks == []
        assert result.timestamp is not None

    def test_create_with_checks(self, sample_checks: list[CheckResult]) -> None:
        """Test creating DebugResult with check results."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        assert result.version == "0.7.0"
        assert result.success is False
        assert len(result.checks) == 5

    def test_timestamp_default(self) -> None:
        """Test timestamp has default value."""
        result = DebugResult(version="0.7.0", success=True)

        assert result.timestamp is not None
        assert isinstance(result.timestamp, datetime)
        # Timestamp should be recent (within last minute)
        now = datetime.now(UTC)
        diff = abs((now - result.timestamp).total_seconds())
        assert diff < 60

    def test_passed_count_property(self, sample_checks: list[CheckResult]) -> None:
        """Test passed_count computed property."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        # sample_checks has 3 PASS statuses
        assert result.passed_count == 3

    def test_failed_count_property(self, sample_checks: list[CheckResult]) -> None:
        """Test failed_count computed property."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        # sample_checks has 1 FAIL status
        assert result.failed_count == 1

    def test_total_count_property(self, sample_checks: list[CheckResult]) -> None:
        """Test total_count computed property."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        assert result.total_count == 5

    def test_warned_count_property(self) -> None:
        """Test warned_count computed property."""
        checks = [
            CheckResult(
                name="Test 1",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="OK",
            ),
            CheckResult(
                name="Test 2",
                category=CheckCategory.AUTH,
                status=CheckStatus.WARN,
                message="Warning",
            ),
            CheckResult(
                name="Test 3",
                category=CheckCategory.AUTH,
                status=CheckStatus.WARN,
                message="Another warning",
            ),
        ]

        result = DebugResult(version="0.7.0", success=True, checks=checks)
        assert result.warned_count == 2

    def test_skipped_count_property(self, sample_checks: list[CheckResult]) -> None:
        """Test skipped_count computed property."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        # sample_checks has 1 SKIP status
        assert result.skipped_count == 1

    def test_by_category_property(self, sample_checks: list[CheckResult]) -> None:
        """Test by_category computed property groups checks correctly."""
        result = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        by_cat = result.by_category

        # Check that categories are present
        assert CheckCategory.SYSTEM in by_cat
        assert CheckCategory.CONFIG in by_cat
        assert CheckCategory.SERVER in by_cat
        assert CheckCategory.DATABASE in by_cat

        # Check counts per category
        assert len(by_cat[CheckCategory.SYSTEM]) == 2
        assert len(by_cat[CheckCategory.CONFIG]) == 1
        assert len(by_cat[CheckCategory.SERVER]) == 1
        assert len(by_cat[CheckCategory.DATABASE]) == 1

    def test_by_category_empty_checks(self) -> None:
        """Test by_category with no checks."""
        result = DebugResult(version="0.7.0", success=True, checks=[])

        by_cat = result.by_category
        assert by_cat == {}

    def test_success_reflects_check_statuses_all_pass(self) -> None:
        """Test that success=True makes sense when all checks pass."""
        checks = [
            CheckResult(
                name="Test 1",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="OK",
            ),
            CheckResult(
                name="Test 2",
                category=CheckCategory.CONFIG,
                status=CheckStatus.PASS,
                message="OK",
            ),
        ]

        result = DebugResult(version="0.7.0", success=True, checks=checks)

        assert result.success is True
        assert result.failed_count == 0
        assert result.passed_count == 2

    def test_success_reflects_check_statuses_with_failure(self) -> None:
        """Test that success=False when any check fails."""
        checks = [
            CheckResult(
                name="Test 1",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="OK",
            ),
            CheckResult(
                name="Test 2",
                category=CheckCategory.CONFIG,
                status=CheckStatus.FAIL,
                message="Failed",
                error="Error",
                remediation="Fix it",
            ),
        ]

        result = DebugResult(version="0.7.0", success=False, checks=checks)

        assert result.success is False
        assert result.failed_count == 1

    def test_success_with_warnings_only(self) -> None:
        """Test success can be True even with warnings (no failures)."""
        checks = [
            CheckResult(
                name="Test 1",
                category=CheckCategory.SYSTEM,
                status=CheckStatus.PASS,
                message="OK",
            ),
            CheckResult(
                name="Test 2",
                category=CheckCategory.AUTH,
                status=CheckStatus.WARN,
                message="Token expires soon",
            ),
        ]

        # Warnings don't cause failure
        result = DebugResult(version="0.7.0", success=True, checks=checks)

        assert result.success is True
        assert result.failed_count == 0
        assert result.warned_count == 1

    def test_json_roundtrip(self, sample_checks: list[CheckResult]) -> None:
        """Test JSON serialization and deserialization."""
        original = DebugResult(
            version="0.7.0",
            success=False,
            checks=sample_checks,
        )

        json_str = original.model_dump_json()
        restored = DebugResult.model_validate_json(json_str)

        assert restored.version == original.version
        assert restored.success == original.success
        assert len(restored.checks) == len(original.checks)
        assert restored.passed_count == original.passed_count
        assert restored.failed_count == original.failed_count

    def test_empty_result(self) -> None:
        """Test DebugResult with empty checks list."""
        result = DebugResult(version="0.7.0", success=True, checks=[])

        assert result.passed_count == 0
        assert result.failed_count == 0
        assert result.total_count == 0
        assert result.by_category == {}


class TestDebugResultComputedProperties:
    """Additional tests for DebugResult computed property edge cases."""

    @pytest.mark.parametrize(
        "statuses,expected_passed,expected_failed",
        [
            ([CheckStatus.PASS], 1, 0),
            ([CheckStatus.FAIL], 0, 1),
            ([CheckStatus.PASS, CheckStatus.PASS], 2, 0),
            ([CheckStatus.FAIL, CheckStatus.FAIL], 0, 2),
            ([CheckStatus.PASS, CheckStatus.FAIL], 1, 1),
            ([CheckStatus.PASS, CheckStatus.WARN, CheckStatus.SKIP], 1, 0),
            ([CheckStatus.WARN, CheckStatus.SKIP], 0, 0),
        ],
    )
    def test_count_combinations(
        self,
        statuses: list[CheckStatus],
        expected_passed: int,
        expected_failed: int,
    ) -> None:
        """Test various combinations of check statuses."""
        checks = [
            CheckResult(
                name=f"Test {i}",
                category=CheckCategory.SYSTEM,
                status=status,
                message="Test",
            )
            for i, status in enumerate(statuses)
        ]

        result = DebugResult(
            version="0.7.0",
            success=(expected_failed == 0),
            checks=checks,
        )

        assert result.passed_count == expected_passed
        assert result.failed_count == expected_failed
        assert result.total_count == len(statuses)
