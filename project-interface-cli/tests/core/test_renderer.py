"""Tests for the DLI Core Engine renderer module."""

from pathlib import Path

import pytest

from dli.core.models import ParameterType, QueryParameter
from dli.core.renderer import SQLRenderer
from dli.core.templates import sql_identifier_escape, sql_list_escape, sql_string_escape


@pytest.fixture
def renderer():
    """Create a SQLRenderer instance."""
    return SQLRenderer()


class TestSQLRenderer:
    """Tests for SQLRenderer class."""

    def test_render_simple(self, renderer):
        """Test rendering simple template."""
        template = "SELECT * FROM t WHERE dt = '{{ date }}' LIMIT {{ limit }}"
        parameters = [
            QueryParameter(name="date", type=ParameterType.DATE),
            QueryParameter(
                name="limit", type=ParameterType.INTEGER, required=False, default=100
            ),
        ]
        result = renderer.render(template, parameters, {"date": "2024-01-01"})
        assert "2024-01-01" in result
        assert "LIMIT 100" in result

    def test_render_with_explicit_value(self, renderer):
        """Test rendering with explicit parameter value."""
        template = "SELECT * FROM t WHERE dt = '{{ date }}' LIMIT {{ limit }}"
        parameters = [
            QueryParameter(name="date", type=ParameterType.DATE),
            QueryParameter(
                name="limit", type=ParameterType.INTEGER, required=False, default=100
            ),
        ]
        result = renderer.render(
            template, parameters, {"date": "2024-01-01", "limit": 50}
        )
        assert "LIMIT 50" in result

    def test_render_missing_required(self, renderer):
        """Test error for missing required parameter."""
        template = "SELECT * FROM t WHERE dt = '{{ date }}'"
        parameters = [
            QueryParameter(name="date", type=ParameterType.DATE, required=True),
        ]
        with pytest.raises(ValueError, match="Required parameter"):
            renderer.render(template, parameters, {})

    def test_render_with_extra_params(self, renderer):
        """Test rendering with extra parameters not in definition."""
        template = "SELECT * FROM {{ table }} WHERE id = {{ id }}"
        parameters = []
        result = renderer.render(template, parameters, {"table": "users", "id": 123})
        assert "FROM users" in result
        assert "id = 123" in result

    def test_render_string(self, renderer):
        """Test render_string method."""
        template = "SELECT * FROM {{ table }} WHERE id = {{ id }}"
        result = renderer.render_string(template, {"table": "users", "id": 123})
        assert "FROM users" in result
        assert "id = 123" in result

    def test_render_file(self, renderer, tmp_path):
        """Test render_file method."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT * FROM {{ table }} WHERE id = {{ id }}")

        parameters = []
        result = renderer.render_file(
            sql_file, parameters, {"table": "users", "id": 123}
        )
        assert "FROM users" in result
        assert "id = 123" in result

    def test_sql_string_filter(self, renderer):
        """Test sql_string filter."""
        template = "SELECT * FROM t WHERE name = {{ name | sql_string }}"
        result = renderer.render_string(template, {"name": "O'Brien"})
        assert "O''Brien" in result

    def test_sql_list_filter_integers(self, renderer):
        """Test sql_list filter with integers."""
        template = "SELECT * FROM t WHERE id IN {{ ids | sql_list }}"
        result = renderer.render_string(template, {"ids": [1, 2, 3]})
        assert "(1, 2, 3)" in result

    def test_sql_list_filter_strings(self, renderer):
        """Test sql_list filter with strings."""
        template = "SELECT * FROM t WHERE name IN {{ names | sql_list }}"
        result = renderer.render_string(template, {"names": ["a", "b", "c"]})
        assert "('a', 'b', 'c')" in result

    def test_sql_list_filter_empty(self, renderer):
        """Test sql_list filter with empty list."""
        template = "SELECT * FROM t WHERE id IN {{ ids | sql_list }}"
        result = renderer.render_string(template, {"ids": []})
        assert "(NULL)" in result

    def test_sql_date_filter(self, renderer):
        """Test sql_date filter."""
        template = "SELECT * FROM t WHERE dt = {{ date | sql_date }}"
        result = renderer.render_string(template, {"date": "2024-01-15"})
        assert "'2024-01-15'" in result

    def test_sql_identifier_filter(self, renderer):
        """Test sql_identifier filter."""
        template = 'SELECT * FROM {{ table | sql_identifier }}'
        result = renderer.render_string(template, {"table": "users"})
        assert '"users"' in result

    def test_jinja2_set_variable(self, renderer):
        """Test Jinja2 set variable."""
        template = """
{% set target = 'iceberg.analytics.clicks' %}
INSERT INTO {{ target }} SELECT 1
"""
        result = renderer.render_string(template, {})
        assert "INSERT INTO iceberg.analytics.clicks" in result


class TestSQLFilters:
    """Tests for custom Jinja2 filters.

    Note: Most SQL filter tests are now in test_templates.py (TestSharedSQLFilters)
    since these functions are shared between SQLRenderer and SafeJinjaEnvironment.
    These tests cover SQLRenderer-specific filters (sql_date) and verify the
    shared filters are properly registered in SQLRenderer.
    """

    def test_sql_string_filter_null(self):
        """Test sql_string filter with None."""
        result = sql_string_escape(None)
        assert result == "NULL"

    def test_sql_string_filter_escaping(self):
        """Test sql_string filter escapes single quotes."""
        result = sql_string_escape("It's a test")
        assert result == "'It''s a test'"

    def test_sql_list_filter_mixed(self):
        """Test sql_list filter with mixed types."""
        result = sql_list_escape([1, "hello", None])
        assert result == "(1, 'hello', NULL)"

    def test_sql_date_filter_string(self):
        """Test sql_date filter with string."""
        result = SQLRenderer._sql_date_filter("2024-01-15")
        assert result == "'2024-01-15'"

    def test_sql_date_filter_null(self):
        """Test sql_date filter with None."""
        result = SQLRenderer._sql_date_filter(None)
        assert result == "NULL"

    def test_sql_identifier_filter_escaping(self):
        """Test sql_identifier filter escapes double quotes."""
        result = sql_identifier_escape('table"name')
        assert result == '"table""name"'
