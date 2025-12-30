# FEATURE: Library Interface for Programmatic Usage

> **Version:** 1.2.0
> **Status:** Draft (User Decisions Applied)
> **Last Updated:** 2025-12-30

---

## 1. 개요

### 1.1 목적

`dataops-cli` 패키지를 라이브러리 형태로 제공하여 Airflow Python Operator, Basecamp Parser 등 외부 시스템에서 프로그래매틱하게 호출할 수 있도록 합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **Import-First Design** | CLI 의존성 없이 핵심 기능 직접 import 가능 |
| **Minimal Dependencies** | Airflow/BigQuery 등은 optional dependency로 분리 |
| **Consistent API** | CLI와 Library API의 일관된 동작 보장 |
| **Type Safety** | 모든 Public API에 타입 힌트 제공 |
| **Testability** | Mock 모드 및 테스트 헬퍼 내장 |

### 1.3 사용 시나리오

| 시나리오 | 설명 | 우선순위 |
|----------|------|----------|
| **Airflow PythonOperator** | DAG 내에서 Dataset/Metric 실행 | P0 |
| **Basecamp Parser** | SQL Transpile 기능 호출 | P0 |
| **CI/CD Pipeline** | 자동화된 검증 스크립트 | P1 |
| **Jupyter Notebook** | 데이터 탐색 및 개발 | P2 |

### 1.4 유사 도구 참조

| 도구 | 참조 포인트 | 출처 |
|------|-------------|------|
| **dbt-core** | `dbt.adapters`, `dbt.contracts` 등 Programmatic API | dbt Labs |
| **SQLMesh** | `sqlmesh.core` Python API, Context 기반 설계 | Tobiko Data |
| **Great Expectations** | `great_expectations.core` Checkpoint API | GX |
| **Prefect** | `prefect.tasks` 데코레이터 기반 API | Prefect |

---

## 2. 아키텍처

### 2.1 패키지 구조 (Library Export)

```
dli/                           # 패키지 루트
├── __init__.py                # Public API exports
│
├── api/                       # NEW: Library API 모듈
│   ├── __init__.py            # api.* exports
│   ├── dataset.py             # DatasetAPI 클래스
│   ├── metric.py              # MetricAPI 클래스
│   ├── transpile.py           # TranspileAPI 클래스
│   ├── quality.py             # QualityAPI 클래스
│   ├── lineage.py             # LineageAPI 클래스
│   ├── workflow.py            # WorkflowAPI 클래스
│   └── catalog.py             # CatalogAPI 클래스 (NEW)
│
├── core/                      # 기존 Core 모듈 (내부 구현)
│   ├── service.py             # DatasetService
│   ├── metric_service.py      # MetricService
│   ├── transpile/             # TranspileEngine
│   ├── quality/               # QualityExecutor
│   ├── lineage/               # LineageClient
│   └── workflow/              # WorkflowModels
│
├── models/                    # NEW: Public 데이터 모델
│   ├── __init__.py
│   ├── dataset.py             # DatasetSpec, DatasetResult
│   ├── metric.py              # MetricSpec, MetricResult
│   ├── transpile.py           # TranspileResult, TranspileRule
│   ├── quality.py             # QualityTestResult
│   └── common.py              # ExecutionContext, ValidationResult
│
└── commands/                  # CLI 커맨드 (Library에서 미사용)
```

### 2.2 Import 구조

```python
# Level 1: 패키지 루트 (권장, 가장 간단)
from dli import DatasetAPI, MetricAPI, TranspileAPI

# Level 2: API 모듈 직접 import
from dli.api import DatasetAPI, MetricAPI, TranspileAPI

# Level 3: 개별 모델 import
from dli.models import DatasetSpec, MetricSpec, TranspileResult

# Level 4: Core 모듈 직접 import (고급 사용자)
from dli.core.transpile import TranspileEngine
from dli.core.service import DatasetService
```

### 2.3 핵심 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| API Layer 신설 | `dli.api.*` 모듈 추가 | Core와 분리된 안정적 Public API 제공 |
| Facade Pattern | API 클래스가 Core Service 래핑 | 사용자에게 단순한 인터페이스 제공 |
| Context 객체 | `ExecutionContext` 도입 | 설정, 인증, 로깅 등 공통 설정 관리 |
| 결과 객체 | Pydantic 모델 반환 | 타입 안전성, JSON 직렬화 용이 |
| Exception 계층 | `DLIError` 기반 예외 체계 | 명확한 에러 핸들링 |

---

## 3. API 설계

### 3.1 ExecutionContext (공통 설정)

