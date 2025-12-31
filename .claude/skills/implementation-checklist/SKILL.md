# Implementation Checklist Skill

FEATURE 문서에서 구현 항목을 자동 추출하여 체크리스트를 생성하는 프로세스.

## 문제 배경

Agent가 FEATURE 문서의 일부만 구현하고 "완료"를 선언하는 문제 발생:
- API만 구현하고 CLI 커맨드 누락
- FEATURE Section 4 (API)만 완료하고 Section 5 (CLI) 건너뜀
- 테스트 파일 미생성

## 적용 시점

이 skill은 다음 상황에서 적용:
- FEATURE_*.md 기반 구현 시작 시
- "구현 완료", "done" 선언 전 검증 시
- RELEASE_*.md 작성 전 점검 시

---

## 체크리스트 생성 프로세스

### Step 1: FEATURE 문서 파싱

FEATURE_*.md에서 다음 항목을 추출:

```markdown
### 추출 대상

1. **API Methods**: "## N. API Design" 또는 "## N. Library API" 섹션
   - 클래스명: `class XXXApi`
   - 메서드 목록: 테이블의 Method 열

2. **CLI Commands**: "## N. CLI Commands" 섹션
   - 커맨드 목록: `dli xxx <subcommand>` 형태
   - 옵션 목록: `--option-name` 형태

3. **Models**: "## N. Data Models" 또는 코드 예시에서
   - 클래스명: `class XXXResult`, `class XXXInfo`

4. **Exceptions**: "## N. Error Handling" 또는 "## N. Exceptions" 섹션
   - 에러 코드: `DLI-XXX` 형태
   - 예외 클래스: `XXXError`

5. **Tests**: "## N. Test Plan" 섹션 또는 암묵적 기대
   - API 테스트: `tests/api/test_xxx_api.py`
   - CLI 테스트: `tests/cli/test_xxx_cmd.py`

6. **Enum Values (신규 2026-01-01)**: Data Models 섹션 내 Enum 정의
   - 모든 Enum 값이 실제 로직에서 사용되어야 함
   - **Dead Code 방지**: 정의만 되고 사용되지 않는 Enum 값은 BLOCKER

7. **Integration Points (신규 2026-01-01)**: 기존 모듈과 연동 필요 항목
   - 새 모듈이 기존 관련 모듈과 연결되어야 함
   - **예시**: Transpile의 Jinja 연동, Workflow의 Airflow 연동
```

### Step 2: 체크리스트 생성

```markdown
## Implementation Checklist: FEATURE_XXX

### API (Section 4)
- [ ] `class XXXApi` in `api/xxx.py`
- [ ] `XXXApi.method_a()` - description
- [ ] `XXXApi.method_b()` - description
- [ ] ...

### CLI Commands (Section 5)
- [ ] `@xxx_app.command("subcommand_a")` in `commands/xxx.py`
- [ ] `@xxx_app.command("subcommand_b")` in `commands/xxx.py`
- [ ] `--option-name` 옵션 in `commands/xxx.py`
- [ ] ...

### Models (Section 3 or embedded)
- [ ] `class XXXResult` in `models/xxx.py`
- [ ] `class XXXInfo` in `models/xxx.py`
- [ ] ...

### Exceptions (Section 7 or embedded)
- [ ] `DLI-XXX` error code in `exceptions.py`
- [ ] `class XXXError` in `exceptions.py`
- [ ] ...

### Tests
- [ ] `tests/api/test_xxx_api.py` exists
- [ ] `tests/cli/test_xxx_cmd.py` exists
- [ ] pytest passing for all test files

### Exports
- [ ] `XXXApi` exported in `__init__.py`
- [ ] `XXXError` exported in `__init__.py`
```

### Step 3: grep 기반 검증

각 체크리스트 항목에 대해 grep 검증:

