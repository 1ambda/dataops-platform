"""Tests for QualityRegistry.

This module tests the QualityRegistry class which manages
test definitions loading, registration, and discovery.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any
from unittest.mock import Mock, patch

import pytest
import yaml

from dli.core.quality.models import (
    DqSeverity,
    DqTestConfig,
    DqTestDefinition,
    DqTestType,
)
from dli.core.quality.registry import QualityRegistry, create_registry


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def registry() -> QualityRegistry:
    """Create a basic QualityRegistry instance."""
    return QualityRegistry()


@pytest.fixture
def registry_with_config() -> QualityRegistry:
    """Create a QualityRegistry with custom config."""
    config = DqTestConfig(
        fail_fast=True,
        severity=DqSeverity.WARN,
        limit=50,
    )
    return QualityRegistry(config=config)


@pytest.fixture
def sample_test_definition() -> DqTestDefinition:
    """Create a sample test definition."""
    return DqTestDefinition(
        name="not_null_user_id",
        test_type=DqTestType.NOT_NULL,
        resource_name="iceberg.analytics.daily_clicks",
        columns=["user_id"],
        description="User ID should not be null",
    )


@pytest.fixture
def sample_tests() -> list[DqTestDefinition]:
    """Create multiple sample test definitions."""
    return [
        DqTestDefinition(
            name="not_null_user_id",
            test_type=DqTestType.NOT_NULL,
            resource_name="iceberg.analytics.daily_clicks",
            columns=["user_id"],
        ),
        DqTestDefinition(
            name="unique_user_dt",
            test_type=DqTestType.UNIQUE,
            resource_name="iceberg.analytics.daily_clicks",
            columns=["user_id", "dt"],
        ),
        DqTestDefinition(
            name="not_null_revenue",
            test_type=DqTestType.NOT_NULL,
            resource_name="iceberg.metrics.revenue",
            columns=["revenue"],
        ),
    ]


# =============================================================================
# Basic Operations Tests
# =============================================================================


class TestQualityRegistryInit:
    """Tests for QualityRegistry initialization."""

    def test_default_initialization(self) -> None:
        """Registry should initialize with defaults."""
        registry = QualityRegistry()
        assert registry.project_path == Path.cwd()
        assert isinstance(registry.config, DqTestConfig)
        assert registry.tests == {}

    def test_initialization_with_project_path(self, tmp_path: Path) -> None:
        """Registry should accept custom project path."""
        registry = QualityRegistry(project_path=tmp_path)
        assert registry.project_path == tmp_path

    def test_initialization_with_config(self) -> None:
        """Registry should accept custom config."""
        config = DqTestConfig(fail_fast=True, limit=50)
        registry = QualityRegistry(config=config)
        assert registry.config.fail_fast is True
        assert registry.config.limit == 50


class TestRegisterTests:
    """Tests for test registration."""

    def test_register_single_test(
        self,
        registry: QualityRegistry,
        sample_test_definition: DqTestDefinition,
    ) -> None:
        """Should register a single test definition."""
        registry.register(sample_test_definition)

        assert len(registry.tests) == 1
        assert sample_test_definition.resource_name in registry.tests
        assert registry.tests[sample_test_definition.resource_name][0] == sample_test_definition

    def test_register_multiple_tests_same_resource(
        self,
        registry: QualityRegistry,
    ) -> None:
        """Should register multiple tests for same resource."""
        resource = "iceberg.test.table"
        test1 = DqTestDefinition(
            name="test1",
            test_type=DqTestType.NOT_NULL,
            resource_name=resource,
            columns=["col1"],
        )
        test2 = DqTestDefinition(
            name="test2",
            test_type=DqTestType.UNIQUE,
            resource_name=resource,
            columns=["col2"],
        )

        registry.register(test1)
        registry.register(test2)

        assert len(registry.tests[resource]) == 2

    def test_register_many(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should register multiple tests at once."""
        registry.register_many(sample_tests)

        # Should have 2 resources (daily_clicks and revenue)
        assert len(registry.tests) == 2
        assert "iceberg.analytics.daily_clicks" in registry.tests
        assert "iceberg.metrics.revenue" in registry.tests


