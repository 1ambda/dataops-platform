"""Data Quality Models for the DLI Core Engine.

This module contains the core models for data quality testing, following
dbt-style generic and singular test patterns.

Models:
    DqTestType: Enum for test type classification (not_null, unique, etc.)
    DqSeverity: Enum for test severity (error, warn)
    DqStatus: Enum for test execution status (pass, fail, warn, error, skipped)
    DqTestDefinition: Test definition from YAML spec
    DqTestResult: Single test execution result
    QualityReport: Aggregated test report

Note:
    Classes use "Dq" prefix to avoid pytest collection warnings
    (pytest treats classes starting with "Test" as test classes).

References:
    - dbt Data Tests: https://docs.getdbt.com/docs/build/data-tests
    - SQLMesh Audits: https://sqlmesh.readthedocs.io/en/latest/concepts/audits/
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import Enum
from typing import Any, Literal


def _utc_now() -> datetime:
    """Return current UTC time as a timezone-aware datetime."""
    return datetime.now(UTC)


class DqTestType(str, Enum):
    """Test type classification.

    Built-in generic tests:
        NOT_NULL: Check that columns have no NULL values
        UNIQUE: Check that column combinations are unique
        ACCEPTED_VALUES: Check that values are in allowed list
        RELATIONSHIPS: Check referential integrity (FK constraints)
        RANGE_CHECK: Check that numeric values are within bounds
        ROW_COUNT: Check table row count is within expected range

    Custom tests:
        SINGULAR: Custom SQL test file (returns rows that fail)
    """

    NOT_NULL = "not_null"
    UNIQUE = "unique"
    ACCEPTED_VALUES = "accepted_values"
    RELATIONSHIPS = "relationships"
    RANGE_CHECK = "range_check"
    ROW_COUNT = "row_count"
    SINGULAR = "singular"


class DqSeverity(str, Enum):
    """Test severity level.

    ERROR: Test failure is treated as an error (blocks pipeline)
    WARN: Test failure is treated as a warning (logs but continues)
    """

    ERROR = "error"
    WARN = "warn"


class DqStatus(str, Enum):
    """Test execution status.

    PASS: Test passed (no failing rows returned)
    FAIL: Test failed (failing rows returned, severity=error)
    WARN: Test failed (failing rows returned, severity=warn)
    ERROR: Test execution error (SQL error, connection issue, etc.)
    SKIPPED: Test was skipped (dependency failed, disabled, etc.)
    """

    PASS = "pass"
    FAIL = "fail"
    WARN = "warn"
    ERROR = "error"
    SKIPPED = "skipped"


@dataclass
class DqTestDefinition:
    """Test definition parsed from YAML spec.

    Attributes:
        name: Unique test name (generated or explicit)
        test_type: Type of test (not_null, unique, etc.)
        resource_name: Name of the resource being tested (table/dataset)
        columns: List of columns to test (for not_null, unique)
        params: Additional parameters for the test type
        description: Human-readable description
        severity: Test severity level (error or warn)
        sql: Custom SQL for singular tests
        file: SQL file path for singular tests
        enabled: Whether the test is enabled
    """

    name: str
    test_type: DqTestType
    resource_name: str
    columns: list[str] | None = None
    params: dict[str, Any] = field(default_factory=dict)
    description: str | None = None
    severity: DqSeverity = DqSeverity.ERROR
    sql: str | None = None
    file: str | None = None
    enabled: bool = True

    def __post_init__(self) -> None:
        """Generate name if not provided."""
        if not self.name:
            self.name = self._generate_name()

    def _generate_name(self) -> str:
        """Generate a unique test name based on type and columns."""
        base = f"{self.test_type.value}"
        if self.columns:
            cols = "_".join(self.columns[:3])  # Limit to 3 columns in name
            if len(self.columns) > 3:
                cols += "_etc"
            base = f"{base}_{cols}"
        return base

    @classmethod
    def from_yaml(
        cls,
        data: dict[str, Any],
        resource_name: str,
    ) -> DqTestDefinition:
        """Create DqTestDefinition from YAML dict.

        Args:
            data: Test definition from YAML
            resource_name: Name of the resource being tested

        Returns:
            DqTestDefinition instance

        Raises:
            ValueError: If test type is invalid or required fields are missing
        """
        test_type_str = data.get("type", "")
        try:
            test_type = DqTestType(test_type_str)
        except ValueError as e:
            msg = f"Invalid test type: {test_type_str}"
            raise ValueError(msg) from e

        # Extract columns (can be single column or list)
        columns = data.get("columns")
        if columns is None and "column" in data:
            columns = [data["column"]]
        elif isinstance(columns, str):
            columns = [columns]

        # Build params from remaining fields
        reserved_keys = {
            "type",
            "columns",
            "column",
            "name",
            "description",
            "severity",
            "sql",
            "file",
            "enabled",
        }
        params = {k: v for k, v in data.items() if k not in reserved_keys}

        return cls(
            name=data.get("name", ""),
            test_type=test_type,
            resource_name=resource_name,
            columns=columns,
            params=params,
            description=data.get("description"),
            severity=DqSeverity(data.get("severity", "error")),
            sql=data.get("sql"),
            file=data.get("file"),
            enabled=data.get("enabled", True),
        )


@dataclass
class DqTestResult:
    """Result of a single test execution.

    Attributes:
        test_name: Name of the test that was executed
        resource_name: Name of the resource that was tested
        status: Execution status (pass, fail, warn, error, skipped)
        failed_rows: Number of rows that failed the test
        failed_samples: Sample of failing rows (up to limit)
        execution_time_ms: Execution time in milliseconds
        executed_at: Timestamp of execution (UTC)
        error_message: Error message if status is ERROR
        executed_on: Where the test was executed (local or server)
        rendered_sql: The SQL that was executed
    """

    test_name: str
    resource_name: str
    status: DqStatus
    failed_rows: int = 0
    failed_samples: list[dict[str, Any]] = field(default_factory=list)
    execution_time_ms: int = 0
    executed_at: datetime = field(default_factory=_utc_now)
    error_message: str | None = None
    executed_on: Literal["local", "server"] = "local"
    rendered_sql: str | None = None


@dataclass
class DqTestConfig:
    """Test execution configuration.

    Attributes:
        fail_fast: Stop on first failure
        severity: Default severity for all tests
        limit: Maximum number of failing rows to collect
        store_failures: Whether to store failing rows
    """

    fail_fast: bool = False
    severity: DqSeverity = DqSeverity.ERROR
    limit: int = 100
    store_failures: bool = True


@dataclass
class QualityReport:
    """Aggregated test report for a resource or project.

    Attributes:
        resource_name: Name of the tested resource (or "all" for project)
        total_tests: Total number of tests executed
        passed: Number of tests that passed
        failed: Number of tests that failed
        warned: Number of tests with warnings
        errors: Number of tests with execution errors
        skipped: Number of tests that were skipped
        results: Individual test results
        executed_at: Report generation timestamp
        executed_on: Where tests were executed (local or server)
        total_execution_time_ms: Total execution time
    """

    resource_name: str
    total_tests: int = 0
    passed: int = 0
    failed: int = 0
    warned: int = 0
    errors: int = 0
    skipped: int = 0
    results: list[DqTestResult] = field(default_factory=list)
    executed_at: datetime = field(default_factory=_utc_now)
    executed_on: Literal["local", "server"] = "local"
    total_execution_time_ms: int = 0

    @property
    def success(self) -> bool:
        """Check if all tests passed (no failures or errors)."""
        return self.failed == 0 and self.errors == 0

    @classmethod
    def from_results(
        cls,
        resource_name: str,
        results: list[DqTestResult],
        executed_on: Literal["local", "server"] = "local",
    ) -> QualityReport:
        """Create a report from a list of test results.

        Args:
            resource_name: Name of the tested resource
            results: List of test results
            executed_on: Where tests were executed

        Returns:
            QualityReport with aggregated statistics
        """
        passed = sum(1 for r in results if r.status == DqStatus.PASS)
        failed = sum(1 for r in results if r.status == DqStatus.FAIL)
        warned = sum(1 for r in results if r.status == DqStatus.WARN)
        errors = sum(1 for r in results if r.status == DqStatus.ERROR)
        skipped = sum(1 for r in results if r.status == DqStatus.SKIPPED)
        total_time = sum(r.execution_time_ms for r in results)

        return cls(
            resource_name=resource_name,
            total_tests=len(results),
            passed=passed,
            failed=failed,
            warned=warned,
            errors=errors,
            skipped=skipped,
            results=results,
            executed_on=executed_on,
            total_execution_time_ms=total_time,
        )
