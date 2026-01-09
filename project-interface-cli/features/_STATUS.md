# project-interface-cli Implementation Status

> **Auto-generated:** 2026-01-10
> **Version:** 1.0.5

---

## Quick Status

| Area | Status | Latest |
|------|--------|--------|
| **Audit** | **✅ v1.0.0** | **TraceContext, TracedHttpClient, @with_trace (72 tests)** |
| **Integration Testing** | **✅ v1.1.0** | **Trino integration + RunAPI tests (~65 tests)** |
| **Execution Model** | **✅ v2.0.0** | **TrinoExecutor, CLI --local/--server/--remote, REMOTE mode** |
| **Execution API** | **✅ v0.9.1** | **Server Execution API integration (4 endpoints)** |
| Library API | ✅ v0.9.0 | DatasetAPI/MetricAPI.format() |
| CLI Commands | ✅ v0.9.0 | `dli dataset format`, `dli metric format` |
| **Format** | **✅ v0.9.0** | **SqlFormatter, YamlFormatter, DLI-15xx** |
| Debug | ✅ v0.8.0 | DebugAPI, 12 checks, 196 tests, DLI-95x |
| Environment | ✅ v0.7.0 | ConfigLoader, template resolution, layer priority |
| Quality | ✅ v0.3.0 | list, get, run, validate |
| Workflow | ✅ v0.4.0 | WorkflowAPI (11 methods) |
| Lineage | ✅ v1.1.0 | LineageAPI (3 methods), 60 tests (CLI 17 + API 43) |
| Query | ✅ v1.0.0 | QueryAPI (3 methods) |
| Run | ✅ v1.0.0 | RunAPI (3 methods) |
| **SQL** | **✅ v1.0.0** | **SqlAPI (3 methods), 87 tests, DLI-79x** |
| Tests | ✅ ~2870 passed | pyright 0 errors, ~65 integration tests |

---

## Core Components

### Execution Model (v2.0.0)

| Component | File | Status | Notes |
|-----------|------|--------|-------|
| ExecutionMode enum | `models/common.py` | ✅ Complete | LOCAL, SERVER, MOCK, **REMOTE** |
| ExecutionContext | `models/common.py` | ✅ Complete | execution_mode, timeout 필드 |
| mock_mode deprecation | `models/common.py` | ✅ Complete | DeprecationWarning + validator |
| QueryExecutor Protocol | `core/executor.py` | ✅ Complete | DI용 인터페이스 |
| ExecutorFactory | `core/executor.py` | ✅ Complete | 모드별 Executor 생성 (**Trino 지원**) |
| **BasecampClient Execution** | `core/client.py` | ✅ Complete | 4 Execution API 메서드 |
| BigQueryExecutor | `adapters/bigquery.py` | ✅ Complete | 실제 BigQuery 연동 |
| **TrinoExecutor** | `adapters/trino.py` | ✅ Complete | **OIDC 인증, Trino 쿼리 실행** |
| **ExecutionConfig** | `models/config.py` | ✅ Complete | **Config YAML execution 섹션** |
| **ServerExecutor** | `core/executor.py` | ✅ Complete | **Basecamp API 연동** |
| **CLI --local/--server/--remote** | `commands/*.py` | ✅ Complete | **4개 실행 커맨드에 적용** |

#### Server Execution API Integration (v0.9.1)

| API Method | Endpoint | Description |
|------------|----------|-------------|
| `execute_rendered_dataset()` | POST /api/v1/execution/datasets/run | Dataset SQL 실행 |
| `execute_rendered_metric()` | POST /api/v1/execution/metrics/run | Metric SQL 실행 |
| `execute_rendered_quality()` | POST /api/v1/execution/quality/run | Quality Test SQL 실행 |
| `execute_rendered_sql()` | POST /api/v1/execution/sql/run | Ad-hoc SQL 실행 |

**Execution Flow:**
- CLI renders SQL locally (SQLGlot transpilation)
- Both LOCAL and SERVER modes call Server Execution API
- Server executes pre-rendered SQL against Query Engine

### Integration Testing (v1.1.0 Standard)