class TestGetTests:
    """Tests for retrieving tests."""

    def test_get_tests_all(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should return all tests when no filter specified."""
        registry.register_many(sample_tests)

        all_tests = registry.get_tests()
        assert len(all_tests) == 3

    def test_get_tests_by_resource(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should filter tests by resource name."""
        registry.register_many(sample_tests)

        tests = registry.get_tests(resource_name="iceberg.analytics.daily_clicks")
        assert len(tests) == 2
        for test in tests:
            assert test.resource_name == "iceberg.analytics.daily_clicks"

    def test_get_tests_by_test_name(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should filter tests by test name."""
        registry.register_many(sample_tests)

        tests = registry.get_tests(test_name="not_null_user_id")
        assert len(tests) == 1
        assert tests[0].name == "not_null_user_id"

    def test_get_tests_by_resource_and_name(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should filter by both resource and test name."""
        registry.register_many(sample_tests)

        tests = registry.get_tests(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="unique_user_dt",
        )
        assert len(tests) == 1
        assert tests[0].name == "unique_user_dt"

    def test_get_tests_empty_result(self, registry: QualityRegistry) -> None:
        """Should return empty list when no matches."""
        tests = registry.get_tests(resource_name="nonexistent")
        assert tests == []

    def test_get_test_single(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should get single test by resource and name."""
        registry.register_many(sample_tests)

        test = registry.get_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="not_null_user_id",
        )
        assert test is not None
        assert test.name == "not_null_user_id"

    def test_get_test_not_found(self, registry: QualityRegistry) -> None:
        """Should return None when test not found."""
        test = registry.get_test("nonexistent", "nonexistent")
        assert test is None


class TestListResources:
    """Tests for listing resources."""

    def test_list_resources_empty(self, registry: QualityRegistry) -> None:
        """Should return empty list when no tests registered."""
        assert registry.list_resources() == []

    def test_list_resources(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should list all resource names."""
        registry.register_many(sample_tests)

        resources = registry.list_resources()
        assert set(resources) == {
            "iceberg.analytics.daily_clicks",
            "iceberg.metrics.revenue",
        }


class TestClear:
    """Tests for clearing registry."""

    def test_clear_registry(
        self,
        registry: QualityRegistry,
        sample_tests: list[DqTestDefinition],
    ) -> None:
        """Should clear all registered tests."""
        registry.register_many(sample_tests)
        assert len(registry.tests) > 0

        registry.clear()

        assert registry.tests == {}
        assert registry.list_resources() == []


# =============================================================================
# Load from Spec Tests
# =============================================================================


class TestLoadFromSpec:
    """Tests for loading tests from spec objects."""

    def test_load_from_spec_with_tests(self, registry: QualityRegistry) -> None:
        """Should load tests from spec with tests section."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.test.table"
        mock_spec.tests = [
            {"type": "not_null", "columns": ["user_id"]},
            {"type": "unique", "columns": ["id", "dt"]},
        ]
        mock_spec.test_config = None

        tests = registry.load_from_spec(mock_spec)

        assert len(tests) == 2
        assert len(registry.tests["iceberg.test.table"]) == 2

    def test_load_from_spec_no_tests_attr(self, registry: QualityRegistry) -> None:
        """Should return empty list when spec has no tests attribute."""
        mock_spec = Mock(spec=[])
        del mock_spec.tests
        mock_spec.name = "test"

        tests = registry.load_from_spec(mock_spec)
        assert tests == []

    def test_load_from_spec_empty_tests(self, registry: QualityRegistry) -> None:
        """Should return empty list when tests is empty."""
        mock_spec = Mock()
        mock_spec.name = "test"
        mock_spec.tests = []

        tests = registry.load_from_spec(mock_spec)
        assert tests == []

    def test_load_from_spec_with_test_config(self, registry: QualityRegistry) -> None:
        """Should apply test_config defaults."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.test.table"
        mock_spec.tests = [
            {"type": "not_null", "columns": ["user_id"]},
        ]
        mock_spec.test_config = {"severity": "warn"}

        tests = registry.load_from_spec(mock_spec)

        assert len(tests) == 1
        assert tests[0].severity == DqSeverity.WARN

    def test_load_from_spec_invalid_test_definition(
        self,
        registry: QualityRegistry,
    ) -> None:
        """Should skip invalid test definitions with warning."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.test.table"
        mock_spec.tests = [
            {"type": "not_null", "columns": ["valid"]},
            {"type": "invalid_type"},  # Invalid type
            {"type": "unique", "columns": ["also_valid"]},
        ]
        mock_spec.test_config = None

        with patch("dli.core.quality.registry.logger") as mock_logger:
            tests = registry.load_from_spec(mock_spec)

        # Should load 2 valid tests, skip 1 invalid
        assert len(tests) == 2


# =============================================================================
# Load from YAML Tests
# =============================================================================


class TestLoadFromYaml:
    """Tests for loading tests from YAML files."""

    def test_load_from_yaml_success(self, tmp_path: Path) -> None:
        """Should load tests from valid YAML file."""
        yaml_content = """
name: iceberg.test.table
tests:
  - type: not_null
    columns: [user_id]
  - type: unique
    columns: [id, dt]
"""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert len(tests) == 2
        assert registry.tests["iceberg.test.table"][0].test_type == DqTestType.NOT_NULL

    def test_load_from_yaml_file_not_found(
        self,
        tmp_path: Path,
    ) -> None:
        """Should return empty list when file not found."""
        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(tmp_path / "nonexistent.yaml")

        assert tests == []

    def test_load_from_yaml_invalid_yaml(self, tmp_path: Path) -> None:
        """Should return empty list for invalid YAML syntax."""
        yaml_file = tmp_path / "invalid.yaml"
        yaml_file.write_text("invalid: yaml: syntax: [")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert tests == []

    def test_load_from_yaml_empty_file(self, tmp_path: Path) -> None:
        """Should return empty list for empty YAML file."""
        yaml_file = tmp_path / "empty.yaml"
        yaml_file.write_text("")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert tests == []

    def test_load_from_yaml_no_tests_section(self, tmp_path: Path) -> None:
        """Should return empty list when no tests section."""
        yaml_content = """
name: iceberg.test.table
owner: test@example.com
"""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert tests == []

    def test_load_from_yaml_with_test_config(self, tmp_path: Path) -> None:
        """Should apply test_config from YAML."""
        yaml_content = """
name: iceberg.test.table
test_config:
  severity: warn
tests:
  - type: not_null
    columns: [user_id]
"""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert tests[0].severity == DqSeverity.WARN

    def test_load_from_yaml_uses_filename_as_resource(
        self,
        tmp_path: Path,
    ) -> None:
        """Should use filename as resource name when name not in YAML."""
        yaml_content = """
tests:
  - type: not_null
    columns: [user_id]
"""
        yaml_file = tmp_path / "my_resource.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert tests[0].resource_name == "my_resource"


# =============================================================================
# Discover Singular Tests
# =============================================================================


class TestDiscoverSingularTests:
    """Tests for discovering singular SQL test files."""

    def test_discover_singular_tests_nested_structure(
        self,
        tmp_path: Path,
    ) -> None:
        """Should discover tests from nested directory structure."""
        # Create nested test structure
        tests_dir = tmp_path / "tests" / "iceberg.analytics.daily_clicks"
        tests_dir.mkdir(parents=True)

        test_file = tests_dir / "test_no_future_dates.sql"
        test_file.write_text("SELECT * FROM t WHERE dt > CURRENT_DATE")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 1
        assert tests[0].name == "test_no_future_dates"
        assert tests[0].resource_name == "iceberg.analytics.daily_clicks"
        assert tests[0].test_type == DqTestType.SINGULAR
        assert "SELECT * FROM t" in tests[0].sql

    def test_discover_singular_tests_flat_structure(
        self,
        tmp_path: Path,
    ) -> None:
        """Should handle flat test structure."""
        tests_dir = tmp_path / "tests"
        tests_dir.mkdir()

        test_file = tests_dir / "test_my_check.sql"
        test_file.write_text("SELECT 1")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 1
        assert tests[0].resource_name == "my_check"

    def test_discover_singular_tests_no_tests_dir(
        self,
        tmp_path: Path,
    ) -> None:
        """Should return empty list when tests directory missing."""
        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert tests == []

    def test_discover_singular_tests_custom_dir(self, tmp_path: Path) -> None:
        """Should use custom tests directory."""
        custom_dir = tmp_path / "custom_tests" / "resource"
        custom_dir.mkdir(parents=True)

        test_file = custom_dir / "test_check.sql"
        test_file.write_text("SELECT 1")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests(tests_dir=tmp_path / "custom_tests")

        assert len(tests) == 1

    def test_discover_singular_tests_ignores_non_test_files(
        self,
        tmp_path: Path,
    ) -> None:
        """Should only discover files matching test_*.sql pattern."""
        tests_dir = tmp_path / "tests" / "resource"
        tests_dir.mkdir(parents=True)

        # Create various files
        (tests_dir / "test_valid.sql").write_text("SELECT 1")
        (tests_dir / "not_a_test.sql").write_text("SELECT 2")
        (tests_dir / "test_also_valid.sql").write_text("SELECT 3")
        (tests_dir / "helper.py").write_text("# helper")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 2
        test_names = {t.name for t in tests}
        assert test_names == {"test_valid", "test_also_valid"}

    def test_discover_singular_tests_multiple_resources(
        self,
        tmp_path: Path,
    ) -> None:
        """Should discover tests for multiple resources."""
        tests_dir = tmp_path / "tests"

        # Resource 1
        res1_dir = tests_dir / "resource_one"
        res1_dir.mkdir(parents=True)
        (res1_dir / "test_check1.sql").write_text("SELECT 1")

        # Resource 2
        res2_dir = tests_dir / "resource_two"
        res2_dir.mkdir(parents=True)
        (res2_dir / "test_check2.sql").write_text("SELECT 2")
        (res2_dir / "test_check3.sql").write_text("SELECT 3")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 3
        assert len(registry.tests) == 2  # 2 resources


# =============================================================================
# Load All Tests
# =============================================================================


class TestLoadAll:
    """Tests for loading all tests from project."""

    def test_load_all_empty_project(self, tmp_path: Path) -> None:
        """Should handle empty project gracefully."""
        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert tests == {}

    def test_load_all_datasets_directory(self, tmp_path: Path) -> None:
        """Should load tests from datasets directory."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()

        yaml_content = """
name: iceberg.analytics.table
tests:
  - type: not_null
    columns: [id]
"""
        (datasets_dir / "dataset.iceberg.analytics.table.yaml").write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert "iceberg.analytics.table" in tests
        assert len(tests["iceberg.analytics.table"]) == 1

    def test_load_all_metrics_directory(self, tmp_path: Path) -> None:
        """Should load tests from metrics directory."""
        metrics_dir = tmp_path / "metrics"
        metrics_dir.mkdir()

        yaml_content = """
name: iceberg.metrics.revenue
tests:
  - type: not_null
    columns: [revenue]
"""
        (metrics_dir / "metric.iceberg.metrics.revenue.yaml").write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert "iceberg.metrics.revenue" in tests

    def test_load_all_spec_prefix(self, tmp_path: Path) -> None:
        """Should also load files with spec. prefix."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()

        yaml_content = """
name: iceberg.test.table
tests:
  - type: unique
    columns: [id]
"""
        (datasets_dir / "spec.iceberg.test.table.yaml").write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert "iceberg.test.table" in tests

    def test_load_all_ignores_non_spec_files(self, tmp_path: Path) -> None:
        """Should ignore YAML files without spec/dataset/metric prefix."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()

        # Valid spec
        (datasets_dir / "dataset.valid.yaml").write_text("""
name: valid
tests:
  - type: not_null
    columns: [id]
""")

        # Non-spec file (should be ignored)
        (datasets_dir / "config.yaml").write_text("""
name: config
tests:
  - type: not_null
    columns: [id]
""")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert "valid" in tests
        assert "config" not in tests

    def test_load_all_combined_sources(self, tmp_path: Path) -> None:
        """Should combine tests from specs and singular tests."""
        # Create spec tests
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()
        (datasets_dir / "dataset.resource.yaml").write_text("""
name: resource
tests:
  - type: not_null
    columns: [id]
""")

        # Create singular tests
        tests_dir = tmp_path / "tests" / "resource"
        tests_dir.mkdir(parents=True)
        (tests_dir / "test_custom.sql").write_text("SELECT 1")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        # Should have both spec test and singular test
        assert "resource" in tests
        assert len(tests["resource"]) == 2


# =============================================================================
# Factory Function Tests
# =============================================================================


class TestCreateRegistry:
    """Tests for create_registry factory function."""

    def test_create_registry_defaults(self) -> None:
        """Should create registry with defaults."""
        registry = create_registry()

        assert isinstance(registry, QualityRegistry)
        assert registry.tests == {}

    def test_create_registry_with_config(self) -> None:
        """Should accept custom config."""
        config = DqTestConfig(limit=25)
        registry = create_registry(config=config)

        assert registry.config.limit == 25

    def test_create_registry_auto_load(self, tmp_path: Path) -> None:
        """Should auto-load tests when auto_load=True."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()
        (datasets_dir / "dataset.test.yaml").write_text("""
name: test
tests:
  - type: not_null
    columns: [id]
""")

        registry = create_registry(project_path=tmp_path, auto_load=True)

        assert "test" in registry.tests

    def test_create_registry_no_auto_load(self, tmp_path: Path) -> None:
        """Should not load tests when auto_load=False."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()
        (datasets_dir / "dataset.test.yaml").write_text("""
name: test
tests:
  - type: not_null
    columns: [id]
""")

        registry = create_registry(project_path=tmp_path, auto_load=False)

        assert registry.tests == {}


# =============================================================================
# Edge Cases and Error Handling
# =============================================================================


class TestEdgeCases:
    """Tests for edge cases and error handling."""

    def test_tests_property_returns_copy(self, registry: QualityRegistry) -> None:
        """Tests property should return copy to prevent external mutation."""
        test = DqTestDefinition(
            name="test",
            test_type=DqTestType.NOT_NULL,
            resource_name="resource",
            columns=["col"],
        )
        registry.register(test)

        tests_copy = registry.tests
        tests_copy.clear()  # Mutate the copy

        # Original should be unchanged
        assert len(registry.tests) == 1

    def test_concurrent_registration(self, registry: QualityRegistry) -> None:
        """Should handle multiple registrations for same resource."""
        for i in range(100):
            test = DqTestDefinition(
                name=f"test_{i}",
                test_type=DqTestType.NOT_NULL,
                resource_name="shared_resource",
                columns=["col"],
            )
            registry.register(test)

        assert len(registry.tests["shared_resource"]) == 100

    def test_duplicate_test_names(self, registry: QualityRegistry) -> None:
        """Should allow duplicate test names (user responsibility)."""
        test1 = DqTestDefinition(
            name="duplicate_name",
            test_type=DqTestType.NOT_NULL,
            resource_name="resource",
            columns=["col1"],
        )
        test2 = DqTestDefinition(
            name="duplicate_name",
            test_type=DqTestType.UNIQUE,
            resource_name="resource",
            columns=["col2"],
        )

        registry.register(test1)
        registry.register(test2)

        # Both should be registered
        assert len(registry.tests["resource"]) == 2

    def test_load_from_yaml_invalid_test_definition(self, tmp_path: Path) -> None:
        """Should skip invalid test definitions in YAML and continue loading valid ones.

        This test covers the exception handling path (lines 239-243) where
        invalid test definitions are logged as warnings and skipped.
        """
        yaml_content = """
name: iceberg.test.resource
tests:
  - type: not_null
    columns: [valid_column]
  - type: invalid_type_xyz
    columns: [should_fail]
  - type: unique
    columns: [another_valid]
"""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        # Should load 2 valid tests, skip 1 invalid
        assert len(tests) == 2
        test_types = {t.test_type for t in tests}
        assert test_types == {DqTestType.NOT_NULL, DqTestType.UNIQUE}

    def test_discover_singular_tests_read_error(self, tmp_path: Path) -> None:
        """Should handle file read errors gracefully during test discovery.

        This test covers the exception handling path (lines 302-304) where
        errors during SQL file reading are logged as warnings and skipped.
        """
        tests_dir = tmp_path / "tests" / "resource"
        tests_dir.mkdir(parents=True)

        # Create a valid test file
        valid_file = tests_dir / "test_valid.sql"
        valid_file.write_text("SELECT 1")

        # Create a test file that will cause read error
        error_file = tests_dir / "test_error.sql"
        error_file.write_text("SELECT 2")
        # Make the file unreadable (this approach works on Unix systems)
        try:
            error_file.chmod(0o000)

            registry = QualityRegistry(project_path=tmp_path)
            tests = registry.discover_singular_tests()

            # Should still load the valid test even if one fails
            # On systems where chmod doesn't work, both will load
            assert len(tests) >= 1
        finally:
            # Restore permissions so cleanup can happen
            error_file.chmod(0o644)

    def test_register_many_empty_list(self, registry: QualityRegistry) -> None:
        """Should handle empty list gracefully."""
        registry.register_many([])
        assert registry.tests == {}

    def test_get_tests_test_name_only(
        self,
        registry: QualityRegistry,
    ) -> None:
        """Should filter by test name across all resources."""
        test1 = DqTestDefinition(
            name="shared_test_name",
            test_type=DqTestType.NOT_NULL,
            resource_name="resource_a",
            columns=["col"],
        )
        test2 = DqTestDefinition(
            name="shared_test_name",
            test_type=DqTestType.UNIQUE,
            resource_name="resource_b",
            columns=["col"],
        )
        test3 = DqTestDefinition(
            name="different_name",
            test_type=DqTestType.NOT_NULL,
            resource_name="resource_a",
            columns=["col"],
        )

        registry.register_many([test1, test2, test3])

        # Filter by test name only (no resource filter)
        tests = registry.get_tests(test_name="shared_test_name")

        assert len(tests) == 2
        assert all(t.name == "shared_test_name" for t in tests)

    def test_load_from_spec_preserves_explicit_severity(
        self,
        registry: QualityRegistry,
    ) -> None:
        """Should preserve explicit severity in test even when test_config differs."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.test.table"
        mock_spec.tests = [
            {"type": "not_null", "columns": ["col1"], "severity": "error"},
            {"type": "not_null", "columns": ["col2"]},  # No explicit severity
        ]
        mock_spec.test_config = {"severity": "warn"}

        tests = registry.load_from_spec(mock_spec)

        # First test should keep explicit 'error' severity
        assert tests[0].severity == DqSeverity.ERROR
        # Second test should get default 'warn' from test_config
        assert tests[1].severity == DqSeverity.WARN

    def test_load_from_spec_no_test_config_attribute(
        self,
        registry: QualityRegistry,
    ) -> None:
        """Should use registry defaults when spec has no test_config attribute."""
        mock_spec = Mock(spec=["name", "tests"])
        mock_spec.name = "iceberg.test.table"
        mock_spec.tests = [{"type": "not_null", "columns": ["col"]}]
        # No test_config attribute at all

        tests = registry.load_from_spec(mock_spec)

        assert len(tests) == 1
        # Should use registry's default severity (ERROR)
        assert tests[0].severity == DqSeverity.ERROR

    def test_discover_singular_tests_sets_file_path(
        self,
        tmp_path: Path,
    ) -> None:
        """Should set the file path in singular test definitions."""
        tests_dir = tmp_path / "tests" / "resource"
        tests_dir.mkdir(parents=True)

        test_file = tests_dir / "test_check.sql"
        test_file.write_text("SELECT * FROM t WHERE x IS NULL")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 1
        assert tests[0].file == str(test_file)
        assert tests[0].description == "Singular test from test_check.sql"

    def test_load_all_nested_directories(self, tmp_path: Path) -> None:
        """Should discover spec files in nested subdirectories."""
        nested_dir = tmp_path / "datasets" / "analytics" / "deep"
        nested_dir.mkdir(parents=True)

        yaml_content = """
name: nested.deep.resource
tests:
  - type: not_null
    columns: [id]
"""
        (nested_dir / "dataset.nested.deep.resource.yaml").write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_all()

        assert "nested.deep.resource" in tests

    def test_unicode_sql_content(self, tmp_path: Path) -> None:
        """Should handle Unicode content in SQL files."""
        tests_dir = tmp_path / "tests" / "resource"
        tests_dir.mkdir(parents=True)

        test_file = tests_dir / "test_unicode.sql"
        test_file.write_text(
            "SELECT * FROM users WHERE name = 'Test'",
            encoding="utf-8",
        )

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 1
        assert "Test" in tests[0].sql

    def test_special_characters_in_resource_name(self, tmp_path: Path) -> None:
        """Should handle special characters (dots) in resource names."""
        tests_dir = tmp_path / "tests" / "iceberg.analytics.daily_clicks"
        tests_dir.mkdir(parents=True)

        (tests_dir / "test_check.sql").write_text("SELECT 1")

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.discover_singular_tests()

        assert len(tests) == 1
        assert tests[0].resource_name == "iceberg.analytics.daily_clicks"

    def test_load_from_yaml_with_complex_test_types(self, tmp_path: Path) -> None:
        """Should handle complex test types like relationships and range_check."""
        yaml_content = """
name: iceberg.test.orders
tests:
  - type: relationships
    column: user_id
    to: users
    to_column: id
  - type: range_check
    column: amount
    min: 0
    max: 10000
  - type: accepted_values
    column: status
    values: [pending, completed, cancelled]
"""
        yaml_file = tmp_path / "spec.yaml"
        yaml_file.write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        tests = registry.load_from_yaml(yaml_file)

        assert len(tests) == 3
        test_types = {t.test_type for t in tests}
        assert test_types == {
            DqTestType.RELATIONSHIPS,
            DqTestType.RANGE_CHECK,
            DqTestType.ACCEPTED_VALUES,
        }

    def test_clear_then_reload(self, tmp_path: Path) -> None:
        """Should be able to clear and reload tests."""
        datasets_dir = tmp_path / "datasets"
        datasets_dir.mkdir()

        yaml_content = """
name: resource
tests:
  - type: not_null
    columns: [id]
"""
        (datasets_dir / "dataset.resource.yaml").write_text(yaml_content)

        registry = QualityRegistry(project_path=tmp_path)
        registry.load_all()
        assert len(registry.tests) == 1

        registry.clear()
        assert len(registry.tests) == 0

        registry.load_all()
        assert len(registry.tests) == 1
