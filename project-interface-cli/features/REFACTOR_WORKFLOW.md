# REFACTOR: Workflow 기능 개선 계획

> **Version:** 1.0.0
> **Created:** 2025-12-31
> **Status:** Draft

---

## 1. 현재 구현 상태 요약

### 1.1 구현 완료 항목

| 구성요소 | 항목 | 상태 |
|----------|------|------|
| **WorkflowAPI** | 11개 메서드 (get, register, unregister, run, backfill, stop, get_status, list_workflows, history, pause, unpause) | Mock 모드 완전 지원 |
| **BasecampClient** | 10개 workflow_* 메서드 (run, backfill, stop, status, list, history, pause, unpause, register, unregister) | Mock 모드 완전 지원 |
| **CLI Commands** | 8개 커맨드 (run, backfill, stop, status, list, history, pause, unpause) | 기본 동작 구현됨 |
| **Result Models** | 5개 모델 (WorkflowRegisterResult, WorkflowRunResult, WorkflowListResult, WorkflowStatusResult, WorkflowHistoryResult) | Pydantic frozen 모델 |
| **Core Models** | SourceType, WorkflowStatus, RunStatus, WorkflowInfo, WorkflowRun, ScheduleConfig, RetryConfig | 완전 구현 |
| **Exception Hierarchy** | DLI-8xx 범위 (800-808) 8개 에러 코드 | @dataclass 패턴 준수 |

### 1.2 미구현/Stub 항목

| 항목 | 현재 상태 | 비고 |
|------|-----------|------|
| **`workflow register` CLI 커맨드** | 미구현 | FEATURE_WORKFLOW.md에 정의됨, commands/workflow.py에 없음 |
| **`--show-dataset-info` CLI 옵션** | 미구현 | history 커맨드에 미반영 |
| **Server 모드 실제 구현** | Stub (501 Not Implemented) | BasecampClient.workflow_register() 등 |
| **Cron 표현식 검증** | 미구현 | croniter 라이브러리 미사용 |
| **S3 연동** | 미구현 | manual/ path 업로드 로직 없음 |
| **Airflow REST API 연동** | 미구현 | 실제 Airflow 호출 없음 |

---

## 2. 확장성 분석

### 2.1 새 커맨드 추가 용이성

| 평가 항목 | 현재 상태 | 문제점 | 개선안 |
|-----------|-----------|--------|--------|
| **CLI 커맨드 추가** | Typer 패턴 사용 | `register` 커맨드 미구현됨 | Phase 1에서 register 추가 |
| **API 메서드 추가** | Facade 패턴 | 일관된 패턴으로 확장 용이 | 현재 구조 유지 |
| **Result 모델 추가** | Pydantic BaseModel | `models/workflow.py` 분리되어 관리 용이 | 현재 구조 유지 |
| **BasecampClient 메서드** | 직접 HTTP 호출 패턴 | 메서드마다 중복 코드 존재 | Helper 메서드 추출 고려 |

**확장성 점수: 8/10** - 전반적으로 좋은 구조이나 CLI register 미구현이 Gap

### 2.2 Server 연동 용이성

| 평가 항목 | 현재 상태 | 문제점 | 개선안 |
|-----------|-----------|--------|--------|
| **Mock -> Server 전환** | `mock_mode` 플래그 | mock 응답 구조가 서버 응답과 다를 수 있음 | 서버 API 스키마 정의 후 mock 데이터 정렬 |
| **BasecampClient 구현** | 501 반환 Stub | 실제 HTTP 호출 구조 미정의 | Phase 3에서 httpx/requests 도입 |
| **응답 파싱** | `response.data` 직접 접근 | 서버 응답 구조 변경 시 취약 | Response DTO 도입 고려 |
| **에러 핸들링** | status_code 기반 분기 | 서버 에러 형식 미정의 | 서버 팀과 에러 스키마 합의 필요 |

**Server 연동 준비도: 6/10** - Mock 구조는 좋으나 실제 연동 시 변경 필요

### 2.3 코드 중복/추상화

