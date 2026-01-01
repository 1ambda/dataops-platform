# FEATURE: Lineage 기능

> **Version:** 1.1.0
> **Status:** ✅ Complete (CLI + Library API + Tests)
> **Last Updated:** 2026-01-01
> **Industry Benchmarked:** OpenLineage, DataHub, dbt, SqlMesh

---

## 1. 개요

### 1.1 목적

`dli lineage` 커맨드는 데이터 리소스 간의 의존성 관계를 탐색하고 시각화합니다. 테이블, 데이터셋, 메트릭 간의 업스트림(소스) 및 다운스트림(소비자) 관계를 파악하여 데이터 영향도 분석과 디버깅을 지원합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **서버 기반** | 모든 Lineage 데이터는 Basecamp Server에서 조회 (로컬 SQLGlot 미사용) |
| **테이블 레벨 우선** | MVP는 테이블 레벨 Lineage만 지원 (컬럼 레벨은 Phase 2) |
| **심층 탐색** | 다단계 의존성 탐색 지원 (`--depth` 옵션) |
| **다중 출력 형식** | Tree, Table, JSON 형식 지원 (Mermaid/GraphViz는 Phase 2) |

### 1.3 구현 완료 기능 (v1.1.0)

- ✅ **전체 Lineage 조회**: 업스트림 + 다운스트림 동시 표시
- ✅ **업스트림 분석**: 리소스가 의존하는 소스 테이블 탐색
- ✅ **다운스트림 분석**: 리소스를 사용하는 소비자 탐색
- ✅ **트리 시각화**: Rich 기반 계층 구조 출력
- ✅ **JSON 출력**: 프로그래매틱 처리용 구조화 데이터
- ✅ **LineageAPI**: Library API 클래스 (get_lineage, get_upstream, get_downstream)
- ✅ **DLI-9xx 에러 코드**: LineageError, LineageNotFoundError, LineageTimeoutError
- ✅ **테스트 커버리지**: 60개 테스트 (CLI 17 + API 43)

### 1.4 향후 확장 기능 (Phase 2+)

- ⏸ **컬럼 레벨 Lineage**: 컬럼 단위 의존성 추적
- ⏸ **OpenLineage 통합**: 표준 메타데이터 포맷 지원
- ⏸ **Export 형식**: Mermaid, GraphViz, JSON-LD
- ⏸ **Impact Analysis**: 변경 영향도 분석 (고급 기능)

### 1.5 업계 표준 벤치마킹

| 도구 | 핵심 기능 | dli에 반영 |
|------|-----------|------------|
| **OpenLineage** | 표준화된 Lineage 메타데이터 포맷, Facets | Phase 2: OpenLineage 호환 출력 |
| **DataHub** | GraphQL 기반 Lineage 조회, Impact Analysis | 깊이 기반 탐색, Impact 분석 |
| **dbt** | `dbt docs generate`, DAG 시각화 | Tree 시각화, --depth 옵션 |
| **SqlMesh** | Column-level lineage, AST 분석 | Phase 2: 컬럼 레벨 지원 |
| **Atlan** | 영향도 분석, 비즈니스 컨텍스트 | Impact Summary 통합 |

---

## 2. 아키텍처

### 2.1 컴포넌트 관계

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLI Flow                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │ User Input  │───▶│ LineageClient    │───▶│ Basecamp      │  │
│  │ (Resource   │    │ (CLI/API Layer)  │    │ Server API    │  │
│  │  Name)      │    └──────────────────┘    └───────────────┘  │
│  └─────────────┘                                    │          │
│                                                     ▼          │
│                              ┌──────────────────────────────┐  │
│                              │ Lineage Storage              │  │
│                              │ (Dataset Dependencies,       │  │
│                              │  Table References)           │  │
│                              └──────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                       Data Sources                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │ Dataset     │    │ Metric           │    │ External      │  │
│  │ Specs       │    │ Definitions      │    │ Tables        │  │
│  │ (SQL refs)  │    │ (Aggregations)   │    │ (Source)      │  │
│  └─────────────┘    └──────────────────┘    └───────────────┘  │
│                                                                 │
│  Note: Lineage는 등록된 Dataset/Metric의 SQL 참조에서 추출      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| Lineage 소스 | Basecamp Server Only | 등록된 Dataset의 정합성 보장, 로컬 분석 복잡성 회피 |
| 로컬 SQLGlot 사용 | 미사용 (Phase 1) | 서버에서 파싱/분석 수행, CLI는 조회만 담당 |
| 기본 깊이 | -1 (무제한) | 전체 의존성 파악이 일반적 사용 패턴 |
| 출력 형식 | Tree (기본) + Table + JSON | 직관적 시각화 + 프로그래매틱 처리 |
| 컬럼 레벨 | Phase 2 | MVP 범위 제한, 테이블 레벨 우선 |

