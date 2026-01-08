"""SQL Framework Adapters.

This module provides concrete executor implementations for various
database backends.

Available adapters:
- BigQueryExecutor: Google BigQuery executor (requires google-cloud-bigquery)
- TrinoExecutor: Trino executor (requires trino)
"""

from dli.adapters.bigquery import BigQueryExecutor
from dli.adapters.trino import TrinoExecutor

__all__ = ["BigQueryExecutor", "TrinoExecutor"]
