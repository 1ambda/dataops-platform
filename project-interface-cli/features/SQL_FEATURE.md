# FEATURE: SQL Command - Saved Query Management

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | Implemented |
| **Created** | 2026-01-06 |
| **Last Updated** | 2026-01-09 |
| **Implementation** | See [SQL_RELEASE.md](./SQL_RELEASE.md) |
| **References** | Databricks SQL Editor, BigQuery Saved Queries, Redash, Metabase |

---

## 1. Overview

### 1.1 Purpose

`dli sql` provides management of saved queries stored in Basecamp Server. Users can list, download, and upload SQL queries to collaborate with team members and maintain version-controlled query files locally.

**Key Use Case:**
- Download saved query SQL to local file for editing/version control
- Upload modified SQL file back to the saved query
- List available saved queries with filtering by project/folder

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Minimal Scope** | Only 3 commands: `list`, `get`, `put` - no complex folder management |
| **File-Based Workflow** | SQL content is downloaded/uploaded via local files |
| **Server-Based Storage** | All queries are stored on Basecamp Server (not local-only) |
| **Project-Scoped** | Queries are organized by project on the server |
| **Bidirectional Sync** | Get (download) and Put (upload) enable local editing workflow |

### 1.3 Key Features

| Feature | Status | Description |
|---------|--------|-------------|
| **List Queries** | ✅ Complete | List saved queries with project/folder/starred filters |
| **Download Query** | ✅ Complete | Download query SQL content to local file |
| **Upload Query** | ✅ Complete | Upload local SQL file to update saved query |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to `dli sql` |
|------|--------------|----------------------|
| **Databricks SQL Editor** | Saved queries, folders, sharing | Folder organization, project scope |
| **BigQuery Saved Queries** | Project-based, labels, scheduling | Project filtering |
| **Redash** | Query versioning, favorites | Starred filter |
| **Metabase** | Collections, permissions | Folder-based organization |

### 1.5 System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **CLI Commands** | `commands/query.py` structure | Similar subcommand approach |
| **Library API** | `QueryAPI` facade pattern | `SqlAPI` follows same pattern |
| **Client** | `BasecampClient.query_*` methods | Add `sql_*` methods |
| **Exceptions** | DLI-78x Query errors | Use DLI-79x sub-range for SQL |

---

## 2. Data Model

### 2.1 Core Entities

| Entity | Description |
|--------|-------------|
| **SavedQuery** | A named SQL query stored on the server |
| **Project** | Logical grouping of queries (e.g., "marketing", "finance") |
| **Folder** | Organizational container within a project |

### 2.2 SavedQuery Model

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Unique query identifier |
| `name` | string | Query name |
| `project` | string | Project name |
| `folder` | string | null | Folder path within project |
| `dialect` | string | SQL dialect (bigquery, trino) |
| `starred` | bool | Whether query is starred/favorited |
| `created_at` | datetime | Creation timestamp |
| `updated_at` | datetime | Last modification timestamp |
| `created_by` | string | Creator account |
| `updated_by` | string | Last modifier account |

---

## 3. CLI Design

### 3.1 Command Structure

```
dli sql <subcommand> [arguments] [options]
```

| Subcommand | Status | Arguments | Description |
|------------|--------|-----------|-------------|
| `list` | ✅ Complete | - | List saved queries with filters |
| `get` | ✅ Complete | `<QUERY_ID>` | Download query SQL to local file |
| `put` | ✅ Complete | `<QUERY_ID>` | Upload local SQL file to saved query |

### 3.2 Subcommand: `list` - List Saved Queries

```bash
dli sql list [options]
```

