# FEATURE: SQL Command - Saved Worksheet Management

| Attribute | Value |
|-----------|-------|
| **Version** | 3.2.0 |
| **Status** | Team Migration Complete |
| **Created** | 2026-01-06 |
| **Last Updated** | 2026-01-10 |
| **Implementation** | See [SQL_RELEASE.md](./SQL_RELEASE.md) |
| **References** | Databricks SQL Editor, BigQuery Saved Queries, Redash, Metabase |
| **Server Spec** | [`project-basecamp-server/features/SQL_FEATURE.md`](../../project-basecamp-server/features/SQL_FEATURE.md) |

---

## v3.2.0 Changes (2026-01-10)

- **Terminology**: "Snippet" → "Worksheet" (industry-standard naming like Snowflake, Databricks)
- **Model**: SqlSnippet* → SqlWorksheet* (documentation only, implementation separate)
- **Server API**: Path updated to `/api/v1/teams/{teamId}/sql/worksheets`

## v3.1.0 Changes (2026-01-10)

- **Terminology**: "Query" → "Snippet" (unified with server implementation)
- **Server API**: Path updated to `/api/v1/teams/{teamId}/sql/snippets`
- **Organization**: Project-based → Team-based (completed in v2.0.0)
- **CLI Option**: `--project` → `--team`
- **Data Model**: `project` field → `team` field
- **Permission**: Project Membership → Team Membership
- **Role**: OWNER/EDITOR/VIEWER → MANAGER/EDITOR/VIEWER

---

## 1. Overview

### 1.1 Purpose

`dli sql` provides management of saved worksheets stored in Basecamp Server. Users can list, download, and upload SQL worksheets to collaborate with team members and maintain version-controlled worksheet files locally.

**Key Use Case:**
- Download saved worksheet SQL to local file for editing/version control
- Upload modified SQL file back to the saved worksheet
- List available saved worksheets with filtering by team/folder

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Minimal Scope** | Only 3 commands: `list`, `get`, `put` - no complex folder management |
| **File-Based Workflow** | SQL content is downloaded/uploaded via local files |
| **Server-Based Storage** | All worksheets are stored on Basecamp Server (not local-only) |
| **Team-Scoped** | Worksheets are organized by team on the server |
| **Bidirectional Sync** | Get (download) and Put (upload) enable local editing workflow |

### 1.3 Key Features

| Feature | Status | Description |
|---------|--------|-------------|
| **List Worksheets** | Implemented | List saved worksheets with team/folder/starred filters |
| **Download Worksheet** | Implemented | Download worksheet SQL content to local file |
| **Upload Worksheet** | Implemented | Upload local SQL file to update saved worksheet |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to `dli sql` |
|------|--------------|----------------------|
| **Databricks SQL Editor** | Saved queries, folders, sharing | Folder organization, team scope |
| **BigQuery Saved Queries** | Project-based, labels, scheduling | Team filtering |
| **Redash** | Query versioning, favorites | Starred filter |
| **Metabase** | Collections, permissions | Folder-based organization |
| **PopSQL** | Team-based collaboration | Team scope (primary influence) |

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
| **SqlWorksheet** | A named SQL worksheet stored on the server |
| **Team** | Logical grouping of worksheets (e.g., "marketing-team", "finance-team") |
| **Folder** | Organizational container within a team (1-level) |

### 2.2 SqlWorksheet Model

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Unique worksheet identifier |
| `name` | string | Worksheet name |
| `team` | string | Team name |
| `folder` | string | null | Folder path within team |
| `dialect` | string | SQL dialect (bigquery, trino) |
| `starred` | bool | Whether worksheet is starred/favorited |
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
| `list` | Implemented | - | List saved worksheets with filters |
| `get` | Implemented | `<WORKSHEET_ID>` | Download worksheet SQL to local file |
| `put` | Implemented | `<WORKSHEET_ID>` | Upload local SQL file to saved worksheet |

### 3.2 Subcommand: `list` - List Saved Worksheets

```bash
dli sql list [options]
```

Lists saved worksheets with optional filtering.

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--team` | `-t` | TEXT | - | Filter by team name |
| `--folder` | - | TEXT | - | Filter by folder path |
| `--starred` | - | FLAG | `false` | Show only starred worksheets |
| `--limit` | `-n` | INT | `20` | Maximum number of results |
| `--offset` | - | INT | `0` | Pagination offset |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# List all accessible worksheets (default: limit=20)
$ dli sql list

# Filter by team
$ dli sql list --team marketing

# Filter by team and folder
$ dli sql list --team marketing --folder "Campaign Analytics"

# Show only starred worksheets
$ dli sql list --starred

# JSON output for scripting
$ dli sql list --format json

# Pagination
$ dli sql list --limit 50 --offset 100
```

