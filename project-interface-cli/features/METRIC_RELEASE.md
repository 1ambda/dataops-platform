# RELEASE: Metric CLI Implementation

> **Version:** 1.0.0
> **Status:** Released
> **Release Date:** 2026-01-08

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **MetricAPI** | Implemented | Library API for Metric CRUD + execution |
| **`dli metric list`** | Implemented | Metric 목록 조회 (local/server) |
| **`dli metric get`** | Implemented | Metric 상세 조회 |
| **`dli metric run`** | Implemented | Metric 실행 (LOCAL/SERVER/REMOTE) |
| **`dli metric validate`** | Implemented | Spec 및 SQL 검증 |
| **`dli metric register`** | Implemented | Server에 Metric 등록 |
| **`dli metric transpile`** | Implemented | SQL 변환 및 렌더링 결과 확인 (v1.2.0) |
| **`dli metric format`** | Implemented | SQL 포맷팅 (v0.9.0) |
| **`--local/--server/--remote`** | Implemented | 실행 모드 선택 옵션 |
| **Jinja Template** | Implemented | `{{ param }}` 파라미터 치환 지원 |
| **Mock Mode** | Implemented | 개발/테스트용 Mock 지원 |

### 1.2 Metric vs Dataset

| 특성 | Metric | Dataset |
|------|--------|---------|
| **쿼리 유형** | SELECT (READ) | DML (WRITE) |
| **결과** | 데이터 행 반환 | 변경된 행 수 반환 |
| **주요 용도** | 리포팅, 분석 | ETL, 집계 |
| **REMOTE 사용** | 드묾 (READ 작업) | 빈번 (장시간 DML) |

### 1.3 API Methods

| Method | Parameters | Return | Description |
|--------|------------|--------|-------------|
| `list_metrics()` | tag?, owner?, limit? | `list[MetricInfo]` | Metric 목록 조회 |
| `get()` | name | `MetricInfo \| None` | Metric 상세 조회 |
| `run()` | name, parameters?, limit?, timeout? | `MetricRunResult` | Metric 실행 |
| `validate()` | name, parameters?, strict? | `ValidationResult` | Spec 검증 |
| `register()` | name, force? | `RegisterResult` | Server 등록 |
| `render_sql()` | name, parameters? | `str` | SQL 렌더링 |
| `get_tables()` | schema? | `list[TableInfo]` | 테이블 목록 |
| `get_columns()` | table_name | `list[ColumnInfo]` | 컬럼 목록 |
| `test_connection()` | - | `bool` | 연결 테스트 |
| `format()` | name, check_only?, sql_only? | `FormatResult` | SQL 포맷팅 |
| `transpile()` | name, target_dialect?, show_rules? | `TranspileResult` | SQL 변환 |

---

## 2. CLI Commands

### 2.1 `dli metric list`

```bash
# 로컬 Spec 목록
dli metric list

# Server 등록된 Metric 목록
dli metric list --server

# 필터링
dli metric list --tag reporting --owner data-team
dli metric list --search user

# JSON 출력
dli metric list --format json
```

**Options:**

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--tag` | `-t` | 태그로 필터링 | |
| `--owner` | | 소유자로 필터링 | |
| `--search` | `-s` | 이름 검색 | |
| `--server` | | Server 등록 Metric 조회 | false |
| `--format` | `-f` | 출력 형식 (table/json) | table |
| `--path` | `-p` | 프로젝트 경로 | . |

### 2.2 `dli metric get`

```bash
# Metric 상세 조회
dli metric get iceberg.reporting.user_summary

# JSON 출력
dli metric get iceberg.reporting.user_summary --format json
```

### 2.3 `dli metric run`

```bash
# 기본 실행 (LOCAL)
dli metric run iceberg.reporting.user_summary -p date=2025-01-15

# Dry-run (SQL만 확인)
dli metric run iceberg.reporting.user_summary -p date=2025-01-15 --dry-run

# SQL 출력
dli metric run iceberg.reporting.user_summary -p date=2025-01-15 --show-sql

