# DataOps CLI (dli)

**DataOps CLI** is a command-line interface and library for the DataOps platform, providing resource management, validation, lineage tracking, and data quality testing for metrics and datasets.

> **Version:** 0.9.0 | **Python:** 3.12+

## Features

- **Resource Management**: Discover, validate, and register metrics and datasets
- **Data Catalog**: Browse and explore table metadata, schema, quality, and impact analysis
- **Spec File System**: YAML-based specifications with safe templating
- **Validation**: Schema validation and dependency checking
- **Lineage Tracking**: Visualize upstream/downstream dependencies
- **Quality Testing**: Quality Spec YAML 기반 독립적 품질 검증 (Generic + Singular tests)
- **Workflow Management**: Server-based workflow execution via Airflow (run, backfill, pause/unpause)
- **Query Metadata**: Browse and analyze query execution history (scope-based filtering, cancellation)
- **Ad-hoc SQL Execution**: Execute SQL files with result download (CSV/TSV/JSON)
- **SQL Transpilation**: Table substitution, METRIC expansion, dialect support (Trino/BigQuery)
- **SQL + YAML Formatting**: Consistent code style with sqlfluff and ruamel.yaml
- **Safe Templating**: dbt/SQLMesh compatible (ds, ds_nodash, var(), date_add(), ref(), env_var())
- **Environment Management**: Hierarchical config layering (global < project < local < env), `${VAR}` templating, secret masking
- **Library API**: DatasetAPI, MetricAPI, TranspileAPI, CatalogAPI, ConfigAPI, QualityAPI, WorkflowAPI, LineageAPI, QueryAPI, RunAPI for Airflow/orchestrator integration

## Installation

### Prerequisites

