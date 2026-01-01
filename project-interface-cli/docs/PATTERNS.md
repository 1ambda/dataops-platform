# DLI CLI Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.
> **Version:** 0.7.0 | **Last Updated:** 2026-01-01

---

## CLI Command Structure

```
dli
├── version / info              # Basic info commands
├── config (show, status, validate, env, init, set)  # Configuration management
├── metric (list, get, run, validate, register)
├── dataset (list, get, run, validate, register)
├── workflow (register, unregister, run, backfill, stop, status, list, history, pause, unpause)
├── quality (list, get, run, validate)
├── query (list, show, cancel)             # Query execution metadata
├── lineage (show, upstream, downstream)   # Top-level
├── catalog                                # Top-level
├── transpile                              # Top-level
└── debug                                  # Environment diagnostics
```

---

## Quick Reference

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| New CLI command | `src/dli/commands/dataset.py` | Typer subcommand app |
| `dli run` | `src/dli/commands/run.py` | Ad-hoc SQL execution |
| Config subcommand | `src/dli/commands/config.py` | Simple settings management |
| Data models | `src/dli/core/workflow/models.py` | Pydantic BaseModel |
| Client methods | `src/dli/core/client.py` | Mock + ServerResponse |
| CLI tests | `tests/cli/test_dataset_cmd.py` | CliRunner |
| Model tests | `tests/core/workflow/test_models.py` | pytest + Pydantic |
| **Library API** | `src/dli/api/dataset.py` | Facade + ExecutionContext |
| **API models** | `src/dli/models/common.py` | Pydantic + BaseSettings |
| **Exceptions** | `src/dli/exceptions.py` | Dataclass + ErrorCode |
| **API tests** | `tests/api/test_dataset_api.py` | Mock mode + pytest fixture |
| **Debug checks** | `src/dli/core/debug/` | Diagnostic check pattern |

---

## 1. CLI Command Pattern

### File Structure

```
src/dli/commands/{feature}.py      # CLI commands
src/dli/core/{feature}/models.py   # Data models
src/dli/core/{feature}/__init__.py # Module exports
```

### Command Template

```python
"""Feature subcommand for DLI CLI."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    get_client,
    get_project_path,
)
from dli.commands.utils import (
    console,
    print_error,
    print_success,
    print_warning,
)

# Create subcommand app
feature_app = typer.Typer(
    name="feature",
    help="Feature management commands.",
    no_args_is_help=True,
)


@feature_app.command("list")
def list_items(
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """List items."""
    project_path = get_project_path(path)
    client = get_client(project_path)

    response = client.feature_list()
    if not response.success:
        print_error(response.error or "Failed to list items")
        raise typer.Exit(1)

    items = response.data or []

    if format_output == "json":
        console.print_json(json.dumps(items, default=str))
        return

    # Table output
    table = Table(title=f"Items ({len(items)})", show_header=True)
    table.add_column("Name", style="cyan")
    table.add_column("Status", style="green")

    for item in items:
        table.add_row(item.get("name", ""), item.get("status", ""))

    console.print(table)
```

### Registration Checklist

1. **Add to `commands/__init__.py`:**
   ```python
   from dli.commands.feature import feature_app
   __all__ = [..., "feature_app"]
   ```

2. **Add to `main.py`:**
   ```python
   from dli.commands import feature_app
   app.add_typer(feature_app, name="feature")
   ```

3. **Update docstring in `main.py`:**
   ```python
   """Commands:
       ...
       feature: Feature management (list, get, etc.)
   """
   ```

---

## 2. Data Model Pattern

### Model Template

```python
"""Feature data models."""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field

__all__ = [
    "FeatureStatus",
    "FeatureInfo",
]


class FeatureStatus(str, Enum):
    """Status of a feature."""
    ACTIVE = "active"
    INACTIVE = "inactive"


class FeatureInfo(BaseModel):
    """Feature information."""

    name: str = Field(..., description="Feature name")
    status: FeatureStatus = Field(default=FeatureStatus.ACTIVE)
    created_at: datetime | None = Field(default=None)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @property
    def is_active(self) -> bool:
        """Check if feature is active."""
        return self.status == FeatureStatus.ACTIVE
```

