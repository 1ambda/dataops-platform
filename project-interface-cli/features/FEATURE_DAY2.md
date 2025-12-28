# Day 2: dli CLI 구현 가이드

## 구현 상태

| 항목 | 상태 |
|------|------|
| 완료일 | 2025-12-29 |
| 테스트 | 442 tests passed (core + CLI) |
| 코드 품질 | pyright 0 errors, ruff 0 errors |

### 구현된 파일

| 파일 | 라인 수 | 설명 |
|------|--------|------|
| `commands/utils.py` | ~120 | 파라미터 파싱, 출력 헬퍼 함수 |
| `commands/metric.py` | ~450 | metric 서브커맨드 (list, get, run, validate, register) |
| `commands/dataset.py` | ~440 | dataset 서브커맨드 (list, get, run, validate, register) |
| `commands/server.py` | ~120 | server 서브커맨드 (config, status) |
| `core/client.py` | ~230 | Basecamp Server API 클라이언트 (mock mode 지원) |
| `tests/cli/test_metric_cmd.py` | ~230 | metric 커맨드 테스트 |
| `tests/cli/test_dataset_cmd.py` | ~235 | dataset 커맨드 테스트 |
| `tests/cli/test_server_cmd.py` | ~66 | server 커맨드 테스트 |
| `tests/core/test_client.py` | ~194 | BasecampClient 테스트 |

---

## 핵심 개념

CLI는 `core/` 라이브러리를 호출하는 얇은 레이어입니다.

```
dli metric run user_summary -p date=2024-01-01
       │
       ▼
  dli.commands.metric  (CLI layer - 파라미터 파싱, 출력 포맷)
       │
       ▼
  dli.core.service  (MetricService - 비즈니스 로직)
       │
       ▼
  dli.core.executor  (MockExecutor / 실행 엔진)
```

**Server 연동 흐름:**
```
dli metric list --source server
       │
       ▼
  dli.commands.metric  (CLI layer)
       │
       ▼
  dli.core.client  (BasecampClient - API 호출)
       │
       ▼
  Basecamp Server  (http://localhost:8081)
```

---

## 주요 변경사항 (Day 2 기준)

### 1. 일관된 커맨드 구조로 리팩토링

기존 `dli query run` 패턴에서 **리소스 기반 서브커맨드**로 변경:

```bash
# 이전 (Day 2 초기 설계)
dli query list --type metric
dli query run daily_retention -p date=2024-01-01

# 현재 (리팩토링 후)
dli metric list
dli metric run user_summary -p date=2024-01-01
dli dataset list
dli dataset run daily_clicks -p execution_date=2024-01-01
```

### 2. Basecamp Server 연동 지원

- `dli.yaml`에 `server` 섹션 추가
- `BasecampClient`로 Mock API 지원 (실제 서버 연동 준비)
- `--source local|server` 옵션으로 데이터 소스 선택

### 3. 새로운 서브커맨드 체계

| 서브커맨드 | 설명 |
|------------|------|
| `dli metric list/get/run/validate/register` | Metric 관리 |
| `dli dataset list/get/run/validate/register` | Dataset 관리 |
| `dli server config/status` | 서버 설정 및 상태 확인 |

---

## 디렉토리 구조

```
src/dli/
├── commands/
│   ├── __init__.py       # 커맨드 exports
│   ├── utils.py          # 파라미터 파싱, 출력 헬퍼
│   ├── metric.py         # metric 서브커맨드
│   ├── dataset.py        # dataset 서브커맨드
│   ├── server.py         # server 서브커맨드
│   ├── validate.py       # SQL 검증 (기존)
│   ├── render.py         # 템플릿 렌더링 (기존)
│   ├── version.py        # 버전 표시 (기존)
│   └── info.py           # 환경 정보 (기존)
├── core/
│   ├── client.py         # Basecamp Server API 클라이언트 (신규)
│   ├── config.py         # 프로젝트 설정 (server 설정 추가)
│   ├── service.py        # MetricService, DatasetService
│   └── ...
└── main.py               # Typer 앱 (서브커맨드 등록 업데이트)
```

---

## 1. Server 설정 (dli.yaml)

```yaml
# dli.yaml
version: "1.0"
name: "my-project"

# Basecamp server configuration
server:
  url: "http://localhost:8081"
  timeout: 30
  # api_key: "your-api-key-here"  # Optional

# 기존 설정
defaults:
  owner: "data-team@example.com"
  team: "@data-eng"
```

