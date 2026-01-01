# FEATURE: Catalog 커맨드

> **Version:** 1.2.0
> **Status:** Enhanced Draft  
> **Last Updated:** 2025-12-31
> **Industry Benchmarked:** Databricks CLI, DBT CLI, SqlMesh CLI

---

## 1. 개요

### 1.1 목적

`dli catalog` 커맨드는 Basecamp Server에서 관리하는 데이터 카탈로그를 탐색하고 테이블 메타데이터를 조회합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **서버 기반** | 모든 메타데이터는 Basecamp API에서 조회 (Query Engine 직접 조회 아님) |
| **암시적 라우팅** | 식별자 파트 수에 따라 동작 자동 결정 |
| **Lineage 재사용** | Impact 정보는 기존 `LineageClient.get_downstream()` 활용 |
| **서버 책임 분리** | URN 변환, PII 마스킹 등은 서버에서 처리 |

### 1.3 주요 기능

- **테이블 탐색**: 프로젝트/데이터셋 계층 탐색
- **테이블 상세**: 스키마, 통계, 품질, 소유권, 영향도 조회
- **키워드 검색**: 테이블/컬럼/설명/태그 통합 검색

### 1.4 업계 표준 벤치마킹 (2025)

| 도구 | 핵심 기능 | dli에 반영 |
|------|-----------|------------|
| **🧱 Databricks CLI** | `tables list CATALOG SCHEMA`<br/>`--include-browse`, `--max-results` | 고급 필터링, 성능 옵션 |
| **📚 DBT CLI** | `docs generate --select --static`<br/>catalog.json 아티팩트 | 정적 문서 출력, 배치 처리 |
| **⚡ SqlMesh CLI** | `create_external_models`<br/>`ui --mode catalog` | 자동 스키마 디스커버리 |

### 1.5 기존 참조 도구

| 도구 | 참조 포인트 |
|------|-------------|
| **OpenMetadata** | 스키마 우선 아키텍처, 통합 메타데이터 모델 |
| **DataHub** | URN 기반 식별자, Popular Queries 기능 |
| **Atlan** | 계층적 태그 시스템, 영향도 분석 |

---

## 2. 식별자 체계

### 2.1 사용자 인터페이스

| 형식 | 예시 | 설명 |
|------|------|------|
| **3-part** | `project.dataset.table` | 기본 형식 (BigQuery 스타일) |
| **4-part** | `bigquery.project.dataset.table` | 엔진 명시 필요 시 |

### 2.2 Engine 감지 (하드코딩)

CLI는 첫 번째 파트가 지원 엔진 목록에 있으면 4-part로 판단합니다:

```python
# src/dli/commands/catalog.py
SUPPORTED_ENGINES = frozenset({"bigquery", "trino", "hive"})

def parse_identifier(identifier: str) -> tuple[str | None, str]:
    """Returns (engine, table_reference)"""
    parts = identifier.split(".", 1)
    if parts[0] in SUPPORTED_ENGINES:
        return parts[0], parts[1]  # 4-part: engine + 3-part
    return None, identifier  # 3-part or less
```

### 2.3 URN 변환

- **책임**: 서버 (Basecamp API)
- **CLI 역할**: 사용자 입력을 그대로 API에 전달
- CLI는 URN 형식을 알 필요 없음

---

## 3. CLI 설계

### 3.1 암시적 라우팅 (주 인터페이스)

```bash
dli catalog <identifier> [options]
```

| 입력 형식 | 동작 | 예시 |
|-----------|------|------|
| 1-part | 프로젝트 내 테이블 목록 | `dli catalog my-project` |
| 2-part | 데이터셋 내 테이블 목록 | `dli catalog my-project.analytics` |
| 3-part | 테이블 상세 정보 | `dli catalog my-project.analytics.users` |
| 4-part | 특정 엔진 테이블 상세 | `dli catalog bigquery.my-project.analytics.users` |

### 3.2 명시적 커맨드 (고급 필터용)

| 커맨드 | 설명 |
|--------|------|
| `dli catalog list` | 필터 조합으로 테이블 목록 조회 |
| `dli catalog search <keyword>` | 키워드 기반 전체 검색 |

### 3.3 공통 옵션

| 옵션 | 단축 | 설명 | 기본값 |
|------|------|------|--------|
| `--format` | `-f` | 출력 형식 (`table`/`json`) | `table` |
| `--limit` | `-n` | 결과 수 제한 | 50 |
| `--offset` | | 페이지네이션 오프셋 | 0 |

