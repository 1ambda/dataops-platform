"""Tests for dli.core.config_loader module.

Covers:
- Hierarchical config loading (global < project < local)
- Template resolution: ${VAR}, ${VAR:-default}, ${VAR:?error}
- Source tracking per key
- Deep merge behavior
- Error handling for missing required vars
- .dli.local.yaml gitignore check
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import pytest

# These imports will work once the module is implemented
# For now, they document the expected interface
try:
    from dli.core.config_loader import ConfigLoader
    from dli.models.config import ConfigSource
except ImportError:
    # Placeholder for pre-implementation testing
    ConfigLoader = None  # type: ignore[misc, assignment]
    ConfigSource = None  # type: ignore[misc, assignment]


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def global_config_dir(tmp_path: Path) -> Path:
    """Create a mock ~/.dli directory."""
    dli_dir = tmp_path / ".dli"
    dli_dir.mkdir()
    return dli_dir


@pytest.fixture
def global_config_file(global_config_dir: Path) -> Path:
    """Create global config file."""
    config_file = global_config_dir / "config.yaml"
    config_file.write_text(
        """version: "1"

server:
  url: "https://global.basecamp.io"
  timeout: 60

defaults:
  dialect: "trino"
  timeout_seconds: 300
"""
    )
    return config_file


@pytest.fixture
def project_dir(tmp_path: Path) -> Path:
    """Create a project directory."""
    project = tmp_path / "my_project"
    project.mkdir()
    return project


@pytest.fixture
def project_config_file(project_dir: Path) -> Path:
    """Create project config file."""
    config_file = project_dir / "dli.yaml"
    config_file.write_text(
        """version: "1"

project:
  name: "my-project"
  description: "Test project"

server:
  url: "https://project.basecamp.io"

defaults:
  dialect: "bigquery"
  timeout_seconds: 600
"""
    )
    return config_file


@pytest.fixture
def local_config_file(project_dir: Path) -> Path:
    """Create local config file."""
    config_file = project_dir / ".dli.local.yaml"
    config_file.write_text(
        """server:
  url: "http://localhost:8081"

defaults:
  timeout_seconds: 60

active_environment: "dev"
"""
    )
    return config_file


@pytest.fixture
def config_with_templates(project_dir: Path) -> Path:
    """Create config file with template variables."""
    config_file = project_dir / "dli.yaml"
    config_file.write_text(
        """version: "1"

server:
  url: "${DLI_SERVER_URL:-https://default.basecamp.io}"
  api_key: "${DLI_API_KEY}"
  timeout: ${DLI_TIMEOUT:-30}

database:
  password: "${DLI_SECRET_DB_PASSWORD:?Database password required}"
"""
    )
    return config_file


@pytest.fixture
def config_with_environments(project_dir: Path) -> Path:
    """Create config file with named environments."""
    config_file = project_dir / "dli.yaml"
    config_file.write_text(
        """version: "1"

environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
    catalog: "dev_catalog"
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
    catalog: "staging_catalog"
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
    catalog: "prod_catalog"
"""
    )
    return config_file


# =============================================================================
# ConfigLoader Initialization Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderInit:
    """Tests for ConfigLoader initialization."""

    def test_init_with_project_path(self, project_dir: Path) -> None:
        """Test initialization with project path."""
        loader = ConfigLoader(project_path=project_dir)

        assert loader.project_path == project_dir

    def test_init_load_global_default_true(self, project_dir: Path) -> None:
        """Test that load_global defaults to True."""
        loader = ConfigLoader(project_path=project_dir)

        assert loader._load_global is True

    def test_init_load_local_default_true(self, project_dir: Path) -> None:
        """Test that load_local defaults to True."""
        loader = ConfigLoader(project_path=project_dir)

        assert loader._load_local is True

    def test_init_disable_global(self, project_dir: Path) -> None:
        """Test disabling global config loading."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        assert loader._load_global is False

    def test_init_disable_local(self, project_dir: Path) -> None:
        """Test disabling local config loading."""
        loader = ConfigLoader(project_path=project_dir, load_local=False)

        assert loader._load_local is False


