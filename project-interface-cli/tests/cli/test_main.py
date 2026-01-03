"""Tests for the DLI CLI main module.

These tests verify CLI commands work correctly using Typer's CliRunner,
allowing direct testing without requiring CLI installation.
"""

from typer.testing import CliRunner

from tests.conftest import strip_ansi

from dli import __version__
from dli.main import app

# CliRunner for testing Typer applications
runner = CliRunner()


class TestVersionCommand:
    """Tests for the version command."""

    def test_version_command(self):
        """Test 'dli version' command displays version."""
        result = runner.invoke(app, ["version"])
        assert result.exit_code == 0
        assert __version__ in result.stdout

    def test_version_flag(self):
        """Test 'dli --version' flag displays version and exits."""
        result = runner.invoke(app, ["--version"])
        assert result.exit_code == 0
        assert __version__ in result.stdout

    def test_version_short_flag(self):
        """Test 'dli -v' short flag displays version and exits."""
        result = runner.invoke(app, ["-v"])
        assert result.exit_code == 0
        assert __version__ in result.stdout


class TestHelpCommand:
    """Tests for the help functionality."""

    def test_help_flag(self):
        """Test 'dli --help' displays help text."""
        result = runner.invoke(app, ["--help"])
        assert result.exit_code == 0
        assert "DataOps CLI" in result.stdout
        assert "version" in result.stdout
        # Verify current commands exist
        assert "dataset" in result.stdout
        assert "metric" in result.stdout
        assert "config" in result.stdout

    def test_no_args_shows_help(self):
        """Test running 'dli' with no args shows help (exit code 0 or 2)."""
        result = runner.invoke(app, [])
        # Typer with no_args_is_help=True may exit with code 0 or 2
        assert result.exit_code in [0, 2]
        assert "DataOps CLI" in result.stdout

    def test_dataset_validate_help(self):
        """Test 'dli dataset validate --help' shows command help."""
        result = runner.invoke(app, ["dataset", "validate", "--help"])
        assert result.exit_code == 0
        assert "validate" in result.stdout.lower()

    def test_metric_validate_help(self):
        """Test 'dli metric validate --help' shows command help."""
        result = runner.invoke(app, ["metric", "validate", "--help"])
        assert result.exit_code == 0
        assert "validate" in result.stdout.lower()


class TestMetricSubcommand:
    """Tests for the metric subcommand."""

    def test_metric_help(self):
        """Test 'dli metric --help' shows command help."""
        result = runner.invoke(app, ["metric", "--help"])
        assert result.exit_code == 0
        assert "list" in result.stdout
        assert "get" in result.stdout
        assert "run" in result.stdout
        assert "validate" in result.stdout

    def test_metric_list_help(self):
        """Test 'dli metric list --help' shows command help."""
        result = runner.invoke(app, ["metric", "list", "--help"])
        assert result.exit_code == 0
        assert "--format" in strip_ansi(result.stdout)


class TestDatasetSubcommand:
    """Tests for the dataset subcommand."""

    def test_dataset_help(self):
        """Test 'dli dataset --help' shows command help."""
        result = runner.invoke(app, ["dataset", "--help"])
        assert result.exit_code == 0
        assert "list" in result.stdout
        assert "get" in result.stdout
        assert "run" in result.stdout
        assert "validate" in result.stdout

    def test_dataset_list_help(self):
        """Test 'dli dataset list --help' shows command help."""
        result = runner.invoke(app, ["dataset", "list", "--help"])
        assert result.exit_code == 0
        assert "--format" in strip_ansi(result.stdout)


class TestConfigSubcommand:
    """Tests for the config subcommand (renamed from server)."""

    def test_config_help(self):
        """Test 'dli config --help' shows command help."""
        result = runner.invoke(app, ["config", "--help"])
        assert result.exit_code == 0
        assert "show" in result.stdout
        assert "status" in result.stdout


class TestInfoCommand:
    """Tests for the info command."""

    def test_info_command(self):
        """Test 'dli info' displays environment information."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        assert "CLI Version" in result.stdout
        assert __version__ in result.stdout
        assert "Python Version" in result.stdout

    def test_info_shows_dependencies(self):
        """Test 'dli info' shows dependency status."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        assert "sqlglot" in result.stdout
        assert "pydantic" in result.stdout

    def test_info_shows_platform(self):
        """Test 'dli info' shows platform information."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        assert "Platform" in result.stdout
        assert "Python Path" in result.stdout

    def test_info_shows_rich_dependency(self):
        """Test 'dli info' shows rich is installed."""
        result = runner.invoke(app, ["info"])
        assert result.exit_code == 0
        assert "rich" in result.stdout


class TestDeprecatedCommands:
    """Tests verifying deprecated commands are properly removed."""

    def test_validate_top_level_removed(self):
        """Test that top-level 'dli validate' command is removed."""
        result = runner.invoke(app, ["validate", "--help"])
        # Should fail since command is removed
        assert result.exit_code != 0

    def test_render_removed(self):
        """Test that 'dli render' command is removed."""
        result = runner.invoke(app, ["render", "--help"])
        # Should fail since command is removed
        assert result.exit_code != 0

    def test_server_removed(self):
        """Test that 'dli server' command is removed (renamed to config)."""
        result = runner.invoke(app, ["server", "--help"])
        # Should fail since command is renamed to 'config'
        assert result.exit_code != 0


class TestWorkflowSubcommand:
    """Tests for the workflow subcommand."""

    def test_workflow_help(self):
        """Test 'dli workflow --help' shows command help."""
        result = runner.invoke(app, ["workflow", "--help"])
        assert result.exit_code == 0
        assert "run" in result.stdout
        assert "backfill" in result.stdout
        assert "status" in result.stdout
        assert "list" in result.stdout


class TestQualitySubcommand:
    """Tests for the quality subcommand."""

    def test_quality_help(self):
        """Test 'dli quality --help' shows command help."""
        result = runner.invoke(app, ["quality", "--help"])
        assert result.exit_code == 0
        assert "list" in result.stdout
        assert "run" in result.stdout


class TestLineageSubcommand:
    """Tests for the lineage subcommand."""

    def test_lineage_help(self):
        """Test 'dli lineage --help' shows command help."""
        result = runner.invoke(app, ["lineage", "--help"])
        assert result.exit_code == 0
        assert "show" in result.stdout
        assert "upstream" in result.stdout
        assert "downstream" in result.stdout


class TestCatalogSubcommand:
    """Tests for the catalog subcommand."""

    def test_catalog_help(self):
        """Test 'dli catalog --help' shows command help."""
        result = runner.invoke(app, ["catalog", "--help"])
        assert result.exit_code == 0


class TestTranspileSubcommand:
    """Tests for the transpile subcommand.

    Note: There is no top-level 'dli transpile' command.
    Transpile is a subcommand of 'dli dataset' and 'dli metric'.
    """

    def test_dataset_transpile_help(self):
        """Test 'dli dataset transpile --help' shows command help."""
        result = runner.invoke(app, ["dataset", "transpile", "--help"])
        assert result.exit_code == 0
        assert "transpile" in result.stdout.lower()
        assert "--dialect" in result.stdout

    def test_metric_transpile_help(self):
        """Test 'dli metric transpile --help' shows command help."""
        result = runner.invoke(app, ["metric", "transpile", "--help"])
        assert result.exit_code == 0
        assert "transpile" in result.stdout.lower()
        assert "--dialect" in result.stdout
