"""Tests for the DLI CLI main module.

These tests verify CLI commands work correctly using Typer's CliRunner,
allowing direct testing without requiring CLI installation.
"""

from pathlib import Path

from typer.testing import CliRunner

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
        assert "validate" in result.stdout

    def test_no_args_shows_help(self):
        """Test running 'dli' with no args shows help (exit code 0 or 2)."""
        result = runner.invoke(app, [])
        # Typer with no_args_is_help=True may exit with code 0 or 2
        assert result.exit_code in [0, 2]
        assert "DataOps CLI" in result.stdout

    def test_command_help(self):
        """Test 'dli validate --help' shows command help."""
        result = runner.invoke(app, ["validate", "--help"])
        assert result.exit_code == 0
        assert "validate" in result.stdout.lower()
        assert "path" in result.stdout.lower()


class TestValidateCommand:
    """Tests for the validate command."""

    def test_validate_valid_sql(self, tmp_path: Path):
        """Test validating a valid SQL file."""
        sql_file = tmp_path / "valid.sql"
        sql_file.write_text("SELECT id, name FROM users WHERE active = true")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 0
        assert "Valid SQL" in result.stdout

    def test_validate_invalid_sql(self, tmp_path: Path):
        """Test validating an invalid SQL file."""
        sql_file = tmp_path / "invalid.sql"
        sql_file.write_text("SELEC id FROM users")  # typo in SELECT

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 1
        # Error message should be in output
        assert "Invalid SQL" in result.output

    def test_validate_with_warnings(self, tmp_path: Path):
        """Test validating SQL with warnings (SELECT *)."""
        sql_file = tmp_path / "warning.sql"
        sql_file.write_text("SELECT * FROM users")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 0
        assert "Valid SQL" in result.stdout
        assert "Warnings" in result.stdout

    def test_validate_strict_mode(self, tmp_path: Path):
        """Test validating with strict mode fails on warnings."""
        sql_file = tmp_path / "warning.sql"
        sql_file.write_text("SELECT * FROM users")

        result = runner.invoke(app, ["validate", str(sql_file), "--strict"])
        assert result.exit_code == 1
        # "Strict mode" message should be in output
        assert "Strict mode" in result.output

    def test_validate_nonexistent_file(self):
        """Test validating a non-existent file fails."""
        result = runner.invoke(app, ["validate", "/nonexistent/file.sql"])
        assert result.exit_code != 0

    def test_validate_with_dialect(self, tmp_path: Path):
        """Test validating with specific dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT id FROM users LIMIT 10")

        result = runner.invoke(app, ["validate", str(sql_file), "--dialect", "bigquery"])
        assert result.exit_code == 0


class TestRenderCommand:
    """Tests for the render command."""

    def test_render_simple_template(self, tmp_path: Path):
        """Test rendering a simple template."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM users WHERE dt = '{{ ds }}'")

        result = runner.invoke(app, ["render", str(template_file)])
        assert result.exit_code == 0
        assert "SELECT * FROM users" in result.stdout
        # ds should be replaced with today's date
        assert "{{ ds }}" not in result.stdout

    def test_render_with_params(self, tmp_path: Path):
        """Test rendering with custom parameters."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM {{ table_name }} WHERE id = {{ id }}")

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--param", "table_name=users",
                "--param", "id=42",
            ],
        )
        assert result.exit_code == 0
        assert "users" in result.stdout
        assert "42" in result.stdout

    def test_render_with_date(self, tmp_path: Path):
        """Test rendering with specific execution date."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM events WHERE dt = '{{ ds }}'")

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--date", "2025-01-15",
            ],
        )
        assert result.exit_code == 0
        assert "2025-01-15" in result.stdout

    def test_render_to_output_file(self, tmp_path: Path):
        """Test rendering to an output file."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM users")
        output_file = tmp_path / "output.sql"

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--output", str(output_file),
            ],
        )
        assert result.exit_code == 0
        assert output_file.exists()
        assert "SELECT * FROM users" in output_file.read_text()

    def test_render_invalid_date_format(self, tmp_path: Path):
        """Test rendering with invalid date format fails."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM events")

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--date", "01-15-2025",  # Invalid format
            ],
        )
        assert result.exit_code == 1
        assert "Invalid date format" in result.output

    def test_render_invalid_param_format(self, tmp_path: Path):
        """Test rendering with invalid param format fails."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT * FROM events")

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--param", "invalid_param",  # Missing =
            ],
        )
        assert result.exit_code == 1
        assert "Invalid parameter format" in result.output

    def test_render_nonexistent_template(self):
        """Test rendering a non-existent template fails."""
        result = runner.invoke(app, ["render", "/nonexistent/template.sql"])
        assert result.exit_code != 0


class TestMetricSubcommand:
    """Tests for the metric subcommand."""

    def test_metric_help(self):
        """Test 'dli metric --help' shows command help."""
        result = runner.invoke(app, ["metric", "--help"])
        assert result.exit_code == 0
        assert "list" in result.stdout
        assert "get" in result.stdout
        assert "run" in result.stdout

    def test_metric_list_help(self):
        """Test 'dli metric list --help' shows command help."""
        result = runner.invoke(app, ["metric", "list", "--help"])
        assert result.exit_code == 0
        assert "--format" in result.stdout


class TestDatasetSubcommand:
    """Tests for the dataset subcommand."""

    def test_dataset_help(self):
        """Test 'dli dataset --help' shows command help."""
        result = runner.invoke(app, ["dataset", "--help"])
        assert result.exit_code == 0
        assert "list" in result.stdout
        assert "get" in result.stdout
        assert "run" in result.stdout

    def test_dataset_list_help(self):
        """Test 'dli dataset list --help' shows command help."""
        result = runner.invoke(app, ["dataset", "list", "--help"])
        assert result.exit_code == 0
        assert "--format" in result.stdout


class TestServerSubcommand:
    """Tests for the server subcommand."""

    def test_server_help(self):
        """Test 'dli server --help' shows command help."""
        result = runner.invoke(app, ["server", "--help"])
        assert result.exit_code == 0
        assert "config" in result.stdout
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


class TestValidateEdgeCases:
    """Edge case tests for the validate command."""

    def test_validate_empty_sql_file(self, tmp_path: Path):
        """Test validating an empty SQL file."""
        sql_file = tmp_path / "empty.sql"
        sql_file.write_text("")

        result = runner.invoke(app, ["validate", str(sql_file)])
        # Empty SQL should fail validation
        assert result.exit_code == 1

    def test_validate_whitespace_only(self, tmp_path: Path):
        """Test validating a file with only whitespace."""
        sql_file = tmp_path / "whitespace.sql"
        sql_file.write_text("   \n\t\n  ")

        result = runner.invoke(app, ["validate", str(sql_file)])
        # Whitespace-only should fail validation
        assert result.exit_code == 1

    def test_validate_yaml_file(self, tmp_path: Path):
        """Test validating a YAML spec file."""
        yaml_file = tmp_path / "dataset.test.spec.yaml"
        # Provide a valid dataset spec with all required fields
        yaml_file.write_text("""name: iceberg.test.example
