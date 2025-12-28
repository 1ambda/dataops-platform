# Day 3: Semantic Layer & Advanced Features

## 개요

2025년 업계 트렌드와 [dbt Semantic Layer](https://docs.getdbt.com/docs/build/about-metricflow), [SQLMesh](https://sqlmesh.readthedocs.io/en/stable/), [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) 표준을 기반으로 dli CLI에 추가할 기능들을 정의합니다.

### 참고 자료

- [dbt Semantic Layer Best Practices](https://docs.getdbt.com/best-practices/how-we-build-our-metrics/semantic-layer-1-intro)
- [MetricFlow Open Source (Apache 2.0)](https://www.getdbt.com/blog/open-source-metricflow-governed-metrics)
- [SQLMesh Model Overview](https://sqlmesh.readthedocs.io/en/stable/concepts/models/overview/)
- [Typer CLI Best Practices](https://typer.tiangolo.com/)

---

## 핵심 추가 기능

### 1. Semantic Layer 명령어 (`dli sl`)

[dbt Semantic Layer](https://docs.getdbt.com/docs/build/about-metricflow)의 MetricFlow 패턴을 참고한 semantic layer 명령어:

```bash
# Semantic Layer 쿼리
dli sl query --metrics revenue,orders --group-by date,region
dli sl query --metrics revenue_per_order --time-grain monthly

# 차원(Dimension) 탐색
dli sl list dimensions --metric revenue
dli sl list metrics --dimension region

# 메트릭 미리보기
dli sl preview iceberg.metrics.daily_revenue -p start_date=2024-01-01
```

**구현 항목:**

```
src/dli/commands/sl.py
├── query     # Semantic query execution
├── list      # List dimensions/metrics
├── preview   # Preview metric output
└── export    # Export to BI tools (Looker, Tableau)
```

**Metric Types 지원 (MetricFlow 기준):**

| Type | 설명 | 예시 |
|------|------|------|
| `simple` | 단일 measure 참조 | `SUM(revenue)` |
| `derived` | 다른 metrics 조합 | `revenue / orders` |
| `ratio` | 비율 계산 | `active_users / total_users` |
| `cumulative` | 누적 집계 | `SUM(revenue) OVER last 30 days` |
| `conversion` | 전환율 | `purchase / page_view` |

---

### 2. Data Lineage 시각화 (`dli lineage`)

[2025년 Data Lineage 트렌드](https://www.5x.co/blogs/data-lineage-tools)에 따라 column-level lineage 지원:

```bash
# 테이블/메트릭 lineage 조회
dli lineage show iceberg.analytics.daily_clicks
dli lineage show iceberg.analytics.daily_clicks --column click_count
dli lineage show iceberg.metrics.revenue --upstream --depth 3

# Lineage 그래프 출력
dli lineage graph --output mermaid
dli lineage graph --output dot > lineage.dot

# 영향도 분석
dli lineage impact iceberg.raw.user_events
```

**구현 항목:**

```
src/dli/core/lineage.py
├── LineageParser      # SQLGlot 기반 lineage 추출
├── LineageGraph       # DAG 구조 관리
├── LineageRenderer    # ASCII/Mermaid/DOT 출력
└── ImpactAnalyzer     # 하류 영향도 분석
```

---

### 3. 버전 관리 & 스키마 변경 (`dli version`)

[Atlan](https://atlan.com/know/semantic-layer/)의 버전 추적 패턴:

```bash
# 스키마 변경 이력 조회
dli version history iceberg.analytics.daily_clicks
dli version show iceberg.analytics.daily_clicks --version v2

# 버전 비교
dli version diff iceberg.analytics.daily_clicks v1 v2

# 마이그레이션 생성
dli version migrate iceberg.analytics.daily_clicks --from v1 --to v2
```

**Spec 확장:**

```yaml
# spec.iceberg.analytics.daily_clicks.yaml
versions:
  - version: "v1"
    started_at: "2024-01-01"
    ended_at: "2024-06-30"
    schema:
      - name: user_id
        type: bigint
      - name: click_count
        type: integer

  - version: "v2"
    started_at: "2024-07-01"
    changes:
      - type: ADD_COLUMN
        column: device_type
        dtype: varchar
      - type: RENAME_COLUMN
        old: click_count
        new: total_clicks
```

---

### 4. 데이터 카탈로그 통합 (`dli catalog`)

[Data Catalog 2025 가이드](https://www.decube.io/post/data-catalog-metadata-management-guide) 기반:

```bash
# 카탈로그 검색
dli catalog search "daily revenue"
dli catalog search --tag kpi --owner data-team

# 메타데이터 동기화
dli catalog sync --to datahub
dli catalog sync --to atlan --incremental

# 거버넌스 정책 확인
dli catalog policies iceberg.pii.user_data
```

**통합 대상:**

| Catalog | 연동 방식 | 우선순위 |
|---------|----------|---------|
| DataHub | REST API | High |
| Atlan | GraphQL | Medium |
| OpenMetadata | REST API | Medium |
| Unity Catalog | Spark Connect | Low |

---

### 5. AI/LLM 지원 기능 (`dli ai`)

[MetricFlow + AI 통합](https://www.getdbt.com/blog/open-source-metricflow-governed-metrics) (83% 정확도):

```bash
# 자연어 쿼리
dli ai query "지난 달 지역별 매출은?"
dli ai query "가장 많이 클릭한 상위 10개 아이템"

# 메트릭 설명 생성
dli ai describe iceberg.metrics.daily_revenue
dli ai suggest-metrics --table iceberg.raw.orders

# SQL 변환
dli ai translate "monthly active users by region" --dialect trino
```

**MCP Server 연동 (dbt Labs 패턴):**

```python
# src/dli/mcp/server.py
@mcp.tool()
def query_semantic_layer(natural_language_query: str) -> dict:
    """Natural language to SQL conversion via semantic layer."""
    ...

@mcp.tool()
def get_metric_details(metric_name: str) -> dict:
    """Get metric metadata and lineage."""
    ...
```

---

### 6. 환경 관리 & 프로모션 (`dli env`)

[SQLMesh Plan](https://sqlmesh.readthedocs.io/en/stable/concepts/plans/) 패턴:

```bash
# 환경 관리
dli env list
dli env create staging --from production
dli env diff staging production

# 변경사항 프로모션
dli env plan staging  # 변경사항 미리보기
dli env apply staging --to production
dli env rollback production --to-version v1.2.3
```

**환경 구성:**

```yaml
# dli.yaml
environments:
  development:
    catalog: dev_iceberg
    schema_suffix: _dev

  staging:
    catalog: staging_iceberg
    requires_approval: true

  production:
    catalog: iceberg
    protected: true
    requires_approval: true
```

---

## 구현 우선순위

| 기능 | 복잡도 | 가치 | 우선순위 | 예상 일정 |
|------|--------|------|----------|----------|
| Semantic Layer Query | Medium | High | P1 | Day 3 |
| Data Lineage | High | High | P1 | Day 3-4 |
| Version Management | Medium | Medium | P2 | Day 4 |
| Environment Management | Low | Medium | P2 | Day 4 |
| Catalog Integration | High | Medium | P3 | Day 5 |
| AI/LLM Support | High | High | P3 | Future |

---

## Day 3 구현 목표

### 목표: Semantic Layer CLI 기본 기능

```
project-interface-cli/
├── src/dli/
│   ├── commands/
│   │   └── sl.py                 # NEW: Semantic layer commands
│   ├── core/
│   │   ├── semantic/
│   │   │   ├── __init__.py
│   │   │   ├── models.py         # SemanticModel, Dimension, Measure
│   │   │   ├── query_builder.py  # MetricFlow-style query builder
│   │   │   └── executor.py       # Query execution
│   │   └── lineage/
│   │       ├── __init__.py
│   │       ├── parser.py         # SQLGlot-based lineage extraction
│   │       └── graph.py          # DAG management
│   └── mcp/                       # MCP server for AI integration
│       └── server.py
└── tests/
    └── core/
        ├── test_semantic.py
        └── test_lineage.py
```

### 명령어 구조

```bash
# Phase 1: Semantic Layer (Day 3)
dli sl list dimensions --metric <name>
dli sl list metrics
dli sl query --metrics m1,m2 --group-by d1,d2

# Phase 2: Lineage (Day 3-4)
dli lineage show <resource>
dli lineage graph --format mermaid

# Phase 3: Versioning (Day 4)
dli version history <resource>
dli version diff <resource> v1 v2
```

---

## Spec 스키마 확장

### Semantic Model Definition

```yaml
# semantic_models/sales.yaml
name: sales
description: "Sales semantic model"
model: iceberg.fact.orders

entities:
  - name: order
    type: primary
    expr: order_id

dimensions:
  - name: order_date
    type: time
    expr: created_at
    time_granularity: day

  - name: region
    type: categorical
    expr: region_code

measures:
  - name: revenue
    expr: amount
    agg: sum

  - name: order_count
    expr: order_id
    agg: count_distinct
```

### Metric Definition (MetricFlow Style)

```yaml
# metrics/revenue_per_order.yaml
name: revenue_per_order
type: derived
description: "Average revenue per order"

metrics:
  - name: revenue_per_order
    type: ratio
    numerator: revenue
    denominator: order_count
    filter: |
      {{ Dimension('order_date') }} >= '2024-01-01'
```

---

## 참고: 업계 표준 비교

| 기능 | dbt Semantic Layer | SQLMesh | Cube | dli (목표) |
|------|-------------------|---------|------|-----------|
| Semantic Models | ✅ | ✅ | ✅ | ✅ |
| Multi-hop Joins | ✅ | ✅ | ✅ | 🔜 |
| Time Granularity | ✅ | ✅ | ✅ | ✅ |
| Column Lineage | ✅ | ✅ | ❌ | ✅ |
| Version Control | Git | Git | ❌ | Git + Schema |
| AI Integration | MCP | ❌ | ❌ | MCP |
| Environment Mgmt | dbt Cloud | ✅ | ❌ | ✅ |

---

## 기술 스택 추가

```toml
# pyproject.toml 추가 의존성
[project.optional-dependencies]
semantic = [
    "networkx>=3.0",      # Lineage graph
    "mcp>=1.0",           # MCP server
]

catalog = [
    "datahub-client>=0.12",
    "openmetadata-client>=1.0",
]
```

---

## 완료 기준

- [ ] `dli sl list` 명령어 구현
- [ ] `dli sl query` 명령어 구현 (기본)
- [ ] Semantic model YAML 파싱
- [ ] `dli lineage show` 명령어 구현
- [ ] SQLGlot 기반 lineage 추출
- [ ] 단위 테스트 80% 이상 커버리지
- [ ] README 문서 업데이트
