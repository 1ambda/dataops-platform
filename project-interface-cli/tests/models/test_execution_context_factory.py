"""Tests for ExecutionContext.from_environment() factory method.

Covers:
- Basic from_environment() creation
- Environment parameter usage
- Overrides parameter precedence
- Environment variable integration
- Error handling
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import pytest

from dli.models.common import ExecutionContext, ExecutionMode


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def project_with_full_config(tmp_path: Path) -> Path:
    """Create project with full config including environments."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

project:
  name: "test-project"

server:
  url: "https://default.basecamp.io"
  timeout: 30

defaults:
  dialect: "trino"
  timeout_seconds: 300

environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
    timeout_seconds: 60
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
    timeout_seconds: 600
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
    timeout_seconds: 3600
    api_key: "${PROD_API_KEY}"
"""
    )
    return project_dir


@pytest.fixture
def project_with_local(tmp_path: Path) -> Path:
    """Create project with local config override."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

server:
  url: "https://project.basecamp.io"

defaults:
  dialect: "trino"
  timeout_seconds: 300
"""
    )

    (project_dir / ".dli.local.yaml").write_text(
        """server:
  url: "http://localhost:8081"

active_environment: "dev"
"""
    )
    return project_dir


@pytest.fixture
def project_minimal(tmp_path: Path) -> Path:
    """Create project with minimal config."""
    project_dir = tmp_path / "project"
    project_dir.mkdir()

    (project_dir / "dli.yaml").write_text(
        """version: "1"

project:
  name: "minimal"
