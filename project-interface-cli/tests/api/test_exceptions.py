"""Tests for dli.exceptions module.

Covers:
- ErrorCode: Error code enum values and categories
- DLIError: Base exception
- ConfigurationError: Config-related errors
- DLIValidationError: Validation failures
- ExecutionError: Execution failures with cause chaining
- DatasetNotFoundError, MetricNotFoundError: Not found errors
- TableNotFoundError, WorkflowNotFoundError: Additional not found errors
- TranspileError: SQL transpilation errors
- ServerError: Server communication errors
"""

from __future__ import annotations

from pathlib import Path

import pytest

from dli.exceptions import (
    ConfigurationError,
    DatasetNotFoundError,
    DLIError,
    DLIValidationError,
    ErrorCode,
    ExecutionError,
    MetricNotFoundError,
    ServerError,
    TableNotFoundError,
    TranspileError,
    WorkflowNotFoundError,
)


class TestErrorCode:
    """Tests for ErrorCode enum."""

    def test_config_error_codes(self) -> None:
        """Test configuration error codes (DLI-0xx)."""
        assert ErrorCode.CONFIG_INVALID.value == "DLI-001"
        assert ErrorCode.CONFIG_NOT_FOUND.value == "DLI-002"
        assert ErrorCode.PROJECT_NOT_FOUND.value == "DLI-003"

    def test_not_found_error_codes(self) -> None:
        """Test not found error codes (DLI-1xx)."""
        assert ErrorCode.DATASET_NOT_FOUND.value == "DLI-101"
        assert ErrorCode.METRIC_NOT_FOUND.value == "DLI-102"
        assert ErrorCode.TABLE_NOT_FOUND.value == "DLI-103"
        assert ErrorCode.WORKFLOW_NOT_FOUND.value == "DLI-104"

    def test_validation_error_codes(self) -> None:
        """Test validation error codes (DLI-2xx)."""
        assert ErrorCode.VALIDATION_FAILED.value == "DLI-201"
        assert ErrorCode.SQL_SYNTAX_ERROR.value == "DLI-202"
        assert ErrorCode.SPEC_INVALID.value == "DLI-203"
        assert ErrorCode.PARAMETER_INVALID.value == "DLI-204"

    def test_transpile_error_codes(self) -> None:
        """Test transpile error codes (DLI-3xx)."""
        assert ErrorCode.TRANSPILE_FAILED.value == "DLI-301"
        assert ErrorCode.DIALECT_UNSUPPORTED.value == "DLI-302"
        assert ErrorCode.RULE_CONFLICT.value == "DLI-303"

    def test_execution_error_codes(self) -> None:
        """Test execution error codes (DLI-4xx)."""
        assert ErrorCode.EXECUTION_FAILED.value == "DLI-401"
        assert ErrorCode.TIMEOUT.value == "DLI-402"
        assert ErrorCode.CONNECTION_FAILED.value == "DLI-403"

    def test_server_error_codes(self) -> None:
        """Test server error codes (DLI-5xx)."""
        assert ErrorCode.SERVER_UNREACHABLE.value == "DLI-501"
        assert ErrorCode.SERVER_AUTH_FAILED.value == "DLI-502"
        assert ErrorCode.SERVER_ERROR.value == "DLI-503"

    def test_enum_is_string(self) -> None:
        """Test that ErrorCode inherits from str.

        Note: In Python 3.11+, str(Enum) returns "Enum.VALUE" even for
        (str, Enum) subclasses. Use .value or == comparison instead.
        """
        code = ErrorCode.EXECUTION_FAILED

        # Can be compared to string directly (works for str subclass)
        assert code == "DLI-401"
        assert code.value == "DLI-401"


