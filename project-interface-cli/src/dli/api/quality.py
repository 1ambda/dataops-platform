"""QualityAPI - Library API for Quality operations.

This module provides the QualityAPI class which wraps Quality testing
functionality for programmatic access.

Example:
    >>> from dli import QualityAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(
    ...     execution_mode=ExecutionMode.LOCAL,
    ...     project_path=Path("/opt/airflow/dags/models"),
    ... )
    >>> api = QualityAPI(context=ctx)
    >>>
    >>> # List registered qualities from server
    >>> qualities = api.list_qualities(target_type="dataset")
    >>>
    >>> # Run quality tests locally
    >>> result = api.run("quality.iceberg.analytics.daily_clicks.yaml")
    >>> print(result.status)
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any

from pydantic import ValidationError as PydanticValidationError
import yaml

from dli.exceptions import (
    QualitySpecNotFoundError,
    QualitySpecParseError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ValidationResult
from dli.models.quality import (
    DqQualityResult,
    DqSeverity,
    DqStatus,
    QualityInfo,
    QualitySpec,
    QualityTargetType,
)

if TYPE_CHECKING:
    from dli.core.executor import QueryExecutor


class QualityAPI:
    """Quality testing Library API.

    Provides programmatic access to quality testing operations including:
    - Listing and querying qualities from server
    - Validating Quality Spec YML files
    - Running quality tests (LOCAL/SERVER mode)

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import QualityAPI, ExecutionContext
        >>> ctx = ExecutionContext(
        ...     project_path="/opt/airflow/dags/models",
        ...     execution_mode=ExecutionMode.LOCAL,
        ... )
        >>> api = QualityAPI(context=ctx)
        >>>
        >>> # Validate a Quality Spec
        >>> result = api.validate("quality.iceberg.analytics.daily_clicks.yaml")
        >>> if not result.valid:
        ...     print(result.errors)
        >>>
        >>> # Run quality tests
        >>> result = api.run("quality.iceberg.analytics.daily_clicks.yaml")
        >>> print(f"Status: {result.status}")
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        executor: QueryExecutor | None = None,
    ) -> None:
        """Initialize QualityAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
            executor: Optional query executor for DI (dependency injection).
        """
        self.context = context or ExecutionContext()
        self._executor = executor

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"QualityAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _resolve_spec_path(self, spec_path: str | Path) -> Path:
        """Resolve spec path relative to project path.

        Args:
            spec_path: Relative or absolute path to spec file.

        Returns:
            Absolute path to spec file.

        Raises:
            QualitySpecNotFoundError: If spec file not found.
        """
        path = Path(spec_path)

        # If absolute and exists, use it
        if path.is_absolute() and path.exists():
            return path

        # Try relative to project path
        if self.context.project_path:
            resolved = self.context.project_path / path
            if resolved.exists():
                return resolved

        # Try current directory
        cwd_path = Path.cwd() / path
        if cwd_path.exists():
            return cwd_path

        # Not found
        raise QualitySpecNotFoundError(
            message=f"Quality Spec not found: {spec_path}",
            spec_path=str(spec_path),
        )

    def _load_spec(self, spec_path: str | Path) -> QualitySpec:
        """Load and parse a Quality Spec file.

        Args:
            spec_path: Path to the Quality Spec YAML file.

        Returns:
            Parsed QualitySpec.

        Raises:
            QualitySpecNotFoundError: If file not found.
            QualitySpecParseError: If parsing fails.
        """
        resolved_path = self._resolve_spec_path(spec_path)

        try:
            return QualitySpec.from_yaml_file(resolved_path)
        except yaml.YAMLError as e:
            raise QualitySpecParseError(
                message=f"YAML parsing error: {e}",
                spec_path=str(resolved_path),
            ) from e
        except PydanticValidationError as e:
            errors = "; ".join(str(err["msg"]) for err in e.errors())
            raise QualitySpecParseError(
                message=f"Validation error: {errors}",
                spec_path=str(resolved_path),
            ) from e

    # === List/Get Operations (Server) ===

    def list_qualities(
        self,
        target_type: str | None = None,
        target_name: str | None = None,
        status: str | None = None,
    ) -> list[QualityInfo]:
        """List quality tests registered on server.

        Args:
            target_type: Filter by target type ("dataset" or "metric").
            target_name: Filter by target name (partial match).
            status: Filter by status ("active" or "inactive").

        Returns:
            List of QualityInfo objects.
        """
        if self._is_mock_mode:
            # Return mock data for testing
            return self._get_mock_qualities(target_type, target_name, status)

        # TODO: Implement server call when BasecampClient supports it
        # For now, return empty list for non-mock mode
        return []

    def get(self, name: str) -> QualityInfo | None:
        """Get quality details from server.

        Args:
            name: Quality name or URN.

        Returns:
            QualityInfo if found, None otherwise.
        """
        if self._is_mock_mode:
            # Return mock data
            qualities = self._get_mock_qualities()
            for q in qualities:
                if q.name == name:
                    return q
            return None

        # TODO: Implement server call
        return None

    def _get_mock_qualities(
        self,
        target_type: str | None = None,
        target_name: str | None = None,
        status: str | None = None,
    ) -> list[QualityInfo]:
        """Return mock quality data for testing."""
        mock_data = [
            QualityInfo(
                name="pk_unique",
                target_urn="dataset:iceberg.analytics.daily_clicks",
                target_type=QualityTargetType.DATASET,
                target_name="iceberg.analytics.daily_clicks",
                test_type="generic",
                status="active",
                severity=DqSeverity.ERROR,
                description="Primary key must be unique",
                schedule="0 6 * * *",
                last_run=datetime(2025, 12, 30, 6, 0, 0, tzinfo=UTC),
                last_status=DqStatus.PASS,
            ),
            QualityInfo(
                name="not_null_user_id",
                target_urn="dataset:iceberg.analytics.daily_clicks",
                target_type=QualityTargetType.DATASET,
                target_name="iceberg.analytics.daily_clicks",
                test_type="generic",
                status="active",
                severity=DqSeverity.ERROR,
                description="user_id must not be null",
            ),
            QualityInfo(
                name="valid_country_code",
                target_urn="dataset:iceberg.analytics.daily_clicks",
                target_type=QualityTargetType.DATASET,
                target_name="iceberg.analytics.daily_clicks",
                test_type="singular",
                status="active",
                severity=DqSeverity.WARN,
            ),
            QualityInfo(
                name="unique_event_id",
                target_urn="metric:iceberg.analytics.user_engagement",
                target_type=QualityTargetType.METRIC,
                target_name="iceberg.analytics.user_engagement",
                test_type="generic",
                status="active",
                severity=DqSeverity.ERROR,
            ),
        ]

        result = mock_data

        # Apply filters
        if target_type:
            result = [
                q for q in result if q.target_type.value == target_type.lower()
            ]
        if target_name:
            result = [q for q in result if target_name.lower() in q.target_name.lower()]
        if status:
            result = [q for q in result if q.status == status.lower()]

        return result

    # === Validation ===

    def validate(
        self,
        spec_path: str | Path,
        *,
        strict: bool = False,
        tests: list[str] | None = None,
    ) -> ValidationResult:
        """Validate a Quality Spec YML file.

        Args:
            spec_path: Path to the Quality Spec file.
            strict: If True, also validate that target exists.
            tests: Optional list of test names to validate (default: all).

        Returns:
            ValidationResult with validation status.
        """
        errors: list[str] = []
        warnings: list[str] = []

        try:
            spec = self._load_spec(spec_path)
        except QualitySpecNotFoundError as e:
            return ValidationResult(valid=False, errors=[str(e)])
        except QualitySpecParseError as e:
            return ValidationResult(valid=False, errors=[str(e)])

        # Validate version
        if spec.version != 1:
            warnings.append(f"Unknown spec version: {spec.version}")

        # Validate target
        if not spec.target.name:
            errors.append("Target name is required")

        # Validate tests
        if not spec.tests:
            warnings.append("No tests defined in spec")
        else:
            test_names = set()
            for test in spec.tests:
                # Check for duplicate names
                if test.name in test_names:
                    errors.append(f"Duplicate test name: {test.name}")
                test_names.add(test.name)

                # Validate test type specific requirements
                if test.type in (
                    "not_null",
                    "unique",
                ) and not (test.columns or test.column):
                    errors.append(f"Test '{test.name}' requires columns")

                if test.type == "accepted_values" and not (
                    test.values or test.values_query
                ):
                    errors.append(
                        f"Test '{test.name}' requires values or values_query"
                    )

                if test.type == "relationships" and not (test.to and test.to_column):
                    errors.append(f"Test '{test.name}' requires to and to_column")

                if test.type == "singular" and not (test.sql or test.file):
                    errors.append(f"Test '{test.name}' requires sql or file")

        # Filter by specific tests if provided
        if tests:
            spec_test_names = {t.name for t in spec.tests}
            missing = set(tests) - spec_test_names
            if missing:
                errors.append(f"Tests not found: {', '.join(missing)}")

        # Strict mode: validate target exists
        if strict and not self._is_mock_mode:
            # TODO: Implement target validation when service is available
            pass

        return ValidationResult(
            valid=len(errors) == 0,
            errors=errors,
            warnings=warnings,
        )

    # === Execution ===

    def run(
        self,
        spec_path: str | Path,
        *,
        tests: list[str] | None = None,
        parameters: dict[str, Any] | None = None,
        fail_fast: bool = False,
    ) -> DqQualityResult:
        """Run quality tests from a Quality Spec.

        Args:
            spec_path: Path to the Quality Spec YML file.
            tests: Optional list of specific test names to run.
            parameters: Runtime parameters for test execution.
            fail_fast: Stop on first failure.

        Returns:
            DqQualityResult with execution results.

        Raises:
            QualitySpecNotFoundError: If spec file not found.
            QualitySpecParseError: If spec parsing fails.
        """
        started_at = datetime.now(tz=UTC)

        # Load spec
        spec = self._load_spec(spec_path)

        # Get tests to run
        tests_to_run = spec.tests
        if tests:
            tests_to_run = [t for t in spec.tests if t.name in tests]

        if self._is_mock_mode:
            # Return mock success result
            finished_at = datetime.now(tz=UTC)
            return DqQualityResult(
                target_urn=spec.target_urn,
                execution_mode="mock",
                started_at=started_at,
                finished_at=finished_at,
                test_results=[
                    {
                        "test_name": t.name,
                        "resource_name": spec.target.name,
                        "status": DqStatus.PASS.value,
                        "failed_rows": 0,
                        "execution_time_ms": 10,
                    }
                    for t in tests_to_run
                ],
            )

        # LOCAL or SERVER execution
        execution_mode = self.context.execution_mode.value

        if self.context.execution_mode == ExecutionMode.SERVER:
            # TODO: Implement server execution
            finished_at = datetime.now(tz=UTC)
            return DqQualityResult(
                target_urn=spec.target_urn,
                execution_mode="server",
                execution_id=f"exec-{datetime.now(tz=UTC).strftime('%Y%m%d%H%M%S')}",
                started_at=started_at,
                finished_at=finished_at,
                test_results=[
                    {
                        "test_name": t.name,
                        "resource_name": spec.target.name,
                        "status": DqStatus.PASS.value,
                        "failed_rows": 0,
                        "execution_time_ms": 100,
                    }
                    for t in tests_to_run
                ],
            )

        # LOCAL execution using QualityExecutor
        try:
            from dli.core.quality import DqTestConfig, QualityExecutor

            # Convert to DqTestDefinition for executor
            test_definitions = [
                t.to_test_definition(spec.target.name) for t in tests_to_run
            ]

            config = DqTestConfig(fail_fast=fail_fast)
            executor = QualityExecutor(client=None, config=config)

            # Run tests
            report = executor.run_all(test_definitions, on_server=False)

            finished_at = datetime.now(tz=UTC)

            # Convert report to result
            return DqQualityResult(
                target_urn=spec.target_urn,
                execution_mode=execution_mode,
                started_at=started_at,
                finished_at=finished_at,
                test_results=[
                    {
                        "test_name": r.test_name,
                        "resource_name": r.resource_name,
                        "status": r.status.value,
                        "failed_rows": r.failed_rows,
                        "execution_time_ms": r.execution_time_ms,
                        "error_message": r.error_message,
                    }
                    for r in report.results
                ],
            )

        except Exception as e:
            finished_at = datetime.now(tz=UTC)
            return DqQualityResult(
                target_urn=spec.target_urn,
                execution_mode=execution_mode,
                started_at=started_at,
                finished_at=finished_at,
                test_results=[
                    {
                        "test_name": t.name,
                        "resource_name": spec.target.name,
                        "status": DqStatus.ERROR.value,
                        "failed_rows": 0,
                        "execution_time_ms": 0,
                        "error_message": str(e),
                    }
                    for t in tests_to_run
                ],
            )

    def get_spec(self, spec_path: str | Path) -> QualitySpec:
        """Load and return a parsed Quality Spec.

        Useful for inspecting spec contents programmatically.

        Args:
            spec_path: Path to the Quality Spec file.

        Returns:
            Parsed QualitySpec.

        Raises:
            QualitySpecNotFoundError: If spec file not found.
            QualitySpecParseError: If parsing fails.
        """
        return self._load_spec(spec_path)


__all__ = ["QualityAPI"]