### config.py 변경사항

```python
# dli/core/config.py

@property
def server_url(self) -> str | None:
    """Get the Basecamp server URL."""
    return self._data.get("server", {}).get("url")

@property
def server_timeout(self) -> int:
    """Get the server request timeout in seconds."""
    return self._data.get("server", {}).get("timeout", 30)

@property
def server_api_key(self) -> str | None:
    """Get the server API key for authentication."""
    return self._data.get("server", {}).get("api_key")
```

---

## 2. BasecampClient (core/client.py)

서버 API 통신을 위한 클라이언트. 현재는 Mock 모드로 구현.

```python
from dataclasses import dataclass
from typing import Any

@dataclass
class ServerConfig:
    """Server connection configuration."""
    url: str = "http://localhost:8081"
    timeout: int = 30
    api_key: str | None = None

@dataclass
class ServerResponse:
    """Response from server API."""
    success: bool
    data: Any = None
    error: str | None = None
    status_code: int = 200

class BasecampClient:
    """Client for Basecamp Server API."""

    def __init__(self, config: ServerConfig, mock_mode: bool = False):
        self.config = config
        self.mock_mode = mock_mode

    def health_check(self) -> ServerResponse:
        """Check server health."""
        if self.mock_mode:
            return ServerResponse(success=True, data={"status": "healthy"})
        # TODO: Implement real HTTP call

    def list_metrics(self, tag: str | None = None) -> ServerResponse:
        """List metrics from server."""
        ...

    def get_metric(self, name: str) -> ServerResponse:
        """Get metric details."""
        ...

    def register_metric(self, spec_data: dict) -> ServerResponse:
        """Register a new metric."""
        ...

    # Dataset methods: list_datasets, get_dataset, register_dataset
```

---

## 3. Metric 서브커맨드 (commands/metric.py)

### 커맨드 목록

```bash
dli metric list [--source local|server] [--tag TAG] [--format table|json]
dli metric get <name> [--source local|server] [--format table|json]
dli metric run <name> -p key=value [-o table|json|csv] [--dry-run]
dli metric validate <name> -p key=value [--show-sql]
dli metric register <name>
```

### 구현 예시

```python
import typer
from typing import Annotated
from pathlib import Path
from rich.console import Console

from dli.commands.utils import parse_params, print_error, print_success, print_sql
from dli.core.service import MetricService
from dli.core.client import BasecampClient, ServerConfig

metric_app = typer.Typer(name="metric", help="Manage metrics.")
console = Console()


@metric_app.command("list")
def list_metrics(
    source: Annotated[str, typer.Option("--source", "-s")] = "local",
    tag: Annotated[str | None, typer.Option("--tag", "-t")] = None,
    output_format: Annotated[str, typer.Option("--format", "-f")] = "table",
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    """List available metrics."""
    if source == "server":
        client = _get_client(path)
        response = client.list_metrics(tag=tag)
        # ... display server data
    else:
        service = _load_metric_service(path)
        metrics = service.list_metrics()
        # ... display local data


@metric_app.command("run")
def run_metric(
    name: Annotated[str, typer.Argument(help="Metric name")],
    params: Annotated[list[str], typer.Option("--param", "-p")] = [],
    output: Annotated[str, typer.Option("--output", "-o")] = "table",
    dry_run: Annotated[bool, typer.Option("--dry-run")] = False,
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    """Execute a metric query (SELECT)."""
    param_dict = parse_params(params)
    service = _load_metric_service(path)

    if dry_run:
        result = service.validate(name, param_dict)
        # ... show validation result
    else:
        result = service.execute(name, param_dict)
        # ... show execution result
```

---

## 4. Dataset 서브커맨드 (commands/dataset.py)

Metric과 동일한 구조, DML 실행 관련 옵션 추가:

```bash
dli dataset list [--source local|server] [--tag TAG] [--format table|json]
dli dataset get <name> [--source local|server] [--format table|json]
dli dataset run <name> -p key=value [--dry-run] [--skip-pre] [--skip-post]
dli dataset validate <name> -p key=value [--show-sql]
dli dataset register <name>
```

### Dataset 전용 옵션

