# Day 1: Core Engine 구현 가이드

## 개요

Day 1에서는 DLI_HOME 기반의 Dataset Spec 시스템과 Core Engine을 구현합니다.

### 구현 상태

| 항목 | 상태 |
|------|------|
| 완료일 | 2025-12-29 |
| 테스트 | 393 tests passed (core + CLI + library) |
| 코드 품질 | pyright 0 errors, ruff 0 errors |

### 주요 변경사항 (v1 → v2 → v3)

| 항목 | v1 (기존) | v2 | v3 (신규) |
|------|----------|----------|----------|
| Spec 타입 | 단일 | 단일 DatasetSpec | **MetricSpec + DatasetSpec 분리** |
| 파일 명명 | `_schema.yml` | `spec.*.yaml` | `metric.*.yaml` + `dataset.*.yaml` |
| 디렉토리 | `queries/` 고정 | `$DLI_HOME/datasets/` | `metrics/` + `datasets/` 분리 |
| 쿼리 타입 | SELECT 중심 | SELECT / DML | **Metric=SELECT, Dataset=DML 강제** |
| 실행 단계 | Main만 | Pre → Main → Post | Pre/Post는 Dataset 전용 |
| 메트릭 정의 | 없음 | DatasetSpec 내 | **MetricSpec 전용** |

---

## Metric/Dataset 분리 (v3)

v3에서는 Metric과 Dataset을 명확히 분리하여 관리합니다.

### 핵심 원칙

| 구분 | MetricSpec | DatasetSpec |
|------|------------|-------------|
| **type 필드** | `Metric` | `Dataset` |
| **query_type** | `SELECT` (강제) | `DML` (강제) |
| **용도** | 읽기 전용 분석 쿼리 | 데이터 처리 (INSERT/UPDATE/DELETE/MERGE) |
| **메트릭/디멘션** | 지원 | 미지원 |
| **Pre/Post 문** | 미지원 | 지원 |
| **파일 패턴** | `metric.*.yaml` | `dataset.*.yaml` |
| **디렉토리** | `metrics/` | `datasets/` |

### 파일 명명 규칙

```
# MetricSpec
metric.{catalog}.{schema}.{name}.yaml
예: metric.iceberg.analytics.user_engagement.yaml

# DatasetSpec
dataset.{catalog}.{schema}.{name}.yaml
예: dataset.iceberg.analytics.daily_clicks.yaml
```

### 디렉토리 구조

```
$DLI_HOME/
├── dli.yaml
├── metrics/                              # 메트릭 전용
│   ├── analytics/
│   │   ├── metric.iceberg.analytics.user_engagement.yaml
│   │   └── user_engagement.sql
│   └── revenue/
│       └── metric.iceberg.analytics.revenue_summary.yaml
├── datasets/                             # 데이터셋 전용
│   ├── feed/
│   │   ├── dataset.iceberg.analytics.daily_clicks.yaml
│   │   └── daily_clicks.sql
│   └── reporting/
│       └── dataset.iceberg.reporting.daily_summary.yaml
```

### dli.yaml 설정

```yaml
discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"

  metric_patterns:
    - "metric.*.yaml"
    - "metric.yaml"
  dataset_patterns:
    - "dataset.*.yaml"
    - "dataset.yaml"
```

### 모델 클래스

```python
from dli.core import (
    SpecType,      # Metric | Dataset
    SpecBase,      # 공통 기반 클래스
    MetricSpec,    # type=Metric, query_type=SELECT
    DatasetSpec,   # type=Dataset, query_type=DML
    Spec,          # MetricSpec | DatasetSpec (Union)
)

# MetricSpec 생성 (SELECT 전용)
metric = MetricSpec(
    name="iceberg.analytics.user_engagement",
    owner="analyst@example.com",
    team="@analytics",
    query_statement="SELECT * FROM user_events",
    metrics=[...],
    dimensions=[...],
)

# DatasetSpec 생성 (DML 전용)
dataset = DatasetSpec(
    name="iceberg.analytics.daily_clicks",
    owner="engineer@example.com",
    team="@data-engineering",
    query_file="daily_clicks.sql",
    pre_statements=[...],
    post_statements=[...],
)
```

### Discovery API