```python
from pathlib import Path
from typing import Any, Literal
from pydantic import BaseModel, Field, ConfigDict
from pydantic_settings import BaseSettings

# Type aliases for clarity
SQLDialect = Literal["trino", "bigquery", "snowflake", "duckdb"]

# DataSource 의미론 (User Decision):
# - "local": 로컬 디스크에서 YAML Spec 파일 읽기 (project_path 기준)
# - "server": Basecamp Server API에서 등록된 Dataset/Metric 조회
DataSource = Literal["local", "server"]

class ExecutionContext(BaseSettings):
    """Library API 실행 컨텍스트.

    환경 변수에서 자동으로 설정을 로드합니다 (DLI_ prefix).
    예: DLI_SERVER_URL, DLI_PROJECT_PATH
    """

    model_config = ConfigDict(
        env_prefix="DLI_",
        env_file=".env",
        extra="ignore",
    )

    # 프로젝트 설정
    project_path: Path | None = Field(default=None, description="Project root path")

    # 서버 연결
    server_url: str | None = Field(default=None, description="Basecamp server URL")
    api_token: str | None = Field(default=None, description="API authentication token")

    # 실행 옵션
    mock_mode: bool = Field(default=False, description="Enable mock mode for testing")
    dry_run: bool = Field(default=False, description="Dry-run mode (no actual execution)")
    dialect: SQLDialect = Field(default="trino", description="Default SQL dialect")

    # 파라미터 (Jinja 렌더링용)
    parameters: dict[str, Any] = Field(default_factory=dict)

    # 로깅
    verbose: bool = Field(default=False, description="Enable verbose logging")

    def __repr__(self) -> str:
        return f"ExecutionContext(server_url={self.server_url!r}, mock_mode={self.mock_mode})"
```

### 3.2 DatasetAPI

```python
from dli.models import DatasetSpec, DatasetResult, ValidationResult
from dli.models.common import SQLDialect, DataSource

class DatasetAPI:
    """Dataset 관리 Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """API 초기화."""
        self.context = context or ExecutionContext()

    def __repr__(self) -> str:
        return f"DatasetAPI(context={self.context!r})"

    # === CRUD Operations ===

    def list_datasets(
        self,
        path: Path | None = None,
        *,
        source: DataSource = "local",
        domain: str | None = None,
        owner: str | None = None,
        tags: list[str] | None = None,
    ) -> list[DatasetSpec]:
        """Dataset 목록 조회."""
        ...

    def get(self, name: str) -> DatasetSpec | None:
        """Dataset 상세 조회."""
        ...

    # === Execution ===

    def run(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
        show_sql: bool = False,
    ) -> DatasetResult:
        """Dataset 실행.

        Args:
            name: Dataset 이름
            parameters: 런타임 파라미터 (Jinja 변수)
            dry_run: True면 SQL 렌더링만 수행
            show_sql: True면 결과에 SQL 포함

        Returns:
            DatasetResult: 실행 결과

        Raises:
            DatasetNotFoundError: Dataset을 찾을 수 없음
            DLIValidationError: 검증 실패
            ExecutionError: 실행 실패
        """
        ...

    def run_sql(
        self,
        sql: str,
        *,
        parameters: dict[str, Any] | None = None,
        transpile: bool = True,
        dialect: SQLDialect | None = None,
    ) -> DatasetResult:
        """임의 SQL 직접 실행.

        Airflow에서 inline SQL 실행 시 사용.
        """
        ...

    # === Validation ===

    def validate(
        self,
        name: str,
        *,
        strict: bool = False,
        check_deps: bool = False,
    ) -> ValidationResult:
        """Dataset Spec 및 SQL 검증.

        Args:
            name: Dataset 이름
            strict: 경고를 에러로 처리
            check_deps: 의존성 유효성 검사 수행
        """
        ...

    # === Registration ===

    def register(
        self,
        name: str,
        *,
        force: bool = False,
    ) -> None:
        """서버에 Dataset 등록."""
        ...

    # === SQL Rendering ===

    def render_sql(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        format_sql: bool = True,
    ) -> str:
        """Dataset SQL 렌더링 (Jinja + Transpile)."""
        ...

    # === Introspection (NEW: Agent Review Feedback) ===

    def get_tables(self, name: str) -> list[str]:
        """Dataset SQL에서 참조된 테이블 목록 추출."""
        ...

    def get_columns(self, name: str) -> list[str]:
        """Dataset SQL에서 참조된 컬럼 목록 추출."""
        ...

    def test_connection(self) -> bool:
        """데이터베이스 연결 테스트."""
        ...
```

### 3.3 MetricAPI

```python
class MetricAPI:
    """Metric 관리 Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext.from_env()

    def list(self, ...) -> list[MetricSpec]: ...
    def get(self, name: str) -> MetricSpec | None: ...
    def run(self, name: str, ...) -> MetricResult: ...
    def validate(self, name: str, ...) -> ValidationResult: ...
    def register(self, name: str, ...) -> None: ...
    def render_sql(self, name: str, ...) -> str: ...
```

### 3.4 TranspileAPI

```python
from dli.models import TranspileResult, TranspileRule

class TranspileAPI:
    """SQL Transpile Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext.from_env()

    def transpile(
        self,
        sql: str,
        *,
        source_dialect: str = "trino",
        target_dialect: str = "trino",
        apply_rules: bool = True,
        expand_metrics: bool = True,
        strict: bool = False,
    ) -> TranspileResult:
        """SQL 변환 수행.

        Args:
            sql: 원본 SQL
            source_dialect: 소스 SQL 다이얼렉트
            target_dialect: 타겟 SQL 다이얼렉트
            apply_rules: 테이블 치환 규칙 적용 여부
            expand_metrics: METRIC() 함수 확장 여부
            strict: 경고를 에러로 처리

        Returns:
            TranspileResult: 변환 결과
        """
        ...

    def validate_sql(
        self,
        sql: str,
        *,
        dialect: str = "trino",
    ) -> ValidationResult:
        """SQL 문법 검증 (파싱만, 실행 없음)."""
        ...

    def get_rules(self) -> list[TranspileRule]:
        """서버에서 Transpile 규칙 조회."""
        ...

    def format_sql(
        self,
        sql: str,
        *,
        dialect: str = "trino",
        indent: int = 2,
    ) -> str:
        """SQL 포맷팅."""
        ...
```

