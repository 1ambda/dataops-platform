# SQL Snippet Management - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-09
> **Status:** MVP Complete

---

## Executive Summary

This release introduces the SQL Snippet Management feature for project-interface-cli, enabling users to list, download, and upload saved SQL snippets from Basecamp Server.

### Key Metrics

| Metric | Value |
|--------|-------|
| **CLI Commands** | 3 (list, get, put) |
| **API Methods** | 3 (list_snippets, get, put) |
| **Total Tests** | 87 (API: 50, CLI: 37) |
| **Error Codes** | 5 (DLI-790 to DLI-794) |

---

## CLI Commands

| Command | Description |
|---------|-------------|
| `dli sql list` | List SQL snippets with filters |
| `dli sql get <ID>` | Download snippet to file or stdout |
| `dli sql put <ID> -f <FILE>` | Upload SQL file to update snippet |

### Options Summary

**list:**
- `--project, -p` - Filter by project name
- `--folder` - Filter by folder name
- `--starred` - Show only starred snippets
- `--limit, -n` - Maximum results (default: 20)
- `--offset` - Pagination offset
- `--format, -f` - Output format: table, json

**get:**
- `--file, -f` - Output file path (stdout if omitted)
- `--overwrite` - Overwrite without prompt
- `--project, -p` - Project name

**put:**
- `--file, -f` - SQL file to upload (required)
- `--force` - Skip confirmation prompt
- `--project, -p` - Project name

---

## API Classes

### SqlAPI

| Method | Returns | Description |
|--------|---------|-------------|
| `list_snippets()` | `SqlListResult` | List snippets with filters |
| `get()` | `SqlSnippetDetail` | Get snippet with SQL content |
| `put()` | `SqlUpdateResult` | Update snippet SQL |

---

## Data Models

| Model | Purpose |
|-------|---------|
| `SqlDialect` | Enum: BIGQUERY, TRINO, SPARK |
| `SqlSnippetInfo` | Summary for list views |
| `SqlSnippetDetail` | Full details with SQL content |
| `SqlListResult` | Paginated list result |
| `SqlUpdateResult` | Update confirmation |

---

## Error Codes (DLI-79x)

| Code | Exception | Description |
|------|-----------|-------------|
| DLI-790 | `SqlFileNotFoundError` | Local SQL file not found |
| DLI-791 | `SqlSnippetNotFoundError` | Snippet ID not found |
| DLI-792 | `SqlAccessDeniedError` | Access denied |
| DLI-793 | `SqlUpdateFailedError` | Update failed |
| DLI-794 | `SqlProjectNotFoundError` | Project not found |

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
| `src/dli/core/client.py` | Added sql_* and project_* methods |
| `src/dli/__init__.py` | Added SqlAPI export |
| `src/dli/main.py` | Registered sql_app |

---

## Server API Integration

CLI uses Basecamp Server endpoints:

| CLI Command | Server Endpoint |
|-------------|-----------------|
| `sql list` | GET `/api/v1/projects/{id}/sql/snippets` |
| `sql get` | GET `/api/v1/projects/{id}/sql/snippets/{id}` |
| `sql put` | PUT `/api/v1/projects/{id}/sql/snippets/{id}` |

---

## Limitations (MVP)

1. **Mock Mode Only**: Server mode HTTP calls are TODO
2. **Folder Filter**: Not implemented (folder name to ID resolution)
3. **No Create/Delete**: Snippet creation/deletion via UI only

---

## Verification

```bash
cd project-interface-cli
uv run pytest tests/api/test_sql_api.py tests/cli/test_sql_cmd.py -v
# 87 passed
```

---

*Document Version: 1.0.0 | Last Updated: 2026-01-09*
