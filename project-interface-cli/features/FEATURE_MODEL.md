# FEATURE: MODEL 추상화 및 CLI 구조 개선

> **Version:** 1.0.0
> **Status:** Draft
> **Last Updated:** 2025-12-30

---

## 1. 개요

### 1.1 목적

DataOps CLI(`dli`)의 핵심 개념인 **MODEL**을 정의하고, 데이터 개발 워크플로우를 표준화합니다.
MODEL은 `metric`과 `dataset`을 아우르는 추상 개념으로, 공통 Spec 구조와 개발 워크플로우를 정의합니다.

### 1.2 핵심 원칙

1. **일관된 개발 경험**: metric과 dataset에 동일한 커맨드 구조 적용
2. **로컬 우선 개발**: 로컬 검증 → 서버 등록 → 스케줄 실행의 단계적 흐름
3. **최소 복잡도**: 불필요한 커맨드 제거, 기능 통합

### 1.3 주요 변경 사항

| 변경 유형 | 대상 | 설명 |
|-----------|------|------|
| 제거 | `dli render` | `run --show-sql`에 동일 기능 존재 |
| 제거 | `dli validate` (top-level) | `dli dataset validate`, `dli metric validate`로 통합 |
| 개명 | `dli server` | `dli config`로 변경 |
| 유지 | `dli lineage` | Top-level 커맨드 유지 |
| 유지 | `dli catalog` | Top-level 커맨드 유지 |
| 유지 | `dli transpile` | Top-level 커맨드 유지 |

### 1.4 유사 도구 참조

| 도구 | 참조 포인트 |
|------|-------------|
| dbt | `dbt run`, `dbt test`, `dbt compile` 커맨드 구조 |
| SQLMesh | Model 정의, Plan/Apply 워크플로우 |
| DataHub CLI | Metadata 관리, Lineage 조회 |

---

## 2. 아키텍처

### 2.1 MODEL 개념 정의

```
MODEL (추상 개념)
├── Metric: SELECT 쿼리 기반, 결과 데이터 반환 (READ)
└── Dataset: DML 쿼리 기반, 테이블 변경 수행 (WRITE)
```

**MODEL 공통 속성:**

| 속성 | 설명 | 필수 |
|------|------|------|
| `name` | 정규화된 이름 (catalog.schema.table) | ✅ |
| `owner` | 담당자 |  |
| `team` | 담당 팀 |  |
| `description` | 설명 |  |
| `tags` | 분류 태그 |  |
| `parameters` | 런타임 파라미터 정의 |  |
| `depends_on` | 의존성 목록 |  |
| `sql` | SQL 쿼리 또는 경로 | ✅ |

### 2.2 CLI 커맨드 구조 (개선 후)

```
dli
├── version                    # CLI 버전 정보
├── info                       # CLI 및 환경 정보
│
├── metric                     # Metric 관리
│   ├── list                   # 목록 조회 (local/server)
│   ├── get                    # 상세 조회
│   ├── run                    # 실행 (local, --dry-run 지원)
│   ├── validate               # 검증 (SQL 문법, Spec, 파라미터)
│   └── register               # 서버 등록
│
├── dataset                    # Dataset 관리
│   ├── list                   # 목록 조회 (local/server)
│   ├── get                    # 상세 조회
│   ├── run                    # 실행 (local, --dry-run, --sql 지원)
│   ├── validate               # 검증 (SQL 문법, Spec, 파라미터)
│   └── register               # 서버 등록
│
├── workflow                   # 워크플로우 관리 (Dataset 전용, 서버 기반)
│   ├── list                   # 워크플로우 목록 (CODE/MANUAL)
│   ├── run                    # Adhoc 실행
│   ├── backfill               # 기간 백필
│   ├── stop                   # 실행 중지
│   ├── status                 # 실행 상태 조회
│   ├── history                # 실행 이력
│   ├── pause                  # 스케줄 일시정지
│   └── unpause                # 스케줄 재개
│
├── quality                    # 데이터 품질 테스트
│   ├── list                   # 테스트 목록
│   ├── run                    # 테스트 실행 (local/server)
│   └── show                   # 테스트 상세
│
├── config                     # 설정 관리 (기존 server 개명)
│   ├── show                   # 현재 설정 표시
│   └── set                    # 설정 변경
│
├── lineage                    # 데이터 계보 조회 (Top-level 유지)
│   ├── show                   # 계보 시각화
│   ├── upstream               # 상위 의존성
│   └── downstream             # 하위 의존성
│
├── catalog                    # 데이터 카탈로그 (Top-level 유지)
│   └── <table_name>           # 테이블 정보 조회
│
└── transpile                  # SQL 변환 (Top-level 유지)
    └── <sql>                  # SQL 변환 및 분석
```

