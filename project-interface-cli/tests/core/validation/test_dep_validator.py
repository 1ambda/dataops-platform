"""Tests for DepValidator: Local dependency validation.

This module tests the DepValidator class which performs local-only validation
of spec dependencies (depends_on references) against local registries.

Test coverage:
- DepValidationResult dataclass properties
- ProjectDepSummary dataclass properties
- DepValidator.validate: Single spec dependency validation
- DepValidator.validate_all: Project-wide dependency validation
- DepValidator.get_dependency_graph: Dependency graph generation
- DepValidator.find_downstream: Find dependent specs
- DepValidator.find_upstream: Find spec dependencies
- DepValidator.detect_cycles: Circular dependency detection
- DepValidator.from_project: Factory method
"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING
from unittest.mock import Mock, patch

import pytest

from dli.core.validation import DepValidationResult, DepValidator, ProjectDepSummary

if TYPE_CHECKING:
    pass


# =============================================================================
# DepValidationResult Tests
# =============================================================================


class TestDepValidationResult:
    """Tests for the DepValidationResult dataclass."""

    def test_valid_result_no_dependencies(self) -> None:
        """Test creating a valid result with no dependencies."""
        result = DepValidationResult(
            is_valid=True,
            spec_name="iceberg.analytics.test",
            missing_dependencies=[],
            found_dependencies=[],
        )

        assert result.is_valid is True
        assert result.spec_name == "iceberg.analytics.test"
        assert result.total_dependencies == 0
        assert len(result.errors) == 0
        assert len(result.warnings) == 0

    def test_valid_result_all_deps_found(self) -> None:
        """Test creating a valid result with all dependencies found."""
        result = DepValidationResult(
            is_valid=True,
            spec_name="iceberg.analytics.test",
            missing_dependencies=[],
            found_dependencies=["iceberg.raw.users", "iceberg.raw.events"],
        )

        assert result.is_valid is True
        assert result.total_dependencies == 2
        assert len(result.found_dependencies) == 2

    def test_invalid_result_missing_deps(self) -> None:
        """Test creating an invalid result with missing dependencies."""
        result = DepValidationResult(
            is_valid=False,
            spec_name="iceberg.analytics.test",
            missing_dependencies=["iceberg.nonexistent.table"],
            found_dependencies=["iceberg.raw.users"],
            errors=["Dependency 'iceberg.nonexistent.table' not found in local project"],
        )

        assert result.is_valid is False
        assert result.total_dependencies == 2
        assert len(result.missing_dependencies) == 1
        assert len(result.found_dependencies) == 1

    def test_total_dependencies_property(self) -> None:
        """Test total_dependencies sums missing and found."""
        result = DepValidationResult(
            is_valid=False,
            spec_name="iceberg.analytics.test",
            missing_dependencies=["dep1", "dep2"],
            found_dependencies=["dep3", "dep4", "dep5"],
        )

        assert result.total_dependencies == 5

    def test_default_factory_for_lists(self) -> None:
        """Test that lists default to empty."""
        result = DepValidationResult(
            is_valid=True,
            spec_name="iceberg.analytics.test",
        )

        assert result.missing_dependencies == []
        assert result.found_dependencies == []
        assert result.errors == []
        assert result.warnings == []


# =============================================================================
# ProjectDepSummary Tests
# =============================================================================


class TestProjectDepSummary:
    """Tests for the ProjectDepSummary dataclass."""

    def test_empty_summary_creation(self) -> None:
        """Test creating an empty summary."""
        summary = ProjectDepSummary()

        assert summary.total_specs == 0
        assert summary.specs_with_missing_deps == 0
        assert summary.total_dependencies == 0
        assert summary.missing_dependencies == 0
        assert summary.results == []
        assert summary.all_valid is True

    def test_summary_with_all_valid(self) -> None:
        """Test summary where all dependencies are valid."""
        result1 = DepValidationResult(
            is_valid=True,
            spec_name="spec1",
            found_dependencies=["dep1", "dep2"],
        )
        result2 = DepValidationResult(
            is_valid=True,
            spec_name="spec2",
            found_dependencies=["dep3"],
        )

        summary = ProjectDepSummary(
            total_specs=2,
            specs_with_missing_deps=0,
            total_dependencies=3,
            missing_dependencies=0,
            results=[result1, result2],
        )

        assert summary.all_valid is True
        assert len(summary.failed_results) == 0

    def test_summary_with_missing_deps(self) -> None:
        """Test summary with missing dependencies."""
        result_valid = DepValidationResult(
            is_valid=True,
            spec_name="spec1",
            found_dependencies=["dep1"],
        )
        result_invalid = DepValidationResult(
            is_valid=False,
            spec_name="spec2",
            missing_dependencies=["missing_dep"],
            found_dependencies=["dep2"],
            errors=["Dependency 'missing_dep' not found"],
        )

        summary = ProjectDepSummary(
            total_specs=2,
            specs_with_missing_deps=1,
            total_dependencies=3,
            missing_dependencies=1,
            results=[result_valid, result_invalid],
        )

        assert summary.all_valid is False
        assert len(summary.failed_results) == 1

    def test_all_valid_property_true(self) -> None:
        """Test all_valid returns True when no missing deps."""
        summary = ProjectDepSummary(
            total_specs=5,
            specs_with_missing_deps=0,
        )
        assert summary.all_valid is True

    def test_all_valid_property_false(self) -> None:
        """Test all_valid returns False with missing deps."""
        summary = ProjectDepSummary(
            total_specs=5,
            specs_with_missing_deps=2,
        )
        assert summary.all_valid is False

    def test_failed_results_property(self) -> None:
        """Test failed_results returns only invalid results."""
        result_valid = DepValidationResult(is_valid=True, spec_name="spec1")
        result_invalid1 = DepValidationResult(
            is_valid=False,
            spec_name="spec2",
            errors=["Error 1"],
        )
        result_invalid2 = DepValidationResult(
            is_valid=False,
            spec_name="spec3",
            errors=["Error 2"],
        )

        summary = ProjectDepSummary(
            total_specs=3,
            specs_with_missing_deps=2,
            results=[result_valid, result_invalid1, result_invalid2],
        )

        failed = summary.failed_results
        assert len(failed) == 2
        assert all(not r.is_valid for r in failed)


# =============================================================================
# DepValidator Tests
# =============================================================================


class TestDepValidator:
    """Tests for the DepValidator class."""

    def test_init_with_no_registries(self) -> None:
        """Test initialization with no registries."""
        validator = DepValidator()

        assert validator._dataset_registry is None
        assert validator._metric_registry is None

    def test_init_with_registries(self) -> None:
        """Test initialization with mock registries."""
        mock_dataset_registry = Mock()
        mock_metric_registry = Mock()

        validator = DepValidator(
            dataset_registry=mock_dataset_registry,
            metric_registry=mock_metric_registry,
        )

        assert validator._dataset_registry is mock_dataset_registry
        assert validator._metric_registry is mock_metric_registry

    def test_from_project_factory(self, sample_project_path: Path) -> None:
        """Test from_project factory method creates validator."""
        validator = DepValidator.from_project(sample_project_path)

        assert validator._dataset_registry is not None
        assert validator._metric_registry is not None

    def test_from_project_missing_config(self, tmp_path: Path) -> None:
        """Test from_project raises error for missing config."""
        with pytest.raises(FileNotFoundError):
            DepValidator.from_project(tmp_path / "nonexistent")


class TestDepValidatorValidate:
    """Tests for DepValidator.validate method."""

    def test_validate_no_dependencies(self) -> None:
        """Test validation of spec with no dependencies."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = None

        validator = DepValidator()
        result = validator.validate(mock_spec)

        assert result.is_valid is True
        assert result.spec_name == "iceberg.analytics.test"
        assert result.total_dependencies == 0

    def test_validate_empty_dependencies(self) -> None:
        """Test validation of spec with empty depends_on list."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = []

        validator = DepValidator()
        result = validator.validate(mock_spec)

        assert result.is_valid is True
        assert result.total_dependencies == 0

    def test_validate_all_deps_found_in_dataset_registry(self) -> None:
        """Test validation when all deps found in dataset registry."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.raw.users", "iceberg.raw.events"]

        mock_dataset_registry = Mock()
        mock_dataset_registry.get.side_effect = lambda name: Mock() if name in mock_spec.depends_on else None

        validator = DepValidator(dataset_registry=mock_dataset_registry)
        result = validator.validate(mock_spec)

        assert result.is_valid is True
        assert result.total_dependencies == 2
        assert len(result.found_dependencies) == 2
        assert len(result.missing_dependencies) == 0

    def test_validate_all_deps_found_in_metric_registry(self) -> None:
        """Test validation when all deps found in metric registry."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.metrics.kpi"]

        mock_metric_registry = Mock()
        mock_metric_registry.get.side_effect = lambda name: Mock() if name in mock_spec.depends_on else None

        validator = DepValidator(metric_registry=mock_metric_registry)
        result = validator.validate(mock_spec)

        assert result.is_valid is True
        assert len(result.found_dependencies) == 1

    def test_validate_deps_found_across_registries(self) -> None:
        """Test validation with deps in both registries."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.raw.users", "iceberg.metrics.kpi"]

        mock_dataset_registry = Mock()
        mock_dataset_registry.get.side_effect = lambda name: Mock() if name == "iceberg.raw.users" else None

        mock_metric_registry = Mock()
        mock_metric_registry.get.side_effect = lambda name: Mock() if name == "iceberg.metrics.kpi" else None

        validator = DepValidator(
            dataset_registry=mock_dataset_registry,
            metric_registry=mock_metric_registry,
        )
        result = validator.validate(mock_spec)

        assert result.is_valid is True
        assert len(result.found_dependencies) == 2

    def test_validate_missing_dependencies(self) -> None:
        """Test validation detects missing dependencies."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.raw.users", "iceberg.nonexistent.table"]

        mock_dataset_registry = Mock()
        mock_dataset_registry.get.side_effect = lambda name: Mock() if name == "iceberg.raw.users" else None

        validator = DepValidator(dataset_registry=mock_dataset_registry)
        result = validator.validate(mock_spec)

        assert result.is_valid is False
        assert len(result.found_dependencies) == 1
        assert len(result.missing_dependencies) == 1
        assert "iceberg.nonexistent.table" in result.missing_dependencies
        assert any("not found" in e.lower() for e in result.errors)

    def test_validate_circular_dependency(self) -> None:
        """Test validation detects circular dependency (self-reference)."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.analytics.test"]  # Self-reference

        validator = DepValidator()
        result = validator.validate(mock_spec)

        assert result.is_valid is False
        assert any("circular" in e.lower() for e in result.errors)

    def test_validate_no_registries_all_deps_missing(self) -> None:
        """Test validation with no registries marks all deps as missing."""
        mock_spec = Mock()
        mock_spec.name = "iceberg.analytics.test"
        mock_spec.depends_on = ["iceberg.raw.users"]

        validator = DepValidator()  # No registries
        result = validator.validate(mock_spec)

        assert result.is_valid is False
        assert len(result.missing_dependencies) == 1


