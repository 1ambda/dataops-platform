"""Catalog module for DLI CLI.

This module provides data catalog functionality for exploring
table metadata, ownership, quality, and lineage information.

Key Features:
- Server-based catalog lookup (all metadata from Basecamp API)
- Table listing with filtering by project, dataset, owner, team, tags
- Table detail view with all metadata sections
- Keyword search across tables, columns, descriptions, and tags

Note:
    This implementation is SERVER-BASED ONLY. All data comes from
    the Basecamp server API, not from direct query engine connections.
"""

from __future__ import annotations

from dli.core.catalog.models import (
    ColumnInfo,
    FreshnessInfo,
    ImpactSummary,
    OwnershipInfo,
    QualityInfo,
    QualityTestResult,
    SampleQuery,
    TableDetail,
    TableInfo,
)

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
