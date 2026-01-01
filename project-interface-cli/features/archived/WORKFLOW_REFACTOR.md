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
| **`workflow register` CLI 커맨드** | 미구현 | WORKFLOW_FEATURE.md에 정의됨, commands/workflow.py에 없음 |
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

**리뷰어**: `feature-interface-cli` Agent
**리뷰 일자**: 2025-12-31

#### 6.2.1 기술 검토 결과

**CLI 패턴 준수 (9/10)**

| 항목 | 상태 | 비고 |
|------|------|------|
| `workflow_app` Typer 생성 | OK | `no_args_is_help=True` 적용됨 |
| `--format`, `--path` 옵션 | OK | `ListOutputFormat`, `get_project_path()` 재사용 |
| `--param` 옵션 | OK | `parse_params()` 유틸리티 활용 |
| Rich 출력 | OK | `Table`, `console.status`, 색상 스타일 적용 |
| 에러 처리 | OK | `print_error()` + `typer.Exit(1)` 패턴 |
| JSON 출력 | OK | `--format json` 시 `console.print_json()` 사용 |

**dataset.py와의 일관성 분석:**

```python
# dataset.py 패턴 (기존)
@dataset_app.command("list")
def list_datasets(
    source: Annotated[SourceType, typer.Option("--source", "-s")] = "local",
    ...
)

# workflow.py 패턴 (현재)
@workflow_app.command("list")
def list_workflows(
    source: Annotated[WorkflowSourceType, typer.Option("--source")] = "all",
    ...
)
```

**차이점 발견:**
- `dataset.py`는 `SourceType`을 `commands/base.py`에서 import
- `workflow.py`는 `WorkflowSourceType`을 로컬에서 정의 (Literal["code", "manual", "all"])
- **권장**: `WorkflowSourceType`을 `commands/base.py`로 이동하여 일관성 유지

**Library API 패턴 준수 (10/10)**

| 항목 | 상태 | 비고 |
|------|------|------|
| Facade 패턴 | OK | `WorkflowAPI` -> `BasecampClient` 래핑 |
| Lazy 초기화 | OK | `_get_client()` 메서드 |
| Mock 모드 | OK | `_is_mock_mode` 프로퍼티 |
| DI 지원 | OK | `client: BasecampClient | None = None` 파라미터 |
| Result 모델 | OK | `WorkflowRunResult`, `WorkflowListResult` 등 frozen 모델 |
| Exception 처리 | OK | `WorkflowNotFoundError`, `WorkflowExecutionError` 등 |

**DatasetAPI와 비교:**
- `WorkflowAPI`가 더 완성도 높은 구조
- 모든 메서드에 docstring과 예시 코드 포함
- Type hint 100% 적용

**테스트 패턴 준수 (9/10)**

| 항목 | 상태 | 비고 |
|------|------|------|
| 클래스 기반 테스트 | OK | `TestWorkflowAPIRun`, `TestWorkflowAPIList` 등 |
| Fixture 활용 | OK | `mock_context`, `mock_api` 정의 |
| Mock 모드 테스트 | OK | 모든 메서드에 mock 테스트 존재 |
| 에러 케이스 | OK | `ConfigurationError` 등 예외 테스트 |
| 모델 속성 테스트 | OK | `is_running`, `is_terminal`, `duration_seconds` 등 |
| Frozen 검증 | OK | 불변성 테스트 포함 |

**미흡한 점:**
- CLI 커맨드 테스트 (`test_workflow_cmd.py`) 파일이 없음
- Server 모드 통합 테스트 없음 (Mock만 존재)

#### 6.2.2 발견된 추가 문제점

| Priority | Issue | 상세 |
|----------|-------|------|
| **P0** | CLI 테스트 파일 누락 | `tests/cli/test_workflow_cmd.py` 미존재. 다른 커맨드는 모두 CLI 테스트 보유 |
| **P0** | `workflow_register` 미구현 확인 | `mcp__jetbrains__search_in_files_by_text`로 검색 결과 0건 - CLI에 register 커맨드 없음 |
| **P1** | `WorkflowSourceType` 위치 | 로컬 Literal 대신 `commands/base.py`로 이동 권장 |
| **P1** | `history --show-dataset-info` 미구현 | FEATURE에 정의된 옵션이 CLI에 없음 |
| **P2** | Mock 데이터 하드코딩 | `WorkflowAPI`의 mock 응답이 상수로 하드코딩됨 |
| **P2** | `format_datetime` 일관성 | `workflow.py`에서 `include_seconds=True` 사용, 다른 커맨드는 기본값 사용 |

