"""Tests for METRIC() function parsing and expansion."""

from __future__ import annotations

from dli.core.transpile.metrics import (
    METRIC_PATTERN,
    expand_metrics,
    find_metric_functions,
)

# =============================================================================
# Test: METRIC_PATTERN regex
# =============================================================================


class TestMetricPattern:
    """Tests for METRIC_PATTERN regex."""

    def test_simple_identifier(self) -> None:
        """Test simple identifier: METRIC(name)."""
        match = METRIC_PATTERN.search("SELECT METRIC(revenue) FROM t")
        assert match is not None
        assert match.group(0) == "METRIC(revenue)"
        # Group 3 captures unquoted identifier
        assert match.group(3) == "revenue"

    def test_single_quoted_name(self) -> None:
        """Test single-quoted name: METRIC('name')."""
        match = METRIC_PATTERN.search("SELECT METRIC('total_sales') FROM t")
        assert match is not None
        assert match.group(0) == "METRIC('total_sales')"
        # Group 1 captures single-quoted name
        assert match.group(1) == "total_sales"

    def test_double_quoted_name(self) -> None:
        """Test double-quoted name: METRIC("name")."""
        match = METRIC_PATTERN.search('SELECT METRIC("daily_users") FROM t')
        assert match is not None
        assert match.group(0) == 'METRIC("daily_users")'
        # Group 2 captures double-quoted name
        assert match.group(2) == "daily_users"

    def test_case_insensitive(self) -> None:
        """Test case-insensitive matching."""
        for keyword in ["METRIC", "metric", "Metric", "MeTrIc"]:
            sql = f"SELECT {keyword}(revenue) FROM t"
            match = METRIC_PATTERN.search(sql)
            assert match is not None, f"Failed for: {keyword}"

    def test_whitespace_handling(self) -> None:
        """Test whitespace inside parentheses."""
        test_cases = [
            "METRIC( revenue )",
            "METRIC(  revenue  )",
            "METRIC(\trevenue\t)",
            "METRIC( 'name' )",
            'METRIC( "name" )',
        ]
        for sql in test_cases:
            match = METRIC_PATTERN.search(sql)
            assert match is not None, f"Failed for: {sql}"

    def test_no_match_invalid_name(self) -> None:
        """Test no match for invalid identifiers."""
        invalid_cases = [
            "METRIC()",  # Empty
            "METRIC(123abc)",  # Starts with number
            "METRIC(-name)",  # Starts with hyphen
        ]
        for sql in invalid_cases:
            match = METRIC_PATTERN.search(sql)
            assert match is None, f"Should not match: {sql}"

    def test_preserves_metric_name_case(self) -> None:
        """Test that metric name case is preserved."""
        match = METRIC_PATTERN.search("SELECT METRIC(TotalRevenue) FROM t")
        assert match is not None
        assert match.group(3) == "TotalRevenue"  # Case preserved


# =============================================================================
# Test: find_metric_functions
# =============================================================================


class TestFindMetricFunctions:
    """Tests for find_metric_functions."""

    def test_no_metrics(self) -> None:
        """Test SQL with no METRIC functions."""
        sql = "SELECT * FROM users"
        matches = find_metric_functions(sql)
        assert matches == []

    def test_single_metric(self) -> None:
        """Test SQL with single METRIC function."""
        sql = "SELECT METRIC(revenue) FROM orders"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == "revenue"
        assert matches[0].full_match == "METRIC(revenue)"

    def test_multiple_metrics(self) -> None:
        """Test SQL with multiple METRIC functions."""
        sql = "SELECT METRIC(revenue), METRIC('users') FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 2
        assert matches[0].metric_name == "revenue"
        assert matches[1].metric_name == "users"

    def test_position_tracking(self) -> None:
        """Test position tracking for matches."""
        sql = "SELECT METRIC(revenue) FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        match = matches[0]
        assert match.start_pos == 7  # Position after "SELECT "
        assert match.end_pos == 22  # Position after "METRIC(revenue)"
        assert sql[match.start_pos : match.end_pos] == "METRIC(revenue)"

    def test_mixed_quote_styles(self) -> None:
        """Test mixed quote styles in same SQL."""
        sql = "SELECT METRIC(a), METRIC('b'), METRIC(\"c\") FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 3
        assert [m.metric_name for m in matches] == ["a", "b", "c"]

    def test_case_insensitive_function_name(self) -> None:
        """Test case-insensitive function name."""
        sql = "SELECT metric(x), METRIC(y), Metric(z) FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 3
        assert [m.metric_name for m in matches] == ["x", "y", "z"]

    def test_metric_with_underscore(self) -> None:
        """Test metric names with underscores."""
        sql = "SELECT METRIC(total_order_count) FROM orders"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == "total_order_count"

    def test_empty_sql(self) -> None:
        """Test empty SQL string."""
        matches = find_metric_functions("")
        assert matches == []

    def test_whitespace_only_sql(self) -> None:
        """Test whitespace-only SQL string."""
        matches = find_metric_functions("   \n\t  ")
        assert matches == []


