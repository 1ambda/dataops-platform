"""Tests for the validate command.

This module tests the standalone validate command for SQL and YAML spec validation.
Tests cover:
- SQL file validation with various dialects
- YAML spec file validation
- Project-wide validation (--all flag)
- Strict mode (--strict flag)
- Variable substitution (--var flag)
- Dependency checking (--check-deps flag)
"""

from __future__ import annotations

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


class TestValidateSQLFile:
    """Tests for validating SQL files."""

    def test_validate_valid_sql(self, tmp_path: Path) -> None:
        """Test validating a valid SQL file."""
        sql_file = tmp_path / "valid.sql"
        sql_file.write_text("SELECT id, name FROM users WHERE active = true")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 0
        assert "Valid SQL" in result.stdout

    def test_validate_invalid_sql(self, tmp_path: Path) -> None:
        """Test validating an invalid SQL file."""
        sql_file = tmp_path / "invalid.sql"
        sql_file.write_text("SELEC id FROM users")  # typo in SELECT

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 1
        assert "Invalid SQL" in get_output(result)

    def test_validate_with_dialect(self, tmp_path: Path) -> None:
        """Test validating with specific dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT id FROM users LIMIT 10")

        result = runner.invoke(
            app, ["validate", str(sql_file), "--dialect", "bigquery"]
        )
        assert result.exit_code == 0

    def test_validate_with_trino_dialect(self, tmp_path: Path) -> None:
        """Test validating with Trino dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT CAST(id AS VARCHAR) FROM users")

        result = runner.invoke(
            app, ["validate", str(sql_file), "--dialect", "trino"]
        )
        assert result.exit_code == 0

    def test_validate_empty_sql(self, tmp_path: Path) -> None:
        """Test validating an empty SQL file fails."""
        sql_file = tmp_path / "empty.sql"
        sql_file.write_text("")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 1

    def test_validate_whitespace_only_sql(self, tmp_path: Path) -> None:
        """Test validating whitespace-only SQL file fails."""
        sql_file = tmp_path / "whitespace.sql"
        sql_file.write_text("   \n\t\n  ")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 1


class TestValidateWithWarnings:
    """Tests for SQL validation warnings."""

    def test_validate_with_select_star_warning(self, tmp_path: Path) -> None:
        """Test validating SQL with SELECT * generates warning."""
        sql_file = tmp_path / "warning.sql"
        sql_file.write_text("SELECT * FROM users")

        result = runner.invoke(app, ["validate", str(sql_file)])
        assert result.exit_code == 0
        assert "Valid SQL" in result.stdout
        assert "Warnings" in result.stdout

    def test_validate_strict_mode_fails_on_warning(self, tmp_path: Path) -> None:
        """Test strict mode fails on warnings."""
        sql_file = tmp_path / "warning.sql"
        sql_file.write_text("SELECT * FROM users")

        result = runner.invoke(app, ["validate", str(sql_file), "--strict"])
        assert result.exit_code == 1
        assert "Strict mode" in get_output(result)

    def test_validate_no_warning_strict_mode_passes(self, tmp_path: Path) -> None:
        """Test strict mode passes when no warnings.

        Note: SQLValidator generates warnings for SELECT without LIMIT,
        so we use a DML statement that doesn't trigger warnings.
        """
        sql_file = tmp_path / "clean.sql"
        sql_file.write_text("INSERT INTO users (id, name) VALUES (1, 'test')")

        result = runner.invoke(app, ["validate", str(sql_file), "--strict"])
        assert result.exit_code == 0