### 3.4 상세 조회 전용 옵션

| 옵션 | 설명 |
|------|------|
| `--section` | 특정 섹션만 출력: `basic`, `columns`, `quality`, `freshness`, `ownership`, `impact`, `queries` |
| `--sample` | 샘플 데이터 포함 (PII는 서버에서 마스킹) |

### 3.5 list 커맨드 필터

| 옵션 | 단축 | 설명 |
|------|------|------|
| `--project` | `-p` | 프로젝트 필터 |
| `--dataset` | `-d` | 데이터셋 필터 |
| `--owner` | `-o` | 소유자 필터 |
| `--team` | `-t` | 팀 필터 |
| `--tag` | | 태그 필터 (복수 가능, AND 조건) |

---

## 4. 테이블 상세 정보 (Sections)

### 4.1 섹션 구성

| 섹션 | 내용 |
|------|------|
| **Basic** | 이름, 엔진, 설명, 태그, Basecamp URL |
| **Columns** | 스키마 (이름, 타입, 설명, PII 여부, 통계) |
| **Quality** | 점수, 테스트 결과 요약, 경고 |
| **Freshness** | 마지막 업데이트, 평균 입수 시간, 지연 이력 |
| **Ownership** | Owner, Stewards, Team, Consumers |
| **Impact** | 영향받는 Tables/Datasets/Metrics/Dashboards (LineageClient 활용) |
| **Sample Queries** | 인기 쿼리 목록 (서버 제공) |
| **Sample Data** | 샘플 레코드 (`--sample` 필요, PII 서버에서 마스킹) |

### 4.2 PII 처리

- PII 컬럼은 `🔒` 아이콘으로 표시
- 샘플 데이터의 PII 값은 **서버에서 마스킹**하여 반환
- CLI는 마스킹 로직 구현 불필요

### 4.3 Impact 연계 (Lineage 모듈 재사용)

```python
# catalog.py
from dli.core.lineage import LineageClient

def get_impact_summary(client: BasecampClient, table_ref: str) -> ImpactSummary:
    lineage_client = LineageClient(client)
    downstream = lineage_client.get_downstream(table_ref, depth=1)

    return ImpactSummary(
        tables=[n for n in downstream.nodes if n.type == "Table"],
        datasets=[n for n in downstream.nodes if n.type == "Dataset"],
        metrics=[n for n in downstream.nodes if n.type == "Metric"],
        dashboards=[n for n in downstream.nodes if n.type == "Dashboard"],
    )
```

---

## 5. 태그 시스템

### 5.1 형식

계층적 태그: `category::value` (예: `tier::critical`, `domain::analytics`)

단독 태그: `pii`, `deprecated`

### 5.2 필터링

```bash
# 단일 태그
dli catalog list --tag tier::critical

# 복수 태그 (AND 조건)
dli catalog list --tag tier::critical --tag domain::analytics
```

---

## 6. Basecamp API

### 6.1 엔드포인트

| 동작 | Method | Endpoint |
|------|--------|----------|
| 테이블 목록 | GET | `/api/v1/catalog/tables` |
| 테이블 검색 | GET | `/api/v1/catalog/search` |
| 테이블 상세 | GET | `/api/v1/catalog/tables/{table_ref}` |
| 샘플 쿼리 | GET | `/api/v1/catalog/tables/{table_ref}/queries` |
| 샘플 데이터 | GET | `/api/v1/catalog/tables/{table_ref}/sample` |

### 6.2 클라이언트 메서드 (BasecampClient 확장)

```python
# client.py에 추가
def catalog_list(self, *, project: str | None = None, dataset: str | None = None,
                 owner: str | None = None, team: str | None = None,
                 tags: list[str] | None = None, limit: int = 50, offset: int = 0) -> ServerResponse

def catalog_search(self, keyword: str, *, project: str | None = None, limit: int = 20) -> ServerResponse

def catalog_get(self, table_ref: str, *, include_sample: bool = False) -> ServerResponse

def catalog_sample_queries(self, table_ref: str, *, limit: int = 5) -> ServerResponse
```

---

## 7. Library API 설계 (CatalogAPI)

### 7.1 클래스 구조 (feature-interface-cli Agent 피드백)

