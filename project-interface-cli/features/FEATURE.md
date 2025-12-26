# dli Dataset Framework 5일 스프린트 계획

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          dli (Python 라이브러리)                              │
├─────────────────┬─────────────────┬─────────────────┬──────────────────────┤
│    core/        │   adapters/     │     cli/        │     airflow/         │
│  - models       │  - trino        │  - dataset cmd  │  - DatasetOperator   │
│  - discovery    │  - bigquery     │  - run cmd      │  - DatasetSensor     │
│  - registry     │  - snowflake    │  - config cmd   │                      │
│  - renderer     │                 │                 │                      │
│  - validator    │                 │                 │                      │
│  - executor     │                 │                 │                      │
│  - service      │                 │                 │                      │
└────────┬────────┴────────┬────────┴────────┬────────┴──────────┬───────────┘
         │                 │                 │                   │
         ▼                 ▼                 ▼                   ▼
     Python API       Trino/BQ/SF        CLI (dli)         Airflow DAG
     (import)         Execution          $ dli run         from dli.airflow
```

### 핵심 개념

```
$DLI_HOME/
├── dli.yaml                              # 프로젝트 설정
└── datasets/                             # Dataset Spec 루트
    ├── feed/                             # Domain 기반 구조
    │   ├── spec.iceberg.analytics.daily_clicks.yaml
    │   ├── daily_clicks.sql              # IDE 자동완성 지원
    │   ├── daily_clicks_pre.sql
    │   └── daily_clicks_post.sql
    └── revenue/
        ├── spec.iceberg.finance.daily_revenue.yaml
        └── daily_revenue.sql
```

### 배포 형태

| 사용처 | 설치 방법 | 사용 예시 |
|--------|----------|----------|
| **로컬 CLI** | `pip install dli` | `$ dli run iceberg.analytics.daily_clicks -p execution_date=2025-01-01` |
| **Python API** | `pip install dli` | `from dli.core import DatasetService` |
| **Airflow** | `pip install dli[airflow]` | `from dli.airflow import DatasetOperator` |
| **Web Backend** | `pip install dli[trino]` | Spring → Python API 호출 |

### 핵심 원칙

1. **Spec + SQL 분리**: YAML로 메타데이터, SQL로 로직 (IDE 지원)
2. **3단계 실행**: Pre → Main → Post (파티션 삭제, 실행, 최적화)
3. **유연한 구조**: Flat, Domain 기반, Catalog/Schema 계층 지원
4. **버전 관리**: 스키마 변경 이력 추적

---

## 업계 표준 참고

| 표준/도구 | 참고 포인트 | 적용 |
|-----------|------------|------|
| [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) | 벤더 중립 YAML 표준 | Spec 파일 구조 |
| [dbt MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow) | semantic_models + metrics | 메타데이터 필드 |
| [Databricks Unity Catalog](https://docs.databricks.com/aws/en/metric-views/) | `catalog.schema.table` 네임스페이스 | 3레벨 식별자 |
| [SQLMesh](https://sqlmesh.readthedocs.io/en/stable/concepts/models/overview/) | MODEL DDL + external YAML | 하이브리드 방식 |

---

## 일정 요약

| Day | 목표 | 산출물 |
|-----|------|--------|
| **Day 1** | Core Engine (v2) | models, discovery, registry, renderer, validator, executor, service |
| **Day 2** | CLI | dli dataset/run 명령어 (list, show, validate, run) |
| **Day 3** | Web UI | FastAPI 엔드포인트, Alpine.js 단일 파일 UI |
| **Day 4** | Airflow + 배포 | DatasetOperator, DatasetSensor, 라이브러리 빌드 |
| **Day 5** | 안정화 | 통합 테스트, 문서, 버그 수정 |

---

## Day 1: Core Engine (8h)

### 프로젝트 구조

```
project-interface-cli/
├── pyproject.toml
├── src/dli/
│   ├── core/
│   │   ├── __init__.py
│   │   ├── models.py        # Spec, Statement, Parameter 모델
│   │   ├── discovery.py     # DLI_HOME 탐색, 파일 로드
│   │   ├── registry.py      # Dataset 레지스트리
│   │   ├── renderer.py      # Jinja2 렌더러
│   │   ├── validator.py     # SQLGlot 검증
│   │   ├── executor.py      # 3단계 실행 엔진
│   │   └── service.py       # 통합 서비스
│   ├── adapters/
│   │   ├── trino.py         # Trino 어댑터
│   │   └── bigquery.py      # BQ 어댑터
│   ├── cli/                  # Day 2
│   ├── api/                  # Day 3
│   └── airflow/              # Day 4
└── tests/
```

### 핵심 의존성

```toml
dependencies = [
    "pydantic>=2.0",
    "jinja2>=3.1",
    "sqlglot>=23.0",
    "pyyaml>=6.0",
    "typer[all]>=0.9",
    "rich>=13.0",
]

