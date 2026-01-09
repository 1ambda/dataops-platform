# FEATURE: CLI Audit - Trace ID and Request Tracking

| Attribute | Value |
|-----------|-------|
| **Version** | v1.2.0 |
| **Status** | Implemented (Phase 1 MVP) |
| **Created** | 2026-01-09 |
| **Last Updated** | 2026-01-10 |
| **Server Dependency** | project-basecamp-server AUDIT_FEATURE.md v1.2.0 |
| **References** | DEBUG_FEATURE.md, Server AUDIT_FEATURE.md |
| **Review** | feature-interface-cli Agent reviewed |

---

## 1. Overview

### 1.1 Purpose

CLI 명령어 실행을 추적하여 문제 발생 시 디버깅 및 사용 패턴 분석을 지원합니다.

**Key Use Cases:**

| Use Case | Description |
|----------|-------------|
| **에러 추적** | 에러 발생 시 Trace ID로 서버 로그 조회 |
| **사용 패턴 분석** | 어떤 명령어가 언제, 얼마나 사용되는지 분석 |
| **문의 대응** | 사용자 문의 시 Trace ID로 빠른 이슈 확인 |
| **성능 모니터링** | 명령어별 응답 시간 추적 |

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **서버 활용** | CLI는 Trace ID 생성/전달만, 기록은 서버 audit_log 활용 |
| **최소 침습** | 기존 코드 변경 최소화, HTTP 클라이언트 레벨에서 처리 |
| **설정 가능** | config로 Trace ID 표시 여부 제어 |
| **표준 준수** | X-Trace-Id 헤더, UUID 형식 사용 |

### 1.3 Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              CLI (dli)                                    │
│  ┌─────────────┐    ┌──────────────────┐    ┌────────────────────────┐  │
│  │   Command    │───▶│  TraceContext    │───▶│   HTTP Client          │  │
│  │ (run, debug) │    │ (UUID 생성/보관)  │    │ (X-Trace-Id 헤더 추가)  │  │
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

## 2. Design Decisions

### 2.1 Trace ID

| Item | Decision | Rationale |
|------|----------|-----------|
| **생성 단위** | 명령어 1회 실행당 1개 UUID | 관련 API 호출들을 그룹화하여 추적 용이 |
| **포맷** | 순수 UUID (36자) | 서버 컬럼 호환성, 표준 형식 |
| **헤더** | X-Trace-Id | 서버 TraceIdFilter와 동일 |
| **저장** | 서버 audit_log.trace_id | CLI는 저장 안함, 서버에 위임 |

### 2.2 User-Agent

| Item | Decision | Rationale |
|------|----------|-----------|
| **포맷** | `dli/{version} ({os}; Python/{py_version}) command/{cmd}` | 풍부한 메타데이터 전달 |
| **서버 저장** | client_metadata JSON 컬럼 | 구조화된 파싱 결과 저장 |

**User-Agent 예시:**
```
dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill
dli/0.9.0 (linux; Python/3.12.0) command/run
dli/0.9.0 (windows; Python/3.11.5) command/debug
```

### 2.3 Config 설정

**TraceMode Enum** (in `src/dli/models/common.py`):
```python
class TraceMode(str, Enum):
    """Trace ID display mode for CLI output."""
    ALWAYS = "always"      # Show trace ID in all output
    ERROR_ONLY = "error_only"  # Show only on errors
    NEVER = "never"        # Never show (server logs only)
```

**Config Schema** (in `~/.dli/config.yaml`):
```yaml
# ~/.dli/config.yaml
trace: always  # always, error_only, never
```

| 값 | 동작 |
|---|------|
| `always` | 모든 출력에 Trace ID 표시 (기본값) |
| `error_only` | 에러 발생 시에만 표시 |
| `never` | 표시 안함 (서버 로그로만 추적) |

**CLI 플래그 오버라이드:**
```bash
dli run metric.yaml --trace        # 강제 표시
dli run metric.yaml --no-trace     # 강제 숨김
```

