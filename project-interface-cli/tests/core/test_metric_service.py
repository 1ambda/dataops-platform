"""Tests for the DLI Core Engine metric service module."""

from pathlib import Path

import pytest
import yaml

from dli.core.executor import MockExecutor
from dli.core.metric_service import MetricService


@pytest.fixture
def sample_project_path():
    """Return path to the sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


@pytest.fixture
def temp_project(tmp_path):
    """Create a temporary project structure with a metric spec."""
    # dli.yaml
    config = {
        "version": "1",
        "project": {"name": "test-project"},
        "discovery": {
            "datasets_dir": "datasets",
            "metrics_dir": "metrics",
        },
        "defaults": {"dialect": "trino", "timeout_seconds": 300},
    }
    (tmp_path / "dli.yaml").write_text(yaml.dump(config))

    # metrics directory
    metrics_dir = tmp_path / "metrics"
    metrics_dir.mkdir(parents=True)

    # Metric spec with inline SQL (type: Metric, query_type: SELECT)
    spec = {
        "name": "iceberg.analytics.test_metric",
        "owner": "owner@example.com",
        "team": "@team",
        "type": "Metric",
        "domains": ["analytics"],
        "tags": ["test"],
        "query_type": "SELECT",
        "parameters": [
            {"name": "date", "type": "date", "required": True},
            {"name": "limit", "type": "integer", "required": False, "default": 10},
        ],
        "query_statement": "SELECT user_id, COUNT(*) as cnt FROM users WHERE dt = '{{ date }}' GROUP BY user_id LIMIT {{ limit }}",
        "metrics": [
            {
                "name": "user_count",
                "aggregation": "count_distinct",
                "expression": "user_id",
                "description": "Count of unique users",
            }
        ],
        "dimensions": [
            {
                "name": "date",
                "type": "time",
                "expression": "dt",
                "description": "Event date",
            }
        ],
    }
    (metrics_dir / "metric.iceberg.analytics.test_metric.yaml").write_text(yaml.dump(spec))

    return tmp_path


@pytest.fixture
def mock_executor():
    """Create a mock executor with sample data."""
    return MockExecutor(
        mock_data=[
            {"user_id": "user1", "cnt": 100},
            {"user_id": "user2", "cnt": 200},
            {"user_id": "user3", "cnt": 150},
        ]
    )


@pytest.fixture
def service(temp_project, mock_executor):
    """Create a MetricService instance."""
    return MetricService(project_path=temp_project, executor=mock_executor)


@pytest.fixture
def fixture_service(sample_project_path, mock_executor):
    """Create a MetricService from the sample project fixture."""
    return MetricService(project_path=sample_project_path, executor=mock_executor)


class TestMetricService:
    """Tests for MetricService class."""

    def test_list_metrics(self, service):
        """Test listing all metrics."""
        metrics = service.list_metrics()
        assert len(metrics) == 1
        assert metrics[0].name == "iceberg.analytics.test_metric"

    def test_list_metrics_with_filter(self, service):
        """Test filtering metrics by tag."""
        metrics = service.list_metrics(tag="test")
        assert len(metrics) == 1

        metrics = service.list_metrics(tag="nonexistent")
        assert len(metrics) == 0

    def test_list_metrics_by_domain(self, service):
        """Test filtering metrics by domain."""
        metrics = service.list_metrics(domain="analytics")
        assert len(metrics) == 1

        metrics = service.list_metrics(domain="nonexistent")
        assert len(metrics) == 0

    def test_get_metric(self, service):
        """Test getting metric by name."""
        metric = service.get_metric("iceberg.analytics.test_metric")
        assert metric is not None
        assert metric.name == "iceberg.analytics.test_metric"

    def test_get_metric_not_found(self, service):
        """Test getting nonexistent metric."""
        metric = service.get_metric("nonexistent")
        assert metric is None

    def test_validate_success(self, service):
        """Test validating metric successfully."""
        results = service.validate("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert len(results) == 1
        assert results[0].is_valid is True
        assert results[0].rendered_sql is not None
        assert "2024-01-01" in results[0].rendered_sql

    def test_validate_metric_not_found(self, service):
        """Test validating nonexistent metric."""
        results = service.validate("nonexistent", {})
        assert len(results) == 1
        assert results[0].is_valid is False
        assert "not found" in results[0].errors[0]

    def test_validate_missing_param(self, service):
        """Test validating with missing required parameter."""
        results = service.validate("iceberg.analytics.test_metric", {})
        assert len(results) == 1
        assert results[0].is_valid is False

    def test_render_sql(self, service):
        """Test rendering SQL."""
        rendered = service.render_sql("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert rendered is not None
        assert "2024-01-01" in rendered
        assert "LIMIT 10" in rendered

    def test_render_sql_with_custom_limit(self, service):
        """Test rendering SQL with custom parameter."""
        rendered = service.render_sql("iceberg.analytics.test_metric", {"date": "2024-01-01", "limit": 50})
        assert rendered is not None
        assert "LIMIT 50" in rendered

    def test_render_sql_not_found(self, service):
        """Test rendering SQL for nonexistent metric."""
        rendered = service.render_sql("nonexistent", {})
        assert rendered is None

    def test_execute_success(self, service, mock_executor):
        """Test executing metric successfully."""
        result = service.execute("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert result.success is True
        assert result.metric_name == "iceberg.analytics.test_metric"
        assert result.row_count == 3
        assert len(result.rows) == 3
        assert result.columns == ["user_id", "cnt"]

    def test_execute_metric_not_found(self, service):
        """Test executing nonexistent metric."""
        result = service.execute("nonexistent", {})
        assert result.success is False
        assert "not found" in result.error_message

    def test_execute_missing_param(self, service):
        """Test executing with missing required parameter."""
        result = service.execute("iceberg.analytics.test_metric", {})
        assert result.success is False

    def test_execute_dry_run(self, service):
        """Test executing with dry run only."""
        result = service.execute("iceberg.analytics.test_metric", {"date": "2024-01-01"}, dry_run=True)
        assert result.success is True
        assert result.row_count == 0  # No actual execution
        assert len(result.rows) == 0  # No data returned

    def test_execute_no_executor(self, temp_project):
        """Test executing without an executor."""
        service = MetricService(project_path=temp_project)
        result = service.execute("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert result.success is False
        assert "Executor not configured" in result.error_message

    def test_get_tables(self, service):
        """Test extracting tables from metric."""
        tables = service.get_tables("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert "users" in tables

    def test_get_tables_not_found(self, service):
        """Test getting tables for nonexistent metric."""
        tables = service.get_tables("nonexistent", {})
        assert tables == []

    def test_format_sql(self, service):
        """Test formatting SQL."""
        formatted = service.format_sql("iceberg.analytics.test_metric", {"date": "2024-01-01"})
        assert formatted is not None
        # Formatted SQL should have newlines
        assert "\n" in formatted

    def test_format_sql_not_found(self, service):
        """Test formatting SQL for nonexistent metric."""
        formatted = service.format_sql("nonexistent", {})
        assert formatted is None

    def test_reload(self, service, temp_project):
        """Test reloading metric specs."""
        # Add a new spec (type: Metric, query_type: SELECT)
        new_spec = {
            "name": "iceberg.new.metric",
            "owner": "owner@example.com",
            "team": "@team",
            "type": "Metric",
            "query_type": "SELECT",
            "query_statement": "SELECT COUNT(*) FROM users",
        }
        (temp_project / "metrics" / "metric.iceberg.new.metric.yaml").write_text(
            yaml.dump(new_spec)
        )

        # Before reload
        assert service.get_metric("iceberg.new.metric") is None

        # After reload
        service.reload()
        assert service.get_metric("iceberg.new.metric") is not None

    def test_test_connection(self, service, mock_executor):
        """Test connection test."""
        assert service.test_connection() is True

    def test_test_connection_no_executor(self, temp_project):
        """Test connection test without executor."""
        service = MetricService(project_path=temp_project)
        assert service.test_connection() is False

    def test_project_name(self, service):
        """Test getting project name."""
        assert service.project_name == "test-project"

    def test_default_dialect(self, service):
        """Test getting default dialect."""
        assert service.default_dialect == "trino"


class TestMetricServiceFromFixture:
    """Tests for MetricService using the sample project fixture."""

    def test_list_metrics(self, fixture_service):
        """Test listing metrics from fixture."""
        metrics = fixture_service.list_metrics()
        # Should find metrics from the sample_project/metrics directory
        assert len(metrics) >= 1

        names = {m.name for m in metrics}
        assert "iceberg.analytics.user_engagement" in names

    def test_validate_user_engagement(self, fixture_service):
        """Test validating user_engagement metric."""
        results = fixture_service.validate(
            "iceberg.analytics.user_engagement",
            {"start_date": "2024-01-01", "end_date": "2024-01-31"},
        )
        # Should have 1 result for main query
        assert len(results) == 1
        assert results[0].is_valid is True

    def test_render_user_engagement(self, fixture_service):
        """Test rendering user_engagement metric."""
        rendered = fixture_service.render_sql(
            "iceberg.analytics.user_engagement",
            {"start_date": "2024-01-01", "end_date": "2024-01-31", "min_sessions": 5},
        )
        assert rendered is not None
        assert "2024-01-01" in rendered
        assert "2024-01-31" in rendered

    def test_execute_user_engagement(self, fixture_service):
        """Test executing user_engagement metric."""
        result = fixture_service.execute(
            "iceberg.analytics.user_engagement",
            {"start_date": "2024-01-01", "end_date": "2024-01-31"},
        )
        assert result.success is True
        assert result.metric_name == "iceberg.analytics.user_engagement"

    def test_get_catalogs(self, fixture_service):
        """Test getting catalogs."""
        catalogs = fixture_service.get_catalogs()
        assert "iceberg" in catalogs

    def test_get_schemas(self, fixture_service):
        """Test getting schemas."""
        schemas = fixture_service.get_schemas()
        assert "analytics" in schemas

    def test_get_domains(self, fixture_service):
        """Test getting domains."""
        domains = fixture_service.get_domains()
        assert "analytics" in domains

    def test_get_tags(self, fixture_service):
        """Test getting tags."""
        tags = fixture_service.get_tags()
        assert "metric" in tags


class TestMetricServiceIntegration:
    """Integration tests for MetricService."""

    def test_full_workflow(self, service, mock_executor):
        """Test complete workflow from list to execute."""
        # 1. List metrics
        metrics = service.list_metrics()
        assert len(metrics) > 0

        # 2. Get specific metric
        metric = service.get_metric("iceberg.analytics.test_metric")
        assert metric is not None

        # 3. Validate with parameters
        params = {"date": "2024-01-01", "limit": 50}
        validation = service.validate("iceberg.analytics.test_metric", params)
        assert all(v.is_valid for v in validation)

        # 4. Render SQL
        rendered = service.render_sql("iceberg.analytics.test_metric", params)
        assert rendered is not None

        # 5. Execute
        result = service.execute("iceberg.analytics.test_metric", params)
        assert result.success is True
        assert result.row_count == 3

    def test_workflow_with_errors(self, service):
        """Test handling errors in workflow."""
        # Nonexistent metric
        result = service.execute("bad_metric", {})
        assert result.success is False

        # Missing parameters
        result = service.execute("iceberg.analytics.test_metric", {})
        assert result.success is False


class TestMetricExecutionResult:
    """Tests for MetricExecutionResult model."""

    def test_result_attributes(self, service):
        """Test MetricExecutionResult attributes."""
        result = service.execute("iceberg.analytics.test_metric", {"date": "2024-01-01"})

        assert result.metric_name == "iceberg.analytics.test_metric"
        assert result.success is True
        assert result.row_count == 3
        assert len(result.rows) == 3
        assert result.columns == ["user_id", "cnt"]
        assert result.error_message is None
        assert result.execution_time_ms is not None
        assert result.rendered_sql is not None
        assert result.executed_at is not None

    def test_result_on_failure(self, service):
        """Test MetricExecutionResult on failure."""
        result = service.execute("nonexistent", {})

        assert result.success is False
        assert result.row_count == 0
        assert len(result.rows) == 0
        assert result.error_message is not None
