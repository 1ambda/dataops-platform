"""DLI Spec discovery and loading.

This module provides:
- SpecDiscovery: Unified spec file discovery for both metrics and datasets
- DatasetDiscovery: Dataset spec file discovery and loading

For project configuration, see dli.core.config module.

File naming conventions:
- Metric specs: metric.{catalog}.{schema}.{name}.yaml in metrics_dir
- Dataset specs: dataset.{catalog}.{schema}.{name}.yaml in datasets_dir
"""

from __future__ import annotations

from collections.abc import Iterator
import logging
from pathlib import Path
from typing import TypeVar, cast

from pydantic import ValidationError
import yaml

# Import from config module for backward compatibility re-exports
from dli.core.config import (
    ProjectConfig,
    get_dli_home,
    load_project,
)
from dli.core.models import (
    DatasetSpec,
    ExecutionConfig,
    MetricSpec,
    QueryType,
    SpecBase,
    SpecType,
)

# Re-export for backward compatibility
__all__ = [
    "DatasetDiscovery",
    "ProjectConfig",
    "SpecDiscovery",
    "get_dli_home",
    "load_project",
]

logger = logging.getLogger(__name__)

# Type variable for spec types
SpecT = TypeVar("SpecT", MetricSpec, DatasetSpec)


class SpecDiscovery:
    """Unified spec file discovery for both metrics and datasets.

    This class provides discovery for all spec types following the
    metric/dataset separation pattern.

    Attributes:
        config: Project configuration
    """

    # Filename prefixes for auto-detection
    _METRIC_PREFIX = "metric."
    _DATASET_PREFIX = "dataset."

    def __init__(self, project_config: ProjectConfig):
        """Initialize the discovery service.

        Args:
            project_config: Project configuration
        """
        self.config = project_config

    def discover_all(self) -> Iterator[SpecBase]:
        """Discover and load all specs (both metrics and datasets).

        Yields:
            SpecBase objects (MetricSpec or DatasetSpec) for each valid spec file
        """
        yield from self.discover_metrics()
        yield from self.discover_datasets()

    def discover_metrics(self) -> Iterator[MetricSpec]:
        """Discover and load all metric specs.

        Searches in:
        1. metrics_dir with metric patterns
        2. datasets_dir with metric patterns (for mixed layouts)

        Yields:
            MetricSpec objects for each valid metric spec file found
        """
        seen_paths: set[Path] = set()

        # Primary: metrics directory with metric patterns
        metrics_dir = self.config.metrics_dir
        if metrics_dir.exists():
            for spec in self._discover_specs_in_dir(
                metrics_dir,
                self.config.metric_patterns,
                SpecType.METRIC,
                seen_paths,
            ):
                yield cast(MetricSpec, spec)

        # Secondary: datasets directory with metric patterns (for mixed layouts)
        datasets_dir = self.config.datasets_dir
        if datasets_dir.exists():
            for spec in self._discover_specs_in_dir(
                datasets_dir,
                self.config.metric_patterns,
                SpecType.METRIC,
                seen_paths,
            ):
                yield cast(MetricSpec, spec)

    def discover_datasets(self) -> Iterator[DatasetSpec]:
        """Discover and load all dataset specs.

        Searches in datasets_dir with dataset patterns (dataset.*.yaml).

        Yields:
            DatasetSpec objects for each valid dataset spec file found
        """
        datasets_dir = self.config.datasets_dir
        if not datasets_dir.exists():
            logger.warning("Datasets directory not found: %s", datasets_dir)
            return

        seen_paths: set[Path] = set()

        # Dataset patterns only (dataset.*.yaml)
        for spec in self._discover_specs_in_dir(
            datasets_dir,
            self.config.dataset_patterns,
            SpecType.DATASET,
            seen_paths,
        ):
            yield cast(DatasetSpec, spec)

    def _discover_specs_in_dir(
        self,
        directory: Path,
        patterns: list[str],
        expected_type: SpecType,
        seen_paths: set[Path],
    ) -> Iterator[MetricSpec | DatasetSpec]:
        """Discover specs of a specific type in a directory.

        Args:
            directory: Directory to search
            patterns: File patterns to match
            expected_type: Expected spec type (Metric or Dataset)
            seen_paths: Set of already processed paths (modified in place)

        Yields:
            Spec objects matching the expected type
        """
        for pattern in patterns:
            for spec_path in directory.rglob(pattern):
                if spec_path in seen_paths:
                    continue
                try:
                    spec = self._load_spec(spec_path, expected_type)
                    if spec:
                        seen_paths.add(spec_path)
                        yield spec
                except (OSError, ValueError, yaml.YAMLError, ValidationError) as e:
                    log_type = "metric" if expected_type == SpecType.METRIC else "dataset"
                    logger.warning("Failed to load %s %s: %s", log_type, spec_path, e)

    def _load_yaml_file(self, spec_path: Path) -> dict:
        """Load and parse a YAML spec file.

        Args:
            spec_path: Path to the YAML file

        Returns:
            Parsed YAML data as dictionary
        """
        with open(spec_path, encoding="utf-8") as f:
            return yaml.safe_load(f) or {}

    def _load_spec(
        self,
        spec_path: Path,
        expected_type: SpecType,
    ) -> MetricSpec | DatasetSpec | None:
        """Load a spec file of the expected type.

        Args:
            spec_path: Path to the spec file
            expected_type: Expected spec type (Metric or Dataset)

        Returns:
            MetricSpec or DatasetSpec object, or None if type doesn't match
        """
        data = self._load_yaml_file(spec_path)
        spec_type_str = data.get("type", "").lower()
        filename = spec_path.name.lower()

        # Determine the actual type from data or filename
        actual_type = self._detect_spec_type(spec_type_str, filename, expected_type)
        if actual_type != expected_type:
            return None

        # Set type and query_type if not already present
        self._set_type_defaults(data, actual_type)
        self._merge_execution_defaults(data)

        # Create the appropriate spec type
        if actual_type == SpecType.METRIC:
            spec = MetricSpec.model_validate(data)
        else:
            spec = DatasetSpec.model_validate(data)

        spec.set_paths(spec_path)
        return spec

    def _detect_spec_type(
        self,
        spec_type_str: str,
        filename: str,
        fallback: SpecType,
    ) -> SpecType:
        """Detect the spec type from type field or filename.

        Args:
            spec_type_str: The type field value (lowercased)
            filename: The spec filename (lowercased)
            fallback: Fallback type if auto-detection fails

        Returns:
            Detected SpecType
        """
        # Explicit type field takes precedence
        if spec_type_str == SpecType.METRIC.value.lower():
            return SpecType.METRIC
        if spec_type_str == SpecType.DATASET.value.lower():
            return SpecType.DATASET

        # Auto-detect from filename prefix
        if filename.startswith(self._METRIC_PREFIX):
            return SpecType.METRIC
        if filename.startswith(self._DATASET_PREFIX):
            return SpecType.DATASET

        return fallback

    def _set_type_defaults(self, data: dict, spec_type: SpecType) -> None:
        """Set default type and query_type based on spec type.

        Args:
            data: Spec data dictionary (modified in place)
            spec_type: The spec type to set defaults for
        """
        data["type"] = spec_type.value
        if "query_type" not in data:
            if spec_type == SpecType.METRIC:
                data["query_type"] = QueryType.SELECT.value
            else:
                data["query_type"] = QueryType.DML.value

    def _merge_execution_defaults(self, data: dict) -> None:
        """Merge project defaults into execution config.

        Args:
            data: Spec data dictionary (modified in place)
        """
        if "execution" not in data:
            data["execution"] = {}

        execution_data = data["execution"]
        defaults = self.config.defaults

        for key in ["dialect", "timeout_seconds", "retry_count", "retry_delay_seconds"]:
            if key not in execution_data and key in defaults:
                execution_data[key] = defaults[key]

        data["execution"] = ExecutionConfig.model_validate(execution_data)

    def find_spec(self, name: str) -> SpecBase | None:
        """Find a spec by name.

        Args:
            name: Fully qualified spec name

        Returns:
            MetricSpec or DatasetSpec if found, None otherwise
        """
        for spec in self.discover_all():
            if spec.name == name:
                return spec
        return None

    def find_metric(self, name: str) -> MetricSpec | None:
        """Find a metric spec by name.

        Args:
            name: Fully qualified metric name

        Returns:
            MetricSpec if found, None otherwise
        """
        for spec in self.discover_metrics():
            if spec.name == name:
                return spec
        return None

    def find_dataset(self, name: str) -> DatasetSpec | None:
        """Find a dataset spec by name.

        Args:
            name: Fully qualified dataset name

        Returns:
            DatasetSpec if found, None otherwise
        """
        for spec in self.discover_datasets():
            if spec.name == name:
                return spec
        return None

    def discover_sql_files(self, base_dir: Path | None = None) -> Iterator[Path]:
        """Discover all SQL files in the project.

        Args:
            base_dir: Base directory to search (defaults to datasets_dir)

        Yields:
            Path objects for each SQL file found
        """
        search_dir = base_dir or self.config.datasets_dir
        if not search_dir.exists():
            return

        for pattern in self.config.sql_patterns:
            yield from search_dir.rglob(pattern)