# =============================================================================
# Layer Loading Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderLayerLoading:
    """Tests for individual layer loading."""

    def test_load_empty_project(self, project_dir: Path) -> None:
        """Test loading when no config files exist."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        config, sources = loader.load()

        assert config == {}
        assert sources == {}

    def test_load_project_config_only(
        self, project_dir: Path, project_config_file: Path
    ) -> None:
        """Test loading project config only."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        config, sources = loader.load()

        assert config["project"]["name"] == "my-project"
        assert config["server"]["url"] == "https://project.basecamp.io"
        assert sources["server.url"] == ConfigSource.PROJECT

    def test_load_local_config_only(
        self, project_dir: Path, local_config_file: Path
    ) -> None:
        """Test loading local config only."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=True
        )

        config, sources = loader.load()

        assert config["server"]["url"] == "http://localhost:8081"
        assert config["active_environment"] == "dev"
        assert sources["server.url"] == ConfigSource.LOCAL

    def test_load_global_config_only(
        self,
        project_dir: Path,
        global_config_dir: Path,
        global_config_file: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Test loading global config only."""
        # Mock home directory to use our test global config
        monkeypatch.setenv("HOME", str(global_config_dir.parent))

        loader = ConfigLoader(
            project_path=project_dir, load_global=True, load_local=False
        )

        config, sources = loader.load()

        assert config["server"]["url"] == "https://global.basecamp.io"
        assert sources["server.url"] == ConfigSource.GLOBAL


