"""Tests for dli.api.dataset module.

Covers:
- DatasetAPI initialization with context
- Mock mode operations
- CRUD operations (list, get)
- Execution (run, run_sql)
- Validation
- SQL rendering
- Introspection (get_tables, get_columns)
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

import pytest

from dli import DatasetAPI, ExecutionContext
from dli.exceptions import ConfigurationError
from dli.models.common import ResultStatus, ValidationResult


class TestDatasetAPIInit:
    """Tests for DatasetAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = DatasetAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(mock_mode=True)
        api = DatasetAPI(context=ctx)

        assert api.context is ctx
        assert api.context.mock_mode is True

    def test_init_with_project_path(self) -> None:
        """Test initialization with project path."""
        ctx = ExecutionContext(project_path=Path("/test/project"))
        api = DatasetAPI(context=ctx)

        assert api.context.project_path == Path("/test/project")

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", mock_mode=True)
        api = DatasetAPI(context=ctx)

        result = repr(api)

        assert "DatasetAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_service_init(self) -> None:
        """Test that service is not created until needed."""
        api = DatasetAPI(context=ExecutionContext(mock_mode=True))

        # _service should be None before any operation
        assert api._service is None


class TestDatasetAPIMockMode:
    """Tests for DatasetAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        ctx = ExecutionContext(mock_mode=True)
        return DatasetAPI(context=ctx)

    def test_list_datasets_returns_empty(self, mock_api: DatasetAPI) -> None:
        """Test list_datasets returns empty list in mock mode."""
        result = mock_api.list_datasets()

        assert result == []

    def test_get_returns_none(self, mock_api: DatasetAPI) -> None:
        """Test get returns None in mock mode."""
        result = mock_api.get("catalog.schema.dataset")

        assert result is None

    def test_run_returns_success(self, mock_api: DatasetAPI) -> None:
        """Test run returns success result in mock mode."""
        result = mock_api.run("catalog.schema.dataset")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "catalog.schema.dataset"
        assert result.duration_ms == 0

    def test_run_with_show_sql(self, mock_api: DatasetAPI) -> None:
        """Test run with show_sql flag in mock mode."""
        result = mock_api.run("my_dataset", show_sql=True)

        assert result.sql == "-- Mock SQL"

    def test_run_sql_returns_success(self, mock_api: DatasetAPI) -> None:
        """Test run_sql returns success in mock mode."""
        result = mock_api.run_sql("SELECT 1")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "<inline>"
        assert result.sql == "SELECT 1"

    def test_validate_returns_valid(self, mock_api: DatasetAPI) -> None:
        """Test validate returns valid in mock mode."""
        result = mock_api.validate("my_dataset")

        assert result.valid is True
        assert result.errors == []

    def test_render_sql_returns_mock(self, mock_api: DatasetAPI) -> None:
        """Test render_sql returns mock SQL in mock mode."""
        result = mock_api.render_sql("my_dataset")

        assert result == "-- Mock SQL for my_dataset"

    def test_get_tables_returns_empty(self, mock_api: DatasetAPI) -> None:
        """Test get_tables returns empty list in mock mode."""
        result = mock_api.get_tables("my_dataset")

        assert result == []

    def test_get_columns_returns_empty(self, mock_api: DatasetAPI) -> None:
        """Test get_columns returns empty list in mock mode."""
        result = mock_api.get_columns("my_dataset")

        assert result == []

    def test_test_connection_returns_true(self, mock_api: DatasetAPI) -> None:
        """Test test_connection returns True in mock mode."""
        result = mock_api.test_connection()

        assert result is True

    def test_register_does_nothing(self, mock_api: DatasetAPI) -> None:
        """Test register does nothing in mock mode."""
        # Should not raise
        mock_api.register("my_dataset")


class TestDatasetAPIRun:
    """Tests for DatasetAPI.run method."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        return DatasetAPI(context=ExecutionContext(mock_mode=True))

    def test_run_basic(self, mock_api: DatasetAPI) -> None:
        """Test basic run execution."""
        result = mock_api.run("catalog.schema.dataset")

        assert result.name == "catalog.schema.dataset"
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.started_at, datetime)

    def test_run_with_parameters(self, mock_api: DatasetAPI) -> None:
        """Test run with parameters."""
        result = mock_api.run(
            "my_dataset",
            parameters={"date": "2025-01-01", "limit": 100},
        )

        assert result.status == ResultStatus.SUCCESS

    def test_run_with_dry_run(self, mock_api: DatasetAPI) -> None:
        """Test run with dry_run flag."""
        result = mock_api.run("my_dataset", dry_run=True)

        assert result.status == ResultStatus.SUCCESS

    def test_run_context_parameters_merged(self) -> None:
        """Test that context parameters are merged with run parameters."""
        ctx = ExecutionContext(
            mock_mode=True,
            parameters={"env": "prod", "date": "default"},
        )
        api = DatasetAPI(context=ctx)

        # Run parameters should override context parameters
        result = api.run("my_dataset", parameters={"date": "2025-01-01"})

        assert result.status == ResultStatus.SUCCESS

    def test_run_uses_context_dry_run(self) -> None:
        """Test that context dry_run is used if not explicitly set."""
        ctx = ExecutionContext(mock_mode=True, dry_run=True)
        api = DatasetAPI(context=ctx)

        result = api.run("my_dataset")

        assert result.status == ResultStatus.SUCCESS


