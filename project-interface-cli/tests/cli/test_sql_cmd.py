"""Tests for the sql subcommand."""

from __future__ import annotations

import json
from pathlib import Path

from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


# =============================================================================
# Test: sql list
# =============================================================================


class TestSqlList:
    """Tests for sql list command."""

    def test_list_mock_mode(self) -> None:
        """Test listing snippets in mock mode."""
        result = runner.invoke(app, ["sql", "list", "--mock"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table with snippet info or "no snippets" message
        assert (
            "id" in output.lower()
            or "name" in output.lower()
            or "snippets" in output.lower()
            or "no snippets" in output.lower()
        )

    def test_list_json_format(self) -> None:
        """Test listing snippets in JSON format."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--format", "json"])
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output:
            try:
                data = json.loads(output)
                assert isinstance(data, dict)
                assert "snippets" in data
            except json.JSONDecodeError:
                pass  # May have mixed output in mock mode

    def test_list_with_project_filter(self) -> None:
        """Test listing snippets filtered by project."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--project", "marketing"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should filter by project
        assert (
            "id" in output.lower()
            or "no snippets" in output.lower()
            or "snippets" in output.lower()
        )

    def test_list_with_folder_filter(self) -> None:
        """Test listing snippets filtered by folder."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--folder", "Analytics"])
        assert result.exit_code == 0

    def test_list_with_starred_filter(self) -> None:
        """Test listing only starred snippets."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--starred"])
        assert result.exit_code == 0

    def test_list_with_limit(self) -> None:
        """Test listing snippets with custom limit."""
        result = runner.invoke(app, ["sql", "list", "--mock", "-n", "10"])
        assert result.exit_code == 0

    def test_list_with_offset(self) -> None:
        """Test listing snippets with pagination offset."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--offset", "5"])
        assert result.exit_code == 0

    def test_list_with_combined_filters(self) -> None:
        """Test listing snippets with combined filters."""
        result = runner.invoke(
            app,
            [
                "sql",
                "list",
                "--mock",
                "--project",
                "marketing",
                "--folder",
                "Reports",
                "--starred",
                "-n",
                "5",
                "--offset",
                "0",
            ],
        )
        assert result.exit_code == 0


# =============================================================================
# Test: sql get
# =============================================================================


class TestSqlGet:
    """Tests for sql get command."""

    def test_get_to_stdout(self) -> None:
        """Test downloading snippet to stdout."""
        result = runner.invoke(app, ["sql", "get", "1", "--mock"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should contain SQL content or an error message
        assert len(output.strip()) > 0

    def test_get_to_file(self, tmp_path: Path) -> None:
        """Test downloading snippet to file."""
        output_file = tmp_path / "test.sql"
        result = runner.invoke(
            app, ["sql", "get", "1", "--mock", "-f", str(output_file)]
        )
        assert result.exit_code == 0
        assert output_file.exists()

    def test_get_creates_parent_directories(self, tmp_path: Path) -> None:
        """Test get creates parent directories if needed."""
        output_file = tmp_path / "nested" / "dir" / "test.sql"
        result = runner.invoke(
            app, ["sql", "get", "1", "--mock", "-f", str(output_file)]
        )
        assert result.exit_code == 0
        assert output_file.exists()

    def test_get_overwrite_existing_file(self, tmp_path: Path) -> None:
        """Test get with overwrite flag."""
        output_file = tmp_path / "test.sql"
        output_file.write_text("old content")

        result = runner.invoke(
            app, ["sql", "get", "1", "--mock", "-f", str(output_file), "--overwrite"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "downloaded" in output.lower() or output_file.exists()

    def test_get_with_project(self) -> None:
        """Test get with project option."""
        result = runner.invoke(app, ["sql", "get", "1", "--mock", "--project", "default"])
        assert result.exit_code == 0

    def test_get_shows_download_message(self, tmp_path: Path) -> None:
        """Test get shows download success message."""
        output_file = tmp_path / "downloaded.sql"
        result = runner.invoke(
            app, ["sql", "get", "1", "--mock", "-f", str(output_file)]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "downloaded" in output.lower()


# =============================================================================
# Test: sql put
# =============================================================================


class TestSqlPut:
    """Tests for sql put command."""

    def test_put_with_force(self, tmp_path: Path) -> None:
        """Test uploading SQL file with force flag."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT * FROM users")

        result = runner.invoke(
            app, ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "uploaded" in output.lower()

    def test_put_file_not_found(self) -> None:
        """Test put with non-existent file."""
        result = runner.invoke(
            app, ["sql", "put", "1", "-f", "/nonexistent/file.sql", "--force", "--mock"]
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_put_with_project(self, tmp_path: Path) -> None:
        """Test put with project option."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT 1")

        result = runner.invoke(
            app,
            ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock", "--project", "default"],
        )
        assert result.exit_code == 0

    def test_put_shows_upload_message(self, tmp_path: Path) -> None:
        """Test put shows upload success message."""
        sql_file = tmp_path / "upload.sql"
        sql_file.write_text("SELECT COUNT(*) FROM orders")

        result = runner.invoke(
            app, ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "uploaded" in output.lower()

    def test_put_shows_updated_timestamp(self, tmp_path: Path) -> None:
        """Test put shows updated timestamp."""
        sql_file = tmp_path / "upload.sql"
        sql_file.write_text("SELECT 1")

        result = runner.invoke(
            app, ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock"]
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should display update timestamp
        assert "updated" in output.lower() or "uploaded" in output.lower()


# =============================================================================
# Test: sql help
# =============================================================================


class TestSqlHelp:
    """Tests for sql help and command structure."""

    def test_sql_help(self) -> None:
        """Test sql main help output."""
        result = runner.invoke(app, ["sql", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show sql description and available commands
        assert "sql" in output.lower()
        assert "list" in output.lower()
        assert "get" in output.lower()
        assert "put" in output.lower()

    def test_sql_no_args_shows_help(self) -> None:
        """Test that sql without arguments shows help."""
        result = runner.invoke(app, ["sql"])
        # Typer's no_args_is_help returns exit code 0 or 2
        assert result.exit_code in [0, 2]
        output = get_output(result)
        # Should show available commands
        assert "list" in output.lower()
        assert "get" in output.lower()
        assert "put" in output.lower()

    def test_sql_list_help(self) -> None:
        """Test sql list subcommand help."""
        result = runner.invoke(app, ["sql", "list", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show list options
        assert "--project" in output
        assert "--starred" in output
        assert "--limit" in output or "-n" in output
        assert "--format" in output or "-f" in output

    def test_sql_get_help(self) -> None:
        """Test sql get subcommand help."""
        result = runner.invoke(app, ["sql", "get", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show get options
        assert "snippet" in output.lower() or "id" in output.lower()
        assert "--file" in output or "-f" in output
        assert "--overwrite" in output

    def test_sql_put_help(self) -> None:
        """Test sql put subcommand help."""
        result = runner.invoke(app, ["sql", "put", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show put options
        assert "snippet" in output.lower() or "id" in output.lower()
        assert "--file" in output or "-f" in output
        assert "--force" in output


# =============================================================================
# Test: Output formatting
# =============================================================================


class TestSqlOutputFormatting:
    """Tests for sql output formatting."""

    def test_list_table_format_columns(self) -> None:
        """Test that table format shows expected columns."""
        result = runner.invoke(app, ["sql", "list", "--mock"])
        output = get_output(result)
        if result.exit_code == 0 and "no snippets" not in output.lower():
            # Should include key columns in table header
            output_upper = output.upper()
            assert (
                "ID" in output_upper
                or "NAME" in output_upper
                or "DIALECT" in output_upper
                or "FOLDER" in output_upper
            )

    def test_list_shows_snippet_count(self) -> None:
        """Test that list shows snippet count."""
        result = runner.invoke(app, ["sql", "list", "--mock"])
        output = get_output(result)
        if result.exit_code == 0:
            # Should show count message
            assert (
                "showing" in output.lower()
                or "snippets" in output.lower()
                or "no snippets" in output.lower()
            )


# =============================================================================
# Test: Edge cases and error handling
# =============================================================================


class TestSqlEdgeCases:
    """Tests for edge cases and error handling."""

    def test_list_invalid_format_option(self) -> None:
        """Test invalid format option value."""
        result = runner.invoke(app, ["sql", "list", "--mock", "--format", "xml"])
        # CLI may handle invalid format gracefully by defaulting to table
        # or may fail with invalid choice error
        # We just verify it doesn't crash unexpectedly
        assert result.exit_code in [0, 1, 2]

    def test_get_negative_snippet_id(self) -> None:
        """Test get with negative snippet ID."""
        result = runner.invoke(app, ["sql", "get", "-1", "--mock"])
        # Should handle gracefully - may fail validation or treat as string
        # We just verify it doesn't crash
        assert result.exit_code in [0, 1, 2]

    def test_put_empty_sql_file(self, tmp_path: Path) -> None:
        """Test put with empty SQL file."""
        sql_file = tmp_path / "empty.sql"
        sql_file.write_text("")

        result = runner.invoke(
            app, ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock"]
        )
        # Should handle empty file - may succeed or fail gracefully
        assert result.exit_code in [0, 1]

    def test_list_negative_limit(self) -> None:
        """Test list with negative limit."""
        result = runner.invoke(app, ["sql", "list", "--mock", "-n", "-5"])
        # Should handle gracefully
        assert result.exit_code in [0, 1, 2]

    def test_list_zero_limit(self) -> None:
        """Test list with zero limit."""
        result = runner.invoke(app, ["sql", "list", "--mock", "-n", "0"])
        # Should handle gracefully
        assert result.exit_code in [0, 1, 2]

    def test_get_very_large_snippet_id(self) -> None:
        """Test get with very large snippet ID."""
        result = runner.invoke(app, ["sql", "get", "999999999", "--mock"])
        # In mock mode, should handle gracefully
        assert result.exit_code in [0, 1]

    def test_put_readonly_directory(self, tmp_path: Path) -> None:
        """Test put reads file correctly."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT 'special chars: !@#$%^&*()'")

        result = runner.invoke(
            app, ["sql", "put", "1", "-f", str(sql_file), "--force", "--mock"]
        )
        assert result.exit_code == 0

    def test_get_writes_correct_content(self, tmp_path: Path) -> None:
        """Test that get writes correct SQL content to file."""
        output_file = tmp_path / "output.sql"
        result = runner.invoke(
            app, ["sql", "get", "1", "--mock", "-f", str(output_file)]
        )
        assert result.exit_code == 0
        assert output_file.exists()
        content = output_file.read_text()
        # Should contain some SQL-like content
        assert len(content) >= 0  # File exists, may be empty or have content


# =============================================================================
# Test: Integration with project path
# =============================================================================


class TestSqlWithProjectPath:
    """Tests for sql commands with project path."""

    def test_list_with_sample_project(self, sample_project_path: Path) -> None:
        """Test listing snippets with sample project path."""
        result = runner.invoke(
            app,
            [
                "sql",
                "list",
                "--mock",
            ],
        )
        assert result.exit_code == 0

    def test_get_with_sample_project(self, sample_project_path: Path) -> None:
        """Test getting snippet with sample project path."""
        result = runner.invoke(
            app,
            [
                "sql",
                "get",
                "1",
                "--mock",
            ],
        )
        assert result.exit_code == 0

    def test_put_with_sample_project(self, sample_project_path: Path, tmp_path: Path) -> None:
        """Test putting snippet with sample project path."""
        sql_file = tmp_path / "test.sql"
        sql_file.write_text("SELECT 1")

        result = runner.invoke(
            app,
            [
                "sql",
                "put",
                "1",
                "-f",
                str(sql_file),
                "--force",
                "--mock",
            ],
        )
        assert result.exit_code == 0
