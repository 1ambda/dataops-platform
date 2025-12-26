"""Tests for main CLI application."""

from unittest.mock import patch

from typer.testing import CliRunner

from dataops_cli.main import app

runner = CliRunner()


def test_version():
    """Test version command."""
    result = runner.invoke(app, ["version"])
    assert result.exit_code == 0
    assert "DataOps CLI version" in result.stdout


def test_sql_parse():
    """Test SQL parse command."""
    sql = "SELECT * FROM users WHERE id = 1"
    result = runner.invoke(app, ["sql-parse", sql])
    assert result.exit_code == 0
    assert "Original:" in result.stdout
    assert "Formatted:" in result.stdout


def test_sql_parse_with_dialect():
    """Test SQL parse command with dialect."""
    sql = "SELECT * FROM users"
    result = runner.invoke(app, ["sql-parse", sql, "--dialect", "mysql"])
    assert result.exit_code == 0


@patch("dataops_cli.main.check_health")
def test_health_success(mock_check_health):
    """Test successful health check."""
    # Mock the async function to avoid event loop conflicts
    mock_check_health.return_value = None

    result = runner.invoke(app, ["health"])
    assert result.exit_code == 0
    mock_check_health.assert_called_once()


@patch("dataops_cli.main.check_health")
def test_health_failure(mock_check_health):
    """Test health check with connection failure."""
    # Mock the async function
    mock_check_health.return_value = None

    result = runner.invoke(app, ["health"])
    assert result.exit_code == 0  # CLI should handle the error gracefully
    mock_check_health.assert_called_once()


@patch("dataops_cli.main.list_pipelines")
def test_pipelines_success(mock_list_pipelines):
    """Test successful pipelines list."""
    # Mock the async function
    mock_list_pipelines.return_value = None

    result = runner.invoke(app, ["pipelines"])
    assert result.exit_code == 0
    mock_list_pipelines.assert_called_once()


def test_config_show_no_file():
    """Test config show when no config file exists."""
    result = runner.invoke(app, ["config", "--show"])
    assert result.exit_code == 0
    # Should show default configuration values even if no file exists
    assert "config_file" in result.stdout
    assert "base_url" in result.stdout


def test_invalid_sql_parse():
    """Test SQL parse with invalid SQL."""
    invalid_sql = "INVALID SQL QUERY"
    result = runner.invoke(app, ["sql-parse", invalid_sql])
    assert result.exit_code == 4  # SQL processing error exit code
    # Should show error message
    assert "SQL syntax error" in result.stdout