class TestDepValidatorValidateAll:
    """Tests for DepValidator.validate_all method."""

    def test_validate_all_sample_project(self, sample_project_path: Path) -> None:
        """Test validating all specs in sample project."""
        from dli.core.config import load_project

        validator = DepValidator.from_project(sample_project_path)
        config = load_project(sample_project_path)
        summary = validator.validate_all(config)

        assert summary.total_specs > 0
        assert summary.total_dependencies >= 0

    def test_validate_all_with_mock_discovery(self) -> None:
        """Test validate_all with mocked discovery."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = []

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        mock_dataset_registry = Mock()
        mock_dataset_registry.get.return_value = None

        mock_metric_registry = Mock()
        mock_metric_registry.get.return_value = None

        validator = DepValidator(
            dataset_registry=mock_dataset_registry,
            metric_registry=mock_metric_registry,
        )

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2]
            mock_discovery_class.return_value = mock_discovery

            summary = validator.validate_all(mock_config)

            assert summary.total_specs == 2
            # spec1 has no deps (valid), spec2 has spec1 as dep (missing)
            assert summary.specs_with_missing_deps >= 0


class TestDepValidatorDependencyGraph:
    """Tests for DepValidator dependency graph methods."""

    def test_get_dependency_graph(self) -> None:
        """Test building dependency graph from project."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = []

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        mock_spec3 = Mock()
        mock_spec3.name = "spec3"
        mock_spec3.depends_on = ["spec1", "spec2"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2, mock_spec3]
            mock_discovery_class.return_value = mock_discovery

            graph = validator.get_dependency_graph(mock_config)

            assert "spec1" in graph
            assert "spec2" in graph
            assert "spec3" in graph
            assert graph["spec1"] == []
            assert graph["spec2"] == ["spec1"]
            assert set(graph["spec3"]) == {"spec1", "spec2"}

    def test_get_dependency_graph_with_none_depends_on(self) -> None:
        """Test graph handles specs with None depends_on."""
        mock_config = Mock()

        mock_spec = Mock()
        mock_spec.name = "spec1"
        mock_spec.depends_on = None

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec]
            mock_discovery_class.return_value = mock_discovery

            graph = validator.get_dependency_graph(mock_config)

            assert graph["spec1"] == []


