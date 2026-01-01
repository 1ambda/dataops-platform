# FEATURE: Environment Management - Configuration Layering and Secrets

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | Implemented |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-01 |
| **Implementation** | [ENV_RELEASE.md](./ENV_RELEASE.md) |
| **References** | 12-Factor App, dbt profiles.yml, SQLMesh config, direnv |

---

## 1. Overview

### 1.1 Purpose

`dli config` commands provide hierarchical configuration management with environment-aware layering, secret handling, and runtime context resolution. This feature unifies project, local, and global configurations while supporting environment variable substitution and secure secrets management.

**Key Distinction:**

| Aspect | Current State | Target State |
|--------|---------------|--------------|
| Config file | Single `dli.yaml` | Layered: `dli.yaml` > `.dli.local.yaml` > `~/.dli/config.yaml` |
| Env vars | Basic `DLI_*` prefix | Templating `${VAR}` + priority override |
| Secrets | Not handled | `DLI_SECRET_*` masking + secure loading |
| Context | Manual construction | `ExecutionContext.from_environment()` |

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Layered Configuration** | Project < Local < Global < EnvVar < CLI; each layer overrides previous |
| **No Secrets in Files** | Secrets via `${ENV_VAR}` templating or `DLI_SECRET_*` environment variables |
| **Explicit Source Tracking** | Every config value shows its origin (file, env, cli) |
| **Environment Variable Templating** | `${VAR}` syntax for dynamic values in YAML files |
| **Fail-Fast Validation** | Missing required config or invalid values fail immediately |
| **Local Override Safety** | `.dli.local.yaml` in `.gitignore` by default |

### 1.3 Key Features

| Feature | Description |
|---------|-------------|
| **Config Layering** | Hierarchical config: global < project < local < env < cli |
| **Environment Templating** | `${DB_PASSWORD}` syntax in YAML files |
| **Secret Masking** | `DLI_SECRET_*` vars displayed as `***` in output |
| **Source Tracking** | `dli config show --show-source` reveals value origins |
| **Config Validation** | `dli config validate` checks required fields |
| **Environment Profiles** | Named environments (dev, staging, prod) with switching |
| **Connection Testing** | `dli config status` tests connectivity |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to dli |
|------|--------------|----------------|
| **12-Factor App** | Config from env, no secrets in code | `DLI_*` env vars, `${VAR}` templating |
| **dbt** | `profiles.yml` in `~/.dbt`, target environments | `~/.dli/config.yaml`, environment profiles |
| **SQLMesh** | `config.yaml` + `local_config.yaml` layering | `dli.yaml` + `.dli.local.yaml` pattern |
| **direnv** | `.envrc` per-directory env loading | `.dli.local.yaml` per-project override |
| **Terraform** | Variable precedence (file < env < cli) | Same priority order |

### 1.5 System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **ExecutionContext** | `models/common.py` BaseSettings | Add `from_environment()` factory |
| **CLI Commands** | `commands/config.py` | Extend with new subcommands |
| **Library API** | `api/config.py` ConfigAPI | Extend with layered loading |
| **ProjectConfig** | `core/config.py` | Extend with layer merging |
| **Exceptions** | DLI-0xx configuration errors | Use existing error codes |

---

## 2. Configuration Hierarchy

### 2.1 File Locations

| Priority | File | Purpose | Git Tracked |
|----------|------|---------|-------------|
| 4 (lowest) | `~/.dli/config.yaml` | Global user defaults | No |
| 3 | `<project>/dli.yaml` | Project configuration | Yes |
| 2 | `<project>/.dli.local.yaml` | Local overrides (dev-specific) | No (`.gitignore`) |
| 1 (highest) | Environment variables | Runtime overrides | N/A |

### 2.2 Priority Resolution

```
CLI Options > Environment Variables > .dli.local.yaml > dli.yaml > ~/.dli/config.yaml
```

**Example Resolution:**

```yaml
# ~/.dli/config.yaml (Priority 4)
server:
  url: "https://prod.basecamp.io"
  timeout: 60

# dli.yaml (Priority 3)
server:
  url: "https://staging.basecamp.io"

# .dli.local.yaml (Priority 2)
server:
  url: "http://localhost:8081"

# DLI_SERVER_URL=http://dev:8080 (Priority 1)
# Final value: http://dev:8080
```

### 2.3 File Structure

#### Global Config (`~/.dli/config.yaml`)

```yaml
# Global user defaults
version: "1"

# Default server for all projects
server:
  url: "https://basecamp.company.com"
  timeout: 30

# Default execution settings
defaults:
  dialect: "trino"
  timeout_seconds: 300

# Named environments (can be selected via --env flag)
environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
```