```python
from dli.core import SpecDiscovery, DatasetDiscovery

# 통합 Discovery (신규)
spec_discovery = SpecDiscovery(config)
for spec in spec_discovery.discover_all():      # MetricSpec | DatasetSpec
    print(f"{spec.type}: {spec.name}")

for metric in spec_discovery.discover_metrics(): # MetricSpec만
    print(f"Metric: {metric.name}")

for dataset in spec_discovery.discover_datasets(): # DatasetSpec만
    print(f"Dataset: {dataset.name}")

# DatasetDiscovery (DatasetSpec 전용)
dataset_discovery = DatasetDiscovery(config)
for dataset in dataset_discovery.discover_all():
    print(f"Dataset: {dataset.name}")
```

### 유효성 검사

```python
# type과 query_type 불일치 시 에러
MetricSpec(
    name="test",
    owner="test@test.com",
    team="@test",
    query_type="DML",  # 에러! Metric은 SELECT만 가능
    query_statement="INSERT INTO t SELECT 1",
)
# ValueError: Metric 'test' must have query_type='SELECT'

DatasetSpec(
    name="test",
    owner="test@test.com",
    team="@test",
    query_type="SELECT",  # 에러! Dataset은 DML만 가능
    query_statement="SELECT 1",
)
# ValueError: Dataset 'test' must have query_type='DML'
```

---

## 업계 표준 참고

