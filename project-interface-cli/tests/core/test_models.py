"""Tests for the DLI Core Engine models."""

from datetime import date
from pathlib import Path

import pytest

from dli.core.models import (
    DatasetExecutionResult,
    DatasetSpec,
    DatasetVersion,
    ExecutionConfig,
    ExecutionResult,
    ParameterType,
    QueryParameter,
    QueryType,
    StatementDefinition,
    ValidationResult,
)


class TestParameterType:
    """Tests for ParameterType enum."""

    def test_enum_values(self):
        """Test all enum values exist."""
        assert ParameterType.STRING.value == "string"
        assert ParameterType.INTEGER.value == "integer"
        assert ParameterType.FLOAT.value == "float"
        assert ParameterType.DATE.value == "date"
        assert ParameterType.BOOLEAN.value == "boolean"
        assert ParameterType.LIST.value == "list"


class TestQueryParameter:
    """Tests for QueryParameter model."""

    def test_validate_required_missing(self):
        """Test validation error for missing required parameter."""
        param = QueryParameter(name="dt", type=ParameterType.DATE, required=True)
        with pytest.raises(ValueError, match="Required parameter"):
            param.validate_value(None)

    def test_validate_with_default(self):
        """Test default value is used when value is None."""
        param = QueryParameter(
            name="days", type=ParameterType.INTEGER, required=False, default=7
        )
        assert param.validate_value(None) == 7

    def test_validate_date_conversion(self):
        """Test date string conversion."""
        param = QueryParameter(name="dt", type=ParameterType.DATE, required=True)
        result = param.validate_value("2025-01-01")
        assert result == date(2025, 1, 1)

    def test_validate_date_passthrough(self):
        """Test date object passthrough."""
        param = QueryParameter(name="dt", type=ParameterType.DATE, required=True)
        dt = date(2025, 1, 1)
        result = param.validate_value(dt)
        assert result == dt

    def test_validate_integer_conversion(self):
        """Test integer conversion."""
        param = QueryParameter(name="count", type=ParameterType.INTEGER)
        assert param.validate_value("42") == 42
        assert param.validate_value(42) == 42

    def test_validate_float_conversion(self):
        """Test float conversion."""
        param = QueryParameter(name="ratio", type=ParameterType.FLOAT)
        assert param.validate_value("3.14") == 3.14
        assert param.validate_value(3.14) == 3.14

    def test_validate_boolean_conversion(self):
        """Test boolean conversion."""
        param = QueryParameter(name="flag", type=ParameterType.BOOLEAN)
        assert param.validate_value("true") is True
        assert param.validate_value("1") is True
        assert param.validate_value("yes") is True
        assert param.validate_value("false") is False
        assert param.validate_value("0") is False

    def test_validate_string_conversion(self):
        """Test string conversion."""
        param = QueryParameter(name="name", type=ParameterType.STRING)
        assert param.validate_value(123) == "123"
        assert param.validate_value("test") == "test"

    def test_validate_list_conversion(self):
        """Test list conversion."""
        param = QueryParameter(name="items", type=ParameterType.LIST)
        assert param.validate_value([1, 2, 3]) == [1, 2, 3]
        assert param.validate_value("single") == ["single"]

    def test_validate_conversion_error(self):
        """Test conversion error handling."""
        param = QueryParameter(name="count", type=ParameterType.INTEGER)
        with pytest.raises(ValueError, match="Failed to convert"):
            param.validate_value("not_a_number")