**Output Example (Table):**

```
$ dli sql list --team marketing

ID   Name                    Folder              Dialect   Updated
---  ----------------------  ------------------  --------  ----------
43   insight_analysis        Campaign Analytics  bigquery  2026-01-05
44   channel_attribution     Campaign Analytics  bigquery  2026-01-04
45   user_cohort_retention   User Acquisition    bigquery  2026-01-03
46   daily_active_users      -                   bigquery  2026-01-02

Showing 4 of 4 worksheets
```

### 3.3 Subcommand: `get` - Download Worksheet SQL

```bash
dli sql get <WORKSHEET_ID> [options]
```

Downloads the SQL content of a saved worksheet to a local file.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `WORKSHEET_ID` | INT | Yes | Worksheet ID to download |

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--file` | `-f` | PATH | - | Output file path (stdout if omitted) |
| `--overwrite` | - | FLAG | `false` | Overwrite existing file without prompt |

**Examples:**

```bash
# Download to file
$ dli sql get 43 -f ./insight.sql
Downloaded worksheet 43 to ./insight.sql

# Download to specific directory
$ dli sql get 43 --file ./worksheets/insight_analysis.sql
Downloaded worksheet 43 to ./worksheets/insight_analysis.sql

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
Downloaded worksheet 43 to ./insight.sql
```

**Behavior:**
- If `--file` is omitted, prints SQL content to stdout
- If file exists and `--overwrite` not set, prompts for confirmation
- Creates parent directories if they don't exist

### 3.4 Subcommand: `put` - Upload SQL to Worksheet

```bash
dli sql put <WORKSHEET_ID> --file <PATH> [options]
```

Uploads a local SQL file to update an existing saved worksheet.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `WORKSHEET_ID` | INT | Yes | Worksheet ID to update |

**Options:**

| Option | Short | Type | Required | Description |
|--------|-------|------|----------|-------------|
| `--file` | `-f` | PATH | Yes | SQL file to upload |
| `--force` | - | FLAG | No | Skip confirmation prompt |

**Examples:**

```bash
# Upload SQL file to worksheet
$ dli sql put 43 -f ./insight.sql
Upload ./insight.sql to worksheet 43 (insight_analysis)?
This will overwrite the existing SQL content. [y/N]: y
Uploaded ./insight.sql to worksheet 43

# Force upload without confirmation
$ dli sql put 43 --file ./insight.sql --force
Uploaded ./insight.sql to worksheet 43

# Error: file not found
$ dli sql put 43 -f ./nonexistent.sql
Error [DLI-790]: File not found: ./nonexistent.sql
```

**Behavior:**
- File must exist and be readable
- Prompts for confirmation unless `--force` is set
- Returns worksheet metadata after successful upload

---

## 4. API Design (SqlAPI)

### 4.1 SqlAPI Class

| Method | Status | Returns | Description |
|--------|--------|---------|-------------|
| `list_worksheets()` | Implemented | `SqlListResult` | List saved worksheets with filters |
| `get()` | Implemented | `SqlWorksheetDetail` | Get worksheet metadata and SQL content |
| `put()` | Implemented | `SqlUpdateResult` | Update worksheet SQL content |

**Usage Example:**

```python
from dli import SqlAPI, ExecutionContext, ExecutionMode
from pathlib import Path

ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080",
)
api = SqlAPI(context=ctx)

# List worksheets
result = api.list_worksheets(team="marketing", starred=True)
for worksheet in result.worksheets:
    print(f"{worksheet.id}: {worksheet.name}")

# Download worksheet SQL
worksheet = api.get(worksheet_id=43)
Path("insight.sql").write_text(worksheet.sql)

# Upload SQL file
with open("insight.sql") as f:
    result = api.put(worksheet_id=43, sql=f.read())
print(f"Updated: {result.updated_at}")
```

### 4.2 Result Models

| Model | Status | Purpose |
|-------|--------|---------|
| `SqlWorksheetInfo` | Implemented | Summary worksheet info for list views |
| `SqlWorksheetDetail` | Implemented | Full worksheet metadata with SQL content |
| `SqlListResult` | Implemented | List operation result with pagination |
| `SqlUpdateResult` | Implemented | Update operation result |

---

## 5. Data Models

### 5.1 Core Models

```python
from enum import Enum
from datetime import datetime
from pydantic import BaseModel, Field

