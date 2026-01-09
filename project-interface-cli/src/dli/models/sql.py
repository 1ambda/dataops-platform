"""SQL Worksheet API result models.

This module provides result models for SQL worksheet operations (list, get, put).

Example:
    >>> from dli.models.sql import SqlListResult, SqlWorksheetDetail, SqlDialect
    >>> result = api.list_worksheets(team="my_team")
    >>> print(f"Found {result.total} worksheets")
    >>> for worksheet in result.worksheets:
    ...     print(f"{worksheet.id}: {worksheet.name} ({worksheet.dialect})")
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "SqlDialect",
    "SqlListResult",
    "SqlWorksheetDetail",
    "SqlWorksheetInfo",
    "SqlUpdateResult",
]


class SqlDialect(str, Enum):
    """SQL dialect for saved worksheets.

    Represents the SQL dialect of a saved worksheet on the server.

    Attributes:
        BIGQUERY: Google BigQuery SQL dialect.
        TRINO: Trino (formerly Presto) SQL dialect.
        SPARK: Apache Spark SQL dialect.
    """

    BIGQUERY = "bigquery"
    TRINO = "trino"
    SPARK = "spark"


class SqlWorksheetInfo(BaseModel):
    """Summary information for list views.

    Contains basic metadata about a SQL worksheet without the full SQL content.
    Used in list operations to minimize data transfer.

    Attributes:
        id: Unique worksheet identifier.
        name: Human-readable worksheet name.
        team: Team the worksheet belongs to.
        folder: Optional folder path within the team.
        dialect: SQL dialect of the worksheet.
        starred: Whether the worksheet is starred/favorited.
        updated_at: When the worksheet was last updated.
        updated_by: Username who last updated the worksheet.

    Example:
        >>> info = SqlWorksheetInfo(
        ...     id=1,
        ...     name="daily_revenue",
        ...     team="analytics",
        ...     dialect=SqlDialect.BIGQUERY,
        ...     starred=True,
        ...     updated_at=datetime.now(),
        ...     updated_by="alice"
        ... )
        >>> print(f"[{info.dialect.value}] {info.name}")
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="Unique worksheet identifier")
    name: str = Field(description="Human-readable worksheet name")
    team: str = Field(description="Team the worksheet belongs to")
    folder: str | None = Field(default=None, description="Optional folder path")
    dialect: SqlDialect = Field(description="SQL dialect of the worksheet")
    starred: bool = Field(default=False, description="Whether worksheet is starred")
    updated_at: datetime = Field(description="Last update timestamp")
    updated_by: str = Field(description="Username who last updated")


class SqlWorksheetDetail(BaseModel):
    """Full worksheet details with SQL content.

    Contains complete information about a SQL worksheet including the SQL source.
    Returned by get operations.

    Attributes:
        id: Unique worksheet identifier.
        name: Human-readable worksheet name.
        team: Team the worksheet belongs to.
        folder: Optional folder path within the team.
        dialect: SQL dialect of the worksheet.
        sql: The SQL source code.
        starred: Whether the worksheet is starred/favorited.
        created_at: When the worksheet was created.
        updated_at: When the worksheet was last updated.
        created_by: Username who created the worksheet.
        updated_by: Username who last updated the worksheet.

    Example:
        >>> detail = api.get(worksheet_id=123)
        >>> print(f"-- {detail.name} ({detail.dialect.value})")
        >>> print(detail.sql)
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="Unique worksheet identifier")
    name: str = Field(description="Human-readable worksheet name")
    team: str = Field(description="Team the worksheet belongs to")
    folder: str | None = Field(default=None, description="Optional folder path")
    dialect: SqlDialect = Field(description="SQL dialect of the worksheet")
    sql: str = Field(description="SQL source code")
    starred: bool = Field(default=False, description="Whether worksheet is starred")
    created_at: datetime = Field(description="Creation timestamp")
    updated_at: datetime = Field(description="Last update timestamp")
    created_by: str = Field(description="Username who created the worksheet")
    updated_by: str = Field(description="Username who last updated")


class SqlListResult(BaseModel):
    """List operation result.

    Contains a paginated list of SQL worksheet summaries.

    Attributes:
        worksheets: List of SqlWorksheetInfo objects.
        total: Total count of matching worksheets (across all pages).
        offset: Current page offset.
        limit: Maximum items per page.

    Example:
        >>> result = api.list_worksheets(team="analytics", limit=10)
        >>> print(f"Showing {len(result.worksheets)} of {result.total}")
        >>> for w in result.worksheets:
        ...     print(f"  {w.id}: {w.name}")
    """

    model_config = ConfigDict(frozen=True)

    worksheets: list[SqlWorksheetInfo] = Field(
        default_factory=list, description="List of worksheet summaries"
    )
    total: int = Field(description="Total count of matching worksheets")
    offset: int = Field(default=0, description="Current page offset")
    limit: int = Field(default=20, description="Maximum items per page")


class SqlUpdateResult(BaseModel):
    """Update operation result.

    Contains confirmation of a successful worksheet update.

    Attributes:
        id: ID of the updated worksheet.
        name: Name of the updated worksheet.
        updated_at: When the update occurred.
        updated_by: Username who performed the update.

    Example:
        >>> result = api.put(worksheet_id=123, sql="SELECT * FROM users")
        >>> print(f"Updated worksheet {result.id} at {result.updated_at}")
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="ID of the updated worksheet")
    name: str = Field(description="Name of the updated worksheet")
    updated_at: datetime = Field(description="When the update occurred")
    updated_by: str = Field(description="Username who performed the update")
