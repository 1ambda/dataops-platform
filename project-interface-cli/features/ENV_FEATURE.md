# FEATURE: Environment Management - Configuration Layering and Secrets

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | ✅ Complete (v0.7.0) |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-01 |
| **Implementation** | [ENV_RELEASE.md](./ENV_RELEASE.md) |
| **Test Coverage** | ~300 tests |
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

## 4. CLI Design ✅

### 4.1 Command Structure (Implemented)

```
dli config <subcommand> [options]
```

| Subcommand | Status | Description |
|------------|--------|-------------|
| `show` | ✅ | Display current configuration with sources |
| `status` | ✅ | Check server connection and validate config |
| `validate` | ✅ | Validate configuration without connecting |
| `env` | ✅ | List or switch named environments |
| `init` | ✅ | Initialize configuration files |
| `set` | ✅ | Set configuration value in local file |

> **Implementation Details**: See [ENV_RELEASE.md](./ENV_RELEASE.md) for command usage examples and output formats.

### 4.2 Command Features (Implemented)

**Key capabilities:**
- ✅ `show` - Display config with optional source tracking (`--show-source`)
- ✅ `status` - Connection testing and file validation
- ✅ `validate` - Strict/lenient validation modes
- ✅ `env` - Environment listing and switching
- ✅ `init` - Template-based initialization (minimal/full)
- ✅ `set` - Dot-notation key updates with target selection

> **Command Usage**: See [ENV_RELEASE.md](./ENV_RELEASE.md) for detailed command examples and output formats.

---

## 5. API Design ✅

### 5.1 ConfigAPI (Implemented)

**Core methods:**
- ✅ `get_all()` - Get merged configuration dict
- ✅ `get(key, default)` - Get value by dot-notation key
- ✅ `get_with_source(key)` - Get value with source tracking
- ✅ `get_all_with_sources()` - Get all values with sources
- ✅ `validate(strict)` - Configuration validation
- ✅ `list_environments()` - List available environments
- ✅ `get_environment(name)` - Get environment-specific config
- ✅ `get_active_environment()` - Get current environment name
- ✅ `get_server_status()` - Server connection status

**Models (in `models/config.py`):**
- ✅ `ConfigSource` - Enum: DEFAULT, GLOBAL, PROJECT, LOCAL, ENV_VAR, CLI
- ✅ `ConfigValueInfo` - Value with source tracking and secret masking
- ✅ `ConfigValidationResult` - Validation errors and warnings
- ✅ `EnvironmentProfile` - Environment configuration

> **API Signatures**: See [ENV_RELEASE.md](./ENV_RELEASE.md) for complete API documentation and usage examples.

### 5.2 ExecutionContext Factory ✅

**Implemented:**
- ✅ `ExecutionContext.from_environment(project_path, environment, overrides)` - Factory for automatic context creation from config layers

**Usage:**
```python
# Auto-load from config files + env vars
ctx = ExecutionContext.from_environment()

# Load specific environment (dev, staging, prod)
ctx = ExecutionContext.from_environment(environment="prod")

# With runtime overrides
ctx = ExecutionContext.from_environment(overrides={"timeout": 600})
```

> **Implementation Details**: See [ENV_RELEASE.md](./ENV_RELEASE.md) for complete implementation.

---

## 6. Core Implementation ✅

### 6.1 ConfigLoader (Implemented in `core/config_loader.py`)

**Key components:**
- ✅ **Hierarchical Loading**: Merges 4 layers (global → project → local → env vars)
- ✅ **Template Resolution**: `${VAR}`, `${VAR:-default}`, `${VAR:?error}` syntax
- ✅ **Source Tracking**: Maintains source map for each config value
- ✅ **Deep Merge**: Recursive dictionary merging with override precedence
- ✅ **YAML Safe Loading**: Uses `yaml.safe_load()` for security

**Priority order:**
```
CLI > ENV_VAR > LOCAL (.dli.local.yaml) > PROJECT (dli.yaml) > GLOBAL (~/.dli/config.yaml) > DEFAULT
```

> **Implementation Details**: See `src/dli/core/config_loader.py` (~350 lines) and [ENV_RELEASE.md](./ENV_RELEASE.md) for algorithm details.

---

## 7. Error Codes ✅

Environment errors use the existing configuration error range (DLI-0xx).

| Code | Name | Status | Description |
|------|------|--------|-------------|
| DLI-001 | `CONFIG_NOT_FOUND` | ✅ | Configuration file not found |
| DLI-002 | `CONFIG_INVALID` | ✅ | Invalid configuration syntax |
| DLI-003 | `CONFIG_MISSING_REQUIRED` | ✅ | Required configuration missing |
| DLI-004 | `CONFIG_ENV_NOT_FOUND` | ✅ | Named environment not found |
| DLI-005 | `CONFIG_TEMPLATE_ERROR` | ✅ | Template resolution failed |
| DLI-006 | `CONFIG_VALIDATION_ERROR` | ✅ | Configuration validation failed |
| DLI-007 | `CONFIG_WRITE_ERROR` | ✅ | Failed to write configuration |

---

## 8. Implementation Status ✅

### Phase 1: Core Layering (MVP) - ✅ Complete

