"""Catalog Models for the DLI Core Engine.

This module contains the core models for catalog operations, including
table information, column metadata, ownership, quality, and freshness tracking.

Models:
    ColumnInfo: Column metadata with PII and statistics
    OwnershipInfo: Table ownership information
    FreshnessInfo: Table freshness and update tracking
    QualityInfo: Quality score and test results
    ImpactSummary: Downstream impact analysis
    SampleQuery: Popular query examples
    TableInfo: Lightweight table info for list views
    TableDetail: Complete table details for detail view

References:
    - Feature Spec: features/FEATURE_CATALOG.md
"""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class ColumnInfo(BaseModel):
    """Column metadata including statistics and PII classification.

    Attributes:
        name: Column name
        data_type: SQL data type (e.g., 'STRING', 'INT64', 'TIMESTAMP')
        description: Human-readable column description
        is_pii: Whether column contains PII data (server determines this)
        fill_rate: Percentage of non-null values (0.0 - 1.0)
        distinct_count: Number of distinct values
    """

    name: str = Field(description="Column name")
    data_type: str = Field(description="SQL data type")
    description: str | None = Field(default=None, description="Column description")
    is_pii: bool = Field(default=False, description="Whether column contains PII")
    fill_rate: float | None = Field(
        default=None, ge=0.0, le=1.0, description="Non-null value percentage"
    )
    distinct_count: int | None = Field(
        default=None, ge=0, description="Number of distinct values"
    )

    @property
    def pii_indicator(self) -> str:
        """Return PII indicator for display."""
        return "[lock]" if self.is_pii else ""

    @property
    def fill_rate_percent(self) -> str:
        """Return fill rate as percentage string."""
        if self.fill_rate is None:
            return "-"
        return f"{self.fill_rate * 100:.1f}%"


class OwnershipInfo(BaseModel):
    """Table ownership and stewardship information.

    Attributes:
        owner: Primary owner (usually email)
        team: Owning team (usually @mention format)
        stewards: List of data stewards
        consumers: Known consumer teams/users
    """

    owner: str | None = Field(default=None, description="Primary owner")
    team: str | None = Field(default=None, description="Owning team")
    stewards: list[str] = Field(
        default_factory=list, description="Data stewards responsible for quality"
    )
    consumers: list[str] = Field(
        default_factory=list, description="Known consumer teams/users"
    )


class FreshnessInfo(BaseModel):
    """Table freshness and update tracking.

    Attributes:
        last_updated: Last data update timestamp
        avg_update_lag_hours: Average time between source and table update
        update_frequency: Expected update frequency description
        is_stale: Whether table is considered stale
        stale_threshold_hours: Hours after which table is considered stale
    """

    last_updated: datetime | None = Field(
        default=None, description="Last data update timestamp"
    )
    avg_update_lag_hours: float | None = Field(
        default=None, ge=0, description="Average update lag in hours"
    )
    update_frequency: str | None = Field(
        default=None, description="Expected update frequency (e.g., 'daily', 'hourly')"
    )
    is_stale: bool = Field(default=False, description="Whether data is considered stale")
    stale_threshold_hours: int | None = Field(
        default=None, ge=0, description="Hours threshold for staleness"
    )

    @property
    def freshness_status(self) -> str:
        """Return freshness status for display."""
        if self.is_stale:
            return "stale"
        return "fresh"


class QualityTestResult(BaseModel):
    """Individual quality test result.

    Attributes:
        test_name: Name of the test
        test_type: Type of test (not_null, unique, etc.)
        status: Test status (pass, fail, warn)
        failed_rows: Number of failing rows
    """

    test_name: str = Field(description="Test name")
    test_type: str = Field(description="Test type")
    status: Literal["pass", "fail", "warn"] = Field(description="Test result status")
    failed_rows: int = Field(default=0, ge=0, description="Number of failing rows")


class QualityInfo(BaseModel):
    """Quality score and test results summary.

    Attributes:
        score: Overall quality score (0-100)
        total_tests: Total number of tests
        passed_tests: Number of passing tests
        failed_tests: Number of failing tests
        warnings: Number of warning tests
        recent_tests: Recent test results
    """

    score: int | None = Field(
        default=None, ge=0, le=100, description="Overall quality score (0-100)"
    )
    total_tests: int = Field(default=0, ge=0, description="Total number of tests")
    passed_tests: int = Field(default=0, ge=0, description="Passing tests count")
    failed_tests: int = Field(default=0, ge=0, description="Failing tests count")
    warnings: int = Field(default=0, ge=0, description="Warning tests count")
    recent_tests: list[QualityTestResult] = Field(
        default_factory=list, description="Recent test results"
    )

    @property
    def score_status(self) -> str:
        """Return score status for display styling."""
        if self.score is None:
            return "unknown"
        if self.score >= 90:
            return "excellent"
        if self.score >= 70:
            return "good"
        if self.score >= 50:
            return "fair"
        return "poor"