### 3.5 QualityAPI

```python
from dli.models import QualityTestResult, QualityTestSpec

class QualityAPI:
    """데이터 품질 테스트 Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext.from_env()

    def run_tests(
        self,
        target: str,
        *,
        tests: list[str] | None = None,
        fail_fast: bool = False,
    ) -> list[QualityTestResult]:
        """품질 테스트 실행."""
        ...

    def list_tests(self) -> list[QualityTestSpec]:
        """사용 가능한 테스트 목록."""
        ...
```

### 3.6 WorkflowAPI

```python
class WorkflowAPI:
    """Workflow 관리 Library API (서버 기반)."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext.from_env()

    def run(
        self,
        name: str,
        *,
        parameters: dict[str, Any] | None = None,
        wait: bool = False,
        timeout: int = 3600,
    ) -> WorkflowRunResult:
        """Workflow Adhoc 실행."""
        ...

    def backfill(
        self,
        name: str,
        *,
        start_date: str,
        end_date: str,
        parameters: dict[str, Any] | None = None,
    ) -> WorkflowRunResult:
        """기간 백필 실행."""
        ...

    def status(self, run_id: str) -> WorkflowStatus:
        """실행 상태 조회."""
        ...

    def stop(self, run_id: str) -> None:
        """실행 중지."""
        ...
```

### 3.7 LineageAPI

```python
class LineageAPI:
    """Lineage 조회 Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()

    def get_upstream(
        self,
        table: str,
        *,
        depth: int = 1,
    ) -> LineageGraph:
        """상위 의존성 조회."""
        ...

    def get_downstream(
        self,
        table: str,
        *,
        depth: int = 1,
    ) -> LineageGraph:
        """하위 의존성 조회."""
        ...
```

### 3.8 CatalogAPI (NEW: Agent Review Feedback)

```python
from dli.models.catalog import TableInfo, TableDetail

class CatalogAPI:
    """Data Catalog 브라우징 Library API."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()

    def list_tables(
        self,
        identifier: str | None = None,
        *,
        limit: int = 100,
    ) -> list[TableInfo]:
        """테이블/스키마/카탈로그 목록 조회.

        Args:
            identifier: 1-4 part identifier (implicit routing)
                - None: 모든 프로젝트 목록
                - "project": 프로젝트 내 데이터셋 목록
                - "project.dataset": 데이터셋 내 테이블 목록
                - "project.dataset.table": 테이블 상세
            limit: 최대 결과 수
        """
        ...

    def get(self, table: str) -> TableDetail:
        """테이블 상세 정보 조회."""
        ...

    def search(
        self,
        pattern: str,
        *,
        limit: int = 100,
    ) -> list[TableInfo]:
        """패턴으로 테이블 검색."""
        ...
```

### 3.9 ConfigAPI (NEW: User Decision - 읽기 전용)

```python
from dli.models.config import EnvironmentInfo, ConfigValue

class ConfigAPI:
    """설정 관리 Library API (읽기 전용).

    NOTE: 설정 변경은 CLI를 통해서만 가능 (안전성)
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()

    def get(self, key: str) -> ConfigValue | None:
        """설정 값 조회."""
        ...

    def list_environments(self) -> list[EnvironmentInfo]:
        """사용 가능한 환경 목록 조회."""
        ...

    def get_current_environment(self) -> str:
        """현재 활성 환경 이름 반환."""
        ...

    def get_server_status(self) -> dict[str, Any]:
        """Basecamp Server 연결 상태 확인."""
        ...
```

---

## 4. 데이터 모델

### 4.1 공통 모델

```python
# dli/models/common.py
from pydantic import BaseModel, Field
from datetime import datetime
from enum import Enum

# NOTE: ResultStatus는 API 결과용, RunStatus(client.py)는 Workflow 전용
# (User Decision: Enum 이름 분리로 의미론적 구분 명확화)
class ResultStatus(str, Enum):
    """API 실행 결과 상태."""
    SUCCESS = "success"
    FAILURE = "failure"
    SKIPPED = "skipped"
    PENDING = "pending"

# 기존 RunStatus (client.py)는 Workflow 전용으로 유지:
# class RunStatus(str, Enum):
#     RUNNING = "running"
#     SUCCESS = "success"
#     FAILURE = "failure"
#     PENDING = "pending"
#     CANCELLED = "cancelled"

class ValidationResult(BaseModel):
    """검증 결과."""
    valid: bool = Field(..., description="검증 통과 여부")
    errors: list[str] = Field(default_factory=list, description="에러 목록")
    warnings: list[str] = Field(default_factory=list, description="경고 목록")

    @property
    def has_errors(self) -> bool:
        return len(self.errors) > 0

    @property
    def has_warnings(self) -> bool:
        return len(self.warnings) > 0

class BaseResult(BaseModel):
    """실행 결과 기본 클래스."""
    status: ResultStatus
    started_at: datetime
    ended_at: datetime | None = None
    duration_ms: int | None = None
    error_message: str | None = None
```

### 4.2 Transpile 모델

