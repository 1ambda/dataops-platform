# RELEASE: Catalog Command Implementation

> **Version:** 1.2.0
> **Release Date:** 2025-12-31
> **Status:** Phase 1 (MVP) Complete + Library API + Result Models
> **Industry Benchmarked:** Databricks CLI, DBT CLI, SqlMesh CLI

---

## Summary

`dli catalog` 커맨드의 Phase 1 (MVP) 구현이 완료되었습니다. Basecamp Server에서 관리하는 데이터 카탈로그를 탐색하고 테이블 메타데이터를 조회할 수 있습니다.

---

## Implemented Features

### Phase 1 (MVP) - Complete

| Feature | Status | Notes |
|---------|--------|-------|
| Implicit routing (1/2/3/4-part) | ✅ | 식별자 파트 수에 따라 자동 동작 결정 |
| Table list (project) | ✅ | `dli catalog <project>` |
| Table list (dataset) | ✅ | `dli catalog <project.dataset>` |
| Table detail | ✅ | `dli catalog <project.dataset.table>` |
| Engine-specific detail | ✅ | `dli catalog <engine.project.dataset.table>` |
| `catalog list` command | ✅ | 필터 조합으로 테이블 목록 조회 |
| `catalog search` command | ✅ | 키워드 기반 전체 검색 |
| Rich output | ✅ | 테이블 형식 출력 |
| JSON output | ✅ | `--format json` |
| Mock mode | ✅ | 테스트용 모의 데이터 |
| Basic filters (project, dataset) | ✅ | `--project`, `--dataset` |

### Phase 2 - Pending

| Feature | Status | Notes |
|---------|--------|-------|
| `--sample` option | ⏳ | 샘플 데이터 포함 |
| `--section` option | ✅ | 특정 섹션만 출력 (MVP에서 구현됨) |
| Sample Queries section | ✅ | Popular queries 표시 (MVP에서 구현됨) |
| Additional filters (owner, team, tag) | ✅ | MVP에서 구현됨 |

### Phase 3 - Pending

| Feature | Status | Notes |
|---------|--------|-------|
| Cursor-based pagination | ⏳ | 대규모 데이터셋 지원 |
| Local cache | ⏳ | 응답 캐싱 (선택적) |

---

## Error Codes (DLI-7xx)

| Code | Exception | 설명 |
|------|-----------|------|
| DLI-701 | `CatalogError` | 기본 Catalog 에러 |
| DLI-702 | `CatalogTableNotFoundError` | 테이블 미발견 |
| DLI-703 | `InvalidIdentifierError` | 잘못된 식별자 형식 |
| DLI-704 | (Reserved) | 접근 거부 |
| DLI-705 | `UnsupportedEngineError` | 미지원 엔진 |

---

## Library API (CatalogAPI)

```python
from dli import CatalogAPI, ExecutionContext, ExecutionMode
from dli.models.common import ResultStatus

# Mock 모드로 테스트
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = CatalogAPI(context=ctx)

# 테이블 목록 조회 → CatalogListResult
result = api.list_tables("my-project")
if result.status == ResultStatus.SUCCESS:
    print(f"Found {result.total_count} tables, has_more={result.has_more}")
    for table in result.tables:
        print(f"  - {table.name}")

# 테이블 상세 조회 → TableDetailResult
detail = api.get("my-project.analytics.users")
if detail.status == ResultStatus.SUCCESS and detail.table:
    print(f"Table: {detail.table.name}")
    print(f"Columns: {len(detail.table.columns)}")
else:
    print(f"Error: {detail.error_message}")

# 키워드 검색 → CatalogSearchResult
search = api.search("user")
print(f"Found {search.total_matches} matches for '{search.keyword}'")
for table in search.tables:
    print(f"  - {table.name}")
```

---

## Files Created

| File | Purpose |
|------|---------|
| `src/dli/core/catalog/__init__.py` | 모듈 exports |
| `src/dli/core/catalog/models.py` | Pydantic 데이터 모델 |
| `src/dli/commands/catalog.py` | CLI 커맨드 구현 |
| `src/dli/api/catalog.py` | Library API (CatalogAPI) |
| `tests/cli/test_catalog_cmd.py` | CLI 테스트 (30 tests) |
| `tests/api/test_catalog_api.py` | API 테스트 (30 tests) |
| `tests/core/catalog/test_models.py` | 모델 테스트 (54 tests) |

