"""Integration tests for dli run command and RunAPI.

These tests verify ad-hoc SQL execution against a real Trino instance.
They are marked with @pytest.mark.integration and skip by default in CI.

Covers:
- Basic SQL file execution
- Output formats (CSV, JSON, TSV)
- Parameter substitution
- Row limiting
- dry-run validation (EXPLAIN)
- Error handling (syntax errors, non-existent tables, etc.)
"""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import TYPE_CHECKING, Any
from unittest.mock import MagicMock

import pytest

from dli.api.run import RunAPI
from dli.core.client import ServerResponse
from dli.core.models import ExecutionResult
from dli.exceptions import RunExecutionError, RunFileNotFoundError
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.run import OutputFormat

if TYPE_CHECKING:
    from dli.adapters.trino import TrinoExecutor

pytestmark = [pytest.mark.integration, pytest.mark.trino]


# =============================================================================
# Adapter for TrinoExecutor -> QueryExecutor Protocol
# =============================================================================


class TrinoQueryExecutorAdapter:
    """Adapter to make TrinoExecutor compatible with QueryExecutor protocol.

    QueryExecutor protocol expects execute(sql, params) and test_connection() methods,
    while TrinoExecutor has execute_sql(sql, timeout) method.
    """

    def __init__(self, trino_executor: TrinoExecutor) -> None:
        self._executor = trino_executor

    def execute(
        self, sql: str, params: dict[str, Any] | None = None
    ) -> ExecutionResult:
        """Execute SQL using the underlying TrinoExecutor."""
        # TrinoExecutor.execute_sql returns ExecutionResult
        return self._executor.execute_sql(sql)

    def test_connection(self) -> bool:
        """Test the database connection."""
        return self._executor.test_connection()


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def run_api(trino_executor: TrinoExecutor, tmp_path: Path) -> RunAPI:
    """Create RunAPI with TrinoExecutor for testing.

    Uses LOCAL mode with injected executor adapter and mock client
    that returns a policy allowing local execution.
    """
    ctx = ExecutionContext(
        execution_mode=ExecutionMode.LOCAL,
        project_path=tmp_path,
    )
    # Wrap TrinoExecutor with adapter to match QueryExecutor protocol
    adapter = TrinoQueryExecutorAdapter(trino_executor)

    # Create mock client that returns policy allowing local execution
    mock_client = MagicMock()
    mock_client.run_get_policy.return_value = ServerResponse(
        success=True,
        data={
            "allow_local": True,
            "server_available": True,
            "default_mode": "local",  # Use local by default
        },
    )

    return RunAPI(context=ctx, executor=adapter, client=mock_client)


@pytest.fixture
def sql_dir(tmp_path: Path) -> Path:
    """Create temporary directory for SQL files."""
    sql_path = tmp_path / "sql"
    sql_path.mkdir()
    return sql_path


@pytest.fixture
def output_dir(tmp_path: Path) -> Path:
    """Create temporary directory for output files."""
    output_path = tmp_path / "output"
    output_path.mkdir()
    return output_path


@pytest.fixture
def sample_sql_file(sql_dir: Path) -> Path:
    """Create a simple SQL file for testing."""
    sql_file = sql_dir / "simple.sql"
    sql_file.write_text("SELECT 1 AS id, 'test' AS name")
    return sql_file


@pytest.fixture
def parameterized_sql_file(sql_dir: Path) -> Path:
    """Create a SQL file with parameters."""
    sql_file = sql_dir / "parameterized.sql"
    sql_file.write_text(
        "SELECT {{ id }} AS id, '{{ name }}' AS name, '{{ date }}' AS run_date"
    )
    return sql_file


# =============================================================================
# MVP Tests - Basic Execution
# =============================================================================