owner: test@example.com
team: "@test-team"
type: Dataset
query_type: DML
query_statement: "INSERT INTO table SELECT * FROM source"
""")

        result = runner.invoke(app, ["validate", str(yaml_file)])
        # YAML validation is now implemented, should succeed
        assert result.exit_code == 0
        assert "Valid spec" in result.stdout

    def test_validate_invalid_yaml_file(self, tmp_path: Path):
        """Test validating an invalid YAML spec file."""
        yaml_file = tmp_path / "spec.yaml"
        # Missing required fields: team, type, query_type
        yaml_file.write_text("name: test\nowner: test@example.com")

        result = runner.invoke(app, ["validate", str(yaml_file)])
        # Should fail due to missing required fields
        assert result.exit_code == 1
        assert "Invalid spec" in result.output


class TestRenderEdgeCases:
    """Edge case tests for the render command."""

    def test_render_empty_file(self, tmp_path: Path):
        """Test rendering an empty template file."""
        template_file = tmp_path / "empty.sql"
        template_file.write_text("")

        result = runner.invoke(app, ["render", str(template_file)])
        # Empty file should render successfully (empty output)
        assert result.exit_code == 0

    def test_render_multiple_params(self, tmp_path: Path):
        """Test rendering with multiple parameters."""
        template_file = tmp_path / "query.sql"
        template_file.write_text(
            "SELECT * FROM {{ schema }}.{{ table }} WHERE status = '{{ status }}'"
        )

        result = runner.invoke(
            app,
            [
                "render",
                str(template_file),
                "--param", "schema=analytics",
                "--param", "table=users",
                "--param", "status=active",
            ],
        )
        assert result.exit_code == 0
        assert "analytics.users" in result.stdout
        assert "active" in result.stdout


class TestCLIIntegration:
    """Integration tests for CLI workflows."""

    def test_validate_then_render(self, tmp_path: Path):
        """Test validating and then rendering a template."""
        template_file = tmp_path / "query.sql"
        template_file.write_text("SELECT id, name FROM users WHERE dt = '{{ ds }}'")

        # First validate
        result = runner.invoke(app, ["validate", str(template_file)])
        assert result.exit_code == 0

        # Then render
        result = runner.invoke(app, ["render", str(template_file), "--date", "2025-01-15"])
        assert result.exit_code == 0
        assert "2025-01-15" in result.stdout

    def test_validate_complex_query(self, tmp_path: Path):
        """Test validating a complex SQL query with CTEs."""
        sql_file = tmp_path / "complex.sql"
        sql_file.write_text("""
            WITH active_users AS (
                SELECT user_id, COUNT(*) as event_count
                FROM events
                WHERE dt >= DATE('2025-01-01')
                GROUP BY user_id
                HAVING COUNT(*) > 10
            )
            SELECT
                u.user_id,
                u.name,
                au.event_count
            FROM users u
            JOIN active_users au ON u.user_id = au.user_id
            ORDER BY au.event_count DESC
            LIMIT 100
        """)

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 0
        assert "Valid SQL" in result.stdout