```python
# src/dli/api/catalog.py
from dli.models.common import ExecutionContext, ResultStatus
from dli.exceptions import CatalogError, ErrorCode

class CatalogAPI:
    """Programmatic Catalog API for integration with Airflow, Jupyter, etc."""
    
    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()
        self._service: CatalogService | None = None

    @property
    def _is_mock_mode(self) -> bool:
        return self.context.execution_mode == ExecutionMode.MOCK

    def list_tables(self, *, project: str | None = None, dataset: str | None = None,
                   owner: str | None = None, limit: int = 50, offset: int = 0) -> CatalogListResult:
        """List tables with filters."""
        if self._is_mock_mode:
            return self._mock_list_tables(project, dataset, limit, offset)
        
        # Server execution logic...

    def get_table(self, table_ref: str, *, include_sample: bool = False) -> TableDetailResult:
        """Get table details."""
        if self._is_mock_mode:
            return self._mock_table_detail(table_ref, include_sample)
        
        # Server execution logic...

    def search_tables(self, keyword: str, *, project: str | None = None, 
                     limit: int = 20) -> CatalogSearchResult:
        """Search tables by keyword."""
        if self._is_mock_mode:
            return self._mock_search_results(keyword, project, limit)
        
        # Server execution logic...
```

### 7.2 결과 모델

```python
# src/dli/models/catalog.py
from dli.models.common import BaseResult, ResultStatus

class CatalogListResult(BaseResult):
    tables: list[TableInfo]
    total_count: int
    has_more: bool

class TableDetailResult(BaseResult):
    table: TableDetail

class CatalogSearchResult(BaseResult):
    tables: list[TableInfo]
    total_matches: int
    keyword: str
```

---

## 8. Mock 모드 설계

### 8.1 Mock 데이터 구조 (expert-python Agent 피드백)

```python
# src/dli/core/client.py - Mock data 추가
MOCK_CATALOG_DATA = {
    "tables": [
        {
            "name": "my-project.analytics.users",
            "engine": "bigquery",
            "owner": "data-team@company.com",
            "team": "analytics",
            "tags": ["tier::critical", "pii"],
            "row_count": 1000000,
            "last_updated": "2025-12-30T10:00:00Z"
        },
        {
            "name": "my-project.finance.transactions",
            "engine": "bigquery", 
            "owner": "finance-team@company.com",
            "team": "finance",
            "tags": ["tier::high", "audit"],
            "row_count": 50000000,
            "last_updated": "2025-12-31T02:30:00Z"
        }
    ],
    "table_details": {
        "my-project.analytics.users": {
            "name": "my-project.analytics.users",
            "engine": "bigquery",
            "description": "Customer user data with PII",
            "columns": [
                {"name": "user_id", "data_type": "STRING", "is_pii": False, "fill_rate": 1.0},
                {"name": "email", "data_type": "STRING", "is_pii": True, "fill_rate": 0.95},
                {"name": "created_at", "data_type": "TIMESTAMP", "is_pii": False, "fill_rate": 1.0}
            ]
        }
    }
}
```

### 8.2 Mock 클라이언트 메서드

```python
# BasecampClient에 추가
def catalog_list(self, *, project=None, dataset=None, **filters) -> ServerResponse:
    if self.mock_mode:
        tables = self._filter_mock_tables(project, dataset, **filters)
        return ServerResponse(success=True, data=tables)
    # 실제 API 호출...

def _filter_mock_tables(self, project=None, dataset=None, **filters):
    """Mock 테이블 필터링 로직"""
    tables = MOCK_CATALOG_DATA["tables"].copy()
    
    if project:
        tables = [t for t in tables if t["name"].startswith(f"{project}.")]
    if dataset:
        tables = [t for t in tables if f".{dataset}." in t["name"]]
    
    return tables[:filters.get("limit", 50)]
```

---

## 9. 데이터 모델

### 7.1 목록용 (TableInfo)

```python
class TableInfo(BaseModel):
    name: str = Field(..., description="Table name (project.dataset.table)")
    engine: str
    owner: str | None = None
    team: str | None = None
    tags: list[str] = Field(default_factory=list)
    row_count: int | None = None
    last_updated: datetime | None = None
```

### 7.2 상세용 (TableDetail)

