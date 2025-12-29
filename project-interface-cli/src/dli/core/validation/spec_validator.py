"""YAML Spec Validator for the DLI Core Engine.

This module provides the SpecValidator class for validating YAML spec files
using Pydantic schemas. This is a LOCAL ONLY validation - no server interaction.

Validation stages:
1. YAML Schema Validation (Pydantic)
   - Required fields check (name, owner, team, type, query_type)
   - Type/QueryType consistency (Metric->SELECT, Dataset->DML)
   - Parameter type validation
2. SQL Syntax Validation (SQLGlot)
   - Parse with dialect (trino, bigquery, postgres, etc.)
   - Detect syntax errors
   - Generate warnings (SELECT *, missing LIMIT)
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import TYPE_CHECKING, Literal

from pydantic import ValidationError
import yaml

if TYPE_CHECKING:
    from dli.core.models.spec import SpecBase


@dataclass
class SpecValidationResult:
    """Result of validating a single spec file.

    Attributes:
        is_valid: Whether the spec passed validation
        spec_path: Path to the validated spec file
        spec_name: Fully qualified name of the spec (if parsed successfully)
        spec_type: Type of spec (metric or dataset)
        errors: List of validation errors
        warnings: List of validation warnings
    """

    is_valid: bool
    spec_path: Path
    spec_name: str | None = None
    spec_type: Literal["metric", "dataset"] | None = None
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def has_warnings(self) -> bool:
        """Check if there are any warnings."""
        return len(self.warnings) > 0


@dataclass
class ValidationSummary:
    """Summary of project-wide validation results.

    Attributes:
        total: Total number of specs validated
        passed: Number of specs that passed validation
        failed: Number of specs that failed validation
        warnings: Number of specs with warnings (but passed)
        results: List of individual validation results
    """

    total: int = 0
    passed: int = 0
    failed: int = 0
    warnings: int = 0
    results: list[SpecValidationResult] = field(default_factory=list)

    @property
    def all_passed(self) -> bool:
        """Check if all specs passed validation."""
        return self.failed == 0

    @property
    def metrics_count(self) -> int:
        """Count of metric specs."""
        return sum(1 for r in self.results if r.spec_type == "metric")

    @property
    def datasets_count(self) -> int:
        """Count of dataset specs."""
        return sum(1 for r in self.results if r.spec_type == "dataset")

    @property
    def failed_results(self) -> list[SpecValidationResult]:
        """Get only failed validation results."""
        return [r for r in self.results if not r.is_valid]

    @property
    def warning_results(self) -> list[SpecValidationResult]:
        """Get only results with warnings (but passed)."""
        return [r for r in self.results if r.is_valid and r.has_warnings]


class SpecValidator:
    """YAML Spec file validator (LOCAL ONLY).

    Validates spec files using Pydantic schemas and SQLGlot for SQL syntax.
    No server interaction is performed.

    Attributes:
        dialect: SQL dialect for validation (e.g., 'trino', 'bigquery', 'postgres')
        strict: If True, treat warnings as errors

    Example:
        >>> validator = SpecValidator(dialect="trino")
        >>> result = validator.validate_file(Path("spec.yaml"))
        >>> if result.is_valid:
        ...     print(f"Valid: {result.spec_name}")
        ... else:
        ...     for error in result.errors:
        ...         print(f"Error: {error}")
    """

    # Filename prefixes for type detection
    _METRIC_PREFIX = "metric."
    _DATASET_PREFIX = "dataset."

    def __init__(
        self,
        dialect: str = "trino",
        *,
        strict: bool = False,
    ) -> None:
        """Initialize the spec validator.

        Args:
            dialect: SQL dialect for parsing (default: trino)
            strict: If True, treat warnings as errors
        """
        self.dialect = dialect
        self.strict = strict

    def validate_file(
        self,
        path: Path,
        *,
        variables: dict[str, str] | None = None,
    ) -> SpecValidationResult:
        """Validate a single spec file.

        Args:
            path: Path to the spec file (YAML)
            variables: Optional variable substitutions for SQL rendering

        Returns:
            SpecValidationResult with validation status
        """
        errors: list[str] = []
        warnings: list[str] = []
        spec_name: str | None = None
        spec_type: Literal["metric", "dataset"] | None = None

        # Stage 0: Check file exists and is readable
        if not path.exists():
            errors.append(f"File not found: {path}")
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                errors=errors,
            )

        # Stage 1: Load and parse YAML
        try:
            data = self._load_yaml(path)
        except yaml.YAMLError as e:
            errors.append(f"YAML parse error: {e}")
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                errors=errors,
            )
        except OSError as e:
            errors.append(f"File read error: {e}")
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                errors=errors,
            )

        if not data:
            errors.append("Empty YAML file")
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                errors=errors,
            )

        # Stage 2: Detect spec type from data or filename
        spec_type = self._detect_spec_type(data, path)
        spec_name = data.get("name")

        # Stage 3: Pydantic schema validation
        spec, validation_errors = self._validate_schema(data, spec_type, path)
        errors.extend(validation_errors)

        if errors:
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                spec_name=spec_name,
                spec_type=spec_type,
                errors=errors,
            )

        if spec is None:
            errors.append("Failed to create spec object")
            return SpecValidationResult(
                is_valid=False,
                spec_path=path,
                spec_name=spec_name,
                spec_type=spec_type,
                errors=errors,
            )

        # Update spec_name from validated spec
        spec_name = spec.name

        # Stage 4: SQL syntax validation
        sql_errors, sql_warnings = self._validate_sql(spec, path, variables)
        errors.extend(sql_errors)
        warnings.extend(sql_warnings)

        # In strict mode, warnings become errors
        if self.strict and warnings:
            errors.extend([f"[strict] {w}" for w in warnings])
            warnings = []

        return SpecValidationResult(
            is_valid=len(errors) == 0,
            spec_path=path,
            spec_name=spec_name,
            spec_type=spec_type,
            errors=errors,
            warnings=warnings,
        )

    def validate_all(
        self,
        project_path: Path,
        *,
        spec_type: Literal["metric", "dataset", "all"] = "all",
        variables: dict[str, str] | None = None,
    ) -> ValidationSummary:
        """Validate all specs in a project.

        Args:
            project_path: Path to the project directory containing dli.yaml
            spec_type: Type of specs to validate ("metric", "dataset", or "all")
            variables: Optional variable substitutions for SQL rendering

        Returns:
            ValidationSummary with results for all validated specs
        """
        from dli.core.config import load_project  # noqa: PLC0415
        from dli.core.discovery import SpecDiscovery  # noqa: PLC0415

        summary = ValidationSummary()

        try:
            config = load_project(project_path)
        except FileNotFoundError as e:
            # Return summary with a single error result
            result = SpecValidationResult(
                is_valid=False,
                spec_path=project_path / "dli.yaml",
                errors=[str(e)],
            )
            summary.results.append(result)
            summary.total = 1
            summary.failed = 1
            return summary

        discovery = SpecDiscovery(config)

        # Collect spec paths based on type filter
        spec_paths: list[tuple[Path, Literal["metric", "dataset"]]] = []

        if spec_type in ("metric", "all"):
            for spec in discovery.discover_metrics():
                if spec.spec_path:
                    spec_paths.append((spec.spec_path, "metric"))

        if spec_type in ("dataset", "all"):
            for spec in discovery.discover_datasets():
                if spec.spec_path:
                    spec_paths.append((spec.spec_path, "dataset"))

        # Validate each spec
        for path, detected_type in spec_paths:
            result = self.validate_file(path, variables=variables)
            # Override detected type if we know it from discovery
            if result.spec_type is None:
                result.spec_type = detected_type

            summary.results.append(result)
            summary.total += 1

            if result.is_valid:
                summary.passed += 1
                if result.has_warnings:
                    summary.warnings += 1
            else:
                summary.failed += 1

        return summary

    def validate_by_name(
        self,
        resource_name: str,
        project_path: Path,
        *,
        variables: dict[str, str] | None = None,
    ) -> SpecValidationResult:
        """Validate a spec by its fully qualified name.

        Args:
            resource_name: Fully qualified resource name (catalog.schema.table)
            project_path: Path to the project directory
            variables: Optional variable substitutions

        Returns:
            SpecValidationResult for the found spec
        """
        from dli.core.config import load_project  # noqa: PLC0415
        from dli.core.discovery import SpecDiscovery  # noqa: PLC0415

        try:
            config = load_project(project_path)
        except FileNotFoundError as e:
            return SpecValidationResult(
                is_valid=False,
                spec_path=project_path / "dli.yaml",
                errors=[str(e)],
            )

        discovery = SpecDiscovery(config)
        spec = discovery.find_spec(resource_name)

        if spec is None:
            return SpecValidationResult(
                is_valid=False,
                spec_path=project_path,
                spec_name=resource_name,
                errors=[f"Resource '{resource_name}' not found in project"],
            )

        if spec.spec_path is None:
            return SpecValidationResult(
                is_valid=False,
                spec_path=project_path,
                spec_name=resource_name,
                errors=[f"Resource '{resource_name}' has no spec path"],
            )

        return self.validate_file(spec.spec_path, variables=variables)

    def _load_yaml(self, path: Path) -> dict:
        """Load and parse a YAML file.

        Args:
            path: Path to the YAML file

        Returns:
            Parsed YAML as dictionary
        """
        with open(path, encoding="utf-8") as f:
            return yaml.safe_load(f) or {}

    def _detect_spec_type(
        self,
        data: dict,
        path: Path,
    ) -> Literal["metric", "dataset"]:
        """Detect spec type from data or filename.

        Args:
            data: Parsed YAML data
            path: Path to the spec file

        Returns:
            Detected spec type
        """
        # Check explicit type field
        type_value = data.get("type", "").lower()
        if type_value == "metric":
            return "metric"
        if type_value == "dataset":
            return "dataset"

        # Auto-detect from filename
        filename = path.name.lower()
        if filename.startswith(self._METRIC_PREFIX):
            return "metric"
        if filename.startswith(self._DATASET_PREFIX):
            return "dataset"

        # Default to dataset
        return "dataset"

    def _validate_schema(
        self,
        data: dict,
        spec_type: Literal["metric", "dataset"],
        path: Path,
    ) -> tuple[SpecBase | None, list[str]]:
        """Validate spec data against Pydantic schema.

        Args:
            data: Parsed YAML data
            spec_type: Type of spec to validate
            path: Path to the spec file (for context in errors)

        Returns:
            Tuple of (validated spec or None, list of errors)
        """
        from dli.core.models import (  # noqa: PLC0415
            DatasetSpec,
            MetricSpec,
            QueryType,
            SpecType,
        )

        errors: list[str] = []

        # Set defaults based on type
        data_copy = data.copy()
        if "type" not in data_copy:
            data_copy["type"] = SpecType.METRIC.value if spec_type == "metric" else SpecType.DATASET.value

        if "query_type" not in data_copy:
            data_copy["query_type"] = (
                QueryType.SELECT.value if spec_type == "metric" else QueryType.DML.value
            )

        # Validate with appropriate model
        try:
            if spec_type == "metric":
                spec = MetricSpec.model_validate(data_copy)
            else:
                spec = DatasetSpec.model_validate(data_copy)

            spec.set_paths(path)
            return spec, errors

        except ValidationError as e:
            for error in e.errors():
                loc = ".".join(str(x) for x in error["loc"])
                msg = error["msg"]
                errors.append(f"Schema error at '{loc}': {msg}")
            return None, errors

    def _validate_sql(
        self,
        spec: SpecBase,
        _path: Path,  # Reserved for future file-based error reporting
        variables: dict[str, str] | None = None,
    ) -> tuple[list[str], list[str]]:
        """Validate SQL syntax in the spec.

        Args:
            spec: Validated spec object
            _path: Path to the spec file (reserved for future error reporting)
            variables: Optional variable substitutions

        Returns:
            Tuple of (errors, warnings)
        """
        from dli.core.validator import SQLValidator  # noqa: PLC0415

        errors: list[str] = []
        warnings: list[str] = []

        # Get SQL content
        try:
            sql = spec.get_main_sql()
        except ValueError as e:
            # No SQL content - this may be acceptable for some specs
            warnings.append(f"No SQL content: {e}")
            return errors, warnings
        except FileNotFoundError as e:
            errors.append(f"SQL file not found: {e}")
            return errors, warnings

        if not sql or not sql.strip():
            warnings.append("Empty SQL content")
            return errors, warnings

        # Render SQL with variables if provided
        if variables:
            try:
                sql = self._render_sql(sql, variables, spec)
            except Exception as e:
                warnings.append(f"SQL rendering warning: {e}")

        # Validate with SQLGlot
        validator = SQLValidator(dialect=self.dialect)
        result = validator.validate(sql)

        if not result.is_valid:
            errors.extend(result.errors)
        warnings.extend(result.warnings)

        return errors, warnings

    def _render_sql(
        self,
        sql: str,
        variables: dict[str, str],
        _spec: SpecBase,  # Reserved for future context-based rendering
    ) -> str:
        """Render SQL template with variables.

        Args:
            sql: SQL template string
            variables: Variable substitutions
            _spec: Spec object (reserved for future context-based rendering)

        Returns:
            Rendered SQL string
        """
        from dli.core.templates import SafeTemplateRenderer  # noqa: PLC0415

        renderer = SafeTemplateRenderer()
        return renderer.render(sql, variables)