### Module Export (`__init__.py`)

```python
"""Feature module."""

from dli.core.feature.models import (
    FeatureInfo,
    FeatureStatus,
)

__all__ = [
    "FeatureInfo",
    "FeatureStatus",
]
```

---

## 3. Client Method Pattern

### Adding to BasecampClient

```python
# In src/dli/core/client.py

def feature_list(
    self,
    status_filter: str | None = None,
) -> ServerResponse:
    """List features.

    Args:
        status_filter: Optional status filter

    Returns:
        ServerResponse with list of features
    """
    if self.mock_mode:
        features = self._mock_data.get("features", [])
        if status_filter:
            features = [f for f in features if f.get("status") == status_filter]
        return ServerResponse(success=True, data=features)

    # TODO: Implement actual HTTP call
    return ServerResponse(
        success=False,
        error="Real API not implemented yet",
        status_code=501,
    )
```

### Mock Data Setup

```python
# In _init_mock_data() method
def _init_mock_data(self) -> dict[str, list[dict[str, Any]]]:
    return {
        # ... existing data ...
        "features": [
            {
                "name": "feature_one",
                "status": "active",
                "created_at": "2024-01-01T00:00:00Z",
            },
            {
                "name": "feature_two",
                "status": "inactive",
                "created_at": "2024-01-02T00:00:00Z",
            },
        ],
    }
```

### Important: Check Existing Enums

Before creating new Enums, check `client.py` for existing definitions:
- `WorkflowSource` - code/manual
- `RunStatus` - workflow run statuses

If your feature uses similar concepts, **reuse existing enums** or ensure values match with `models.py`.

---

## 4. Test Patterns

### CLI Test Template

```python
"""Tests for feature CLI commands."""

import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


class TestFeatureList:
    """Tests for `dli feature list` command."""

    def test_list_default(self) -> None:
        """Test listing with default options."""
        result = runner.invoke(app, ["feature", "list"])
        assert result.exit_code == 0
        assert "Items" in result.output

    def test_list_json_format(self) -> None:
        """Test listing with JSON output."""
        result = runner.invoke(app, ["feature", "list", "--format", "json"])
        assert result.exit_code == 0
        # Should be valid JSON
        import json
        json.loads(result.output)

    def test_list_with_filter(self) -> None:
        """Test listing with filter option."""
        result = runner.invoke(app, ["feature", "list", "--status", "active"])
        assert result.exit_code == 0


class TestFeatureHelp:
    """Tests for help output."""

    def test_feature_help(self) -> None:
        """Test feature command help."""
        result = runner.invoke(app, ["feature", "--help"])
        assert result.exit_code == 0
        assert "Feature management" in result.output
```

### Model Test Template

```python
"""Tests for feature models."""

import pytest
from pydantic import ValidationError

from dli.core.feature.models import FeatureInfo, FeatureStatus


class TestFeatureStatus:
    """Tests for FeatureStatus enum."""

    def test_values(self) -> None:
        """Test enum values."""
        assert FeatureStatus.ACTIVE.value == "active"
        assert FeatureStatus.INACTIVE.value == "inactive"

    def test_string_conversion(self) -> None:
        """Test string conversion."""
        assert str(FeatureStatus.ACTIVE) == "active"


class TestFeatureInfo:
    """Tests for FeatureInfo model."""

    def test_minimal_creation(self) -> None:
        """Test creating with minimal fields."""
        info = FeatureInfo(name="test")
        assert info.name == "test"
        assert info.status == FeatureStatus.ACTIVE

    def test_is_active_property(self) -> None:
        """Test is_active convenience property."""
        active = FeatureInfo(name="test", status=FeatureStatus.ACTIVE)
        inactive = FeatureInfo(name="test", status=FeatureStatus.INACTIVE)
        assert active.is_active is True
        assert inactive.is_active is False

    def test_json_serialization(self) -> None:
        """Test JSON round-trip."""
        original = FeatureInfo(name="test", status=FeatureStatus.ACTIVE)
        json_str = original.model_dump_json()
        restored = FeatureInfo.model_validate_json(json_str)
        assert restored == original
```