# =============================================================================
# Test: expand_metrics
# =============================================================================


class TestExpandMetrics:
    """Tests for expand_metrics function."""

    def test_no_metrics(self) -> None:
        """Test SQL without METRIC functions."""

        def resolver(name: str) -> str | None:
            return "SUM(x)"

        sql = "SELECT * FROM users"
        expanded, errors = expand_metrics(sql, resolver)
        assert expanded == sql
        assert errors == []

    def test_single_metric_expansion(self) -> None:
        """Test single METRIC expansion."""

        def resolver(name: str) -> str | None:
            if name == "revenue":
                return "SUM(amount)"
            return None

        sql = "SELECT METRIC(revenue) FROM orders"
        expanded, errors = expand_metrics(sql, resolver)
        assert expanded == "SELECT (SUM(amount)) FROM orders"
        assert errors == []

    def test_metric_not_found(self) -> None:
        """Test METRIC with unknown name."""

        def resolver(name: str) -> str | None:
            return None

        sql = "SELECT METRIC(unknown_metric) FROM t"
        expanded, errors = expand_metrics(sql, resolver)
        # Returns original SQL on error
        assert expanded == sql
        assert len(errors) == 1
        assert "unknown_metric" in errors[0]

    def test_expression_wrapping(self) -> None:
        """Test that expression is wrapped in parentheses."""

        def resolver(name: str) -> str | None:
            return "a + b"

        sql = "SELECT METRIC(x) * 2 FROM t"
        expanded, errors = expand_metrics(sql, resolver)
        # Should wrap to preserve precedence
        assert expanded == "SELECT (a + b) * 2 FROM t"
        assert errors == []

    def test_mvp_limitation_single_metric(self) -> None:
        """Test MVP limitation: only 1 METRIC per SQL by default."""

        def resolver(name: str) -> str | None:
            return "SUM(x)"

        sql = "SELECT METRIC(a), METRIC(b) FROM t"
        expanded, errors = expand_metrics(sql, resolver, max_metrics=1)
        # Should return original with error
        assert expanded == sql
        assert len(errors) == 1
        assert "Only 1 METRIC()" in errors[0]

    def test_unlimited_metrics(self) -> None:
        """Test unlimited METRIC expansion with max_metrics=0."""

        def resolver(name: str) -> str | None:
            return "SUM(x)"

        sql = "SELECT METRIC(a), METRIC(b), METRIC(c) FROM t"
        expanded, errors = expand_metrics(sql, resolver, max_metrics=0)
        # Should expand all
        assert expanded == "SELECT (SUM(x)), (SUM(x)), (SUM(x)) FROM t"
        assert errors == []

    def test_multiple_metrics_with_limit(self) -> None:
        """Test multiple METRIC expansion with custom limit."""

        def resolver(name: str) -> str | None:
            return f"SUM({name})"

        sql = "SELECT METRIC(a), METRIC(b) FROM t"
        expanded, errors = expand_metrics(sql, resolver, max_metrics=2)
        assert expanded == "SELECT (SUM(a)), (SUM(b)) FROM t"
        assert errors == []

    def test_position_preservation(self) -> None:
        """Test that positions are correctly maintained."""

        def resolver(name: str) -> str | None:
            if name == "short":
                return "x"
            if name == "long":
                return "very_long_expression"
            return None

        # Replacements of different lengths
        sql = "SELECT METRIC(short), METRIC(long) FROM t"
        expanded, errors = expand_metrics(sql, resolver, max_metrics=2)
        assert expanded == "SELECT (x), (very_long_expression) FROM t"
        assert errors == []

    def test_complex_expression_expansion(self) -> None:
        """Test expansion of complex SQL expressions."""

        def resolver(name: str) -> str | None:
            if name == "conversion_rate":
                return "CAST(converted_users AS DOUBLE) / total_users"
            return None

        sql = "SELECT METRIC(conversion_rate) * 100 AS rate_pct FROM funnel"
        expanded, errors = expand_metrics(sql, resolver)
        assert (
            expanded
            == "SELECT (CAST(converted_users AS DOUBLE) / total_users) * 100 AS rate_pct FROM funnel"
        )
        assert errors == []

    def test_resolver_called_with_correct_name(self) -> None:
        """Test that resolver is called with correct metric name."""
        called_with: list[str] = []

        def resolver(name: str) -> str | None:
            called_with.append(name)
            return f"resolved_{name}"

        sql = "SELECT METRIC(my_metric) FROM t"
        expand_metrics(sql, resolver)
        assert called_with == ["my_metric"]

    def test_partial_failure(self) -> None:
        """Test when some metrics resolve and some don't."""

        def resolver(name: str) -> str | None:
            if name == "known":
                return "SUM(x)"
            return None

        sql = "SELECT METRIC(known), METRIC(unknown) FROM t"
        # With max_metrics=2, both are attempted
        expanded, errors = expand_metrics(sql, resolver, max_metrics=2)
        # Partial expansion: known expands, unknown errors
        assert "SUM(x)" in expanded
        assert len(errors) == 1
        assert "unknown" in errors[0]