class ImpactSummary(BaseModel):
    """Downstream impact analysis summary.

    Generated from LineageClient.get_downstream() results.

    Attributes:
        total_downstream: Total downstream dependencies
        tables: Affected table names
        datasets: Affected dataset names
        metrics: Affected metric names
        dashboards: Affected dashboard names
    """

    total_downstream: int = Field(
        default=0, ge=0, description="Total downstream dependencies"
    )
    tables: list[str] = Field(default_factory=list, description="Affected tables")
    datasets: list[str] = Field(default_factory=list, description="Affected datasets")
    metrics: list[str] = Field(default_factory=list, description="Affected metrics")
    dashboards: list[str] = Field(
        default_factory=list, description="Affected dashboards"
    )


class SampleQuery(BaseModel):
    """Popular query example from the catalog.

    Attributes:
        title: Query title/description
        sql: SQL query text
        author: Query author
        run_count: Number of times this query was executed
        last_run: Last execution timestamp
    """

    title: str = Field(description="Query title or description")
    sql: str = Field(description="SQL query text")
    author: str | None = Field(default=None, description="Query author")
    run_count: int = Field(default=0, ge=0, description="Execution count")
    last_run: datetime | None = Field(
        default=None, description="Last execution timestamp"
    )


class TableInfo(BaseModel):
    """Lightweight table information for list views.

    Used in catalog list and search results.

    Attributes:
        name: Fully qualified table name (project.dataset.table)
        engine: Query engine (bigquery, trino, hive)
        owner: Primary owner
        team: Owning team
        tags: List of tags
        row_count: Approximate row count
        last_updated: Last data update timestamp
    """

    name: str = Field(description="Fully qualified table name")
    engine: str = Field(description="Query engine (bigquery, trino, hive)")
    owner: str | None = Field(default=None, description="Primary owner")
    team: str | None = Field(default=None, description="Owning team")
    tags: list[str] = Field(default_factory=list, description="Table tags")
    row_count: int | None = Field(default=None, ge=0, description="Approximate row count")
    last_updated: datetime | None = Field(
        default=None, description="Last data update timestamp"
    )

    @property
    def project(self) -> str:
        """Extract project from name."""
        parts = self.name.split(".")
        return parts[0] if parts else ""

    @property
    def dataset(self) -> str:
        """Extract dataset from name."""
        parts = self.name.split(".")
        return parts[1] if len(parts) > 1 else ""

    @property
    def table(self) -> str:
        """Extract table from name."""
        parts = self.name.split(".")
        return parts[2] if len(parts) > 2 else ""


class TableDetail(BaseModel):
    """Complete table details for detail view.

    Contains all available metadata about a table.

    Attributes:
        name: Fully qualified table name
        engine: Query engine
        description: Human-readable table description
        tags: List of tags
        basecamp_url: URL to view in Basecamp UI
        ownership: Ownership information
        columns: Column metadata list
        freshness: Freshness tracking information
        quality: Quality score and test results
        impact: Downstream impact summary
        sample_queries: Popular query examples
        sample_data: Sample row data (when --sample is used)
    """

    name: str = Field(description="Fully qualified table name")
    engine: str = Field(description="Query engine (bigquery, trino, hive)")
    description: str | None = Field(default=None, description="Table description")
    tags: list[str] = Field(default_factory=list, description="Table tags")
    basecamp_url: str = Field(description="URL to view in Basecamp UI")

    ownership: OwnershipInfo = Field(
        default_factory=OwnershipInfo, description="Ownership information"
    )
    columns: list[ColumnInfo] = Field(
        default_factory=list, description="Column metadata"
    )
    freshness: FreshnessInfo = Field(
        default_factory=FreshnessInfo, description="Freshness information"
    )
    quality: QualityInfo = Field(
        default_factory=QualityInfo, description="Quality information"
    )
    impact: ImpactSummary = Field(
        default_factory=ImpactSummary, description="Impact summary"
    )
    sample_queries: list[SampleQuery] = Field(
        default_factory=list, description="Popular queries"
    )
    sample_data: list[dict] | None = Field(
        default=None, description="Sample data rows (PII masked by server)"
    )

    @property
    def project(self) -> str:
        """Extract project from name."""
        parts = self.name.split(".")
        return parts[0] if parts else ""

    @property
    def dataset(self) -> str:
        """Extract dataset from name."""
        parts = self.name.split(".")
        return parts[1] if len(parts) > 1 else ""

    @property
    def table(self) -> str:
        """Extract table from name."""
        parts = self.name.split(".")
        return parts[2] if len(parts) > 2 else ""

    @property
    def pii_column_count(self) -> int:
        """Count columns marked as PII."""
        return sum(1 for c in self.columns if c.is_pii)


__all__ = [
    "ColumnInfo",
    "FreshnessInfo",
    "ImpactSummary",
    "OwnershipInfo",
    "QualityInfo",
    "QualityTestResult",
    "SampleQuery",
    "TableDetail",
    "TableInfo",
]
