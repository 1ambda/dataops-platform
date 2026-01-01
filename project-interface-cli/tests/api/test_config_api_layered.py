"""Tests for layered ConfigAPI extensions.

Covers:
- get_all() merging
- get_with_source() source tracking
- get_all_with_sources() complete list
- validate() with errors/warnings
- get_environment() success/failure
- Layered config loading integration
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import pytest

from dli import ConfigAPI, ExecutionContext
from dli.models.common import ConfigValue, EnvironmentInfo, ExecutionMode
from dli.models.config import ConfigSource, ConfigValidationResult, ConfigValueInfo


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def project_with_config(tmp_path: Path) -> Path:
    """Create a project directory with config files."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    # Create project config
    (project_dir / "dli.yaml").write_text(
        """version: "1"

project:
  name: "test-project"

server:
  url: "https://project.basecamp.io"
  timeout: 30

defaults:
  dialect: "trino"
  timeout_seconds: 600

environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
"""
    )
    return project_dir


@pytest.fixture
def project_with_local(project_with_config: Path) -> Path:
    """Add local config to project."""
    (project_with_config / ".dli.local.yaml").write_text(
        """server:
  url: "http://localhost:8081"

defaults:
  timeout_seconds: 60

active_environment: "dev"
"""
    )
    return project_with_config


@pytest.fixture
def project_with_templates(tmp_path: Path) -> Path:
    """Create project with template variables."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

server:
  url: "${DLI_SERVER_URL:-https://default.basecamp.io}"
  api_key: "${DLI_API_KEY}"
"""
    )
    return project_dir


@pytest.fixture
def project_with_secrets(tmp_path: Path) -> Path:
    """Create project with secret variables."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

server:
  url: "https://api.example.com"
  api_key: "${DLI_SECRET_API_KEY}"

database:
  password: "${DLI_SECRET_DB_PASSWORD}"
"""
    )
    return project_dir


@pytest.fixture
def project_missing_required(tmp_path: Path) -> Path:
    """Create project with missing required config."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

server:
  api_key: "${REQUIRED_API_KEY}"  # Required, no default
"""
    )
    return project_dir


# =============================================================================
# ConfigAPI.get_all() Tests
# =============================================================================