### 2.3 핵심 워크플로우

```
┌─────────────────────────────────────────────────────────────────┐
│                    데이터 개발 워크플로우                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 로컬 개발                                                    │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  SQL 파일 작성 → Spec YML 작성                        │    │
│     │  datasets/catalog/schema/table.sql                  │    │
│     │  spec.catalog.schema.table.yaml                     │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  2. 로컬 검증 (쿼리 실행 없음)                                   │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  dli dataset run <name> --dry-run                   │    │
│     │  dli dataset validate <name> -p param=value         │    │
│     │  dli metric validate <name> -p param=value          │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  3. 품질 테스트 (로컬 또는 서버 실행)                              │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  dli quality run <name>           # 로컬 실행        │    │
│     │  dli quality run <name> --server  # 서버 실행        │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  4. 서버 등록 (MANUAL 타입)                                      │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  dli dataset register <name>                        │    │
│     │  → Basecamp Server에 MANUAL 타입으로 등록            │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  5. 워크플로우 실행 (서버 기반)                                   │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  dli workflow run <name> -p execution_date=...      │    │
│     │  dli workflow backfill <name> -s 2024-01-01 -e ...  │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ OR ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─    │
│                           │                                     │
│  5'. Git 머지 후 CODE 타입 전환                                  │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  Git에 코드 머지                                      │    │
│     │  → Basecamp Server Polling                          │    │
│     │  → CODE 타입으로 자동 전환                            │    │
│     │  → Airflow에서 스케줄 실행                            │    │
│     └─────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Use Cases

### 3.1 Use-case 1: 신규 Dataset 개발 및 배포

**시나리오**: DA가 일일 집계 데이터셋을 개발하고 배포

```bash
# 1. 로컬에서 SQL/Spec 파일 작성 (에디터에서)
# datasets/iceberg/analytics/daily_clicks.sql
# spec.iceberg.analytics.daily_clicks.yaml

# 2. 로컬 검증
dli dataset validate iceberg.analytics.daily_clicks -p execution_date=2024-01-01

# 3. 렌더링된 SQL 확인
dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01 --dry-run --show-sql

# 4. 품질 테스트 실행
dli quality run iceberg.analytics.daily_clicks --server

# 5. 서버에 등록 (MANUAL 타입)
dli dataset register iceberg.analytics.daily_clicks

# 6. Adhoc 실행
dli workflow run iceberg.analytics.daily_clicks -p execution_date=2024-01-15

# 7. 백필 실행
dli workflow backfill iceberg.analytics.daily_clicks -s 2024-01-01 -e 2024-01-14
```

### 3.2 Use-case 2: Ad-hoc SQL 실행 with Transpile

**시나리오**: DS가 임시 쿼리를 변환하여 실행

```bash
# Ad-hoc SQL with transpilation
dli dataset run --sql "SELECT * FROM raw.events LIMIT 10" --show-sql

# SQL 파일에서 실행
dli dataset run -f query.sql --transpile-strict

# Transpile만 수행 (실행 없이)
dli transpile "SELECT * FROM analytics.users"
```

### 3.3 Use-case 3: Metric 개발 및 검증

**시나리오**: DS가 리포팅용 Metric 개발

```bash
# 1. 로컬 검증
dli metric validate iceberg.reporting.user_summary -p date=2024-01-01

# 2. 로컬 실행 (결과 확인)
dli metric run iceberg.reporting.user_summary -p date=2024-01-01 --limit 10

# 3. JSON 출력
dli metric run iceberg.reporting.user_summary -p date=2024-01-01 -o json