[project.optional-dependencies]
trino = ["trino>=0.320"]
bigquery = ["google-cloud-bigquery>=3.0"]
airflow = ["apache-airflow>=2.7"]
```

### 구현 순서 (8h)

1. **models.py** (2h) - DatasetSpec, StatementDefinition, QueryParameter
2. **discovery.py** (1.5h) - DLI_HOME 탐색, Spec/SQL 로드
3. **registry.py** (1h) - 캐싱, 검색
4. **renderer.py** (1h) - Jinja2 렌더링
5. **validator.py** (1h) - SQLGlot 검증
6. **executor.py** (1.5h) - 3단계 실행 (Pre → Main → Post)

### Spec 파일 스키마

```yaml
# spec.{catalog}.{schema}.{table}.yaml
name: "iceberg.analytics.daily_clicks"
description: "1인당 item 평균 클릭수"

owner: "henrykim@example.com"
team: "@data-analytics"
domains: ["feed", "engagement"]
tags: ["daily", "kpi"]

versions:
  - version: "v1"
    started_at: "2015-12-01"
    ended_at: "2022-05-31"
  - version: "v2"
    started_at: "2022-06-01"    # ended_at: null = 현재 활성

query_type: "DML"               # SELECT | DML

parameters:
  - name: "execution_date"
    type: "date"
    required: true
  - name: "lookback_days"
    type: "integer"
    default: 7

# SQL: 인라인 또는 파일 참조
query_file: "daily_clicks.sql"  # IDE 자동완성 지원

pre_statements:
  - name: "delete_partition"
    sql: |
      DELETE FROM iceberg.analytics.daily_clicks
      WHERE dt = '{{ execution_date }}'

post_statements:
  - name: "optimize"
    file: "optimize.sql"
    continue_on_error: true

depends_on:
  - "iceberg.raw.user_events"

execution:
  timeout_seconds: 1800
  dialect: "trino"
```

### 참고 코드

- dbt-core: https://github.com/dbt-labs/dbt-core
- SQLMesh: https://github.com/TobikoData/sqlmesh
- MetricFlow: https://github.com/dbt-labs/metricflow

---

## Day 2: CLI (8h)

### 명령어 구조

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
dli run <dataset_name> -p execution_date=2025-01-01 --phase pre   # Pre만
dli run <dataset_name> -p execution_date=2025-01-01 --phase main  # Main만
dli run <dataset_name> -p execution_date=2025-01-01 --skip-pre --skip-post
```

### 구현 순서 (8h)

1. **main.py** (2h) - Typer 앱 구조, 서비스 초기화
2. **dataset.py** (2h) - list, show, validate 명령어
3. **run.py** (3h) - run 명령어 + 출력 형식
4. **utils.py** (1h) - 파라미터 파싱, 에러 핸들링

### 핵심 코드 패턴

```python
import typer
from rich.console import Console
from dli.core import DatasetService

app = typer.Typer()
console = Console()

@app.command()
def run(
    dataset_name: str,
    params: list[str] = typer.Option([], "-p"),
    dry_run: bool = typer.Option(False, "--dry-run"),
    skip_pre: bool = typer.Option(False, "--skip-pre"),
    skip_post: bool = typer.Option(False, "--skip-post"),
):
    service = DatasetService()
    result = service.execute(
        dataset_name,
        parse_params(params),
        dry_run=dry_run,
        skip_pre=skip_pre,
        skip_post=skip_post,
    )

    if not result.success:
        console.print(f"[red]Error: {result.error_message}[/red]")
        raise typer.Exit(1)

    display_result(result)
```

### 참고 코드

- Typer 문서: https://typer.tiangolo.com/
- dbt CLI: https://github.com/dbt-labs/dbt-core/blob/main/core/dbt/cli/main.py

---

