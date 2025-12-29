"""Local Dependency Validator for the DLI Core Engine.

This module provides the DepValidator class for validating that dependencies
declared in spec files (via depends_on) exist within the local project.

This is a LOCAL ONLY validation - no server interaction.

Example:
    >>> from dli.core.validation import DepValidator
    >>> from dli.core.config import load_project
    >>> from dli.core.registry import DatasetRegistry, MetricRegistry
    >>>
    >>> config = load_project(Path("/my/project"))
    >>> validator = DepValidator(
    ...     dataset_registry=DatasetRegistry(config),
    ...     metric_registry=MetricRegistry(config),
    ... )
    >>> result = validator.validate(spec)
    >>> if not result.is_valid:
    ...     for error in result.errors:
    ...         print(f"Missing dependency: {error}")
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from dli.core.config import ProjectConfig
    from dli.core.models.spec import SpecBase
    from dli.core.registry import DatasetRegistry, MetricRegistry


@dataclass
class DepValidationResult:
    """Result of validating dependencies for a spec.

    Attributes:
        is_valid: Whether all dependencies are valid
        spec_name: Name of the spec being validated
        missing_dependencies: List of dependencies not found locally
        found_dependencies: List of dependencies found locally
        errors: List of error messages
        warnings: List of warning messages
    """

    is_valid: bool
    spec_name: str
    missing_dependencies: list[str] = field(default_factory=list)
    found_dependencies: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def total_dependencies(self) -> int:
        """Total number of declared dependencies."""
        return len(self.missing_dependencies) + len(self.found_dependencies)


@dataclass
class ProjectDepSummary:
    """Summary of dependency validation across a project.

    Attributes:
        total_specs: Total number of specs validated
        specs_with_missing_deps: Number of specs with missing dependencies
        total_dependencies: Total dependency references
        missing_dependencies: Total missing dependency count
        results: Individual validation results
    """

    total_specs: int = 0
    specs_with_missing_deps: int = 0
    total_dependencies: int = 0
    missing_dependencies: int = 0
    results: list[DepValidationResult] = field(default_factory=list)

    @property
    def all_valid(self) -> bool:
        """Check if all dependencies are valid."""
        return self.specs_with_missing_deps == 0

    @property
    def failed_results(self) -> list[DepValidationResult]:
        """Get only failed validation results."""
        return [r for r in self.results if not r.is_valid]


class DepValidator:
    """Local dependency validator for spec files.

    Validates that all dependencies declared in spec.depends_on exist
    within the local project (in either metrics or datasets registries).

    This is LOCAL ONLY validation - no server queries are performed.

    Attributes:
        dataset_registry: Registry of local dataset specs
        metric_registry: Registry of local metric specs

    Example:
        >>> validator = DepValidator(
        ...     dataset_registry=DatasetRegistry(config),
        ...     metric_registry=MetricRegistry(config),
        ... )
        >>> result = validator.validate(my_spec)
    """

    def __init__(
        self,
        dataset_registry: DatasetRegistry | None = None,
        metric_registry: MetricRegistry | None = None,
    ) -> None:
        """Initialize the dependency validator.

        Args:
            dataset_registry: Registry for dataset specs (optional)
            metric_registry: Registry for metric specs (optional)
        """
        self._dataset_registry = dataset_registry
        self._metric_registry = metric_registry

    @classmethod
    def from_project(cls, project_path: Path) -> DepValidator:
        """Create a DepValidator from a project path.

        This factory method loads the project configuration and creates
        the necessary registries.

        Args:
            project_path: Path to the project directory

        Returns:
            Configured DepValidator instance

        Raises:
            FileNotFoundError: If dli.yaml is not found
        """
        from dli.core.config import load_project  # noqa: PLC0415
        from dli.core.registry import DatasetRegistry, MetricRegistry  # noqa: PLC0415

        config = load_project(project_path)
        return cls(
            dataset_registry=DatasetRegistry(config),
            metric_registry=MetricRegistry(config),
        )

    def validate(self, spec: SpecBase) -> DepValidationResult:
        """Validate dependencies for a single spec.

        Checks if all resources listed in spec.depends_on exist
        in the local project registries.

        Args:
            spec: Spec to validate dependencies for

        Returns:
            DepValidationResult with validation status
        """
        missing: list[str] = []
        found: list[str] = []
        errors: list[str] = []
        warnings: list[str] = []

        dependencies = spec.depends_on or []

        if not dependencies:
            return DepValidationResult(
                is_valid=True,
                spec_name=spec.name,
                missing_dependencies=missing,
                found_dependencies=found,
            )

        for dep_name in dependencies:
            if self._find_resource(dep_name):
                found.append(dep_name)
            else:
                missing.append(dep_name)
                errors.append(f"Dependency '{dep_name}' not found in local project")

        # Check for circular dependencies
        if spec.name in dependencies:
            errors.append(f"Circular dependency: '{spec.name}' depends on itself")

        return DepValidationResult(
            is_valid=len(missing) == 0 and len(errors) == 0,
            spec_name=spec.name,
            missing_dependencies=missing,
            found_dependencies=found,
            errors=errors,
            warnings=warnings,
        )

    def validate_all(self, project_config: ProjectConfig) -> ProjectDepSummary:
        """Validate dependencies for all specs in a project.

        Args:
            project_config: Project configuration

        Returns:
            ProjectDepSummary with results for all specs
        """
        from dli.core.discovery import SpecDiscovery  # noqa: PLC0415

        summary = ProjectDepSummary()
        discovery = SpecDiscovery(project_config)

        # Validate all specs
        for spec in discovery.discover_all():
            result = self.validate(spec)
            summary.results.append(result)
            summary.total_specs += 1
            summary.total_dependencies += result.total_dependencies

            if not result.is_valid:
                summary.specs_with_missing_deps += 1
                summary.missing_dependencies += len(result.missing_dependencies)

        return summary

    def get_dependency_graph(
        self,
        project_config: ProjectConfig,
    ) -> dict[str, list[str]]:
        """Build a dependency graph for all specs in the project.

        Args:
            project_config: Project configuration

        Returns:
            Dictionary mapping spec names to their dependencies
        """
        from dli.core.discovery import SpecDiscovery  # noqa: PLC0415

        graph: dict[str, list[str]] = {}
        discovery = SpecDiscovery(project_config)

        for spec in discovery.discover_all():
            graph[spec.name] = list(spec.depends_on or [])

        return graph

    def find_downstream(
        self,
        resource_name: str,
        project_config: ProjectConfig,
    ) -> list[str]:
        """Find all specs that depend on a given resource.

        Args:
            resource_name: Name of the resource to find dependents for
            project_config: Project configuration

        Returns:
            List of spec names that depend on the resource
        """
        graph = self.get_dependency_graph(project_config)
        downstream: list[str] = []

        for spec_name, dependencies in graph.items():
            if resource_name in dependencies:
                downstream.append(spec_name)

        return sorted(downstream)

    def find_upstream(
        self,
        resource_name: str,
        project_config: ProjectConfig,
    ) -> list[str]:
        """Find all dependencies of a given resource (transitive).

        Args:
            resource_name: Name of the resource to find dependencies for
            project_config: Project configuration

        Returns:
            List of spec names that the resource depends on (direct only)
        """
        graph = self.get_dependency_graph(project_config)
        return sorted(graph.get(resource_name, []))

    def detect_cycles(
        self,
        project_config: ProjectConfig,
    ) -> list[list[str]]:
        """Detect circular dependencies in the project.

        Uses depth-first search to find all cycles in the dependency graph.

        Args:
            project_config: Project configuration

        Returns:
            List of cycles (each cycle is a list of spec names)
        """
        graph = self.get_dependency_graph(project_config)
        cycles: list[list[str]] = []
        visited: set[str] = set()
        rec_stack: set[str] = set()
        path: list[str] = []

        def dfs(node: str) -> None:
            visited.add(node)
            rec_stack.add(node)
            path.append(node)

            for neighbor in graph.get(node, []):
                if neighbor not in visited:
                    dfs(neighbor)
                elif neighbor in rec_stack:
                    # Found a cycle
                    cycle_start = path.index(neighbor)
                    cycle = path[cycle_start:] + [neighbor]
                    cycles.append(cycle)

            path.pop()
            rec_stack.remove(node)

        for node in graph:
            if node not in visited:
                dfs(node)

        return cycles

    def _find_resource(self, name: str) -> bool:
        """Check if a resource exists in local registries.

        Args:
            name: Fully qualified resource name

        Returns:
            True if the resource exists locally
        """
        # Check dataset registry
        if self._dataset_registry is not None and self._dataset_registry.get(name) is not None:
            return True

        # Check metric registry
        if self._metric_registry is not None and self._metric_registry.get(name) is not None:
            return True

        return False
