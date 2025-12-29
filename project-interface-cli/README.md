# DataOps CLI (dli)

**DataOps CLI** is a command-line interface for the DataOps platform, providing resource management, validation, lineage tracking, and data quality testing for metrics and datasets.

## Features

- **Resource Management**: Discover, validate, and register metrics and datasets
- **Data Catalog**: Browse and explore table metadata, schema, quality, and impact analysis
- **Spec File System**: YAML-based specifications with safe templating
- **Validation**: Schema validation and dependency checking
- **Lineage Tracking**: Visualize upstream/downstream dependencies
- **Quality Testing**: 6 built-in tests (not_null, unique, accepted_values, relationships, range_check, row_count)
- **Workflow Management**: Server-based workflow execution via Airflow (run, backfill, pause/unpause)
- **Safe Templating**: dbt/SQLMesh compatible (ds, ds_nodash, var(), date_add(), ref(), env_var())
- **Library API**: DatasetService, MetricService for Airflow/orchestrator integration

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

# Validate all specs in current directory
dli validate --all
dli validate --all --strict --check-deps
dli validate --all --type metric

# Render a spec file with template variables
dli render path/to/spec.yaml --ds 2024-01-15
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

# Server
dli server config                         # Show server configuration
dli server status                         # Check server health
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
# List available quality tests
dli quality list

# Run quality tests
dli quality run                           # Run all tests
dli quality run --resource <name>         # Run tests for specific resource
dli quality run --test not_null           # Run specific test type
dli quality run --server                  # Fetch test definitions from server
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

### Environment Variables

```bash
export DLI_LOG_LEVEL=DEBUG
export DLI_DEBUG=true
export DLI_CONFIG_FILE=~/.my-dli-config.json
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

For programmatic access (e.g., Airflow operators):

```python
from dli.core.service import DatasetService, MetricService

# Initialize services
dataset_service = DatasetService(base_path="./datasets")
metric_service = MetricService(base_path="./metrics")

# Get all resources
datasets = dataset_service.list_all()
metrics = metric_service.list_all()

# Get specific resource
dataset = dataset_service.get("catalog.schema.name")

# Render SQL with variables
rendered_sql = dataset_service.render(
    "catalog.schema.name",
    ds="2024-01-15",
    vars={"env": "prod"}
)

# Validate resource
result = dataset_service.validate("catalog.schema.name")
```

## Development

### Setup

```bash
uv sync --group dev
uv pip install -e .
```

### Running Tests

```bash
uv run pytest                    # Run all tests
uv run pytest --cov=dli          # With coverage
uv run pytest -v                 # Verbose
```

### Code Quality

```bash
uv run ruff check --fix          # Lint and fix
uv run ruff format               # Format
uv run pyright src/              # Type check
```

### Building

```bash
# Standard package
uv build

# Standalone executable (includes Python runtime)
uv sync --group build
uv run python build_standalone.py
```

## Project Structure

```
project-interface-cli/
├── src/dli/
│   ├── commands/                # CLI commands
│   │   ├── metric.py            # dli metric subcommands
│   │   ├── dataset.py           # dli dataset subcommands
│   │   ├── server.py            # dli server subcommands
│   │   ├── validate.py          # dli validate command
│   │   ├── lineage.py           # dli lineage subcommands
│   │   ├── quality.py           # dli quality subcommands
│   │   ├── workflow.py          # dli workflow subcommands
│   │   ├── catalog.py           # dli catalog subcommands
│   │   └── utils.py             # Shared CLI utilities
│   ├── core/                    # Core library
│   │   ├── models/              # Pydantic models
│   │   │   ├── spec.py          # Spec file models
│   │   │   ├── metric.py        # Metric-specific models
│   │   │   ├── dataset.py       # Dataset-specific models
│   │   │   └── results.py       # Execution result models
│   │   ├── validation/          # Validators
│   │   │   ├── spec_validator.py
│   │   │   └── dep_validator.py
│   │   ├── lineage/             # Lineage client
│   │   ├── quality/             # Quality testing
│   │   │   ├── builtin_tests.py # 6 built-in tests
│   │   │   ├── executor.py      # Test executor
│   │   │   └── registry.py      # Test registry
│   │   ├── workflow/            # Workflow models
│   │   │   └── models.py        # WorkflowRun, WorkflowStatus
│   │   ├── catalog/             # Catalog models
│   │   │   └── models.py        # TableInfo, TableDetail
│   │   ├── service.py           # DatasetService, MetricService
│   │   ├── discovery.py         # Spec file discovery
│   │   ├── registry.py          # Resource registry
│   │   ├── renderer.py          # Template rendering
│   │   └── templates.py         # Template functions
│   ├── adapters/                # External integrations
│   ├── main.py                  # CLI entry point
│   └── config.py                # Configuration
├── tests/                       # Test suite
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
