"""Configuration models for DLI Library API.

This module provides configuration-related models for the layered
configuration system:
- ConfigSource: Enum for configuration value sources
- ConfigValueInfo: Extended config value with source tracking
- ValidationResult: Config validation result model
- EnvironmentProfile: Named environment configuration
- ExecutionConfig: Execution settings (mode, dialect, timeout, etc.)
- BigQueryConfig: BigQuery-specific settings
- TrinoConfig: Trino-specific settings
- ServerModeConfig: Server mode connection settings

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
from typing import Any, Literal

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


# Type alias for execution mode string values
ExecutionModeStr = Literal["local", "server", "mock"]

# Type alias for SQL dialect string values
SQLDialectStr = Literal["trino", "bigquery", "snowflake", "duckdb", "spark"]

# Type alias for Trino authentication types
TrinoAuthType = Literal["basic", "oidc", "jwt", "kerberos", "none"]


class BigQueryConfig(BaseModel):
    """BigQuery-specific execution configuration.

    Settings for executing queries against BigQuery in local mode.

    Attributes:
        project: GCP project ID for BigQuery.
        location: BigQuery dataset location (e.g., "US", "EU").
        credentials_path: Path to service account JSON key file.
        job_timeout_ms: Maximum job execution time in milliseconds.

    Example:
        >>> config = BigQueryConfig(
        ...     project="my-gcp-project",
        ...     location="US",
        ... )
    """

    model_config = ConfigDict(frozen=True, extra="allow")

    project: str | None = Field(default=None, description="GCP project ID")
    location: str | None = Field(default=None, description="BigQuery location")
    credentials_path: str | None = Field(
        default=None, description="Path to credentials JSON"
    )
    job_timeout_ms: int | None = Field(
        default=None, description="Job timeout in milliseconds"
    )


class TrinoConfig(BaseModel):
    """Trino-specific execution configuration.

    Settings for executing queries against Trino in local mode.

    Attributes:
        host: Trino coordinator hostname.
        port: Trino coordinator port (default: 8080).
        user: Trino user for authentication.
        catalog: Default Trino catalog.
        schema: Default Trino schema.
        ssl: Whether to use SSL/TLS connection.
        auth_type: Authentication type (basic, oidc, jwt, kerberos, none).
        auth_token: Authentication token (for oidc/jwt).
        password: Password for basic authentication.

    Example:
        >>> config = TrinoConfig(
        ...     host="trino.example.com",
        ...     port=8443,
        ...     user="analyst",
        ...     catalog="iceberg",
        ...     schema="analytics",
        ...     ssl=True,
        ...     auth_type="oidc",
        ...     auth_token="${DLI_TRINO_TOKEN}",
        ... )
    """

    model_config = ConfigDict(frozen=True, extra="allow")

    host: str | None = Field(default=None, description="Trino coordinator host")
    port: int = Field(default=8080, description="Trino coordinator port")
    user: str | None = Field(default=None, description="Trino user")
    catalog: str | None = Field(default=None, description="Default catalog")
    schema_name: str | None = Field(
        default=None, alias="schema", description="Default schema"
    )
    ssl: bool = Field(default=False, description="Use SSL/TLS")
    auth_type: TrinoAuthType = Field(default="none", description="Authentication type")
    auth_token: str | None = Field(
        default=None, description="Auth token (oidc/jwt)"
    )
    password: str | None = Field(default=None, description="Password (basic auth)")


class ServerModeConfig(BaseModel):
    """Server mode execution configuration.

    Settings for executing queries via Basecamp Server API.

    Attributes:
        url: Basecamp server URL.
        api_token: API authentication token.
        verify_ssl: Whether to verify SSL certificates.

    Example:
        >>> config = ServerModeConfig(
        ...     url="https://basecamp.example.com",
        ...     api_token="${DLI_API_TOKEN}",
        ... )
    """

    model_config = ConfigDict(frozen=True, extra="allow")

    url: str | None = Field(default=None, description="Basecamp server URL")
    api_token: str | None = Field(default=None, description="API token")
    verify_ssl: bool = Field(default=True, description="Verify SSL certificates")


class ExecutionConfig(BaseModel):
    """Execution configuration section.

    Top-level execution settings that can be defined in dli.yaml:

    ```yaml
    execution:
      mode: local           # local, server, mock
      dialect: bigquery     # bigquery, trino, snowflake, duckdb, spark
      timeout: 300          # timeout in seconds

      bigquery:
        project: my-gcp-project
        location: US

      trino:
        host: trino.example.com
        port: 8080
        user: analyst
        catalog: iceberg
        schema: analytics
        ssl: true
        auth_type: oidc
        auth_token: ${DLI_TRINO_TOKEN}

      server:
        url: https://basecamp.example.com
        api_token: ${DLI_API_TOKEN}
    ```

    Attributes:
        mode: Execution mode (local, server, mock). Default: local.
        dialect: SQL dialect (bigquery, trino, etc.). Default: bigquery.
        timeout: Execution timeout in seconds. Default: 300.
        bigquery: BigQuery-specific settings.
        trino: Trino-specific settings.
        server: Server mode settings.

    Example:
        >>> config = ExecutionConfig(
        ...     mode="local",
        ...     dialect="bigquery",
        ...     timeout=600,
        ...     bigquery=BigQueryConfig(project="my-project"),
        ... )
    """

    model_config = ConfigDict(frozen=True, extra="allow")

    mode: ExecutionModeStr = Field(
        default="local", description="Execution mode (local/server/mock)"
    )
    dialect: SQLDialectStr = Field(
        default="bigquery", description="Default SQL dialect"
    )
    timeout: int = Field(
        default=300,
        ge=1,
        le=3600,
        description="Execution timeout in seconds",
    )

    # Engine-specific configurations
    bigquery: BigQueryConfig | None = Field(
        default=None, description="BigQuery settings"
    )
    trino: TrinoConfig | None = Field(
        default=None, description="Trino settings"
    )

    # Server mode configuration
    server: ServerModeConfig | None = Field(
        default=None, description="Server mode settings"
    )

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None) -> ExecutionConfig:
        """Create ExecutionConfig from dictionary.

        Handles nested config parsing with graceful defaults.

        Args:
            data: Configuration dictionary (may be None).

        Returns:
            ExecutionConfig instance with defaults applied.
        """
        if not data:
            return cls()

        # Parse nested configs
        bigquery_data = data.get("bigquery")
        trino_data = data.get("trino")
        server_data = data.get("server")

        return cls(
            mode=data.get("mode", "local"),
            dialect=data.get("dialect", "bigquery"),
            timeout=data.get("timeout", 300),
            bigquery=BigQueryConfig(**bigquery_data) if bigquery_data else None,
            trino=TrinoConfig(**trino_data) if trino_data else None,
            server=ServerModeConfig(**server_data) if server_data else None,
        )


__all__ = [
    "BigQueryConfig",
    "ConfigSource",
    "ConfigValidationResult",
    "ConfigValueInfo",
    "EnvironmentProfile",
    "ExecutionConfig",
    "ExecutionModeStr",
    "SQLDialectStr",
    "ServerModeConfig",
    "TrinoAuthType",
    "TrinoConfig",
]
