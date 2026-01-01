"""Tests for FORMAT feature models (FormatStatus, FileFormatStatus, FileFormatResult, FormatResult).

These tests validate the data models defined in dli/models/format.py for the FORMAT feature.
"""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from dli.models.format import (
    FileFormatResult,
    FileFormatStatus,
    FormatResult,
    FormatStatus,
    LintViolation,
)


class TestFormatStatus:
    """Tests for FormatStatus enum."""

    def test_enum_values(self) -> None:
        """Test FormatStatus enum has expected values."""
        assert FormatStatus.SUCCESS.value == "success"
        assert FormatStatus.CHANGED.value == "changed"
        assert FormatStatus.FAILED.value == "failed"

    def test_all_values(self) -> None:
        """Test all FormatStatus values are present."""
        values = [e.value for e in FormatStatus]
        assert "success" in values
        assert "changed" in values
        assert "failed" in values
        assert len(values) == 3

    def test_string_conversion(self) -> None:
        """Test string representation of FormatStatus."""
        assert FormatStatus.SUCCESS.value == "success"
        assert FormatStatus.CHANGED.value == "changed"
        assert FormatStatus.FAILED.value == "failed"

    def test_from_string(self) -> None:
        """Test creating FormatStatus from string value."""
        assert FormatStatus("success") == FormatStatus.SUCCESS
        assert FormatStatus("changed") == FormatStatus.CHANGED
        assert FormatStatus("failed") == FormatStatus.FAILED

    def test_invalid_value(self) -> None:
        """Test invalid FormatStatus value raises error."""
        with pytest.raises(ValueError):
            FormatStatus("invalid")


class TestFileFormatStatus:
    """Tests for FileFormatStatus enum."""

    def test_enum_values(self) -> None:
        """Test FileFormatStatus enum has expected values."""
        assert FileFormatStatus.UNCHANGED.value == "unchanged"
        assert FileFormatStatus.CHANGED.value == "changed"
        assert FileFormatStatus.ERROR.value == "error"

    def test_all_values(self) -> None:
        """Test all FileFormatStatus values are present."""
        values = [e.value for e in FileFormatStatus]
        assert "unchanged" in values
        assert "changed" in values
        assert "error" in values
        assert len(values) == 3

    def test_from_string(self) -> None:
        """Test creating FileFormatStatus from string value."""
        assert FileFormatStatus("unchanged") == FileFormatStatus.UNCHANGED
        assert FileFormatStatus("changed") == FileFormatStatus.CHANGED
        assert FileFormatStatus("error") == FileFormatStatus.ERROR


class TestFileFormatResult:
    """Tests for FileFormatResult model."""

    def test_create_minimal(self) -> None:
        """Test creating FileFormatResult with required fields."""
        result = FileFormatResult(
            path="sql/daily_clicks.sql",
            status=FileFormatStatus.UNCHANGED,
        )

        assert result.path == "sql/daily_clicks.sql"
        assert result.status == FileFormatStatus.UNCHANGED
        assert result.changes == []  # default
        assert result.lint_violations == []  # default

    def test_create_with_changes(self) -> None:
        """Test creating FileFormatResult with diff changes."""
        changes = [
            "-SELECT a,b FROM t",
            "+SELECT",
            "+    a,",
            "+    b",
            "+FROM t",
        ]
        result = FileFormatResult(
            path="sql/my_query.sql",
            status=FileFormatStatus.CHANGED,
            changes=changes,
        )

        assert result.status == FileFormatStatus.CHANGED
        assert len(result.changes) == 5
        assert "+SELECT" in result.changes

    def test_create_with_lint_violations(self) -> None:
        """Test creating FileFormatResult with lint violations."""
        violations = [
            LintViolation(rule="L010", line=5, description="Keywords should be upper case"),
            LintViolation(rule="L031", line=12, description="Avoid aliases in FROM clauses"),
        ]
        result = FileFormatResult(
            path="sql/my_query.sql",
            status=FileFormatStatus.CHANGED,
            lint_violations=violations,
        )

        assert len(result.lint_violations) == 2
        assert result.lint_violations[0].rule == "L010"
        assert result.lint_violations[1].rule == "L031"

    def test_create_with_error(self) -> None:
        """Test creating FileFormatResult for error case."""
        result = FileFormatResult(
            path="sql/invalid.sql",
            status=FileFormatStatus.ERROR,
            changes=["Parse error: Unexpected token at line 5"],
        )

        assert result.status == FileFormatStatus.ERROR
        assert "Parse error" in result.changes[0]

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        original = FileFormatResult(
            path="sql/test.sql",
            status=FileFormatStatus.CHANGED,
            changes=["+SELECT 1"],
            lint_violations=[LintViolation(rule="L010", line=5, description="test")],
        )

        json_str = original.model_dump_json()
        restored = FileFormatResult.model_validate_json(json_str)

        assert restored.path == original.path
        assert restored.status == original.status
        assert restored.changes == original.changes
        assert len(restored.lint_violations) == len(original.lint_violations)
        assert restored.lint_violations[0].rule == original.lint_violations[0].rule


