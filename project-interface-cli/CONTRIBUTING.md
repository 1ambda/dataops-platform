# Contributing to DLI CLI

> **Last Updated:** 2025-12-30

---

## Table of Contents

- [Development Environment](#development-environment)
- [Project Structure](#project-structure)
- [Code Style](#code-style)
- [Testing](#testing)
- [Adding New Features](#adding-new-features)
- [Commit Conventions](#commit-conventions)

---

## Development Environment

### Prerequisites

- Python 3.12+
- [uv](https://docs.astral.sh/uv/) package manager

### Setup

```bash
cd project-interface-cli

# Install dependencies (with dev group)
uv sync --all-groups

# Verify installation
uv run dli version
```

### IDE Configuration

| IDE | Setup |
|-----|-------|
| **VSCode** | Install Python, Pylance, Ruff extensions |
| **PyCharm** | Enable Ruff plugin, set interpreter to `.venv/bin/python` |

---

## Project Structure

```
src/dli/
├── main.py                  # Entry point, subcommand registration
├── commands/                # CLI layer (Typer)
│   ├── __init__.py          # Export all *_app
│   ├── base.py              # get_client, get_project_path, ListOutputFormat
│   ├── utils.py             # console, print_error, print_success
│   └── {feature}.py         # Subcommand implementations
├── core/                    # Business logic
│   ├── client.py            # BasecampClient (mock + real API)
│   ├── models/              # Shared Pydantic models
│   └── {module}/            # Feature modules (workflow, catalog, etc.)
└── api/                     # Library API (programmatic access)

tests/
├── cli/                     # CLI command tests
├── core/                    # Core module tests
└── api/                     # Library API tests
```

---

## Code Style

### Tools

| Tool | Purpose | Command |
|------|---------|---------|
| **ruff** | Lint + format | `uv run ruff check . --fix && uv run ruff format .` |
| **pyright** | Type check | `uv run pyright` |

### Pre-commit (optional)

```bash
uv run pre-commit install
uv run pre-commit run --all-files
```

### Style Guidelines

- [ ] Type hints on all function signatures
- [ ] Docstrings on public functions/classes
- [ ] `from __future__ import annotations` at file top
- [ ] Line length: 88 characters

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| CLI subcommand app | `{feature}_app` | `workflow_app` |
| CLI command | kebab-case | `dli workflow list` |
| Python function | snake_case | `list_workflows` |
| Model class | PascalCase | `WorkflowInfo` |
| Enum | PascalCase + UPPER | `Status.ACTIVE` |
| Test class | `Test{Feature}{Action}` | `TestWorkflowList` |
| Test file | `test_{feature}_cmd.py` | `test_workflow_cmd.py` |

---

## Testing

### Commands

```bash
# All tests with coverage
uv run pytest

# Specific test
uv run pytest tests/cli/test_workflow_cmd.py -v

# Fast (no coverage)
uv run pytest --no-cov
```

### Coverage

- Target: 80%+
- Report: `htmlcov/index.html`

---

## Adding New Features

### Pre-Implementation Checklist

- [ ] Check `client.py` for existing enums/methods to reuse
- [ ] Review similar command for patterns (`dataset.py`, `workflow.py`)
- [ ] Plan model structure and test scenarios

---

### A. Adding a CLI Command

#### Step 1: Create Command

`src/dli/commands/{feature}.py`:

```python
"""Feature subcommand for DLI CLI."""
from __future__ import annotations

from typing import Annotated
import typer

from dli.commands.base import ListOutputFormat, get_client
from dli.commands.utils import console, print_error

feature_app = typer.Typer(
    name="feature",
    help="Feature management commands.",
    no_args_is_help=True,
)

@feature_app.command("list")
def list_items(
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f"),
    ] = "table",
) -> None:
    """List items."""
    # Implementation...
```

#### Step 2: Register

**`commands/__init__.py`:**
```python
from dli.commands.feature import feature_app
__all__ = [..., "feature_app"]
```

**`main.py`:**
```python
from dli.commands import feature_app
app.add_typer(feature_app, name="feature")
```

#### Step 3: Add Client Method (if needed)

`src/dli/core/client.py`:
```python
def feature_list(self) -> ServerResponse:
    if self.mock_mode:
        return ServerResponse(success=True, data=self._mock_data.get("features", []))
    return ServerResponse(success=False, error="Not implemented", status_code=501)
```

#### Step 4: Write Tests

`tests/cli/test_feature_cmd.py`:
```python
from typer.testing import CliRunner
from dli.main import app

runner = CliRunner()

class TestFeatureList:
    def test_list_default(self) -> None:
        result = runner.invoke(app, ["feature", "list"])
        assert result.exit_code == 0
```

#### CLI Command Checklist

- [ ] Export in `commands/__init__.py`
- [ ] Register in `main.py` with `app.add_typer()`
- [ ] Update `main.py` docstring Commands list
- [ ] Add mock data in `client.py` (if mock mode)
- [ ] Write tests in `tests/cli/`

---

### B. Adding a Library API

Library APIs provide programmatic Python access.

#### Step 1: Create API Module

`src/dli/api/{feature}.py`:

```python
"""Feature API for programmatic access."""
from __future__ import annotations

from dli.core.client import BasecampClient, ServerConfig

class FeatureAPI:
    """High-level API for feature operations."""

    def __init__(self, config: ServerConfig | None = None) -> None:
        self._client = BasecampClient(config or ServerConfig())

    def list(self) -> list[dict]:
        """List all features."""
        response = self._client.feature_list()
        if not response.success:
            raise RuntimeError(response.error)
        return response.data or []
```

#### Step 2: Export

`src/dli/api/__init__.py`:
```python
from dli.api.feature import FeatureAPI
__all__ = [..., "FeatureAPI"]
```

#### Step 3: Write Tests

`tests/api/test_feature_api.py`:
```python
from dli.api.feature import FeatureAPI

class TestFeatureAPI:
    def test_list(self) -> None:
        api = FeatureAPI()
        result = api.list()
        assert isinstance(result, list)
```

#### Library API Checklist

- [ ] Create API class with type hints
- [ ] Export in `api/__init__.py`
- [ ] Write tests in `tests/api/`
- [ ] Add docstrings with usage examples

---

### C. Adding Data Models

`src/dli/core/{feature}/models.py`:

```python
"""Feature data models."""
from __future__ import annotations

from enum import Enum
from pydantic import BaseModel, Field

__all__ = ["FeatureStatus", "FeatureInfo"]

class FeatureStatus(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"

class FeatureInfo(BaseModel):
    name: str = Field(..., description="Feature name")
    status: FeatureStatus = Field(default=FeatureStatus.ACTIVE)
```

**Model Checklist:**

- [ ] Use `Field()` with descriptions
- [ ] Use `default_factory` for mutable defaults (not `default=[]`)
- [ ] Export in module `__init__.py`

---

## Commit Conventions

### Format

```
<type>: <subject>
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation |
| `refactor` | Code refactoring |
| `test` | Tests |
| `chore` | Build, CI, deps |

### Examples

```bash
feat: Add workflow pause/unpause commands
fix: Handle empty response in catalog list
docs: Update CLI command reference
test: Add coverage for workflow backfill
```

### Guidelines

- Subject: 50 chars max, imperative mood
- Keep commits focused on single change
- Include tests with feature/fix commits

---

## Shared Utilities Reference

### From `commands/base.py`

| Utility | Type |
|---------|------|
| `ListOutputFormat` | `Literal["table", "json"]` |
| `get_client(path)` | Returns `BasecampClient` |
| `get_project_path(path)` | Resolves project path |

### From `commands/utils.py`

| Utility | Purpose |
|---------|---------|
| `console` | Rich Console instance |
| `print_error(msg)` | Error message (red) |
| `print_success(msg)` | Success message (green) |
| `print_warning(msg)` | Warning message (yellow) |
| `format_datetime(dt)` | Format datetime |
| `parse_params(["k=v"])` | Parse CLI params |

---

## Related Documentation

- [docs/PATTERNS.md](./docs/PATTERNS.md) - Detailed development patterns
- [README.md](./README.md) - CLI usage and installation
- [features/](./features/) - Feature specifications
