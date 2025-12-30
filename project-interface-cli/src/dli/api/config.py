"""ConfigAPI - Library API for configuration management.

This module provides the ConfigAPI class for read-only access to
DLI configuration settings.

NOTE: Configuration changes are only allowed through CLI for safety.

Example:
    >>> from dli import ConfigAPI, ExecutionContext
    >>> api = ConfigAPI()
    >>> value = api.get("server.url")
    >>> envs = api.list_environments()
"""

from __future__ import annotations

from typing import Any

from dli.core.client import BasecampClient, ServerConfig
from dli.models.common import (
    ConfigValue,
    EnvironmentInfo,
    ExecutionContext,
    ExecutionMode,
)


class ConfigAPI:
    """Configuration management Library API (read-only).

    Provides programmatic access to configuration:
    - Get configuration values
    - List available environments
    - Check server status

    NOTE: For safety, configuration changes are only allowed through
    the CLI (`dli config set`), not through this API.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import ConfigAPI
        >>> api = ConfigAPI()
        >>> print(api.get_current_environment())
        dev
        >>> status = api.get_server_status()
        >>> print(status["healthy"])
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """Initialize ConfigAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
        """
        self.context = context or ExecutionContext()
        self._client: BasecampClient | None = None
        self._project_config: Any | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"ConfigAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance."""
        if self._client is None:
            config = ServerConfig(
                url=self.context.server_url or "http://localhost:8081",
            )
            self._client = BasecampClient(
                config=config,
                mock_mode=self._is_mock_mode,
            )
        return self._client

    def _get_project_config(self) -> Any | None:
        """Get ProjectConfig if project_path is set."""
        if self._project_config is None and self.context.project_path:
            try:
                from dli.core.config import load_project

                self._project_config = load_project(self.context.project_path)
            except FileNotFoundError:
                pass
        return self._project_config

    def get(self, key: str) -> ConfigValue | None:
        """Get configuration value by key.

        Supported keys:
        - project.name
        - project.description
        - server.url
        - server.timeout
        - defaults.dialect
        - defaults.timeout_seconds

        Args:
            key: Dot-separated configuration key.

        Returns:
            ConfigValue if found, None otherwise.
        """
        project_config = self._get_project_config()

        if project_config is None:
            # Return from context if no project config
            if key == "server.url":
                return ConfigValue(
                    key=key,
                    value=self.context.server_url,
                    source="context",
                )
            if key == "defaults.dialect":
                return ConfigValue(
                    key=key,
                    value=self.context.dialect,
                    source="context",
                )
            return None

        # Get from project config
        parts = key.split(".")
        value: Any = None
        source = "config"

        if parts[0] == "project":
            if len(parts) > 1 and parts[1] == "name":
                value = project_config.project_name
            elif len(parts) > 1 and parts[1] == "description":
                value = project_config.project_description
        elif parts[0] == "server":
            if len(parts) > 1 and parts[1] == "url":
                value = project_config.server_url
            elif len(parts) > 1 and parts[1] == "timeout":
                value = project_config.server_timeout
        elif parts[0] == "defaults":
            if len(parts) > 1 and parts[1] == "dialect":
                value = project_config.default_dialect
            elif len(parts) > 1 and parts[1] == "timeout_seconds":
                value = project_config.default_timeout

        if value is not None:
            return ConfigValue(key=key, value=value, source=source)

        return None

    def list_environments(self) -> list[EnvironmentInfo]:
        """List available environments.

        Returns:
            List of EnvironmentInfo objects.
        """
        if self._is_mock_mode:
            return [
                EnvironmentInfo(name="dev", is_active=True),
                EnvironmentInfo(name="staging", is_active=False),
                EnvironmentInfo(name="prod", is_active=False),
            ]

        project_config = self._get_project_config()

        if project_config is None:
            return []

        # Get environments from config
        # Note: This requires accessing internal _data which is not ideal
        # In a real implementation, ProjectConfig would expose environments
        environments: list[EnvironmentInfo] = []

        # Add default environment
        current_env = self.get_current_environment()

        # Check common environment names
        for env_name in ["dev", "staging", "prod", "local"]:
            env_config = project_config.get_environment(env_name)
            if env_config:
                environments.append(
                    EnvironmentInfo(
                        name=env_name,
                        connection_string="***",  # Masked for security
                        is_active=(env_name == current_env),
                    )
                )

        return environments

    def get_current_environment(self) -> str:
        """Get current active environment name.

        Returns:
            Environment name (default: "dev").
        """
        import os

        return os.environ.get("DLI_ENV", "dev")

    def get_server_status(self) -> dict[str, Any]:
        """Check Basecamp server connection status.

        Returns:
            Dictionary with status information:
            - healthy: Whether server is reachable
            - url: Server URL
            - version: Server version (if available)
            - error: Error message (if unhealthy)
        """
        client = self._get_client()

        try:
            response = client.health_check()

            if response.success and response.data and isinstance(response.data, dict):
                return {
                    "healthy": True,
                    "url": client.config.url,
                    "version": response.data.get("version", "unknown"),
                }

            return {
                "healthy": False,
                "url": client.config.url,
                "error": response.error or "Unknown error",
            }

        except Exception as e:
            return {
                "healthy": False,
                "url": client.config.url,
                "error": str(e),
            }


__all__ = ["ConfigAPI"]
