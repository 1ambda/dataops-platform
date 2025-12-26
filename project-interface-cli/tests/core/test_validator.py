"""Tests for the DLI Core Engine validator module."""

import pytest

from dli.core.validator import SQLValidator


@pytest.fixture
def validator():
    """Create a SQLValidator instance with Trino dialect."""
    return SQLValidator(dialect="trino")


class TestSQLValidator:
    """Tests for SQLValidator class."""

    def test_valid_simple_sql(self, validator):
        """Test validating simple SELECT statement."""
        result = validator.validate("SELECT * FROM users WHERE id = 1")
        assert result.is_valid is True
        assert result.errors == []

    def test_valid_complex_sql(self, validator):
        """Test validating complex SQL with CTEs."""
        sql = """
        WITH active_users AS (
            SELECT user_id, COUNT(*) as events
            FROM events
            GROUP BY user_id
        )
        SELECT u.*, a.events
        FROM users u
        JOIN active_users a ON u.id = a.user_id
        ORDER BY a.events DESC
        LIMIT 100
        """
        result = validator.validate(sql)
        assert result.is_valid is True

    def test_invalid_sql(self, validator):
        """Test detecting invalid SQL syntax."""
        result = validator.validate("SELECT * FROM")
        assert result.is_valid is False
        assert len(result.errors) > 0
        assert "syntax error" in result.errors[0].lower()

    def test_empty_sql(self, validator):
        """Test rejecting empty SQL."""
        result = validator.validate("")
        assert result.is_valid is False
        assert "Empty" in result.errors[0]

    def test_whitespace_only_sql(self, validator):
        """Test rejecting whitespace-only SQL."""
        result = validator.validate("   \n\t  ")
        assert result.is_valid is False

    def test_warning_select_without_limit(self, validator):
        """Test warning about SELECT without LIMIT."""
        result = validator.validate("SELECT * FROM users")
        assert result.is_valid is True
        assert any("LIMIT" in w for w in result.warnings)

    def test_warning_select_star(self, validator):
        """Test warning about SELECT *."""
        result = validator.validate("SELECT * FROM users LIMIT 10")
        assert result.is_valid is True
        assert any("*" in w for w in result.warnings)

    def test_no_warning_specific_columns(self, validator):
        """Test no warning for specific columns with LIMIT."""
        result = validator.validate("SELECT id, name FROM users LIMIT 10")
        assert result.is_valid is True
        assert not any("unnecessary columns" in w for w in result.warnings)

    def test_phase_parameter(self, validator):
        """Test phase parameter is set correctly."""
        result = validator.validate("SELECT 1", phase="pre")
        assert result.phase == "pre"

        result = validator.validate("SELECT 1", phase="main")
        assert result.phase == "main"

        result = validator.validate("SELECT 1", phase="post")
        assert result.phase == "post"

    def test_validate_multiple(self, validator):
        """Test validate_multiple method."""
        sqls = [
            ("SELECT 1", "pre"),
            ("SELECT 2", "main"),
            ("SELECT 3", "post"),
        ]
        results = validator.validate_multiple(sqls)
        assert len(results) == 3
        assert all(r.is_valid for r in results)
        assert results[0].phase == "pre"
        assert results[1].phase == "main"
        assert results[2].phase == "post"


class TestExtractTables:
    """Tests for table extraction."""

    def test_simple_table(self, validator):
        """Test extracting simple table name."""
        tables = validator.extract_tables("SELECT * FROM users")
        assert "users" in tables

    def test_multiple_tables(self, validator):
        """Test extracting multiple table names."""
        sql = """
        SELECT *
        FROM users u
        JOIN orders o ON u.id = o.user_id
        JOIN products p ON o.product_id = p.id
        """
        tables = validator.extract_tables(sql)
        assert "users" in tables
        assert "orders" in tables
        assert "products" in tables

    def test_qualified_table_name(self, validator):
        """Test extracting qualified table names."""
        tables = validator.extract_tables("SELECT * FROM catalog.schema.table")
        assert any("table" in t for t in tables)

    def test_subquery_tables(self, validator):
        """Test extracting tables from subqueries."""
        sql = """
        SELECT * FROM (
            SELECT user_id FROM events
        ) sub
        """
        tables = validator.extract_tables(sql)
        assert "events" in tables

    def test_cte_tables(self, validator):
        """Test extracting tables from CTEs."""
        sql = """
        WITH cte AS (
            SELECT * FROM source_table
        )
        SELECT * FROM cte
        """
        tables = validator.extract_tables(sql)
        assert "source_table" in tables

    def test_invalid_sql_returns_empty(self, validator):
        """Test returning empty list for invalid SQL."""
        tables = validator.extract_tables("SELECT * FROM")
        assert tables == []