### 2.4 HTTP Client Integration Strategy

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **통합 방식** | BasecampClient 내부 조합 | 기존 mock_mode 로직 유지하면서 trace 기능 추가 |
| **책임 분리** | TracedHttpClient = transport, BasecampClient = API | 각 계층 역할 명확화 |
| **의존성** | httpx 사용 | 기존 의존성 활용, async 지원 가능 |

```python
# client.py 통합 패턴
class BasecampClient:
    def __init__(self, config: ServerConfig, mock_mode: bool = True):
        self._http = TracedHttpClient(config.url, config.timeout) if not mock_mode else None
        # ... existing mock logic
```

---

## 3. Implementation

### 3.1 TraceContext Class

**File:** `src/dli/core/trace.py`

```python
"""Trace ID context for CLI command execution.

This module provides trace context management for CLI commands,
enabling request tracking across server API calls.
"""

from __future__ import annotations

import platform
import uuid
from contextvars import ContextVar
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    pass

__all__ = ["TraceContext", "get_current_trace", "with_trace"]

# Context variable for current trace - single trace per CLI command invocation.
# Note: Designed for synchronous CLI commands, not for concurrent/async patterns.
_current_trace: ContextVar[TraceContext | None] = ContextVar("current_trace", default=None)


def get_current_trace() -> TraceContext | None:
    """Get the current trace context (convenience function)."""
    return _current_trace.get()


@dataclass
class TraceContext:
    """Trace context for a CLI command execution.

    Attributes:
        trace_id: Unique identifier for this command execution
        command: CLI command being executed (e.g., "workflow backfill")
        cli_version: dli CLI version
        os_name: Operating system name
        python_version: Python version
    """

    trace_id: str
    command: str
    cli_version: str
    os_name: str
    python_version: str

    def __repr__(self) -> str:
        """Return concise representation for debugging."""
        return f"TraceContext(trace_id={self.short_id}, command={self.command!r})"

    @classmethod
    def create(cls, command: str) -> TraceContext:
        """Create a new trace context for a command.

        Args:
            command: CLI command being executed (e.g., "workflow backfill")

        Returns:
            New TraceContext instance
        """
        from dli import __version__

        return cls(
            trace_id=str(uuid.uuid4()),
            command=command,
            cli_version=__version__,
            os_name=platform.system().lower(),
            python_version=platform.python_version(),
        )

    @property
    def user_agent(self) -> str:
        """Generate User-Agent header value.

        Format: dli/{version} ({os}; Python/{py_version}) command/{cmd}
        """
        # command에서 공백을 하이픈으로 변환
        cmd_slug = self.command.replace(" ", "-")
        return (
            f"dli/{self.cli_version} "
            f"({self.os_name}; Python/{self.python_version}) "
            f"command/{cmd_slug}"
        )

    @property
    def short_id(self) -> str:
        """Return first 8 characters of trace_id for display."""
        return self.trace_id[:8]

    def set_current(self) -> None:
        """Set this context as the current trace context."""
        _current_trace.set(self)

    @classmethod
    def get_current(cls) -> TraceContext | None:
        """Get the current trace context."""
        return _current_trace.get()

    @classmethod
    def clear_current(cls) -> None:
        """Clear the current trace context."""
        _current_trace.set(None)
```

### 3.2 HTTP Client Integration

**File:** `src/dli/core/http.py`

```python
"""HTTP client with trace ID support.

This module provides a low-level HTTP transport with automatic
trace header injection. Used by BasecampClient for server communication.
"""

from __future__ import annotations

from typing import Any

import httpx

from dli.core.trace import TraceContext

__all__ = ["TracedHttpClient"]


class TracedHttpClient:
    """HTTP client that automatically adds trace headers.

    Responsibilities:
    - Low-level HTTP transport
    - Automatic X-Trace-Id header injection
    - User-Agent header with CLI metadata

    Note: BasecampClient uses this for actual API calls when not in mock mode.
    """

    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url
        self.timeout = timeout

    def __repr__(self) -> str:
        """Return representation for debugging."""
        return f"TracedHttpClient(base_url={self.base_url!r})"

    def _get_headers(self) -> dict[str, str]:
        """Get headers including trace ID if available."""
        headers: dict[str, str] = {}
        trace = TraceContext.get_current()
        if trace:
            headers["X-Trace-Id"] = trace.trace_id
            headers["User-Agent"] = trace.user_agent
        return headers

    def get(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make GET request with trace headers."""
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.get(path, headers=headers, **kwargs)

    def post(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make POST request with trace headers."""
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.post(path, headers=headers, **kwargs)

    def put(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make PUT request with trace headers."""
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.put(path, headers=headers, **kwargs)

    def delete(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make DELETE request with trace headers."""
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.delete(path, headers=headers, **kwargs)
```