class SqlDialect(str, Enum):
    """SQL dialect for saved worksheets."""
    BIGQUERY = "bigquery"
    TRINO = "trino"

class SqlWorksheetInfo(BaseModel):
    """Summary information for list views."""
    id: int = Field(..., description="Worksheet ID")
    name: str = Field(..., description="Worksheet name")
    team: str = Field(..., description="Team name")
    folder: str | None = Field(default=None, description="Folder path")
    dialect: SqlDialect = Field(..., description="SQL dialect")
    starred: bool = Field(default=False, description="Starred status")
    updated_at: datetime = Field(..., description="Last update time")
    updated_by: str = Field(..., description="Last modifier")

class SqlWorksheetDetail(BaseModel):
    """Full worksheet details with SQL content."""
    id: int
    name: str
    team: str
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
    queries: list[SqlWorksheetInfo]
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
| DLI-791 | SQL_WORKSHEET_NOT_FOUND | Saved worksheet ID not found |
| DLI-792 | SQL_ACCESS_DENIED | No permission to access/modify worksheet |
| DLI-793 | SQL_UPDATE_FAILED | Failed to update worksheet |
| DLI-794 | SQL_TEAM_NOT_FOUND | Team not found |
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

class SqlWorksheetNotFoundError(DLIError):
    """Saved worksheet not found."""
    def __init__(self, worksheet_id: int):
        super().__init__(
            message=f"Worksheet not found: {worksheet_id}",
            code=ErrorCode.SQL_WORKSHEET_NOT_FOUND,
        )
        self.worksheet_id = worksheet_id

class SqlAccessDeniedError(DLIError):
    """Access denied to worksheet."""
    def __init__(self, worksheet_id: int):
        super().__init__(
            message=f"Access denied to worksheet: {worksheet_id}",
            code=ErrorCode.SQL_ACCESS_DENIED,
        )
        self.worksheet_id = worksheet_id

class SqlTeamNotFoundError(DLIError):
    """Team not found."""
    def __init__(self, team: str):
        super().__init__(
            message=f"Team not found: {team}",
            code=ErrorCode.SQL_TEAM_NOT_FOUND,
        )
        self.team = team
