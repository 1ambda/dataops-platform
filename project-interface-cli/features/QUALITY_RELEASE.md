# RELEASE: Quality Spec Implementation

> **Version:** 0.3.0
> **Release Date:** 2026-01-01
> **Status:** ✅ Phase 1 MVP Complete

---

## 1. Release Summary

QUALITY_FEATURE.md v1.0.0 스펙에 따라 Quality Spec 분리 구현을 완료했습니다.
Quality Spec을 Dataset/Metric Spec에서 분리하여 독립적인 데이터 품질 검증 체계를 구축합니다.

**핵심 변경 사항:**
- Quality Spec YML 독립 파일로 분리 (1 Dataset/Metric : N Quality Tests)
- QualityAPI Library Interface 구현 (list_qualities, get, run, validate)
- CLI 커맨드 구현 (list, get, run, validate)
- DLI-6xx 에러 코드 체계 추가
- Built-in Generic Tests 지원 (not_null, unique, accepted_values, relationships, singular)

---

## 2. Implemented Components

### 2.1 Exception Hierarchy (`dli/exceptions.py`)

| Class | ErrorCode | Description |
|-------|-----------|-------------|
| `QualitySpecNotFoundError` | DLI-601 | Quality Spec 파일을 찾을 수 없음 |
| `QualitySpecParseError` | DLI-602 | YML 파싱 오류 |
| `QualityTargetNotFoundError` | DLI-603 | 참조 대상(Dataset/Metric)을 찾을 수 없음 |
| `QualityTestExecutionError` | DLI-604 | 테스트 실행 중 오류 |
| `QualityTestTimeoutError` | DLI-605 | 테스트 실행 타임아웃 |
| `QualityNotFoundError` | DLI-606 | 서버에 등록된 Quality를 찾을 수 없음 |

### 2.2 Data Models (`dli/models/quality.py`)

| Model | Type | Description |
|-------|------|-------------|
| `QualityTargetType` | Enum | 타겟 유형 (dataset, metric) |
| `QualityTarget` | Pydantic | 타겟 정보 (type, name, urn) |
| `DqTestDefinitionSpec` | Pydantic | 개별 테스트 정의 (YAML 파싱용) |
| `QualitySchedule` | Pydantic | Airflow DAG 스케줄링 정보 |
| `SlackNotification` | Pydantic | Slack 알림 설정 |
| `EmailNotification` | Pydantic | Email 알림 설정 |
| `QualityNotifications` | Pydantic | 알림 설정 그룹 |
| `QualityMetadata` | Pydantic | 메타데이터 (owner, team, tags) |
| `QualitySpec` | Pydantic | Quality Spec YML 루트 모델 |
| `DqQualityResult` | Pydantic | 실행 결과 (status, test_results) |
| `QualityInfo` | Pydantic | 서버 조회용 Quality 정보 |

**기존 Enum 재사용:**
- `DqTestType` - 테스트 유형 (not_null, unique, accepted_values, relationships, singular 등)
- `DqSeverity` - 심각도 (error, warn)
- `DqStatus` - 상태 (pass, fail, warn, error, skipped)

### 2.3 API Class (`dli/api/quality.py`)

| Method | Parameters | Return | Description |
|--------|------------|--------|-------------|
| `list_qualities` | target_type?, target_name?, status? | `list[QualityInfo]` | 서버에서 Quality 목록 조회 |
| `get` | name | `QualityInfo \| None` | 특정 Quality 상세 조회 |
| `run` | spec_path, tests?, parameters?, fail_fast? | `DqQualityResult` | Quality Spec 실행 |
| `validate` | spec_path, strict?, tests? | `ValidationResult` | Spec YML 유효성 검증 |
| `get_spec` | spec_path | `QualitySpec` | Spec 파일 로드 및 반환 |

### 2.4 CLI Commands (`dli/commands/quality.py`)

