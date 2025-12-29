# FEATURE: Workflow 커맨드

> **Version:** 2.0.0
> **Status:** Draft
> **Created:** 2025-12-30
> **Last Updated:** 2025-12-30

---

## 1. 개요

### 1.1 목적

`dli workflow` 커맨드는 서버에 등록된 Dataset Spec의 스케줄을 실행하고 관리합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **실행 엔진** | Airflow를 단일 실행 엔진으로 사용 |
| **스케줄 정의** | Dataset Spec YAML 파일에서만 정의/변경 가능 |
| **Basecamp 역할** | Stateless 컨트롤 플레인 (Airflow API 호출) |
| **CLI 역할** | 실행, 상태 조회, 활성화/비활성화 토글 담당 |

### 1.3 핵심 기능

| 기능 | 설명 |
|------|------|
| **Source Type 관리** | Manual/Code 두 가지 등록 방식 지원 |
| **Adhoc 실행** | 파라미터를 지정하여 즉시 실행 |
| **Backfill** | 날짜 범위를 지정하여 순차 실행 |
| **상태 관리** | 스케줄 활성화/비활성화 (일시 중지/재개) |
| **모니터링** | 실행 상태 조회, 실행 기록 확인 |
| **실행 제어** | 실행 중단 (Force Kill) |

---

## 2. Source Type: Manual vs Code

### 2.1 개념

Dataset 스케줄은 두 가지 방식으로 등록될 수 있습니다:

| Source Type | 설명 | 등록 경로 |
|-------------|------|----------|
| **Manual** | 사용자가 CLI/API를 통해 직접 등록 | CLI/API → Basecamp → S3 manual/ |
| **Code** | Git 기반 CI/CD를 통해 자동 등록 | Git → CI/CD → S3 code/ |

### 2.2 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Source Type: Code                           │
├─────────────────────────────────────────────────────────────────────┤
│  사용자 → Git (YAML) → CI/CD Pipeline → S3 code/ → Airflow DAG     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        Source Type: Manual                          │
├─────────────────────────────────────────────────────────────────────┤
│  사용자 → CLI (dli dataset register) → Basecamp → S3 manual/       │
│                                                    ↓                │
│                                            Airflow DAG              │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      Basecamp Server 역할                           │
├─────────────────────────────────────────────────────────────────────┤
│  • Stateless 컨트롤 플레인 (스케줄 정보는 Airflow에서 조회)          │
│  • S3 + Airflow가 Source of Truth                                  │
│  • Airflow REST API 호출: adhoc/backfill/status/history            │
│  • 주기적 S3 검사로 Code/Manual Override 처리                       │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 S3 저장소 구조

```
s3://bucket/
├── code/                    # CI/CD가 관리 (Git → CI/CD → S3)
│   ├── daily_clicks.yaml
│   └── user_metrics.yaml
└── manual/                  # Basecamp가 관리 (CLI/API → S3)
    ├── ad_hoc_report.yaml
    └── daily_clicks.yaml    # Code에 의해 Override될 수 있음
```

