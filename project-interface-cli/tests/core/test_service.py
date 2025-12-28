"""Tests for the DLI Core Engine service module."""

from pathlib import Path

import pytest
import yaml

from dli.core.executor import MockExecutor
from dli.core.service import DatasetService


@pytest.fixture
def sample_project_path():
    """Return path to the sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


@pytest.fixture
def temp_project(tmp_path):
    """Create a temporary project structure."""
    # dli.yaml
    config = {
        "version": "1",
        "project": {"name": "test-project"},
        "discovery": {"datasets_dir": "datasets"},
        "defaults": {"dialect": "trino", "timeout_seconds": 300},
    }
    (tmp_path / "dli.yaml").write_text(yaml.dump(config))

    # datasets directory
    datasets_dir = tmp_path / "datasets"
    datasets_dir.mkdir(parents=True)

    # Dataset with inline SQL (type: Dataset, query_type: DML)
    spec = {
        "name": "iceberg.analytics.test",
        "owner": "owner@example.com",
        "team": "@team",
        "type": "Dataset",
        "domains": ["analytics"],
        "tags": ["test"],
        "query_type": "DML",
        "parameters": [
            {"name": "date", "type": "date", "required": True},
            {"name": "limit", "type": "integer", "required": False, "default": 10},
        ],
        "query_statement": "INSERT INTO results SELECT * FROM users WHERE dt = '{{ date }}' LIMIT {{ limit }}",
    }
    (datasets_dir / "dataset.iceberg.analytics.test.yaml").write_text(yaml.dump(spec))

    return tmp_path


@pytest.fixture
def mock_executor():
    """Create a mock executor with sample data."""
    return MockExecutor(
        mock_data=[
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
        ]
    )


@pytest.fixture
def service(temp_project, mock_executor):
    """Create a DatasetService instance."""
    return DatasetService(project_path=temp_project, executor=mock_executor)


@pytest.fixture
def fixture_service(sample_project_path, mock_executor):
    """Create a DatasetService from the sample project fixture."""
    return DatasetService(project_path=sample_project_path, executor=mock_executor)


class TestDatasetService:
    """Tests for DatasetService class."""

    def test_list_datasets(self, service):
        """Test listing all datasets."""
        datasets = service.list_datasets()
        assert len(datasets) == 1
        assert datasets[0].name == "iceberg.analytics.test"

    def test_list_datasets_with_filter(self, service):
        """Test filtering datasets by tag."""
        datasets = service.list_datasets(tag="test")
        assert len(datasets) == 1

        datasets = service.list_datasets(tag="nonexistent")
        assert len(datasets) == 0

    def test_get_dataset(self, service):
        """Test getting dataset by name."""
        dataset = service.get_dataset("iceberg.analytics.test")
        assert dataset is not None
        assert dataset.name == "iceberg.analytics.test"

    def test_get_dataset_not_found(self, service):
        """Test getting nonexistent dataset."""
        dataset = service.get_dataset("nonexistent")
        assert dataset is None

    def test_validate_success(self, service):
        """Test validating dataset successfully."""
        results = service.validate("iceberg.analytics.test", {"date": "2024-01-01"})
        assert len(results) == 1  # Only main query
        assert results[0].is_valid is True
        assert results[0].rendered_sql is not None
        assert "2024-01-01" in results[0].rendered_sql

    def test_validate_dataset_not_found(self, service):
        """Test validating nonexistent dataset."""
        results = service.validate("nonexistent", {})
        assert len(results) == 1
        assert results[0].is_valid is False
        assert "not found" in results[0].errors[0]

    def test_validate_missing_param(self, service):
        """Test validating with missing required parameter."""
        results = service.validate("iceberg.analytics.test", {})
        assert len(results) == 1
        assert results[0].is_valid is False

    def test_render_sql(self, service):
        """Test rendering SQL."""
        rendered = service.render_sql("iceberg.analytics.test", {"date": "2024-01-01"})
        assert rendered is not None
        assert "main" in rendered
        assert "2024-01-01" in rendered["main"]
        assert "LIMIT 10" in rendered["main"]

    def test_render_sql_not_found(self, service):
        """Test rendering SQL for nonexistent dataset."""
        rendered = service.render_sql("nonexistent", {})
        assert rendered is None

    def test_execute_success(self, service, mock_executor):
        """Test executing dataset successfully."""
        result = service.execute("iceberg.analytics.test", {"date": "2024-01-01"})
        assert result.success is True
        assert result.dataset_name == "iceberg.analytics.test"
        assert result.main_result is not None
        assert result.main_result.row_count == 2

    def test_execute_dataset_not_found(self, service):
        """Test executing nonexistent dataset."""
        result = service.execute("nonexistent", {})
        assert result.success is False
        assert "not found" in result.error_message

    def test_execute_missing_param(self, service):
        """Test executing with missing required parameter."""
        result = service.execute("iceberg.analytics.test", {})
        assert result.success is False

    def test_execute_dry_run(self, service):
        """Test executing with dry run only."""
        result = service.execute("iceberg.analytics.test", {"date": "2024-01-01"}, dry_run=True)
        assert result.success is True
        assert result.main_result is None  # No actual execution

    def test_get_tables(self, service):
        """Test extracting tables from dataset."""
        tables = service.get_tables("iceberg.analytics.test", {"date": "2024-01-01"})
        assert "users" in tables

    def test_get_tables_not_found(self, service):
        """Test getting tables for nonexistent dataset."""
        tables = service.get_tables("nonexistent", {})
        assert tables == []

    def test_format_sql(self, service):
        """Test formatting SQL."""
        formatted = service.format_sql("iceberg.analytics.test", {"date": "2024-01-01"})
        assert formatted is not None
        assert "main" in formatted
        # Formatted SQL should have newlines
        assert "\n" in formatted["main"]

    def test_format_sql_not_found(self, service):
        """Test formatting SQL for nonexistent dataset."""
        formatted = service.format_sql("nonexistent", {})
        assert formatted is None

    def test_reload(self, service, temp_project):
        """Test reloading dataset specs."""
        # Add a new dataset (type: Dataset, query_type: DML)
        new_spec = {
            "name": "iceberg.new.dataset",
            "owner": "owner@example.com",
            "team": "@team",
            "type": "Dataset",
            "query_type": "DML",
            "query_statement": "INSERT INTO t SELECT 1",
        }
        (temp_project / "datasets" / "dataset.iceberg.new.dataset.yaml").write_text(
            yaml.dump(new_spec)
        )

        # Before reload
        assert service.get_dataset("iceberg.new.dataset") is None

        # After reload
        service.reload()
        assert service.get_dataset("iceberg.new.dataset") is not None

    def test_test_connection(self, service, mock_executor):
        """Test connection test."""
        assert service.test_connection() is True

    def test_test_connection_no_executor(self, temp_project):
        """Test connection test without executor."""
        service = DatasetService(project_path=temp_project)
        assert service.test_connection() is False

    def test_project_name(self, service):
        """Test getting project name."""
        assert service.project_name == "test-project"

    def test_default_dialect(self, service):
        """Test getting default dialect."""
        assert service.default_dialect == "trino"


class TestDatasetServiceFromFixture:
    """Tests for DatasetService using the sample project fixture.

    Note: DatasetService only works with DatasetSpec types (type: Dataset, query_type: DML).
    MetricSpec types (type: Metric, query_type: SELECT) are not included.
    For both types, use SpecDiscovery directly.
    """

    def test_list_datasets(self, fixture_service):
        """Test listing datasets from fixture.

        The fixture contains 2 datasets:
        - iceberg.analytics.daily_clicks (from dataset.*.yaml pattern)
        - iceberg.reporting.daily_summary (from dataset.*.yaml pattern)
        """
        datasets = fixture_service.list_datasets()
        # 2 datasets using dataset.*.yaml pattern
        assert len(datasets) == 2

        names = {d.name for d in datasets}
        assert "iceberg.analytics.daily_clicks" in names
        assert "iceberg.reporting.daily_summary" in names

    def test_validate_daily_clicks(self, fixture_service):
        """Test validating daily_clicks dataset."""
        results = fixture_service.validate(
            "iceberg.analytics.daily_clicks",
            {"execution_date": "2024-01-01"},
        )
        # Should have pre, main, and post results
        assert len(results) == 5  # 2 pre + 1 main + 2 post

        # Check that all are valid
        for result in results:
            assert result.is_valid is True

    def test_render_daily_clicks(self, fixture_service):
        """Test rendering daily_clicks dataset."""
        rendered = fixture_service.render_sql(
            "iceberg.analytics.daily_clicks",
            {"execution_date": "2024-01-01", "lookback_days": 7},
        )
        assert rendered is not None
        assert "pre" in rendered
        assert "main" in rendered
        assert "post" in rendered

        # Check pre statement
        assert "DELETE FROM" in rendered["pre"][0]
        assert "2024-01-01" in rendered["pre"][0]

        # Check main statement
        assert "INSERT INTO" in rendered["main"]
        assert "iceberg.analytics.daily_clicks" in rendered["main"]

    def test_execute_daily_clicks(self, fixture_service):
        """Test executing daily_clicks dataset."""
        result = fixture_service.execute(
            "iceberg.analytics.daily_clicks",
            {"execution_date": "2024-01-01"},
        )
        assert result.success is True
        assert len(result.pre_results) == 2
        assert result.main_result is not None
        assert len(result.post_results) == 2

    def test_get_catalogs(self, fixture_service):
        """Test getting catalogs."""
        catalogs = fixture_service.get_catalogs()
        assert "iceberg" in catalogs

    def test_get_schemas(self, fixture_service):
        """Test getting schemas from datasets only (not metrics)."""
        schemas = fixture_service.get_schemas()
        assert "analytics" in schemas
        # "reporting" is now in metrics, not datasets

    def test_get_domains(self, fixture_service):
        """Test getting domains from datasets only (not metrics)."""
        domains = fixture_service.get_domains()
        assert "feed" in domains
        # "reporting" is now in metrics, not datasets

    def test_get_tags(self, fixture_service):
        """Test getting tags from datasets only (not metrics)."""
        tags = fixture_service.get_tags()
        assert "daily" in tags
        # "report" is now in metrics, not datasets


class TestServiceIntegration:
    """Integration tests for DatasetService."""

    def test_full_workflow(self, service, mock_executor):
        """Test complete workflow from list to execute."""
        # 1. List datasets
        datasets = service.list_datasets()
        assert len(datasets) > 0

        # 2. Get specific dataset
        dataset = service.get_dataset("iceberg.analytics.test")
        assert dataset is not None

        # 3. Validate with parameters
        params = {"date": "2024-01-01", "limit": 50}
        validation = service.validate("iceberg.analytics.test", params)
        assert all(v.is_valid for v in validation)

        # 4. Render SQL
        rendered = service.render_sql("iceberg.analytics.test", params)
        assert rendered is not None

        # 5. Execute
        result = service.execute("iceberg.analytics.test", params)
        assert result.success is True
        assert result.main_result.row_count == 2

    def test_workflow_with_errors(self, service):
        """Test handling errors in workflow."""
        # Nonexistent dataset
        result = service.execute("bad_dataset", {})
        assert result.success is False

        # Missing parameters
        result = service.execute("iceberg.analytics.test", {})
        assert result.success is False