## Files Modified

| File | Changes |
|------|---------|
| `src/dli/core/client.py` | `catalog_*` 메서드 및 mock 데이터 추가 |
| `src/dli/commands/__init__.py` | `catalog_app` export 추가 |
| `src/dli/commands/utils.py` | `format_datetime` 공유 함수 추가 |
| `src/dli/commands/workflow.py` | `_format_datetime` 중복 제거, 공유 함수 사용 |
| `src/dli/main.py` | `catalog` 서브커맨드 등록 |
| `src/dli/models/common.py` | `CatalogListResult`, `TableDetailResult`, `CatalogSearchResult` 추가 (v1.2.0) |
| `src/dli/api/catalog.py` | 반환 타입을 Result 모델로 변경 (v1.2.0) |
| `tests/api/test_catalog_api.py` | 새 Result 모델 반환 타입에 맞게 테스트 업데이트 (v1.2.0) |

---

## Data Models

### Result Models (v1.2.0)

```python
class CatalogListResult(BaseModel):
    status: ResultStatus          # SUCCESS/FAILURE
    tables: list[TableInfo]       # 테이블 목록
    total_count: int              # 전체 개수
    has_more: bool                # 추가 결과 존재 여부
    error_message: str | None     # 에러 메시지

class TableDetailResult(BaseModel):
    status: ResultStatus
    table: TableDetail | None     # 테이블 상세 정보
    error_message: str | None

class CatalogSearchResult(BaseModel):
    status: ResultStatus
    tables: list[TableInfo]       # 검색 결과
    total_matches: int            # 전체 매치 수
    keyword: str                  # 검색 키워드
    error_message: str | None
```

### TableInfo (목록용)

```python
class TableInfo(BaseModel):
    name: str           # project.dataset.table
    engine: str
    owner: str | None
    team: str | None
    tags: list[str]
    row_count: int | None
    last_updated: datetime | None
```

### TableDetail (상세용)

```python
class TableDetail(BaseModel):
    name: str
    engine: str
    description: str | None
    tags: list[str]
    basecamp_url: str
    ownership: OwnershipInfo
    columns: list[ColumnInfo]
    freshness: FreshnessInfo
    quality: QualityInfo
    impact: ImpactSummary
    sample_queries: list[SampleQuery]
    sample_data: list[dict] | None
```

### Supporting Models

- `ColumnInfo` - 컬럼 메타데이터 (name, type, PII, statistics)
- `OwnershipInfo` - 소유권 정보 (owner, stewards, team, consumers)
- `FreshnessInfo` - 신선도 정보 (last_updated, avg_ingestion_time)
- `QualityInfo` - 품질 정보 (score, tests, warnings)
- `ImpactSummary` - 영향도 요약 (downstream tables, datasets, metrics, dashboards)
- `SampleQuery` - 인기 쿼리 (sql, usage_count)

---

## Client Methods Added

```python
# BasecampClient에 추가된 메서드

def catalog_list(
    self, *,
    project: str | None = None,
    dataset: str | None = None,
    owner: str | None = None,
    team: str | None = None,
    tags: list[str] | None = None,
    limit: int = 50,
    offset: int = 0
) -> ServerResponse

def catalog_search(
    self,
    keyword: str,
    *,
    project: str | None = None,
    limit: int = 20
) -> ServerResponse

def catalog_get(
    self,
    table_ref: str,
    *,
    include_sample: bool = False
) -> ServerResponse

def catalog_sample_queries(
    self,
    table_ref: str,
    *,
    limit: int = 5
) -> ServerResponse
```

---

## Usage Examples

```bash
# Implicit Routing
dli catalog my-project                           # 1-part: 프로젝트 내 목록
dli catalog my-project.analytics                 # 2-part: 데이터셋 내 목록
dli catalog my-project.analytics.users           # 3-part: 테이블 상세
dli catalog bigquery.my-project.analytics.users  # 4-part: 엔진 지정

# Explicit Commands
dli catalog list --project my-project
dli catalog list --owner data@example.com --tag tier::critical
dli catalog search user

# Common Options
dli catalog my-project --format json
dli catalog my-project --limit 10
dli catalog my-project.analytics.users --section columns
```

