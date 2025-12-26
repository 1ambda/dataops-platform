# Day 1: Core Engine 구현 완료

## 개요

Day 1에서는 DLI_HOME 기반의 Dataset Spec 시스템과 Core Engine을 성공적으로 구현했습니다.

**완료일**: 2025-12-28
**테스트 결과**: 179 tests passed
**코드 품질**: pyright 0 errors, ruff checks passed

---

## 구현 파일 목록

### Core Engine (`src/dli/core/`)

| 파일 | 설명 | 라인수 |
|------|------|--------|
| `models.py` | Pydantic 데이터 모델 | ~220 |
| `discovery.py` | DLI_HOME 탐색, Spec/SQL 파일 로드 | ~80 |
| `registry.py` | Dataset 레지스트리 (캐싱, 검색) | ~130 |
| `renderer.py` | Jinja2 SQL 렌더러 (커스텀 필터) | ~120 |
| `validator.py` | SQLGlot SQL 검증 | ~170 |
| `executor.py` | 3단계 실행 엔진 (Pre → Main → Post) | ~190 |
| `service.py` | 통합 서비스 레이어 | ~220 |
| `__init__.py` | 모듈 exports | ~50 |

### 테스트 (`tests/core/`)

| 파일 | 테스트 수 | 설명 |
|------|----------|------|
| `test_models.py` | 29 | 데이터 모델 테스트 |
| `test_discovery.py` | 19 | 프로젝트 탐색 테스트 |
| `test_registry.py` | 30 | 레지스트리 테스트 |
| `test_renderer.py` | 19 | SQL 렌더링 테스트 |
| `test_validator.py` | 26 | SQL 검증 테스트 |
| `test_executor.py` | 20 | 실행 엔진 테스트 |
| `test_service.py` | 36 | 통합 서비스 테스트 |

### 샘플 Fixtures (`tests/fixtures/sample_project/`)

```
sample_project/
├── dli.yaml                                           # 프로젝트 설정
├── datasets/
│   ├── feed/
│   │   ├── spec.iceberg.analytics.daily_clicks.yaml   # DML Spec
│   │   └── daily_clicks.sql                           # SQL 파일
│   └── reporting/
│       ├── spec.iceberg.reporting.user_summary.yaml   # SELECT Spec
│       └── user_summary.sql                           # SQL 파일
```

---

## 핵심 기능

### 1. DatasetSpec 모델

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

### 2. 프로젝트 탐색

```python
from dli.core import load_project, DatasetDiscovery

config = load_project(Path("/path/to/dli_home"))
discovery = DatasetDiscovery(config)

for spec in discovery.discover_all():
    print(f"{spec.name}: {spec.description}")
```

### 3. Dataset 레지스트리

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

### 4. SQL 렌더링

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

### 5. SQL 검증

```python
from dli.core import SQLValidator

validator = SQLValidator(dialect="trino")
result = validator.validate("SELECT * FROM users")

if result.is_valid:
    tables = validator.extract_tables("SELECT * FROM users JOIN orders")
    formatted = validator.format_sql("select * from users")
```

### 6. 3단계 실행 엔진

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

# 결과 확인
print(f"Success: {result.success}")
print(f"Pre results: {len(result.pre_results)}")
print(f"Main result: {result.main_result}")
print(f"Post results: {len(result.post_results)}")
```

### 7. 통합 서비스

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

---

## 코드 리뷰 개선사항

expert-python Agent를 통한 리뷰 후 적용된 개선사항:

### 1. Pydantic 패턴 개선 (`models.py`)
- `PrivateAttr` 사용으로 private 필드 정의 개선
- Public `base_dir`, `spec_path` 프로퍼티 추가
- `set_paths()` 메서드로 깔끔한 초기화

### 2. 예외 처리 개선 (`discovery.py`, `service.py`)
- 광범위한 `Exception` 대신 구체적 예외 타입 사용
- `(OSError, ValueError, yaml.YAMLError, ValidationError)` 등

### 3. Python 패턴 매칭 활용 (`validator.py`, `executor.py`)
```python
match parsed:
    case exp.Select():
        return "SELECT"
    case exp.Insert():
        return "INSERT"
```

### 4. 타입 힌트 개선 (`registry.py`, `renderer.py`)
- `Iterator[DatasetSpec]` 반환 타입 명시
- `Any` 타입 적절한 사용

### 5. 코드 품질
- ruff 린팅 통과
- pyright 타입 체크 통과 (0 errors)

---

## Day 1 체크리스트 완료

- [x] models.py (DatasetSpec, StatementDefinition, QueryParameter, etc.)
- [x] discovery.py (ProjectConfig, DatasetDiscovery)
- [x] registry.py (DatasetRegistry)
- [x] renderer.py (SQLRenderer)
- [x] validator.py (SQLValidator)
- [x] executor.py (BaseExecutor, MockExecutor, DatasetExecutor)
- [x] service.py (DatasetService)
- [x] 샘플 파일 (dli.yaml, spec.yaml, .sql)
- [x] 전체 테스트 (179 tests passed)
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

- [FEATURE_DAY1.md](./FEATURE_DAY1.md) - 구현 가이드 원본
- [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) - YAML 표준
- [dbt MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow) - 메타데이터 참고
- [SQLglot Documentation](https://sqlglot.com/sqlglot.html) - SQL 파싱
