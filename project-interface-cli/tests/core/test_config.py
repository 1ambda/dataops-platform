"""Tests for core config module."""

from __future__ import annotations

from pathlib import Path

import pytest

from dli.core.config import ProjectConfig, get_dli_home, load_project


# =============================================================================
# Test: get_dli_home
# =============================================================================


class TestGetDliHome:
    """Tests for get_dli_home function."""

    def test_default_home_uses_cwd(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test default DLI home is current working directory."""
        monkeypatch.delenv("DLI_HOME", raising=False)
        home = get_dli_home()
        assert home == Path.cwd()

    def test_custom_home_from_env(
        self, monkeypatch: pytest.MonkeyPatch, tmp_path: Path
    ) -> None:
        """Test custom DLI home from environment variable."""
        custom_home = tmp_path / "custom_dli"
        monkeypatch.setenv("DLI_HOME", str(custom_home))
        home = get_dli_home()
        assert home == custom_home

    def test_env_path_does_not_need_to_exist(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test that DLI_HOME path doesn't need to exist."""
        nonexistent = "/some/nonexistent/path/for/testing"
        monkeypatch.setenv("DLI_HOME", nonexistent)
        home = get_dli_home()
        assert home == Path(nonexistent)


# =============================================================================
# Test: load_project
# =============================================================================


class TestLoadProject:
    """Tests for load_project function."""

    def test_load_valid_project(self, sample_project_path: Path) -> None:
        """Test loading a valid project configuration."""
        config = load_project(sample_project_path)
        assert config is not None
        assert isinstance(config, ProjectConfig)

    def test_load_uses_dli_home_when_no_path(
        self, monkeypatch: pytest.MonkeyPatch, sample_project_path: Path
    ) -> None:
        """Test load_project uses DLI_HOME when no path is provided."""
        monkeypatch.setenv("DLI_HOME", str(sample_project_path))
        config = load_project()
        assert config is not None
        assert config.project_name == "test-project"

    def test_load_missing_config_raises_error(self, tmp_path: Path) -> None:
        """Test loading from directory without dli.yaml raises FileNotFoundError."""
        with pytest.raises(FileNotFoundError, match="dli.yaml not found"):
            load_project(tmp_path)

    def test_load_from_file_path_uses_parent(
        self, sample_project_path: Path
    ) -> None:
        """Test that config_path's parent is used as root_dir."""
        config = load_project(sample_project_path)
        assert config.root_dir == sample_project_path


# =============================================================================
# Test: ProjectConfig Basic Properties
# =============================================================================


class TestProjectConfigBasicProperties:
    """Tests for basic ProjectConfig properties."""

    def test_version(self, sample_project_path: Path) -> None:
        """Test version property."""
        config = load_project(sample_project_path)
        assert config.version == "1"

    def test_project_name(self, sample_project_path: Path) -> None:
        """Test project_name property."""
        config = load_project(sample_project_path)
        assert config.project_name == "test-project"

    def test_project_description(self, sample_project_path: Path) -> None:
        """Test project_description property."""
        config = load_project(sample_project_path)
        assert config.project_description == "Test Project for DLI Core Engine"

    def test_root_dir(self, sample_project_path: Path) -> None:
        """Test root_dir is set correctly."""
        config = load_project(sample_project_path)
        assert config.root_dir == sample_project_path

    def test_config_path(self, sample_project_path: Path) -> None:
        """Test config_path is set correctly."""
        config = load_project(sample_project_path)
        assert config.config_path == sample_project_path / "dli.yaml"


# =============================================================================
# Test: ProjectConfig Discovery Properties
# =============================================================================


class TestProjectConfigDiscovery:
    """Tests for ProjectConfig discovery properties."""

    def test_datasets_dir(self, sample_project_path: Path) -> None:
        """Test datasets_dir property."""
        config = load_project(sample_project_path)
        assert config.datasets_dir == sample_project_path / "datasets"

    def test_metrics_dir(self, sample_project_path: Path) -> None:
        """Test metrics_dir property."""
        config = load_project(sample_project_path)
        assert config.metrics_dir == sample_project_path / "metrics"

    def test_metric_patterns(self, sample_project_path: Path) -> None:
        """Test metric_patterns property."""
        config = load_project(sample_project_path)
        assert "metric.*.yaml" in config.metric_patterns
        assert "metric.yaml" in config.metric_patterns

    def test_dataset_patterns(self, sample_project_path: Path) -> None:
        """Test dataset_patterns property."""
        config = load_project(sample_project_path)
        assert "dataset.*.yaml" in config.dataset_patterns
        assert "dataset.yaml" in config.dataset_patterns

    def test_sql_patterns_default(self, sample_project_path: Path) -> None:
        """Test sql_patterns returns default when not specified."""
        config = load_project(sample_project_path)
        assert "*.sql" in config.sql_patterns


# =============================================================================
# Test: ProjectConfig Defaults Properties
# =============================================================================


class TestProjectConfigDefaults:
    """Tests for ProjectConfig default settings."""

    def test_defaults_dict(self, sample_project_path: Path) -> None:
        """Test defaults returns dictionary."""
        config = load_project(sample_project_path)
        assert isinstance(config.defaults, dict)

    def test_default_dialect(self, sample_project_path: Path) -> None:
        """Test default_dialect property."""
        config = load_project(sample_project_path)
        assert config.default_dialect == "trino"

    def test_default_timeout(self, sample_project_path: Path) -> None:
        """Test default_timeout property."""
        config = load_project(sample_project_path)
        assert config.default_timeout == 3600

    def test_default_retry_count(self, sample_project_path: Path) -> None:
        """Test default_retry_count property."""
        config = load_project(sample_project_path)
        assert config.default_retry_count == 2


# =============================================================================
# Test: ProjectConfig Environment Methods
# =============================================================================


class TestProjectConfigEnvironments:
    """Tests for ProjectConfig environment methods."""

    def test_get_environment_dev(self, sample_project_path: Path) -> None:
        """Test getting dev environment configuration."""
        config = load_project(sample_project_path)
        dev_env = config.get_environment("dev")
        assert dev_env is not None
        assert "connection_string" in dev_env

    def test_get_environment_prod(self, sample_project_path: Path) -> None:
        """Test getting prod environment configuration."""
        config = load_project(sample_project_path)
        prod_env = config.get_environment("prod")
        assert prod_env is not None
        assert "connection_string" in prod_env

    def test_get_environment_nonexistent(self, sample_project_path: Path) -> None:
        """Test getting nonexistent environment returns empty dict."""
        config = load_project(sample_project_path)
        unknown_env = config.get_environment("nonexistent")
        assert unknown_env == {}

    def test_get_connection_string_dev(self, sample_project_path: Path) -> None:
        """Test getting connection string for dev environment."""
        config = load_project(sample_project_path)
        conn_str = config.get_connection_string("dev")
        assert conn_str == "trino://localhost:8080/iceberg"

    def test_get_connection_string_prod(self, sample_project_path: Path) -> None:
        """Test getting connection string for prod environment."""
        config = load_project(sample_project_path)
        conn_str = config.get_connection_string("prod")
        assert conn_str == "trino://trino-prod:8080/iceberg"

    def test_get_connection_string_nonexistent(
        self, sample_project_path: Path
    ) -> None:
        """Test getting connection string for nonexistent environment returns None."""
        config = load_project(sample_project_path)
        conn_str = config.get_connection_string("nonexistent")
        assert conn_str is None


# =============================================================================
# Test: ProjectConfig Server Properties
# =============================================================================


class TestProjectConfigServer:
    """Tests for ProjectConfig server properties."""

    def test_server_url(self, sample_project_path: Path) -> None:
        """Test server_url property."""
        config = load_project(sample_project_path)
        assert config.server_url == "http://localhost:8081"

    def test_server_timeout(self, sample_project_path: Path) -> None:
        """Test server_timeout property."""
        config = load_project(sample_project_path)
        assert config.server_timeout == 30

    def test_server_api_key_not_set(self, sample_project_path: Path) -> None:
        """Test server_api_key returns None when not configured."""
        config = load_project(sample_project_path)
        # In the sample project, api_key is commented out
        assert config.server_api_key is None


# =============================================================================
# Test: ProjectConfig Defaults with Missing Values
# =============================================================================


class TestProjectConfigMissingValues:
    """Tests for ProjectConfig with missing/default values."""

    def test_minimal_config(self, tmp_path: Path) -> None:
        """Test loading minimal configuration uses defaults."""
        # Create minimal dli.yaml
        config_file = tmp_path / "dli.yaml"
        config_file.write_text("version: '1'\n")

        config = load_project(tmp_path)

        # Check defaults are applied
        assert config.version == "1"
        assert config.project_name == "unnamed"
        assert config.project_description == ""
        assert config.default_dialect == "trino"
        assert config.default_timeout == 3600
        assert config.default_retry_count == 2
        assert config.server_timeout == 30
        assert config.server_url is None
        assert config.server_api_key is None

    def test_empty_config(self, tmp_path: Path) -> None:
        """Test loading empty configuration file."""
        config_file = tmp_path / "dli.yaml"
        config_file.write_text("")

        config = load_project(tmp_path)

        # All defaults should be used
        assert config.version == "1"
        assert config.project_name == "unnamed"

    def test_datasets_dir_default(self, tmp_path: Path) -> None:
        """Test datasets_dir defaults to 'datasets'."""
        config_file = tmp_path / "dli.yaml"
        config_file.write_text("version: '1'\n")

        config = load_project(tmp_path)
        assert config.datasets_dir == tmp_path / "datasets"

    def test_metrics_dir_default(self, tmp_path: Path) -> None:
        """Test metrics_dir defaults to 'metrics'."""
        config_file = tmp_path / "dli.yaml"
        config_file.write_text("version: '1'\n")

        config = load_project(tmp_path)
        assert config.metrics_dir == tmp_path / "metrics"
