"""Tests for transpile Pydantic models."""

from __future__ import annotations

from datetime import UTC, datetime

from pydantic import ValidationError
import pytest

from dli.core.transpile.models import (
    Dialect,
    MetricDefinition,
    MetricMatch,
    RuleType,
    TranspileConfig,
    TranspileMetadata,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    WarningType,
)

# =============================================================================
# Test: Enum Values
# =============================================================================


class TestDialect:
    """Tests for Dialect enum."""

    def test_values(self) -> None:
        """Test enum values."""
        assert Dialect.TRINO.value == "trino"
        assert Dialect.BIGQUERY.value == "bigquery"

    def test_string_conversion(self) -> None:
        """Test string conversion."""
        assert str(Dialect.TRINO) == "Dialect.TRINO"
        assert Dialect.TRINO.value == "trino"

    def test_from_string(self) -> None:
        """Test creating from string value."""
        assert Dialect("trino") == Dialect.TRINO
        assert Dialect("bigquery") == Dialect.BIGQUERY

    def test_invalid_dialect(self) -> None:
        """Test invalid dialect raises error."""
        with pytest.raises(ValueError):
            Dialect("postgres")


class TestRuleType:
    """Tests for RuleType enum."""

    def test_values(self) -> None:
        """Test enum values."""
        assert RuleType.TABLE_SUBSTITUTION.value == "table_substitution"
        assert RuleType.METRIC_EXPANSION.value == "metric_expansion"

    def test_all_values(self) -> None:
        """Test all enum members."""
        assert len(RuleType) == 2
        assert RuleType.TABLE_SUBSTITUTION in RuleType
        assert RuleType.METRIC_EXPANSION in RuleType


class TestWarningType:
    """Tests for WarningType enum."""

    def test_values(self) -> None:
        """Test all warning type values."""
        assert WarningType.NO_LIMIT.value == "no_limit"
        assert WarningType.SELECT_STAR.value == "select_star"
        assert WarningType.DUPLICATE_CTE.value == "duplicate_cte"
        assert WarningType.CORRELATED_SUBQUERY.value == "correlated_subquery"
        assert WarningType.DANGEROUS_STATEMENT.value == "dangerous_statement"
        assert WarningType.METRIC_ERROR.value == "metric_error"

    def test_all_warning_types(self) -> None:
        """Test all warning types are defined."""
        assert len(WarningType) == 6


# =============================================================================
# Test: MetricMatch (NamedTuple)
# =============================================================================


class TestMetricMatch:
    """Tests for MetricMatch NamedTuple."""

    def test_creation(self) -> None:
        """Test basic creation."""
        match = MetricMatch(
            full_match="METRIC(revenue)",
            metric_name="revenue",
            start_pos=7,
            end_pos=22,
        )
        assert match.full_match == "METRIC(revenue)"
        assert match.metric_name == "revenue"
        assert match.start_pos == 7
        assert match.end_pos == 22

    def test_tuple_unpacking(self) -> None:
        """Test tuple unpacking."""
        match = MetricMatch("METRIC(x)", "x", 0, 9)
        full, name, start, end = match
        assert full == "METRIC(x)"
        assert name == "x"
        assert start == 0
        assert end == 9

    def test_field_access(self) -> None:
        """Test named field access."""
        match = MetricMatch("METRIC('test')", "test", 10, 24)
        assert match[0] == "METRIC('test')"  # Index access
        assert match.metric_name == "test"  # Named access


# =============================================================================
# Test: TranspileConfig
# =============================================================================


class TestTranspileConfig:
    """Tests for TranspileConfig model."""

    def test_default_values(self) -> None:
        """Test default configuration."""
        config = TranspileConfig()
        assert config.dialect == Dialect.TRINO
        assert config.strict_mode is False
        assert config.validate_syntax is False
        assert config.retry_count == 1
        assert config.server_url is None

    def test_custom_values(self) -> None:
        """Test custom configuration."""
        config = TranspileConfig(
            dialect=Dialect.BIGQUERY,
            strict_mode=True,
            validate_syntax=True,
            retry_count=3,
            server_url="http://localhost:8080",
        )
        assert config.dialect == Dialect.BIGQUERY
        assert config.strict_mode is True
        assert config.validate_syntax is True
        assert config.retry_count == 3
        assert config.server_url == "http://localhost:8080"

    def test_retry_count_validation_min(self) -> None:
        """Test retry_count minimum bound."""
        config = TranspileConfig(retry_count=0)
        assert config.retry_count == 0

        with pytest.raises(ValidationError):
            TranspileConfig(retry_count=-1)

    def test_retry_count_validation_max(self) -> None:
        """Test retry_count maximum bound."""
        config = TranspileConfig(retry_count=5)
        assert config.retry_count == 5

        with pytest.raises(ValidationError):
            TranspileConfig(retry_count=6)

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = TranspileConfig(
            dialect=Dialect.BIGQUERY,
            strict_mode=True,
            retry_count=2,
        )
        json_str = original.model_dump_json()
        restored = TranspileConfig.model_validate_json(json_str)
        assert restored == original