| Component | File | Status | Notes |
|-----------|------|--------|-------|
| Docker Compose | `tests/integration/docker-compose.trino.yaml` | ✅ Complete | Trino 467 + memory catalog |
| Memory Catalog | `tests/integration/trino-config/catalog/memory.properties` | ✅ Complete | 256MB data per node |
| pytest-docker fixtures | `tests/integration/conftest.py` | ✅ Complete | trino_service, trino_executor, trino_test_schema |
| TrinoExecutor Tests | `tests/integration/test_trino_integration.py` | ✅ Complete | Connection, query, schema, dry_run, error handling |
| Spec Execution Tests | `tests/integration/test_spec_execution_integration.py` | ✅ Complete | Dataset/Metric/Quality SQL rendering + Trino execution |
| **RunAPI Tests** | `tests/integration/test_run_integration.py` | ✅ Complete | **Ad-hoc SQL execution + output formats** |
| GitHub Actions CI | `.github/workflows/interface-cli-ci.yml` | ✅ Complete | integration-test job (main push / manual trigger) |
| TESTING.md | `docs/TESTING.md` | ✅ Complete | Local dev setup (uv, pyenv-virtualenv) |
| TEST_RELEASE.md | `features/TEST_RELEASE.md` | ✅ Complete | MVP + Standard roadmap |

#### Test Categories (v1.0.0 MVP)

| Category | Count | Description |
|----------|-------|-------------|
| Trino Connection | 9 | Connection, query, columns, timing |
| Schema Operations | 3 | Create schema, table, insert/select |
| Query Execution | 4 | Aggregation, join, filter, date functions |
| Dry Run | 3 | EXPLAIN validation |
| Error Handling | 3 | Non-existent table, syntax, division |
| Table Schema | 2 | Schema inspection |
| Dataset Spec | 4 | SQL rendering and execution |
| Metric Spec | 2 | Metric calculation validation |
| Quality Spec | 4 | NOT NULL, UNIQUE, accepted_values |
| Complex Queries | 4 | Window, CTE, subquery, CASE |
| **v1.0.0 Total** | **~38** | |

#### Test Categories (v1.1.0 Standard - NEW)

| Category | Count | Description |
|----------|-------|-------------|
| RunAPI Basic Execution | 3 | Simple SELECT, multi-column, table data |
| RunAPI Output Formats | 3 | CSV, JSON, TSV format validation |
| RunAPI Parameters | 2 | Parameter substitution, render_sql |
| RunAPI Row Limiting | 1 | Limit option |
| RunAPI Complex Queries | 2 | Aggregation, JOIN |
| RunAPI dry-run | 3 | EXPLAIN, parameters, execution mode |
| RunAPI Error Handling | 5 | File not found, syntax, table, column, division |
| RunAPI Advanced | 4 | CTE, Window, Subquery, CASE |
| RunAPI Result Validation | 4 | Rendered SQL, duration, mode, empty result |
| **v1.1.0 Total** | **~27** | |

#### Combined Total: **~65 integration tests**

### Library API (v0.9.1)

| API Class | File | Status | Execution API |
|-----------|------|--------|---------------|
| DatasetAPI | `api/dataset.py` | ✅ Complete | ✅ execute_rendered_dataset() |
| MetricAPI | `api/metric.py` | ✅ Complete | ✅ execute_rendered_metric() |
| TranspileAPI | `api/transpile.py` | ✅ Complete | - |
| CatalogAPI | `api/catalog.py` | ✅ Complete | - |
| ConfigAPI | `api/config.py` | ✅ Extended | - |
| QualityAPI | `api/quality.py` | ✅ Complete | ✅ execute_rendered_quality() |
| WorkflowAPI | `api/workflow.py` | ✅ Complete | - |
| LineageAPI | `api/lineage.py` | ✅ Complete | - |
| QueryAPI | `api/query.py` | ✅ Complete | - |
| RunAPI | `api/run.py` | ✅ Complete | ✅ execute_rendered_sql() |
| DebugAPI | `api/debug.py` | ✅ Complete | - |
| **SqlAPI** | `api/sql.py` | ✅ Complete | - |

### CLI Commands (v0.8.0)

