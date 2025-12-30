"""Tests for TranspileEngine."""

from __future__ import annotations

import pytest

from dli.core.transpile.client import MockTranspileClient
from dli.core.transpile.engine import TranspileEngine
from dli.core.transpile.exceptions import (
    RuleFetchError,
    SqlParseError,
    TranspileError,
)
from dli.core.transpile.models import (
    Dialect,
    MetricDefinition,
    RuleType,
    TranspileConfig,
    TranspileRule,
    WarningType,
)

# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def mock_client() -> MockTranspileClient:
    """Create a mock transpile client."""
    return MockTranspileClient()


@pytest.fixture
def empty_client() -> MockTranspileClient:
    """Create an empty mock client (no rules/metrics)."""
    client = MockTranspileClient()
    client.clear()
    return client


@pytest.fixture
def default_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create engine with default config."""
    return TranspileEngine(client=mock_client)


@pytest.fixture
def strict_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create engine with strict mode."""
    config = TranspileConfig(strict_mode=True)
    return TranspileEngine(client=mock_client, config=config)


# =============================================================================
# Test: Engine Initialization
# =============================================================================


class TestEngineInit:
    """Tests for TranspileEngine initialization."""

    def test_default_initialization(self) -> None:
        """Test engine with default settings."""
        engine = TranspileEngine()
        assert engine.config is not None
        assert engine.config.dialect == Dialect.TRINO
        assert engine.client is not None

    def test_custom_client(self, mock_client: MockTranspileClient) -> None:
        """Test engine with custom client."""
        engine = TranspileEngine(client=mock_client)
        assert engine.client is mock_client

    def test_custom_config(self) -> None:
        """Test engine with custom config."""
        config = TranspileConfig(
            dialect=Dialect.BIGQUERY,
            strict_mode=True,
        )
        engine = TranspileEngine(config=config)
        assert engine.config.dialect == Dialect.BIGQUERY
        assert engine.config.strict_mode is True


# =============================================================================
# Test: Basic Transpilation
# =============================================================================


class TestBasicTranspile:
    """Tests for basic transpile operations."""

    def test_no_changes_needed(self, default_engine: TranspileEngine) -> None:
        """Test SQL that doesn't need transpilation."""
        sql = "SELECT * FROM some_table"
        result = default_engine.transpile(sql)
        assert result.success is True
        assert result.sql == sql  # No changes, just parsed and regenerated
        assert result.error is None

    def test_table_substitution(self, default_engine: TranspileEngine) -> None:
        """Test basic table substitution."""
        sql = "SELECT * FROM raw.events"
        result = default_engine.transpile(sql)
        assert result.success is True
        assert "warehouse.events_v2" in result.sql
        assert len(result.applied_rules) >= 1
        # Find the applied rule
        table_rules = [
            r for r in result.applied_rules if r.type == RuleType.TABLE_SUBSTITUTION
        ]
        assert len(table_rules) >= 1

    def test_multiple_table_substitutions(
        self, default_engine: TranspileEngine
    ) -> None:
        """Test multiple table substitutions in same query."""
        sql = "SELECT * FROM raw.events e JOIN analytics.users u ON e.user_id = u.id"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Both tables should be substituted
        assert "warehouse.events_v2" in result.sql
        assert "analytics.users_v2" in result.sql

    def test_empty_sql(self, default_engine: TranspileEngine) -> None:
        """Test empty SQL string."""
        result = default_engine.transpile("")
        assert result.success is True
        assert result.sql == ""

    def test_whitespace_only_sql(self, default_engine: TranspileEngine) -> None:
        """Test whitespace-only SQL."""
        result = default_engine.transpile("   \n\t  ")
        assert result.success is True


# =============================================================================
# Test: METRIC Expansion
# =============================================================================


class TestMetricExpansion:
    """Tests for METRIC() function expansion."""

    def test_metric_expansion(self, default_engine: TranspileEngine) -> None:
        """Test METRIC() expansion."""
        sql = "SELECT METRIC(revenue) FROM orders"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Should contain the expanded expression
        assert "SUM(amount * quantity)" in result.sql
        # Should have metric expansion rule
        metric_rules = [
            r for r in result.applied_rules if r.type == RuleType.METRIC_EXPANSION
        ]
        assert len(metric_rules) >= 1

    def test_metric_not_found_graceful(self, default_engine: TranspileEngine) -> None:
        """Test unknown metric in non-strict mode."""
        sql = "SELECT METRIC(unknown_metric) FROM t"
        result = default_engine.transpile(sql)
        # Non-strict mode should still succeed with warnings
        assert result.success is True
        assert len(result.warnings) >= 1

    def test_metric_not_found_strict(self, strict_engine: TranspileEngine) -> None:
        """Test unknown metric in strict mode."""
        sql = "SELECT METRIC(unknown_metric) FROM t"
        with pytest.raises(TranspileError):
            strict_engine.transpile(sql)

    def test_metric_with_table_substitution(
        self, default_engine: TranspileEngine
    ) -> None:
        """Test METRIC expansion combined with table substitution."""
        sql = "SELECT METRIC(revenue) FROM raw.events"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Both should be applied
        assert "SUM(amount * quantity)" in result.sql
        assert "warehouse.events_v2" in result.sql