### 3.3 Command Decorator

**File:** `src/dli/core/trace.py` (included in same file as TraceContext)

```python
"""Decorator for CLI commands with trace support."""

import functools
from typing import Callable, ParamSpec, TypeVar

from dli.core.trace import TraceContext

P = ParamSpec("P")
R = TypeVar("R")


def with_trace(command_name: str) -> Callable[[Callable[P, R]], Callable[P, R]]:
    """Decorator to add trace context to CLI commands.

    Args:
        command_name: Name of the command (e.g., "workflow backfill")

    Usage:
        @app.command()
        @with_trace("run")
        def run(file: str) -> None:
            ...

    Note: Place @with_trace AFTER @app.command() so it wraps the actual function.
    """

    def decorator(func: Callable[P, R]) -> Callable[P, R]:
        @functools.wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            trace = TraceContext.create(command_name)
            trace.set_current()
            try:
                return func(*args, **kwargs)
            finally:
                TraceContext.clear_current()

        return wrapper

    return decorator
```

**Re-export via `commands/base.py`:**
```python
# In src/dli/commands/base.py
from dli.core.trace import with_trace

__all__ = [..., "with_trace"]
```

### 3.4 Error Display

**File:** `src/dli/commands/utils.py` (extend existing print_error)

```python
"""Error handling with trace ID display.

Integrates with existing commands/utils.py print_error function.
"""

from rich.console import Console

from dli.core.trace import TraceContext, get_current_trace
from dli.exceptions import DLIError
from dli.models.common import TraceMode

console = Console()


def print_error(
    message: str,
    *,
    error_code: str | None = None,
    trace_mode: TraceMode = TraceMode.ALWAYS,
) -> None:
    """Print error message with optional trace ID.

    Args:
        message: Error message to display
        error_code: Optional DLI error code (e.g., "DLI-501")
        trace_mode: When to show trace ID (ALWAYS, ERROR_ONLY, NEVER)

    Examples:
        print_error("Connection failed")
        # Output: Connection failed

        print_error("Server error", error_code="DLI-501")
        # Output: [DLI-501] [trace:550e8400] Server error
    """
    trace = get_current_trace()
    parts: list[str] = []

    # Add error code if provided
    if error_code:
        parts.append(f"[{error_code}]")

    # Add trace ID based on mode
    if trace and trace_mode != TraceMode.NEVER:
        parts.append(f"[trace:{trace.short_id}]")

    # Add message
    parts.append(message)

    console.print(f"[red]{' '.join(parts)}[/red]")


def display_error(error: DLIError, trace_mode: TraceMode = TraceMode.ALWAYS) -> None:
    """Display DLI error with trace ID.

    Args:
        error: DLI error to display
        trace_mode: When to show trace ID
    """
    print_error(error.message, error_code=error.code.value, trace_mode=trace_mode)
```

**Integration Note:**
- `DLIError.__str__` remains unchanged (returns `[CODE] message`)
- `display_error()` is used in command error handlers
- `print_error()` is extended with trace support for non-DLIError messages

---

## 4. Usage Examples

### 4.1 CLI Output

**에러 발생 시 (trace: always):**
```bash
$ dli run metric.yaml
[DLI-501] [trace:550e8400] Server error: Connection refused

Trace ID를 사용하여 서버 로그를 확인하세요:
  trace_id: 550e8400-e29b-41d4-a716-446655440000
```

**성공 시 (trace: always):**
```bash
$ dli run metric.yaml
[trace:550e8400] Running metric: cpu_usage
✓ Execution completed (1.2s)
```