| Status | Task |
|--------|------|
| ✅ | `ConfigLoader` with layer merging |
| ✅ | Template resolution (`${VAR}`, `${VAR:-default}`, `${VAR:?error}`) |
| ✅ | `ConfigAPI.get_all()` and `ConfigAPI.get()` |
| ✅ | `dli config show` with basic output |
| ✅ | `.dli.local.yaml` support |
| ✅ | Unit tests for config loading (~100 tests) |

### Phase 2: Source Tracking & Validation - ✅ Complete

| Status | Task |
|--------|------|
| ✅ | Source tracking per configuration value |
| ✅ | `dli config show --show-source` |
| ✅ | `DLI_SECRET_*` masking |
| ✅ | `dli config validate` command |
| ✅ | `ExecutionContext.from_environment()` factory |

### Phase 3: Environment Management - ✅ Complete

| Status | Task |
|--------|------|
| ✅ | Named environments support |
| ✅ | `dli config env` list and switch |
| ✅ | `dli config init` scaffolding (minimal/full templates) |
| ✅ | `dli config set` for runtime updates |
| ✅ | Integration tests (~200 tests total) |

---

## 9. Success Criteria ✅

### 9.1 Feature Completion - ✅ All Met

| Feature | Status | Verification |
|---------|--------|--------------|
| Config layering | ✅ | Three layers merge correctly |
| Template resolution | ✅ | `${VAR}`, `${VAR:-default}`, `${VAR:?error}` work |
| Source tracking | ✅ | Every value shows correct origin |
| Secret masking | ✅ | `DLI_SECRET_*` values displayed as `***` |
| Validation | ✅ | Missing required fields detected |

### 9.2 Test Quality - ✅ Exceeded Target

| Metric | Target | Actual |
|--------|--------|--------|
| Unit test coverage | >= 80% | ~90%+ |
| Layer merge tests | All priority combinations | ✅ Complete |
| Template tests | All syntax variations | ✅ Complete |
| CLI tests | All subcommands | ✅ Complete |
| **Total tests** | ~200 | **~300** |

### 9.3 Code Quality - ✅ Verified

| Principle | Status | Verification |
|-----------|--------|--------------|
| Single Responsibility | ✅ | ConfigLoader only loads, ConfigAPI only provides access |
| Explicit is Better | ✅ | Source tracking for every value |
| Fail-Fast | ✅ | Missing required config fails immediately |

---

## 10. Directory Structure ✅

```
project-interface-cli/src/dli/
├── api/
│   └── config.py         # ✅ EXTENDED: ConfigAPI with layered loading
├── commands/
│   └── config.py         # ✅ EXTENDED: New subcommands (env, init, set, validate)
├── core/
│   ├── config.py         # ✅ EXTENDED: ProjectConfig updates
│   └── config_loader.py  # ✅ NEW: ConfigLoader class (~350 lines)
├── models/
│   ├── common.py         # ✅ EXTENDED: ExecutionContext.from_environment()
│   └── config.py         # ✅ NEW: ConfigSource, ConfigValueInfo, etc.
└── exceptions.py         # ✅ EXTENDED: CONFIG_ENV_NOT_FOUND, etc.
```

**Test coverage:**
```
tests/
├── api/test_config_api.py              # ✅ ConfigAPI tests
├── api/test_config_api_layered.py      # ✅ Layered loading tests
├── cli/test_config_cmd.py              # ✅ CLI command tests
├── cli/test_config_cmd_extended.py     # ✅ Extended command tests
├── core/test_config.py                 # ✅ Core config tests
├── core/test_config_loader.py          # ✅ ConfigLoader tests (~100 tests)
└── models/test_config_models.py        # ✅ Config models tests
```

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

## Implementation Summary ✅

**Completion Date**: 2026-01-01 (v0.7.0)

### What Was Built

| Component | Files | LOC | Tests |
|-----------|-------|-----|-------|
| ConfigLoader | `core/config_loader.py` | ~350 | ~100 |
| Config Models | `models/config.py` | ~150 | ~50 |
| ConfigAPI Extensions | `api/config.py` | +200 | ~80 |
| CLI Commands | `commands/config.py` | +300 | ~70 |
| **Total** | **4 files** | **~1000** | **~300** |

### Key Achievements

1. ✅ **Zero Breaking Changes** - All existing code continues to work
2. ✅ **Production Ready** - Comprehensive error handling and validation
3. ✅ **Well Tested** - ~300 tests covering all scenarios
4. ✅ **Documented** - Complete API docs and usage examples
5. ✅ **Secure** - Secret masking, safe YAML loading, fail-fast validation

### Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Lenient `from_environment()` | Users call `validate()` explicitly for strict checking |
| `.dli.local.yaml` gitignore check | Warn about potential secret commits |
| ConfigSource in `models/config.py` | Separate from `models/common.py` to avoid circular imports |
| Template syntax `${VAR:-default}` | Industry standard (shell, Docker, Terraform) |

---

## Related Documents

- **[ENV_RELEASE.md](./ENV_RELEASE.md)** - Complete implementation documentation
- **[_STATUS.md](./_STATUS.md)** - Project-wide status and changelog
