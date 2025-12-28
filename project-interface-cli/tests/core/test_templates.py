"""Tests for the DLI Core Engine templates module.

Tests cover:
- TemplateContext: Date variables and functions (ds, ds_nodash, var, date_add, etc.)
- SafeJinjaEnvironment: Sandboxed Jinja2 environment
- SafeTemplateRenderer: Unified rendering with context
- SQLRenderer integration: render_with_template_context method
- Security: Sandbox blocks dangerous operations
"""

from datetime import date

import pytest
from jinja2.exceptions import SecurityError, UndefinedError

from dli.core.renderer import SQLRenderer
from dli.core.templates import (
    SafeJinjaEnvironment,
    SafeTemplateRenderer,
    TemplateContext,
    sql_identifier_escape,
    sql_list_escape,
    sql_string_escape,
)


class TestTemplateContext:
    """Tests for TemplateContext class."""

    def test_default_execution_date(self):
        """Test default execution date is today."""
        ctx = TemplateContext()
        assert ctx.execution_date == date.today()

    def test_custom_execution_date(self):
        """Test custom execution date."""
        exec_date = date(2025, 1, 15)
        ctx = TemplateContext(execution_date=exec_date)
        assert ctx.execution_date == exec_date

    def test_ds_format(self):
        """Test ds returns YYYY-MM-DD format."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.ds == "2025-01-15"

    def test_ds_nodash_format(self):
        """Test ds_nodash returns YYYYMMDD format."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.ds_nodash == "20250115"

    def test_yesterday_ds(self):
        """Test yesterday_ds returns previous day."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.yesterday_ds == "2025-01-14"

    def test_tomorrow_ds(self):
        """Test tomorrow_ds returns next day."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.tomorrow_ds == "2025-01-16"

    def test_week_start_ds(self):
        """Test week_start_ds returns Monday of the week."""
        # 2025-01-15 is a Wednesday, so week start is 2025-01-13 (Monday)
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.week_start_ds == "2025-01-13"

    def test_month_start_ds(self):
        """Test month_start_ds returns first day of month."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.month_start_ds == "2025-01-01"

    def test_year(self):
        """Test year property."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.year == 2025

    def test_month(self):
        """Test month property."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.month == 1

    def test_day(self):
        """Test day property."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        assert ctx.day == 15


class TestTemplateContextFunctions:
    """Tests for TemplateContext function methods."""

    def test_var_with_value(self):
        """Test var() returns variable value."""
        ctx = TemplateContext(variables={"schema": "prod"})
        assert ctx.var("schema") == "prod"

    def test_var_with_default(self):
        """Test var() returns default for missing variable."""
        ctx = TemplateContext()
        assert ctx.var("missing", "default_value") == "default_value"

    def test_var_missing_no_default(self):
        """Test var() returns None for missing variable without default."""
        ctx = TemplateContext()
        assert ctx.var("missing") is None

    def test_date_add(self):
        """Test date_add() adds days."""
        ctx = TemplateContext()
        assert ctx.date_add("2025-01-15", 7) == "2025-01-22"

    def test_date_add_negative(self):
        """Test date_add() with negative days."""
        ctx = TemplateContext()
        assert ctx.date_add("2025-01-15", -1) == "2025-01-14"

    def test_date_sub(self):
        """Test date_sub() subtracts days."""
        ctx = TemplateContext()
        assert ctx.date_sub("2025-01-15", 7) == "2025-01-08"

    def test_ref_with_mapping(self):
        """Test ref() returns mapped reference."""
        ctx = TemplateContext(refs={"users": "prod.analytics.users"})
        assert ctx.ref("users") == "prod.analytics.users"

    def test_ref_without_mapping(self):
        """Test ref() returns original name if not mapped."""
        ctx = TemplateContext()
        assert ctx.ref("users") == "users"

    def test_source_with_mapping(self):
        """Test source() returns mapped reference."""
        ctx = TemplateContext(refs={"raw.events": "raw_db.events_table"})
        assert ctx.source("raw", "events") == "raw_db.events_table"

    def test_source_without_mapping(self):
        """Test source() returns formatted key if not mapped."""
        ctx = TemplateContext()
        assert ctx.source("raw", "events") == "raw.events"

    def test_env_var(self, monkeypatch):
        """Test env_var() returns environment variable."""
        monkeypatch.setenv("TEST_VAR", "test_value")
        ctx = TemplateContext()
        assert ctx.env_var("TEST_VAR") == "test_value"

    def test_env_var_default(self):
        """Test env_var() returns default for missing variable."""
        ctx = TemplateContext()
        assert ctx.env_var("NONEXISTENT_VAR", "default") == "default"