| 표준/도구 | 참고 포인트 | 적용 |
|-----------|------------|------|
| [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) | 벤더 중립 YAML 표준 | Spec 파일 구조 |
| [dbt MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow) | semantic_models + metrics | 메타데이터 필드 |
| [Databricks Unity Catalog](https://docs.databricks.com/aws/en/metric-views/) | `catalog.schema.table` 네임스페이스 | 3레벨 식별자 |
| [SQLMesh](https://sqlmesh.readthedocs.io/en/stable/concepts/models/overview/) | MODEL DDL + external YAML | 하이브리드 방식 |

---

## 프로젝트 구조

```
project-interface-cli/
├── pyproject.toml
├── src/dli/
│   ├── __init__.py
│   ├── __main__.py          # python -m dli 지원
│   ├── main.py              # Typer CLI 엔트리포인트
│   ├── core/
│   │   ├── __init__.py
│   │   ├── models/          # 모델 패키지 (분리됨)
│   │   │   ├── __init__.py  # 하위 호환 re-exports
│   │   │   ├── base.py      # 기본 Enums, QueryParameter, StatementDefinition
│   │   │   ├── spec.py      # SpecBase 추상 클래스
│   │   │   ├── metric.py    # MetricSpec, MetricDefinition, DimensionDefinition
│   │   │   ├── dataset.py   # DatasetSpec
│   │   │   └── results.py   # ValidationResult, ExecutionResult
│   │   ├── discovery.py     # DLI_HOME 탐색, 파일 로드
│   │   ├── registry.py      # Dataset 레지스트리
│   │   ├── renderer.py      # Jinja2 렌더러
│   │   ├── templates.py     # Safe 템플릿 컨텍스트 (dbt/SQLMesh 호환)
│   │   ├── validator.py     # SQLGlot 검증
│   │   ├── executor.py      # 실행 추상화
│   │   └── service.py       # 통합 서비스
│   └── adapters/
│       ├── __init__.py
│       └── bigquery.py
└── tests/
    ├── cli/                 # CLI 테스트 (신규)
    │   ├── __init__.py
    │   └── test_main.py     # 32 CLI 커맨드 테스트
    ├── core/
    │   ├── test_models.py
    │   ├── test_discovery.py
    │   ├── test_registry.py
    │   ├── test_renderer.py
    │   ├── test_templates.py
    │   ├── test_validator.py
    │   ├── test_executor.py
    │   └── test_service.py
    └── fixtures/
        └── sample_project/
```

---

## DLI_HOME 디렉토리 구조

### 유연한 구조 지원

```
$DLI_HOME/
├── dli.yaml                          # 프로젝트 설정 (필수)
├── datasets/                         # 데이터셋 루트
│   │
│   │  # 옵션 A: Flat 구조 (소규모)
│   ├── dataset.iceberg.analytics.daily_clicks.yaml
│   ├── daily_clicks.sql
│   │
│   │  # 옵션 B: Domain 기반 구조 (권장)
│   ├── feed/
│   │   ├── dataset.iceberg.analytics.daily_clicks.yaml
│   │   ├── daily_clicks.sql
│   │   ├── daily_clicks_pre.sql
│   │   └── daily_clicks_post.sql
│   │
│   │  # 옵션 C: Catalog/Schema 계층 (대규모)
│   └── iceberg/
│       └── analytics/
│           └── daily_clicks/
│               ├── dataset.yaml
│               ├── main.sql
│               ├── pre.sql
│               └── post.sql
│
└── templates/                        # 공통 Jinja2 매크로
    └── macros.sql
```

### dli.yaml (프로젝트 설정)

```yaml
version: "1"

project:
  name: "dataops-metrics"
  description: "Data Analytics Metrics Project"

discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns:
    - "dataset.*.yaml"
    - "dataset.yaml"
  metric_patterns:
    - "metric.*.yaml"
    - "metric.yaml"
  sql_patterns:
    - "*.sql"

defaults:
  dialect: "trino"
  timeout_seconds: 3600
  retry_count: 2

environments:
  dev:
    connection_string: "trino://localhost:8080/iceberg"
  prod:
    connection_string: "trino://trino-prod.internal:8080/iceberg"
```

---

## Spec 파일 스키마

```yaml
# dataset.{catalog}.{schema}.{table}.yaml 또는 metric.{catalog}.{schema}.{table}.yaml

# ─────────────────────────────────────────────
# 1. 기본 식별자 (Required)
# ─────────────────────────────────────────────
name: "iceberg.analytics.daily_clicks"    # catalog.schema.table
description: "1인당 item 평균 클릭수"

# ─────────────────────────────────────────────
# 2. 소유권 및 도메인 (Required)
# ─────────────────────────────────────────────
owner: "henrykim@example.com"
team: "@data-analytics"
domains:
  - "feed"
  - "engagement"
tags:
  - "daily"
  - "kpi"

# ─────────────────────────────────────────────
# 3. 버전 관리 (Optional)
# ─────────────────────────────────────────────
versions:
  - version: "v1"
    started_at: "2015-12-01"
    ended_at: "2022-05-31"
    description: "초기 버전"
  - version: "v2"
    started_at: "2022-06-01"
    ended_at: null              # null = 현재 활성
    description: "Window Function 적용"

# ─────────────────────────────────────────────
# 4. 쿼리 타입 (Required)
# ─────────────────────────────────────────────
query_type: "DML"               # SELECT | DML

# ─────────────────────────────────────────────
# 5. 파라미터 (Optional)
# ─────────────────────────────────────────────
parameters:
  - name: "execution_date"
    type: "date"
    required: true
    description: "실행 기준 날짜"
  - name: "lookback_days"
    type: "integer"
    required: false
    default: 7

# ─────────────────────────────────────────────
# 6. Pre Statements (Optional)
# ─────────────────────────────────────────────
pre_statements:
  - name: "delete_partition"
    sql: |
      DELETE FROM iceberg.analytics.daily_clicks
      WHERE dt = '{{ execution_date }}'
  - name: "analyze_source"
    file: "analyze_source.sql"
    continue_on_error: true

# ─────────────────────────────────────────────
# 7. Main Query (Required: 택일)
# ─────────────────────────────────────────────
# 방식 A: 인라인 SQL
query_statement: |
  INSERT INTO iceberg.analytics.daily_clicks
  SELECT ...

# 방식 B: 파일 참조 (IDE 자동완성 지원)
query_file: "daily_clicks.sql"

# ─────────────────────────────────────────────
# 8. Post Statements (Optional)
# ─────────────────────────────────────────────
post_statements:
  - name: "optimize"
    file: "optimize.sql"
  - name: "expire_snapshots"
    file: "expire_snapshots.sql"
    continue_on_error: true

# ─────────────────────────────────────────────
# 9. 실행 설정 (Optional)
# ─────────────────────────────────────────────
execution:
  timeout_seconds: 3600
  retry_count: 2
  retry_delay_seconds: 60
  dialect: "trino"

# ─────────────────────────────────────────────
# 10. 의존성 (Optional)
# ─────────────────────────────────────────────
depends_on:
  - "iceberg.raw.user_events"
  - "iceberg.dim.users"

# ─────────────────────────────────────────────
# 11. 출력 스키마 (Optional, SELECT 권장)
# ─────────────────────────────────────────────
schema:
  - name: "dt"
    type: "date"
  - name: "user_id"
    type: "string"
  - name: "click_count"
    type: "integer"
```

---

## 구현된 모듈

### 1. models.py

Pydantic 기반 데이터 모델:

- `QueryType`: SELECT | DML enum
- `ParameterType`: string, integer, float, date, boolean, list
- `QueryParameter`: 파라미터 정의 및 타입 변환
- `StatementDefinition`: Pre/Post SQL 정의
- `DatasetVersion`: 버전 정보
- `ExecutionConfig`: 실행 설정
- `DatasetSpec`: Spec 파일 전체 구조
- `ValidationResult`: SQL 검증 결과
- `ExecutionResult`: 단일 SQL 실행 결과
- `DatasetExecutionResult`: 3단계 실행 전체 결과

```python
from dli.core import DatasetSpec, QueryType, QueryParameter, ParameterType

spec = DatasetSpec(
    name="iceberg.analytics.daily_clicks",
    owner="henry@example.com",
    team="@analytics",
    domains=["feed"],
    tags=["daily", "kpi"],
    query_type=QueryType.DML,
    parameters=[
        QueryParameter(name="execution_date", type=ParameterType.DATE, required=True),
        QueryParameter(name="lookback_days", type=ParameterType.INTEGER, default=7),
    ],
    query_statement="INSERT INTO t SELECT * FROM s WHERE dt = '{{ execution_date }}'",
)
```

### 2. discovery.py

DLI_HOME 탐색 및 프로젝트 설정 로드:

- `ProjectConfig`: dli.yaml 파싱
- `DatasetDiscovery`: Spec 파일 탐색 및 로드
- `get_dli_home()`: DLI_HOME 환경 변수 또는 현재 디렉토리
- `load_project()`: 프로젝트 설정 로드

```python
from dli.core import load_project, DatasetDiscovery

config = load_project(Path("/path/to/dli_home"))
discovery = DatasetDiscovery(config)

for spec in discovery.discover_all():
    print(f"{spec.name}: {spec.description}")
```

### 3. registry.py

Dataset 캐싱 및 다차원 검색:

```python
from dli.core import DatasetRegistry

registry = DatasetRegistry(config)

# 검색
datasets = registry.search(domain="feed", tag="kpi")
dataset = registry.get("iceberg.analytics.daily_clicks")

# 메타데이터 조회
catalogs = registry.get_catalogs()
domains = registry.get_domains()
```

### 4. renderer.py

Jinja2 기반 SQL 렌더러:

```python
from dli.core import SQLRenderer

renderer = SQLRenderer()
sql = renderer.render(
    "SELECT * FROM t WHERE dt = '{{ execution_date }}'",
    parameters=[QueryParameter(name="execution_date", type=ParameterType.DATE)],
    values={"execution_date": "2025-01-01"},
)
```

**커스텀 필터**:
- `sql_string`: SQL 문자열 이스케이프 (`'value'`)
- `sql_list`: 리스트를 SQL IN 절로 (`(1, 2, 3)`)
- `sql_date`: 날짜 포맷 (`DATE '2025-01-01'`)
- `sql_identifier`: 식별자 이스케이프 (`"table_name"`)

### 5. validator.py

SQLGlot 기반 SQL 검증:

```python
from dli.core import SQLValidator

validator = SQLValidator(dialect="trino")
result = validator.validate("SELECT * FROM users")

if result.is_valid:
    tables = validator.extract_tables("SELECT * FROM users JOIN orders")
    formatted = validator.format_sql("select * from users")
```

### 6. executor.py

3단계 실행 엔진 (Pre → Main → Post):

- `BaseExecutor`: 추상 실행 인터페이스
- `MockExecutor`: 테스트용 Mock 실행기
- `DatasetExecutor`: 3단계 실행 오케스트레이터

```python
from dli.core import MockExecutor, DatasetExecutor

executor = MockExecutor()
dataset_executor = DatasetExecutor(executor)

result = dataset_executor.execute(
    spec,
    rendered_sqls={
        "pre": ["DELETE FROM t WHERE dt = '2025-01-01'"],
        "main": "INSERT INTO t SELECT ...",
        "post": ["OPTIMIZE TABLE t"],
    },
)

print(f"Success: {result.success}")
print(f"Pre results: {len(result.pre_results)}")
print(f"Main result: {result.main_result}")
print(f"Post results: {len(result.post_results)}")
```

### 7. service.py

통합 서비스 레이어:

```python
from dli.core import DatasetService, MockExecutor

service = DatasetService(
    project_path=Path("/path/to/dli_home"),
    executor=MockExecutor(),
)

# 데이터셋 목록
datasets = service.list_datasets(domain="feed")

# 검증
results = service.validate("iceberg.analytics.daily_clicks", {"execution_date": "2025-01-01"})

# 실행
result = service.execute(
    "iceberg.analytics.daily_clicks",
    {"execution_date": "2025-01-01"},
    dry_run=False,
)
```

### 8. metric_service.py

메트릭 실행 서비스 (SELECT 쿼리 전용):

```python
from dli.core import MetricService, MockExecutor

service = MetricService(
    project_path=Path("/path/to/dli_home"),
    executor=MockExecutor(),
)

# 메트릭 목록
metrics = service.list_metrics(domain="reporting")

# 검증
results = service.validate("iceberg.reporting.user_summary", {"date": "2025-01-01"})

# 실행 (SELECT 결과 반환)
result = service.execute(
    "iceberg.reporting.user_summary",
    {"date": "2025-01-01"},
)

if result.success:
    print(f"Rows: {result.rows}")
    print(f"Columns: {result.columns}")
    print(f"Row count: {result.row_count}")
```

---

## Library API (Airflow 연동)

dli는 CLI 뿐만 아니라 **Python 라이브러리**로도 사용 가능합니다.
Airflow의 PythonOperator에서 직접 호출하여 사용할 수 있습니다.

### 설치

```bash
# 기본 설치
pip install dataops-cli

# Airflow 환경에서 사용 시
pip install dataops-cli[airflow]

# BigQuery 실행 시
pip install dataops-cli[bigquery]
```

### Airflow DAG 예시

```python
from datetime import datetime
from airflow import DAG
from airflow.operators.python import PythonOperator

from dli.core import DatasetService, MetricService

def run_dataset(ds: str, **context):
    """Dataset 실행 (INSERT/UPDATE/DELETE/MERGE)."""
    service = DatasetService(project_path="/opt/airflow/dli_home")
    result = service.execute(
        "iceberg.analytics.daily_clicks",
        {"execution_date": ds},
    )
    if not result.success:
        raise Exception(result.error_message)
    return {"success": True, "main_rows": result.main_result.row_count}

def run_metric(ds: str, **context):
    """Metric 실행 (SELECT)."""
    service = MetricService(project_path="/opt/airflow/dli_home")
    result = service.execute(
        "iceberg.reporting.user_summary",
        {"date": ds},
    )
    if not result.success:
        raise Exception(result.error_message)
    # XCom으로 결과 전달
    return {"rows": result.rows, "row_count": result.row_count}

with DAG(
    dag_id="dli_example",
    start_date=datetime(2025, 1, 1),
    schedule="@daily",
) as dag:

    dataset_task = PythonOperator(
        task_id="run_daily_clicks",
        python_callable=run_dataset,
    )

    metric_task = PythonOperator(
        task_id="run_user_summary",
        python_callable=run_metric,
    )

    dataset_task >> metric_task
```

### 주요 API

| 클래스 | 용도 | 쿼리 타입 |
|--------|------|----------|
| `DatasetService` | Dataset 실행 (데이터 처리) | DML (INSERT/UPDATE/DELETE/MERGE) |
| `MetricService` | Metric 실행 (분석 쿼리) | SELECT |

### DatasetService 메서드

| 메서드 | 설명 |
|--------|------|
| `list_datasets()` | 데이터셋 목록 (필터링 지원) |
| `get_dataset(name)` | 데이터셋 조회 |
| `validate(name, params)` | SQL 검증 (Pre, Main, Post) |
| `render_sql(name, params)` | SQL 렌더링 |
| `execute(name, params)` | 실행 (skip_pre, skip_post, dry_run 옵션) |

### MetricService 메서드

| 메서드 | 설명 |
|--------|------|
| `list_metrics()` | 메트릭 목록 (필터링 지원) |
| `get_metric(name)` | 메트릭 조회 |
| `validate(name, params)` | SQL 검증 |
| `render_sql(name, params)` | SQL 렌더링 |
| `execute(name, params)` | 실행 (결과 행 반환) |

### 실행 결과 모델

```python
# Dataset 실행 결과
class DatasetExecutionResult:
    dataset_name: str
    success: bool
    pre_results: list[ExecutionResult]
    main_result: ExecutionResult | None
    post_results: list[ExecutionResult]
    error_message: str | None

# Metric 실행 결과
class MetricExecutionResult:
    metric_name: str
    success: bool
    rows: list[dict[str, Any]]      # SELECT 결과
    row_count: int
    columns: list[str]
    error_message: str | None
    execution_time_ms: float | None
```

---

## 테스트 Fixtures

### 샘플 프로젝트 (`tests/fixtures/sample_project/`)

```
sample_project/
├── dli.yaml                                              # 프로젝트 설정
├── metrics/
│   └── reporting/
│       ├── metric.iceberg.reporting.user_summary.yaml    # Metric Spec
│       └── user_summary.sql                              # SQL 파일
├── datasets/
│   └── feed/
│       ├── dataset.iceberg.analytics.daily_clicks.yaml   # Dataset Spec
│       └── daily_clicks.sql                              # SQL 파일
```

### 테스트 현황

| 파일 | 테스트 수 | 설명 |
|------|----------|------|
| `cli/test_main.py` | 32 | CLI 커맨드 테스트 (version, validate, render, info) |
| `cli/test_list_cmd.py` | 28 | List 커맨드 테스트 (필터, 포맷, 에러) |
| `test_models.py` | 69 | 데이터 모델 + SpecType/MetricSpec/DatasetSpec + Name Validation |
| `test_discovery.py` | 30 | ProjectConfig + SpecDiscovery + DatasetDiscovery |
| `test_registry.py` | 30 | 레지스트리 테스트 (DatasetSpec + MetricSpec) |
| `test_renderer.py` | 19 | SQL 렌더링 테스트 |
| `test_templates.py` | 71 | Safe 템플릿 컨텍스트 테스트 |
| `test_validator.py` | 26 | SQL 검증 테스트 |
| `test_executor.py` | 20 | 실행 엔진 테스트 |
| `test_service.py` | 31 | DatasetService 통합 테스트 |
| `test_metric_service.py` | 37 | MetricService 통합 테스트 (Airflow 연동) |
| **합계** | **393** | core + CLI + library 테스트 |

---

## Safe Templating (dbt/SQLMesh 호환)

임의의 Python 코드 실행을 방지하는 안전한 템플릿 시스템입니다.

### 지원 변수 (Phase 1)

| 변수 | 설명 | 예시 |
|------|------|------|
| `ds` | 실행 날짜 (YYYY-MM-DD) | `2025-01-15` |
| `ds_nodash` | 실행 날짜 (YYYYMMDD) | `20250115` |
| `execution_date` | `ds` alias | `2025-01-15` |
| `yesterday_ds` | 어제 날짜 | `2025-01-14` |
| `tomorrow_ds` | 내일 날짜 | `2025-01-16` |

### 지원 함수 (Phase 1)

| 함수 | 설명 | 예시 |
|------|------|------|
| `var(name, default)` | 프로젝트 변수 조회 | `{{ var('env', 'dev') }}` |
| `date_add(date, days)` | 날짜 더하기 | `{{ date_add(ds, 7) }}` |
| `date_sub(date, days)` | 날짜 빼기 | `{{ date_sub(ds, 7) }}` |
| `ref(dataset)` | 데이터셋 참조 | `{{ ref('users') }}` |
| `env_var(name, default)` | 환경변수 조회 | `{{ env_var('DB_HOST') }}` |

### 사용 예시

```sql
-- SQL 템플릿
SELECT *
FROM {{ ref('raw_events') }}
WHERE dt BETWEEN '{{ date_sub(ds, 7) }}' AND '{{ ds }}'
  AND country = '{{ var("target_country", "KR") }}'
```

---

## Metric 정의 (dbt MetricFlow 호환)

SELECT 타입 쿼리에 대한 메트릭 정의를 지원합니다.

### 지원 집계 타입 (Phase 1)

| 타입 | SQL | 설명 |
|------|-----|------|
| `count` | `COUNT(*)` | 행 수 |
| `count_distinct` | `COUNT(DISTINCT col)` | 고유 값 수 |
| `sum` | `SUM(col)` | 합계 |
| `avg` | `AVG(col)` | 평균 |
| `min` | `MIN(col)` | 최소값 |
| `max` | `MAX(col)` | 최대값 |

### 지원 Dimension 타입 (Phase 1)

| 타입 | 설명 |
|------|------|
| `categorical` | 범주형 (country, status 등) |
| `time` | 시간형 (dt, created_at 등) |

### Spec 예시

```yaml
# spec.iceberg.reporting.user_summary.yaml
name: "iceberg.reporting.user_summary"
query_type: "SELECT"

metrics:
  - name: "user_count"
    aggregation: "count_distinct"
    expression: "user_id"
    description: "사용자 수"
  - name: "total_clicks"
    aggregation: "sum"
    expression: "click_count"
  - name: "avg_session"
    aggregation: "avg"
    expression: "session_duration_ms"
    filters:
      - "session_duration_ms > 0"

dimensions:
  - name: "country"
    type: "categorical"
    expression: "country_code"
  - name: "dt"
    type: "time"
    expression: "dt"
```

### Python 사용 예시

```python
from dli.core import MetricDefinition, AggregationType

metric = MetricDefinition(
    name="user_count",
    aggregation=AggregationType.COUNT_DISTINCT,
    expression="user_id",
)
print(metric.to_sql())  # COUNT(DISTINCT user_id)
```

---

## 코드 품질 개선사항

expert-python Agent 리뷰 후 적용:

### 1. Type Alias 수정 (`models.py`)
- `Spec = SpecBase` → `Spec = MetricSpec | DatasetSpec` (Union 타입)
- Type Union으로 실제 구현 타입 반영

### 2. Name Validation 강화 (`models.py`)
- 선행/후행 점(`.`) 검사 추가
- 연속 점(`..`) 검사 추가
- 공백 전용 파트 검사 추가
- 파트 앞/뒤 공백 검사 추가

### 3. DRY 원칙 적용 (`discovery.py`)
- `_load_yaml_file()`: 공통 YAML 로딩
- `_load_spec()`: 통합 spec 로딩
- `_detect_spec_type()`: 타입 감지
- `_set_type_defaults()`: 기본값 설정
- `_discover_specs_in_dir()`: 범용 디렉토리 탐색
- Magic string 제거 → Enum 값 사용

### 4. 중복 제거 및 타입 안전성
- `seen_paths: set[Path]`로 중복 처리
- `typing.cast()` 사용으로 pyright 타입 체크 통과
- 불필요한 import 정리

### 5. Pydantic 패턴 개선
- `PrivateAttr` 사용으로 private 필드 정의 개선
- Public `base_dir`, `spec_path` 프로퍼티 추가
- `set_paths()` 메서드로 깔끔한 초기화

### 6. 예외 처리 개선
- 광범위한 `Exception` 대신 구체적 예외 타입 사용
- `(OSError, ValueError, yaml.YAMLError, ValidationError)` 등

### 7. Python 패턴 매칭 활용
```python
match parsed:
    case exp.Select():
        return "SELECT"
    case exp.Insert():
        return "INSERT"
```

---

## CLI 커맨드 (Typer 기반)

### 지원 커맨드

```bash
# 버전 정보
dli version                    # 버전 상세 표시
dli --version / -v             # 버전 플래그

# SQL 검증
dli validate <path>            # SQL 파일 검증
dli validate query.sql --dialect trino --strict

# 스펙 목록
dli list                       # 모든 spec 표시
dli list --type metric         # Metric만 표시
dli list --format json         # JSON 출력

# 템플릿 렌더링
dli render template.sql --param key=value --date 2025-01-01 --output out.sql

# 환경 정보
dli info                       # 플랫폼, 의존성 정보
```

### CLI 아키텍처

```python
# src/dli/main.py - Typer 앱
from typer import Typer, Option, Argument
from rich.console import Console

app = Typer(name="dli", help="DataOps Interface CLI")

@app.command()
def version(): ...

@app.command()
def validate(path: Path, dialect: str = "trino", strict: bool = False): ...

@app.command()
def render(path: Path, param: list[str], date: str, output: Path): ...

# src/dli/__main__.py - python -m dli 지원
from dli.main import app
app()
```

---

## Models 패키지 구조

기존 `models.py` (750 lines)를 역할별로 분리:

| 파일 | 라인 수 | 내용 |
|------|--------|------|
| `models/__init__.py` | 81 | 하위 호환 re-exports (`from dli.core.models import *` 지원) |
| `models/base.py` | 185 | `QueryType`, `SpecType`, `ParameterType`, `QueryParameter`, `StatementDefinition` |
| `models/spec.py` | 241 | `SpecBase` 추상 기반 클래스 |
| `models/metric.py` | 201 | `AggregationType`, `MetricDefinition`, `DimensionDefinition`, `MetricSpec` |
| `models/dataset.py` | 73 | `DatasetSpec` (DML 전용) |
| `models/results.py` | 86 | `ValidationResult`, `ExecutionResult`, `DatasetExecutionResult` |

### 하위 호환성

```python
# 기존 import 그대로 사용 가능
from dli.core.models import DatasetSpec, MetricSpec, SpecType
from dli.core import QueryType, ValidationResult
```

---

## 프로젝트 구조 평가 (Senior Engineer Analysis)

### 현재 상태 평가

| 지표 | 값 | 평가 |
|------|---|------|
| 소스 코드 | 4,551 lines / 27 files | **적정** |
| 테스트 코드 | 3,500+ lines / 11 files | **양호** |
| 평균 파일 크기 | 169 lines | **건강함** |
| 가장 큰 파일 | templates.py (505 lines) | **허용 범위** |
| 테스트 커버리지 | 86% | **양호** |

### 업계 기준 비교

| CLI 도구 | Lines | Files | Avg Size |
|----------|-------|-------|----------|
| httpie | ~15,000 | 40+ | 375 |
| typer | ~8,000 | 30 | 267 |
| **dli (현재)** | **4,551** | **27** | **169** |

### 판단: ✅ 현재 구조 적절함

**추가 리팩토링 불필요** - 다음 조건을 모두 충족:

1. **파일 크기가 건강한 범위** (최대 505 lines < 700 lines 기준)
2. **모듈 응집도 높음** (관련 클래스가 함께 위치)
3. **관심사 분리 완료** (commands/, core/, adapters/)
4. **하위 호환성 유지** (models/__init__.py re-exports)

### 보안 개선사항 (적용 완료)

| 이슈 | 심각도 | 상태 |
|------|-------|------|
| SQL date filter injection | Critical | ✅ 수정됨 |
| Type hints for dict returns | High | 🔄 개선 권장 |
| Error context in file loading | Medium | 🔄 개선 권장 |

---

## Day 1 체크리스트

- [x] models.py → **models/ 패키지 분리**
- [x] discovery.py (ProjectConfig, SpecDiscovery, DatasetDiscovery)
- [x] registry.py (DatasetRegistry)
- [x] renderer.py (SQLRenderer)
- [x] templates.py (TemplateContext, SafeJinjaEnvironment)
- [x] validator.py (SQLValidator)
- [x] executor.py (BaseExecutor, MockExecutor, DatasetExecutor)
- [x] service.py (DatasetService)
- [x] **metric_service.py (MetricService) - Airflow 연동용**
- [x] **main.py (Typer CLI 엔트리포인트)**
- [x] **__main__.py (python -m dli 지원)**
- [x] 샘플 파일 (dli.yaml, dataset.yaml, metric.yaml, .sql)
- [x] Safe Templating (dbt/SQLMesh 호환 변수 및 함수)
- [x] Metric 정의 (MetricDefinition, DimensionDefinition)
- [x] **Metric/Dataset 분리 (SpecType, MetricSpec, DatasetSpec)**
- [x] **metrics_dir 설정 및 metric/dataset 파일 패턴**
- [x] **metrics/ 및 datasets/ 샘플 파일**
- [x] **CLI 커맨드 테스트 (60 tests)**
- [x] **Library API 테스트 (37 tests)**
- [x] **TypedDict 타입 힌트 추가**
- [x] 전체 테스트 (393 tests passed)
- [x] 코드 리뷰 및 리팩토링

---

## 테스트 실행

```bash
# 전체 테스트
cd project-interface-cli
uv run pytest tests/core/ -v

# 커버리지
uv run pytest tests/core/ --cov=dli.core --cov-report=term-missing

# 타입 체크
uv run pyright src/dli/core/

# 린팅
uv run ruff check src/dli/core/
```

---

## 다음 단계 (Day 2: CLI)

Day 2에서는 Typer 기반 CLI 인터페이스를 구현합니다:

```bash
# 프로젝트 관리
dli init [--home PATH]
dli config show

# 데이터셋 관리
dli dataset list [--catalog CATALOG] [--domain DOMAIN] [--tag TAG]
dli dataset show <dataset_name>
dli dataset validate <dataset_name> -p key=value

# 실행
dli run <dataset_name> -p execution_date=2025-01-01 [--dry-run]
dli run <dataset_name> -p execution_date=2025-01-01 --phase pre
dli run <dataset_name> -p execution_date=2025-01-01 --skip-pre --skip-post
```

---

## 참고 자료

- [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) - YAML 표준
- [dbt MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow) - Semantic Layer
- [dbt Jinja Functions](https://docs.getdbt.com/reference/dbt-jinja-functions) - 템플릿 함수
- [SQLMesh Macros](https://sqlmesh.readthedocs.io/en/stable/concepts/macros/sqlmesh_macros/) - 매크로 시스템
- [SQLglot Documentation](https://sqlglot.com/sqlglot.html) - SQL 파싱
