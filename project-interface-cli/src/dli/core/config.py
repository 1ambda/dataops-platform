"""DLI Project Configuration.

This module provides:
- ProjectConfig: dli.yaml project configuration with metrics/datasets separation
- get_dli_home(): DLI_HOME environment variable or cwd
- load_project(): Load project configuration
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import yaml

from dli.core.types import EnvironmentConfig, ProjectDefaults


class ProjectConfig:
    """Project configuration loaded from dli.yaml.

    Supports separate configuration for metrics and datasets following
    the metric/dataset separation pattern.

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
    def metrics_dir(self) -> Path:
        """Get the metrics directory path.

        Defaults to 'metrics' directory in the project root.
        Metrics are stored separately from datasets for better organization.
        """
        rel_path = self._data.get("discovery", {}).get("metrics_dir", "metrics")
        return self.root_dir / rel_path

    @property
    def metric_patterns(self) -> list[str]:
        """Get the metric spec file patterns for discovery.

        File naming convention: metric.{catalog}.{schema}.{name}.yaml
        """
        return self._data.get("discovery", {}).get(
            "metric_patterns", ["metric.*.yaml", "metric.yaml"]
        )

    @property
    def dataset_patterns(self) -> list[str]:
        """Get the dataset spec file patterns for discovery.

        File naming convention: dataset.{catalog}.{schema}.{name}.yaml
        """
        return self._data.get("discovery", {}).get(
            "dataset_patterns", ["dataset.*.yaml", "dataset.yaml"]
        )

    @property
    def sql_patterns(self) -> list[str]:
        """Get the SQL file patterns for discovery."""
        return self._data.get("discovery", {}).get("sql_patterns", ["*.sql"])

    @property
    def defaults(self) -> ProjectDefaults:
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

    def get_environment(self, env_name: str) -> EnvironmentConfig:
        """Get environment-specific configuration.

        Args:
            env_name: Environment name (e.g., "dev", "prod")

        Returns:
            EnvironmentConfig dictionary with environment-specific settings
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

    @property
    def server_url(self) -> str | None:
        """Get the Basecamp server URL.

        Returns:
            Server URL or None if not configured
        """
        return self._data.get("server", {}).get("url")

    @property
    def server_timeout(self) -> int:
        """Get the server request timeout in seconds.

        Returns:
            Timeout in seconds (default: 30)
        """
        return self._data.get("server", {}).get("timeout", 30)

    @property
    def server_api_key(self) -> str | None:
        """Get the server API key for authentication.

        Returns:
            API key or None if not configured
        """
        return self._data.get("server", {}).get("api_key")


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
