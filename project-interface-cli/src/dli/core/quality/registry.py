"""Quality Test Registry for the DLI Core Engine.

This module provides a registry for managing test definitions, loading
tests from spec files and singular test directories.

Classes:
    QualityRegistry: Central registry for test definitions

Responsibilities:
    - Load test definitions from YAML spec files
    - Discover singular test SQL files
    - Provide test lookup by resource name and test name
    - Manage test configuration inheritance
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import TYPE_CHECKING, Any

import yaml

from dli.core.quality.models import (
    DqSeverity,
    DqTestConfig,
    DqTestDefinition,
    DqTestType,
)

if TYPE_CHECKING:
    from dli.core.models import DatasetSpec, MetricSpec

logger = logging.getLogger(__name__)


class QualityRegistry:
    """Registry for managing data quality test definitions.

    The registry loads and stores test definitions from:
    1. Inline tests defined in spec YAML files (tests section)
    2. Singular test SQL files from a tests/ directory

    Attributes:
        project_path: Path to the project root
        tests: Dictionary mapping resource names to their test definitions
        config: Default test configuration
    """

    def __init__(
        self,
        project_path: Path | None = None,
        config: DqTestConfig | None = None,
    ) -> None:
        """Initialize the quality registry.

        Args:
            project_path: Path to the project root
            config: Default test configuration
        """
        self.project_path = project_path or Path.cwd()
        self.config = config or DqTestConfig()
        self._tests: dict[str, list[DqTestDefinition]] = {}

    @property
    def tests(self) -> dict[str, list[DqTestDefinition]]:
        """Get all registered tests grouped by resource name."""
        return self._tests.copy()

    def register(self, test: DqTestDefinition) -> None:
        """Register a test definition.

        Args:
            test: Test definition to register
        """
        resource = test.resource_name
        if resource not in self._tests:
            self._tests[resource] = []
        self._tests[resource].append(test)
        logger.debug(f"Registered test: {test.name} for resource: {resource}")

    def register_many(self, tests: list[DqTestDefinition]) -> None:
        """Register multiple test definitions.

        Args:
            tests: List of test definitions to register
        """
        for test in tests:
            self.register(test)

    def get_tests(
        self,
        resource_name: str | None = None,
        test_name: str | None = None,
    ) -> list[DqTestDefinition]:
        """Get tests by resource name and/or test name.

        Args:
            resource_name: Filter by resource name (optional)
            test_name: Filter by specific test name (optional)

        Returns:
            List of matching test definitions
        """
        if resource_name:
            tests = self._tests.get(resource_name, [])
        else:
            # Return all tests from all resources
            tests = []
            for resource_tests in self._tests.values():
                tests.extend(resource_tests)

        if test_name:
            tests = [t for t in tests if t.name == test_name]

        return tests

    def get_test(
        self,
        resource_name: str,
        test_name: str,
    ) -> DqTestDefinition | None:
        """Get a specific test by resource and test name.

        Args:
            resource_name: Resource name
            test_name: Test name

        Returns:
            DqTestDefinition if found, None otherwise
        """
        tests = self.get_tests(resource_name, test_name)
        return tests[0] if tests else None

    def list_resources(self) -> list[str]:
        """List all resource names with registered tests.

        Returns:
            List of resource names
        """
        return list(self._tests.keys())

    def clear(self) -> None:
        """Clear all registered tests."""
        self._tests.clear()

    def load_from_spec(
        self,
        spec: DatasetSpec | MetricSpec,
        spec_path: Path | None = None,
    ) -> list[DqTestDefinition]:
        """Load tests from a spec object.

        Tests are defined in the 'tests' section of the spec YAML.

        Args:
            spec: Spec object (DatasetSpec or MetricSpec)
            spec_path: Path to the spec file (for resolving file references)

        Returns:
            List of loaded test definitions
        """
        tests: list[DqTestDefinition] = []

        # Get test definitions from spec if available
        if not hasattr(spec, "tests") or not spec.tests:
            return tests

        # Get test config from spec if available
        test_config = getattr(spec, "test_config", {}) or {}
        default_severity = DqSeverity(
            test_config.get("severity", self.config.severity.value)
        )

        for test_data in spec.tests:
            try:
                # Parse test definition
                test_def = DqTestDefinition.from_yaml(test_data, spec.name)

                # Apply default severity if not specified
                if "severity" not in test_data:
                    test_def.severity = default_severity

                tests.append(test_def)
            except (ValueError, KeyError) as e:
                logger.warning(
                    f"Failed to parse test definition for {spec.name}: {e}"
                )
                continue

        # Register all loaded tests
        self.register_many(tests)
        return tests

    def load_from_yaml(
        self,
        yaml_path: Path,
    ) -> list[DqTestDefinition]:
        """Load tests from a YAML spec file.

        Args:
            yaml_path: Path to the YAML spec file

        Returns:
            List of loaded test definitions
        """
        if not yaml_path.exists():
            logger.warning(f"Spec file not found: {yaml_path}")
            return []

        try:
            with open(yaml_path, encoding="utf-8") as f:
                data = yaml.safe_load(f)
        except yaml.YAMLError as e:
            logger.error(f"Failed to parse YAML: {yaml_path}: {e}")
            return []

        if not data:
            return []

        resource_name = data.get("name", yaml_path.stem)
        tests_data = data.get("tests", [])
        test_config = data.get("test_config", {})

        if not tests_data:
            return []

        default_severity = DqSeverity(
            test_config.get("severity", self.config.severity.value)
        )

        tests: list[DqTestDefinition] = []
        for test_data in tests_data:
            try:
                test_def = DqTestDefinition.from_yaml(test_data, resource_name)
                if "severity" not in test_data:
                    test_def.severity = default_severity
                tests.append(test_def)
            except (ValueError, KeyError) as e:
                logger.warning(
                    f"Failed to parse test in {yaml_path}: {e}"
                )
                continue

        self.register_many(tests)
        return tests

    def discover_singular_tests(
        self,
        tests_dir: Path | None = None,
    ) -> list[DqTestDefinition]:
        """Discover singular test SQL files from tests directory.

        Singular tests are SQL files that return rows that fail the test.
        The directory structure determines the resource being tested:
            tests/<resource_name>/test_*.sql

        Args:
            tests_dir: Directory containing test SQL files
                       (defaults to project_path/tests)

        Returns:
            List of discovered test definitions
        """
        tests_dir = tests_dir or (self.project_path / "tests")
        if not tests_dir.exists():
            logger.debug(f"Tests directory not found: {tests_dir}")
            return []

        discovered: list[DqTestDefinition] = []

        # Find all SQL files matching test_*.sql pattern
        for sql_file in tests_dir.rglob("test_*.sql"):
            try:
                # Determine resource name from directory structure
                # tests/iceberg.analytics.daily_clicks/test_no_future.sql
                # -> resource_name = iceberg.analytics.daily_clicks
                relative = sql_file.relative_to(tests_dir)
                if len(relative.parts) >= 2:
                    resource_name = relative.parts[0]
                else:
                    # Flat structure - use filename
                    resource_name = sql_file.stem.replace("test_", "")

                # Read SQL content
                sql_content = sql_file.read_text(encoding="utf-8")

                # Create test definition
                test_name = sql_file.stem  # e.g., test_no_future_dates
                test_def = DqTestDefinition(
                    name=test_name,
                    test_type=DqTestType.SINGULAR,
                    resource_name=resource_name,
                    sql=sql_content,
                    file=str(sql_file),
                    description=f"Singular test from {sql_file.name}",
                )

                discovered.append(test_def)
                self.register(test_def)

            except Exception as e:
                logger.warning(f"Failed to load singular test {sql_file}: {e}")
                continue

        return discovered

    def load_all(self) -> dict[str, list[DqTestDefinition]]:
        """Load all tests from the project.

        Loads tests from:
        1. All spec YAML files in datasets/ and metrics/ directories
        2. Singular test SQL files from tests/ directory

        Returns:
            Dictionary of resource names to test definitions
        """
        # Discover specs and load their tests
        spec_dirs = [
            self.project_path / "datasets",
            self.project_path / "metrics",
        ]

        for spec_dir in spec_dirs:
            if not spec_dir.exists():
                continue

            for yaml_file in spec_dir.rglob("*.yaml"):
                # Skip non-spec files
                if not (
                    yaml_file.name.startswith("spec.")
                    or yaml_file.name.startswith("dataset.")
                    or yaml_file.name.startswith("metric.")
                ):
                    continue
                self.load_from_yaml(yaml_file)

        # Discover singular tests
        self.discover_singular_tests()

        return self._tests


def create_registry(
    project_path: Path | None = None,
    config: DqTestConfig | None = None,
    auto_load: bool = False,
) -> QualityRegistry:
    """Factory function to create a QualityRegistry.

    Args:
        project_path: Path to project root
        config: Test configuration
        auto_load: If True, automatically load all tests

    Returns:
        Configured QualityRegistry instance
    """
    registry = QualityRegistry(project_path=project_path, config=config)
    if auto_load:
        registry.load_all()
    return registry