### Client Test Template

```python
"""Tests for feature client methods."""

import pytest

from dli.core.client import BasecampClient, ServerConfig


@pytest.fixture
def mock_client() -> BasecampClient:
    """Create a mock client for testing."""
    config = ServerConfig(url="http://test:8080")
    return BasecampClient(config, mock_mode=True)


class TestFeatureList:
    """Tests for feature_list method."""

    def test_list_all(self, mock_client: BasecampClient) -> None:
        """Test listing all features."""
        response = mock_client.feature_list()
        assert response.success is True
        assert isinstance(response.data, list)

    def test_list_with_filter(self, mock_client: BasecampClient) -> None:
        """Test listing with status filter."""
        response = mock_client.feature_list(status_filter="active")
        assert response.success is True
        for item in response.data or []:
            assert item.get("status") == "active"
```

---

## 5. Common Utilities

### From `commands/base.py`

```python
from dli.commands.base import (
    ListOutputFormat,      # Literal["table", "json"]
    SourceType,            # Literal["local", "server"]
    get_client,            # Get BasecampClient instance
    get_project_path,      # Resolve project path
    format_tags_display,   # Format tags for table display
)
```

### From `commands/utils.py`

```python
from dli.commands.utils import (
    console,               # Rich Console instance
    parse_params,          # Parse key=value params
    print_error,           # Print error message (red)
    print_success,         # Print success message (green)
    print_warning,         # Print warning message (yellow)
    print_sql,             # Print formatted SQL
    print_validation_result,  # Print validation results
)
```

---

## 6. Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| CLI subcommand | `{feature}_app` | `workflow_app`, `config_app` |
| CLI command | kebab-case | `dli workflow list`, `dli config show` |
| Python function | snake_case | `list_workflows`, `show_config` |
| Model class | PascalCase | `WorkflowInfo`, `RunStatus` |
| Enum | PascalCase + UPPER values | `class Status(Enum): ACTIVE = "active"` |
| Test class | `Test{Feature}{Action}` | `TestWorkflowList`, `TestConfigShow` |
| Test file | `test_{feature}_cmd.py` | `test_workflow_cmd.py`, `test_config_cmd.py` |

---

## 7. Pre-Implementation Checklist

Before starting a new feature:

- [ ] Read `FEATURE_{NAME}.md` requirements fully
- [ ] Check `client.py` for existing enums/methods to reuse
- [ ] Review similar command (e.g., `dataset.py`) for patterns
- [ ] Plan model structure based on API requirements
- [ ] Identify test scenarios (happy path + error cases)

---

## 8. Deprecated/Removed Commands

| Command | Status | Migration |
|---------|--------|-----------|
| `dli render` | Removed (v1.0.0) | Use `dli dataset run --dry-run --show-sql` |
| `dli validate` (top-level) | Removed (v1.0.0) | Use `dli dataset validate` or `dli metric validate` |
| `dli server` | Renamed (v1.0.0) | Use `dli config` |

---

## 9. Library API Pattern

The Library API provides programmatic access to DLI functionality, designed for Airflow DAGs and external integrations.

### Architecture Overview

```
Library API Layer (src/dli/api/)
├── dataset.py        # DatasetAPI (Facade)
├── metric.py         # MetricAPI (Facade)
├── quality.py        # QualityAPI (Facade)
├── transpile.py      # TranspileAPI (Facade)
└── __init__.py       # Public exports

Shared Models (src/dli/models/)
├── common.py         # ExecutionContext, Result models
└── __init__.py       # Public exports

Exceptions (src/dli/exceptions.py)
└── Structured exception hierarchy with error codes
```

### QualityAPI Quick Example

