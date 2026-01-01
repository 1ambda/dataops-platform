# ENV Feature Release Documentation

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | Implemented |
| **Release Date** | 2026-01-01 |
| **Test Count** | ~300 tests |

---

## Overview

Environment Management feature provides hierarchical configuration loading with template resolution, secret handling, and environment profiles.

---

## Components Implemented

### Core Components

| Component | File | Lines | Description |
|-----------|------|-------|-------------|
| ConfigLoader | `core/config_loader.py` | ~350 | Hierarchical config loading with template resolution |
| Config Models | `models/config.py` | ~150 | ConfigSource, ConfigValueInfo, ConfigValidationResult, EnvironmentProfile |

### API Extensions

| Method | Description |
|--------|-------------|
| `ConfigAPI.get_all()` | Get merged configuration dict |
| `ConfigAPI.get(key, default)` | Get config value by dot-notation key |
| `ConfigAPI.get_with_source(key)` | Get value with source tracking |
| `ConfigAPI.get_all_with_sources()` | Get all values with sources |
| `ConfigAPI.validate(strict)` | Validate configuration |
| `ConfigAPI.get_environment(name)` | Get environment-specific config |
| `ExecutionContext.from_environment()` | Factory method for context creation |

### CLI Commands

| Command | Description |
|---------|-------------|
| `dli config show --show-source` | Show config with source tracking |
| `dli config show --section` | Show specific section |
| `dli config validate --strict` | Strict validation |
| `dli config env --list` | List environments |
| `dli config env <name>` | Switch environment |
| `dli config init --global` | Initialize config |
| `dli config set <key> <value>` | Set config value |

### Error Codes

| Code | Name | Description |
|------|------|-------------|
| DLI-004 | CONFIG_ENV_NOT_FOUND | Named environment not found |
| DLI-005 | CONFIG_TEMPLATE_ERROR | Template resolution failed |
| DLI-006 | CONFIG_VALIDATION_ERROR | Validation failed |
| DLI-007 | CONFIG_WRITE_ERROR | Failed to write config |

---

## Key Features

### Hierarchical Configuration
Priority: CLI > ENV_VAR > LOCAL > PROJECT > GLOBAL

### Template Resolution
- `${VAR}` - Required
- `${VAR:-default}` - With default
- `${VAR:?error}` - Custom error

### Secret Handling
- `DLI_SECRET_*` prefix masked
- Key pattern detection (password, api_key, token)

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| Lenient `from_environment()` | Use `validate()` for strict checking |
| `.dli.local.yaml` gitignore check | Warn about potential secret commits |

---

## Related Documents

- [ENV_FEATURE.md](./ENV_FEATURE.md) - Feature specification
- [_STATUS.md](./_STATUS.md) - Project status
