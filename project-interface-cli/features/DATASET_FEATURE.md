# FEATURE: Dataset CLI 기능

> **Version:** 1.0.0
> **Status:** Draft
> **Last Updated:** 2026-01-07

---

## 1. 개요

### 1.1 목적

`dli dataset` 커맨드는 Dataset의 전체 라이프사이클을 관리합니다. CLI에서 100% SQL 렌더링을 수행하고, Execution Mode에 따라 로컬 또는 Server에서 실행합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **Self-Contained Transpile** | CLI가 SQL을 100% 렌더링 (Jinja → Transpile → SQLGlot) |
| **Execution Mode 지원** | LOCAL/SERVER/REMOTE 3가지 실행 모드 |
| **Server Policy Optional** | Transpile 정책 다운로드는 선택적 (`--use-server-policy`) |

### 1.3 주요 커맨드

| Command | Description |
|---------|-------------|
| `dli dataset list` | Dataset 목록 조회 (local/server) |
| `dli dataset get <name>` | Dataset 상세 조회 |
| `dli dataset run <name>` | Dataset 실행 (LOCAL/SERVER/REMOTE) |
| `dli dataset validate <name>` | Spec 및 SQL 검증 |
| `dli dataset register <name>` | Server에 Dataset 등록 |
| `dli dataset transpile <name>` | SQL 변환 및 렌더링 결과 확인 |
| `dli dataset format <name>` | SQL 포맷팅 |

---

## 2. Execution Modes

### 2.1 Mode 정의

| Mode | Description | Flow |
|------|-------------|------|
| **LOCAL** | CLI가 QueryEngine에 직접 연결하여 실행 | CLI → BigQuery/Trino |
| **SERVER** | CLI가 렌더링된 SQL을 Server로 전송 | CLI → Server → QueryEngine |
| **REMOTE** | 비동기 Queue 실행 | CLI → Server → Redis/Kafka → Worker |

### 2.2 CLI 옵션

```bash
# LOCAL 모드 (기본값)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15

# SERVER 모드
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --mode server

# REMOTE 모드 (비동기)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --mode remote

# 모드 축약
dli dataset run iceberg.analytics.daily_clicks --local   # LOCAL
dli dataset run iceberg.analytics.daily_clicks --server  # SERVER
dli dataset run iceberg.analytics.daily_clicks --remote  # REMOTE
```

### 2.3 Config 설정

```yaml
# dli.yaml
execution:
  default_mode: local  # local, server, remote
  timeout_seconds: 600
  max_rows: 10000
```

---

## 3. SQL Rendering Flow

### 3.1 렌더링 순서

```
[1] Spec YML 로드
    spec.iceberg.analytics.daily_clicks.yaml
                    │
                    ▼
[2] Jinja 변수 치환 (기본 변수 치환만)
    {{ execution_date }} → 2025-01-15
                    │
                    ▼
[3] Transpile 정책 적용 (Optional)
    --use-server-policy 시 Server에서 규칙 다운로드
                    │
                    ▼
[4] SQLGlot으로 최종 SQL 생성
    BigQuery → Trino 또는 Trino → BigQuery
                    │
                    ▼
[5] Execution Mode에 따른 실행
```

### 3.2 Jinja Template 지원

| 지원 | 미지원 |
|------|--------|
| `{{ param_name }}` | `{% if %}...{% endif %}` |
| `{{ param_name \| default('value') }}` | `{% for %}...{% endfor %}` |
| `{{ param_name \| upper }}` | `{% macro %}...{% endmacro %}` |

### 3.3 Server Policy Option

```bash
# Server에서 transpile 규칙 다운로드 후 적용
dli dataset run <name> --use-server-policy

# Bypass 강제
dli dataset run <name> --no-server-policy
```

---

## 4. CLI 커맨드 상세

### 4.1 `dli dataset list`

```bash
# 로컬 Spec 목록
dli dataset list

# Server 등록된 Dataset 목록
dli dataset list --server

# 필터링
dli dataset list --tag analytics --owner data-team
dli dataset list --search daily
```

### 4.2 `dli dataset get`

```bash
# Dataset 상세 조회
dli dataset get iceberg.analytics.daily_clicks

# JSON 출력
dli dataset get iceberg.analytics.daily_clicks --format json
```

### 4.3 `dli dataset run`

```bash
# 기본 실행 (LOCAL)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15

# Dry-run (SQL만 확인)
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --dry-run

# SQL 출력
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --show-sql

# 결과 제한
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --limit 1000

# Server 모드
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --server

# Server Policy 사용
dli dataset run iceberg.analytics.daily_clicks -p date=2025-01-15 --use-server-policy
```