# =============================================================================
# Test: TranspileRule
# =============================================================================


class TestTranspileRule:
    """Tests for TranspileRule model."""

    def test_minimal_creation(self) -> None:
        """Test creation with required fields only."""
        rule = TranspileRule(
            id="rule-001",
            type=RuleType.TABLE_SUBSTITUTION,
            source="old_table",
            target="new_table",
        )
        assert rule.id == "rule-001"
        assert rule.type == RuleType.TABLE_SUBSTITUTION
        assert rule.source == "old_table"
        assert rule.target == "new_table"
        assert rule.description is None
        assert rule.enabled is True  # Default

    def test_full_creation(self) -> None:
        """Test creation with all fields."""
        rule = TranspileRule(
            id="rule-002",
            type=RuleType.METRIC_EXPANSION,
            source="METRIC(revenue)",
            target="SUM(amount)",
            description="Revenue metric expansion",
            enabled=False,
        )
        assert rule.id == "rule-002"
        assert rule.type == RuleType.METRIC_EXPANSION
        assert rule.description == "Revenue metric expansion"
        assert rule.enabled is False

    def test_missing_required_field(self) -> None:
        """Test validation error for missing required field."""
        with pytest.raises(ValidationError):
            TranspileRule(  # type: ignore[call-arg]
                type=RuleType.TABLE_SUBSTITUTION,
                source="old",
                target="new",
            )  # Missing 'id'

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = TranspileRule(
            id="rule-test",
            type=RuleType.TABLE_SUBSTITUTION,
            source="schema.old",
            target="schema.new",
            description="Test rule",
            enabled=True,
        )
        json_str = original.model_dump_json()
        restored = TranspileRule.model_validate_json(json_str)
        assert restored == original


# =============================================================================
# Test: TranspileWarning
# =============================================================================


class TestTranspileWarning:
    """Tests for TranspileWarning model."""

    def test_minimal_creation(self) -> None:
        """Test creation with required fields only."""
        warning = TranspileWarning(
            type=WarningType.NO_LIMIT,
            message="No LIMIT clause detected",
        )
        assert warning.type == WarningType.NO_LIMIT
        assert warning.message == "No LIMIT clause detected"
        assert warning.line is None
        assert warning.column is None

    def test_full_creation(self) -> None:
        """Test creation with all fields."""
        warning = TranspileWarning(
            type=WarningType.SELECT_STAR,
            message="Consider specifying columns",
            line=5,
            column=10,
        )
        assert warning.type == WarningType.SELECT_STAR
        assert warning.message == "Consider specifying columns"
        assert warning.line == 5
        assert warning.column == 10

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = TranspileWarning(
            type=WarningType.DANGEROUS_STATEMENT,
            message="DROP TABLE detected",
            line=1,
        )
        json_str = original.model_dump_json()
        restored = TranspileWarning.model_validate_json(json_str)
        assert restored == original


# =============================================================================
# Test: MetricDefinition
# =============================================================================


class TestMetricDefinition:
    """Tests for MetricDefinition model."""

    def test_minimal_creation(self) -> None:
        """Test creation with required fields only."""
        metric = MetricDefinition(
            name="revenue",
            expression="SUM(amount)",
        )
        assert metric.name == "revenue"
        assert metric.expression == "SUM(amount)"
        assert metric.source_table is None
        assert metric.description is None

    def test_full_creation(self) -> None:
        """Test creation with all fields."""
        metric = MetricDefinition(
            name="daily_active_users",
            expression="COUNT(DISTINCT user_id)",
            source_table="analytics.events",
            description="Daily active users count",
        )
        assert metric.name == "daily_active_users"
        assert metric.expression == "COUNT(DISTINCT user_id)"
        assert metric.source_table == "analytics.events"
        assert metric.description == "Daily active users count"

    def test_missing_required_field(self) -> None:
        """Test validation error for missing required field."""
        with pytest.raises(ValidationError):
            MetricDefinition(name="test")  # type: ignore[call-arg]  # Missing 'expression'

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = MetricDefinition(
            name="conversion_rate",
            expression="COUNT(converted) / COUNT(*)",
            source_table="analytics.conversions",
            description="User conversion rate",
        )
        json_str = original.model_dump_json()
        restored = MetricDefinition.model_validate_json(json_str)
        assert restored == original


# =============================================================================
# Test: TranspileMetadata
# =============================================================================