# =============================================================================
# Test: Warning Detection
# =============================================================================


class TestWarningDetection:
    """Tests for SQL warning detection."""

    def test_select_star_warning(self, default_engine: TranspileEngine) -> None:
        """Test SELECT * warning."""
        sql = "SELECT * FROM users LIMIT 10"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Should detect SELECT *
        star_warnings = [
            w for w in result.warnings if w.type == WarningType.SELECT_STAR
        ]
        assert len(star_warnings) >= 1

    def test_no_limit_warning(self, default_engine: TranspileEngine) -> None:
        """Test missing LIMIT warning."""
        sql = "SELECT id, name FROM users"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Should detect missing LIMIT
        limit_warnings = [w for w in result.warnings if w.type == WarningType.NO_LIMIT]
        assert len(limit_warnings) >= 1

    def test_dangerous_statement_warning(self, default_engine: TranspileEngine) -> None:
        """Test dangerous statement warning (DROP)."""
        sql = "DROP TABLE users"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Should detect dangerous statement
        dangerous_warnings = [
            w for w in result.warnings if w.type == WarningType.DANGEROUS_STATEMENT
        ]
        assert len(dangerous_warnings) >= 1

    def test_no_warnings_for_safe_query(self, default_engine: TranspileEngine) -> None:
        """Test no warnings for safe query."""
        sql = "SELECT id, name FROM users LIMIT 100"
        result = default_engine.transpile(sql)
        assert result.success is True
        # Should have no SELECT_STAR or NO_LIMIT warnings
        bad_warnings = [
            w
            for w in result.warnings
            if w.type in (WarningType.SELECT_STAR, WarningType.NO_LIMIT)
        ]
        assert len(bad_warnings) == 0


# =============================================================================
# Test: Strict Mode vs Graceful Degradation
# =============================================================================


class TestStrictMode:
    """Tests for strict mode behavior."""

    def test_parse_error_graceful(self, default_engine: TranspileEngine) -> None:
        """Test parse error in non-strict mode."""
        # SQL that truly fails to parse (unclosed parenthesis)
        sql = "SELECT * FROM users WHERE id IN (1, 2"
        result = default_engine.transpile(sql)
        assert result.success is False
        assert result.error is not None
        # Original SQL should be preserved
        assert result.sql == sql

    def test_parse_error_strict(self, strict_engine: TranspileEngine) -> None:
        """Test parse error in strict mode."""
        # SQL that truly fails to parse (unclosed parenthesis)
        sql = "SELECT * FROM users WHERE id IN (1, 2"
        with pytest.raises((TranspileError, SqlParseError)):
            strict_engine.transpile(sql)

    def test_rule_fetch_error_graceful(self, empty_client: MockTranspileClient) -> None:
        """Test rule fetch failure in non-strict mode."""

        class FailingClient:
            def get_rules(self, project_id: str | None = None) -> list[TranspileRule]:
                raise RuleFetchError("Server unavailable")

            def get_metric(self, name: str) -> MetricDefinition:
                raise RuleFetchError("Server unavailable")

            def list_metric_names(self) -> list[str]:
                return []

        engine = TranspileEngine(
            client=FailingClient(),  # type: ignore
            config=TranspileConfig(strict_mode=False, retry_count=0),
        )
        sql = "SELECT * FROM users"
        result = engine.transpile(sql)
        # Should gracefully degrade
        assert result.success is True
        assert result.sql == sql  # No changes applied

    def test_rule_fetch_error_strict(self) -> None:
        """Test rule fetch failure in strict mode."""

        class FailingClient:
            def get_rules(self, project_id: str | None = None) -> list[TranspileRule]:
                raise RuleFetchError("Server unavailable")

            def get_metric(self, name: str) -> MetricDefinition:
                raise RuleFetchError("Server unavailable")

            def list_metric_names(self) -> list[str]:
                return []

        engine = TranspileEngine(
            client=FailingClient(),  # type: ignore
            config=TranspileConfig(strict_mode=True, retry_count=0),
        )
        sql = "SELECT * FROM users"
        with pytest.raises(RuleFetchError):
            engine.transpile(sql)


