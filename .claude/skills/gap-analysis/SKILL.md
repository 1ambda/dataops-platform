# Gap Analysis Skill

FEATURE 명세와 RELEASE 구현 결과를 체계적으로 비교하여 GAP을 식별하는 프로세스.

## 문제 배경

Agent가 구현 완료 후에도 다음 문제 발생:
- FEATURE에 정의된 항목이 구현되지 않은 채 방치
- Phase 1 MVP 완료 후 Phase 2 항목이 "Future Work"로 묻힘
- 구현과 명세 간 차이가 감지되지 않음
- 외부 의존성(다른 컴포넌트) 필요 항목이 추적되지 않음

## 적용 시점

이 skill은 다음 상황에서 적용:
- "Phase N complete" 또는 "MVP 완료" 선언 시
- RELEASE_*.md 작성 전
- 사용자가 gap 분석 요청 시
- `completion-gate` 통과 후 선택적 실행

---

## Gap 분석 프로세스

### Step 1: FEATURE 항목 추출

FEATURE_*.md에서 모든 구현 항목을 추출:

```markdown
### 추출 대상

| 섹션 | 추출 내용 |
|------|----------|
| Use Cases | UC-N 시나리오 |
| API Design | 메서드, 파라미터, 반환 타입 |
| CLI Commands | 커맨드, 옵션, 인자 |
| Data Models | 클래스, 필드, Enum |
| Error Codes | DLI-xxx 코드, 예외 클래스 |
| Phase 구분 | Phase 1/2/MVP 마커 |
```

### Step 2: RELEASE/구현 상태 확인

RELEASE_*.md 및 실제 코드에서 구현 상태 확인:

```bash
# API 메서드 존재 확인
grep -r "def method_name" src/dli/api/{feature}.py

# CLI 커맨드 존재 확인
grep -r "@{feature}_app.command" src/dli/commands/{feature}.py

# 에러 코드 존재 확인
grep -r "DLI-{xxx}" src/dli/exceptions.py

# 모델 클래스 존재 확인
grep -r "class {ClassName}" src/dli/models/
```

### Step 3: Gap 매핑

```markdown
## Gap Matrix

| FEATURE Section | Item | RELEASE Status | Code Status | Gap Type |
|-----------------|------|----------------|-------------|----------|
| 4.1 API | method_a() | Documented | grep OK | None |
| 4.1 API | method_b() | Documented | grep OK | None |
| 4.2 API | method_c(mode=SERVER) | "Stub" | Mock only | **PARTIAL** |
| 5.1 CLI | --option-x | Missing | grep EMPTY | **MISSING** |
| 6.1 Errors | DLI-605 | Missing | grep EMPTY | **MISSING** |
| 7.1 Phase 2 | schedule | Future Work | N/A | **PHASE_2** |
```

---

## Gap 유형 정의

### Gap Types

| Type | 의미 | 액션 |
|------|------|------|
| `None` | 완전 구현 | 없음 |
| `MISSING` | 명세에 있으나 미구현 | 즉시 구현 필요 |
| `PARTIAL` | 일부만 구현 (예: SERVER → Mock) | 나머지 구현 필요 |
| `PHASE_2` | Phase 2로 정의됨 | 추적 대상 |
| `EXTERNAL` | 외부 의존성 필요 | 의존성 추적 |
| `DRIFT` | 구현이 명세와 다름 | 명세 또는 구현 수정 |

### Severity Levels

| Severity | 조건 | 완료 선언 가능 |
|----------|------|---------------|
| **BLOCKER** | MISSING in Phase 1 | ❌ 불가 |
| **CRITICAL** | PARTIAL in Phase 1 | ⚠️ 사유 필요 |
| **DEFERRED** | PHASE_2 items | ✅ Phase 1 완료 가능 |
| **TRACKED** | EXTERNAL dependencies | ✅ 추적만 필요 |

---

## Gap 분석 출력 형식

### 분석 결과 템플릿