#### `dli quality list`
```bash
dli quality list [OPTIONS]

Options:
  --target-type [dataset|metric]  대상 타입으로 필터링
  --target TEXT                   대상 이름으로 필터링 (부분 일치)
  --status [active|inactive]      상태로 필터링
  --format, -f [table|json]       출력 포맷 (기본: table)
  --path, -p PATH                 프로젝트 경로 (기본: 현재 디렉토리)
```

#### `dli quality get`
```bash
dli quality get QUALITY_NAME [OPTIONS]

Arguments:
  QUALITY_NAME                  조회할 Quality 이름 (URN 또는 이름)

Options:
  --format, -f [table|json]     출력 포맷 (기본: table)
  --include-history             최근 실행 이력 포함
```

#### `dli quality run`
```bash
dli quality run SPEC_PATH [OPTIONS]

Arguments:
  SPEC_PATH                     Quality Spec YML 파일 경로

Options:
  --mode, -m [local|server]     실행 모드 (기본: local)
  --test, -t TEXT               특정 테스트만 실행 (여러 개 가능)
  --fail-fast                   첫 실패 시 중단
  --format, -f [table|json]     출력 포맷 (기본: table)
  --path, -p PATH               프로젝트 경로
  --param, -P KEY=VALUE         실행 파라미터 전달 (여러 개 가능)
```

#### `dli quality validate`
```bash
dli quality validate SPEC_PATH [OPTIONS]

Arguments:
  SPEC_PATH                     Quality Spec YML 파일 경로

Options:
  --strict                      참조 대상 존재 여부도 검증
  --format, -f [table|json]     출력 포맷 (기본: table)
  --test, -t TEXT               특정 테스트 상세 보기
```

---

## 3. Usage Examples

### 3.1 CLI Usage

```bash
# 서버에서 Quality 목록 조회
dli quality list
dli quality list --target-type dataset
dli quality list --target iceberg.analytics.daily_clicks

# 특정 Quality 상세 조회
dli quality get pk_unique
dli quality get pk_unique --format json

# Quality Spec 실행 (LOCAL 모드)
dli quality run quality.iceberg.analytics.daily_clicks.yaml
dli quality run quality.yaml --test pk_unique --test not_null_user_id
dli quality run quality.yaml --fail-fast

# Quality Spec 실행 (SERVER 모드)
dli quality run quality.yaml --mode server

# Spec 검증
dli quality validate quality.iceberg.analytics.daily_clicks.yaml
dli quality validate quality.yaml --strict
dli quality validate quality.yaml --test pk_unique
```

### 3.2 Library API Usage

```python
from dli import QualityAPI, ExecutionContext, ExecutionMode
from pathlib import Path

# API 인스턴스 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    project_path=Path("/opt/airflow/dags/models"),
)
api = QualityAPI(context=ctx)

# 서버에서 Quality 목록 조회
qualities = api.list_qualities(target_type="dataset")
for q in qualities:
    print(f"{q.name}: {q.target_urn}")

# 특정 Quality 조회
quality = api.get("pk_unique")
if quality:
    print(f"Severity: {quality.severity}")

# Quality Spec 실행
result = api.run(
    spec_path="quality.iceberg.analytics.daily_clicks.yaml",
    tests=["pk_unique", "not_null_user_id"],
    parameters={"execution_date": "2025-01-01"},
)
print(f"Status: {result.status}")
print(f"Passed: {result.passed_count}, Failed: {result.failed_count}")

# Spec 검증
validation = api.validate(
    "quality.iceberg.analytics.daily_clicks.yaml",
    strict=True,
)
if not validation.valid:
    for error in validation.errors:
        print(f"Error: {error}")
```

### 3.3 Quality Spec YML Example