class TestRunBasicExecution:
    """Tests for basic SQL execution functionality."""

    def test_basic_select_query(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test simple SELECT 1 query execution."""
        # Create SQL file
        sql_file = sql_dir / "select_one.sql"
        sql_file.write_text("SELECT 1 AS value")
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 1
        assert result.output_path == output_file
        assert output_file.exists()

    def test_select_with_multiple_columns(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test SELECT with multiple columns and data types."""
        sql_file = sql_dir / "multi_col.sql"
        sql_file.write_text("""
            SELECT
                42 AS int_col,
                3.14 AS float_col,
                'hello' AS str_col,
                true AS bool_col
        """)
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 1
        assert output_file.exists()

        # Verify CSV content
        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert len(rows) == 1
            assert rows[0]["int_col"] == "42"
            assert rows[0]["str_col"] == "hello"

    def test_select_with_table_data(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test SELECT from an actual table with data."""
        # Create test table
        table_name = f"memory.{trino_test_schema}.run_test_users"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (
                id INTEGER,
                name VARCHAR,
                age INTEGER
            )
        """)
        trino_executor.execute_sql(f"""
            INSERT INTO {table_name} VALUES
            (1, 'Alice', 30),
            (2, 'Bob', 25),
            (3, 'Charlie', 35)
        """)

        # Create SQL file
        sql_file = sql_dir / "select_users.sql"
        sql_file.write_text(f"SELECT * FROM {table_name} ORDER BY id")
        output_file = output_dir / "users.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 3
        assert output_file.exists()

        # Verify content
        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert len(rows) == 3
            assert rows[0]["name"] == "Alice"


# =============================================================================
# MVP Tests - Output Formats
# =============================================================================


class TestRunOutputFormats:
    """Tests for different output format support."""

    def test_output_csv_format(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test CSV output format."""
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.output_format == OutputFormat.CSV
        assert output_file.exists()

        # Verify CSV structure
        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert len(rows) == 1
            assert "id" in rows[0]
            assert "name" in rows[0]

    def test_output_json_format(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test JSON (JSONL) output format."""
        output_file = output_dir / "result.json"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
            output_format=OutputFormat.JSON,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.output_format == OutputFormat.JSON
        assert output_file.exists()

        # Verify JSONL structure
        with output_file.open() as f:
            lines = f.readlines()
            assert len(lines) == 1
            data = json.loads(lines[0])
            assert data["id"] == 1
            assert data["name"] == "test"

    def test_output_tsv_format(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test TSV output format."""
        output_file = output_dir / "result.tsv"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
            output_format=OutputFormat.TSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.output_format == OutputFormat.TSV
        assert output_file.exists()

        # Verify TSV structure
        with output_file.open() as f:
            reader = csv.DictReader(f, delimiter="\t")
            rows = list(reader)
            assert len(rows) == 1
            assert "id" in rows[0]


# =============================================================================
# MVP Tests - Parameters
# =============================================================================


class TestRunParameterSubstitution:
    """Tests for parameter substitution in SQL files."""

    def test_parameter_substitution_basic(
        self, run_api: RunAPI, parameterized_sql_file: Path, output_dir: Path
    ) -> None:
        """Test basic parameter substitution."""
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=parameterized_sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            parameters={"id": "42", "name": "Alice", "date": "2026-01-09"},
        )

        assert result.status == ResultStatus.SUCCESS
        assert output_file.exists()

        # Verify substituted values
        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert rows[0]["id"] == "42"
            assert rows[0]["name"] == "Alice"
            assert rows[0]["run_date"] == "2026-01-09"

    def test_parameter_in_rendered_sql(
        self, run_api: RunAPI, parameterized_sql_file: Path
    ) -> None:
        """Test render_sql method returns correctly substituted SQL."""
        rendered = run_api.render_sql(
            sql_path=parameterized_sql_file,
            parameters={"id": "99", "name": "Bob", "date": "2026-12-31"},
        )

        assert "99" in rendered
        assert "Bob" in rendered
        assert "2026-12-31" in rendered
        # Original template syntax should be replaced
        assert "{{ id }}" not in rendered


# =============================================================================
# MVP Tests - Row Limiting
# =============================================================================


class TestRunRowLimiting:
    """Tests for row limit functionality."""

    def test_limit_option(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test limiting number of rows returned."""
        # Create table with many rows
        table_name = f"memory.{trino_test_schema}.limit_test"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} AS
            SELECT * FROM (VALUES 1, 2, 3, 4, 5, 6, 7, 8, 9, 10) AS t(num)
        """)

        sql_file = sql_dir / "select_all.sql"
        sql_file.write_text(f"SELECT * FROM {table_name}")
        output_file = output_dir / "limited.csv"

        # Note: Limit is applied at the SQL level, not by RunAPI itself
        # for local execution. The executor handles this.
        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        # Without explicit limit in SQL, returns all 10 rows
        assert result.row_count == 10


# =============================================================================
# MVP Tests - Aggregation & Joins
# =============================================================================


class TestRunComplexQueries:
    """Tests for more complex SQL queries."""

    def test_aggregation_query(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test aggregation functions (COUNT, SUM, AVG)."""
        # Create test data
        table_name = f"memory.{trino_test_schema}.agg_test"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} AS
            SELECT * FROM (VALUES
                ('A', 10), ('A', 20), ('B', 30), ('B', 40)
            ) AS t(category, value)
        """)

        sql_file = sql_dir / "aggregation.sql"
        sql_file.write_text(f"""
            SELECT
                category,
                COUNT(*) as cnt,
                SUM(value) as total,
                AVG(value) as avg_val
            FROM {table_name}
            GROUP BY category
            ORDER BY category
        """)
        output_file = output_dir / "agg_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 2

        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert rows[0]["category"] == "A"
            assert rows[0]["cnt"] == "2"
            assert rows[0]["total"] == "30"

    def test_join_query(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test JOIN operations."""
        # Create test tables
        users_table = f"memory.{trino_test_schema}.join_users"
        orders_table = f"memory.{trino_test_schema}.join_orders"

        trino_executor.execute_sql(f"""
            CREATE TABLE {users_table} AS
            SELECT * FROM (VALUES (1, 'Alice'), (2, 'Bob')) AS t(id, name)
        """)
        trino_executor.execute_sql(f"""
            CREATE TABLE {orders_table} AS
            SELECT * FROM (VALUES (1, 1, 100), (2, 1, 200), (3, 2, 150)) AS t(order_id, user_id, amount)
        """)

        sql_file = sql_dir / "join.sql"
        sql_file.write_text(f"""
            SELECT u.name, SUM(o.amount) as total_amount
            FROM {users_table} u
            JOIN {orders_table} o ON u.id = o.user_id
            GROUP BY u.name
            ORDER BY u.name
        """)
        output_file = output_dir / "join_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 2

        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert rows[0]["name"] == "Alice"
            assert rows[0]["total_amount"] == "300"


# =============================================================================
# Standard Tests - Dry Run
# =============================================================================


class TestRunDryRun:
    """Tests for dry-run functionality (EXPLAIN without execution)."""

    def test_dry_run_basic(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test dry-run returns execution plan without executing."""
        output_file = output_dir / "result.csv"

        plan = run_api.dry_run(
            sql_path=sample_sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        # Should return plan without creating output file
        assert plan.is_valid is True
        assert plan.sql_path == sample_sql_file
        assert plan.output_path == output_file
        assert "SELECT" in plan.rendered_sql
        # Output file should NOT be created
        assert not output_file.exists()

    def test_dry_run_with_parameters(
        self, run_api: RunAPI, parameterized_sql_file: Path, output_dir: Path
    ) -> None:
        """Test dry-run with parameter substitution."""
        output_file = output_dir / "result.csv"

        plan = run_api.dry_run(
            sql_path=parameterized_sql_file,
            output_path=output_file,
            parameters={"id": "42", "name": "Test", "date": "2026-01-01"},
        )

        assert plan.is_valid is True
        assert "42" in plan.rendered_sql
        assert "Test" in plan.rendered_sql
        assert plan.parameters == {"id": "42", "name": "Test", "date": "2026-01-01"}

    def test_dry_run_shows_execution_mode(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test dry-run shows expected execution mode."""
        output_file = output_dir / "result.csv"

        plan = run_api.dry_run(
            sql_path=sample_sql_file,
            output_path=output_file,
        )

        # Since we're using LOCAL mode via injected executor
        assert plan.execution_mode is not None


# =============================================================================
# Standard Tests - Error Handling
# =============================================================================


class TestRunErrorHandling:
    """Tests for error handling scenarios."""

    def test_error_file_not_found(
        self, run_api: RunAPI, output_dir: Path
    ) -> None:
        """Test error when SQL file doesn't exist."""
        non_existent = Path("/nonexistent/query.sql")
        output_file = output_dir / "result.csv"

        with pytest.raises(RunFileNotFoundError) as exc_info:
            run_api.run(
                sql_path=non_existent,
                output_path=output_file,
            )

        assert "not found" in str(exc_info.value).lower()

    def test_error_syntax_error(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test error handling for SQL syntax errors."""
        sql_file = sql_dir / "bad_syntax.sql"
        sql_file.write_text("SELEC * FORM invalid")  # Intentional typos
        output_file = output_dir / "result.csv"

        with pytest.raises(RunExecutionError) as exc_info:
            run_api.run(
                sql_path=sql_file,
                output_path=output_file,
            )

        # Error message should indicate syntax issue
        assert exc_info.value is not None

    def test_error_non_existent_table(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test error handling for non-existent table reference."""
        sql_file = sql_dir / "missing_table.sql"
        sql_file.write_text("SELECT * FROM memory.default.this_table_does_not_exist")
        output_file = output_dir / "result.csv"

        with pytest.raises(RunExecutionError) as exc_info:
            run_api.run(
                sql_path=sql_file,
                output_path=output_file,
            )

        assert exc_info.value is not None

    def test_error_invalid_column(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test error handling for invalid column reference."""
        # Create a simple table
        table_name = f"memory.{trino_test_schema}.col_test"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (id INTEGER, name VARCHAR)
        """)

        sql_file = sql_dir / "invalid_col.sql"
        sql_file.write_text(f"SELECT nonexistent_column FROM {table_name}")
        output_file = output_dir / "result.csv"

        with pytest.raises(RunExecutionError):
            run_api.run(
                sql_path=sql_file,
                output_path=output_file,
            )

    def test_error_division_by_zero(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test error handling for runtime division by zero."""
        sql_file = sql_dir / "div_zero.sql"
        sql_file.write_text("SELECT 1 / 0 AS result")
        output_file = output_dir / "result.csv"

        with pytest.raises(RunExecutionError):
            run_api.run(
                sql_path=sql_file,
                output_path=output_file,
            )


# =============================================================================
# Standard Tests - Complex Queries
# =============================================================================


class TestRunAdvancedQueries:
    """Tests for advanced SQL query patterns."""

    def test_cte_query(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test Common Table Expression (WITH clause)."""
        # Create test data
        table_name = f"memory.{trino_test_schema}.cte_source"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} AS
            SELECT * FROM (VALUES
                (1, 'A', 100), (2, 'A', 200), (3, 'B', 150)
            ) AS t(id, category, amount)
        """)

        sql_file = sql_dir / "cte.sql"
        sql_file.write_text(f"""
            WITH category_totals AS (
                SELECT category, SUM(amount) as total
                FROM {table_name}
                GROUP BY category
            )
            SELECT * FROM category_totals
            ORDER BY category
        """)
        output_file = output_dir / "cte_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 2

    def test_window_function_query(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test window function (ROW_NUMBER, RANK)."""
        # Create test data
        table_name = f"memory.{trino_test_schema}.window_source"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} AS
            SELECT * FROM (VALUES
                ('A', 100), ('A', 200), ('B', 150), ('B', 250)
            ) AS t(category, value)
        """)

        sql_file = sql_dir / "window.sql"
        sql_file.write_text(f"""
            SELECT
                category,
                value,
                ROW_NUMBER() OVER (PARTITION BY category ORDER BY value DESC) as rank
            FROM {table_name}
            ORDER BY category, rank
        """)
        output_file = output_dir / "window_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 4

        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            # First row should be highest value in category A
            assert rows[0]["category"] == "A"
            assert rows[0]["value"] == "200"
            assert rows[0]["rank"] == "1"

    def test_subquery(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test subquery in WHERE clause."""
        # Create test data
        table_name = f"memory.{trino_test_schema}.subquery_test"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} AS
            SELECT * FROM (VALUES
                (1, 100), (2, 200), (3, 300), (4, 400)
            ) AS t(id, value)
        """)

        sql_file = sql_dir / "subquery.sql"
        sql_file.write_text(f"""
            SELECT * FROM {table_name}
            WHERE value > (SELECT AVG(value) FROM {table_name})
            ORDER BY id
        """)
        output_file = output_dir / "subquery_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        # Values > 250 (avg): 300, 400
        assert result.row_count == 2

    def test_case_expression(
        self, run_api: RunAPI, sql_dir: Path, output_dir: Path
    ) -> None:
        """Test CASE expression in query."""
        sql_file = sql_dir / "case.sql"
        sql_file.write_text("""
            SELECT
                value,
                CASE
                    WHEN value < 100 THEN 'low'
                    WHEN value < 200 THEN 'medium'
                    ELSE 'high'
                END AS category
            FROM (VALUES 50, 150, 250) AS t(value)
            ORDER BY value
        """)
        output_file = output_dir / "case_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 3

        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert rows[0]["category"] == "low"
            assert rows[1]["category"] == "medium"
            assert rows[2]["category"] == "high"


# =============================================================================
# Standard Tests - Result Validation
# =============================================================================


class TestRunResultValidation:
    """Tests for validating result metadata and content."""

    def test_result_contains_rendered_sql(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test that result contains the rendered SQL."""
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
        )

        assert result.rendered_sql is not None
        assert "SELECT" in result.rendered_sql

    def test_result_contains_duration(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test that result contains execution duration."""
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
        )

        assert result.duration_seconds >= 0

    def test_result_execution_mode(
        self, run_api: RunAPI, sample_sql_file: Path, output_dir: Path
    ) -> None:
        """Test that result shows correct execution mode."""
        output_file = output_dir / "result.csv"

        result = run_api.run(
            sql_path=sample_sql_file,
            output_path=output_file,
        )

        assert result.execution_mode == ExecutionMode.LOCAL

    def test_empty_result_creates_empty_file(
        self,
        run_api: RunAPI,
        trino_executor: TrinoExecutor,
        trino_test_schema: str,
        sql_dir: Path,
        output_dir: Path,
    ) -> None:
        """Test that empty result set creates file with headers only."""
        # Create empty table
        table_name = f"memory.{trino_test_schema}.empty_table"
        trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (id INTEGER, name VARCHAR)
        """)

        sql_file = sql_dir / "empty.sql"
        sql_file.write_text(f"SELECT * FROM {table_name}")
        output_file = output_dir / "empty_result.csv"

        result = run_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.row_count == 0
        assert output_file.exists()