class DatasetDiscovery:
    """Dataset spec file discovery and loading.

    Note: For new code, prefer SpecDiscovery which supports both metrics and datasets.
    This class delegates to SpecDiscovery for dataset discovery.

    Attributes:
        config: Project configuration
    """

    def __init__(self, project_config: ProjectConfig):
        """Initialize the discovery service.

        Args:
            project_config: Project configuration
        """
        self.config = project_config
        self._spec_discovery = SpecDiscovery(project_config)

    def discover_all(self) -> Iterator[DatasetSpec]:
        """Discover and load all dataset specs.

        Uses dataset.*.yaml patterns for discovery.

        Yields:
            DatasetSpec objects for each valid spec file found
        """
        yield from self._spec_discovery.discover_datasets()

    def find_spec(self, dataset_name: str) -> DatasetSpec | None:
        """Find a spec by dataset name.

        Args:
            dataset_name: Fully qualified dataset name

        Returns:
            DatasetSpec if found, None otherwise
        """
        return self._spec_discovery.find_dataset(dataset_name)

    def discover_sql_files(self, base_dir: Path | None = None) -> Iterator[Path]:
        """Discover all SQL files in the project.

        Args:
            base_dir: Base directory to search (defaults to datasets_dir)

        Yields:
            Path objects for each SQL file found
        """
        return self._spec_discovery.discover_sql_files(base_dir)