```python
from dli import QualityAPI, ExecutionContext
from dli.models.common import ExecutionMode

# Create context (mock mode for testing)
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = QualityAPI(context=ctx)

# List quality specs from server
qualities = api.list_qualities(target_type="dataset")

# Run quality test by spec path
result = api.run("quality.iceberg.analytics.daily_clicks.yaml")
print(f"Status: {result.status}, Duration: {result.duration_ms}ms")

# Validate spec before execution
validation = api.validate("quality.iceberg.analytics.daily_clicks.yaml", strict=True)
if not validation.valid:
    print(f"Errors: {validation.errors}")
```

### WorkflowAPI Quick Example

```python
from dli import WorkflowAPI, ExecutionContext
from dli.models.common import ExecutionMode

# Create context
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = WorkflowAPI(context=ctx)

# Register a MANUAL workflow (creates DAG in Airflow)
register_result = api.register("iceberg.analytics.daily_clicks")
print(f"Registered: {register_result.dag_id}")

# Run workflow
run_result = api.run("iceberg.analytics.daily_clicks", execution_date="2025-01-01")
print(f"Run ID: {run_result.run_id}, Status: {run_result.status}")

# Backfill date range
backfill_result = api.backfill(
    "iceberg.analytics.daily_clicks",
    start_date="2025-01-01",
    end_date="2025-01-07"
)

# Get workflow status
status = api.get_status("iceberg.analytics.daily_clicks", run_id="run_123")
print(f"State: {status.state}")

# List workflows with filtering
workflows = api.list_workflows(source="manual", status="running")

# Unregister workflow (removes DAG from Airflow)
api.unregister("iceberg.analytics.daily_clicks")
```

### API Class Template (Facade Pattern)