class TestStatementDefinition:
    """Tests for StatementDefinition model."""

    def test_get_sql_inline(self):
        """Test getting inline SQL."""
        stmt = StatementDefinition(name="test", sql="SELECT 1")
        assert stmt.get_sql(Path(".")) == "SELECT 1"

    def test_get_sql_file(self, tmp_path):
        """Test getting SQL from file."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT 2")
        stmt = StatementDefinition(name="test", file="test.sql")
        assert stmt.get_sql(tmp_path) == "SELECT 2"

    def test_get_sql_no_source(self):
        """Test error when no SQL source is provided."""
        stmt = StatementDefinition(name="test")
        with pytest.raises(ValueError, match="has no sql or file"):
            stmt.get_sql(Path("."))

    def test_continue_on_error_default(self):
        """Test default continue_on_error is False."""
        stmt = StatementDefinition(name="test", sql="SELECT 1")
        assert stmt.continue_on_error is False

    def test_continue_on_error_true(self):
        """Test continue_on_error can be set to True."""
        stmt = StatementDefinition(name="test", sql="SELECT 1", continue_on_error=True)
        assert stmt.continue_on_error is True


class TestDatasetVersion:
    """Tests for DatasetVersion model."""

    def test_active_version(self):
        """Test version with no end date is active."""
        v = DatasetVersion(version="v2", started_at=date(2022, 6, 1), ended_at=None)
        assert v.is_active is True

    def test_inactive_version(self):
        """Test version with end date is inactive."""
        v = DatasetVersion(
            version="v1", started_at=date(2015, 1, 1), ended_at=date(2022, 5, 31)
        )
        assert v.is_active is False


class TestExecutionConfig:
    """Tests for ExecutionConfig model."""

    def test_default_values(self):
        """Test default configuration values."""
        config = ExecutionConfig()
        assert config.timeout_seconds == 3600
        assert config.retry_count == 2
        assert config.retry_delay_seconds == 60
        assert config.dialect == "trino"

    def test_custom_values(self):
        """Test custom configuration values."""
        config = ExecutionConfig(
            timeout_seconds=1800,
            retry_count=3,
            retry_delay_seconds=30,
            dialect="bigquery",
        )
        assert config.timeout_seconds == 1800
        assert config.retry_count == 3
        assert config.retry_delay_seconds == 30
        assert config.dialect == "bigquery"


class TestDatasetSpec:
    """Tests for DatasetSpec model."""

    def test_parse_name(self):
        """Test parsing fully qualified name."""
        spec = DatasetSpec(
            name="iceberg.analytics.daily_clicks",
            owner="henry@example.com",
            team="@analytics",
            query_type=QueryType.DML,
            query_statement="SELECT 1",
        )
        assert spec.catalog == "iceberg"
        assert spec.schema_name == "analytics"
        assert spec.table == "daily_clicks"

    def test_parse_name_partial(self):
        """Test parsing partial name."""
        spec = DatasetSpec(
            name="catalog.schema",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.SELECT,
            query_statement="SELECT 1",
        )
        assert spec.catalog == "catalog"
        assert spec.schema_name == "schema"
        assert spec.table == ""

    def test_active_version(self):
        """Test getting active version."""
        spec = DatasetSpec(
            name="iceberg.analytics.test",
            owner="henry@example.com",
            team="@analytics",
            query_type=QueryType.SELECT,
            query_statement="SELECT 1",
            versions=[
                DatasetVersion(
                    version="v1",
                    started_at=date(2020, 1, 1),
                    ended_at=date(2022, 12, 31),
                ),
                DatasetVersion(version="v2", started_at=date(2023, 1, 1), ended_at=None),
            ],
        )
        assert spec.active_version is not None
        assert spec.active_version.version == "v2"

    def test_no_active_version(self):
        """Test when no active version exists."""
        spec = DatasetSpec(
            name="iceberg.analytics.test",
            owner="henry@example.com",
            team="@analytics",
            query_type=QueryType.SELECT,
            query_statement="SELECT 1",
            versions=[
                DatasetVersion(
                    version="v1",
                    started_at=date(2020, 1, 1),
                    ended_at=date(2022, 12, 31),
                ),
            ],
        )
        assert spec.active_version is None

    def test_get_main_sql_inline(self):
        """Test getting main SQL from inline statement."""
        spec = DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.SELECT,
            query_statement="SELECT 1",
        )
        assert spec.get_main_sql() == "SELECT 1"

    def test_get_main_sql_file(self, tmp_path):
        """Test getting main SQL from file."""
        sql_file = tmp_path / "main.sql"
        sql_file.write_text("SELECT 2")

        spec = DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.SELECT,
            query_file="main.sql",
        )
        object.__setattr__(spec, "_base_dir", tmp_path)
        assert spec.get_main_sql() == "SELECT 2"

    def test_get_main_sql_no_source(self):
        """Test error when no SQL source is provided."""
        spec = DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.SELECT,
        )
        with pytest.raises(ValueError, match="has no query_statement or query_file"):
            spec.get_main_sql()


class TestValidationResult:
    """Tests for ValidationResult model."""

    def test_valid_result(self):
        """Test valid result creation."""
        result = ValidationResult(
            is_valid=True, rendered_sql="SELECT 1", phase="main"
        )
        assert result.is_valid is True
        assert result.errors == []
        assert result.warnings == []
        assert result.rendered_sql == "SELECT 1"
        assert result.phase == "main"

    def test_invalid_result(self):
        """Test invalid result creation."""
        result = ValidationResult(
            is_valid=False,
            errors=["Syntax error"],
            phase="pre",
        )
        assert result.is_valid is False
        assert "Syntax error" in result.errors
        assert result.phase == "pre"


class TestExecutionResult:
    """Tests for ExecutionResult model."""

    def test_successful_result(self):
        """Test successful execution result."""
        result = ExecutionResult(
            dataset_name="test.test.test",
            phase="main",
            success=True,
            row_count=10,
            columns=["id", "name"],
            data=[{"id": 1, "name": "test"}],
            rendered_sql="SELECT 1",
            execution_time_ms=100,
        )
        assert result.success is True
        assert result.row_count == 10
        assert result.error_message is None

    def test_failed_result(self):
        """Test failed execution result."""
        result = ExecutionResult(
            dataset_name="test.test.test",
            phase="pre",
            statement_name="delete_partition",
            success=False,
            error_message="Connection failed",
            rendered_sql="DELETE FROM t",
            execution_time_ms=50,
        )
        assert result.success is False
        assert result.error_message == "Connection failed"
        assert result.statement_name == "delete_partition"


class TestDatasetExecutionResult:
    """Tests for DatasetExecutionResult model."""

    def test_successful_result(self):
        """Test successful dataset execution result."""
        result = DatasetExecutionResult(
            dataset_name="test.test.test",
            success=True,
            pre_results=[
                ExecutionResult(
                    dataset_name="test.test.test",
                    phase="pre",
                    success=True,
                    rendered_sql="DELETE FROM t",
                )
            ],
            main_result=ExecutionResult(
                dataset_name="test.test.test",
                phase="main",
                success=True,
                rendered_sql="INSERT INTO t SELECT 1",
            ),
            post_results=[],
            total_execution_time_ms=1000,
        )
        assert result.success is True
        assert len(result.pre_results) == 1
        assert result.main_result is not None
        assert len(result.post_results) == 0

    def test_failed_result(self):
        """Test failed dataset execution result."""
        result = DatasetExecutionResult(
            dataset_name="test.test.test",
            success=False,
            error_message="Pre statement failed",
        )
        assert result.success is False
        assert result.error_message == "Pre statement failed"