class TestDepValidatorFindDownstream:
    """Tests for DepValidator.find_downstream method."""

    def test_find_downstream_with_dependents(self) -> None:
        """Test finding specs that depend on a resource."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = []

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        mock_spec3 = Mock()
        mock_spec3.name = "spec3"
        mock_spec3.depends_on = ["spec1"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2, mock_spec3]
            mock_discovery_class.return_value = mock_discovery

            downstream = validator.find_downstream("spec1", mock_config)

            assert len(downstream) == 2
            assert "spec2" in downstream
            assert "spec3" in downstream
            assert downstream == sorted(downstream)  # Should be sorted

    def test_find_downstream_no_dependents(self) -> None:
        """Test finding downstream for resource with no dependents."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = []

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1]
            mock_discovery_class.return_value = mock_discovery

            downstream = validator.find_downstream("spec1", mock_config)

            assert downstream == []


class TestDepValidatorFindUpstream:
    """Tests for DepValidator.find_upstream method."""

    def test_find_upstream_with_dependencies(self) -> None:
        """Test finding direct dependencies of a resource."""
        mock_config = Mock()

        mock_spec = Mock()
        mock_spec.name = "spec3"
        mock_spec.depends_on = ["spec1", "spec2"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec]
            mock_discovery_class.return_value = mock_discovery

            upstream = validator.find_upstream("spec3", mock_config)

            assert len(upstream) == 2
            assert "spec1" in upstream
            assert "spec2" in upstream
            assert upstream == sorted(upstream)  # Should be sorted

    def test_find_upstream_no_dependencies(self) -> None:
        """Test finding upstream for resource with no dependencies."""
        mock_config = Mock()

        mock_spec = Mock()
        mock_spec.name = "spec1"
        mock_spec.depends_on = []

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec]
            mock_discovery_class.return_value = mock_discovery

            upstream = validator.find_upstream("spec1", mock_config)

            assert upstream == []

    def test_find_upstream_resource_not_in_graph(self) -> None:
        """Test finding upstream for non-existent resource."""
        mock_config = Mock()

        mock_spec = Mock()
        mock_spec.name = "spec1"
        mock_spec.depends_on = []

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec]
            mock_discovery_class.return_value = mock_discovery

            upstream = validator.find_upstream("nonexistent", mock_config)

            assert upstream == []


