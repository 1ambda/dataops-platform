"""Tests for Quality Data Models.

This module tests the core quality data models including
DqTestDefinition, DqTestResult, QualityReport, and related enums.
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

import pytest

from dli.core.quality.models import (
    DqSeverity,
    DqStatus,
    DqTestConfig,
    DqTestDefinition,
    DqTestResult,
    DqTestType,
    QualityReport,
)


# =============================================================================
# Enum Tests
# =============================================================================


class TestDqTestTypeEnum:
    """Tests for DqTestType enum."""

    def test_all_types_defined(self) -> None:
        """All expected test types should be defined."""
        expected_types = {
            "NOT_NULL",
            "UNIQUE",
            "ACCEPTED_VALUES",
            "RELATIONSHIPS",
            "RANGE_CHECK",
            "ROW_COUNT",
            "SINGULAR",
        }
        actual_types = {t.name for t in DqTestType}
        assert actual_types == expected_types

    def test_type_values(self) -> None:
        """Test type values should be lowercase strings."""
        assert DqTestType.NOT_NULL.value == "not_null"
        assert DqTestType.UNIQUE.value == "unique"
        assert DqTestType.ACCEPTED_VALUES.value == "accepted_values"
        assert DqTestType.RELATIONSHIPS.value == "relationships"
        assert DqTestType.RANGE_CHECK.value == "range_check"
        assert DqTestType.ROW_COUNT.value == "row_count"
        assert DqTestType.SINGULAR.value == "singular"

    def test_type_is_string_enum(self) -> None:
        """DqTestType should be a string enum."""
        assert isinstance(DqTestType.NOT_NULL.value, str)
        # String enum comparison
        assert DqTestType.NOT_NULL == "not_null"


class TestDqSeverityEnum:
    """Tests for DqSeverity enum."""

    def test_severity_values(self) -> None:
        """Severity values should be error and warn."""
        assert DqSeverity.ERROR.value == "error"
        assert DqSeverity.WARN.value == "warn"

    def test_severity_is_string_enum(self) -> None:
        """DqSeverity should be a string enum."""
        assert DqSeverity.ERROR == "error"
        assert DqSeverity.WARN == "warn"


class TestDqStatusEnum:
    """Tests for DqStatus enum."""

    def test_status_values(self) -> None:
        """All status values should be defined correctly."""
        assert DqStatus.PASS.value == "pass"
        assert DqStatus.FAIL.value == "fail"
        assert DqStatus.WARN.value == "warn"
        assert DqStatus.ERROR.value == "error"
        assert DqStatus.SKIPPED.value == "skipped"

    def test_status_from_string(self) -> None:
        """Status should be creatable from string."""
        assert DqStatus("pass") == DqStatus.PASS
        assert DqStatus("fail") == DqStatus.FAIL


# =============================================================================
# DqTestDefinition Tests
# =============================================================================


class TestDqTestDefinitionCreation:
    """Tests for DqTestDefinition creation."""

    def test_minimal_definition(self) -> None:
        """Minimal definition with required fields only."""
        test = DqTestDefinition(
            name="my_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
        )
        assert test.name == "my_test"
        assert test.test_type == DqTestType.NOT_NULL
        assert test.resource_name == "users"
        assert test.enabled is True
        assert test.severity == DqSeverity.ERROR

    def test_definition_with_columns(self) -> None:
        """Definition with columns specified."""
        test = DqTestDefinition(
            name="not_null_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email", "name"],
        )
        assert test.columns == ["email", "name"]

    def test_definition_with_params(self) -> None:
        """Definition with additional params."""
        test = DqTestDefinition(
            name="accepted_values_test",
            test_type=DqTestType.ACCEPTED_VALUES,
            resource_name="users",
            columns=["status"],
            params={"values": ["active", "inactive"]},
        )
        assert test.params["values"] == ["active", "inactive"]

    def test_definition_with_warn_severity(self) -> None:
        """Definition with WARN severity."""
        test = DqTestDefinition(
            name="warn_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            severity=DqSeverity.WARN,
        )
        assert test.severity == DqSeverity.WARN

    def test_definition_disabled(self) -> None:
        """Disabled test definition."""
        test = DqTestDefinition(
            name="disabled_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            enabled=False,
        )
        assert test.enabled is False

    def test_definition_with_description(self) -> None:
        """Definition with description."""
        test = DqTestDefinition(
            name="documented_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            description="This test checks for NULL values",
        )
        assert test.description == "This test checks for NULL values"

    def test_singular_with_sql(self) -> None:
        """SINGULAR test with inline SQL."""
        test = DqTestDefinition(
            name="custom_test",
            test_type=DqTestType.SINGULAR,
            resource_name="users",
            sql="SELECT * FROM users WHERE created_at > NOW()",
        )
        assert test.sql == "SELECT * FROM users WHERE created_at > NOW()"

    def test_singular_with_file(self) -> None:
        """SINGULAR test with file reference."""
        test = DqTestDefinition(
            name="file_test",
            test_type=DqTestType.SINGULAR,
            resource_name="users",
            file="tests/freshness.sql",
        )
        assert test.file == "tests/freshness.sql"


class TestDqTestDefinitionNameGeneration:
    """Tests for automatic name generation."""

    def test_auto_generate_name_with_columns(self) -> None:
        """Name should be generated from type and columns."""
        test = DqTestDefinition(
            name="",  # Empty name triggers generation
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email", "name"],
        )
        # Name generated in __post_init__
        assert test.name == "not_null_email_name"

    def test_auto_generate_name_many_columns(self) -> None:
        """Name with many columns should be truncated."""
        test = DqTestDefinition(
            name="",
            test_type=DqTestType.UNIQUE,
            resource_name="users",
            columns=["a", "b", "c", "d", "e"],  # More than 3
        )
        assert test.name == "unique_a_b_c_etc"

    def test_auto_generate_name_no_columns(self) -> None:
        """Name with no columns should use type only."""
        test = DqTestDefinition(
            name="",
            test_type=DqTestType.ROW_COUNT,
            resource_name="users",
        )
        assert test.name == "row_count"


class TestDqTestDefinitionFromYaml:
    """Tests for from_yaml class method."""

    def test_from_yaml_basic(self) -> None:
        """Basic YAML parsing."""
        data: dict[str, Any] = {
            "type": "not_null",
            "columns": ["email"],
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.test_type == DqTestType.NOT_NULL
        assert test.resource_name == "users"
        assert test.columns == ["email"]

    def test_from_yaml_with_name(self) -> None:
        """YAML with explicit name."""
        data: dict[str, Any] = {
            "type": "not_null",
            "name": "email_required",
            "columns": ["email"],
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.name == "email_required"

    def test_from_yaml_single_column(self) -> None:
        """YAML with single column (not list)."""
        data: dict[str, Any] = {
            "type": "not_null",
            "column": "email",  # Single column key
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.columns == ["email"]

    def test_from_yaml_columns_as_string(self) -> None:
        """YAML with columns as single string."""
        data: dict[str, Any] = {
            "type": "not_null",
            "columns": "email",  # String instead of list
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.columns == ["email"]

    def test_from_yaml_with_severity(self) -> None:
        """YAML with severity specified."""
        data: dict[str, Any] = {
            "type": "not_null",
            "columns": ["optional_field"],
            "severity": "warn",
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.severity == DqSeverity.WARN

    def test_from_yaml_with_params(self) -> None:
        """YAML with additional params."""
        data: dict[str, Any] = {
            "type": "accepted_values",
            "column": "status",
            "values": ["active", "inactive"],
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.params["values"] == ["active", "inactive"]

    def test_from_yaml_relationships(self) -> None:
        """YAML for relationships test."""
        data: dict[str, Any] = {
            "type": "relationships",
            "column": "user_id",
            "to": "users",
            "to_column": "id",
        }
        test = DqTestDefinition.from_yaml(data, "orders")

        assert test.test_type == DqTestType.RELATIONSHIPS
        assert test.columns == ["user_id"]
        assert test.params["to"] == "users"
        assert test.params["to_column"] == "id"

    def test_from_yaml_range_check(self) -> None:
        """YAML for range_check test."""
        data: dict[str, Any] = {
            "type": "range_check",
            "column": "quantity",
            "min": 0,
            "max": 1000,
        }
        test = DqTestDefinition.from_yaml(data, "orders")

        assert test.test_type == DqTestType.RANGE_CHECK
        assert test.params["min"] == 0
        assert test.params["max"] == 1000

    def test_from_yaml_singular_with_sql(self) -> None:
        """YAML for singular test with inline SQL."""
        data: dict[str, Any] = {
            "type": "singular",
            "name": "freshness_check",
            "sql": "SELECT * FROM users WHERE updated_at < NOW() - INTERVAL 1 HOUR",
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.test_type == DqTestType.SINGULAR
        assert "updated_at" in test.sql

    def test_from_yaml_singular_with_file(self) -> None:
        """YAML for singular test with file reference."""
        data: dict[str, Any] = {
            "type": "singular",
            "name": "custom_check",
            "file": "tests/custom.sql",
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.file == "tests/custom.sql"

    def test_from_yaml_disabled(self) -> None:
        """YAML with enabled=false."""
        data: dict[str, Any] = {
            "type": "not_null",
            "columns": ["email"],
            "enabled": False,
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.enabled is False

    def test_from_yaml_with_description(self) -> None:
        """YAML with description."""
        data: dict[str, Any] = {
            "type": "not_null",
            "columns": ["email"],
            "description": "Email should never be null",
        }
        test = DqTestDefinition.from_yaml(data, "users")

        assert test.description == "Email should never be null"

    def test_from_yaml_invalid_type(self) -> None:
        """Invalid test type should raise ValueError."""
        data: dict[str, Any] = {
            "type": "invalid_type",
            "columns": ["email"],
        }
        with pytest.raises(ValueError, match="Invalid test type"):
            DqTestDefinition.from_yaml(data, "users")


# =============================================================================
# DqTestResult Tests
# =============================================================================


class TestDqTestResultCreation:
    """Tests for DqTestResult creation."""

    def test_minimal_result(self) -> None:
        """Minimal result with required fields only."""
        result = DqTestResult(
            test_name="my_test",
            resource_name="users",
            status=DqStatus.PASS,
        )
        assert result.test_name == "my_test"
        assert result.resource_name == "users"
        assert result.status == DqStatus.PASS
        assert result.failed_rows == 0
        assert result.executed_on == "local"

    def test_result_with_failures(self) -> None:
        """Result with failed rows and samples."""
        result = DqTestResult(
            test_name="null_check",
            resource_name="users",
            status=DqStatus.FAIL,
            failed_rows=5,
            failed_samples=[{"id": 1, "email": None}, {"id": 2, "email": None}],
        )
        assert result.failed_rows == 5
        assert len(result.failed_samples) == 2

    def test_result_with_error(self) -> None:
        """Result with error status and message."""
        result = DqTestResult(
            test_name="connection_test",
            resource_name="users",
            status=DqStatus.ERROR,
            error_message="Connection refused",
        )
        assert result.status == DqStatus.ERROR
        assert result.error_message == "Connection refused"

    def test_result_with_execution_time(self) -> None:
        """Result with execution time."""
        result = DqTestResult(
            test_name="slow_test",
            resource_name="users",
            status=DqStatus.PASS,
            execution_time_ms=1500,
        )
        assert result.execution_time_ms == 1500

    def test_result_with_rendered_sql(self) -> None:
        """Result with rendered SQL."""
        result = DqTestResult(
            test_name="sql_test",
            resource_name="users",
            status=DqStatus.PASS,
            rendered_sql="SELECT * FROM users WHERE email IS NULL",
        )
        assert "email IS NULL" in result.rendered_sql

    def test_result_executed_at_defaults_to_now(self) -> None:
        """executed_at should default to current time."""
        before = datetime.now(UTC)
        result = DqTestResult(
            test_name="test",
            resource_name="users",
            status=DqStatus.PASS,
        )
        after = datetime.now(UTC)

        assert before <= result.executed_at <= after

    def test_result_server_execution(self) -> None:
        """Result from server execution."""
        result = DqTestResult(
            test_name="server_test",
            resource_name="users",
            status=DqStatus.PASS,
            executed_on="server",
        )
        assert result.executed_on == "server"


# =============================================================================
# DqTestConfig Tests
# =============================================================================


class TestDqTestConfigCreation:
    """Tests for DqTestConfig creation."""

    def test_default_config(self) -> None:
        """Default config values."""
        config = DqTestConfig()

        assert config.fail_fast is False
        assert config.severity == DqSeverity.ERROR
        assert config.limit == 100
        assert config.store_failures is True

    def test_custom_config(self) -> None:
        """Custom config values."""
        config = DqTestConfig(
            fail_fast=True,
            severity=DqSeverity.WARN,
            limit=50,
            store_failures=False,
        )

        assert config.fail_fast is True
        assert config.severity == DqSeverity.WARN
        assert config.limit == 50
        assert config.store_failures is False


# =============================================================================
# QualityReport Tests
# =============================================================================


class TestQualityReportCreation:
    """Tests for QualityReport creation."""

    def test_empty_report(self) -> None:
        """Empty report with defaults."""
        report = QualityReport(resource_name="users")

        assert report.resource_name == "users"
        assert report.total_tests == 0
        assert report.passed == 0
        assert report.failed == 0
        assert report.warned == 0
        assert report.errors == 0
        assert report.skipped == 0
        assert report.results == []
        assert report.executed_on == "local"

    def test_report_success_property(self) -> None:
        """success property should check failed and errors."""
        # All pass - success
        report = QualityReport(
            resource_name="users",
            total_tests=3,
            passed=3,
        )
        assert report.success is True

        # One failure - not success
        report = QualityReport(
            resource_name="users",
            total_tests=3,
            passed=2,
            failed=1,
        )
        assert report.success is False

        # One error - not success
        report = QualityReport(
            resource_name="users",
            total_tests=3,
            passed=2,
            errors=1,
        )
        assert report.success is False

        # Warnings are allowed for success
        report = QualityReport(
            resource_name="users",
            total_tests=3,
            passed=2,
            warned=1,
        )
        assert report.success is True


class TestQualityReportFromResults:
    """Tests for QualityReport.from_results class method."""

    def test_from_empty_results(self) -> None:
        """from_results with empty list."""
        report = QualityReport.from_results("users", [])

        assert report.total_tests == 0
        assert report.success is True

    def test_from_single_passing_result(self) -> None:
        """from_results with single passing result."""
        results = [
            DqTestResult(
                test_name="test1",
                resource_name="users",
                status=DqStatus.PASS,
                execution_time_ms=100,
            )
        ]
        report = QualityReport.from_results("users", results)

        assert report.total_tests == 1
        assert report.passed == 1
        assert report.success is True

    def test_from_multiple_results(self) -> None:
        """from_results with multiple results."""
        results = [
            DqTestResult(
                test_name="test1",
                resource_name="users",
                status=DqStatus.PASS,
                execution_time_ms=100,
            ),
            DqTestResult(
                test_name="test2",
                resource_name="users",
                status=DqStatus.FAIL,
                failed_rows=5,
                execution_time_ms=200,
            ),
            DqTestResult(
                test_name="test3",
                resource_name="users",
                status=DqStatus.WARN,
                failed_rows=2,
                execution_time_ms=150,
            ),
        ]
        report = QualityReport.from_results("users", results)

        assert report.total_tests == 3
        assert report.passed == 1
        assert report.failed == 1
        assert report.warned == 1
        assert report.success is False

    def test_from_results_aggregates_time(self) -> None:
        """from_results should sum execution times."""
        results = [
            DqTestResult(
                test_name="test1",
                resource_name="users",
                status=DqStatus.PASS,
                execution_time_ms=100,
            ),
            DqTestResult(
                test_name="test2",
                resource_name="users",
                status=DqStatus.PASS,
                execution_time_ms=200,
            ),
            DqTestResult(
                test_name="test3",
                resource_name="users",
                status=DqStatus.PASS,
                execution_time_ms=300,
            ),
        ]
        report = QualityReport.from_results("users", results)

        assert report.total_execution_time_ms == 600

    def test_from_results_preserves_results(self) -> None:
        """from_results should store all result objects."""
        results = [
            DqTestResult(
                test_name="test1",
                resource_name="users",
                status=DqStatus.PASS,
            ),
            DqTestResult(
                test_name="test2",
                resource_name="users",
                status=DqStatus.FAIL,
            ),
        ]
        report = QualityReport.from_results("users", results)

        assert len(report.results) == 2
        assert report.results[0].test_name == "test1"
        assert report.results[1].test_name == "test2"

    def test_from_results_with_all_statuses(self) -> None:
        """from_results should count all status types."""
        results = [
            DqTestResult(test_name="pass", resource_name="u", status=DqStatus.PASS),
            DqTestResult(test_name="fail", resource_name="u", status=DqStatus.FAIL),
            DqTestResult(test_name="warn", resource_name="u", status=DqStatus.WARN),
            DqTestResult(test_name="error", resource_name="u", status=DqStatus.ERROR),
            DqTestResult(test_name="skip", resource_name="u", status=DqStatus.SKIPPED),
        ]
        report = QualityReport.from_results("u", results)

        assert report.total_tests == 5
        assert report.passed == 1
        assert report.failed == 1
        assert report.warned == 1
        assert report.errors == 1
        assert report.skipped == 1

    def test_from_results_server_execution(self) -> None:
        """from_results with server execution."""
        results = [
            DqTestResult(
                test_name="test1",
                resource_name="users",
                status=DqStatus.PASS,
                executed_on="server",
            ),
        ]
        report = QualityReport.from_results("users", results, executed_on="server")

        assert report.executed_on == "server"

    def test_from_results_executed_at_is_set(self) -> None:
        """from_results should set executed_at timestamp."""
        before = datetime.now(UTC)
        report = QualityReport.from_results("users", [])
        after = datetime.now(UTC)

        assert before <= report.executed_at <= after


# =============================================================================
# Integration Tests
# =============================================================================


class TestModelIntegration:
    """Integration tests for model interactions."""

    def test_full_workflow(self) -> None:
        """Test complete workflow from definition to report."""
        # Create test definitions
        definitions = [
            DqTestDefinition.from_yaml(
                {"type": "not_null", "columns": ["email"]},
                "users",
            ),
            DqTestDefinition.from_yaml(
                {"type": "unique", "columns": ["email"]},
                "users",
            ),
        ]

        # Simulate execution results
        results = [
            DqTestResult(
                test_name=definitions[0].name,
                resource_name=definitions[0].resource_name,
                status=DqStatus.PASS,
                execution_time_ms=100,
            ),
            DqTestResult(
                test_name=definitions[1].name,
                resource_name=definitions[1].resource_name,
                status=DqStatus.FAIL,
                failed_rows=2,
                execution_time_ms=150,
            ),
        ]

        # Generate report
        report = QualityReport.from_results("users", results)

        assert report.total_tests == 2
        assert report.passed == 1
        assert report.failed == 1
        assert report.success is False
        assert report.total_execution_time_ms == 250

    def test_definition_result_relationship(self) -> None:
        """DqTestResult should reference DqTestDefinition correctly."""
        definition = DqTestDefinition(
            name="custom_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="iceberg.analytics.users",
            columns=["email"],
            severity=DqSeverity.WARN,
        )

        result = DqTestResult(
            test_name=definition.name,
            resource_name=definition.resource_name,
            status=DqStatus.WARN,  # Matches severity
            failed_rows=3,
        )

        assert result.test_name == definition.name
        assert result.resource_name == definition.resource_name