| Command | File | Status |
|---------|------|--------|
| dli version/info | `commands/version.py`, `info.py` | ✅ Complete |
| dli config | `commands/config.py` | ✅ Extended (show --show-source, validate, env, init, set) |
| dli metric | `commands/metric.py` | ✅ Complete (includes `transpile` subcommand) |
| dli dataset | `commands/dataset.py` | ✅ Complete (includes `transpile` subcommand) |
| dli workflow | `commands/workflow.py` | ✅ Complete |
| dli catalog | `commands/catalog.py` | ✅ Complete |
| ~~dli transpile~~ | ~~`commands/transpile.py`~~ | ⚠️ **Removed (v1.2.0)** - moved to dataset/metric subcommands |
| dli lineage | `commands/lineage.py` | ✅ Complete |
| dli quality | `commands/quality.py` | ✅ Complete (list, get, run, validate) |
| dli query | `commands/query.py` | ✅ Complete |
| dli run | `commands/run.py` | ✅ Complete |
| dli debug | `commands/debug.py` | ✅ Complete |
| **dli sql** | `commands/sql.py` | ✅ Complete (list, get, put) |

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
| **DLI-79x** | **SQL** | **DLI-794** | **✅ Complete (790-794)** |
| DLI-8xx | Workflow | DLI-803 | ✅ Complete (800-803) |
| DLI-9xx | Lineage | DLI-904 | ✅ Complete (900-904) |
| DLI-41x | Run | DLI-416 | ✅ Complete (410-416) |
| DLI-95x | Debug | DLI-956 | ✅ Complete (950-956) |
| **DLI-15xx** | **Format** | **DLI-1506** | **✅ Complete (1501-1506)** |

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

### Debug Error Codes (DLI-95x)

| Code | Name | Description |
|------|------|-------------|
| DLI-950 | DEBUG_SYSTEM_CHECK_FAILED | System environment check failed |
| DLI-951 | DEBUG_CONFIG_CHECK_FAILED | Configuration check failed |
| DLI-952 | DEBUG_SERVER_CHECK_FAILED | Server connection check failed |
| DLI-953 | DEBUG_AUTH_CHECK_FAILED | Authentication check failed |
| DLI-954 | DEBUG_CONNECTION_CHECK_FAILED | Database connection check failed |
| DLI-955 | DEBUG_NETWORK_CHECK_FAILED | Network check failed |
| DLI-956 | DEBUG_TIMEOUT | Check timed out |

### SQL Error Codes (DLI-79x)

| Code | Name | Description |
|------|------|-------------|
| DLI-790 | SQL_FILE_NOT_FOUND | Local SQL file not found |
| DLI-791 | SQL_SNIPPET_NOT_FOUND | Snippet ID not found |
| DLI-792 | SQL_ACCESS_DENIED | Access denied to snippet |
| DLI-793 | SQL_UPDATE_FAILED | Failed to update snippet |
| DLI-794 | SQL_TEAM_NOT_FOUND | Team not found |

### Format Error Codes (DLI-15xx)

| Code | Name | Description |
|------|------|-------------|
| DLI-1501 | FORMAT_ERROR | General formatting error |
| DLI-1502 | FORMAT_SQL_ERROR | SQL formatting failed |
| DLI-1503 | FORMAT_YAML_ERROR | YAML formatting failed |
| DLI-1504 | FORMAT_DIALECT_ERROR | Unsupported SQL dialect |
| DLI-1505 | FORMAT_CONFIG_ERROR | Config file error |
| DLI-1506 | FORMAT_LINT_ERROR | Lint rule violation |

---

## Test Coverage

| Category | Tests | Status |
|----------|-------|--------|
| API Tests | ~630 (+50 SQL) | ✅ All pass |
| CLI Tests | ~1087 (+37 SQL) | ✅ All pass |
| Core Tests | ~860 (+42 Trino/Server) | ✅ All pass |
| Model Tests | ~235 (+14 ExecutionConfig) | ✅ All pass |
| Integration | ~110 (+27 RunAPI) | ✅ All pass (65 deselected by default) |
| Exception Tests | ~75 (+2 Execution) | ✅ All pass |
| **Total** | **~2997** (+87 SQL) | ✅ All pass, 35 skipped, 65 integration deselected |

