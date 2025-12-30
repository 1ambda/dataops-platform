"""Tests for info command."""

from __future__ import annotations

from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


class TestInfoCommand:
    """Tests for `dli info` command."""

    def test_info_exits_successfully(self) -> None:
        """Test info command exits with code 0."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0

    def test_info_shows_cli_version(self) -> None:
        """Test info command displays CLI version."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # The output should contain version information
        assert "CLI Version" in result.output or "Version" in result.output

    def test_info_shows_python_version(self) -> None:
        """Test info command displays Python version."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # Python version should be displayed
        assert "Python" in result.output

    def test_info_shows_platform(self) -> None:
        """Test info command displays platform information."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # Platform info should be displayed
        assert "Platform" in result.output

    def test_info_shows_dependencies(self) -> None:
        """Test info command displays dependency information."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # Should show some common dependencies
        output = result.output
        # At least one of these should appear
        assert any(
            dep in output
            for dep in ["sqlglot", "pydantic", "jinja2", "httpx", "rich"]
        )

    def test_info_output_is_table_format(self) -> None:
        """Test info command outputs in a table format."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        # Rich table output contains table formatting characters or title
        assert "DLI Environment Information" in result.output or "Property" in result.output
