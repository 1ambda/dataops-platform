"""Tests for the DLI Core Engine discovery module."""

from pathlib import Path

import pytest
import yaml

from dli.core.discovery import (
    DatasetDiscovery,
    ProjectConfig,
    SpecDiscovery,
    get_dli_home,
    load_project,
)
from dli.core.models import QueryType, SpecType


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
        "project": {"name": "test-project", "description": "Test Project"},
        "discovery": {"datasets_dir": "datasets"},
        "defaults": {"dialect": "trino", "timeout_seconds": 300},
        "environments": {
            "dev": {"connection_string": "trino://localhost:8080/iceberg"},
        },
    }
    (tmp_path / "dli.yaml").write_text(yaml.dump(config))

    # datasets directory
    datasets_dir = tmp_path / "datasets" / "feed"
    datasets_dir.mkdir(parents=True)

    # Dataset spec file (using dataset.*.yaml pattern)
    spec = {
        "name": "iceberg.analytics.daily_clicks",
        "owner": "henry@example.com",
        "team": "@analytics",
        "type": "Dataset",
        "domains": ["feed"],
        "query_type": "DML",
        "query_file": "daily_clicks.sql",
    }
    (datasets_dir / "dataset.iceberg.analytics.daily_clicks.yaml").write_text(
        yaml.dump(spec)
    )

    # SQL file
    (datasets_dir / "daily_clicks.sql").write_text(
        "INSERT INTO t SELECT * FROM s WHERE dt = '{{ execution_date }}'"
    )

    return tmp_path


class TestProjectConfig:
    """Tests for ProjectConfig class."""

    def test_load_config(self, temp_project):
        """Test loading project configuration."""
        config = load_project(temp_project)
        assert config.project_name == "test-project"
        assert config.datasets_dir == temp_project / "datasets"

    def test_load_config_from_fixture(self, sample_project_path):
        """Test loading project configuration from fixture."""
        config = load_project(sample_project_path)
        assert config.project_name == "test-project"

    def test_version(self, temp_project):
        """Test configuration version."""
        config = load_project(temp_project)
        assert config.version == "1"

    def test_project_description(self, temp_project):
        """Test project description."""
        config = load_project(temp_project)
        assert config.project_description == "Test Project"

    def test_metric_patterns(self, temp_project):
        """Test metric patterns."""
        config = load_project(temp_project)
        patterns = config.metric_patterns
        assert "metric.*.yaml" in patterns

    def test_dataset_patterns(self, temp_project):
        """Test dataset patterns."""
        config = load_project(temp_project)
        patterns = config.dataset_patterns
        assert "dataset.*.yaml" in patterns

    def test_defaults(self, temp_project):
        """Test default settings."""
        config = load_project(temp_project)
        assert config.defaults["dialect"] == "trino"
        assert config.default_dialect == "trino"
        assert config.default_timeout == 300

    def test_get_environment(self, temp_project):
        """Test getting environment configuration."""
        config = load_project(temp_project)
        dev_env = config.get_environment("dev")
        assert "connection_string" in dev_env

    def test_get_connection_string(self, temp_project):
        """Test getting connection string."""
        config = load_project(temp_project)
        conn_str = config.get_connection_string("dev")
        assert conn_str == "trino://localhost:8080/iceberg"

    def test_missing_environment(self, temp_project):
        """Test getting non-existent environment."""
        config = load_project(temp_project)
        assert config.get_environment("nonexistent") == {}

    def test_missing_dli_yaml(self, tmp_path):
        """Test error when dli.yaml is missing."""
        with pytest.raises(FileNotFoundError, match="dli.yaml not found"):
            load_project(tmp_path)


class TestDatasetDiscovery:
    """Tests for DatasetDiscovery class."""

    def test_discover_all(self, temp_project):
        """Test discovering all datasets."""
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        specs = list(discovery.discover_all())
        assert len(specs) == 1
        assert specs[0].name == "iceberg.analytics.daily_clicks"

    def test_discover_from_fixture(self, sample_project_path):
        """Test discovering datasets from fixture.

        Note: DatasetDiscovery only discovers DatasetSpec files (type=Dataset).
        The user_summary.yaml has type=Metric, so it's correctly skipped.
        Use SpecDiscovery.discover_all() to get both metrics and datasets.
        """
        config = load_project(sample_project_path)
        discovery = DatasetDiscovery(config)

        specs = list(discovery.discover_all())
        # 2 datasets: daily_clicks and daily_summary
        assert len(specs) == 2

        names = {s.name for s in specs}
        assert "iceberg.analytics.daily_clicks" in names
        assert "iceberg.reporting.daily_summary" in names
        # user_summary is type=Metric, so it's not discovered by DatasetDiscovery
        assert "iceberg.reporting.user_summary" not in names

    def test_find_spec(self, temp_project):
        """Test finding a specific spec."""
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("iceberg.analytics.daily_clicks")
        assert spec is not None
        assert spec.query_type == QueryType.DML

    def test_find_spec_not_found(self, temp_project):
        """Test finding non-existent spec."""
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("nonexistent.dataset.name")
        assert spec is None

    def test_sql_file_loading(self, temp_project):
        """Test loading SQL from file."""
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("iceberg.analytics.daily_clicks")
        sql = spec.get_main_sql()
        assert "INSERT INTO" in sql
        assert "{{ execution_date }}" in sql

    def test_defaults_merged(self, temp_project):
        """Test that defaults are merged into spec."""
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("iceberg.analytics.daily_clicks")
        assert spec.execution.dialect == "trino"
        assert spec.execution.timeout_seconds == 300

    def test_discover_empty_directory(self, tmp_path):
        """Test discovering from empty directory."""
        config_data = {
            "version": "1",
            "project": {"name": "empty"},
            "discovery": {"datasets_dir": "datasets"},
        }
        (tmp_path / "dli.yaml").write_text(yaml.dump(config_data))

        config = load_project(tmp_path)
        discovery = DatasetDiscovery(config)

        specs = list(discovery.discover_all())
        assert len(specs) == 0