class TestTemplateContextToDict:
    """Tests for TemplateContext.to_dict() method."""

    def test_to_dict_contains_date_variables(self):
        """Test to_dict() includes date variables."""
        ctx = TemplateContext(execution_date=date(2025, 1, 15))
        d = ctx.to_dict()

        assert d["execution_date"] == "2025-01-15"
        assert d["ds"] == "2025-01-15"
        assert d["ds_nodash"] == "20250115"
        assert d["yesterday_ds"] == "2025-01-14"
        assert d["tomorrow_ds"] == "2025-01-16"

    def test_to_dict_contains_functions(self):
        """Test to_dict() includes callable functions."""
        ctx = TemplateContext()
        d = ctx.to_dict()

        assert callable(d["var"])
        assert callable(d["env_var"])
        assert callable(d["date_add"])
        assert callable(d["date_sub"])
        assert callable(d["ref"])
        assert callable(d["source"])


class TestSafeJinjaEnvironment:
    """Tests for SafeJinjaEnvironment class."""

    def test_create_environment(self):
        """Test environment creation."""
        env = SafeJinjaEnvironment.create_environment()
        assert env is not None

    def test_render_simple_template(self):
        """Test rendering simple template."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("SELECT * FROM {{ table }}")
        result = template.render(table="users")
        assert result == "SELECT * FROM users"

    def test_sql_string_filter(self):
        """Test sql_string filter."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("WHERE name = {{ name | sql_string }}")
        result = template.render(name="O'Brien")
        assert result == "WHERE name = 'O''Brien'"

    def test_sql_list_filter(self):
        """Test sql_list filter."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("WHERE id IN {{ ids | sql_list }}")
        result = template.render(ids=[1, 2, 3])
        assert result == "WHERE id IN (1, 2, 3)"

    def test_sql_identifier_filter(self):
        """Test sql_identifier filter."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("SELECT * FROM {{ table | sql_identifier }}")
        result = template.render(table="users")
        assert result == 'SELECT * FROM "users"'

    def test_is_safe_attribute(self):
        """Test is_safe_attribute method."""
        assert SafeJinjaEnvironment.is_safe_attribute("name") is True
        assert SafeJinjaEnvironment.is_safe_attribute("__class__") is False
        assert SafeJinjaEnvironment.is_safe_attribute("__globals__") is False

    def test_is_safe_callable(self):
        """Test is_safe_callable method."""
        assert SafeJinjaEnvironment.is_safe_callable("my_function") is True
        assert SafeJinjaEnvironment.is_safe_callable("eval") is False
        assert SafeJinjaEnvironment.is_safe_callable("exec") is False


class TestSafeTemplateRenderer:
    """Tests for SafeTemplateRenderer class."""

    def test_render_with_ds(self):
        """Test rendering with ds variable."""
        renderer = SafeTemplateRenderer()
        result = renderer.render(
            "SELECT * FROM t WHERE dt = '{{ ds }}'",
            execution_date=date(2025, 1, 15),
        )
        assert result == "SELECT * FROM t WHERE dt = '2025-01-15'"

    def test_render_with_ds_nodash(self):
        """Test rendering with ds_nodash variable."""
        renderer = SafeTemplateRenderer()
        result = renderer.render(
            "SELECT * FROM t_{{ ds_nodash }}",
            execution_date=date(2025, 1, 15),
        )
        assert result == "SELECT * FROM t_20250115"

    def test_render_with_var(self):
        """Test rendering with var() function."""
        renderer = SafeTemplateRenderer(variables={"schema": "prod"})
        result = renderer.render("SELECT * FROM {{ var('schema') }}.users")
        assert result == "SELECT * FROM prod.users"

    def test_render_with_ref(self):
        """Test rendering with ref() function."""
        renderer = SafeTemplateRenderer(refs={"users": "prod.analytics.users"})
        result = renderer.render("SELECT * FROM {{ ref('users') }}")
        assert result == "SELECT * FROM prod.analytics.users"

    def test_render_with_date_add(self):
        """Test rendering with date_add() function."""
        renderer = SafeTemplateRenderer()
        result = renderer.render(
            "WHERE dt >= '{{ date_sub(ds, 7) }}' AND dt <= '{{ ds }}'",
            execution_date=date(2025, 1, 15),
        )
        assert result == "WHERE dt >= '2025-01-08' AND dt <= '2025-01-15'"

    def test_render_with_extra_params(self):
        """Test rendering with extra parameters."""
        renderer = SafeTemplateRenderer()
        result = renderer.render(
            "SELECT * FROM {{ table }} WHERE id = {{ id }}",
            extra_params={"table": "users", "id": 123},
        )
        assert result == "SELECT * FROM users WHERE id = 123"

    def test_render_with_context(self):
        """Test render_with_context method."""
        renderer = SafeTemplateRenderer()
        ctx = TemplateContext(
            execution_date=date(2025, 1, 15),
            variables={"schema": "prod"},
        )
        result = renderer.render_with_context(
            "SELECT * FROM {{ var('schema') }}.t WHERE dt = '{{ ds }}'",
            ctx,
        )
        assert result == "SELECT * FROM prod.t WHERE dt = '2025-01-15'"