#### 6.2.3 expert-spec 제안에 대한 의견

| ID | expert-spec 제안 | 동의 여부 | feature-interface-cli 의견 |
|----|------------------|-----------|---------------------------|
| **P0-1** | CLI `register` 구현 | **동의** | API는 구현됨, CLI만 추가하면 됨. 예상 30분 |
| **P0-2** | CLI `unregister` 구현 | **동의** | 동일. 예상 20분 |
| **P0-3** | LOCAL 모드 에러 개선 | **동의** | 단, `ConfigurationError` 대신 `ExecutionModeError` 신규 예외 제안 |
| **P1-1** | `--show-dataset-info` 추가 | **동의** | API에 이미 `include_dataset_info` 파라미터 존재 |
| **P1-2** | 에러 메시지 가이드 추가 | **부분 동의** | 에러 메시지보다 `--help` 개선이 더 효과적 |
| **P1-3** | Cron 검증 로직 | **동의** | `croniter` 라이브러리 추가 필요, dev dependency로 |
| **P1-4** | Mock 데이터 현실성 | **부분 동의** | 테스트 목적상 현재 수준 충분, Server 연동 시 조정 |
| **P2-1** | HTTP 호출 추상화 | **연기 권장** | Server 연동 확정 후 진행 |
| **P2-2** | Response DTO 도입 | **동의** | 서버 스키마 정의 후 동시 진행 |

#### 6.2.4 구현 권장사항 (코드 레벨)

**1. CLI register/unregister 구현 템플릿:**

```python
# commands/workflow.py에 추가

@workflow_app.command("register")
def register_workflow(
    dataset_name: Annotated[str, typer.Argument(help="Dataset name to register.")],
    cron: Annotated[str, typer.Option("--cron", "-c", help="Cron expression (e.g., '0 9 * * *').")],
    timezone: Annotated[str, typer.Option("--timezone", "-z", help="IANA timezone.")] = "UTC",
    enabled: Annotated[bool, typer.Option("--enabled/--disabled", help="Enable schedule.")] = True,
    force: Annotated[bool, typer.Option("--force", "-f", help="Overwrite existing.")] = False,
    path: Annotated[Path | None, typer.Option("--path", help="Project path.")] = None,
) -> None:
    """Register a local dataset as MANUAL workflow.

    Examples:
        dli workflow register iceberg.analytics.daily_clicks -c "0 9 * * *"
        dli workflow register iceberg.analytics.daily_clicks -c "0 10 * * *" -z Asia/Seoul
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Registering workflow..."):
        response = client.workflow_register(
            dataset_name=dataset_name,
            cron=cron,
            timezone=timezone,
            enabled=enabled,
            force=force,
        )

    if not response.success:
        print_error(response.error or "Failed to register workflow")
        raise typer.Exit(1)

    print_success(f"Workflow registered: {dataset_name}")
    console.print(f"  [dim]Schedule:[/dim] {cron} ({timezone})")
    console.print(f"  [dim]Enabled:[/dim] {'Yes' if enabled else 'No'}")
```

**2. CLI 테스트 파일 생성 필요:**

```python
# tests/cli/test_workflow_cmd.py

class TestWorkflowList:
    def test_list_default(self) -> None:
        result = runner.invoke(app, ["workflow", "list"])
        assert result.exit_code == 0

    def test_list_json_format(self) -> None:
        result = runner.invoke(app, ["workflow", "list", "--format", "json"])
        assert result.exit_code == 0
        json.loads(result.output)

class TestWorkflowRegister:
    def test_register_success(self) -> None:
        result = runner.invoke(app, [
            "workflow", "register",
            "test.dataset",
            "--cron", "0 9 * * *"
        ])
        assert result.exit_code == 0
        assert "registered" in result.output.lower()
```

**3. `WorkflowSourceType` 이동:**

```python
# commands/base.py에 추가
WorkflowSourceType = Literal["code", "manual", "all"]

# commands/workflow.py에서 import
from dli.commands.base import WorkflowSourceType
```

#### 6.2.5 우선순위 조정 의견

