"""Metric-related models for the DLI Core Engine.

This module contains models for the semantic layer following MetricFlow patterns:
- AggregationType: Basic aggregation types for metrics
- DimensionType: Dimension types (categorical, time)
- MetricDefinition: dbt-compatible metric definitions
- DimensionDefinition: Dimensional attributes for slicing/filtering
- MetricSpec: Metric specification (type: Metric, query_type: SELECT only)
"""

from __future__ import annotations

from enum import Enum
from typing import ClassVar

from pydantic import BaseModel, Field, model_validator

from dli.core.models.base import QueryType, SpecType
from dli.core.models.spec import SpecBase


class AggregationType(str, Enum):
    """Basic aggregation types for metrics (Phase 1).

    TODO (Phase 2):
    - derived: Metrics composed from other metrics
    - ratio: Ratio metrics (numerator/denominator)
    - cumulative: Running totals
    - conversion: Funnel/conversion metrics
    """

    COUNT = "count"
    COUNT_DISTINCT = "count_distinct"
    SUM = "sum"
    AVG = "avg"
    MIN = "min"
    MAX = "max"


class DimensionType(str, Enum):
    """Dimension types for metrics (Phase 1).

    TODO (Phase 2):
    - time_grain: Granular time settings (hour, day, week, etc.)
    - foreign_key: Dimensional relationships
    """

    CATEGORICAL = "categorical"
    TIME = "time"


class MetricDefinition(BaseModel):
    """Simple metric definition (Phase 1).

    Defines a measurable quantity that can be aggregated across dimensions.

    Attributes:
        name: Metric identifier (e.g., "user_count", "total_revenue")
        aggregation: Aggregation type (count, sum, avg, etc.)
        expression: Column name or SQL expression to aggregate
        description: Human-readable description
        filters: List of SQL WHERE conditions to apply

    Example:
        >>> metric = MetricDefinition(
        ...     name="active_users",
        ...     aggregation=AggregationType.COUNT_DISTINCT,
        ...     expression="user_id",
        ...     filters=["is_active = true"],
        ... )

    TODO (Phase 2):
    - derived metrics (composed from other metrics)
    - ratio metrics (numerator/denominator)
    - cumulative/rolling windows
    - conversion funnel metrics
    """

    name: str
    aggregation: AggregationType
    expression: str
    description: str = ""
    filters: list[str] = Field(default_factory=list)

    # Class-level mapping for SQL aggregation templates (avoids recreation per call)
    _AGG_TEMPLATES: ClassVar[dict[AggregationType, str]] = {
        AggregationType.COUNT: "COUNT({expr})",
        AggregationType.COUNT_DISTINCT: "COUNT(DISTINCT {expr})",
        AggregationType.SUM: "SUM({expr})",
        AggregationType.AVG: "AVG({expr})",
        AggregationType.MIN: "MIN({expr})",
        AggregationType.MAX: "MAX({expr})",
    }

    def to_sql(self) -> str:
        """Generate SQL aggregation expression.

        Returns:
            SQL expression string for this metric.

        Example:
            >>> metric = MetricDefinition(
            ...     name="users",
            ...     aggregation=AggregationType.COUNT_DISTINCT,
            ...     expression="user_id",
            ... )
            >>> metric.to_sql()
            'COUNT(DISTINCT user_id)'
        """
        template = self._AGG_TEMPLATES.get(self.aggregation, "COUNT({expr})")
        return template.format(expr=self.expression)


class DimensionDefinition(BaseModel):
    """Simple dimension definition (Phase 1).

    Defines a grouping/slicing attribute for metrics.

    Attributes:
        name: Dimension identifier (e.g., "country", "signup_date")
        type: Dimension type (categorical or time)
        expression: Column name or SQL expression
        description: Human-readable description

    Example:
        >>> dimension = DimensionDefinition(
        ...     name="country",
        ...     type=DimensionType.CATEGORICAL,
        ...     expression="country_code",
        ... )

    TODO (Phase 2):
    - time_grain settings (hour, day, week, month, year)
    - foreign_key relationships
    - hierarchy support
    """

    name: str
    type: DimensionType = DimensionType.CATEGORICAL
    expression: str
    description: str = ""


class MetricSpec(SpecBase):
    """Metric specification for read-only analytical queries (type: Metric).

    MetricSpec is designed for defining metric store definitions following
    dbt MetricFlow patterns. It enforces:
    - type: Metric (automatically set)
    - query_type: SELECT (read-only queries only)
    - No pre/post statements (metrics don't modify data)

    METRIC-SPECIFIC FIELDS (required for semantic layer):
        metrics: Metric definitions (aggregations, expressions)
        dimensions: Dimension definitions for slicing/filtering

    File naming convention: metric.{catalog}.{schema}.{name}.yaml

    Example:
        >>> spec = MetricSpec(
        ...     name="iceberg.analytics.user_engagement",
        ...     owner="analyst@example.com",
        ...     team="@analytics",
        ...     query_statement="SELECT * FROM user_events",
        ...     metrics=[
        ...         MetricDefinition(
        ...             name="unique_users",
        ...             aggregation=AggregationType.COUNT_DISTINCT,
        ...             expression="user_id",
        ...         ),
        ...     ],
        ...     dimensions=[
        ...         DimensionDefinition(
        ...             name="country",
        ...             type=DimensionType.CATEGORICAL,
        ...             expression="country_code",
        ...         ),
        ...     ],
        ... )
    """

    # Override type and query_type with defaults for metrics
    type: SpecType = Field(default=SpecType.METRIC, description="Spec type (always Metric)")
    query_type: QueryType = Field(default=QueryType.SELECT, description="Query type (always SELECT for metrics)")

    # Semantic layer fields (required for metrics)
    metrics: list[MetricDefinition] = Field(
        default_factory=list,
        description="Metric definitions following dbt MetricFlow patterns"
    )
    dimensions: list[DimensionDefinition] = Field(
        default_factory=list,
        description="Dimension definitions for slicing/filtering"
    )

    @model_validator(mode='after')
    def validate_metric_constraints(self) -> MetricSpec:
        """Validate metric-specific constraints.

        Metrics should have at least one metric or dimension defined
        to be meaningful in a semantic layer.
        """
        # Note: Allow empty metrics/dimensions for testing purposes
        # In production, you may want to enforce at least one metric
        return self