```python
"""FeatureAPI - Library API for Feature operations.

Example:
    >>> from dli import FeatureAPI, ExecutionContext
    >>> ctx = ExecutionContext(project_path="/path/to/project")
    >>> api = FeatureAPI(context=ctx)
    >>> items = api.list_items()
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any

from dli.exceptions import (
    ConfigurationError,
    FeatureNotFoundError,
    ErrorCode,
    ExecutionError,
)
from dli.models.common import (
    DataSource,
    ExecutionContext,
    FeatureResult,
    ResultStatus,
    ValidationResult,
)

if TYPE_CHECKING:
    from dli.core.models.feature import FeatureSpec
    from dli.core.service import FeatureService


class FeatureAPI:
    """Feature management Library API.

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations (standard pattern for Kubernetes Airflow).

    Example:
        >>> from dli import FeatureAPI, ExecutionContext
        >>> ctx = ExecutionContext(
        ...     project_path="/opt/airflow/dags/models",
        ...     parameters={"execution_date": "2025-01-01"},
        ... )
        >>> api = FeatureAPI(context=ctx)
        >>> result = api.run("catalog.schema.feature", dry_run=True)
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """Initialize FeatureAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
        """
        self.context = context or ExecutionContext()
        self._service: FeatureService | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"FeatureAPI(context={self.context!r})"

    def _get_service(self) -> FeatureService:
        """Get or create FeatureService instance (lazy initialization).

        Returns:
            FeatureService instance.

        Raises:
            ConfigurationError: If project_path is required but not set.
        """
        if self._service is None:
            from dli.core.service import FeatureService as FeatureServiceImpl
            from dli.models.common import ExecutionMode

            project_path = self.context.project_path
            if project_path is None and self.context.execution_mode != ExecutionMode.MOCK:
                msg = "project_path is required for FeatureAPI"
                raise ConfigurationError(message=msg, code=ErrorCode.CONFIG_INVALID)

            self._service = FeatureServiceImpl(project_path=project_path)

        return self._service

    # === CRUD Operations ===

    def list_items(
        self,
        *,
        source: DataSource = "local",
        domain: str | None = None,
    ) -> list[FeatureSpec]:
        """List items with optional filtering.

        Args:
            source: Data source ("local" for disk, "server" for API).
            domain: Filter by domain.

        Returns:
            List of FeatureSpec objects.
        """
        from dli.models.common import ExecutionMode
        if self.context.execution_mode == ExecutionMode.MOCK:
            return []

        service = self._get_service()
        return service.list_items(domain=domain)

    def get(self, name: str) -> FeatureSpec | None:
        """Get item by name.

        Args:
            name: Fully qualified name (catalog.schema.name).

        Returns:
            FeatureSpec if found, None otherwise.
        """
        from dli.models.common import ExecutionMode
        if self.context.execution_mode == ExecutionMode.MOCK:
            return None

        service = self._get_service()
        return service.get_item(name)

    # === Execution ===

    def run(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
        show_sql: bool = False,
    ) -> FeatureResult:
        """Execute a feature.

        Args:
            name: Fully qualified name.
            parameters: Runtime parameters (merged with context.parameters).
            dry_run: If True, validate and render SQL without execution.
            show_sql: If True, include rendered SQL in result.

        Returns:
            FeatureResult with execution status and details.

        Raises:
            FeatureNotFoundError: If feature not found.
            DLIValidationError: If validation fails.
            ExecutionError: If execution fails.
        """
        started_at = datetime.now(tz=UTC)

        # Merge parameters
        merged_params = {**self.context.parameters, **(parameters or {})}

        from dli.models.common import ExecutionMode
        if self.context.execution_mode == ExecutionMode.MOCK:
            return FeatureResult(
                name=name,
                status=ResultStatus.SUCCESS,
                started_at=started_at,
                ended_at=datetime.now(tz=UTC),
                duration_ms=0,
                sql="-- Mock SQL" if show_sql else None,
            )

        try:
            service = self._get_service()

            # Check if exists
            spec = service.get_item(name)
            if spec is None:
                raise FeatureNotFoundError(
                    message=f"Feature '{name}' not found",
                    name=name,
                )

            # Use dry_run from context if not explicitly set
            actual_dry_run = dry_run or self.context.dry_run

            result = service.execute(name, merged_params, dry_run=actual_dry_run)
            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            return FeatureResult(
                name=name,
                status=ResultStatus.SUCCESS if result.success else ResultStatus.FAILURE,
                started_at=started_at,
                ended_at=ended_at,
                duration_ms=duration_ms,
                error_message=result.error_message,
            )

        except FeatureNotFoundError:
            raise
        except Exception as e:
            ended_at = datetime.now(tz=UTC)
            duration_ms = int((ended_at - started_at).total_seconds() * 1000)

            raise ExecutionError(
                message=f"Feature execution failed: {e}",
                cause=e,
            ) from e

    # === Validation ===

    def validate(
        self,
        name: str,
        *,
        strict: bool = False,
    ) -> ValidationResult:
        """Validate spec and SQL.

        Args:
            name: Fully qualified name.
            strict: If True, treat warnings as errors.

        Returns:
            ValidationResult with validation status.
        """
        from dli.models.common import ExecutionMode
        if self.context.execution_mode == ExecutionMode.MOCK:
            return ValidationResult(valid=True)

        try:
            service = self._get_service()

            spec = service.get_item(name)
            if spec is None:
                return ValidationResult(
                    valid=False,
                    errors=[f"Feature '{name}' not found"],
                )

            results = service.validate(name, self.context.parameters)
            errors: list[str] = []
            warnings: list[str] = []

            for result in results:
                errors.extend(result.errors)
                warnings.extend(result.warnings)

            if strict and warnings:
                errors.extend(warnings)
                warnings = []

            return ValidationResult(
                valid=len(errors) == 0,
                errors=errors,
                warnings=warnings,
            )

        except Exception as e:
            return ValidationResult(valid=False, errors=[str(e)])


__all__ = ["FeatureAPI"]
```

### ExecutionMode

`ExecutionMode` determines where SQL queries are executed:

```python
from dli.models.common import ExecutionMode

class ExecutionMode(str, Enum):
    LOCAL = "local"    # Direct Query Engine execution
    SERVER = "server"  # Via Basecamp Server API
    MOCK = "mock"      # Test mode (no execution)
```

| Mode | Use Case |
|------|----------|
| `LOCAL` | Production: direct BigQuery/Trino execution |
| `SERVER` | Centralized: execution via Basecamp Server |
| `MOCK` | Testing: unit tests, CI/CD pipelines |