### 2.3 기존 시스템 통합 지점

| 통합 영역 | 기존 패턴 | Lineage 적용 |
|-----------|-----------|--------------|
| **CLI 커맨드** | `commands/dataset.py`, `commands/catalog.py` | `commands/lineage.py` (구현 완료) |
| **출력 유틸리티** | `commands/utils.py` (print_error, console) | Rich Tree, Panel 활용 |
| **API 클라이언트** | `core/client.py` (BasecampClient) | `get_lineage()` 메서드 활용 |
| **Core 모듈** | `core/workflow/`, `core/catalog/` 구조 | `core/lineage/` 모듈 (구현 완료) |
| **Catalog 연계** | `commands/catalog.py` (Impact 섹션) | `LineageClient.get_downstream()` 재사용 |

### 2.4 개발 불확실성 및 해결 전략

| 불확실성 | 심각도 | 해결 전략 |
|----------|--------|-----------|
| 대규모 Lineage 성능 | 🟡 중간 | --depth 옵션으로 제한, 페이지네이션 고려 |
| 컬럼 레벨 복잡성 | 🟡 중간 | Phase 2로 연기, 테이블 레벨 우선 안정화 |
| OpenLineage 호환성 | 🟢 낮음 | 표준 포맷 연구 후 Phase 2에서 구현 |
| 순환 의존성 처리 | 🟢 낮음 | visited set으로 사이클 방지 (구현 완료) |

---

## 3. Use Cases

### 3.1 Use-case 1: 전체 Lineage 조회

데이터셋의 전체 의존성 그래프를 조회합니다.

**예시:**
```bash
$ dli lineage show iceberg.analytics.daily_clicks

┌──────────────────────────────────────────────────────┐
│ Resource                                             │
├──────────────────────────────────────────────────────┤
│ iceberg.analytics.daily_clicks                       │
│ Type: Dataset                                        │
│ Owner: analytics-team@company.com                    │
│ Team: analytics                                      │
│ Description: Daily aggregated click events          │
└──────────────────────────────────────────────────────┘

Upstream (depends on)
└── iceberg.raw.click_events (Dataset)
    └── kafka.events.clicks (External)

Downstream (depended by)
├── iceberg.analytics.weekly_clicks (Dataset)
│   └── metrics.weekly_click_rate (Metric)
└── metrics.daily_ctr (Metric)

Summary: 2 upstream, 3 downstream
```

### 3.2 Use-case 2: 업스트림 분석 (소스 탐색)

데이터셋이 의존하는 소스 테이블을 분석합니다.

**예시:**
```bash
$ dli lineage upstream iceberg.analytics.daily_clicks --depth 2

Upstream Dependencies
└── iceberg.analytics.daily_clicks (Dataset)
    └── iceberg.raw.click_events (Dataset)
        └── kafka.events.clicks (External)
            └── [Max depth reached]

Total upstream dependencies: 2
```

### 3.3 Use-case 3: 다운스트림 분석 (영향도 파악)

데이터셋을 사용하는 소비자를 분석합니다. 테이블 변경 전 영향도 확인에 유용합니다.

**예시:**
```bash
$ dli lineage downstream iceberg.raw.click_events --depth 1

Downstream Dependents
└── iceberg.raw.click_events (Dataset)
    ├── iceberg.analytics.daily_clicks (Dataset)
    ├── iceberg.analytics.hourly_clicks (Dataset)
    └── metrics.click_throughput (Metric)

Total downstream dependents: 3
```

### 3.4 Use-case 4: JSON 출력 (프로그래매틱 처리)

CI/CD 파이프라인이나 스크립트에서 Lineage 데이터를 활용합니다.

**예시:**
```bash
$ dli lineage show iceberg.analytics.daily_clicks --format json

{
  "root": {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "analytics-team@company.com",
    "team": "analytics",
    "description": "Daily aggregated click events",
    "tags": ["tier::critical", "pii"]
  },
  "nodes": [
    {
      "name": "iceberg.raw.click_events",
      "type": "Dataset",
      "depth": -1
    },
    {
      "name": "metrics.daily_ctr",
      "type": "Metric",
      "depth": 1
    }
  ],
  "edges": [
    {
      "source": "iceberg.raw.click_events",
      "target": "iceberg.analytics.daily_clicks",
      "edge_type": "direct"
    },
    {
      "source": "iceberg.analytics.daily_clicks",
      "target": "metrics.daily_ctr",
      "edge_type": "direct"
    }
  ],
  "summary": {
    "direction": "both",
    "max_depth": -1,
    "total_upstream": 1,
    "total_downstream": 1
  }
}
```

