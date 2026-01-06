# Implementation Checklist Skill

FEATURE 문서에서 구현 항목을 자동 추출하여 체크리스트를 생성하는 프로세스.

## 문제 배경

Agent가 FEATURE 문서의 일부만 구현하고 "완료"를 선언하는 문제 발생:
- API만 구현하고 CLI 커맨드 누락
- FEATURE Section 4 (API)만 완료하고 Section 5 (CLI) 건너뜀
- 테스트 파일 미생성

## 적용 시점

이 skill은 다음 상황에서 적용:
- *_FEATURE.md 기반 구현 시작 시
- "구현 완료", "done" 선언 전 검증 시
- *_RELEASE.md 작성 전 점검 시

---

## 체크리스트 생성 프로세스

### Step 1: FEATURE 문서 파싱

*_FEATURE.md에서 다음 항목을 추출:

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
   - **Test File Existence**: 새 모듈마다 테스트 파일 필수 생성

6. **Exception Integration (신규 2026-01-01)**: 커스텀 예외 검증
   - 모든 커스텀 예외는 `DLIError` 상속 필수
   - `exceptions.py`에 정의 및 `__init__.py`에서 export
   - **상속 체인 검증**: `grep -r "class XXXError(DLIError)"` 결과 확인

7. **API-CLI Coupling (신규 2026-01-01)**: CLI 기능에 대응하는 API 클래스
   - CLI 커맨드 추가 시 해당 Library API 클래스 필수 생성
   - **예시**: `dli quality` CLI → `QualityAPI` 클래스 필수
   - API 클래스는 `api/` 디렉토리에 위치

8. **Enum Values (신규 2026-01-01)**: Data Models 섹션 내 Enum 정의
   - 모든 Enum 값이 실제 로직에서 사용되어야 함
   - **Dead Code 방지**: 정의만 되고 사용되지 않는 Enum 값은 BLOCKER

9. **Integration Points (신규 2026-01-01)**: 기존 모듈과 연동 필요 항목
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
- [ ] `class XXXError(DLIError)` in `exceptions.py` - **must extend DLIError**
- [ ] ...

### Exception Integration Check
- [ ] All custom exceptions extend `DLIError` base class
- [ ] Exception hierarchy verified: `grep "class XXXError(DLIError)"`

### API-CLI Coupling Check
- [ ] CLI feature has corresponding Library API class
- [ ] `class XXXApi` exists in `api/xxx.py` for `dli xxx` command

### Tests
- [ ] `tests/api/test_xxx_api.py` exists
- [ ] `tests/cli/test_xxx_cmd.py` exists
- [ ] pytest passing for all test files
- [ ] **New modules have test files**: every new `.py` in `api/` or `commands/` has corresponding test

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

# Exception Integration Check: DLIError 상속 검증
grep -r "class XXXError(DLIError)" src/dli/exceptions.py
# 모든 커스텀 예외가 DLIError를 상속하는지 확인
grep -E "class \w+Error\(" src/dli/exceptions.py | grep -v "DLIError"
# 위 결과가 비어있어야 함 (모두 DLIError 상속)

# API-CLI Coupling Check: CLI 기능에 대응하는 API 클래스 확인
# CLI 커맨드 목록
grep -r "= typer.Typer" src/dli/commands/ | sed 's/.*\///'
# API 클래스 목록
grep -r "class .*API" src/dli/api/ | sed 's/.*class //' | sed 's/(.*/:/'
# 둘을 비교하여 누락된 API 확인

# Test File Check: 새 모듈에 대한 테스트 파일 존재 확인
ls tests/api/test_xxx_api.py tests/cli/test_xxx_cmd.py

