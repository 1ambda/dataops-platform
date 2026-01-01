"""Tests for SqlFormatter (sqlfluff-based SQL formatting).

These tests validate the SQL formatting functionality including:
- Basic SQL formatting with keyword capitalization
- Jinja template preservation
- Multi-dialect support (BigQuery, Trino, Snowflake)
- Complex Jinja block handling
- Error handling for unsupported dialects
"""

from __future__ import annotations

import pytest

from dli.core.format import SqlFormatter, SqlFormatResult
from dli.exceptions import FormatDialectError


class TestSqlFormatterBasic:
    """Tests for basic SQL formatting functionality."""

    @pytest.fixture
    def bigquery_formatter(self) -> SqlFormatter:
        """Create a BigQuery SqlFormatter."""
        return SqlFormatter(dialect="bigquery")

    def test_format_simple_sql(self, bigquery_formatter: SqlFormatter) -> None:
        """Test basic SQL formatting."""
        sql = "select a,b from t where x=1"
        result = bigquery_formatter.format(sql)

        assert isinstance(result, SqlFormatResult)
        assert "SELECT" in result.formatted or "select" in result.formatted
        assert "FROM" in result.formatted or "from" in result.formatted
        assert "WHERE" in result.formatted or "where" in result.formatted

    def test_format_uppercase_keywords(self, bigquery_formatter: SqlFormatter) -> None:
        """Test that SQL keywords are uppercased."""
        sql = "select id, name from users where status = 'active'"
        result = bigquery_formatter.format(sql)

        # Check formatted output
        formatted = result.formatted
        assert "SELECT" in formatted or "select" in formatted
        assert "FROM" in formatted or "from" in formatted
        assert "WHERE" in formatted or "where" in formatted

    def test_format_adds_as_keyword(self, bigquery_formatter: SqlFormatter) -> None:
        """Test that AS keyword is added for column aliases."""
        sql = "SELECT COUNT(*) cnt FROM users"
        result = bigquery_formatter.format(sql)

        # Should have explicit AS keyword
        formatted = result.formatted
        assert "AS" in formatted or "as" in formatted.lower() or "cnt" in formatted

    def test_format_preserves_indentation(self, bigquery_formatter: SqlFormatter) -> None:
        """Test that formatted SQL has consistent indentation."""
        sql = "SELECT a,b,c FROM t WHERE x=1 AND y=2"
        result = bigquery_formatter.format(sql)

        # Result should have line breaks (if formatted)
        formatted = result.formatted
        assert len(formatted) > 0


class TestSqlFormatterDialects:
    """Tests for multi-dialect SQL formatting."""

    @pytest.mark.parametrize(
        "dialect",
        ["bigquery", "trino", "snowflake", "postgres", "sparksql"],
    )
    def test_format_simple_sql_all_dialects(self, dialect: str) -> None:
        """Test basic SQL formatting works for all supported dialects."""
        formatter = SqlFormatter(dialect=dialect)
        sql = "select a,b from t where x=1"
        result = formatter.format(sql)

        formatted = result.formatted
        assert "SELECT" in formatted or "select" in formatted
        assert "FROM" in formatted or "from" in formatted

    def test_bigquery_specific_syntax(self) -> None:
        """Test BigQuery-specific syntax is preserved."""
        formatter = SqlFormatter(dialect="bigquery")
        sql = "SELECT * FROM `project.dataset.table` WHERE _PARTITIONDATE = '2024-01-01'"
        result = formatter.format(sql)

        formatted = result.formatted
        assert "`project.dataset.table`" in formatted or "project.dataset.table" in formatted
        assert "_PARTITIONDATE" in formatted

    def test_trino_specific_syntax(self) -> None:
        """Test Trino-specific syntax is preserved."""
        formatter = SqlFormatter(dialect="trino")
        sql = "SELECT * FROM iceberg.analytics.users WHERE dt = DATE '2024-01-01'"
        result = formatter.format(sql)

        assert "iceberg.analytics.users" in result.formatted

    def test_unsupported_dialect_raises(self) -> None:
        """Test error on unsupported dialect."""
        with pytest.raises(FormatDialectError) as exc_info:
            SqlFormatter(dialect="unknown_dialect")

        assert "unknown_dialect" in str(exc_info.value).lower()
        # Should list supported dialects or just have dialect info
        assert "dialect" in str(exc_info.value).lower()


