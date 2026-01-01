# project-interface-cli Implementation Status

> **Auto-generated:** 2026-01-01
> **Version:** 0.7.0

---

## Quick Status

| Area | Status | Latest |
|------|--------|--------|
| Library API | ✅ v0.7.0 | ConfigAPI extensions (get_all, validate, get_environment) |
| CLI Commands | ✅ v0.7.0 | `dli config` extended (show --show-source, validate, env, init, set) |
| Environment | ✅ v1.0.0 | ConfigLoader, template resolution, layer priority |
| Quality | ✅ v0.3.0 | list, get, run, validate |
| Workflow | ✅ v0.4.0 | WorkflowAPI (11 methods) |
| Lineage | ✅ v0.4.3 | LineageAPI (3 methods) |
| Query | ✅ v1.0.0 | QueryAPI (3 methods) |
| Run | ✅ v1.0.0 | RunAPI (3 methods) |
| Tests | ✅ ~2300 passed | pyright 0 errors |

---

## Core Components

### Execution Model (v0.2.1)

| Component | File | Status | Notes |
|-----------|------|--------|-------|
| ExecutionMode enum | `models/common.py` | ✅ Complete | LOCAL, SERVER, MOCK |
| ExecutionContext | `models/common.py` | ✅ Complete | execution_mode, timeout 필드 |
| mock_mode deprecation | `models/common.py` | ✅ Complete | DeprecationWarning + validator |
| QueryExecutor Protocol | `core/executor.py` | ✅ Complete | DI용 인터페이스 |
| ExecutorFactory | `core/executor.py` | ✅ Complete | 모드별 Executor 생성 |
| ServerExecutor | `core/executor.py` | ⏳ Stub | Phase 2에서 완전 구현 |
| BigQueryExecutor | `adapters/bigquery.py` | ✅ Complete | 실제 BigQuery 연동 |

### Library API (v0.7.0)

| API Class | File | Status | DI Support |
|-----------|------|--------|------------|
| DatasetAPI | `api/dataset.py` | ✅ Complete | ✅ executor 파라미터 |
| MetricAPI | `api/metric.py` | ✅ Complete | ✅ executor 파라미터 |
| TranspileAPI | `api/transpile.py` | ✅ Complete | - |
| CatalogAPI | `api/catalog.py` | ✅ Complete | - |
| ConfigAPI | `api/config.py` | ✅ Extended | get_all, get_with_source, validate, list_environments, get_environment |
| QualityAPI | `api/quality.py` | ✅ Complete | - |
| WorkflowAPI | `api/workflow.py` | ✅ Complete | ✅ client 파라미터 |
| LineageAPI | `api/lineage.py` | ✅ Complete | ✅ client 파라미터 |
| QueryAPI | `api/query.py` | ✅ Complete | ✅ client 파라미터 |
| RunAPI | `api/run.py` | ✅ Complete | ✅ executor 파라미터 |

### CLI Commands (v0.7.0)

| Command | File | Status |
|---------|------|--------|
| dli version/info | `commands/version.py`, `info.py` | ✅ Complete |
| dli config | `commands/config.py` | ✅ Extended (show --show-source, validate, env, init, set) |
| dli metric | `commands/metric.py` | ✅ Complete |
| dli dataset | `commands/dataset.py` | ✅ Complete |
| dli workflow | `commands/workflow.py` | ✅ Complete |
| dli catalog | `commands/catalog.py` | ✅ Complete |
| dli transpile | `commands/transpile.py` | ✅ Complete |
| dli lineage | `commands/lineage.py` | ✅ Complete |
| dli quality | `commands/quality.py` | ✅ Complete (list, get, run, validate) |
| dli query | `commands/query.py` | ✅ Complete |
| dli run | `commands/run.py` | ✅ Complete |

---

## Error Codes

| Code Range | Category | Latest Code | Status |
|------------|----------|-------------|--------|
| DLI-0xx | Configuration | DLI-007 | ✅ Extended (001-007) |
| DLI-1xx | Not Found | DLI-104 | ✅ Complete |
| DLI-2xx | Validation | DLI-204 | ✅ Complete |
| DLI-3xx | Transpile | DLI-303 | ✅ Complete |
| DLI-4xx | Execution | DLI-405 | ✅ Complete |
| DLI-5xx | Server | DLI-504 | ✅ Complete |
| DLI-6xx | Quality | DLI-606 | ✅ Complete (601-606) |
| DLI-7xx | Catalog | DLI-784 | ✅ Complete (701-706, 780-784 Query) |
| DLI-8xx | Workflow | DLI-803 | ✅ Complete (800-803) |
| DLI-9xx | Lineage | DLI-904 | ✅ Complete (900-904) |
| DLI-41x | Run | DLI-416 | ✅ Complete (410-416) |

### Configuration Error Codes (DLI-00x Extended)