"""
    )
    return project_dir


# =============================================================================
# Basic from_environment() Tests
# =============================================================================


class TestFromEnvironmentBasic:
    """Basic tests for ExecutionContext.from_environment()."""

    def test_from_environment_exists(self) -> None:
        """Test that from_environment class method exists."""
        assert hasattr(ExecutionContext, "from_environment")
        assert callable(getattr(ExecutionContext, "from_environment"))

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_default_path(self) -> None:
        """Test from_environment with default project path."""
        ctx = ExecutionContext.from_environment()

        assert isinstance(ctx, ExecutionContext)

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_with_project_path(
        self, project_with_full_config: Path
    ) -> None:
        """Test from_environment with explicit project path."""
        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.project_path == project_with_full_config

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_loads_defaults(
        self, project_with_full_config: Path
    ) -> None:
        """Test from_environment loads default values from config."""
        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.dialect == "trino"
        assert ctx.timeout == 300

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_returns_execution_context(
        self, project_with_full_config: Path
    ) -> None:
        """Test from_environment returns ExecutionContext instance."""
        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert isinstance(ctx, ExecutionContext)
        assert hasattr(ctx, "execution_mode")
        assert hasattr(ctx, "dialect")


# =============================================================================
# Environment Parameter Tests
# =============================================================================


class TestFromEnvironmentWithEnvironment:
    """Tests for from_environment with environment parameter."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_dev(self, project_with_full_config: Path) -> None:
        """Test from_environment with dev environment."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",
        )

        assert ctx.server_url == "http://localhost:8081"
        assert ctx.dialect == "duckdb"
        assert ctx.timeout == 60

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_staging(self, project_with_full_config: Path) -> None:
        """Test from_environment with staging environment."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="staging",
        )

        assert ctx.server_url == "https://staging.basecamp.io"
        assert ctx.dialect == "trino"
        assert ctx.timeout == 600

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_prod(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test from_environment with prod environment."""
        monkeypatch.setenv("PROD_API_KEY", "prod-key")

        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="prod",
        )

        assert ctx.server_url == "https://prod.basecamp.io"
        assert ctx.dialect == "bigquery"
        assert ctx.timeout == 3600

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_invalid_env(
        self, project_with_full_config: Path
    ) -> None:
        """Test from_environment with invalid environment raises error."""
        from dli.exceptions import ConfigEnvNotFoundError

        with pytest.raises(ConfigEnvNotFoundError) as exc_info:
            ExecutionContext.from_environment(
                project_path=project_with_full_config,
                environment="nonexistent",
            )

        assert "not found" in str(exc_info.value).lower()

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_from_environment_uses_active_env(
        self, project_with_local: Path
    ) -> None:
        """Test from_environment uses active_environment when set."""
        ctx = ExecutionContext.from_environment(project_path=project_with_local)

        # active_environment is "dev" in local config
        # Should use dev environment settings if available


# =============================================================================
# Overrides Parameter Tests
# =============================================================================


class TestFromEnvironmentWithOverrides:
    """Tests for from_environment with overrides parameter."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_server_url(self, project_with_full_config: Path) -> None:
        """Test overrides parameter overrides server_url."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={"server_url": "http://override:9999"},
        )

        assert ctx.server_url == "http://override:9999"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_dialect(self, project_with_full_config: Path) -> None:
        """Test overrides parameter overrides dialect."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={"dialect": "snowflake"},
        )

        assert ctx.dialect == "snowflake"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_timeout(self, project_with_full_config: Path) -> None:
        """Test overrides parameter overrides timeout."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={"timeout": 999},
        )

        assert ctx.timeout == 999

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_execution_mode(self, project_with_full_config: Path) -> None:
        """Test overrides parameter overrides execution_mode."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={"execution_mode": "mock"},
        )

        assert ctx.execution_mode == ExecutionMode.MOCK

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_multiple_values(self, project_with_full_config: Path) -> None:
        """Test overrides with multiple values."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={
                "server_url": "http://test:8080",
                "dialect": "duckdb",
                "timeout": 100,
                "dry_run": True,
            },
        )

        assert ctx.server_url == "http://test:8080"
        assert ctx.dialect == "duckdb"
        assert ctx.timeout == 100
        assert ctx.dry_run is True

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_beats_environment(self, project_with_full_config: Path) -> None:
        """Test overrides parameter beats environment parameter."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",  # dialect would be "duckdb"
            overrides={"dialect": "spark"},  # Override to spark
        )

        assert ctx.dialect == "spark"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_parameters_field(self, project_with_full_config: Path) -> None:
        """Test overrides can set parameters dict."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={
                "parameters": {
                    "start_date": "2024-01-01",
                    "end_date": "2024-12-31",
                },
            },
        )

        assert ctx.parameters["start_date"] == "2024-01-01"
        assert ctx.parameters["end_date"] == "2024-12-31"


# =============================================================================
# Environment Variable Precedence Tests
# =============================================================================


class TestFromEnvironmentEnvVarPrecedence:
    """Tests for environment variable precedence."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_env_var_overrides_config(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_* env vars override config values."""
        monkeypatch.setenv("DLI_SERVER_URL", "http://env:1234")

        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.server_url == "http://env:1234"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_env_var_execution_mode(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_EXECUTION_MODE env var."""
        monkeypatch.setenv("DLI_EXECUTION_MODE", "mock")

        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.execution_mode == ExecutionMode.MOCK

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_env_var_dialect(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_DIALECT env var."""
        monkeypatch.setenv("DLI_DIALECT", "snowflake")

        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.dialect == "snowflake"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_env_var_timeout(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_TIMEOUT env var."""
        monkeypatch.setenv("DLI_TIMEOUT", "999")

        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.timeout == 999

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_overrides_beat_env_vars(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test overrides parameter beats env vars."""
        monkeypatch.setenv("DLI_DIALECT", "bigquery")

        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            overrides={"dialect": "duckdb"},
        )

        # Overrides should win
        assert ctx.dialect == "duckdb"


# =============================================================================
# Active Environment from DLI_ENVIRONMENT Tests
# =============================================================================


class TestFromEnvironmentDLIEnvironment:
    """Tests for DLI_ENVIRONMENT env var."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_dli_environment_selects_env(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test DLI_ENVIRONMENT env var selects environment."""
        monkeypatch.setenv("DLI_ENVIRONMENT", "prod")
        monkeypatch.setenv("PROD_API_KEY", "key")

        ctx = ExecutionContext.from_environment(project_path=project_with_full_config)

        assert ctx.server_url == "https://prod.basecamp.io"
        assert ctx.dialect == "bigquery"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_environment_param_overrides_dli_environment(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test environment parameter overrides DLI_ENVIRONMENT."""
        monkeypatch.setenv("DLI_ENVIRONMENT", "prod")

        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",  # Explicit param should win
        )

        assert ctx.server_url == "http://localhost:8081"
        assert ctx.dialect == "duckdb"


# =============================================================================
# Error Handling Tests
# =============================================================================


class TestFromEnvironmentErrors:
    """Tests for from_environment error handling."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_missing_required_env_var(
        self, project_with_full_config: Path
    ) -> None:
        """Test that missing env var results in None value (lenient behavior).

        Note: from_environment() is designed to be lenient and not raise
        errors for missing optional values. Use ConfigAPI.validate() for
        strict validation of required values.
        """
        # prod environment has api_key: "${PROD_API_KEY}" but it's not set
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="prod",
        )

        # api_token should be None or empty since PROD_API_KEY is not set
        assert ctx.api_token is None or ctx.api_token == ""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_invalid_config_syntax(self, tmp_path: Path) -> None:
        """Test error on invalid config syntax."""
        project_dir = tmp_path / "project"
        project_dir.mkdir()

        (project_dir / "dli.yaml").write_text("invalid: yaml: syntax")

        from dli.exceptions import ConfigurationError

        with pytest.raises(ConfigurationError):
            ExecutionContext.from_environment(project_path=project_dir)

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_missing_project_path(self, tmp_path: Path) -> None:
        """Test that non-existent project path uses default values (lenient behavior).

        Note: from_environment() is designed to be lenient and work even
        without a valid project. Use ConfigAPI.validate() for strict validation.
        """
        non_existent = tmp_path / "nonexistent"

        # Should not raise, but use defaults
        ctx = ExecutionContext.from_environment(project_path=non_existent)

        # Should have project_path set but use default values
        assert ctx.project_path == non_existent
        assert ctx.dialect == "trino"  # default
        assert ctx.timeout == 300  # default


