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

### API Parity Check (신규 2026-01-01)

> **원칙**: CLI 커맨드가 있으면 반드시 대응하는 Library API가 존재해야 함

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 7 | **API Parity** | CLI 커맨드 ↔ Library API 1:1 매핑 | 누락된 API 구현 요청 |

```bash
# API Parity 검증 스크립트
# 1. CLI 커맨드 목록 추출
cli_commands=$(grep -r "@.*_app.command" src/dli/commands/{feature}.py | grep -oP 'command\("\K[^"]+')

# 2. 대응 API 메서드 확인
for cmd in $cli_commands; do
  # CLI 커맨드 → API 메서드 이름 변환 (예: list → list_{feature}s, run → run)
  api_method=$(echo $cmd | sed 's/-/_/g')
  if ! grep -q "def $api_method\|def ${api_method}_" src/dli/api/{feature}.py; then
    echo "PARITY_FAIL: CLI '$cmd' has no matching API method"
  fi
done

# 3. 역방향 확인: API에만 있고 CLI에 없는 메서드 (경고)
api_methods=$(grep -oP "def \K\w+(?=\(self)" src/dli/api/{feature}.py | grep -v "^_")
for method in $api_methods; do
  if ! grep -q "command.*$method\|command.*$(echo $method | sed 's/_/-/g')" src/dli/commands/{feature}.py; then
    echo "PARITY_WARN: API '$method' has no CLI command (may be intentional)"
  fi
done
```

**Parity 규칙:**

| CLI 커맨드 | API 메서드 (필수) | 비고 |
|------------|-------------------|------|
| `dli {feature} list` | `{Feature}API.list_{feature}s()` | 목록 조회 |
| `dli {feature} get` | `{Feature}API.get()` | 단일 조회 |
| `dli {feature} run` | `{Feature}API.run()` | 실행 |
| `dli {feature} validate` | `{Feature}API.validate()` | 검증 |
| `dli {feature} register` | `{Feature}API.register()` | 등록 |

### Exception Integration Check (신규 2026-01-01)

> **원칙**: Feature별 예외는 DLI-xxx 에러 코드 체계를 따라야 함

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 8 | **Exception Integration** | Feature 예외가 DLI 에러코드 사용 | 예외 클래스 수정 요청 |

```bash
# Exception Integration 검증 스크립트
# 1. Feature 예외 클래스 확인
feature_exceptions=$(grep -r "class.*Error.*DLIError\|class.*Exception.*DLIError" src/dli/ --include="*.py")

# 2. 각 예외가 ErrorCode를 사용하는지 확인
for exc in $(grep -oP "class \K\w+(?=\(.*DLIError)" src/dli/exceptions.py); do
  if ! grep -A5 "class $exc" src/dli/exceptions.py | grep -q "ErrorCode\|error_code\|DLI-"; then
    echo "EXCEPTION_FAIL: $exc does not use DLI error code"
  fi
done

# 3. Feature 코드에서 raise되는 예외 확인
for raised_exc in $(grep -oP "raise \K\w+Error" src/dli/api/{feature}.py src/dli/commands/{feature}.py 2>/dev/null | sort -u); do
  if ! grep -q "class $raised_exc" src/dli/exceptions.py; then
    echo "EXCEPTION_FAIL: $raised_exc is raised but not defined in exceptions.py"
  fi
done
```

**Error Code 범위:**

| Feature | Error Code Range | 예시 |
|---------|------------------|------|
| Dataset | DLI-1xx | DLI-101 (DatasetNotFoundError) |
| Metric | DLI-2xx | DLI-201 (MetricNotFoundError) |
| Transpile | DLI-3xx | DLI-301 (TranspileError) |
| Catalog | DLI-4xx | DLI-401 (CatalogError) |
| Workflow | DLI-5xx | DLI-501 (WorkflowError) |
| Quality | DLI-6xx | DLI-601 (QualityError) |
| Config | DLI-7xx | DLI-701 (ConfigError) |
| Core/Common | DLI-8xx | DLI-801 (ConnectionError) |

### 문서 동기화 조건 (Gate 통과 후 필수)

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 9 | **RELEASE_*.md 존재** | `ls features/RELEASE_{feature}.md` | 문서 작성 요청 |
| 10 | **STATUS.md 업데이트** | `grep "{feature}" features/STATUS.md` | 업데이트 요청 |
| 11 | **Serena memory 동기화** | `mcp__serena__read_memory` 확인 | 동기화 요청 |

> **Note:** 조건 9-11은 `docs-synchronize` skill과 연동됩니다. Gate 통과 후 자동으로 문서 동기화 검증이 실행됩니다.

### 선택 조건 (권장)