```python
class TableDetail(BaseModel):
    name: str
    engine: str
    description: str | None = None
    tags: list[str] = Field(default_factory=list)
    basecamp_url: str

    ownership: OwnershipInfo
    columns: list[ColumnInfo]
    freshness: FreshnessInfo
    quality: QualityInfo
    impact: ImpactSummary
    sample_queries: list[SampleQuery] = Field(default_factory=list)
    sample_data: list[dict] | None = None  # --sample 시에만

class ColumnInfo(BaseModel):
    name: str
    data_type: str
    description: str | None = None
    is_pii: bool = False
    fill_rate: float | None = None    # 0.0 ~ 1.0
    distinct_count: int | None = None

class ImpactSummary(BaseModel):
    total_downstream: int
    tables: list[str] = Field(default_factory=list)
    datasets: list[str] = Field(default_factory=list)
    metrics: list[str] = Field(default_factory=list)
    dashboards: list[str] = Field(default_factory=list)
```

---

## 10. 에러 처리 및 코드 (Agent 리뷰 반영)

### 10.1 Error Code 할당 (DLI-7xx 범위)

```python
# src/dli/exceptions.py에 추가
class ErrorCode(str, Enum):
    # ... 기존 코드들 ...
    
    # Catalog errors (DLI-7xx)
    CATALOG_CONNECTION_ERROR = "DLI-701"
    CATALOG_TABLE_NOT_FOUND = "DLI-702" 
    CATALOG_INVALID_IDENTIFIER = "DLI-703"
    CATALOG_ACCESS_DENIED = "DLI-704"
    CATALOG_ENGINE_NOT_SUPPORTED = "DLI-705"
    CATALOG_SCHEMA_TOO_LARGE = "DLI-706"

class CatalogError(DLIError):
    """Base catalog error."""
    pass

class TableNotFoundError(CatalogError):
    def __init__(self, table_ref: str):
        super().__init__(
            message=f"Table '{table_ref}' not found",
            code=ErrorCode.CATALOG_TABLE_NOT_FOUND
        )

class InvalidIdentifierError(CatalogError):
    def __init__(self, identifier: str):
        super().__init__(
            message=f"Invalid identifier format: '{identifier}'",
            code=ErrorCode.CATALOG_INVALID_IDENTIFIER
        )
```

### 10.2 에러 메시지 매핑

| 상황 | Error Code | Exception | 메시지 |
|------|-----------|-----------|--------|
| 서버 연결 불가 | DLI-701 | `CatalogConnectionError` | `Cannot connect to Basecamp server` |
| 테이블 없음 | DLI-702 | `TableNotFoundError` | `Table '{ref}' not found` |
| 잘못된 식별자 | DLI-703 | `InvalidIdentifierError` | `Invalid identifier format: '{identifier}'` |
| 권한 없음 | DLI-704 | `CatalogAccessDeniedError` | `Access denied to catalog resources` |
| 미지원 엔진 | DLI-705 | `UnsupportedEngineError` | `Engine '{engine}' not supported` |

---

## 11. CLI 등록 단계 (feature-interface-cli Agent 피드백)

### 11.1 commands/__init__.py 업데이트

```python
# src/dli/commands/__init__.py
from .dataset import dataset_app
from .metric import metric_app  
from .workflow import workflow_app
from .catalog import catalog_app  # ADD THIS
from .transpile import transpile_app
from .lineage import lineage_app
from .quality import quality_app
from .config import config_app

__all__ = [
    "dataset_app",
    "metric_app", 
    "workflow_app",
    "catalog_app",  # ADD THIS
    "transpile_app", 
    "lineage_app",
    "quality_app",
    "config_app",
]
```

### 11.2 main.py 등록

```python
# src/dli/main.py
from dli.commands import (
    config_app,
    dataset_app,
    metric_app,
    workflow_app,
    catalog_app,  # ADD THIS
    transpile_app,
    lineage_app,
    quality_app,
)

# Register subcommands
app.add_typer(config_app, name="config")
app.add_typer(dataset_app, name="dataset")
app.add_typer(metric_app, name="metric")
app.add_typer(workflow_app, name="workflow")
app.add_typer(catalog_app, name="catalog")  # ADD THIS
app.add_typer(transpile_app, name="transpile")
app.add_typer(lineage_app, name="lineage")
app.add_typer(quality_app, name="quality")
```

### 11.3 API 등록 (__init__.py)

```python
# src/dli/__init__.py
from .api.catalog import CatalogAPI  # ADD THIS
from .api.config import ConfigAPI
from .api.dataset import DatasetAPI
# ... other imports ...

__all__ = [
    # API Classes
    "CatalogAPI",  # ADD THIS
    "ConfigAPI", 
    "DatasetAPI",
    # ... other exports ...
]
```

