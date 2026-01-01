# ENV Feature Release Documentation

| Attribute | Value |
|-----------|-------|
| **Version** | v0.7.0 |
| **Status** | ✅ Complete |
| **Release Date** | 2026-01-01 |
| **Test Count** | ~300 tests |
| **Code Coverage** | ~90%+ |

---

## Overview

Environment Management feature provides hierarchical configuration loading with template resolution, secret handling, and environment profiles. Supports `~/.dli/config.yaml` (global), `dli.yaml` (project), `.dli.local.yaml` (local), and environment variable overrides.

**Key Benefits:**
- 🔐 Secure secret management (`DLI_SECRET_*` masking)
- 🌍 Named environments (dev, staging, prod)
- 📝 Template syntax (`${VAR:-default}`)
- 🔍 Source tracking for debugging
- ✅ Fail-fast validation

---

## Components Implemented

### Core Components

| Component | File | Lines | Description |
|-----------|------|-------|-------------|
| **ConfigLoader** | `core/config_loader.py` | ~350 | Hierarchical config loading with template resolution |
| **Config Models** | `models/config.py` | ~150 | ConfigSource, ConfigValueInfo, ConfigValidationResult, EnvironmentProfile |

### API Extensions

| Method | Signature | Description |
|--------|-----------|-------------|
| `ConfigAPI.get_all()` | `() -> dict[str, Any]` | Get merged configuration dict |
| `ConfigAPI.get(key, default)` | `(str, Any) -> Any` | Get config value by dot-notation key |
| `ConfigAPI.get_with_source(key)` | `(str) -> ConfigValueInfo \| None` | Get value with source tracking |
| `ConfigAPI.get_all_with_sources()` | `() -> list[ConfigValueInfo]` | Get all values with sources |
| `ConfigAPI.validate(strict)` | `(bool) -> ConfigValidationResult` | Validate configuration |
| `ConfigAPI.list_environments()` | `() -> list[str]` | List available environments |
| `ConfigAPI.get_environment(name)` | `(str) -> dict[str, Any]` | Get environment-specific config |
| `ConfigAPI.get_active_environment()` | `() -> str \| None` | Get active environment name |
| `ConfigAPI.get_server_status()` | `() -> dict[str, Any]` | Get server connection status |
| **ExecutionContext.from_environment()** | `(Path?, str?, dict?) -> ExecutionContext` | Factory method for context creation |

### CLI Commands

| Command | Options | Description |
|---------|---------|-------------|
| `dli config show` | `--show-source`, `--section`, `--format` | Show config with source tracking |
| `dli config status` | `--env`, `--verbose` | Check server connection and config validity |
| `dli config validate` | `--strict` | Strict validation |
| `dli config env` | `--list`, `--format` | List/switch environments |
| `dli config init` | `--global`, `--template`, `--force` | Initialize config files |
| `dli config set <key> <value>` | `--local`, `--project`, `--global` | Set config value |

### Error Codes

| Code | Name | Description | Usage |
|------|------|-------------|-------|
| DLI-004 | CONFIG_ENV_NOT_FOUND | Named environment not found | `ConfigAPI.get_environment("invalid")` |
| DLI-005 | CONFIG_TEMPLATE_ERROR | Template resolution failed | `${MISSING_VAR}` without default |
| DLI-006 | CONFIG_VALIDATION_ERROR | Validation failed | Missing required fields |
| DLI-007 | CONFIG_WRITE_ERROR | Failed to write config | Permission denied |

---

## Implementation Details

### 1. ConfigLoader (`core/config_loader.py`)

**Purpose:** Load and merge configuration from 4 layers with template resolution.

**Algorithm:**
```python
config = {}
sources = {}

# Layer 1: Global (~/.dli/config.yaml)
if load_global:
    config = deep_merge(config, load_global_config())

# Layer 2: Project (dli.yaml)
config = deep_merge(config, load_project_config())

# Layer 3: Local (.dli.local.yaml)
if load_local:
    config = deep_merge(config, load_local_config())

# Layer 4: Template resolution (${VAR} -> os.environ)
config = resolve_templates(config, sources)

return config, sources
```

**Template Regex:**
```python
TEMPLATE_PATTERN = re.compile(
    r"\$\{([A-Z_][A-Z0-9_]*)(?::-([^}]*)|:\?([^}]*))?\}"
)
# Matches:
# - ${VAR}           → Required variable
# - ${VAR:-default}  → Variable with default
# - ${VAR:?error}    → Custom error message
```

