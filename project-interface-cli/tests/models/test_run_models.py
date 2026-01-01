"""Tests for public run models (OutputFormat, RunResult, ExecutionPlan)."""

from pathlib import Path

import pytest
from pydantic import ValidationError

from dli.models.common import ExecutionMode, ResultStatus
from dli.models.run import ExecutionPlan, OutputFormat, RunResult


class TestOutputFormat:
    """Tests for OutputFormat enum."""

    def test_enum_values(self) -> None:
        """Test OutputFormat enum has expected values."""
        assert OutputFormat.CSV.value == "csv"
        assert OutputFormat.TSV.value == "tsv"
        assert OutputFormat.JSON.value == "json"

    def test_all_values(self) -> None:
        """Test all OutputFormat values are present."""
        values = [e.value for e in OutputFormat]
        assert "csv" in values
        assert "tsv" in values
        assert "json" in values
        assert len(values) == 3

    def test_string_conversion(self) -> None:
        """Test string representation of OutputFormat."""
        # OutputFormat inherits from str, so .value gives the string
        assert OutputFormat.CSV.value == "csv"
        assert OutputFormat.TSV.value == "tsv"
        assert OutputFormat.JSON.value == "json"

    def test_from_string(self) -> None:
        """Test creating OutputFormat from string value."""
        assert OutputFormat("csv") == OutputFormat.CSV
        assert OutputFormat("tsv") == OutputFormat.TSV
        assert OutputFormat("json") == OutputFormat.JSON

    def test_invalid_value(self) -> None:
        """Test invalid OutputFormat value raises error."""
        with pytest.raises(ValueError):
            OutputFormat("invalid")


class TestRunResult:
    """Tests for RunResult model."""

    @pytest.fixture
    def sample_result(self, tmp_path: Path) -> RunResult:
        """Create a sample RunResult for testing."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"
        output_file.write_text("id,name\n1,test")

        return RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            row_count=100,
            duration_seconds=1.5,
            execution_mode=ExecutionMode.SERVER,
            rendered_sql="SELECT 1",
        )

    def test_create_minimal(self, tmp_path: Path) -> None:
        """Test creating RunResult with required fields."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        result = RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            row_count=50,
            duration_seconds=0.5,
            execution_mode=ExecutionMode.MOCK,
            rendered_sql="SELECT * FROM users",
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.sql_path == sql_file
        assert result.output_path == output_file
        assert result.output_format == OutputFormat.CSV
        assert result.row_count == 50
        assert result.duration_seconds == 0.5
        assert result.execution_mode == ExecutionMode.MOCK
        assert result.rendered_sql == "SELECT * FROM users"
        assert result.bytes_processed is None
        assert result.bytes_billed is None

    def test_create_with_all_fields(self, tmp_path: Path) -> None:
        """Test creating RunResult with all fields specified."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.json"

        result = RunResult(
            status=ResultStatus.SUCCESS,
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.JSON,
            row_count=1000,
            duration_seconds=5.5,
            execution_mode=ExecutionMode.LOCAL,
            rendered_sql="SELECT * FROM users WHERE id > 0",
            bytes_processed=1024000,
            bytes_billed=512000,
        )

        assert result.bytes_processed == 1024000
        assert result.bytes_billed == 512000

    def test_is_success_property_true(self, sample_result: RunResult) -> None:
        """Test is_success property returns True for SUCCESS status."""
        assert sample_result.is_success is True

    def test_is_success_property_false(self, tmp_path: Path) -> None:
        """Test is_success property returns False for FAILURE status."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        result = RunResult(
            status=ResultStatus.FAILURE,
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            row_count=0,
            duration_seconds=0.0,
            execution_mode=ExecutionMode.SERVER,
            rendered_sql="SELECT 1",
        )

        assert result.is_success is False

    def test_frozen_model(self, sample_result: RunResult) -> None:
        """Test that RunResult is frozen (immutable)."""
        with pytest.raises(ValidationError):
            sample_result.row_count = 200  # type: ignore[misc]

    def test_json_roundtrip(self, sample_result: RunResult) -> None:
        """Test JSON serialization roundtrip."""
        json_str = sample_result.model_dump_json()
        restored = RunResult.model_validate_json(json_str)

        assert str(restored.sql_path) == str(sample_result.sql_path)
        assert str(restored.output_path) == str(sample_result.output_path)
        assert restored.status == sample_result.status
        assert restored.row_count == sample_result.row_count
        assert restored.duration_seconds == sample_result.duration_seconds
        assert restored.execution_mode == sample_result.execution_mode
        assert restored.rendered_sql == sample_result.rendered_sql

    def test_different_output_formats(self, tmp_path: Path) -> None:
        """Test RunResult with different output formats."""
        sql_file = tmp_path / "query.sql"

        for format_type in OutputFormat:
            output_file = tmp_path / f"output.{format_type.value}"
            result = RunResult(
                status=ResultStatus.SUCCESS,
                sql_path=sql_file,
                output_path=output_file,
                output_format=format_type,
                row_count=10,
                duration_seconds=0.1,
                execution_mode=ExecutionMode.MOCK,
                rendered_sql="SELECT 1",
            )
            assert result.output_format == format_type