- Python 3.12+
- [uv](https://github.com/astral-sh/uv) (recommended) or pip

### Using uv (Recommended)

```bash
cd project-interface-cli
uv sync
uv pip install -e .
```

### Using pip

```bash
cd project-interface-cli
python -m venv venv
source venv/bin/activate
pip install -e .
```

## Usage

### Core Commands

```bash
# Show version and environment info
dli version
dli info

# Configuration
dli config show              # Show current configuration
dli config show --show-source  # Show with value origins
dli config status            # Check server connection status
dli config validate          # Validate configuration
dli config validate --strict # Strict validation (warnings as errors)
dli config env --list        # List available environments
dli config env staging       # Switch to named environment
dli config init              # Initialize config files
dli config set server.url "http://localhost:8081"  # Set config value

# Environment Diagnostics
dli debug                     # Run all diagnostic checks
dli debug --connection        # Database connectivity only
dli debug --auth              # Authentication validation
dli debug --network           # Network diagnostics
dli debug --verbose           # Detailed output
dli debug --json              # JSON format output
```

### Resource Management

```bash
# Metrics
dli metric list                           # List all metrics
dli metric get <name>                     # Get metric details
dli metric validate <name>                # Validate metric spec
dli metric run <name> --ds 2024-01-15     # Execute metric query
dli metric register <name>                # Register with server

# Datasets
dli dataset list                          # List all datasets
dli dataset get <name>                    # Get dataset details
dli dataset validate <name>               # Validate dataset spec
dli dataset run <name> --ds 2024-01-15    # Execute dataset query
dli dataset register <name>               # Register with server
```

### Formatting

Format SQL and YAML files for consistent code style.

```bash
# Format dataset SQL and YAML files
dli dataset format <name>                # Format SQL + YAML
dli dataset format <name> --check        # CI mode (exit 1 if changes)
dli dataset format <name> --sql-only     # SQL only
dli dataset format <name> --lint         # With lint rules
dli dataset format <name> --dialect trino  # Specific dialect

# Format metric
dli metric format <name>
dli metric format <name> --check
dli metric format <name> --yaml-only
```

### Lineage

```bash
# Show lineage graph for a resource
dli lineage show <resource_name>
dli lineage show <resource_name> --depth 3

# Get upstream/downstream dependencies
dli lineage upstream <resource_name>
dli lineage downstream <resource_name>
```

### Quality Testing

```bash
# List quality tests from server
dli quality list [--target-type dataset|metric] [--target TEXT]

# Get quality details from server
dli quality get QUALITY_NAME [--include-history]

# Run quality spec (LOCAL/SERVER mode)
dli quality run SPEC_PATH [--mode local|server] [--test TEXT] [--fail-fast]

# Validate quality spec YAML
dli quality validate SPEC_PATH [--strict] [--test TEXT]
```

### Workflow Management

Unlike `dli dataset run` (local execution), workflow commands trigger server-based execution via Airflow.

```bash
# Trigger adhoc execution
dli workflow run <dataset_name> -p execution_date=2024-01-15
dli workflow run <dataset_name> --dry-run

# Backfill date range
dli workflow backfill <dataset_name> -s 2024-01-01 -e 2024-01-07

# Manage running workflows
dli workflow stop <run_id>
dli workflow status <run_id>

# List and history
dli workflow list                         # List all workflows
dli workflow list --source code           # Filter by source (code/manual)
dli workflow list --running               # Show only running
dli workflow history -d <dataset_name>    # Execution history

# Pause/unpause schedules
dli workflow pause <dataset_name>
dli workflow unpause <dataset_name>
```

### Query Metadata

Browse and analyze query execution history from the server.

```bash
# List my queries (default scope)
dli query list

# List system queries (airflow, dbt-runner, etc.)
dli query list --scope system

# Filter by account and status
dli query list airflow --scope system --status failed

# Show query detail
dli query show <query_id>

# Cancel running query
dli query cancel <query_id>
dli query cancel --user airflow-prod
```

### Ad-hoc SQL Execution

Execute SQL files directly and download results to local files.

```bash
# Basic usage
dli run --sql query.sql --output results.csv

# With parameters
dli run --sql report.sql -o out.csv -p date=2026-01-01 -p region=us-west

# JSON output with limit
dli run --sql users.sql -o users.json -f json -n 100

# Dry run (show execution plan)
dli run --sql query.sql -o results.csv --dry-run

# Local execution (if allowed by policy)
dli run --sql query.sql -o results.csv --local
```

### Data Catalog

Browse and explore table metadata from Basecamp Server.

```bash
# Implicit routing (by identifier parts)
dli catalog my-project                           # List tables in project
dli catalog my-project.analytics                 # List tables in dataset
dli catalog my-project.analytics.users           # Table detail
dli catalog bigquery.my-project.analytics.users  # Engine-specific

# Explicit commands
dli catalog list --owner data@example.com
dli catalog list --tag tier::critical
dli catalog search user --project my-project
```

### SQL Transpilation

Transpile SQL with table substitution and METRIC expansion using resource-specific commands.

```bash
# Transpile dataset SQL
dli dataset transpile iceberg.analytics.daily_clicks

# Transpile metric SQL
dli metric transpile iceberg.analytics.daily_active_users

# With options
dli dataset transpile iceberg.analytics.daily_clicks --strict
dli dataset transpile iceberg.analytics.daily_clicks --show-rules
dli dataset transpile iceberg.analytics.daily_clicks --format json
dli metric transpile iceberg.analytics.daily_active_users --validate
dli metric transpile iceberg.analytics.daily_active_users --dialect bigquery
```

**Note:** The top-level `dli transpile` command has been removed in v1.2.0. For ad-hoc SQL transpilation, use `TranspileAPI` from the Library API or `dli dataset run --sql`.

### Configuration Hierarchy

DLI uses layered configuration with the following priority (highest to lowest):

| Priority | Source | Description |
|----------|--------|-------------|
| 1 | CLI options | Command-line flags |
| 2 | Environment variables | `DLI_*` prefix |
| 3 | `.dli.local.yaml` | Local overrides (not committed) |
| 4 | `dli.yaml` | Project configuration |
| 5 | `~/.dli/config.yaml` | Global user defaults |

**Template Syntax** (in YAML files):

| Syntax | Description |
|--------|-------------|
| `${VAR}` | Required variable |
| `${VAR:-default}` | Variable with default |

**Secret Handling**: Variables prefixed with `DLI_SECRET_*` are masked in output.

### Environment Variables

```bash
export DLI_SERVER_URL="https://basecamp.example.com"
export DLI_EXECUTION_MODE="local"   # local, server, mock
export DLI_DIALECT="trino"
export DLI_TIMEOUT="300"
export DLI_ENVIRONMENT="dev"        # Named environment
export DLI_SECRET_API_KEY="sk-..."  # Masked in output
export DLI_LOG_LEVEL=DEBUG
export DLI_DEBUG=true
```

## Spec File Format

### Directory Structure

```
your-project/
├── metrics/
│   └── metric.catalog.schema.metric_name.yaml
└── datasets/
    └── dataset.catalog.schema.dataset_name.yaml
```

### Metric Spec Example

```yaml
version: "1.0"
kind: metric
metadata:
  name: daily_active_users
  catalog: analytics
  schema: core
  owner: data-team
  tags: [kpi, daily]
spec:
  sql: |
    SELECT COUNT(DISTINCT user_id) as value
    FROM {{ ref('events') }}
    WHERE event_date = '{{ ds }}'
  schedule: "0 6 * * *"
  quality_tests:
    - test: row_count
      params:
        min: 1
```

### Dataset Spec Example

```yaml
version: "1.0"
kind: dataset
metadata:
  name: user_events
  catalog: warehouse
  schema: raw
  owner: data-team
spec:
  sql: |
    SELECT * FROM source_events
    WHERE dt = '{{ ds }}'
  materialization: table
  quality_tests:
    - test: not_null
      column: user_id
    - test: unique
      column: event_id
```

### Template Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `{{ ds }}` | Execution date (YYYY-MM-DD) | 2024-01-15 |
| `{{ ds_nodash }}` | Execution date (YYYYMMDD) | 20240115 |
| `{{ var('key') }}` | User-defined variable | var('env') |
| `{{ date_add(ds, -1) }}` | Date arithmetic | 2024-01-14 |
| `{{ ref('name') }}` | Resource reference | catalog.schema.name |
| `{{ env_var('KEY') }}` | Environment variable | - |

## Library API

v0.4.0 provides a full-featured Library API for programmatic access from Airflow, Basecamp Parser, and other systems.

### API Classes

| API Class | Methods | Description |
|-----------|---------|-------------|
| `DatasetAPI` | list_datasets, get, run, run_sql, validate, register, render_sql, get_tables, get_columns, test_connection, format | Dataset CRUD + execution + introspection + formatting |
| `MetricAPI` | list_metrics, get, run, validate, register, render_sql, get_tables, get_columns, test_connection, format | Metric CRUD + execution + introspection + formatting |
| `TranspileAPI` | transpile, validate_sql, get_rules, format_sql | SQL transpilation |
| `CatalogAPI` | list_tables, get, search | Data catalog browsing |
| `ConfigAPI` | get, get_all, get_with_source, validate, list_environments, get_environment, get_active_environment | Hierarchical config with source tracking |
| `QualityAPI` | list_qualities, get, run, validate | Quality spec 실행 및 검증 |
| `WorkflowAPI` | get, register, unregister, run, backfill, stop, get_status, list_workflows, history, pause, unpause | Server-based workflow orchestration |
| `RunAPI` | run, dry_run, render_sql | Ad-hoc SQL execution with result download |
| `DebugAPI` | run_all, check_system, check_project, check_server, check_auth, check_connection, check_network | Environment diagnostics and connection testing |

### ExecutionMode

`ExecutionMode` determines where SQL queries are executed:

| Mode | Description | Use Case |
|------|-------------|----------|
| `LOCAL` | Direct execution on Query Engine (BigQuery, Trino) | Production data pipelines |
| `SERVER` | Execution via Basecamp Server API | Centralized execution management |
| `MOCK` | Test mode with no actual execution | Unit tests, CI/CD |

```python
from dli import DatasetAPI, ExecutionContext
from dli.models.common import ExecutionMode

# Local execution (default)
ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)

# Server execution
ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)

# Mock mode for testing
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = DatasetAPI(context=ctx)
result = api.run("my_dataset")  # No actual execution
```

**Dependency Injection Support:**

```python
from dli import DatasetAPI
from dli.core.executor import MockExecutor

# Inject custom executor for testing
mock_executor = MockExecutor(mock_data=[{"id": 1, "value": 100}])
api = DatasetAPI(context=ctx, executor=mock_executor)
```

### ExecutionContext

`ExecutionContext` supports two initialization patterns:

```python
from dli import DatasetAPI, ExecutionContext

# Pattern 1: Auto-loads from environment (DLI_* vars)
ctx = ExecutionContext()
api = DatasetAPI(context=ctx)

# Pattern 2: From layered config files (v0.7.0)
ctx = ExecutionContext.from_environment(
    project_path=Path("/opt/airflow/dags/models"),
    environment="prod",  # Named environment from dli.yaml
)
api = DatasetAPI(context=ctx)
```

**Migration Note:** `mock_mode` parameter is deprecated. Use `execution_mode=ExecutionMode.MOCK` instead.

### Airflow PythonOperator Example

```python
from airflow.decorators import task
from dli import DatasetAPI, ExecutionContext

@task
def run_dataset(dataset_name: str, execution_date: str) -> dict:
    ctx = ExecutionContext(
        project_path="/opt/airflow/dags/models",
        server_url="https://basecamp.example.com",
        parameters={"execution_date": execution_date},
    )
    api = DatasetAPI(context=ctx)
    result = api.run(dataset_name)
    return result.model_dump()
```

### Basecamp Parser Integration

```python
from dli import TranspileAPI, ExecutionContext

def transpile_sql(sql: str, dialect: str = "trino") -> dict:
    ctx = ExecutionContext(server_url="http://basecamp-server:8080")
    api = TranspileAPI(context=ctx)
    result = api.transpile(sql, source_dialect=dialect, target_dialect=dialect)
    return result.model_dump()
```

### QualityAPI Example

```python
from dli import QualityAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(
    execution_mode=ExecutionMode.MOCK,
    project_path=Path("/opt/airflow/dags/models"),
)
api = QualityAPI(context=ctx)

# List qualities from server
qualities = api.list_qualities(target_type="dataset")

# Run quality spec
result = api.run("quality.iceberg.analytics.daily_clicks.yaml")

# Validate spec
validation = api.validate("quality.iceberg.analytics.daily_clicks.yaml")
```

### WorkflowAPI Example

```python
from dli import WorkflowAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
api = WorkflowAPI(context=ctx)

# Trigger adhoc run
result = api.run("my_dataset", parameters={"date": "2025-01-15"})

# Backfill date range
result = api.backfill("my_dataset", start_date="2025-01-01", end_date="2025-01-07")

# Check status
status = api.get_status(run_id="abc123")

# List and manage workflows
workflows = api.list_workflows(source="code", running=True)
api.pause("my_dataset")
```

### RunAPI Example

```python
from dli import RunAPI, ExecutionContext
from pathlib import Path

ctx = ExecutionContext()
api = RunAPI(context=ctx)

# Execute SQL and save to CSV
result = api.run(
    sql_path=Path("query.sql"),
    output_path=Path("results.csv"),
    parameters={"date": "2026-01-01"},
)
print(f"Saved {result.row_count} rows")

# Dry run
plan = api.dry_run(sql_path=Path("query.sql"), output_path=Path("results.csv"))
print(f"Would execute: {plan.rendered_sql}")
```

### DebugAPI Example

```python
from dli import DebugAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = DebugAPI(context=ctx)
result = api.run_all()
print(f"Passed: {result.passed_count}/{result.total_count}")
```

### Exception Handling

```python
from dli import DatasetAPI, DatasetNotFoundError, ExecutionError

api = DatasetAPI()
try:
    result = api.run("my_dataset")
except DatasetNotFoundError as e:
    print(f"[{e.code}] {e.message}")  # [DLI-101] Dataset 'my_dataset' not found
except ExecutionError as e:
    print(f"Execution failed: {e.details}")
```

## Development

> **Full Guide:** See [docs/TESTING.md](./docs/TESTING.md) for comprehensive testing documentation.

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

# Activate virtual environment (optional, uv run handles this)
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

### Building and Installing Locally

#### Install as Editable Package

For development, install in editable mode so changes are reflected immediately:

```bash
# Using uv
uv sync
uv run dli --version

# Using pip
pip install -e .
dli --version
```

#### Build Standalone Binary

Build a standalone executable that doesn't require Python:

```bash
# Install build dependencies
uv sync --group build

# Build with PyInstaller
uv run pyinstaller dli.spec --noconfirm

# Binary is at dist/dli
./dist/dli --version
```

#### Build Python Package

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

### Running Tests

#### Unit Tests

Unit tests run without external dependencies (Docker, Trino, etc.):

```bash
# Run all unit tests (default, skips integration tests)
uv run pytest

# Run with verbose output
uv run pytest -v

# Run specific test file
uv run pytest tests/api/test_dataset_api.py

# Run specific test class or method
uv run pytest tests/api/test_dataset_api.py::TestDatasetAPIRun
uv run pytest tests/api/test_dataset_api.py::TestDatasetAPIRun::test_run_success

# Run with coverage report
uv run pytest --cov=src --cov-report=html

# Run in parallel (faster)
uv run pytest -n auto
```

#### Integration Tests

Integration tests require Docker and Trino:

```bash
# Install integration dependencies
uv sync --group integration

# Option 1: Let pytest-docker manage containers
uv run pytest tests/integration/ -m integration -v

# Option 2: Manual Docker management
cd tests/integration
docker compose -f docker-compose.trino.yaml up -d
cd ../..
uv run pytest tests/integration/ -m integration -v
docker compose -f tests/integration/docker-compose.trino.yaml down -v
```

#### Test Markers

| Marker | Description | Command |
|--------|-------------|---------|
| `integration` | Requires Docker + Trino | `pytest -m integration` |
| `trino` | Requires Trino database | `pytest -m trino` |
| `slow` | Long-running tests | `pytest -m "not slow"` |

### Code Quality

```bash
uv run ruff check --fix          # Lint and fix
uv run ruff format               # Format
uv run pyright src/              # Type check
```

### Troubleshooting

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

# Check container logs
docker logs trino-test
```

#### "Module 'trino' not found"
```bash
# Install integration dependencies
uv sync --group integration
```

## Project Structure

```
project-interface-cli/
├── src/dli/
│   ├── __init__.py              # Public API exports (v0.4.0)
│   ├── exceptions.py            # DLIError hierarchy
│   ├── api/                     # Library API (v0.4.0)
│   │   ├── __init__.py          # API exports
│   │   ├── dataset.py           # DatasetAPI
│   │   ├── metric.py            # MetricAPI
│   │   ├── transpile.py         # TranspileAPI
│   │   ├── catalog.py           # CatalogAPI
│   │   ├── config.py            # ConfigAPI
│   │   ├── quality.py           # QualityAPI
│   │   ├── workflow.py          # WorkflowAPI
│   │   └── run.py               # RunAPI
│   ├── models/                  # Shared models (v0.4.0)
│   │   ├── __init__.py          # Model exports
│   │   └── common.py            # ExecutionContext, Results
│   ├── commands/                # CLI commands
│   │   ├── metric.py            # dli metric subcommands
│   │   ├── dataset.py           # dli dataset subcommands
│   │   ├── config.py            # dli config subcommands
│   │   ├── lineage.py           # dli lineage subcommands
│   │   ├── quality.py           # dli quality subcommands
│   │   ├── workflow.py          # dli workflow subcommands
│   │   ├── catalog.py           # dli catalog subcommands
│   │   ├── run.py               # dli run subcommands
│   │   └── utils.py             # Shared CLI utilities
│   ├── core/                    # Core library
│   │   ├── models/              # Spec models
│   │   │   ├── spec.py          # Spec file models
│   │   │   ├── metric.py        # Metric-specific models
│   │   │   ├── dataset.py       # Dataset-specific models
│   │   │   └── results.py       # Execution result models
│   │   ├── validation/          # Validators
│   │   ├── lineage/             # Lineage client
│   │   ├── quality/             # Quality testing
│   │   ├── workflow/            # Workflow models
│   │   ├── catalog/             # Catalog models
│   │   ├── debug/               # Environment diagnostics
│   │   ├── service.py           # DatasetService, MetricService
│   │   ├── discovery.py         # Spec file discovery
│   │   ├── registry.py          # Resource registry
│   │   ├── renderer.py          # Template rendering
│   │   └── templates.py         # Template functions
│   ├── adapters/                # External integrations
│   ├── main.py                  # CLI entry point
│   └── config.py                # Configuration
├── tests/
│   ├── api/                     # Library API tests (330 tests)
│   └── ...                      # CLI and core tests
├── metrics/                     # Example metrics (optional)
├── datasets/                    # Example datasets (optional)
└── pyproject.toml
```

## Built-in Quality Tests

| Test | Description | Parameters |
|------|-------------|------------|
| `not_null` | Check column has no NULL values | `column` |
| `unique` | Check column values are unique | `column` |
| `accepted_values` | Check column values are in allowed list | `column`, `values` |
| `relationships` | Check foreign key relationship exists | `column`, `to`, `field` |
| `range_check` | Check numeric column is within range | `column`, `min`, `max` |
| `row_count` | Check table has expected row count | `min`, `max` (optional) |

## License

[License information to be added]