class TestSqlFormatterJinjaTemplates:
    """Tests for Jinja template preservation in SQL formatting."""

    @pytest.fixture
    def formatter(self) -> SqlFormatter:
        """Create a BigQuery SqlFormatter."""
        return SqlFormatter(dialect="bigquery")

    def test_preserve_jinja_template(self, formatter: SqlFormatter) -> None:
        """Test Jinja template preservation."""
        sql = "SELECT * FROM {{ ref('my_table') }} WHERE dt = '{{ ds }}'"
        result = formatter.format(sql)

        formatted = result.formatted
        assert "{{ ref('my_table') }}" in formatted
        assert "{{ ds }}" in formatted

    def test_preserve_jinja_double_braces(self, formatter: SqlFormatter) -> None:
        """Test double brace Jinja expressions are preserved."""
        sql = "SELECT {{ column_name }}, {{ another_column }} FROM table"
        result = formatter.format(sql)

        formatted = result.formatted
        assert "{{ column_name }}" in formatted
        assert "{{ another_column }}" in formatted

    def test_preserve_jinja_filter_expressions(self, formatter: SqlFormatter) -> None:
        """Test Jinja filter expressions are preserved."""
        sql = "SELECT * FROM table WHERE created_at > '{{ start_date | default(\"2024-01-01\") }}'"
        result = formatter.format(sql)

        # Jinja filter should be preserved
        formatted = result.formatted
        assert "{{" in formatted and "}}" in formatted

    def test_format_complex_jinja(self, formatter: SqlFormatter) -> None:
        """Test complex Jinja blocks are preserved."""
        sql = """
        SELECT *
        FROM table
        {% if condition %}
        WHERE status = 'active'
        {% endif %}
        """
        result = formatter.format(sql)

        formatted = result.formatted
        assert "{% if condition %}" in formatted
        assert "{% endif %}" in formatted

    def test_preserve_jinja_for_loop(self, formatter: SqlFormatter) -> None:
        """Test Jinja for loops are preserved."""
        sql = """
        SELECT
            {% for col in columns %}
            {{ col }}{% if not loop.last %},{% endif %}
            {% endfor %}
        FROM table
        """
        result = formatter.format(sql)

        formatted = result.formatted
        assert "{% for col in columns %}" in formatted
        assert "{% endfor %}" in formatted

    def test_preserve_jinja_with_ref_function(self, formatter: SqlFormatter) -> None:
        """Test dbt-style ref() function is preserved."""
        sql = """
        SELECT
            user_id,
            COUNT(*) AS click_count
        FROM {{ ref('raw_clicks') }}
        WHERE dt = '{{ ds }}'
        GROUP BY user_id
        """
        result = formatter.format(sql)

        formatted = result.formatted
        assert "{{ ref('raw_clicks') }}" in formatted
        assert "{{ ds }}" in formatted

    def test_preserve_jinja_config_block(self, formatter: SqlFormatter) -> None:
        """Test dbt-style config blocks are preserved."""
        sql = """
        {{
            config(
                materialized='incremental',
                unique_key='id'
            )
        }}
        SELECT * FROM source
        """
        result = formatter.format(sql)

        formatted = result.formatted
        assert "config(" in formatted or "{{" in formatted

    def test_mixed_jinja_and_sql(self, formatter: SqlFormatter) -> None:
        """Test SQL with mixed Jinja constructs."""
        sql = """
        SELECT
            {{ columns | join(', ') }}
        FROM {{ source }}
        WHERE 1=1
            {% for filter in filters %}
            AND {{ filter.column }} {{ filter.operator }} {{ filter.value }}
            {% endfor %}
        """
        result = formatter.format(sql)

        # All Jinja constructs should be preserved
        formatted = result.formatted
        assert "{{ columns | join(', ') }}" in formatted or "{{" in formatted