**Key Methods:**

| Method | Purpose | Lines |
|--------|---------|-------|
| `load()` | Main entry point, returns `(config, sources)` | ~30 |
| `_deep_merge(base, override)` | Recursive dict merging | ~15 |
| `_resolve_templates(config, sources)` | Apply `${VAR}` resolution | ~25 |
| `_build_source_map(config, source)` | Flatten config to dot-notation keys | ~15 |

---

### 2. Config Models (`models/config.py`)

#### ConfigSource Enum
```python
class ConfigSource(str, Enum):
    DEFAULT = "default"
    GLOBAL = "global"      # ~/.dli/config.yaml
    PROJECT = "project"    # dli.yaml
    LOCAL = "local"        # .dli.local.yaml
    ENV_VAR = "env"        # Environment variable
    CLI = "cli"            # CLI option
```

#### ConfigValueInfo Model
```python
class ConfigValueInfo(BaseModel):
    key: str                    # Dot-notation key (e.g., "server.url")
    value: Any                  # Resolved value
    source: ConfigSource        # Where it came from
    is_secret: bool = False     # Auto-detected or DLI_SECRET_*
    raw_value: str | None = None  # Template before resolution

    def display_value(self, mask_secrets: bool = True) -> str:
        """Return masked value if secret."""
        if self.is_secret and mask_secrets:
            return "***"
        return str(self.value)

    def source_label(self) -> str:
        """Human-readable source label."""
        return {
            ConfigSource.DEFAULT: "(default)",
            ConfigSource.GLOBAL: "~/.dli/config.yaml",
            ConfigSource.PROJECT: "dli.yaml",
            ConfigSource.LOCAL: ".dli.local.yaml",
            ConfigSource.ENV_VAR: f"${self.key.upper().replace('.', '_')}",
            ConfigSource.CLI: "(CLI option)",
        }[self.source]
```

#### ConfigValidationResult Model
```python
class ConfigValidationResult(BaseModel):
    errors: list[str] = []
    warnings: list[str] = []

    def has_errors(self) -> bool:
        return len(self.errors) > 0

    def has_warnings(self) -> bool:
        return len(self.warnings) > 0
```

#### EnvironmentProfile Model
```python
class EnvironmentProfile(BaseModel):
    name: str
    server_url: str | None = None
    api_token: str | None = None
    dialect: str | None = None
    timeout: int | None = None
    catalog: str | None = None
    # ... additional fields
```

---

### 3. ConfigAPI (`api/config.py`)

**Complete method list:**

```python
class ConfigAPI:
    def __init__(
        self,
        project_path: Path | None = None,
        *,
        load_global: bool = True,
        load_local: bool = True,
        context: ExecutionContext | None = None,
    ) -> None:
        """Initialize ConfigAPI."""

    def get_all(self) -> dict[str, Any]:
        """Get merged configuration dict."""

    def get(self, key: str, default: Any = None) -> Any:
        """Get value by dot-notation key (e.g., 'server.url')."""

    def get_with_source(self, key: str) -> ConfigValueInfo | None:
        """Get value with source tracking."""

    def get_all_with_sources(self) -> list[ConfigValueInfo]:
        """Get all config values with sources."""

    def validate(self, *, strict: bool = False) -> ConfigValidationResult:
        """Validate configuration."""

    def list_environments(self) -> list[str]:
        """List available environment names."""

    def get_environment(self, name: str) -> dict[str, Any]:
        """Get environment-specific config (raises DLI-004 if not found)."""

    def get_active_environment(self) -> str | None:
        """Get active environment from DLI_ENVIRONMENT or config."""

    def get_server_status(self) -> dict[str, Any]:
        """Get server connection status."""

    def get_current_environment(self) -> EnvironmentProfile | None:
        """Get current environment profile."""
```

**Usage Examples:**

```python
from dli import ConfigAPI

# Initialize
api = ConfigAPI()  # Uses cwd as project_path

# Get all config
config = api.get_all()
print(config["server"]["url"])  # http://localhost:8081

# Get single value with default
timeout = api.get("defaults.timeout_seconds", 300)

# Get with source tracking
value_info = api.get_with_source("server.url")
print(f"{value_info.value} from {value_info.source_label()}")
# Output: http://localhost:8081 from .dli.local.yaml

# List environments
envs = api.list_environments()  # ["dev", "staging", "prod"]

# Get environment config
prod_config = api.get_environment("prod")
print(prod_config["server_url"])  # https://prod.basecamp.io

# Validate config
result = api.validate(strict=True)
if result.has_errors():
    for error in result.errors:
        print(f"ERROR: {error}")
```