class TestDLIError:
    """Tests for DLIError base exception."""

    def test_basic_creation(self) -> None:
        """Test creating basic DLI error."""
        error = DLIError(message="Something went wrong")

        assert error.message == "Something went wrong"
        assert error.code == ErrorCode.EXECUTION_FAILED  # default
        assert error.details == {}

    def test_with_code(self) -> None:
        """Test creating error with specific code."""
        error = DLIError(
            message="Config issue",
            code=ErrorCode.CONFIG_INVALID,
        )

        assert error.code == ErrorCode.CONFIG_INVALID

    def test_with_details(self) -> None:
        """Test creating error with details dict."""
        error = DLIError(
            message="Error occurred",
            details={"key": "value", "count": 5},
        )

        assert error.details == {"key": "value", "count": 5}

    def test_str_format(self) -> None:
        """Test __str__ returns formatted message with code."""
        error = DLIError(
            message="Test error message",
            code=ErrorCode.EXECUTION_FAILED,
        )

        result = str(error)

        assert result == "[DLI-401] Test error message"

    def test_repr_format(self) -> None:
        """Test __repr__ returns detailed representation."""
        error = DLIError(message="Test", code=ErrorCode.TIMEOUT)

        result = repr(error)

        assert "DLIError" in result
        assert "message='Test'" in result
        assert "ErrorCode.TIMEOUT" in result

    def test_is_exception(self) -> None:
        """Test that DLIError is a proper Exception."""
        error = DLIError(message="Test")

        assert isinstance(error, Exception)

        # Can be raised and caught
        with pytest.raises(DLIError) as exc_info:
            raise error

        assert exc_info.value.message == "Test"

    def test_exception_hierarchy(self) -> None:
        """Test that DLIError can be caught as Exception."""
        error = DLIError(message="Test")

        with pytest.raises(DLIError):
            raise error


class TestConfigurationError:
    """Tests for ConfigurationError."""

    def test_basic_creation(self) -> None:
        """Test creating configuration error."""
        error = ConfigurationError(message="Invalid config")

        assert error.code == ErrorCode.CONFIG_INVALID
        assert error.config_path is None

    def test_with_path(self) -> None:
        """Test creating with config path."""
        path = Path("/etc/dli/config.yaml")
        error = ConfigurationError(
            message="Config file not found",
            code=ErrorCode.CONFIG_NOT_FOUND,
            config_path=path,
        )

        assert error.config_path == path
        assert error.code == ErrorCode.CONFIG_NOT_FOUND

    def test_str_with_path(self) -> None:
        """Test __str__ includes path when present."""
        error = ConfigurationError(
            message="Invalid format",
            config_path=Path("/path/to/config.yaml"),
        )

        result = str(error)

        assert "[DLI-001]" in result
        assert "Invalid format" in result
        assert "/path/to/config.yaml" in result

    def test_str_without_path(self) -> None:
        """Test __str__ without path."""
        error = ConfigurationError(message="Config error")

        result = str(error)

        assert result == "[DLI-001] Config error"

    def test_inheritance(self) -> None:
        """Test ConfigurationError inherits from DLIError."""
        error = ConfigurationError(message="Test")

        assert isinstance(error, DLIError)
        assert isinstance(error, Exception)


class TestDLIValidationError:
    """Tests for DLIValidationError."""

    def test_basic_creation(self) -> None:
        """Test creating validation error."""
        error = DLIValidationError(message="Validation failed")

        assert error.code == ErrorCode.VALIDATION_FAILED
        assert error.errors == []
        assert error.warnings == []

    def test_with_errors_and_warnings(self) -> None:
        """Test creating with error and warning lists."""
        error = DLIValidationError(
            message="Spec invalid",
            errors=["Missing field: name", "Invalid type: owner"],
            warnings=["Deprecated field: tag"],
        )

        assert len(error.errors) == 2
        assert len(error.warnings) == 1

    def test_str_with_counts(self) -> None:
        """Test __str__ includes error/warning counts."""
        error = DLIValidationError(
            message="Validation failed",
            errors=["Error 1", "Error 2"],
            warnings=["Warning 1"],
        )

        result = str(error)

        assert "[DLI-201]" in result
        assert "Validation failed" in result
        assert "2 errors" in result
        assert "1 warnings" in result

    def test_str_without_counts(self) -> None:
        """Test __str__ without error/warning counts when empty."""
        error = DLIValidationError(message="No issues")

        result = str(error)

        assert result == "[DLI-201] No issues"


class TestExecutionError:
    """Tests for ExecutionError."""

    def test_basic_creation(self) -> None:
        """Test creating execution error."""
        error = ExecutionError(message="Execution failed")

        assert error.code == ErrorCode.EXECUTION_FAILED
        assert error.cause is None

    def test_with_cause(self) -> None:
        """Test creating with cause exception."""
        original = ValueError("Original error")
        error = ExecutionError(
            message="Wrapped error",
            cause=original,
        )

        assert error.cause is original
        assert error.__cause__ is original  # Python exception chaining

    def test_cause_chaining(self) -> None:
        """Test that cause is properly chained."""
        original = RuntimeError("Root cause")
        error = ExecutionError(message="Wrapper", cause=original)

        with pytest.raises(ExecutionError) as exc_info:
            raise error

        assert exc_info.value.__cause__ is original