**옵션:**

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--param` | `-p` | 파라미터 (key=value) | |
| `--mode` | | 실행 모드 (local/server/remote) | local |
| `--local` | | LOCAL 모드 축약 | |
| `--server` | | SERVER 모드 축약 | |
| `--remote` | | REMOTE 모드 축약 | |
| `--dry-run` | | SQL만 확인 (실행 안 함) | false |
| `--show-sql` | | 렌더링된 SQL 출력 | false |
| `--limit` | `-l` | 결과 행 수 제한 | |
| `--use-server-policy` | | Server 정책 사용 | false |
| `--no-server-policy` | | Bypass 강제 | |

### 4.4 `dli dataset validate`

```bash
# Spec 및 SQL 검증
dli dataset validate iceberg.analytics.daily_clicks

# 파라미터 포함 검증
dli dataset validate iceberg.analytics.daily_clicks -p date=2025-01-15

# Strict 모드
dli dataset validate iceberg.analytics.daily_clicks --strict
```

### 4.5 `dli dataset register`

```bash
# Server에 등록
dli dataset register iceberg.analytics.daily_clicks

# 강제 업데이트
dli dataset register iceberg.analytics.daily_clicks --force
```

### 4.6 `dli dataset transpile`

```bash
# Transpile 결과 확인
dli dataset transpile iceberg.analytics.daily_clicks

# 규칙 상세
dli dataset transpile iceberg.analytics.daily_clicks --show-rules

# JSON 출력
dli dataset transpile iceberg.analytics.daily_clicks --format json

# Server Policy 사용
dli dataset transpile iceberg.analytics.daily_clicks --use-server-policy
```

### 4.7 `dli dataset format`

```bash
# SQL 포맷팅
dli dataset format iceberg.analytics.daily_clicks

# 출력 다이얼렉트 지정
dli dataset format iceberg.analytics.daily_clicks --dialect trino
```

---

## 5. Library API

### 5.1 DatasetAPI 클래스

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode

# ExecutionContext 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    project_path=Path("/opt/airflow/dags/models"),
)

# DatasetAPI 초기화
api = DatasetAPI(context=ctx)

# Dataset 목록
datasets = api.list_datasets(tag="analytics", limit=50)

# Dataset 상세
dataset = api.get("iceberg.analytics.daily_clicks")

# Dataset 실행
result = api.run(
    name="iceberg.analytics.daily_clicks",
    parameters={"date": "2025-01-15"},
    limit=1000,
)

# SQL 렌더링
rendered = api.render_sql(
    name="iceberg.analytics.daily_clicks",
    parameters={"date": "2025-01-15"},
    use_server_policy=True,
)

# Transpile
transpiled = api.transpile(
    name="iceberg.analytics.daily_clicks",
    target_dialect="trino",
)
```

### 5.2 Result 모델

```python
from pydantic import BaseModel
from typing import Any

class DatasetRunResult(BaseModel):
    """Dataset 실행 결과"""
    execution_id: str
    status: str  # COMPLETED, PENDING, FAILED
    rows: list[dict[str, Any]] | None
    row_count: int | None
    duration_seconds: float | None
    rendered_sql: str

class DatasetRenderResult(BaseModel):
    """SQL 렌더링 결과"""
    original_sql: str
    rendered_sql: str
    parameters: dict[str, Any]
    transpile_info: TranspileInfo
```

---

## 6. Server API Integration

### 6.1 Execution API

SERVER/REMOTE 모드에서 호출하는 Server API:

```http
POST /api/v1/execution/datasets/run
Content-Type: application/json

{
  "resource_name": "iceberg.analytics.daily_clicks",
  "execution_mode": "SERVER",
  "rendered_sql": "SELECT ...",
  "original_spec": { ... },
  "dependencies": ["iceberg.raw.events"],
  "parameters": { "date": "2025-01-15" },
  "transpile_info": {
    "source_dialect": "bigquery",
    "target_dialect": "trino",
    "used_server_policy": false
  },
  "options": {
    "limit": 1000,
    "timeout": 600
  }
}
```

### 6.2 Response

**SERVER 모드 (동기):**
```json
{
  "execution_id": "exec-12345",
  "status": "COMPLETED",
  "rows": [...],
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

## 7. Related Documents

| Document | Description |
|----------|-------------|
| [`MODEL_FEATURE.md`](./MODEL_FEATURE.md) | MODEL 추상화 (Dataset/Metric 공통) |
| [`TRANSPILE_FEATURE.md`](./TRANSPILE_FEATURE.md) | SQL Transpile 기능 |
| [`Server DATASET_FEATURE.md`](../../project-basecamp-server/features/DATASET_FEATURE.md) | Server-side Dataset API |

---

*Document Version: 1.0.0 | Last Updated: 2026-01-07*