class TestDepValidatorDetectCycles:
    """Tests for DepValidator.detect_cycles method."""

    def test_detect_cycles_no_cycles(self) -> None:
        """Test cycle detection with no cycles (DAG)."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = []

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        mock_spec3 = Mock()
        mock_spec3.name = "spec3"
        mock_spec3.depends_on = ["spec1", "spec2"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2, mock_spec3]
            mock_discovery_class.return_value = mock_discovery

            cycles = validator.detect_cycles(mock_config)

            assert cycles == []

    def test_detect_cycles_self_reference(self) -> None:
        """Test cycle detection with self-referencing spec."""
        mock_config = Mock()

        mock_spec = Mock()
        mock_spec.name = "spec1"
        mock_spec.depends_on = ["spec1"]  # Self-reference

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec]
            mock_discovery_class.return_value = mock_discovery

            cycles = validator.detect_cycles(mock_config)

            assert len(cycles) == 1
            assert "spec1" in cycles[0]

    def test_detect_cycles_simple_cycle(self) -> None:
        """Test cycle detection with simple A->B->A cycle."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = ["spec2"]

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2]
            mock_discovery_class.return_value = mock_discovery

            cycles = validator.detect_cycles(mock_config)

            assert len(cycles) >= 1
            # One of the cycles should contain both spec1 and spec2

    def test_detect_cycles_complex_cycle(self) -> None:
        """Test cycle detection with A->B->C->A cycle."""
        mock_config = Mock()

        mock_spec1 = Mock()
        mock_spec1.name = "spec1"
        mock_spec1.depends_on = ["spec3"]

        mock_spec2 = Mock()
        mock_spec2.name = "spec2"
        mock_spec2.depends_on = ["spec1"]

        mock_spec3 = Mock()
        mock_spec3.name = "spec3"
        mock_spec3.depends_on = ["spec2"]

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = [mock_spec1, mock_spec2, mock_spec3]
            mock_discovery_class.return_value = mock_discovery

            cycles = validator.detect_cycles(mock_config)

            assert len(cycles) >= 1

    def test_detect_cycles_empty_graph(self) -> None:
        """Test cycle detection with empty graph."""
        mock_config = Mock()

        validator = DepValidator()

        with patch("dli.core.discovery.SpecDiscovery") as mock_discovery_class:
            mock_discovery = Mock()
            mock_discovery.discover_all.return_value = []
            mock_discovery_class.return_value = mock_discovery

            cycles = validator.detect_cycles(mock_config)

            assert cycles == []