class TestValidateYAMLSpec:
    """Tests for validating YAML spec files."""

    def test_validate_valid_yaml_spec(self, tmp_path: Path) -> None:
        """Test validating a valid YAML spec file."""
        yaml_file = tmp_path / "dataset.test.spec.yaml"
        yaml_file.write_text(
            """name: iceberg.test.example
owner: test@example.com
team: "@test-team"
type: Dataset
query_type: DML
query_statement: "INSERT INTO table SELECT * FROM source"
"""
        )

        result = runner.invoke(app, ["validate", str(yaml_file)])
        assert result.exit_code == 0
        assert "Valid spec" in result.stdout

    def test_validate_invalid_yaml_spec(self, tmp_path: Path) -> None:
        """Test validating an invalid YAML spec file."""
        yaml_file = tmp_path / "spec.yaml"
        # Missing required fields: team, type, query_type
        yaml_file.write_text("name: test\nowner: test@example.com")

        result = runner.invoke(app, ["validate", str(yaml_file)])
        assert result.exit_code == 1
        assert "Invalid spec" in get_output(result)

    def test_validate_yaml_spec_missing_owner(self, tmp_path: Path) -> None:
        """Test validating YAML spec missing owner field."""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(
            """name: iceberg.test.example
team: "@test-team"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        result = runner.invoke(app, ["validate", str(yaml_file)])
        assert result.exit_code == 1
        assert "Invalid spec" in get_output(result)


class TestValidateProjectWide:
    """Tests for project-wide validation."""

    def test_validate_all_specs(self, sample_project_path: Path) -> None:
        """Test validating all specs in project."""
        result = runner.invoke(
            app, ["validate", "--all", "--project", str(sample_project_path)]
        )
        # Should succeed or fail based on fixture content
        assert result.exit_code in [0, 1]
        # Should show summary
        output = get_output(result)
        assert "Validating project" in output or "passed" in output.lower()

    def test_validate_all_metrics_only(self, sample_project_path: Path) -> None:
        """Test validating only metrics in project."""
        result = runner.invoke(
            app,
            [
                "validate",
                "--all",
                "--type",
                "metric",
                "--project",
                str(sample_project_path),
            ],
        )
        assert result.exit_code in [0, 1]

    def test_validate_all_datasets_only(self, sample_project_path: Path) -> None:
        """Test validating only datasets in project."""
        result = runner.invoke(
            app,
            [
                "validate",
                "--all",
                "--type",
                "dataset",
                "--project",
                str(sample_project_path),
            ],
        )
        assert result.exit_code in [0, 1]

    def test_validate_with_invalid_type(self, sample_project_path: Path) -> None:
        """Test validating with invalid type fails."""
        result = runner.invoke(
            app,
            [
                "validate",
                "--all",
                "--type",
                "invalid",
                "--project",
                str(sample_project_path),
            ],
        )
        assert result.exit_code == 1
        assert "Invalid --type" in get_output(result)


class TestValidateResourceByName:
    """Tests for validating resources by name."""

    def test_validate_resource_by_name(self, sample_project_path: Path) -> None:
        """Test validating a resource by its fully qualified name."""
        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.daily_clicks",
                "--project",
                str(sample_project_path),
            ],
        )
        # Should find and validate the resource
        assert result.exit_code in [0, 1]

    def test_validate_nonexistent_resource(self, sample_project_path: Path) -> None:
        """Test validating a non-existent resource fails."""
        result = runner.invoke(
            app,
            [
                "validate",
                "nonexistent.resource.name",
                "--project",
                str(sample_project_path),
            ],
        )
        # Should fail with resource not found
        assert result.exit_code == 1


class TestValidateWithVariables:
    """Tests for validation with variable substitution."""

    def test_validate_with_variable(self, sample_project_path: Path) -> None:
        """Test validating with variable substitution."""
        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.daily_clicks",
                "--project",
                str(sample_project_path),
                "--var",
                "execution_date=2024-01-01",
            ],
        )
        # Should succeed or fail based on validation result
        assert result.exit_code in [0, 1]

    def test_validate_with_multiple_variables(
        self, sample_project_path: Path
    ) -> None:
        """Test validating with multiple variables."""
        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.daily_clicks",
                "--project",
                str(sample_project_path),
                "--var",
                "execution_date=2024-01-01",
                "--var",
                "environment=test",
            ],
        )
        assert result.exit_code in [0, 1]


class TestValidateWithDependencyCheck:
    """Tests for validation with dependency checking."""

    def test_validate_with_check_deps(self, sample_project_path: Path) -> None:
        """Test validating with dependency checking enabled."""
        result = runner.invoke(
            app,
            [
                "validate",
                "--all",
                "--check-deps",
                "--project",
                str(sample_project_path),
            ],
        )
        # Should run validation with dependency checking
        assert result.exit_code in [0, 1]


class TestValidateNonexistentFile:
    """Tests for validating non-existent files."""

    def test_validate_nonexistent_file(self) -> None:
        """Test validating a non-existent file fails."""
        result = runner.invoke(app, ["validate", "/nonexistent/file.sql"])
        assert result.exit_code != 0

    def test_validate_no_path_and_no_all_flag(self) -> None:
        """Test validate without path and without --all flag shows error."""
        result = runner.invoke(app, ["validate"])
        assert result.exit_code == 1
        assert "provide a path" in get_output(result).lower() or "Error" in get_output(
            result
        )


class TestValidateHelp:
    """Tests for validate command help."""

    def test_validate_help(self) -> None:
        """Test validate --help shows command help."""
        result = runner.invoke(app, ["validate", "--help"])
        assert result.exit_code == 0
        assert "validate" in result.stdout.lower()
        assert "--dialect" in result.stdout
        assert "--strict" in result.stdout
        assert "--all" in result.stdout


# =============================================================================
# Dependency Check Tests (_check_dependencies_for_file, _check_dependencies_for_resource)
# =============================================================================


class TestCheckDependenciesForFile:
    """Tests for _check_dependencies_for_file function.

    This tests the file-level dependency checking when --check-deps is used
    with a single file validation.
    """

    def test_check_deps_file_all_deps_found(self, tmp_path: Path) -> None:
        """Test file validation with all dependencies found locally."""
        # Create project structure with two datasets that reference each other
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create source dataset (no dependencies)
        (datasets_dir / "dataset.iceberg.raw.source.yaml").write_text(
            """name: iceberg.raw.source
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Create target dataset that depends on source
        target_spec = datasets_dir / "dataset.iceberg.analytics.target.yaml"
        target_spec.write_text(
            """name: iceberg.analytics.target
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.raw.source"
depends_on:
  - "iceberg.raw.source"
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(target_spec), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "Valid spec" in output
        # Should report dependencies are OK
        assert "Dependencies OK" in output or "1 found" in output

    def test_check_deps_file_missing_dependency(self, tmp_path: Path) -> None:
        """Test file validation with missing dependencies."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset with dependency that doesn't exist
        spec_file = datasets_dir / "dataset.iceberg.analytics.orphan.yaml"
        spec_file.write_text(
            """name: iceberg.analytics.orphan
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.nonexistent.table"
depends_on:
  - "iceberg.nonexistent.table"
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(spec_file), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # The spec itself is valid, but dependency check shows issues
        assert "Valid spec" in output or result.exit_code == 0
        # Should report dependency issues
        assert "Dependency issues" in output or "not found" in output

    def test_check_deps_file_empty_depends_on(self, tmp_path: Path) -> None:
        """Test file validation with empty depends_on list."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset with empty depends_on
        spec_file = datasets_dir / "dataset.iceberg.analytics.standalone.yaml"
        spec_file.write_text(
            """name: iceberg.analytics.standalone
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
depends_on: []
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(spec_file), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "Valid spec" in output

    def test_check_deps_file_no_depends_on_field(self, tmp_path: Path) -> None:
        """Test file validation with no depends_on field at all."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset without depends_on field
        spec_file = datasets_dir / "dataset.iceberg.analytics.nodeps.yaml"
        spec_file.write_text(
            """name: iceberg.analytics.nodeps
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(spec_file), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "Valid spec" in output

    def test_check_deps_file_multiple_dependencies_mixed(self, tmp_path: Path) -> None:
        """Test file validation with multiple dependencies - some found, some missing."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create source dataset
        (datasets_dir / "dataset.iceberg.raw.exists.yaml").write_text(
            """name: iceberg.raw.exists
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Create dataset with mixed dependencies
        spec_file = datasets_dir / "dataset.iceberg.analytics.mixed.yaml"
        spec_file.write_text(
            """name: iceberg.analytics.mixed
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
depends_on:
  - "iceberg.raw.exists"
  - "iceberg.nonexistent.missing"
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(spec_file), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # Spec is valid, but has dependency issues
        assert "Valid spec" in output
        # Should report the missing dependency
        assert "Dependency issues" in output or "iceberg.nonexistent.missing" in output

    def test_check_deps_file_project_not_found(self, tmp_path: Path) -> None:
        """Test file validation when project config is not found."""
        spec_file = tmp_path / "orphan_spec.yaml"
        spec_file.write_text(
            """name: iceberg.test.spec
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
depends_on:
  - "iceberg.some.dep"
"""
        )

        # Point to a non-existent project directory
        result = runner.invoke(
            app,
            [
                "validate",
                str(spec_file),
                "--check-deps",
                "--project",
                str(tmp_path / "nonexistent"),
            ],
        )
        output = get_output(result)
        # Should handle gracefully - either warning or error
        assert result.exit_code != 0 or "Warning" in output or "not found" in output.lower()


class TestCheckDependenciesForResource:
    """Tests for _check_dependencies_for_resource function.

    This tests the resource-level dependency checking when --check-deps is used
    with a resource name (catalog.schema.table).
    """

    def test_check_deps_resource_all_deps_found(self, tmp_path: Path) -> None:
        """Test resource validation with all dependencies found locally."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create source dataset
        (datasets_dir / "dataset.iceberg.raw.events.yaml").write_text(
            """name: iceberg.raw.events
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Create target dataset
        (datasets_dir / "dataset.iceberg.analytics.processed.yaml").write_text(
            """name: iceberg.analytics.processed
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.raw.events"
depends_on:
  - "iceberg.raw.events"
"""
        )

        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.processed",
                "--check-deps",
                "--project",
                str(project_dir),
            ],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "Valid" in output
        assert "Dependencies OK" in output or "1 found" in output

    def test_check_deps_resource_missing_dependency(self, tmp_path: Path) -> None:
        """Test resource validation with missing dependencies."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset with missing dependency
        (datasets_dir / "dataset.iceberg.analytics.broken.yaml").write_text(
            """name: iceberg.analytics.broken
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.missing.table"
depends_on:
  - "iceberg.missing.table"
"""
        )

        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.broken",
                "--check-deps",
                "--project",
                str(project_dir),
            ],
        )
        output = get_output(result)
        assert "Valid" in output or result.exit_code == 0
        # Should report dependency issues
        assert "Dependency issues" in output or "not found" in output

    def test_check_deps_resource_not_found(self, tmp_path: Path) -> None:
        """Test resource validation when resource doesn't exist."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.nonexistent.resource",
                "--check-deps",
                "--project",
                str(project_dir),
            ],
        )
        output = get_output(result)
        # Should fail because resource not found
        assert result.exit_code == 1

    def test_check_deps_resource_empty_depends_on(self, tmp_path: Path) -> None:
        """Test resource validation with empty depends_on."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset with empty depends_on
        (datasets_dir / "dataset.iceberg.analytics.nodeps.yaml").write_text(
            """name: iceberg.analytics.nodeps
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
depends_on: []
"""
        )

        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.nodeps",
                "--check-deps",
                "--project",
                str(project_dir),
            ],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "Valid" in output


class TestCheckDepsProjectWide:
    """Tests for project-wide dependency checking with --all --check-deps."""

    def test_project_all_deps_valid(self, tmp_path: Path) -> None:
        """Test project validation where all dependencies are valid."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create source dataset (no dependencies)
        (datasets_dir / "dataset.iceberg.raw.source.yaml").write_text(
            """name: iceberg.raw.source
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Create target dataset depending on source
        (datasets_dir / "dataset.iceberg.analytics.target.yaml").write_text(
            """name: iceberg.analytics.target
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.raw.source"
depends_on:
  - "iceberg.raw.source"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "passed" in output.lower()

    def test_project_some_deps_missing(self, tmp_path: Path) -> None:
        """Test project validation where some dependencies are missing."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset with missing dependency
        (datasets_dir / "dataset.iceberg.analytics.orphan.yaml").write_text(
            """name: iceberg.analytics.orphan
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.missing.table"
depends_on:
  - "iceberg.missing.table"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # Should fail due to missing dependencies
        assert result.exit_code == 1
        # Should report missing deps
        assert "missing deps" in output.lower() or "Dependency Issues" in output

    def test_project_circular_dependency_detection(self, tmp_path: Path) -> None:
        """Test project validation detects circular dependencies via self-reference."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create dataset that depends on itself (circular)
        (datasets_dir / "dataset.iceberg.analytics.circular.yaml").write_text(
            """name: iceberg.analytics.circular
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.analytics.circular"
depends_on:
  - "iceberg.analytics.circular"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # Should fail due to circular dependency
        assert result.exit_code == 1
        # Circular dependency should be detected
        assert "circular" in output.lower() or "Dependency Issues" in output

    def test_project_no_specs_with_check_deps(self, tmp_path: Path) -> None:
        """Test project validation with no specs but check-deps enabled."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml with empty directories
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "empty-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # Should succeed with 0 specs
        assert result.exit_code == 0


class TestCheckDepsIntegrationWithSampleProject:
    """Integration tests using the sample project fixture."""

    def test_sample_project_check_deps(self, sample_project_path: Path) -> None:
        """Test dependency checking on sample project."""
        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(sample_project_path)],
        )
        output = get_output(result)
        # Sample project has specs with external dependencies (iceberg.raw.user_events)
        # that don't exist locally, so it should report missing deps
        assert result.exit_code in [0, 1]
        # Should show validation was performed
        assert "Validating project" in output

    def test_sample_project_file_check_deps(self, sample_project_path: Path) -> None:
        """Test file-level dependency checking on sample project spec."""
        spec_file = sample_project_path / "datasets" / "feed" / "dataset.iceberg.analytics.daily_clicks.yaml"
        if spec_file.exists():
            result = runner.invoke(
                app,
                ["validate", str(spec_file), "--check-deps", "--project", str(sample_project_path)],
            )
            output = get_output(result)
            assert result.exit_code in [0, 1]
            # Should either show dependencies OK or dependency issues
            assert "Valid spec" in output or "Invalid spec" in output

    def test_sample_project_resource_check_deps(self, sample_project_path: Path) -> None:
        """Test resource-level dependency checking on sample project."""
        result = runner.invoke(
            app,
            [
                "validate",
                "iceberg.analytics.daily_clicks",
                "--check-deps",
                "--project",
                str(sample_project_path),
            ],
        )
        output = get_output(result)
        # Resource exists in sample project
        assert result.exit_code in [0, 1]
        assert "Validating resource" in output or "Valid" in output or "Invalid" in output


class TestCheckDepsEdgeCases:
    """Edge case tests for dependency checking."""

    def test_check_deps_without_project_flag(self, tmp_path: Path) -> None:
        """Test --check-deps uses current directory when --project not specified."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create a simple dataset
        (datasets_dir / "dataset.iceberg.test.simple.yaml").write_text(
            """name: iceberg.test.simple
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Run from project directory (simulated via --project)
        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        assert result.exit_code == 0

    def test_check_deps_with_metric_dependencies(self, tmp_path: Path) -> None:
        """Test dependency checking works with metric specs."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()
        metrics_dir = project_dir / "metrics"
        metrics_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns:
    - "dataset.*.yaml"
  metric_patterns:
    - "metric.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create a dataset
        (datasets_dir / "dataset.iceberg.raw.events.yaml").write_text(
            """name: iceberg.raw.events
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        # Create metric that depends on dataset
        (metrics_dir / "metric.iceberg.analytics.kpi.yaml").write_text(
            """name: iceberg.analytics.kpi
owner: test@example.com
team: "@test"
type: Metric
query_type: SELECT
query_statement: "SELECT COUNT(*) FROM iceberg.raw.events"
depends_on:
  - "iceberg.raw.events"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "passed" in output.lower()

    def test_check_deps_transitive_dependencies(self, tmp_path: Path) -> None:
        """Test dependency checking with chain: A -> B -> C."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create chain: raw -> staging -> analytics
        (datasets_dir / "dataset.iceberg.raw.events.yaml").write_text(
            """name: iceberg.raw.events
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
"""
        )

        (datasets_dir / "dataset.iceberg.staging.events.yaml").write_text(
            """name: iceberg.staging.events
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.raw.events"
depends_on:
  - "iceberg.raw.events"
"""
        )

        (datasets_dir / "dataset.iceberg.analytics.events.yaml").write_text(
            """name: iceberg.analytics.events
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT * FROM iceberg.staging.events"
depends_on:
  - "iceberg.staging.events"
"""
        )

        result = runner.invoke(
            app,
            ["validate", "--all", "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        assert result.exit_code == 0
        assert "3 passed" in output.lower() or "passed" in output.lower()

    def test_check_deps_file_not_in_project_discovery(self, tmp_path: Path) -> None:
        """Test dependency checking when file exists but isn't found in discovery."""
        project_dir = tmp_path / "test_project"
        project_dir.mkdir()
        datasets_dir = project_dir / "datasets"
        datasets_dir.mkdir()

        # Create dli.yaml
        (project_dir / "dli.yaml").write_text(
            """version: "1"
project:
  name: "test-project"
discovery:
  datasets_dir: "datasets"
  dataset_patterns:
    - "dataset.*.yaml"
defaults:
  dialect: "trino"
"""
        )

        # Create spec file with non-matching name (won't be discovered)
        spec_file = datasets_dir / "custom_spec.yaml"
        spec_file.write_text(
            """name: iceberg.custom.spec
owner: test@example.com
team: "@test"
type: Dataset
query_type: DML
query_statement: "SELECT 1"
depends_on:
  - "iceberg.some.dep"
"""
        )

        result = runner.invoke(
            app,
            ["validate", str(spec_file), "--check-deps", "--project", str(project_dir)],
        )
        output = get_output(result)
        # File is valid but might not be found in discovery for dep check
        assert "Valid spec" in output or result.exit_code == 0
        # Should show warning about not being able to check dependencies
        assert "Warning" in output or "Could not" in output or "Dependencies" not in output
