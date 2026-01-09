# CLI Audit Feature Release

| Attribute | Value |
|-----------|-------|
| **Version** | v1.0.0 |
| **Status** | Implemented (Phase 1 MVP) |
| **Release Date** | 2026-01-10 |
| **Test Count** | 72 tests (31 + 17 + 24) |
| **Components** | TraceContext, TracedHttpClient, @with_trace decorator |

---

## Summary

CLI Audit feature enables request tracing across CLI commands via `X-Trace-Id` headers and configurable trace display. This allows error tracking, usage pattern analysis, and rapid issue diagnosis by correlating CLI requests with server audit logs.

---

## Implementation Summary

### New Files

| File | Lines | Description |
|------|-------|-------------|
| `src/dli/core/trace.py` | ~170 | TraceContext, with_trace decorator |
| `src/dli/core/http.py` | ~130 | TracedHttpClient with header injection |
| `tests/core/test_trace.py` | ~400 | TraceContext unit tests (31 tests) |
| `tests/core/test_http.py` | ~250 | TracedHttpClient tests (17 tests) |
| `tests/commands/test_utils_trace.py` | ~410 | Utils trace tests (24 tests) |

### Modified Files

| File | Change |
|------|--------|
| `src/dli/models/common.py` | Added TraceMode enum (ALWAYS, ERROR_ONLY, NEVER) |
| `src/dli/core/client/baseclient.py` | Integrated TracedHttpClient |
| `src/dli/commands/utils.py` | Added print_error trace support, get_effective_trace_mode |
| `src/dli/api/config.py` | Added get_trace_mode() method |
| `src/dli/commands/*.py` | Added @with_trace to 49 commands |
| `src/dli/__init__.py` | Added public exports (TraceContext, TraceMode, with_trace) |

---

## Features Implemented

### TraceContext Class

| Feature | Description |
|---------|-------------|
| UUID Generation | Unique trace ID per CLI command invocation |
| User-Agent Format | `dli/{version} ({os}; Python/{py_version}) command/{cmd}` |
| Short ID | First 8 characters for display (`trace.short_id`) |
| Context Management | `set_current()`, `get_current()`, `clear_current()` |

### TracedHttpClient

| Feature | Description |
|---------|-------------|
| X-Trace-Id Header | Automatically injected on all requests |
| User-Agent Header | CLI metadata for server audit |
| HTTP Methods | GET, POST, PUT, DELETE with trace headers |

### @with_trace Decorator

| Feature | Description |
|---------|-------------|
| Auto Context | Creates TraceContext for command execution |
| Cleanup | Clears context on completion or exception |
| Applied To | 49 CLI commands |

### TraceMode Configuration

| Mode | Behavior |
|------|----------|
| `ALWAYS` | Show trace ID in all output |
| `ERROR_ONLY` | Show trace ID only on errors (default) |
| `NEVER` | Never show (server logs only) |

### CLI Flags

| Flag | Description |
|------|-------------|
| `--trace` | Force show trace ID |
| `--no-trace` | Force hide trace ID |

Applied to 7 execution commands:
- `dli metric run`
- `dli dataset run`
- `dli quality run`
- `dli run`
- `dli workflow run`
- `dli workflow backfill`
- `dli workflow stop`

---

## Configuration

### Config File

```yaml
# ~/.dli/config.yaml
trace: error_only  # always, error_only, never
```

### Environment Variable

```bash
DLI_TRACE=always dli run query.sql
```

### Priority Order

1. CLI flag (`--trace` / `--no-trace`) - highest
2. Environment variable (`DLI_TRACE`)
3. Config file (`trace:`)
4. Default (`error_only`)

---

## Error Display

### Error with Trace ID

```
[DLI-501] [trace:550e8400] Server error: Connection refused

Trace ID를 사용하여 서버 로그를 확인하세요:
  trace_id: 550e8400-e29b-41d4-a716-446655440000
```

### Server Log Query

```sql
-- Query audit_log by trace_id
SELECT *
FROM audit_log
WHERE trace_id = '550e8400-e29b-41d4-a716-446655440000'
ORDER BY created_at;
```

---

## Test Coverage

| Test File | Tests | Description |
|-----------|-------|-------------|
| `tests/core/test_trace.py` | 31 | TraceContext unit tests |
| `tests/core/test_http.py` | 17 | TracedHttpClient tests |
| `tests/commands/test_utils_trace.py` | 24 | Utils trace integration |
| **Total** | **72** | All passing |

### Key Test Cases

| Test | Description |
|------|-------------|
| `test_trace_context_create` | TraceContext creation |
| `test_trace_context_user_agent` | User-Agent format validation |
| `test_trace_context_short_id` | Short ID (8 chars) |
| `test_trace_context_lifecycle` | set/get/clear context |
| `test_with_trace_decorator` | Decorator operation |
| `test_with_trace_cleanup_on_exception` | Exception cleanup |
| `test_traced_http_client_headers` | HTTP header injection |
| `test_print_error_with_trace` | Error message trace display |
| `test_trace_mode_config` | Config file parsing |
| `test_cli_flag_override` | --trace/--no-trace flags |

---

## Architecture

```
CLI (dli)
┌─────────────────────────────────────────────────────────────────────────┐
│  ┌─────────────┐    ┌──────────────────┐    ┌────────────────────────┐  │
│  │   Command    │───▶│  TraceContext    │───▶│   TracedHttpClient     │  │
│  │ (run, debug) │    │ (UUID, metadata) │    │ (X-Trace-Id header)    │  │
│  └─────────────┘    └──────────────────┘    └──────────┬─────────────┘  │
│                                                         │                │
│  ┌─────────────────────────────────────────────────────┼────────────┐   │
│  │  Error Handler                                       │            │   │
│  │  [DLI-501] [trace:550e8400] Server error            │            │   │
│  └─────────────────────────────────────────────────────┼────────────┘   │
└────────────────────────────────────────────────────────┼────────────────┘
                                                         │
                    HTTP Request                         │
                    X-Trace-Id: 550e8400-...            ▼
                    User-Agent: dli/0.9.0 (...)   ┌─────────────┐
                                                  │   Server    │
                                                  │ audit_log   │
                                                  │   table     │
                                                  └─────────────┘
```

---

## Public API Exports

Added to `src/dli/__init__.py`:

```python
from dli.core.trace import TraceContext, get_current_trace, with_trace
from dli.models.common import TraceMode

__all__ = [
    # ... existing exports
    "TraceContext",
    "TraceMode",
    "get_current_trace",
    "with_trace",
]
```

---

## Phase 2 Roadmap (Future)

| Priority | Task | Description |
|----------|------|-------------|
| P2 | Local Logging | Save command history to ~/.dli/logs/ |
| P2 | Performance Stats | Per-command response time tracking |
| P2 | Retry Tracking | Maintain same trace ID across retries |

---

## Related Documents

- [AUDIT_FEATURE.md](./AUDIT_FEATURE.md) - Feature specification
- [DEBUG_FEATURE.md](./DEBUG_FEATURE.md) - Debug feature (prerequisite)
- [Server AUDIT_FEATURE.md](../../project-basecamp-server/features/AUDIT_FEATURE.md) - Server audit implementation
- [../docs/PATTERNS.md](../docs/PATTERNS.md) - Development patterns

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| v1.0.0 | 2026-01-10 | Phase 1 MVP complete - TraceContext, TracedHttpClient, @with_trace, TraceMode config |