### Integration Test Markers

| Marker | Description | Default |
|--------|-------------|---------|
| `integration` | Requires Docker + Trino | Skipped by default |
| `trino` | Requires Trino database | Skipped by default |
| `slow` | Long-running tests | Included |

### Integration Test Files

| File | Tests | Description |
|------|-------|-------------|
| `test_trino_integration.py` | ~30 | TrinoExecutor tests |
| `test_spec_execution_integration.py` | ~17 | Dataset/Metric/Quality spec tests |
| `test_run_integration.py` | ~27 | RunAPI ad-hoc SQL tests |
| `test_debug_integration.py` | ~15 | Debug feature tests |
| `test_format_integration.py` | ~24 | Format feature tests |

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
| LINEAGE_FEATURE.md | ✅ Updated v1.1.0 | `project-interface-cli/features/LINEAGE_FEATURE.md` |
| LINEAGE_RELEASE.md | ✅ Updated v1.1.0 | `project-interface-cli/features/LINEAGE_RELEASE.md` |
| LINEAGE_GAP.md | ✅ Created | `project-interface-cli/features/LINEAGE_GAP.md` |
| QUERY_FEATURE.md | ✅ Created | `project-interface-cli/features/QUERY_FEATURE.md` |
| QUERY_RELEASE.md | ✅ Created | `project-interface-cli/features/QUERY_RELEASE.md` |
| RUN_FEATURE.md | ✅ Updated | `project-interface-cli/features/RUN_FEATURE.md` |
| RUN_RELEASE.md | ✅ Created | `project-interface-cli/features/RUN_RELEASE.md` |
| ENV_FEATURE.md | ✅ Updated | `project-interface-cli/features/ENV_FEATURE.md` |
| ENV_RELEASE.md | ✅ Created | `project-interface-cli/features/ENV_RELEASE.md` |
| DEBUG_FEATURE.md | ✅ Created | `project-interface-cli/features/DEBUG_FEATURE.md` |
| DEBUG_RELEASE.md | ✅ Created | `project-interface-cli/features/DEBUG_RELEASE.md` |
| FORMAT_FEATURE.md | ✅ Created | `project-interface-cli/features/FORMAT_FEATURE.md` |
| FORMAT_RELEASE.md | ✅ Created | `project-interface-cli/features/FORMAT_RELEASE.md` |
| DATASET_FEATURE.md | ✅ Created | `project-interface-cli/features/DATASET_FEATURE.md` |
| DATASET_RELEASE.md | ✅ Created | `project-interface-cli/features/DATASET_RELEASE.md` |
| METRIC_FEATURE.md | ✅ Created | `project-interface-cli/features/METRIC_FEATURE.md` |
| METRIC_RELEASE.md | ✅ Created | `project-interface-cli/features/METRIC_RELEASE.md` |
| **TEST_RELEASE.md** | **✅ Created** | `project-interface-cli/features/TEST_RELEASE.md` |
| **TESTING.md** | **✅ Created** | `project-interface-cli/docs/TESTING.md` |
| **SQL_FEATURE.md** | **✅ Updated** | `project-interface-cli/features/SQL_FEATURE.md` |
| **SQL_RELEASE.md** | **✅ Created** | `project-interface-cli/features/SQL_RELEASE.md` |
| **AUDIT_FEATURE.md** | **✅ Updated** | `project-interface-cli/features/AUDIT_FEATURE.md` |
| **AUDIT_RELEASE.md** | **✅ Created** | `project-interface-cli/features/AUDIT_RELEASE.md` |

### Archived Documents

| Document | Reason | Location |
|----------|--------|----------|
| MODEL_FEATURE.md | Implemented | `features/archived/MODEL_FEATURE.md` |
| MODEL_RELEASE.md | Implemented | `features/archived/MODEL_RELEASE.md` |

---

## Related Documents

