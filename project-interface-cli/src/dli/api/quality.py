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
    from dli.models.quality import DqTestDefinitionSpec


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

    def _render_test_sql(
        self,
        test: DqTestDefinitionSpec,
        target_name: str,
        parameters: dict[str, Any] | None = None,
    ) -> str:
        """Render SQL for a quality test.

        This generates the test SQL locally based on test type using
        the BuiltinTests generator.

        Args:
            test: Test definition spec.
            target_name: Fully qualified table name.
            parameters: Runtime parameters (unused currently).

        Returns:
            Generated SQL query for the test.

        Raises:
            ValueError: If test type is unknown or singular test has no SQL.
        """
        from dli.core.quality.builtin_tests import BuiltinTests
        from dli.core.quality.models import DqTestType

        test_type = test.type.value if isinstance(test.type, DqTestType) else test.type

        # Handle singular tests (custom SQL)
        if test_type == "singular":
            if test.sql:
                return test.sql
            if test.file:
                # Load SQL from file (relative to project path if available)
                sql_path = Path(test.file)
                if self.context.project_path and not sql_path.is_absolute():
                    sql_path = self.context.project_path / sql_path
                if sql_path.exists():
                    return sql_path.read_text(encoding="utf-8")
                raise ValueError(f"SQL file not found: {test.file}")
            raise ValueError(f"Singular test '{test.name}' requires 'sql' or 'file'")

        # Build kwargs for builtin test generator
        kwargs: dict[str, Any] = {}

        # Handle columns (can be list or single)
        if test.columns:
            kwargs["columns"] = test.columns
        elif test.column:
            kwargs["columns"] = [test.column]
            kwargs["column"] = test.column

        # Additional parameters for specific test types
        if test.values:
            kwargs["values"] = test.values
        if test.to:
            kwargs["to"] = test.to
        if test.to_column:
            kwargs["to_column"] = test.to_column
        if test.min is not None:
            kwargs["min"] = test.min
        if test.max is not None:
            kwargs["max"] = test.max

        return BuiltinTests.generate(test_type, target_name, **kwargs)

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

        # LOCAL and SERVER: Use Execution API
        execution_mode = self.context.execution_mode.value

        try:
            from dli.core.client import create_client

            # Create client for API calls
            # Note: use mock_mode when server_url is not configured (test environment)
            # This is separate from ExecutionMode.MOCK which is handled above for
            # explicit mock behavior requested by user
            use_mock = self.context.server_url is None
            client = create_client(
                url=self.context.server_url,
                mock_mode=use_mock,
            )

            # Prepare rendered tests (CLI generates SQL locally)
            rendered_tests: list[dict[str, Any]] = []
            for test in tests_to_run:
                try:
                    rendered_sql = self._render_test_sql(
                        test, spec.target.name, parameters
                    )
                    rendered_tests.append({
                        "name": test.name,
                        "type": test.type.value if hasattr(test.type, "value") else test.type,
                        "rendered_sql": rendered_sql,
                    })
                except Exception as render_err:
                    # If SQL rendering fails, include error in the test entry
                    rendered_tests.append({
                        "name": test.name,
                        "type": test.type.value if hasattr(test.type, "value") else test.type,
                        "rendered_sql": "",
                        "render_error": str(render_err),
                    })

            # Call server execution API
            response = client.execute_rendered_quality(
                resource_name=spec.target.name,
                tests=rendered_tests,
                execution_timeout=self.context.timeout or 300,
            )

            finished_at = datetime.now(tz=UTC)

            # Type narrowing: execute_rendered_quality returns dict, not list
            if response.success and response.data and isinstance(response.data, dict):
                data = response.data
                return DqQualityResult(
                    target_urn=spec.target_urn,
                    execution_mode=execution_mode,
                    execution_id=data.get("execution_id"),
                    started_at=started_at,
                    finished_at=finished_at,
                    test_results=[
                        {
                            "test_name": r["test_name"],
                            "resource_name": spec.target.name,
                            "status": DqStatus.PASS.value if r.get("passed") else DqStatus.FAIL.value,
                            "failed_rows": r.get("failed_count", 0),
                            "execution_time_ms": r.get("duration_ms", 0),
                        }
                        for r in data.get("results", [])
                    ],
                )

            # Handle API error response
            error_message = response.error or "Execution failed"
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
                        "error_message": error_message,
                    }
                    for t in tests_to_run
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