class TestDatasetAPIValidation:
    """Tests for DatasetAPI.validate method."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        return DatasetAPI(context=ExecutionContext(mock_mode=True))

    def test_validate_basic(self, mock_api: DatasetAPI) -> None:
        """Test basic validation."""
        result = mock_api.validate("my_dataset")

        assert isinstance(result, ValidationResult)
        assert result.valid is True

    def test_validate_with_strict(self, mock_api: DatasetAPI) -> None:
        """Test validation with strict mode."""
        result = mock_api.validate("my_dataset", strict=True)

        assert result.valid is True

    def test_validate_with_check_deps(self, mock_api: DatasetAPI) -> None:
        """Test validation with dependency checking."""
        result = mock_api.validate("my_dataset", check_deps=True)

        assert result.valid is True


class TestDatasetAPIConfiguration:
    """Tests for DatasetAPI configuration requirements."""

    def test_requires_project_path_in_non_mock_mode(self) -> None:
        """Test that project_path is required in non-mock mode."""
        ctx = ExecutionContext(mock_mode=False, project_path=None)
        api = DatasetAPI(context=ctx)

        # Should raise ConfigurationError when trying to get service
        with pytest.raises(ConfigurationError) as exc_info:
            api._get_service()

        assert "project_path is required" in exc_info.value.message

    def test_project_path_not_required_in_mock_mode(self) -> None:
        """Test that project_path is not required in mock mode."""
        ctx = ExecutionContext(mock_mode=True, project_path=None)
        api = DatasetAPI(context=ctx)

        # Should not raise - mock mode doesn't need project_path
        result = api.list_datasets()
        assert result == []


class TestDatasetAPIListDatasets:
    """Tests for DatasetAPI.list_datasets method."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        return DatasetAPI(context=ExecutionContext(mock_mode=True))

    def test_list_basic(self, mock_api: DatasetAPI) -> None:
        """Test basic list operation."""
        result = mock_api.list_datasets()

        assert isinstance(result, list)

    def test_list_with_path_filter(self, mock_api: DatasetAPI) -> None:
        """Test list with path filter."""
        result = mock_api.list_datasets(path=Path("/custom/path"))

        assert isinstance(result, list)

    def test_list_with_source_local(self, mock_api: DatasetAPI) -> None:
        """Test list with local source."""
        result = mock_api.list_datasets(source="local")

        assert isinstance(result, list)

    def test_list_with_source_server(self, mock_api: DatasetAPI) -> None:
        """Test list with server source."""
        result = mock_api.list_datasets(source="server")

        assert isinstance(result, list)

    def test_list_with_filters(self, mock_api: DatasetAPI) -> None:
        """Test list with multiple filters."""
        result = mock_api.list_datasets(
            domain="analytics",
            owner="data-team",
            tags=["production", "critical"],
            catalog="iceberg",
            schema="reporting",
        )

        assert isinstance(result, list)


class TestDatasetAPIRenderSQL:
    """Tests for DatasetAPI.render_sql method."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        return DatasetAPI(context=ExecutionContext(mock_mode=True))

    def test_render_basic(self, mock_api: DatasetAPI) -> None:
        """Test basic SQL rendering."""
        result = mock_api.render_sql("my_dataset")

        assert "Mock SQL" in result
        assert "my_dataset" in result

    def test_render_with_parameters(self, mock_api: DatasetAPI) -> None:
        """Test rendering with parameters."""
        result = mock_api.render_sql(
            "my_dataset",
            parameters={"date": "2025-01-01"},
        )

        assert isinstance(result, str)

    def test_render_with_format_sql_false(self, mock_api: DatasetAPI) -> None:
        """Test rendering without SQL formatting."""
        result = mock_api.render_sql("my_dataset", format_sql=False)

        assert isinstance(result, str)


class TestDatasetAPIRunSQL:
    """Tests for DatasetAPI.run_sql method."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create DatasetAPI in mock mode."""
        return DatasetAPI(context=ExecutionContext(mock_mode=True))

    def test_run_sql_basic(self, mock_api: DatasetAPI) -> None:
        """Test basic SQL execution."""
        result = mock_api.run_sql("SELECT * FROM table")

        assert result.name == "<inline>"
        assert result.status == ResultStatus.SUCCESS

    def test_run_sql_with_parameters(self, mock_api: DatasetAPI) -> None:
        """Test SQL execution with parameters."""
        result = mock_api.run_sql(
            "SELECT * FROM {{ table }}",
            parameters={"table": "events"},
        )

        assert result.status == ResultStatus.SUCCESS

    def test_run_sql_with_transpile_false(self, mock_api: DatasetAPI) -> None:
        """Test SQL execution without transpilation."""
        result = mock_api.run_sql("SELECT 1", transpile=False)

        assert result.status == ResultStatus.SUCCESS

    def test_run_sql_with_dialect(self, mock_api: DatasetAPI) -> None:
        """Test SQL execution with specific dialect."""
        result = mock_api.run_sql("SELECT 1", dialect="bigquery")

        assert result.status == ResultStatus.SUCCESS
