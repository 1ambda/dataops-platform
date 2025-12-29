"""Tests for BuiltinTests SQL generators.

This module tests all built-in generic test SQL generators,
including SQL injection prevention and edge cases.
"""

from __future__ import annotations

import pytest

from dli.core.quality.builtin_tests import BuiltinTests


# =============================================================================
# Identifier Validation Tests (SQL Injection Prevention)
# =============================================================================


class TestIdentifierValidation:
    """Tests for SQL identifier validation to prevent injection attacks."""

    def test_valid_simple_identifier(self) -> None:
        """Valid simple identifiers should pass validation."""
        assert BuiltinTests._validate_identifier("users") == "users"
        assert BuiltinTests._validate_identifier("_private") == "_private"
        assert BuiltinTests._validate_identifier("table123") == "table123"

    def test_valid_qualified_identifier(self) -> None:
        """Fully qualified table names (catalog.schema.table) should pass."""
        assert BuiltinTests._validate_identifier("catalog.schema.table") == "catalog.schema.table"
        assert BuiltinTests._validate_identifier("iceberg.analytics.daily_clicks") == "iceberg.analytics.daily_clicks"

    def test_empty_identifier_rejected(self) -> None:
        """Empty identifiers should be rejected."""
        with pytest.raises(ValueError, match="cannot be empty"):
            BuiltinTests._validate_identifier("")

    def test_too_long_identifier_rejected(self) -> None:
        """Identifiers exceeding max length should be rejected."""
        long_name = "a" * 257
        with pytest.raises(ValueError, match="too long"):
            BuiltinTests._validate_identifier(long_name)

    def test_sql_injection_patterns_rejected(self) -> None:
        """SQL injection patterns should be rejected."""
        # Various injection patterns
        injection_patterns = [
            "table; DROP TABLE users--",
            "table' OR '1'='1",
            "table`; DELETE FROM users;",
            "table\"; SELECT * FROM passwords--",
            "table<script>",
            "table/**/OR/**/1=1",
            "table\nDROP TABLE users",
            "table\tOR 1=1",
            "table$evil",
            "table@injection",
            "table#comment",
            "table%00",
            "-leading_hyphen",
            "123_starts_with_number",
        ]

        for pattern in injection_patterns:
            with pytest.raises(ValueError, match="Invalid identifier"):
                BuiltinTests._validate_identifier(pattern)

    def test_validate_identifiers_batch(self) -> None:
        """Batch validation should work for lists of identifiers."""
        valid_list = ["user_id", "name", "email"]
        result = BuiltinTests._validate_identifiers(valid_list)
        assert result == valid_list

    def test_validate_identifiers_fails_on_first_invalid(self) -> None:
        """Batch validation should fail if any identifier is invalid."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests._validate_identifiers(["valid", "invalid;drop", "also_valid"])


class TestStringEscaping:
    """Tests for string value escaping."""

    def test_escape_single_quote(self) -> None:
        """Single quotes should be doubled for SQL safety."""
        assert BuiltinTests._escape_string("O'Connor") == "O''Connor"
        assert BuiltinTests._escape_string("it's") == "it''s"

    def test_escape_multiple_quotes(self) -> None:
        """Multiple single quotes should all be escaped."""
        assert BuiltinTests._escape_string("'quoted'") == "''quoted''"

    def test_escape_no_quotes(self) -> None:
        """Strings without quotes should remain unchanged."""
        assert BuiltinTests._escape_string("normal_value") == "normal_value"


# =============================================================================
# NOT NULL Test Generator
# =============================================================================


class TestNotNullGenerator:
    """Tests for the not_null SQL generator."""

    def test_single_column(self) -> None:
        """NOT NULL with single column."""
        sql = BuiltinTests.not_null("users", ["email"])
        assert "SELECT * FROM users" in sql
        assert "WHERE email IS NULL" in sql

    def test_multiple_columns(self) -> None:
        """NOT NULL with multiple columns should use OR."""
        sql = BuiltinTests.not_null("users", ["email", "name", "created_at"])
        assert "email IS NULL" in sql
        assert "name IS NULL" in sql
        assert "created_at IS NULL" in sql
        assert " OR " in sql

    def test_fully_qualified_table(self) -> None:
        """NOT NULL with fully qualified table name."""
        sql = BuiltinTests.not_null("iceberg.analytics.daily_clicks", ["user_id"])
        assert "FROM iceberg.analytics.daily_clicks" in sql

    def test_empty_columns_rejected(self) -> None:
        """NOT NULL with empty columns list should raise error."""
        with pytest.raises(ValueError, match="At least one column"):
            BuiltinTests.not_null("users", [])

    def test_invalid_table_rejected(self) -> None:
        """NOT NULL with invalid table name should raise error."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests.not_null("users; DROP TABLE--", ["email"])

    def test_invalid_column_rejected(self) -> None:
        """NOT NULL with invalid column name should raise error."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests.not_null("users", ["email", "name; DROP"])


# =============================================================================
# UNIQUE Test Generator
# =============================================================================


class TestUniqueGenerator:
    """Tests for the unique SQL generator."""

    def test_single_column(self) -> None:
        """UNIQUE with single column."""
        sql = BuiltinTests.unique("users", ["email"])
        assert "SELECT email, COUNT(*) as _dli_count" in sql
        assert "FROM users" in sql
        assert "GROUP BY email" in sql
        assert "HAVING COUNT(*) > 1" in sql

    def test_multiple_columns(self) -> None:
        """UNIQUE with multiple columns (composite key)."""
        sql = BuiltinTests.unique("orders", ["order_id", "line_item"])
        assert "SELECT order_id, line_item, COUNT(*) as _dli_count" in sql
        assert "GROUP BY order_id, line_item" in sql

    def test_empty_columns_rejected(self) -> None:
        """UNIQUE with empty columns list should raise error."""
        with pytest.raises(ValueError, match="At least one column"):
            BuiltinTests.unique("users", [])


# =============================================================================
# ACCEPTED VALUES Test Generator
# =============================================================================


class TestAcceptedValuesGenerator:
    """Tests for the accepted_values SQL generator."""

    def test_string_values(self) -> None:
        """ACCEPTED VALUES with string list."""
        sql = BuiltinTests.accepted_values("users", "status", ["active", "inactive", "pending"])
        assert "FROM users" in sql
        assert "status NOT IN" in sql
        assert "'active'" in sql
        assert "'inactive'" in sql
        assert "'pending'" in sql
        assert "status IS NOT NULL" in sql

    def test_integer_values(self) -> None:
        """ACCEPTED VALUES with integer list."""
        sql = BuiltinTests.accepted_values("orders", "priority", [1, 2, 3])
        assert "priority NOT IN (1, 2, 3)" in sql
        # Integers should not have quotes
        assert "'1'" not in sql

    def test_float_values(self) -> None:
        """ACCEPTED VALUES with float list."""
        sql = BuiltinTests.accepted_values("products", "discount_rate", [0.1, 0.25, 0.5])
        assert "discount_rate NOT IN (0.1, 0.25, 0.5)" in sql

    def test_mixed_values(self) -> None:
        """ACCEPTED VALUES with mixed types (should work)."""
        sql = BuiltinTests.accepted_values("data", "value", ["text", 123, 45.67])
        assert "'text'" in sql
        assert "123" in sql
        assert "45.67" in sql

    def test_string_with_quote_escaped(self) -> None:
        """String values with quotes should be escaped."""
        sql = BuiltinTests.accepted_values("users", "company", ["O'Brien Inc", "Normal Corp"])
        assert "O''Brien Inc" in sql  # Escaped quote

    def test_empty_values_rejected(self) -> None:
        """ACCEPTED VALUES with empty values list should raise error."""
        with pytest.raises(ValueError, match="At least one value"):
            BuiltinTests.accepted_values("users", "status", [])

    def test_excludes_null_values(self) -> None:
        """ACCEPTED VALUES should exclude NULL values from check."""
        sql = BuiltinTests.accepted_values("users", "status", ["active"])
        assert "status IS NOT NULL" in sql


# =============================================================================
# RELATIONSHIPS Test Generator
# =============================================================================


class TestRelationshipsGenerator:
    """Tests for the relationships (FK check) SQL generator."""

    def test_basic_relationship(self) -> None:
        """Basic foreign key relationship check."""
        sql = BuiltinTests.relationships("orders", "user_id", "users", "id")
        assert "FROM orders a" in sql
        assert "LEFT JOIN users b" in sql
        assert "a.user_id = b.id" in sql
        assert "b.id IS NULL" in sql
        assert "a.user_id IS NOT NULL" in sql

    def test_qualified_tables(self) -> None:
        """Relationship with fully qualified table names."""
        sql = BuiltinTests.relationships(
            "iceberg.sales.orders",
            "customer_id",
            "iceberg.crm.customers",
            "id",
        )
        assert "FROM iceberg.sales.orders a" in sql
        assert "LEFT JOIN iceberg.crm.customers b" in sql

    def test_invalid_table_rejected(self) -> None:
        """Invalid table names should be rejected."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests.relationships("orders; DROP", "user_id", "users", "id")

    def test_invalid_to_table_rejected(self) -> None:
        """Invalid to_table names should be rejected."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests.relationships("orders", "user_id", "users; DROP", "id")


# =============================================================================
# RANGE CHECK Test Generator
# =============================================================================


class TestRangeCheckGenerator:
    """Tests for the range_check SQL generator."""

    def test_min_only(self) -> None:
        """RANGE CHECK with only minimum bound."""
        sql = BuiltinTests.range_check("orders", "quantity", min_value=0)
        assert "FROM orders" in sql
        assert "quantity < 0" in sql
        assert "quantity >" not in sql

    def test_max_only(self) -> None:
        """RANGE CHECK with only maximum bound."""
        sql = BuiltinTests.range_check("orders", "quantity", max_value=1000)
        assert "quantity > 1000" in sql
        assert "quantity <" not in sql

    def test_both_bounds(self) -> None:
        """RANGE CHECK with both minimum and maximum bounds."""
        sql = BuiltinTests.range_check("products", "price", min_value=0, max_value=9999.99)
        assert "price < 0" in sql
        assert "price > 9999.99" in sql
        assert " OR " in sql

    def test_negative_bounds(self) -> None:
        """RANGE CHECK with negative bounds."""
        sql = BuiltinTests.range_check("temperatures", "celsius", min_value=-273.15, max_value=1000)
        assert "celsius < -273.15" in sql
        assert "celsius > 1000" in sql

    def test_no_bounds_returns_false(self) -> None:
        """RANGE CHECK with no bounds should return WHERE FALSE (always pass)."""
        sql = BuiltinTests.range_check("orders", "quantity")
        assert "WHERE FALSE" in sql


# =============================================================================
# ROW COUNT Test Generator
# =============================================================================


class TestRowCountGenerator:
    """Tests for the row_count SQL generator."""

    def test_min_only(self) -> None:
        """ROW COUNT with only minimum count."""
        sql = BuiltinTests.row_count("users", min_count=1)
        assert "SELECT COUNT(*) as cnt FROM users" in sql
        assert "cnt < 1" in sql

    def test_max_only(self) -> None:
        """ROW COUNT with only maximum count."""
        sql = BuiltinTests.row_count("events", max_count=1000000)
        assert "cnt > 1000000" in sql

    def test_both_bounds(self) -> None:
        """ROW COUNT with both minimum and maximum."""
        sql = BuiltinTests.row_count("orders", min_count=10, max_count=10000)
        assert "cnt < 10" in sql
        assert "cnt > 10000" in sql
        assert " OR " in sql

    def test_no_bounds_returns_false(self) -> None:
        """ROW COUNT with no bounds should return WHERE FALSE."""
        sql = BuiltinTests.row_count("users")
        assert "WHERE FALSE" in sql

    def test_uses_cte(self) -> None:
        """ROW COUNT should use CTE for proper aggregation."""
        sql = BuiltinTests.row_count("users", min_count=1)
        assert "WITH _dli_counts AS" in sql
        assert "_dli_row_count" in sql


# =============================================================================
# Generic Generate Method
# =============================================================================


class TestGenerateDispatcher:
    """Tests for the generate() dispatcher method."""

    def test_dispatch_not_null(self) -> None:
        """Generate should dispatch to not_null correctly."""
        sql = BuiltinTests.generate("not_null", "users", columns=["email"])
        assert "email IS NULL" in sql

    def test_dispatch_unique(self) -> None:
        """Generate should dispatch to unique correctly."""
        sql = BuiltinTests.generate("unique", "users", columns=["email"])
        assert "GROUP BY email" in sql
        assert "HAVING COUNT(*) > 1" in sql

    def test_dispatch_accepted_values(self) -> None:
        """Generate should dispatch to accepted_values correctly."""
        sql = BuiltinTests.generate(
            "accepted_values",
            "users",
            column="status",
            values=["active", "inactive"],
        )
        assert "NOT IN" in sql

    def test_dispatch_relationships(self) -> None:
        """Generate should dispatch to relationships correctly."""
        sql = BuiltinTests.generate(
            "relationships",
            "orders",
            column="user_id",
            to="users",
            to_column="id",
        )
        assert "LEFT JOIN users" in sql

    def test_dispatch_range_check(self) -> None:
        """Generate should dispatch to range_check correctly."""
        sql = BuiltinTests.generate(
            "range_check",
            "orders",
            column="quantity",
            min=0,
            max=1000,
        )
        assert "quantity < 0" in sql
        assert "quantity > 1000" in sql

    def test_dispatch_row_count(self) -> None:
        """Generate should dispatch to row_count correctly."""
        sql = BuiltinTests.generate("row_count", "users", min=1)
        assert "cnt < 1" in sql

    def test_unknown_test_type_rejected(self) -> None:
        """Unknown test types should raise ValueError."""
        with pytest.raises(ValueError, match="Unknown test type"):
            BuiltinTests.generate("invalid_test_type", "users")

    def test_error_message_lists_valid_types(self) -> None:
        """Error message should list all valid test types."""
        with pytest.raises(ValueError) as exc_info:
            BuiltinTests.generate("bad_type", "users")

        error_message = str(exc_info.value)
        assert "not_null" in error_message
        assert "unique" in error_message
        assert "accepted_values" in error_message
        assert "relationships" in error_message
        assert "range_check" in error_message
        assert "row_count" in error_message


# =============================================================================
# SQL Output Quality Tests
# =============================================================================


class TestSqlOutputQuality:
    """Tests for SQL output formatting and correctness."""

    def test_sql_is_valid_syntax(self) -> None:
        """Generated SQL should be syntactically valid."""
        # Each generated SQL should be parseable
        test_cases = [
            BuiltinTests.not_null("t", ["c"]),
            BuiltinTests.unique("t", ["c"]),
            BuiltinTests.accepted_values("t", "c", ["v"]),
            BuiltinTests.relationships("t1", "c1", "t2", "c2"),
            BuiltinTests.range_check("t", "c", min_value=0, max_value=100),
            BuiltinTests.row_count("t", min_count=1),
        ]

        for sql in test_cases:
            # Basic structure validation
            assert "SELECT" in sql.upper()
            # No dangling keywords
            assert sql.strip().upper().startswith("SELECT") or sql.strip().upper().startswith("WITH")

    def test_multiline_formatting(self) -> None:
        """SQL should be formatted with newlines for readability."""
        sql = BuiltinTests.not_null("users", ["email"])
        lines = sql.strip().split("\n")
        # Should have at least 2 lines (SELECT and WHERE)
        assert len(lines) >= 2

    def test_no_trailing_whitespace(self) -> None:
        """SQL lines should not have trailing whitespace."""
        sql = BuiltinTests.unique("users", ["email", "name"])
        for line in sql.split("\n"):
            # Allow empty lines
            if line:
                assert line == line.rstrip(), f"Trailing whitespace found: {repr(line)}"