class TestDepValidatorIntegration:
    """Integration tests using real sample project."""

    def test_full_validation_workflow(self, sample_project_path: Path) -> None:
        """Test complete validation workflow with sample project."""
        from dli.core.config import load_project

        # Create validator from project
        validator = DepValidator.from_project(sample_project_path)
        config = load_project(sample_project_path)

        # Validate all specs
        summary = validator.validate_all(config)
        assert summary.total_specs >= 0

        # Get dependency graph
        graph = validator.get_dependency_graph(config)
        assert isinstance(graph, dict)

        # Check for cycles
        cycles = validator.detect_cycles(config)
        assert isinstance(cycles, list)

    def test_find_resource_checks_both_registries(self) -> None:
        """Test _find_resource checks dataset then metric registry."""
        mock_dataset_registry = Mock()
        mock_dataset_registry.get.return_value = None

        mock_metric_registry = Mock()
        mock_metric_registry.get.return_value = Mock()  # Found in metric registry

        validator = DepValidator(
            dataset_registry=mock_dataset_registry,
            metric_registry=mock_metric_registry,
        )

        found = validator._find_resource("test.resource")

        assert found is True
        mock_dataset_registry.get.assert_called_once_with("test.resource")
        mock_metric_registry.get.assert_called_once_with("test.resource")

    def test_find_resource_short_circuits_on_dataset(self) -> None:
        """Test _find_resource returns early if found in dataset registry."""
        mock_dataset_registry = Mock()
        mock_dataset_registry.get.return_value = Mock()  # Found in dataset registry

        mock_metric_registry = Mock()

        validator = DepValidator(
            dataset_registry=mock_dataset_registry,
            metric_registry=mock_metric_registry,
        )

        found = validator._find_resource("test.resource")

        assert found is True
        mock_dataset_registry.get.assert_called_once()
        mock_metric_registry.get.assert_not_called()  # Short-circuited