#### Project Config (`dli.yaml`)

```yaml
version: "1"

project:
  name: "analytics-models"
  description: "Data transformation models"

# Discovery configuration
discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns: ["dataset.*.yaml"]
  metric_patterns: ["metric.*.yaml"]

# Server connection (with env var templating)
server:
  url: "${DLI_SERVER_URL:-https://basecamp.company.com}"
  timeout: 30
  api_key: "${DLI_API_KEY}"  # Required from env

# Default settings
defaults:
  dialect: "trino"
  timeout_seconds: 3600
  retry_count: 2

# Named environments for this project
environments:
  dev:
    connection_string: "${DEV_CONNECTION_STRING}"
    catalog: "dev_catalog"
  prod:
    connection_string: "${PROD_CONNECTION_STRING}"
    catalog: "prod_catalog"
```

#### Local Override (`.dli.local.yaml`)

```yaml
# Local development overrides (NOT committed to git)
server:
  url: "http://localhost:8081"
  api_key: "${DLI_SECRET_API_KEY}"  # Local dev key

defaults:
  timeout_seconds: 60  # Shorter for dev

# Override active environment
active_environment: "dev"
```

---

## 3. Environment Variable Integration

### 3.1 Environment Variable Prefix

| Prefix | Purpose | Display in Output |
|--------|---------|-------------------|
| `DLI_*` | Regular configuration | Shown as-is |
| `DLI_SECRET_*` | Sensitive values | Masked as `***` |

### 3.2 Standard Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DLI_SERVER_URL` | Basecamp server URL | None |
| `DLI_API_TOKEN` | API authentication token | None |
| `DLI_PROJECT_PATH` | Project root directory | Current directory |
| `DLI_EXECUTION_MODE` | Execution mode (local/server/mock) | `local` |
| `DLI_DIALECT` | Default SQL dialect | `trino` |
| `DLI_TIMEOUT` | Query timeout (seconds) | `300` |
| `DLI_VERBOSE` | Verbose logging | `false` |
| `DLI_ENVIRONMENT` | Active environment name | None |
| `DLI_SECRET_API_KEY` | API key (masked) | None |
| `DLI_SECRET_DB_PASSWORD` | Database password (masked) | None |

### 3.3 Templating Syntax

Supported in `dli.yaml` and `.dli.local.yaml`:

| Syntax | Description | Example |
|--------|-------------|---------|
| `${VAR}` | Required variable (fail if missing) | `${DLI_API_KEY}` |
| `${VAR:-default}` | Variable with default | `${DLI_TIMEOUT:-300}` |
| `${VAR:?error}` | Custom error message | `${API_KEY:?API key required}` |

**Example:**

```yaml
server:
  url: "${DLI_SERVER_URL:-http://localhost:8081}"
  api_key: "${DLI_SECRET_API_KEY:?API key must be set}"
  timeout: ${DLI_TIMEOUT:-30}

database:
  password: "${DLI_SECRET_DB_PASSWORD}"
```

### 3.4 Environment Variable Resolution

```python
# Resolution priority for DLI_SERVER_URL
1. os.environ["DLI_SERVER_URL"]           # Direct env var
2. .dli.local.yaml server.url             # Local override
3. dli.yaml server.url (after templating) # Project config
4. ~/.dli/config.yaml server.url          # Global default
5. None (or error if required)            # Not found
```

---

## 4. CLI Design

### 4.1 Command Structure

```
dli config <subcommand> [options]
```

| Subcommand | Description |
|------------|-------------|
| `show` | Display current configuration with sources |
| `status` | Check server connection and validate config |
| `validate` | Validate configuration without connecting |
| `env` | List or switch named environments |
| `init` | Initialize configuration files |
| `set` | Set configuration value in local file |

### 4.2 Subcommand: `show` - Display Configuration

```bash
dli config show [options]
```

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--show-source` | `-s` | FLAG | `false` | Show value origin |
| `--show-secrets` | | FLAG | `false` | Reveal secret values |
| `--section` | | TEXT | (all) | Show specific section only |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json`, `yaml` |
| `--path` | `-p` | PATH | `.` | Project path |

**Examples:**

