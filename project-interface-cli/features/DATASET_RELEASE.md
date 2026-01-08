# RELEASE: Dataset CLI Implementation

> **Version:** 1.0.0
> **Status:** Released
> **Release Date:** 2026-01-08

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **DatasetAPI** | Implemented | Library API for Dataset CRUD + execution |
| **`dli dataset list`** | Implemented | Dataset 목록 조회 (local/server) |
| **`dli dataset get`** | Implemented | Dataset 상세 조회 |
| **`dli dataset run`** | Implemented | Dataset 실행 (LOCAL/SERVER/REMOTE) |
| **`dli dataset validate`** | Implemented | Spec 및 SQL 검증 |
| **`dli dataset register`** | Implemented | Server에 Dataset 등록 |
| **`dli dataset transpile`** | Implemented | SQL 변환 및 렌더링 결과 확인 (v1.2.0) |
| **`dli dataset format`** | Implemented | SQL 포맷팅 (v0.9.0) |
| **`--local/--server/--remote`** | Implemented | 실행 모드 선택 옵션 |
| **Jinja Template** | Implemented | `{{ param }}` 파라미터 치환 지원 |
| **Mock Mode** | Implemented | 개발/테스트용 Mock 지원 |

### 1.2 API Methods

| Method | Parameters | Return | Description |
|--------|------------|--------|-------------|
| `list_datasets()` | tag?, owner?, limit? | `list[DatasetInfo]` | Dataset 목록 조회 |
| `get()` | name | `DatasetInfo \| None` | Dataset 상세 조회 |
| `run()` | name, parameters?, limit?, timeout? | `DatasetRunResult` | Dataset 실행 |
| `run_sql()` | sql, parameters? | `DatasetRunResult` | Ad-hoc SQL 실행 |
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

### 2.1 `dli dataset list`

```bash
# 로컬 Spec 목록
dli dataset list

# Server 등록된 Dataset 목록
dli dataset list --server

# 필터링
dli dataset list --tag analytics --owner data-team
dli dataset list --search daily

# JSON 출력
dli dataset list --format json
```

**Options:**

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--tag` | `-t` | 태그로 필터링 | |
| `--owner` | | 소유자로 필터링 | |
| `--search` | `-s` | 이름 검색 | |
| `--server` | | Server 등록 Dataset 조회 | false |
| `--format` | `-f` | 출력 형식 (table/json) | table |
| `--path` | `-p` | 프로젝트 경로 | . |

### 2.2 `dli dataset get`

```bash
# Dataset 상세 조회
dli dataset get iceberg.analytics.daily_clicks

# JSON 출력
dli dataset get iceberg.analytics.daily_clicks --format json
```

### 2.3 `dli dataset run`

```bash
# 기본 실행 (LOCAL)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15

# Dry-run (SQL만 확인)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --dry-run

# SQL 출력
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --show-sql

# 결과 제한
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --limit 1000

# 실행 모드 선택
dli dataset run iceberg.analytics.daily_clicks --local   # LOCAL 모드
dli dataset run iceberg.analytics.daily_clicks --server  # SERVER 모드
dli dataset run iceberg.analytics.daily_clicks --remote  # REMOTE 모드 (비동기)
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
| `--timeout` | `-t` | 타임아웃 (초) | 600 |
| `--format` | `-f` | 출력 형식 (table/json) | table |

### 2.4 `dli dataset validate`

```bash
# Spec 검증
dli dataset validate iceberg.analytics.daily_clicks

# 파라미터 포함 검증
dli dataset validate iceberg.analytics.daily_clicks -p date=2025-01-15

# Strict 모드
dli dataset validate iceberg.analytics.daily_clicks --strict
```

### 2.5 `dli dataset register`

```bash
# Server에 등록
dli dataset register iceberg.analytics.daily_clicks

# 강제 업데이트
dli dataset register iceberg.analytics.daily_clicks --force
```

### 2.6 `dli dataset transpile`

```bash
# Transpile 결과 확인
dli dataset transpile iceberg.analytics.daily_clicks

# 규칙 상세
dli dataset transpile iceberg.analytics.daily_clicks --show-rules

# JSON 출력
dli dataset transpile iceberg.analytics.daily_clicks --format json

# 대상 dialect 지정
dli dataset transpile iceberg.analytics.daily_clicks --dialect trino
```

