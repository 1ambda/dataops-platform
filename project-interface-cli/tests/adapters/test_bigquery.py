"""Tests for BigQueryExecutor adapter.

This module tests the BigQuery executor with mocked google-cloud-bigquery client.
"""

from __future__ import annotations

from concurrent.futures import TimeoutError as FuturesTimeoutError
from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import pytest

if TYPE_CHECKING:
    from dli.adapters.bigquery import BigQueryExecutor


class TestBigQueryAvailability:
    """Tests for optional BigQuery dependency handling."""

    def test_bigquery_available_flag(self) -> None:
        """BIGQUERY_AVAILABLE should indicate import status."""
        from dli.adapters import bigquery

        # Flag should be boolean
        assert isinstance(bigquery.BIGQUERY_AVAILABLE, bool)

    def test_import_error_when_dependency_missing(self) -> None:
        """Should raise ImportError when google-cloud-bigquery not installed."""
        with patch.dict("sys.modules", {"google.cloud": None, "google.api_core": None}):
            # Simulate missing dependency by patching the flag
            with patch("dli.adapters.bigquery.BIGQUERY_AVAILABLE", False):
                from dli.adapters.bigquery import BigQueryExecutor

                with pytest.raises(ImportError) as exc_info:
                    BigQueryExecutor(project="test-project")

                assert "google-cloud-bigquery" in str(exc_info.value)
                assert "uv add google-cloud-bigquery" in str(exc_info.value)


@pytest.fixture
def mock_bigquery_client() -> MagicMock:
    """Create a mocked BigQuery client."""
    client = MagicMock()
    return client


@pytest.fixture
def mock_bigquery_module(mock_bigquery_client: MagicMock) -> MagicMock:
    """Create a mocked bigquery module with Client class."""
    module = MagicMock()
    module.Client.return_value = mock_bigquery_client
    module.QueryJobConfig = MagicMock()
    return module


@pytest.fixture
def bigquery_executor(mock_bigquery_module: MagicMock) -> "BigQueryExecutor":
    """Create a BigQueryExecutor with mocked dependencies."""
    with (
        patch("dli.adapters.bigquery.BIGQUERY_AVAILABLE", True),
        patch("dli.adapters.bigquery._bigquery_module", mock_bigquery_module),
    ):
        from dli.adapters.bigquery import BigQueryExecutor

        executor = BigQueryExecutor(project="test-project", location="US")
        # Replace client with mock after initialization
        executor.client = mock_bigquery_module.Client.return_value
        return executor


class TestBigQueryExecutorInit:
    """Tests for BigQueryExecutor initialization."""

    def test_init_with_defaults(self, mock_bigquery_module: MagicMock) -> None:
        """Should initialize with default location."""
        with (
            patch("dli.adapters.bigquery.BIGQUERY_AVAILABLE", True),
            patch("dli.adapters.bigquery._bigquery_module", mock_bigquery_module),
        ):
            from dli.adapters.bigquery import BigQueryExecutor

            executor = BigQueryExecutor(project="my-project")

            assert executor.project == "my-project"
            assert executor.location == "US"
            mock_bigquery_module.Client.assert_called_once_with(
                project="my-project",
                location="US",
            )

    def test_init_with_custom_location(self, mock_bigquery_module: MagicMock) -> None:
        """Should initialize with custom location."""
        with (
            patch("dli.adapters.bigquery.BIGQUERY_AVAILABLE", True),
            patch("dli.adapters.bigquery._bigquery_module", mock_bigquery_module),
        ):
            from dli.adapters.bigquery import BigQueryExecutor

            executor = BigQueryExecutor(project="my-project", location="EU")

            assert executor.location == "EU"
            mock_bigquery_module.Client.assert_called_once_with(
                project="my-project",
                location="EU",
            )