```bash
# API 클래스 존재 확인
grep -r "class XXXApi" src/dli/api/

# CLI 커맨드 존재 확인
grep -r "@xxx_app.command(\"subcommand_a\")" src/dli/commands/

# 옵션 존재 확인
grep -r "\-\-option-name" src/dli/commands/xxx.py

# 모델 클래스 존재 확인
grep -r "class XXXResult" src/dli/models/

# 에러 코드 존재 확인
grep -r "DLI-XXX" src/dli/exceptions.py

# 테스트 파일 존재 확인
ls tests/api/test_xxx_api.py tests/cli/test_xxx_cmd.py
```

---

## 체크리스트 출력 형식

### 구현 전 (Planning)

```markdown
## Implementation Checklist: FEATURE_WORKFLOW

Generated from: FEATURE_WORKFLOW.md v3.0.0

### Phase 1 MVP Items

| Category | Item | Location | Status |
|----------|------|----------|--------|
| API | `WorkflowAPI.register()` | `api/workflow.py` | ⏳ Pending |
| API | `WorkflowAPI.unregister()` | `api/workflow.py` | ⏳ Pending |
| CLI | `@workflow_app.command("register")` | `commands/workflow.py` | ⏳ Pending |
| CLI | `@workflow_app.command("unregister")` | `commands/workflow.py` | ⏳ Pending |
| CLI | `--show-dataset-info` option | `commands/workflow.py` | ⏳ Pending |
| Test | `test_workflow_api.py` | `tests/api/` | ⏳ Pending |
| Test | `test_workflow_cmd.py` | `tests/cli/` | ⏳ Pending |
```

### 구현 중 (Tracking)

```markdown
## Implementation Checklist: FEATURE_WORKFLOW

### Progress: 5/7 items complete (71%)

| Category | Item | Location | Status | Verified |
|----------|------|----------|--------|----------|
| API | `WorkflowAPI.register()` | `api/workflow.py` | ✅ Done | grep OK |
| API | `WorkflowAPI.unregister()` | `api/workflow.py` | ✅ Done | grep OK |
| CLI | `@workflow_app.command("register")` | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| CLI | `@workflow_app.command("unregister")` | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| CLI | `--show-dataset-info` option | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| Test | `test_workflow_api.py` | `tests/api/` | ✅ Done | file exists |
| Test | `test_workflow_cmd.py` | `tests/cli/` | ❌ Missing | file not found |
```

### 구현 완료 (Verification)

```markdown
## Implementation Verification: FEATURE_WORKFLOW

### Result: 7/7 items complete (100%) ✅

| Category | Item | Verified By |
|----------|------|-------------|
| API | `WorkflowAPI.register()` | `grep -r "def register" src/dli/api/workflow.py` |
| API | `WorkflowAPI.unregister()` | `grep -r "def unregister" src/dli/api/workflow.py` |
| CLI | `register` command | `grep -r "@workflow_app.command(\"register\")" src/` |
| CLI | `unregister` command | `grep -r "@workflow_app.command(\"unregister\")" src/` |
| CLI | `--show-dataset-info` | `grep -r "show-dataset-info" src/dli/commands/workflow.py` |
| Test | API tests | `ls tests/api/test_workflow_api.py` exists |
| Test | CLI tests | `ls tests/cli/test_workflow_cmd.py` exists |

### Test Results
```
pytest tests/api/test_workflow_api.py → 59 passed
pytest tests/cli/test_workflow_cmd.py → 25 passed
pytest tests/ → 1740 passed
```

### Ready for RELEASE ✅
```

---

## 관련 Skills

- `implementation-verification`: 구현 완료 검증 (이 skill과 함께 사용)
- `completion-gate`: 완료 선언 Gate (체크리스트 완료 후 적용)
- `spec-validation`: 명세 품질 검증
- `testing`: TDD 워크플로우

---

## Integration with Agent Workflow

```
FEATURE_*.md 수신
       ↓
[implementation-checklist skill 적용]
       ↓
체크리스트 생성
       ↓
구현 진행 (API → CLI → Tests)
       ↓
각 항목 완료 시 grep 검증
       ↓
체크리스트 100% 완료
       ↓
[completion-gate skill 적용]
       ↓
RELEASE_*.md 작성
```