class TestDatasetNotFoundError:
    """Tests for DatasetNotFoundError."""

    def test_basic_creation(self) -> None:
        """Test creating dataset not found error."""
        error = DatasetNotFoundError(message="Not found")

        assert error.code == ErrorCode.DATASET_NOT_FOUND
        assert error.name == ""
        assert error.searched_paths == []

    def test_with_name_and_paths(self) -> None:
        """Test creating with name and searched paths."""
        error = DatasetNotFoundError(
            message="Dataset not found",
            name="catalog.schema.dataset",
            searched_paths=[Path("/path1"), Path("/path2")],
        )

        assert error.name == "catalog.schema.dataset"
        assert len(error.searched_paths) == 2

    def test_str_format(self) -> None:
        """Test __str__ includes name and paths."""
        error = DatasetNotFoundError(
            message="Not found",
            name="my_dataset",
            searched_paths=[Path("/search/path1"), Path("/search/path2")],
        )

        result = str(error)

        assert "[DLI-101]" in result
        assert "my_dataset" in result
        assert "/search/path1" in result

    def test_str_format_no_paths(self) -> None:
        """Test __str__ with no searched paths shows N/A."""
        error = DatasetNotFoundError(
            message="Not found",
            name="my_dataset",
        )

        result = str(error)

        assert "N/A" in result


class TestMetricNotFoundError:
    """Tests for MetricNotFoundError."""

    def test_basic_creation(self) -> None:
        """Test creating metric not found error."""
        error = MetricNotFoundError(message="Not found")

        assert error.code == ErrorCode.METRIC_NOT_FOUND
        assert error.name == ""
        assert error.searched_paths == []

    def test_with_name_and_paths(self) -> None:
        """Test creating with name and searched paths."""
        error = MetricNotFoundError(
            message="Metric not found",
            name="catalog.schema.metric",
            searched_paths=[Path("/metrics")],
        )

        assert error.name == "catalog.schema.metric"
        assert len(error.searched_paths) == 1

    def test_str_with_paths(self) -> None:
        """Test __str__ with searched paths."""
        error = MetricNotFoundError(
            message="Not found",
            name="my_metric",
            searched_paths=[Path("/path")],
        )

        result = str(error)

        assert "[DLI-102]" in result
        assert "my_metric" in result
        assert "Searched:" in result

    def test_str_without_paths(self) -> None:
        """Test __str__ without searched paths."""
        error = MetricNotFoundError(
            message="Not found",
            name="my_metric",
        )

        result = str(error)

        assert "[DLI-102]" in result
        assert "my_metric" in result
        assert "Searched:" not in result


class TestTableNotFoundError:
    """Tests for TableNotFoundError."""

    def test_basic_creation(self) -> None:
        """Test creating table not found error."""
        error = TableNotFoundError(message="Not found")

        assert error.code == ErrorCode.TABLE_NOT_FOUND
        assert error.table_ref == ""

    def test_with_table_ref(self) -> None:
        """Test creating with table reference."""
        error = TableNotFoundError(
            message="Table not found",
            table_ref="project.dataset.table",
        )

        assert error.table_ref == "project.dataset.table"

    def test_str_format(self) -> None:
        """Test __str__ format."""
        error = TableNotFoundError(
            message="Not found",
            table_ref="my_table",
        )

        result = str(error)

        assert "[DLI-103]" in result
        assert "my_table" in result
        assert "catalog" in result.lower()


class TestWorkflowNotFoundError:
    """Tests for WorkflowNotFoundError."""

    def test_basic_creation(self) -> None:
        """Test creating workflow not found error."""
        error = WorkflowNotFoundError(message="Not found")

        assert error.code == ErrorCode.WORKFLOW_NOT_FOUND
        assert error.dataset_name == ""

    def test_with_dataset_name(self) -> None:
        """Test creating with dataset name."""
        error = WorkflowNotFoundError(
            message="Workflow not found",
            dataset_name="my_dataset",
        )

        assert error.dataset_name == "my_dataset"

    def test_str_format(self) -> None:
        """Test __str__ format."""
        error = WorkflowNotFoundError(
            message="Not found",
            dataset_name="catalog.schema.dataset",
        )

        result = str(error)

        assert "[DLI-104]" in result
        assert "catalog.schema.dataset" in result