---

## 4. CLI 설계

### 4.1 커맨드 구조 (구현 완료)

```bash
# 전체 Lineage (업스트림 + 다운스트림)
dli lineage show <resource>

# 업스트림만
dli lineage upstream <resource>

# 다운스트림만
dli lineage downstream <resource>
```

### 4.2 공통 옵션

| 옵션 | 단축 | 설명 | 기본값 |
|------|------|------|--------|
| `--depth` | `-d` | 최대 탐색 깊이 (-1: 무제한) | `-1` |
| `--format` | `-f` | 출력 형식 (`table`/`json`) | `table` |
| `--path` | `-p` | 프로젝트 경로 | 현재 디렉토리 |

### 4.3 Phase 2 추가 예정 옵션

| 옵션 | 설명 |
|------|------|
| `--export` | 내보내기 형식 (`mermaid`/`graphviz`/`json-ld`) |
| `--column` | 컬럼 레벨 Lineage 활성화 |
| `--include-external` | 외부 테이블 포함 여부 |
| `--filter-type` | 노드 타입 필터 (`Dataset`/`Metric`/`External`) |

### 4.4 출력 형식

#### Tree 형식 (기본)

```
Upstream (depends on)
└── source_table (Dataset)
    └── raw_events (External)

Downstream (depended by)
├── derived_table_a (Dataset)
└── metric_x (Metric)
```

#### Table 형식 (`--format table`)

```
┌────────────────────────┬──────────┬───────────┬───────┬────────┐
│ Name                   │ Type     │ Direction │ Depth │ Owner  │
├────────────────────────┼──────────┼───────────┼───────┼────────┤
│ iceberg.raw.events     │ Dataset  │ upstream  │ 1     │ data@  │
│ metrics.daily_ctr      │ Metric   │ downstream│ 1     │ ml@    │
└────────────────────────┴──────────┴───────────┴───────┴────────┘
```

#### JSON 형식 (`--format json`)

Section 3.4 참조

---

## 5. Library API 설계

### 5.1 LineageAPI 클래스

> **Status:** ✅ Implemented (v1.1.0) - See [LINEAGE_RELEASE.md](./LINEAGE_RELEASE.md) for implementation details

**API 인터페이스:**
- `get_lineage(resource_name, direction, depth)`: 전체 Lineage 조회
- `get_upstream(resource_name, depth)`: 업스트림 의존성 조회
- `get_downstream(resource_name, depth)`: 다운스트림 의존성 조회

**실행 모드:**
- MOCK: 테스트용 모의 데이터
- SERVER: Basecamp Server API 호출
- DI 지원: `client` 파라미터로 BasecampClient 주입 가능

**사용 예시:**
```python
from dli import LineageAPI, ExecutionContext, ExecutionMode

# Mock 모드 (테스트)
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = LineageAPI(context=ctx)
result = api.get_lineage("iceberg.analytics.daily_clicks")

# Server 모드 (프로덕션)
ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
api = LineageAPI(context=ctx)
upstream = api.get_upstream("iceberg.analytics.daily_clicks", depth=2)
```

### 5.2 향후 확장 (Phase 2+)

- `get_impact_summary()`: 영향도 분석 요약
- `export_mermaid()`: Mermaid 다이어그램 생성
- `export_graphviz()`: GraphViz DOT 포맷 변환

---

## 6. Basecamp API

### 6.1 엔드포인트

| 동작 | Method | Endpoint |
|------|--------|----------|
| Lineage 조회 | GET | `/api/v1/lineage/{resource_name}` |
| 업스트림 조회 | GET | `/api/v1/lineage/{resource_name}/upstream` |
| 다운스트림 조회 | GET | `/api/v1/lineage/{resource_name}/downstream` |

### 6.2 쿼리 파라미터

| 파라미터 | 타입 | 설명 | 기본값 |
|----------|------|------|--------|
| `direction` | string | `upstream`, `downstream`, `both` | `both` |
| `depth` | int | 최대 탐색 깊이 (-1: 무제한) | `-1` |
| `include_external` | bool | 외부 테이블 포함 여부 | `true` |