**--verbose 모드:**
```bash
$ dli run metric.yaml --verbose
[trace:550e8400] Running metric: cpu_usage
  → POST /api/v1/metrics/cpu_usage/run
  ← 200 OK (120ms)
✓ Execution completed (1.2s)
```

### 4.2 Config 설정

```yaml
# ~/.dli/config.yaml
server:
  url: https://basecamp.example.com
  timeout: 30

trace: always  # always, error_only, never
```

### 4.3 서버 Audit Log 조회

```sql
-- trace_id로 CLI 요청 조회
SELECT *
FROM audit_log
WHERE trace_id = '550e8400-e29b-41d4-a716-446655440000'
ORDER BY created_at;

-- 특정 CLI 버전의 요청 조회
SELECT *
FROM audit_log
WHERE JSON_EXTRACT(client_metadata, '$.version') = '0.9.0'
  AND JSON_EXTRACT(client_metadata, '$.name') = 'dli';

-- 명령어별 사용 통계
SELECT
  JSON_EXTRACT(client_metadata, '$.command') as command,
  COUNT(*) as count
FROM audit_log
WHERE client_type = 'CLI'
GROUP BY command
ORDER BY count DESC;
```

---

## 5. Files to Create/Modify

### 5.1 신규 생성

| File | Full Path | Description |
|------|-----------|-------------|
| `trace.py` | `src/dli/core/trace.py` | TraceContext, with_trace decorator |
| `http.py` | `src/dli/core/http.py` | TracedHttpClient 클래스 |

### 5.2 수정

| File | Full Path | Change |
|------|-----------|--------|
| `common.py` | `src/dli/models/common.py` | TraceMode enum 추가 |
| `client.py` | `src/dli/core/client.py` | TracedHttpClient 통합 |
| `utils.py` | `src/dli/commands/utils.py` | print_error에 trace 지원 추가 |
| `base.py` | `src/dli/commands/base.py` | with_trace re-export |
| `__init__.py` | `src/dli/__init__.py` | TraceContext, TraceMode public export |
| `commands/*.py` | `src/dli/commands/` | @with_trace 데코레이터 적용 |

### 5.3 Config 수정

| File | Full Path | Change |
|------|-----------|--------|
| Config schema | `src/dli/core/config.py` | trace 키 추가 (default: "always") |
| Config loader | `src/dli/core/config_loader.py` | trace 설정 로드 로직 |

---

## 6. Config Schema

### 6.1 New Config Key

```yaml
# Type: string
# Default: "always"
# Options: "always", "error_only", "never"
trace: always
```

### 6.2 Environment Variable

```bash
# Environment variable override
DLI_TRACE=error_only dli run metric.yaml
```

---

## 7. Testing Strategy

### 7.1 Test File Locations

| Test File | Full Path | Coverage |
|-----------|-----------|----------|
| `test_trace.py` | `tests/core/test_trace.py` | TraceContext, with_trace |
| `test_http.py` | `tests/core/test_http.py` | TracedHttpClient |
| `test_trace_integration.py` | `tests/cli/test_trace_integration.py` | CLI integration |

### 7.2 Unit Tests

**File:** `tests/core/test_trace.py`

| Test | Description |
|------|-------------|
| `test_trace_context_create` | TraceContext 생성 검증 |
| `test_trace_context_user_agent` | User-Agent 포맷 검증 |
| `test_trace_context_short_id` | Short ID (8자) 검증 |
| `test_trace_context_repr` | __repr__ 출력 검증 |
| `test_trace_context_set_get_clear` | context lifecycle 검증 |
| `test_with_trace_decorator` | 데코레이터 동작 검증 |
| `test_with_trace_decorator_cleanup_on_exception` | 예외 시 cleanup 검증 |

**File:** `tests/core/test_http.py`

| Test | Description |
|------|-------------|
| `test_traced_http_client_headers` | HTTP 헤더 추가 검증 |
| `test_traced_http_client_without_trace` | trace 없을 때 동작 검증 |
| `test_traced_http_client_repr` | __repr__ 출력 검증 |

### 7.3 Pytest Fixture Examples