| # | 조건 | 검증 방법 | 미충족 시 |
|---|------|-----------|-----------|
| 12 | lint 통과 | `ruff check src/` | 경고만 출력 |
| 13 | docstring 존재 | grep 검증 | 경고만 출력 |

### Dead Code Check (신규 2026-01-01)

> **배경**: Transpile 구현에서 `DUPLICATE_CTE`, `CORRELATED_SUBQUERY` enum 값이 정의만 되고 구현 로직이 없는 문제 발견

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 14 | **Enum 값 사용 확인** | 모든 Enum 값이 코드에서 사용됨 | 미사용 enum 제거 또는 로직 구현 |
| 15 | **정의된 예외 클래스 사용** | 모든 Exception이 raise 또는 catch됨 | 미사용 예외 제거 |

```bash
# Dead Code 검증 명령어
# 1. Enum 값 사용 확인
for enum_val in $(grep -oP "class \w+\(.*Enum\)" src/dli/ -r | ...); do
  usage_count=$(grep -r "$enum_val" src/dli/ --include="*.py" | grep -v "class\|Enum" | wc -l)
  if [ "$usage_count" -eq 0 ]; then
    echo "DEAD_CODE: $enum_val defined but never used"
  fi
done

# 2. Exception 클래스 사용 확인
grep -r "class.*Error.*Exception" src/dli/exceptions.py | while read exc; do
  exc_name=$(echo $exc | grep -oP "class \K\w+")
  if ! grep -r "raise $exc_name\|except $exc_name" src/dli/ --include="*.py" | grep -v exceptions.py; then
    echo "DEAD_CODE: $exc_name defined but never raised"
  fi
done
```

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

코드/테스트 Gate 통과. 이제 **문서 동기화 검증**을 진행합니다.

→ **docs-synchronize skill 자동 실행**

문서 동기화 완료 후 "최종 완료"를 선언할 수 있습니다.
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

## Phase 경계 검사 (Phase Boundary Check)

> **신규 기능 (2026-01-01)**: `phase-tracking` skill과 연동하여 Phase 경계를 인식합니다.

### Phase 1 Completion Gate (신규 2026-01-01)

> **원칙**: Phase 2 작업 시작 전 반드시 Phase 1 완료 검증 필수

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 16 | **Phase 1 완료 검증** | Phase 2 시작 전 Phase 1 모든 항목 체크 | Phase 1 먼저 완료 요청 |

```bash
# Phase 1 완료 검증 스크립트
# 1. FEATURE.md에서 Phase 구분 파싱
phase1_items=$(grep -A100 "## Phase 1" features/FEATURE_{feature}.md | grep -B100 "## Phase 2" | grep -P "^\s*-\s*\[" | wc -l)
phase1_complete=$(grep -A100 "## Phase 1" features/FEATURE_{feature}.md | grep -B100 "## Phase 2" | grep -P "^\s*-\s*\[x\]" -i | wc -l)

# 2. Phase 1 완료율 확인
if [ "$phase1_items" -gt 0 ] && [ "$phase1_complete" -lt "$phase1_items" ]; then
  echo "PHASE_GATE_FAIL: Phase 1 incomplete ($phase1_complete/$phase1_items)"
  echo "Cannot start Phase 2 until Phase 1 is complete"
  # 미완료 항목 출력
  grep -A100 "## Phase 1" features/FEATURE_{feature}.md | grep -B100 "## Phase 2" | grep -P "^\s*-\s*\[\s\]"
fi

# 3. Phase 2 시작 시도 감지
if grep -q "Phase 2\|phase-2\|Phase2" <<< "$CURRENT_TASK"; then
  if [ "$phase1_complete" -lt "$phase1_items" ]; then
    echo "BLOCKED: Phase 2 cannot start - Phase 1 has $((phase1_items - phase1_complete)) incomplete items"
  fi
fi
```

**Phase Gate 규칙:**

| 상황 | 허용 | 액션 |
|------|------|------|
| Phase 1 진행 중 | O | Phase 1 항목 작업 계속 |
| Phase 1 완료, Phase 2 시작 | O | Phase 2 진입 허용 |
| Phase 1 미완료, Phase 2 시작 시도 | X | Phase 1 완료 요청, 미완료 목록 출력 |
| Phase 1 일부만 완료, "MVP 완료" 선언 | X | 모든 Phase 1 항목 완료 필요 |

**Phase 1 완료 필수 조건 (Gate 17-20):**

| # | 조건 | 설명 |
|---|------|------|
| 17 | Phase 1 모든 기능 구현 | FEATURE의 Phase 1 섹션 항목 100% |
| 18 | Phase 1 테스트 통과 | Phase 1 범위 테스트 모두 통과 |
| 19 | Phase 1 문서화 완료 | RELEASE_*.md에 Phase 1 기록 |
| 20 | STATUS.md Phase 1 표기 | `Phase 1 ✅` 상태 업데이트 |

