# Testing Guide

> **Version:** 1.0.0
> **Last Updated:** 2026-01-09

This document describes how to set up the development environment, run tests locally, and understand the testing infrastructure for `project-interface-cli`.

---

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
  - [Prerequisites](#prerequisites)
  - [Using uv (Recommended)](#using-uv-recommended)
  - [Using pyenv-virtualenv](#using-pyenv-virtualenv)
- [Building and Installing Locally](#building-and-installing-locally)
- [Running Tests](#running-tests)
  - [Unit Tests](#unit-tests)
  - [Integration Tests](#integration-tests)
- [Test Structure](#test-structure)
- [Writing Tests](#writing-tests)
- [CI/CD Pipeline](#cicd-pipeline)
- [Troubleshooting](#troubleshooting)

---

## Development Environment Setup

### Prerequisites

- **Python 3.12+** (required)
- **Docker** (required for integration tests)
- **uv** or **pyenv-virtualenv** (package management)

### Using uv (Recommended)

[uv](https://docs.astral.sh/uv/) is a fast Python package manager that handles virtual environments and dependencies.

```bash
# Install uv (macOS/Linux)
curl -LsSf https://astral.sh/uv/install.sh | sh

# Or with Homebrew
brew install uv

# Navigate to project
cd project-interface-cli

# Create virtual environment and install dependencies
uv sync

# Install with development dependencies
uv sync --group dev

# Install with integration test dependencies
uv sync --group integration

# Install all optional dependencies
uv sync --group dev --group integration --group build

# Activate the virtual environment (optional, uv run handles this)
source .venv/bin/activate

# Run CLI
uv run dli --version

# Run tests
uv run pytest
```

### Using pyenv-virtualenv

If you prefer using pyenv and virtualenv:

```bash
# Install pyenv (macOS)
brew install pyenv pyenv-virtualenv

# Add to shell profile (~/.zshrc or ~/.bashrc)
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"
eval "$(pyenv init -)"
eval "$(pyenv virtualenv-init -)"

# Install Python 3.12
pyenv install 3.12.3

# Create virtual environment
pyenv virtualenv 3.12.3 dli-dev

# Activate
pyenv activate dli-dev

# Navigate to project
cd project-interface-cli

# Install dependencies with pip (from pyproject.toml)
pip install -e ".[dev]"

# For integration tests
pip install -e ".[dev]" pytest-docker trino

# Run CLI
dli --version

# Run tests
pytest
```

---

## Building and Installing Locally

### Install as Editable Package

For development, install in editable mode so changes are reflected immediately:

```bash
# Using uv
uv sync
uv run dli --version

# Using pip
pip install -e .
dli --version
```

### Build Standalone Binary

Build a standalone executable that doesn't require Python:

```bash
# Install build dependencies
uv sync --group build

# Build with PyInstaller
uv run pyinstaller dli.spec --noconfirm

# Binary is at dist/dli
./dist/dli --version
```

### Build Python Package

Build distributable wheel and sdist:

```bash
# Using uv
uv build

# Output in dist/
ls dist/
# dataops_cli-0.1.0-py3-none-any.whl
# dataops_cli-0.1.0.tar.gz

# Install from wheel
pip install dist/dataops_cli-0.1.0-py3-none-any.whl
```

---

## Running Tests

### Unit Tests

Unit tests run without external dependencies (Docker, Trino, etc.):

```bash
# Run all unit tests (default, skips integration tests)
uv run pytest

# Run with verbose output
uv run pytest -v

# Run specific test file
uv run pytest tests/api/test_dataset_api.py

# Run specific test class
uv run pytest tests/api/test_dataset_api.py::TestDatasetAPIRun

# Run specific test
uv run pytest tests/api/test_dataset_api.py::TestDatasetAPIRun::test_run_success

# Run with coverage report
uv run pytest --cov=src --cov-report=html

# Run and show warnings
uv run pytest -W default

# Run in parallel (faster)
uv run pytest -n auto
```

#### Test Configuration

The default pytest configuration (`pyproject.toml`) skips integration tests:

```toml
[tool.pytest.ini_options]
addopts = [
    "-m", "not integration",  # Skip integration tests by default
]
markers = [
    "integration: mark test as integration test (requires Docker + Trino)",
    "trino: mark test as requiring Trino database",
    "slow: mark test as slow running",
]
```

### Integration Tests

Integration tests require Docker and Trino. They test against a real Trino instance with memory catalog.

#### Option 1: Manual Docker Startup

```bash
# Start Trino container
cd tests/integration
docker compose -f docker-compose.trino.yaml up -d

# Wait for Trino to be ready (~30 seconds)
# Check: curl http://localhost:8080/v1/info

# Run integration tests
cd ../..
uv run pytest tests/integration/ -m integration -v

# Stop Trino container
cd tests/integration
docker compose -f docker-compose.trino.yaml down -v
```

#### Option 2: Let pytest-docker Handle It

The integration test fixtures automatically start/stop Docker containers:

```bash
# Install integration dependencies
uv sync --group integration

# Run integration tests (Docker managed automatically)
uv run pytest tests/integration/ -m integration -v
```

#### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TRINO_HOST` | `localhost` | Trino server host |
| `TRINO_PORT` | `8080` | Trino server port |

Example with custom Trino server:
```bash
TRINO_HOST=trino.internal TRINO_PORT=443 uv run pytest tests/integration/ -m integration
```

---

## Test Structure

```
tests/
├── conftest.py              # Shared fixtures (ANSI stripping, sample projects)
├── fixtures/                # Test data files
│   └── sample_project/      # Sample dli project for tests
│
├── api/                     # Library API tests
│   ├── test_dataset_api.py
│   ├── test_metric_api.py
│   ├── test_quality_api.py
│   └── ...
│
├── cli/                     # CLI command tests (Typer)
│   ├── conftest.py          # CLI-specific fixtures
│   ├── test_dataset_cmd.py
│   ├── test_metric_cmd.py
│   └── ...
│
├── core/                    # Core module tests
│   ├── test_executor.py
│   ├── test_config_loader.py
│   └── ...
│
├── models/                  # Model tests
│   ├── test_config_models.py
│   └── ...
│
├── exceptions/              # Exception tests
│   └── test_exceptions.py
│
└── integration/             # Integration tests (Docker required)
    ├── conftest.py          # pytest-docker fixtures, Trino connection
    ├── docker-compose.trino.yaml
    ├── trino-config/
    │   └── catalog/
    │       └── memory.properties
    ├── test_trino_integration.py      # TrinoExecutor tests
    ├── test_spec_execution_integration.py  # Spec rendering + execution
    ├── test_run_integration.py        # RunAPI ad-hoc SQL tests
    ├── test_debug_integration.py      # Debug feature tests
    └── test_format_integration.py     # Format feature tests
```

### Test Categories by Marker

| Marker | Count | Description |
|--------|-------|-------------|
| (none) | ~2845 | Unit tests (run by default) |
| `integration` | ~65 | Require Docker + Trino |
| `slow` | ~10 | Long-running tests |
| `trino` | ~65 | Require Trino specifically |

Run by marker:
```bash
# Only integration tests
uv run pytest -m integration

# Skip slow tests
uv run pytest -m "not slow"

# Integration + slow
uv run pytest -m "integration and slow"
```

---

## Writing Tests

### Unit Test Pattern

```python
"""Tests for DatasetAPI."""
import pytest
from dli import DatasetAPI, ExecutionContext, ExecutionMode

class TestDatasetAPIRun:
    """Tests for DatasetAPI.run() method."""

    @pytest.fixture
    def mock_context(self, tmp_path):
        """Create mock execution context."""
        return ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=tmp_path,
        )

    def test_run_success(self, mock_context):
        """Test successful dataset run."""
        api = DatasetAPI(context=mock_context)
        result = api.run("test.dataset")
        assert result.success is True

    def test_run_not_found(self, mock_context):
        """Test dataset not found error."""
        api = DatasetAPI(context=mock_context)
        with pytest.raises(DatasetNotFoundError):
            api.run("nonexistent.dataset")
```

### Integration Test Pattern

```python
"""Integration tests for Trino executor."""
import pytest

pytestmark = [pytest.mark.integration, pytest.mark.trino]

class TestTrinoConnection:
    """Tests for Trino connection."""

    def test_simple_query(self, trino_executor):
        """Test basic query execution."""
        result = trino_executor.execute_sql("SELECT 1 AS value")
        assert result.success is True
        assert result.data[0]["value"] == 1

    def test_create_table(self, trino_executor, trino_test_schema):
        """Test table creation in memory catalog."""
        table_name = f"memory.{trino_test_schema}.test_table"
        result = trino_executor.execute_sql(f"""
            CREATE TABLE {table_name} (id INTEGER, name VARCHAR)
        """)
        assert result.success is True
```

### Available Fixtures

#### Global Fixtures (`tests/conftest.py`)
- `fixtures_path` - Path to test fixtures directory
- `sample_project_path` - Path to sample dli project
- `tmp_project_path` - Temporary project with minimal structure
- `mock_client` - Mock BasecampClient
- `sample_dataset_yaml`, `sample_metric_yaml` - Sample YAML specs

#### Integration Fixtures (`tests/integration/conftest.py`)
- `trino_executor` - Connected TrinoExecutor instance
- `trino_test_schema` - Unique schema for test isolation
- `sample_users_table` - Preloaded users table
- `sample_events_table` - Preloaded events table
- `integration_project_path` - Project configured for Trino

---

## CI/CD Pipeline

### GitHub Actions Workflow

The CI pipeline runs on every push and PR:

```yaml
# .github/workflows/interface-cli-ci.yml

jobs:
  build:
    # Unit tests, linting, type checking, build
    runs-on: [ubuntu-latest, macos-latest]
    steps:
      - Run linting (ruff)
      - Run type checking (pyright)
      - Run unit tests (pytest)
      - Build standalone binary (PyInstaller)

  integration-test:
    # Integration tests with Trino
    runs-on: ubuntu-latest
    if: main branch push OR workflow_dispatch with run_integration_tests=true
    steps:
      - Start Trino container
      - Run integration tests
      - Stop Trino container
```

### Running Integration Tests in CI

Integration tests run automatically on:
- Push to `main` branch
- Manual trigger with `run_integration_tests: true`

To trigger manually:
1. Go to Actions → CI - Interface CLI
2. Click "Run workflow"
3. Check "Run integration tests"
4. Click "Run workflow"

---

## Troubleshooting

### Common Issues

#### "integration marker not found"
```bash
# Solution: Add marker configuration
uv run pytest --strict-markers -m integration
```

#### "Docker not available"
```bash
# Check Docker is running
docker info

# Start Docker Desktop (macOS)
open -a Docker
```

#### "Trino connection refused"
```bash
# Check Trino is running
curl http://localhost:8080/v1/info

# Check Trino logs
docker logs trino-test

# Restart Trino
cd tests/integration
docker compose -f docker-compose.trino.yaml restart
```

#### "Module 'trino' not found"
```bash
# Install integration dependencies
uv sync --group integration
```

#### Tests pass locally but fail in CI
```bash
# Run with same options as CI
uv run pytest -v --tb=short -m "not integration"

# Check for environment differences
uv run pytest --collect-only
```

### Debug Tips

```bash
# Run single test with full output
uv run pytest tests/path/to/test.py::TestClass::test_method -vvs

# Drop into debugger on failure
uv run pytest --pdb

# Show locals on failure
uv run pytest -l

# Re-run only failed tests
uv run pytest --lf
```

---

## Test Coverage

Current coverage statistics:

| Category | Tests | Coverage |
|----------|-------|----------|
| API Tests | ~580 | 95% |
| CLI Tests | ~1050 | 92% |
| Core Tests | ~860 | 88% |
| Model Tests | ~235 | 98% |
| Integration | ~65 | 85% |
| **Total** | **~2910** | **~91%** |

### Integration Test Breakdown

| File | Tests | Description |
|------|-------|-------------|
| `test_trino_integration.py` | ~30 | TrinoExecutor connection and query tests |
| `test_spec_execution_integration.py` | ~17 | Dataset/Metric/Quality spec execution |
| `test_run_integration.py` | ~27 | RunAPI ad-hoc SQL execution |
| `test_debug_integration.py` | ~15 | Debug feature tests |
| `test_format_integration.py` | ~24 | Format feature tests |

Generate coverage report:
```bash
uv run pytest --cov=src --cov-report=html
open htmlcov/index.html
```

---

## Future Enhancements

### Standard Level (✅ Complete)

Integration tests with:
- ✅ RunAPI ad-hoc SQL execution tests
- ✅ Parameter substitution tests
- ✅ dry-run (EXPLAIN) validation
- ✅ Error handling tests

### Full Level (Future)

Integration tests with:
- Trino + Iceberg + MinIO
- Actual table persistence
- Complex data type support
- Large dataset handling

See `features/TEST_RELEASE.md` for detailed roadmap.
