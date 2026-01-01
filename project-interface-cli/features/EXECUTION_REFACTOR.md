# REFACTOR: Execution Model

> **Version:** 1.1.0
> **Status:** Reviewed
> **Last Updated:** 2025-12-30

---

## 1. 개요

### 1.1 목적

DLI CLI의 실행 모델을 명확히 정의하고, 로컬 실행과 서버 실행을 일관되게 지원하여 데이터 개발자가 다양한 환경에서 효율적으로 작업할 수 있도록 합니다.

### 1.2 핵심 원칙

1. **일관된 실행 모델**: 모든 실행 가능 커맨드는 동일한 패턴으로 로컬/서버 실행을 지원
2. **보안 우선**: 직접 쿼리 실행이 불가한 환경에서도 서버를 통한 안전한 실행 제공
3. **개발자 경험**: 로컬 개발 시 빠른 피드백, 프로덕션 환경에서는 안전한 서버 실행

### 1.3 주요 변경사항

| 항목 | 현재 | 변경 후 |
|------|------|---------|
| dataset/metric run | MockExecutor 사용 (실제 DB 연결 없음) | 실제 쿼리 실행 (로컬/서버) |
| 실행 위치 선택 | 커맨드별 상이 | 통일된 `--local`/`--server` 옵션 |
| 결과 출력 | 미리보기 | 전체 결과 + 파일 저장 옵션 |
| Library API | mock_mode만 | execution_mode (local/server/mock) |

### 1.4 용어 정의

> **중요**: `DataSource`와 `ExecutionMode`는 다른 개념입니다.

| 용어 | 정의 |
|------|------|
| **DataSource** | Spec 파일을 어디서 읽을지 (`local`: 로컬 YAML, `server`: Basecamp API) |
| **ExecutionMode** | 쿼리를 어디서 실행할지 (`local`: BigQuery 직접, `server`: Basecamp API, `mock`: 테스트용) |
| 로컬 실행 | DLI CLI에서 BigQuery/Trino에 직접 연결하여 쿼리 실행 |
| 서버 실행 | Basecamp Server API를 통해 원격에서 쿼리 실행 |
| 렌더링 | SQL 템플릿의 파라미터 치환 및 METRIC 확장 (실행 없음) |

---

## 2. 실행 모델 아키텍처

### 2.1 커맨드별 실행 위치

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DLI CLI Commands                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              LOCAL ONLY (쿼리 실행 없음)                      │    │
│  │                                                              │    │
│  │  • transpile    - SQL 렌더링/변환 (SQLglot)                  │    │
│  │  • validate     - Spec/SQL 유효성 검증                       │    │
│  │  • lineage      - 의존성 분석 (로컬 파일 기반)                │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              LOCAL + SERVER (쿼리 실행)                       │    │
│  │                                                              │    │
│  │  • dataset run  - Dataset 쿼리 실행                          │    │
│  │  • metric run   - Metric 쿼리 실행                           │    │
│  │  • quality run  - 데이터 품질 테스트 실행                     │    │
│  │                                                              │    │
│  │  [--local (기본)] → ExecutorFactory → BigQueryExecutor       │    │
│  │  [--server]       → ExecutorFactory → ServerExecutor         │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              SERVER ONLY (Airflow/Catalog)                    │    │
│  │                                                              │    │
│  │  • workflow run/backfill  - Airflow DAG 실행                 │    │
│  │  • catalog                - 메타데이터 조회                   │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 실행 흐름

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│  CLI Command │────▶│ ExecutionMode│────▶│ ExecutorFactory  │
│              │     │  Resolver    │     │                  │
└──────────────┘     └──────────────┘     └──────────────────┘
                            │                      │
                     ┌──────┴──────┐               │
                     ▼             ▼               ▼
              ┌──────────┐  ┌───────────┐  ┌─────────────────┐
              │  LOCAL   │  │  SERVER   │  │ Query Engine    │
              │          │  │           │  │ (BigQuery/Trino)│
              │ BigQuery │  │ Server    │  │                 │
              │ Executor │  │ Executor  │  │ OR              │
              │          │  │           │  │                 │
              │          │  │           │  │ Basecamp Server │
              └──────────┘  └───────────┘  └─────────────────┘
```

### 2.3 ExecutionMode 결정 로직

```python
def resolve_execution_mode(
    local_flag: bool,
    server_flag: bool,
    config: DLIConfig,
) -> ExecutionMode:
    """
    우선순위:
    1. CLI 옵션 (--local / --server)
    2. 환경 변수 (DLI_EXECUTION_MODE)
    3. Config 파일 설정 (execution.default_mode)
    4. 기본값: LOCAL
    """
    if server_flag:
        return ExecutionMode.SERVER
    if local_flag:
        return ExecutionMode.LOCAL
    # pydantic_settings가 환경변수와 config를 자동으로 처리
    return config.execution.default_mode
