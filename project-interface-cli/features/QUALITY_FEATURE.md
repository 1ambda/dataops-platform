# FEATURE: Quality Spec 분리 및 확장

> **Version:** 1.0.0
> **Status:** Phase 1 MVP Implemented
> **Last Updated:** 2025-12-31

---

## 1. 개요

### 1.1 목적

Quality Spec을 Dataset/Metric Spec에서 분리하여 독립적인 데이터 품질 검증 체계를 구축합니다.

**핵심 문제:**
- 현재 Quality 테스트가 Dataset/Metric Spec 내에 임베디드되어 있어 관리가 어려움
- 하나의 데이터 자산에 여러 품질 검증 규칙을 유연하게 적용하기 어려움
- 품질 검증 규칙의 재사용과 독립적인 생명주기 관리가 불가능

**해결 방향:**
- Quality Spec을 별도 YML 파일로 분리
- 1:N 관계 지원 (하나의 Dataset/Metric에 여러 Quality 테스트)
- LOCAL/SERVER 실행 모드 지원
- Airflow DAG 자동 생성을 위한 스케줄링 정보 포함

### 1.2 핵심 원칙

1. **분리된 관심사**: Quality Spec은 데이터 정의(Dataset/Metric)와 독립적으로 관리
2. **유연한 타겟팅**: URN 체계로 Dataset/Metric을 참조
3. **이중 실행 모드**: LOCAL(개발/검증) + SERVER(프로덕션)
4. **선언적 구성**: YML 기반으로 품질 검증 규칙 정의
5. **DBT 호환성**: DBT 스타일의 Generic/Singular 테스트 패턴 지원

### 1.3 주요 기능

| 기능 | 설명 | MVP |
|------|------|-----|
| Quality Spec YML 정의 | 독립적인 품질 검증 규칙 파일 | ✅ |
| Generic Tests (Built-in) | not_null, unique, accepted_values 등 내장 테스트 | ✅ |
| Singular Tests (Custom SQL) | 사용자 정의 SQL 테스트 | ✅ |
| LOCAL 실행 | CLI에서 로컬 실행 및 결과 출력 | ✅ |
| SERVER 실행 | Basecamp Server를 통한 실행 및 DB 저장 | ✅ |
| list/get 조회 | 서버에 등록된 Quality 조회 | ✅ |
| Airflow DAG 메타데이터 | 스케줄링 정보 포함 (cron) | Phase 2 |
| Slack 알림 설정 | Quality Spec 내 알림 채널 설정 | Phase 2 |

### 1.4 유사 도구 참조