```bash
# Show all configuration
$ dli config show
DLI Configuration

Setting                 Value
----------------------  --------------------------------
Server URL              http://localhost:8081
Server Timeout          30s
API Key                 *** (configured)
Dialect                 trino
Execution Mode          local
Active Environment      dev

# Show with sources
$ dli config show --show-source
Setting                 Value                     Source
----------------------  ------------------------  ------------------
Server URL              http://localhost:8081     .dli.local.yaml
Server Timeout          30s                       dli.yaml
API Key                 *** (configured)          DLI_SECRET_API_KEY
Dialect                 trino                     ~/.dli/config.yaml
Execution Mode          local                     (default)
Active Environment      dev                       .dli.local.yaml

# Show specific section
$ dli config show --section server
Server Configuration

Setting       Value
------------  ----------------------
URL           http://localhost:8081
Timeout       30s
API Key       *** (configured)

# JSON output
$ dli config show --format json
{
  "server": {
    "url": "http://localhost:8081",
    "timeout": 30,
    "api_key_configured": true
  },
  "defaults": {
    "dialect": "trino",
    "timeout_seconds": 300
  }
}

# Show secrets (requires confirmation)
$ dli config show --show-secrets
Warning: This will display sensitive values. Continue? [y/N]: y
...
API Key                 sk-1234567890abcdef
```

### 4.3 Subcommand: `status` - Connection Check

```bash
dli config status [options]
```

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--env` | `-e` | TEXT | (active) | Environment to check |
| `--path` | `-p` | PATH | `.` | Project path |

**Examples:**

```bash
# Check current configuration
$ dli config status
Configuration Status

Files:
  [OK] ~/.dli/config.yaml (global)
  [OK] dli.yaml (project)
  [OK] .dli.local.yaml (local)

Required Settings:
  [OK] Server URL: http://localhost:8081
  [OK] API Key: configured

Connection:
  [OK] Server reachable (latency: 45ms)
  [OK] Authentication valid
  [OK] API version: v1.2.0

# Check with verbose output
$ dli config status --verbose
...
Testing connection to http://localhost:8081...
  DNS resolution: 2ms
  TCP connect: 15ms
  TLS handshake: 28ms
  Server response: 45ms

# Check specific environment
$ dli config status --env prod
Checking 'prod' environment...
  Server URL: https://prod.basecamp.io
  [OK] Server reachable (latency: 120ms)
  [OK] Authentication valid
```

### 4.4 Subcommand: `validate` - Configuration Validation

```bash
dli config validate [options]
```

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--strict` | | FLAG | `false` | Fail on warnings |
| `--path` | `-p` | PATH | `.` | Project path |

**Examples:**

```bash
# Validate configuration
$ dli config validate
Configuration Validation

[OK] dli.yaml syntax valid
[OK] .dli.local.yaml syntax valid
[OK] All required fields present
[WARN] server.api_key from environment variable (not in config file)

Validation passed with 1 warning.

# Strict mode (warnings are errors)
$ dli config validate --strict
[ERROR] server.api_key should be in config file with ${VAR} template
Validation failed.

# Missing required field
$ dli config validate
[ERROR] server.url is required but not configured
[ERROR] Missing environment variable: DLI_SECRET_API_KEY
Validation failed with 2 errors.
```

### 4.5 Subcommand: `env` - Environment Management

```bash
dli config env [options]
dli config env <NAME>
```

**Options:**

| Option | Short | Type | Description |
|--------|-------|------|-------------|
| `--list` | `-l` | FLAG | List available environments |
| `--format` | `-f` | ENUM | Output format: `table`, `json` |

**Examples:**

```bash
# List available environments
$ dli config env --list
Available Environments

Name      Server URL                    Active
--------  ----------------------------  ------
dev       http://localhost:8081         *
staging   https://staging.basecamp.io
prod      https://prod.basecamp.io

# Switch to environment (updates .dli.local.yaml)
$ dli config env staging
Switched to 'staging' environment.
  Server URL: https://staging.basecamp.io
  Dialect: trino

# Show current environment
$ dli config env
Current environment: dev
  Server URL: http://localhost:8081
  Dialect: duckdb
```

### 4.6 Subcommand: `init` - Initialize Configuration

```bash
dli config init [options]
```

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--global` | `-g` | FLAG | `false` | Create global config |
| `--force` | | FLAG | `false` | Overwrite existing |
| `--template` | `-t` | ENUM | `minimal` | Template: `minimal`, `full` |

**Examples:**

```bash
# Initialize project configuration
$ dli config init
Created dli.yaml with minimal configuration.
Created .dli.local.yaml template.
Added .dli.local.yaml to .gitignore.

# Initialize global configuration
$ dli config init --global
Created ~/.dli/config.yaml with default settings.

# Full template with all options documented
$ dli config init --template full
Created dli.yaml with full configuration template.
```

### 4.7 Subcommand: `set` - Set Configuration Value

```bash
dli config set <KEY> <VALUE> [options]
```

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--local` | `-l` | FLAG | `true` | Write to .dli.local.yaml |
| `--project` | | FLAG | `false` | Write to dli.yaml |
| `--global` | `-g` | FLAG | `false` | Write to ~/.dli/config.yaml |