### ExecutionContext Usage

```python
from dli import ExecutionContext
from dli.models.common import ExecutionMode

# Default: loads from environment variables (DLI_* prefix)
ctx = ExecutionContext()

# Explicit configuration (recommended)
ctx = ExecutionContext(
    project_path=Path("/opt/airflow/dags/models"),
    server_url="https://basecamp.example.com",
    execution_mode=ExecutionMode.LOCAL,  # LOCAL, SERVER, or MOCK
    timeout=300,                          # seconds (default: 300)
    dry_run=False,
    dialect="trino",
    parameters={"execution_date": "2025-01-01"},
    verbose=True,
)

# Mock mode for testing
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

# Key attributes
ctx.project_path     # Path | None - project root for local specs
ctx.server_url       # str | None - Basecamp server URL
ctx.execution_mode   # ExecutionMode - query execution location
ctx.timeout          # int - execution timeout in seconds
ctx.dry_run          # bool - dry-run mode (no actual execution)
ctx.dialect          # SQLDialect - default SQL dialect
ctx.parameters       # dict[str, Any] - Jinja template parameters
```

**Deprecation:** `mock_mode` parameter is deprecated. Use `execution_mode=ExecutionMode.MOCK`.

### Dependency Injection Pattern

For advanced testing, inject custom executors:

```python
from dli import DatasetAPI, ExecutionContext
from dli.core.executor import MockExecutor
from dli.models.common import ExecutionMode

# Create context
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

# Inject custom executor
mock_executor = MockExecutor(mock_data=[{"id": 1, "value": 100}])
api = DatasetAPI(context=ctx, executor=mock_executor)

result = api.run("my_dataset")
```

### Exception Handling Pattern

```python
from dli import DatasetAPI, ExecutionContext
from dli.exceptions import (
    DLIError,
    ConfigurationError,
    DatasetNotFoundError,
    DLIValidationError,
    ExecutionError,
    ErrorCode,
)

ctx = ExecutionContext(project_path=Path("/path/to/project"))
api = DatasetAPI(context=ctx)

try:
    result = api.run("catalog.schema.dataset")
except DatasetNotFoundError as e:
    # Access structured error info
    print(f"Error code: {e.code.value}")  # "DLI-101"
    print(f"Dataset: {e.name}")
    print(f"Searched: {e.searched_paths}")
except DLIValidationError as e:
    print(f"Validation errors: {e.errors}")
    print(f"Warnings: {e.warnings}")
except ExecutionError as e:
    print(f"Execution failed: {e.message}")
    if e.cause:
        print(f"Caused by: {e.cause}")
except DLIError as e:
    # Catch any DLI error
    print(f"[{e.code.value}] {e.message}")
```

### Error Code Reference

| Code | Category | Exception |
|------|----------|-----------|
| DLI-0xx | Configuration | `ConfigurationError` |
| DLI-1xx | Not Found | `DatasetNotFoundError`, `MetricNotFoundError` |
| DLI-2xx | Validation | `DLIValidationError` |
| DLI-3xx | Transpile | `TranspileError` |
| DLI-4xx | Execution | `ExecutionError` |
| DLI-41x | Run | `RunFileNotFoundError`, `RunLocalDeniedError`, etc. |
| DLI-5xx | Server | `ServerError` |
| DLI-6xx | Quality | `QualitySpecNotFoundError`, `QualityNotFoundError` |
| DLI-7xx | Catalog | `CatalogError`, `CatalogNotFoundError` |
| DLI-78x | Query | `QueryNotFoundError`, `QueryAccessDeniedError`, `QueryCancelError`, `QueryInvalidFilterError` |
| DLI-8xx | Workflow | `WorkflowNotFoundError`, `WorkflowRegistrationError`, `WorkflowExecutionError`, `WorkflowPermissionError` |

### Module Exports (`__init__.py`)

