# RELEASE: Execution Model Implementation

> **Version:** 2.0.0
> **Status:** Phase 2 Complete
> **Last Updated:** 2026-01-08

---

## Implementation Summary

EXECUTION_REFACTOR.md 사양에 따른 Execution Model Phase 1 & Phase 2 구현이 완료되었습니다.

### Implementation Status

| Phase | Component | Status | Notes |
|-------|-----------|--------|-------|
| Phase 1 | ExecutionMode enum | ✅ Complete | LOCAL, SERVER, MOCK, **REMOTE** |
| Phase 1 | ExecutionContext 확장 | ✅ Complete | execution_mode 필드, timeout 필드 |
| Phase 1 | mock_mode 마이그레이션 | ✅ Complete | DeprecationWarning + model_validator |
| Phase 1 | QueryExecutor Protocol | ✅ Complete | DI용 인터페이스 |
| Phase 1 | ExecutorFactory | ✅ Complete | 모드별 Executor 생성 (BigQuery, Trino 지원) |
| Phase 1 | DatasetAPI DI 지원 | ✅ Complete | executor 파라미터 |
| Phase 1 | MetricAPI DI 지원 | ✅ Complete | executor 파라미터 |
| Phase 1 | 에러 코드 확장 | ✅ Complete | DLI-404, 405, 504 |
| Phase 1 | 테스트 | ✅ Complete | 65개 신규 테스트 |
| **Phase 2** | **TrinoExecutor 구현** | ✅ Complete | OIDC 인증, Trino 쿼리 실행 |
| **Phase 2** | **ServerExecutor 구현** | ✅ Complete | Basecamp API 연동 |
| **Phase 2** | **CLI --local/--server/--remote** | ✅ Complete | 4개 실행 커맨드에 적용 |
| **Phase 2** | **ExecutionConfig 모델** | ✅ Complete | Config YAML execution 섹션 |
| **Phase 2** | **quality run 통일** | ✅ Complete | ⚠️ Breaking: --mode → --local/--server/--remote |

---

## Modified Files

### Core Implementation (Phase 1)

| File | Changes |
|------|---------|
| `src/dli/models/common.py` | ExecutionMode enum, ExecutionContext 확장 |
| `src/dli/exceptions.py` | DLI-404, DLI-405, DLI-504 에러 코드 |
| `src/dli/core/executor.py` | QueryExecutor Protocol, ExecutorFactory, ServerExecutor |
| `src/dli/api/dataset.py` | executor DI, _is_mock_mode property |
| `src/dli/api/metric.py` | executor DI, _is_mock_mode property |
| `src/dli/api/transpile.py` | _is_mock_mode property |
| `src/dli/api/config.py` | _is_mock_mode property |
| `src/dli/api/catalog.py` | _is_mock_mode property |
| `src/dli/api/__init__.py` | docstring 예제 업데이트 |

### Phase 2 Implementation

| File | Changes |
|------|---------|
| `src/dli/adapters/trino.py` | **신규** - TrinoExecutor (OIDC 인증, 쿼리 실행) |
| `src/dli/core/executor.py` | ExecutorFactory에 trino dialect 지원 추가 |
| `src/dli/models/common.py` | ExecutionMode.REMOTE enum 값 추가 |
| `src/dli/models/config.py` | **신규** - ExecutionConfig 모델 (execution 섹션) |
| `src/dli/core/client.py` | ServerExecutor Basecamp API 연동 구현 |
| `src/dli/commands/dataset.py` | --local/--server/--remote 옵션 추가 |
| `src/dli/commands/metric.py` | --local/--server/--remote 옵션 추가 |
| `src/dli/commands/quality.py` | ⚠️ **Breaking**: --mode → --local/--server/--remote |
| `src/dli/commands/run.py` | --local/--server/--remote 옵션 추가 |

### Test Files (Phase 1)