**Examples:**

```bash
# Set server URL in local config
$ dli config set server.url "http://localhost:8081"
Set server.url = "http://localhost:8081" in .dli.local.yaml

# Set in project config
$ dli config set defaults.dialect "bigquery" --project
Set defaults.dialect = "bigquery" in dli.yaml

# Set global default
$ dli config set defaults.timeout_seconds 600 --global
Set defaults.timeout_seconds = 600 in ~/.dli/config.yaml
```

---

## 5. API Design

### 5.1 ConfigAPI Extensions

```python
# File: dli/api/config.py

from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field

from dli.models.common import ExecutionContext


class ConfigSource(str, Enum):
    """Configuration value source."""

    DEFAULT = "default"
    GLOBAL = "global"      # ~/.dli/config.yaml
    PROJECT = "project"    # dli.yaml
    LOCAL = "local"        # .dli.local.yaml
    ENV_VAR = "env"        # Environment variable
    CLI = "cli"            # CLI option


class ConfigValue(BaseModel):
    """Configuration value with source tracking."""

    key: str = Field(..., description="Configuration key (dot notation)")
    value: Any = Field(..., description="Resolved value")
    source: ConfigSource = Field(..., description="Value source")
    is_secret: bool = Field(default=False, description="Is sensitive value")
    raw_value: str | None = Field(default=None, description="Template before resolution")


class ConfigAPI:
    """Library API for configuration management.

    Provides hierarchical configuration loading with source tracking.

    Example:
        >>> from dli import ConfigAPI
        >>> api = ConfigAPI()
        >>> config = api.get_all()
        >>> print(config["server"]["url"])

        >>> # With source tracking
        >>> values = api.get_with_sources()
        >>> for v in values:
        ...     print(f"{v.key} = {v.value} (from {v.source})")
    """

    def __init__(
        self,
        project_path: Path | None = None,
        *,
        load_global: bool = True,
        load_local: bool = True,
    ) -> None:
        """Initialize ConfigAPI.

        Args:
            project_path: Project directory. Defaults to cwd.
            load_global: Load ~/.dli/config.yaml.
            load_local: Load .dli.local.yaml.
        """
        self.project_path = project_path or Path.cwd()
        self._load_global = load_global
        self._load_local = load_local
        self._config: dict[str, Any] | None = None
        self._sources: dict[str, ConfigSource] | None = None

    def get_all(self) -> dict[str, Any]:
        """Get merged configuration.

        Returns:
            Merged configuration dictionary with all layers applied.

        Example:
            >>> config = api.get_all()
            >>> print(config["server"]["url"])
        """
        if self._config is None:
            self._load_config()
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

    def get_with_source(self, key: str) -> ConfigValue | None:
        """Get configuration value with source information.

        Args:
            key: Dot-notation key.

        Returns:
            ConfigValue with source tracking, or None if not found.

        Example:
            >>> cv = api.get_with_source("server.url")
            >>> print(f"{cv.value} from {cv.source}")
        """
        value = self.get(key)
        if value is None:
            return None
        source = self._get_source(key)
        is_secret = key.startswith("secret.") or "password" in key.lower()
        return ConfigValue(
            key=key,
            value=value,
            source=source,
            is_secret=is_secret,
        )

    def get_all_with_sources(self) -> list[ConfigValue]:
        """Get all configuration values with sources.

        Returns:
            List of ConfigValue objects with source tracking.
        """
        # Implementation flattens config and tracks sources
        ...

    def list_environments(self) -> list[str]:
        """List available named environments.

        Returns:
            List of environment names.

        Example:
            >>> envs = api.list_environments()
            >>> print(envs)  # ["dev", "staging", "prod"]
        """
        config = self.get_all()
        return list(config.get("environments", {}).keys())

    def get_environment(self, name: str) -> dict[str, Any]:
        """Get configuration for named environment.

        Args:
            name: Environment name.

        Returns:
            Environment-specific configuration.

        Raises:
            ConfigurationError: If environment not found.
        """
        config = self.get_all()
        envs = config.get("environments", {})
        if name not in envs:
            from dli.exceptions import ConfigurationError, ErrorCode
            raise ConfigurationError(
                message=f"Environment '{name}' not found",
                code=ErrorCode.CONFIG_ENV_NOT_FOUND,
            )
        return envs[name]

    def get_active_environment(self) -> str | None:
        """Get currently active environment name.

        Returns:
            Active environment name or None.
        """
        # Check env var first
        import os
        env = os.environ.get("DLI_ENVIRONMENT")
        if env:
            return env
        # Check local config
        return self.get("active_environment")

    def validate(self, *, strict: bool = False) -> ValidationResult:
        """Validate configuration.

        Args:
            strict: Treat warnings as errors.

        Returns:
            ValidationResult with errors and warnings.
        """
        ...

    def _load_config(self) -> None:
        """Load and merge all configuration layers."""
        from dli.core.config_loader import ConfigLoader

        loader = ConfigLoader(
            project_path=self.project_path,
            load_global=self._load_global,
            load_local=self._load_local,
        )
        self._config, self._sources = loader.load()

    def _get_source(self, key: str) -> ConfigSource:
        """Get source for a configuration key."""
        if self._sources is None:
            self._load_config()
        return self._sources.get(key, ConfigSource.DEFAULT)
```

