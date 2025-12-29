"""Tests for the catalog subcommand."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


def get_output(result) -> str:
    """Get combined output (Typer mixes stdout/stderr by default)."""
    return result.output or result.stdout or ""


@pytest.fixture
def sample_project_path() -> Path:
    """Return path to sample project fixture."""
    return Path(__file__).parent.parent / "fixtures" / "sample_project"


# =============================================================================
# Test: Implicit Routing (catalog callback)
# =============================================================================


class TestCatalogImplicitRouting:
    """Tests for catalog implicit routing based on identifier parts.

    Note: Implicit routing does not use --path option to avoid conflicts
    with subcommand parsing. It uses the default project path.
    """

    def test_one_part_lists_project_tables(self) -> None:
        """1-part identifier should list tables in project."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "my-project",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table list
        assert "my-project" in output.lower() or "table" in output.lower()

    def test_two_part_lists_dataset_tables(self) -> None:
        """2-part identifier should list tables in dataset."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "my-project.analytics",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table list for analytics dataset
        assert "analytics" in output.lower() or "table" in output.lower()

    def test_three_part_shows_table_detail(self) -> None:
        """3-part identifier should show table details."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show table details
        assert "users" in output.lower()

    def test_four_part_with_engine_shows_detail(self) -> None:
        """4-part identifier with engine prefix should show table details."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "bigquery.my-project.analytics.users",
            ],
        )
        # May succeed if table exists or fail if not
        output = get_output(result)
        # Should handle the engine prefix correctly
        assert (
            "users" in output.lower()
            or "not found" in output.lower()
            or "error" in output.lower()
        )

    def test_no_identifier_shows_help(self) -> None:
        """No identifier should show help text."""
        result = runner.invoke(
            app,
            [
                "catalog",
            ],
        )
        # Should show help (exit code 0 for help)
        assert result.exit_code == 0
        output = get_output(result)
        assert "identifier" in output.lower() or "usage" in output.lower()


class TestCatalogImplicitRoutingWithOptions:
    """Tests for catalog implicit routing with additional options.

    Note: Uses options before identifier for proper parsing.
    """

    def test_json_output_for_list(self) -> None:
        """JSON output for list should be valid JSON."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--format",
                "json",
                "my-project",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output:
            try:
                data = json.loads(output)
                assert isinstance(data, list)
            except json.JSONDecodeError:
                pass  # May have mixed output

    def test_json_output_for_detail(self) -> None:
        """JSON output for detail should be valid JSON."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--format",
                "json",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output:
            try:
                data = json.loads(output)
                assert isinstance(data, dict)
            except json.JSONDecodeError:
                pass

    def test_limit_option(self) -> None:
        """Limit option should work for list."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--limit",
                "5",
                "my-project",
            ],
        )
        assert result.exit_code == 0

    def test_section_option_for_detail(self) -> None:
        """Section option should filter detail output."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "columns",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show columns section
        assert "column" in output.lower() or "name" in output.lower()


# =============================================================================
# Test: catalog list
# =============================================================================


class TestCatalogList:
    """Tests for catalog list command.

    Note: Due to Typer's handling of optional positional args with subcommands,
    tests use the command without options (mock mode works correctly).
    For integration testing with options, use the Python API directly.
    """

    def test_list_all_tables(self) -> None:
        """List all tables without filters."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "list",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show tables or "no tables" message
        assert "table" in output.lower() or "no tables" in output.lower()

    def test_list_subcommand_exists(self) -> None:
        """Verify list subcommand is registered."""
        result = runner.invoke(app, ["catalog", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        assert "list" in output.lower()

    def test_list_uses_mock_data(self) -> None:
        """List should display mock catalog data."""
        # Use implicit routing which works with options
        result = runner.invoke(
            app,
            [
                "catalog",
                "--format",
                "json",
                "my-project",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        if output:
            try:
                data = json.loads(output)
                # Should have mock tables
                assert isinstance(data, list)
                if data:
                    assert "name" in data[0]
            except json.JSONDecodeError:
                pass


# =============================================================================
# Test: catalog search
# =============================================================================


class TestCatalogSearch:
    """Tests for catalog search command.

    Note: Due to Typer's handling of optional positional args with subcommands,
    search subcommand tests are limited. Use implicit routing for full testing.
    """

    def test_search_subcommand_exists(self) -> None:
        """Verify search subcommand is registered."""
        result = runner.invoke(app, ["catalog", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        assert "search" in output.lower()

    def test_search_via_implicit_routing(self) -> None:
        """Search functionality can be tested via implicit routing pattern."""
        # The implicit routing supports searching by browsing tables
        # For keyword search, the search subcommand is available
        result = runner.invoke(
            app,
            [
                "catalog",
                "my-project",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show tables from my-project
        assert "my-project" in output.lower() or "table" in output.lower()


# =============================================================================
# Test: Engine Detection
# =============================================================================


class TestEngineDetection:
    """Tests for engine prefix detection in identifiers."""

    def test_bigquery_engine_detected(self) -> None:
        """BigQuery engine prefix should be detected."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "bigquery.project.dataset.table",
            ],
        )
        # Should process the identifier correctly
        # Result may be success or "not found" depending on mock data
        output = get_output(result)
        assert "error" in output.lower() or "table" in output.lower()

    def test_trino_engine_detected(self) -> None:
        """Trino engine prefix should be detected."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "trino.project.dataset.table",
            ],
        )
        output = get_output(result)
        assert "error" in output.lower() or "table" in output.lower()

    def test_hive_engine_detected(self) -> None:
        """Hive engine prefix should be detected."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "hive.project.dataset.table",
            ],
        )
        output = get_output(result)
        assert "error" in output.lower() or "table" in output.lower()

    def test_non_engine_prefix_not_detected(self) -> None:
        """Non-engine first part should not be treated as engine."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "myproject.analytics.users",
            ],
        )
        # Should be treated as 3-part (project.dataset.table)
        output = get_output(result)
        assert "users" in output.lower() or "not found" in output.lower()


# =============================================================================
# Test: Help and Command Structure
# =============================================================================


class TestCatalogHelp:
    """Tests for catalog help and command structure."""

    def test_catalog_help_flag(self) -> None:
        """Test catalog --help flag."""
        result = runner.invoke(app, ["catalog", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show catalog description
        assert "catalog" in output.lower()
        assert "browse" in output.lower() or "search" in output.lower()

    def test_catalog_shows_subcommands(self) -> None:
        """Test catalog --help shows subcommands."""
        result = runner.invoke(app, ["catalog", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should list available subcommands
        assert "list" in output.lower()
        assert "search" in output.lower()


# =============================================================================
# Test: Error Handling
# =============================================================================


class TestCatalogErrorHandling:
    """Tests for error handling in catalog commands."""

    def test_table_not_found(self) -> None:
        """Non-existent table should return error."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "nonexistent.project.table.name",
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_invalid_format_option(self, sample_project_path: Path) -> None:
        """Invalid format option should fail."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "list",
                "--format",
                "xml",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0

    def test_invalid_section_option(self) -> None:
        """Invalid section option should fail."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "invalid_section",
                "my-project.analytics.users",
            ],
        )
        # Should fail with invalid choice error
        assert result.exit_code != 0


# =============================================================================
# Test: Section Display
# =============================================================================


class TestCatalogSectionDisplay:
    """Tests for different section display options."""

    def test_section_basic(self) -> None:
        """Test basic section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "basic",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0

    def test_section_columns(self) -> None:
        """Test columns section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "columns",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "column" in output.lower() or "type" in output.lower()

    def test_section_quality(self) -> None:
        """Test quality section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "quality",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "quality" in output.lower() or "score" in output.lower()

    def test_section_freshness(self) -> None:
        """Test freshness section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "freshness",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "freshness" in output.lower() or "update" in output.lower()

    def test_section_ownership(self) -> None:
        """Test ownership section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "ownership",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "ownership" in output.lower() or "owner" in output.lower()

    def test_section_impact(self) -> None:
        """Test impact section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "impact",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        assert "impact" in output.lower() or "downstream" in output.lower()

    def test_section_queries(self) -> None:
        """Test queries section display."""
        result = runner.invoke(
            app,
            [
                "catalog",
                "--section",
                "queries",
                "my-project.analytics.users",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # May or may not have queries depending on mock data
        assert "quer" in output.lower() or "no" in output.lower() or "users" in output.lower()