class TestExtractColumns:
    """Tests for column extraction."""

    def test_simple_columns(self, validator):
        """Test extracting column names."""
        columns = validator.extract_columns("SELECT id, name, email FROM users")
        assert "id" in columns
        assert "name" in columns
        assert "email" in columns

    def test_aliased_columns(self, validator):
        """Test extracting column aliases."""
        columns = validator.extract_columns(
            "SELECT id, name AS user_name, COUNT(*) AS total FROM users GROUP BY id, name"
        )
        assert "id" in columns
        assert "user_name" in columns
        assert "total" in columns

    def test_star_column(self, validator):
        """Test indicating star selection."""
        columns = validator.extract_columns("SELECT * FROM users")
        assert "*" in columns


class TestFormatSQL:
    """Tests for SQL formatting."""

    def test_format_simple(self, validator):
        """Test formatting simple SQL."""
        sql = "select * from users where id=1"
        formatted = validator.format_sql(sql)
        assert "SELECT" in formatted
        assert "FROM" in formatted
        # Pretty formatting adds newlines
        assert "\n" in formatted

    def test_format_not_pretty(self, validator):
        """Test formatting without prettifying."""
        sql = "select * from users where id=1"
        formatted = validator.format_sql(sql, pretty=False)
        assert "SELECT" in formatted

    def test_format_invalid_returns_original(self, validator):
        """Test returning original for invalid SQL."""
        sql = "SELECT * FROM"
        formatted = validator.format_sql(sql)
        assert formatted == sql


class TestTranspile:
    """Tests for SQL transpilation."""

    def test_transpile_to_postgres(self, validator):
        """Test transpiling Trino to PostgreSQL."""
        sql = "SELECT EXTRACT(DAY FROM DATE '2024-01-01')"
        result = validator.transpile(sql, "postgres")
        assert result is not None

    def test_transpile_invalid_returns_original(self, validator):
        """Test returning original for invalid SQL."""
        sql = "SELECT * FROM"
        result = validator.transpile(sql, "postgres")
        assert result == sql


class TestQueryType:
    """Tests for query type detection."""

    def test_select_type(self, validator):
        """Test detecting SELECT statement."""
        assert validator.get_query_type("SELECT * FROM users") == "SELECT"
        assert validator.is_select("SELECT * FROM users") is True
        assert validator.is_dml("SELECT * FROM users") is False

    def test_insert_type(self, validator):
        """Test detecting INSERT statement."""
        assert validator.get_query_type("INSERT INTO t VALUES (1)") == "INSERT"
        assert validator.is_dml("INSERT INTO t VALUES (1)") is True
        assert validator.is_select("INSERT INTO t VALUES (1)") is False

    def test_update_type(self, validator):
        """Test detecting UPDATE statement."""
        assert validator.get_query_type("UPDATE t SET x = 1") == "UPDATE"
        assert validator.is_dml("UPDATE t SET x = 1") is True

    def test_delete_type(self, validator):
        """Test detecting DELETE statement."""
        assert validator.get_query_type("DELETE FROM t WHERE id = 1") == "DELETE"
        assert validator.is_dml("DELETE FROM t WHERE id = 1") is True

    def test_create_type(self, validator):
        """Test detecting CREATE statement."""
        assert validator.get_query_type("CREATE TABLE t (id INT)") == "CREATE"
        assert validator.is_dml("CREATE TABLE t (id INT)") is False

    def test_invalid_sql_returns_none(self, validator):
        """Test returning None for invalid SQL."""
        assert validator.get_query_type("SELECT * FROM") is None