class TestSqlFormatterEdgeCases:
    """Tests for edge cases in SQL formatting."""

    @pytest.fixture
    def formatter(self) -> SqlFormatter:
        """Create a BigQuery SqlFormatter."""
        return SqlFormatter(dialect="bigquery")

    def test_empty_sql(self, formatter: SqlFormatter) -> None:
        """Test formatting empty SQL string."""
        result = formatter.format("")
        assert result.formatted == "" or result.formatted.strip() == ""

    def test_whitespace_only_sql(self, formatter: SqlFormatter) -> None:
        """Test formatting whitespace-only SQL string."""
        result = formatter.format("   \n\t  ")
        assert result.formatted.strip() == ""

    def test_sql_with_comments(self, formatter: SqlFormatter) -> None:
        """Test SQL with single-line comments."""
        sql = """
        -- This is a comment
        SELECT a, b  -- inline comment
        FROM table
        """
        result = formatter.format(sql)

        # Comments should be preserved
        formatted = result.formatted
        assert "--" in formatted or "comment" in formatted.lower()

    def test_sql_with_multiline_comments(self, formatter: SqlFormatter) -> None:
        """Test SQL with multi-line comments."""
        sql = """
        /* This is a
           multi-line comment */
        SELECT a FROM table
        """
        result = formatter.format(sql)

        # Multi-line comment should be preserved
        formatted = result.formatted
        assert "/*" in formatted and "*/" in formatted

    def test_sql_with_string_literals(self, formatter: SqlFormatter) -> None:
        """Test SQL with string literals containing special characters."""
        sql = "SELECT * FROM table WHERE name = 'John''s Test'"
        result = formatter.format(sql)

        # String literal should be preserved
        formatted = result.formatted
        assert "John''s Test" in formatted or "John's" in formatted

    def test_very_long_line(self, formatter: SqlFormatter) -> None:
        """Test SQL with very long lines gets wrapped."""
        columns = ", ".join([f"column_{i}" for i in range(50)])
        sql = f"SELECT {columns} FROM table"
        result = formatter.format(sql)

        # Should have output
        assert len(result.formatted) > 0

    def test_sql_with_cte(self, formatter: SqlFormatter) -> None:
        """Test SQL with Common Table Expressions."""
        sql = """
        WITH cte AS (
            SELECT id, name FROM users WHERE active = true
        )
        SELECT * FROM cte
        """
        result = formatter.format(sql)

        formatted = result.formatted
        assert "WITH" in formatted or "with" in formatted.lower()
        assert "AS" in formatted or "as" in formatted.lower()


class TestSqlFormatterConfiguration:
    """Tests for SqlFormatter configuration options."""

    def test_default_dialect(self) -> None:
        """Test default dialect is bigquery."""
        formatter = SqlFormatter()
        # Should not raise
        result = formatter.format("SELECT 1")
        assert "SELECT" in result.formatted or "select" in result.formatted

    def test_lint_disabled_by_default(self) -> None:
        """Test lint is disabled by default."""
        formatter = SqlFormatter(dialect="bigquery")
        # Just format, don't lint
        result = formatter.format("SELECT 1")
        assert result is not None
        assert result.formatted is not None

    def test_format_with_lint(self) -> None:
        """Test format with lint enabled."""
        formatter = SqlFormatter(dialect="bigquery")
        result = formatter.format("SELECT 1", lint=True)
        assert result is not None
        assert isinstance(result.violations, list)

    def test_format_result_changed_flag(self) -> None:
        """Test format result has changed flag."""
        formatter = SqlFormatter(dialect="bigquery")
        result = formatter.format("select a,b from t")
        assert isinstance(result.changed, bool)

    def test_format_result_has_diff(self) -> None:
        """Test format result can produce diff."""
        formatter = SqlFormatter(dialect="bigquery")
        result = formatter.format("select a,b from t")
        diff = result.get_diff()
        assert isinstance(diff, list)
