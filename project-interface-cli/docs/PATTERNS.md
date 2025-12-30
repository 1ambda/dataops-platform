# DLI CLI Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.
> **Last Updated:** 2025-12-30

---

## CLI Command Structure

```
dli
├── version / info              # Basic info commands
├── config (show, status)       # Configuration management
├── metric (list, get, run, validate, register)
├── dataset (list, get, run, validate, register)
├── workflow (run, backfill, stop, status, list, history, pause, unpause)
├── quality (list, run, show)
├── lineage (show, upstream, downstream)   # Top-level
├── catalog                                # Top-level
└── transpile                              # Top-level
```

---

## Quick Reference

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| New CLI command | `src/dli/commands/dataset.py` | Typer subcommand app |
| Config subcommand | `src/dli/commands/config.py` | Simple settings management |
| Data models | `src/dli/core/workflow/models.py` | Pydantic BaseModel |
| Client methods | `src/dli/core/client.py` | Mock + ServerResponse |
| CLI tests | `tests/cli/test_dataset_cmd.py` | CliRunner |
| Model tests | `tests/core/workflow/test_models.py` | pytest + Pydantic |

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

## Related Documents

- [CONTRIBUTING.md](../CONTRIBUTING.md) - Contribution guidelines
- [README.md](../README.md) - CLI documentation
- [features/](../features/) - Feature specifications
- [features/FEATURE_MODEL.md](../features/FEATURE_MODEL.md) - MODEL abstraction spec
