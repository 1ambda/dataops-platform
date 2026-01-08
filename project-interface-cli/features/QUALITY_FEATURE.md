# FEATURE: Quality Spec 분리 및 확장

> **Version:** 1.1.0
> **Status:** ✅ Phase 1 MVP Complete (v0.3.0) + CLI SQL Generation (v1.1.0)
> **Last Updated:** 2026-01-07

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

### 4.1 CLI 커맨드 구조 ✅

```
dli quality
├── list     # 서버 등록된 Quality 목록 조회
├── get      # 특정 Quality 상세 조회 (서버)
├── run      # Quality Spec 실행 (LOCAL/SERVER)
└── validate # Quality Spec YML 유효성 검증
```

**핵심 설계 결정:**
- `show` 커맨드 제거 → `validate` (로컬 Spec) / `get` (서버 등록) 분리
- `--target-type` 옵션 사용 (기존 `-t`와 충돌 방지)
- `table|json` 출력 포맷만 지원 (일관성)

**상세 구현:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#24-cli-commands) 참조

### 4.2 Library API ✅

**QualityAPI 메서드:**
- `list_qualities(target_type?, target_name?, status?)` → 서버 Quality 목록
- `get(name)` → 특정 Quality 상세
- `run(spec_path, tests?, parameters?)` → Spec 실행
- `validate(spec_path, strict?)` → Spec 검증
- `get_spec(spec_path)` → Spec 로드

**상세 구현:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#23-api-class) 참조

---

## 5. 데이터 모델

### 5.1 Quality Spec YML 스키마 ✅

**구조:**
- `target`: 대상 정보 (type: dataset|metric, name: catalog.schema.name)
- `metadata`: owner, team, description, tags
- `schedule`: cron, timezone, enabled (Airflow DAG용)
- `notifications`: slack, email 설정
- `tests`: 테스트 정의 목록

**상세 스키마 및 예시:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#33-quality-spec-yml-example) 참조

### 5.2 Built-in Generic Test Types ✅

| Type | Phase | 설명 |
|------|-------|------|
| `not_null` | ✅ MVP | NULL 값 검사 |
| `unique` | ✅ MVP | 고유값 검사 |
| `accepted_values` | ✅ MVP | 허용 값 목록 검사 |
| `relationships` | ✅ MVP | 참조 무결성 검사 |
| `singular` | ✅ MVP | Custom SQL 테스트 |
| `expression` | Phase 2 | SQL 표현식 검사 |
| `row_count` | Phase 2 | 행 수 범위 검사 |

### 5.3 Pydantic 모델 ✅

**핵심 모델:**
- `QualitySpec` - YML 루트 모델
- `QualityTarget` - 대상 정보 (URN 생성)
- `DqTestDefinitionSpec` - 개별 테스트 정의
- `DqQualityResult` - 실행 결과
- `QualityInfo` - 서버 조회용

**설계 원칙:**
- 기존 `Dq*` enum 재사용 (`DqTestType`, `DqSeverity`, `DqStatus`)
- Pydantic (YAML 파싱) ↔ dataclass (core 모듈) 변환 지원
- `Dq` prefix 유지 (pytest 충돌 방지)

**상세 구현:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#22-data-models) 참조

---

## 6. 에러 처리 ✅

### 6.1 에러 코드 (DLI-6xx)

| Code | Exception 클래스 | 설명 |
|------|------------------|------|
| DLI-601 | `QualitySpecNotFoundError` | Quality Spec 파일을 찾을 수 없음 |
| DLI-602 | `QualitySpecParseError` | YML 파싱 오류 |
| DLI-603 | `QualityTargetNotFoundError` | 참조 대상(Dataset/Metric)을 찾을 수 없음 |
| DLI-604 | `QualityTestExecutionError` | 테스트 실행 중 오류 |
| DLI-605 | `QualityTestTimeoutError` | 테스트 실행 타임아웃 |
| DLI-606 | `QualityNotFoundError` | 서버에 등록된 Quality를 찾을 수 없음 |