# 4. 서버 등록
dli metric register iceberg.reporting.user_summary
```

### 3.4 Edge Cases

| 상황 | 동작 |
|------|------|
| 존재하지 않는 리소스 실행 | 에러 메시지와 함께 exit code 1 |
| 필수 파라미터 누락 | 파라미터 목록과 함께 에러 표시 |
| 서버 연결 실패 | 로컬 모드로 폴백 가능 여부 안내 |
| SQL 문법 오류 | SQLGlot 에러 메시지 + 위치 정보 |
| Spec YAML 오류 | Pydantic 검증 에러 상세 |

---

## 4. 인터페이스 설계

### 4.1 제거할 커맨드

#### 4.1.1 `dli render` (제거)

**제거 사유**: `dli dataset run --dry-run --show-sql`와 `dli metric run --dry-run --show-sql`에서 동일 기능 제공

**기존 사용법:**
```bash
dli render query.sql --param dt=2025-01-01 --date 2025-01-01 --output rendered.sql
```

**대체 방법:**
```bash
# Dataset의 경우
dli dataset run <name> -p execution_date=2025-01-01 --dry-run --show-sql

# Ad-hoc SQL의 경우
dli dataset run --sql "SELECT ..." --dry-run --show-sql

# 파일 출력이 필요한 경우
dli dataset run <name> --dry-run --show-sql > rendered.sql
```

#### 4.1.2 `dli validate` (top-level 제거)

**제거 사유**: `dli dataset validate`와 `dli metric validate`로 기능 분리됨

**기존 사용법:**
```bash
dli validate query.sql --dialect trino
dli validate --all --check-deps
```

**대체 방법:**
```bash
# 개별 리소스 검증
dli dataset validate iceberg.analytics.daily_clicks -p execution_date=2025-01-01
dli metric validate iceberg.reporting.user_summary -p date=2025-01-01

# 프로젝트 전체 검증 (신규 옵션 필요 시 추가)
dli dataset validate --all
dli metric validate --all
```

### 4.2 변경할 커맨드

#### 4.2.1 `dli server` → `dli config`

**변경 사유**: 더 일반적인 설정 관리 커맨드로 확장 가능

**변경 전:**
```bash
dli server config
dli server status
```

**변경 후:**
```bash
dli config show           # 현재 설정 표시
dli config set <key> <value>  # 설정 변경
```

### 4.3 유지할 Top-level 커맨드

| 커맨드 | 설명 | 유지 사유 |
|--------|------|-----------|
| `dli lineage` | 데이터 계보 조회 | 크로스-리소스 기능, MODEL에 종속되지 않음 |
| `dli catalog` | 카탈로그 브라우징 | 외부 메타데이터 조회, MODEL과 독립적 |
| `dli transpile` | SQL 변환 | 독립적 SQL 분석 도구 |

---

## 5. 데이터 모델

### 5.1 MODEL 공통 Spec 구조

```yaml
# spec.{catalog}.{schema}.{table}.yaml
apiVersion: v1
kind: Dataset  # 또는 Metric
metadata:
  name: iceberg.analytics.daily_clicks
  owner: data-team
  team: analytics
  description: 일일 클릭 집계 데이터셋
  tags:
    - daily
    - analytics
spec:
  parameters:
    - name: execution_date
      type: date
      required: true
      description: 실행 날짜
  depends_on:
    - iceberg.raw.events
    - iceberg.dim.users
  sql: datasets/iceberg/analytics/daily_clicks.sql
  # Dataset 전용
  schedule: "0 9 * * *"
  pre_statements:
    - "SET session.timezone = 'Asia/Seoul'"
  post_statements:
    - "ANALYZE iceberg.analytics.daily_clicks"
```

### 5.2 Workflow 상태 모델

```
WorkflowSource:
  - CODE: Git 머지 후 Basecamp Server Polling으로 등록
  - MANUAL: CLI를 통해 직접 등록

WorkflowStatus:
  - active: 스케줄 활성
  - paused: 스케줄 일시정지
  - disabled: 비활성화

RunStatus:
  - PENDING: 대기 중
  - RUNNING: 실행 중
  - COMPLETED: 완료
  - FAILED: 실패
  - KILLED: 강제 종료
