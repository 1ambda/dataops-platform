"""Tests for the config subcommand."""

from __future__ import annotations

from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


@pytest.fixture
def sample_project_path() -> Path:
    """Return path to sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


class TestConfigShow:
    """Tests for config show command."""

    def test_show_config(self, sample_project_path: Path) -> None:
        """Test showing configuration."""
        result = runner.invoke(
            app, ["config", "show", "--path", str(sample_project_path)]
        )
        assert result.exit_code == 0
        assert "Configuration" in result.stdout
        assert "http://localhost:8081" in result.stdout

    def test_show_config_json(self, sample_project_path: Path) -> None:
        """Test showing configuration in JSON format."""
        result = runner.invoke(
            app,
            ["config", "show", "--path", str(sample_project_path), "--format", "json"],
        )
        assert result.exit_code == 0
        assert "url" in result.stdout
        assert "localhost" in result.stdout

    def test_show_config_no_project(self, tmp_path: Path) -> None:
        """Test showing config when no dli.yaml exists."""
        result = runner.invoke(app, ["config", "show", "--path", str(tmp_path)])
        assert result.exit_code == 0
        assert "No dli.yaml" in result.stdout


class TestConfigStatus:
    """Tests for config status command."""

    def test_check_status(self, sample_project_path: Path) -> None:
        """Test checking server status (mock mode)."""
        result = runner.invoke(
            app, ["config", "status", "--path", str(sample_project_path)]
        )
        assert result.exit_code == 0
        assert "healthy" in result.stdout.lower()

    def test_check_status_no_project(self, tmp_path: Path) -> None:
        """Test checking status when no dli.yaml exists."""
        result = runner.invoke(app, ["config", "status", "--path", str(tmp_path)])
        assert result.exit_code == 0
        assert "No dli.yaml" in result.stdout