### Phase 경계 인식

FEATURE에 Phase 1/2 구분이 있는 경우, Gate는 **Phase 단위**로 완료를 판단합니다:

```markdown
## Completion Gate: PASSED (Phase 1 MVP) ⚠️

### Code/Test Verification: ✅ PASSED

| Condition | Status | Evidence |
|-----------|--------|----------|
| Phase 1 checklist | ✅ 5/5 complete | implementation-checklist 결과 |
| API tests exist | ✅ | `tests/api/test_quality_api.py` |
| CLI tests exist | ✅ | `tests/cli/test_quality_cmd.py` |
| All tests pass | ✅ | `pytest tests/ → 47 passed` |
| Type check pass | ✅ | `pyright src/ → 0 errors` |

### Phase Boundary: ⚠️ Phase 2 Items Pending

| Phase | Total | Complete | Status |
|-------|-------|----------|--------|
| Phase 1 MVP | 5 | 5 | ✅ Complete |
| Phase 1.5 | 2 | 0 | ⏳ Blocked |
| Phase 2 | 7 | 0 | ⏳ Not Started |

### Phase 2 Backlog Auto-Generated

다음 항목이 Phase 2 백로그로 등록되었습니다:

| Priority | Item | Dependency |
|----------|------|------------|
| P0 | SERVER mode execution | Basecamp Server |
| P1 | Airflow DAG generation | Airflow |
| P1 | Slack notifications | Basecamp Connect |
| ... | (7 items total) | ... |

### Declaration Options

1. **"Phase 1 MVP Complete"** ✅ (권장)
   - STATUS.md: `Phase 1 ✅, Phase 2 ⏳`
   - Phase 2 백로그 추적 시작
   - RELEASE_*.md에 "Phase 1 MVP" 명시

2. **"Feature Complete"** ❌ (불가)
   - Phase 2 항목 존재로 전체 완료 불가
   - 모든 Phase 완료 후 가능
```

### Phase 완료 유형

| 완료 유형 | 조건 | STATUS.md 표기 |
|----------|------|----------------|
| **Phase N MVP Complete** | Phase N 항목 100% | `✅ Phase N, ⏳ Phase N+1` |
| **Feature Complete** | 모든 Phase 완료 | `✅ Complete` |
| **Phase N Partial** | 일부 구현, 사유 있음 | `⏳ Partial (사유)` |

### Phase 경계 실패 예시

```markdown
## Completion Gate: FAILED ❌

### Issue: Phase 1 항목 미완료

"Feature Complete"를 선언했으나 Phase 1 항목이 미완료입니다.

| Phase 1 Item | Status |
|--------------|--------|
| QualityAPI.validate | ✅ |
| DLI-605 error code | ❌ **MISSING** |

### Required Action

Phase 1을 완료하려면:
1. DLI-605 (QualityTestTimeoutError) 구현, 또는
2. FEATURE에서 공식 제외 후 "Phase 1 MVP Complete" 선언

"Feature Complete"는 모든 Phase 완료 후 가능합니다.
```

---

## 관련 Skills

- `docs-synchronize`: **문서 동기화 검증** (Gate 통과 후 자동 연결)
- `implementation-checklist`: FEATURE → 체크리스트 생성
- `implementation-verification`: 코드 존재 검증
- `testing`: TDD 워크플로우, pytest 실행
- `code-review`: 코드 품질 검증
- `gap-analysis`: **FEATURE vs RELEASE 비교** (선택적 실행)
- `phase-tracking`: **Phase 경계 관리** (Phase 구분 있을 때 연동)
- `dependency-coordination`: **외부 의존성 추적** (Blocked 항목 있을 때 연동)

---

## Agent Integration

```
Agent가 "완료" 선언
       ↓
[completion-gate skill 자동 적용]
       ↓
코드/테스트 검증 (조건 1-6)
       ↓
  ┌─────┴─────┐
  │           │
 PASS        FAIL
  │           │
  ↓           ↓
docs-synchronize   액션 목록 출력
skill 실행              ↓
  │           미완료 항목 구현
  ↓                   ↓
문서 동기화 검증      [재시도]
(조건 7-9)
       ↓
  ┌─────┴─────┐
  │           │
 PASS        FAIL
  │           │
  ↓           ↓
최종 완료    문서 작성 요청
승인              ↓
             [문서 작성 후 재시도]
```

### 완료 상태 정의

| 상태 | 의미 | 조건 |
|------|------|------|
| **코드 완료** | 코드/테스트 Gate 통과 | 조건 1-6 충족 |
| **최종 완료** | 문서 동기화 완료 | 조건 1-9 충족 |

> **중요**: "완료"를 선언하려면 **최종 완료** 상태여야 합니다.