### 6.3 응답 형식

```json
{
  "root": {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "analytics-team@company.com",
    "team": "analytics",
    "description": "Daily aggregated click events",
    "tags": ["tier::critical"]
  },
  "nodes": [
    {
      "name": "iceberg.raw.click_events",
      "type": "Dataset",
      "owner": "data-team@company.com",
      "team": "data",
      "description": "Raw click event stream",
      "tags": [],
      "depth": -1
    }
  ],
  "edges": [
    {
      "source": "iceberg.raw.click_events",
      "target": "iceberg.analytics.daily_clicks",
      "edge_type": "direct"
    }
  ],
  "total_upstream": 1,
  "total_downstream": 2
}
```

### 6.4 클라이언트 메서드 (BasecampClient - 구현 완료)

```python
# core/client.py
def get_lineage(
    self,
    resource_name: str,
    direction: str = "both",
    depth: int = -1,
) -> ServerResponse:
    """Get lineage information for a resource.

    Args:
        resource_name: Fully qualified resource name
        direction: 'upstream', 'downstream', or 'both'
        depth: Maximum traversal depth (-1 for unlimited)

    Returns:
        ServerResponse with lineage data
    """
    if self.mock_mode:
        return self._mock_lineage(resource_name, direction, depth)

    return self._get(
        f"/api/v1/lineage/{resource_name}",
        params={"direction": direction, "depth": depth},
    )
```

---

## 7. 데이터 모델 (구현 완료)

### 7.1 LineageNode

```python
# core/lineage/__init__.py
@dataclass
class LineageNode:
    """Represents a single node in the lineage graph."""

    name: str                          # Fully qualified name
    type: str = "Dataset"              # Dataset, Metric, External
    owner: str | None = None           # Owner email
    team: str | None = None            # Team name
    description: str | None = None     # Resource description
    tags: list[str] = field(default_factory=list)
    depth: int = 0                     # Distance from root (negative=upstream)
```

### 7.2 LineageEdge

```python
@dataclass
class LineageEdge:
    """Represents an edge (dependency) between two lineage nodes."""

    source: str                        # Upstream node name
    target: str                        # Downstream node name
    edge_type: str = "direct"          # direct, indirect
```

### 7.3 LineageResult

```python
@dataclass
class LineageResult:
    """Result of a lineage query."""

    root: LineageNode
    nodes: list[LineageNode] = field(default_factory=list)
    edges: list[LineageEdge] = field(default_factory=list)
    direction: LineageDirection = LineageDirection.BOTH
    max_depth: int = -1
    total_upstream: int = 0
    total_downstream: int = 0

    @property
    def upstream_nodes(self) -> list[LineageNode]:
        """Get nodes that are upstream of the root."""
        ...

    @property
    def downstream_nodes(self) -> list[LineageNode]:
        """Get nodes that are downstream of the root."""
        ...
```

### 7.4 LineageDirection

```python
class LineageDirection(str, Enum):
    """Direction for lineage traversal."""

    UPSTREAM = "upstream"
    DOWNSTREAM = "downstream"
    BOTH = "both"
```

---

## 8. 에러 처리

### 8.1 Error Code 할당 (DLI-9xx 범위)

> **Status:** ✅ Implemented (v1.1.0)

| Code | Name | Exception Class |
|------|------|-----------------|
| DLI-900 | LINEAGE_NOT_FOUND | LineageNotFoundError |
| DLI-901 | LINEAGE_DEPTH_EXCEEDED | LineageError |
| DLI-902 | LINEAGE_CYCLE_DETECTED | LineageError |
| DLI-903 | LINEAGE_SERVER_ERROR | LineageError |
| DLI-904 | LINEAGE_TIMEOUT | LineageTimeoutError |

### 8.2 Exception 클래스

- `LineageError`: Base exception for lineage operations
- `LineageNotFoundError`: Resource not found in lineage graph
- `LineageTimeoutError`: Lineage query timeout

### 8.3 내부 예외 (core/lineage/client.py)

```python
class LineageClientError(Exception):
    """Exception raised for lineage client errors."""
    def __init__(self, message: str, status_code: int = 500):
        self.message = message
        self.status_code = status_code
```

---

## 9. 구현 현황

### 9.1 v1.1.0 완료 항목