| File | Changes |
|------|---------|
| `tests/api/test_common.py` | ExecutionMode, ExecutionContext 테스트 |
| `tests/core/test_executor_factory.py` | 신규 - ExecutorFactory 테스트 |
| `tests/api/test_api_di.py` | 신규 - DI 주입 테스트 |
| `tests/api/test_dataset_api.py` | execution_mode 마이그레이션 |
| `tests/api/test_metric_api.py` | execution_mode 마이그레이션 |
| `tests/api/test_transpile_api.py` | execution_mode 마이그레이션 |
| `tests/api/test_catalog_api.py` | execution_mode 마이그레이션 |
| `tests/api/test_config_api.py` | execution_mode 마이그레이션 |

### Test Files (Phase 2)

| File | Changes |
|------|---------|
| `tests/adapters/test_trino_executor.py` | 신규 - TrinoExecutor 테스트 |
| `tests/core/test_client.py` | ServerExecutor API 연동 테스트 |
| `tests/cli/test_execution_options.py` | CLI --local/--server/--remote 테스트 |

---

## Test Results

```
====================== 2645 passed, 35 skipped =======================
pyright: 0 errors, 0 warnings
```

---

## Migration Guide

### Phase 1: mock_mode → execution_mode

#### 기존 코드 (Deprecated)

```python
from dli import ExecutionContext

# mock_mode=True 사용 시 DeprecationWarning 발생
ctx = ExecutionContext(mock_mode=True)
```

#### 신규 코드 (Recommended)

```python
from dli import ExecutionContext, ExecutionMode

# Mock 모드 (테스트용)
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

# 로컬 실행 (BigQuery 직접 연결)
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    dialect="bigquery",
    project_path=Path("/path/to/project"),
)

# 서버 실행 (Basecamp API)
ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="https://basecamp.example.com",
    api_token="your-token",
)

# 원격 실행 (Remote Query Engine via Server)
ctx = ExecutionContext(
    execution_mode=ExecutionMode.REMOTE,
    server_url="https://basecamp.example.com",
    api_token="your-token",
)
```

### Phase 2: CLI Breaking Change (quality run --mode)

#### ⚠️ Breaking Change: quality run 커맨드

```bash
# 기존 (v1.x) - 더 이상 지원 안함
dli quality run my_spec --mode local
dli quality run my_spec --mode server

# 신규 (v2.0.0) - 다른 실행 커맨드와 일관된 패턴
dli quality run my_spec --local
dli quality run my_spec --server
dli quality run my_spec --remote
```

#### CLI 실행 옵션 (모든 실행 커맨드 공통)

| 커맨드 | --local | --server | --remote |
|--------|---------|----------|----------|
| `dli dataset run` | ✅ | ✅ | ✅ |
| `dli metric run` | ✅ | ✅ | ✅ |
| `dli quality run` | ✅ | ✅ | ✅ |
| `dli run` | ✅ | ✅ | ✅ |

### Phase 2: TrinoExecutor 사용

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode

# Trino 로컬 실행
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    dialect="trino",
    project_path=Path("/path/to/project"),
)
api = DatasetAPI(context=ctx)
result = api.run("my_dataset")
```

### Phase 2: Config YAML execution 섹션

```yaml
# dli.yaml
execution:
  mode: local          # local, server, remote
  dialect: trino       # bigquery, trino, snowflake
  timeout: 300         # seconds
```

### DI (Dependency Injection) 사용

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode
from dli.core.executor import MockExecutor

# 테스트 시 Mock executor 주입
mock_executor = MockExecutor(mock_data=[{"id": 1, "name": "test"}])
api = DatasetAPI(
    context=ExecutionContext(execution_mode=ExecutionMode.LOCAL),
    executor=mock_executor,
)
result = api.run("test.schema.dataset")
```

---

## Backward Compatibility

| 기존 사용 | 동작 | 권장 변경 |
|-----------|------|-----------|
| `mock_mode=True` | ✅ 자동 마이그레이션 | `execution_mode=ExecutionMode.MOCK` |
| `mock_mode=False` | ✅ 기본 동작 | `execution_mode=ExecutionMode.LOCAL` |
| `ctx.mock_mode` 접근 | ⚠️ DeprecationWarning | `ctx.execution_mode == ExecutionMode.MOCK` |