```python
# tests/core/test_trace.py
import pytest
from dli.core.trace import TraceContext, with_trace


@pytest.fixture
def trace_context() -> TraceContext:
    """Create test trace context."""
    return TraceContext(
        trace_id="550e8400-e29b-41d4-a716-446655440000",
        command="workflow backfill",
        cli_version="0.9.0",
        os_name="darwin",
        python_version="3.12.1",
    )


def test_trace_context_short_id(trace_context: TraceContext) -> None:
    """Verify short_id returns first 8 characters."""
    assert len(trace_context.short_id) == 8
    assert trace_context.short_id == "550e8400"


def test_trace_context_user_agent(trace_context: TraceContext) -> None:
    """Verify User-Agent format."""
    expected = "dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill"
    assert trace_context.user_agent == expected


def test_trace_context_repr(trace_context: TraceContext) -> None:
    """Verify repr output."""
    assert "550e8400" in repr(trace_context)
    assert "workflow backfill" in repr(trace_context)


def test_trace_context_lifecycle() -> None:
    """Verify set/get/clear context lifecycle."""
    assert TraceContext.get_current() is None

    trace = TraceContext.create("test")
    trace.set_current()
    assert TraceContext.get_current() is trace

    TraceContext.clear_current()
    assert TraceContext.get_current() is None


def test_with_trace_decorator_cleanup_on_exception() -> None:
    """Verify trace cleanup on exception."""
    @with_trace("failing")
    def failing_command() -> None:
        raise ValueError("Test error")

    with pytest.raises(ValueError):
        failing_command()

    # Context should be cleared even after exception
    assert TraceContext.get_current() is None
```

### 7.4 Integration Tests

**File:** `tests/cli/test_trace_integration.py`

| Test | Description |
|------|-------------|
| `test_error_display_with_trace` | 에러 메시지에 trace ID 포함 검증 |
| `test_config_trace_setting` | config 설정 적용 검증 |
| `test_cli_flag_override` | --trace/--no-trace 플래그 검증 |
| `test_trace_mode_never` | trace: never 설정 시 표시 안함 검증 |
| `test_trace_mode_error_only` | trace: error_only 설정 시 에러만 표시 검증 |

---

## 8. Implementation Phases

### Phase 1 (MVP)

| Priority | Task | Description |
|----------|------|-------------|
| P0 | TraceContext 구현 | UUID 생성, User-Agent 포맷 |
| P0 | HTTP Client 수정 | X-Trace-Id 헤더 자동 추가 |
| P0 | 에러 출력 수정 | 에러 메시지에 trace ID 포함 |
| P1 | Config 설정 추가 | trace 키, 기본값 always |
| P1 | CLI 플래그 추가 | --trace, --no-trace |

### Phase 2 (Enhancement)

| Priority | Task | Description |
|----------|------|-------------|
| P2 | 로컬 로깅 | ~/.dli/logs/에 명령어 기록 |
| P2 | 성능 통계 | 명령어별 평균 응답 시간 |
| P2 | 재시도 추적 | 재시도 시 동일 trace ID 유지 |

---

## 9. Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.1.0 | 2026-01-09 | - | feature-interface-cli Agent review incorporated |
| 1.0.0 | 2026-01-09 | - | Initial specification |

### v1.1.0 Changes (Agent Review)

| Category | Change | Rationale |
|----------|--------|-----------|
| **Type Safety** | Added TraceMode enum | Proper type instead of string literals |
| **Integration** | Clarified BasecampClient integration strategy | Composition pattern with TracedHttpClient |
| **File Paths** | Added explicit file locations | Clear implementation guidance |
| **Testing** | Added pytest fixture examples | Testability best practices |
| **Exports** | Added `__all__` and `__repr__` | Python conventions compliance |
| **Error Display** | Integrated with commands/utils.py print_error | Consistent with existing patterns |
| **Documentation** | Added ContextVar thread-safety note | Future consideration clarity |

---

## 10. Related Documents

- **[DEBUG_FEATURE.md](./DEBUG_FEATURE.md)** - 환경 진단 기능
- **[Server AUDIT_FEATURE.md](../../project-basecamp-server/features/AUDIT_FEATURE.md)** - 서버 Audit 기능 (v1.2.0)
- **[../docs/PATTERNS.md](../docs/PATTERNS.md)** - 개발 패턴