# 모든 api/*.py 파일에 대응하는 테스트 존재 확인
for f in src/dli/api/*.py; do
  base=$(basename "$f" .py)
  if [ "$base" != "__init__" ]; then
    test -f "tests/api/test_${base}_api.py" || echo "Missing: tests/api/test_${base}_api.py"
  fi
done

# 모든 commands/*.py 파일에 대응하는 테스트 존재 확인
for f in src/dli/commands/*.py; do
  base=$(basename "$f" .py)
  if [ "$base" != "__init__" ] && [ "$base" != "base" ] && [ "$base" != "utils" ]; then
    test -f "tests/cli/test_${base}_cmd.py" || echo "Missing: tests/cli/test_${base}_cmd.py"
  fi
done
```

---

## 체크리스트 출력 형식

### 구현 전 (Planning)

```markdown
## Implementation Checklist: FEATURE_WORKFLOW

Generated from: WORKFLOW_FEATURE.md v3.0.0

### Phase 1 MVP Items

| Category | Item | Location | Status |
|----------|------|----------|--------|
| API | `WorkflowAPI.register()` | `api/workflow.py` | ⏳ Pending |
| API | `WorkflowAPI.unregister()` | `api/workflow.py` | ⏳ Pending |
| CLI | `@workflow_app.command("register")` | `commands/workflow.py` | ⏳ Pending |
| CLI | `@workflow_app.command("unregister")` | `commands/workflow.py` | ⏳ Pending |
| CLI | `--show-dataset-info` option | `commands/workflow.py` | ⏳ Pending |
| Exception | `WorkflowError(DLIError)` | `exceptions.py` | ⏳ Pending |
| API-CLI | `WorkflowAPI` class for `dli workflow` | `api/workflow.py` | ⏳ Pending |
| Test | `test_workflow_api.py` | `tests/api/` | ⏳ Pending |
| Test | `test_workflow_cmd.py` | `tests/cli/` | ⏳ Pending |
| Test | All new modules have test files | `tests/` | ⏳ Pending |
| Export | `WorkflowAPI` exported | `__init__.py` | ⏳ Pending |
```

### 구현 중 (Tracking)

```markdown
## Implementation Checklist: FEATURE_WORKFLOW

### Progress: 7/11 items complete (64%)

| Category | Item | Location | Status | Verified |
|----------|------|----------|--------|----------|
| API | `WorkflowAPI.register()` | `api/workflow.py` | ✅ Done | grep OK |
| API | `WorkflowAPI.unregister()` | `api/workflow.py` | ✅ Done | grep OK |
| CLI | `@workflow_app.command("register")` | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| CLI | `@workflow_app.command("unregister")` | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| CLI | `--show-dataset-info` option | `commands/workflow.py` | ❌ Missing | grep EMPTY |
| Exception | `WorkflowError(DLIError)` | `exceptions.py` | ✅ Done | extends DLIError |
| API-CLI | `WorkflowAPI` for `dli workflow` | `api/workflow.py` | ✅ Done | class exists |
| Test | `test_workflow_api.py` | `tests/api/` | ✅ Done | file exists |
| Test | `test_workflow_cmd.py` | `tests/cli/` | ❌ Missing | file not found |
| Test | All new modules have tests | `tests/` | ✅ Done | loop check OK |
| Export | `WorkflowAPI` in `__init__.py` | `__init__.py` | ✅ Done | grep OK |
```

### 구현 완료 (Verification)

```markdown
## Implementation Verification: FEATURE_WORKFLOW

### Result: 11/11 items complete (100%) ✅

| Category | Item | Verified By |
|----------|------|-------------|
| API | `WorkflowAPI.register()` | `grep -r "def register" src/dli/api/workflow.py` |
| API | `WorkflowAPI.unregister()` | `grep -r "def unregister" src/dli/api/workflow.py` |
| CLI | `register` command | `grep -r "@workflow_app.command(\"register\")" src/` |
| CLI | `unregister` command | `grep -r "@workflow_app.command(\"unregister\")" src/` |
| CLI | `--show-dataset-info` | `grep -r "show-dataset-info" src/dli/commands/workflow.py` |
| Exception | `WorkflowError(DLIError)` | `grep "class WorkflowError(DLIError)" src/dli/exceptions.py` |
| API-CLI | `WorkflowAPI` exists | `grep "class WorkflowAPI" src/dli/api/workflow.py` |
| Test | API tests | `ls tests/api/test_workflow_api.py` exists |
| Test | CLI tests | `ls tests/cli/test_workflow_cmd.py` exists |
| Test | All modules covered | loop check: no missing test files |
| Export | `WorkflowAPI` exported | `grep "WorkflowAPI" src/dli/__init__.py` |

### Exception Inheritance Verification
```
grep -E "class \w+Error\(" src/dli/exceptions.py | grep -v "DLIError"
# Result: (empty) - All exceptions extend DLIError ✅
```

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

- `completion-gate`: 완료 선언 Gate + 코드 존재 검증 (체크리스트 완료 후 적용)
- `spec-validation`: 명세 품질 검증
- `testing`: TDD 워크플로우

---

## Integration with Agent Workflow

```
*_FEATURE.md 수신
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
*_RELEASE.md 작성
```