- [TEST_RELEASE.md](./TEST_RELEASE.md) - Integration Testing 구현 상세 및 로드맵
- [../docs/TESTING.md](../docs/TESTING.md) - 로컬 개발 환경 및 테스트 가이드
- [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) - Execution Model 구현 상세
- [LIBRARY_RELEASE.md](./LIBRARY_RELEASE.md) - Library API 구현 상세
- [DATASET_RELEASE.md](./DATASET_RELEASE.md) - Dataset CLI 구현 상세
- [METRIC_RELEASE.md](./METRIC_RELEASE.md) - Metric CLI 구현 상세
- [QUALITY_RELEASE.md](./QUALITY_RELEASE.md) - Quality Spec 구현 상세
- [RUN_RELEASE.md](./RUN_RELEASE.md) - Run 기능 구현 상세
- [SQL_RELEASE.md](./SQL_RELEASE.md) - SQL Snippet 관리 구현 상세
- [../docs/PATTERNS.md](../docs/PATTERNS.md) - 개발 패턴

---

## Changelog

### v1.0.5 (2026-01-10)
- **Audit Feature Phase 1 MVP Complete**
  - TraceContext: UUID generation, User-Agent formatting
  - TracedHttpClient: Auto X-Trace-Id, User-Agent header injection
  - @with_trace decorator: Applied to 49 CLI commands
  - TraceMode config: ALWAYS, ERROR_ONLY, NEVER
  - --trace/--no-trace flags: 7 execution commands
  - Error display: Trace ID in error messages
- **New Files**
  - `src/dli/core/trace.py` - TraceContext, with_trace decorator (~170 lines)
  - `src/dli/core/http.py` - TracedHttpClient (~130 lines)
  - `tests/core/test_trace.py` - TraceContext tests (31 tests)
  - `tests/core/test_http.py` - TracedHttpClient tests (17 tests)
  - `tests/commands/test_utils_trace.py` - Utils trace tests (24 tests)
- **Modified Files**
  - `src/dli/models/common.py` - Added TraceMode enum
  - `src/dli/core/client/baseclient.py` - Integrated TracedHttpClient
  - `src/dli/commands/utils.py` - Added print_error trace support
  - `src/dli/api/config.py` - Added get_trace_mode() method
  - `src/dli/commands/*.py` - Added @with_trace to 49 commands
  - `src/dli/__init__.py` - Added public exports
- **Test Coverage**
  - 72 new tests (31 + 17 + 24)
  - Total: ~2870 tests passed

### v1.0.4 (2026-01-09)
- **SQL Snippet Management Feature Complete**
  - ✅ `dli sql list` - List SQL snippets with filters (team, folder, starred)
  - ✅ `dli sql get <ID>` - Download snippet SQL to file or stdout
  - ✅ `dli sql put <ID> -f <FILE>` - Upload SQL file to update snippet
  - ✅ SqlAPI class (list_snippets, get, put methods)
  - ✅ SQL data models (SqlDialect, SqlSnippetInfo, SqlSnippetDetail, SqlListResult, SqlUpdateResult)
  - ✅ DLI-79x error codes (790-794)
  - ✅ 87 new tests (50 API + 37 CLI)
- **New Files**
  - `src/dli/models/sql.py` - SQL data models (165 lines)
  - `src/dli/api/sql.py` - SqlAPI class (316 lines)
  - `src/dli/commands/sql.py` - CLI commands (290 lines)
  - `tests/api/test_sql_api.py` - API tests (615 lines)
  - `tests/cli/test_sql_cmd.py` - CLI tests (400 lines)
- **Modified Files**
  - `src/dli/exceptions.py` - Added DLI-79x error codes
  - `src/dli/core/client.py` - Added sql_* and team_* methods
  - `src/dli/__init__.py` - Added SqlAPI export
  - `src/dli/main.py` - Registered sql_app

### v1.0.3 (2026-01-09)
- **Integration Testing Standard Level Complete**
  - ✅ RunAPI integration tests (~27 tests)
  - ✅ Ad-hoc SQL execution tests (CSV, JSON, TSV)
  - ✅ Parameter substitution tests
  - ✅ dry-run tests (EXPLAIN validation)
  - ✅ Error handling tests (syntax, non-existent table, invalid column)
  - ✅ Advanced query tests (CTE, Window, Subquery, CASE)
- **New Files**
  - `tests/integration/test_run_integration.py` (~500 lines)
  - `TrinoQueryExecutorAdapter` for QueryExecutor protocol compatibility