class TestGetDliHome:
    """Tests for get_dli_home function."""

    def test_returns_cwd_by_default(self, monkeypatch):
        """Test that get_dli_home returns cwd when DLI_HOME is not set."""
        monkeypatch.delenv("DLI_HOME", raising=False)
        result = get_dli_home()
        assert result == Path.cwd()

    def test_returns_env_var(self, monkeypatch, tmp_path):
        """Test that get_dli_home returns DLI_HOME environment variable."""
        monkeypatch.setenv("DLI_HOME", str(tmp_path))
        result = get_dli_home()
        assert result == tmp_path


class TestSpecDiscovery:
    """Tests for SpecDiscovery class."""

    def test_discover_all_from_fixture(self, sample_project_path):
        """Test discovering all specs (metrics and datasets) from fixture."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        specs = list(discovery.discover_all())
        # Should include both metrics and datasets
        assert len(specs) >= 4  # 2 metrics + at least 2 datasets

        types = {spec.type for spec in specs}
        assert SpecType.METRIC in types
        assert SpecType.DATASET in types

    def test_discover_metrics_from_fixture(self, sample_project_path):
        """Test discovering metric specs from fixture."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        metrics = list(discovery.discover_metrics())
        assert len(metrics) == 3  # user_engagement, revenue_summary, and user_summary

        names = {m.name for m in metrics}
        assert "iceberg.analytics.user_engagement" in names
        assert "iceberg.analytics.revenue_summary" in names
        assert "iceberg.reporting.user_summary" in names

        # Verify all are metrics
        for metric in metrics:
            assert metric.type == SpecType.METRIC
            assert metric.query_type == QueryType.SELECT

    def test_discover_datasets_from_fixture(self, sample_project_path):
        """Test discovering dataset specs from fixture."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        datasets = list(discovery.discover_datasets())
        # Should find datasets from both new patterns and legacy patterns
        assert len(datasets) >= 2

        # Verify all are datasets
        for dataset in datasets:
            assert dataset.type == SpecType.DATASET
            assert dataset.query_type == QueryType.DML

    def test_find_metric(self, sample_project_path):
        """Test finding a specific metric by name."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        metric = discovery.find_metric("iceberg.analytics.user_engagement")
        assert metric is not None
        assert metric.name == "iceberg.analytics.user_engagement"
        assert metric.type == SpecType.METRIC
        assert len(metric.metrics) > 0  # Has metric definitions

    def test_find_metric_not_found(self, sample_project_path):
        """Test finding non-existent metric."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        metric = discovery.find_metric("nonexistent.metric.name")
        assert metric is None

    def test_find_dataset(self, sample_project_path):
        """Test finding a specific dataset by name."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        dataset = discovery.find_dataset("iceberg.analytics.daily_clicks")
        assert dataset is not None
        assert dataset.name == "iceberg.analytics.daily_clicks"
        assert dataset.type == SpecType.DATASET

    def test_find_dataset_not_found(self, sample_project_path):
        """Test finding non-existent dataset."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        dataset = discovery.find_dataset("nonexistent.dataset.name")
        assert dataset is None

    def test_find_spec_returns_metric_or_dataset(self, sample_project_path):
        """Test find_spec returns either metric or dataset."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        # Find a metric
        metric = discovery.find_spec("iceberg.analytics.user_engagement")
        assert metric is not None
        assert metric.type == SpecType.METRIC

        # Find a dataset
        dataset = discovery.find_spec("iceberg.analytics.daily_clicks")
        assert dataset is not None
        assert dataset.type == SpecType.DATASET

    def test_deduplication(self, tmp_path):
        """Test that specs are not yielded twice even if matched by multiple patterns."""
        # Create a project where a file could match multiple patterns
        config_data = {
            "version": "1",
            "project": {"name": "test"},
            "discovery": {
                "datasets_dir": "datasets",
                "dataset_patterns": ["dataset.*.yaml", "*.dataset.yaml"],
            },
        }
        (tmp_path / "dli.yaml").write_text(yaml.dump(config_data))

        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()

        # Create a dataset spec
        spec_data = {
            "name": "test.test.test",
            "owner": "owner@example.com",
            "team": "@team",
            "type": "Dataset",
            "query_type": "DML",
            "query_statement": "INSERT INTO t SELECT 1",
        }
        (datasets_dir / "dataset.test.test.test.yaml").write_text(yaml.dump(spec_data))

        config = load_project(tmp_path)
        discovery = SpecDiscovery(config)

        datasets = list(discovery.discover_datasets())
        names = [d.name for d in datasets]

        # Should only appear once even if it matches the pattern
        assert names.count("test.test.test") == 1

    def test_metrics_merged_from_project_defaults(self, sample_project_path):
        """Test that project defaults are merged into metric specs."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        metric = discovery.find_metric("iceberg.analytics.user_engagement")
        assert metric is not None
        # Default dialect should be merged from project config
        assert metric.execution.dialect == "trino"

    def test_discover_sql_files(self, sample_project_path):
        """Test discovering SQL files."""
        config = load_project(sample_project_path)
        discovery = SpecDiscovery(config)

        sql_files = list(discovery.discover_sql_files())
        assert len(sql_files) >= 1

        # All should be .sql files
        for sql_file in sql_files:
            assert sql_file.suffix == ".sql"
