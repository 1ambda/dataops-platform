"""ConfigAPI - Library API for configuration management.

This module provides the ConfigAPI class for hierarchical configuration
access with source tracking.

Features:
- Layered configuration: global < project < local < env vars
- Template resolution: ${VAR}, ${VAR:-default}, ${VAR:?error}
- Source tracking: know where each value came from
- Secret masking: DLI_SECRET_* values displayed as ***

NOTE: Configuration changes are only allowed through CLI for safety.

Example:
    >>> from dli import ConfigAPI, ExecutionContext
    >>> api = ConfigAPI()
    >>> config = api.get_all()
    >>> print(config["server"]["url"])

    >>> # With source tracking
    >>> info = api.get_with_source("server.url")
    >>> print(f"{info.value} from {info.source_label}")
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from dli.core.client import BasecampClient, ServerConfig
from dli.models.common import (
    ConfigValue,
    EnvironmentInfo,
    ExecutionContext,
    ExecutionMode,
    TraceMode,
)
from dli.models.config import (
    ConfigSource,
    ConfigValidationResult,
    ConfigValueInfo,
    EnvironmentProfile,
)


class ConfigAPI:
    """Configuration management Library API with layered loading.

    Provides programmatic access to hierarchical configuration:
    - Get merged configuration from all layers
    - Get configuration values with source tracking
    - List and access named environments
    - Validate configuration
    - Check server status

    Configuration layers (in priority order, lowest to highest):
    1. ~/.dli/config.yaml (global)
    2. dli.yaml (project)
    3. .dli.local.yaml (local)
    4. Environment variables (DLI_*)
    5. CLI options (when applicable)

    NOTE: For safety, configuration changes are only allowed through
    the CLI (`dli config set`), not through this API.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import ConfigAPI
        >>> api = ConfigAPI()
        >>> print(api.get("server.url"))

        >>> # Get with source tracking
        >>> info = api.get_with_source("server.url")
        >>> print(f"{info.value} from {info.source_label}")

        >>> # Validate configuration
        >>> result = api.validate()
        >>> if not result.valid:
        ...     for error in result.errors:
        ...         print(f"Error: {error}")
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        project_path: Path | None = None,
        load_global: bool = True,
        load_local: bool = True,
    ) -> None:
        """Initialize ConfigAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
            project_path: Project directory (overrides context.project_path).
            load_global: Whether to load ~/.dli/config.yaml.
            load_local: Whether to load .dli.local.yaml.
        """
        self.context = context or ExecutionContext()
        self._project_path = project_path or self.context.project_path or Path.cwd()
        self._load_global = load_global
        self._load_local = load_local
        self._client: BasecampClient | None = None
        self._config: dict[str, Any] | None = None
        self._sources: dict[str, ConfigSource] | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"ConfigAPI(project_path={self._project_path!r})"

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

    def _load_config(self) -> None:
        """Load and merge all configuration layers."""
        from dli.core.config_loader import ConfigLoader

        loader = ConfigLoader(
            project_path=self._project_path,
            load_global=self._load_global,
            load_local=self._load_local,
        )
        self._config, self._sources = loader.load()

    def _ensure_loaded(self) -> None:
        """Ensure configuration is loaded."""
        if self._config is None:
            self._load_config()

    def get_all(self) -> dict[str, Any]:
        """Get merged configuration.

        Returns:
            Merged configuration dictionary with all layers applied.

        Example:
            >>> config = api.get_all()
            >>> print(config["server"]["url"])
        """
        self._ensure_loaded()
        return self._config or {}

    def get(self, key: str, default: Any = None) -> Any:
        """Get configuration value by key.

        Args:
            key: Dot-notation key (e.g., "server.url").
            default: Default value if not found.

        Returns:
            Configuration value or default.

        Example:
            >>> url = api.get("server.url")
            >>> timeout = api.get("defaults.timeout_seconds", 300)
        """
        config = self.get_all()
        parts = key.split(".")
        value = config
        for part in parts:
            if isinstance(value, dict) and part in value:
                value = value[part]
            else:
                return default
        return value

    def get_with_source(self, key: str) -> ConfigValueInfo | None:
        """Get configuration value with source information.

        Args:
            key: Dot-notation key.

        Returns:
            ConfigValueInfo with source tracking, or None if not found.

        Example:
            >>> info = api.get_with_source("server.url")
            >>> if info:
            ...     print(f"{info.value} from {info.source_label}")
        """
        value = self.get(key)
        if value is None:
            return None

        source = self._get_source(key)
        is_secret = self._is_secret_key(key)

        return ConfigValueInfo(
            key=key,
            value=value,
            source=source,
            is_secret=is_secret,
        )

    def get_all_with_sources(self) -> list[ConfigValueInfo]:
        """Get all configuration values with sources.

        Returns:
            List of ConfigValueInfo objects with source tracking.

        Example:
            >>> for info in api.get_all_with_sources():
            ...     print(f"{info.key} = {info.display_value} ({info.source_label})")
        """
        self._ensure_loaded()
        result: list[ConfigValueInfo] = []

        def flatten(
            obj: Any, prefix: str = ""
        ) -> None:
            if isinstance(obj, dict):
                for key, value in obj.items():
                    path = f"{prefix}.{key}" if prefix else key
                    if isinstance(value, dict):
                        flatten(value, path)
                    else:
                        source = self._get_source(path)
                        is_secret = self._is_secret_key(path)
                        result.append(
                            ConfigValueInfo(
                                key=path,
                                value=value,
                                source=source,
                                is_secret=is_secret,
                            )
                        )

        flatten(self._config or {})
        return result

    def list_environments(self) -> list[EnvironmentProfile]:
        """List available named environments.

        Returns:
            List of EnvironmentProfile objects.

        Example:
            >>> for env in api.list_environments():
            ...     status = "[active]" if env.is_active else ""
            ...     print(f"{env.name} {status}")
        """
        if self._is_mock_mode:
            return [
                EnvironmentProfile(name="dev", is_active=True),
                EnvironmentProfile(name="staging", is_active=False),
                EnvironmentProfile(name="prod", is_active=False),
            ]

        config = self.get_all()
        envs = config.get("environments", {})
        current_env = self.get_active_environment()

        result: list[EnvironmentProfile] = []
        for name, env_config in envs.items():
            if isinstance(env_config, dict):
                result.append(
                    EnvironmentProfile(
                        name=name,
                        server_url=env_config.get("server_url"),
                        dialect=env_config.get("dialect"),
                        catalog=env_config.get("catalog"),
                        connection_string="***" if env_config.get("connection_string") else None,
                        is_active=(name == current_env),
                    )
                )

        return result

    def get_environment(self, name: str) -> dict[str, Any]:
        """Get configuration for named environment.

        Templates in the environment config are resolved when accessed.

        Args:
            name: Environment name.

        Returns:
            Environment-specific configuration dictionary with resolved templates.

        Raises:
            ConfigEnvNotFoundError: If environment not found.
            ConfigurationError: If required template variable is missing.

        Example:
            >>> env_config = api.get_environment("prod")
            >>> print(env_config["server_url"])
        """
        config = self.get_all()
        envs = config.get("environments", {})

        if name not in envs:
            from dli.exceptions import ConfigEnvNotFoundError

            raise ConfigEnvNotFoundError(
                message=f"Environment '{name}' not found",
                env_name=name,
                available=list(envs.keys()),
            )

        # Resolve templates in the environment config
        env_config = envs[name]
        return self._resolve_environment_templates(env_config)

    def _resolve_environment_templates(self, env_config: dict[str, Any]) -> dict[str, Any]:
        """Resolve templates in environment config.

        Args:
            env_config: Environment configuration dictionary.

        Returns:
            Configuration with resolved templates.

        Raises:
            ConfigurationError: If required template variable is missing.
        """
        import re

        from dli.exceptions import ConfigurationError, ErrorCode

        template_pattern = re.compile(
            r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*)|:\?([^}]*))?\}"
        )

        def resolve_value(value: Any) -> Any:
            if isinstance(value, str):
                def replace_match(match: re.Match[str]) -> str:
                    var_name = match.group(1)
                    default_value = match.group(2)
                    error_message = match.group(3)

                    env_value = os.environ.get(var_name)

                    if env_value is not None:
                        return env_value

                    if default_value is not None:
                        return default_value

                    if error_message is not None:
                        raise ConfigurationError(
                            message=error_message,
                            code=ErrorCode.CONFIG_TEMPLATE_ERROR,
                        )

                    raise ConfigurationError(
                        message=f"Environment variable {var_name} is not set",
                        code=ErrorCode.CONFIG_TEMPLATE_ERROR,
                    )

                return template_pattern.sub(replace_match, value)
            elif isinstance(value, dict):
                return {k: resolve_value(v) for k, v in value.items()}
            elif isinstance(value, list):
                return [resolve_value(v) for v in value]
            return value

        return resolve_value(env_config)

    def get_active_environment(self) -> str | None:
        """Get currently active environment name.

        Checks in order:
        1. DLI_ENVIRONMENT env var
        2. active_environment in local config

        Returns:
            Active environment name or None.

        Example:
            >>> env = api.get_active_environment()
            >>> if env:
            ...     print(f"Active environment: {env}")
        """
        # Check env var first
        env = os.environ.get("DLI_ENVIRONMENT")
        if env:
            return env

        # Check local config
        return self.get("active_environment")

    def get_trace_mode(self) -> TraceMode:
        """Get trace ID display mode.

        Checks configuration in priority order:
        1. DLI_TRACE environment variable
        2. trace key in config files
        3. Default: TraceMode.ERROR_ONLY

        Valid values: "always", "error_only", "never"

        Returns:
            TraceMode enum value.

        Example:
            >>> mode = api.get_trace_mode()
            >>> if mode == TraceMode.ALWAYS:
            ...     print("Trace IDs shown in all output")

        Config file example:
            >>> # In ~/.dli/config.yaml or dli.yaml:
            >>> # trace: error_only
        """
        # Check env var first (highest priority)
        env_value = os.environ.get("DLI_TRACE")
        if env_value:
            try:
                return TraceMode(env_value.lower())
            except ValueError:
                # Invalid value, fall through to config
                pass

        # Check config file
        config_value = self.get("trace")
        if config_value:
            try:
                return TraceMode(str(config_value).lower())
            except ValueError:
                # Invalid value, use default
                pass

        # Default to showing trace only on errors
        return TraceMode.ERROR_ONLY

    def validate(self, *, strict: bool = False) -> ConfigValidationResult:
        """Validate configuration.

        Checks:
        - Required fields are present
        - Template variables are resolvable
        - Server URL is configured (warning if not)
        - API key is configured (warning if not)

        Args:
            strict: Treat warnings as errors.

        Returns:
            ConfigValidationResult with errors and warnings.

        Example:
            >>> result = api.validate(strict=True)
            >>> if not result.valid:
            ...     for error in result.errors:
            ...         print(f"Error: {error}")
        """
        errors: list[str] = []
        warnings: list[str] = []
        files_found: list[str] = []
        files_missing: list[str] = []

        # Check config files
        global_path = Path.home() / ".dli" / "config.yaml"
        project_path = self._project_path / "dli.yaml"
        local_path = self._project_path / ".dli.local.yaml"

        for path, name in [
            (global_path, "~/.dli/config.yaml"),
            (project_path, "dli.yaml"),
            (local_path, ".dli.local.yaml"),
        ]:
            if path.exists():
                files_found.append(name)
            else:
                files_missing.append(name)

        # Require at least project config
        if not project_path.exists():
            errors.append("dli.yaml not found in project directory")

        # Try to load and validate
        try:
            self._load_config()
            config = self._config or {}

            # Check server URL
            server_url = config.get("server", {}).get("url")
            if not server_url:
                warnings.append("server.url not configured")

            # Check API key
            api_key = config.get("server", {}).get("api_key")
            if not api_key:
                warnings.append("server.api_key not configured")

        except Exception as e:
            errors.append(f"Configuration loading failed: {e}")

        # In strict mode, warnings become errors
        if strict:
            errors.extend(warnings)
            warnings = []

        valid = len(errors) == 0

        return ConfigValidationResult(
            valid=valid,
            errors=errors,
            warnings=warnings,
            files_found=files_found,
            files_missing=files_missing,
        )

    def get_server_status(self) -> dict[str, Any]:
        """Check Basecamp server connection status.

        Returns:
            Dictionary with status information:
            - healthy: Whether server is reachable
            - url: Server URL
            - version: Server version (if available)
            - error: Error message (if unhealthy)

        Example:
            >>> status = api.get_server_status()
            >>> if status["healthy"]:
            ...     print(f"Server OK: {status['version']}")
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

    # Backward compatibility methods

    def get_current_environment(self) -> str:
        """Get current active environment name.

        Deprecated: Use get_active_environment() instead.

        Returns:
            Environment name (default: "dev").
        """
        return self.get_active_environment() or "dev"

    # Legacy method for backward compatibility with existing ConfigValue usage
    def _get_legacy_config_value(self, key: str) -> ConfigValue | None:
        """Get configuration value in legacy format.

        For backward compatibility with code using the old ConfigValue model.

        Args:
            key: Dot-notation key.

        Returns:
            ConfigValue (legacy format) or None.
        """
        value = self.get(key)
        if value is None:
            return None

        source = self._get_source(key)
        return ConfigValue(
            key=key,
            value=value,
            source=source.value,
        )

    def _get_source(self, key: str) -> ConfigSource:
        """Get source for a configuration key.

        Args:
            key: Dot-notation key.

        Returns:
            ConfigSource enum value.
        """
        self._ensure_loaded()
        return (self._sources or {}).get(key, ConfigSource.DEFAULT)

    def _is_secret_key(self, key: str) -> bool:
        """Check if a key represents a secret value.

        Detects secrets based on:
        1. Key name patterns (password, secret, api_key, token, credential)
        2. Template references to DLI_SECRET_* environment variables
        3. Template references to variables containing SECRET in name

        Args:
            key: Configuration key.

        Returns:
            True if the key is for a secret value.
        """
        secret_patterns = [
            "password",
            "secret",
            "api_key",
            "token",
            "credential",
        ]
        key_lower = key.lower()
        if any(pattern in key_lower for pattern in secret_patterns):
            return True

        # Check if the raw value references a DLI_SECRET_* variable
        value = self.get(key)
        if isinstance(value, str):
            # Check for ${DLI_SECRET_*} pattern or if value came from DLI_SECRET_* env var
            if "DLI_SECRET_" in value.upper() or "${DLI_SECRET_" in value:
                return True

        return False


__all__ = ["ConfigAPI"]
