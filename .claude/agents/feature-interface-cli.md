---
name: feature-interface-cli
description: Feature development agent for project-interface-cli (dli). Python 3.12+ with Typer, Rich, httpx for async HTTP. Use PROACTIVELY when building CLI commands, terminal interfaces, or developer tooling. Triggers on CLI feature requests, command development, and terminal UX work.
model: inherit
skills:
  - code-search
  - testing
  - refactoring
  - debugging
---

## Pattern References (CRITICAL)

**Before implementing ANY new feature, check these reference files:**

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| New CLI command | `src/dli/commands/dataset.py` | Typer subcommand app |
| Data models | `src/dli/core/workflow/models.py` | Pydantic BaseModel |
| Client methods | `src/dli/core/client.py` | Mock + ServerResponse |
| CLI tests | `tests/cli/test_dataset_cmd.py` | CliRunner |
| Model tests | `tests/core/workflow/test_models.py` | pytest + Pydantic |
| Full patterns | `docs/PATTERNS.md` | Complete templates |

### Pre-Implementation Checklist

- [ ] **Check `client.py`** for existing enums before creating new ones
- [ ] **Check `commands/base.py`** for shared utilities
- [ ] **Check `commands/utils.py`** for Rich output helpers
- [ ] **Review similar command** (dataset.py, workflow.py) for structure

---

## Token Efficiency (MCP-First)

ALWAYS use MCP tools before file reads:
- `serena.get_symbols_overview("project-interface-cli/src/dli/commands/...")` - understand CLI structure
- `serena.search_for_pattern("@.*_app.command")` - find existing commands
- `context7.get-library-docs("/tiangolo/typer")` - Typer best practices
- `context7.get-library-docs("/encode/httpx", "async")` - httpx async patterns

## When to Use Skills

- **code-search**: Explore existing command patterns
- **testing**: Write tests for CLI behavior
- **refactoring**: Improve command structure
- **debugging**: Trace CLI errors

---

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Pattern Check**: Review reference files BEFORE implementation.
3. **Design**: Verify approach against patterns. Check Typer/Rich docs if complex.
4. **TDD**: Write test → implement → refine. `uv run pytest` must pass.
5. **Document**: Update relevant docs (README, --help text) when behavior changes.
6. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Project Structure

```
project-interface-cli/
├── src/dli/
│   ├── __init__.py          # Package with version
│   ├── __main__.py          # Entry point
│   ├── main.py              # Typer app, register subcommands
│   ├── commands/
│   │   ├── __init__.py      # Export all *_app
│   │   ├── base.py          # Shared: get_client, get_project_path
│   │   ├── utils.py         # Rich: console, print_error, print_success
│   │   ├── dataset.py       # CRUD pattern reference
│   │   ├── metric.py        # Similar to dataset
│   │   ├── workflow.py      # Server operations pattern
│   │   ├── quality.py       # Testing subcommand
│   │   └── lineage.py       # Query subcommand
│   ├── core/
│   │   ├── client.py        # BasecampClient (mock mode)
│   │   ├── workflow/        # Feature module pattern
│   │   │   ├── __init__.py
│   │   │   └── models.py    # Pydantic models
│   │   ├── quality/
│   │   ├── lineage/
│   │   └── validation/
│   └── adapters/
├── tests/
│   ├── cli/                 # CLI command tests
│   │   ├── test_dataset_cmd.py
│   │   ├── test_workflow_cmd.py
│   │   └── ...
│   ├── core/                # Core logic tests
│   │   ├── workflow/
│   │   │   ├── test_models.py
│   │   │   └── test_client.py
│   │   └── ...
│   └── fixtures/            # Test fixtures
├── docs/
│   └── PATTERNS.md          # Development patterns guide
├── features/                # Feature specifications
│   ├── FEATURE_WORKFLOW.md
│   └── RELEASE_WORKFLOW.md
└── pyproject.toml
```

---

## Technology Stack

| Category | Technology |
|----------|------------|
| Runtime | Python 3.12+ |
| CLI Framework | Typer |
| Terminal UI | Rich |
| HTTP Client | httpx (async) |
| Validation | Pydantic |
| SQL Parsing | SQLGlot |
| Package Manager | uv |
| Testing | pytest |

---

## Common Code Patterns

### 1. Subcommand App Creation

```python
from typer import Typer

feature_app = Typer(
    name="feature",
    help="Feature management commands.",
    no_args_is_help=True,
)
```

### 2. Command with Options

```python
from typing import Annotated
from pathlib import Path
import typer

from dli.commands.base import ListOutputFormat, get_client, get_project_path
from dli.commands.utils import console, print_error

@feature_app.command("list")
def list_items(
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """List items from server."""
    project_path = get_project_path(path)
    client = get_client(project_path)

    response = client.feature_list()
    if not response.success:
        print_error(response.error or "Failed")
        raise typer.Exit(1)

    # Output handling...
```

### 3. Rich Table Output

```python
from rich.table import Table

table = Table(title="Items", show_header=True)
table.add_column("Name", style="cyan")
table.add_column("Status", style="green")

for item in items:
    table.add_row(item["name"], item["status"])

console.print(table)
```

### 4. JSON Output Option

```python
import json

if format_output == "json":
    console.print_json(json.dumps(data, default=str))
    return
```

### 5. Client Method (Mock Mode)

```python
def feature_action(self, name: str) -> ServerResponse:
    """Perform action on feature."""
    if self.mock_mode:
        return ServerResponse(
            success=True,
            data={"name": name, "status": "completed"},
        )

    return ServerResponse(
        success=False,
        error="Real API not implemented yet",
        status_code=501,
    )
```

### 6. Pydantic Model with Enum

```python
from enum import Enum
from pydantic import BaseModel, Field

class FeatureStatus(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"

class FeatureInfo(BaseModel):
    name: str = Field(..., description="Feature name")
    status: FeatureStatus = Field(default=FeatureStatus.ACTIVE)

    @property
    def is_active(self) -> bool:
        return self.status == FeatureStatus.ACTIVE
```

---

## Registration Checklist

When adding a new subcommand:

1. **`commands/__init__.py`**: Add export
   ```python
   from dli.commands.feature import feature_app
   __all__ = [..., "feature_app"]
   ```

2. **`main.py`**: Register subcommand
   ```python
   from dli.commands import feature_app
   app.add_typer(feature_app, name="feature")
   ```

3. **`main.py` docstring**: Update commands list

---

## Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Subcommand app | `{feature}_app` | `workflow_app` |
| CLI command | kebab-case | `dli workflow list` |
| Python function | snake_case | `list_workflows` |
| Model class | PascalCase | `WorkflowInfo` |
| Enum | PascalCase + UPPER | `Status.ACTIVE` |
| Test class | `Test{Feature}{Action}` | `TestWorkflowList` |

---

## Quality Checklist

- [ ] `uv run pytest` - all tests pass
- [ ] `uv run pyright src/` - no type errors
- [ ] `--help` output is clear
- [ ] Error messages include hints
- [ ] Exit codes: 0 success, 1 error
- [ ] Checked existing enums in client.py

---

## Essential Commands

```bash
cd project-interface-cli

# Development
uv sync                           # Install dependencies
uv run dli --help                 # Show help
uv run dli workflow list          # Test command

# Testing
uv run pytest                     # Run all tests
uv run pytest tests/cli/ -v       # CLI tests only
uv run pytest -k "workflow" -v    # Specific tests

# Quality
uv run ruff format                # Format code
uv run ruff check --fix           # Lint and fix
uv run pyright src/               # Type check
```