- ✅ `LineageDirection` enum
- ✅ `LineageNode` dataclass
- ✅ `LineageEdge` dataclass
- ✅ `LineageResult` dataclass
- ✅ `LineageClient` 클래스 (core/lineage/client.py)
- ✅ `LineageClientError` 예외
- ✅ `dli lineage show` 커맨드
- ✅ `dli lineage upstream` 커맨드
- ✅ `dli lineage downstream` 커맨드
- ✅ Tree 시각화 출력
- ✅ Table 출력 지원
- ✅ JSON 출력 지원
- ✅ `--depth` 옵션
- ✅ 순환 의존성 방지 (visited set)
- ✅ `LineageAPI` 클래스 (api/lineage.py, 367 lines)
- ✅ DLI-9xx 에러 코드 (DLI-900 ~ DLI-904)
- ✅ Exception 클래스 (LineageError, LineageNotFoundError, LineageTimeoutError)
- ✅ 테스트 커버리지 (60 tests: CLI 17 + API 43)
- ✅ Mock 모드 지원

### 9.2 향후 확장 (Phase 2+)

- ⏸ `--export` 옵션 (Mermaid, GraphViz)
- ⏸ 컬럼 레벨 Lineage
- ⏸ OpenLineage 호환 출력
- ⏸ JSON-LD 형식 지원
- ⏸ Impact Analysis 고급 기능
- ⏸ 실시간 Lineage 업데이트

---

## 10. 디렉토리 구조

### 10.1 구현 완료 (v1.1.0)

```
src/dli/
├── api/
│   └── lineage.py              # LineageAPI (367 lines) ✅
├── commands/
│   └── lineage.py              # CLI 커맨드 (383 lines) ✅
└── core/
    └── lineage/
        ├── __init__.py         # 모델 정의 (108 lines) ✅
        └── client.py           # LineageClient (211 lines) ✅

tests/
├── api/
│   └── test_lineage_api.py     # API 테스트 (43 tests) ✅
└── cli/
    └── test_lineage_cmd.py     # CLI 테스트 (17 tests) ✅
```

### 10.2 향후 확장 (Phase 2+)

```
src/dli/
└── core/
    └── lineage/
        └── export.py           # Export 기능 (Mermaid, GraphViz)
```

---

## 11. CLI 등록

> **Status:** ✅ Complete

- `commands/__init__.py`: Export `lineage_app`
- `main.py`: Register `lineage_app` as `dli lineage` subcommand

---

## Appendix A: 커맨드 요약

```bash
# 전체 Lineage (업스트림 + 다운스트림)
dli lineage show <resource>                    # Tree 출력
dli lineage show <resource> --format json      # JSON 출력
dli lineage show <resource> --depth 3          # 깊이 제한

# 업스트림만 (소스 분석)
dli lineage upstream <resource>
dli lineage upstream <resource> --depth 2

# 다운스트림만 (영향도 분석)
dli lineage downstream <resource>
dli lineage downstream <resource> --depth 1

# 공통 옵션
--depth, -d     <number>    최대 탐색 깊이 (-1: 무제한)
--format, -f    table|json  출력 형식
--path, -p      <path>      프로젝트 경로
```

---

## Appendix B: Catalog 연계 (Impact 섹션)

CATALOG_FEATURE.md의 Impact 섹션에서 LineageClient를 재사용합니다:

```python
# commands/catalog.py
from dli.core.lineage import LineageClient

def get_impact_summary(client: BasecampClient, table_ref: str) -> ImpactSummary:
    """Get impact summary using LineageClient."""
    lineage_client = LineageClient(client)
    downstream = lineage_client.get_downstream(table_ref, depth=1)

    return ImpactSummary(
        total_downstream=downstream.total_downstream,
        tables=[n.name for n in downstream.nodes if n.type == "Dataset"],
        datasets=[n.name for n in downstream.nodes if n.type == "Dataset"],
        metrics=[n.name for n in downstream.nodes if n.type == "Metric"],
        dashboards=[],
    )
```

---

## Appendix C: 결정 사항 요약

| 항목 | 결정 | 근거 |
|------|------|------|
| Lineage 소스 | Server Only | 등록된 Dataset 정합성 보장 |
| 기본 깊이 | -1 (무제한) | 전체 의존성 파악이 일반적 |
| 기본 출력 | Tree | 직관적 계층 시각화 |
| 컬럼 레벨 | Phase 2+ | MVP 범위 제한, 테이블 레벨 우선 |
| 순환 처리 | visited set | 무한 루프 방지 |
| Error Code | DLI-9xx | Quality(DLI-6xx), Catalog(DLI-7xx), Workflow(DLI-8xx), Debug(DLI-95x)와 구분 |
