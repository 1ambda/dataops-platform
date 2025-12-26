# Day 1: Core Engine 구현 가이드 (v2 - Spec 기반)

## 개요

Day 1에서는 DLI_HOME 기반의 Dataset Spec 시스템과 Core Engine을 구현합니다.

### 주요 변경사항 (v1 → v2)

| 항목 | v1 (기존) | v2 (신규) |
|------|----------|----------|
| 쿼리 정의 | `_schema.yml` 단일 파일 | `spec.*.yaml` + `.sql` 파일 분리 |
| 디렉토리 | `queries/` 고정 | `$DLI_HOME/datasets/` 유연 구조 |
| 쿼리 타입 | SELECT 중심 | SELECT / DML (INSERT, MERGE, UPDATE) |
| 실행 단계 | Main만 | Pre → Main → Post 3단계 |
| 버전 관리 | 없음 | versions 배열 지원 |

---

## 업계 표준 참고

| 표준/도구 | 참고 포인트 | 적용 |
|-----------|------------|------|
| [Open Semantic Interchange (OSI)](https://opensemanticinterchange.org/) | 벤더 중립 YAML 표준 | Spec 파일 구조 |
| [dbt MetricFlow](https://docs.getdbt.com/docs/build/about-metricflow) | semantic_models + metrics | 메타데이터 필드 |
| [Databricks Unity Catalog](https://docs.databricks.com/aws/en/metric-views/) | `catalog.schema.table` 네임스페이스 | 3레벨 식별자 |
| [SQLMesh](https://sqlmesh.readthedocs.io/en/stable/concepts/models/overview/) | MODEL DDL + external YAML | 하이브리드 방식 |

---

## 구현 순서 및 시간

| 순서 | 파일 | 시간 | 설명 |
|------|------|------|------|
| 1 | models.py | 2h | Pydantic 데이터 모델 (Spec, Statement, Parameter) |
| 2 | discovery.py | 1.5h | DLI_HOME 탐색, Spec/SQL 파일 로드 |
| 3 | registry.py | 1h | Dataset 레지스트리 (캐싱, 검색) |
| 4 | renderer.py | 1h | Jinja2 렌더러 (SQL 필터) |
| 5 | validator.py | 1h | SQLGlot 검증 |
| 6 | executor.py | 1.5h | 3단계 실행 엔진 (Pre → Main → Post) |

---

## 1. 프로젝트 구조

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
│   │   ├── executor.py      # 실행 추상화
│   │   └── service.py       # 통합 서비스
│   ├── adapters/
│   │   ├── __init__.py
│   │   ├── bigquery.py
│   │   └── trino.py
│   └── cli/                  # Day 2
└── tests/
    └── core/
```

---

## 2. DLI_HOME 디렉토리 구조

### 유연한 구조 지원

```
$DLI_HOME/
├── dli.yaml                          # 프로젝트 설정 (필수)
├── datasets/                         # 데이터셋 루트
│   │
│   │  # 옵션 A: Flat 구조 (소규모)
│   ├── spec.iceberg.analytics.daily_clicks.yaml
│   ├── daily_clicks.sql
│   │
│   │  # 옵션 B: Domain 기반 구조 (권장)
│   ├── feed/
│   │   ├── spec.iceberg.analytics.daily_clicks.yaml
│   │   ├── daily_clicks.sql
│   │   ├── daily_clicks_pre.sql
│   │   └── daily_clicks_post.sql
│   │
│   │  # 옵션 C: Catalog/Schema 계층 (대규모)
│   └── iceberg/
│       └── analytics/
│           └── daily_clicks/
│               ├── spec.yaml
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
  spec_patterns:
    - "spec.*.yaml"
    - "spec.yaml"
    - "*.spec.yaml"
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

## 3. Spec 파일 스키마

### 전체 스키마 정의

```yaml
# spec.{catalog}.{schema}.{table}.yaml

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
#    - 인라인 SQL 또는 파일 참조
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

## 4. models.py

### 기능
- DatasetSpec: Spec 파일 전체 구조
- StatementDefinition: Pre/Post SQL 정의
- QueryParameter: 파라미터 정의
- DatasetVersion: 버전 정보
- ExecutionConfig: 실행 설정

### 코드

```python
from __future__ import annotations

from datetime import UTC, datetime, date
from enum import Enum
from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field


class QueryType(str, Enum):
    SELECT = "SELECT"
    DML = "DML"


class ParameterType(str, Enum):
    STRING = "string"
    INTEGER = "integer"
    FLOAT = "float"
    DATE = "date"
    BOOLEAN = "boolean"
    LIST = "list"


class QueryParameter(BaseModel):
    """쿼리 파라미터 정의"""
    name: str
    type: ParameterType = ParameterType.STRING
    required: bool = True
    default: Any | None = None
    description: str = ""

    def validate_value(self, value: Any) -> Any:
        if value is None:
            if self.required and self.default is None:
                raise ValueError(f"Required parameter '{self.name}' is missing")
            return self.default

        converters = {
            ParameterType.INTEGER: int,
            ParameterType.FLOAT: float,
            ParameterType.BOOLEAN: lambda x: str(x).lower() in ("true", "1"),
            ParameterType.STRING: str,
            ParameterType.DATE: lambda x: x if isinstance(x, date) else date.fromisoformat(str(x)),
        }
        return converters.get(self.type, str)(value)


class StatementDefinition(BaseModel):
    """Pre/Post SQL 정의 (인라인 또는 파일)"""
    name: str
    sql: str | None = None           # 인라인 SQL
    file: str | None = None          # SQL 파일 경로
    continue_on_error: bool = False  # 실패 시 계속 진행

    def get_sql(self, base_dir: Path) -> str:
        if self.sql:
            return self.sql
        if self.file:
            file_path = base_dir / self.file
            return file_path.read_text(encoding="utf-8")
        raise ValueError(f"Statement '{self.name}' has no sql or file")


class DatasetVersion(BaseModel):
    """버전 정보"""
    version: str
    started_at: date
    ended_at: date | None = None
    description: str = ""

    @property
    def is_active(self) -> bool:
        return self.ended_at is None


class ExecutionConfig(BaseModel):
    """실행 설정"""
    timeout_seconds: int = 3600
    retry_count: int = 2
    retry_delay_seconds: int = 60
    dialect: str = "trino"


class DatasetSpec(BaseModel):
    """Dataset Spec 전체 정의"""
    # 기본 식별자
    name: str                                    # catalog.schema.table
    description: str = ""

    # 소유권
    owner: str
    team: str
    domains: list[str] = []
    tags: list[str] = []

    # 버전
    versions: list[DatasetVersion] = []

    # 쿼리 정의
    query_type: QueryType
    parameters: list[QueryParameter] = []

    # SQL (인라인 또는 파일)
    query_statement: str | None = None
    query_file: str | None = None

    # Pre/Post
    pre_statements: list[StatementDefinition] = []
    post_statements: list[StatementDefinition] = []

    # 실행 설정
    execution: ExecutionConfig = Field(default_factory=ExecutionConfig)

    # 메타데이터
    depends_on: list[str] = []
    schema_fields: list[dict[str, Any]] = Field(default=[], alias="schema")

    # 내부 필드 (로드 시 설정)
    _spec_path: Path | None = None
    _base_dir: Path | None = None

    def get_main_sql(self) -> str:
        if self.query_statement:
            return self.query_statement
        if self.query_file and self._base_dir:
            return (self._base_dir / self.query_file).read_text(encoding="utf-8")
        raise ValueError(f"Dataset '{self.name}' has no query_statement or query_file")

    @property
    def catalog(self) -> str:
        parts = self.name.split(".")
        return parts[0] if len(parts) >= 1 else ""

    @property
    def schema(self) -> str:
        parts = self.name.split(".")
        return parts[1] if len(parts) >= 2 else ""

    @property
    def table(self) -> str:
        parts = self.name.split(".")
        return parts[2] if len(parts) >= 3 else ""

    @property
    def active_version(self) -> DatasetVersion | None:
        for v in self.versions:
            if v.is_active:
                return v
        return None


class ValidationResult(BaseModel):
    """검증 결과"""
    is_valid: bool
    errors: list[str] = []
    warnings: list[str] = []
    rendered_sql: str | None = None
    phase: str = "main"  # pre, main, post


class ExecutionResult(BaseModel):
    """실행 결과"""
    dataset_name: str
    phase: str                           # pre, main, post
    statement_name: str | None = None    # Pre/Post statement 이름
    success: bool
    row_count: int | None = None
    columns: list[str] = []
    data: list[dict[str, Any]] = []
    rendered_sql: str = ""
    execution_time_ms: int = 0
    error_message: str | None = None
    executed_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class DatasetExecutionResult(BaseModel):
    """Dataset 전체 실행 결과"""
    dataset_name: str
    success: bool
    pre_results: list[ExecutionResult] = []
    main_result: ExecutionResult | None = None
    post_results: list[ExecutionResult] = []
    total_execution_time_ms: int = 0
    error_message: str | None = None
```

### 테스트

```python
# tests/core/test_models.py
import pytest
from datetime import date
from dli.core.models import (
    QueryParameter, ParameterType, DatasetSpec, QueryType,
    StatementDefinition, DatasetVersion
)


class TestQueryParameter:
    def test_validate_required_missing(self):
        param = QueryParameter(name="dt", type=ParameterType.DATE, required=True)
        with pytest.raises(ValueError, match="Required parameter"):
            param.validate_value(None)

    def test_validate_with_default(self):
        param = QueryParameter(name="days", type=ParameterType.INTEGER, default=7)
        assert param.validate_value(None) == 7

    def test_validate_date_conversion(self):
        param = QueryParameter(name="dt", type=ParameterType.DATE)
        result = param.validate_value("2025-01-01")
        assert result == date(2025, 1, 1)


class TestDatasetVersion:
    def test_active_version(self):
        v = DatasetVersion(version="v2", started_at=date(2022, 6, 1), ended_at=None)
        assert v.is_active is True

    def test_inactive_version(self):
        v = DatasetVersion(version="v1", started_at=date(2015, 1, 1), ended_at=date(2022, 5, 31))
        assert v.is_active is False


class TestDatasetSpec:
    def test_parse_name(self):
        spec = DatasetSpec(
            name="iceberg.analytics.daily_clicks",
            owner="henry@example.com",
            team="@analytics",
            query_type=QueryType.DML,
            query_statement="SELECT 1",
        )
        assert spec.catalog == "iceberg"
        assert spec.schema == "analytics"
        assert spec.table == "daily_clicks"

    def test_active_version(self):
        spec = DatasetSpec(
            name="iceberg.analytics.test",
            owner="henry@example.com",
            team="@analytics",
            query_type=QueryType.SELECT,
            query_statement="SELECT 1",
            versions=[
                DatasetVersion(version="v1", started_at=date(2020, 1, 1), ended_at=date(2022, 12, 31)),
                DatasetVersion(version="v2", started_at=date(2023, 1, 1), ended_at=None),
            ],
        )
        assert spec.active_version.version == "v2"
```

---

## 5. discovery.py

### 기능
- DLI_HOME 환경 변수 또는 경로에서 프로젝트 로드
- dli.yaml 파싱
- Spec 파일 및 SQL 파일 탐색
- DatasetSpec 객체 생성

### 코드

```python
from pathlib import Path
from typing import Iterator
import os

import yaml

from .models import DatasetSpec


class ProjectConfig:
    """dli.yaml 프로젝트 설정"""

    def __init__(self, config_path: Path):
        self.config_path = config_path
        self.root_dir = config_path.parent

        with open(config_path, encoding="utf-8") as f:
            self._data = yaml.safe_load(f) or {}

    @property
    def project_name(self) -> str:
        return self._data.get("project", {}).get("name", "unnamed")

    @property
    def datasets_dir(self) -> Path:
        rel_path = self._data.get("discovery", {}).get("datasets_dir", "datasets")
        return self.root_dir / rel_path

    @property
    def spec_patterns(self) -> list[str]:
        return self._data.get("discovery", {}).get(
            "spec_patterns", ["spec.*.yaml", "spec.yaml", "*.spec.yaml"]
        )

    @property
    def defaults(self) -> dict:
        return self._data.get("defaults", {})

    def get_environment(self, env_name: str) -> dict:
        return self._data.get("environments", {}).get(env_name, {})


class DatasetDiscovery:
    """Dataset Spec 탐색 및 로드"""

    def __init__(self, project_config: ProjectConfig):
        self.config = project_config

    def discover_all(self) -> Iterator[DatasetSpec]:
        """모든 Spec 파일 탐색 및 로드"""
        datasets_dir = self.config.datasets_dir
        if not datasets_dir.exists():
            return

        for pattern in self.config.spec_patterns:
            for spec_path in datasets_dir.rglob(pattern):
                try:
                    yield self._load_spec(spec_path)
                except Exception as e:
                    # 로깅 또는 경고 처리
                    print(f"Warning: Failed to load {spec_path}: {e}")

    def _load_spec(self, spec_path: Path) -> DatasetSpec:
        """Spec 파일 로드"""
        with open(spec_path, encoding="utf-8") as f:
            data = yaml.safe_load(f)

        # 기본값 병합
        if "execution" not in data:
            data["execution"] = {}
        for key, value in self.config.defaults.items():
            if key not in data["execution"]:
                data["execution"][key] = value

        spec = DatasetSpec.model_validate(data)

        # 내부 경로 설정
        spec._spec_path = spec_path
        spec._base_dir = spec_path.parent

        return spec

    def find_spec(self, dataset_name: str) -> DatasetSpec | None:
        """이름으로 Spec 찾기"""
        for spec in self.discover_all():
            if spec.name == dataset_name:
                return spec
        return None


def get_dli_home() -> Path:
    """DLI_HOME 환경 변수 또는 현재 디렉토리"""
    dli_home = os.environ.get("DLI_HOME")
    if dli_home:
        return Path(dli_home)
    return Path.cwd()


def load_project(path: Path | None = None) -> ProjectConfig:
    """프로젝트 설정 로드"""
    if path is None:
        path = get_dli_home()

    config_path = path / "dli.yaml"
    if not config_path.exists():
        raise FileNotFoundError(f"dli.yaml not found in {path}")

    return ProjectConfig(config_path)
```

### 테스트

```python
# tests/core/test_discovery.py
import pytest
import tempfile
from pathlib import Path

import yaml

from dli.core.discovery import (
    ProjectConfig, DatasetDiscovery, load_project, get_dli_home
)
from dli.core.models import QueryType


@pytest.fixture
def temp_project():
    with tempfile.TemporaryDirectory() as tmpdir:
        root = Path(tmpdir)

        # dli.yaml
        config = {
            "version": "1",
            "project": {"name": "test-project"},
            "discovery": {"datasets_dir": "datasets"},
            "defaults": {"dialect": "trino", "timeout_seconds": 300},
        }
        (root / "dli.yaml").write_text(yaml.dump(config))

        # datasets 디렉토리
        datasets_dir = root / "datasets" / "feed"
        datasets_dir.mkdir(parents=True)

        # Spec 파일
        spec = {
            "name": "iceberg.analytics.daily_clicks",
            "owner": "henry@example.com",
            "team": "@analytics",
            "domains": ["feed"],
            "query_type": "DML",
            "query_file": "daily_clicks.sql",
        }
        (datasets_dir / "spec.iceberg.analytics.daily_clicks.yaml").write_text(
            yaml.dump(spec)
        )

        # SQL 파일
        (datasets_dir / "daily_clicks.sql").write_text(
            "INSERT INTO t SELECT * FROM s WHERE dt = '{{ execution_date }}'"
        )

        yield root


class TestProjectConfig:
    def test_load_config(self, temp_project):
        config = load_project(temp_project)
        assert config.project_name == "test-project"
        assert config.datasets_dir == temp_project / "datasets"

    def test_defaults(self, temp_project):
        config = load_project(temp_project)
        assert config.defaults["dialect"] == "trino"


class TestDatasetDiscovery:
    def test_discover_all(self, temp_project):
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        specs = list(discovery.discover_all())
        assert len(specs) == 1
        assert specs[0].name == "iceberg.analytics.daily_clicks"

    def test_find_spec(self, temp_project):
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("iceberg.analytics.daily_clicks")
        assert spec is not None
        assert spec.query_type == QueryType.DML

    def test_sql_file_loading(self, temp_project):
        config = load_project(temp_project)
        discovery = DatasetDiscovery(config)

        spec = discovery.find_spec("iceberg.analytics.daily_clicks")
        sql = spec.get_main_sql()
        assert "INSERT INTO" in sql
        assert "{{ execution_date }}" in sql
```

---

## 6. registry.py

### 기능
- Dataset 캐싱
- 이름/태그/도메인으로 검색
- 리로드 지원

### 코드

```python
from .models import DatasetSpec
from .discovery import ProjectConfig, DatasetDiscovery


class DatasetRegistry:
    """Dataset 레지스트리 (캐싱 및 검색)"""

    def __init__(self, project_config: ProjectConfig):
        self.config = project_config
        self._discovery = DatasetDiscovery(project_config)
        self._cache: dict[str, DatasetSpec] = {}
        self._load_all()

    def _load_all(self) -> None:
        """모든 Dataset 로드"""
        for spec in self._discovery.discover_all():
            self._cache[spec.name] = spec

    def get(self, name: str) -> DatasetSpec | None:
        return self._cache.get(name)

    def list_all(self) -> list[DatasetSpec]:
        return list(self._cache.values())

    def search(
        self,
        *,
        tag: str | None = None,
        domain: str | None = None,
        owner: str | None = None,
        catalog: str | None = None,
        schema: str | None = None,
    ) -> list[DatasetSpec]:
        results = self.list_all()

        if tag:
            results = [s for s in results if tag in s.tags]
        if domain:
            results = [s for s in results if domain in s.domains]
        if owner:
            results = [s for s in results if s.owner == owner]
        if catalog:
            results = [s for s in results if s.catalog == catalog]
        if schema:
            results = [s for s in results if s.schema == schema]

        return results

    def reload(self) -> None:
        self._cache.clear()
        self._load_all()
```

---

## 7. executor.py (3단계 실행)

### 기능
- Pre → Main → Post 3단계 실행
- continue_on_error 처리
- 실행 결과 수집

### 코드

```python
from abc import ABC, abstractmethod
import time
from typing import Any

from .models import (
    DatasetSpec, ExecutionResult, DatasetExecutionResult
)


class BaseExecutor(ABC):
    @abstractmethod
    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:
        pass

    @abstractmethod
    def dry_run(self, sql: str) -> dict[str, Any]:
        pass

    @abstractmethod
    def test_connection(self) -> bool:
        pass


class MockExecutor(BaseExecutor):
    """테스트용 Mock 실행기"""

    def __init__(self, mock_data: list[dict] | None = None):
        self.mock_data = mock_data or []
        self.executed_sqls: list[str] = []

    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:
        self.executed_sqls.append(sql)
        return ExecutionResult(
            dataset_name="",
            phase="main",
            success=True,
            row_count=len(self.mock_data),
            columns=list(self.mock_data[0].keys()) if self.mock_data else [],
            data=self.mock_data,
            rendered_sql=sql,
            execution_time_ms=10,
        )

    def dry_run(self, sql: str) -> dict[str, Any]:
        return {"valid": True, "bytes_processed": 1000}

    def test_connection(self) -> bool:
        return True


class DatasetExecutor:
    """Dataset 3단계 실행 (Pre → Main → Post)"""

    def __init__(self, executor: BaseExecutor):
        self.executor = executor

    def execute(
        self,
        spec: DatasetSpec,
        rendered_sqls: dict[str, str | list[str]],
        *,
        skip_pre: bool = False,
        skip_post: bool = False,
    ) -> DatasetExecutionResult:
        """
        Args:
            spec: DatasetSpec
            rendered_sqls: {
                "pre": [sql1, sql2, ...],
                "main": sql,
                "post": [sql1, sql2, ...]
            }
        """
        start_time = time.time()
        pre_results: list[ExecutionResult] = []
        post_results: list[ExecutionResult] = []
        main_result: ExecutionResult | None = None
        overall_success = True
        error_message = None

        timeout = spec.execution.timeout_seconds

        # 1. Pre Statements
        if not skip_pre and "pre" in rendered_sqls:
            for i, sql in enumerate(rendered_sqls["pre"]):
                stmt = spec.pre_statements[i] if i < len(spec.pre_statements) else None
                stmt_name = stmt.name if stmt else f"pre_{i}"

                result = self.executor.execute_sql(sql, timeout)
                result.phase = "pre"
                result.statement_name = stmt_name
                result.dataset_name = spec.name
                pre_results.append(result)

                if not result.success:
                    if stmt and stmt.continue_on_error:
                        continue
                    overall_success = False
                    error_message = f"Pre statement '{stmt_name}' failed: {result.error_message}"
                    break

        # 2. Main Statement
        if overall_success and "main" in rendered_sqls:
            main_result = self.executor.execute_sql(rendered_sqls["main"], timeout)
            main_result.phase = "main"
            main_result.dataset_name = spec.name

            if not main_result.success:
                overall_success = False
                error_message = f"Main query failed: {main_result.error_message}"

        # 3. Post Statements
        if overall_success and not skip_post and "post" in rendered_sqls:
            for i, sql in enumerate(rendered_sqls["post"]):
                stmt = spec.post_statements[i] if i < len(spec.post_statements) else None
                stmt_name = stmt.name if stmt else f"post_{i}"

                result = self.executor.execute_sql(sql, timeout)
                result.phase = "post"
                result.statement_name = stmt_name
                result.dataset_name = spec.name
                post_results.append(result)

                if not result.success:
                    if stmt and stmt.continue_on_error:
                        continue
                    overall_success = False
                    error_message = f"Post statement '{stmt_name}' failed: {result.error_message}"
                    break

        total_time = int((time.time() - start_time) * 1000)

        return DatasetExecutionResult(
            dataset_name=spec.name,
            success=overall_success,
            pre_results=pre_results,
            main_result=main_result,
            post_results=post_results,
            total_execution_time_ms=total_time,
            error_message=error_message,
        )
```

---

## 8. service.py (통합 서비스)

### 코드

```python
from pathlib import Path
from typing import Any

from .models import (
    DatasetSpec, ValidationResult, DatasetExecutionResult
)
from .discovery import ProjectConfig, load_project
from .registry import DatasetRegistry
from .renderer import SQLRenderer
from .validator import SQLValidator
from .executor import BaseExecutor, DatasetExecutor


class DatasetService:
    """Dataset 통합 서비스 (CLI/Web/Airflow 공통)"""

    def __init__(
        self,
        project_path: Path | None = None,
        executor: BaseExecutor | None = None,
    ):
        self.config = load_project(project_path)
        self.registry = DatasetRegistry(self.config)
        self.renderer = SQLRenderer()
        self.validator = SQLValidator(self.config.defaults.get("dialect", "trino"))
        self._executor = executor
        self._dataset_executor = DatasetExecutor(executor) if executor else None

    def list_datasets(
        self,
        *,
        tag: str | None = None,
        domain: str | None = None,
        catalog: str | None = None,
    ) -> list[DatasetSpec]:
        return self.registry.search(tag=tag, domain=domain, catalog=catalog)

    def get_dataset(self, name: str) -> DatasetSpec | None:
        return self.registry.get(name)

    def validate(
        self,
        dataset_name: str,
        params: dict[str, Any],
    ) -> list[ValidationResult]:
        """모든 SQL 검증 (Pre, Main, Post)"""
        spec = self.registry.get(dataset_name)
        if not spec:
            return [ValidationResult(
                is_valid=False,
                errors=[f"Dataset '{dataset_name}' not found"],
            )]

        results: list[ValidationResult] = []

        # Pre Statements
        for stmt in spec.pre_statements:
            sql = stmt.get_sql(spec._base_dir)
            rendered = self.renderer.render(sql, spec.parameters, params)
            result = self.validator.validate(rendered)
            result.phase = "pre"
            results.append(result)

        # Main
        main_sql = spec.get_main_sql()
        rendered = self.renderer.render(main_sql, spec.parameters, params)
        result = self.validator.validate(rendered)
        result.phase = "main"
        results.append(result)

        # Post Statements
        for stmt in spec.post_statements:
            sql = stmt.get_sql(spec._base_dir)
            rendered = self.renderer.render(sql, spec.parameters, params)
            result = self.validator.validate(rendered)
            result.phase = "post"
            results.append(result)

        return results

    def execute(
        self,
        dataset_name: str,
        params: dict[str, Any],
        *,
        skip_pre: bool = False,
        skip_post: bool = False,
        dry_run: bool = False,
    ) -> DatasetExecutionResult:
        """Dataset 실행"""
        if not self._dataset_executor:
            raise RuntimeError("Executor not configured")

        spec = self.registry.get(dataset_name)
        if not spec:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=False,
                error_message=f"Dataset '{dataset_name}' not found",
            )

        # SQL 렌더링
        rendered_sqls: dict[str, str | list[str]] = {}

        if not skip_pre and spec.pre_statements:
            rendered_sqls["pre"] = [
                self.renderer.render(
                    stmt.get_sql(spec._base_dir), spec.parameters, params
                )
                for stmt in spec.pre_statements
            ]

        rendered_sqls["main"] = self.renderer.render(
            spec.get_main_sql(), spec.parameters, params
        )

        if not skip_post and spec.post_statements:
            rendered_sqls["post"] = [
                self.renderer.render(
                    stmt.get_sql(spec._base_dir), spec.parameters, params
                )
                for stmt in spec.post_statements
            ]

        # 검증
        for phase, sqls in rendered_sqls.items():
            sql_list = sqls if isinstance(sqls, list) else [sqls]
            for sql in sql_list:
                result = self.validator.validate(sql)
                if not result.is_valid:
                    return DatasetExecutionResult(
                        dataset_name=dataset_name,
                        success=False,
                        error_message=f"Validation failed in {phase}: {result.errors}",
                    )

        if dry_run:
            return DatasetExecutionResult(
                dataset_name=dataset_name,
                success=True,
                error_message="Dry run completed (no execution)",
            )

        # 실행
        return self._dataset_executor.execute(
            spec, rendered_sqls, skip_pre=skip_pre, skip_post=skip_post
        )

    def reload(self) -> None:
        self.registry.reload()
```

---

## 9. 샘플 파일

### dli.yaml

```yaml
version: "1"

project:
  name: "dataops-metrics"
  description: "Data Analytics Metrics Project"

discovery:
  datasets_dir: "datasets"
  spec_patterns:
    - "spec.*.yaml"
    - "spec.yaml"

defaults:
  dialect: "trino"
  timeout_seconds: 3600
  retry_count: 2

environments:
  dev:
    connection_string: "trino://localhost:8080/iceberg"
  prod:
    connection_string: "trino://trino-prod:8080/iceberg"
```

### spec.iceberg.analytics.daily_clicks.yaml

```yaml
name: "iceberg.analytics.daily_clicks"
description: "1인당 item 평균 클릭수"

owner: "henrykim@example.com"
team: "@data-analytics"
domains:
  - "feed"
  - "engagement"
tags:
  - "daily"
  - "kpi"

versions:
  - version: "v1"
    started_at: "2015-12-01"
    ended_at: "2022-05-31"
  - version: "v2"
    started_at: "2022-06-01"

query_type: "DML"

parameters:
  - name: "execution_date"
    type: "date"
    required: true
  - name: "lookback_days"
    type: "integer"
    default: 7

query_file: "daily_clicks.sql"

pre_statements:
  - name: "delete_partition"
    sql: |
      DELETE FROM iceberg.analytics.daily_clicks
      WHERE dt = '{{ execution_date }}'

post_statements:
  - name: "optimize"
    sql: |
      ALTER TABLE iceberg.analytics.daily_clicks
      EXECUTE optimize WHERE dt = '{{ execution_date }}'
    continue_on_error: true

depends_on:
  - "iceberg.raw.user_events"

execution:
  timeout_seconds: 1800
  dialect: "trino"
```

### daily_clicks.sql (IDE 자동완성 지원)

```sql
-- daily_clicks.sql
-- 1인당 item 평균 클릭수 집계

{% set target = 'iceberg.analytics.daily_clicks' %}
{% set source = 'iceberg.raw.user_events' %}

INSERT INTO {{ target }}
SELECT
    dt,
    user_id,
    COUNT(DISTINCT item_id) AS click_count,
    AVG(click_duration_ms) AS avg_duration_ms
FROM {{ source }}
WHERE dt BETWEEN DATE_ADD('{{ execution_date }}', -{{ lookback_days }})
      AND '{{ execution_date }}'
  AND event_type = 'click'
GROUP BY dt, user_id
```

---

## Day 1 체크리스트

- [ ] models.py (DatasetSpec, StatementDefinition, QueryParameter, etc.)
- [ ] discovery.py (ProjectConfig, DatasetDiscovery)
- [ ] registry.py (DatasetRegistry)
- [ ] renderer.py (SQLRenderer)
- [ ] validator.py (SQLValidator)
- [ ] executor.py (BaseExecutor, MockExecutor, DatasetExecutor)
- [ ] service.py (DatasetService)
- [ ] 샘플 파일 (dli.yaml, spec.yaml, .sql)
- [ ] 전체 테스트

---

## 다음 단계 (Day 2: CLI)

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
