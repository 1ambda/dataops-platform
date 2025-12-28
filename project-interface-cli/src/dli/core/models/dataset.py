"""Dataset-related models for the DLI Core Engine.

This module contains:
- DatasetSpec: Dataset specification (type: Dataset, query_type: DML only)
"""

from __future__ import annotations

from pydantic import Field, model_validator

from dli.core.models.base import QueryType, SpecType, StatementDefinition
from dli.core.models.spec import SpecBase


class DatasetSpec(SpecBase):
    """Dataset specification for data processing operations (type: Dataset).

    DatasetSpec is designed for data processing pipelines with DML operations
    (INSERT, UPDATE, DELETE, MERGE). It supports:
    - type: Dataset (automatically set)
    - query_type: DML (data modification only)
    - Pre/Post statements for data processing workflows
    - No metrics/dimensions (those belong to MetricSpec)

    DATASET-SPECIFIC FIELDS:
        pre_statements: Pre-execution statements (e.g., DELETE partition)
        post_statements: Post-execution statements (e.g., OPTIMIZE table)

    File naming convention: dataset.{catalog}.{schema}.{name}.yaml

    Example:
        >>> spec = DatasetSpec(
        ...     name="iceberg.analytics.daily_clicks",
        ...     owner="engineer@example.com",
        ...     team="@data-engineering",
        ...     query_file="daily_clicks.sql",
        ...     pre_statements=[
        ...         StatementDefinition(
        ...             name="delete_partition",
        ...             sql="DELETE FROM t WHERE dt = '{{ execution_date }}'",
        ...         ),
        ...     ],
        ...     post_statements=[
        ...         StatementDefinition(
        ...             name="optimize",
        ...             sql="ALTER TABLE t EXECUTE optimize",
        ...             continue_on_error=True,
        ...         ),
        ...     ],
        ... )
    """

    # Override type and query_type with defaults for datasets
    type: SpecType = Field(default=SpecType.DATASET, description="Spec type (always Dataset)")
    query_type: QueryType = Field(default=QueryType.DML, description="Query type (always DML for datasets)")

    # Dataset-specific fields (data processing)
    pre_statements: list[StatementDefinition] = Field(
        default_factory=list,
        description="Pre-execution statements (e.g., DELETE partition)"
    )
    post_statements: list[StatementDefinition] = Field(
        default_factory=list,
        description="Post-execution statements (e.g., OPTIMIZE table)"
    )

    @model_validator(mode='after')
    def validate_dataset_constraints(self) -> DatasetSpec:
        """Validate dataset-specific constraints.

        Datasets should not have metrics or dimensions - those belong to MetricSpec.
        """
        return self