# =============================================================================
# Test: Empty Rules
# =============================================================================


class TestEmptyRules:
    """Tests for transpilation with no rules."""

    def test_no_rules_no_changes(self, empty_client: MockTranspileClient) -> None:
        """Test transpilation with empty rules list."""
        engine = TranspileEngine(client=empty_client)
        sql = "SELECT * FROM raw.events"
        result = engine.transpile(sql)
        assert result.success is True
        # No substitution should happen
        assert result.applied_rules == []

    def test_no_metrics_available(self, empty_client: MockTranspileClient) -> None:
        """Test METRIC expansion with no metrics defined."""
        engine = TranspileEngine(client=empty_client)
        sql = "SELECT METRIC(revenue) FROM orders"
        result = engine.transpile(sql)
        assert result.success is True
        # Should have warning about metric not found
        assert len(result.warnings) >= 1


# =============================================================================
# Test: Metadata
# =============================================================================


class TestMetadata:
    """Tests for transpile metadata."""

    def test_metadata_populated(self, default_engine: TranspileEngine) -> None:
        """Test metadata is populated correctly."""
        sql = "SELECT * FROM users"
        result = default_engine.transpile(sql)
        assert result.metadata is not None
        assert result.metadata.original_sql == sql
        assert result.metadata.dialect == Dialect.TRINO
        assert result.metadata.duration_ms >= 0
        assert result.metadata.transpiled_at is not None

    def test_metadata_preserves_original_sql(
        self, default_engine: TranspileEngine
    ) -> None:
        """Test original SQL is preserved in metadata."""
        sql = "SELECT * FROM raw.events"
        result = default_engine.transpile(sql)
        # SQL may be transformed
        assert "warehouse.events_v2" in result.sql
        # But original is preserved
        assert result.metadata.original_sql == sql
        assert "raw.events" in result.metadata.original_sql


# =============================================================================
# Test: Context
# =============================================================================


class TestContext:
    """Tests for transpile context."""

    def test_context_passed_to_client(
        self, mock_client: MockTranspileClient
    ) -> None:
        """Test context is available for rule scoping."""
        engine = TranspileEngine(client=mock_client)
        sql = "SELECT * FROM users"
        context = {"project_id": "my-project"}
        result = engine.transpile(sql, context=context)
        assert result.success is True

    def test_empty_context(self, default_engine: TranspileEngine) -> None:
        """Test transpilation with empty context."""
        sql = "SELECT * FROM users"
        result = default_engine.transpile(sql, context={})
        assert result.success is True

    def test_none_context(self, default_engine: TranspileEngine) -> None:
        """Test transpilation with None context."""
        sql = "SELECT * FROM users"
        result = default_engine.transpile(sql, context=None)
        assert result.success is True


# =============================================================================
# Test: Validate SQL
# =============================================================================


class TestValidateSql:
    """Tests for SQL validation."""

    def test_valid_sql(self, default_engine: TranspileEngine) -> None:
        """Test validation of valid SQL."""
        sql = "SELECT id, name FROM users WHERE active = true"
        errors = default_engine.validate_sql(sql)
        assert errors == []

    def test_invalid_sql_transpile(self, default_engine: TranspileEngine) -> None:
        """Test that invalid SQL fails during transpilation (not validation alone).

        Note: validate_sql with empty rules doesn't parse (early return optimization).
        Use transpile() to detect parse errors in real scenarios.
        """
        # SQL that truly fails to parse (unclosed parenthesis)
        sql = "SELECT * FROM users WHERE id IN (1, 2"
        result = default_engine.transpile(sql)
        # Should fail during transpilation
        assert result.success is False
        assert result.error is not None
        assert "parse" in result.error.lower() or "expecting" in result.error.lower()

    def test_empty_sql_validation(self, default_engine: TranspileEngine) -> None:
        """Test validation of empty SQL."""
        errors = default_engine.validate_sql("")
        assert errors == []

    def test_complex_valid_sql(self, default_engine: TranspileEngine) -> None:
        """Test validation of complex valid SQL."""
        sql = """
        WITH active_users AS (
            SELECT id, name FROM users WHERE active = true
        )
        SELECT u.*, o.total
        FROM active_users u
        JOIN (
            SELECT user_id, SUM(amount) as total
            FROM orders
            GROUP BY user_id
        ) o ON u.id = o.user_id
        ORDER BY o.total DESC
        LIMIT 100
        """
        errors = default_engine.validate_sql(sql)
        assert errors == []


