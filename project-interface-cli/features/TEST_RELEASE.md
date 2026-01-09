# Integration Testing Release Notes

> **Version:** 1.1.0 (Standard)
> **Released:** 2026-01-09
> **Status:** ✅ Complete

---

## Overview

This release introduces Trino integration testing infrastructure for `project-interface-cli`. Tests verify that CLI specs (Dataset, Metric, Quality) and RunAPI can be rendered and executed against a real Trino instance.

---

## What's New in v1.1.0 (Standard)

### RunAPI Integration Tests

| Component | Status | Description |
|-----------|--------|-------------|
| Basic SQL Execution | ✅ Complete | SELECT, multi-column, table data |
| Output Formats | ✅ Complete | CSV, JSON (JSONL), TSV |
| Parameter Substitution | ✅ Complete | `{{ param }}` syntax |
| dry-run | ✅ Complete | EXPLAIN validation without execution |
| Error Handling | ✅ Complete | Syntax, non-existent table, invalid column |
| Advanced Queries | ✅ Complete | CTE, Window, Subquery, CASE |

### v1.1.0 Test Categories (New)

| Category | Test Count | Description |
|----------|------------|-------------|
| RunAPI Basic Execution | 3 | Simple SELECT, multi-column, table data |
| RunAPI Output Formats | 3 | CSV, JSON, TSV format validation |
| RunAPI Parameters | 2 | Parameter substitution, render_sql |
| RunAPI Row Limiting | 1 | Limit option |
| RunAPI Complex Queries | 2 | Aggregation, JOIN |
| RunAPI dry-run | 3 | EXPLAIN, parameters, execution mode |
| RunAPI Error Handling | 5 | File not found, syntax, table, column, division |
| RunAPI Advanced | 4 | CTE, Window, Subquery, CASE |
| RunAPI Result Validation | 4 | Rendered SQL, duration, mode, empty result |
| **v1.1.0 New Total** | **~27** | |

---

## What's New in v1.0.0 (MVP)

### Trino Integration Testing

| Component | Status | Description |
|-----------|--------|-------------|
| Docker Compose | ✅ Complete | Trino 467 with memory catalog |
| pytest-docker | ✅ Complete | Automatic container lifecycle |
| TrinoExecutor Tests | ✅ Complete | Connection, query, schema tests |
| Spec Execution Tests | ✅ Complete | Dataset/Metric/Quality → Trino |
| CI Integration | ✅ Complete | GitHub Actions optional job |

### v1.0.0 Test Categories

| Category | Test Count | Description |
|----------|------------|-------------|
| Trino Connection | 9 | Connection, query, columns, timing |
| Schema Operations | 3 | Create schema, create table, insert/select |
| Query Execution | 4 | Aggregation, join, filter, date functions |
| Dry Run | 3 | EXPLAIN validation |
| Error Handling | 3 | Non-existent table, syntax, division |
| Table Schema | 2 | Schema inspection |
| Dataset Spec | 4 | SQL rendering and execution |
| Metric Spec | 2 | Metric calculation validation |
| Quality Spec | 4 | NOT NULL, UNIQUE, accepted values |
| Complex Queries | 4 | Window, CTE, subquery, CASE |
| **v1.0.0 Total** | **~38** | |

### Combined Total

| Version | Tests | Description |
|---------|-------|-------------|
| v1.0.0 (MVP) | ~38 | TrinoExecutor, Spec Execution |
| v1.1.0 (Standard) | ~27 | RunAPI Integration |
| **Total** | **~65** | |

---

## Implementation Details

### Infrastructure

#### Docker Compose (`tests/integration/docker-compose.trino.yaml`)

```yaml
services:
  trino:
    image: trinodb/trino:467
    ports:
      - "8080:8080"
    volumes:
      - ./trino-config/catalog:/etc/trino/catalog:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/v1/info"]
```

#### Memory Catalog (`trino-config/catalog/memory.properties`)

```properties
connector.name=memory
memory.max-data-per-node=256MB
```

### pytest Configuration

```toml
# pyproject.toml
[tool.pytest.ini_options]
addopts = ["-m", "not integration"]  # Skip by default
markers = [
    "integration: requires Docker + Trino",
    "trino: requires Trino database",
    "slow: slow running tests",
]

[dependency-groups]
integration = ["pytest-docker>=3.1.0", "trino>=0.330.0"]
```

### Key Fixtures

```python
# tests/integration/conftest.py

@pytest.fixture(scope="session")
def trino_service():
    """Start Trino container, wait for ready, yield connection info."""

@pytest.fixture(scope="function")
def trino_executor(trino_service):
    """Create TrinoExecutor for each test."""

@pytest.fixture(scope="function")
def trino_test_schema(trino_executor):
    """Create unique schema per test for isolation."""

@pytest.fixture
def sample_users_table(trino_executor, trino_test_schema):
    """Preload users table with test data."""

@pytest.fixture
def sample_events_table(trino_executor, trino_test_schema):
    """Preload events table with test data."""
```