| 순위 | 작업 ID | 조정 전 | 조정 후 | 사유 |
|------|---------|---------|---------|------|
| 1 | **NEW** | - | **P0** | CLI 테스트 파일 생성 (`test_workflow_cmd.py`) - 품질 게이트 필수 |
| 2 | P0-1 | P0 | P0 | 유지 (API 있음, CLI만 추가) |
| 3 | P0-2 | P0 | P0 | 유지 |
| 4 | P1-1 | P1 | **P0** | FEATURE에 명시된 필수 기능 |
| 5 | P0-3 | P0 | P1 | 에러 개선은 기능 완성 후 |
| 6 | P1-3 | P1 | P1 | Cron 검증 유지 |
| 7 | P1-2 | P1 | P2 | `--help` 개선으로 대체 가능 |

**최종 권장 구현 순서:**

```
Phase 1 (즉시 - 2시간)
├── Step 1: CLI register 커맨드 추가 (30분)
├── Step 2: CLI unregister 커맨드 추가 (20분)
├── Step 3: history --show-dataset-info 옵션 추가 (30분)
└── Step 4: test_workflow_cmd.py 생성 (40분)

Phase 2 (권장 - 2시간)
├── Step 5: Cron 검증 로직 추가 (croniter) (1시간)
├── Step 6: ExecutionMode 에러 메시지 개선 (30분)
└── Step 7: WorkflowSourceType base.py 이동 (30분)

Phase 3 (Server 연동 시)
├── Step 8: Response DTO 도입
└── Step 9: HTTP 호출 추상화
```

**종합 평가:**
- **코드 품질**: 9/10 - API와 모델 구현 우수, CLI Gap만 존재
- **패턴 준수**: 9/10 - 기존 dataset/metric 패턴과 높은 일관성
- **테스트 커버리지**: 7/10 - API 테스트 우수, CLI 테스트 부재
- **확장성**: 8/10 - 새 커맨드 추가 용이, Server 연동 준비 필요

### 6.3 expert-python Agent

**리뷰어**: `expert-python` Agent
**리뷰 일자**: 2025-12-31
**코드 품질 등급**: **B+** (Good)

#### 6.3.1 파일별 코드 품질 평가

| 파일 | 줄 수 | Type Hints | DRY | 문서화 | 테스트 | 등급 |
|------|-------|------------|-----|--------|--------|------|
| `api/workflow.py` | 895 | **A** (완전) | **C** (반복 패턴) | **A** (우수) | **A** | **B+** |
| `models/workflow.py` | 202 | **A** (완전) | **A** | **A** (우수) | **A** | **A** |
| `commands/workflow.py` | 570 | **A** (완전) | **C** (반복 패턴) | **B** | **B** | **B** |
| `core/client.py` (workflow) | ~500 | **A** (완전) | **C** (중복 로직) | **A** | **B** | **B+** |
| `tests/api/test_workflow_api.py` | 805 | **A** | **A** | **A** | N/A | **A** |

#### 6.3.2 발견된 코드 이슈

| Priority | Issue | 위치 | 영향 | Resolution |
|----------|-------|------|------|------------|
| **P0** | CLI `register`/`unregister` 커맨드 미구현 | `commands/workflow.py` | API-CLI Gap | P0-1, P0-2 동의 |
| **P1** | Mock 모드 체크 반복 (`if self._is_mock_mode:...`) | `api/workflow.py` 모든 메서드 | 유지보수성 저하 | Template Method 또는 데코레이터 패턴 적용 |
| **P1** | 에러 메시지에 해결 가이드 부족 | `api/workflow.py`, `exceptions.py` | 사용자 경험 | hint 필드 활용 |
| **P1** | `_get_client()` 반복 호출 패턴 | `commands/workflow.py` 8개 커맨드 | 코드 중복 | Context Manager 또는 데코레이터 |
| **P2** | `WorkflowStatusResult.run_type` 타입이 `str` | `models/workflow.py:144` | 타입 안전성 | `RunType` Enum 사용 (현재 혼용) |
| **P2** | `stop()` 메서드에서 `dataset_name="unknown"` 하드코딩 | `api/workflow.py:523,542` | 정보 손실 | 서버 응답에서 추출 또는 추적 |
| **P2** | `history()` 메서드 `dataset_info` 로직 불완전 | `api/workflow.py:787-795` | Mock/Server 차이 | Server 모드에서 실제 API 호출 필요 |
| **P3** | 테스트에서 `MagicMock` 미사용 경고 | `test_workflow_api.py:16` | import but unused | 제거 또는 활용 |

#### 6.3.3 expert-spec 제안에 대한 의견

