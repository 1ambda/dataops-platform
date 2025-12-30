"""Tests for dli.models.common module.

Covers:
- ExecutionContext: Configuration and env var loading
- ResultStatus: Enum values and string conversion
- ValidationResult: Validation result model
- BaseResult: Base execution result model
- DatasetResult, MetricResult, TranspileResult: Specialized result models
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from pydantic import ValidationError
import pytest

from dli.models.common import (
    BaseResult,
    ConfigValue,
    DatasetResult,
    EnvironmentInfo,
    ExecutionContext,
    MetricResult,
    ResultStatus,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    ValidationResult,
)


class TestResultStatus:
    """Tests for ResultStatus enum."""

    def test_values(self) -> None:
        """Test enum values match expected strings."""
        assert ResultStatus.SUCCESS.value == "success"
        assert ResultStatus.FAILURE.value == "failure"
        assert ResultStatus.SKIPPED.value == "skipped"
        assert ResultStatus.PENDING.value == "pending"

    def test_string_conversion(self) -> None:
        """Test string conversion via .value property.

        Note: In Python 3.11+, str(Enum) returns "Enum.VALUE" even for
        (str, Enum) subclasses, so we use .value for consistent behavior.
        """
        assert ResultStatus.SUCCESS.value == "success"
        assert ResultStatus.FAILURE.value == "failure"

    def test_enum_membership(self) -> None:
        """Test that string values map to enum members."""
        assert ResultStatus("success") is ResultStatus.SUCCESS
        assert ResultStatus("failure") is ResultStatus.FAILURE

    def test_invalid_value_raises(self) -> None:
        """Test that invalid values raise ValueError."""
        with pytest.raises(ValueError):
            ResultStatus("invalid")


class TestExecutionContext:
    """Tests for ExecutionContext (pydantic-settings based)."""

    def test_default_values(self) -> None:
        """Test default values are set correctly."""
        ctx = ExecutionContext()

        assert ctx.project_path is None
        assert ctx.server_url is None
        assert ctx.api_token is None
        assert ctx.mock_mode is False
        assert ctx.dry_run is False
        assert ctx.dialect == "trino"
        assert ctx.parameters == {}
        assert ctx.verbose is False

    def test_explicit_values(self) -> None:
        """Test explicit configuration."""
        ctx = ExecutionContext(
            project_path=Path("/test/path"),
            server_url="https://example.com",
            api_token="secret-token",
            mock_mode=True,
            dry_run=True,
            dialect="bigquery",
            parameters={"key": "value"},
            verbose=True,
        )

        assert ctx.project_path == Path("/test/path")
        assert ctx.server_url == "https://example.com"
        assert ctx.api_token == "secret-token"
        assert ctx.mock_mode is True
        assert ctx.dry_run is True
        assert ctx.dialect == "bigquery"
        assert ctx.parameters == {"key": "value"}
        assert ctx.verbose is True

    def test_env_var_loading(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test loading from environment variables with DLI_ prefix."""
        monkeypatch.setenv("DLI_SERVER_URL", "https://env.example.com")
        monkeypatch.setenv("DLI_MOCK_MODE", "true")
        monkeypatch.setenv("DLI_DIALECT", "snowflake")

        ctx = ExecutionContext()

        assert ctx.server_url == "https://env.example.com"
        assert ctx.mock_mode is True
        assert ctx.dialect == "snowflake"

    def test_explicit_overrides_env(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test explicit values override environment variables."""
        monkeypatch.setenv("DLI_SERVER_URL", "https://env.example.com")

        ctx = ExecutionContext(server_url="https://explicit.example.com")

        assert ctx.server_url == "https://explicit.example.com"

    def test_repr(self) -> None:
        """Test __repr__ returns concise representation."""
        ctx = ExecutionContext(
            server_url="https://test.com",
            mock_mode=True,
            dialect="trino",
        )

        repr_str = repr(ctx)

        assert "ExecutionContext" in repr_str
        assert "server_url='https://test.com'" in repr_str
        assert "mock_mode=True" in repr_str
        assert "dialect='trino'" in repr_str

    def test_dialect_literal_values(self) -> None:
        """Test that dialect accepts valid literal values."""
        for dialect in ["trino", "bigquery", "snowflake", "duckdb", "spark"]:
            ctx = ExecutionContext(dialect=dialect)  # type: ignore[arg-type]
            assert ctx.dialect == dialect

    def test_extra_fields_ignored(self) -> None:
        """Test that extra fields are ignored (per settings config)."""
        # This should not raise due to extra="ignore"
        ctx = ExecutionContext(unknown_field="value")  # type: ignore[call-arg]
        assert not hasattr(ctx, "unknown_field")


class TestValidationResult:
    """Tests for ValidationResult model."""

    def test_valid_result(self) -> None:
        """Test creating a valid result."""
        result = ValidationResult(valid=True)

        assert result.valid is True
        assert result.errors == []
        assert result.warnings == []

    def test_invalid_result_with_errors(self) -> None:
        """Test creating an invalid result with errors."""
        result = ValidationResult(
            valid=False,
            errors=["Error 1", "Error 2"],
            warnings=["Warning 1"],
        )

        assert result.valid is False
        assert len(result.errors) == 2
        assert len(result.warnings) == 1

    def test_has_errors_property(self) -> None:
        """Test has_errors property."""
        result_ok = ValidationResult(valid=True)
        result_err = ValidationResult(valid=False, errors=["Error"])

        assert result_ok.has_errors is False
        assert result_err.has_errors is True

    def test_has_warnings_property(self) -> None:
        """Test has_warnings property."""
        result_ok = ValidationResult(valid=True)
        result_warn = ValidationResult(valid=True, warnings=["Warning"])

        assert result_ok.has_warnings is False
        assert result_warn.has_warnings is True

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        result = ValidationResult(valid=True)

        with pytest.raises(ValidationError):
            result.valid = False  # type: ignore[misc]

    def test_required_valid_field(self) -> None:
        """Test that valid field is required."""
        with pytest.raises(ValidationError):
            ValidationResult()  # type: ignore[call-arg]


class TestBaseResult:
    """Tests for BaseResult model."""

    def test_minimal_creation(self) -> None:
        """Test creating with minimal required fields."""
        now = datetime.now(tz=UTC)
        result = BaseResult(
            status=ResultStatus.SUCCESS,
            started_at=now,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.started_at == now
        assert result.ended_at is None
        assert result.duration_ms is None
        assert result.error_message is None

    def test_full_creation(self) -> None:
        """Test creating with all fields."""
        start = datetime.now(tz=UTC)
        end = datetime.now(tz=UTC)

        result = BaseResult(
            status=ResultStatus.FAILURE,
            started_at=start,
            ended_at=end,
            duration_ms=1500,
            error_message="Something failed",
        )

        assert result.status == ResultStatus.FAILURE
        assert result.ended_at == end
        assert result.duration_ms == 1500
        assert result.error_message == "Something failed"

    def test_frozen_model(self) -> None:
        """Test that model is frozen."""
        now = datetime.now(tz=UTC)
        result = BaseResult(status=ResultStatus.SUCCESS, started_at=now)

        with pytest.raises(ValidationError):
            result.status = ResultStatus.FAILURE  # type: ignore[misc]


class TestDatasetResult:
    """Tests for DatasetResult model."""

    def test_minimal_creation(self) -> None:
        """Test creating with minimal required fields."""
        now = datetime.now(tz=UTC)
        result = DatasetResult(
            name="test.dataset",
            status=ResultStatus.SUCCESS,
            started_at=now,
        )

        assert result.name == "test.dataset"
        assert result.status == ResultStatus.SUCCESS
        assert result.sql is None
        assert result.rows_affected is None

    def test_with_sql_and_rows(self) -> None:
        """Test creating with SQL and rows affected."""
        now = datetime.now(tz=UTC)
        result = DatasetResult(
            name="catalog.schema.table",
            status=ResultStatus.SUCCESS,
            started_at=now,
            ended_at=now,
            duration_ms=500,
            sql="INSERT INTO catalog.schema.table SELECT * FROM source",
            rows_affected=1000,
        )

        assert result.sql is not None
        assert "INSERT INTO" in result.sql
        assert result.rows_affected == 1000


class TestMetricResult:
    """Tests for MetricResult model."""

    def test_minimal_creation(self) -> None:
        """Test creating with minimal required fields."""
        now = datetime.now(tz=UTC)
        result = MetricResult(
            name="test.metric",
            status=ResultStatus.SUCCESS,
            started_at=now,
        )

        assert result.name == "test.metric"
        assert result.data is None
        assert result.row_count is None
        assert result.columns is None

    def test_with_data(self) -> None:
        """Test creating with result data."""
        now = datetime.now(tz=UTC)
        data = [{"id": 1, "value": 100}, {"id": 2, "value": 200}]

        result = MetricResult(
            name="catalog.schema.metric",
            status=ResultStatus.SUCCESS,
            started_at=now,
            data=data,
            row_count=2,
            columns=["id", "value"],
        )

        assert result.data == data
        assert result.row_count == 2
        assert result.columns == ["id", "value"]


class TestTranspileResult:
    """Tests for TranspileResult model."""

    def test_successful_transpile(self) -> None:
        """Test successful transpilation result."""
        result = TranspileResult(
            original_sql="SELECT * FROM old_table",
            transpiled_sql="SELECT * FROM new_table",
            success=True,
            duration_ms=10,
        )

        assert result.success is True
        assert result.has_changes is True
        assert result.applied_rules == []
        assert result.warnings == []

    def test_no_changes(self) -> None:
        """Test has_changes property when SQL is unchanged."""
        sql = "SELECT * FROM table"
        result = TranspileResult(
            original_sql=sql,
            transpiled_sql=sql,
            success=True,
            duration_ms=5,
        )

        assert result.has_changes is False

    def test_with_rules_and_warnings(self) -> None:
        """Test result with applied rules and warnings."""
        rule = TranspileRule(
            source_table="raw.events",
            target_table="warehouse.events_v2",
            priority=10,
            enabled=True,
        )
        warning = TranspileWarning(
            message="Deprecated table reference",
            line=5,
            column=10,
            rule="deprecated_table",
        )

        result = TranspileResult(
            original_sql="SELECT * FROM raw.events",
            transpiled_sql="SELECT * FROM warehouse.events_v2",
            success=True,
            applied_rules=[rule],
            warnings=[warning],
            duration_ms=15,
        )

        assert len(result.applied_rules) == 1
        assert result.applied_rules[0].source_table == "raw.events"
        assert len(result.warnings) == 1
        assert result.warnings[0].line == 5


class TestTranspileRule:
    """Tests for TranspileRule model."""

    def test_creation(self) -> None:
        """Test rule creation with defaults."""
        rule = TranspileRule(
            source_table="source",
            target_table="target",
        )

        assert rule.source_table == "source"
        assert rule.target_table == "target"
        assert rule.priority == 0
        assert rule.enabled is True

    def test_frozen(self) -> None:
        """Test that model is frozen."""
        rule = TranspileRule(source_table="a", target_table="b")

        with pytest.raises(ValidationError):
            rule.source_table = "c"  # type: ignore[misc]


class TestTranspileWarning:
    """Tests for TranspileWarning model."""

    def test_minimal_creation(self) -> None:
        """Test warning with just message."""
        warning = TranspileWarning(message="Test warning")

        assert warning.message == "Test warning"
        assert warning.line is None
        assert warning.column is None
        assert warning.rule is None

    def test_full_creation(self) -> None:
        """Test warning with all fields."""
        warning = TranspileWarning(
            message="Deprecated syntax",
            line=10,
            column=5,
            rule="no_select_star",
        )

        assert warning.line == 10
        assert warning.column == 5
        assert warning.rule == "no_select_star"


class TestEnvironmentInfo:
    """Tests for EnvironmentInfo model."""

    def test_creation(self) -> None:
        """Test basic creation."""
        env = EnvironmentInfo(name="prod", is_active=True)

        assert env.name == "prod"
        assert env.connection_string is None
        assert env.is_active is True

    def test_with_connection(self) -> None:
        """Test with connection string."""
        env = EnvironmentInfo(
            name="staging",
            connection_string="postgres://...",
            is_active=False,
        )

        assert env.connection_string == "postgres://..."


class TestConfigValue:
    """Tests for ConfigValue model."""

    def test_creation(self) -> None:
        """Test basic creation."""
        cv = ConfigValue(key="server.url", value="https://example.com")

        assert cv.key == "server.url"
        assert cv.value == "https://example.com"
        assert cv.source == "config"

    def test_with_source(self) -> None:
        """Test with explicit source."""
        cv = ConfigValue(
            key="dialect",
            value="trino",
            source="environment",
        )

        assert cv.source == "environment"

    def test_any_value_type(self) -> None:
        """Test that value can be any type."""
        # String
        cv1 = ConfigValue(key="str", value="text")
        assert cv1.value == "text"

        # Int
        cv2 = ConfigValue(key="int", value=42)
        assert cv2.value == 42

        # List
        cv3 = ConfigValue(key="list", value=[1, 2, 3])
        assert cv3.value == [1, 2, 3]

        # Dict
        cv4 = ConfigValue(key="dict", value={"nested": True})
        assert cv4.value == {"nested": True}
