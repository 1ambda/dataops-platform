"""Tests for dli.api.transpile module.

Covers:
- TranspileAPI initialization with context
- Mock mode operations
- SQL transpilation
- SQL validation
- SQL formatting
- Rule retrieval
"""

from __future__ import annotations

import pytest

from dli import ExecutionContext, TranspileAPI
from dli.models.common import (
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    ValidationResult,
)


class TestTranspileAPIInit:
    """Tests for TranspileAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = TranspileAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(mock_mode=True, dialect="bigquery")
        api = TranspileAPI(context=ctx)

        assert api.context is ctx
        assert api.context.mock_mode is True
        assert api.context.dialect == "bigquery"

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", mock_mode=True)
        api = TranspileAPI(context=ctx)

        result = repr(api)

        assert "TranspileAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_engine_init(self) -> None:
        """Test that engine is not created until needed."""
        api = TranspileAPI(context=ExecutionContext(mock_mode=True))

        # _engine should be None before any operation
        assert api._engine is None


class TestTranspileAPIMockMode:
    """Tests for TranspileAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        ctx = ExecutionContext(mock_mode=True)
        return TranspileAPI(context=ctx)

    def test_transpile_returns_original(self, mock_api: TranspileAPI) -> None:
        """Test transpile returns original SQL in mock mode."""
        sql = "SELECT * FROM events"
        result = mock_api.transpile(sql)

        assert result.original_sql == sql
        assert result.transpiled_sql == sql
        assert result.success is True
        assert result.applied_rules == []
        assert result.warnings == []

    def test_validate_sql_basic(self, mock_api: TranspileAPI) -> None:
        """Test SQL validation in mock mode."""
        result = mock_api.validate_sql("SELECT * FROM table")

        assert result.valid is True
        assert result.errors == []

    def test_validate_sql_empty(self, mock_api: TranspileAPI) -> None:
        """Test validation of empty SQL."""
        result = mock_api.validate_sql("")

        assert result.valid is False
        assert "Empty SQL" in result.errors

    def test_validate_sql_whitespace_only(self, mock_api: TranspileAPI) -> None:
        """Test validation of whitespace-only SQL."""
        result = mock_api.validate_sql("   ")

        assert result.valid is False

    def test_get_rules_returns_mock(self, mock_api: TranspileAPI) -> None:
        """Test get_rules returns mock rules."""
        rules = mock_api.get_rules()

        assert len(rules) == 1
        assert isinstance(rules[0], TranspileRule)
        assert rules[0].source_table == "raw.events"
        assert rules[0].target_table == "warehouse.events_v2"

    def test_format_sql_returns_original(self, mock_api: TranspileAPI) -> None:
        """Test format_sql returns original in mock mode."""
        sql = "select * from table"
        result = mock_api.format_sql(sql)

        assert result == sql


class TestTranspileAPITranspile:
    """Tests for TranspileAPI.transpile method."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_transpile_basic(self, mock_api: TranspileAPI) -> None:
        """Test basic transpilation."""
        result = mock_api.transpile("SELECT * FROM events")

        assert isinstance(result, TranspileResult)
        assert result.success is True

    def test_transpile_source_dialect(self, mock_api: TranspileAPI) -> None:
        """Test transpilation with source dialect."""
        result = mock_api.transpile(
            "SELECT * FROM table",
            source_dialect="trino",
        )

        assert result.success is True

    def test_transpile_target_dialect(self, mock_api: TranspileAPI) -> None:
        """Test transpilation with target dialect."""
        result = mock_api.transpile(
            "SELECT * FROM table",
            target_dialect="bigquery",
        )

        assert result.success is True

    def test_transpile_apply_rules_false(self, mock_api: TranspileAPI) -> None:
        """Test transpilation without applying rules."""
        result = mock_api.transpile(
            "SELECT * FROM table",
            apply_rules=False,
        )

        assert result.success is True

    def test_transpile_expand_metrics_false(self, mock_api: TranspileAPI) -> None:
        """Test transpilation without METRIC expansion."""
        result = mock_api.transpile(
            "SELECT METRIC('revenue') FROM table",
            expand_metrics=False,
        )

        assert result.success is True

    def test_transpile_result_has_duration(self, mock_api: TranspileAPI) -> None:
        """Test that result includes duration."""
        result = mock_api.transpile("SELECT 1")

        assert result.duration_ms >= 0

    def test_transpile_has_changes_false(self, mock_api: TranspileAPI) -> None:
        """Test has_changes when SQL is unchanged."""
        result = mock_api.transpile("SELECT 1")

        # In mock mode, SQL is unchanged
        assert result.has_changes is False


class TestTranspileAPIValidation:
    """Tests for TranspileAPI.validate_sql method."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_validate_valid_sql(self, mock_api: TranspileAPI) -> None:
        """Test validation of valid SQL."""
        result = mock_api.validate_sql("SELECT id, name FROM users WHERE id = 1")

        assert isinstance(result, ValidationResult)
        assert result.valid is True

    def test_validate_with_dialect(self, mock_api: TranspileAPI) -> None:
        """Test validation with specific dialect."""
        result = mock_api.validate_sql(
            "SELECT * FROM table",
            dialect="bigquery",
        )

        assert result.valid is True