| expert-spec 제안 | 동의/반대 | 이유 |
|------------------|-----------|------|
| **P0-1**: CLI register 구현 | **동의** | API 존재, CLI Gap. 즉시 구현 가능 |
| **P0-2**: CLI unregister 구현 | **동의** | 동일한 이유 |
| **P0-3**: LOCAL 모드 에러 개선 | **동의** | 단, 에러 메시지만이 아닌 `__init__`에서 조기 검증 권장 |
| **P1-1**: `--show-dataset-info` 옵션 | **반대 (P2로 하향)** | 현재 Mock 구현도 placeholder 수준. Server 연동 후 의미 있음 |
| **P1-2**: 에러 메시지 가이드 추가 | **동의** | `DLIError.hint` 필드 적극 활용 필요 |
| **P1-3**: Cron 검증 로직 | **동의** | `croniter` 라이브러리 추가 시 register에서 사전 검증 |
| **P2-1**: HTTP 호출 추상화 | **동의** | 향후 Server 연동 시 필수. 지금은 시기상조 |
| **P2-2**: Response DTO 도입 | **동의** | Mock/Server 응답 구조 정렬 필요 |

#### 6.3.4 추가 리팩토링 제안 (Python 관점)

| ID | 작업 | 근거 | 예상 공수 | 우선순위 |
|----|------|------|-----------|----------|
| **PY-1** | `_is_mock_mode` 분기 제거: Strategy 패턴 적용 | DRY 원칙 위반, 11개 메서드에서 동일 패턴 반복 | 4시간 | P2 |
| **PY-2** | `WorkflowAPI.__init__`에서 LOCAL 모드 조기 검증 | 사용 시점 아닌 생성 시점 에러가 명확함 | 30분 | P0 |
| **PY-3** | CLI 커맨드 데코레이터로 공통 로직 추출 | `get_project_path` + `get_client` 반복 제거 | 2시간 | P2 |
| **PY-4** | `run_type` 필드를 `str` 대신 `RunType` Enum 통일 | 타입 안전성 향상 | 1시간 | P1 |
| **PY-5** | `@cached_property`로 `_client` lazy init 개선 | 표준 라이브러리 패턴 활용 | 30분 | P3 |
| **PY-6** | 테스트 Server 모드 커버리지 강화 | 현재 Mock 모드 위주, Server 모드 에러 경로 테스트 부족 | 3시간 | P1 |

#### 6.3.5 코드 품질 상세 분석

**강점:**
1. **완전한 Type Hints**: 모든 public 메서드에 반환 타입, 매개변수 타입 명시됨
2. **Pydantic 모델 설계 우수**: `frozen=True`, `Field(description=...)` 패턴 일관적
3. **풍부한 docstring**: Google style 준수, 예시 코드 포함
4. **명확한 Exception 계층**: ErrorCode 기반, @dataclass 패턴
5. **테스트 구조 우수**: 클래스 기반 조직, fixture 재사용, 805줄 커버리지

**약점:**
1. **DRY 위반 (주요)**:
   ```python
   # api/workflow.py - 11개 메서드에서 반복
   if self._is_mock_mode:
       return MockResult(...)
   client = self._get_client()
   response = client.workflow_xxx(...)
   ```

2. **CLI 보일러플레이트**:
   ```python
   # commands/workflow.py - 8개 커맨드에서 반복
   project_path = get_project_path(path)
   client = get_client(project_path)
   ```

3. **에러 메시지 가이드 부재**:
   ```python
   # 현재: 문제만 알려줌
   raise WorkflowPermissionError(message="Cannot register: CODE workflow exists")

   # 권장: 해결 방법 제시
   raise WorkflowPermissionError(
       message="Cannot register: CODE workflow exists",
       hint="Git에서 workflow 정의를 수정하거나 삭제하세요"
   )
   ```

#### 6.3.6 우선순위 조정 의견

**최종 권장 우선순위:**

| 순위 | 작업 ID | 내용 | 근거 |
|------|---------|------|------|
| 1 | **P0-1** | CLI register 구현 | API-CLI Gap 해소 |
| 2 | **P0-2** | CLI unregister 구현 | API-CLI Gap 해소 |
| 3 | **PY-2** | LOCAL 모드 조기 검증 | 사용자 혼란 방지, 30분 |
| 4 | **P1-2** | 에러 메시지 hint 추가 | UX 개선, 1시간 |
| 5 | **PY-4** | run_type Enum 통일 | 타입 안전성 |
| 6 | **PY-6** | Server 모드 테스트 강화 | 안정성 |
| 7 | **P1-3** | Cron 검증 (croniter) | 사전 오류 방지 |
| 8 | **PY-1** | Strategy 패턴 적용 | P2로 유지, Server 연동 전 |