```python
# dli/models/transpile.py
from pydantic import BaseModel, Field

class TranspileRule(BaseModel):
    """Transpile 규칙."""
    source_table: str = Field(..., description="소스 테이블")
    target_table: str = Field(..., description="타겟 테이블")
    priority: int = Field(default=0, description="규칙 우선순위")
    enabled: bool = Field(default=True, description="활성화 여부")

class TranspileWarning(BaseModel):
    """Transpile 경고."""
    message: str
    line: int | None = None
    column: int | None = None
    rule: str | None = None

class TranspileResult(BaseModel):
    """Transpile 결과."""
    original_sql: str = Field(..., description="원본 SQL")
    transpiled_sql: str = Field(..., description="변환된 SQL")
    success: bool = Field(..., description="변환 성공 여부")
    applied_rules: list[TranspileRule] = Field(default_factory=list)
    warnings: list[TranspileWarning] = Field(default_factory=list)
    duration_ms: int = Field(..., description="처리 시간 (ms)")

    @property
    def has_changes(self) -> bool:
        """SQL이 변경되었는지 여부."""
        return self.original_sql != self.transpiled_sql
```

### 4.3 Dataset/Metric 결과 모델

```python
# dli/models/dataset.py
from pydantic import BaseModel, Field
from .common import BaseResult, ExecutionStatus

class DatasetResult(BaseResult):
    """Dataset 실행 결과."""
    name: str
    sql: str | None = None
    rows_affected: int | None = None
    transpile_result: TranspileResult | None = None

class MetricResult(BaseResult):
    """Metric 실행 결과."""
    name: str
    sql: str | None = None
    data: list[dict] | None = None
    row_count: int | None = None
```

---

## 5. Exception 계층 (Updated: Agent Review Feedback)

```python
# dli/exceptions.py
from dataclasses import dataclass, field
from enum import Enum
from typing import Any
from pathlib import Path

class ErrorCode(str, Enum):
    """에러 코드 (프로그래매틱 핸들링용)."""
    # Configuration Errors (DLI-0xx)
    CONFIG_INVALID = "DLI-001"
    CONFIG_NOT_FOUND = "DLI-002"

    # Not Found Errors (DLI-1xx)
    DATASET_NOT_FOUND = "DLI-101"
    METRIC_NOT_FOUND = "DLI-102"
    TABLE_NOT_FOUND = "DLI-103"

    # Validation Errors (DLI-2xx)
    VALIDATION_FAILED = "DLI-201"
    SQL_SYNTAX_ERROR = "DLI-202"
    SPEC_INVALID = "DLI-203"

    # Transpile Errors (DLI-3xx)
    TRANSPILE_FAILED = "DLI-301"
    DIALECT_UNSUPPORTED = "DLI-302"

    # Execution Errors (DLI-4xx)
    EXECUTION_FAILED = "DLI-401"
    TIMEOUT = "DLI-402"

    # Server Errors (DLI-5xx)
    SERVER_UNREACHABLE = "DLI-501"
    SERVER_AUTH_FAILED = "DLI-502"
    SERVER_ERROR = "DLI-503"


@dataclass
class DLIError(Exception):
    """DLI 라이브러리 기본 예외.

    모든 DLI 예외는 에러 코드와 상세 정보를 포함합니다.
    """
    message: str
    code: ErrorCode
    details: dict[str, Any] = field(default_factory=dict)

    def __str__(self) -> str:
        return f"[{self.code.value}] {self.message}"


@dataclass
class ConfigurationError(DLIError):
    """설정 관련 오류."""
    code: ErrorCode = ErrorCode.CONFIG_INVALID


# NOTE: DLIValidationError로 명명하여 pydantic.ValidationError와 충돌 방지
@dataclass
class DLIValidationError(DLIError):
    """검증 실패."""
    code: ErrorCode = ErrorCode.VALIDATION_FAILED
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


@dataclass
class ExecutionError(DLIError):
    """실행 중 오류."""
    code: ErrorCode = ErrorCode.EXECUTION_FAILED
    cause: Exception | None = None

    def __post_init__(self) -> None:
        if self.cause:
            self.__cause__ = self.cause


@dataclass
class DatasetNotFoundError(DLIError):
    """Dataset을 찾을 수 없음."""
    code: ErrorCode = ErrorCode.DATASET_NOT_FOUND
    name: str = ""
    searched_paths: list[Path] = field(default_factory=list)

    def __str__(self) -> str:
        paths = ", ".join(str(p) for p in self.searched_paths) if self.searched_paths else "N/A"
        return f"[{self.code.value}] Dataset '{self.name}' not found. Searched: {paths}"


@dataclass
class MetricNotFoundError(DLIError):
    """Metric을 찾을 수 없음."""
    code: ErrorCode = ErrorCode.METRIC_NOT_FOUND
    name: str = ""


@dataclass
class TranspileError(DLIError):
    """SQL 변환 오류."""
    code: ErrorCode = ErrorCode.TRANSPILE_FAILED
    sql: str = ""
    line: int | None = None
    column: int | None = None


@dataclass
class ServerError(DLIError):
    """서버 통신 오류."""
    code: ErrorCode = ErrorCode.SERVER_ERROR
    status_code: int | None = None
    url: str | None = None
```

---

## 6. 사용 예시

### 6.1 Airflow PythonOperator에서 Dataset 실행