| Code | Name | Description |
|------|------|-------------|
| DLI-001 | CONFIG_NOT_FOUND | Configuration file not found |
| DLI-002 | CONFIG_INVALID | Invalid configuration syntax |
| DLI-003 | CONFIG_MISSING_REQUIRED | Required configuration missing |
| DLI-004 | CONFIG_ENV_NOT_FOUND | Named environment not found |
| DLI-005 | CONFIG_TEMPLATE_ERROR | Template resolution failed |
| DLI-006 | CONFIG_VALIDATION_FAILED | Configuration validation failed |
| DLI-007 | CONFIG_WRITE_FAILED | Failed to write configuration |

---

## Test Coverage

| Category | Tests | Status |
|----------|-------|--------|
| API Tests | ~507 (+80 ConfigAPI ext) | ✅ All pass |
| CLI Tests | ~948 (+70 Config ext) | ✅ All pass |
| Core Tests | ~716 (+100 ConfigLoader, +50 Config models) | ✅ All pass |
| **Total** | **~2300** | ✅ All pass |

---

## Documentation

| Document | Status | Location |
|----------|--------|----------|
| README.md | ✅ Updated | `project-interface-cli/README.md` |
| CONTRIBUTION.md | ✅ Created | `project-interface-cli/CONTRIBUTION.md` |
| PATTERNS.md | ✅ Updated | `project-interface-cli/docs/PATTERNS.md` |
| EXECUTION_RELEASE.md | ✅ Created | `project-interface-cli/features/EXECUTION_RELEASE.md` |
| QUALITY_RELEASE.md | ✅ Created | `project-interface-cli/features/QUALITY_RELEASE.md` |
| WORKFLOW_RELEASE.md | ✅ Updated | `project-interface-cli/features/WORKFLOW_RELEASE.md` |
| CATALOG_FEATURE.md | ✅ Created | `project-interface-cli/features/CATALOG_FEATURE.md` |
| CATALOG_RELEASE.md | ✅ Created | `project-interface-cli/features/CATALOG_RELEASE.md` |
| CATALOG_GAP.md | ✅ Created | `project-interface-cli/features/CATALOG_GAP.md` |
| LINEAGE_FEATURE.md | ✅ Created | `project-interface-cli/features/LINEAGE_FEATURE.md` |
| LINEAGE_RELEASE.md | ✅ Created | `project-interface-cli/features/LINEAGE_RELEASE.md` |
| LINEAGE_GAP.md | ✅ Created | `project-interface-cli/features/LINEAGE_GAP.md` |
| QUERY_FEATURE.md | ✅ Created | `project-interface-cli/features/QUERY_FEATURE.md` |
| QUERY_RELEASE.md | ✅ Created | `project-interface-cli/features/QUERY_RELEASE.md` |
| RUN_FEATURE.md | ✅ Updated | `project-interface-cli/features/RUN_FEATURE.md` |
| RUN_RELEASE.md | ✅ Created | `project-interface-cli/features/RUN_RELEASE.md` |
| ENV_FEATURE.md | ✅ Updated | `project-interface-cli/features/ENV_FEATURE.md` |
| ENV_RELEASE.md | ✅ Created | `project-interface-cli/features/ENV_RELEASE.md` |

---

## Related Documents

- [EXECUTION_REFACTOR.md](./EXECUTION_REFACTOR.md) - Execution Model 스펙
- [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) - 구현 상세
- [LIBRARY_RELEASE.md](./LIBRARY_RELEASE.md) - Library API 구현 상세
- [../docs/PATTERNS.md](../docs/PATTERNS.md) - 개발 패턴

---

## Changelog

### v0.7.0 (2026-01-01)
- **Environment Feature 구현 완료**
  - ConfigLoader: 계층적 설정 로딩 (global < project < local < env < cli)
  - 템플릿 해석: `${VAR}`, `${VAR:-default}`, `${VAR:?error}` 문법 지원
  - Secret 탐지: `DLI_SECRET_*` 접두사 및 키 이름 패턴 자동 마스킹
  - Source 추적: 모든 설정 값의 출처 표시
- **ConfigAPI 확장** (`api/config.py`)
  - get_all(), get_with_source(), validate(), list_environments(), get_environment()
  - get_active_environment(), get_all_with_sources()
- **ExecutionContext.from_environment() 팩토리 메서드**
  - 설정 파일 기반 컨텍스트 생성
  - 관대한 동작 (missing config에 예외 발생 안함)
  - 환경별 오버라이드 지원
- **CLI 커맨드 확장**
  - `dli config show --show-source`: 값 출처 표시
  - `dli config validate --strict`: 엄격 검증 모드
  - `dli config env`: 환경 목록/전환
  - `dli config init`: 설정 파일 초기화
  - `dli config set`: 설정 값 저장
