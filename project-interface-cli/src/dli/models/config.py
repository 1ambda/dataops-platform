"""Configuration models for DLI Library API.

This module provides configuration-related models for the layered
configuration system:
- ConfigSource: Enum for configuration value sources
- ConfigValueInfo: Extended config value with source tracking
- ValidationResult: Config validation result model
- EnvironmentProfile: Named environment configuration

Example:
    >>> from dli.models.config import ConfigSource, ConfigValueInfo
    >>> value = ConfigValueInfo(
    ...     key="server.url",
    ...     value="http://localhost:8081",
    ...     source=ConfigSource.LOCAL,
    ... )
    >>> print(f"{value.key} from {value.source.value}")
"""

from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ConfigSource(str, Enum):
    """Configuration value source.

    Indicates where a configuration value originated from,
    in order of priority (lowest to highest):
    - DEFAULT: Built-in default value
    - GLOBAL: ~/.dli/config.yaml
    - PROJECT: dli.yaml
    - LOCAL: .dli.local.yaml
    - ENV_VAR: Environment variable
    - CLI: Command-line option

    Example:
        >>> if source == ConfigSource.ENV_VAR:
        ...     print("Value from environment")
    """

    DEFAULT = "default"
    GLOBAL = "global"  # ~/.dli/config.yaml
    PROJECT = "project"  # dli.yaml
    LOCAL = "local"  # .dli.local.yaml
    ENV_VAR = "env"  # Environment variable
    CLI = "cli"  # CLI option


class ConfigValueInfo(BaseModel):
    """Configuration value with extended source tracking.

    Provides detailed information about a configuration value including
    its source, whether it's a secret, and the raw template value.

    Attributes:
        key: Configuration key (dot notation).
        value: Resolved configuration value.
        source: Where the value came from.
        is_secret: Whether the value is sensitive.
        raw_value: Original template value before resolution.

    Example:
        >>> info = ConfigValueInfo(
        ...     key="server.api_key",
        ...     value="sk-abc123",
        ...     source=ConfigSource.ENV_VAR,
        ...     is_secret=True,
        ...     raw_value="${DLI_SECRET_API_KEY}",
        ... )
        >>> print(info.display_value)  # Shows "***"
    """

    model_config = ConfigDict(frozen=True)

    key: str = Field(..., description="Configuration key (dot notation)")
    value: Any = Field(..., description="Resolved value")
    source: ConfigSource = Field(
        default=ConfigSource.DEFAULT, description="Value source"
    )
    is_secret: bool = Field(default=False, description="Is sensitive value")
    raw_value: str | None = Field(
        default=None, description="Template before resolution"
    )

    @property
    def display_value(self) -> str:
        """Get display value (masked if secret).

        Returns:
            "***" if secret, otherwise string representation of value.
        """
        if self.is_secret and self.value is not None:
            return "***"
        return str(self.value) if self.value is not None else "(not set)"

    @property
    def source_label(self) -> str:
        """Get human-readable source label.

        Returns:
            Formatted source label string.
        """
        labels = {
            ConfigSource.DEFAULT: "(default)",
            ConfigSource.GLOBAL: "~/.dli/config.yaml",
            ConfigSource.PROJECT: "dli.yaml",
            ConfigSource.LOCAL: ".dli.local.yaml",
            ConfigSource.ENV_VAR: "environment variable",
            ConfigSource.CLI: "CLI option",
        }
        return labels.get(self.source, self.source.value)


class ConfigValidationResult(BaseModel):
    """Configuration validation result.

    Returned by ConfigAPI.validate() to indicate configuration validity.

    Attributes:
        valid: Whether the configuration is valid.
        errors: List of error messages.
        warnings: List of warning messages.
        files_found: List of config files that were found.
        files_missing: List of config files that were not found.

    Example:
        >>> result = api.validate(strict=True)
        >>> if not result.valid:
        ...     for error in result.errors:
        ...         print(f"Error: {error}")
    """

    model_config = ConfigDict(frozen=True)

    valid: bool = Field(..., description="Whether configuration is valid")
    errors: list[str] = Field(default_factory=list, description="Error messages")
    warnings: list[str] = Field(default_factory=list, description="Warning messages")
    files_found: list[str] = Field(
        default_factory=list, description="Config files found"
    )
    files_missing: list[str] = Field(
        default_factory=list, description="Config files missing"
    )

    @property
    def has_errors(self) -> bool:
        """Check if there are any errors."""
        return len(self.errors) > 0

    @property
    def has_warnings(self) -> bool:
        """Check if there are any warnings."""
        return len(self.warnings) > 0


class EnvironmentProfile(BaseModel):
    """Named environment configuration profile.

    Represents a named environment (e.g., dev, staging, prod) with
    its specific configuration settings.

    Attributes:
        name: Environment name.
        server_url: Server URL for this environment.
        dialect: SQL dialect for this environment.
        catalog: Default catalog name.
        connection_string: Database connection string (masked).
        is_active: Whether this is the currently active environment.

    Example:
        >>> profile = EnvironmentProfile(
        ...     name="prod",
        ...     server_url="https://prod.basecamp.io",
        ...     dialect="bigquery",
        ... )
    """

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., description="Environment name")
    server_url: str | None = Field(default=None, description="Server URL")
    dialect: str | None = Field(default=None, description="SQL dialect")
    catalog: str | None = Field(default=None, description="Default catalog")
    connection_string: str | None = Field(
        default=None, description="Connection string (masked)"
    )
    is_active: bool = Field(default=False, description="Is active environment")


__all__ = [
    "ConfigSource",
    "ConfigValidationResult",
    "ConfigValueInfo",
    "EnvironmentProfile",
]
