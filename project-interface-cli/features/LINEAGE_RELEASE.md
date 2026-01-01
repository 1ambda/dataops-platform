# RELEASE: Lineage Feature

> **Version:** 1.1.0
> **Status:** Implemented (Library API Complete)
> **Release Date:** 2026-01-01

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **LineageClient** | Implemented | Server-based lineage queries with error handling |
| **LineageNode** | Implemented | Node dataclass with 7 fields (name, type, owner, etc.) |
| **LineageEdge** | Implemented | Edge dataclass with 3 fields (source, target, edge_type) |
| **LineageResult** | Implemented | Result container with computed properties |
| **`dli lineage show`** | Implemented | Full lineage with tree visualization |
| **`dli lineage upstream`** | Implemented | Upstream dependencies only |
| **`dli lineage downstream`** | Implemented | Downstream dependents only |
| **Tree Visualization** | Implemented | Rich Tree for dependency graph |
| **Table Output** | Implemented | Tabular format with depth/direction |
| **JSON Output** | Implemented | `--format json` for programmatic use |
| **Mock Mode** | Implemented | Full mock support via BasecampClient |

### 1.2 Files Created/Modified

#### New Files (core/lineage/)

| File | Lines | Purpose |
|------|-------|---------|
| `core/lineage/__init__.py` | 108 | Module exports, dataclasses (LineageNode, LineageEdge, LineageResult), LineageDirection enum |
| `core/lineage/client.py` | 211 | LineageClient, LineageClientError, response parsing |

#### New Files (commands/)

| File | Lines | Purpose |
|------|-------|---------|
| `commands/lineage.py` | 383 | `dli lineage` CLI commands (show, upstream, downstream) |

#### Modified Files

| File | Changes |
|------|---------|
| `core/client.py` | Added `get_lineage()` method with mock data support |
| `commands/__init__.py` | Export `lineage_app` |
| `main.py` | Register lineage subcommand |

#### New Files (api/) - v1.1.0

| File | Lines | Purpose |
|------|-------|---------|
| `api/lineage.py` | 367 | LineageAPI class with MOCK/SERVER mode support |

#### New Files (tests/) - v1.1.0

| File | Lines | Purpose |
|------|-------|---------|
| `tests/api/test_lineage_api.py` | ~500 | 43 tests for LineageAPI |

#### Modified Files (v1.1.0)

| File | Changes |
|------|---------|
| `exceptions.py` | Added DLI-9xx error codes, LineageError, LineageNotFoundError, LineageTimeoutError |
| `api/__init__.py` | Export LineageAPI |
| `__init__.py` | Export LineageAPI, Lineage exceptions |

### 1.3 Recently Implemented (v1.1.0)

| Feature | Status | Notes |
|---------|--------|-------|
| **LineageAPI** | ✅ Implemented | Library API class (367 lines) |
| **DLI-9xx Error Codes** | ✅ Implemented | DLI-900 ~ DLI-904 |
| **Lineage Exceptions** | ✅ Implemented | LineageError, LineageNotFoundError, LineageTimeoutError |
| **Test Files** | ✅ Implemented | 43 tests in test_lineage_api.py |

### 1.4 Not Implemented (Phase 2+)

| Feature | Status | Notes |
|---------|--------|-------|
| **Column-level Lineage** | Not Started | Only table-level lineage supported |
| **Local SQLGlot Processing** | Not Started | Server-based only (no local parsing) |

---

## 2. Usage Guide

### 2.1 `dli lineage show` Command

Display full lineage (upstream and downstream) for a resource:

```bash
# Basic usage
dli lineage show iceberg.analytics.daily_clicks

# Limit depth
dli lineage show iceberg.analytics.daily_clicks --depth 3

# JSON output
dli lineage show iceberg.analytics.daily_clicks --format json

# With custom project path
dli lineage show iceberg.analytics.daily_clicks --path /opt/airflow/dags
```

### 2.2 `dli lineage upstream` Command

Show what the resource depends on:

```bash
# Show upstream dependencies
dli lineage upstream iceberg.analytics.daily_clicks

# Limit traversal depth
dli lineage upstream iceberg.analytics.daily_clicks --depth 2

# JSON output
dli lineage upstream iceberg.analytics.daily_clicks --format json
```

### 2.3 `dli lineage downstream` Command

Show what depends on the resource:

```bash
# Show downstream dependents
dli lineage downstream iceberg.analytics.daily_clicks

# Limit traversal depth
dli lineage downstream iceberg.analytics.daily_clicks --depth 2

# JSON output
dli lineage downstream iceberg.analytics.daily_clicks --format json
```