Lists saved queries with optional filtering.

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--project` | `-p` | TEXT | - | Filter by project name |
| `--folder` | - | TEXT | - | Filter by folder path |
| `--starred` | - | FLAG | `false` | Show only starred queries |
| `--limit` | `-n` | INT | `20` | Maximum number of results |
| `--offset` | - | INT | `0` | Pagination offset |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# List all accessible queries (default: limit=20)
$ dli sql list

# Filter by project
$ dli sql list --project marketing

# Filter by project and folder
$ dli sql list --project marketing --folder "Campaign Analytics"

# Show only starred queries
$ dli sql list --starred

# JSON output for scripting
$ dli sql list --format json

# Pagination
$ dli sql list --limit 50 --offset 100
```

**Output Example (Table):**

```
$ dli sql list --project marketing

ID   Name                    Folder              Dialect   Updated
---  ----------------------  ------------------  --------  ----------
43   insight_analysis        Campaign Analytics  bigquery  2026-01-05
44   channel_attribution     Campaign Analytics  bigquery  2026-01-04
45   user_cohort_retention   User Acquisition    bigquery  2026-01-03
46   daily_active_users      -                   bigquery  2026-01-02

Showing 4 of 4 queries
```

### 3.3 Subcommand: `get` - Download Query SQL

```bash
dli sql get <QUERY_ID> [options]
```

Downloads the SQL content of a saved query to a local file.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `QUERY_ID` | INT | Yes | Query ID to download |

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--file` | `-f` | PATH | - | Output file path (stdout if omitted) |
| `--overwrite` | - | FLAG | `false` | Overwrite existing file without prompt |

**Examples:**

```bash
# Download to file
$ dli sql get 43 -f ./insight.sql
Downloaded query 43 to ./insight.sql

# Download to specific directory
$ dli sql get 43 --file ./queries/insight_analysis.sql
Downloaded query 43 to ./queries/insight_analysis.sql

# Print to stdout (for piping)
$ dli sql get 43
SELECT
  user_id,
  COUNT(*) as event_count
FROM events
WHERE event_date = '{{ date }}'
GROUP BY user_id

# Overwrite existing file without prompt
$ dli sql get 43 -f ./insight.sql --overwrite
Downloaded query 43 to ./insight.sql
```

**Behavior:**
- If `--file` is omitted, prints SQL content to stdout
- If file exists and `--overwrite` not set, prompts for confirmation
- Creates parent directories if they don't exist

### 3.4 Subcommand: `put` - Upload SQL to Query

```bash
dli sql put <QUERY_ID> --file <PATH> [options]
```

Uploads a local SQL file to update an existing saved query.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `QUERY_ID` | INT | Yes | Query ID to update |

**Options:**

| Option | Short | Type | Required | Description |
|--------|-------|------|----------|-------------|
| `--file` | `-f` | PATH | Yes | SQL file to upload |
| `--force` | - | FLAG | No | Skip confirmation prompt |

**Examples:**

```bash
# Upload SQL file to query
$ dli sql put 43 -f ./insight.sql
Upload ./insight.sql to query 43 (insight_analysis)?
This will overwrite the existing SQL content. [y/N]: y
Uploaded ./insight.sql to query 43

# Force upload without confirmation
$ dli sql put 43 --file ./insight.sql --force
Uploaded ./insight.sql to query 43

# Error: file not found
$ dli sql put 43 -f ./nonexistent.sql
Error [DLI-790]: File not found: ./nonexistent.sql
```

**Behavior:**
- File must exist and be readable
- Prompts for confirmation unless `--force` is set
- Returns query metadata after successful upload

---

## 4. API Design (SqlAPI)

### 4.1 SqlAPI Class

| Method | Status | Returns | Description |
|--------|--------|---------|-------------|
| `list_snippets()` | ✅ Complete | `SqlListResult` | List saved queries with filters |
| `get()` | ✅ Complete | `SqlSnippetDetail` | Get query metadata and SQL content |
| `put()` | ✅ Complete | `SqlUpdateResult` | Update query SQL content |

**Usage Example:**

```python
from dli import SqlAPI, ExecutionContext, ExecutionMode
from pathlib import Path

ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080",
)
api = SqlAPI(context=ctx)