```markdown
## Gap Analysis: FEATURE_{FEATURE} v{VERSION}

**Analysis Date:** {DATE}
**Analyzed Against:** RELEASE_{FEATURE} v{VERSION}

---

### Executive Summary

| Category | Total | Implemented | Gap |
|----------|-------|-------------|-----|
| API Methods | 11 | 11 | 0 |
| CLI Commands | 4 | 4 | 0 |
| CLI Options | 12 | 10 | 2 |
| Error Codes | 6 | 5 | **1** |
| Models | 8 | 8 | 0 |

**Overall Status:** 95% Complete (1 MISSING, 1 PARTIAL)

---

### Phase 1 MVP Gaps (Must Fix)

| Section | Item | Status | Gap | Severity |
|---------|------|--------|-----|----------|
| 6.1 | DLI-605 QualityTestTimeoutError | Missing | MISSING | BLOCKER |
| 4.1 | QualityAPI.run(mode=SERVER) | Stub | PARTIAL | CRITICAL |

**Required Actions:**
1. Implement `QualityTestTimeoutError` (DLI-605) or formally defer
2. Document SERVER mode as "Phase 1.5" or implement

---

### Phase 2 Items (Tracked)

| Section | Item | Dependency | Owner |
|---------|------|------------|-------|
| 5.1 | schedule processing | Airflow DAG | expert-devops-cicd |
| 5.1 | notifications.slack | Basecamp Connect | feature-basecamp-connect |
| 5.1 | notifications.email | Basecamp Connect | feature-basecamp-connect |
| 5.2 | expression test type | None | feature-interface-cli |
| 5.2 | row_count test type | None | feature-interface-cli |

**Total Phase 2 Items:** 5

---

### External Dependencies

| Item | Required From | Status | Request ID |
|------|---------------|--------|------------|
| POST /api/quality/run | feature-basecamp-server | Not requested | - |
| Slack webhook | feature-basecamp-connect | Not requested | - |

**Recommendation:** Use `dependency-coordination` skill to create requests.

---

### Drift Detection

| Item | FEATURE Definition | Actual Implementation | Action |
|------|-------------------|----------------------|--------|
| Model name | `QualityTestDefinition` | `DqTestDefinitionSpec` | Update FEATURE |
| Result type | `QualityResult` | `DqQualityResult` | Update FEATURE |

---

### Recommendations

1. **Immediate (Phase 1 Completion):**
   - Resolve 1 BLOCKER (DLI-605)
   - Document PARTIAL items with justification

2. **Short-term (Phase 1.5):**
   - Implement SERVER mode execution
   - Request Server API from basecamp-server team

3. **Long-term (Phase 2):**
   - Track 5 Phase 2 items in backlog
   - Create dependency requests for external items
```

---

## 관련 Skills 연동

### completion-gate 연동

`completion-gate` 통과 후 선택적으로 `gap-analysis` 실행:

```
completion-gate PASSED (코드/테스트)
       ↓
[gap-analysis skill 실행 - 선택]
       ↓
Gap 분석 결과 출력
       ↓
  ┌───────────────────┐
  │ BLOCKER 발견 시   │
  │  → 즉시 수정 필요 │
  └───────────────────┘
       ↓
docs-synchronize 실행
       ↓
최종 완료
```

### phase-tracking 연동

Gap 분석에서 Phase 2 항목 발견 시 `phase-tracking` skill로 전달:

```
gap-analysis: Phase 2 items found
       ↓
[phase-tracking skill]
       ↓
Phase 2 백로그 생성
       ↓
추적 대상으로 등록
```

### dependency-coordination 연동

External dependency 발견 시 `dependency-coordination` skill로 전달:

```
gap-analysis: External dependencies found
       ↓
[dependency-coordination skill]
       ↓
의존성 요청 생성
       ↓
담당 Agent에 요청 전송
```

---

## 자동화 트리거

다음 상황에서 자동 실행 권장:

| 트리거 | 실행 모드 |
|--------|----------|
| "Phase 1 complete" | Full analysis |
| "MVP 완료" | Full analysis |
| RELEASE_*.md 작성 전 | Quick check |
| `--gap-check` 플래그 | On-demand |

---

## Agent Integration

### feature-interface-cli Agent 통합

```markdown
## Pre-Release Checklist

RELEASE_*.md 작성 전:

1. implementation-checklist 실행 → 100% 확인
2. completion-gate 실행 → PASSED
3. **gap-analysis 실행 → BLOCKER 없음 확인**
4. docs-synchronize 실행 → PASSED
5. RELEASE_*.md 작성
```

### 분석 결과 저장

Gap 분석 결과를 `features/GAP_{FEATURE}.md`로 저장:

```markdown
# GAP_{FEATURE}.md

> Generated by gap-analysis skill
> Date: {DATE}

[분석 결과 전체 내용]
```

---

## 사용 예시

### 명령어 형태

```bash
# 암묵적 호출 (completion-gate 후)
"Phase 1 MVP complete" → gap-analysis 자동 실행

# 명시적 호출
"gap 분석 실행해줘" → gap-analysis 실행
"FEATURE vs RELEASE 비교해줘" → gap-analysis 실행
```

### 분석 범위 지정

```markdown
# 전체 분석
"FEATURE_QUALITY 전체 gap 분석"

# 특정 섹션만
"API 섹션 gap 분석"
"에러 코드 gap 확인"

# Phase별
"Phase 2 항목만 확인"
```

---

## 관련 Skills

- `implementation-checklist`: FEATURE → 체크리스트 (구현 추적)
- `completion-gate`: 완료 조건 검증 (선행 조건)
- `phase-tracking`: Phase별 진행 관리 (후속 처리)
- `dependency-coordination`: 외부 의존성 추적 (후속 처리)
- `docs-synchronize`: 문서 동기화 (후속 처리)