### 2.4 Command Options

| Option | Description | Default |
|--------|-------------|---------|
| `resource` | Resource name (positional argument) | Required |
| `-d, --depth` | Maximum traversal depth (-1 for unlimited) | `-1` |
| `-f, --format` | Output format (table, json) | `table` |
| `-p, --path` | Project path | Current directory |

---

## 3. Architecture

### 3.1 Module Structure

```
src/dli/core/lineage/
├── __init__.py       # Exports, dataclasses, enum
└── client.py         # LineageClient, error handling

src/dli/commands/
└── lineage.py        # CLI commands
```

### 3.2 Data Models

```python
class LineageDirection(str, Enum):
    UPSTREAM = "upstream"
    DOWNSTREAM = "downstream"
    BOTH = "both"

@dataclass
class LineageNode:
    name: str                          # Fully qualified name
    type: str = "Dataset"              # Dataset, Metric, External
    owner: str | None = None
    team: str | None = None
    description: str | None = None
    tags: list[str] = field(default_factory=list)
    depth: int = 0                     # Distance from root

@dataclass
class LineageEdge:
    source: str                        # Upstream node
    target: str                        # Downstream node
    edge_type: str = "direct"          # direct, indirect

@dataclass
class LineageResult:
    root: LineageNode
    nodes: list[LineageNode]
    edges: list[LineageEdge]
    direction: LineageDirection
    max_depth: int = -1
    total_upstream: int = 0
    total_downstream: int = 0

    @property
    def upstream_nodes(self) -> list[LineageNode]: ...

    @property
    def downstream_nodes(self) -> list[LineageNode]: ...
```

### 3.3 Data Flow

```
User CLI Command
    │
    ▼
┌──────────────────────┐
│ lineage.py (CLI)     │
├──────────────────────┤
│ 1. Parse arguments   │
│ 2. Get LineageClient │
│ 3. Query lineage     │
│ 4. Format output     │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ LineageClient        │
├──────────────────────┤
│ 1. Call server API   │
│ 2. Parse response    │
│ 3. Build result      │
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ BasecampClient       │
├──────────────────────┤
│ get_lineage()        │
│ (mock/real API)      │
└──────────────────────┘
    │
    ▼
LineageResult
```

### 3.4 Exception Handling

```python
class LineageClientError(Exception):
    """Exception raised for lineage client errors."""

    def __init__(self, message: str, status_code: int = 500):
        self.message = message
        self.status_code = status_code
```

---

## 4. Display Formats

### 4.1 Tree Visualization (Default)

Full lineage (`dli lineage show`):

```
Resource
+-- iceberg.analytics.daily_clicks
    Type: Dataset
    Owner: data-team
    Team: analytics

Upstream (depends on)
+-- raw.events (Dataset)
    +-- external.ga4_events (External)

Downstream (depended by)
+-- iceberg.reports.weekly_summary (Dataset)
+-- iceberg.metrics.click_rate (Metric)

Summary: 2 upstream, 2 downstream
```

### 4.2 Table Format

When using `--format table`:

```
         Lineage for iceberg.analytics.daily_clicks
+-----------------------------+----------+-----------+-------+------------+
| Name                        | Type     | Direction | Depth | Owner      |
+-----------------------------+----------+-----------+-------+------------+
| raw.events                  | Dataset  | upstream  | 1     | data-team  |
| external.ga4_events         | External | upstream  | 2     | -          |
| iceberg.reports.weekly_sum  | Dataset  | downstream| 1     | analytics  |
+-----------------------------+----------+-----------+-------+------------+
```

### 4.3 JSON Format

When using `--format json`:

```json
{
  "root": {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "data-team",
    "team": "analytics",
    "description": "Daily click aggregation",
    "tags": ["production", "pii"]
  },
  "nodes": [
    {
      "name": "raw.events",
      "type": "Dataset",
      "owner": "data-team",
      "team": null,
      "description": null,
      "tags": [],
      "depth": -1
    }
  ],
  "edges": [
    {
      "source": "raw.events",
      "target": "iceberg.analytics.daily_clicks",
      "edge_type": "direct"
    }
  ],
  "summary": {
    "direction": "both",
    "max_depth": -1,
    "total_upstream": 2,
    "total_downstream": 2
  }
}
```

---

## 5. Mock Data

The mock implementation in `BasecampClient.get_lineage()` returns sample data for testing:

```python
# Mock lineage structure
{
    "root": {"name": resource_name, "type": "Dataset", ...},
    "nodes": [
        {"name": "upstream.source", "type": "Dataset", "depth": -1, ...},
        {"name": "downstream.consumer", "type": "Dataset", "depth": 1, ...},
    ],
    "edges": [
        {"source": "upstream.source", "target": resource_name},
        {"source": resource_name, "target": "downstream.consumer"},
    ],
    "total_upstream": 1,
    "total_downstream": 1,
}
```

---

## 6. Known Limitations

| Limitation | Description | Future Phase | Status |
|------------|-------------|--------------|--------|
| Table-level only | No column-level lineage tracking | Phase 3 | Pending |
| Server-based only | No local SQLGlot parsing | Phase 3 | Pending |
| ~~No LineageAPI~~ | ~~No Library API for programmatic access~~ | ~~Phase 2~~ | ✅ v1.1.0 |
| ~~No tests~~ | ~~Unit and integration tests not written~~ | ~~Phase 2~~ | ✅ 43 tests |
| No caching | Each query hits the server | Phase 3 | Pending |
| No graph export | No DOT/Mermaid graph export | Phase 3 | Pending |

---

## 7. Future Work

### Phase 2 (Library API + Tests) - ✅ COMPLETE

- [x] Implement `LineageAPI` class for programmatic access (367 lines)
- [x] Add unit tests for LineageAPI (43 tests)
- [x] Add DLI-9xx error codes (DLI-900 ~ DLI-904)
- [x] Add Lineage exception classes (LineageError, LineageNotFoundError, LineageTimeoutError)
- [ ] Add CLI integration tests (Pending)
- [ ] Add mock data fixtures (Pending)

### Phase 3 (Enhanced Features)

- [ ] Column-level lineage tracking
- [ ] Local SQLGlot-based lineage extraction
- [ ] Graph visualization export (DOT, Mermaid)
- [ ] Lineage caching with TTL
- [ ] Impact analysis (`--impact` flag)

### Phase 4 (Server Integration)

- [ ] Real server API integration
- [ ] Lineage registration from local specs
- [ ] Automatic lineage updates on dataset changes

---

## 8. Quality Metrics

| Metric | Value |
|--------|-------|
| pyright errors | 0 |
| ruff violations | 0 |
| Test count | 95 (52 CLI + 43 API) |
| New code lines | ~1,069 (702 CLI + 367 API) |

---

## 9. API Reference

### LineageAPI Methods (v1.1.0)

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `get_lineage()` | resource_name, direction, depth | `LineageResult` | Full lineage query |
| `get_upstream()` | resource_name, depth | `LineageResult` | Upstream dependencies only |
| `get_downstream()` | resource_name, depth | `LineageResult` | Downstream dependents only |

### LineageAPI Usage Example

```python
from dli import LineageAPI, ExecutionContext, ExecutionMode

# Mock mode (testing)
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = LineageAPI(context=ctx)

# Get full lineage
result = api.get_lineage("iceberg.analytics.daily_clicks")
print(f"Upstream: {result.total_upstream}, Downstream: {result.total_downstream}")

# Get upstream only with depth limit
upstream = api.get_upstream("iceberg.analytics.daily_clicks", depth=2)
for node in upstream.nodes:
    print(f"  {node.name} ({node.type})")

# Server mode (production)
ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
api = LineageAPI(context=ctx)
result = api.get_downstream("iceberg.analytics.daily_clicks")
```

### LineageClient Methods (Internal)

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `get_lineage()` | resource_name, direction, depth | `LineageResult` | Full lineage query |
| `get_upstream()` | resource_name, depth | `LineageResult` | Upstream dependencies only |
| `get_downstream()` | resource_name, depth | `LineageResult` | Downstream dependents only |

### CLI Commands

| Command | Arguments | Options | Description |
|---------|-----------|---------|-------------|
| `dli lineage show` | `resource` | `-d`, `-f`, `-p` | Full lineage |
| `dli lineage upstream` | `resource` | `-d`, `-f`, `-p` | Upstream only |
| `dli lineage downstream` | `resource` | `-d`, `-f`, `-p` | Downstream only |

### DLI-9xx Error Codes (v1.1.0)

| Code | Name | Description |
|------|------|-------------|
| DLI-900 | LINEAGE_NOT_FOUND | Lineage not found for resource |
| DLI-901 | LINEAGE_DEPTH_EXCEEDED | Depth limit exceeded |
| DLI-902 | LINEAGE_CYCLE_DETECTED | Circular dependency detected |
| DLI-903 | LINEAGE_SERVER_ERROR | Server-side lineage error |
| DLI-904 | LINEAGE_TIMEOUT | Lineage query timeout |

---

**Last Updated:** 2026-01-01