class TestFormatResult:
    """Tests for FormatResult model."""

    @pytest.fixture
    def sample_file_results(self) -> list[FileFormatResult]:
        """Create sample file results for testing."""
        return [
            FileFormatResult(
                path="dataset.iceberg.analytics.daily_clicks.yaml",
                status=FileFormatStatus.UNCHANGED,
            ),
            FileFormatResult(
                path="sql/daily_clicks.sql",
                status=FileFormatStatus.CHANGED,
                changes=["-select a", "+SELECT a"],
            ),
        ]

    def test_create_success(self, sample_file_results: list[FileFormatResult]) -> None:
        """Test creating FormatResult with SUCCESS status."""
        # No changes needed
        result = FormatResult(
            name="iceberg.analytics.daily_clicks",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=[
                FileFormatResult(
                    path="sql/test.sql",
                    status=FileFormatStatus.UNCHANGED,
                ),
            ],
        )

        assert result.name == "iceberg.analytics.daily_clicks"
        assert result.resource_type == "dataset"
        assert result.status == FormatStatus.SUCCESS
        assert result.message is None

    def test_create_changed(self, sample_file_results: list[FileFormatResult]) -> None:
        """Test creating FormatResult with CHANGED status."""
        result = FormatResult(
            name="iceberg.analytics.daily_clicks",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=sample_file_results,
            message="2 files would be changed",
        )

        assert result.status == FormatStatus.CHANGED
        assert result.message == "2 files would be changed"

    def test_create_failed(self) -> None:
        """Test creating FormatResult with FAILED status."""
        result = FormatResult(
            name="iceberg.analytics.invalid",
            resource_type="dataset",
            status=FormatStatus.FAILED,
            files=[
                FileFormatResult(
                    path="sql/invalid.sql",
                    status=FileFormatStatus.ERROR,
                    changes=["Parse error at line 1"],
                ),
            ],
            message="Formatting failed due to syntax error",
        )

        assert result.status == FormatStatus.FAILED
        assert result.message is not None
        assert "syntax error" in result.message.lower()

    def test_changed_count_property(self, sample_file_results: list[FileFormatResult]) -> None:
        """Test changed_count property returns correct count."""
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=sample_file_results,
        )

        # sample_file_results has 1 UNCHANGED and 1 CHANGED
        assert result.changed_count == 1

    def test_error_count_property(self) -> None:
        """Test error_count property returns correct count."""
        files = [
            FileFormatResult(path="sql/a.sql", status=FileFormatStatus.UNCHANGED),
            FileFormatResult(path="sql/b.sql", status=FileFormatStatus.ERROR),
            FileFormatResult(path="sql/c.sql", status=FileFormatStatus.CHANGED),
            FileFormatResult(path="sql/d.sql", status=FileFormatStatus.ERROR),
        ]
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.FAILED,
            files=files,
        )

        assert result.error_count == 2

    def test_changed_count_zero(self) -> None:
        """Test changed_count returns 0 when all files unchanged."""
        files = [
            FileFormatResult(path="sql/a.sql", status=FileFormatStatus.UNCHANGED),
            FileFormatResult(path="sql/b.sql", status=FileFormatStatus.UNCHANGED),
        ]
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=files,
        )

        assert result.changed_count == 0
        assert result.error_count == 0

    def test_resource_type_dataset(self) -> None:
        """Test FormatResult with dataset resource type."""
        result = FormatResult(
            name="my_dataset",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=[],
        )

        assert result.resource_type == "dataset"

    def test_resource_type_metric(self) -> None:
        """Test FormatResult with metric resource type."""
        result = FormatResult(
            name="my_metric",
            resource_type="metric",
            status=FormatStatus.SUCCESS,
            files=[],
        )

        assert result.resource_type == "metric"

    def test_json_roundtrip(self, sample_file_results: list[FileFormatResult]) -> None:
        """Test JSON serialization roundtrip."""
        original = FormatResult(
            name="iceberg.analytics.daily_clicks",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=sample_file_results,
            message="Files formatted",
        )

        json_str = original.model_dump_json()
        restored = FormatResult.model_validate_json(json_str)

        assert restored.name == original.name
        assert restored.resource_type == original.resource_type
        assert restored.status == original.status
        assert restored.message == original.message
        assert len(restored.files) == len(original.files)
        assert restored.changed_count == original.changed_count

    def test_model_has_expected_attributes(self, sample_file_results: list[FileFormatResult]) -> None:
        """Test that FormatResult has all expected attributes."""
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=sample_file_results,
        )

        # Verify model has expected attributes
        assert hasattr(result, "name")
        assert hasattr(result, "resource_type")
        assert hasattr(result, "status")
        assert hasattr(result, "files")
        assert hasattr(result, "message")
        assert hasattr(result, "check_mode")
        assert hasattr(result, "lint_enabled")

    def test_unchanged_count_property(self) -> None:
        """Test unchanged_count property returns correct count."""
        files = [
            FileFormatResult(path="sql/a.sql", status=FileFormatStatus.UNCHANGED),
            FileFormatResult(path="sql/b.sql", status=FileFormatStatus.UNCHANGED),
            FileFormatResult(path="sql/c.sql", status=FileFormatStatus.CHANGED),
        ]
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=files,
        )

        assert result.unchanged_count == 2

    def test_total_lint_violations_property(self) -> None:
        """Test total_lint_violations property returns correct count."""
        files = [
            FileFormatResult(
                path="sql/a.sql",
                status=FileFormatStatus.CHANGED,
                lint_violations=[
                    LintViolation(rule="L010", line=5, description="test"),
                    LintViolation(rule="L031", line=10, description="test"),
                ],
            ),
            FileFormatResult(
                path="sql/b.sql",
                status=FileFormatStatus.CHANGED,
                lint_violations=[
                    LintViolation(rule="L014", line=1, description="test"),
                ],
            ),
        ]
        result = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=files,
        )

        assert result.total_lint_violations == 3

    def test_has_changes_property(self) -> None:
        """Test has_changes property."""
        # With changes
        result_with_changes = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.CHANGED,
            files=[
                FileFormatResult(path="sql/a.sql", status=FileFormatStatus.CHANGED),
            ],
        )
        assert result_with_changes.has_changes is True

        # Without changes
        result_no_changes = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=[
                FileFormatResult(path="sql/a.sql", status=FileFormatStatus.UNCHANGED),
            ],
        )
        assert result_no_changes.has_changes is False

    def test_has_errors_property(self) -> None:
        """Test has_errors property."""
        # With errors
        result_with_errors = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.FAILED,
            files=[
                FileFormatResult(path="sql/a.sql", status=FileFormatStatus.ERROR),
            ],
        )
        assert result_with_errors.has_errors is True

        # Without errors
        result_no_errors = FormatResult(
            name="test",
            resource_type="dataset",
            status=FormatStatus.SUCCESS,
            files=[
                FileFormatResult(path="sql/a.sql", status=FileFormatStatus.UNCHANGED),
            ],
        )
        assert result_no_errors.has_errors is False