**재사용 에러 코드:**
- DLI-5xx: 서버 통신 오류
- DLI-402: 타임아웃

**상세 구현:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#21-exception-hierarchy) 참조

---

## 7. 구현 우선순위

### ✅ Phase 1 (MVP) - v0.3.0 Complete

1. **Quality Spec YML 스키마 정의** ✅
   - QualitySpec Pydantic 모델
   - YML 파싱 및 검증
   - Built-in Generic Test Types (not_null, unique, accepted_values, relationships, singular)

2. **QualityAPI 구현** ✅
   - list_qualities(), get(), run(), validate(), get_spec()
   - MOCK/LOCAL/SERVER 모드 지원

3. **CLI 커맨드 구현** ✅
   - dli quality list, get, run, validate
   - `show` 커맨드 제거 (validate로 통합)

4. **Exception Hierarchy** ✅
   - DLI-6xx 에러 코드 (601-606)
   - 6개 Quality Exception 클래스

5. **테스트 작성** ✅
   - 47개 테스트 (API 19 + CLI 28)
   - pyright 0 errors, ruff clean

**상세:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md) 참조

### Phase 2 (향후 계획)

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

## 8. CLI-Side SQL Generation for Built-in Rules (v1.1.0)

> **Added in v1.1.0** - CLI generates SQL for Built-in Quality Test Rules

### 8.1 Architecture Change

기존에는 Server에서 Quality Test SQL을 생성했으나, v1.1.0부터 **CLI가 Built-in Rule에 대한 SQL을 직접 생성**합니다:

```
┌─────────────────────────────────────────────────────────────────┐
│                  Before v1.1.0 (Server-side Generation)          │
├─────────────────────────────────────────────────────────────────┤
│  CLI ─────────────────► Server ─────────────────► QueryEngine   │
│       Quality Spec         │                                     │
│                            │ Server generates SQL                │
│                            ├─────────────────────►               │
│                            │      SELECT COUNT(*) ...            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  After v1.1.0 (CLI-side Generation)              │
├─────────────────────────────────────────────────────────────────┤
│  CLI ─────────────────────────────────────────────► Server      │
│    │ 1. Load Quality Spec                              │        │
│    │ 2. Generate SQL for Built-in Rules               │        │
│    │ 3. Send rendered_sql + spec                      │        │
│    │                                                   ▼        │
│    │                                           Execute SQL      │
│    └──────────────────────────────────────────────────►         │
│                    rendered_sql                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 Built-in Rule SQL Generation

CLI는 다음 3가지 Built-in Rule에 대해 SQL을 생성합니다:

| Rule Type | Description | Generated SQL Pattern |
|-----------|-------------|----------------------|
| `not_null` | Column null check | `SELECT COUNT(*) as failed_count FROM {{ table }} WHERE {{ column }} IS NULL` |
| `unique` | Column uniqueness | `SELECT {{ column }}, COUNT(*) as cnt FROM {{ table }} GROUP BY {{ column }} HAVING COUNT(*) > 1` |
| `row_count` | Table row validation | `SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed FROM {{ table }}` |

### 8.3 Generated SQL Examples

**not_null test:**
```sql
-- Test: user_id_not_null
-- Type: not_null
-- Column: user_id
SELECT COUNT(*) as failed_count
FROM iceberg.analytics.users
WHERE user_id IS NULL
```

**unique test:**
```sql
-- Test: email_unique
-- Type: unique
-- Column: email
SELECT email, COUNT(*) as cnt
FROM iceberg.analytics.users
GROUP BY email
HAVING COUNT(*) > 1
```

**row_count test:**
```sql
-- Test: table_has_rows
-- Type: row_count
-- Condition: COUNT(*) > 0
SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed
FROM iceberg.analytics.users
```

### 8.4 Quality Spec Example with Built-in Rules

```yaml
# quality/iceberg.analytics.users.yaml
apiVersion: v1
kind: QualitySpec
target:
  type: dataset
  name: iceberg.analytics.users
