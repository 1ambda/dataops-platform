"""SQL Snippet API result models.

This module provides result models for SQL snippet operations (list, get, put).

Example:
    >>> from dli.models.sql import SqlListResult, SqlSnippetDetail, SqlDialect
    >>> result = api.list_snippets(project="my_project")
    >>> print(f"Found {result.total} snippets")
    >>> for snippet in result.snippets:
    ...     print(f"{snippet.id}: {snippet.name} ({snippet.dialect})")
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field

__all__ = [
    "SqlDialect",
    "SqlListResult",
    "SqlSnippetDetail",
    "SqlSnippetInfo",
    "SqlUpdateResult",
]


class SqlDialect(str, Enum):
    """SQL dialect for saved snippets.

    Represents the SQL dialect of a saved snippet on the server.

    Attributes:
        BIGQUERY: Google BigQuery SQL dialect.
        TRINO: Trino (formerly Presto) SQL dialect.
        SPARK: Apache Spark SQL dialect.
    """

    BIGQUERY = "bigquery"
    TRINO = "trino"
    SPARK = "spark"


class SqlSnippetInfo(BaseModel):
    """Summary information for list views.

    Contains basic metadata about a SQL snippet without the full SQL content.
    Used in list operations to minimize data transfer.

    Attributes:
        id: Unique snippet identifier.
        name: Human-readable snippet name.
        project: Project the snippet belongs to.
        folder: Optional folder path within the project.
        dialect: SQL dialect of the snippet.
        starred: Whether the snippet is starred/favorited.
        updated_at: When the snippet was last updated.
        updated_by: Username who last updated the snippet.

    Example:
        >>> info = SqlSnippetInfo(
        ...     id=1,
        ...     name="daily_revenue",
        ...     project="analytics",
        ...     dialect=SqlDialect.BIGQUERY,
        ...     starred=True,
        ...     updated_at=datetime.now(),
        ...     updated_by="alice"
        ... )
        >>> print(f"[{info.dialect.value}] {info.name}")
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="Unique snippet identifier")
    name: str = Field(description="Human-readable snippet name")
    project: str = Field(description="Project the snippet belongs to")
    folder: str | None = Field(default=None, description="Optional folder path")
    dialect: SqlDialect = Field(description="SQL dialect of the snippet")
    starred: bool = Field(default=False, description="Whether snippet is starred")
    updated_at: datetime = Field(description="Last update timestamp")
    updated_by: str = Field(description="Username who last updated")


class SqlSnippetDetail(BaseModel):
    """Full snippet details with SQL content.

    Contains complete information about a SQL snippet including the SQL source.
    Returned by get operations.

    Attributes:
        id: Unique snippet identifier.
        name: Human-readable snippet name.
        project: Project the snippet belongs to.
        folder: Optional folder path within the project.
        dialect: SQL dialect of the snippet.
        sql: The SQL source code.
        starred: Whether the snippet is starred/favorited.
        created_at: When the snippet was created.
        updated_at: When the snippet was last updated.
        created_by: Username who created the snippet.
        updated_by: Username who last updated the snippet.

    Example:
        >>> detail = api.get(snippet_id=123)
        >>> print(f"-- {detail.name} ({detail.dialect.value})")
        >>> print(detail.sql)
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="Unique snippet identifier")
    name: str = Field(description="Human-readable snippet name")
    project: str = Field(description="Project the snippet belongs to")
    folder: str | None = Field(default=None, description="Optional folder path")
    dialect: SqlDialect = Field(description="SQL dialect of the snippet")
    sql: str = Field(description="SQL source code")
    starred: bool = Field(default=False, description="Whether snippet is starred")
    created_at: datetime = Field(description="Creation timestamp")
    updated_at: datetime = Field(description="Last update timestamp")
    created_by: str = Field(description="Username who created the snippet")
    updated_by: str = Field(description="Username who last updated")


class SqlListResult(BaseModel):
    """List operation result.

    Contains a paginated list of SQL snippet summaries.

    Attributes:
        snippets: List of SqlSnippetInfo objects.
        total: Total count of matching snippets (across all pages).
        offset: Current page offset.
        limit: Maximum items per page.

    Example:
        >>> result = api.list_snippets(project="analytics", limit=10)
        >>> print(f"Showing {len(result.snippets)} of {result.total}")
        >>> for s in result.snippets:
        ...     print(f"  {s.id}: {s.name}")
    """

    model_config = ConfigDict(frozen=True)

    snippets: list[SqlSnippetInfo] = Field(
        default_factory=list, description="List of snippet summaries"
    )
    total: int = Field(description="Total count of matching snippets")
    offset: int = Field(default=0, description="Current page offset")
    limit: int = Field(default=20, description="Maximum items per page")


class SqlUpdateResult(BaseModel):
    """Update operation result.

    Contains confirmation of a successful snippet update.

    Attributes:
        id: ID of the updated snippet.
        name: Name of the updated snippet.
        updated_at: When the update occurred.
        updated_by: Username who performed the update.

    Example:
        >>> result = api.put(snippet_id=123, sql="SELECT * FROM users")
        >>> print(f"Updated snippet {result.id} at {result.updated_at}")
    """

    model_config = ConfigDict(frozen=True)

    id: int = Field(description="ID of the updated snippet")
    name: str = Field(description="Name of the updated snippet")
    updated_at: datetime = Field(description="When the update occurred")
    updated_by: str = Field(description="Username who performed the update")