### 5.2 ExecutionContext Factory

```python
# File: dli/models/common.py (additions)

class ExecutionContext(BaseSettings):
    """Library API execution context.

    (existing docstring)
    """

    # ... existing fields ...

    @classmethod
    def from_environment(
        cls,
        project_path: Path | None = None,
        *,
        environment: str | None = None,
        overrides: dict[str, Any] | None = None,
    ) -> "ExecutionContext":
        """Create context from environment configuration.

        Loads configuration from the layered config system and creates
        an ExecutionContext with proper defaults.

        Args:
            project_path: Project directory (defaults to cwd).
            environment: Named environment to use (e.g., "dev", "prod").
            overrides: Additional overrides (highest priority).

        Returns:
            Configured ExecutionContext.

        Example:
            >>> # From current environment
            >>> ctx = ExecutionContext.from_environment()

            >>> # From specific environment
            >>> ctx = ExecutionContext.from_environment(environment="prod")

            >>> # With overrides
            >>> ctx = ExecutionContext.from_environment(
            ...     overrides={"timeout": 600}
            ... )
        """
        from dli.api.config import ConfigAPI

        api = ConfigAPI(project_path=project_path)
        config = api.get_all()

        # Get environment-specific config if requested
        env_config = {}
        if environment:
            env_config = api.get_environment(environment)
        elif api.get_active_environment():
            env_config = api.get_environment(api.get_active_environment())

        # Merge configurations
        server_url = (
            overrides.get("server_url") if overrides else None
        ) or env_config.get("server_url") or config.get("server", {}).get("url")

        api_token = (
            overrides.get("api_token") if overrides else None
        ) or env_config.get("api_key") or config.get("server", {}).get("api_key")

        dialect = (
            overrides.get("dialect") if overrides else None
        ) or env_config.get("dialect") or config.get("defaults", {}).get("dialect", "trino")

        timeout = (
            overrides.get("timeout") if overrides else None
        ) or env_config.get("timeout_seconds") or config.get("defaults", {}).get("timeout_seconds", 300)

        execution_mode_str = (
            overrides.get("execution_mode") if overrides else None
        ) or env_config.get("execution_mode") or os.environ.get("DLI_EXECUTION_MODE", "local")

        execution_mode = ExecutionMode(execution_mode_str)

        return cls(
            project_path=project_path or Path.cwd(),
            server_url=server_url,
            api_token=api_token,
            execution_mode=execution_mode,
            timeout=timeout,
            dialect=dialect,
            parameters=overrides.get("parameters", {}) if overrides else {},
        )
```

---

## 6. Core Implementation

### 6.1 ConfigLoader