**종합 의견:**
현재 Workflow 코드는 **Mock 모드 기준 완성도가 높음**. Type hints, Pydantic 모델, Exception 계층 모두 프로젝트 표준을 잘 따르고 있음. 주요 문제는 CLI 커맨드 Gap(P0)과 DRY 위반(P1-P2). DRY 위반은 Server 연동 시 자연스럽게 리팩토링되므로 현 단계에서는 P0 작업과 에러 메시지 개선에 집중 권장.

---

## 7. 합의 사항

> **합의 일자**: 2025-12-31
> **참여 Agent**: expert-spec, feature-interface-cli, expert-python
> **사용자 검토**: 완료

### 7.1 최종 우선순위

| 순위 | 작업 ID | 작업 내용 | 예상 공수 | 상태 |
|------|---------|-----------|-----------|------|
| 1 | **P0-1** | CLI `workflow register` 커맨드 구현 | 30분 | ⏳ 대기 |
| 2 | **P0-2** | CLI `workflow unregister` 커맨드 구현 | 20분 | ⏳ 대기 |
| 3 | **P0-3** | `__init__`에서 LOCAL 모드 조기 검증 | 30분 | ⏳ 대기 |
| 4 | **P1-NEW** | `tests/cli/test_workflow_cmd.py` 생성 | 40분 | ⏳ 대기 |
| 5 | **P1-2** | 에러 메시지에 hint 가이드 추가 | 1시간 | ⏳ 대기 |
| 6 | **P1-3** | Cron 표현식 검증 (croniter) | 1시간 | ⏳ 대기 |
| 7 | P2-1 | `--show-dataset-info` 옵션 | Server 연동 시 | 📅 연기 |
| 8 | P2-2 | HTTP 호출 추상화 | Server 연동 시 | 📅 연기 |

**총 예상 작업 시간**: ~4시간 (P0 + P1)

### 7.2 합의된 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| 작업 범위 | **P0 + P1** | 즉시 필요한 기능 완성 + 품질 보장 |
| `--show-dataset-info` | **P2 연기** | Server 연동 전까지 Mock 데이터 불완전 |
| CLI 테스트 파일 | **필수 생성** | 다른 커맨드와 동일 품질 기준 적용 |
| Strategy 패턴 (DRY) | **P2 연기** | Server 연동 시 자연스럽게 리팩토링 |

### 7.3 구현 순서

```
Phase 1 - 즉시 구현 (~1.5시간)
├── Step 1: CLI register 커맨드 추가
├── Step 2: CLI unregister 커맨드 추가
└── Step 3: WorkflowAPI.__init__ LOCAL 모드 검증

Phase 2 - 품질 강화 (~2.5시간)
├── Step 4: test_workflow_cmd.py 생성 (CLI 테스트)
├── Step 5: DLIError hint 필드 활용 (에러 가이드)
└── Step 6: croniter 기반 Cron 검증

Phase 3 - Server 연동 시 (예정)
├── Step 7: --show-dataset-info 옵션
├── Step 8: Response DTO 도입
└── Step 9: HTTP 호출 추상화 (Strategy 패턴)
```

### 7.4 Agent 의견 차이 해소

| 항목 | expert-spec | feature-cli | expert-python | 최종 결정 |
|------|-------------|-------------|---------------|-----------|
| CLI register | P0 | P0 | P0 | **P0** ✅ |
| CLI unregister | P0 | P0 | P0 | **P0** ✅ |
| LOCAL 검증 | P0-3 | P1 | P0 (PY-2) | **P0** (조기 검증) |
| dataset-info | P1-1 | P0 ⬆️ | P2 ⬇️ | **P2** (연기) |
| CLI 테스트 | 미언급 | P0 (신규) | 미언급 | **P1** (필수) |
| Cron 검증 | P1-3 | P1 | P1 | **P1** ✅ |

---

## Related Documents

- [WORKFLOW_FEATURE.md](./WORKFLOW_FEATURE.md) - Workflow 기능 상세 명세
- [WORKFLOW_RELEASE.md](./WORKFLOW_RELEASE.md) - Workflow 릴리스 체크리스트
- [_STATUS.md](./_STATUS.md) - 전체 기능 구현 현황

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
