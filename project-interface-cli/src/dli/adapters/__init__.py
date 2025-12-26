"""SQL Framework Adapters.

This module provides concrete executor implementations for various
database backends.

Available adapters:
- BigQueryExecutor: Google BigQuery executor (requires google-cloud-bigquery)
"""

from dli.adapters.bigquery import BigQueryExecutor

__all__ = ["BigQueryExecutor"]
