"""DLI_HOME discovery and project configuration loading.

This module provides:
- ProjectConfig: dli.yaml project configuration
- DatasetDiscovery: Dataset spec file discovery and loading
- get_dli_home(): DLI_HOME environment variable or cwd
- load_project(): Load project configuration
"""

from __future__ import annotations

from collections.abc import Iterator
import logging
import os
from pathlib import Path

from pydantic import ValidationError
import yaml

from dli.core.models import DatasetSpec, ExecutionConfig

logger = logging.getLogger(__name__)


class ProjectConfig:
    """Project configuration loaded from dli.yaml.

    Attributes:
        config_path: Path to the dli.yaml file
        root_dir: Project root directory
    """

    def __init__(self, config_path: Path):
        """Initialize the project configuration.

        Args:
            config_path: Path to the dli.yaml file
        """
        self.config_path = config_path
        self.root_dir = config_path.parent

        with open(config_path, encoding="utf-8") as f:
            self._data = yaml.safe_load(f) or {}

    @property
    def version(self) -> str:
        """Get the configuration version."""
        return str(self._data.get("version", "1"))

    @property
    def project_name(self) -> str:
        """Get the project name."""
        return self._data.get("project", {}).get("name", "unnamed")

    @property
    def project_description(self) -> str:
        """Get the project description."""
        return self._data.get("project", {}).get("description", "")

    @property
    def datasets_dir(self) -> Path:
        """Get the datasets directory path."""
        rel_path = self._data.get("discovery", {}).get("datasets_dir", "datasets")
        return self.root_dir / rel_path

    @property
    def spec_patterns(self) -> list[str]:
        """Get the spec file patterns for discovery."""
        return self._data.get("discovery", {}).get(
            "spec_patterns", ["spec.*.yaml", "spec.yaml", "*.spec.yaml"]
        )

    @property
    def sql_patterns(self) -> list[str]:
        """Get the SQL file patterns for discovery."""
        return self._data.get("discovery", {}).get("sql_patterns", ["*.sql"])

    @property
    def defaults(self) -> dict:
        """Get the default settings."""
        return self._data.get("defaults", {})

    @property
    def default_dialect(self) -> str:
        """Get the default SQL dialect."""
        return self.defaults.get("dialect", "trino")

    @property
    def default_timeout(self) -> int:
        """Get the default timeout in seconds."""
        return self.defaults.get("timeout_seconds", 3600)

    @property
    def default_retry_count(self) -> int:
        """Get the default retry count."""
        return self.defaults.get("retry_count", 2)

    def get_environment(self, env_name: str) -> dict:
        """Get environment-specific configuration.

        Args:
            env_name: Environment name (e.g., "dev", "prod")

        Returns:
            Environment configuration dictionary
        """
        return self._data.get("environments", {}).get(env_name, {})

    def get_connection_string(self, env_name: str) -> str | None:
        """Get connection string for an environment.

        Args:
            env_name: Environment name

        Returns:
            Connection string or None if not configured
        """
        env = self.get_environment(env_name)
        return env.get("connection_string")


class DatasetDiscovery:
    """Dataset spec file discovery and loading.

    Attributes:
        config: Project configuration
    """

    def __init__(self, project_config: ProjectConfig):
        """Initialize the discovery service.

        Args:
            project_config: Project configuration
        """
        self.config = project_config

    def discover_all(self) -> Iterator[DatasetSpec]:
        """Discover and load all dataset specs.

        Yields:
            DatasetSpec objects for each valid spec file found
        """
        datasets_dir = self.config.datasets_dir
        if not datasets_dir.exists():
            logger.warning("Datasets directory not found: %s", datasets_dir)
            return

        for pattern in self.config.spec_patterns:
            for spec_path in datasets_dir.rglob(pattern):
                try:
                    yield self._load_spec(spec_path)
                except (OSError, ValueError, yaml.YAMLError, ValidationError) as e:
                    logger.warning("Failed to load %s: %s", spec_path, e)

    def _load_spec(self, spec_path: Path) -> DatasetSpec:
        """Load a single spec file.

        Args:
            spec_path: Path to the spec file

        Returns:
            DatasetSpec object

        Raises:
            Exception: If the spec file cannot be loaded or validated
        """
        with open(spec_path, encoding="utf-8") as f:
            data = yaml.safe_load(f)

        # Merge defaults into execution config
        if "execution" not in data:
            data["execution"] = {}

        execution_data = data["execution"]
        defaults = self.config.defaults

        for key in ["dialect", "timeout_seconds", "retry_count", "retry_delay_seconds"]:
            if key not in execution_data and key in defaults:
                execution_data[key] = defaults[key]

        # Ensure execution is an ExecutionConfig
        data["execution"] = ExecutionConfig.model_validate(execution_data)

        # Create the spec
        spec = DatasetSpec.model_validate(data)

        # Set internal paths using the clean interface
        spec.set_paths(spec_path)

        return spec

    def find_spec(self, dataset_name: str) -> DatasetSpec | None:
        """Find a spec by dataset name.

        Args:
            dataset_name: Fully qualified dataset name

        Returns:
            DatasetSpec if found, None otherwise
        """
        for spec in self.discover_all():
            if spec.name == dataset_name:
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


def get_dli_home() -> Path:
    """Get the DLI_HOME directory.

    Returns:
        Path to DLI_HOME (from environment variable or current directory)
    """
    dli_home = os.environ.get("DLI_HOME")
    if dli_home:
        return Path(dli_home)
    return Path.cwd()


def load_project(path: Path | None = None) -> ProjectConfig:
    """Load project configuration.

    Args:
        path: Path to the project directory (defaults to DLI_HOME)

    Returns:
        ProjectConfig object

    Raises:
        FileNotFoundError: If dli.yaml is not found
    """
    if path is None:
        path = get_dli_home()

    config_path = path / "dli.yaml"
    if not config_path.exists():
        msg = f"dli.yaml not found in {path}"
        raise FileNotFoundError(msg)

    return ProjectConfig(config_path)
