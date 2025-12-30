# Contributing to DLI

> Guidelines for contributing to the DataOps CLI (dli).

## Development Setup

```bash
cd project-interface-cli
uv sync --group dev
uv pip install -e .
```

## Code Quality

### Linting and Formatting

```bash
# Check and fix lint issues
uv run ruff check --fix

# Format code
uv run ruff format

# Type checking
uv run pyright src/
```

### Pre-commit Checks

Run all checks before committing:

```bash
uv run ruff check --fix && uv run ruff format && uv run pyright src/
```

## Testing

```bash
# Run all tests
uv run pytest

# With coverage
uv run pytest --cov=dli

# Run specific test file
uv run pytest tests/api/test_dataset_api.py -v

# Run tests matching pattern
uv run pytest -k "test_run" -v
```

### Test Organization

| Directory | Purpose |
|-----------|---------|
| `tests/api/` | Library API tests |
| `tests/cli/` | CLI command tests |
| `tests/core/` | Core module tests |

### Writing Tests

```python
"""Test file template."""

import pytest
from dli import DatasetAPI, ExecutionContext
from dli.models.common import ExecutionMode

class TestFeatureName:
    """Tests for feature."""

    @pytest.fixture
    def mock_api(self) -> DatasetAPI:
        """Create API in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DatasetAPI(context=ctx)

    def test_basic_operation(self, mock_api: DatasetAPI) -> None:
        """Test description."""
        result = mock_api.list_datasets()
        assert result == []
```

## Pull Request Process

1. **Branch naming**: `feature/short-description` or `fix/issue-description`
2. **Commit messages**: Use conventional commits (`feat:`, `fix:`, `docs:`, `refactor:`)
3. **Tests**: Add tests for new functionality
4. **Documentation**: Update README.md and PATTERNS.md if needed

## Code Style Guidelines

### Imports

```python
# Standard library
from __future__ import annotations
from datetime import datetime
from pathlib import Path

# Third-party
import pytest
from pydantic import BaseModel

# Local
from dli import DatasetAPI, ExecutionContext
from dli.exceptions import DLIError
```

### Type Hints

- Use type hints for all function signatures
- Use `from __future__ import annotations` for forward references
- Prefer `X | None` over `Optional[X]`

### Docstrings

```python
def run_dataset(name: str, *, dry_run: bool = False) -> DatasetResult:
    """Execute a dataset query.

    Args:
        name: Fully qualified dataset name (catalog.schema.name).
        dry_run: If True, validate without execution.

    Returns:
        DatasetResult with execution status.

    Raises:
        DatasetNotFoundError: If dataset not found.
        ExecutionError: If execution fails.
    """
```

## Architecture Reference

See [docs/PATTERNS.md](./docs/PATTERNS.md) for implementation patterns.