class TestSQLRendererTemplateIntegration:
    """Tests for SQLRenderer.render_with_template_context method."""

    def test_render_with_template_context_ds(self):
        """Test render_with_template_context with ds."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "SELECT * FROM t WHERE dt = '{{ ds }}'",
            execution_date=date(2025, 1, 15),
        )
        assert result == "SELECT * FROM t WHERE dt = '2025-01-15'"

    def test_render_with_template_context_ds_nodash(self):
        """Test render_with_template_context with ds_nodash."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "SELECT * FROM t_{{ ds_nodash }}",
            execution_date=date(2025, 1, 15),
        )
        assert result == "SELECT * FROM t_20250115"

    def test_render_with_template_context_var(self):
        """Test render_with_template_context with var()."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "SELECT * FROM {{ var('schema', 'public') }}.users",
            variables={"schema": "prod"},
        )
        assert result == "SELECT * FROM prod.users"

    def test_render_with_template_context_ref(self):
        """Test render_with_template_context with ref()."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "SELECT * FROM {{ ref('users') }}",
            refs={"users": "prod.analytics.users"},
        )
        assert result == "SELECT * FROM prod.analytics.users"

    def test_render_with_template_context_date_functions(self):
        """Test render_with_template_context with date functions."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "WHERE dt >= '{{ date_sub(ds, 7) }}' AND dt <= '{{ date_add(ds, 1) }}'",
            execution_date=date(2025, 1, 15),
        )
        assert result == "WHERE dt >= '2025-01-08' AND dt <= '2025-01-16'"

    def test_render_with_template_context_extra_params(self):
        """Test render_with_template_context with extra_params."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            "SELECT * FROM {{ ref('users') }} WHERE status = '{{ status }}'",
            refs={"users": "prod.users"},
            extra_params={"status": "active"},
        )
        assert result == "SELECT * FROM prod.users WHERE status = 'active'"

    def test_render_with_template_context_full_example(self):
        """Test render_with_template_context with all features combined."""
        renderer = SQLRenderer()
        result = renderer.render_with_template_context(
            """
            SELECT
                user_id,
                COUNT(*) as count
            FROM {{ ref('events') }}
            WHERE dt >= '{{ date_sub(ds, 7) }}'
                AND dt <= '{{ ds }}'
                AND country = '{{ var('country', 'US') }}'
            GROUP BY user_id
            """,
            execution_date=date(2025, 1, 15),
            variables={"country": "KR"},
            refs={"events": "iceberg.analytics.events"},
        )
        assert "FROM iceberg.analytics.events" in result
        assert "dt >= '2025-01-08'" in result
        assert "dt <= '2025-01-15'" in result
        assert "country = 'KR'" in result