# List queries
result = api.list_queries(project="marketing", starred=True)
for query in result.queries:
    print(f"{query.id}: {query.name}")

# Download query SQL
query = api.get(query_id=43)
Path("insight.sql").write_text(query.sql)

# Upload SQL file
with open("insight.sql") as f:
    result = api.put(query_id=43, sql=f.read())
print(f"Updated: {result.updated_at}")
```

### 4.2 Result Models

| Model | Status | Purpose |
|-------|--------|---------|
| `SqlSnippetInfo` | ✅ Complete | Summary query info for list views |
| `SqlSnippetDetail` | ✅ Complete | Full query metadata with SQL content |
| `SqlListResult` | ✅ Complete | List operation result with pagination |
| `SqlUpdateResult` | ✅ Complete | Update operation result |

---

## 5. Data Models

### 5.1 Core Models

```python
from enum import Enum
from datetime import datetime
from pydantic import BaseModel, Field

class SqlDialect(str, Enum):
    """SQL dialect for saved queries."""
    BIGQUERY = "bigquery"
    TRINO = "trino"

class SqlQueryInfo(BaseModel):
    """Summary information for list views."""
    id: int = Field(..., description="Query ID")
    name: str = Field(..., description="Query name")
    project: str = Field(..., description="Project name")
    folder: str | None = Field(default=None, description="Folder path")
    dialect: SqlDialect = Field(..., description="SQL dialect")
    starred: bool = Field(default=False, description="Starred status")
    updated_at: datetime = Field(..., description="Last update time")
    updated_by: str = Field(..., description="Last modifier")

class SqlQueryDetail(BaseModel):
    """Full query details with SQL content."""
    id: int
    name: str
    project: str
    folder: str | None = None
    dialect: SqlDialect
    sql: str = Field(..., description="SQL query content")
    starred: bool = False
    created_at: datetime
    updated_at: datetime
    created_by: str
    updated_by: str

class SqlListResult(BaseModel):
    """List operation result."""
    queries: list[SqlQueryInfo]
    total: int
    offset: int
    limit: int

class SqlUpdateResult(BaseModel):
    """Update operation result."""
    id: int
    name: str
    updated_at: datetime
    updated_by: str
```

---

## 6. Error Codes (DLI-79x)

SQL errors use a sub-range of Query errors (DLI-7xx).

| Code | Name | Description |
|------|------|-------------|
| DLI-790 | SQL_FILE_NOT_FOUND | Local SQL file not found |
| DLI-791 | SQL_QUERY_NOT_FOUND | Saved query ID not found |
| DLI-792 | SQL_ACCESS_DENIED | No permission to access/modify query |
| DLI-793 | SQL_UPDATE_FAILED | Failed to update query |
| DLI-794 | SQL_PROJECT_NOT_FOUND | Project not found |
| DLI-795 | SQL_FOLDER_NOT_FOUND | Folder not found |

**Exception Classes:**

```python
class SqlFileNotFoundError(DLIError):
    """Local SQL file not found."""
    def __init__(self, path: str):
        super().__init__(
            message=f"File not found: {path}",
            code=ErrorCode.SQL_FILE_NOT_FOUND,
        )
        self.path = path

class SqlQueryNotFoundError(DLIError):
    """Saved query not found."""
    def __init__(self, query_id: int):
        super().__init__(
            message=f"Query not found: {query_id}",
            code=ErrorCode.SQL_QUERY_NOT_FOUND,
        )
        self.query_id = query_id

class SqlAccessDeniedError(DLIError):
    """Access denied to query."""
    def __init__(self, query_id: int):
        super().__init__(
            message=f"Access denied to query: {query_id}",
            code=ErrorCode.SQL_ACCESS_DENIED,
        )
        self.query_id = query_id