# =============================================================================
# Test: Edge Cases
# =============================================================================


class TestEdgeCases:
    """Tests for edge cases in METRIC parsing."""

    def test_metric_in_where_clause(self) -> None:
        """Test METRIC in WHERE clause."""

        def resolver(name: str) -> str | None:
            return "SUM(amount)"

        sql = "SELECT * FROM orders WHERE METRIC(revenue) > 1000"
        expanded, errors = expand_metrics(sql, resolver)
        assert expanded == "SELECT * FROM orders WHERE (SUM(amount)) > 1000"
        assert errors == []

    def test_metric_in_subquery(self) -> None:
        """Test METRIC in subquery."""

        def resolver(name: str) -> str | None:
            return "COUNT(*)"

        sql = "SELECT * FROM (SELECT METRIC(total) FROM t) sub"
        expanded, errors = expand_metrics(sql, resolver)
        assert expanded == "SELECT * FROM (SELECT (COUNT(*)) FROM t) sub"
        assert errors == []

    def test_metric_with_alias(self) -> None:
        """Test METRIC with column alias."""

        def resolver(name: str) -> str | None:
            return "SUM(amount)"

        sql = "SELECT METRIC(revenue) AS total_revenue FROM orders"
        expanded, errors = expand_metrics(sql, resolver)
        assert expanded == "SELECT (SUM(amount)) AS total_revenue FROM orders"
        assert errors == []

    def test_metric_in_group_by(self) -> None:
        """Test METRIC in GROUP BY clause."""

        def resolver(name: str) -> str | None:
            return "category"

        sql = "SELECT METRIC(segment), COUNT(*) FROM t GROUP BY METRIC(segment)"
        # With max_metrics=2
        expanded, errors = expand_metrics(sql, resolver, max_metrics=2)
        assert expanded == "SELECT (category), COUNT(*) FROM t GROUP BY (category)"
        assert errors == []

    def test_special_characters_in_quoted_name(self) -> None:
        """Test special characters in quoted metric name."""
        sql = "SELECT METRIC('my-metric-name') FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == "my-metric-name"

    def test_spaces_in_quoted_name(self) -> None:
        """Test spaces in quoted metric name."""
        sql = "SELECT METRIC('my metric') FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == "my metric"

    def test_numeric_suffix_in_name(self) -> None:
        """Test metric name with numeric suffix."""
        sql = "SELECT METRIC(revenue_2024) FROM t"
        matches = find_metric_functions(sql)
        assert len(matches) == 1
        assert matches[0].metric_name == "revenue_2024"