| 도구 | 특징 | 참조 포인트 |
|------|------|------------|
| [dbt data tests](https://docs.getdbt.com/docs/build/data-tests) | Generic + Singular tests, schema.yml 정의 | 테스트 유형, severity 설정 |
| [SQLMesh Audits](https://sqlmesh.readthedocs.io/en/latest/concepts/tests/) | 데이터 assertion, SQL 기반 검증 | 실행 패턴, 결과 포맷 |
| [Great Expectations](https://greatexpectations.io/) | 데이터 품질 검증 프레임워크 | Expectation 개념, 배치 검증 |

---

## 2. 아키텍처 / 설계

### 2.1 URN (Uniform Resource Name) 체계

데이터 자산을 고유하게 식별하기 위한 URN 체계를 정의합니다:

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

### 2.2 파일 구조 및 위치

```
models/
├── dataset.iceberg.analytics.daily_clicks.yaml    # Dataset Spec
├── quality.iceberg.analytics.daily_clicks.yaml    # Quality Spec (대상별 그룹화)
├── metric.iceberg.analytics.user_engagement.yaml  # Metric Spec
└── quality.iceberg.analytics.user_engagement.yaml # Quality Spec
```

**파일 명명 규칙:**
- `quality.{catalog}.{schema}.{name}.yaml` (대상 자산과 동일한 네이밍)
- `quality.` prefix로 구분
- 위치: 대상 자산 YML과 동일 디렉토리

**설정을 통한 커스터마이징 (dli config):**
```yaml
quality:
  path_pattern: "quality.{catalog}.{schema}.{name}.yaml"  # 기본값
  directory: null  # null이면 대상 자산과 같은 디렉토리
```

### 2.3 컴포넌트 관계

```
┌─────────────────────────────────────────────────────────────┐
│                      DLI CLI                                 │
├──────────────────┬──────────────────┬───────────────────────┤
│  quality list    │   quality run    │    quality get        │
│  (Server 조회)   │  (LOCAL/SERVER)  │   (Server 조회)       │
└────────┬─────────┴────────┬─────────┴──────────┬────────────┘
         │                  │                    │
         ▼                  ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    QualityAPI (Library)                      │
├──────────────────────────────────────────────────────────────┤
│  - list_qualities(target_type?, target_name?)                │
│  - get(quality_name)                                         │
│  - run(quality_spec_path, mode=LOCAL|SERVER)                 │
│  - validate(quality_spec_path)                               │
└──────────────────────────────────────────────────────────────┘
         │                  │                    │
         │ SERVER mode      │ LOCAL mode         │
         ▼                  ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Basecamp Server │  │ QualityExecutor │  │ QualityRegistry │
│   (REST API)    │  │  (Local Engine) │  │  (YML Parser)   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Basecamp Server                            │
├──────────────────────────────────────────────────────────────┤
│  - Quality 등록/관리 (UI 또는 Git Sync)                      │
│  - 실행 결과 DB 저장                                         │
│  - Airflow API 연동 (DAG 상태/이력 조회)                     │
└──────────────────────────────────────────────────────────────┘
```

### 2.4 핵심 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| Spec 분리 | Quality Spec 별도 YML | 1:N 관계, 재사용성, 생명주기 분리 |
| 파일 구조 | 대상별 그룹화 (1 파일 = N 테스트) | 관리 편의성, DBT 호환성 |
| 참조 검증 | Lazy (실행 시점) | 개발 유연성, 순환 의존성 방지 |
| Generic Test | Built-in (CLI 내장) | 라이브러리 형태로 다른 컴포넌트에서 재사용 |
| 사용자 확장 | Custom SQL (Singular Test) | Generic 확장 대신 SQL로 유연하게 해결 |
| 알림 발송 | Basecamp Server 담당 | CLI는 Spec에 설정만 포함, 실제 발송은 서버 |

---

## 3. Use Cases

### 3.1 Use-case 1: 로컬 개발 중 품질 검증

**시나리오:** DA가 새로운 Dataset을 개발하면서 품질 검증 규칙을 정의하고 로컬에서 테스트

```bash
# 1. Quality Spec 파일 생성
# models/quality.iceberg.analytics.daily_clicks.yaml

# 2. 로컬 실행 (CLI 출력만, DB 저장 없음)
$ dli quality run quality.iceberg.analytics.daily_clicks.yaml

# 3. 특정 테스트만 실행
$ dli quality run quality.iceberg.analytics.daily_clicks.yaml --test pk_unique

# 4. 실행 결과 (CLI 출력)
Quality Test Report
═══════════════════
Target: dataset:iceberg.analytics.daily_clicks
Mode: LOCAL

Tests:
  ✓ pk_unique          PASS    0.23s
  ✓ not_null_user_id   PASS    0.15s
  ✗ valid_country_code FAIL    0.31s  (12 rows failed)
    Sample failures:
      {"user_id": 123, "country_code": "XX"}
      {"user_id": 456, "country_code": null}

Summary: 2 passed, 1 failed
```

### 3.2 Use-case 2: 서버 등록된 Quality 조회

**시나리오:** 팀에서 등록한 Quality 규칙을 CLI로 조회

```bash
# 전체 Quality 목록 조회
$ dli quality list
Quality Tests (registered on server)
═════════════════════════════════════
NAME                              TARGET                                   TYPE      STATUS
pk_unique                         dataset:iceberg.analytics.daily_clicks   generic   active
not_null_user_id                  dataset:iceberg.analytics.daily_clicks   generic   active
valid_country_code                dataset:iceberg.analytics.daily_clicks   singular  active
unique_event_id                   metric:iceberg.analytics.user_engagement generic   active

# Dataset 타입만 필터링
$ dli quality list --target-type dataset

# 특정 대상의 Quality만 조회
$ dli quality list --target iceberg.analytics.daily_clicks

# 특정 Quality 상세 조회
$ dli quality get pk_unique
Quality: pk_unique
═══════════════════
Target: dataset:iceberg.analytics.daily_clicks
Type: generic (unique)
Severity: error
Columns: [id]
Schedule: 0 6 * * *
Last Run: 2025-12-30 06:00:00 (PASS)
```

### 3.3 Use-case 3: 서버 모드 실행

**시나리오:** 프로덕션 환경에서 Quality 테스트 실행 및 결과 저장

```bash
# SERVER 모드로 실행 (Basecamp Server를 통해 실행, DB에 결과 저장)
$ dli quality run quality.iceberg.analytics.daily_clicks.yaml --mode server

Quality Test Report
═══════════════════
Target: dataset:iceberg.analytics.daily_clicks
Mode: SERVER (results saved)
Execution ID: exec-2025-12-31-001

Tests:
  ✓ pk_unique          PASS    0.45s
  ✓ not_null_user_id   PASS    0.28s
  ✓ valid_country_code PASS    0.52s

Summary: 3 passed, 0 failed
Results saved to Basecamp Server.
```

### 3.4 Edge Cases

| 상황 | 동작 |
|------|------|
| 참조 대상(Dataset/Metric)이 없음 | 실행 시점에 오류 반환 (lazy validation) |
| YML 문법 오류 | 파싱 시점에 상세 오류 메시지와 위치 표시 |
| 중복 테스트 이름 | 같은 Quality Spec 내에서 중복 불가, 오류 반환 |
| 네트워크 오류 (SERVER 모드) | 재시도 후 실패 시 오류 반환, 로컬 모드 전환 안내 |
| 테스트 타임아웃 | 설정된 timeout 초과 시 ERROR 상태로 기록 |

---

## 4. 인터페이스 설계 (CLI/API)

### 4.1 CLI 커맨드 구조

```
dli quality
├── list     # 서버 등록된 Quality 목록 조회
├── get      # 특정 Quality 상세 조회 (서버)
├── run      # Quality Spec 실행 (LOCAL/SERVER)
└── validate # Quality Spec YML 유효성 검증 (로컬 Spec 상세 보기 포함)
```

> **Note**: 기존 `show` 커맨드는 `validate` 및 `get`으로 대체하여 제거합니다.
> - 로컬 Spec 내용 확인: `dli quality validate <spec_path>`
> - 서버 등록된 Quality 조회: `dli quality get <name>`

### 4.2 커맨드별 옵션

#### `dli quality list`
```bash
dli quality list [OPTIONS]

Options:
  --target-type [dataset|metric]  대상 타입으로 필터링 (기존 -t와 충돌 방지)
  --target TEXT                   대상 이름으로 필터링 (부분 일치)
  --status [active|inactive]      상태로 필터링
  --format, -f [table|json]       출력 포맷 (기본: table)
  --path, -p PATH                 프로젝트 경로 (기본: 현재 디렉토리)
```

> **Note**: `--type, -t` 대신 `--target-type`을 사용합니다. 기존 CLI에서 `-t`는 `--tag` 옵션으로 사용되어 충돌을 방지합니다.

#### `dli quality get`
```bash
dli quality get QUALITY_NAME [OPTIONS]

Arguments:
  QUALITY_NAME                  조회할 Quality 이름 (URN 또는 이름)

Options:
  --format, -f [table|json]     출력 포맷 (기본: table)
  --include-history             최근 실행 이력 포함
```

> **Note**: `yaml` 출력 포맷은 CLI 전체 패턴과의 일관성을 위해 제거합니다. machine-readable 출력이 필요하면 `json`을 사용합니다.

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
  --test, -t TEXT               특정 테스트 상세 보기 (show 기능 대체)
```

> **Note**: `validate` 커맨드가 기존 `show` 커맨드의 역할도 수행합니다.
> 로컬 Spec의 상세 내용을 확인하려면 `dli quality validate <path>`를 사용하세요.

### 4.3 Library API

```python
from dli import QualityAPI, ExecutionContext, ExecutionMode
from dli.models.quality import QualitySpec, QualityResult

# API 인스턴스 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    project_path=Path("/opt/airflow/dags/models"),
)
api = QualityAPI(context=ctx)

# 서버 등록된 Quality 목록 조회
qualities = api.list_qualities(target_type="dataset")

# 특정 Quality 조회
quality = api.get("pk_unique")

# Quality Spec 실행
result: QualityResult = api.run(
    spec_path="quality.iceberg.analytics.daily_clicks.yaml",
    tests=["pk_unique", "not_null_user_id"],  # 특정 테스트만
    parameters={"execution_date": "2025-01-01"},
)

# 결과 확인
print(f"Status: {result.status}")  # PASS, FAIL, ERROR
for test_result in result.test_results:
    print(f"  {test_result.name}: {test_result.status}")
```

---

## 5. 데이터 모델

### 5.1 Quality Spec YML 스키마

```yaml
# quality.{catalog}.{schema}.{name}.yaml
version: 1

# 대상 정보
target:
  type: dataset  # dataset | metric
  name: iceberg.analytics.daily_clicks  # catalog.schema.name

# 메타데이터
metadata:
  owner: analyst@example.com
  team: "@data-quality"
  description: "Daily clicks 데이터셋 품질 검증"
  tags:
    - production
    - critical

# 스케줄링 (Airflow DAG 생성용)
schedule:
  cron: "0 6 * * *"  # 매일 06:00 UTC
  timezone: "UTC"
  enabled: true

# 알림 설정 (Basecamp Server에서 처리)
notifications:
  slack:
    channel: "#data-quality-alerts"
    on_failure: true
    on_success: false
  email:
    recipients:
      - analyst@example.com
    on_failure: true

# 테스트 정의
tests:
  # Generic Test (Built-in)
  - name: pk_unique
    type: unique
    columns: [id]
    severity: error
    description: "Primary key must be unique"

  - name: not_null_user_id
    type: not_null
    columns: [user_id, event_time]
    severity: error

  - name: valid_status
    type: accepted_values
    column: status
    values: [active, inactive, pending]
    severity: warn

  - name: valid_country_code
    type: accepted_values
    column: country_code
    values_query: "SELECT DISTINCT code FROM reference.countries"
    severity: warn

  - name: fk_user_exists
    type: relationships
    column: user_id
    to: iceberg.core.users
    to_column: id
    severity: error

  # Singular Test (Custom SQL) - 기존 DqTestType.SINGULAR과 호환
  - name: no_future_dates
    type: singular
    severity: error
    description: "Event time should not be in the future"
    sql: |
      SELECT *
      FROM {{ target }}
      WHERE event_time > CURRENT_TIMESTAMP

  - name: revenue_in_range
    type: singular
    severity: warn
    file: tests/revenue_validation.sql  # 외부 SQL 파일 참조
    params:
      min_revenue: 0
      max_revenue: 1000000
```

### 5.2 Built-in Generic Test Types

| Type | 설명 | 필수 파라미터 | 선택 파라미터 | Phase |
|------|------|--------------|--------------|-------|
| `not_null` | NULL 값 검사 | columns | - | MVP |
| `unique` | 고유값 검사 | columns | - | MVP |
| `accepted_values` | 허용 값 목록 검사 | column, values/values_query | quote | MVP |
| `relationships` | 참조 무결성 검사 | column, to, to_column | - | MVP |
| `singular` | Custom SQL 테스트 | sql/file | params | MVP |
| `expression` | SQL 표현식 검사 | expression | - | Phase 2 |
| `row_count` | 행 수 범위 검사 | - | min, max | Phase 2 |

### 5.3 Pydantic 모델

> **Note**: 기존 `dli/core/quality/models.py`의 `Dq*` enum/dataclass와의 호환성을 유지합니다.
> pytest 테스트 수집 충돌 방지를 위해 `Dq` prefix를 유지합니다.

```python
# dli/models/quality.py
# 새 모델은 Pydantic 기반, 기존 Dq* enum 재사용

from enum import Enum
from typing import Any
from pydantic import BaseModel, Field

# 기존 enum 재사용 (dli/core/quality/models.py에서 import)
from dli.core.quality.models import DqTestType, DqSeverity, DqStatus


class QualityTargetType(str, Enum):
    """Quality 테스트 대상 유형 (신규)."""
    DATASET = "dataset"
    METRIC = "metric"


class QualityTarget(BaseModel):
    """Quality 테스트 대상 정보."""
    type: QualityTargetType
    name: str = Field(..., description="catalog.schema.name 형식")

    @property
    def urn(self) -> str:
        return f"{self.type.value}:{self.name}"


class DqTestDefinitionSpec(BaseModel):
    """개별 Quality 테스트 정의 (Pydantic 버전).

    기존 DqTestDefinition (dataclass)과 호환되며 YAML 파싱용으로 사용합니다.
    """
    name: str
    type: DqTestType  # 기존 enum 재사용
    severity: DqSeverity = DqSeverity.ERROR  # 기존 enum 재사용
    description: str | None = None
    enabled: bool = True

    # Generic test parameters
    columns: list[str] | None = None
    column: str | None = None
    values: list[str] | None = None
    values_query: str | None = None
    to: str | None = None  # relationships target
    to_column: str | None = None
    expression: str | None = None
    min: int | None = None
    max: int | None = None

    # Singular test parameters
    sql: str | None = None
    file: str | None = None
    params: dict[str, Any] = Field(default_factory=dict)

    def to_test_definition(self, resource_name: str) -> "DqTestDefinition":
        """기존 DqTestDefinition (dataclass)으로 변환."""
        from dli.core.quality.models import DqTestDefinition
        return DqTestDefinition(
            name=self.name,
            test_type=self.type,
            resource_name=resource_name,
            columns=self.columns,
            params=self.params,
            description=self.description,
            severity=self.severity,
            sql=self.sql,
            file=self.file,
            enabled=self.enabled,
        )


class QualitySchedule(BaseModel):
    """Airflow DAG 스케줄링 정보."""
    cron: str = Field(..., description="Cron expression")
    timezone: str = "UTC"
    enabled: bool = True


class SlackNotification(BaseModel):
    channel: str
    on_failure: bool = True
    on_success: bool = False


class EmailNotification(BaseModel):
    recipients: list[str]
    on_failure: bool = True
    on_success: bool = False


class QualityNotifications(BaseModel):
    slack: SlackNotification | None = None
    email: EmailNotification | None = None


class QualityMetadata(BaseModel):
    owner: str
    team: str | None = None
    description: str | None = None
    tags: list[str] = Field(default_factory=list)


class QualitySpec(BaseModel):
    """Quality Spec YML의 루트 모델."""
    version: int = 1
    target: QualityTarget
    metadata: QualityMetadata
    schedule: QualitySchedule | None = None
    notifications: QualityNotifications | None = None
    tests: list[QualityTestDefinition]

    @classmethod
    def from_yaml_file(cls, path: Path) -> "QualitySpec":
        """YML 파일에서 QualitySpec 로드."""
        ...
```

### 5.4 실행 결과 모델

> **Note**: 기존 `DqStatus`, `DqTestResult` (dataclass)를 재사용합니다.

```python
from datetime import datetime
from dli.core.quality.models import DqStatus, DqTestResult  # 기존 모델 재사용
from dli.models.common import ExecutionMode


class DqQualityResult(BaseModel):
    """Quality Spec 전체 실행 결과 (Pydantic 버전).

    기존 QualityReport를 대체하며, QualitySpec 실행 결과를 담습니다.
    """
    target_urn: str
    execution_mode: ExecutionMode
    execution_id: str | None = None  # SERVER 모드일 때만
    started_at: datetime
    finished_at: datetime
    test_results: list[DqTestResult]  # 기존 dataclass 재사용

    @property
    def status(self) -> DqStatus:
        """전체 상태 (가장 심각한 상태 반환)."""
        priority = {DqStatus.ERROR: 0, DqStatus.FAIL: 1, DqStatus.WARN: 2, DqStatus.PASS: 3, DqStatus.SKIPPED: 4}
        return min((r.status for r in self.test_results), key=lambda s: priority.get(s, 99), default=DqStatus.PASS)

    @property
    def passed_count(self) -> int:
        return sum(1 for r in self.test_results if r.status == DqStatus.PASS)

    @property
    def failed_count(self) -> int:
        return sum(1 for r in self.test_results if r.status in (DqStatus.FAIL, DqStatus.ERROR))
```

---

## 6. 에러 처리

### 6.1 에러 코드

> **Note**: DLI-6xx 범위를 사용하여 기존 에러 코드 순서(0xx~5xx)를 유지합니다.

| Code | 이름 | 설명 |
|------|------|------|
| DLI-601 | `QualitySpecNotFoundError` | Quality Spec 파일을 찾을 수 없음 |
| DLI-602 | `QualitySpecParseError` | YML 파싱 오류 |
| DLI-603 | `QualityTargetNotFoundError` | 참조 대상(Dataset/Metric)을 찾을 수 없음 |
| DLI-604 | `QualityTestExecutionError` | 테스트 실행 중 오류 |
| DLI-605 | `QualityTestTimeoutError` | 테스트 실행 타임아웃 (기존 DLI-402 재사용 가능) |
| DLI-606 | `QualityNotFoundError` | 서버에 등록된 Quality를 찾을 수 없음 |

**기존 에러 코드 재사용:**
- 서버 통신 오류: 기존 DLI-5xx (`ServerError`) 재사용
- 타임아웃: 기존 DLI-402 (`TIMEOUT`) 재사용 가능

### 6.2 에러 메시지 예시

```python
# ErrorCode 확장 (dli/exceptions.py에 추가)
class ErrorCode(str, Enum):
    # ... 기존 코드 ...
    # Quality Errors (DLI-6xx)
    QUALITY_SPEC_NOT_FOUND = "DLI-601"
    QUALITY_SPEC_PARSE = "DLI-602"
    QUALITY_TARGET_NOT_FOUND = "DLI-603"
    QUALITY_TEST_EXECUTION = "DLI-604"
    QUALITY_TEST_TIMEOUT = "DLI-605"
    QUALITY_NOT_FOUND = "DLI-606"


# QualitySpecParseError
raise QualitySpecParseError(
    message="Invalid test type 'invalid_type' at tests[2]",
    code=ErrorCode.QUALITY_SPEC_PARSE,
    spec_path="quality.iceberg.analytics.daily_clicks.yaml",
    line=45,
    column=10,
)

# QualityTargetNotFoundError
raise QualityTargetNotFoundError(
    message="Target dataset 'iceberg.analytics.daily_clicks' not found",
    code=ErrorCode.QUALITY_TARGET_NOT_FOUND,
    target_urn="dataset:iceberg.analytics.daily_clicks",
)
```

---

## 7. 구현 우선순위

### Phase 1 (MVP)

1. **Quality Spec YML 스키마 정의**
   - QualitySpec Pydantic 모델
   - YML 파싱 및 검증
   - Built-in Generic Test Types (not_null, unique, accepted_values, relationships)

2. **QualityAPI 구현**
   - list_qualities() - 서버 조회 (Mock 우선)
   - get() - 서버 조회 (Mock 우선)
   - run() - LOCAL/SERVER 실행
   - validate() - Spec 검증

3. **CLI 커맨드 구현**
   - dli quality list (서버 조회)
   - dli quality get (서버 상세 조회)
   - dli quality run (LOCAL/SERVER 실행)
   - dli quality validate (로컬 Spec 검증 + 상세 보기)

4. **기존 코드 정리**
   - Dataset/Metric Spec 내 test 필드 제거 (있는 경우)
   - 기존 quality 모듈과의 통합/마이그레이션

### Phase 2

1. **Airflow DAG 메타데이터**
   - schedule 섹션 처리
   - DAG 생성 로직 (Basecamp Server 또는 별도 컴포넌트)

2. **알림 기능**
   - notifications 섹션 처리
   - Basecamp Server에서 Slack/Email 발송

3. **Git Sync**
   - PR 머지 시 Quality Spec 자동 등록
   - 버전 관리 및 변경 이력

4. **Basecamp UI 연동**
   - Quality Spec YML 에디터
   - 실행 결과 대시보드

---

## Appendix: 결정 사항 (인터뷰 기반)

### A.1 분리 목적
- **결정**: 1:N 관계, 재사용성, 생명주기 분리 모두 적용
- **근거**: 하나의 Dataset에 여러 품질 규칙을 유연하게 적용하고, Quality 규칙의 독립적인 변경/배포 필요

### A.2 대상 지정 방식
- **결정**: Dataset/Metric 이름을 URN으로 지정 (`dataset:catalog.schema.name`)
- **근거**: 명확한 식별자 체계로 타입과 이름을 함께 표현

### A.3 YML 파일 위치
- **결정**: 대상 자산 옆에 배치, `quality.` prefix, dli config로 변경 가능
- **근거**: 관련 파일을 함께 관리하여 가시성 확보

### A.4 YML 구조
- **결정**: 대상별 그룹화 (1 파일 = 1 Dataset/Metric의 N 테스트)
- **근거**: 관리 편의성, DBT 호환성

### A.5 실행 결과 저장
- **결정**: LOCAL은 CLI 출력만, SERVER는 DB 저장
- **근거**: 개발 환경과 프로덕션 환경의 용도 구분

### A.6 Airflow DAG 실행 시점
- **결정**: 독립 스케줄 (cron)
- **근거**: Dataset/Metric 실행과 분리된 품질 검증 워크플로우

### A.7 실패 처리
- **결정**: Severity 기반 (warn/error)
- **근거**: 테스트별 중요도에 따른 유연한 처리

### A.8 알림 처리
- **결정**: Slack 설정을 Spec에 포함, Basecamp Server에서 발송
- **근거**: CLI는 설정만 정의, 실제 발송은 서버에서 중앙 관리

### A.9 Generic Test 확장
- **결정**: Built-in (CLI 내장) + 사용자 확장은 Custom SQL로
- **근거**: 라이브러리 형태로 basecamp-parser, Airflow에서 재사용 가능

### A.10 참조 검증
- **결정**: Lazy (실행 시점에 오류)
- **근거**: 개발 유연성, 순환 의존성 방지

---

## Appendix B: 에이전트 리뷰 결과

### B.1 feature-interface-cli Agent 리뷰

| 항목 | 판정 | 변경 내용 |
|------|------|----------|
| CLI 커맨드 구조 | MODIFY | `show` 커맨드 제거, `validate`로 통합 |
| 옵션 네이밍 | MODIFY | `--type, -t` → `--target-type` (기존 `-t`와 충돌 방지) |
| 출력 포맷 | MODIFY | `yaml` 포맷 제거, `table|json`으로 표준화 |
| 에러 코드 | MODIFY | DLI-7xx → DLI-6xx (기존 패턴 순서 유지) |
| QualityAPI 패턴 | ACCEPT | 기존 DatasetAPI/MetricAPI 패턴과 일치 |

### B.2 expert-python Agent 리뷰

| 항목 | 판정 | 변경 내용 |
|------|------|----------|
| Enum 재사용 | MODIFY | 신규 생성 대신 기존 `DqTestType`, `DqSeverity`, `DqStatus` 재사용 |
| Test Type 명칭 | MODIFY | `custom` → `singular` (기존 `DqTestType.SINGULAR`과 호환) |
| Model 네이밍 | MODIFY | `QualityTestDefinition` → `DqTestDefinitionSpec` (Pydantic 버전) |
| Result Model | MODIFY | `QualityResult` → `DqQualityResult` (Dq prefix 유지) |
| 기존 모델 호환 | ACCEPT | `to_test_definition()` 메서드로 dataclass 변환 지원 |

### B.3 반영된 주요 변경 사항

1. **CLI 단순화**: `show` 커맨드 제거, `validate`가 로컬 Spec 상세 보기 역할 수행
2. **옵션 충돌 해결**: `--target-type` 사용으로 기존 `--tag, -t` 옵션과 충돌 방지
3. **기존 코드 재사용**: `Dq*` enum/dataclass 재사용으로 마이그레이션 부담 최소화
4. **에러 코드 연속성**: DLI-6xx 범위로 기존 패턴(0xx~5xx) 유지
5. **Pydantic 변환**: YAML 파싱용 Pydantic 모델 + 기존 dataclass 호환 메서드 제공

---

## Appendix C: 구현 상태 (Implementation Status)

> **Last Updated:** 2025-12-31
> **Status:** Phase 1 MVP 완료

### C.1 구현 완료 항목

| 구분 | 파일 | 설명 |
|------|------|------|
| **Models** | `dli/models/quality.py` | QualitySpec, DqTestDefinitionSpec, DqQualityResult 등 Pydantic 모델 |
| **API** | `dli/api/quality.py` | QualityAPI (list_qualities, get, run, validate) |
| **CLI** | `dli/commands/quality.py` | list, get, run, validate 커맨드 |
| **Exceptions** | `dli/exceptions.py` | DLI-6xx 에러 코드 및 Quality 관련 예외 클래스 |
| **Tests** | `tests/api/test_quality_api.py` | API 테스트 19개 |
| **Tests** | `tests/cli/test_quality_cmd.py` | CLI 테스트 28개 |
| **Fixtures** | `tests/fixtures/sample_project/` | Quality Spec 샘플 3개 |

### C.2 CLI 커맨드 요약

```bash
# 서버에서 Quality 목록 조회
dli quality list [--target-type dataset|metric] [--target TEXT] [--format table|json]

# 서버에서 특정 Quality 상세 조회
dli quality get QUALITY_NAME [--format table|json] [--include-history]

# Quality Spec 실행 (LOCAL/SERVER 모드)
dli quality run SPEC_PATH [--mode local|server] [--test TEXT] [--fail-fast] [--format table|json]

# Quality Spec YML 유효성 검증
dli quality validate SPEC_PATH [--strict] [--format table|json] [--test TEXT]
```

### C.3 Library API 사용 예시

```python
from dli import QualityAPI, ExecutionContext, ExecutionMode
from pathlib import Path

# API 인스턴스 생성
ctx = ExecutionContext(
    execution_mode=ExecutionMode.MOCK,  # 또는 LOCAL, SERVER
    project_path=Path("/opt/airflow/dags/models"),
)
api = QualityAPI(context=ctx)

# 서버에서 Quality 목록 조회
qualities = api.list_qualities(target_type="dataset")

# Quality Spec 실행
result = api.run(
    spec_path="quality.iceberg.analytics.daily_clicks.yaml",
    tests=["pk_unique"],
    parameters={"execution_date": "2025-01-01"},
)

# Spec 검증
validation = api.validate("quality.iceberg.analytics.daily_clicks.yaml")
```

### C.4 테스트 결과

```
============= 47 passed in 0.90s =============
pyright: 0 errors, 0 warnings, 0 informations
ruff: All checks passed!
```

### C.5 삭제된 파일/코드

| 항목 | 이유 |
|------|------|
| `dli/core/quality/registry.py` | 로컬 레지스트리 기반 → SERVER 기반으로 변경 |
| `QualityRegistry`, `create_registry` | registry.py 삭제에 따른 export 제거 |
| `show` 커맨드 | `validate` 커맨드로 통합 |

### C.6 향후 구현 예정 (Phase 2)

| 기능 | 설명 | 우선순위 |
|------|------|----------|
| SERVER 모드 실행 | Basecamp Server API 연동 구현 | P0 |
| Airflow DAG 메타데이터 | schedule 섹션 처리 및 DAG 생성 | P1 |
| 알림 기능 | Slack/Email 발송 (Server에서 처리) | P1 |
| Git Sync | PR 머지 시 자동 등록 | P2 |
| Basecamp UI 연동 | Quality Spec 에디터, 결과 대시보드 | P2 |