```python
from airflow.decorators import task
from dli import DatasetAPI, ExecutionContext

@task
def run_dataset(dataset_name: str, execution_date: str) -> dict:
    """Airflow Task로 Dataset 실행."""

    # 컨텍스트 설정
    ctx = ExecutionContext(
        project_path="/opt/airflow/dags/dataops-models",
        server_url="https://basecamp.example.com",
        parameters={"execution_date": execution_date},
    )

    # Dataset 실행
    api = DatasetAPI(context=ctx)
    result = api.run(dataset_name, dry_run=False)

    # 결과 반환 (XCom)
    return result.model_dump()
```

### 6.2 Basecamp Parser에서 Transpile 호출

```python
from dli import TranspileAPI, ExecutionContext

def transpile_sql(sql: str, dialect: str = "trino") -> dict:
    """Flask API에서 Transpile 수행."""

    ctx = ExecutionContext(
        server_url="http://basecamp-server:8080",
        dialect=dialect,
    )

    api = TranspileAPI(context=ctx)
    result = api.transpile(
        sql,
        source_dialect=dialect,
        target_dialect=dialect,
        apply_rules=True,
        expand_metrics=True,
    )

    return result.model_dump()
```

### 6.3 CI/CD에서 검증 수행

```python
from dli import DatasetAPI, MetricAPI
from pathlib import Path

def validate_all_models(project_path: str) -> bool:
    """모든 모델 검증."""

    path = Path(project_path)

    # Dataset 검증
    dataset_api = DatasetAPI()
    datasets = dataset_api.list(path=path)

    all_valid = True
    for ds in datasets:
        result = dataset_api.validate(ds.name, strict=True)
        if not result.valid:
            print(f"[FAIL] {ds.name}: {result.errors}")
            all_valid = False

    # Metric 검증
    metric_api = MetricAPI()
    metrics = metric_api.list(path=path)

    for m in metrics:
        result = metric_api.validate(m.name, strict=True)
        if not result.valid:
            print(f"[FAIL] {m.name}: {result.errors}")
            all_valid = False

    return all_valid
```

### 6.4 Jupyter Notebook에서 사용

```python
from dli import DatasetAPI, TranspileAPI

# Dataset 목록 조회
api = DatasetAPI()
datasets = api.list()

# SQL 미리보기
sql = api.render_sql("catalog.schema.my_dataset", parameters={"date": "2025-01-01"})
print(sql)

# Transpile 테스트
transpile = TranspileAPI()
result = transpile.transpile(sql, apply_rules=True)
print(result.transpiled_sql)
```

---

## 7. 패키지 배포 설정

### 7.1 pyproject.toml 변경사항 (Updated: Agent Review Feedback)

```toml
[project]
name = "dataops-cli"
version = "0.2.0"  # Library 기능 추가로 마이너 버전 업
requires-python = ">=3.12"

# Core dependencies (Library + CLI 공통)
dependencies = [
    "typer>=0.12.0",
    "rich>=13.7.0",
    "sqlglot>=28.5.0",
    "httpx>=0.27.0",
    "pydantic>=2.9.0",
    "pydantic-settings>=2.0.0",  # NEW: ExecutionContext용
    "python-dotenv>=1.0.0",
    "jinja2>=3.1",
    "pyyaml>=6.0",
]

# Entry points (CLI 유지)
[project.scripts]
dli = "dli.main:app"

# Optional dependencies (버전 상한 추가)
[project.optional-dependencies]
airflow = ["apache-airflow>=2.7.0,<3.0"]
bigquery = ["google-cloud-bigquery>=3.0,<4.0"]
snowflake = ["snowflake-connector-python>=3.0,<4.0"]
dev = [
    "pytest>=8.0",
    "pytest-cov>=4.0",
    "pytest-asyncio>=0.23",
    "mypy>=1.8",
    "ruff>=0.1",
    "hypothesis>=6.0",  # Property-based testing
]
docs = [
    "mkdocs-material>=9.0",
    "mkdocstrings[python]>=0.24",
]
all = [
    "apache-airflow>=2.7.0,<3.0",
    "google-cloud-bigquery>=3.0,<4.0",
    "snowflake-connector-python>=3.0,<4.0",
]

# Type hints marker (PEP 561)
[tool.setuptools.package-data]
dli = ["py.typed"]
```

### 7.2 __init__.py 변경 (Public API Export) (Updated: Agent Review Feedback)

```python
# dli/__init__.py
"""DataOps CLI - Command-line interface and Library for DataOps platform."""

__version__ = "0.2.0"

# Public API classes
from dli.api import (
    DatasetAPI,
    MetricAPI,
    TranspileAPI,
    QualityAPI,
    WorkflowAPI,
    LineageAPI,
    CatalogAPI,
    ConfigAPI,  # NEW: User Decision (읽기 전용)
)

# Context and Configuration
from dli.models.common import ExecutionContext

# Exceptions (DLIValidationError로 명명하여 pydantic 충돌 방지)
from dli.exceptions import (
    DLIError,
    ErrorCode,
    ConfigurationError,
    DLIValidationError,
    ExecutionError,
    DatasetNotFoundError,
    MetricNotFoundError,
    TranspileError,
    ServerError,
)

__all__ = [
    # Version
    "__version__",
    # API Classes
    "DatasetAPI",
    "MetricAPI",
    "TranspileAPI",
    "QualityAPI",
    "WorkflowAPI",
    "LineageAPI",
    "CatalogAPI",
    "ConfigAPI",  # NEW
    # Context
    "ExecutionContext",
    # Exceptions
    "DLIError",
    "ErrorCode",
    "ConfigurationError",
    "DLIValidationError",
    "ExecutionError",
    "DatasetNotFoundError",
    "MetricNotFoundError",
    "TranspileError",
    "ServerError",
]
```