```yaml
# quality.iceberg.analytics.daily_clicks.yaml
version: 1

target:
  type: dataset
  name: iceberg.analytics.daily_clicks

metadata:
  owner: analyst@example.com
  team: "@data-quality"
  description: "Daily clicks dataset quality tests"
  tags:
    - production
    - critical

schedule:
  cron: "0 6 * * *"
  timezone: "UTC"
  enabled: true

notifications:
  slack:
    channel: "#data-quality-alerts"
    on_failure: true
    on_success: false

tests:
  # Generic Test - Primary key uniqueness
  - name: pk_unique
    type: unique
    columns: [id]
    severity: error
    description: "Primary key must be unique"

  # Generic Test - Not null checks
  - name: not_null_user_id
    type: not_null
    columns: [user_id, event_time]
    severity: error

  # Generic Test - Accepted values
  - name: valid_country_code
    type: accepted_values
    column: country_code
    values: [US, CA, GB, DE, FR]
    severity: warn

  # Generic Test - Referential integrity
  - name: fk_user_exists
    type: relationships
    column: user_id
    to: iceberg.core.users
    to_column: id
    severity: error

  # Singular Test - Custom SQL
  - name: no_future_dates
    type: singular
    severity: error
    description: "Event time should not be in the future"
    sql: |
      SELECT *
      FROM {{ target }}
      WHERE event_time > CURRENT_TIMESTAMP
```

---

## 4. Test Coverage

### 4.1 Test Files

| File | Tests | Coverage |
|------|-------|----------|
| `tests/api/test_quality_api.py` | 19 | QualityAPI methods |
| `tests/cli/test_quality_cmd.py` | 28 | CLI commands |
| **Total** | **47** | - |

### 4.2 Test Results

```bash
# All tests pass
uv run pytest tests/api/test_quality_api.py tests/cli/test_quality_cmd.py -v
# ========================= 47 passed =========================

# Type check
uv run pyright src/dli/api/quality.py src/dli/models/quality.py src/dli/commands/quality.py
# 0 errors, 0 warnings

# Lint
uv run ruff check src/dli/api/quality.py src/dli/models/quality.py src/dli/commands/quality.py
# All checks passed!
```

### 4.3 Test Fixtures

Quality Spec 샘플 파일 3개:

| Fixture | Target | Tests |
|---------|--------|-------|
| `quality.iceberg.analytics.daily_clicks.yaml` | dataset | 5 (pk_unique, not_null_user_id, valid_country_code, fk_user_exists, no_future_dates) |
| `quality.iceberg.analytics.user_sessions.yaml` | dataset | N/A |
| `quality.iceberg.core.users.yaml` | dataset | 5 (pk_unique, valid_email, valid_status, not_null_created_at, no_future_created_at) |

---

## 5. Code Review Summary (expert-python)

### 5.1 Issues Fixed from QUALITY_FEATURE.md Review

| File | Issue | Fix |
|------|-------|-----|
| CLI | `show` 커맨드 중복 | `show` 제거, `validate`로 통합 |
| CLI | `--type, -t` 옵션 충돌 | `--target-type` 사용 |
| Models | 새 Enum 생성 | 기존 `DqTestType`, `DqSeverity`, `DqStatus` 재사용 |
| Models | `QualityTestDefinition` 명칭 | `DqTestDefinitionSpec` (Dq prefix 유지) |
| Models | `QualityResult` 명칭 | `DqQualityResult` (Dq prefix 유지) |
| Errors | DLI-7xx 범위 | DLI-6xx (기존 패턴 순서 유지) |

### 5.2 Review Assessment

| Criteria | Status | Notes |
|----------|--------|-------|
| Type Safety | GOOD | Pydantic models with strict typing |
| Enum Reuse | FIXED | Existing `Dq*` enums reused |
| DRY Principle | OK | API pattern consistent with DatasetAPI/MetricAPI |
| Error Handling | GOOD | Structured exception hierarchy |
| Docstrings | EXCELLENT | All public APIs documented |
| Test Coverage | GOOD | 47 tests covering API and CLI |

---

## 6. File Structure

