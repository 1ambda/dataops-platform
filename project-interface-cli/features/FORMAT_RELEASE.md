# FORMAT Feature Release Notes (v0.9.0)

> **Status:** ✅ Implemented
> **Released:** 2026-01-01
> **Phase:** MVP Complete

---

## Summary

Implements SQL + YAML formatting for Dataset/Metric specs using sqlfluff and ruamel.yaml.

## New Components

### Error Codes (DLI-15xx)

| Code | Name | Description |
|------|------|-------------|
| DLI-1501 | FORMAT_ERROR | General formatting error |
| DLI-1502 | FORMAT_SQL_ERROR | SQL formatting failed |
| DLI-1503 | FORMAT_YAML_ERROR | YAML formatting failed |
| DLI-1504 | FORMAT_DIALECT_ERROR | Unsupported SQL dialect |
| DLI-1505 | FORMAT_CONFIG_ERROR | Config file error |
| DLI-1506 | FORMAT_LINT_ERROR | Lint rule violation |

### Models

- `FormatStatus` - Format operation status (SUCCESS, CHANGED, FAILED)
- `FileFormatStatus` - Per-file status (UNCHANGED, CHANGED, ERROR)
- `LintViolation` - Lint violation details
- `FileFormatResult` - Single file format result
- `FormatResult` - Overall format result

### Core Formatters

- `SqlFormatter` - sqlfluff-based SQL formatting with Jinja preservation
- `YamlFormatter` - ruamel.yaml-based YAML formatting with DLI key ordering
- `FormatConfig` - Configuration loading (.sqlfluff, .dli-format.yaml)

### API Methods

- `DatasetAPI.format()` - Format dataset SQL/YAML files
- `MetricAPI.format()` - Format metric SQL/YAML files

### CLI Commands

- `dli dataset format <name>` - Format dataset
- `dli metric format <name>` - Format metric

Options: --check, --sql-only, --yaml-only, --dialect, --lint, --fix, --diff, --format

---

## Test Coverage

| Category | Tests | Status |
|----------|-------|--------|
| Model Tests | 50+ | ✅ |
| SQL Formatter Tests | 35 | ✅ |
| YAML Formatter Tests | 30 | ✅ |
| API Tests | 27 | ✅ |
| CLI Tests | 25 | ✅ |
| Integration Tests | 24 | ✅ |
| Exception Tests | 40+ | ✅ |

**Total: 239 passed, 35 skipped (optional deps)**

---

## Dependencies

Optional dependencies (install with `pip install dli[format]`):

- `sqlfluff>=3.0.0` - SQL formatting and linting
- `ruamel.yaml>=0.18.0` - YAML formatting with comment preservation

---

## Usage

### CLI

```bash
# Format dataset
dli dataset format iceberg.analytics.daily_clicks

# Check mode (CI-friendly)
dli dataset format iceberg.analytics.daily_clicks --check

# With lint rules
dli dataset format iceberg.analytics.daily_clicks --lint

# SQL only
dli dataset format iceberg.analytics.daily_clicks --sql-only

# YAML only
dli dataset format iceberg.analytics.daily_clicks --yaml-only

# Specific dialect
dli dataset format iceberg.analytics.daily_clicks --dialect trino

# Show diff
dli dataset format iceberg.analytics.daily_clicks --check --diff

# Auto-fix lint violations
dli dataset format iceberg.analytics.daily_clicks --lint --fix
```

### Library API

```python
from dli import DatasetAPI, ExecutionContext
from pathlib import Path

api = DatasetAPI(context=ExecutionContext(project_path=Path(".")))

# Check mode (no modifications)
result = api.format("catalog.schema.my_dataset", check_only=True)
print(f"Changed files: {result.changed_count}")

# Apply formatting
result = api.format(
    "catalog.schema.my_dataset",
    check_only=False,
    sql_only=False,
    yaml_only=False,
    dialect="bigquery",
    lint=False,
)

# With lint rules
result = api.format("catalog.schema.my_dataset", lint=True, fix=True)
for violation in result.files[0].lint_violations:
    print(f"{violation.code}: {violation.message}")
```

---

## Files Changed

| File | Changes |
|------|---------|
| `src/dli/exceptions.py` | +6 error codes, +6 exception classes |
| `src/dli/models/format.py` | NEW - Format result models |
| `src/dli/core/format/__init__.py` | NEW - Format module |
| `src/dli/core/format/sql_formatter.py` | NEW - SqlFormatter class |
| `src/dli/core/format/yaml_formatter.py` | NEW - YamlFormatter class |
| `src/dli/core/format/config.py` | NEW - FormatConfig class |
| `src/dli/api/dataset.py` | +format() method |
| `src/dli/api/metric.py` | +format() method |
| `src/dli/commands/dataset.py` | +format subcommand |
| `src/dli/commands/metric.py` | +format subcommand |
| `pyproject.toml` | +format optional dependencies |

---

## Implementation Details

### SQL Formatting

- **Engine:** sqlfluff Python API (not subprocess)
- **Dialects:** bigquery, trino, snowflake, sparksql, hive, postgres
- **Jinja Support:** Native templater with `{{ ref() }}`, `{{ ds }}`, `{% if %}` preservation
- **Rules:**
  - Keywords uppercase (SELECT, FROM, WHERE)
  - 4-space indentation
  - Trailing commas
  - AS keyword for aliases

### YAML Formatting

- **Engine:** ruamel.yaml (round-trip mode)
- **Features:**
  - Comment preservation
  - 2-space indentation
  - DLI standard key ordering
- **Key Order:**
  1. name, owner, team, type, query_type (required)
  2. description
  3. domains, tags (classification)
  4. query_file / query_statement
  5. parameters
  6. execution
  7. depends_on
  8. schema
  9. pre_statements, post_statements
  10. versions

### Configuration Hierarchy

```
1. CLI options          --dialect trino
2. .sqlfluff            [sqlfluff] dialect = bigquery
3. .dli-format.yaml     format.sql.dialect: bigquery
4. Defaults             bigquery
```

---

## Exception Classes

```python
class FormatError(DLIError):
    """Base format error (DLI-1501)."""

class FormatSqlError(FormatError):
    """SQL formatting failed (DLI-1502)."""

class FormatYamlError(FormatError):
    """YAML formatting failed (DLI-1503)."""

class FormatDialectError(FormatError):
    """Unsupported SQL dialect (DLI-1504)."""

class FormatConfigError(FormatError):
    """Config file error (DLI-1505)."""

class FormatLintError(FormatError):
    """Lint rule violation (DLI-1506)."""
```

---

## Related Documents

- [FORMAT_FEATURE.md](./FORMAT_FEATURE.md) - Feature specification
- [PATTERNS.md](../docs/PATTERNS.md) - Development patterns
- [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) - ExecutionMode patterns

---

## Changelog

### v0.9.0 (2026-01-01)

- **Format Feature MVP Complete**
  - `dli dataset format` / `dli metric format` commands
  - SqlFormatter (sqlfluff), YamlFormatter (ruamel.yaml)
  - FormatConfig with hierarchy (.sqlfluff, .dli-format.yaml)
  - DLI-15xx error codes (1501-1506)
  - 6 exception classes
  - 239 tests (35 skipped for optional deps)
- **DatasetAPI/MetricAPI.format()** method
  - check_only, sql_only, yaml_only options
  - dialect selection
  - lint/fix options
- **Optional Dependencies**
  - `pip install dli[format]` for sqlfluff + ruamel.yaml