metadata:
  owner: data-team
spec:
  tests:
    # Built-in Rule - CLI generates SQL
    - name: user_id_not_null
      type: not_null
      column: user_id

    # Built-in Rule - CLI generates SQL
    - name: email_unique
      type: unique
      column: email

    # Built-in Rule - CLI generates SQL
    - name: table_has_rows
      type: row_count
      operator: ">"
      value: 0

    # Custom Rule - Uses provided SQL expression
    - name: valid_status
      type: singular
      sql: "SELECT * FROM iceberg.analytics.users WHERE status NOT IN ('active', 'inactive', 'pending')"
```

### 8.5 CLI Usage

```bash
# LOCAL 모드 (CLI가 SQL 생성 후 직접 실행)
dli quality run quality.iceberg.analytics.users.yaml

# SERVER 모드 (CLI가 SQL 생성 후 Server로 전송)
dli quality run quality.iceberg.analytics.users.yaml --mode server

# Dry-run (생성된 SQL 확인)
dli quality run quality.iceberg.analytics.users.yaml --dry-run --show-sql
```

### 8.6 Server API for Pre-rendered SQL

CLI가 생성한 SQL을 Server로 전송하는 API:

```http
POST /api/v1/execution/quality/run
Content-Type: application/json

{
  "resource_name": "iceberg.analytics.users",
  "execution_mode": "SERVER",

  "tests": [
    {
      "name": "user_id_not_null",
      "type": "not_null",
      "rendered_sql": "SELECT COUNT(*) as failed_count FROM iceberg.analytics.users WHERE user_id IS NULL"
    },
    {
      "name": "email_unique",
      "type": "unique",
      "rendered_sql": "SELECT email, COUNT(*) as cnt FROM iceberg.analytics.users GROUP BY email HAVING COUNT(*) > 1"
    }
  ],

  "original_spec": { ... },

  "transpile_info": {
    "source_dialect": "bigquery",
    "target_dialect": "trino",
    "used_server_policy": false
  }
}
```

### 8.7 Backward Compatibility

기존 Server-side SQL 생성 API는 유지됩니다:

| API | Usage |
|-----|-------|
| `POST /api/v1/quality/test/{resource_name}` | 기존 API - Server가 SQL 생성 |
| `POST /api/v1/execution/quality/run` | 새 API - CLI가 SQL 생성 후 전송 |

### 8.8 Related Documents

| Document | Description |
|----------|-------------|
| [`TRANSPILE_FEATURE.md`](./TRANSPILE_FEATURE.md) | CLI SQL Rendering Flow (Section 0) |
| [`Server QUALITY_FEATURE.md`](../../project-basecamp-server/features/QUALITY_FEATURE.md) | Server-side 구현 상세 |

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

> **Last Updated:** 2026-01-01
> **Status:** ✅ Phase 1 MVP Complete (v0.3.0)

### C.1 구현 완료 항목 ✅

| 구분 | 파일/Component | 완료 |
|------|---------------|------|
| Models | `dli/models/quality.py` (12 classes) | ✅ |
| API | `dli/api/quality.py` (5 methods) | ✅ |
| CLI | `dli/commands/quality.py` (4 commands) | ✅ |
| Exceptions | `dli/exceptions.py` (DLI-601~606) | ✅ |
| Tests | 47 tests (API 19 + CLI 28) | ✅ |
| Fixtures | 3 Quality Spec 샘플 | ✅ |

**상세 구현:** [QUALITY_RELEASE.md](./QUALITY_RELEASE.md#2-implemented-components) 참조

### C.2 삭제된 코드

| 항목 | 이유 |
|------|------|
| `dli/core/quality/registry.py` | 로컬 레지스트리 → SERVER 기반으로 변경 |
| `QualityRegistry` class | registry.py 삭제에 따른 제거 |
| `show` 커맨드 | `validate` 커맨드로 통합 |

### C.3 Phase 2 향후 계획

[Section 7. 구현 우선순위 - Phase 2](#phase-2-향후-계획) 참조