class TestExecutionPlan:
    """Tests for ExecutionPlan model."""

    @pytest.fixture
    def sample_plan(self, tmp_path: Path) -> ExecutionPlan:
        """Create a sample ExecutionPlan for testing."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{ table }}")
        output_file = tmp_path / "output.csv"

        return ExecutionPlan(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            dialect="bigquery",
            execution_mode=ExecutionMode.SERVER,
            rendered_sql="SELECT * FROM users",
            parameters={"table": "users"},
            is_valid=True,
        )

    def test_create_minimal(self, tmp_path: Path) -> None:
        """Test creating ExecutionPlan with required fields."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        plan = ExecutionPlan(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            dialect="bigquery",
            execution_mode=ExecutionMode.SERVER,
            rendered_sql="SELECT 1",
            is_valid=True,
        )

        assert plan.sql_path == sql_file
        assert plan.output_path == output_file
        assert plan.output_format == OutputFormat.CSV
        assert plan.dialect == "bigquery"
        assert plan.execution_mode == ExecutionMode.SERVER
        assert plan.rendered_sql == "SELECT 1"
        assert plan.parameters == {}  # default
        assert plan.is_valid is True
        assert plan.validation_error is None  # default

    def test_create_with_validation_error(self, tmp_path: Path) -> None:
        """Test creating ExecutionPlan with validation error."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        plan = ExecutionPlan(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.CSV,
            dialect="bigquery",
            execution_mode=ExecutionMode.SERVER,
            rendered_sql="INVALID SQL",
            is_valid=False,
            validation_error="Local execution not permitted by server policy",
        )

        assert plan.is_valid is False
        assert plan.validation_error == "Local execution not permitted by server policy"

    def test_create_with_parameters(self, sample_plan: ExecutionPlan) -> None:
        """Test ExecutionPlan with parameters."""
        assert sample_plan.parameters == {"table": "users"}
        assert "users" in sample_plan.rendered_sql

    def test_frozen_model(self, sample_plan: ExecutionPlan) -> None:
        """Test that ExecutionPlan is frozen (immutable)."""
        with pytest.raises(ValidationError):
            sample_plan.is_valid = False  # type: ignore[misc]

    def test_json_roundtrip(self, sample_plan: ExecutionPlan) -> None:
        """Test JSON serialization roundtrip."""
        json_str = sample_plan.model_dump_json()
        restored = ExecutionPlan.model_validate_json(json_str)

        assert str(restored.sql_path) == str(sample_plan.sql_path)
        assert str(restored.output_path) == str(sample_plan.output_path)
        assert restored.output_format == sample_plan.output_format
        assert restored.dialect == sample_plan.dialect
        assert restored.execution_mode == sample_plan.execution_mode
        assert restored.rendered_sql == sample_plan.rendered_sql
        assert restored.parameters == sample_plan.parameters
        assert restored.is_valid == sample_plan.is_valid

    def test_different_dialects(self, tmp_path: Path) -> None:
        """Test ExecutionPlan with different SQL dialects."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        for dialect in ["bigquery", "trino"]:
            plan = ExecutionPlan(
                sql_path=sql_file,
                output_path=output_file,
                output_format=OutputFormat.CSV,
                dialect=dialect,
                execution_mode=ExecutionMode.SERVER,
                rendered_sql="SELECT 1",
                is_valid=True,
            )
            assert plan.dialect == dialect

    def test_different_execution_modes(self, tmp_path: Path) -> None:
        """Test ExecutionPlan with different execution modes."""
        sql_file = tmp_path / "query.sql"
        output_file = tmp_path / "output.csv"

        for mode in [ExecutionMode.LOCAL, ExecutionMode.SERVER, ExecutionMode.MOCK]:
            plan = ExecutionPlan(
                sql_path=sql_file,
                output_path=output_file,
                output_format=OutputFormat.CSV,
                dialect="bigquery",
                execution_mode=mode,
                rendered_sql="SELECT 1",
                is_valid=True,
            )
            assert plan.execution_mode == mode
