# Completion Gate Skill

"완료" 선언 전 필수 조건을 강제하여 거짓 완료를 방지하는 프로세스.

## 문제 배경

Agent가 다음과 같은 거짓 완료 선언을 하는 문제:
- API만 구현 후 "완료" (CLI 누락)
- FEATURE 일부만 구현 후 "MVP 완료"
- 테스트 없이 "구현 완료"
- STATUS.md에 거짓 상태 기록

## 적용 시점

이 skill은 Agent가 다음 단어 사용 시 **자동** 적용:
- "구현 완료", "구현 끝", "implementation complete"
- "done", "완료", "finished"
- "모두 구현", "all implemented"
- "MVP 완료", "Phase 1 complete"

---

## 완료 Gate 조건

### 필수 조건 (모두 충족해야 "완료" 가능)

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 1 | **FEATURE 전체 항목 구현** | implementation-checklist 실행 | 미완료 항목 목록 출력 |
| 2 | **API 테스트 존재** | `ls tests/api/test_{feature}_api.py` | 테스트 작성 요청 |
| 3 | **CLI 테스트 존재** | `ls tests/cli/test_{feature}_cmd.py` | 테스트 작성 요청 |
| 4 | **전체 테스트 통과** | `pytest tests/` 실행 | 실패 테스트 수정 요청 |
| 5 | **타입 체크 통과** | `pyright src/` 실행 | 타입 에러 수정 요청 |
| 6 | **Export 완료** | `grep "XXXApi" src/dli/__init__.py` | export 추가 요청 |

### 선택 조건 (권장)

| # | 조건 | 검증 방법 | 미충족 시 |
|---|------|-----------|-----------|
| 7 | lint 통과 | `ruff check src/` | 경고만 출력 |
| 8 | docstring 존재 | grep 검증 | 경고만 출력 |

---

## Gate 검증 프로세스

### Step 1: 트리거 감지

Agent가 완료 관련 단어 사용 시:

```python
COMPLETION_TRIGGERS = [
    "구현 완료", "구현 끝", "implementation complete",
    "done", "완료", "finished", "implemented",
    "모두 구현", "all implemented",
    "MVP 완료", "Phase 1 complete"
]

# 트리거 감지 시 → Gate 검증 시작
```

### Step 2: 자동 검증 실행

```bash
# 1. API 테스트 파일 존재
ls tests/api/test_{feature}_api.py 2>/dev/null || echo "GATE FAIL: API test file missing"

# 2. CLI 테스트 파일 존재
ls tests/cli/test_{feature}_cmd.py 2>/dev/null || echo "GATE FAIL: CLI test file missing"

# 3. pytest 실행
uv run pytest tests/ -q || echo "GATE FAIL: Tests failing"

# 4. pyright 실행
uv run pyright src/dli/ || echo "GATE FAIL: Type errors exist"

# 5. FEATURE 체크리스트 검증 (implementation-checklist skill 호출)
# → 미완료 항목이 있으면 GATE FAIL
```

### Step 3: Gate 결과 출력

#### Gate 통과

```markdown
## Completion Gate: PASSED ✅

### Verification Results

| Condition | Status | Evidence |
|-----------|--------|----------|
| FEATURE checklist | ✅ 7/7 complete | implementation-checklist 결과 |
| API tests exist | ✅ | `tests/api/test_workflow_api.py` |
| CLI tests exist | ✅ | `tests/cli/test_workflow_cmd.py` |
| All tests pass | ✅ | `pytest tests/ → 1740 passed` |
| Type check pass | ✅ | `pyright src/ → 0 errors` |
| Exports complete | ✅ | `WorkflowAPI in __init__.py` |

### 완료 선언 승인 ✅

이제 "구현 완료"를 선언할 수 있습니다.
다음 단계:
1. RELEASE_*.md 작성/업데이트
2. STATUS.md 업데이트
3. Serena memory 업데이트
```

#### Gate 실패

```markdown
## Completion Gate: FAILED ❌

### Verification Results

| Condition | Status | Issue |
|-----------|--------|-------|
| FEATURE checklist | ❌ 5/7 complete | 2 items missing |
| API tests exist | ✅ | OK |
| CLI tests exist | ❌ | File not found |
| All tests pass | ⚠️ | N/A (CLI tests missing) |
| Type check pass | ✅ | OK |
| Exports complete | ✅ | OK |

### Missing Items

1. **CLI Commands** (from FEATURE Section 5):
   - `@workflow_app.command("register")` - grep result empty
   - `@workflow_app.command("unregister")` - grep result empty

2. **Test Files**:
   - `tests/cli/test_workflow_cmd.py` - file not found

### Required Actions

"완료"를 선언하려면 다음을 먼저 수행하세요:

1. CLI `register` 커맨드 구현
   ```python
   @workflow_app.command("register")
   def register_workflow(...):
       ...
   ```

2. CLI `unregister` 커맨드 구현

3. CLI 테스트 파일 생성
   ```bash
   touch tests/cli/test_workflow_cmd.py
   ```

4. 테스트 작성 및 실행
   ```bash
   uv run pytest tests/cli/test_workflow_cmd.py
   ```

### Gate 재시도

위 작업 완료 후 다시 "완료"를 선언하면 Gate가 재검증됩니다.
```

---

## STATUS.md 연동

Gate 통과 시에만 STATUS.md 업데이트 허용:

```markdown
### Gate 통과 전
❌ STATUS.md 업데이트 불가
→ "Gate 조건 미충족. 위 항목을 먼저 완료하세요."

### Gate 통과 후
✅ STATUS.md 업데이트 가능
→ STATUS.md 자동 업데이트 제안:

| Component | Old Status | New Status |
|-----------|------------|------------|
| WorkflowAPI | ✅ Complete | ✅ Complete |
| CLI Commands | ⏳ Partial | ✅ Complete |
| Tests | ⏳ Partial | ✅ Complete |
```

---

## Gate 우회 (예외 상황)

특정 상황에서 Gate 우회가 필요한 경우:

### 허용되는 우회

```markdown
## Gate 우회 요청

**사유**: Server 연동 전이라 일부 기능 구현 불가

**미완료 항목**:
- [ ] CLI `register` (Server API 미구현)
- [ ] CLI `unregister` (Server API 미구현)

**우회 승인 조건**:
1. 명확한 사유 기술
2. 미완료 항목 목록 명시
3. RELEASE_*.md "Future Work" 섹션에 기록
4. STATUS.md에 "⏳ Partial" 표시

**우회 승인**: ✅ (사유 합리적)
```

### 허용되지 않는 우회

```markdown
❌ "시간이 없어서" → 사유 불충분
❌ "나중에 하겠다" (구체적 계획 없음) → 사유 불충분
❌ 테스트 없이 배포 → 품질 기준 미충족
```

---

## 관련 Skills

- `implementation-checklist`: FEATURE → 체크리스트 생성
- `implementation-verification`: 코드 존재 검증
- `testing`: TDD 워크플로우, pytest 실행
- `code-review`: 코드 품질 검증

---

## Agent Integration

```
Agent가 "완료" 선언
       ↓
[completion-gate skill 자동 적용]
       ↓
조건 검증 시작
       ↓
  ┌─────┴─────┐
  │           │
 PASS        FAIL
  │           │
  ↓           ↓
완료 승인    액션 목록 출력
  │           │
  ↓           ↓
RELEASE 작성  미완료 항목 구현
STATUS 업데이트    ↓
              [재시도]
```