- **Test Coverage**
  - ~27 new RunAPI integration tests
  - Combined total: ~65 integration tests

### v1.0.2 (2026-01-09)
- **Integration Testing MVP Complete**
  - ✅ Trino integration tests with memory catalog (~38 tests)
  - ✅ pytest-docker for container lifecycle management
  - ✅ Docker Compose configuration (`docker-compose.trino.yaml`)
  - ✅ Test fixtures: `trino_executor`, `trino_test_schema`, `sample_users_table`, `sample_events_table`
  - ✅ TrinoExecutor tests (connection, query, schema, dry_run, error handling)
  - ✅ Spec execution tests (Dataset/Metric/Quality SQL rendering + Trino execution)
  - ✅ GitHub Actions CI integration (`integration-test` job)
- **New Dependencies**
  - `pytest-docker>=3.1.0` - Docker container lifecycle
  - `trino>=0.330.0` - Trino Python client
- **New pytest markers**
  - `integration` - Requires Docker + Trino (skipped by default)
  - `trino` - Requires Trino database
  - `slow` - Long-running tests
- **Documentation**
  - ✅ Created `docs/TESTING.md` - Local development setup (uv, pyenv-virtualenv, building, testing)
  - ✅ Created `features/TEST_RELEASE.md` - MVP release notes + Standard/Full roadmap
- **Test Coverage**
  - ~38 new integration tests
  - Total: ~2883 tests (2632 unit + 251 other categories)

### v1.0.1 (2026-01-08)
- **Documentation Consolidation**
  - ✅ Archived `MODEL_FEATURE.md`, `MODEL_RELEASE.md` (implemented content)
  - ✅ Created `DATASET_RELEASE.md` - Dataset CLI 구현 상세
  - ✅ Created `METRIC_RELEASE.md` - Metric CLI 구현 상세
  - ✅ Updated `QUALITY_RELEASE.md` v0.4.0 - CLI option breaking change (`--mode` -> `--local/--server/--remote`)
  - ✅ Updated `RUN_RELEASE.md` v1.1.0 - Added `--remote` option

### v1.0.0 (2026-01-08)
- **Execution Model Phase 2 Complete**
  - ✅ **TrinoExecutor** 구현 (`adapters/trino.py`) - OIDC 인증, 쿼리 실행
  - ✅ **ExecutorFactory** Trino dialect 지원 추가
  - ✅ **ExecutionMode.REMOTE** enum 값 추가
  - ✅ **ExecutionConfig** 모델 (Config YAML execution 섹션)
  - ✅ **ServerExecutor** Basecamp API 연동 구현
  - ✅ **CLI --local/--server/--remote** 옵션 (4개 실행 커맨드)
  - ⚠️ **Breaking Change**: `dli quality run --mode` → `--local/--server/--remote`
- **CLI 실행 옵션 일관성**
  - `dli dataset run --local/--server/--remote`
  - `dli metric run --local/--server/--remote`
  - `dli quality run --local/--server/--remote`
  - `dli run --local/--server/--remote`
- **116개 신규 테스트 추가** (전체 ~2645 → ~2845)

### v0.9.1 (2026-01-01)
- **Transpile Refactoring to Subcommands (v1.2.0)**
  - ⚠️ **Breaking Change**: Removed top-level `dli transpile` command
  - ✅ Added `dli dataset transpile <name>` subcommand
  - ✅ Added `dli metric transpile <name>` subcommand
  - Improved consistency with other resource commands
  - Better integration with spec file workflows

### v0.9.0 (2026-01-01)
- **Format Feature MVP Complete**
  - `dli dataset format` / `dli metric format` commands
  - SqlFormatter (sqlfluff), YamlFormatter (ruamel.yaml)
  - FormatConfig with hierarchy (.sqlfluff, .dli-format.yaml)
  - Jinja template preservation ({{ ref() }}, {{ ds }}, {% if %})
- **DatasetAPI/MetricAPI.format()** method
  - check_only, sql_only, yaml_only options
  - dialect selection (bigquery, trino, snowflake, etc.)
  - lint/fix options