```

---

## 6. 에러 처리

### 6.1 에러 카테고리

| 카테고리 | Exit Code | 예시 |
|----------|-----------|------|
| Validation Error | 1 | SQL 문법 오류, Spec 스키마 오류 |
| Not Found | 1 | 리소스 없음, 파일 없음 |
| Connection Error | 2 | 서버 연결 실패 |
| Permission Error | 3 | 권한 없음 |
| Internal Error | 128 | 예상치 못한 오류 |

### 6.2 사용자 친화적 에러 메시지

```bash
# Bad
Error: KeyError 'execution_date'

# Good
Error: Missing required parameter 'execution_date'

Required parameters for 'iceberg.analytics.daily_clicks':
  - execution_date (date): 실행 날짜 [required]

Example:
  dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01
```

---

## 7. 구현 우선순위

### Phase 1: 정리 및 제거 (MVP)

| 작업 | 설명 | 우선순위 |
|------|------|----------|
| `render` 커맨드 제거 | main.py, commands/__init__.py 수정 | P0 |
| Top-level `validate` 제거 | main.py 수정, 기존 기능 dataset/metric으로 이관 확인 | P0 |
| `server` → `config` 개명 | server.py → config.py 리네임, 커맨드 구조 변경 | P1 |

### Phase 2: 기능 개선

| 작업 | 설명 | 우선순위 |
|------|------|----------|
| `--all` 옵션 추가 | dataset validate --all, metric validate --all | P1 |
| 에러 메시지 개선 | 사용자 친화적 에러 포맷 | P2 |
| 문서화 | CLI 도움말 개선, README 업데이트 | P2 |

### Phase 3: 미래 확장 (MODEL 통합 준비)

| 작업 | 설명 | 우선순위 |
|------|------|----------|
| MODEL 베이스 클래스 | metric/dataset 공통 로직 추출 | P3 |
| workflow의 Metric 지원 | Metric 스케줄링 기능 (필요 시) | P3 |
| model alias 커맨드 | dli model → dli dataset/metric 라우팅 | P3 |

---

## Appendix A: 결정 사항 (인터뷰 기반)

### A.1 구조 결정

| 질문 | 결정 | 근거 |
|------|------|------|
| metric/dataset 통합 여부 | 분리 유지 | 기존 사용자 경험 유지, 점진적 통합 가능 |
| render 커맨드 처리 | 제거 | run --show-sql에 동일 기능 존재 |
| validate 통합 방식 | 서브커맨드만 유지 | 리소스별 검증이 더 명확 |
| server 커맨드 처리 | config로 개명 | 범용 설정 관리로 확장 가능 |

### A.2 workflow 범위

| 질문 | 결정 | 근거 |
|------|------|------|
| workflow 대상 | Dataset 전용 (현재) | DML 작업이 스케줄링 대상 |
| Metric 지원 | 미래 확장으로 보류 | SELECT 쿼리 스케줄링 수요 확인 필요 |
| workflow register | 현재 구조 유지 | dataset register로 spec 등록, workflow는 서버 관리 |

### A.3 Top-level 커맨드 유지

| 커맨드 | 결정 | 근거 |
|--------|------|------|
| lineage | 유지 | 크로스-리소스 분석 기능 |
| catalog | 유지 | 외부 메타데이터 조회 |
| transpile | 유지 | 독립적 SQL 분석 도구 |

---

## Appendix B: 마이그레이션 가이드

### B.1 render 커맨드 사용자

**Before:**
```bash
dli render query.sql --param dt=2025-01-01
```

**After:**
```bash
# 파일 기반
dli dataset run -f query.sql --dry-run --show-sql

# 또는 spec 기반
dli dataset run <name> -p execution_date=2025-01-01 --dry-run --show-sql
```

### B.2 validate 커맨드 사용자

**Before:**
```bash
dli validate query.sql --dialect trino
dli validate iceberg.analytics.daily_clicks --var execution_date=2025-01-01
```

**After:**
```bash
dli dataset validate iceberg.analytics.daily_clicks -p execution_date=2025-01-01
dli metric validate iceberg.reporting.user_summary -p date=2025-01-01
```

### B.3 server 커맨드 사용자

**Before:**
```bash
dli server config
dli server status
```

**After:**
```bash
dli config show
dli config status  # 또는 서버 상태 확인을 위한 새 서브커맨드
```