```

---

## 7. Server API Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| List Queries | GET | `/api/v1/sql/queries` |
| Get Query | GET | `/api/v1/sql/queries/{query_id}` |
| Update Query | PUT | `/api/v1/sql/queries/{query_id}` |

### 7.1 List Queries

**Request:**
```http
GET /api/v1/sql/queries?project=marketing&folder=Campaign%20Analytics&starred=true&limit=20&offset=0
```

**Response:**
```json
{
  "queries": [
    {
      "id": 43,
      "name": "insight_analysis",
      "project": "marketing",
      "folder": "Campaign Analytics",
      "dialect": "bigquery",
      "starred": true,
      "updated_at": "2026-01-05T10:30:00Z",
      "updated_by": "user@example.com"
    }
  ],
  "total": 1,
  "offset": 0,
  "limit": 20
}
```

### 7.2 Get Query

**Request:**
```http
GET /api/v1/sql/queries/43
```

**Response:**
```json
{
  "id": 43,
  "name": "insight_analysis",
  "project": "marketing",
  "folder": "Campaign Analytics",
  "dialect": "bigquery",
  "sql": "SELECT user_id, COUNT(*) as event_count\nFROM events\nWHERE event_date = '{{ date }}'\nGROUP BY user_id",
  "starred": true,
  "created_at": "2026-01-01T09:00:00Z",
  "updated_at": "2026-01-05T10:30:00Z",
  "created_by": "admin@example.com",
  "updated_by": "user@example.com"
}
```

### 7.3 Update Query

**Request:**
```http
PUT /api/v1/sql/queries/43
Content-Type: application/json

{
  "sql": "SELECT user_id, COUNT(*) as event_count\nFROM events\nWHERE event_date = '{{ date }}'\nGROUP BY user_id\nORDER BY event_count DESC"
}
```

**Response:**
```json
{
  "id": 43,
  "name": "insight_analysis",
  "updated_at": "2026-01-06T14:20:00Z",
  "updated_by": "user@example.com"
}
```

---

## 8. Success Criteria

### 8.1 Feature Completion

| Feature | Status | Completion Condition |
|---------|--------|----------------------|
| SqlAPI | ✅ Complete | `list_snippets()`, `get()`, `put()` work in mock mode |
| CLI list | ✅ Complete | `dli sql list` returns formatted output |
| CLI get | ✅ Complete | `dli sql get 43 -f file.sql` downloads file |
| CLI put | ✅ Complete | `dli sql put 43 -f file.sql` uploads file |
| Filters | ✅ Complete | `--project`, `--folder`, `--starred` work correctly |
| Error handling | ✅ Complete | DLI-79x codes return appropriate messages |

### 8.2 Test Quality

| Metric | Target | Status |
|--------|--------|--------|
| Unit test coverage | >= 80% | ✅ 87 tests |
| Mock mode tests | All methods | ✅ Complete |
| CLI command tests | Each subcommand | ✅ Complete |

### 8.3 Code Quality

| Principle | Verification |
|-----------|--------------|
| **Single Responsibility** | SqlAPI delegates to BasecampClient |
| **Consistent Pattern** | Follows QueryAPI/CatalogAPI facade pattern |
| **Dependency Inversion** | SqlAPI accepts optional client via DI |

---

## 9. Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # Add SqlAPI export
├── api/
│   ├── __init__.py       # Add SqlAPI export
│   └── sql.py            # SqlAPI class
├── models/
│   ├── __init__.py       # Add sql model exports
│   └── sql.py            # SqlQueryInfo, SqlQueryDetail, etc.
├── commands/
│   ├── __init__.py       # Add sql_app export
│   └── sql.py            # CLI commands
├── core/
│   ├── client.py         # Add sql_* methods
│   └── sql/
│       ├── __init__.py
│       └── models.py     # Internal models
└── exceptions.py         # Add DLI-79x codes

tests/
├── api/
│   └── test_sql_api.py   # API tests
├── cli/
│   └── test_sql_cmd.py   # CLI tests
└── core/sql/
    └── test_models.py    # Model tests
```