```python
"""DLI API module exports."""

from dli.api.dataset import DatasetAPI
from dli.api.metric import MetricAPI
from dli.api.quality import QualityAPI
from dli.models.common import ExecutionContext, ResultStatus, ValidationResult

__all__ = [
    "DatasetAPI",
    "ExecutionContext",
    "MetricAPI",
    "QualityAPI",
    "ResultStatus",
    "ValidationResult",
]
```

---

## 10. API Test Patterns

### API Test Template

```python
"""Tests for dli.api.feature module.

Covers:
- FeatureAPI initialization with context
- Mock mode operations
- CRUD operations (list, get)
- Execution (run)
- Validation
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

import pytest

from dli import FeatureAPI, ExecutionContext
from dli.exceptions import ConfigurationError
from dli.models.common import ResultStatus, ValidationResult


class TestFeatureAPIInit:
    """Tests for FeatureAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = FeatureAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        from dli.models.common import ExecutionMode
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = FeatureAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_project_path(self) -> None:
        """Test initialization with project path."""
        ctx = ExecutionContext(project_path=Path("/test/project"))
        api = FeatureAPI(context=ctx)

        assert api.context.project_path == Path("/test/project")

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", mock_mode=True)
        api = FeatureAPI(context=ctx)

        result = repr(api)

        assert "FeatureAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_service_init(self) -> None:
        """Test that service is not created until needed."""
        from dli.models.common import ExecutionMode
        api = FeatureAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _service should be None before any operation
        assert api._service is None


class TestFeatureAPIMockMode:
    """Tests for FeatureAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> FeatureAPI:
        """Create FeatureAPI in mock mode."""
        from dli.models.common import ExecutionMode
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return FeatureAPI(context=ctx)

    def test_list_returns_empty(self, mock_api: FeatureAPI) -> None:
        """Test list returns empty list in mock mode."""
        result = mock_api.list_items()

        assert result == []

    def test_get_returns_none(self, mock_api: FeatureAPI) -> None:
        """Test get returns None in mock mode."""
        result = mock_api.get("catalog.schema.feature")

        assert result is None

    def test_run_returns_success(self, mock_api: FeatureAPI) -> None:
        """Test run returns success result in mock mode."""
        result = mock_api.run("catalog.schema.feature")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "catalog.schema.feature"
        assert result.duration_ms == 0

    def test_run_with_show_sql(self, mock_api: FeatureAPI) -> None:
        """Test run with show_sql flag in mock mode."""
        result = mock_api.run("my_feature", show_sql=True)

        assert result.sql == "-- Mock SQL"

    def test_validate_returns_valid(self, mock_api: FeatureAPI) -> None:
        """Test validate returns valid in mock mode."""
        result = mock_api.validate("my_feature")

        assert result.valid is True
        assert result.errors == []


class TestFeatureAPIRun:
    """Tests for FeatureAPI.run method."""

    @pytest.fixture
    def mock_api(self) -> FeatureAPI:
        """Create FeatureAPI in mock mode."""
        from dli.models.common import ExecutionMode
        return FeatureAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

    def test_run_basic(self, mock_api: FeatureAPI) -> None:
        """Test basic run execution."""
        result = mock_api.run("catalog.schema.feature")

        assert result.name == "catalog.schema.feature"
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.started_at, datetime)

    def test_run_with_parameters(self, mock_api: FeatureAPI) -> None:
        """Test run with parameters."""
        result = mock_api.run(
            "my_feature",
            parameters={"date": "2025-01-01", "limit": 100},
        )

        assert result.status == ResultStatus.SUCCESS

    def test_run_with_dry_run(self, mock_api: FeatureAPI) -> None:
        """Test run with dry_run flag."""
        result = mock_api.run("my_feature", dry_run=True)

        assert result.status == ResultStatus.SUCCESS

    def test_run_context_parameters_merged(self) -> None:
        """Test that context parameters are merged with run parameters."""
        from dli.models.common import ExecutionMode
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            parameters={"env": "prod", "date": "default"},
        )
        api = FeatureAPI(context=ctx)

        result = api.run("my_feature", parameters={"date": "2025-01-01"})

        assert result.status == ResultStatus.SUCCESS


class TestFeatureAPIConfiguration:
    """Tests for FeatureAPI configuration requirements."""

    def test_requires_project_path_in_non_mock_mode(self) -> None:
        """Test that project_path is required in non-mock mode."""
        ctx = ExecutionContext(mock_mode=False, project_path=None)
        api = FeatureAPI(context=ctx)

        with pytest.raises(ConfigurationError) as exc_info:
            api._get_service()

        assert "project_path is required" in exc_info.value.message

    def test_project_path_not_required_in_mock_mode(self) -> None:
        """Test that project_path is not required in mock mode."""
        ctx = ExecutionContext(mock_mode=True, project_path=None)
        api = FeatureAPI(context=ctx)

        result = api.list_items()
        assert result == []


class TestFeatureAPIValidation:
    """Tests for FeatureAPI.validate method."""

    @pytest.fixture
    def mock_api(self) -> FeatureAPI:
        """Create FeatureAPI in mock mode."""
        return FeatureAPI(context=ExecutionContext(mock_mode=True))

    def test_validate_basic(self, mock_api: FeatureAPI) -> None:
        """Test basic validation."""
        result = mock_api.validate("my_feature")

        assert isinstance(result, ValidationResult)
        assert result.valid is True

    def test_validate_with_strict(self, mock_api: FeatureAPI) -> None:
        """Test validation with strict mode."""
        result = mock_api.validate("my_feature", strict=True)

        assert result.valid is True
```