# 결과 제한
dli metric run iceberg.reporting.user_summary -p date=2025-01-15 --limit 100

# 출력 형식
dli metric run iceberg.reporting.user_summary -p date=2025-01-15 --format json
dli metric run iceberg.reporting.user_summary -p date=2025-01-15 --format csv

# 실행 모드 선택
dli metric run iceberg.reporting.user_summary --local   # LOCAL 모드
dli metric run iceberg.reporting.user_summary --server  # SERVER 모드
dli metric run iceberg.reporting.user_summary --remote  # REMOTE 모드 (비동기, 드묾)
```

**Options:**

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--param` | `-p` | 파라미터 (key=value) | |
| `--local` | | LOCAL 모드 (기본값) | true |
| `--server` | | SERVER 모드 | false |
| `--remote` | | REMOTE 모드 (비동기) | false |
| `--dry-run` | | SQL만 확인 | false |
| `--show-sql` | | 렌더링된 SQL 출력 | false |
| `--limit` | `-l` | 결과 행 수 제한 | |
| `--timeout` | `-t` | 타임아웃 (초) | 300 |
| `--format` | `-o` | 출력 형식 (table/json/csv) | table |

### 2.4 `dli metric validate`

```bash
# Spec 검증
dli metric validate iceberg.reporting.user_summary

# 파라미터 포함 검증
dli metric validate iceberg.reporting.user_summary -p date=2025-01-15

# Strict 모드
dli metric validate iceberg.reporting.user_summary --strict
```

### 2.5 `dli metric register`

```bash
# Server에 등록
dli metric register iceberg.reporting.user_summary

# 강제 업데이트
dli metric register iceberg.reporting.user_summary --force
```

### 2.6 `dli metric transpile`

```bash
# Transpile 결과 확인
dli metric transpile iceberg.reporting.user_summary

# 규칙 상세
dli metric transpile iceberg.reporting.user_summary --show-rules

# JSON 출력
dli metric transpile iceberg.reporting.user_summary --format json

# 대상 dialect 지정
dli metric transpile iceberg.reporting.user_summary --dialect trino
```

### 2.7 `dli metric format`

```bash
# SQL 포맷팅
dli metric format iceberg.reporting.user_summary

# Check only (수정 없이 검사만)
dli metric format iceberg.reporting.user_summary --check-only

# SQL만 포맷팅 (YAML 제외)
dli metric format iceberg.reporting.user_summary --sql-only
```

---

## 3. Library API Usage

### 3.1 Basic Usage

```python
from dli import MetricAPI, ExecutionContext, ExecutionMode
from pathlib import Path

# ExecutionContext 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    project_path=Path("/opt/airflow/dags/models"),
)

# MetricAPI 초기화
api = MetricAPI(context=ctx)

# Metric 목록
metrics = api.list_metrics(tag="reporting", limit=50)
for m in metrics:
    print(f"{m.name}: {m.description}")

# Metric 실행
result = api.run(
    name="iceberg.reporting.user_summary",
    parameters={"date": "2025-01-15"},
    limit=100,
)
print(f"Status: {result.status}, Rows: {result.row_count}")

# 결과 출력
for row in result.rows:
    print(row)
```

### 3.2 Execution Modes

```python
from dli import MetricAPI, ExecutionContext, ExecutionMode

# LOCAL 모드 - CLI가 Query Engine에 직접 연결
ctx_local = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    dialect="bigquery",
)

# SERVER 모드 - Server API 통해 실행
ctx_server = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="https://basecamp.example.com",
    api_token="your-token",
)

# REMOTE 모드 - 비동기 Queue 실행 (Metric은 드묾)
ctx_remote = ExecutionContext(
    execution_mode=ExecutionMode.REMOTE,
    server_url="https://basecamp.example.com",
    api_token="your-token",
)

# Mock 모드 - 테스트용
ctx_mock = ExecutionContext(execution_mode=ExecutionMode.MOCK)
```