---

## Test Results

```
Total Tests: 1573 (전체 테스트 스위트)
- Catalog Model Tests: 54 passed
- Catalog CLI Tests: 30 passed  
- Catalog API Tests: 30 passed
- Type Check (pyright): 0 errors
- Lint Check (ruff): passed
```

---

## Code Quality Improvements (Refactoring)

1. **DRY 원칙 적용**: `_format_datetime` 중복 제거
   - `catalog.py`, `workflow.py`의 중복 함수를 `utils.py`의 공유 함수로 통합

2. **타입 안전성 개선**:
   - `dict` → `dict[str, Any]` 타입 힌트 강화

3. **불필요한 import 제거**:
   - `Panel` (미사용), `datetime` (공유 함수로 대체)

---

## Architecture Notes

### Engine Detection (Hardcoded)

```python
SUPPORTED_ENGINES = frozenset({"bigquery", "trino", "hive"})
```

CLI는 첫 번째 파트가 지원 엔진 목록에 있으면 4-part로 판단합니다.

### URN Conversion

- **책임**: Server (Basecamp API)
- **CLI 역할**: 사용자 입력을 그대로 API에 전달

### Impact Section (Lineage Reuse)

Impact 정보는 기존 `LineageClient.get_downstream()` 활용 가능 (Phase 2에서 통합 예정)

---

## Next Steps

### Phase 2 구현 시 참고사항

1. `--sample` 옵션: `catalog_get(..., include_sample=True)` 활용
2. Lineage 연계: `LineageClient` import하여 `ImpactSummary` 실제 데이터 채우기
3. 실제 API 연동: mock 조건문 아래에 HTTP 클라이언트 코드 추가

### API 엔드포인트 (서버 구현 필요)

| 동작 | Method | Endpoint |
|------|--------|----------|
| 테이블 목록 | GET | `/api/v1/catalog/tables` |
| 테이블 검색 | GET | `/api/v1/catalog/search` |
| 테이블 상세 | GET | `/api/v1/catalog/tables/{table_ref}` |
| 샘플 쿼리 | GET | `/api/v1/catalog/tables/{table_ref}/queries` |
| 샘플 데이터 | GET | `/api/v1/catalog/tables/{table_ref}/sample` |

---

## Changelog

### v1.2.0 (2025-12-31)

- **Result Models**: FEATURE_CATALOG.md 명세 기반 Result 모델 구현
  - `CatalogListResult`: 테이블 목록 조회 결과 (status, tables, total_count, has_more)
  - `TableDetailResult`: 테이블 상세 조회 결과 (status, table, error_message)
  - `CatalogSearchResult`: 검색 결과 (status, tables, total_matches, keyword)
- **CatalogAPI 반환 타입 개선**:
  - `list_tables()` → `CatalogListResult` (기존: `list[TableInfo]`)
  - `get()` → `TableDetailResult` (기존: `TableDetail | None`)
  - `search()` → `CatalogSearchResult` (기존: `list[TableInfo]`)
- **테스트 업데이트**: 새 Result 모델 반환 타입에 맞게 30개 API 테스트 업데이트
- **전체 테스트**: 1573개 통과

### v1.1.0 (2025-12-31)

- **Library API**: `CatalogAPI` 클래스 추가 (`list_tables`, `get`, `search`)
- **Error Codes**: DLI-7xx 범위 에러 코드 할당
  - `CatalogError` (DLI-701)
  - `CatalogTableNotFoundError` (DLI-702)
  - `InvalidIdentifierError` (DLI-703)
  - `UnsupportedEngineError` (DLI-705)
- **FEATURE_CATALOG.md v1.2.0**: 업계 표준 벤치마킹 (Databricks, DBT, SqlMesh)
- **Code Review**: expert-python Agent 리뷰 통과 (리팩토링 불필요)
- **테스트**: API 테스트 30개 추가 (총 114 catalog 테스트)

### v1.0.0 (2025-12-30)

- Initial implementation of `dli catalog` command
- Implicit routing with 1/2/3/4-part identifier support
- `catalog list` and `catalog search` subcommands
- Complete data models (TableInfo, TableDetail, etc.)
- Mock mode support for development/testing
- Rich and JSON output formats
- 84 tests with full coverage
- Code quality refactoring (DRY, type safety)