---

### 4. ExecutionContext.from_environment()

**Factory method for automatic context creation:**

```python
from dli import ExecutionContext

# Auto-load from config files + env vars
ctx = ExecutionContext.from_environment()

# Load specific environment
ctx = ExecutionContext.from_environment(environment="prod")

# With overrides (highest priority)
ctx = ExecutionContext.from_environment(
    overrides={"timeout": 600, "dialect": "bigquery"}
)

# Custom project path
ctx = ExecutionContext.from_environment(
    project_path=Path("/opt/airflow/dags/models"),
    environment="staging"
)
```

**Resolution priority inside `from_environment()`:**
```
overrides > env_config > config["defaults"] > hardcoded defaults
```

---

### 5. CLI Commands

#### `dli config show`

**Basic usage:**
```bash
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
```

**With source tracking:**
```bash
$ dli config show --show-source
Setting                 Value                     Source
----------------------  ------------------------  ------------------
Server URL              http://localhost:8081     .dli.local.yaml
Server Timeout          30s                       dli.yaml
API Key                 *** (configured)          DLI_SECRET_API_KEY
Dialect                 trino                     ~/.dli/config.yaml
Execution Mode          local                     (default)
Active Environment      dev                       .dli.local.yaml
```

**Specific section:**
```bash
$ dli config show --section server
Server Configuration

Setting       Value
------------  ----------------------
URL           http://localhost:8081
Timeout       30s
API Key       *** (configured)
```

**JSON output:**
```bash
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
  },
  "active_environment": "dev"
}
```

#### `dli config validate`

```bash
$ dli config validate
Configuration Validation

[OK] dli.yaml syntax valid
[OK] .dli.local.yaml syntax valid
[OK] All required fields present
[WARN] server.api_key from environment variable

Validation passed with 1 warning.
```

**Strict mode:**
```bash
$ dli config validate --strict
[ERROR] server.api_key should be in config file with ${VAR} template
Validation failed.
```

#### `dli config env`

**List environments:**
```bash
$ dli config env --list
Available Environments

Name      Server URL                    Active
--------  ----------------------------  ------
dev       http://localhost:8081         *
staging   https://staging.basecamp.io
prod      https://prod.basecamp.io
```

**Switch environment:**
```bash
$ dli config env staging
Switched to 'staging' environment.
Updated .dli.local.yaml

Environment Details:
  Server URL: https://staging.basecamp.io
  Dialect: trino
  Catalog: staging_catalog
```

#### `dli config init`

**Project config:**
```bash
$ dli config init
Created dli.yaml with minimal configuration.
Created .dli.local.yaml template.
Added .dli.local.yaml to .gitignore.

Next steps:
  1. Edit dli.yaml to configure your project
  2. Set secrets in .dli.local.yaml (not committed)
  3. Run: dli config validate
```

**Global config:**
```bash
$ dli config init --global
Created ~/.dli/config.yaml with default settings.

Configuration:
  Server Timeout: 30s
  Default Dialect: trino
```

**Full template:**
```bash
$ dli config init --template full
Created dli.yaml with full configuration template.
Includes:
  - All available options
  - Inline documentation
  - Example environments
```

#### `dli config set`

```bash
# Set in local config (default)
$ dli config set server.url "http://localhost:8081"
Set server.url = "http://localhost:8081" in .dli.local.yaml

# Set in project config
$ dli config set defaults.dialect "bigquery" --project
Set defaults.dialect = "bigquery" in dli.yaml

# Set in global config
$ dli config set defaults.timeout_seconds 600 --global
Set defaults.timeout_seconds = 600 in ~/.dli/config.yaml
```

---

## Configuration File Examples

### Minimal `dli.yaml`
```yaml
version: "1"

project:
  name: "my-project"

server:
  url: "${DLI_SERVER_URL}"
  api_key: "${DLI_SECRET_API_KEY}"
```

### Full `dli.yaml`
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

### `.dli.local.yaml` (not committed)
```yaml
# Local development overrides - DO NOT COMMIT
server:
  url: "http://localhost:8081"

active_environment: "dev"

defaults:
  timeout_seconds: 60
```

### `~/.dli/config.yaml`
```yaml
version: "1"

# Global user defaults
server:
  timeout: 30

defaults:
  dialect: "trino"
  timeout_seconds: 300
```

---

## Test Structure

### Test Files