class TestTranspileMetadata:
    """Tests for TranspileMetadata model."""

    def test_minimal_creation(self) -> None:
        """Test creation with required fields only."""
        metadata = TranspileMetadata(
            original_sql="SELECT * FROM users",
            dialect=Dialect.TRINO,
        )
        assert metadata.original_sql == "SELECT * FROM users"
        assert metadata.dialect == Dialect.TRINO
        assert metadata.transpiled_at is not None
        assert metadata.rules_version is None
        assert metadata.duration_ms == 0

    def test_full_creation(self) -> None:
        """Test creation with all fields."""
        now = datetime.now(tz=UTC)
        metadata = TranspileMetadata(
            original_sql="SELECT * FROM users",
            transpiled_at=now,
            dialect=Dialect.BIGQUERY,
            rules_version="v1.2.3",
            duration_ms=150,
        )
        assert metadata.original_sql == "SELECT * FROM users"
        assert metadata.transpiled_at == now
        assert metadata.dialect == Dialect.BIGQUERY
        assert metadata.rules_version == "v1.2.3"
        assert metadata.duration_ms == 150

    def test_duration_validation_non_negative(self) -> None:
        """Test duration_ms must be non-negative."""
        with pytest.raises(ValidationError):
            TranspileMetadata(
                original_sql="SELECT 1",
                dialect=Dialect.TRINO,
                duration_ms=-1,
            )

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        original = TranspileMetadata(
            original_sql="SELECT * FROM orders",
            dialect=Dialect.TRINO,
            rules_version="v1.0.0",
            duration_ms=50,
        )
        json_str = original.model_dump_json()
        restored = TranspileMetadata.model_validate_json(json_str)
        assert restored.original_sql == original.original_sql
        assert restored.dialect == original.dialect
        assert restored.duration_ms == original.duration_ms


# =============================================================================
# Test: TranspileResult
# =============================================================================


class TestTranspileResult:
    """Tests for TranspileResult model."""

    def test_successful_result(self) -> None:
        """Test successful transpile result."""
        metadata = TranspileMetadata(
            original_sql="SELECT * FROM old_table",
            dialect=Dialect.TRINO,
        )
        rule = TranspileRule(
            id="rule-001",
            type=RuleType.TABLE_SUBSTITUTION,
            source="old_table",
            target="new_table",
        )
        result = TranspileResult(
            success=True,
            sql="SELECT * FROM new_table",
            applied_rules=[rule],
            warnings=[],
            metadata=metadata,
            error=None,
        )
        assert result.success is True
        assert result.sql == "SELECT * FROM new_table"
        assert len(result.applied_rules) == 1
        assert len(result.warnings) == 0
        assert result.error is None

    def test_failed_result(self) -> None:
        """Test failed transpile result."""
        metadata = TranspileMetadata(
            original_sql="INVALID SQL",
            dialect=Dialect.TRINO,
        )
        result = TranspileResult(
            success=False,
            sql="INVALID SQL",
            applied_rules=[],
            warnings=[],
            metadata=metadata,
            error="Failed to parse SQL",
        )
        assert result.success is False
        assert result.error == "Failed to parse SQL"

    def test_result_with_warnings(self) -> None:
        """Test result with warnings."""
        metadata = TranspileMetadata(
            original_sql="SELECT * FROM users",
            dialect=Dialect.TRINO,
        )
        warning = TranspileWarning(
            type=WarningType.SELECT_STAR,
            message="Consider specifying columns",
        )
        result = TranspileResult(
            success=True,
            sql="SELECT * FROM users",
            applied_rules=[],
            warnings=[warning],
            metadata=metadata,
        )
        assert len(result.warnings) == 1
        assert result.warnings[0].type == WarningType.SELECT_STAR

    def test_to_json_method(self) -> None:
        """Test to_json method."""
        metadata = TranspileMetadata(
            original_sql="SELECT 1",
            dialect=Dialect.TRINO,
        )
        result = TranspileResult(
            success=True,
            sql="SELECT 1",
            metadata=metadata,
        )
        json_str = result.to_json()
        assert isinstance(json_str, str)
        assert '"success": true' in json_str
        assert '"sql": "SELECT 1"' in json_str

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization and deserialization."""
        metadata = TranspileMetadata(
            original_sql="SELECT * FROM test",
            dialect=Dialect.BIGQUERY,
            duration_ms=100,
        )
        rule = TranspileRule(
            id="r1",
            type=RuleType.TABLE_SUBSTITUTION,
            source="test",
            target="test_v2",
        )
        warning = TranspileWarning(
            type=WarningType.NO_LIMIT,
            message="Add LIMIT",
        )
        original = TranspileResult(
            success=True,
            sql="SELECT * FROM test_v2",
            applied_rules=[rule],
            warnings=[warning],
            metadata=metadata,
        )
        json_str = original.model_dump_json()
        restored = TranspileResult.model_validate_json(json_str)
        assert restored.success == original.success
        assert restored.sql == original.sql
        assert len(restored.applied_rules) == 1
        assert len(restored.warnings) == 1