## Day 3: Web UI (8h)

### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | /datasets | Dataset 목록 |
| GET | /datasets/{name} | Dataset 상세 |
| POST | /validate | 검증 |
| POST | /dry-run | 비용 추정 |
| POST | /execute | 실행 |

### 구현 순서 (8h)

1. **api/main.py** (3h) - FastAPI 엔드포인트
2. **api/static/index.html** (5h) - Alpine.js + Tailwind 단일 파일

### 프론트엔드 기술 선택

```html
<!-- Alpine.js + Tailwind CSS CDN - 빌드 불필요 -->
<script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3/dist/cdn.min.js"></script>
<script src="https://cdn.tailwindcss.com"></script>
```

### 핵심 API 패턴

```python
from fastapi import FastAPI, Depends
from dli.core import DatasetService

app = FastAPI()

@app.post("/execute")
async def execute(request: ExecuteRequest, service = Depends(get_service)):
    result = service.execute(request.dataset_name, request.params)
    if not result.success:
        raise HTTPException(400, result.error_message)
    return result
```

---

## Day 4: Airflow (8h)

### Operator 종류

| Operator | 용도 |
|----------|------|
| DatasetOperator | Dataset 실행 (Pre → Main → Post) |
| DatasetValidateOperator | 검증 (CI/CD용) |
| DatasetSensor | 조건 대기 |

### 구현 순서 (8h)

1. **operators.py** (4h) - Operator, ValidateOperator, Sensor
2. **examples/dag.py** (2h) - 샘플 DAG
3. **__init__.py** (1h) - 패키지 등록
4. **테스트** (1h)

### 핵심 코드 패턴

```python
from airflow.models import BaseOperator
from dli.core import DatasetService

class DatasetOperator(BaseOperator):
    template_fields = ('params', 'dataset_name')

    def __init__(
        self,
        dataset_name: str,
        params: dict = None,
        skip_pre: bool = False,
        skip_post: bool = False,
        **kwargs
    ):
        super().__init__(**kwargs)
        self.dataset_name = dataset_name
        self.params = params or {}
        self.skip_pre = skip_pre
        self.skip_post = skip_post

    def execute(self, context):
        service = DatasetService()
        result = service.execute(
            self.dataset_name,
            self.params,
            skip_pre=self.skip_pre,
            skip_post=self.skip_post,
        )

        if not result.success:
            raise Exception(result.error_message)

        return {
            'dataset_name': result.dataset_name,
            'success': result.success,
            'execution_time_ms': result.total_execution_time_ms,
        }
```

### DAG 사용 예시

```python
from dli.airflow import DatasetOperator

task = DatasetOperator(
    task_id='run_daily_clicks',
    dataset_name='iceberg.analytics.daily_clicks',
    params={'execution_date': '{{ ds }}'},  # Airflow 매크로 지원
)
```

---

## Day 5: 안정화 (8h)

| 시간 | 작업 |
|------|------|
| 4h | 통합 테스트 (pytest + DuckDB) |
| 2h | README + 사용 가이드 |
| 2h | 버그 수정 버퍼 |

---

## 핵심 주의사항

### 기술적

1. **Service Layer 필수** - CLI/Web/Airflow 모두 동일 로직 공유
2. **template_fields** - Airflow Operator에서 Jinja 매크로 지원 필수
3. **3단계 실행** - Pre 실패 시 Main 실행 안 함, continue_on_error 지원

### 스코프

1. **단일 DB 우선** - Trino 먼저, 이후 BigQuery/Snowflake 확장
2. **인증 생략** - MVP에서는 제외, Phase 2에서 추가
3. **Lineage 생략** - MVP에서는 제외

### Phase 2 확장

- Multi-dialect 지원
- 인증/인가
- Column-level Lineage
- 비용 제한
- 의존성 그래프 시각화

---

## 참고 GitHub 레포지토리

| 프로젝트 | 참고 포인트 | URL |
|----------|------------|-----|
| dbt-core | CLI 구조, Service Layer | https://github.com/dbt-labs/dbt-core |
| SQLMesh | SQLGlot 활용, 전체 아키텍처 | https://github.com/TobikoData/sqlmesh |
| MetricFlow | Semantic Layer, YAML 스키마 | https://github.com/dbt-labs/metricflow |
| SQLGlot | SQL 파싱/검증 | https://github.com/tobymao/sqlglot |