| File | Tests | Coverage |
|------|-------|----------|
| `tests/core/test_config_loader.py` | ~100 | ConfigLoader class |
| `tests/models/test_config_models.py` | ~50 | Config models |
| `tests/api/test_config_api.py` | ~50 | ConfigAPI basic methods |
| `tests/api/test_config_api_layered.py` | ~30 | Layered loading |
| `tests/cli/test_config_cmd.py` | ~40 | CLI commands |
| `tests/cli/test_config_cmd_extended.py` | ~30 | Extended CLI features |
| **Total** | **~300** | **~90%+** |

### Test Categories

**Unit Tests:**
- Template resolution (all syntax variations)
- Deep merge logic
- Source tracking accuracy
- Secret detection and masking
- Validation rules

**Integration Tests:**
- Full layer merge with temp files
- Environment variable injection
- CLI command execution with `CliRunner`
- Error handling and recovery

**Edge Cases:**
- Missing files
- Invalid YAML syntax
- Circular template references
- Missing required environment variables
- `.dli.local.yaml` not in `.gitignore`

---

## Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Lenient `from_environment()` | Users call `validate()` explicitly for strict checking |
| 2 | `.dli.local.yaml` gitignore check | Warn about potential secret commits |
| 3 | ConfigSource in `models/config.py` | Separate from `models/common.py` to avoid circular imports |
| 4 | Template syntax `${VAR:-default}` | Industry standard (shell, Docker, Terraform) |
| 5 | No auto-creation of config files | Explicit `init` command required for clarity |
| 6 | YAML only (no JSON/TOML) | Consistency, comments support |
| 7 | Secret detection by prefix + key pattern | `DLI_SECRET_*` prefix + "password", "api_key", "token" in key name |
| 8 | Three-file hierarchy | Balance between flexibility and complexity |

---

## Common Use Cases

### Use Case 1: Local Development

```bash
# Initialize project
cd /path/to/project
dli config init

# Set local server URL (not committed)
dli config set server.url "http://localhost:8081" --local

# Verify config
dli config show --show-source

# Use in code
from dli import ExecutionContext
ctx = ExecutionContext.from_environment()
```

### Use Case 2: CI/CD Pipeline

```bash
# Set via environment variables (no config files)
export DLI_SERVER_URL="https://prod.basecamp.io"
export DLI_SECRET_API_KEY="sk-prod-abc123"
export DLI_ENVIRONMENT="prod"

# Validate before running
dli config validate --strict

# Run commands
dli dataset run my_dataset
```

### Use Case 3: Multi-Environment Development

```yaml
# dli.yaml
environments:
  dev:
    server_url: "http://localhost:8081"
    catalog: "dev_catalog"
  staging:
    server_url: "https://staging.basecamp.io"
    catalog: "staging_catalog"
  prod:
    server_url: "https://prod.basecamp.io"
    catalog: "prod_catalog"
```

```bash
# Switch to staging
dli config env staging

# Verify current environment
dli config env
# Current environment: staging

# Use in code
ctx = ExecutionContext.from_environment(environment="prod")
```

### Use Case 4: Secret Management

```yaml
# dli.yaml (committed)
server:
  url: "${DLI_SERVER_URL}"
  api_key: "${DLI_SECRET_API_KEY:?API key must be set}"

database:
  password: "${DLI_SECRET_DB_PASSWORD}"
```

```bash
# Set secrets in .dli.local.yaml or environment
export DLI_SECRET_API_KEY="sk-dev-xyz789"
export DLI_SECRET_DB_PASSWORD="password123"

# Verify secrets are masked
dli config show
# API Key                 *** (configured)
# DB Password             *** (configured)
```

---

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Load config (first time) | O(n) | n = total config keys across all layers |
| Load config (cached) | O(1) | Lazy loading pattern |
| Get single value | O(d) | d = key depth (e.g., "server.url" → d=2) |
| Template resolution | O(n*m) | n = config keys, m = avg templates per value |
| Deep merge | O(n) | Recursive dict merge |

**Memory usage:**
- ConfigLoader: ~1-5 KB per project (depending on config size)
- ConfigAPI cache: ~1-5 KB (same as ConfigLoader)

---

## Related Documents

- **[ENV_FEATURE.md](./ENV_FEATURE.md)** - Feature specification and requirements
- **[_STATUS.md](./_STATUS.md)** - Project-wide status and changelog
- **[../README.md](../README.md)** - CLI overview and quick start
- **[../docs/PATTERNS.md](../docs/PATTERNS.md)** - Development patterns