---

## 9. 구현 가이드

### 9.1 디렉토리 구조

```
src/dli/
├── commands/
│   └── catalog.py         # CLI 커맨드 (catalog_app)
└── core/
    └── catalog/
        ├── __init__.py
        └── models.py      # TableInfo, TableDetail, etc.
```

### 9.2 참조 패턴

| 구현 항목 | 참조 파일 |
|-----------|-----------|
| CLI 커맨드 구조 | `commands/dataset.py`, `commands/workflow.py` |
| Rich 출력 | `commands/utils.py` |
| API 클라이언트 메서드 | `core/client.py` |
| Pydantic 모델 | `core/workflow/models.py` |
| Lineage 연계 | `core/lineage/client.py` |

### 9.3 테스트 참조

| 테스트 항목 | 참조 파일 |
|-------------|-----------|
| CLI 테스트 | `tests/cli/test_workflow_cmd.py` |
| 모델 테스트 | `tests/core/workflow/test_models.py` |

---

## 12. 구현 우선순위 (간소화)

### Phase 1 (MVP) - 핵심 기능

- [ ] 암시적 라우팅 (1/2/3/4-part 감지)
- [ ] 테이블 목록 조회 (`dli catalog <1-part>`, `<2-part>`)  
- [ ] 테이블 상세 조회 (기본 섹션들)
- [ ] `catalog list` 기본 필터 (project, dataset)
- [ ] `catalog search` 키워드 검색
- [ ] Rich 테이블 출력 + JSON 형식
- [ ] Mock 모드 (기존 client.py 패턴)
- [ ] CatalogAPI 클래스 (Library API)
- [ ] Error Code 할당 (DLI-7xx)

### Phase 2 - 실용적 확장

- [ ] `--sample` 옵션 (샘플 데이터)
- [ ] `--section` 옵션 (특정 섹션만)  
- [ ] Sample Queries 섹션
- [ ] 추가 필터 (owner, team, tag)

### Phase 3 - 향후 고려

- [ ] 기본 페이지네이션 (offset/limit)
- [ ] 외부 메타데이터 연동

## 13. 구현 가이드 (간소화)

### 13.1 핵심 파일 구조

```
src/dli/
├── api/catalog.py              # CatalogAPI (Library API)
├── commands/catalog.py         # CLI 명령어 
├── core/catalog/
│   ├── __init__.py
│   └── models.py              # TableInfo, TableDetail 등
└── exceptions.py              # Error codes 추가
```

### 13.2 구현 순서

1. **모델 정의** (`core/catalog/models.py`)
2. **Mock 데이터** (`core/client.py`에 추가)
3. **CLI 명령어** (`commands/catalog.py`)
4. **Library API** (`api/catalog.py`)  
5. **CLI 등록** (`main.py`, `commands/__init__.py`)
6. **테스트** (기본 CLI 테스트)

---

## Appendix: 커맨드 요약

```bash
# 암시적 라우팅 (주 인터페이스)
dli catalog <project>                           # 1-part: 프로젝트 내 목록
dli catalog <project.dataset>                   # 2-part: 데이터셋 내 목록
dli catalog <project.dataset.table>             # 3-part: 테이블 상세
dli catalog <engine.project.dataset.table>      # 4-part: 엔진 지정

# 명시적 커맨드 (고급 필터)
dli catalog list [--project] [--dataset] [--owner] [--team] [--tag]
dli catalog search <keyword> [--project]

# 공통 옵션
--format, -f    table|json
--limit, -n     <number>
--offset        <number>

# 상세 조회 옵션
--section, -s   <section>
--sample
```

---

## Appendix: 결정 사항 (인터뷰 기반)

| 항목 | 결정 | 근거 |
|------|------|------|
| Engine 감지 | CLI 하드코딩 | 지원 엔진이 제한적, 서버 호출 최소화 |
| URN 변환 | 서버 책임 | CLI 독립성 유지, 형식 변경 시 CLI 수정 불필요 |
| PII 마스킹 | 서버 책임 | 정책 일관성, CLI는 표시만 담당 |
| CLI 구조 | 암시적 우선 | 간결한 UX, list/search는 고급 사용자용 |
| 태그 필터 | AND 조건 | 교집합 검색이 더 실용적 |