---

## 8. 테스트 전략

### 8.1 테스트 구조

```
tests/
├── api/                       # API 레이어 테스트
│   ├── test_dataset_api.py
│   ├── test_metric_api.py
│   ├── test_transpile_api.py
│   ├── test_quality_api.py
│   └── test_workflow_api.py
│
├── integration/               # 통합 테스트
│   ├── test_airflow_usage.py  # Airflow 환경 시뮬레이션
│   ├── test_parser_usage.py   # Parser 환경 시뮬레이션
│   └── test_library_install.py # 라이브러리 설치 테스트
│
└── models/                    # 모델 테스트
    ├── test_transpile_models.py
    └── test_common_models.py
```

### 8.2 Library 설치 테스트

```python
# tests/integration/test_library_install.py
"""라이브러리 설치 및 import 테스트."""

import subprocess
import sys
import pytest
from pathlib import Path


class TestLibraryInstall:
    """Library 설치 테스트."""

    def test_package_importable(self) -> None:
        """패키지 import 가능 여부."""
        import dli
        assert hasattr(dli, "__version__")
        assert hasattr(dli, "DatasetAPI")
        assert hasattr(dli, "TranspileAPI")

    def test_api_classes_available(self) -> None:
        """Public API 클래스 사용 가능."""
        from dli import (
            DatasetAPI,
            MetricAPI,
            TranspileAPI,
            QualityAPI,
            WorkflowAPI,
            LineageAPI,
            ExecutionContext,
        )

        # 인스턴스 생성 가능
        ctx = ExecutionContext(mock_mode=True)
        api = DatasetAPI(context=ctx)
        assert api is not None

    def test_no_cli_dependency(self) -> None:
        """API 사용 시 CLI 의존성 없음."""
        # Typer, Rich 없이도 core 기능 동작해야 함
        from dli.api import TranspileAPI
        from dli.models.common import ExecutionContext

        ctx = ExecutionContext(mock_mode=True)
        api = TranspileAPI(context=ctx)

        # Mock 모드에서는 CLI 없이 동작
        result = api.validate_sql("SELECT 1")
        assert result.valid

    def test_exception_hierarchy(self) -> None:
        """예외 계층 확인."""
        from dli import DLIError, ValidationError, ExecutionError

        assert issubclass(ValidationError, DLIError)
        assert issubclass(ExecutionError, DLIError)


class TestTranspileLibraryUsage:
    """Transpile Library 사용 테스트."""

    def test_transpile_basic(self) -> None:
        """기본 Transpile 동작."""
        from dli import TranspileAPI, ExecutionContext

        ctx = ExecutionContext(mock_mode=True)
        api = TranspileAPI(context=ctx)

        sql = "SELECT * FROM source_table"
        result = api.transpile(sql)

        assert result.success
        assert result.original_sql == sql
        assert result.transpiled_sql is not None

    def test_validate_sql(self) -> None:
        """SQL 문법 검증."""
        from dli import TranspileAPI, ExecutionContext

        ctx = ExecutionContext(mock_mode=True)
        api = TranspileAPI(context=ctx)

        # 유효한 SQL
        result = api.validate_sql("SELECT id, name FROM users")
        assert result.valid

        # 무효한 SQL
        result = api.validate_sql("SELEC id FROM")
        assert not result.valid


class TestDatasetLibraryUsage:
    """Dataset Library 사용 테스트."""

    def test_list_datasets(self) -> None:
        """Dataset 목록 조회."""
        from dli import DatasetAPI, ExecutionContext

        ctx = ExecutionContext(mock_mode=True)
        api = DatasetAPI(context=ctx)

        datasets = api.list()
        assert isinstance(datasets, list)

    def test_run_dry_run(self) -> None:
        """Dry-run 모드 실행."""
        from dli import DatasetAPI, ExecutionContext

        ctx = ExecutionContext(mock_mode=True)
        api = DatasetAPI(context=ctx)

        # Mock 데이터 있는 경우
        result = api.run("test_dataset", dry_run=True)
        assert result.status in ["success", "skipped"]
```

### 8.3 Airflow 환경 시뮬레이션 테스트