---

## 10. Reference Patterns

| Implementation | Reference File | Pattern |
|----------------|----------------|---------|
| `SqlAPI` | `api/query.py` | Facade pattern, mock mode, DI |
| Result models | `models/query.py` | Pydantic `BaseModel` with `Field` |
| CLI command | `commands/query.py` | Typer command structure |
| Client methods | `core/client.py` | `ServerResponse`, `mock_mode` check |
| Exceptions | `exceptions.py` | `DLIError` inheritance, `ErrorCode` |

---

## Appendix A: Command Summary

### List Queries

```bash
dli sql list [options]

Options:
  -p, --project TEXT    Filter by project name
      --folder TEXT     Filter by folder path
      --starred         Show only starred queries
  -n, --limit INT       Maximum results (default: 20)
      --offset INT      Pagination offset (default: 0)
  -f, --format ENUM     Output: table, json (default: table)
```

### Get Query

```bash
dli sql get <QUERY_ID> [options]

Arguments:
  QUERY_ID              Query ID to download (required)

Options:
  -f, --file PATH       Output file (stdout if omitted)
      --overwrite       Overwrite existing file without prompt
```

### Put Query

```bash
dli sql put <QUERY_ID> --file <PATH> [options]

Arguments:
  QUERY_ID              Query ID to update (required)

Options:
  -f, --file PATH       SQL file to upload (required)
      --force           Skip confirmation prompt
```

---

## Appendix B: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Command scope | Only list/get/put | Minimal MVP, folder management via UI |
| 2 | File requirement for put | `--file` is required | Explicit input, avoid stdin complexity |
| 3 | Stdout default for get | Print to stdout if no `--file` | Unix pipe-friendly |
| 4 | Confirmation for put | Prompt unless `--force` | Prevent accidental overwrites |
| 5 | Project as filter | Optional `--project` filter | Not all users have multi-project access |
| 6 | No create command | Put updates existing only | Query creation via UI, safer workflow |
| 7 | Error code range | DLI-79x | Sub-range of Query (78x) errors |
| 8 | Default limit | 20 | Balance between overview and performance |
| 9 | Dialect in output | Show in list table | Important context for users |
| 10 | Starred filter | Dedicated `--starred` flag | Common use case, not mixed with search |

---

## Appendix C: Workflow Examples

### Typical Edit Workflow

```bash
# 1. Find the query to edit
$ dli sql list --project marketing
ID   Name                    Folder              Dialect   Updated
43   insight_analysis        Campaign Analytics  bigquery  2026-01-05

# 2. Download to local file
$ dli sql get 43 -f ./insight.sql
Downloaded query 43 to ./insight.sql

# 3. Edit locally (vim, VS Code, etc.)
$ vim ./insight.sql

# 4. Upload changes
$ dli sql put 43 -f ./insight.sql
Upload ./insight.sql to query 43 (insight_analysis)?
This will overwrite the existing SQL content. [y/N]: y
Uploaded ./insight.sql to query 43
```

### Version Control Workflow

```bash
# Download all queries in a project
$ for id in $(dli sql list --project marketing --format json | jq -r '.queries[].id'); do
    dli sql get $id -f ./queries/${id}.sql
  done

# Track in git
$ git add queries/
$ git commit -m "feat: sync marketing queries from Basecamp"

# Later, upload modified query
$ dli sql put 43 -f ./queries/43.sql --force
```

### CI/CD Integration

```bash
# In CI pipeline - validate and upload
$ dli sql get 43 -f /tmp/current.sql
$ diff -q /tmp/current.sql ./queries/insight.sql || \
    dli sql put 43 -f ./queries/insight.sql --force
```

---

**Last Updated:** 2026-01-09