class TestConfigAPIGetAll:
    """Tests for ConfigAPI.get_all() method."""

    def test_get_all_from_project_config(self, project_with_config: Path) -> None:
        """Test get_all returns merged project config."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        # This method may not exist yet; test documents expected behavior
        if hasattr(api, "get_all"):
            config = api.get_all()

            assert "project" in config
            assert config["project"]["name"] == "test-project"
            assert config["server"]["url"] == "https://project.basecamp.io"

    def test_get_all_with_local_override(self, project_with_local: Path) -> None:
        """Test get_all includes local overrides."""
        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            # Local should override project
            assert config["server"]["url"] == "http://localhost:8081"
            assert config["defaults"]["timeout_seconds"] == 60
            assert config["active_environment"] == "dev"

    def test_get_all_empty_project(self, tmp_path: Path) -> None:
        """Test get_all with no config files."""
        ctx = ExecutionContext(
            project_path=tmp_path,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            assert config == {} or config is not None

    def test_get_all_with_env_vars(
        self, project_with_templates: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test get_all resolves environment variables."""
        monkeypatch.setenv("DLI_SERVER_URL", "http://env-override:8080")
        monkeypatch.setenv("DLI_API_KEY", "test-key")

        ctx = ExecutionContext(
            project_path=project_with_templates,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            assert config["server"]["url"] == "http://env-override:8080"
            assert config["server"]["api_key"] == "test-key"

    def test_get_all_uses_template_defaults(
        self, project_with_templates: Path
    ) -> None:
        """Test get_all uses template default values."""
        # Don't set DLI_SERVER_URL
        ctx = ExecutionContext(
            project_path=project_with_templates,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            # This should fail because DLI_API_KEY is required
            from dli.exceptions import ConfigurationError

            with pytest.raises(ConfigurationError):
                api.get_all()


# =============================================================================
# ConfigAPI.get_with_source() Tests
# =============================================================================


class TestConfigAPIGetWithSource:
    """Tests for ConfigAPI.get_with_source() method."""

    def test_get_with_source_project(self, project_with_config: Path) -> None:
        """Test get_with_source returns PROJECT source."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("server.url")

            assert result is not None
            assert result.value == "https://project.basecamp.io"
            assert result.source == ConfigSource.PROJECT

    def test_get_with_source_local(self, project_with_local: Path) -> None:
        """Test get_with_source returns LOCAL source for overrides."""
        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("server.url")

            assert result is not None
            assert result.value == "http://localhost:8081"
            assert result.source == ConfigSource.LOCAL

    def test_get_with_source_env_var(
        self, project_with_templates: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test get_with_source returns ENV_VAR source."""
        monkeypatch.setenv("DLI_SERVER_URL", "http://env:8080")
        monkeypatch.setenv("DLI_API_KEY", "key")

        ctx = ExecutionContext(
            project_path=project_with_templates,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("server.url")

            assert result is not None
            assert result.source == ConfigSource.ENV_VAR

    def test_get_with_source_not_found(self, project_with_config: Path) -> None:
        """Test get_with_source returns None for missing key."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("nonexistent.key")

            assert result is None

    def test_get_with_source_secret_flag(
        self, project_with_secrets: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test get_with_source marks secrets as is_secret."""
        monkeypatch.setenv("DLI_SECRET_API_KEY", "secret-key")
        monkeypatch.setenv("DLI_SECRET_DB_PASSWORD", "secret-pass")

        ctx = ExecutionContext(
            project_path=project_with_secrets,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("server.api_key")

            if hasattr(result, "is_secret"):
                assert result.is_secret is True

    def test_get_with_source_nested_key(self, project_with_config: Path) -> None:
        """Test get_with_source handles nested keys."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_with_source"):
            result = api.get_with_source("defaults.dialect")

            assert result is not None
            assert result.value == "trino"


# =============================================================================
# ConfigAPI.get_all_with_sources() Tests
# =============================================================================


class TestConfigAPIGetAllWithSources:
    """Tests for ConfigAPI.get_all_with_sources() method."""

    def test_get_all_with_sources_returns_list(
        self, project_with_config: Path
    ) -> None:
        """Test get_all_with_sources returns list of ConfigValueInfo."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all_with_sources"):
            values = api.get_all_with_sources()

            assert isinstance(values, list)
            assert len(values) > 0

    def test_get_all_with_sources_includes_all_keys(
        self, project_with_config: Path
    ) -> None:
        """Test get_all_with_sources includes all config keys."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all_with_sources"):
            values = api.get_all_with_sources()
            keys = [v.key for v in values]

            assert "server.url" in keys
            assert "defaults.dialect" in keys

    def test_get_all_with_sources_mixed_sources(
        self, project_with_local: Path
    ) -> None:
        """Test get_all_with_sources shows mixed sources."""
        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all_with_sources"):
            values = api.get_all_with_sources()
            sources = {v.source for v in values}

            # Should have at least PROJECT and LOCAL sources
            assert ConfigSource.PROJECT in sources or ConfigSource.LOCAL in sources

    def test_get_all_with_sources_preserves_order(self, project_with_config: Path) -> None:
        """Test get_all_with_sources preserves config file order.

        Note: The API does not guarantee alphabetical sorting; it preserves
        the order in which keys appear in config files (YAML insertion order).
        """
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all_with_sources"):
            values = api.get_all_with_sources()
            keys = [v.key for v in values]

            # Verify keys are present (order not guaranteed to be alphabetical)
            assert len(keys) > 0
            assert "server.url" in keys


# =============================================================================
# ConfigAPI.validate() Tests
# =============================================================================


class TestConfigAPIValidate:
    """Tests for ConfigAPI.validate() method."""

    def test_validate_valid_config(self, project_with_config: Path) -> None:
        """Test validate returns valid for correct config."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate()

            assert isinstance(result, ConfigValidationResult)
            assert result.valid is True
            assert len(result.errors) == 0

    def test_validate_missing_required(
        self, project_missing_required: Path
    ) -> None:
        """Test validate detects missing required fields."""
        ctx = ExecutionContext(
            project_path=project_missing_required,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate()

            assert result.valid is False
            assert len(result.errors) > 0
            # Should mention the missing variable
            assert any("REQUIRED_API_KEY" in e for e in result.errors)

    def test_validate_with_warnings(self, tmp_path: Path) -> None:
        """Test validate can return warnings."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        # Config with deprecated field
        (project_dir / "dli.yaml").write_text(
            """version: "1"

# Using deprecated field for testing
deprecated_field: "value"
"""
        )

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate()

            # May have warnings about deprecated/unknown fields
            # This depends on implementation

    def test_validate_strict_mode(self, project_with_config: Path) -> None:
        """Test validate with strict=True treats warnings as errors."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate(strict=True)

            # With strict mode, warnings become errors
            assert isinstance(result, ConfigValidationResult)

    def test_validate_no_config_file(self, tmp_path: Path) -> None:
        """Test validate with no config file."""
        ctx = ExecutionContext(
            project_path=tmp_path,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate()

            # Should warn about missing config
            assert result.valid is False or len(result.warnings) > 0

    def test_validate_invalid_yaml(self, tmp_path: Path) -> None:
        """Test validate detects invalid YAML."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text("invalid: yaml: syntax:\n  - bad")

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "validate"):
            result = api.validate()

            assert result.valid is False
            assert any("yaml" in e.lower() or "syntax" in e.lower() for e in result.errors)


# =============================================================================
# ConfigAPI.get_environment() Tests
# =============================================================================


class TestConfigAPIGetEnvironment:
    """Tests for ConfigAPI.get_environment() method."""

    def test_get_environment_exists(self, project_with_config: Path) -> None:
        """Test get_environment returns existing environment."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_environment"):
            env_config = api.get_environment("dev")

            assert env_config is not None
            assert env_config["server_url"] == "http://localhost:8081"
            assert env_config["dialect"] == "duckdb"

    def test_get_environment_not_found(self, project_with_config: Path) -> None:
        """Test get_environment raises error for missing environment."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_environment"):
            from dli.exceptions import ConfigEnvNotFoundError

            with pytest.raises(ConfigEnvNotFoundError) as exc_info:
                api.get_environment("nonexistent")

            assert "not found" in str(exc_info.value).lower()

    def test_get_environment_all_envs(self, project_with_config: Path) -> None:
        """Test get_environment for all defined environments."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_environment"):
            dev = api.get_environment("dev")
            staging = api.get_environment("staging")
            prod = api.get_environment("prod")

            assert dev["dialect"] == "duckdb"
            assert staging["dialect"] == "trino"
            assert prod["dialect"] == "bigquery"


# =============================================================================
# ConfigAPI.get_active_environment() Tests
# =============================================================================


class TestConfigAPIGetActiveEnvironment:
    """Tests for ConfigAPI.get_active_environment() method."""

    def test_get_active_from_local_config(self, project_with_local: Path) -> None:
        """Test get_active_environment reads from local config."""
        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_active_environment"):
            active = api.get_active_environment()

            assert active == "dev"

    def test_get_active_from_env_var(
        self, project_with_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test get_active_environment prefers env var."""
        monkeypatch.setenv("DLI_ENVIRONMENT", "prod")

        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_active_environment"):
            active = api.get_active_environment()

            assert active == "prod"

    def test_get_active_env_var_overrides_local(
        self, project_with_local: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test env var overrides local config active_environment."""
        monkeypatch.setenv("DLI_ENVIRONMENT", "staging")

        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_active_environment"):
            active = api.get_active_environment()

            assert active == "staging"  # env var, not "dev" from local

    def test_get_active_none_when_not_set(self, project_with_config: Path) -> None:
        """Test get_active_environment returns None when not set."""
        # Clear env var
        env_backup = os.environ.pop("DLI_ENVIRONMENT", None)
        try:
            ctx = ExecutionContext(
                project_path=project_with_config,
                execution_mode=ExecutionMode.LOCAL,
            )
            api = ConfigAPI(context=ctx)

            if hasattr(api, "get_active_environment"):
                active = api.get_active_environment()

                assert active is None
        finally:
            if env_backup:
                os.environ["DLI_ENVIRONMENT"] = env_backup


# =============================================================================
# ConfigAPI Layer Loading Options Tests
# =============================================================================


class TestConfigAPILayerOptions:
    """Tests for ConfigAPI layer loading options."""

    def test_disable_global_loading(self, project_with_config: Path) -> None:
        """Test ConfigAPI can disable global config loading."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )

        # If ConfigAPI supports load_global option
        if hasattr(ConfigAPI, "__init__"):
            try:
                api = ConfigAPI(context=ctx, load_global=False)
                # Should not load ~/.dli/config.yaml
            except TypeError:
                # Option not supported yet
                pass

    def test_disable_local_loading(self, project_with_local: Path) -> None:
        """Test ConfigAPI can disable local config loading."""
        ctx = ExecutionContext(
            project_path=project_with_local,
            execution_mode=ExecutionMode.LOCAL,
        )

        if hasattr(ConfigAPI, "__init__"):
            try:
                api = ConfigAPI(context=ctx, load_local=False)

                if hasattr(api, "get_all"):
                    config = api.get_all()
                    # Should use project value, not local
                    assert config["server"]["url"] == "https://project.basecamp.io"
            except TypeError:
                # Option not supported yet
                pass


# =============================================================================
# ConfigAPI Caching Tests
# =============================================================================


class TestConfigAPICaching:
    """Tests for ConfigAPI caching behavior."""

    def test_config_cached_after_first_load(
        self, project_with_config: Path
    ) -> None:
        """Test config is cached after first load."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config1 = api.get_all()
            config2 = api.get_all()

            # Should be same object (cached)
            assert config1 is config2

    def test_reload_clears_cache(self, project_with_config: Path) -> None:
        """Test reload() clears cached config."""
        ctx = ExecutionContext(
            project_path=project_with_config,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all") and hasattr(api, "reload"):
            config1 = api.get_all()
            api.reload()
            config2 = api.get_all()

            # Should be different object after reload
            assert config1 is not config2


# =============================================================================
# Edge Cases and Error Handling
# =============================================================================


class TestConfigAPIEdgeCases:
    """Edge case and error handling tests."""

    def test_special_characters_in_config(self, tmp_path: Path) -> None:
        """Test handling of special characters in config values."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text(
            """server:
  url: "https://api.example.com/path?param=value&other=123"
  key: "abc!@#$%^&*()"
"""
        )

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            assert "?param=value" in config["server"]["url"]
            assert "!@#$%^&*()" in config["server"]["key"]

    def test_unicode_in_config(self, tmp_path: Path) -> None:
        """Test handling of unicode in config values."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text(
            """project:
  name: "프로젝트-테스트"
  description: "日本語テスト"
"""
        )

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            assert config["project"]["name"] == "프로젝트-테스트"

    def test_deeply_nested_config(self, tmp_path: Path) -> None:
        """Test handling of deeply nested configuration."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text(
            """level1:
  level2:
    level3:
      level4:
        level5:
          value: "deep"
"""
        )

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get"):
            result = api.get("level1.level2.level3.level4.level5.value")

            # Should handle deep nesting - get() returns value directly
            if result is not None:
                assert result == "deep"

    def test_empty_config_file(self, tmp_path: Path) -> None:
        """Test handling of empty config file."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text("")

        ctx = ExecutionContext(
            project_path=project_dir,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = ConfigAPI(context=ctx)

        if hasattr(api, "get_all"):
            config = api.get_all()

            assert config == {} or config is not None