```python
# tests/integration/test_airflow_usage.py
"""Airflow PythonOperator 환경 시뮬레이션."""

import pytest
from unittest.mock import patch, MagicMock


class TestAirflowIntegration:
    """Airflow 환경에서 Library 사용 테스트."""

    def test_python_operator_pattern(self) -> None:
        """PythonOperator에서 호출 패턴."""
        from dli import DatasetAPI, ExecutionContext

        def airflow_task(dataset_name: str, execution_date: str) -> dict:
            """시뮬레이션된 Airflow Task."""
            ctx = ExecutionContext(
                mock_mode=True,
                parameters={"execution_date": execution_date},
            )
            api = DatasetAPI(context=ctx)
            result = api.run(dataset_name, dry_run=True)
            return result.model_dump()

        # Task 실행
        output = airflow_task("my_dataset", "2025-01-01")
        assert isinstance(output, dict)
        assert "status" in output

    def test_context_from_airflow_variables(self) -> None:
        """Airflow Variable에서 컨텍스트 생성."""
        import os

        # Airflow 환경 변수 시뮬레이션
        with patch.dict(os.environ, {
            "DLI_SERVER_URL": "http://basecamp:8080",
            "DLI_PROJECT_PATH": "/opt/airflow/dags/models",
        }):
            from dli import ExecutionContext
            ctx = ExecutionContext.from_env()

            assert ctx.server_url == "http://basecamp:8080"
```

---

## 9. 구현 로드맵 (Updated: Agent Review Feedback)

### Phase 1: Core API (P0) - Week 1-2

| Task | 설명 | 파일 |
|------|------|------|
| ExecutionContext | Pydantic BaseSettings 기반 구현 | `dli/models/common.py` |
| TranspileAPI | Transpile 기능 래핑 | `dli/api/transpile.py` |
| Exception 계층 | ErrorCode + 상세 예외 클래스 | `dli/exceptions.py` |
| Public Export | __init__.py 정리 | `dli/__init__.py` |
| py.typed | PEP 561 타입 마커 추가 | `dli/py.typed` |
| Enum 통합 | 기존 RunStatus 재사용/확장 | `dli/core/client.py` |
| 기본 테스트 | Library 설치/import 테스트 | `tests/integration/` |

### Phase 2: Dataset/Metric/Catalog API (P0) - Week 2-3

| Task | 설명 | 파일 |
|------|------|------|
| DatasetAPI | Dataset 기능 래핑 + Introspection | `dli/api/dataset.py` |
| MetricAPI | Metric 기능 래핑 | `dli/api/metric.py` |
| CatalogAPI | Catalog 브라우징 기능 | `dli/api/catalog.py` |
| Result 모델 | 결과 Pydantic 모델 | `dli/models/dataset.py` |
| 통합 테스트 | Airflow 시뮬레이션 테스트 | `tests/integration/` |

### Phase 3: Quality/Workflow/Lineage API (P1) - Week 3-4

| Task | 설명 | 파일 |
|------|------|------|
| QualityAPI | Quality 테스트 기능 | `dli/api/quality.py` |
| WorkflowAPI | Workflow 관리 기능 | `dli/api/workflow.py` |
| LineageAPI | Lineage 조회 기능 | `dli/api/lineage.py` |
| Import Guards | Optional 의존성 체크 | `dli/api/_compat.py` |

### Phase 4: Documentation & Polish - Week 4-5

| Task | 설명 |
|------|------|
| API Documentation | MkDocs 문서화 |
| Usage Examples | 상세 사용 예시 추가 |
| Contract Tests | Public API 안정성 테스트 |
| Version Bump | 0.2.0 릴리스 |

### Phase 5: CLI 리팩토링 (P2) - Week 5-6 (NEW)

| Task | 설명 |
|------|------|
| CLI → Library API | CLI 커맨드가 Library API 사용하도록 리팩토링 |
| 일관성 보장 | CLI와 Library의 동일한 동작 보장 |
| Async API | I/O 바운드 작업용 비동기 API 추가 (선택) |

---

## 10. 위험 요소 및 대응

| 위험 | 영향 | 대응 |
|------|------|------|
| CLI/Library 코드 중복 | 유지보수 비용 증가 | Facade 패턴으로 Core 재사용 |
| Breaking Changes | 기존 CLI 사용자 영향 | v0.2.0에서 허용 (초기 버전), 마이그레이션 가이드 제공 |
| 의존성 충돌 | Airflow 환경에서 버전 충돌 | Optional dependency 분리, 버전 상한 추가 |
| 성능 이슈 | 대규모 데이터 처리 시 | Lazy loading, 스트리밍 결과 고려 |
| 예외 계층 충돌 | 기존 `core/transpile/exceptions.py`와 충돌 | 통합 계층으로 마이그레이션 (P0 결정) |

### 10.1 Thread Safety (User Decision)

**정책**: API 인스턴스는 **thread-safe하지 않음**

```python
# 올바른 사용법 (각 프로세스/스레드에서 새 인스턴스)
def task_a():
    api = DatasetAPI()  # 인스턴스 생성
    api.run("dataset_a")

def task_b():
    api = DatasetAPI()  # 별도 인스턴스
    api.run("dataset_b")
```

**근거**:
- Kubernetes Airflow (PodOperator/CeleryExecutor) 환경에서 각 Task는 별도 프로세스
- 프로세스 격리로 인해 thread-safety 불필요
- Python 라이브러리 일반적 관례 준수 (SQLAlchemy Session, httpx.Client 등)

---

## 11. 참고 문서

- [FEATURE_MODEL.md](./FEATURE_MODEL.md) - MODEL 추상화 명세
- [FEATURE_TRANSPILE.md](./FEATURE_TRANSPILE.md) - SQL Transpile 기능 명세
- [FEATURE_WORKFLOW.md](./FEATURE_WORKFLOW.md) - Workflow 관리 명세
- [project-interface-cli/docs/PATTERNS.md](../docs/PATTERNS.md) - 개발 패턴 가이드