---

## Running Integration Tests

### Local Development

```bash
# Install dependencies
uv sync --group integration

# Option 1: Manual Docker management
cd tests/integration
docker compose -f docker-compose.trino.yaml up -d
cd ../..
uv run pytest tests/integration/ -m integration -v
docker compose -f tests/integration/docker-compose.trino.yaml down -v

# Option 2: Let pytest-docker manage it
uv run pytest tests/integration/ -m integration -v
```

### CI/CD

Integration tests run on:
- Push to `main` branch (automatic)
- Manual trigger with `run_integration_tests: true`

```yaml
# .github/workflows/interface-cli-ci.yml
integration-test:
  if: github.ref == 'refs/heads/main' OR inputs.run_integration_tests
  steps:
    - Start Trino container
    - Run integration tests
    - Publish results
```

---

## Test Coverage

### MVP Scope (Implemented)

- ✅ Trino connection and basic queries
- ✅ Memory catalog table operations
- ✅ TrinoExecutor execute_sql, dry_run, test_connection
- ✅ Dataset spec SQL rendering → Trino execution
- ✅ Metric spec SQL rendering → Trino execution
- ✅ Quality check SQL patterns (NOT NULL, UNIQUE, accepted_values)
- ✅ Error handling (non-existent tables, syntax errors)
- ✅ GitHub Actions CI integration

### Not in MVP (See Roadmap)

- ⏳ DatasetAPI/MetricAPI integration (spec loading)
- ⏳ Jinja parameter rendering end-to-end
- ⏳ Full Quality test runner
- ⏳ Iceberg catalog support
- ⏳ Performance/load testing

---

## Roadmap

### v1.1.0 - Standard Level (Planned)

**Target:** Spec API integration

| Feature | Description |
|---------|-------------|
| DatasetAPI.run() | Full pipeline: load spec → render SQL → execute |
| MetricAPI.run() | Full pipeline with Trino execution |
| QualityAPI.run() | Run quality tests against Trino |
| Jinja Parameters | End-to-end parameter substitution |
| Pre/Post Statements | Execute pre/post SQL statements |

Estimated: +25 tests

### v2.0.0 - Full Level (Future)

**Target:** Production-like environment

| Feature | Description |
|---------|-------------|
| Iceberg Catalog | Real Iceberg tables with MinIO storage |
| Table Persistence | Data survives container restart |
| Complex Types | Array, Map, Struct column types |
| Large Data | Performance testing with large datasets |
| Multi-Engine | Trino + BigQuery emulator |

Estimated: +40 tests

---

## Dependencies

### Runtime (Integration Tests)

| Package | Version | Purpose |
|---------|---------|---------|
| pytest-docker | ≥3.1.0 | Docker container management |
| trino | ≥0.330.0 | Trino Python client |

### Infrastructure

| Component | Version | Purpose |
|-----------|---------|---------|
| Docker | Latest | Container runtime |
| trinodb/trino | 467 | Trino server image |

---

## Files Changed

### New Files

| File | Lines | Description |
|------|-------|-------------|
| `tests/integration/conftest.py` | 320 | pytest-docker fixtures |
| `tests/integration/docker-compose.trino.yaml` | 25 | Trino container config |
| `tests/integration/trino-config/catalog/memory.properties` | 2 | Memory catalog |
| `tests/integration/test_trino_integration.py` | 395 | TrinoExecutor tests |
| `tests/integration/test_spec_execution_integration.py` | 620 | Spec execution tests |
| `docs/TESTING.md` | 400 | Testing documentation |
| `features/TEST_RELEASE.md` | This file | Release notes |

### Modified Files

| File | Changes |
|------|---------|
| `pyproject.toml` | Added `integration` dependency group, pytest markers |
| `.github/workflows/interface-cli-ci.yml` | Added `integration-test` job |

---

## Migration Guide

### From No Integration Tests

1. Install integration dependencies:
   ```bash
   uv sync --group integration
   ```

2. Ensure Docker is running:
   ```bash
   docker info
   ```

3. Run integration tests:
   ```bash
   uv run pytest tests/integration/ -m integration
   ```

### CI Configuration

To enable integration tests in CI:

1. Go to GitHub Actions
2. Select "CI - Interface CLI" workflow
3. Click "Run workflow"
4. Check "Run integration tests (requires Docker)"
5. Run

---

## Known Limitations

1. **Memory Catalog Only**: Tables don't persist between tests (by design)
2. **Single Node**: Trino runs in single-node mode
3. **No Auth**: Tests use anonymous authentication
4. **Container Startup**: ~30 seconds for Trino to be ready
5. **macOS Docker**: May need Docker Desktop running

---

## Related Documents

- [TESTING.md](../docs/TESTING.md) - Full testing guide
- [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) - TrinoExecutor implementation
- [README.md](../README.md) - Project overview