---

## Error Codes Added

| Code | Name | Description |
|------|------|-------------|
| DLI-404 | `EXECUTION_PERMISSION` | 쿼리 실행 권한 없음 |
| DLI-405 | `EXECUTION_QUERY` | SQL 실행 오류 |
| DLI-504 | `SERVER_EXECUTION` | 서버 측 실행 오류 |

---

## Architecture Decisions

### QueryExecutor vs BaseExecutor

| Interface | Purpose | Usage |
|-----------|---------|-------|
| `QueryExecutor` (Protocol) | DI용 인터페이스 | API 클래스에서 타입 힌트, Mock 주입 |
| `BaseExecutor` (ABC) | 실제 구현용 | BigQueryExecutor, TrinoExecutor, MockExecutor 상속 |

### ExecutorFactory Pattern

```python
from dli.core.executor import ExecutorFactory, ExecutionMode

# 모드에 따른 적절한 Executor 생성
executor = ExecutorFactory.create(
    mode=ExecutionMode.LOCAL,
    dialect="bigquery",  # or "trino"
    context=ctx,
)
```

### ExecutionMode 설계

| Mode | Description | Use Case |
|------|-------------|----------|
| `LOCAL` | 로컬 Query Engine 직접 실행 | 개발/테스트 환경에서 BigQuery/Trino 직접 연결 |
| `SERVER` | Basecamp Server API 실행 | 프로덕션 환경, 감사 추적 필요 시 |
| `REMOTE` | Server를 통한 원격 엔진 실행 | 로컬 연결 불가능하나 서버 통해 실행 필요 시 |
| `MOCK` | 테스트용 Mock 실행 | 단위 테스트, 네트워크 격리 환경 |

### TrinoExecutor 인증

```python
# OIDC 인증 방식
executor = TrinoExecutor(
    host="trino.example.com",
    port=443,
    catalog="iceberg",
    schema="analytics",
    auth_type="oidc",  # or "jwt", "basic"
)
```

---

## Completed in Phase 2

1. **TrinoExecutor 구현** ✅
   - OIDC 인증 지원
   - 쿼리 실행 및 결과 처리
   - 타임아웃 처리

2. **ServerExecutor 완전 구현** ✅
   - Basecamp API 연동 (`execute_rendered_*` 엔드포인트)
   - 인증 처리 (API Token)
   - 에러 핸들링

3. **CLI 커맨드 확장** ✅
   - `dli dataset run --local/--server/--remote`
   - `dli metric run --local/--server/--remote`
   - `dli quality run --local/--server/--remote`
   - `dli run --local/--server/--remote`

4. **Config YAML execution 섹션** ✅
   - `execution.mode` 설정
   - `execution.dialect` 설정
   - `execution.timeout` 설정

---

## Future Enhancements (Phase 3)

1. **추가 Query Engine 지원**
   - SnowflakeExecutor
   - SparkExecutor

2. **결과 스트리밍**
   - 대용량 결과 처리
   - 진행률 표시

3. **실행 이력 관리**
   - 로컬 캐시
   - 결과 재사용

---

## Review Summary

| 항목 | 평가 | 비고 |
|------|------|------|
| 타입 안전성 | ✅ PASS | Protocol, type hints 올바르게 사용 |
| Pythonic 패턴 | ✅ PASS | PEP 8, match 문, property 적절 사용 |
| pydantic_settings | ✅ PASS | BaseSettings, model_validator 올바름 |
| 테스트 커버리지 | ✅ PASS | 전체 2645개 통과 |
| 역호환성 | ⚠️ BREAKING | quality run --mode → --local/--server/--remote |
| 코드 품질 | ✅ PASS | expert-python Agent 리뷰 통과 |
| TrinoExecutor | ✅ PASS | OIDC 인증, 쿼리 실행 검증 완료 |
| ServerExecutor | ✅ PASS | Basecamp API 연동 검증 완료 |
| CLI 옵션 일관성 | ✅ PASS | 4개 실행 커맨드 동일 패턴 |