```

---

## 3. 커맨드별 상세 설계

### 3.1 dataset/metric run

**현재 동작**: MockExecutor 사용 (3단계 실행 흐름은 있으나 실제 DB 연결 없음)

**변경 후 동작**: ExecutorFactory를 통해 실제 쿼리 실행

#### CLI 인터페이스

```bash
# 로컬 실행 (기본)
dli dataset run <name> [--param key=value]

# 서버 실행
dli dataset run <name> --server [--param key=value]

# 결과 파일 저장
dli dataset run <name> --output results.csv
dli dataset run <name> --output results.json --format json

# dry-run (렌더링만, 실행 안함)
dli dataset run <name> --dry-run
```

#### 옵션 정의

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `--local` | flag | ✅ | 로컬 Query Engine 직접 실행 |
| `--server` | flag | | Basecamp Server를 통한 실행 |
| `--param`, `-p` | key=value | | 파라미터 전달 (다중) |
| `--output`, `-o` | path | | 결과 저장 파일 경로 |
| `--format` | csv/json | csv | 출력 포맷 |
| `--dry-run` | flag | | SQL 렌더링만, 실행 안함 |
| `--timeout` | int | 300 | 실행 타임아웃 (초) |

#### 결과 출력

```
┌────────────────────────────────────────────────────────────┐
│ Dataset: iceberg.analytics.daily_clicks                    │
│ Execution: LOCAL (BigQuery)                                │
│ Duration: 2.34s | Rows: 1,523                              │
├────────────────────────────────────────────────────────────┤
│ date       │ click_count │ unique_users │ conversion_rate  │
├────────────┼─────────────┼──────────────┼──────────────────┤
│ 2025-01-01 │ 45,231      │ 12,456       │ 0.0234           │
│ 2025-01-02 │ 48,102      │ 13,021       │ 0.0251           │
│ ...        │ ...         │ ...          │ ...              │
└────────────────────────────────────────────────────────────┘

✓ Results saved to: results.csv (--output 사용 시)
```

### 3.2 quality run

**현재 동작**: `--on-server` 플래그로 실행 위치 선택

**변경 후 동작**: `--local`/`--server` 통일 패턴 적용

#### CLI 인터페이스

```bash
# 로컬 실행 (기본)
dli quality run <resource_name>

# 서버 실행
dli quality run <resource_name> --server

# 특정 테스트만 실행
dli quality run <resource_name> --test not_null_user_id

# 실패 시 즉시 중단
dli quality run <resource_name> --fail-fast
```

### 3.3 workflow run/backfill

**유지**: 서버 전용 (Airflow를 통한 실행)

```bash
# Adhoc 실행 (서버)
dli workflow run <dataset_name> [--param key=value]

# Backfill 실행 (서버)
dli workflow backfill <dataset_name> --start 2025-01-01 --end 2025-01-07
```

---

## 4. Library API 설계

### 4.1 ExecutionMode 및 ExecutionContext 확장

> **중요**: 현재 `ExecutionContext`는 `pydantic_settings.BaseSettings`를 상속합니다. 이 패턴을 유지하면서 `execution_mode`를 추가합니다.

```python
# models/common.py

from enum import Enum
from typing import Any
from pathlib import Path

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ExecutionMode(str, Enum):
    """쿼리 실행 위치."""
    LOCAL = "local"    # 로컬 Query Engine 직접 실행
    SERVER = "server"  # Basecamp Server를 통한 실행
    MOCK = "mock"      # 테스트용 Mock 실행