| 위치 | 중복 패턴 | 영향도 |
|------|-----------|--------|
| **WorkflowAPI 메서드** | `if self._is_mock_mode: ... else: client = self._get_client()` | 모든 메서드에서 반복 |
| **BasecampClient workflow_*` | Mock 응답 생성 로직 | 10개 메서드에서 유사 패턴 |
| **CLI 커맨드** | `project_path = get_project_path(path)` + `client = get_client(...)` | 8개 커맨드에서 반복 |
| **에러 처리** | `if response.status_code == 404: raise WorkflowNotFoundError(...)` | 여러 메서드에서 반복 |

**중복도 평가: 중간** - 현재 단계에서 허용 가능, 메서드 수 증가 시 추상화 필요

---

## 3. 사용자 혼란 가능성

### 3.1 API 네이밍 일관성

| 현재 | 다른 API 패턴 | 문제 | 제안 |
|------|---------------|------|------|
| `WorkflowAPI.list_workflows()` | `DatasetAPI.list_datasets()` | 일관성 OK | 유지 |
| `WorkflowAPI.get()` | `DatasetAPI.get()` | 일관성 OK | 유지 |
| `WorkflowAPI.history()` | 해당 없음 | Workflow 전용 기능, OK | 유지 |
| `WorkflowAPI.get_status(run_id)` | 해당 없음 | `get()`과 혼동 가능 | 문서화로 구분 명확화 |
| `WorkflowAPI.pause/unpause` | 해당 없음 | Workflow 전용, OK | 유지 |

**네이밍 일관성: 9/10** - 전반적으로 양호

### 3.2 ExecutionMode 차이

| API | 지원 모드 | 기본 모드 예시 | 혼란 요소 |
|-----|-----------|----------------|-----------|
| `DatasetAPI` | LOCAL, SERVER, MOCK | `ExecutionMode.LOCAL` | 로컬 실행 지원 |
| `MetricAPI` | LOCAL, SERVER, MOCK | `ExecutionMode.LOCAL` | 로컬 실행 지원 |
| `QualityAPI` | LOCAL, SERVER, MOCK | `ExecutionMode.LOCAL` | 로컬 실행 지원 |
| **`WorkflowAPI`** | **SERVER, MOCK only** | `ExecutionMode.SERVER` | **LOCAL 미지원** |

**혼란 요소 분석:**

1. **WorkflowAPI만 SERVER 필수** - 다른 API는 LOCAL 기본인데 Workflow는 SERVER 모드 강제
2. **예시 코드 차이**:
   ```python
   # DatasetAPI (LOCAL 기본)
   api = DatasetAPI(context=ExecutionContext(execution_mode=ExecutionMode.LOCAL))

   # WorkflowAPI (SERVER 필수)
   api = WorkflowAPI(context=ExecutionContext(
       execution_mode=ExecutionMode.SERVER,
       server_url="http://basecamp:8080",  # 필수!
   ))
   ```
3. **사용자 기대 vs 실제**:
   - 사용자 기대: "다른 API처럼 로컬에서 테스트하고 싶다"
   - 실제: "Workflow는 Airflow 기반이라 서버가 필수"

**제안:**
- P1: docstring에 "SERVER 모드 필수" 명시 강화
- P2: LOCAL 모드 시 명확한 에러 메시지 (`ConfigurationError: WorkflowAPI requires SERVER mode`)

### 3.3 에러 메시지 명확성

| 에러 상황 | 현재 메시지 | 문제 | 개선 제안 |
|-----------|-------------|------|-----------|
| CODE 워크플로우 수정 시도 | "Cannot register: CODE workflow exists" | 어떻게 해야 하는지 불명확 | "Git에서 수정하세요. CLI는 pause/unpause만 가능" |
| SERVER 모드 없이 WorkflowAPI 사용 | "server_url required for SERVER mode" | LOCAL 모드로 착각 가능 | "WorkflowAPI는 SERVER 모드만 지원. MOCK으로 테스트하세요" |
| pause된 워크플로우 run 시 | 서버 에러 반환 | 상태 설명 부족 | "워크플로우가 pause 상태. unpause 후 다시 시도하세요" |
| 없는 run_id로 status 조회 | "Run '{run_id}' not found" | 양호 | 유지 |

---

## 4. 시스템 정책 검토

### 4.1 Source Type 정책 (MANUAL vs CODE)

| 정책 | 현재 동작 | 문제점 | 제안 |
|------|-----------|--------|------|
| **MANUAL 등록** | CLI `workflow register`로 등록 | CLI 커맨드 미구현 | P0: 커맨드 구현 |
| **CODE 등록** | Git -> CI/CD -> S3 code/ | CLI에서 등록 불가 (403) | 정책 준수 OK |
| **MANUAL 삭제** | CLI `workflow unregister` | API 구현됨, CLI 미구현 | P1: CLI 추가 |
| **CODE 삭제** | Git에서만 가능 | CLI 403 반환 | 정책 준수 OK |

**정책 정합성: 8/10** - 구현 Gap만 해결하면 정책 OK

### 4.2 Override 정책

| 시나리오 | 현재 동작 | 예상 사용자 기대 | 차이 |
|----------|-----------|------------------|------|
| CODE + MANUAL 동시 존재 | CODE 우선, MANUAL은 `overridden` 상태 | MANUAL은 무시될 것 | 일치 |
| CODE 삭제 후 | MANUAL 자동 활성화 | MANUAL이 살아날 것 | 일치 |
| MANUAL 파일 보존 | S3에서 삭제 안됨 | 삭제될 수도 있다고 생각 | **차이 존재** |
| Override 상태 확인 | `workflow list`에서 `overridden` 표시 | 표시될 것 | 일치 |

**제안:**
- P2: `workflow list` 출력에 "by code" 주석 추가
- P2: `workflow get` 상세 조회에서 override 사유 표시

### 4.3 권한 모델

| 작업 | MANUAL | CODE | 문제점 |
|------|--------|------|--------|
| **register** | CLI/API | Git only | OK |
| **unregister** | CLI/API | Git only (403) | OK |
| **modify (cron 변경)** | CLI/API (`--force`) | Git only | MANUAL modify API 미구현 |
| **pause** | CLI/API | CLI/API | OK |
| **unpause** | CLI/API | CLI/API | OK, 단 overridden 시 403 |
| **run** | CLI/API | CLI/API | OK |
| **stop** | CLI/API | CLI/API | OK |

**권한 모델 정합성: 9/10** - MANUAL modify 기능 추가 필요

### 4.4 상태 전이 규칙

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkflowStatus 상태 전이                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌──────────┐        pause()        ┌──────────┐           │
│   │  active  │ ───────────────────▶  │  paused  │           │
│   └──────────┘                       └──────────┘           │
│        ▲                                   │                │
│        │          unpause()                │                │
│        └───────────────────────────────────┘                │
│                                                              │
│   ┌──────────┐   CODE 등록 시         ┌────────────┐        │
│   │  active  │ ───────────────────▶  │ overridden │        │
│   │ (MANUAL) │                       │  (MANUAL)  │        │
│   └──────────┘                       └────────────┘        │
│        ▲                                   │                │
│        │         CODE 삭제 시              │                │
│        └───────────────────────────────────┘                │
│                                                              │
│   [문제] overridden 상태에서 unpause 시도:                   │
│   - 현재: 403 에러 반환                                     │
│   - 사용자 혼란: "왜 unpause가 안 되지?"                    │
│   - 제안: 명확한 에러 메시지 필요                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**상태 전이 문제점:**

1. **overridden -> active 불가능 설명 부족**: unpause 시 "Workflow is overridden by CODE" 메시지는 있으나, 해결 방법(CODE 삭제) 안내 없음
2. **paused + overridden 동시 가능 여부 불명확**: CODE가 등록될 때 pause 상태인 MANUAL은 어떻게 되는가?

---

## 5. 리팩토링 제안

### 5.1 우선순위 P0 (필수)

| ID | 작업 | 근거 | 예상 공수 |
|----|------|------|-----------|
| **P0-1** | CLI `workflow register` 커맨드 구현 | FEATURE에 정의됨, 미구현 Gap | 2시간 |
| **P0-2** | CLI `workflow unregister` 커맨드 구현 | API 있음, CLI 없음 Gap | 1시간 |
| **P0-3** | WorkflowAPI LOCAL 모드 시 명확한 에러 | 사용자 혼란 방지 | 30분 |

### 5.2 우선순위 P1 (권장)

| ID | 작업 | 근거 | 예상 공수 |
|----|------|------|-----------|
| **P1-1** | CLI `--show-dataset-info` 옵션 추가 | FEATURE에 정의됨, 미구현 | 1시간 |
| **P1-2** | 에러 메시지에 해결 방법 추가 | 사용자 경험 개선 | 1시간 |
| **P1-3** | Cron 표현식 검증 로직 추가 | 잘못된 cron 사전 방지 | 1시간 |
| **P1-4** | Mock 데이터 현실성 개선 | 테스트 신뢰성 | 2시간 |

### 5.3 우선순위 P2 (선택)

| ID | 작업 | 근거 | 예상 공수 |
|----|------|------|-----------|
| **P2-1** | BasecampClient HTTP 호출 추상화 | 코드 중복 감소 | 3시간 |
| **P2-2** | Response DTO 도입 | 서버 연동 안정성 | 2시간 |
| **P2-3** | `workflow list` overridden 상세 표시 | 상태 이해 개선 | 1시간 |
| **P2-4** | MANUAL modify (`--force` 시 스케줄 변경) | 완전한 권한 모델 | 2시간 |
| **P2-5** | 상태 전이 다이어그램 문서화 | 사용자/개발자 이해 | 1시간 |

---

## 6. Agent 리뷰 의견

### 6.1 expert-spec Agent

**리뷰어**: `expert-spec` Agent
**리뷰 일자**: 2025-12-31

| Priority | Issue | Analysis |
|----------|-------|----------|
| **P0** | CLI register/unregister 미구현 | FEATURE 문서와 코드 간 Gap. 명세 대로 구현 필수 |
| **P0** | ExecutionMode 정책 문서화 부족 | WorkflowAPI가 SERVER 전용인 이유 설명 필요 |
| **P1** | Override 정책 사용자 가이드 부족 | 상태 전이 규칙이 명세에만 있고 에러 메시지에 없음 |
| **P1** | Mock 데이터와 Server 응답 스키마 정렬 필요 | Phase 3 Server 연동 시 혼란 방지 |
| **P2** | history 커맨드 `--show-dataset-info` 누락 | 명세된 기능이 미구현 |

**종합 의견:**
현재 구현은 Mock 모드 기준으로 잘 동작하나, CLI 커맨드 Gap(register/unregister)이 가장 시급한 문제. ExecutionMode 차이에 대한 명확한 문서화 및 에러 메시지 개선이 필요함.

### 6.2 feature-interface-cli Agent

(이 섹션은 feature-interface-cli Agent가 리뷰 후 작성)

| Priority | Issue | Resolution |
|----------|-------|------------|
| TBD | TBD | TBD |

### 6.3 expert-python Agent

(이 섹션은 expert-python Agent가 리뷰 후 작성)

| Priority | Issue | Resolution |
|----------|-------|------------|
| TBD | TBD | TBD |

---

## 7. 합의 사항

### 7.1 최종 우선순위

(Agent 리뷰 완료 후 채움)

| 순위 | 작업 ID | 작업 내용 | 담당 | 예상 완료일 |
|------|---------|-----------|------|-------------|
| 1 | P0-1 | CLI register 구현 | TBD | TBD |
| 2 | P0-2 | CLI unregister 구현 | TBD | TBD |
| 3 | P0-3 | ExecutionMode 에러 개선 | TBD | TBD |
| ... | ... | ... | ... | ... |

### 7.2 구현 순서

(Agent 리뷰 완료 후 채움)

```
Phase 1 (P0 작업)
├── Step 1: CLI register 커맨드 추가
├── Step 2: CLI unregister 커맨드 추가
└── Step 3: ExecutionMode 에러 메시지 개선