- **code/**: CI/CD 파이프라인이 Git에서 YAML을 읽어 업로드
- **manual/**: Basecamp가 CLI/API 요청을 받아 업로드
- **파일 형식**: Dataset Spec YAML 그대로 저장 (Airflow DAG Factory가 읽어서 DAG 생성)

### 2.4 충돌 정책 (Override)

동일한 Dataset 이름이 `code/`와 `manual/` 양쪽에 존재할 경우:

| 규칙 | 설명 |
|------|------|
| **Code 우선** | Code가 있으면 Manual은 자동으로 Override됨 |
| **Manual 파일 유지** | Override되어도 Manual 파일은 삭제되지 않음 (사용자 데이터 보호) |
| **자동 폴백** | Code가 삭제되면 Manual이 자동 활성화됨 |
| **주기적 검사** | Basecamp가 주기적으로 S3를 검사하여 Override 상태 갱신 |

```
상태 예시:

[초기 상태]
code/daily_clicks.yaml      ✅ 활성
manual/daily_clicks.yaml    ⏸️ Override됨 (Code에 의해)

[Code 삭제 후]
manual/daily_clicks.yaml    ✅ 자동 활성화 (폴백)
```

### 2.5 권한 모델

| Source Type | 수정 | 삭제 | pause/unpause |
|-------------|:----:|:----:|:-------------:|
| **Manual** | ✅ CLI/API | ✅ CLI/API | ✅ CLI/API |
| **Code** | ❌ Git에서만 | ❌ Git에서만 | ✅ CLI/API |

> **중요:** Code로 등록된 Dataset의 스케줄 설정(cron, timezone, retry 등)은 CLI/API로 변경할 수 없습니다.
> Git에서 YAML을 수정하고 CI/CD를 통해 반영해야 합니다.

### 2.6 상태 (Status)

| 상태 | 설명 |
|------|------|
| `active` | 스케줄 활성화됨 (정상 실행) |
| `paused` | 스케줄 일시 중지됨 |
| `overridden` | Code에 의해 Override됨 (Manual만 해당) |

### 2.7 에러 메시지

**Code 등록된 Dataset 수정/삭제 시도 시:**

```bash
$ dli dataset update iceberg.analytics.daily_clicks --cron "0 10 * * *"
Error: This dataset is managed by Code (GitOps).
       Modify the YAML in Git and deploy via CI/CD.
       Only pause/unpause operations are allowed via CLI.
```

**Manual 등록 시 Code에 동일 Dataset이 존재할 경우:**

```bash
$ dli dataset register iceberg.analytics.daily_clicks
Warning: This dataset already exists in Code path.
         Your Manual registration will be overridden by Code.
         The Manual file will be kept but inactive.
Proceed? [y/N]:
```

---

## 3. CLI 설계 원칙

### 3.1 `dataset run` vs `workflow run` 구분

| 구분 | `dli dataset run` | `dli workflow run` |
|------|-------------------|-------------------|
| **실행 환경** | 로컬 (CLI 머신) | 서버 (Airflow) |
| **용도** | 개발/테스트, 빠른 피드백 | 프로덕션 실행, 스케줄 작업 |
| **서버 필요** | 불필요 | 필수 |
| **리소스 관리** | 로컬 리소스 사용 | Airflow 리소스 관리 |
| **실행 기록** | 로컬 로그만 | Airflow에 히스토리 저장 |

> **도움말(--help)에 이 차이점을 명확히 표시합니다.**

### 3.2 공통 옵션 통일

`dataset run`과 `workflow run`은 동일한 공통 옵션을 사용합니다:

| 옵션 | 단축 | 설명 |
|------|------|------|
| `--param` | `-p` | 파라미터 (`key=value`, 복수 가능) |
| `--format` | `-f` | 출력 형식 (`table` / `json`) |
| `--dry-run` | - | 검증만 수행 (실제 실행 안 함) |
| `--path` | - | 프로젝트 경로 (기본: `.`) |
| `--verbose` | `-v` | 상세 로그 출력 |

### 3.3 Source Type 필터링 옵션

| 옵션 | 설명 |
|------|------|
| `--source code` | Code 등록된 것만 표시 |
| `--source manual` | Manual 등록된 것만 표시 |
| `--source all` | 모두 표시 (기본값) |

### 3.4 파라미터 검증

**엄격한 검증** 정책을 적용합니다:

- Spec에 정의된 파라미터만 허용
- 필수 파라미터 누락 시 즉시 에러
- 정의되지 않은 파라미터 전달 시 에러

```bash
# 에러 예시: 정의되지 않은 파라미터
$ dli workflow run my_dataset -p undefined_param=value
Error: Unknown parameter 'undefined_param'.
Allowed parameters: execution_date (required), limit (optional)
```

---

## 4. Dataset Spec Schedule 정의

스케줄은 Dataset Spec YAML 파일의 `schedule` 섹션에서 정의합니다.

```yaml
# dataset.iceberg.analytics.daily_clicks.yaml
name: iceberg.analytics.daily_clicks
type: Dataset
owner: engineer@example.com
team: "@data-engineering"
query_file: daily_clicks.sql

parameters:
  - name: execution_date
    type: string
    required: true

# 스케줄 설정
schedule:
  enabled: true
  cron: "0 9 * * *"           # 매일 09:00
  timezone: "Asia/Seoul"       # 기본: UTC
  retry:
    max_attempts: 3
    delay_seconds: 300
  notifications:
    on_failure:
      - type: slack
        channel: "#data-alerts"
    on_source_change:          # Code↔Manual 전환 시 알림 (선택적)
      - type: slack
        channel: "#data-alerts"
```

### Schedule 필드

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|------|------|:----:|--------|------|
| `enabled` | bool | N | `true` | 활성화 여부 |
| `cron` | string | **Y** | - | Cron 표현식 (5-field) |
| `timezone` | string | N | `"UTC"` | IANA 타임존 |
| `retry.max_attempts` | int | N | `1` | 최대 재시도 횟수 |
| `retry.delay_seconds` | int | N | `300` | 재시도 대기 시간(초) |
| `notifications.on_source_change` | list | N | - | Source Type 전환 시 알림 |

---

## 5. CLI 커맨드

### 5.1 커맨드 구조

```
dli workflow <subcommand> [options]
```

| 서브커맨드 | 설명 |
|-----------|------|
| `run` | Adhoc 실행 |
| `backfill` | 날짜 범위 Backfill 실행 |
| `stop` | 실행 중단 |
| `status` | 실행 상태 조회 |
| `list` | 스케줄 등록 목록 / 실행 중 목록 |
| `history` | 실행 기록 조회 |
| `pause` | 스케줄 비활성화 (일시 중지) |
| `unpause` | 스케줄 활성화 (재개) |

---

### 5.2 `run` - Adhoc 실행

```bash
dli workflow run <dataset_name> [options]
```

| 옵션 | 단축 | 설명 |
|------|------|------|
| `--param` | `-p` | 파라미터 (`key=value`, 복수 가능) |
| `--dry-run` | - | 서버 검증만 수행 (SQL 파싱 포함, 실행은 안 함) |

**출력:** run_id만 간결하게 출력

```bash
$ dli workflow run iceberg.analytics.daily_clicks -p execution_date=2024-01-15
Run started: iceberg.analytics.daily_clicks_20240115_093045
```

---

### 5.3 `backfill` - Backfill 실행

```bash
dli workflow backfill <dataset_name> --start <date> --end <date> [options]
```

| 옵션 | 단축 | 설명 |
|------|------|------|
| `--start` | `-s` | 시작 날짜 (YYYY-MM-DD) |
| `--end` | `-e` | 종료 날짜 (YYYY-MM-DD) |
| `--param` | `-p` | 추가 파라미터 (복수 가능) |
| `--dry-run` | - | 검증만 수행 |

**실행 규칙:**
- 순차 실행 (시작 → 종료 날짜)
- 실패 시 전체 중단
- **재시작 시 항상 처음부터** (멱등성 보장)

---

### 5.4 `stop` - 실행 중단

```bash
dli workflow stop <run_id>
```

---

### 5.5 `status` - 상태 조회

```bash
dli workflow status <run_id>
```

**상태 값:**

| 상태 | 설명 |
|------|------|
| `PENDING` | 대기 중 |
| `RUNNING` | 실행 중 |
| `COMPLETED` | 성공 |
| `FAILED` | 실패 |
| `KILLED` | 강제 종료됨 |

---

### 5.6 `list` - 목록 조회

```bash
dli workflow list [options]
```

| 옵션 | 설명 |
|------|------|
| `--source` | Source Type 필터 (`code` / `manual` / `all`) |
| `--running` | 실행 중인 것만 표시 |
| `--enabled-only` | 활성화된 스케줄만 표시 |
| `--dataset`, `-d` | Dataset 이름으로 필터 |

**출력 예시:**

```bash
$ dli workflow list
DATASET                              SOURCE   STATUS      CRON          NEXT RUN
iceberg.analytics.daily_clicks       code     active      0 9 * * *     2024-01-16 09:00
iceberg.analytics.user_metrics       code     paused      0 10 * * *    -
reports.ad_hoc_summary               manual   active      0 12 * * 1    2024-01-22 12:00
iceberg.analytics.daily_clicks       manual   overridden  0 8 * * *     - (overridden by code)
```

---

### 5.7 `history` - 실행 기록

```bash
dli workflow history [options]
```

| 옵션 | 단축 | 기본값 | 설명 |
|------|------|--------|------|
| `--dataset` | `-d` | - | Dataset 이름으로 필터 |
| `--source` | - | `all` | Source Type 필터 |
| `--limit` | `-n` | `20` | 조회 건수 |
| `--status` | `-s` | - | 상태로 필터 |

---

### 5.8 `pause` / `unpause` - 스케줄 활성화 관리

```bash
# 스케줄 일시 중지
dli workflow pause <dataset_name>

# 스케줄 재개
dli workflow unpause <dataset_name>
```

> **참고:** Code와 Manual 모두 pause/unpause 가능합니다.

---

## 6. Basecamp Server 동작

### 6.1 주기적 S3 검사

Basecamp Server는 주기적으로 S3를 검사하여 Override 상태를 갱신합니다.

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `SYNC_INTERVAL_SECONDS` | `300` (5분) | S3 검사 주기 (설정 가능) |

**검사 로직:**
1. S3 `code/` 경로의 모든 YAML 파일 목록 조회
2. S3 `manual/` 경로의 모든 YAML 파일 목록 조회
3. 동일 Dataset 이름이 양쪽에 존재하면 Manual을 `overridden` 상태로 설정
4. Code에만 존재하던 Dataset이 삭제되면 Manual을 `active`로 복원
5. 변경 사항이 있고 `notifications.on_source_change`가 설정되어 있으면 알림 발송

### 6.2 Airflow API 연동

Basecamp Server는 Airflow REST API를 호출하여 실행을 제어합니다:

| 동작 | Airflow API |
|------|-------------|
| Adhoc 실행 | `POST /api/v1/dags/{dag_id}/dagRuns` |
| Backfill | `POST /api/v1/dags/{dag_id}/dagRuns` (날짜별 반복) |
| 상태 조회 | `GET /api/v1/dags/{dag_id}/dagRuns/{dag_run_id}` |
| 실행 중단 | `PATCH /api/v1/dags/{dag_id}/dagRuns/{dag_run_id}` |
| 목록 조회 | `GET /api/v1/dags` |
| 실행 기록 | `GET /api/v1/dags/{dag_id}/dagRuns` |
| pause | `PATCH /api/v1/dags/{dag_id}` (is_paused=true) |
| unpause | `PATCH /api/v1/dags/{dag_id}` (is_paused=false) |

---

## 7. 에러 처리

### 7.1 에러 메시지

| 상황 | 메시지 |
|------|--------|
| 서버 연결 불가 | `Error: Cannot connect to server. Check your connection settings.` |
| 필수 파라미터 누락 | `Error: Missing required parameter: execution_date` |
| 정의되지 않은 파라미터 | `Error: Unknown parameter 'xxx'. Allowed: execution_date, limit` |
| Dataset 미등록 | `Error: Dataset not found. Register first: dli dataset register` |
| Code Dataset 수정 시도 | `Error: This dataset is managed by Code. Modify via Git.` |
| Override된 Dataset 실행 시도 | `Error: This dataset is overridden by Code. Use the Code version.` |

---

## 8. 데이터 모델

### SourceType

```python
class SourceType(str, Enum):
    MANUAL = "manual"
    CODE = "code"
```

### WorkflowStatus

```python
class WorkflowStatus(str, Enum):
    ACTIVE = "active"
    PAUSED = "paused"
    OVERRIDDEN = "overridden"  # Manual이 Code에 의해 Override된 상태
```

### ScheduleConfig

```python
class ScheduleConfig(BaseModel):
    enabled: bool = True
    cron: str
    timezone: str = "UTC"
    retry: RetryConfig = RetryConfig()
    notifications: NotificationConfig = NotificationConfig()
```

### WorkflowInfo

```python
class WorkflowInfo(BaseModel):
    dataset_name: str
    source_type: SourceType
    status: WorkflowStatus
    cron: str
    timezone: str
    next_run: datetime | None
    overridden_by: str | None  # Override한 source_type (있을 경우)
```

### WorkflowRun

```python
class RunStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    KILLED = "KILLED"

class WorkflowRun(BaseModel):
    run_id: str
    dataset_name: str
    source_type: SourceType
    status: RunStatus
    run_type: Literal["adhoc", "scheduled", "backfill"]
    parameters: dict[str, Any]
    started_at: datetime | None
    finished_at: datetime | None
```

---

## 9. 구현 우선순위

### Phase 1 (MVP)
- **Source Type 지원**: Manual + Code 둘 다 지원
- **Override 로직**: Code 우선, 주기적 검사
- **기본 커맨드**: `run`, `status`, `stop`, `list`, `pause`, `unpause`
- **필터링**: `--source` 옵션
- **에러 처리**: Code 수정 차단, Override 상태 표시

### Phase 2
- `backfill`, `history`
- 알림 기능 (`notifications.on_source_change`)
- 모니터링 대시보드 연동

### Phase 3
- 고급 스케줄링 (의존성, DAG)
- 성능 최적화

---

## 10. CLI 참고 자료

| Tool | 유사 기능 | 참고 |
|------|----------|------|
| [Airflow CLI](https://airflow.apache.org/docs/apache-airflow/stable/cli-and-env-variables-ref.html) | `dags trigger`, `dags pause/unpause` | pause/unpause 패턴 |
| [Airflow S3 Sync](https://airflow.apache.org/docs/apache-airflow/stable/howto/add-dag-sources.html) | S3에서 DAG 동기화 | S3 기반 DAG 관리 |
| [SqlMesh CLI](https://sqlmesh.readthedocs.io/en/stable/reference/cli/) | `plan --start --end`, `run` | Backfill 날짜 범위 |
| [dbt CLI](https://docs.getdbt.com/reference/commands/run) | `run --vars` | 파라미터 전달 |

---

## Appendix A: 커맨드 요약

```bash
# 실행
dli workflow run <dataset> -p key=value [--dry-run] [-v]
dli workflow backfill <dataset> -s <start> -e <end> [--dry-run]
dli workflow stop <run_id>

# 조회
dli workflow status <run_id>
dli workflow list [--source code|manual|all] [--running] [--enabled-only]
dli workflow history [-d dataset] [--source code|manual|all] [-n limit] [-s status]

# 스케줄 활성화 관리 (Code/Manual 모두 가능)
dli workflow pause <dataset>
dli workflow unpause <dataset>
```

---

## Appendix B: 인터뷰 결과 요약

| 항목 | 결정사항 |
|------|----------|
| **Source Type 용어** | Manual / Code |
| **아키텍처** | S3 기반, Airflow 단일 실행 엔진, Basecamp Stateless |
| **S3 구조** | `code/` (CI/CD), `manual/` (Basecamp) 경로 분리 |
| **충돌 정책** | Code 우선, Manual 파일 유지 (자동 삭제 안 함) |
| **Override 처리** | Basecamp 주기적 검사로 상태 갱신 |
| **Code 삭제 시** | Manual 자동 활성화 (폴백) |
| **Code 권한** | pause/unpause만 가능, 수정/삭제는 Git에서만 |
| **Override 상태 표시** | STATUS 칼럼에 'overridden' 표시 |
| **검사 주기** | 설정 가능 (기본 5분) |
| **알림** | 선택적 (`notifications.on_source_change`) |
| **CLI 필터링** | `--source` 옵션 제공 |
| **구현 우선순위** | Phase 1에서 Manual + Code 둘 다 지원 |