class TestFileFormatResultProperties:
    """Tests for FileFormatResult computed properties."""

    def test_has_changes_property(self) -> None:
        """Test has_changes property on FileFormatResult."""
        result_changed = FileFormatResult(
            path="sql/a.sql",
            status=FileFormatStatus.CHANGED,
        )
        assert result_changed.has_changes is True

        result_unchanged = FileFormatResult(
            path="sql/b.sql",
            status=FileFormatStatus.UNCHANGED,
        )
        assert result_unchanged.has_changes is False

    def test_has_errors_property(self) -> None:
        """Test has_errors property on FileFormatResult."""
        result_error = FileFormatResult(
            path="sql/a.sql",
            status=FileFormatStatus.ERROR,
        )
        assert result_error.has_errors is True

        result_ok = FileFormatResult(
            path="sql/b.sql",
            status=FileFormatStatus.UNCHANGED,
        )
        assert result_ok.has_errors is False

    def test_lint_violation_count_property(self) -> None:
        """Test lint_violation_count property on FileFormatResult."""
        result_with_violations = FileFormatResult(
            path="sql/a.sql",
            status=FileFormatStatus.CHANGED,
            lint_violations=[
                LintViolation(rule="L010", line=5, description="test 1"),
                LintViolation(rule="L031", line=10, description="test 2"),
                LintViolation(rule="L044", line=15, description="test 3"),
            ],
        )
        assert result_with_violations.lint_violation_count == 3

        result_no_violations = FileFormatResult(
            path="sql/b.sql",
            status=FileFormatStatus.UNCHANGED,
        )
        assert result_no_violations.lint_violation_count == 0


class TestLintViolation:
    """Tests for LintViolation model."""

    def test_create_with_required_fields(self) -> None:
        """Test creating LintViolation with required fields."""
        violation = LintViolation(
            rule="L010",
            line=5,
            description="Keywords should be upper case",
        )

        assert violation.rule == "L010"
        assert violation.line == 5
        assert violation.description == "Keywords should be upper case"
        assert violation.column == 1  # default
        assert violation.severity == "warning"  # default

    def test_create_with_all_fields(self) -> None:
        """Test creating LintViolation with all fields."""
        violation = LintViolation(
            rule="L031",
            line=12,
            column=4,
            description="Avoid aliases in FROM clauses",
            severity="error",
        )

        assert violation.rule == "L031"
        assert violation.line == 12
        assert violation.column == 4
        assert violation.description == "Avoid aliases in FROM clauses"
        assert violation.severity == "error"

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip for LintViolation."""
        original = LintViolation(
            rule="L044",
            line=18,
            column=10,
            description="Query produces more than 10 columns",
            severity="info",
        )

        json_str = original.model_dump_json()
        restored = LintViolation.model_validate_json(json_str)

        assert restored.rule == original.rule
        assert restored.line == original.line
        assert restored.column == original.column
        assert restored.description == original.description
        assert restored.severity == original.severity