Phase 2 (P1 작업)
├── Step 4: --show-dataset-info 옵션 추가
├── Step 5: 에러 메시지 가이드 추가
└── Step 6: Cron 검증 로직 추가

Phase 3 (P2 작업, 선택)
├── Step 7: HTTP 호출 추상화
└── Step 8: Response DTO 도입
```

---

## Related Documents

- [FEATURE_WORKFLOW.md](./FEATURE_WORKFLOW.md) - Workflow 기능 상세 명세
- [RELEASE_WORKFLOW.md](./RELEASE_WORKFLOW.md) - Workflow 릴리스 체크리스트
- [STATUS.md](./STATUS.md) - 전체 기능 구현 현황

---

## Appendix A: 코드 참조

### A.1 WorkflowAPI 메서드 목록

```python
# dli/api/workflow.py - 11개 메서드
class WorkflowAPI:
    def get(self, dataset_name: str) -> WorkflowInfo | None
    def register(self, dataset_name: str, *, cron: str, ...) -> WorkflowRegisterResult
    def unregister(self, dataset_name: str) -> WorkflowRunResult
    def run(self, dataset_name: str, *, parameters: dict, ...) -> WorkflowRunResult
    def backfill(self, dataset_name: str, *, start_date: str, ...) -> WorkflowRunResult
    def stop(self, run_id: str) -> WorkflowRunResult
    def get_status(self, run_id: str) -> WorkflowStatusResult
    def list_workflows(self, *, source_type: SourceType, ...) -> WorkflowListResult
    def history(self, *, dataset_name: str, ...) -> WorkflowHistoryResult
    def pause(self, dataset_name: str) -> WorkflowRunResult
    def unpause(self, dataset_name: str) -> WorkflowRunResult