```
project-interface-cli/src/dli/
├── __init__.py                  # UPDATED: QualityAPI export 추가
├── exceptions.py                # UPDATED: DLI-6xx Quality errors 추가
├── models/
│   ├── __init__.py              # UPDATED: quality models export 추가
│   └── quality.py               # NEW: Quality Pydantic models
├── api/
│   ├── __init__.py              # UPDATED: QualityAPI export 추가
│   └── quality.py               # NEW: QualityAPI class
└── commands/
    └── quality.py               # UPDATED: list, get, run, validate 커맨드

project-interface-cli/tests/
├── api/
│   └── test_quality_api.py      # NEW: 19 tests
├── cli/
│   └── test_quality_cmd.py      # NEW: 28 tests
└── fixtures/sample_project/
    ├── quality.iceberg.analytics.daily_clicks.yaml  # NEW
    ├── quality.iceberg.analytics.user_sessions.yaml # NEW
    └── quality.iceberg.core.users.yaml              # NEW
```

---

## 7. Deleted Files / Removed Code

| Item | Reason |
|------|--------|
| `dli/core/quality/registry.py` | 로컬 레지스트리 기반 → SERVER 기반으로 변경 |
| `QualityRegistry` class | registry.py 삭제에 따른 제거 |
| `create_registry` function | registry.py 삭제에 따른 제거 |
| `dli quality show` command | `validate` 커맨드로 통합 |

---

## 8. Future Work (Phase 2)

| Priority | Feature | Description |
|----------|---------|-------------|
| P0 | SERVER 모드 구현 | Basecamp Server API 연동 (현재 Mock) |
| P1 | Airflow DAG 메타데이터 | schedule 섹션 처리 및 DAG 생성 |
| P1 | 알림 기능 | Slack/Email 발송 (Server에서 처리) |
| P2 | Git Sync | PR 머지 시 Quality Spec 자동 등록 |
| P2 | Basecamp UI 연동 | Quality Spec 에디터, 결과 대시보드 |
| P2 | Expression Test | SQL 표현식 기반 테스트 타입 추가 |
| P2 | Row Count Test | 행 수 범위 검사 테스트 타입 추가 |

---

## 9. Migration Guide

### 9.1 기존 `show` 커맨드 사용자

```bash
# Before (deprecated)
dli quality show quality.yaml

# After
dli quality validate quality.yaml
dli quality validate quality.yaml --test pk_unique  # 특정 테스트 상세 보기
```

### 9.2 서버 등록 Quality 조회

```bash
# 서버에서 Quality 목록 조회 (새 기능)
dli quality list

# 특정 Quality 상세 조회 (새 기능)
dli quality get pk_unique
```

### 9.3 Library API 마이그레이션

```python
# Before (core module 직접 사용)
from dli.core.quality import QualityExecutor, DqTestDefinition
executor = QualityExecutor(client=None)
report = executor.run_all(test_definitions)

# After (Library API 사용)
from dli import QualityAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)
api = QualityAPI(context=ctx)
result = api.run("quality.yaml")
```

---

## 10. URN (Uniform Resource Name) 체계

Quality Spec에서 데이터 자산을 고유하게 식별하기 위한 URN 체계:

```
# Dataset URN
dataset:{catalog}.{schema}.{name}
예: dataset:iceberg.analytics.daily_clicks

# Metric URN
metric:{catalog}.{schema}.{name}
예: metric:iceberg.analytics.user_engagement

# Quality URN (서버 등록 시 생성)
quality:{target_type}:{catalog}.{schema}.{name}:{quality_name}
예: quality:dataset:iceberg.analytics.daily_clicks:pk_unique
```

---

## 11. Built-in Generic Test Types

| Type | Description | Required Parameters | Optional |
|------|-------------|---------------------|----------|
| `not_null` | NULL 값 검사 | columns | - |
| `unique` | 고유값 검사 | columns | - |
| `accepted_values` | 허용 값 목록 검사 | column, values/values_query | quote |
| `relationships` | 참조 무결성 검사 | column, to, to_column | - |
| `singular` | Custom SQL 테스트 | sql/file | params | MVP |
| `expression` | SQL 표현식 검사 | expression | - | Phase 2 |
| `row_count` | 행 수 범위 검사 | - | min, max | Phase 2 |

---

**Last Updated:** 2025-12-31
**Implemented By:** feature-interface-cli Agent
**Reviewed By:** expert-python Agent