```python
# File: dli/core/config_loader.py

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any

import yaml

from dli.api.config import ConfigSource
from dli.exceptions import ConfigurationError, ErrorCode


class ConfigLoader:
    """Hierarchical configuration loader with template resolution.

    Loads and merges configuration from multiple sources:
    1. ~/.dli/config.yaml (global)
    2. dli.yaml (project)
    3. .dli.local.yaml (local)
    4. Environment variables (DLI_*)

    Supports ${VAR} and ${VAR:-default} template syntax.
    """

    # Regex for ${VAR}, ${VAR:-default}, ${VAR:?error}
    TEMPLATE_PATTERN = re.compile(
        r"\$\{([A-Z_][A-Z0-9_]*)(?::-([^}]*)|:\?([^}]*))?\}"
    )

    def __init__(
        self,
        project_path: Path,
        *,
        load_global: bool = True,
        load_local: bool = True,
    ) -> None:
        self.project_path = project_path
        self._load_global = load_global
        self._load_local = load_local

    def load(self) -> tuple[dict[str, Any], dict[str, ConfigSource]]:
        """Load and merge all configuration layers.

        Returns:
            Tuple of (merged_config, source_map).
        """
        config: dict[str, Any] = {}
        sources: dict[str, ConfigSource] = {}

        # Layer 1: Global config
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

        # Layer 4: Environment variables (applied during template resolution)
        config = self._resolve_templates(config, sources)

        return config, sources

    def _load_global_config(self) -> tuple[dict, dict]:
        """Load ~/.dli/config.yaml."""
        global_path = Path.home() / ".dli" / "config.yaml"
        if not global_path.exists():
            return {}, {}

        with open(global_path, encoding="utf-8") as f:
            config = yaml.safe_load(f) or {}

        sources = self._build_source_map(config, ConfigSource.GLOBAL)
        return config, sources

    def _load_project_config(self) -> tuple[dict, dict]:
        """Load dli.yaml from project."""
        config_path = self.project_path / "dli.yaml"
        if not config_path.exists():
            return {}, {}

        with open(config_path, encoding="utf-8") as f:
            config = yaml.safe_load(f) or {}

        sources = self._build_source_map(config, ConfigSource.PROJECT)
        return config, sources

    def _load_local_config(self) -> tuple[dict, dict]:
        """Load .dli.local.yaml from project."""
        local_path = self.project_path / ".dli.local.yaml"
        if not local_path.exists():
            return {}, {}

        with open(local_path, encoding="utf-8") as f:
            config = yaml.safe_load(f) or {}

        sources = self._build_source_map(config, ConfigSource.LOCAL)
        return config, sources

    def _resolve_templates(
        self,
        config: dict[str, Any],
        sources: dict[str, ConfigSource],
    ) -> dict[str, Any]:
        """Resolve ${VAR} templates in configuration."""
        def resolve_value(value: Any, path: str) -> Any:
            if isinstance(value, str):
                return self._resolve_string_template(value, path, sources)
            elif isinstance(value, dict):
                return {
                    k: resolve_value(v, f"{path}.{k}")
                    for k, v in value.items()
                }
            elif isinstance(value, list):
                return [resolve_value(v, f"{path}[{i}]") for i, v in enumerate(value)]
            return value

        return resolve_value(config, "")

    def _resolve_string_template(
        self,
        value: str,
        path: str,
        sources: dict[str, ConfigSource],
    ) -> str:
        """Resolve ${VAR:-default} template in string."""
        def replace_match(match: re.Match) -> str:
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
                    code=ErrorCode.CONFIG_MISSING_REQUIRED,
                )

            # No value, no default, no error - return empty
            raise ConfigurationError(
                message=f"Environment variable {var_name} is not set",
                code=ErrorCode.CONFIG_MISSING_REQUIRED,
            )

        return self.TEMPLATE_PATTERN.sub(replace_match, value)

    def _deep_merge(
        self,
        base: dict[str, Any],
        override: dict[str, Any],
    ) -> dict[str, Any]:
        """Deep merge two dictionaries."""
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
        """Build flat source map from nested config."""
        sources = {}
        for key, value in config.items():
            path = f"{prefix}.{key}" if prefix else key
            sources[path] = source
            if isinstance(value, dict):
                sources.update(self._build_source_map(value, source, path))
        return sources
```

---

## 7. Error Codes

Environment errors use the existing configuration error range (DLI-0xx).

| Code | Name | Description |
|------|------|-------------|
| DLI-001 | `CONFIG_NOT_FOUND` | Configuration file not found |
| DLI-002 | `CONFIG_INVALID` | Invalid configuration syntax |
| DLI-003 | `CONFIG_MISSING_REQUIRED` | Required configuration missing |
| DLI-004 | `CONFIG_ENV_NOT_FOUND` | Named environment not found |
| DLI-005 | `CONFIG_TEMPLATE_ERROR` | Template resolution failed |
| DLI-006 | `CONFIG_VALIDATION_FAILED` | Configuration validation failed |

---

## 8. Implementation Priority

### Phase 1: Core Layering (MVP)

| Priority | Task |
|----------|------|
| 1 | `ConfigLoader` with layer merging |
| 2 | Template resolution (`${VAR}`, `${VAR:-default}`) |
| 3 | `ConfigAPI.get_all()` and `ConfigAPI.get()` |
| 4 | `dli config show` with basic output |
| 5 | `.dli.local.yaml` support |
| 6 | Unit tests for config loading |

### Phase 2: Source Tracking & Validation

| Priority | Task |
|----------|------|
| 1 | Source tracking per configuration value |
| 2 | `dli config show --show-source` |
| 3 | `DLI_SECRET_*` masking |
| 4 | `dli config validate` command |
| 5 | `ExecutionContext.from_environment()` factory |

### Phase 3: Environment Management

