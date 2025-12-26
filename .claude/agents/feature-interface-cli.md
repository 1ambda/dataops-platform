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

## Token Efficiency (MCP-First)

ALWAYS use MCP tools before file reads:
- `serena.get_symbols_overview("src/dataops_cli/...")` - understand CLI structure
- `serena.search_for_pattern("@app.command")` - find existing commands
- `context7.get-library-docs("/tiangolo/typer")` - Typer best practices
- `context7.get-library-docs("/encode/httpx", "async")` - httpx async patterns

## When to Use Skills

- **code-search**: Explore existing command patterns
- **testing**: Write tests for CLI behavior
- **refactoring**: Improve command structure
- **debugging**: Trace CLI errors

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Check Typer/Rich docs if complex.
3. **TDD**: Write test → implement → refine. `uv run pytest` must pass.
4. **Document**: Update relevant docs (README, --help text) when behavior changes.
5. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Project Structure

```
project-interface-cli/
├── src/dataops_cli/
│   ├── __init__.py          # Package initialization with version
│   ├── main.py              # Typer CLI (@app.command decorators)
│   │   - version, health, pipelines, sql-parse, config commands
│   ├── config.py            # CliConfig (Pydantic, JSON persistence)
│   ├── exceptions.py        # CliException, ApiError, ConfigError
│   └── logging_config.py    # Rich logging setup
├── tests/
│   └── test_main.py         # CLI tests with CliRunner
├── build_standalone.py      # PyInstaller build script
├── main.py                  # Entry point
└── pyproject.toml           # Project configuration (uv)
```

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
| Testing | pytest + pytest-asyncio |

---

## Typer Command Patterns

```python
import typer
from rich.console import Console
from rich.table import Table

app = typer.Typer(name="dli", help="DataOps CLI")
console = Console()

@app.command()
def health(url: str = typer.Option(None, "--url", "-u", help="Server URL")) -> None:
    """Check the health of the DataOps server."""
    try:
        config = CliConfig.load()
        result = check_health(url or config.base_url)
        console.print(f"[green]Server healthy:[/green] {result.status}")
    except ApiError as e:
        console.print(f"[red]Error:[/red] {e.message}")
        raise typer.Exit(1)
```

### Async Command with httpx
```python
import asyncio
import httpx

async def fetch_pipelines(url: str, timeout: int = 30) -> list[Pipeline]:
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.get(f"{url}/api/pipelines")
        response.raise_for_status()
        return [Pipeline(**p) for p in response.json()]

@app.command()
def pipelines(url: str = typer.Option(None, "--url", "-u")) -> None:
    """List all pipelines."""
    try:
        result = asyncio.run(fetch_pipelines(url or CliConfig.load().base_url))
        display_table(result)
    except httpx.HTTPError as e:
        console.print(f"[red]HTTP Error:[/red] {e}")
        raise typer.Exit(1)
```

### Rich Output Formatting
```python
def display_table(pipelines: list[Pipeline]) -> None:
    table = Table(title="Pipelines")
    table.add_column("ID", style="cyan")
    table.add_column("Name", style="magenta")
    table.add_column("Status", style="green")
    for p in pipelines:
        table.add_row(str(p.id), p.name, p.status)
    console.print(table)
```

---

## Configuration Management

```python
from pathlib import Path
from pydantic import BaseModel
import json

CONFIG_FILE = Path.home() / ".dli" / "config.json"

class CliConfig(BaseModel):
    base_url: str = "http://localhost:8080"
    timeout: int = 30

    @classmethod
    def load(cls) -> "CliConfig":
        if CONFIG_FILE.exists():
            return cls(**json.loads(CONFIG_FILE.read_text()))
        return cls()

    def save(self) -> None:
        CONFIG_FILE.parent.mkdir(parents=True, exist_ok=True)
        CONFIG_FILE.write_text(self.model_dump_json(indent=2))
```

## Implementation Order

1. **Configuration** (src/dataops_cli/config.py) - `CliConfig`
2. **Exceptions** (src/dataops_cli/exceptions.py) - `CliException`, `ApiError`
3. **Command Logic** (src/dataops_cli/main.py) - `@app.command()` decorators

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Commands (CLI) | kebab-case | `sql-parse`, `list-pipelines` |
| Functions | snake_case | `sql_parse`, `list_pipelines` |
| Classes | PascalCase | `CliConfig`, `ApiError` |
| Module Files | snake_case | `main.py`, `config.py` |

## Anti-Patterns to Avoid

- Blocking operations for API calls (use async with httpx)
- Cryptic error messages without actionable hints
- Missing `--help` text on commands and options
- Ignoring exit codes (exit non-zero on error)
- Not using Rich for terminal output

## Quality Checklist

- [ ] `uv run pytest` - all tests pass
- [ ] `uv run pyright src/` - no type errors
- [ ] `--help` output is clear and complete
- [ ] Error messages include hints for resolution
- [ ] Async used for HTTP operations
- [ ] Exit codes: 0 for success, 1 for errors

## Essential Commands

```bash
uv sync                                    # Install dependencies
uv run python -m dataops_cli --help        # Show help
uv run python -m dataops_cli health        # Check server health
uv run python -m dataops_cli pipelines     # List pipelines
uv run python -m dataops_cli sql-parse "SELECT * FROM users"
uv run pytest                              # Run tests
uv run ruff format && uv run ruff check --fix  # Format and lint
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DLI_LOG_LEVEL` | Logging level | INFO |
| `DLI_CONFIG_FILE` | Config file path | ~/.dli/config.json |
