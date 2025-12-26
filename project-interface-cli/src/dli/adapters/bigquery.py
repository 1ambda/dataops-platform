"""BigQuery executor for the SQL Framework.

This module provides a BigQuery implementation of the BaseExecutor.
The google-cloud-bigquery package is an optional dependency.
"""

from __future__ import annotations

from concurrent.futures import TimeoutError as FuturesTimeoutError
import time
from typing import TYPE_CHECKING, Any

from dli.core.executor import BaseExecutor
from dli.core.models import ExecutionResult

# Optional BigQuery dependency
BIGQUERY_AVAILABLE = False
_bigquery_module: Any = None
_google_exceptions: Any = None

try:
    from google.api_core import exceptions as _gexc
    from google.cloud import bigquery as _bq

    _bigquery_module = _bq
    _google_exceptions = _gexc
    BIGQUERY_AVAILABLE = True
except ImportError:
    pass

if TYPE_CHECKING:
    from google.cloud import bigquery as _bigquery_module


class BigQueryExecutor(BaseExecutor):
    """Google BigQuery query executor.

    This executor requires the google-cloud-bigquery package to be installed.
    Install with: uv add google-cloud-bigquery

    Attributes:
        project: GCP project ID
        location: BigQuery location (default: US)
        client: BigQuery client instance
    """

    def __init__(self, project: str, location: str = "US"):
        """Initialize the BigQuery executor.

        Args:
            project: GCP project ID
            location: BigQuery location

        Raises:
            ImportError: If google-cloud-bigquery is not installed
        """
        if not BIGQUERY_AVAILABLE:
            msg = (
                "google-cloud-bigquery is not installed. "
                "Install it with: uv add google-cloud-bigquery"
            )
            raise ImportError(msg)

        self.project = project
        self.location = location
        self.client = _bigquery_module.Client(project=project, location=location)

    def execute(self, sql: str, timeout: int = 300) -> ExecutionResult:
        """Execute a SQL query on BigQuery.

        Args:
            sql: SQL query to execute
            timeout: Execution timeout in seconds

        Returns:
            ExecutionResult with query results
        """
        start = time.time()

        try:
            job = self.client.query(sql)
            results = job.result(timeout=timeout)

            # Convert results to list of dictionaries
            rows = [dict(row.items()) for row in results]
            columns = (
                [field.name for field in results.schema]
                if results.schema
                else []
            )

            return ExecutionResult(
                query_name="",
                success=True,
                row_count=len(rows),
                columns=columns,
                data=rows,
                rendered_sql=sql,
                execution_time_ms=int((time.time() - start) * 1000),
            )

        except FuturesTimeoutError:
            return ExecutionResult(
                query_name="",
                success=False,
                error_message=f"Query timed out after {timeout} seconds",
                rendered_sql=sql,
                execution_time_ms=int((time.time() - start) * 1000),
            )
        except Exception as e:
            # Catch BigQuery API errors and other unexpected errors
            return ExecutionResult(
                query_name="",
                success=False,
                error_message=str(e),
                rendered_sql=sql,
                execution_time_ms=int((time.time() - start) * 1000),
            )

    def dry_run(self, sql: str) -> dict[str, Any]:
        """Perform a dry run to estimate query cost.

        Args:
            sql: SQL query to validate

        Returns:
            Dictionary with validation status and cost estimate
        """
        try:
            job_config = _bigquery_module.QueryJobConfig(
                dry_run=True,
                use_query_cache=False,
            )
            job = self.client.query(sql, job_config=job_config)

            bytes_processed = job.total_bytes_processed or 0
            # BigQuery pricing: $5 per TB processed
            estimated_cost = (bytes_processed / 1e12) * 5

            return {
                "valid": True,
                "bytes_processed": bytes_processed,
                "bytes_processed_gb": bytes_processed / 1e9,
                "estimated_cost_usd": estimated_cost,
            }

        except Exception as e:
            # Catch BigQuery API errors (BadRequest, NotFound, etc.)
            return {
                "valid": False,
                "error": str(e),
            }

    def test_connection(self) -> bool:
        """Test connection to BigQuery.

        Returns:
            True if connection is successful
        """
        try:
            # Execute a simple query to test connection
            result = self.client.query("SELECT 1").result()
            return list(result) is not None
        except Exception:
            # Any error indicates connection failure
            return False

    def get_table_schema(self, table_id: str) -> list[dict[str, str]]:
        """Get the schema of a BigQuery table.

        Args:
            table_id: Fully qualified table ID (project.dataset.table)

        Returns:
            List of column definitions with name and type
        """
        try:
            table = self.client.get_table(table_id)
            return [
                {"name": field.name, "type": field.field_type}
                for field in table.schema
            ]
        except Exception:
            # Table not found or access denied
            return []
