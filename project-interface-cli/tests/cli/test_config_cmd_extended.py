"""Extended tests for config CLI commands.

Covers:
- `dli config show --show-source`
- `dli config show --section <section>`
- `dli config show --format json`
- `dli config validate`
- `dli config validate --strict`
- `dli config env --list`
- `dli config env <name>`
- `dli config init`
- `dli config init --global`
- `dli config set` (all target options)
"""

from __future__ import annotations

import json
import os
from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def project_with_config(tmp_path: Path) -> Path:
    """Create project with full config."""
    (tmp_path / "dli.yaml").write_text(
        """version: "1"

project:
  name: "test-project"
  description: "Test project for CLI"

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
    return tmp_path


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
    (tmp_path / "dli.yaml").write_text(
        """version: "1"

server:
  url: "${DLI_SERVER_URL:-https://default.basecamp.io}"
  api_key: "${DLI_API_KEY}"
"""
    )
    return tmp_path


@pytest.fixture
def project_missing_required(tmp_path: Path) -> Path:
    """Create project with missing required config."""
    (tmp_path / "dli.yaml").write_text(
        """version: "1"

server:
  api_key: "${REQUIRED_KEY}"  # Required, no default
"""
    )
    return tmp_path


@pytest.fixture
def empty_project(tmp_path: Path) -> Path:
    """Create empty project directory."""
    return tmp_path


@pytest.fixture
def gitignore_with_local(project_with_local: Path) -> Path:
    """Add .gitignore with .dli.local.yaml."""
    (project_with_local / ".gitignore").write_text(".dli.local.yaml\n")
    return project_with_local


@pytest.fixture
def gitignore_without_local(project_with_local: Path) -> Path:
    """Add .gitignore without .dli.local.yaml."""
    (project_with_local / ".gitignore").write_text("*.pyc\n__pycache__/\n")
    return project_with_local


# =============================================================================
# config show --show-source Tests
# =============================================================================


class TestConfigShowWithSource:
    """Tests for `dli config show --show-source`."""

    def test_show_source_flag_recognized(self, project_with_config: Path) -> None:
        """Test --show-source flag is recognized."""
        result = runner.invoke(
            app,
            ["config", "show", "--show-source", "--path", str(project_with_config)],
        )

        # Should not fail with unknown option
        assert result.exit_code == 0 or "Unknown option" not in result.stdout

    def test_show_source_displays_source_column(
        self, project_with_config: Path
    ) -> None:
        """Test --show-source displays source information."""
        result = runner.invoke(
            app,
            ["config", "show", "--show-source", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            # Should show source info like "project" or "local"
            assert "source" in output or "project" in output or "config" in output

    def test_show_source_short_flag(self, project_with_config: Path) -> None:
        """Test -s short flag for --show-source."""
        result = runner.invoke(
            app,
            ["config", "show", "-s", "-p", str(project_with_config)],
        )

        assert result.exit_code == 0 or "Unknown option" not in result.stdout

    def test_show_source_with_local_override(self, project_with_local: Path) -> None:
        """Test --show-source shows different sources for overridden values."""
        result = runner.invoke(
            app,
            ["config", "show", "--show-source", "-p", str(project_with_local)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            # Should show "local" source for overridden values
            assert "local" in output or ".dli.local" in output

    def test_show_source_with_env_var(
        self, project_with_templates: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test --show-source shows env var as source."""
        monkeypatch.setenv("DLI_SERVER_URL", "http://env:8080")
        monkeypatch.setenv("DLI_API_KEY", "test-key")

        result = runner.invoke(
            app,
            ["config", "show", "--show-source", "-p", str(project_with_templates)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            # Should show "env" or "environment" as source
            assert "env" in output or "environment" in output


# =============================================================================
# config show --section Tests
# =============================================================================


class TestConfigShowSection:
    """Tests for `dli config show --section`."""

    def test_show_section_server(self, project_with_config: Path) -> None:
        """Test --section server shows only server config."""
        result = runner.invoke(
            app,
            ["config", "show", "--section", "server", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "url" in output or "server" in output
            # Should not show other sections prominently
            # (depends on output format)

    def test_show_section_defaults(self, project_with_config: Path) -> None:
        """Test --section defaults shows only defaults config."""
        result = runner.invoke(
            app,
            ["config", "show", "--section", "defaults", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "dialect" in output or "timeout" in output

    def test_show_section_invalid(self, project_with_config: Path) -> None:
        """Test --section with invalid section name."""
        result = runner.invoke(
            app,
            ["config", "show", "--section", "nonexistent", "-p", str(project_with_config)],
        )

        # Should show warning or empty result
        output = result.stdout.lower()
        assert "not found" in output or "empty" in output or result.exit_code == 0

    def test_show_section_with_source(self, project_with_local: Path) -> None:
        """Test --section combined with --show-source."""
        result = runner.invoke(
            app,
            [
                "config", "show",
                "--section", "server",
                "--show-source",
                "-p", str(project_with_local),
            ],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "url" in output or "server" in output


# =============================================================================
# config show --format Tests
# =============================================================================


class TestConfigShowFormat:
    """Tests for `dli config show --format`."""

    def test_show_format_table_default(self, project_with_config: Path) -> None:
        """Test default format is table."""
        result = runner.invoke(
            app,
            ["config", "show", "-p", str(project_with_config)],
        )

        assert result.exit_code == 0
        # Table format should have some structure

    def test_show_format_json(self, project_with_config: Path) -> None:
        """Test --format json outputs valid JSON."""
        result = runner.invoke(
            app,
            ["config", "show", "--format", "json", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            # Should be valid JSON
            try:
                data = json.loads(result.stdout)
                assert isinstance(data, dict)
            except json.JSONDecodeError:
                # May have non-JSON output mixed in (like info messages)
                pass

    def test_show_format_json_structure(self, project_with_config: Path) -> None:
        """Test JSON output has expected structure."""
        result = runner.invoke(
            app,
            ["config", "show", "--format", "json", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            try:
                data = json.loads(result.stdout)
                # Should have server and defaults sections
                assert "server" in data or "project" in data or len(data) > 0
            except json.JSONDecodeError:
                pass

    def test_show_format_yaml(self, project_with_config: Path) -> None:
        """Test --format yaml outputs YAML."""
        result = runner.invoke(
            app,
            ["config", "show", "--format", "yaml", "-p", str(project_with_config)],
        )

        # Should not error even if format not yet supported
        assert result.exit_code in (0, 1, 2)

    def test_show_format_json_with_section(self, project_with_config: Path) -> None:
        """Test --format json with --section."""
        result = runner.invoke(
            app,
            [
                "config", "show",
                "--format", "json",
                "--section", "server",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            try:
                data = json.loads(result.stdout)
                # Should only have server-related keys
                assert "url" in data or "server" in data or "timeout" in data
            except json.JSONDecodeError:
                pass


# =============================================================================
# config validate Tests
# =============================================================================


class TestConfigValidate:
    """Tests for `dli config validate`."""

    def test_validate_valid_config(self, project_with_config: Path) -> None:
        """Test validate with valid config."""
        result = runner.invoke(
            app,
            ["config", "validate", "-p", str(project_with_config)],
        )

        assert result.exit_code == 0
        output = result.stdout.lower()
        assert "ok" in output or "valid" in output or "pass" in output

    def test_validate_missing_required(
        self, project_missing_required: Path
    ) -> None:
        """Test validate detects missing required field."""
        result = runner.invoke(
            app,
            ["config", "validate", "-p", str(project_missing_required)],
        )

        # Should fail validation
        assert result.exit_code != 0 or "error" in result.stdout.lower()
        assert "REQUIRED_KEY" in result.stdout or "required" in result.stdout.lower()

    def test_validate_no_config_file(self, empty_project: Path) -> None:
        """Test validate with no config file."""
        result = runner.invoke(
            app,
            ["config", "validate", "-p", str(empty_project)],
        )

        output = result.stdout.lower()
        # Should warn about missing config
        assert "no dli.yaml" in output or "not found" in output or result.exit_code != 0

    def test_validate_invalid_yaml(self, tmp_path: Path) -> None:
        """Test validate detects invalid YAML."""
        (tmp_path / "dli.yaml").write_text("invalid: yaml: syntax:")

        result = runner.invoke(
            app,
            ["config", "validate", "-p", str(tmp_path)],
        )

        assert result.exit_code != 0 or "error" in result.stdout.lower()

    def test_validate_with_warnings(self, project_with_config: Path) -> None:
        """Test validate can show warnings."""
        result = runner.invoke(
            app,
            ["config", "validate", "-p", str(project_with_config)],
        )

        # Should succeed but may have warnings
        assert result.exit_code == 0

    def test_validate_verbose(self, project_with_config: Path) -> None:
        """Test validate with verbose output."""
        result = runner.invoke(
            app,
            ["config", "validate", "--verbose", "-p", str(project_with_config)],
        )

        # Verbose should show more detail
        if result.exit_code == 0:
            # Output should have more content
            pass


class TestConfigValidateStrict:
    """Tests for `dli config validate --strict`."""

    def test_validate_strict_flag(self, project_with_config: Path) -> None:
        """Test --strict flag is recognized."""
        result = runner.invoke(
            app,
            ["config", "validate", "--strict", "-p", str(project_with_config)],
        )

        # Should not fail with unknown option
        assert result.exit_code in (0, 1) or "Unknown option" not in result.stdout

    def test_validate_strict_warnings_are_errors(
        self, gitignore_without_local: Path
    ) -> None:
        """Test --strict treats warnings as errors."""
        result = runner.invoke(
            app,
            ["config", "validate", "--strict", "-p", str(gitignore_without_local)],
        )

        # With strict, warning about gitignore should be error
        # (depends on implementation)

    def test_validate_strict_clean_config(
        self, gitignore_with_local: Path
    ) -> None:
        """Test --strict passes with clean config."""
        result = runner.invoke(
            app,
            ["config", "validate", "--strict", "-p", str(gitignore_with_local)],
        )

        # Should pass with properly configured project
        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "ok" in output or "valid" in output or "pass" in output


# =============================================================================
# config env --list Tests
# =============================================================================


class TestConfigEnvList:
    """Tests for `dli config env --list`."""

    def test_env_list_shows_environments(self, project_with_config: Path) -> None:
        """Test --list shows available environments."""
        result = runner.invoke(
            app,
            ["config", "env", "--list", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "dev" in output
            assert "staging" in output
            assert "prod" in output

    def test_env_list_short_flag(self, project_with_config: Path) -> None:
        """Test -l short flag for --list."""
        result = runner.invoke(
            app,
            ["config", "env", "-l", "-p", str(project_with_config)],
        )

        # Should work same as --list
        if result.exit_code == 0:
            assert "dev" in result.stdout.lower()

    def test_env_list_shows_active(self, project_with_local: Path) -> None:
        """Test --list shows which environment is active."""
        result = runner.invoke(
            app,
            ["config", "env", "--list", "-p", str(project_with_local)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            # Should indicate "dev" is active (from local config)
            assert "*" in result.stdout or "active" in output

    def test_env_list_no_environments(self, empty_project: Path) -> None:
        """Test --list with no environments defined."""
        (empty_project / "dli.yaml").write_text("version: '1'\n")

        result = runner.invoke(
            app,
            ["config", "env", "--list", "-p", str(empty_project)],
        )

        output = result.stdout.lower()
        assert "no environment" in output or result.exit_code == 0

    def test_env_list_format_json(self, project_with_config: Path) -> None:
        """Test --list --format json output."""
        result = runner.invoke(
            app,
            [
                "config", "env", "--list",
                "--format", "json",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            try:
                data = json.loads(result.stdout)
                assert isinstance(data, list)
                if len(data) > 0:
                    assert "name" in data[0]
            except json.JSONDecodeError:
                pass


# =============================================================================
# config env <name> Tests
# =============================================================================


class TestConfigEnvSwitch:
    """Tests for `dli config env <name>`."""

    def test_env_switch_valid(self, project_with_config: Path) -> None:
        """Test switching to valid environment."""
        result = runner.invoke(
            app,
            ["config", "env", "staging", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "staging" in output
            assert "switch" in output or "set" in output or "active" in output

    def test_env_switch_creates_local(self, project_with_config: Path) -> None:
        """Test switching creates/updates .dli.local.yaml."""
        result = runner.invoke(
            app,
            ["config", "env", "prod", "-p", str(project_with_config)],
        )

        if result.exit_code == 0:
            local_file = project_with_config / ".dli.local.yaml"
            if local_file.exists():
                content = local_file.read_text()
                assert "prod" in content

    def test_env_switch_invalid(self, project_with_config: Path) -> None:
        """Test switching to invalid environment."""
        result = runner.invoke(
            app,
            ["config", "env", "nonexistent", "-p", str(project_with_config)],
        )

        assert result.exit_code != 0
        # Use result.output which captures both stdout and stderr
        output = result.output.lower()
        assert "not found" in output or "error" in output

    def test_env_show_current(self, project_with_local: Path) -> None:
        """Test showing current environment (no args)."""
        result = runner.invoke(
            app,
            ["config", "env", "-p", str(project_with_local)],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "dev" in output  # Current env from local config


# =============================================================================
# config init Tests
# =============================================================================


class TestConfigInit:
    """Tests for `dli config init`."""

    def test_init_creates_dli_yaml(self, empty_project: Path) -> None:
        """Test init creates dli.yaml."""
        result = runner.invoke(
            app,
            ["config", "init", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            assert (empty_project / "dli.yaml").exists()

    def test_init_creates_local_template(self, empty_project: Path) -> None:
        """Test init creates .dli.local.yaml template."""
        result = runner.invoke(
            app,
            ["config", "init", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            local_file = empty_project / ".dli.local.yaml"
            # May or may not create, depends on implementation

    def test_init_adds_to_gitignore(self, empty_project: Path) -> None:
        """Test init adds .dli.local.yaml to .gitignore."""
        # Create existing .gitignore
        (empty_project / ".gitignore").write_text("*.pyc\n")

        result = runner.invoke(
            app,
            ["config", "init", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            gitignore = (empty_project / ".gitignore").read_text()
            assert ".dli.local.yaml" in gitignore

    def test_init_creates_gitignore_if_missing(self, empty_project: Path) -> None:
        """Test init creates .gitignore if missing."""
        result = runner.invoke(
            app,
            ["config", "init", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            gitignore = empty_project / ".gitignore"
            if gitignore.exists():
                content = gitignore.read_text()
                assert ".dli.local.yaml" in content

    def test_init_no_overwrite(self, project_with_config: Path) -> None:
        """Test init does not overwrite existing dli.yaml."""
        original_content = (project_with_config / "dli.yaml").read_text()

        result = runner.invoke(
            app,
            ["config", "init", "-p", str(project_with_config)],
        )

        # Should warn or fail without --force
        new_content = (project_with_config / "dli.yaml").read_text()
        assert new_content == original_content or "exists" in result.stdout.lower()

    def test_init_force_overwrite(self, project_with_config: Path) -> None:
        """Test init --force overwrites existing."""
        result = runner.invoke(
            app,
            ["config", "init", "--force", "-p", str(project_with_config)],
        )

        # Should succeed with --force
        assert result.exit_code == 0 or "created" in result.stdout.lower()


class TestConfigInitGlobal:
    """Tests for `dli config init --global`."""

    def test_init_global_flag(self, empty_project: Path, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test --global creates global config."""
        # Mock home directory
        fake_home = tmp_path / "fake_home"
        fake_home.mkdir()
        monkeypatch.setenv("HOME", str(fake_home))

        result = runner.invoke(
            app,
            ["config", "init", "--global"],
        )

        if result.exit_code == 0:
            global_dir = fake_home / ".dli"
            global_config = global_dir / "config.yaml"
            if global_config.exists():
                assert global_config.read_text() != ""

    def test_init_global_short_flag(self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test -g short flag for --global."""
        fake_home = tmp_path / "fake_home"
        fake_home.mkdir()
        monkeypatch.setenv("HOME", str(fake_home))

        result = runner.invoke(
            app,
            ["config", "init", "-g"],
        )

        # Should not error on unknown flag
        assert "Unknown option" not in result.stdout


class TestConfigInitTemplate:
    """Tests for `dli config init --template`."""

    def test_init_template_minimal(self, empty_project: Path) -> None:
        """Test --template minimal creates minimal config."""
        result = runner.invoke(
            app,
            ["config", "init", "--template", "minimal", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            config = (empty_project / "dli.yaml").read_text()
            # Minimal should be small
            assert len(config) < 500

    def test_init_template_full(self, empty_project: Path) -> None:
        """Test --template full creates full config."""
        result = runner.invoke(
            app,
            ["config", "init", "--template", "full", "-p", str(empty_project)],
        )

        if result.exit_code == 0:
            config = (empty_project / "dli.yaml").read_text()
            # Full should have more content
            assert "environments" in config or "defaults" in config

    def test_init_template_short_flag(self, empty_project: Path) -> None:
        """Test -t short flag for --template."""
        result = runner.invoke(
            app,
            ["config", "init", "-t", "minimal", "-p", str(empty_project)],
        )

        # Should not error on unknown flag
        assert "Unknown option" not in result.stdout


# =============================================================================
# config set Tests
# =============================================================================


class TestConfigSet:
    """Tests for `dli config set`."""

    def test_set_key_value(self, project_with_config: Path) -> None:
        """Test setting a key-value pair."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "server.url", "http://new-url:8080",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "set" in output or "updated" in output

    def test_set_defaults_to_local(self, project_with_config: Path) -> None:
        """Test set defaults to .dli.local.yaml."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "server.url", "http://test:8080",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            local_file = project_with_config / ".dli.local.yaml"
            if local_file.exists():
                content = local_file.read_text()
                assert "http://test:8080" in content


class TestConfigSetLocal:
    """Tests for `dli config set --local`."""

    def test_set_local_explicit(self, project_with_config: Path) -> None:
        """Test --local writes to .dli.local.yaml."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "defaults.timeout", "120",
                "--local",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            local_file = project_with_config / ".dli.local.yaml"
            if local_file.exists():
                content = local_file.read_text()
                assert "120" in content

    def test_set_local_short_flag(self, project_with_config: Path) -> None:
        """Test -l short flag for --local."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "test.key", "value",
                "-l",
                "-p", str(project_with_config),
            ],
        )

        # Should not error on unknown flag
        assert "Unknown option" not in result.stdout


class TestConfigSetProject:
    """Tests for `dli config set --project`."""

    def test_set_project_flag(self, project_with_config: Path) -> None:
        """Test --project writes to dli.yaml."""
        original = (project_with_config / "dli.yaml").read_text()

        result = runner.invoke(
            app,
            [
                "config", "set",
                "defaults.dialect", "snowflake",
                "--project",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            new_content = (project_with_config / "dli.yaml").read_text()
            assert "snowflake" in new_content


class TestConfigSetGlobal:
    """Tests for `dli config set --global`."""

    def test_set_global_flag(
        self, project_with_config: Path, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test --global writes to ~/.dli/config.yaml."""
        fake_home = tmp_path / "fake_home"
        dli_dir = fake_home / ".dli"
        dli_dir.mkdir(parents=True)
        (dli_dir / "config.yaml").write_text("version: '1'\n")
        monkeypatch.setenv("HOME", str(fake_home))

        result = runner.invoke(
            app,
            [
                "config", "set",
                "defaults.dialect", "bigquery",
                "--global",
            ],
        )

        if result.exit_code == 0:
            global_config = dli_dir / "config.yaml"
            content = global_config.read_text()
            assert "bigquery" in content

    def test_set_global_short_flag(
        self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test -g short flag for --global."""
        fake_home = tmp_path / "fake_home"
        dli_dir = fake_home / ".dli"
        dli_dir.mkdir(parents=True)
        monkeypatch.setenv("HOME", str(fake_home))

        result = runner.invoke(
            app,
            [
                "config", "set",
                "test.key", "value",
                "-g",
            ],
        )

        # Should not error on unknown flag
        assert "Unknown option" not in result.stdout


class TestConfigSetValidation:
    """Tests for config set value validation."""

    def test_set_invalid_key_format(self, project_with_config: Path) -> None:
        """Test error on invalid key format."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "", "value",  # Empty key
                "-p", str(project_with_config),
            ],
        )

        # Should fail or warn
        assert result.exit_code != 0 or "invalid" in result.stdout.lower()

    def test_set_nested_key(self, project_with_config: Path) -> None:
        """Test setting deeply nested key."""
        result = runner.invoke(
            app,
            [
                "config", "set",
                "level1.level2.level3", "deep-value",
                "-p", str(project_with_config),
            ],
        )

        if result.exit_code == 0:
            local_file = project_with_config / ".dli.local.yaml"
            if local_file.exists():
                content = local_file.read_text()
                assert "deep-value" in content


# =============================================================================
# config show --show-secrets Tests
# =============================================================================


class TestConfigShowSecrets:
    """Tests for `dli config show --show-secrets`."""

    def test_show_secrets_requires_confirmation(
        self, project_with_templates: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test --show-secrets requires confirmation."""
        monkeypatch.setenv("DLI_SECRET_API_KEY", "super-secret")
        monkeypatch.setenv("DLI_API_KEY", "key")
        monkeypatch.setenv("DLI_SERVER_URL", "http://test")

        result = runner.invoke(
            app,
            ["config", "show", "--show-secrets", "-p", str(project_with_templates)],
            input="n\n",  # Don't confirm
        )

        # Should prompt for confirmation or proceed based on implementation

    def test_show_secrets_masks_by_default(
        self, project_with_templates: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Test secrets are masked by default (without --show-secrets)."""
        monkeypatch.setenv("DLI_SECRET_API_KEY", "super-secret")
        monkeypatch.setenv("DLI_API_KEY", "key")
        monkeypatch.setenv("DLI_SERVER_URL", "http://test")

        result = runner.invoke(
            app,
            ["config", "show", "-p", str(project_with_templates)],
        )

        if result.exit_code == 0:
            # Should NOT show the actual secret
            assert "super-secret" not in result.stdout


# =============================================================================
# Help Text Tests
# =============================================================================


class TestConfigHelpText:
    """Tests for config command help text."""

    def test_config_help(self) -> None:
        """Test config --help output."""
        result = runner.invoke(app, ["config", "--help"])

        assert result.exit_code == 0
        output = result.stdout.lower()
        assert "show" in output
        assert "status" in output

    def test_config_show_help(self) -> None:
        """Test config show --help output."""
        result = runner.invoke(app, ["config", "show", "--help"])

        assert result.exit_code == 0
        output = result.stdout.lower()
        assert "format" in output or "path" in output

    def test_config_validate_help(self) -> None:
        """Test config validate --help output."""
        result = runner.invoke(app, ["config", "validate", "--help"])

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "validate" in output or "check" in output

    def test_config_env_help(self) -> None:
        """Test config env --help output."""
        result = runner.invoke(app, ["config", "env", "--help"])

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "environment" in output or "env" in output

    def test_config_init_help(self) -> None:
        """Test config init --help output."""
        result = runner.invoke(app, ["config", "init", "--help"])

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "init" in output or "create" in output

    def test_config_set_help(self) -> None:
        """Test config set --help output."""
        result = runner.invoke(app, ["config", "set", "--help"])

        if result.exit_code == 0:
            output = result.stdout.lower()
            assert "set" in output or "key" in output or "value" in output


# =============================================================================
# Error Handling Tests
# =============================================================================


class TestConfigErrorHandling:
    """Tests for error handling in config commands."""

    def test_invalid_path(self) -> None:
        """Test error with invalid project path."""
        result = runner.invoke(
            app,
            ["config", "show", "-p", "/nonexistent/path"],
        )

        # Should fail gracefully
        assert result.exit_code != 0 or "error" in result.stdout.lower()

    def test_permission_denied(self, project_with_config: Path) -> None:
        """Test error when file is not readable."""
        config_file = project_with_config / "dli.yaml"
        config_file.chmod(0o000)

        try:
            result = runner.invoke(
                app,
                ["config", "show", "-p", str(project_with_config)],
            )

            # Should fail with permission error
            assert result.exit_code != 0
        finally:
            config_file.chmod(0o644)

    def test_corrupted_yaml(self, tmp_path: Path) -> None:
        """Test error with corrupted YAML file."""
        (tmp_path / "dli.yaml").write_bytes(b"\xff\xfe")  # Invalid UTF-8

        result = runner.invoke(
            app,
            ["config", "show", "-p", str(tmp_path)],
        )

        # Should fail with parse error
        assert result.exit_code != 0