# =============================================================================
# Test: Dialect Handling
# =============================================================================


class TestDialectHandling:
    """Tests for SQL dialect handling."""

    def test_trino_dialect(self, mock_client: MockTranspileClient) -> None:
        """Test Trino dialect."""
        config = TranspileConfig(dialect=Dialect.TRINO)
        engine = TranspileEngine(client=mock_client, config=config)
        sql = "SELECT * FROM users"
        result = engine.transpile(sql)
        assert result.success is True
        assert result.metadata.dialect == Dialect.TRINO

    def test_bigquery_dialect(self, mock_client: MockTranspileClient) -> None:
        """Test BigQuery dialect."""
        config = TranspileConfig(dialect=Dialect.BIGQUERY)
        engine = TranspileEngine(client=mock_client, config=config)
        sql = "SELECT * FROM users"
        result = engine.transpile(sql)
        assert result.success is True
        assert result.metadata.dialect == Dialect.BIGQUERY


# =============================================================================
# Test: Complex Queries
# =============================================================================


class TestComplexQueries:
    """Tests for complex SQL queries."""

    def test_cte_query(self, default_engine: TranspileEngine) -> None:
        """Test Common Table Expression (CTE)."""
        sql = """
        WITH active_events AS (
            SELECT * FROM raw.events WHERE status = 'active'
        )
        SELECT * FROM active_events LIMIT 100
        """
        result = default_engine.transpile(sql)
        assert result.success is True
        # Table should be substituted in CTE
        assert "warehouse.events_v2" in result.sql

    def test_subquery(self, default_engine: TranspileEngine) -> None:
        """Test subquery transpilation."""
        sql = """
        SELECT *
        FROM (
            SELECT * FROM analytics.users WHERE active = true
        ) sub
        LIMIT 10
        """
        result = default_engine.transpile(sql)
        assert result.success is True
        assert "analytics.users_v2" in result.sql

    def test_join_query(self, default_engine: TranspileEngine) -> None:
        """Test JOIN query transpilation."""
        sql = """
        SELECT e.*, u.name
        FROM raw.events e
        LEFT JOIN analytics.users u ON e.user_id = u.id
        LIMIT 100
        """
        result = default_engine.transpile(sql)
        assert result.success is True
        assert "warehouse.events_v2" in result.sql
        assert "analytics.users_v2" in result.sql

    def test_union_query(self, default_engine: TranspileEngine) -> None:
        """Test UNION query transpilation."""
        sql = """
        SELECT id, name FROM analytics.users WHERE region = 'us'
        UNION ALL
        SELECT id, name FROM analytics.users WHERE region = 'eu'
        LIMIT 100
        """
        result = default_engine.transpile(sql)
        assert result.success is True
        # Both references should be substituted
        assert result.sql.count("analytics.users_v2") == 2


# =============================================================================
# Test: Retry Logic
# =============================================================================


class TestRetryLogic:
    """Tests for retry logic in rule fetching."""

    def test_retry_on_failure(self) -> None:
        """Test that retries are attempted."""
        call_count = 0

        class RetryableClient:
            def get_rules(self, project_id: str | None = None) -> list[TranspileRule]:
                nonlocal call_count
                call_count += 1
                if call_count < 2:
                    raise RuleFetchError("Temporary failure")
                return []

            def get_metric(self, name: str) -> MetricDefinition:
                return MetricDefinition(name=name, expression="1")

            def list_metric_names(self) -> list[str]:
                return []

        config = TranspileConfig(retry_count=2, strict_mode=False)
        engine = TranspileEngine(client=RetryableClient(), config=config)  # type: ignore
        result = engine.transpile("SELECT 1")
        assert result.success is True
        # Should have retried
        assert call_count == 2

    def test_no_retry_when_zero(self) -> None:
        """Test no retries when retry_count is 0."""
        call_count = 0

        class FailingClient:
            def get_rules(self, project_id: str | None = None) -> list[TranspileRule]:
                nonlocal call_count
                call_count += 1
                raise RuleFetchError("Always fails")

            def get_metric(self, name: str) -> MetricDefinition:
                raise RuleFetchError("Always fails")

            def list_metric_names(self) -> list[str]:
                return []

        config = TranspileConfig(retry_count=0, strict_mode=False)
        engine = TranspileEngine(client=FailingClient(), config=config)  # type: ignore
        result = engine.transpile("SELECT 1")
        assert result.success is True  # Graceful degradation
        assert call_count == 1  # Only one attempt
