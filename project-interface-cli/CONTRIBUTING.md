# Contributing to DLI CLI

This guide helps you quickly add new features to the `dli` CLI following established patterns.

## Table of Contents

1. [Project Architecture](#project-architecture)
2. [Adding CLI Commands](#1-adding-cli-commands)
3. [Adding Spec Model Fields](#2-adding-spec-model-fields)
4. [Adding Quality Test Types](#3-adding-quality-test-types)
5. [Adding Validation Checks](#4-adding-validation-checks)
6. [Adding Core Modules](#5-adding-core-modules)
7. [Writing Tests](#6-writing-tests)

---

## Project Architecture

```
src/dli/
├── commands/                   # CLI layer (Typer)
│   ├── __init__.py             # Exports all commands and sub-apps
│   ├── base.py                 # Shared CLI utilities (get_project_path, get_client)
│   ├── utils.py                # Output helpers (console, print_error, print_success)
│   ├── metric.py               # metric_app subcommand (list, get, run)
│   ├── dataset.py              # dataset_app subcommand
│   ├── server.py               # server_app subcommand
│   ├── validate.py             # validate command (top-level)
│   ├── lineage.py              # lineage_app subcommand
│   ├── quality.py              # quality_app subcommand
│   ├── workflow.py             # workflow_app subcommand
│   ├── catalog.py              # catalog_app subcommand
│   ├── render.py               # render command (SQL rendering)
│   ├── info.py                 # info command (project information)
│   └── version.py              # version command
├── core/                       # Core library
│   ├── __init__.py             # Re-exports from all submodules
│   ├── client.py               # BasecampClient for server communication
│   ├── config.py               # Configuration management
│   ├── discovery.py            # Spec file discovery
│   ├── executor.py             # SQL execution engine
│   ├── registry.py             # Spec registry
│   ├── renderer.py             # SQL template rendering
│   ├── service.py              # Core service layer
│   ├── metric_service.py       # Metric-specific service
│   ├── validator.py            # SQL validation
│   ├── sql_filters.py          # SQL filter utilities
│   ├── templates.py            # Jinja template utilities
│   ├── types.py                # Type definitions
│   ├── models/                 # Pydantic models
│   │   ├── __init__.py         # Re-exports all models
│   │   ├── base.py             # Enums and shared types
│   │   ├── spec.py             # SpecBase class
│   │   ├── metric.py           # MetricSpec
│   │   ├── dataset.py          # DatasetSpec
│   │   └── results.py          # Execution result types
│   ├── validation/             # Local validation (no server)
│   │   ├── __init__.py
│   │   ├── spec_validator.py   # YAML + SQL syntax validation
│   │   └── dep_validator.py    # Dependency graph validation
│   ├── lineage/                # Lineage client
│   │   ├── __init__.py
│   │   └── client.py           # LineageClient (server-based)
│   ├── quality/                # Data quality testing
│   │   ├── __init__.py
│   │   ├── models.py           # DqTestDefinition, DqTestResult, etc.
│   │   ├── builtin_tests.py    # SQL generators (not_null, unique, etc.)
│   │   ├── registry.py         # QualityRegistry for test discovery
│   │   └── executor.py         # QualityExecutor for running tests
│   ├── workflow/               # Workflow management
│   │   ├── __init__.py
│   │   └── models.py           # WorkflowSpec, WorkflowStatus, etc.
│   └── catalog/                # Catalog browsing
│       ├── __init__.py
│       └── models.py           # TableInfo, TableDetail, etc.
└── adapters/                   # External system adapters
    └── bigquery.py
```

**Test Organization:**
```
tests/
├── cli/                        # CLI command tests
│   ├── test_main.py            # Top-level command tests
│   ├── test_base.py            # Shared utility tests
│   ├── test_utils.py           # Output helper tests
│   ├── test_metric_cmd.py      # metric subcommand tests
│   ├── test_dataset_cmd.py     # dataset subcommand tests
│   ├── test_server_cmd.py      # server subcommand tests
│   ├── test_validate_cmd.py    # validate command tests
│   ├── test_lineage_cmd.py     # lineage subcommand tests
│   ├── test_quality_cmd.py     # quality subcommand tests
│   ├── test_workflow_cmd.py    # workflow subcommand tests
│   └── test_catalog_cmd.py     # catalog subcommand tests
├── core/                       # Core library tests
│   ├── validation/             # Validation module tests
│   │   ├── test_spec_validator.py
│   │   └── test_dep_validator.py
│   ├── lineage/                # Lineage module tests
│   │   └── test_client.py
│   ├── quality/                # Quality module tests
│   │   ├── test_models.py
│   │   ├── test_builtin_tests.py
│   │   ├── test_registry.py
│   │   └── test_executor.py
│   ├── workflow/               # Workflow module tests
│   │   └── test_models.py
│   ├── catalog/                # Catalog module tests
│   │   └── test_models.py
│   ├── test_client.py          # BasecampClient tests
│   ├── test_discovery.py       # Spec discovery tests
│   ├── test_executor.py        # SQL executor tests
│   ├── test_models.py          # Core model tests
│   ├── test_registry.py        # Spec registry tests
│   ├── test_renderer.py        # SQL renderer tests
│   ├── test_service.py         # Service layer tests
│   ├── test_metric_service.py  # Metric service tests
│   ├── test_validator.py       # SQL validator tests
│   └── test_templates.py       # Jinja template tests
├── fixtures/                   # Test fixtures
│   ├── sample_project/
│   └── examples/
└── conftest.py                 # Shared pytest fixtures
```

---

## 1. Adding CLI Commands

### Simple Command (top-level)

Create a new file in `src/dli/commands/` and register it in `main.py`.

**File: `src/dli/commands/my_command.py`**
```python
"""My command description."""
from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer

from dli.commands.utils import console, print_error, print_success


def my_command(
    name: Annotated[str, typer.Argument(help="Resource name.")],
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    verbose: Annotated[
        bool,
        typer.Option("--verbose", "-v", help="Enable verbose output."),
    ] = False,
) -> None:
    """Do something useful.

    Examples:
        dli my-command resource_name
        dli my-command resource_name --verbose
    """
    try:
        # Implementation here
        print_success(f"Success: {name}")
    except Exception as e:
        print_error(str(e))
        raise typer.Exit(1)
```

**Register in `src/dli/main.py`:**
```python
from dli.commands import my_command as my_command_cmd

app.command()(my_command_cmd)
```

### Subcommand Pattern (grouped commands)

For related commands (e.g., `dli metric list`, `dli metric get`), use a Typer sub-app.

**File: `src/dli/commands/my_feature.py`**
```python
"""My feature subcommand for DLI CLI."""
from __future__ import annotations

import typer
from dli.commands.base import get_project_path, OutputFormat
from dli.commands.utils import console, print_error

# Create subcommand app
my_feature_app = typer.Typer(
    name="my-feature",
    help="My feature management commands.",
    no_args_is_help=True,
)


@my_feature_app.command("list")
def list_items(...) -> None:
    """List items."""
    pass


@my_feature_app.command("get")
def get_item(...) -> None:
    """Get item details."""
    pass
```

**Register in `src/dli/commands/__init__.py`:**
```python
from dli.commands.my_feature import my_feature_app

__all__ = [..., "my_feature_app"]
```

**Register in `src/dli/main.py`:**
```python
from dli.commands import my_feature_app

app.add_typer(my_feature_app, name="my-feature")
```

**Key files to reference:**
- `src/dli/commands/metric.py` - Complete subcommand example
- `src/dli/commands/quality.py` - Subcommand with core module integration
- `src/dli/commands/workflow.py` - Workflow subcommand with server operations
- `src/dli/commands/base.py` - Shared utilities (`get_project_path`, `get_client`)
- `src/dli/commands/utils.py` - Output helpers (`print_error`, `print_success`, `console`)

---

## 2. Adding Spec Model Fields

### Adding a Field to SpecBase (shared by Metric and Dataset)

**File: `src/dli/core/models/spec.py`**
```python
class SpecBase(BaseModel):
    # Existing fields...

    # Add new field with proper type and default
    priority: int = Field(default=0, description="Execution priority (higher = first)")

    # For optional fields
    custom_config: dict[str, Any] | None = Field(default=None, description="Custom config")
```

### Adding a Field to MetricSpec or DatasetSpec Only

**File: `src/dli/core/models/metric.py` or `dataset.py`**
```python
class MetricSpec(SpecBase):
    """Metric-specific fields."""

    # Add metric-only field
    aggregation_type: str = Field(default="sum", description="Aggregation method")
```

### Adding Complex Nested Types

**File: `src/dli/core/models/base.py`**
```python
class AlertConfig(BaseModel):
    """Alert configuration for specs."""
    enabled: bool = True
    threshold: float = 0.0
    channels: list[str] = Field(default_factory=list)
```

Then use in spec:
```python
alerts: AlertConfig = Field(default_factory=AlertConfig, description="Alert settings")
```

**Key files to reference:**
- `src/dli/core/models/spec.py` - Base spec with validators
- `src/dli/core/models/base.py` - Enums and nested types
- `src/dli/core/models/metric.py` - Type-specific extensions

**Pitfalls to avoid:**
- Always use `Field(default_factory=list)` for mutable defaults, not `default=[]`
- Add `field_validator` for complex validation
- Update `spec_to_dict()` in `commands/base.py` if the field should appear in CLI output

---

## 3. Adding Quality Test Types

The quality module uses the pattern: **"A test is a SELECT query that returns rows that fail the test."**

### Adding a Built-in Generic Test

**File: `src/dli/core/quality/builtin_tests.py`**

Add a new class method to `BuiltinTests`:

```python
@classmethod
def freshness(
    cls,
    table: str,
    column: str,
    max_age_hours: int = 24,
) -> str:
    """Generate FRESHNESS test SQL.

    Returns rows if the newest timestamp is older than max_age_hours.
    Test passes if no rows are returned.

    Args:
        table: Fully qualified table name
        column: Timestamp column to check
        max_age_hours: Maximum allowed age in hours

    Returns:
        SQL query that returns a row if data is stale
    """
    table = cls._validate_identifier(table)
    column = cls._validate_identifier(column)

    return f"""WITH _dli_freshness AS (
    SELECT MAX({column}) as latest FROM {table}
)
SELECT latest as _dli_latest_timestamp
FROM _dli_freshness
WHERE latest < CURRENT_TIMESTAMP - INTERVAL '{max_age_hours}' HOUR"""
```

**Update the `generate()` dispatcher:**

```python
@classmethod
def generate(cls, test_type: str, table: str, **kwargs: Any) -> str:
    generators = {
        # ... existing generators ...
        "freshness": cls.freshness,  # Add new test type
    }

    # Add dispatch logic in the if-elif chain:
    elif test_type == "freshness":
        return generator(
            table,
            kwargs.get("column", kwargs.get("columns", [""])[0]),
            kwargs.get("max_age_hours", 24),
        )
```

### Test File Example

**File: `tests/core/quality/test_builtin_tests.py`**

```python
class TestFreshnessTest:
    """Tests for freshness test SQL generation."""

    def test_freshness_basic(self):
        """Test basic freshness SQL generation."""
        sql = BuiltinTests.freshness("db.events", "created_at")
        assert "MAX(created_at)" in sql
        assert "INTERVAL '24' HOUR" in sql

    def test_freshness_custom_age(self):
        """Test freshness with custom max age."""
        sql = BuiltinTests.freshness("db.events", "ts", max_age_hours=1)
        assert "INTERVAL '1' HOUR" in sql

    def test_freshness_invalid_identifier(self):
        """Test validation rejects invalid identifiers."""
        with pytest.raises(ValueError, match="Invalid identifier"):
            BuiltinTests.freshness("db.events; DROP TABLE--", "created_at")
```

---

## 4. Adding Validation Checks

The validation module has two validators:
- **SpecValidator** (`spec_validator.py`): YAML schema + SQL syntax validation
- **DepValidator** (`dep_validator.py`): Dependency graph validation

### Adding a Schema Validation Rule

Add a Pydantic validator to the spec model:

**File: `src/dli/core/models/spec.py`**

```python
from pydantic import field_validator

class SpecBase(BaseModel):
    # ... existing fields ...

    @field_validator("name")
    @classmethod
    def validate_name_format(cls, v: str) -> str:
        """Validate name follows catalog.schema.table format."""
        parts = v.split(".")
        if len(parts) != 3:
            raise ValueError("Name must be catalog.schema.table format")
        return v
```

### Adding a SQL Validation Warning

**File: `src/dli/core/validator.py`**

Add warning detection in `SQLValidator.validate()`:

```python
def validate(self, sql: str) -> ValidationResult:
    # ... existing validation ...

    # Add custom warning
    if "SELECT *" in sql.upper():
        warnings.append("SELECT * detected - consider explicit column list")

    # Check for missing WHERE in DELETE/UPDATE
    if re.search(r"\b(DELETE|UPDATE)\b", sql, re.IGNORECASE):
        if not re.search(r"\bWHERE\b", sql, re.IGNORECASE):
            warnings.append("DELETE/UPDATE without WHERE clause")

    return ValidationResult(is_valid=..., errors=errors, warnings=warnings)
```

### Adding a Dependency Validation Rule

**File: `src/dli/core/validation/dep_validator.py`**

Add validation in `DepValidator.validate()`:

```python
def validate(self, spec: SpecBase) -> DepValidationResult:
    # ... existing validation ...

    # Add custom validation: check for cross-catalog dependencies
    spec_catalog = spec.name.split(".")[0]
    for dep_name in dependencies:
        dep_catalog = dep_name.split(".")[0]
        if dep_catalog != spec_catalog:
            warnings.append(
                f"Cross-catalog dependency: {spec.name} -> {dep_name}"
            )

    return DepValidationResult(...)
```

### Test File Example

**File: `tests/core/validation/test_spec_validator.py`**

```python
class TestSpecValidator:
    """Tests for SpecValidator."""

    def test_valid_spec(self, tmp_path: Path):
        """Test validation of a valid spec."""
        spec_file = tmp_path / "metric.test.yaml"
        spec_file.write_text("""
name: catalog.schema.metric
owner: team@example.com
team: data-platform
type: metric
query_type: select
sql: SELECT COUNT(*) FROM users
""")
        validator = SpecValidator(dialect="trino")
        result = validator.validate_file(spec_file)
        assert result.is_valid

    def test_invalid_name_format(self, tmp_path: Path):
        """Test validation fails for invalid name format."""
        spec_file = tmp_path / "metric.test.yaml"
        spec_file.write_text("""
name: invalid_name
owner: team@example.com
team: data-platform
""")
        validator = SpecValidator()
        result = validator.validate_file(spec_file)
        assert not result.is_valid
        assert any("catalog.schema.table" in e for e in result.errors)
```

---

## 5. Adding Core Modules

Follow the `quality/` or `lineage/` module structure.

### Module Structure
```
src/dli/core/my_module/
    __init__.py     # Public API exports
    models.py       # Pydantic/dataclass models
    registry.py     # Discovery and loading (optional)
    executor.py     # Execution logic (optional)
    client.py       # External API client (optional)
```

### Example: `__init__.py`
```python
"""My Module for the DLI Core Engine.

Usage:
    >>> from dli.core.my_module import MyExecutor, MyModel
"""

from dli.core.my_module.executor import MyExecutor, create_executor
from dli.core.my_module.models import MyModel, MyConfig

__all__ = [
    "MyModel",
    "MyConfig",
    "MyExecutor",
    "create_executor",
]
```

### Example: `models.py`
```python
"""Models for my module."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class MyStatus(str, Enum):
    """Status enumeration."""
    PENDING = "pending"
    COMPLETE = "complete"


@dataclass
class MyModel:
    """Core model."""
    name: str
    status: MyStatus = MyStatus.PENDING
    metadata: dict = field(default_factory=dict)
```

### Export from Core
**File: `src/dli/core/__init__.py`**
```python
# Import from your module
from dli.core.my_module import MyExecutor, MyModel

__all__ = [
    # ... existing exports ...
    # My Module
    "MyExecutor",
    "MyModel",
]
```

**Key files to reference:**
- `src/dli/core/quality/` - Complete feature module (models, registry, executor, builtin_tests)
- `src/dli/core/validation/` - Validator module (spec_validator, dep_validator)
- `src/dli/core/lineage/` - Simpler client-based module
- `src/dli/core/workflow/` - Workflow module (models for workflow management)

---

## 6. Writing Tests

### CLI Command Tests

Use `CliRunner` from Typer for testing CLI commands.

**File: `tests/cli/test_my_command.py`**
```python
"""Tests for my command."""
from pathlib import Path

from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


class TestMyCommand:
    """Tests for my-command."""

    def test_basic_usage(self, tmp_path: Path):
        """Test basic command execution."""
        result = runner.invoke(app, ["my-command", "test-name"])
        assert result.exit_code == 0
        assert "Success" in result.stdout

    def test_with_options(self, tmp_path: Path):
        """Test with CLI options."""
        result = runner.invoke(app, ["my-command", "test-name", "--verbose"])
        assert result.exit_code == 0

    def test_error_handling(self):
        """Test error case."""
        result = runner.invoke(app, ["my-command", "invalid"])
        assert result.exit_code == 1
        assert "Error" in result.output
```

### Core Module Tests

**File: `tests/core/my_module/test_executor.py`**
```python
"""Tests for my module executor."""
import pytest
from dli.core.my_module import MyExecutor, MyModel


class TestMyExecutor:
    """Tests for MyExecutor."""

    @pytest.fixture
    def executor(self):
        """Create test executor."""
        return MyExecutor()

    def test_basic_execution(self, executor):
        """Test basic execution."""
        model = MyModel(name="test")
        result = executor.execute(model)
        assert result.success
```

### Using Shared Fixtures

**File: `tests/conftest.py`** provides common fixtures:

```python
# Path fixtures
@pytest.fixture
def fixtures_path() -> Path:
    """Return path to test fixtures directory."""
    return Path(__file__).parent / "fixtures"

@pytest.fixture
def tmp_project_path(tmp_path: Path) -> Path:
    """Create a temporary project with dli.yaml."""
    ...

# Mock fixtures
@pytest.fixture
def mock_client() -> Mock:
    """Create a mock BasecampClient."""
    ...

# Sample YAML fixtures
@pytest.fixture
def sample_metric_yaml() -> str:
    """Sample metric YAML specification."""
    ...
```

**Key files to reference:**
- `tests/conftest.py` - Shared fixtures
- `tests/cli/test_main.py` - CLI test patterns
- `tests/cli/test_quality_cmd.py` - CLI with mocked core modules
- `tests/cli/test_workflow_cmd.py` - Workflow command test patterns
- `tests/core/quality/` - Core module test patterns
- `tests/core/validation/` - Validation test patterns
- `tests/core/workflow/` - Workflow module test patterns

**Pitfalls to avoid:**
- Always use `result.output` (not `result.stdout`) when checking error messages
- Use `tmp_path` fixture for file-based tests
- Check both `exit_code` and output content

---

## Quick Checklist

Before submitting:

- [ ] `uv run pytest` passes
- [ ] `uv run ruff format && uv run ruff check --fix` passes
- [ ] `--help` text is clear for new commands
- [ ] Docstrings include usage examples
- [ ] New modules exported in `__init__.py`
- [ ] Tests cover happy path and error cases
- [ ] Quality test SQL follows "return failing rows" pattern
- [ ] Validation checks include both errors and warnings where appropriate