class ExecutionContext(BaseSettings):
    """Library API execution context.

    환경 변수 DLI_ 접두사로 자동 로딩됩니다.
    예: DLI_EXECUTION_MODE=server, DLI_PROJECT_PATH=/path/to/project
    """

    model_config = SettingsConfigDict(
        env_prefix="DLI_",
        env_file=".env",
        extra="ignore",
    )

    # 프로젝트 설정
    project_path: Path | None = Field(
        default=None,
        description="Project root path for local spec files",
    )

    # 서버 연결
    server_url: str | None = Field(
        default=None,
        description="Basecamp server URL",
    )
    api_token: str | None = Field(
        default=None,
        description="API authentication token",
    )

    # 실행 모드 (NEW)
    execution_mode: ExecutionMode = Field(
        default=ExecutionMode.LOCAL,
        description="Query execution mode (local/server/mock)",
    )

    # 쿼리 설정
    dialect: str = Field(default="trino", description="SQL dialect")
    timeout: int = Field(default=300, ge=1, le=3600, description="Timeout in seconds")
    dry_run: bool = Field(default=False, description="Dry-run mode")

    # 파라미터
    parameters: dict[str, Any] = Field(default_factory=dict)

    # DEPRECATED: mock_mode → execution_mode 마이그레이션
    @property
    def mock_mode(self) -> bool:
        """Deprecated. Use execution_mode == ExecutionMode.MOCK instead."""
        import warnings
        warnings.warn(
            "mock_mode is deprecated. Use execution_mode == ExecutionMode.MOCK",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.execution_mode == ExecutionMode.MOCK

    @model_validator(mode="before")
    @classmethod
    def _migrate_mock_mode(cls, data: dict[str, Any]) -> dict[str, Any]:
        """기존 mock_mode=True 사용자를 위한 자동 마이그레이션."""
        if isinstance(data, dict) and data.get("mock_mode") is True:
            data.setdefault("execution_mode", ExecutionMode.MOCK)
        return data
```

### 4.2 ExecutorFactory 패턴

```python
# core/executor.py

from typing import Protocol, Any


class QueryExecutor(Protocol):
    """쿼리 실행기 프로토콜 (DI를 위한 인터페이스)."""

    def execute(self, sql: str, params: dict[str, Any] | None = None) -> ExecutionResult:
        ...

    def test_connection(self) -> bool:
        ...


class ExecutorFactory:
    """실행 모드에 따른 Executor 생성."""

    @staticmethod
    def create(
        mode: ExecutionMode,
        context: ExecutionContext,
    ) -> QueryExecutor:
        match mode:
            case ExecutionMode.LOCAL:
                engine = context.dialect
                if engine == "bigquery":
                    from dli.adapters.bigquery import BigQueryExecutor
                    return BigQueryExecutor(
                        project=context.parameters.get("project", ""),
                        location=context.parameters.get("location", "US"),
                    )
                raise ValueError(f"Unsupported engine: {engine}")

            case ExecutionMode.SERVER:
                from dli.core.executors import ServerExecutor
                return ServerExecutor(
                    server_url=context.server_url,
                    api_token=context.api_token,
                )

            case ExecutionMode.MOCK:
                return MockExecutor()
```

### 4.3 API 사용 예시

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode

# 로컬 실행
ctx = ExecutionContext(
    project_path=Path("/opt/airflow/dags/models"),
    execution_mode=ExecutionMode.LOCAL,
    dialect="bigquery",
)
api = DatasetAPI(context=ctx)
result = api.run("my_dataset", parameters={"date": "2025-01-01"})

# 서버 실행
ctx = ExecutionContext(
    project_path=Path("/opt/airflow/dags/models"),
    execution_mode=ExecutionMode.SERVER,
    server_url="https://basecamp.example.com",
)
api = DatasetAPI(context=ctx)
result = api.run("my_dataset", parameters={"date": "2025-01-01"})

# 테스트 시 Mock executor 주입 (DI)
mock_executor = MockExecutor(return_value=QueryResult(rows=[{"id": 1}]))
api = DatasetAPI(context=ctx, executor=mock_executor)
result = api.run("test_dataset")

# 결과 접근
print(f"Rows: {result.row_count}")
print(f"Duration: {result.duration_ms}ms")
for row in result.data:
    print(row)
```

---

## 5. Config 설정

### 5.1 실행 관련 설정 스키마 (Pydantic)

```python
# core/config.py

from pydantic import BaseModel, Field
from typing import Literal


class BigQueryConfig(BaseModel):
    """BigQuery 연결 설정."""
    project: str = Field(..., description="GCP project ID")
    location: str = Field(default="US", description="Dataset location")


class LocalExecutionConfig(BaseModel):
    """로컬 실행 설정."""
    engine: Literal["bigquery", "trino"] = "bigquery"
    bigquery: BigQueryConfig | None = None


class ServerExecutionConfig(BaseModel):
    """서버 실행 설정."""
    url: str = Field(..., description="Basecamp server URL")
    api_key: str | None = Field(default=None, description="API key")


class ExecutionConfig(BaseModel):
    """실행 설정."""
    default_mode: ExecutionMode = ExecutionMode.LOCAL
    timeout: int = Field(default=300, ge=1, le=3600)


class DLIConfig(BaseModel):
    """DLI 전체 설정."""
    execution: ExecutionConfig = Field(default_factory=ExecutionConfig)
    local: LocalExecutionConfig = Field(default_factory=LocalExecutionConfig)
    server: ServerExecutionConfig | None = None
```

### 5.2 YAML 설정 예시

```yaml
# ~/.dli/config.yaml

execution:
  default_mode: local
  timeout: 300

local:
  engine: bigquery
  bigquery:
    project: my-gcp-project
    location: US

server:
  url: https://basecamp.example.com
  api_key: ${BASECAMP_API_KEY}
```

### 5.3 설정 우선순위

```
1. CLI 옵션         (--local, --server, --timeout)
2. 환경 변수        (DLI_EXECUTION_MODE, DLI_TIMEOUT)
3. 프로젝트 설정     (./dli.yaml)
4. 사용자 설정       (~/.dli/config.yaml)
5. 기본값           (local, 300초)
```

---

## 6. 에러 처리

### 6.1 실행 관련 에러 코드

> **중요**: 현재 `exceptions.py`의 ErrorCode와 일치시킴. 새로운 코드는 기존 범위에 추가.

| 코드 | 이름 | 설명 |
|------|------|------|
| DLI-401 | `EXECUTION_FAILED` | 쿼리 실행 오류 (기존) |
| DLI-402 | `EXECUTION_TIMEOUT` | 실행 타임아웃 (기존 TIMEOUT) |
| DLI-403 | `EXECUTION_CONNECTION` | Query Engine 연결 실패 (기존 CONNECTION_FAILED) |
| DLI-404 | `EXECUTION_PERMISSION` | 실행 권한 없음 (NEW) |
| DLI-405 | `EXECUTION_QUERY` | SQL 실행 오류 (NEW) |
| DLI-501 | `SERVER_UNREACHABLE` | Basecamp Server 연결 실패 (기존) |
| DLI-502 | `SERVER_AUTH_FAILED` | 서버 인증 실패 (기존) |
| DLI-503 | `SERVER_ERROR` | 서버 오류 (기존) |
| DLI-504 | `SERVER_EXECUTION` | 서버 실행 오류 (NEW) |

### 6.2 에러 메시지 예시

```
[DLI-403] Failed to connect to BigQuery
  Project: my-gcp-project
  Location: US

  Possible causes:
  - Missing GOOGLE_APPLICATION_CREDENTIALS
  - Invalid project ID
  - Network connectivity issues

  Run 'dli config check' to verify your configuration.
```

---

## 7. 구현 우선순위

### Phase 1: Core Execution (MVP) - 약 1주

> **Status:** ✅ Complete (2025-12-30)
> **Implementation Details:** [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md)

#### 1.1 모델 확장 (1일)
- [x] `ExecutionMode` enum 추가 (`models/common.py`)
- [x] `ExecutionContext`에 `execution_mode` 필드 추가
- [x] `mock_mode` deprecation warning 추가
- [x] `model_validator`로 마이그레이션 로직 추가

#### 1.2 Executor 통합 (2일)
- [x] `QueryExecutor` Protocol 정의 (`core/executor.py`)
- [x] `ExecutorFactory` 클래스 구현
- [x] `BigQueryExecutor` 연동 테스트
- [x] `ServerExecutor` 스텁 구현

#### 1.3 dataset run 확장 (2일)
- [ ] `--local`/`--server` 플래그 추가 (Phase 2)
- [x] `ExecutorFactory` 연동 (DI 준비 완료)
- [ ] 결과 출력 테이블 개선 (Phase 2)
- [x] `--dry-run` 옵션 동작 확인

#### 1.4 metric run 확장 (1일)
- [x] dataset run과 동일한 패턴 적용

#### 1.5 결과 저장 (1일)
- [ ] `--output` 옵션 구현 (Phase 2)
- [ ] CSV/JSON writer 구현 (Phase 2)

### Phase 2: Library API & Quality - 약 1주

#### 2.1 Library API 확장 (3일)
- [ ] `DatasetAPI.run()` 실제 실행 구현
- [ ] `MetricAPI.run()` 실제 실행 구현
- [ ] `ExecutionResult` 모델 확장
- [ ] Executor DI 지원 (테스트 용이성)

#### 2.2 quality run 통일 (2일)
- [ ] `--on-server` → `--server` 변경
- [ ] 로컬 실행 로직 개선

### Phase 3: 추가 기능 - 추후 (수요 확인 후)

- [ ] `dli config set execution.default_mode server`
- [ ] `dli config check` 연결 테스트
- [ ] Trino Executor 구현 (실제 수요 확인 후)

---

## 8. 테스트 전략

### 8.1 Unit Tests

| 파일 | 테스트 대상 |
|------|-------------|
| `test_execution_mode.py` | ExecutionMode enum, resolve 로직 |
| `test_executor_factory.py` | Factory 패턴, 모드별 생성 |
| `test_execution_context.py` | pydantic_settings 환경변수 로딩, 마이그레이션 |

### 8.2 Integration Tests

| 파일 | 테스트 대상 |
|------|-------------|
| `test_dataset_run_mock.py` | MockExecutor 사용 E2E |
| `test_dataset_run_local.py` | BigQuery 연동 (CI에서 skip, 로컬 수동 실행) |
| `test_api_di.py` | Executor DI 주입 테스트 |

### 8.3 테스트 예시

```python
# tests/test_execution_context.py

def test_execution_mode_default():
    """기본 실행 모드는 LOCAL."""
    ctx = ExecutionContext()
    assert ctx.execution_mode == ExecutionMode.LOCAL


def test_mock_mode_migration():
    """기존 mock_mode=True 사용자 마이그레이션."""
    ctx = ExecutionContext(mock_mode=True)
    assert ctx.execution_mode == ExecutionMode.MOCK


def test_mock_mode_deprecation_warning():
    """mock_mode 접근 시 DeprecationWarning 발생."""
    ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
    with pytest.warns(DeprecationWarning):
        _ = ctx.mock_mode


# tests/test_dataset_api.py

def test_run_with_mock_executor():
    """Mock executor 주입으로 단위 테스트."""
    mock_executor = MockExecutor(
        return_value=QueryResult(rows=[{"id": 1}], row_count=1)
    )

    api = DatasetAPI(
        context=ExecutionContext(execution_mode=ExecutionMode.LOCAL),
        executor=mock_executor,
    )

    result = api.run("test.schema.dataset")

    assert result.status == ResultStatus.SUCCESS
    assert mock_executor.call_count == 1
```

---

## Appendix A: 결정 사항 (인터뷰 기반)

### A.1 인터뷰 요약

| 질문 | 결정 |
|------|------|
| dataset/metric run 실행 범위 | MockExecutor → 실제 실행으로 변경, 로컬 + 서버 모두 지원 |
| 실행 위치 선택 방식 | `--local` (기본), `--server` 옵션, config 기본값 지원 |
| Query Engine | BigQuery 우선, Trino 추후 확장 (수요 확인 후) |
| 결과 출력 범위 | LIMIT 없이 전체 결과, 파일 저장 옵션 제공 |
| quality run 방식 | dataset/metric과 동일한 패턴 적용 |
| workflow 실행 위치 | 서버 전용 유지 (Airflow) |
| Library API | CLI와 동일한 로컬/서버 실행 지원 |
| 인증 방식 | API Key 방식 유지 |

---

## Appendix B: Agent Review Section

### B.1 feature-interface-cli Agent 리뷰

**리뷰 일자:** 2025-12-30

#### 긍정적 평가
- 기존 아키텍처(adapters/, core/executor.py)와 높은 호환성
- 점진적 구현 가능한 Phase 구분
- 에러 코드 체계가 기존 ErrorCode enum과 일관됨

#### 반영된 개선 사항
1. ✅ ExecutionContext를 dataclass → pydantic_settings 유지로 변경
2. ✅ DataSource vs ExecutionMode 개념 구분 명확화 (1.4 용어 정의)
3. ✅ 에러 코드를 현재 코드와 일치시킴 (404/405, 504 추가)
4. ✅ Phase 1 체크리스트 세분화 (일자별 태스크)
5. ✅ 테스트 전략 섹션 추가 (Section 8)

### B.2 expert-python Agent 리뷰

**리뷰 일자:** 2025-12-30

#### 긍정적 평가
- `ExecutionMode(str, Enum)` 패턴이 Pythonic
- 설정 우선순위가 12-factor app 원칙 준수
- dry-run 옵션이 CLI 도구 표준 패턴

#### 반영된 개선 사항
1. ✅ pydantic_settings 패턴 유지 + execution_mode 추가
2. ✅ mock_mode deprecation 전략 (DeprecationWarning + model_validator)
3. ✅ 에러 코드 충돌 해결 (기존 코드 유지, 세부 코드 추가)
4. ✅ Protocol 기반 QueryExecutor로 DI 지원 (테스트 용이성)
5. ✅ Config 설정 스키마를 Pydantic으로 정의
6. ✅ `parameters: dict` → `dict[str, Any]` 타입 명시
