# RELEASE: Library Interface Implementation

> **Version:** 0.2.0
> **Release Date:** 2025-12-30
> **Status:** Implemented & Tested

---

## 1. Release Summary

LIBRARY_FEATURE.md v1.2.0 스펙에 따라 Library Interface를 구현했습니다.
`dataops-cli` 패키지를 라이브러리 형태로 제공하여 Airflow Python Operator, Basecamp Parser 등
외부 시스템에서 프로그래매틱하게 호출할 수 있습니다.

---

## 2. Implemented Components

### 2.1 Exception Hierarchy (`dli/exceptions.py`)

| Class | ErrorCode | Description |
|-------|-----------|-------------|
| `DLIError` | - | Base exception with code + details |
| `ConfigurationError` | DLI-001, DLI-002 | Config errors |
| `DatasetNotFoundError` | DLI-101 | Dataset not found |
| `MetricNotFoundError` | DLI-102 | Metric not found |
| `TableNotFoundError` | DLI-103 | Table not found |
| `DLIValidationError` | DLI-201 ~ DLI-203 | Validation errors |
| `TranspileError` | DLI-301, DLI-302 | SQL transpile errors |
| `ExecutionError` | DLI-401, DLI-402 | Execution errors |
| `ServerError` | DLI-501 ~ DLI-503 | Server communication errors |
| `WorkflowNotFoundError` | DLI-601 | Workflow not found |

### 2.2 Data Models (`dli/models/common.py`)

| Model | Type | Description |
|-------|------|-------------|
| `ExecutionContext` | Pydantic Settings | 환경변수 자동 로딩 (DLI_ prefix) |
| `ResultStatus` | Enum | SUCCESS, FAILURE, SKIPPED, PENDING |
| `ValidationResult` | BaseModel | valid, errors, warnings |
| `BaseResult` | BaseModel | status, started_at, duration_ms |
| `DatasetResult` | BaseModel | 실행 결과 + SQL |
| `MetricResult` | BaseModel | 실행 결과 + data |
| `TranspileResult` | BaseModel | 변환 결과 + rules |
| `TranspileRule` | BaseModel | 변환 규칙 |
| `TranspileWarning` | BaseModel | 변환 경고 |
| `EnvironmentInfo` | BaseModel | 환경 정보 |
| `ConfigValue` | BaseModel | 설정 값 |

### 2.3 API Classes (`dli/api/`)

| API Class | Methods | Description |
|-----------|---------|-------------|
| `DatasetAPI` | list_datasets, get, run, run_sql, validate, register, render_sql | Dataset CRUD + 실행 |
| `MetricAPI` | list_metrics, get, run, validate, register, render_sql | Metric CRUD + 실행 |
| `TranspileAPI` | transpile, validate_sql, get_rules, format_sql | SQL 변환 |
| `CatalogAPI` | list_tables, get, search | 카탈로그 브라우징 |
| `ConfigAPI` | get, list_environments, get_current_environment, get_server_status | 설정 조회 (읽기 전용) |

### 2.4 Public API Exports (`dli/__init__.py`)

```python
from dli import (
    # Version
    __version__,  # "0.2.0"
    # API Classes
    DatasetAPI, MetricAPI, TranspileAPI, CatalogAPI, ConfigAPI,
    # Context
    ExecutionContext,
    # Exceptions
    DLIError, ErrorCode, ConfigurationError, DLIValidationError,
    ExecutionError, DatasetNotFoundError, MetricNotFoundError,
    TranspileError, ServerError, TableNotFoundError, WorkflowNotFoundError,
)
```

---

## 3. Usage Examples

### 3.1 Airflow PythonOperator

```python
from airflow.decorators import task
from dli import DatasetAPI, ExecutionContext

@task
def run_dataset(dataset_name: str, execution_date: str) -> dict:
    ctx = ExecutionContext(
        project_path="/opt/airflow/dags/models",
        server_url="https://basecamp.example.com",
        parameters={"execution_date": execution_date},
    )
    api = DatasetAPI(context=ctx)
    result = api.run(dataset_name)
    return result.model_dump()
```

### 3.2 Basecamp Parser Integration

```python
from dli import TranspileAPI, ExecutionContext

def transpile_sql(sql: str, dialect: str = "trino") -> dict:
    ctx = ExecutionContext(server_url="http://basecamp-server:8080")
    api = TranspileAPI(context=ctx)
    result = api.transpile(sql, source_dialect=dialect, target_dialect=dialect)
    return result.model_dump()
```

### 3.3 Environment Variable Configuration

```bash
# .env or environment
export DLI_SERVER_URL="https://basecamp.example.com"
export DLI_PROJECT_PATH="/path/to/models"
export DLI_MOCK_MODE="true"
export DLI_DIALECT="trino"
```