```

---

## 7. Server API Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| List Worksheets | GET | `/api/v1/teams/{teamId}/sql/worksheets` |
| Get Worksheet | GET | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` |
| Update Worksheet | PUT | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` |

### 7.1 List Worksheets

**Request:**
```http
GET /api/v1/teams/1/sql/worksheets?folderName=Campaign%20Analytics&starred=true&size=20&page=0
```

**Response:**
```json
{
  "content": [
    {
      "id": 43,
      "name": "insight_analysis",
      "teamId": 1,
      "teamName": "marketing",
      "folderId": 5,
      "folderName": "Campaign Analytics",
      "dialect": "BIGQUERY",
      "starred": true,
      "updatedAt": "2026-01-05T10:30:00Z",
      "createdBy": "user@example.com"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 7.2 Get Worksheet

**Request:**
```http
GET /api/v1/teams/1/sql/worksheets/43
```

**Response:**
```json
{
  "id": 43,
  "name": "insight_analysis",
  "teamId": 1,
  "teamName": "marketing",
  "folderId": 5,
  "folderName": "Campaign Analytics",
  "dialect": "BIGQUERY",
  "sqlText": "SELECT user_id, COUNT(*) as event_count\nFROM events\nWHERE event_date = '{{ date }}'\nGROUP BY user_id",
  "starred": true,
  "createdAt": "2026-01-01T09:00:00Z",
  "updatedAt": "2026-01-05T10:30:00Z",
  "createdBy": "admin@example.com",
  "updatedBy": "user@example.com"
}
```

### 7.3 Update Worksheet

**Request:**
```http
PUT /api/v1/teams/1/sql/worksheets/43
Content-Type: application/json

{
  "sqlText": "SELECT user_id, COUNT(*) as event_count\nFROM events\nWHERE event_date = '{{ date }}'\nGROUP BY user_id\nORDER BY event_count DESC"
}
```

**Response:**
```json
{
  "id": 43,
  "name": "insight_analysis",
  "updatedAt": "2026-01-06T14:20:00Z"
}
```

---

## 8. Success Criteria

### 8.1 Feature Completion

| Feature | Status | Completion Condition |
|---------|--------|----------------------|
| SqlAPI | Implemented | `list_worksheets()`, `get()`, `put()` work in mock mode |
| CLI list | Implemented | `dli sql list` returns formatted output |
| CLI get | Implemented | `dli sql get 43 -f file.sql` downloads worksheet |
| CLI put | Implemented | `dli sql put 43 -f file.sql` uploads worksheet |
| Team Filters | Implemented | `--team` filter works correctly |
| Error handling | Implemented | DLI-79x codes return appropriate messages |

### 8.2 Test Quality

| Metric | Target | Status |
|--------|--------|--------|
| Unit test coverage | >= 80% | 87 tests |
| Mock mode tests | All methods | Complete |
| CLI command tests | Each subcommand | Complete |

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
│   └── sql.py            # SqlWorksheetInfo, SqlWorksheetDetail, etc.
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

### List Worksheets

```bash
dli sql list [options]

Options:
  -t, --team TEXT       Filter by team name
      --folder TEXT     Filter by folder path
      --starred         Show only starred worksheets
  -n, --limit INT       Maximum results (default: 20)
      --offset INT      Pagination offset (default: 0)
  -f, --format ENUM     Output: table, json (default: table)
```

### Get Worksheet

```bash
dli sql get <WORKSHEET_ID> [options]

Arguments:
  WORKSHEET_ID            Worksheet ID to download (required)

Options:
  -f, --file PATH       Output file (stdout if omitted)
      --overwrite       Overwrite existing file without prompt
```

### Put Worksheet

```bash
dli sql put <WORKSHEET_ID> --file <PATH> [options]

Arguments:
  WORKSHEET_ID            Worksheet ID to update (required)

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
| 5 | Team as filter | Optional `--team` filter | Not all users have multi-team access |
| 6 | No create command | Put updates existing only | Worksheet creation via UI, safer workflow |
| 7 | Error code range | DLI-79x | Sub-range of SQL (79x) errors |
| 8 | Default limit | 20 | Balance between overview and performance |
| 9 | Dialect in output | Show in list table | Important context for users |
| 10 | Starred filter | Dedicated `--starred` flag | Common use case, not mixed with search |

---

## Appendix C: Workflow Examples

### Typical Edit Workflow

```bash
# 1. Find the worksheet to edit
$ dli sql list --team marketing
ID   Name                    Folder              Dialect   Updated
43   insight_analysis        Campaign Analytics  bigquery  2026-01-05

# 2. Download to local file
$ dli sql get 43 -f ./insight.sql
Downloaded worksheet 43 to ./insight.sql

# 3. Edit locally (vim, VS Code, etc.)
$ vim ./insight.sql

# 4. Upload changes
$ dli sql put 43 -f ./insight.sql
Upload ./insight.sql to worksheet 43 (insight_analysis)?
This will overwrite the existing SQL content. [y/N]: y
Uploaded ./insight.sql to worksheet 43
```

### Version Control Workflow

```bash
# Download all worksheets in a team
$ for id in $(dli sql list --team marketing --format json | jq -r '.worksheets[].id'); do
    dli sql get $id -f ./worksheets/${id}.sql
  done

# Track in git
$ git add worksheets/
$ git commit -m "feat: sync marketing team worksheets from Basecamp"

# Later, upload modified worksheet
$ dli sql put 43 -f ./worksheets/43.sql --force
```

### CI/CD Integration

```bash
# In CI pipeline - validate and upload
$ dli sql get 43 -f /tmp/current.sql
$ diff -q /tmp/current.sql ./worksheets/insight.sql || \
    dli sql put 43 -f ./worksheets/insight.sql --force
```

---

## Appendix D: Migration from v1.x

### Breaking Changes from v1.0.0

| Change | v1.0.0 | v2.0.0 | Migration |
|--------|--------|--------|-----------|
| CLI filter option | `--project` | `--team` | Update CLI scripts |
| API parameter | `projectName` | `teamName` | Update API calls |
| Data model field | `project` | `team` | Update client code |
| Server endpoint param | `project=` | `teamName=` | Update HTTP calls |

### Migration Steps

```bash
# Old v1.x command
$ dli sql list --project marketing

# New v2.0 command
$ dli sql list --team marketing
```

---

## Appendix E: Version History

| Version | Date | Changes |
|---------|------|---------|
| 3.2.0 | 2026-01-10 | Unified "Snippet" → "Worksheet" terminology (Snowflake/Databricks style) |
| 3.1.0 | 2026-01-10 | Unified "Query" → "Snippet" terminology, Server API path update |
| 2.0.0 | 2026-01-10 | Migration from Project-based to Team-based organization |
| 1.0.0 | 2026-01-09 | Initial implementation with list/get/put commands |

---

**Last Updated:** 2026-01-10 (v3.2.0 - Worksheet terminology)