- **DLI-00x 에러 코드 확장**
  - DLI-004: CONFIG_ENV_NOT_FOUND
  - DLI-005: CONFIG_TEMPLATE_ERROR
  - DLI-006: CONFIG_VALIDATION_FAILED
  - DLI-007: CONFIG_WRITE_FAILED
- **Config 모델 추가**
  - models/config.py: ConfigSource, ConfigValueInfo, ConfigValidationResult, EnvironmentProfile
  - core/config_loader.py: ConfigLoader 클래스
- **~300개 신규 테스트 추가**
  - tests/core/test_config_loader.py (~100 tests)
  - tests/models/test_config.py (~50 tests)
  - tests/api/test_config_api_ext.py (~80 tests)
  - tests/cli/test_config_cmd_ext.py (~70 tests)

### v0.6.0 (2026-01-01)
- **Run Command 구현 완료**
  - `dli run` - Ad-hoc SQL 파일 실행 및 결과 다운로드
  - CSV, TSV, JSON (JSONL) 출력 형식 지원
  - `--local` / `--server` 실행 모드 선택
  - `--param key=value` Jinja 스타일 파라미터 치환
  - `--dry-run` 실행 계획 미리보기
- **RunAPI 구현** (`api/run.py`)
  - run(), dry_run(), render_sql()
  - MOCK/SERVER/LOCAL 모드 지원, DI 지원 (executor 파라미터)
- **DLI-41x Run 에러 코드 추가**
  - DLI-410: RUN_FILE_NOT_FOUND
  - DLI-411: RUN_LOCAL_DENIED
  - DLI-412: RUN_SERVER_UNAVAILABLE
  - DLI-413: RUN_EXECUTION_FAILED
  - DLI-414: RUN_OUTPUT_FAILED
  - DLI-415: RUN_TIMEOUT
  - DLI-416: RUN_INVALID_PARAM
- **Run Exception 클래스 7종**
  - RunFileNotFoundError, RunLocalDeniedError, RunServerUnavailableError
  - RunExecutionError, RunOutputError, RunTimeoutError, RunInvalidParamError
- **Run 모델 추가**
  - models/run.py: OutputFormat, RunResult, ExecutionPlan
  - core/run/models.py: RunConfig, ExecutionData
- **~120개 신규 테스트 추가**
  - tests/cli/test_run_cmd.py (~50 tests)
  - tests/api/test_run_api.py (~40 tests)
  - tests/core/run/test_models.py (~30 tests)
- **dli/__init__.py Export 업데이트**
  - RunAPI, Run 관련 Exception 클래스들

### v0.5.0 (2026-01-01)
- **Query Command 구현 완료**
  - `dli query list` - Scope 기반 쿼리 메타데이터 조회 (my/system/user/all)
  - `dli query show` - 쿼리 상세 정보 조회
  - `dli query cancel` - 쿼리 취소 (ID 또는 --user 계정)
- **QueryAPI 구현** (`api/query.py`)
  - list_queries(), get(), get_result(), cancel()
  - MOCK/SERVER 모드 지원, DI 지원 (client 파라미터)
- **DLI-78x Query 에러 코드 추가**
  - DLI-780: QUERY_NOT_FOUND
  - DLI-781: QUERY_ACCESS_DENIED
  - DLI-782: QUERY_CANCEL_FAILED
  - DLI-783: QUERY_INVALID_FILTER
  - DLI-784: QUERY_SERVER_ERROR
- **Query Exception 클래스 4종**
  - QueryNotFoundError, QueryAccessDeniedError
  - QueryCancelError, QueryInvalidFilterError
- **Query 모델 추가**
  - core/query/models.py: AccountType, QueryScope, QueryState, TableReference, QueryInfo, QueryDetail
  - models/query.py: QueryListResult, QueryDetailResult, QueryCancelResult
- **170개 신규 테스트 추가**
  - tests/cli/test_query_cmd.py (50 tests)
  - tests/api/test_query_api.py (54 tests)
  - tests/core/query/test_models.py (66 tests)
- **dli/__init__.py Export 업데이트**
  - QueryAPI, QueryNotFoundError, QueryAccessDeniedError, QueryCancelError, QueryInvalidFilterError

### v0.4.3 (2026-01-01)
- **LineageAPI 구현 완료**
  - `api/lineage.py` (367 lines): get_lineage, get_upstream, get_downstream
  - MOCK/SERVER 모드 지원, DI 지원 (client 파라미터)
  - WorkflowAPI 패턴 기반 구현
- **DLI-9xx Lineage 에러 코드 추가**
  - DLI-900: LINEAGE_NOT_FOUND
  - DLI-901: LINEAGE_DEPTH_EXCEEDED
  - DLI-902: LINEAGE_CYCLE_DETECTED
  - DLI-903: LINEAGE_SERVER_ERROR
  - DLI-904: LINEAGE_TIMEOUT