class TestTranspileAPIFormatSQL:
    """Tests for TranspileAPI.format_sql method."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_format_basic(self, mock_api: TranspileAPI) -> None:
        """Test basic SQL formatting."""
        sql = "select * from table"
        result = mock_api.format_sql(sql)

        assert isinstance(result, str)

    def test_format_with_dialect(self, mock_api: TranspileAPI) -> None:
        """Test formatting with specific dialect."""
        sql = "SELECT * FROM table"
        result = mock_api.format_sql(sql, dialect="trino")

        assert isinstance(result, str)

    def test_format_with_indent(self, mock_api: TranspileAPI) -> None:
        """Test formatting with custom indent."""
        sql = "SELECT * FROM table"
        result = mock_api.format_sql(sql, indent=4)

        assert isinstance(result, str)


class TestTranspileAPIGetRules:
    """Tests for TranspileAPI.get_rules method."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_get_rules_returns_list(self, mock_api: TranspileAPI) -> None:
        """Test that get_rules returns a list."""
        rules = mock_api.get_rules()

        assert isinstance(rules, list)

    def test_get_rules_items_are_transpile_rules(self, mock_api: TranspileAPI) -> None:
        """Test that returned items are TranspileRule instances."""
        rules = mock_api.get_rules()

        for rule in rules:
            assert isinstance(rule, TranspileRule)
            assert hasattr(rule, "source_table")
            assert hasattr(rule, "target_table")
            assert hasattr(rule, "priority")
            assert hasattr(rule, "enabled")


class TestTranspileAPIStrictMode:
    """Tests for TranspileAPI strict mode behavior."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_strict_false_does_not_raise(self, mock_api: TranspileAPI) -> None:
        """Test that strict=False doesn't raise on warnings."""
        # In mock mode, this should not raise
        result = mock_api.transpile("SELECT 1", strict=False)

        assert result.success is True

    def test_strict_mode_documentation(self, mock_api: TranspileAPI) -> None:
        """Document strict mode behavior (in real mode would raise)."""
        # In mock mode, strict=True still works because there are no warnings
        result = mock_api.transpile("SELECT 1", strict=True)

        assert result.success is True


class TestTranspileResultModel:
    """Tests for TranspileResult model returned by API."""

    @pytest.fixture
    def mock_api(self) -> TranspileAPI:
        """Create TranspileAPI in mock mode."""
        return TranspileAPI(context=ExecutionContext(mock_mode=True))

    def test_result_is_frozen(self, mock_api: TranspileAPI) -> None:
        """Test that result is immutable."""
        from pydantic import ValidationError

        result = mock_api.transpile("SELECT 1")

        with pytest.raises(ValidationError):
            result.original_sql = "SELECT 2"  # type: ignore[misc]

    def test_result_fields(self, mock_api: TranspileAPI) -> None:
        """Test result contains all expected fields."""
        result = mock_api.transpile("SELECT 1")

        assert hasattr(result, "original_sql")
        assert hasattr(result, "transpiled_sql")
        assert hasattr(result, "success")
        assert hasattr(result, "applied_rules")
        assert hasattr(result, "warnings")
        assert hasattr(result, "duration_ms")

    def test_result_has_changes_property(self, mock_api: TranspileAPI) -> None:
        """Test has_changes property."""
        result = mock_api.transpile("SELECT 1")

        # Property should be callable
        _ = result.has_changes


class TestTranspileWarningModel:
    """Tests for TranspileWarning model."""

    def test_warning_minimal(self) -> None:
        """Test warning with minimal fields."""
        warning = TranspileWarning(message="Test warning")

        assert warning.message == "Test warning"
        assert warning.line is None
        assert warning.column is None
        assert warning.rule is None

    def test_warning_full(self) -> None:
        """Test warning with all fields."""
        warning = TranspileWarning(
            message="Deprecated syntax",
            line=10,
            column=5,
            rule="no_select_star",
        )

        assert warning.message == "Deprecated syntax"
        assert warning.line == 10
        assert warning.column == 5
        assert warning.rule == "no_select_star"