| Priority | Task |
|----------|------|
| 1 | Named environments support |
| 2 | `dli config env` list and switch |
| 3 | `dli config init` scaffolding |
| 4 | `dli config set` for runtime updates |
| 5 | Integration tests |

---

## 9. Success Criteria

### 9.1 Feature Completion

| Feature | Completion Condition |
|---------|----------------------|
| Config layering | Three layers merge correctly |
| Template resolution | `${VAR}` and `${VAR:-default}` work |
| Source tracking | Every value shows correct origin |
| Secret masking | `DLI_SECRET_*` values displayed as `***` |
| Validation | Missing required fields detected |

### 9.2 Test Quality

| Metric | Target |
|--------|--------|
| Unit test coverage | >= 80% |
| Layer merge tests | All priority combinations |
| Template tests | All syntax variations |
| CLI tests | All subcommands |

### 9.3 Code Quality

| Principle | Verification |
|-----------|--------------|
| Single Responsibility | ConfigLoader only loads, ConfigAPI only provides access |
| Explicit is Better | Source tracking for every value |
| Fail-Fast | Missing required config fails immediately |

---

## 10. Directory Structure

```
project-interface-cli/src/dli/
├── api/
│   └── config.py         # EXTEND: ConfigAPI with layered loading
├── commands/
│   └── config.py         # EXTEND: New subcommands
├── core/
│   ├── config.py         # EXTEND: ProjectConfig updates
│   └── config_loader.py  # NEW: ConfigLoader class
├── models/
│   └── common.py         # EXTEND: ExecutionContext.from_environment()
└── exceptions.py         # EXTEND: CONFIG_ENV_NOT_FOUND, etc.
```

**Legend:** `NEW` = new file, `EXTEND` = additions to existing file

---

## 11. Reference Patterns

| Implementation | Reference File | Pattern |
|----------------|----------------|---------|
| ConfigAPI | `api/catalog.py` | Facade pattern with lazy loading |
| ConfigLoader | `core/config.py` | YAML loading with safe_load |
| CLI commands | `commands/config.py` | Typer subcommand structure |
| Template resolution | 12-Factor App | `${VAR:-default}` syntax |
| Layer merging | dbt profiles.yml | Global < Project < Local |

---

## Appendix A: Configuration Examples

### Minimal Project Config (`dli.yaml`)

```yaml
version: "1"

project:
  name: "my-project"

server:
  url: "${DLI_SERVER_URL}"
```

### Full Project Config (`dli.yaml`)

```yaml
version: "1"

project:
  name: "analytics-models"
  description: "Central data transformation layer"

discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns: ["dataset.*.yaml"]
  metric_patterns: ["metric.*.yaml"]

server:
  url: "${DLI_SERVER_URL:-https://basecamp.company.com}"
  timeout: 30
  api_key: "${DLI_SECRET_API_KEY}"

defaults:
  dialect: "trino"
  timeout_seconds: 3600
  retry_count: 2

environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
    catalog: "dev_catalog"
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
    catalog: "staging_catalog"
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
    catalog: "prod_catalog"
```

### Local Override (`.dli.local.yaml`)

```yaml
# Local development overrides - DO NOT COMMIT
server:
  url: "http://localhost:8081"

active_environment: "dev"

defaults:
  timeout_seconds: 60
```

### Global User Config (`~/.dli/config.yaml`)

```yaml
version: "1"

# Default server for all projects
server:
  timeout: 30

# User preferences
defaults:
  dialect: "trino"
  timeout_seconds: 300
```

---

## Appendix B: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Layer count | 3 files + env | Balance between flexibility and complexity |
| 2 | Template syntax | `${VAR:-default}` | Standard shell/Docker syntax, familiar to users |
| 3 | Secret prefix | `DLI_SECRET_*` | Explicit marking for auto-masking |
| 4 | Local file name | `.dli.local.yaml` | Dot prefix = hidden, clear purpose |
| 5 | Global location | `~/.dli/config.yaml` | Standard XDG-style user config |
| 6 | Environment switching | Via CLI command | Explicit action, not magic |
| 7 | Source tracking | Always available | Debugging configuration issues |
| 8 | Validation | Fail-fast | Missing required config errors immediately |
| 9 | No auto-creation | `init` command required | Explicit user action |
| 10 | YAML format | Single format | Consistency, no JSON/TOML complexity |

---

## Appendix C: Migration Guide

### From Current dli.yaml to Layered Config

**Step 1: No changes required**
- Existing `dli.yaml` works as-is
- New layers are additive

