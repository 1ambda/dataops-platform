"""Tests for run core models (RunConfig, ExecutionData)."""

from pathlib import Path
from typing import Any

import pytest
from pydantic import ValidationError

from dli.core.run.models import ExecutionData, RunConfig


class TestRunConfig:
    """Tests for RunConfig model."""

    def test_create_minimal(self, tmp_path: Path) -> None:
        """Test creating RunConfig with minimal required fields."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        config = RunConfig(sql_path=sql_file, output_path=output_file)

        assert config.sql_path == sql_file
        assert config.output_path == output_file
        assert config.output_format == "csv"  # default
        assert config.parameters == {}  # default
        assert config.limit is None  # default
        assert config.timeout == 300  # default
        assert config.dialect == "bigquery"  # default

    def test_create_with_all_fields(self, tmp_path: Path) -> None:
        """Test creating RunConfig with all fields specified."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.json"

        config = RunConfig(
            sql_path=sql_file,
            output_path=output_file,
            output_format="json",
            parameters={"date": "2026-01-01"},
            limit=1000,
            timeout=600,
            dialect="trino",
        )

        assert config.sql_path == sql_file
        assert config.output_path == output_file
        assert config.output_format == "json"
        assert config.parameters == {"date": "2026-01-01"}
        assert config.limit == 1000
        assert config.timeout == 600
        assert config.dialect == "trino"

    def test_frozen_model(self, tmp_path: Path) -> None:
        """Test that RunConfig is frozen (immutable)."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        config = RunConfig(sql_path=sql_file, output_path=output_file)

        with pytest.raises(ValidationError):
            config.timeout = 600  # type: ignore[misc]

    def test_missing_required_fields(self) -> None:
        """Test that missing required fields raises ValidationError."""
        with pytest.raises(ValidationError) as exc_info:
            RunConfig()  # type: ignore[call-arg]

        errors = exc_info.value.errors()
        # Should have errors for sql_path and output_path
        field_names = [e["loc"][0] for e in errors]
        assert "sql_path" in field_names
        assert "output_path" in field_names

    def test_json_roundtrip(self, tmp_path: Path) -> None:
        """Test JSON serialization roundtrip."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        original = RunConfig(
            sql_path=sql_file,
            output_path=output_file,
            parameters={"date": "2026-01-01"},
            limit=100,
        )

        json_str = original.model_dump_json()
        restored = RunConfig.model_validate_json(json_str)

        # Paths will be strings after JSON roundtrip
        assert str(restored.sql_path) == str(original.sql_path)
        assert str(restored.output_path) == str(original.output_path)
        assert restored.parameters == original.parameters
        assert restored.limit == original.limit


class TestExecutionData:
    """Tests for ExecutionData model."""

    def test_create_minimal(self) -> None:
        """Test creating ExecutionData with minimal required fields."""
        data = ExecutionData(row_count=10, duration_seconds=1.5)

        assert data.rows == []  # default
        assert data.row_count == 10
        assert data.duration_seconds == 1.5
        assert data.bytes_processed is None  # default
        assert data.bytes_billed is None  # default

    def test_create_with_rows(self) -> None:
        """Test creating ExecutionData with result rows."""
        rows: list[dict[str, Any]] = [
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
        ]

        data = ExecutionData(
            rows=rows,
            row_count=2,
            duration_seconds=0.5,
        )

        assert len(data.rows) == 2
        assert data.rows[0]["name"] == "Alice"
        assert data.row_count == 2

    def test_create_with_all_fields(self) -> None:
        """Test creating ExecutionData with all fields specified."""
        rows: list[dict[str, Any]] = [{"value": 100}]

        data = ExecutionData(
            rows=rows,
            row_count=1,
            duration_seconds=2.5,
            bytes_processed=1024,
            bytes_billed=512,
        )

        assert data.rows == rows
        assert data.row_count == 1
        assert data.duration_seconds == 2.5
        assert data.bytes_processed == 1024
        assert data.bytes_billed == 512

    def test_frozen_model(self) -> None:
        """Test that ExecutionData is frozen (immutable)."""
        data = ExecutionData(row_count=10, duration_seconds=1.0)

        with pytest.raises(ValidationError):
            data.row_count = 20  # type: ignore[misc]

    def test_missing_required_fields(self) -> None:
        """Test that missing required fields raises ValidationError."""
        with pytest.raises(ValidationError) as exc_info:
            ExecutionData()  # type: ignore[call-arg]

        errors = exc_info.value.errors()
        field_names = [e["loc"][0] for e in errors]
        assert "row_count" in field_names
        assert "duration_seconds" in field_names

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        rows: list[dict[str, Any]] = [{"id": 1}, {"id": 2}]
        original = ExecutionData(
            rows=rows,
            row_count=2,
            duration_seconds=0.75,
            bytes_processed=2048,
        )

        json_str = original.model_dump_json()
        restored = ExecutionData.model_validate_json(json_str)

        assert restored.rows == original.rows
        assert restored.row_count == original.row_count
        assert restored.duration_seconds == original.duration_seconds
        assert restored.bytes_processed == original.bytes_processed

    def test_empty_rows_with_zero_count(self) -> None:
        """Test ExecutionData with empty results."""
        data = ExecutionData(rows=[], row_count=0, duration_seconds=0.1)

        assert data.rows == []
        assert data.row_count == 0
        assert data.duration_seconds == 0.1