```python
# Auto-loads from environment
from dli import DatasetAPI, ExecutionContext

ctx = ExecutionContext()  # Loads DLI_* vars automatically
api = DatasetAPI(context=ctx)
```

---

## 4. Test Coverage

### 4.1 Test Files Created

| File | Tests | Coverage |
|------|-------|----------|
| `tests/api/__init__.py` | - | Module init |
| `tests/api/test_common.py` | 66 | Models + Context |
| `tests/api/test_exceptions.py` | 49 | Exception hierarchy |
| `tests/api/test_dataset_api.py` | 35 | DatasetAPI |
| `tests/api/test_metric_api.py` | 35 | MetricAPI |
| `tests/api/test_transpile_api.py` | 28 | TranspileAPI |
| `tests/api/test_catalog_api.py` | 30 | CatalogAPI |
| `tests/api/test_config_api.py` | 27 | ConfigAPI |
| **Total** | **252** | - |

### 4.2 Test Results

```bash
# All tests pass
uv run pytest tests/api/ -v
# ========================= 252 passed =========================

# Type check
uv run pyright tests/api/
# 0 errors, 0 warnings

# Lint
uv run ruff check tests/api/
# All checks passed!
```

---

## 5. Code Review Summary (expert-python)

### 5.1 Issues Fixed

| File | Issue | Fix |
|------|-------|-----|
| `exceptions.py` | `Exception.args` not initialized | Added `__post_init__` with `super().__init__()` |
| `models/common.py` | Missing `TypeAlias` annotation | Added explicit `TypeAlias` imports |
| `api/transpile.py` | Confusing import alias | Removed `as DLITranspileError` alias |
| `api/dataset.py` | `Any` type overuse | Applied `TYPE_CHECKING` pattern |
| `api/metric.py` | Same as dataset.py | Applied `TYPE_CHECKING` pattern |
| Tests | Python 3.11+ enum str change | Use `.value` instead of `str()` |

### 5.2 Review Assessment

| Criteria | Status | Notes |
|----------|--------|-------|
| Type Safety | IMPROVED | Specific types instead of `Any` |
| Pydantic Patterns | GOOD | `ConfigDict`, `Field` used correctly |
| Exception Hierarchy | FIXED | `Exception.args` now initialized |
| DRY Principle | OK | API structure similar but domain-separated |
| Python 3.12+ | IMPROVED | Explicit `TypeAlias` usage |
| Import Structure | FIXED | Confusing aliases removed |
| Docstrings | EXCELLENT | All public APIs documented |

---

## 6. Dependencies Added

```toml
# pyproject.toml
dependencies = [
    "pydantic-settings>=2.0.0",  # NEW: for ExecutionContext
    # ... existing deps
]
```

---

## 7. File Structure

```
project-interface-cli/src/dli/
├── __init__.py          # UPDATED: Public exports (v0.2.0)
├── exceptions.py        # NEW: DLIError hierarchy
├── models/
│   ├── __init__.py      # NEW: Model exports
│   └── common.py        # NEW: ExecutionContext, Results
└── api/
    ├── __init__.py      # NEW: API exports
    ├── dataset.py       # NEW: DatasetAPI
    ├── metric.py        # NEW: MetricAPI
    ├── transpile.py     # NEW: TranspileAPI
    ├── catalog.py       # NEW: CatalogAPI
    └── config.py        # NEW: ConfigAPI

project-interface-cli/tests/api/
├── __init__.py          # NEW
├── test_common.py       # NEW: 66 tests
├── test_exceptions.py   # NEW: 49 tests
├── test_dataset_api.py  # NEW: 35 tests
├── test_metric_api.py   # NEW: 35 tests
├── test_transpile_api.py# NEW: 28 tests
├── test_catalog_api.py  # NEW: 30 tests
└── test_config_api.py   # NEW: 27 tests
```

---

## 8. Future Work (P1-P2)

| Priority | Feature | Description |
|----------|---------|-------------|
| P1 | QualityAPI | 데이터 품질 테스트 API |
| P1 | WorkflowAPI | Workflow 관리 API |
| P1 | LineageAPI | 리니지 조회 API |
| P2 | Async Support | `asyncio` 기반 비동기 API |
| P2 | Connection Pooling | Server 연결 풀링 |
| P2 | Retry Logic | 자동 재시도 로직 |

---

## 9. Migration Guide

### From CLI-only to Library

```python
# Before (CLI only)
# $ dli dataset run my_dataset --param date=2025-01-01

# After (Library)
from dli import DatasetAPI

api = DatasetAPI()
result = api.run("my_dataset", parameters={"date": "2025-01-01"})
```

---

**Last Updated:** 2025-12-30
**Implemented By:** feature-interface-cli Agent
**Reviewed By:** expert-python Agent