# =============================================================================
# Priority/Merge Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderPriority:
    """Tests for configuration priority/merging."""

    def test_local_overrides_project(
        self, project_dir: Path, project_config_file: Path, local_config_file: Path
    ) -> None:
        """Test that local config overrides project config."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=True
        )

        config, sources = loader.load()

        # Local should override project
        assert config["server"]["url"] == "http://localhost:8081"
        assert sources["server.url"] == ConfigSource.LOCAL

        # Project values should remain for non-overridden keys
        assert config["project"]["name"] == "my-project"
        assert sources["project.name"] == ConfigSource.PROJECT

    def test_project_overrides_global(
        self,
        project_dir: Path,
        global_config_dir: Path,
        global_config_file: Path,
        project_config_file: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Test that project config overrides global config."""
        monkeypatch.setenv("HOME", str(global_config_dir.parent))

        loader = ConfigLoader(
            project_path=project_dir, load_global=True, load_local=False
        )

        config, sources = loader.load()

        # Project should override global
        assert config["server"]["url"] == "https://project.basecamp.io"
        assert sources["server.url"] == ConfigSource.PROJECT

        # Global timeout should be overridden by project
        assert config["defaults"]["timeout_seconds"] == 600
        assert sources["defaults.timeout_seconds"] == ConfigSource.PROJECT

    def test_full_layer_priority(
        self,
        project_dir: Path,
        global_config_dir: Path,
        global_config_file: Path,
        project_config_file: Path,
        local_config_file: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Test full priority: local > project > global."""
        monkeypatch.setenv("HOME", str(global_config_dir.parent))

        loader = ConfigLoader(
            project_path=project_dir, load_global=True, load_local=True
        )

        config, sources = loader.load()

        # Local wins
        assert config["server"]["url"] == "http://localhost:8081"
        assert sources["server.url"] == ConfigSource.LOCAL

        # Project value preserved (not in local)
        assert config["defaults"]["dialect"] == "bigquery"
        assert sources["defaults.dialect"] == ConfigSource.PROJECT

        # Global version preserved (not overridden)
        assert config["version"] == "1"

    def test_env_var_highest_priority(
        self,
        project_dir: Path,
        config_with_templates: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Test that environment variables have highest priority."""
        monkeypatch.setenv("DLI_SERVER_URL", "http://env-override:8080")
        monkeypatch.setenv("DLI_API_KEY", "test-key")
        monkeypatch.setenv("DLI_SECRET_DB_PASSWORD", "secret123")

        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        config, sources = loader.load()

        # Env var overrides template default
        assert config["server"]["url"] == "http://env-override:8080"
        assert sources["server.url"] == ConfigSource.ENV_VAR


# =============================================================================
# Deep Merge Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderDeepMerge:
    """Tests for deep merge behavior."""

    def test_deep_merge_nested_dicts(self, project_dir: Path) -> None:
        """Test that nested dicts are merged, not replaced."""
        # Create base config
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "https://base.com"
  timeout: 30
  retry:
    count: 3
    delay: 1
"""
        )

        # Create local override
        (project_dir / ".dli.local.yaml").write_text(
            """server:
  url: "http://localhost"
  retry:
    count: 5
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        # URL should be overridden
        assert config["server"]["url"] == "http://localhost"
        # Timeout should be preserved from base
        assert config["server"]["timeout"] == 30
        # Retry count should be overridden
        assert config["server"]["retry"]["count"] == 5
        # Retry delay should be preserved
        assert config["server"]["retry"]["delay"] == 1

    def test_deep_merge_list_replacement(self, project_dir: Path) -> None:
        """Test that lists are replaced, not merged."""
        (project_dir / "dli.yaml").write_text(
            """patterns:
  - "*.yaml"
  - "*.yml"
"""
        )

        (project_dir / ".dli.local.yaml").write_text(
            """patterns:
  - "override.yaml"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        # Lists should be replaced entirely
        assert config["patterns"] == ["override.yaml"]

    def test_deep_merge_preserves_all_levels(self, project_dir: Path) -> None:
        """Test deep merge preserves values at all nesting levels."""
        (project_dir / "dli.yaml").write_text(
            """level1:
  level2:
    level3:
      value_a: "a"
      value_b: "b"
    other: "x"
"""
        )

        (project_dir / ".dli.local.yaml").write_text(
            """level1:
  level2:
    level3:
      value_a: "override"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        assert config["level1"]["level2"]["level3"]["value_a"] == "override"
        assert config["level1"]["level2"]["level3"]["value_b"] == "b"
        assert config["level1"]["level2"]["other"] == "x"


# =============================================================================
# Template Resolution Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderTemplateBasic:
    """Tests for basic template variable resolution."""

    def test_template_simple_var(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test simple ${VAR} template."""
        (project_dir / "dli.yaml").write_text(
            """server:
  api_key: "${DLI_API_KEY}"
"""
        )
        monkeypatch.setenv("DLI_API_KEY", "my-secret-key")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["api_key"] == "my-secret-key"

    def test_template_var_not_set_error(self, project_dir: Path) -> None:
        """Test ${VAR} raises error when not set."""
        (project_dir / "dli.yaml").write_text(
            """server:
  api_key: "${DLI_API_KEY}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        from dli.exceptions import ConfigurationError

        with pytest.raises(ConfigurationError) as exc_info:
            loader.load()

        assert "DLI_API_KEY" in str(exc_info.value)

    def test_template_multiple_vars_in_string(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test multiple variables in one string."""
        (project_dir / "dli.yaml").write_text(
            """connection:
  url: "${DB_HOST}:${DB_PORT}"
"""
        )
        monkeypatch.setenv("DB_HOST", "localhost")
        monkeypatch.setenv("DB_PORT", "5432")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["connection"]["url"] == "localhost:5432"


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderTemplateDefault:
    """Tests for ${VAR:-default} template syntax."""

    def test_template_with_default_uses_default(self, project_dir: Path) -> None:
        """Test ${VAR:-default} uses default when var not set."""
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "${DLI_SERVER_URL:-http://localhost:8081}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["url"] == "http://localhost:8081"

    def test_template_with_default_uses_env_var(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test ${VAR:-default} uses env var when set."""
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "${DLI_SERVER_URL:-http://localhost:8081}"
"""
        )
        monkeypatch.setenv("DLI_SERVER_URL", "https://prod.basecamp.io")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["url"] == "https://prod.basecamp.io"

    def test_template_default_empty_string(self, project_dir: Path) -> None:
        """Test ${VAR:-} with empty default."""
        (project_dir / "dli.yaml").write_text(
            """optional:
  value: "${OPTIONAL_VAR:-}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["optional"]["value"] == ""

    def test_template_default_with_special_chars(self, project_dir: Path) -> None:
        """Test default with special characters."""
        (project_dir / "dli.yaml").write_text(
            """connection:
  url: "${DB_URL:-postgres://user:pass@localhost/db}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["connection"]["url"] == "postgres://user:pass@localhost/db"

    def test_template_default_numeric(self, project_dir: Path) -> None:
        """Test numeric default value."""
        (project_dir / "dli.yaml").write_text(
            """server:
  timeout: ${DLI_TIMEOUT:-30}
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        # Note: YAML parsing may interpret as int or string
        assert config["server"]["timeout"] in [30, "30"]


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderTemplateError:
    """Tests for ${VAR:?error} template syntax."""

    def test_template_error_message_when_missing(self, project_dir: Path) -> None:
        """Test ${VAR:?error} shows custom error message."""
        (project_dir / "dli.yaml").write_text(
            """database:
  password: "${DB_PASSWORD:?Database password is required}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        from dli.exceptions import ConfigurationError

        with pytest.raises(ConfigurationError) as exc_info:
            loader.load()

        assert "Database password is required" in str(exc_info.value)

    def test_template_error_syntax_with_env_var(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test ${VAR:?error} works when var is set."""
        (project_dir / "dli.yaml").write_text(
            """database:
  password: "${DB_PASSWORD:?Database password is required}"
"""
        )
        monkeypatch.setenv("DB_PASSWORD", "secret123")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["database"]["password"] == "secret123"


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderTemplateEdgeCases:
    """Tests for template edge cases."""

    def test_no_template_passthrough(self, project_dir: Path) -> None:
        """Test strings without templates pass through unchanged."""
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "https://plain.basecamp.io"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["url"] == "https://plain.basecamp.io"

    def test_escaped_dollar_sign(self, project_dir: Path) -> None:
        """Test $$ escapes to literal $."""
        (project_dir / "dli.yaml").write_text(
            """message: "Price: $$100"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        # Depending on implementation, might be $100 or $$100
        assert "$" in config["message"]

    def test_template_in_list(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test template resolution in list items."""
        (project_dir / "dli.yaml").write_text(
            """hosts:
  - "${HOST_1:-host1.example.com}"
  - "${HOST_2:-host2.example.com}"
"""
        )
        monkeypatch.setenv("HOST_1", "custom.host.com")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["hosts"][0] == "custom.host.com"
        assert config["hosts"][1] == "host2.example.com"

    def test_template_partial_match(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test template with surrounding text."""
        (project_dir / "dli.yaml").write_text(
            """url: "https://${DOMAIN}/api/v1"
"""
        )
        monkeypatch.setenv("DOMAIN", "api.example.com")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["url"] == "https://api.example.com/api/v1"

    def test_template_case_sensitive(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test template variables are case-sensitive."""
        (project_dir / "dli.yaml").write_text(
            """value: "${MY_VAR}"
"""
        )
        monkeypatch.setenv("my_var", "lowercase")  # Wrong case

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        from dli.exceptions import ConfigurationError

        with pytest.raises(ConfigurationError):
            loader.load()

    def test_template_underscore_in_name(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test variable names with underscores."""
        (project_dir / "dli.yaml").write_text(
            """value: "${MY_COMPLEX_VAR_NAME}"
"""
        )
        monkeypatch.setenv("MY_COMPLEX_VAR_NAME", "works")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["value"] == "works"


# =============================================================================
# Source Tracking Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderSourceTracking:
    """Tests for configuration source tracking."""

    def test_source_tracking_project(
        self, project_dir: Path, project_config_file: Path
    ) -> None:
        """Test source tracking for project config."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        _, sources = loader.load()

        assert sources["server.url"] == ConfigSource.PROJECT
        assert sources["project.name"] == ConfigSource.PROJECT
        assert sources["defaults.dialect"] == ConfigSource.PROJECT

    def test_source_tracking_local(
        self, project_dir: Path, project_config_file: Path, local_config_file: Path
    ) -> None:
        """Test source tracking for local overrides."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        _, sources = loader.load()

        assert sources["server.url"] == ConfigSource.LOCAL
        assert sources["active_environment"] == ConfigSource.LOCAL
        # Non-overridden values keep original source
        assert sources["project.name"] == ConfigSource.PROJECT

    def test_source_tracking_env_var(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test source tracking for env var templates."""
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "${DLI_SERVER_URL}"
"""
        )
        monkeypatch.setenv("DLI_SERVER_URL", "http://test.com")

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        _, sources = loader.load()

        assert sources["server.url"] == ConfigSource.ENV_VAR

    def test_source_tracking_default_not_env(self, project_dir: Path) -> None:
        """Test source tracking uses PROJECT when default used (not env var)."""
        (project_dir / "dli.yaml").write_text(
            """server:
  url: "${DLI_SERVER_URL:-http://default.com}"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        _, sources = loader.load()

        # When using default, source should be PROJECT not ENV_VAR
        assert sources["server.url"] == ConfigSource.PROJECT

    def test_source_tracking_nested_keys(
        self, project_dir: Path, project_config_file: Path
    ) -> None:
        """Test source tracking for nested keys uses dot notation."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        _, sources = loader.load()

        assert "server" in sources
        assert "server.url" in sources
        assert "defaults.dialect" in sources


# =============================================================================
# YAML File Handling Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderYAMLHandling:
    """Tests for YAML file handling."""

    def test_invalid_yaml_syntax(self, project_dir: Path) -> None:
        """Test error on invalid YAML syntax."""
        (project_dir / "dli.yaml").write_text(
            """invalid: yaml: syntax
  - not: valid
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        from dli.exceptions import ConfigurationError

        with pytest.raises(ConfigurationError):
            loader.load()

    def test_empty_yaml_file(self, project_dir: Path) -> None:
        """Test handling of empty YAML file."""
        (project_dir / "dli.yaml").write_text("")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config == {}

    def test_yaml_with_comments(self, project_dir: Path) -> None:
        """Test YAML with comments is parsed correctly."""
        (project_dir / "dli.yaml").write_text(
            """# This is a comment
server:
  url: "https://test.com"  # inline comment
  # another comment
  timeout: 30
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["url"] == "https://test.com"
        assert config["server"]["timeout"] == 30

    def test_yaml_with_anchors(self, project_dir: Path) -> None:
        """Test YAML anchors and aliases work."""
        (project_dir / "dli.yaml").write_text(
            """defaults: &defaults
  timeout: 30
  retry: 3

server:
  <<: *defaults
  url: "https://test.com"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["server"]["url"] == "https://test.com"
        assert config["server"]["timeout"] == 30
        assert config["server"]["retry"] == 3

    def test_yaml_unicode_content(self, project_dir: Path) -> None:
        """Test YAML with unicode content."""
        (project_dir / "dli.yaml").write_text(
            """project:
  name: "프로젝트"
  description: "日本語テスト"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["project"]["name"] == "프로젝트"
        assert config["project"]["description"] == "日本語テスト"

    def test_missing_project_config_file(self, project_dir: Path) -> None:
        """Test loading when dli.yaml doesn't exist."""
        loader = ConfigLoader(
            project_path=project_dir, load_global=False, load_local=False
        )

        config, sources = loader.load()

        assert config == {}
        assert sources == {}

    def test_yaml_multiline_string(self, project_dir: Path) -> None:
        """Test YAML multiline string handling."""
        (project_dir / "dli.yaml").write_text(
            """query: |
  SELECT *
  FROM table
  WHERE condition = true
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert "SELECT *" in config["query"]
        assert "WHERE condition" in config["query"]


# =============================================================================
# Error Handling Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderErrorHandling:
    """Tests for error handling."""

    def test_permission_denied_file(self, project_dir: Path) -> None:
        """Test handling of permission denied error."""
        config_file = project_dir / "dli.yaml"
        config_file.write_text("server:\n  url: test")
        config_file.chmod(0o000)

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        try:
            from dli.exceptions import ConfigurationError

            with pytest.raises((ConfigurationError, PermissionError)):
                loader.load()
        finally:
            config_file.chmod(0o644)

    def test_directory_instead_of_file(self, project_dir: Path) -> None:
        """Test handling when config path is a directory."""
        (project_dir / "dli.yaml").mkdir()  # Create as directory

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        from dli.exceptions import ConfigurationError

        with pytest.raises((ConfigurationError, IsADirectoryError)):
            loader.load()

    def test_circular_include_prevention(self, project_dir: Path) -> None:
        """Test that circular includes are handled (if implemented)."""
        # This test documents expected behavior if includes are supported
        pass  # Placeholder - depends on implementation

    def test_invalid_template_syntax(self, project_dir: Path) -> None:
        """Test invalid template syntax handling."""
        (project_dir / "dli.yaml").write_text(
            """value: "${UNCLOSED"
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        # Invalid template should pass through as-is
        assert config["value"] == "${UNCLOSED"


# =============================================================================
# Environment Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderEnvironments:
    """Tests for named environment handling."""

    def test_load_environments_section(
        self, project_dir: Path, config_with_environments: Path
    ) -> None:
        """Test loading environments section."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        assert "environments" in config
        assert "dev" in config["environments"]
        assert "staging" in config["environments"]
        assert "prod" in config["environments"]

    def test_environment_config_structure(
        self, project_dir: Path, config_with_environments: Path
    ) -> None:
        """Test environment configuration structure."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        dev = config["environments"]["dev"]
        assert dev["server_url"] == "http://localhost:8081"
        assert dev["dialect"] == "duckdb"
        assert dev["catalog"] == "dev_catalog"

    def test_environment_templates_resolved(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test environment configs can use templates."""
        (project_dir / "dli.yaml").write_text(
            """environments:
  prod:
    api_key: "${PROD_API_KEY}"
"""
        )
        monkeypatch.setenv("PROD_API_KEY", "prod-secret")

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config, _ = loader.load()

        assert config["environments"]["prod"]["api_key"] == "prod-secret"


# =============================================================================
# Gitignore Check Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderGitignore:
    """Tests for .dli.local.yaml gitignore verification."""

    def test_warn_if_local_not_in_gitignore(
        self, project_dir: Path, local_config_file: Path
    ) -> None:
        """Test warning when .dli.local.yaml not in .gitignore."""
        # Create .gitignore without .dli.local.yaml
        (project_dir / ".gitignore").write_text("*.pyc\n__pycache__/\n")

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        import warnings

        with warnings.catch_warnings(record=True) as w:
            warnings.simplefilter("always")
            loader.load()

            # Should have warning about gitignore
            gitignore_warnings = [
                x for x in w if ".dli.local.yaml" in str(x.message)
            ]
            assert len(gitignore_warnings) >= 1

    def test_no_warn_if_local_in_gitignore(
        self, project_dir: Path, local_config_file: Path
    ) -> None:
        """Test no warning when .dli.local.yaml is in .gitignore."""
        (project_dir / ".gitignore").write_text(".dli.local.yaml\n*.pyc\n")

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        import warnings

        with warnings.catch_warnings(record=True) as w:
            warnings.simplefilter("always")
            loader.load()

            gitignore_warnings = [
                x for x in w if ".dli.local.yaml" in str(x.message)
            ]
            assert len(gitignore_warnings) == 0

    def test_no_warn_if_no_local_config(self, project_dir: Path) -> None:
        """Test no warning when .dli.local.yaml doesn't exist."""
        (project_dir / "dli.yaml").write_text("version: '1'\n")

        loader = ConfigLoader(project_path=project_dir, load_global=False)

        import warnings

        with warnings.catch_warnings(record=True) as w:
            warnings.simplefilter("always")
            loader.load()

            gitignore_warnings = [
                x for x in w if ".dli.local.yaml" in str(x.message)
            ]
            assert len(gitignore_warnings) == 0


# =============================================================================
# Secret Handling Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderSecrets:
    """Tests for secret variable handling."""

    def test_dli_secret_prefix_recognized(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_SECRET_* variables are recognized."""
        (project_dir / "dli.yaml").write_text(
            """database:
  password: "${DLI_SECRET_DB_PASSWORD}"
"""
        )
        monkeypatch.setenv("DLI_SECRET_DB_PASSWORD", "super-secret")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config, _ = loader.load()

        assert config["database"]["password"] == "super-secret"

    def test_secret_source_tracked(
        self, project_dir: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test secret variables track source as ENV_VAR."""
        (project_dir / "dli.yaml").write_text(
            """database:
  password: "${DLI_SECRET_DB_PASSWORD}"
"""
        )
        monkeypatch.setenv("DLI_SECRET_DB_PASSWORD", "secret")

        loader = ConfigLoader(project_path=project_dir, load_global=False)
        _, sources = loader.load()

        assert sources["database.password"] == ConfigSource.ENV_VAR


# =============================================================================
# Build Source Map Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderBuildSourceMap:
    """Tests for _build_source_map method."""

    def test_build_source_map_flat(self, project_dir: Path) -> None:
        """Test building source map for flat config."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config = {"key1": "value1", "key2": "value2"}
        sources = loader._build_source_map(config, ConfigSource.PROJECT)

        assert sources["key1"] == ConfigSource.PROJECT
        assert sources["key2"] == ConfigSource.PROJECT

    def test_build_source_map_nested(self, project_dir: Path) -> None:
        """Test building source map for nested config."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config = {
            "server": {
                "url": "https://test.com",
                "options": {"timeout": 30},
            }
        }
        sources = loader._build_source_map(config, ConfigSource.LOCAL)

        assert sources["server"] == ConfigSource.LOCAL
        assert sources["server.url"] == ConfigSource.LOCAL
        assert sources["server.options"] == ConfigSource.LOCAL
        assert sources["server.options.timeout"] == ConfigSource.LOCAL


# =============================================================================
# Integration Tests
# =============================================================================


@pytest.mark.skipif(ConfigLoader is None, reason="ConfigLoader not yet implemented")
class TestConfigLoaderIntegration:
    """Integration tests for ConfigLoader."""

    def test_full_config_loading(
        self,
        project_dir: Path,
        global_config_dir: Path,
        global_config_file: Path,
        project_config_file: Path,
        local_config_file: Path,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        """Test complete config loading with all layers."""
        monkeypatch.setenv("HOME", str(global_config_dir.parent))
        monkeypatch.setenv("DLI_OVERRIDE", "env-value")

        # Modify project config to include template
        (project_dir / "dli.yaml").write_text(
            """version: "1"

project:
  name: "my-project"

server:
  url: "https://project.basecamp.io"
  extra: "${DLI_OVERRIDE:-default}"

defaults:
  dialect: "bigquery"
  timeout_seconds: 600
"""
        )

        loader = ConfigLoader(project_path=project_dir, load_global=True, load_local=True)
        config, sources = loader.load()

        # Verify layered resolution
        assert config["server"]["url"] == "http://localhost:8081"  # from local
        assert config["server"]["extra"] == "env-value"  # from env
        assert config["project"]["name"] == "my-project"  # from project
        assert config["defaults"]["timeout_seconds"] == 60  # from local
        assert config["active_environment"] == "dev"  # from local

        # Verify sources
        assert sources["server.url"] == ConfigSource.LOCAL
        assert sources["server.extra"] == ConfigSource.ENV_VAR
        assert sources["project.name"] == ConfigSource.PROJECT

    def test_config_loader_immutability(
        self, project_dir: Path, project_config_file: Path
    ) -> None:
        """Test that loaded config cannot accidentally be modified."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)
        config1, _ = loader.load()
        config2, _ = loader.load()

        # Modify config1
        config1["server"]["url"] = "modified"

        # config2 should be unaffected (new dict each time)
        assert config2["server"]["url"] == "https://project.basecamp.io"

    def test_reloading_picks_up_changes(
        self, project_dir: Path, project_config_file: Path
    ) -> None:
        """Test that reload picks up file changes."""
        loader = ConfigLoader(project_path=project_dir, load_global=False)

        config1, _ = loader.load()
        assert config1["server"]["url"] == "https://project.basecamp.io"

        # Modify the file
        (project_dir / "dli.yaml").write_text(
            """version: "1"
server:
  url: "https://changed.basecamp.io"
"""
        )

        config2, _ = loader.load()
        assert config2["server"]["url"] == "https://changed.basecamp.io"