- **DLI-15xx Format 에러 코드 추가**
  - DLI-1501: FORMAT_ERROR
  - DLI-1502: FORMAT_SQL_ERROR
  - DLI-1503: FORMAT_YAML_ERROR
  - DLI-1504: FORMAT_DIALECT_ERROR
  - DLI-1505: FORMAT_CONFIG_ERROR
  - DLI-1506: FORMAT_LINT_ERROR
- **Format Exception 클래스 6종**
  - FormatError, FormatSqlError, FormatYamlError
  - FormatDialectError, FormatConfigError, FormatLintError
- **Format 모델 추가**
  - models/format.py: FormatStatus, FileFormatStatus, LintViolation, FileFormatResult, FormatResult
  - core/format/: SqlFormatter, YamlFormatter, FormatConfig
- **239개 신규 테스트 추가** (35 skipped for optional deps)
  - tests/models/test_format_models.py (50+ tests)
  - tests/core/format/test_sql_formatter.py (35 tests)
  - tests/core/format/test_yaml_formatter.py (30 tests)
  - tests/api/test_format_api.py (27 tests)
  - tests/cli/test_format_cmd.py (25 tests)
  - tests/integration/test_format_integration.py (24 tests)
  - tests/exceptions/test_format_exceptions.py (40+ tests)
- **Optional Dependencies**
  - `pip install dli[format]` for sqlfluff + ruamel.yaml
- **dli/__init__.py Export 업데이트**
  - FormatStatus, FileFormatStatus, FormatResult
  - FormatError, FormatSqlError, FormatYamlError
  - FormatDialectError, FormatConfigError, FormatLintError

### v0.8.0 (2026-01-01)
- **Debug Feature 구현 완료**
  - `dli debug` - 환경 진단 및 연결 테스트
  - 12개 Check 구현 (Python, dli version, OS, Config, Project, Server URL, Server Health, API Token, GCP Credentials, DNS, HTTPS, Proxy)
  - `--connection`, `--auth`, `--network`, `--server`, `--config` 플래그
  - `--verbose`, `--json` 출력 옵션
  - `--dialect`, `--path`, `--timeout` 파라미터
- **DebugAPI 구현** (`api/debug.py`, 369 lines)
  - run_all(), check_system(), check_project(), check_server()
  - check_auth(), check_connection(), check_network()
  - MOCK/LOCAL/SERVER 모드 지원
- **DLI-95x Debug 에러 코드 7종**
  - DLI-950: DEBUG_SYSTEM_CHECK_FAILED
  - DLI-951: DEBUG_CONFIG_CHECK_FAILED
  - DLI-952: DEBUG_SERVER_CHECK_FAILED
  - DLI-953: DEBUG_AUTH_CHECK_FAILED
  - DLI-954: DEBUG_CONNECTION_CHECK_FAILED
  - DLI-955: DEBUG_NETWORK_CHECK_FAILED
  - DLI-956: DEBUG_TIMEOUT
- **Debug Exception 클래스 2종**
  - DebugCheckError (base)
  - DebugTimeoutError
- **Debug 모델 추가**
  - core/debug/models.py: CheckStatus, CheckCategory, CheckResult, DebugResult (181 lines)
  - core/debug/checks.py: BaseCheck, 12 concrete checks (820 lines)
- **196개 신규 테스트 추가 (all passing)**
  - tests/models/test_debug_models.py (39 tests)
  - tests/core/debug/test_debug_checks.py (37 tests)
  - tests/api/test_debug_api.py (32 tests)
  - tests/cli/test_debug_cmd.py (39 tests)
  - tests/integration/test_debug_integration.py (15 tests)
  - Exception tests (34 tests, included in above)
- **dli/__init__.py Export 업데이트**
  - DebugAPI, CheckStatus, CheckCategory, CheckResult, DebugResult
  - DebugCheckError, DebugTimeoutError

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
  - DLI-416: RUN_PARAMETER_INVALID
- **Run Exception 클래스 7종**
  - RunFileNotFoundError, RunLocalDeniedError, RunServerUnavailableError
  - RunExecutionError, RunOutputError, RunTimeoutError, RunParameterInvalidError
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