- **Lineage Exception 클래스 3종**
  - LineageError (base)
  - LineageNotFoundError
  - LineageTimeoutError
- **43개 신규 테스트 추가**
  - `tests/api/test_lineage_api.py`
  - 11 테스트 클래스, 모든 API 메서드 검증
- **dli/__init__.py Export 업데이트**
  - LineageAPI, LineageError, LineageNotFoundError, LineageTimeoutError

### v0.4.2 (2026-01-01)
- **Lineage 문서화 완료**
  - LINEAGE_FEATURE.md: 기능 명세 (~968 lines, Phase 1/2 설계)
  - LINEAGE_RELEASE.md: 구현 상세 (~399 lines, CLI/Models/Client)
  - LINEAGE_GAP.md: Gap 분석 (58% 완성도, 9개 Gap 식별)
- **Agent/Skill 시스템 개선**
  - `api-parity` 스킬 신규 생성 (CLI-API 패리티 검증)
  - `completion-gate` 스킬 강화 (+3 체크: API Parity, Exception, Phase Gate)
  - `docs-synchronize` 스킬 강화 (+3 기능: Test Count, API Export, Changelog)
  - `implementation-checklist` 스킬 강화 (+3 체크: Exception, API-CLI, Test Files)
- **P0 Gap 식별** (향후 구현 필요)
  - GAP-L01: LineageAPI 클래스 누락 (Critical, ~4시간)
  - GAP-L02: DLI-9xx 에러 코드 누락 (High, ~1시간)

### v0.4.1 (2026-01-01)
- **Transpile P0 Gap Resolution**
  - GAP-T03: Jinja Integration - `_render_jinja()` 메서드, `jinja_context` 파라미터
  - GAP-T11: `--transpile-retry` CLI 옵션 (0-5 범위)
  - `TranspileConfig.enable_jinja: bool = True` 옵션
  - 15개 신규 테스트 (Jinja 8 + Retry 7)
- TranspileEngine v1.1.0
  - `transpile(sql, context, jinja_context)` 시그니처 확장
  - dbt/SQLMesh 호환 템플릿: `{{ ds }}`, `{{ ref() }}`, `{{ var() }}`
- 문서 동기화
  - TRANSPILE_FEATURE.md: Status → "Implemented (P0 Complete)"
  - TRANSPILE_GAP.md: GAP-T03, GAP-T11 → ✅ Resolved

### v0.4.0 (2025-12-31)
- **Catalog 커맨드 v1.2.0 통합**
  - 암시적 라우팅 (1/2/3/4-part 식별자)
  - `catalog list`, `catalog search` 서브커맨드
  - Rich/JSON 출력, Mock 모드
  - 114개 Catalog 테스트 (Model 54 + CLI 30 + API 30)
- **CatalogAPI v1.2.0** (list_tables, get, search)
  - Result 모델: CatalogListResult, TableDetailResult, CatalogSearchResult
- **DLI-7xx 에러 코드 (701-706)**
  - CatalogAccessDeniedError (DLI-704) 추가
  - CatalogSchemaError (DLI-706) 추가
- WorkflowAPI 구현 (get, register, unregister, run, backfill, stop, get_status, list_workflows, history, pause, unpause)
- Workflow Result 모델 (WorkflowRegisterResult, WorkflowRunResult, WorkflowListResult, WorkflowStatusResult, WorkflowHistoryResult)
- DLI-8xx 에러 코드 (800-803)
- Workflow Exception 클래스 4종 (WorkflowNotFoundError, WorkflowRegistrationError, WorkflowExecutionError, WorkflowPermissionError)
- BasecampClient 확장 (workflow_register, workflow_unregister)
- 59개 Workflow 신규 테스트

### v0.3.0 (2025-12-31)
- QualityAPI 구현 (list_qualities, get, run, validate)
- QualitySpec Pydantic 모델 (YAML 파싱용)
- CLI 커맨드 업데이트 (list, get, run, validate - show 제거)
- DLI-6xx 에러 코드 (601-606)
- 47개 신규 테스트 (API 19 + CLI 28)
- registry.py 삭제, QualityRegistry 제거

### v0.2.1 (2025-12-30)
- ExecutionMode enum 추가 (LOCAL, SERVER, MOCK)
- ExecutionContext에 execution_mode, timeout 필드 추가
- mock_mode deprecated (자동 마이그레이션)
- QueryExecutor Protocol 및 ExecutorFactory 추가
- DatasetAPI, MetricAPI에 DI 지원 추가
- 에러 코드 확장 (DLI-404, 405, 504)
- 65개 신규 테스트 추가

### v0.2.0 (2025-12-29)
- Library API (DatasetAPI, MetricAPI, TranspileAPI, CatalogAPI, ConfigAPI)
- 252개 API 테스트