class TestBigQueryExecutorExecuteSql:
    """Tests for BigQueryExecutor.execute_sql() method."""

    def test_execute_sql_success(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return successful ExecutionResult with data."""
        # Setup mock results
        mock_row1 = MagicMock()
        mock_row1.items.return_value = [("id", 1), ("name", "Alice")]
        mock_row2 = MagicMock()
        mock_row2.items.return_value = [("id", 2), ("name", "Bob")]

        mock_field1 = MagicMock()
        mock_field1.name = "id"
        mock_field2 = MagicMock()
        mock_field2.name = "name"

        mock_results = MagicMock()
        mock_results.__iter__ = lambda self: iter([mock_row1, mock_row2])
        mock_results.schema = [mock_field1, mock_field2]

        mock_job = MagicMock()
        mock_job.result.return_value = mock_results
        mock_bigquery_client.query.return_value = mock_job

        # Execute
        result = bigquery_executor.execute_sql("SELECT * FROM users")

        # Verify
        assert result.success is True
        assert result.row_count == 2
        assert result.columns == ["id", "name"]
        assert result.data == [
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
        ]
        assert result.rendered_sql == "SELECT * FROM users"
        assert result.execution_time_ms >= 0

    def test_execute_sql_empty_result(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should handle empty result set."""
        mock_results = MagicMock()
        mock_results.__iter__ = lambda self: iter([])
        mock_results.schema = []

        mock_job = MagicMock()
        mock_job.result.return_value = mock_results
        mock_bigquery_client.query.return_value = mock_job

        result = bigquery_executor.execute_sql("SELECT * FROM empty_table")

        assert result.success is True
        assert result.row_count == 0
        assert result.columns == []
        assert result.data == []

    def test_execute_sql_timeout(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should handle query timeout gracefully."""
        mock_job = MagicMock()
        mock_job.result.side_effect = FuturesTimeoutError()
        mock_bigquery_client.query.return_value = mock_job

        result = bigquery_executor.execute_sql("SELECT * FROM slow_query", timeout=10)

        assert result.success is False
        assert "timed out" in result.error_message.lower()
        assert "10" in result.error_message

    def test_execute_sql_api_error(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should handle BigQuery API errors."""
        mock_job = MagicMock()
        mock_job.result.side_effect = Exception("Table not found: dataset.table")
        mock_bigquery_client.query.return_value = mock_job

        result = bigquery_executor.execute_sql("SELECT * FROM missing_table")

        assert result.success is False
        assert "Table not found" in result.error_message


class TestBigQueryExecutorDryRun:
    """Tests for BigQueryExecutor.dry_run() method."""

    def test_dry_run_valid_query(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
        mock_bigquery_module: MagicMock,
    ) -> None:
        """Should return cost estimate for valid query."""
        mock_job = MagicMock()
        mock_job.total_bytes_processed = 1_000_000_000  # 1 GB
        mock_bigquery_client.query.return_value = mock_job

        # Inject the mock module for QueryJobConfig
        with patch(
            "dli.adapters.bigquery._bigquery_module",
            mock_bigquery_module,
        ):
            result = bigquery_executor.dry_run("SELECT * FROM large_table")

        assert result["valid"] is True
        assert result["bytes_processed"] == 1_000_000_000
        assert result["bytes_processed_gb"] == 1.0
        # 1 TB = $5, so 1 GB = $0.005
        assert result["estimated_cost_usd"] == pytest.approx(0.005, rel=1e-3)

    def test_dry_run_zero_bytes(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
        mock_bigquery_module: MagicMock,
    ) -> None:
        """Should handle cached/zero-cost queries."""
        mock_job = MagicMock()
        mock_job.total_bytes_processed = 0
        mock_bigquery_client.query.return_value = mock_job

        with patch(
            "dli.adapters.bigquery._bigquery_module",
            mock_bigquery_module,
        ):
            result = bigquery_executor.dry_run("SELECT 1")

        assert result["valid"] is True
        assert result["bytes_processed"] == 0
        assert result["estimated_cost_usd"] == 0.0

    def test_dry_run_invalid_query(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
        mock_bigquery_module: MagicMock,
    ) -> None:
        """Should return error for invalid SQL."""
        mock_bigquery_client.query.side_effect = Exception(
            "Syntax error at position 10"
        )

        with patch(
            "dli.adapters.bigquery._bigquery_module",
            mock_bigquery_module,
        ):
            result = bigquery_executor.dry_run("SELECT * FORM users")

        assert result["valid"] is False
        assert "Syntax error" in result["error"]


class TestBigQueryExecutorTestConnection:
    """Tests for BigQueryExecutor.test_connection() method."""

    def test_connection_success(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return True on successful connection."""
        mock_result = MagicMock()
        mock_result.__iter__ = lambda self: iter([MagicMock()])

        mock_job = MagicMock()
        mock_job.result.return_value = mock_result
        mock_bigquery_client.query.return_value = mock_job

        result = bigquery_executor.test_connection()

        assert result is True
        mock_bigquery_client.query.assert_called_once_with("SELECT 1")

    def test_connection_failure(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return False on connection error."""
        mock_bigquery_client.query.side_effect = Exception("Network error")

        result = bigquery_executor.test_connection()

        assert result is False


class TestBigQueryExecutorGetTableSchema:
    """Tests for BigQueryExecutor.get_table_schema() method."""

    def test_get_schema_success(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return schema for existing table."""
        mock_field1 = MagicMock()
        mock_field1.name = "id"
        mock_field1.field_type = "INTEGER"
        mock_field2 = MagicMock()
        mock_field2.name = "name"
        mock_field2.field_type = "STRING"
        mock_field3 = MagicMock()
        mock_field3.name = "created_at"
        mock_field3.field_type = "TIMESTAMP"

        mock_table = MagicMock()
        mock_table.schema = [mock_field1, mock_field2, mock_field3]
        mock_bigquery_client.get_table.return_value = mock_table

        result = bigquery_executor.get_table_schema("project.dataset.users")

        assert result == [
            {"name": "id", "type": "INTEGER"},
            {"name": "name", "type": "STRING"},
            {"name": "created_at", "type": "TIMESTAMP"},
        ]
        mock_bigquery_client.get_table.assert_called_once_with("project.dataset.users")

    def test_get_schema_table_not_found(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return empty list for non-existent table."""
        mock_bigquery_client.get_table.side_effect = Exception("Table not found")

        result = bigquery_executor.get_table_schema("project.dataset.missing_table")

        assert result == []

    def test_get_schema_access_denied(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Should return empty list on access denied."""
        mock_bigquery_client.get_table.side_effect = Exception(
            "Access Denied: Table project.dataset.private_table"
        )

        result = bigquery_executor.get_table_schema("project.dataset.private_table")

        assert result == []


class TestBigQueryExecutorIntegration:
    """Integration-like tests verifying executor behavior patterns."""

    def test_execute_preserves_sql_in_result(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Executed SQL should be preserved in result for debugging."""
        mock_results = MagicMock()
        mock_results.__iter__ = lambda self: iter([])
        mock_results.schema = []

        mock_job = MagicMock()
        mock_job.result.return_value = mock_results
        mock_bigquery_client.query.return_value = mock_job

        original_sql = "SELECT id, name FROM users WHERE status = 'active'"
        result = bigquery_executor.execute_sql(original_sql)

        assert result.rendered_sql == original_sql

    def test_execution_time_is_measured(
        self,
        bigquery_executor: "BigQueryExecutor",
        mock_bigquery_client: MagicMock,
    ) -> None:
        """Execution time should be measured and reported."""
        mock_results = MagicMock()
        mock_results.__iter__ = lambda self: iter([])
        mock_results.schema = []

        mock_job = MagicMock()
        mock_job.result.return_value = mock_results
        mock_bigquery_client.query.return_value = mock_job

        result = bigquery_executor.execute_sql("SELECT 1")

        # Execution time should be non-negative
        assert result.execution_time_ms >= 0
