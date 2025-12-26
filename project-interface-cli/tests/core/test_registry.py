"""Tests for the DLI Core Engine registry module."""

from pathlib import Path

import pytest
import yaml

from dli.core.discovery import load_project
from dli.core.registry import DatasetRegistry


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

    # First spec
    spec1 = {
        "name": "iceberg.analytics.daily_clicks",
        "owner": "henry@example.com",
        "team": "@analytics",
        "domains": ["feed", "engagement"],
        "tags": ["daily", "kpi"],
        "query_type": "DML",
        "query_statement": "INSERT INTO t SELECT 1",
    }
    (datasets_dir / "spec.iceberg.analytics.daily_clicks.yaml").write_text(
        yaml.dump(spec1)
    )

    # Second spec
    spec2 = {
        "name": "iceberg.reporting.user_summary",
        "owner": "analyst@example.com",
        "team": "@reporting",
        "domains": ["reporting"],
        "tags": ["report", "user"],
        "query_type": "SELECT",
        "query_statement": "SELECT 1",
    }
    (datasets_dir / "spec.iceberg.reporting.user_summary.yaml").write_text(
        yaml.dump(spec2)
    )

    # Third spec
    spec3 = {
        "name": "iceberg.analytics.weekly_summary",
        "owner": "henry@example.com",
        "team": "@analytics",
        "domains": ["feed"],
        "tags": ["weekly", "kpi"],
        "query_type": "DML",
        "query_statement": "INSERT INTO t SELECT 1",
    }
    (datasets_dir / "spec.iceberg.analytics.weekly_summary.yaml").write_text(
        yaml.dump(spec3)
    )

    return tmp_path


class TestDatasetRegistry:
    """Tests for DatasetRegistry class."""

    def test_load_datasets(self, temp_project):
        """Test loading datasets from project."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)
        assert len(registry) == 3

    def test_list_all(self, temp_project):
        """Test listing all datasets."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        specs = registry.list_all()
        assert len(specs) == 3

        names = {s.name for s in specs}
        assert "iceberg.analytics.daily_clicks" in names
        assert "iceberg.reporting.user_summary" in names
        assert "iceberg.analytics.weekly_summary" in names

    def test_get_dataset(self, temp_project):
        """Test getting dataset by name."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        spec = registry.get("iceberg.analytics.daily_clicks")
        assert spec is not None
        assert spec.name == "iceberg.analytics.daily_clicks"
        assert spec.owner == "henry@example.com"

    def test_get_nonexistent(self, temp_project):
        """Test getting non-existent dataset."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)
        assert registry.get("nonexistent") is None

    def test_search_by_tag(self, temp_project):
        """Test searching by tag."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        # Search for 'kpi' tag
        results = registry.search(tag="kpi")
        assert len(results) == 2

        names = {s.name for s in results}
        assert "iceberg.analytics.daily_clicks" in names
        assert "iceberg.analytics.weekly_summary" in names

    def test_search_by_domain(self, temp_project):
        """Test searching by domain."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        # Search for 'feed' domain
        results = registry.search(domain="feed")
        assert len(results) == 2

        # Search for 'reporting' domain
        results = registry.search(domain="reporting")
        assert len(results) == 1
        assert results[0].name == "iceberg.reporting.user_summary"

    def test_search_by_owner(self, temp_project):
        """Test searching by owner."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.search(owner="henry@example.com")
        assert len(results) == 2

        results = registry.search(owner="analyst@example.com")
        assert len(results) == 1

    def test_search_by_team(self, temp_project):
        """Test searching by team."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.search(team="@analytics")
        assert len(results) == 2

        results = registry.search(team="@reporting")
        assert len(results) == 1

    def test_search_by_catalog(self, temp_project):
        """Test searching by catalog."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.search(catalog="iceberg")
        assert len(results) == 3

    def test_search_by_schema(self, temp_project):
        """Test searching by schema."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.search(schema="analytics")
        assert len(results) == 2

        results = registry.search(schema="reporting")
        assert len(results) == 1

    def test_search_combined(self, temp_project):
        """Test searching with multiple filters."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.search(domain="feed", tag="daily")
        assert len(results) == 1
        assert results[0].name == "iceberg.analytics.daily_clicks"

    def test_get_by_catalog(self, temp_project):
        """Test getting datasets by catalog."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.get_by_catalog("iceberg")
        assert len(results) == 3

    def test_get_by_schema(self, temp_project):
        """Test getting datasets by schema."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.get_by_schema("iceberg", "analytics")
        assert len(results) == 2

    def test_get_by_domain(self, temp_project):
        """Test getting datasets by domain."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.get_by_domain("engagement")
        assert len(results) == 1
        assert results[0].name == "iceberg.analytics.daily_clicks"

    def test_get_by_tag(self, temp_project):
        """Test getting datasets by tag."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        results = registry.get_by_tag("weekly")
        assert len(results) == 1
        assert results[0].name == "iceberg.analytics.weekly_summary"

    def test_get_catalogs(self, temp_project):
        """Test getting unique catalogs."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        catalogs = registry.get_catalogs()
        assert catalogs == ["iceberg"]

    def test_get_schemas(self, temp_project):
        """Test getting unique schemas."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        schemas = registry.get_schemas()
        assert "analytics" in schemas
        assert "reporting" in schemas

    def test_get_schemas_filtered(self, temp_project):
        """Test getting schemas filtered by catalog."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        schemas = registry.get_schemas(catalog="iceberg")
        assert "analytics" in schemas
        assert "reporting" in schemas

    def test_get_domains(self, temp_project):
        """Test getting unique domains."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        domains = registry.get_domains()
        assert "feed" in domains
        assert "engagement" in domains
        assert "reporting" in domains

    def test_get_tags(self, temp_project):
        """Test getting unique tags."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        tags = registry.get_tags()
        assert "daily" in tags
        assert "weekly" in tags
        assert "kpi" in tags
        assert "report" in tags
        assert "user" in tags

    def test_get_owners(self, temp_project):
        """Test getting unique owners."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        owners = registry.get_owners()
        assert "henry@example.com" in owners
        assert "analyst@example.com" in owners

    def test_get_teams(self, temp_project):
        """Test getting unique teams."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        teams = registry.get_teams()
        assert "@analytics" in teams
        assert "@reporting" in teams

    def test_reload(self, temp_project):
        """Test reloading registry."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)
        assert len(registry) == 3

        # Add a new spec
        new_spec = {
            "name": "iceberg.new.dataset",
            "owner": "new@example.com",
            "team": "@new",
            "query_type": "SELECT",
            "query_statement": "SELECT 1",
        }
        (temp_project / "datasets" / "spec.iceberg.new.dataset.yaml").write_text(
            yaml.dump(new_spec)
        )

        # Reload
        registry.reload()
        assert len(registry) == 4
        assert registry.get("iceberg.new.dataset") is not None

    def test_contains(self, temp_project):
        """Test 'in' operator."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        assert "iceberg.analytics.daily_clicks" in registry
        assert "nonexistent" not in registry

    def test_iter(self, temp_project):
        """Test iteration."""
        config = load_project(temp_project)
        registry = DatasetRegistry(config)

        specs = list(registry)
        assert len(specs) == 3

    def test_from_fixture(self, sample_project_path):
        """Test loading from fixture project."""
        config = load_project(sample_project_path)
        registry = DatasetRegistry(config)

        assert len(registry) == 2
        assert "iceberg.analytics.daily_clicks" in registry
        assert "iceberg.reporting.user_summary" in registry
