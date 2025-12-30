"""Tests for dli.api.metric module.

Covers:
- MetricAPI initialization with context
- Mock mode operations
- CRUD operations (list, get)
- Execution (run)
- Validation
- SQL rendering
- Introspection (get_tables, get_columns)
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

import pytest

from dli import ExecutionContext, MetricAPI
from dli.exceptions import ConfigurationError
from dli.models.common import ResultStatus, ValidationResult


class TestMetricAPIInit:
    """Tests for MetricAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = MetricAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(mock_mode=True)
        api = MetricAPI(context=ctx)

        assert api.context is ctx
        assert api.context.mock_mode is True

    def test_init_with_project_path(self) -> None:
        """Test initialization with project path."""
        ctx = ExecutionContext(project_path=Path("/test/project"))
        api = MetricAPI(context=ctx)

        assert api.context.project_path == Path("/test/project")

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", mock_mode=True)
        api = MetricAPI(context=ctx)

        result = repr(api)

        assert "MetricAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_service_init(self) -> None:
        """Test that service is not created until needed."""
        api = MetricAPI(context=ExecutionContext(mock_mode=True))

        # _service should be None before any operation
        assert api._service is None


class TestMetricAPIMockMode:
    """Tests for MetricAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        ctx = ExecutionContext(mock_mode=True)
        return MetricAPI(context=ctx)

    def test_list_metrics_returns_empty(self, mock_api: MetricAPI) -> None:
        """Test list_metrics returns empty list in mock mode."""
        result = mock_api.list_metrics()

        assert result == []

    def test_get_returns_none(self, mock_api: MetricAPI) -> None:
        """Test get returns None in mock mode."""
        result = mock_api.get("catalog.schema.metric")

        assert result is None

    def test_run_returns_success(self, mock_api: MetricAPI) -> None:
        """Test run returns success result in mock mode."""
        result = mock_api.run("catalog.schema.metric")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "catalog.schema.metric"
        assert result.duration_ms == 0
        assert result.data == []
        assert result.row_count == 0

    def test_run_with_show_sql(self, mock_api: MetricAPI) -> None:
        """Test run with show_sql flag in mock mode."""
        result = mock_api.run("my_metric", show_sql=True)

        assert result.sql == "-- Mock SQL"

    def test_validate_returns_valid(self, mock_api: MetricAPI) -> None:
        """Test validate returns valid in mock mode."""
        result = mock_api.validate("my_metric")

        assert result.valid is True
        assert result.errors == []

    def test_render_sql_returns_mock(self, mock_api: MetricAPI) -> None:
        """Test render_sql returns mock SQL in mock mode."""
        result = mock_api.render_sql("my_metric")

        assert result == "-- Mock SQL for my_metric"

    def test_get_tables_returns_empty(self, mock_api: MetricAPI) -> None:
        """Test get_tables returns empty list in mock mode."""
        result = mock_api.get_tables("my_metric")

        assert result == []

    def test_get_columns_returns_empty(self, mock_api: MetricAPI) -> None:
        """Test get_columns returns empty list in mock mode."""
        result = mock_api.get_columns("my_metric")

        assert result == []

    def test_test_connection_returns_true(self, mock_api: MetricAPI) -> None:
        """Test test_connection returns True in mock mode."""
        result = mock_api.test_connection()

        assert result is True

    def test_register_does_nothing(self, mock_api: MetricAPI) -> None:
        """Test register does nothing in mock mode."""
        # Should not raise
        mock_api.register("my_metric")


class TestMetricAPIRun:
    """Tests for MetricAPI.run method."""

    @pytest.fixture
    def mock_api(self) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        return MetricAPI(context=ExecutionContext(mock_mode=True))

    def test_run_basic(self, mock_api: MetricAPI) -> None:
        """Test basic run execution."""
        result = mock_api.run("catalog.schema.metric")

        assert result.name == "catalog.schema.metric"
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.started_at, datetime)

    def test_run_with_parameters(self, mock_api: MetricAPI) -> None:
        """Test run with parameters."""
        result = mock_api.run(
            "my_metric",
            parameters={"date": "2025-01-01", "limit": 100},
        )

        assert result.status == ResultStatus.SUCCESS

    def test_run_with_dry_run(self, mock_api: MetricAPI) -> None:
        """Test run with dry_run flag."""
        result = mock_api.run("my_metric", dry_run=True)

        assert result.status == ResultStatus.SUCCESS

    def test_run_context_parameters_merged(self) -> None:
        """Test that context parameters are merged with run parameters."""
        ctx = ExecutionContext(
            mock_mode=True,
            parameters={"env": "prod", "date": "default"},
        )
        api = MetricAPI(context=ctx)

        result = api.run("my_metric", parameters={"date": "2025-01-01"})

        assert result.status == ResultStatus.SUCCESS

    def test_run_uses_context_dry_run(self) -> None:
        """Test that context dry_run is used if not explicitly set."""
        ctx = ExecutionContext(mock_mode=True, dry_run=True)
        api = MetricAPI(context=ctx)

        result = api.run("my_metric")

        assert result.status == ResultStatus.SUCCESS

    def test_run_result_includes_data_fields(self, mock_api: MetricAPI) -> None:
        """Test that run result includes metric-specific fields."""
        result = mock_api.run("my_metric")

        assert hasattr(result, "data")
        assert hasattr(result, "row_count")
        assert hasattr(result, "columns")


class TestMetricAPIValidation:
    """Tests for MetricAPI.validate method."""

    @pytest.fixture
    def mock_api(self) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        return MetricAPI(context=ExecutionContext(mock_mode=True))

    def test_validate_basic(self, mock_api: MetricAPI) -> None:
        """Test basic validation."""
        result = mock_api.validate("my_metric")

        assert isinstance(result, ValidationResult)
        assert result.valid is True

    def test_validate_with_strict(self, mock_api: MetricAPI) -> None:
        """Test validation with strict mode."""
        result = mock_api.validate("my_metric", strict=True)

        assert result.valid is True

    def test_validate_with_check_deps(self, mock_api: MetricAPI) -> None:
        """Test validation with dependency checking."""
        result = mock_api.validate("my_metric", check_deps=True)

        assert result.valid is True


class TestMetricAPIConfiguration:
    """Tests for MetricAPI configuration requirements."""

    def test_requires_project_path_in_non_mock_mode(self) -> None:
        """Test that project_path is required in non-mock mode."""
        ctx = ExecutionContext(mock_mode=False, project_path=None)
        api = MetricAPI(context=ctx)

        # Should raise ConfigurationError when trying to get service
        with pytest.raises(ConfigurationError) as exc_info:
            api._get_service()

        assert "project_path is required" in exc_info.value.message

    def test_project_path_not_required_in_mock_mode(self) -> None:
        """Test that project_path is not required in mock mode."""
        ctx = ExecutionContext(mock_mode=True, project_path=None)
        api = MetricAPI(context=ctx)

        # Should not raise - mock mode doesn't need project_path
        result = api.list_metrics()
        assert result == []


class TestMetricAPIListMetrics:
    """Tests for MetricAPI.list_metrics method."""

    @pytest.fixture
    def mock_api(self) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        return MetricAPI(context=ExecutionContext(mock_mode=True))

    def test_list_basic(self, mock_api: MetricAPI) -> None:
        """Test basic list operation."""
        result = mock_api.list_metrics()

        assert isinstance(result, list)

    def test_list_with_path_filter(self, mock_api: MetricAPI) -> None:
        """Test list with path filter."""
        result = mock_api.list_metrics(path=Path("/custom/path"))

        assert isinstance(result, list)

    def test_list_with_source_local(self, mock_api: MetricAPI) -> None:
        """Test list with local source."""
        result = mock_api.list_metrics(source="local")

        assert isinstance(result, list)

    def test_list_with_source_server(self, mock_api: MetricAPI) -> None:
        """Test list with server source."""
        result = mock_api.list_metrics(source="server")

        assert isinstance(result, list)

    def test_list_with_filters(self, mock_api: MetricAPI) -> None:
        """Test list with multiple filters."""
        result = mock_api.list_metrics(
            domain="analytics",
            owner="data-team",
            tags=["production", "critical"],
            catalog="iceberg",
            schema="reporting",
        )

        assert isinstance(result, list)


class TestMetricAPIRenderSQL:
    """Tests for MetricAPI.render_sql method."""

    @pytest.fixture
    def mock_api(self) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        return MetricAPI(context=ExecutionContext(mock_mode=True))

    def test_render_basic(self, mock_api: MetricAPI) -> None:
        """Test basic SQL rendering."""
        result = mock_api.render_sql("my_metric")

        assert "Mock SQL" in result
        assert "my_metric" in result

    def test_render_with_parameters(self, mock_api: MetricAPI) -> None:
        """Test rendering with parameters."""
        result = mock_api.render_sql(
            "my_metric",
            parameters={"date": "2025-01-01"},
        )

        assert isinstance(result, str)

    def test_render_with_format_sql_false(self, mock_api: MetricAPI) -> None:
        """Test rendering without SQL formatting."""
        result = mock_api.render_sql("my_metric", format_sql=False)

        assert isinstance(result, str)


class TestMetricAPIVsDatasetAPI:
    """Tests comparing MetricAPI and DatasetAPI behavior."""

    def test_metric_result_has_data_field(self) -> None:
        """Test that metric result has data field (unlike dataset)."""
        api = MetricAPI(context=ExecutionContext(mock_mode=True))
        result = api.run("my_metric")

        # Metric results have data (SELECT query results)
        assert hasattr(result, "data")
        assert hasattr(result, "row_count")

    def test_both_apis_share_context_interface(self) -> None:
        """Test that both APIs use the same context interface."""
        from dli import DatasetAPI

        ctx = ExecutionContext(
            mock_mode=True,
            dialect="bigquery",
            parameters={"date": "2025-01-01"},
        )

        dataset_api = DatasetAPI(context=ctx)
        metric_api = MetricAPI(context=ctx)

        # Both use same context type
        assert dataset_api.context.dialect == metric_api.context.dialect
        assert dataset_api.context.mock_mode == metric_api.context.mock_mode
