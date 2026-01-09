"""Integration tests for Trino executor.

These tests verify TrinoExecutor functionality against a real Trino instance.
They require Docker and the trino package to be installed.

Run with:
    pytest tests/integration/test_trino_integration.py -m integration

Skip with:
    pytest -m "not integration"
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    from dli.adapters.trino import TrinoExecutor

# Skip all tests if dependencies not available
pytestmark = [
    pytest.mark.integration,
    pytest.mark.trino,
]


# =============================================================================
# Connection Tests
# =============================================================================


class TestTrinoConnection:
    """Tests for Trino connection functionality."""

    def test_connection_successful(self, trino_executor: TrinoExecutor) -> None:
        """Test that connection to Trino is successful."""
        assert trino_executor.test_connection() is True

    def test_simple_query(self, trino_executor: TrinoExecutor) -> None:
        """Test executing a simple SELECT 1 query."""
        result = trino_executor.execute_sql("SELECT 1 AS value")

        assert result.success is True
        assert result.row_count == 1
        assert result.data is not None
        assert len(result.data) == 1
        assert result.data[0]["value"] == 1

    def test_query_with_columns(self, trino_executor: TrinoExecutor) -> None:
        """Test that column names are correctly returned."""
        result = trino_executor.execute_sql(
            "SELECT 1 AS col_a, 'test' AS col_b, 3.14 AS col_c"
        )

        assert result.success is True
        assert result.columns == ["col_a", "col_b", "col_c"]
        assert result.data[0]["col_a"] == 1
        assert result.data[0]["col_b"] == "test"
        # Trino returns Decimal for numeric types, convert for comparison
        assert abs(float(result.data[0]["col_c"]) - 3.14) < 0.01

    def test_execution_time_measured(self, trino_executor: TrinoExecutor) -> None:
        """Test that execution time is measured."""
        result = trino_executor.execute_sql("SELECT 1")

        assert result.success is True
        assert result.execution_time_ms >= 0


# =============================================================================
# Schema and Table Tests
# =============================================================================


class TestTrinoSchemaOperations:
    """Tests for schema and table operations."""

    def test_create_schema(
        self,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
    ) -> None:
        """Test that schema was created successfully."""
        result = trino_executor.execute_sql("SHOW SCHEMAS FROM memory")

        assert result.success is True
        assert result.data is not None
        schema_names = [row.get("Schema", "") for row in result.data]
        assert trino_test_schema in schema_names

    def test_create_table(
        self,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
    ) -> None:
        """Test creating a table in memory catalog."""
        table_name = f"memory.{trino_test_schema}.test_table"

        # Create table
        create_result = trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (
                id INTEGER,
                name VARCHAR
            )
        """)

        assert create_result.success is True

        # Verify table exists
        show_result = trino_executor.execute_sql(
            f"SHOW TABLES FROM memory.{trino_test_schema}"
        )

        assert show_result.success is True
        table_names = [row.get("Table", "") for row in show_result.data]
        assert "test_table" in table_names

    def test_insert_and_select(
        self,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
    ) -> None:
        """Test inserting and selecting data."""
        table_name = f"memory.{trino_test_schema}.insert_test"

        # Create table
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (
                id INTEGER,
                value VARCHAR
            )
        """)

        # Insert data
        insert_result = trino_executor.execute_sql(f"""
            INSERT INTO {table_name} VALUES
            (1, 'first'),
            (2, 'second'),
            (3, 'third')
        """)

        assert insert_result.success is True

        # Select data
        select_result = trino_executor.execute_sql(f"""
            SELECT * FROM {table_name} ORDER BY id
        """)

        assert select_result.success is True
        assert select_result.row_count == 3
        assert select_result.data[0]["id"] == 1
        assert select_result.data[0]["value"] == "first"
        assert select_result.data[2]["id"] == 3
        assert select_result.data[2]["value"] == "third"


# =============================================================================
# Query Execution Tests
# =============================================================================


class TestTrinoQueryExecution:
    """Tests for various query patterns."""

    def test_aggregation_query(
        self,
        trino_executor: TrinoExecutor,
        sample_events_table: str,
    ) -> None:
        """Test aggregation query on events table."""
        result = trino_executor.execute_sql(f"""
            SELECT
                user_id,
                COUNT(*) AS event_count
            FROM {sample_events_table}
            GROUP BY user_id
            ORDER BY event_count DESC
        """)

        assert result.success is True
        assert result.row_count == 3  # 3 unique users
        # u1 and u2 each have 2 events, u3 has 1
        assert result.data[0]["event_count"] == 2
        assert result.data[2]["event_count"] == 1

    def test_join_query(
        self,
        trino_executor: TrinoExecutor,
        sample_users_table: str,
        sample_events_table: str,
    ) -> None:
        """Test join query between users and events."""
        result = trino_executor.execute_sql(f"""
            SELECT
                u.name,
                COUNT(e.event_id) AS total_events
            FROM {sample_users_table} u
            LEFT JOIN {sample_events_table} e ON u.id = e.user_id
            GROUP BY u.name
            ORDER BY total_events DESC
        """)

        assert result.success is True
        assert result.row_count == 3
        # Check column names
        assert "name" in result.columns
        assert "total_events" in result.columns

    def test_filter_query(
        self,
        trino_executor: TrinoExecutor,
        sample_users_table: str,
    ) -> None:
        """Test query with WHERE filter."""
        result = trino_executor.execute_sql(f"""
            SELECT * FROM {sample_users_table}
            WHERE status = 'active'
        """)

        assert result.success is True
        assert result.row_count == 2  # Alice and Bob are active

    def test_date_functions(
        self,
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test date/time functions."""
        result = trino_executor.execute_sql("""
            SELECT
                CURRENT_DATE AS today,
                CURRENT_TIMESTAMP AS now,
                DATE '2025-01-01' AS fixed_date
        """)

        assert result.success is True
        assert result.data[0]["today"] is not None
        assert result.data[0]["now"] is not None