### 3.3 DI (Dependency Injection)

```python
from dli import MetricAPI, ExecutionContext, ExecutionMode
from dli.core.executor import MockExecutor

# 테스트 시 Mock executor 주입
mock_executor = MockExecutor(mock_data=[{"user_id": 1, "count": 100}])
api = MetricAPI(
    context=ExecutionContext(execution_mode=ExecutionMode.LOCAL),
    executor=mock_executor,
)
result = api.run("test.schema.metric")
```

---

## 4. Metric Spec YAML Structure

```yaml
# spec.iceberg.reporting.user_summary.yaml
apiVersion: v1
kind: Metric
metadata:
  name: iceberg.reporting.user_summary
  owner: analytics-team
  team: reporting
  description: User activity summary metric
  tags:
    - reporting
    - users
spec:
  parameters:
    - name: date
      type: date
      required: true
      description: Report date
    - name: region
      type: string
      required: false
      default: "all"
      description: Region filter
  depends_on:
    - iceberg.raw.users
    - iceberg.raw.events
  sql: metrics/iceberg/reporting/user_summary.sql
```

---

## 5. Server API Integration

### 5.1 Execution API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/execution/metrics/run` | Metric SQL 실행 |

### 5.2 Request Format

```json
{
  "resource_name": "iceberg.reporting.user_summary",
  "execution_mode": "SERVER",
  "rendered_sql": "SELECT ...",
  "parameters": { "date": "2025-01-15" },
  "transpile_info": {
    "source_dialect": "bigquery",
    "target_dialect": "trino"
  },
  "options": {
    "limit": 100,
    "timeout": 300
  }
}
```

### 5.3 Response Format

**SERVER 모드 (동기):**
```json
{
  "execution_id": "exec-12345",
  "status": "COMPLETED",
  "rows": [...],
  "row_count": 100,
  "duration_seconds": 1.5
}
```

**REMOTE 모드 (비동기):**
```json
{
  "execution_id": "exec-12345",
  "status": "PENDING",
  "poll_url": "/api/v1/execution/status/exec-12345"
}
```

---

## 6. Error Handling

Metric 관련 에러는 DLI-1xx (Not Found), DLI-2xx (Validation), DLI-4xx (Execution), DLI-5xx (Server) 범위 사용:

| Code | Name | Description |
|------|------|-------------|
| DLI-102 | METRIC_NOT_FOUND | Metric Spec 파일 없음 |
| DLI-202 | METRIC_VALIDATION | Spec 검증 실패 |
| DLI-402 | METRIC_EXECUTION | 쿼리 실행 오류 |
| DLI-502 | METRIC_SERVER | Server API 오류 |

---

## 7. Related Documents

| Document | Description |
|----------|-------------|
| [METRIC_FEATURE.md](./METRIC_FEATURE.md) | Metric CLI 기능 명세 |
| [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) | Execution Model 구현 상세 |
| [TRANSPILE_RELEASE.md](./TRANSPILE_RELEASE.md) | SQL Transpile 구현 상세 |
| [FORMAT_RELEASE.md](./FORMAT_RELEASE.md) | Format 기능 구현 상세 |
| [DATASET_RELEASE.md](./DATASET_RELEASE.md) | Dataset CLI 구현 (Metric 대응) |
| [../docs/PATTERNS.md](../docs/PATTERNS.md) | CLI 개발 패턴 |

---

## 8. Changelog

### v1.0.0 (2026-01-08)
- MetricAPI Library Interface 완전 구현
  - list_metrics(), get(), run(), validate(), register()
  - render_sql(), get_tables(), get_columns(), test_connection()
  - format(), transpile()
- CLI 커맨드 완전 구현
  - list, get, run, validate, register, transpile, format
- `--local/--server/--remote` 실행 모드 옵션
- Jinja Template 파라미터 치환 지원
- Mock Mode 개발/테스트 지원
- Server Execution API 연동

---

**Last Updated:** 2026-01-08
**Implemented By:** feature-interface-cli Agent