### API Test Fixture Pattern

```python
@pytest.fixture
def mock_api() -> FeatureAPI:
    """Create FeatureAPI in mock mode for testing."""
    ctx = ExecutionContext(mock_mode=True)
    return FeatureAPI(context=ctx)


@pytest.fixture
def configured_api(tmp_path: Path) -> FeatureAPI:
    """Create FeatureAPI with real project path for integration tests."""
    # Create minimal project structure
    spec_dir = tmp_path / "specs"
    spec_dir.mkdir()

    ctx = ExecutionContext(
        project_path=tmp_path,
        mock_mode=False,
    )
    return FeatureAPI(context=ctx)
```

### Test Organization

| Test File | Coverage |
|-----------|----------|
| `tests/api/test_feature_api.py` | API class tests |
| `tests/api/test_common_models.py` | ExecutionContext, Result models |
| `tests/api/test_exceptions.py` | Exception hierarchy tests |

---

## 11. Environment Management Pattern (v0.7.0)

### ConfigLoader Usage

```python
from dli.core.config_loader import ConfigLoader

loader = ConfigLoader(project_path=Path("."))
config, sources = loader.load()  # Returns (merged_config, source_map)
```

### ExecutionContext.from_environment()

```python
from dli import ExecutionContext
from pathlib import Path

# Load from layered config with named environment
ctx = ExecutionContext.from_environment(
    project_path=Path("/opt/airflow/dags/models"),
    environment="prod",  # From dli.yaml environments section
)
```

### Template Syntax Reference

| Syntax | Description | Example |
|--------|-------------|---------|
| `${VAR}` | Required (fails if missing) | `${DLI_API_KEY}` |
| `${VAR:-default}` | With fallback | `${DLI_TIMEOUT:-300}` |

### Config File Hierarchy

```
~/.dli/config.yaml      # Global defaults (Priority 4)
dli.yaml                 # Project config (Priority 3)
.dli.local.yaml          # Local overrides (Priority 2, gitignored)
DLI_* env vars           # Environment (Priority 1)
```

**Details:** See [features/ENV_FEATURE.md](../features/ENV_FEATURE.md)

---

## Related Documents

- [CONTRIBUTING.md](../CONTRIBUTING.md) - Contribution guidelines
- [README.md](../README.md) - CLI documentation
- [features/](../features/) - Feature specifications
- [features/MODEL_FEATURE.md](../features/MODEL_FEATURE.md) - MODEL abstraction spec
- [features/ENV_FEATURE.md](../features/ENV_FEATURE.md) - Environment Management spec