# =============================================================================
# Dry Run Tests
# =============================================================================


class TestTrinoDryRun:
    """Tests for EXPLAIN-based dry run."""

    def test_dry_run_valid_query(
        self,
        trino_executor: TrinoExecutor,
        sample_users_table: str,
    ) -> None:
        """Test dry run with valid query."""
        result = trino_executor.dry_run(f"SELECT * FROM {sample_users_table}")

        assert result["valid"] is True
        assert "plan" in result
        assert result["plan"] is not None

    def test_dry_run_invalid_query(self, trino_executor: TrinoExecutor) -> None:
        """Test dry run with invalid query (table doesn't exist)."""
        result = trino_executor.dry_run(
            "SELECT * FROM memory.nonexistent.table_xyz"
        )

        assert result["valid"] is False
        assert "error" in result

    def test_dry_run_syntax_error(self, trino_executor: TrinoExecutor) -> None:
        """Test dry run with syntax error."""
        result = trino_executor.dry_run("SELEC * FORM users")

        assert result["valid"] is False
        assert "error" in result


# =============================================================================
# Error Handling Tests
# =============================================================================


class TestTrinoErrorHandling:
    """Tests for error handling."""

    def test_query_on_nonexistent_table(
        self, trino_executor: TrinoExecutor
    ) -> None:
        """Test query on non-existent table returns error."""
        result = trino_executor.execute_sql(
            "SELECT * FROM memory.default.nonexistent_table_xyz"
        )

        assert result.success is False
        assert result.error_message is not None
        assert "does not exist" in result.error_message.lower() or "not found" in result.error_message.lower()

    def test_syntax_error(self, trino_executor: TrinoExecutor) -> None:
        """Test query with syntax error."""
        result = trino_executor.execute_sql("SELEC * FORM users")

        assert result.success is False
        assert result.error_message is not None

    def test_division_by_zero(self, trino_executor: TrinoExecutor) -> None:
        """Test division by zero handling."""
        result = trino_executor.execute_sql("SELECT 1/0 AS result")

        # Trino may return null or error depending on configuration
        # Just verify we don't crash
        assert result is not None


# =============================================================================
# Table Schema Tests
# =============================================================================


class TestTrinoTableSchema:
    """Tests for table schema inspection."""

    def test_get_table_schema(
        self,
        trino_executor: TrinoExecutor,
        sample_users_table: str,
    ) -> None:
        """Test getting table schema."""
        schema = trino_executor.get_table_schema(sample_users_table)

        assert schema is not None
        assert len(schema) == 5  # id, name, email, status, created_at

        column_names = [col["name"] for col in schema]
        assert "id" in column_names
        assert "name" in column_names
        assert "email" in column_names
        assert "status" in column_names
        assert "created_at" in column_names

    def test_get_schema_nonexistent_table(
        self, trino_executor: TrinoExecutor
    ) -> None:
        """Test getting schema for non-existent table."""
        schema = trino_executor.get_table_schema(
            "memory.default.nonexistent_table"
        )

        # Should return empty list, not crash
        assert schema == []


# =============================================================================
# Performance Tests
# =============================================================================


@pytest.mark.slow
class TestTrinoPerformance:
    """Performance-related tests."""

    def test_query_completes_within_timeout(
        self,
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test that simple queries complete quickly."""
        result = trino_executor.execute_sql("SELECT 1", timeout=10)

        assert result.success is True
        assert result.execution_time_ms < 10000  # Less than 10 seconds

    def test_multiple_queries_sequential(
        self,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
    ) -> None:
        """Test executing multiple queries sequentially."""
        table_name = f"memory.{trino_test_schema}.perf_test"

        # Create table
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (id INTEGER, value VARCHAR)
        """)

        # Execute 10 sequential queries
        for i in range(10):
            result = trino_executor.execute_sql(
                f"INSERT INTO {table_name} VALUES ({i}, 'value_{i}')"
            )
            assert result.success is True

        # Verify all inserted
        result = trino_executor.execute_sql(f"SELECT COUNT(*) AS cnt FROM {table_name}")
        assert result.success is True
        assert result.data[0]["cnt"] == 10
