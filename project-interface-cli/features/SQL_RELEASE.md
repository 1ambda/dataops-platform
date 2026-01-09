# SQL Worksheet Management - Release Document

> **Version:** 3.2.0
> **Release Date:** 2026-01-10
> **Status:** MVP Complete (Team-based)

---

## v3.2.0 Changes (2026-01-10)

- **Terminology**: Unified "Snippet" → "Worksheet" (industry-standard naming like Snowflake, Databricks)
- **Models**: SqlSnippet* → SqlWorksheet* (documentation only, implementation separate)
- **API Paths**: Updated server endpoints to `/api/v1/teams/{teamId}/sql/worksheets`

## v3.1.0 Changes (2026-01-10)

- **Terminology**: Unified "Query" → "Snippet" across all documentation
- **API Paths**: Updated server endpoints to `/api/v1/teams/{teamId}/sql/snippets`

---

## Migration Note (v3.0.0)

**Breaking Change:** Project-based organization replaced with Team-based architecture.
- `--project, -p` flag renamed to `--team, -t`
- Server endpoints migrated: `/api/v1/projects/{id}/sql/*` -> `/api/v1/teams/{teamId}/sql/*`
- `DLI-794 SqlProjectNotFoundError` renamed to `DLI-794 SqlTeamNotFoundError`

---

## Executive Summary

This release introduces the SQL Worksheet Management feature for project-interface-cli, enabling users to list, download, and upload saved SQL worksheets within Teams from Basecamp Server.

### Key Metrics

| Metric | Value |
|--------|-------|
| **CLI Commands** | 3 (list, get, put) |
| **API Methods** | 3 (list_worksheets, get, put) |
| **Total Tests** | 87 (API: 50, CLI: 37) |
| **Error Codes** | 5 (DLI-790 to DLI-794) |

---

## CLI Commands

| Command | Description |
|---------|-------------|
| `dli sql list` | List SQL worksheets with filters |
| `dli sql get <ID>` | Download worksheet to file or stdout |
| `dli sql put <ID> -f <FILE>` | Upload SQL file to update worksheet |

### Options Summary

**list:**
- `--team, -t` - Filter by team name (v3.0.0: renamed from --project)
- `--folder` - Filter by folder name
- `--starred` - Show only starred worksheets
- `--limit, -n` - Maximum results (default: 20)
- `--offset` - Pagination offset
- `--format, -f` - Output format: table, json

**get:**
- `--file, -f` - Output file path (stdout if omitted)
- `--overwrite` - Overwrite without prompt
- `--team, -t` - Team name (v3.0.0: renamed from --project)

**put:**
- `--file, -f` - SQL file to upload (required)
- `--force` - Skip confirmation prompt
- `--team, -t` - Team name (v3.0.0: renamed from --project)

---

## API Classes

### SqlAPI

| Method | Returns | Description |
|--------|---------|-------------|
| `list_worksheets()` | `SqlListResult` | List worksheets with filters |
| `get()` | `SqlWorksheetDetail` | Get worksheet with SQL content |
| `put()` | `SqlUpdateResult` | Update worksheet SQL |

---

## Data Models

| Model | Purpose |
|-------|---------|
| `SqlDialect` | Enum: BIGQUERY, TRINO, SPARK |
| `SqlWorksheetInfo` | Summary for list views |
| `SqlWorksheetDetail` | Full details with SQL content |
| `SqlListResult` | Paginated list result |
| `SqlUpdateResult` | Update confirmation |

---

## Error Codes (DLI-79x)

| Code | Exception | Description |
|------|-----------|-------------|
| DLI-790 | `SqlFileNotFoundError` | Local SQL file not found |
| DLI-791 | `SqlWorksheetNotFoundError` | Worksheet ID not found |
| DLI-792 | `SqlAccessDeniedError` | Access denied |
| DLI-793 | `SqlUpdateFailedError` | Update failed |
| DLI-794 | `SqlTeamNotFoundError` | Team not found (v3.0.0: renamed from SqlProjectNotFoundError) |

---

## Files Created/Modified

### New Files

| File | Lines | Description |
|------|-------|-------------|
| `src/dli/models/sql.py` | 165 | SQL data models |
| `src/dli/api/sql.py` | 316 | SqlAPI class |
| `src/dli/commands/sql.py` | 290 | CLI commands |
| `tests/api/test_sql_api.py` | 615 | API tests |
| `tests/cli/test_sql_cmd.py` | 400 | CLI tests |

### Modified Files

| File | Changes |
|------|---------|
| `src/dli/exceptions.py` | Added DLI-79x error codes |
| `src/dli/core/client.py` | Added sql_* and team_* methods (v3.0.0: renamed from project_*) |
| `src/dli/__init__.py` | Added SqlAPI export |
| `src/dli/main.py` | Registered sql_app |

---

## Server API Integration (v3.0.0 Team-based)

CLI uses Basecamp Server endpoints:

| CLI Command | Server Endpoint |
|-------------|-----------------|
| `sql list --team <id>` | GET `/api/v1/teams/{teamId}/sql/worksheets` |
| `sql get --team <id>` | GET `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` |
| `sql put --team <id>` | PUT `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` |

---

## Limitations (MVP)

1. **Mock Mode Only**: Server mode HTTP calls are TODO
2. **Folder Filter**: Not implemented (folder name to ID resolution)
3. **No Create/Delete**: Worksheet creation/deletion via UI only

---

## Verification

```bash
cd project-interface-cli
uv run pytest tests/api/test_sql_api.py tests/cli/test_sql_cmd.py -v
# 87 passed
```

---

*Document Version: 3.2.0 | Last Updated: 2026-01-10*