```python
@dataset_app.command("run")
def run_dataset(
    name: Annotated[str, typer.Argument()],
    params: Annotated[list[str], typer.Option("--param", "-p")] = [],
    dry_run: Annotated[bool, typer.Option("--dry-run")] = False,
    skip_pre: Annotated[bool, typer.Option("--skip-pre")] = False,
    skip_post: Annotated[bool, typer.Option("--skip-post")] = False,
    show_sql: Annotated[bool, typer.Option("--show-sql")] = False,
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    """Execute a dataset (DML operations)."""
    ...
```

---

## 5. Server 서브커맨드 (commands/server.py)

```bash
dli server config [--format table|json]
dli server status
```

```python
@server_app.command("config")
def show_config(
    output_format: Annotated[str, typer.Option("--format", "-f")] = "table",
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    """Show server configuration."""
    config = _get_project_config(path)
    if not config:
        console.print("[yellow]No dli.yaml found.[/yellow]")
        return
    # Display server URL, timeout, etc.


@server_app.command("status")
def check_status(
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    """Check server health status."""
    client = _get_client(path)
    response = client.health_check()
    if response.success:
        print_success(f"Server is healthy: {response.data}")
    else:
        print_error(f"Server check failed: {response.error}")
```

---

## 6. main.py 업데이트

```python
# dli/main.py

from dli.commands.metric import metric_app
from dli.commands.dataset import dataset_app
from dli.commands.server import server_app

# 서브커맨드 등록
app.add_typer(metric_app, name="metric")
app.add_typer(dataset_app, name="dataset")
app.add_typer(server_app, name="server")
```

---

## CLI 사용 예시

```bash
# Metric 관리
$ dli metric list
$ dli metric list --source server --tag reporting
$ dli metric list --format json
$ dli metric get iceberg.reporting.user_summary
$ dli metric run iceberg.reporting.user_summary -p date=2024-01-01
$ dli metric run iceberg.reporting.user_summary -p date=2024-01-01 -o json
$ dli metric validate iceberg.reporting.user_summary -p date=2024-01-01 --show-sql
$ dli metric register iceberg.reporting.user_summary

# Dataset 관리
$ dli dataset list
$ dli dataset list --source server
$ dli dataset get iceberg.analytics.daily_clicks
$ dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01
$ dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01 --dry-run
$ dli dataset validate iceberg.analytics.daily_clicks -p execution_date=2024-01-01
$ dli dataset register iceberg.analytics.daily_clicks

# Server 관리
$ dli server config
$ dli server config --format json
$ dli server status
```

---

## 테스트 구조

```bash
tests/
├── cli/
│   ├── test_main.py           # 메인 CLI 테스트
│   ├── test_metric_cmd.py     # metric 서브커맨드 테스트 (신규)
│   ├── test_dataset_cmd.py    # dataset 서브커맨드 테스트 (신규)
│   ├── test_server_cmd.py     # server 서브커맨드 테스트 (신규)
│   └── test_utils.py          # 유틸리티 함수 테스트
├── core/
│   ├── test_client.py         # BasecampClient 테스트 (신규)
│   ├── test_service.py        # MetricService/DatasetService 테스트
│   └── ...
└── fixtures/
    └── sample_project/
        ├── dli.yaml           # server 설정 포함
        ├── datasets/feed/...
        └── metrics/reporting/...
```

---

## Day 2 체크리스트

- [x] dli.yaml에 server 설정 추가
- [x] BasecampClient 구현 (Mock mode)
- [x] metric 서브커맨드 (list, get, run, validate, register)
- [x] dataset 서브커맨드 (list, get, run, validate, register)
- [x] server 서브커맨드 (config, status)
- [x] 기존 query/list 커맨드 제거
- [x] 테스트 작성 및 통과 (442 tests)
- [x] expert-python 리뷰 완료

### 향후 개선사항 (Future Work)

1. **BasecampClient HTTP 구현**: Mock 모드에서 실제 HTTP 호출로 전환
2. **DRY 리팩토링**: metric.py/dataset.py 공통 로직 추출
3. **타입 안전성**: `Literal["local", "server"]` 등 제약 타입 추가
4. **에러 처리 세분화**: 구체적인 예외 타입 정의

---

## 참고 코드

| 참고 | URL |
|------|-----|
| dbt CLI 구조 | https://github.com/dbt-labs/dbt-core/tree/main/core/dbt/cli |
| Typer 문서 | https://typer.tiangolo.com/ |
| Rich 문서 | https://rich.readthedocs.io/ |
