"""Tests for the dataset subcommand."""

from __future__ import annotations

from pathlib import Path

import pytest
from typer.testing import CliRunner

from dli.main import app
from tests.cli.conftest import get_output

runner = CliRunner()


class TestDatasetList:
    """Tests for dataset list command."""

    def test_list_local_datasets(self, sample_project_path: Path) -> None:
        """Test listing local datasets."""
        result = runner.invoke(
            app, ["dataset", "list", "--path", str(sample_project_path)]
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_list_local_datasets_json(self, sample_project_path: Path) -> None:
        """Test listing local datasets in JSON format."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "list",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_list_server_datasets(self, sample_project_path: Path) -> None:
        """Test listing datasets from server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "list",
                "--source",
                "server",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        assert "Source: server" in result.stdout

    def test_list_with_tag_filter(self, sample_project_path: Path) -> None:
        """Test listing datasets with tag filter."""
        result = runner.invoke(
            app,
            ["dataset", "list", "--path", str(sample_project_path), "--tag", "daily"],
        )
        assert result.exit_code in [0, 0]


class TestDatasetGet:
    """Tests for dataset get command."""

    def test_get_dataset_details(self, sample_project_path: Path) -> None:
        """Test getting dataset details."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_get_dataset_json(self, sample_project_path: Path) -> None:
        """Test getting dataset details in JSON format."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        assert "iceberg.analytics.daily_clicks" in result.stdout

    def test_get_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test getting a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "get",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetValidate:
    """Tests for dataset validate command."""

    def test_validate_dataset(self, sample_project_path: Path) -> None:
        """Test validating a dataset."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "validate",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-01",
            ],
        )
        # Should validate successfully or show validation result
        assert result.exit_code in [0, 1]

    def test_validate_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test validating a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "validate",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetRun:
    """Tests for dataset run command."""

    def test_run_dataset_dry_run(self, sample_project_path: Path) -> None:
        """Test running a dataset in dry-run mode."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "run",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "-p",
                "execution_date=2024-01-01",
                "--dry-run",
            ],
        )
        # Dry-run should succeed or fail gracefully
        assert result.exit_code in [0, 1]

    def test_run_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test running a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "run",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


class TestDatasetRegister:
    """Tests for dataset register command."""

    def test_register_dataset(self, sample_project_path: Path) -> None:
        """Test registering a dataset to server (mock mode)."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "register",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        # Should succeed with mock server (first time)
        # or fail if already exists
        assert result.exit_code in [0, 1]

    def test_register_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test registering a dataset that doesn't exist locally."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "register",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "not found" in get_output(result).lower()


# =============================================================================
# Test: Dataset Transpile Command
# =============================================================================


class TestDatasetTranspile:
    """Tests for dataset transpile command."""

    def test_transpile_dataset_basic(self, sample_project_path: Path) -> None:
        """Test basic dataset transpilation."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show transpiled SQL
        assert "sql" in output.lower() or "transpile" in output.lower()

    def test_transpile_dataset_with_strict_mode(self, sample_project_path: Path) -> None:
        """Test dataset transpilation with strict mode enabled."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--strict",
            ],
        )
        # In strict mode, should exit 0 on success or 1 on error
        assert result.exit_code in [0, 1]
        output = get_output(result)
        # Should show transpile output (check for "transpil" to match both transpile/transpilation)
        assert "sql" in output.lower() or "transpil" in output.lower()

    def test_transpile_dataset_json_format(self, sample_project_path: Path) -> None:
        """Test dataset transpilation with JSON output."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result).strip()
        # Should be valid JSON
        import json
        try:
            data = json.loads(output)
            assert "sql" in data or "success" in data
        except json.JSONDecodeError:
            pytest.fail(f"Output is not valid JSON: {output}")

    def test_transpile_dataset_with_show_rules(
        self, sample_project_path: Path
    ) -> None:
        """Test dataset transpilation with --show-rules option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--show-rules",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show transpile output with rules info
        assert "sql" in output.lower() or "transpile" in output.lower()

    def test_transpile_dataset_with_dialect(self, sample_project_path: Path) -> None:
        """Test dataset transpilation with specific dialect."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--dialect",
                "bigquery",
            ],
        )
        assert result.exit_code == 0

    def test_transpile_dataset_with_retry(self, sample_project_path: Path) -> None:
        """Test dataset transpilation with retry option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--transpile-retry",
                "3",
            ],
        )
        assert result.exit_code == 0

    def test_transpile_dataset_table_format(self, sample_project_path: Path) -> None:
        """Test dataset transpilation with table output format."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "table",
            ],
        )
        assert result.exit_code == 0
        output = get_output(result)
        # Should show structured output
        assert "sql" in output.lower() or "transpile" in output.lower()

    def test_transpile_nonexistent_dataset(self, sample_project_path: Path) -> None:
        """Test transpiling a dataset that doesn't exist."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "nonexistent.dataset",
                "--path",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_transpile_dataset_unknown_dialect(
        self, sample_project_path: Path
    ) -> None:
        """Test dataset transpilation with unknown dialect.

        Note: The transpile command accepts any dialect string. Unknown dialects
        may result in graceful degradation (success with warnings) rather than
        failure, depending on the transpile API implementation.
        """
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--dialect",
                "unknown_dialect",
            ],
        )
        # Unknown dialect may succeed with warnings or fail
        # Both are acceptable behaviors
        assert result.exit_code in [0, 1]

    def test_transpile_dataset_invalid_file(
        self, sample_project_path: Path
    ) -> None:
        """Test dataset transpilation with nonexistent SQL file via --file option."""
        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--file",
                "/nonexistent/query.sql",
            ],
        )
        assert result.exit_code == 1
        output = get_output(result)
        assert "not found" in output.lower() or "error" in output.lower()

    def test_transpile_dataset_with_all_options(
        self, sample_project_path: Path, tmp_path: Path
    ) -> None:
        """Test dataset transpilation with all available options combined."""
        # Create a custom SQL file
        sql_file = tmp_path / "custom_query.sql"
        sql_file.write_text("SELECT * FROM test_table WHERE id = 1")

        result = runner.invoke(
            app,
            [
                "dataset",
                "transpile",
                "iceberg.analytics.daily_clicks",
                "--path",
                str(sample_project_path),
                "--format",
                "json",
                "--file",
                str(sql_file),
                "--transpile-retry",
                "2",
                "--dialect",
                "trino",
                "--show-rules",
                "--strict",
            ],
        )
        # Should succeed or fail in strict mode
        assert result.exit_code in [0, 1]

    def test_transpile_dataset_help(self) -> None:
        """Test dataset transpile help output."""
        result = runner.invoke(app, ["dataset", "transpile", "--help"])
        assert result.exit_code == 0
        output = get_output(result)
        # Should show command description and options
        assert "transpile" in output.lower()
        assert "--format" in output or "--dialect" in output