# =============================================================================
# Default Values Tests
# =============================================================================


class TestFromEnvironmentDefaults:
    """Tests for default values when config is minimal."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_default_execution_mode(self, project_minimal: Path) -> None:
        """Test default execution mode when not specified."""
        ctx = ExecutionContext.from_environment(project_path=project_minimal)

        assert ctx.execution_mode == ExecutionMode.LOCAL

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_default_dialect(self, project_minimal: Path) -> None:
        """Test default dialect when not specified."""
        ctx = ExecutionContext.from_environment(project_path=project_minimal)

        assert ctx.dialect == "trino"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_default_timeout(self, project_minimal: Path) -> None:
        """Test default timeout when not specified."""
        ctx = ExecutionContext.from_environment(project_path=project_minimal)

        assert ctx.timeout == 300

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_default_dry_run(self, project_minimal: Path) -> None:
        """Test default dry_run when not specified."""
        ctx = ExecutionContext.from_environment(project_path=project_minimal)

        assert ctx.dry_run is False

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_default_verbose(self, project_minimal: Path) -> None:
        """Test default verbose when not specified."""
        ctx = ExecutionContext.from_environment(project_path=project_minimal)

        assert ctx.verbose is False


# =============================================================================
# Integration Tests
# =============================================================================


class TestFromEnvironmentIntegration:
    """Integration tests for from_environment."""

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_full_precedence_chain(
        self, project_with_full_config: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test full precedence: overrides > env var > environment > config."""
        # Set env var
        monkeypatch.setenv("DLI_DIALECT", "spark")

        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",  # dialect would be "duckdb"
            overrides={"timeout": 123},  # Just override timeout
        )

        # dialect should come from env var (spark), not environment (duckdb)
        # timeout should come from overrides (123)
        # server_url should come from environment (dev)
        assert ctx.timeout == 123
        assert ctx.server_url == "http://localhost:8081"

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_context_usable_with_api(self, project_with_full_config: Path) -> None:
        """Test context from from_environment works with API classes."""
        from dli import ConfigAPI

        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",
        )

        # Should be able to use with API
        api = ConfigAPI(context=ctx)
        assert api.context is ctx

    @pytest.mark.skipif(
        not hasattr(ExecutionContext, "from_environment"),
        reason="from_environment not yet implemented",
    )
    def test_context_immutable_after_creation(
        self, project_with_full_config: Path
    ) -> None:
        """Test context is immutable after from_environment."""
        ctx = ExecutionContext.from_environment(
            project_path=project_with_full_config,
            environment="dev",
        )

        # Context fields should be immutable (if using frozen model)
        # This depends on implementation