**Step 2: Add local overrides**
```bash
# Create local config for development
$ dli config init
Created .dli.local.yaml template.

# Edit with local settings
$ dli config set server.url "http://localhost:8081" --local
```

**Step 3: Add global defaults**
```bash
$ dli config init --global
Created ~/.dli/config.yaml
```

**Step 4: Use environment templating**
```yaml
# Update dli.yaml
server:
  url: "${DLI_SERVER_URL:-https://basecamp.company.com}"
  api_key: "${DLI_SECRET_API_KEY}"
```

---

## Implementation Review (feature-interface-cli)

**Date**: 2026-01-01 | **Reviewer**: feature-interface-cli agent

### Architecture Compatibility

| Aspect | Status | Notes |
|--------|--------|-------|
| CLI Structure | OK | Extends existing `config_app` (show, status commands exist) |
| API Pattern | OK | Follows existing `ConfigAPI` facade pattern |
| Model Reuse | WARN | `ConfigValue` already exists in `models/common.py` |
| Error Codes | WARN | Proposed DLI-003~006 may conflict with existing |
| ExecutionContext | OK | `from_environment()` factory is additive |

### Specific Issues

1. **ConfigValue Conflict (HIGH)**
   - Existing: `ConfigValue(key, value, source: str)`
   - Proposed: `ConfigValue(key, value, source: ConfigSource, is_secret, raw_value)`
   - **Resolution**: Extend existing model with optional fields using `Field(default=...)`

2. **ErrorCode Conflicts (HIGH)**
   - `DLI-003` is `PROJECT_NOT_FOUND`, not `CONFIG_MISSING_REQUIRED`
   - **Resolution**: Use DLI-004~007 for new config errors, update table in Section 7

3. **Existing CLI Commands**
   - `dli config show` and `dli config status` already exist
   - **Resolution**: Extend, not replace. Add `--show-source` to existing `show`

4. **ConfigSource Enum Location (MEDIUM)**
   - Proposed in `api/config.py`, but enums belong in `models/` or `core/`
   - **Resolution**: Define in `models/common.py` alongside `ConfigValue`

### Recommendations

1. Create `ConfigSource` enum in `models/common.py`
2. Extend existing `ConfigValue` with `is_secret`, `raw_value`, and typed `source`
3. New file: `core/config_loader.py` for layer merging logic
4. Add `validate`, `env`, `init`, `set` subcommands to existing `config_app`
5. Update ErrorCode enum with gap-filling (DLI-004 onwards)

---

## Python Review (expert-python)

**Date**: 2026-01-01 | **Reviewer**: expert-python agent

### Pydantic Best Practices

| Pattern | Status | Recommendation |
|---------|--------|----------------|
| BaseSettings usage | OK | `ExecutionContext` already uses it correctly |
| ConfigDict | OK | `frozen=True` for `ConfigValue` is good |
| Enum inheritance | OK | `ConfigSource(str, Enum)` is correct pattern |
| Field defaults | OK | Using `Field(default=...)` appropriately |

### Type Safety Issues

1. **Generic dict returns (MEDIUM)**
   ```python
   # Current in spec
   def get_all(self) -> dict[str, Any]:

   # Better with TypedDict
   class ServerConfig(TypedDict):
       url: str
       timeout: int
       api_key: NotRequired[str]
   ```

2. **Template regex robustness (LOW)**
   - Current pattern handles `${VAR}`, `${VAR:-default}`, `${VAR:?error}`
   - Edge case: Nested templates `${FOO:-${BAR}}` not supported
   - **Resolution**: Document limitation, defer nested support

### Security Considerations

1. **YAML Loading**: `yaml.safe_load()` correctly used
2. **Secret Masking**: `DLI_SECRET_*` pattern is clear
3. **Env Var Access**: Direct `os.environ.get()` is safe

### Performance

1. **ConfigLoader caching (MEDIUM)**
   ```python
   # Add @lru_cache to factory or use singleton
   @lru_cache(maxsize=1)
   def get_config_loader(project_path: Path) -> ConfigLoader:
       return ConfigLoader(project_path)
   ```

2. **Lazy loading**: `ConfigAPI._config: dict | None` pattern is correct

### Testing Strategy

| Test Type | Recommendation |
|-----------|----------------|
| Unit | Isolated `ConfigLoader` tests with temp YAML files |
| Integration | Full layer merge with monkeypatch env vars |
| CLI | `CliRunner` with `env={"DLI_*": ...}` injection |
| Edge cases | Missing files, invalid YAML, circular refs |

### Code Quality

1. Use `from __future__ import annotations` consistently
2. Add `__all__` exports to new modules
3. Follow existing docstring format (Google style)
4. Regex pattern should have `re.VERBOSE` for readability
