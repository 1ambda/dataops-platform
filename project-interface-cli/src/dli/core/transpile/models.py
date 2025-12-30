"""
Pydantic models for SQL transpile functionality.

This module defines all data models used by the TranspileEngine,
including configuration, rules, warnings, and results.
"""

from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from typing import NamedTuple

from pydantic import BaseModel, Field

__all__ = [
    "Dialect",
    "MetricDefinition",
    "MetricMatch",
    "RuleType",
    "TranspileConfig",
    "TranspileMetadata",
    "TranspileResult",
    "TranspileRule",
    "TranspileWarning",
    "WarningType",
]


def _utc_now() -> datetime:
    """Return current UTC datetime."""
    return datetime.now(tz=UTC)


# =============================================================================
# Enums
# =============================================================================


class Dialect(str, Enum):
    """Supported SQL dialects."""

    TRINO = "trino"
    BIGQUERY = "bigquery"


class RuleType(str, Enum):
    """Transpile rule types."""

    TABLE_SUBSTITUTION = "table_substitution"
    METRIC_EXPANSION = "metric_expansion"


class WarningType(str, Enum):
    """Warning types for SQL analysis."""

    NO_LIMIT = "no_limit"
    SELECT_STAR = "select_star"
    DUPLICATE_CTE = "duplicate_cte"
    CORRELATED_SUBQUERY = "correlated_subquery"
    DANGEROUS_STATEMENT = "dangerous_statement"
    METRIC_ERROR = "metric_error"


# =============================================================================
# Dialect Mapping (shared across modules)
# =============================================================================


DIALECT_MAP: dict[Dialect, str] = {
    Dialect.TRINO: "trino",
    Dialect.BIGQUERY: "bigquery",
}


# =============================================================================
# NamedTuple for METRIC() function matching
# =============================================================================


class MetricMatch(NamedTuple):
    """METRIC() function match result.

    Represents a single occurrence of METRIC(name) in SQL text,
    including position information for replacement.
    """

    full_match: str
    """Full matched text, e.g., 'METRIC(revenue)'."""

    metric_name: str
    """Extracted metric name, e.g., 'revenue'."""

    start_pos: int
    """Start position in the SQL string."""

    end_pos: int
    """End position in the SQL string."""


# =============================================================================
# Configuration Models
# =============================================================================


class TranspileConfig(BaseModel):
    """Transpile engine configuration.

    Controls behavior of the TranspileEngine including dialect selection,
    error handling mode, and retry settings.
    """

    dialect: Dialect = Field(
        default=Dialect.TRINO,
        description="Input SQL dialect for parsing",
    )
    strict_mode: bool = Field(
        default=False,
        description="If True, raise exceptions on transpile failures; "
        "otherwise fall back to original SQL with warnings",
    )
    validate_syntax: bool = Field(
        default=False,
        description="If True, perform SQL syntax validation",
    )
    retry_count: int = Field(
        default=1,
        ge=0,
        le=5,
        description="Number of API retry attempts (0-5)",
    )
    server_url: str | None = Field(
        default=None,
        description="Basecamp Server URL; if None, uses environment default",
    )


# =============================================================================
# Rule and Warning Models
# =============================================================================


class TranspileRule(BaseModel):
    """Applied transpile rule.

    Represents a single rule that was applied during transpilation,
    used for auditing and debugging.
    """

    id: str = Field(
        ...,
        description="Unique rule identifier",
    )
    type: RuleType = Field(
        ...,
        description="Rule type (table_substitution, metric_expansion)",
    )
    source: str = Field(
        ...,
        description="Original value (table name or METRIC function)",
    )
    target: str = Field(
        ...,
        description="Replacement value (new table name or SQL expression)",
    )
    description: str | None = Field(
        default=None,
        description="Human-readable rule description",
    )
    enabled: bool = Field(
        default=True,
        description="Whether the rule is currently enabled",
    )


class TranspileWarning(BaseModel):
    """Transpile warning.

    Advisory information about SQL patterns that may need attention.
    Warnings do not prevent SQL execution but provide optimization hints.
    """

    type: WarningType = Field(
        ...,
        description="Warning type classification",
    )
    message: str = Field(
        ...,
        description="Human-readable warning message",
    )
    line: int | None = Field(
        default=None,
        description="Line number where issue was detected (1-based)",
    )
    column: int | None = Field(
        default=None,
        description="Column number where issue was detected (1-based)",
    )


# =============================================================================
# Metric Models
# =============================================================================


class MetricDefinition(BaseModel):
    """Metric definition from server.

    Contains the SQL expression that METRIC(name) should expand to,
    along with metadata about the metric.
    """

    name: str = Field(
        ...,
        description="Metric identifier (unique)",
    )
    expression: str = Field(
        ...,
        description="SQL expression to substitute for METRIC(name)",
    )
    source_table: str | None = Field(
        default=None,
        description="Base table for the metric (optional)",
    )
    description: str | None = Field(
        default=None,
        description="Human-readable metric description",
    )


# =============================================================================
# Result Models
# =============================================================================


class TranspileMetadata(BaseModel):
    """Audit trail metadata for transpile operations.

    Captures timing, configuration, and version information
    for debugging and reproducibility.
    """

    original_sql: str = Field(
        ...,
        description="Original SQL before transpilation",
    )
    transpiled_at: datetime = Field(
        default_factory=_utc_now,
        description="UTC timestamp when transpilation occurred",
    )
    dialect: Dialect = Field(
        ...,
        description="SQL dialect used for parsing",
    )
    rules_version: str | None = Field(
        default=None,
        description="Server-provided rules version identifier",
    )
    duration_ms: int = Field(
        default=0,
        ge=0,
        description="Transpilation duration in milliseconds",
    )


class TranspileResult(BaseModel):
    """Complete transpile operation result.

    Contains the transpiled SQL, applied rules, warnings,
    and metadata for a single transpile operation.
    """

    success: bool = Field(
        ...,
        description="Whether transpilation completed successfully",
    )
    sql: str = Field(
        ...,
        description="Transpiled SQL (or original if failed)",
    )
    applied_rules: list[TranspileRule] = Field(
        default_factory=list,
        description="List of rules that were applied",
    )
    warnings: list[TranspileWarning] = Field(
        default_factory=list,
        description="List of advisory warnings",
    )
    metadata: TranspileMetadata = Field(
        ...,
        description="Audit trail metadata",
    )
    error: str | None = Field(
        default=None,
        description="Error message if transpilation failed",
    )

    def to_json(self) -> str:
        """Serialize result to JSON string."""
        return self.model_dump_json(indent=2)
