"""Base specification model for the DLI Core Engine.

This module contains the SpecBase class which is the abstract base model
for both MetricSpec and DatasetSpec.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    PrivateAttr,
    field_validator,
    model_validator,
)

from dli.core.models.base import (
    DatasetVersion,
    ExecutionConfig,
    QueryParameter,
    QueryType,
    SpecType,
)

# Constants for fully qualified name parsing (catalog.schema.table)
_FQN_CATALOG_INDEX = 0
_FQN_SCHEMA_INDEX = 1
_FQN_TABLE_INDEX = 2
_FQN_MIN_PARTS_FOR_SCHEMA = 2
_FQN_MIN_PARTS_FOR_TABLE = 3


class SpecBase(BaseModel):
    """Base specification with common fields for both MetricSpec and DatasetSpec.

    This abstract base model defines the shared structure following 2025 best practices
    from SQLMesh and dbt for metric store frameworks.

    REQUIRED FIELDS (must be provided):
        name: Fully qualified name (catalog.schema.table)
        owner: Owner email address
        team: Team identifier (e.g., "@data-analytics")
        type: Spec type (Metric or Dataset)
        query_type: Query type (SELECT for metrics, DML for datasets)

    OPTIONAL FIELDS (have defaults or can be omitted):
        description: Human-readable description (default: empty)
        domains: List of domain tags (default: empty list)
        tags: List of general tags (default: empty list)
        versions: List of version information (default: empty list)
        parameters: List of query parameters (default: empty list)
        query_statement: Inline SQL content (alternative to query_file)
        query_file: SQL file path (alternative to query_statement)
        execution: Execution configuration (default: standard config)
        depends_on: List of upstream dependencies (default: empty list)
        schema_fields: Output schema definition (default: empty list)
    """

    # Required fields
    name: str = Field(..., description="Fully qualified name (catalog.schema.table)")
    owner: str = Field(..., description="Owner email address")
    team: str = Field(..., description="Team identifier (e.g., '@data-analytics')")
    type: SpecType = Field(..., description="Spec type (Metric or Dataset)")
    query_type: QueryType = Field(..., description="Query type (SELECT for metrics, DML for datasets)")

    # Optional fields
    description: str = Field(default="", description="Human-readable description")
    domains: list[str] = Field(default_factory=list, description="List of domain tags")
    tags: list[str] = Field(default_factory=list, description="List of general tags")
    versions: list[DatasetVersion] = Field(default_factory=list, description="Version information")
    parameters: list[QueryParameter] = Field(default_factory=list, description="Query parameters")

    # SQL content (exactly one of these should be provided)
    query_statement: str | None = Field(default=None, description="Inline SQL content")
    query_file: str | None = Field(default=None, description="SQL file path")

    # Execution configuration
    execution: ExecutionConfig = Field(default_factory=ExecutionConfig, description="Execution settings")

    # Dependencies and schema
    depends_on: list[str] = Field(default_factory=list, description="Upstream dependencies")
    schema_fields: list[dict[str, Any]] = Field(default_factory=list, alias="schema", description="Output schema")

    # Internal fields (set during loading via PrivateAttr for Pydantic compatibility)
    _spec_path: Path | None = PrivateAttr(default=None)
    _base_dir: Path | None = PrivateAttr(default=None)

    model_config = ConfigDict(populate_by_name=True)

    @model_validator(mode='after')
    def validate_type_query_type_consistency(self) -> SpecBase:
        """Validate that type and query_type are consistent.

        - Metric type must use SELECT query_type
        - Dataset type must use DML query_type
        """
        if self.type == SpecType.METRIC and self.query_type != QueryType.SELECT:
            raise ValueError(
                f"Metric '{self.name}' must have query_type='SELECT'. "
                f"Got query_type='{self.query_type.value}'. "
                "Metrics are read-only analytical queries."
            )
        if self.type == SpecType.DATASET and self.query_type != QueryType.DML:
            raise ValueError(
                f"Dataset '{self.name}' must have query_type='DML'. "
                f"Got query_type='{self.query_type.value}'. "
                "Datasets are for data processing operations (INSERT/UPDATE/DELETE/MERGE)."
            )
        return self

    @model_validator(mode='after')
    def validate_sql_content(self) -> SpecBase:
        """Validate SQL content configuration.

        Recommends having either query_statement or query_file, but allows
        empty configurations for testing and template purposes.
        """
        has_statement = bool(self.query_statement and self.query_statement.strip())
        has_file = bool(self.query_file and self.query_file.strip())

        # Only validate in production-like scenarios (when both are provided)
        if has_statement and has_file:
            raise ValueError(
                f"Spec '{self.name}' cannot have both 'query_statement' and 'query_file'. "
                "Choose either inline SQL or file-based SQL."
            )

        return self

    @field_validator('name')
    @classmethod
    def validate_fully_qualified_name(cls, v: str) -> str:
        """Validate spec name format.

        Recommends fully qualified names (catalog.schema.table) but allows
        partial names for backward compatibility with existing tests.

        Raises:
            ValueError: If name is empty, contains empty parts, or has invalid format
        """
        if not v or not v.strip():
            raise ValueError("Spec name cannot be empty")

        # Check for leading/trailing dots or consecutive dots
        if v.startswith('.') or v.endswith('.'):
            raise ValueError(f"Spec name '{v}' cannot start or end with a dot")
        if '..' in v:
            raise ValueError(f"Spec name '{v}' cannot contain consecutive dots")

        parts = v.split('.')

        # Validate each part is non-empty and contains valid characters
        for i, part in enumerate(parts):
            stripped = part.strip()
            if not stripped:
                raise ValueError(f"Empty part at position {i + 1} in spec name '{v}'")
            # Check for whitespace-only parts
            if part != stripped:
                raise ValueError(
                    f"Part '{part}' in spec name '{v}' contains leading/trailing whitespace"
                )

        return v

    @field_validator('owner')
    @classmethod
    def validate_owner_email(cls, v: str) -> str:
        """Validate owner field.

        Recommends email format but allows other formats for testing.
        """
        if not v:
            raise ValueError("Owner cannot be empty")

        # Allow non-email formats for backward compatibility
        return v

    def get_main_sql(self) -> str:
        """Get the main SQL content from inline or file.

        Returns:
            Main SQL content string

        Raises:
            ValueError: If neither query_statement nor query_file is provided
        """
        if self.query_statement:
            return self.query_statement
        if self.query_file and self._base_dir:
            return (self._base_dir / self.query_file).read_text(encoding="utf-8")
        msg = f"Spec '{self.name}' has no query_statement or query_file"
        raise ValueError(msg)

    @property
    def catalog(self) -> str:
        """Extract catalog from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_CATALOG_INDEX] if parts else ""

    @property
    def schema_name(self) -> str:
        """Extract schema from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_SCHEMA_INDEX] if len(parts) >= _FQN_MIN_PARTS_FOR_SCHEMA else ""

    @property
    def table(self) -> str:
        """Extract table from the fully qualified name."""
        parts = self.name.split(".")
        return parts[_FQN_TABLE_INDEX] if len(parts) >= _FQN_MIN_PARTS_FOR_TABLE else ""

    @property
    def active_version(self) -> DatasetVersion | None:
        """Get the currently active version."""
        for v in self.versions:
            if v.is_active:
                return v
        return None

    @property
    def spec_path(self) -> Path | None:
        """Get the path to the spec file (set during loading)."""
        return self._spec_path

    @property
    def base_dir(self) -> Path | None:
        """Get the base directory for resolving relative paths (set during loading)."""
        return self._base_dir

    def set_paths(self, spec_path: Path) -> None:
        """Set the spec path and base directory.

        Args:
            spec_path: Path to the spec file
        """
        self._spec_path = spec_path
        self._base_dir = spec_path.parent
