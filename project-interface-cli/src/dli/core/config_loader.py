"""Hierarchical configuration loader with template resolution.

This module provides the ConfigLoader class for loading and merging
configuration from multiple sources:
1. ~/.dli/config.yaml (global)
2. dli.yaml (project)
3. .dli.local.yaml (local)
4. Environment variables (DLI_*)

Supports ${VAR} and ${VAR:-default} template syntax.

Example:
    >>> from dli.core.config_loader import ConfigLoader
    >>> loader = ConfigLoader(project_path=Path.cwd())
    >>> config, sources = loader.load()
    >>> print(config["server"]["url"])
"""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any

import yaml

from dli.models.config import ConfigSource


class ConfigLoader:
    """Hierarchical configuration loader with template resolution.

    Loads and merges configuration from multiple sources:
    1. ~/.dli/config.yaml (global)
    2. dli.yaml (project)
    3. .dli.local.yaml (local)
    4. Environment variables (DLI_*)

    Supports ${VAR} and ${VAR:-default} template syntax.

    Attributes:
        project_path: Project root directory.

    Example:
        >>> loader = ConfigLoader(project_path=Path("/my/project"))
        >>> config, sources = loader.load()
        >>> print(config["server"]["url"])
    """

    # Regex for ${VAR}, ${VAR:-default}, ${VAR:?error}
    TEMPLATE_PATTERN = re.compile(
        r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*)|:\?([^}]*))?\}"
    )

    def __init__(
        self,
        project_path: Path,
        *,
        load_global: bool = True,
        load_local: bool = True,
    ) -> None:
        """Initialize ConfigLoader.

        Args:
            project_path: Project root directory.
            load_global: Whether to load ~/.dli/config.yaml.
            load_local: Whether to load .dli.local.yaml.
        """
        self.project_path = project_path
        self._load_global = load_global
        self._load_local = load_local

    def load(self) -> tuple[dict[str, Any], dict[str, ConfigSource]]:
        """Load and merge all configuration layers.

        Returns:
            Tuple of (merged_config, source_map).

        Raises:
            ConfigurationError: If required template variable is missing.
        """
        import warnings

        config: dict[str, Any] = {}
        sources: dict[str, ConfigSource] = {}

        # Layer 1: Global config (lowest priority)
        if self._load_global:
            global_config, global_sources = self._load_global_config()
            config = self._deep_merge(config, global_config)
            sources.update(global_sources)

        # Layer 2: Project config
        project_config, project_sources = self._load_project_config()
        config = self._deep_merge(config, project_config)
        sources.update(project_sources)

        # Layer 3: Local config
        if self._load_local:
            local_config, local_sources = self._load_local_config()
            config = self._deep_merge(config, local_config)
            sources.update(local_sources)

            # Check if .dli.local.yaml is in .gitignore
            self._check_local_in_gitignore()

        # Layer 4: Environment variables (applied during template resolution)
        config = self._resolve_templates(config, sources)

        return config, sources

    def _check_local_in_gitignore(self) -> None:
        """Warn if .dli.local.yaml exists but is not in .gitignore."""
        import warnings

        local_path = self.project_path / ".dli.local.yaml"
        gitignore_path = self.project_path / ".gitignore"

        if not local_path.exists():
            return

        if not gitignore_path.exists():
            warnings.warn(
                ".dli.local.yaml exists but no .gitignore found. "
                "Consider adding .dli.local.yaml to .gitignore to prevent committing secrets.",
                UserWarning,
                stacklevel=3,
            )
            return

        try:
            with open(gitignore_path, encoding="utf-8") as f:
                gitignore_content = f.read()

            if ".dli.local.yaml" not in gitignore_content:
                warnings.warn(
                    ".dli.local.yaml is not in .gitignore. "
                    "Consider adding it to prevent committing secrets.",
                    UserWarning,
                    stacklevel=3,
                )
        except OSError:
            pass  # Ignore errors reading .gitignore

    def _load_global_config(self) -> tuple[dict[str, Any], dict[str, ConfigSource]]:
        """Load ~/.dli/config.yaml.

        Returns:
            Tuple of (config_dict, source_map).

        Raises:
            ConfigurationError: If YAML parsing fails.
        """
        global_path = Path.home() / ".dli" / "config.yaml"
        if not global_path.exists():
            return {}, {}

        try:
            with open(global_path, encoding="utf-8") as f:
                config = yaml.safe_load(f) or {}
        except yaml.YAMLError as e:
            from dli.exceptions import ConfigurationError, ErrorCode

            raise ConfigurationError(
                message=f"Invalid YAML in global config: {e}",
                code=ErrorCode.CONFIG_INVALID,
                config_path=global_path,
            ) from e

        sources = self._build_source_map(config, ConfigSource.GLOBAL)
        return config, sources

    def _load_project_config(self) -> tuple[dict[str, Any], dict[str, ConfigSource]]:
        """Load dli.yaml from project.

        Returns:
            Tuple of (config_dict, source_map).

        Raises:
            ConfigurationError: If YAML parsing fails.
        """
        config_path = self.project_path / "dli.yaml"
        if not config_path.exists():
            return {}, {}

        try:
            with open(config_path, encoding="utf-8") as f:
                config = yaml.safe_load(f) or {}
        except yaml.YAMLError as e:
            from dli.exceptions import ConfigurationError, ErrorCode

            raise ConfigurationError(
                message=f"Invalid YAML in project config: {e}",
                code=ErrorCode.CONFIG_INVALID,
                config_path=config_path,
            ) from e

        sources = self._build_source_map(config, ConfigSource.PROJECT)
        return config, sources

    def _load_local_config(self) -> tuple[dict[str, Any], dict[str, ConfigSource]]:
        """Load .dli.local.yaml from project.

        Returns:
            Tuple of (config_dict, source_map).

        Raises:
            ConfigurationError: If YAML parsing fails.
        """
        local_path = self.project_path / ".dli.local.yaml"
        if not local_path.exists():
            return {}, {}

        try:
            with open(local_path, encoding="utf-8") as f:
                config = yaml.safe_load(f) or {}
        except yaml.YAMLError as e:
            from dli.exceptions import ConfigurationError, ErrorCode

            raise ConfigurationError(
                message=f"Invalid YAML in local config: {e}",
                code=ErrorCode.CONFIG_INVALID,
                config_path=local_path,
            ) from e

        sources = self._build_source_map(config, ConfigSource.LOCAL)
        return config, sources

    def _resolve_templates(
        self,
        config: dict[str, Any],
        sources: dict[str, ConfigSource],
    ) -> dict[str, Any]:
        """Resolve ${VAR} templates in configuration.

        For the 'environments' section:
        - Templates are resolved if the env var IS set
        - Templates are kept as-is if the env var is NOT set (deferred)

        Args:
            config: Configuration dictionary.
            sources: Source tracking dictionary (mutated in place).

        Returns:
            Configuration with resolved templates.
        """

        def resolve_value(value: Any, path: str, lenient: bool = False) -> Any:
            if isinstance(value, str):
                return self._resolve_string_template(value, path, sources, lenient=lenient)
            elif isinstance(value, dict):
                return {
                    k: resolve_value(
                        v,
                        f"{path}.{k}",
                        # Lenient mode for environments section (missing vars kept as templates)
                        lenient or (path == "" and k == "environments"),
                    )
                    for k, v in value.items()
                }
            elif isinstance(value, list):
                return [
                    resolve_value(v, f"{path}[{i}]", lenient)
                    for i, v in enumerate(value)
                ]
            return value

        return resolve_value(config, "")

    def _resolve_string_template(
        self,
        value: str,
        path: str,
        sources: dict[str, ConfigSource],
        *,
        lenient: bool = False,
    ) -> str:
        """Resolve ${VAR:-default} template in string.

        Template syntax:
        - ${VAR} - Use env var; raise error if not set (unless lenient)
        - ${VAR:-default} - Use env var, or default if not set
        - ${VAR:?error} - Use env var, or raise error with message if not set

        Args:
            value: String value that may contain templates.
            path: Dot-notation path for source tracking.
            sources: Source tracking dictionary (mutated in place).
            lenient: If True, keep unresolved templates as-is instead of raising.

        Returns:
            Resolved string value.

        Raises:
            ConfigurationError: If required template variable is missing (unless lenient).
        """
        from dli.exceptions import ConfigurationError, ErrorCode

        def replace_match(match: re.Match[str]) -> str:
            var_name = match.group(1)
            default_value = match.group(2)
            error_message = match.group(3)

            env_value = os.environ.get(var_name)

            if env_value is not None:
                # Update source to ENV_VAR
                if path:
                    sources[path.lstrip(".")] = ConfigSource.ENV_VAR
                return env_value

            if default_value is not None:
                return default_value

            if error_message is not None:
                raise ConfigurationError(
                    message=error_message,
                    code=ErrorCode.CONFIG_TEMPLATE_ERROR,
                )

            # No value, no default, no error
            if lenient:
                # Keep template as-is for later resolution
                return match.group(0)

            # Strict mode - raise error
            raise ConfigurationError(
                message=f"Environment variable {var_name} is not set",
                code=ErrorCode.CONFIG_TEMPLATE_ERROR,
            )

        return self.TEMPLATE_PATTERN.sub(replace_match, value)

    def _deep_merge(
        self,
        base: dict[str, Any],
        override: dict[str, Any],
    ) -> dict[str, Any]:
        """Deep merge two dictionaries.

        Override values take precedence. Dictionaries are merged recursively.

        Args:
            base: Base dictionary.
            override: Override dictionary (higher priority).

        Returns:
            Merged dictionary.
        """
        result = base.copy()
        for key, value in override.items():
            if (
                key in result
                and isinstance(result[key], dict)
                and isinstance(value, dict)
            ):
                result[key] = self._deep_merge(result[key], value)
            else:
                result[key] = value
        return result

    def _build_source_map(
        self,
        config: dict[str, Any],
        source: ConfigSource,
        prefix: str = "",
    ) -> dict[str, ConfigSource]:
        """Build flat source map from nested config.

        Args:
            config: Configuration dictionary.
            source: Source to assign.
            prefix: Key prefix for nested values.

        Returns:
            Flat dictionary mapping keys to sources.
        """
        sources: dict[str, ConfigSource] = {}
        for key, value in config.items():
            path = f"{prefix}.{key}" if prefix else key
            sources[path] = source
            if isinstance(value, dict):
                sources.update(self._build_source_map(value, source, path))
        return sources


__all__ = ["ConfigLoader"]