### 2.7 `dli dataset format`

```bash
# SQL 포맷팅
dli dataset format iceberg.analytics.daily_clicks

# Check only (수정 없이 검사만)
dli dataset format iceberg.analytics.daily_clicks --check-only

# SQL만 포맷팅 (YAML 제외)
dli dataset format iceberg.analytics.daily_clicks --sql-only
```

---

## 3. Library API Usage

### 3.1 Basic Usage

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode
from pathlib import Path

# ExecutionContext 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    project_path=Path("/opt/airflow/dags/models"),
)

# DatasetAPI 초기화
api = DatasetAPI(context=ctx)

# Dataset 목록
datasets = api.list_datasets(tag="analytics", limit=50)
for ds in datasets:
    print(f"{ds.name}: {ds.description}")

# Dataset 실행
result = api.run(
    name="iceberg.analytics.daily_clicks",
    parameters={"date": "2025-01-15"},
    limit=1000,
)
print(f"Status: {result.status}, Rows: {result.row_count}")
```

### 3.2 Execution Modes

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode

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

# REMOTE 모드 - 비동기 Queue 실행
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

## 4. Dataset Spec YAML Structure

```yaml
# spec.iceberg.analytics.daily_clicks.yaml
apiVersion: v1
kind: Dataset
metadata:
  name: iceberg.analytics.daily_clicks
  owner: data-team
  team: analytics
  description: Daily clicks aggregation dataset
  tags:
    - daily
    - analytics
spec:
  parameters:
    - name: execution_date
      type: date
      required: true
      description: Execution date
  depends_on:
    - iceberg.raw.events
    - iceberg.dim.users
  sql: datasets/iceberg/analytics/daily_clicks.sql
  schedule: "0 9 * * *"
  pre_statements:
    - "SET session.timezone = 'Asia/Seoul'"
  post_statements:
    - "ANALYZE iceberg.analytics.daily_clicks"
```

---

## 5. Server API Integration

### 5.1 Execution API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/execution/datasets/run` | Dataset SQL 실행 |

### 5.2 Request Format

```json
{
  "resource_name": "iceberg.analytics.daily_clicks",
  "execution_mode": "SERVER",
  "rendered_sql": "SELECT ...",
  "parameters": { "date": "2025-01-15" },
  "transpile_info": {
    "source_dialect": "bigquery",
    "target_dialect": "trino"
  },
  "options": {
    "limit": 1000,
    "timeout": 600
  }
}
```

### 5.3 Response Format

**SERVER 모드 (동기):**
```json
{
  "execution_id": "exec-12345",
  "status": "COMPLETED",
  "row_count": 100,
  "duration_seconds": 3.5
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

Dataset 관련 에러는 DLI-1xx (Not Found), DLI-2xx (Validation), DLI-4xx (Execution), DLI-5xx (Server) 범위 사용:

| Code | Name | Description |
|------|------|-------------|
| DLI-101 | DATASET_NOT_FOUND | Dataset Spec 파일 없음 |
| DLI-201 | DATASET_VALIDATION | Spec 검증 실패 |
| DLI-401 | DATASET_EXECUTION | 쿼리 실행 오류 |
| DLI-501 | DATASET_SERVER | Server API 오류 |

---

## 7. Related Documents

| Document | Description |
|----------|-------------|
| [DATASET_FEATURE.md](./DATASET_FEATURE.md) | Dataset CLI 기능 명세 |
| [EXECUTION_RELEASE.md](./EXECUTION_RELEASE.md) | Execution Model 구현 상세 |
| [TRANSPILE_RELEASE.md](./TRANSPILE_RELEASE.md) | SQL Transpile 구현 상세 |
| [FORMAT_RELEASE.md](./FORMAT_RELEASE.md) | Format 기능 구현 상세 |
| [../docs/PATTERNS.md](../docs/PATTERNS.md) | CLI 개발 패턴 |

---

## 8. Changelog

### v1.0.0 (2026-01-08)
- DatasetAPI Library Interface 완전 구현
  - list_datasets(), get(), run(), validate(), register()
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
