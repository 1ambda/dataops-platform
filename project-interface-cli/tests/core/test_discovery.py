"""Tests for the DLI Core Engine discovery module."""

from pathlib import Path

import pytest
import yaml

from dli.core.discovery import (
    DatasetDiscovery,
    ProjectConfig,
    get_dli_home,
    load_project,
)
from dli.core.models import QueryType


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

    # Spec file
    spec = {
        "name": "iceberg.analytics.daily_clicks",
        "owner": "henry@example.com",
        "team": "@analytics",
        "domains": ["feed"],
        "query_type": "DML",
        "query_file": "daily_clicks.sql",
    }
    (datasets_dir / "spec.iceberg.analytics.daily_clicks.yaml").write_text(
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

    def test_spec_patterns(self, temp_project):
        """Test spec patterns."""
        config = load_project(temp_project)
        patterns = config.spec_patterns
        assert "spec.*.yaml" in patterns

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
        """Test discovering datasets from fixture."""
        config = load_project(sample_project_path)
        discovery = DatasetDiscovery(config)

        specs = list(discovery.discover_all())
        assert len(specs) == 2  # daily_clicks and user_summary

        names = {s.name for s in specs}
        assert "iceberg.analytics.daily_clicks" in names
        assert "iceberg.reporting.user_summary" in names

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