---

## 12. 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| 1.0.0 | 2025-12-30 | 초안 작성 |
| 1.1.0 | 2025-12-30 | Agent 리뷰 피드백 반영 |
| 1.2.0 | 2025-12-30 | expert-spec 리뷰 + 사용자 결정 반영 |

---

## 13. Agent Review Feedback

> 이 섹션은 `feature-interface-cli` 및 `expert-python` Agent의 리뷰 결과를 문서화합니다.

### 13.1 feature-interface-cli Agent 리뷰 요약

**Overall Assessment:** 설계가 Facade 패턴과 업계 모범 사례(dbt, SQLMesh)를 잘 따르고 있음. CLI와 85% 정렬됨.

#### 반영된 피드백

| 피드백 | 변경 사항 | 섹션 |
|--------|----------|------|
| CatalogAPI 누락 | CatalogAPI 클래스 추가 | 3.8 |
| Enum 중복 위험 | 기존 RunStatus 재사용 권장 명시 | 9.1 Phase 1 |
| Introspection 메서드 누락 | `get_tables()`, `get_columns()`, `test_connection()` 추가 | 3.2 |
| Dependency validation | `validate(check_deps=True)` 파라미터 추가 | 3.2 |
| CLI 리팩토링 필요 | Phase 5 추가 (CLI → Library API) | 9.5 |

#### 향후 고려 사항

- CLI 커맨드가 Library API를 내부적으로 사용하도록 리팩토링
- ConfigAPI 추가 (설정 관리 프로그래매틱 노출)

### 13.2 expert-python Agent 리뷰 요약

**Overall Assessment:** 전반적으로 견고한 설계. 에러 핸들링과 일부 Python 라이브러리 모범 사례 보완 필요.

#### 반영된 피드백

| Priority | 피드백 | 변경 사항 | 섹션 |
|----------|--------|----------|------|
| P0 | `ValidationError` 이름 충돌 | `DLIValidationError`로 변경 | 5 |
| P0 | 에러 코드 및 컨텍스트 부재 | `ErrorCode` enum + 상세 예외 필드 추가 | 5 |
| P0 | `py.typed` 마커 누락 | pyproject.toml에 추가 | 7.1 |
| P1 | ExecutionContext → Pydantic | `BaseSettings` 기반으로 변경 | 3.1 |
| P1 | `Literal` 타입 미사용 | `SQLDialect`, `DataSource` 타입 별칭 추가 | 3.1, 3.2 |
| P1 | Optional 의존성 import guard | Phase 3에 `_compat.py` 추가 | 9.3 |
| P1 | `dev` extras 누락 | pyproject.toml에 추가 | 7.1 |
| P2 | `list()` 빌트인 섀도잉 | `list_datasets()`로 변경 | 3.2 |
| P2 | `__repr__` 누락 | API 클래스에 추가 | 3.1, 3.2 |

#### 향후 고려 사항 (미반영)

| 항목 | 이유 |
|------|------|
| Async API (`arun()`) | Phase 5에서 선택적으로 추가 예정 |
| Property-based testing (Hypothesis) | 테스트 전략에 언급, 상세 구현은 Phase 4 |
| Registry 패턴 (extensibility) | v0.3.0에서 고려 |
| structlog 로깅 | 현재 verbose 플래그로 충분, 향후 고려 |

### 13.3 리뷰어별 평가

| 리뷰어 | 평점 | 주요 관심사 |
|--------|------|------------|
| feature-interface-cli | 4/5 | CLI 일관성, 기능 완전성 |
| expert-python | 4/5 | Python 모범 사례, 타입 안전성 |

### 13.4 사용자 결정 사항 (v1.2.0)

> expert-spec Agent 리뷰 후 사용자와의 Q&A를 통해 결정된 사항

#### P0 결정 (구현 전 필수)

| 질문 | 결정 | 근거 |
|------|------|------|
| **Enum 중복 처리** | ResultStatus (API용) + RunStatus (Workflow용) 분리 | 의미론적 구분 명확화 |
| **TranspileError 충돌** | 통합 예외 계층 사용 (`core/transpile/exceptions.py` 폐기) | 단일 예외 계층 유지 |
| **DataSource 의미론** | local=디스크, server=API | 가장 직관적인 해석 |
| **DatasetService 통합** | Facade 패턴 (DatasetAPI가 DatasetService 래핑) | 기존 코드 유지, 위험 최소화 |

#### P1 결정 (빠른 시일 내)

| 질문 | 결정 | 근거 |
|------|------|------|
| **ConfigAPI 범위** | 읽기 전용 (get, list_environments) | 안전성 우선 |
| **Batch API** | v0.2.0에서는 제외 | 단순성 유지, 필요시 v0.3.0에 추가 |
| **하위 호환성** | Breaking change 허용 | 초기 버전이므로 유연성 확보 |
| **Thread Safety** | Not thread-safe (문서화만) | K8s Airflow 환경에서 프로세스 격리됨 |

#### 미해결 이슈 (Deferred)

1. **CLI 리팩토링 범위**: Phase 5에서 결정 (핵심 커맨드부터 점진적 적용)
2. **Async API 필요성**: K8s Airflow에서는 불필요, v0.3.0에서 재검토
3. **Breaking Changes 관리**: v0.2.0 릴리스 시 마이그레이션 가이드 제공