```

### A.2 CLI 커맨드 목록

```python
# dli/commands/workflow.py - 8개 커맨드 (구현됨)
workflow_app.command("run")       # run_workflow
workflow_app.command("backfill")  # backfill_workflow
workflow_app.command("stop")      # stop_workflow
workflow_app.command("status")    # status_workflow
workflow_app.command("list")      # list_workflows
workflow_app.command("history")   # history_workflow
workflow_app.command("pause")     # pause_workflow
workflow_app.command("unpause")   # unpause_workflow

# 미구현 (FEATURE에 정의됨)
# workflow_app.command("register")    # P0-1
# workflow_app.command("unregister")  # P0-2
```

### A.3 BasecampClient 메서드 목록

```python
# dli/core/client.py - 10개 workflow_* 메서드
def workflow_run(self, dataset_name: str, params: dict, dry_run: bool) -> ServerResponse
def workflow_backfill(self, dataset_name: str, start_date: str, ...) -> ServerResponse
def workflow_stop(self, run_id: str) -> ServerResponse
def workflow_status(self, run_id: str) -> ServerResponse
def workflow_list(self, *, source: str, ...) -> ServerResponse
def workflow_history(self, *, dataset_filter: str, ...) -> ServerResponse
def workflow_pause(self, dataset_name: str) -> ServerResponse
def workflow_unpause(self, dataset_name: str) -> ServerResponse
def workflow_register(self, dataset_name: str, cron: str, ...) -> ServerResponse
def workflow_unregister(self, dataset_name: str) -> ServerResponse
```

---

## Appendix B: ExecutionMode 비교표

| API | LOCAL | SERVER | MOCK | 기본 예시 |
|-----|:-----:|:------:|:----:|-----------|
| DatasetAPI | O | O | O | `ExecutionMode.LOCAL` |
| MetricAPI | O | O | O | `ExecutionMode.LOCAL` |
| TranspileAPI | O | O | O | `ExecutionMode.MOCK` |
| CatalogAPI | O | O | O | `ExecutionMode.MOCK` |
| ConfigAPI | O | O | O | `ExecutionMode.MOCK` |
| QualityAPI | O | O | O | `ExecutionMode.LOCAL` |
| **WorkflowAPI** | **X** | **O** | **O** | `ExecutionMode.SERVER` |

**WorkflowAPI가 LOCAL 미지원 이유:**
- Workflow는 Airflow 스케줄러 기반 실행
- 로컬에서 Airflow DAG 직접 실행 불가
- SERVER 모드: Basecamp Server -> Airflow REST API 호출
- MOCK 모드: 테스트용 시뮬레이션

---

## Appendix C: 에러 코드 범위

| 범위 | 카테고리 | 예시 |
|------|----------|------|
| DLI-0xx | Configuration | `CONFIG_INVALID` (DLI-001) |
| DLI-1xx | Not Found | `DATASET_NOT_FOUND` (DLI-101) |
| DLI-2xx | Validation | `VALIDATION_FAILED` (DLI-201) |
| DLI-3xx | Transpile | `TRANSPILE_FAILED` (DLI-301) |
| DLI-4xx | Execution | `EXECUTION_FAILED` (DLI-401) |
| DLI-5xx | Server | `SERVER_UNREACHABLE` (DLI-501) |
| DLI-6xx | Quality | `QUALITY_SPEC_NOT_FOUND` (DLI-601) |
| DLI-7xx | Catalog | `CATALOG_CONNECTION_ERROR` (DLI-701) |
| **DLI-8xx** | **Workflow** | `WORKFLOW_NOT_FOUND` (DLI-800) |