class TestSharedSQLFilters:
    """Tests for shared SQL filter functions."""

    def test_sql_string_escape_normal(self):
        """Test sql_string_escape with normal string."""
        assert sql_string_escape("hello") == "'hello'"

    def test_sql_string_escape_with_quotes(self):
        """Test sql_string_escape escapes single quotes."""
        assert sql_string_escape("O'Brien") == "'O''Brien'"

    def test_sql_string_escape_none(self):
        """Test sql_string_escape returns NULL for None."""
        assert sql_string_escape(None) == "NULL"

    def test_sql_list_escape_integers(self):
        """Test sql_list_escape with integers."""
        assert sql_list_escape([1, 2, 3]) == "(1, 2, 3)"

    def test_sql_list_escape_strings(self):
        """Test sql_list_escape with strings."""
        assert sql_list_escape(["a", "b"]) == "('a', 'b')"

    def test_sql_list_escape_with_quotes(self):
        """Test sql_list_escape escapes quotes in strings."""
        assert sql_list_escape(["it's", "ok"]) == "('it''s', 'ok')"

    def test_sql_list_escape_empty(self):
        """Test sql_list_escape returns (NULL) for empty list."""
        assert sql_list_escape([]) == "(NULL)"

    def test_sql_list_escape_with_none(self):
        """Test sql_list_escape handles None values."""
        assert sql_list_escape([1, None, 3]) == "(1, NULL, 3)"

    def test_sql_identifier_escape_normal(self):
        """Test sql_identifier_escape with normal identifier."""
        assert sql_identifier_escape("users") == '"users"'

    def test_sql_identifier_escape_with_quotes(self):
        """Test sql_identifier_escape escapes double quotes."""
        assert sql_identifier_escape('my"table') == '"my""table"'

    def test_sql_identifier_escape_none(self):
        """Test sql_identifier_escape returns empty quotes for None."""
        assert sql_identifier_escape(None) == '""'


class TestSandboxSecurity:
    """Security tests for SafeJinjaEnvironment sandbox."""

    def test_blocks_class_access(self):
        """Test sandbox blocks __class__ attribute access."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("{{ ''.__class__ }}")
        with pytest.raises(SecurityError):
            template.render()

    def test_blocks_mro_access(self):
        """Test sandbox blocks __mro__ attribute access."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("{{ ''.__class__.__mro__ }}")
        with pytest.raises(SecurityError):
            template.render()

    def test_blocks_subclasses_access(self):
        """Test sandbox blocks __subclasses__ access."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("{{ ''.__class__.__subclasses__() }}")
        with pytest.raises(SecurityError):
            template.render()

    def test_blocks_globals_access(self):
        """Test sandbox blocks __globals__ access."""
        env = SafeJinjaEnvironment.create_environment()
        # Create a function to try accessing its globals
        template = env.from_string("{{ func.__globals__ }}")
        with pytest.raises(SecurityError):
            template.render(func=lambda: None)

    def test_strict_undefined_raises_error(self):
        """Test undefined variables raise UndefinedError."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("{{ undefined_var }}")
        with pytest.raises(UndefinedError):
            template.render()

    def test_no_builtin_functions(self):
        """Test builtin functions are not available."""
        env = SafeJinjaEnvironment.create_environment()
        # range, dict, list, etc. should not be available by default
        template = env.from_string("{{ range(10) }}")
        with pytest.raises(UndefinedError):
            template.render()

    def test_safe_rendering_with_provided_context(self):
        """Test safe rendering works with explicitly provided context."""
        env = SafeJinjaEnvironment.create_environment()
        template = env.from_string("SELECT * FROM {{ table }} WHERE id = {{ id }}")
        result = template.render(table="users", id=123)
        assert result == "SELECT * FROM users WHERE id = 123"


class TestTemplateContextEdgeCases:
    """Edge case tests for TemplateContext."""

    def test_date_add_month_boundary(self):
        """Test date_add across month boundary."""
        ctx = TemplateContext()
        # January 31 + 1 day = February 1
        assert ctx.date_add("2025-01-31", 1) == "2025-02-01"

    def test_date_sub_month_boundary(self):
        """Test date_sub across month boundary."""
        ctx = TemplateContext()
        # February 1 - 1 day = January 31
        assert ctx.date_sub("2025-02-01", 1) == "2025-01-31"

    def test_date_add_year_boundary(self):
        """Test date_add across year boundary."""
        ctx = TemplateContext()
        assert ctx.date_add("2024-12-31", 1) == "2025-01-01"

    def test_date_add_invalid_format_raises(self):
        """Test date_add with invalid date format raises ValueError."""
        ctx = TemplateContext()
        with pytest.raises(ValueError):
            ctx.date_add("invalid-date", 1)

    def test_week_start_on_monday(self):
        """Test week_start_ds when execution date is Monday."""
        # 2025-01-13 is a Monday
        ctx = TemplateContext(execution_date=date(2025, 1, 13))
        assert ctx.week_start_ds == "2025-01-13"

    def test_week_start_on_sunday(self):
        """Test week_start_ds when execution date is Sunday."""
        # 2025-01-19 is a Sunday, week start is 2025-01-13 (Monday)
        ctx = TemplateContext(execution_date=date(2025, 1, 19))
        assert ctx.week_start_ds == "2025-01-13"