class TestTranspileError:
    """Tests for TranspileError."""

    def test_basic_creation(self) -> None:
        """Test creating transpile error."""
        error = TranspileError(message="Transpile failed")

        assert error.code == ErrorCode.TRANSPILE_FAILED
        assert error.sql == ""
        assert error.line is None
        assert error.column is None

    def test_with_location(self) -> None:
        """Test creating with line and column."""
        error = TranspileError(
            message="Syntax error",
            sql="SELECT * FORM table",
            line=1,
            column=10,
        )

        assert error.sql == "SELECT * FORM table"
        assert error.line == 1
        assert error.column == 10

    def test_str_with_line_only(self) -> None:
        """Test __str__ with line but no column."""
        error = TranspileError(
            message="Error",
            line=5,
        )

        result = str(error)

        assert "[DLI-301]" in result
        assert "line 5" in result
        assert "column" not in result

    def test_str_with_line_and_column(self) -> None:
        """Test __str__ with both line and column."""
        error = TranspileError(
            message="Syntax error",
            line=10,
            column=15,
        )

        result = str(error)

        assert "line 10" in result
        assert "column 15" in result

    def test_str_without_location(self) -> None:
        """Test __str__ without location info."""
        error = TranspileError(message="Parse failed")

        result = str(error)

        assert result == "[DLI-301] Parse failed"


class TestServerError:
    """Tests for ServerError."""

    def test_basic_creation(self) -> None:
        """Test creating server error."""
        error = ServerError(message="Server unreachable")

        assert error.code == ErrorCode.SERVER_ERROR
        assert error.status_code is None
        assert error.url is None

    def test_with_status_and_url(self) -> None:
        """Test creating with status code and URL."""
        error = ServerError(
            message="Request failed",
            status_code=500,
            url="https://api.example.com/endpoint",
        )

        assert error.status_code == 500
        assert error.url == "https://api.example.com/endpoint"

    def test_str_with_status(self) -> None:
        """Test __str__ with status code."""
        error = ServerError(
            message="Error",
            status_code=404,
        )

        result = str(error)

        assert "[DLI-503]" in result
        assert "HTTP 404" in result

    def test_str_with_url(self) -> None:
        """Test __str__ with URL."""
        error = ServerError(
            message="Error",
            url="https://test.com",
        )

        result = str(error)

        assert "https://test.com" in result

    def test_str_full(self) -> None:
        """Test __str__ with all fields."""
        error = ServerError(
            message="Request failed",
            status_code=503,
            url="https://api.com/health",
        )

        result = str(error)

        assert "[DLI-503]" in result
        assert "Request failed" in result
        assert "HTTP 503" in result
        assert "https://api.com/health" in result


class TestExceptionHierarchy:
    """Tests for exception class hierarchy."""

    def test_all_inherit_from_dli_error(self) -> None:
        """Test all exceptions inherit from DLIError."""
        exceptions = [
            ConfigurationError(message="test"),
            DLIValidationError(message="test"),
            ExecutionError(message="test"),
            DatasetNotFoundError(message="test"),
            MetricNotFoundError(message="test"),
            TableNotFoundError(message="test"),
            WorkflowNotFoundError(message="test"),
            TranspileError(message="test"),
            ServerError(message="test"),
        ]

        for exc in exceptions:
            assert isinstance(exc, DLIError)
            assert isinstance(exc, Exception)

    def test_can_catch_by_dli_error(self) -> None:
        """Test all exceptions can be caught as DLIError."""
        exceptions_to_test = [
            DatasetNotFoundError(message="test"),
            ConfigurationError(message="test"),
            ServerError(message="test"),
        ]

        for exc in exceptions_to_test:
            with pytest.raises(DLIError):
                raise exc

    def test_specific_catch(self) -> None:
        """Test specific exceptions can be caught specifically."""
        with pytest.raises(DatasetNotFoundError):
            raise DatasetNotFoundError(message="test")

        with pytest.raises(MetricNotFoundError):
            raise MetricNotFoundError(message="test")

        # But they can also be caught as DLIError
        try:
            raise DatasetNotFoundError(message="test")
        except DLIError as e:
            assert isinstance(e, DatasetNotFoundError)
