# Documentation Synchronization Skill

구현 완료 후 문서 간 일관성을 검증하고 동기화하는 프로세스.

## 문제 배경

Agent가 코드 구현은 완료했으나 문서 동기화를 누락하는 문제:
- RELEASE_*.md 작성 후 STATUS.md 업데이트 누락
- FEATURE vs RELEASE 버전 불일치
- Serena memory 동기화 누락
- Changelog 항목 누락
- **Test Count Drift**: RELEASE 문서 내 테스트 수와 실제 pytest 결과 불일치 (2026-01-01 추가)
- **API Export 누락**: 새 API 클래스 구현 후 `__init__.py` export 누락 (2026-01-01 추가)
- **STATUS.md Changelog 트리거 누락**: RELEASE 변경 시 Changelog 자동 업데이트 누락 (2026-01-01 추가)

## 적용 시점

이 skill은 `completion-gate` 통과 후 **자동** 적용:
- 코드/테스트 검증 완료
- "완료" 선언 승인 후
- RELEASE_*.md 작성 시점

---

## 문서 동기화 대상

### 필수 동기화 (Mandatory)

| 문서 | 검증 내용 | 우선순위 |
|------|-----------|----------|
| `features/STATUS.md` | Changelog 항목 존재 | P0 |
| `features/RELEASE_*.md` | 구현 결과 문서 존재 | P0 |
| `features/FEATURE_*.md` | 버전 일치 확인 | P1 |
| Serena memory | 최신 상태 반영 | P1 |

### 선택 동기화 (Optional)

| 문서 | 검증 내용 | 우선순위 |
|------|-----------|----------|
| `README.md` | 새 기능 언급 | P2 |
| `CLAUDE.md` | 패턴/가이드 업데이트 | P2 |

---

## 동기화 프로세스

### Step 1: RELEASE 문서 검증

```bash
# RELEASE_*.md 파일 존재 확인
ls features/RELEASE_{feature}.md 2>/dev/null || echo "SYNC FAIL: RELEASE file missing"

# 버전 헤더 확인
grep "Version:" features/RELEASE_{feature}.md
```

**검증 항목:**
- [ ] RELEASE 파일 존재
- [ ] Version 헤더 존재
- [ ] Status: Complete 또는 명확한 상태 표시
- [ ] Changelog 섹션 존재

### Step 2: STATUS.md 동기화

```bash
# STATUS.md에 해당 기능 언급 확인
grep -i "{feature}" features/STATUS.md || echo "SYNC FAIL: STATUS.md missing feature"

# Changelog 항목 확인
grep -A 5 "Changelog" features/STATUS.md | grep "{feature}"
```

**필수 업데이트 항목:**
- [ ] Core Components 또는 관련 섹션에 상태 반영
- [ ] Changelog에 버전별 변경사항 기록
- [ ] Documentation 섹션에 RELEASE/FEATURE 문서 목록 추가

### Step 2.5: STATUS.md Changelog Update Trigger (신규 2026-01-01)

> **배경**: RELEASE 문서 작성 후 STATUS.md Changelog 업데이트를 누락하는 문제

#### 트리거 조건

STATUS.md Changelog 업데이트가 **필수**인 경우:

| 트리거 | 조건 | 우선순위 |
|--------|------|----------|
| RELEASE 문서 생성 | `features/RELEASE_*.md` 신규 생성 | P0 |
| 버전 변경 | RELEASE 내 Version 헤더 변경 | P0 |
| API 클래스 추가 | `api/*.py` 내 새 `*API` 클래스 | P1 |
| 에러 코드 추가 | `exceptions.py` 내 새 `DLI-XXX` 코드 | P1 |
| 테스트 대량 추가 | 10개 이상 새 테스트 추가 | P2 |

#### 자동 트리거 검증

```bash
# 1. RELEASE 문서 변경 감지
git diff --name-only HEAD~1 | grep "features/RELEASE_"

# 2. 버전 변경 감지
git diff HEAD~1 -- features/RELEASE_*.md | grep "^+.*Version:"

# 3. STATUS.md Changelog 업데이트 여부 확인
git diff HEAD~1 -- features/STATUS.md | grep "^+.*###.*v[0-9]"
```

#### MCP 기반 트리거 워크플로우

```python
# Step 1: RELEASE 문서 변경 확인
release_changes = Bash("git diff --name-only HEAD~1 | grep 'RELEASE_'")

# Step 2: STATUS.md Changelog 업데이트 확인
if release_changes:
    status_changelog = Bash("git diff HEAD~1 -- features/STATUS.md | grep '^+.*###.*v[0-9]'")
    if not status_changelog:
        print("CHANGELOG_MISSING: RELEASE 문서 변경되었으나 STATUS.md Changelog 미업데이트")

        # 자동 Changelog 항목 생성
        version = extract_version_from_release()
        date = datetime.now().strftime("%Y-%m-%d")
        changelog_entry = f"""
### {version} ({date})
- {feature} 구현 완료
- {api_class} API 추가
- {test_count}개 테스트 추가
"""
        print(f"SUGGESTED_ENTRY:\n{changelog_entry}")
```

#### Changelog 항목 자동 생성 템플릿

```markdown
### v{version} ({date})
- {Feature}API 구현 ({method_count} methods)
- {Feature} 커맨드 통합 ({command_list})
- DLI-{xxx} 에러 코드 ({code_range})
- {test_count}개 테스트 추가
```

**검증 항목:**
- [ ] RELEASE 문서 변경 시 STATUS.md Changelog 항목 존재
- [ ] Changelog 버전이 RELEASE 버전과 일치
- [ ] Changelog 날짜가 RELEASE 날짜와 일치
- [ ] 주요 변경사항 (API, 에러코드, 테스트) 모두 기록됨

### Step 3: FEATURE-RELEASE 버전 일치

```bash
# FEATURE 버전
feature_ver=$(grep "Version:" features/FEATURE_{feature}.md | head -1)

# RELEASE 버전
release_ver=$(grep "Version:" features/RELEASE_{feature}.md | head -1)

# 버전 비교 (RELEASE >= FEATURE)
```

**검증 규칙:**
- RELEASE 버전 >= FEATURE 버전
- 버전 불일치 시 경고 출력

### Step 3.5: Test Count Synchronization (Updated 2026-01-01)

> **배경**: Transpile 구현에서 FEATURE(163), RELEASE(147), 실제(178) 테스트 수가 모두 달랐던 문제 발견

#### 자동 테스트 수 동기화 프로세스

```bash
# 1. 실제 테스트 수 확인 (pytest --collect-only)
cd project-interface-cli
actual_count=$(uv run pytest tests/{feature}/ --collect-only -q 2>/dev/null | tail -1 | grep -oE "[0-9]+ test" | grep -oE "[0-9]+")

# 2. RELEASE 문서 내 테스트 수 추출
release_count=$(grep -oE "[0-9]+ (passed|tests)" features/RELEASE_{feature}.md | head -1 | grep -oE "[0-9]+")

# 3. 비교 및 자동 수정
if [ "$actual_count" != "$release_count" ]; then
  echo "DOC_DRIFT: Test count mismatch (actual: $actual_count, documented: $release_count)"
  echo "ACTION: Update RELEASE_{feature}.md with correct test count"
fi
```

#### 동기화 대상 파일

| 파일 | 검증 패턴 | 자동 수정 |
|------|-----------|-----------|
| `RELEASE_{feature}.md` | `X passed` 또는 `X tests` | ✅ 필수 |
| `FEATURE_{feature}.md` | Test 섹션 내 count | ⚠️ 권장 |
| `STATUS.md` | Changelog 내 test count | ⚠️ 권장 |

#### MCP 기반 동기화 워크플로우

```python
# Step 1: 실제 테스트 수 수집
result = Bash("cd project-interface-cli && uv run pytest tests/{feature}/ --collect-only -q")
actual_count = parse_test_count(result)

# Step 2: RELEASE 문서 읽기 및 테스트 수 추출
release_content = Read("project-interface-cli/features/RELEASE_{feature}.md")
doc_count = extract_test_count(release_content)

# Step 3: 불일치 시 자동 수정
if actual_count != doc_count:
    Edit(
        file_path="project-interface-cli/features/RELEASE_{feature}.md",
        old_string=f"{doc_count} passed",
        new_string=f"{actual_count} passed"
    )
```

**검증 항목:**
- [ ] RELEASE 문서 내 테스트 수 = 실제 pytest 수
- [ ] FEATURE 문서 Status = "Implemented" 또는 "Complete" (not "Draft")
- [ ] STATUS.md 내 테스트 수가 전체 테스트 수 반영
- [ ] 불일치 발견 시 자동 수정 또는 명확한 경고 출력

### Step 3.6: API Export Verification (신규 2026-01-01)

> **배경**: API 클래스 구현 후 `api/__init__.py`에 export 추가를 누락하는 문제

#### 검증 대상

```
project-interface-cli/src/dli/api/__init__.py
```

#### 자동 검증 프로세스

```bash
# 1. api/ 디렉토리 내 모든 API 클래스 추출
api_classes=$(grep -rh "^class .*API" project-interface-cli/src/dli/api/*.py | grep -oE "[A-Z][a-zA-Z]+API")

# 2. __init__.py 내 export 확인
for cls in $api_classes; do
  if ! grep -q "$cls" project-interface-cli/src/dli/api/__init__.py; then
    echo "EXPORT_MISSING: $cls not exported in api/__init__.py"
  fi
done
```

#### MCP 기반 검증 워크플로우

```python
# Step 1: API 디렉토리 내 모든 *API 클래스 찾기
api_files = Glob("project-interface-cli/src/dli/api/*.py")
api_classes = []
for f in api_files:
    if f.endswith("__init__.py"):
        continue
    content = Read(f)
    classes = re.findall(r"^class (\w+API)", content, re.MULTILINE)
    api_classes.extend(classes)

# Step 2: __init__.py export 확인
init_content = Read("project-interface-cli/src/dli/api/__init__.py")
missing_exports = []
for cls in api_classes:
    if cls not in init_content:
        missing_exports.append(cls)

# Step 3: 누락 시 경고 또는 자동 수정
if missing_exports:
    print(f"EXPORT_MISSING: {missing_exports}")
    # 자동 수정 시 __all__ 리스트에 추가
```

#### 검증 체크리스트

| API Class | File | Exported | Status |
|-----------|------|----------|--------|
| DatasetAPI | dataset.py | ✅ | OK |
| MetricAPI | metric.py | ✅ | OK |
| QualityAPI | quality.py | ✅ | OK |
| WorkflowAPI | workflow.py | ✅ | OK |
| {NewAPI} | {new}.py | ❓ | CHECK |

**검증 항목:**
- [ ] 모든 `*API` 클래스가 `api/__init__.py`에 export됨
- [ ] `__all__` 리스트에 포함됨
- [ ] 최상위 `dli/__init__.py`에서도 re-export됨 (필요 시)

### Step 4: Serena Memory 동기화

```bash
# 관련 memory 확인
mcp__serena__read_memory("cli_implementation_status")

# 필요 시 업데이트
mcp__serena__edit_memory("cli_implementation_status", ...)
```

---

## 동기화 체크리스트 템플릿

구현 완료 후 다음 체크리스트를 사용:

```markdown
## Documentation Sync Checklist

### RELEASE_*.md
- [ ] 파일 존재: `features/RELEASE_{feature}.md`
- [ ] Version 헤더 일치
- [ ] Implemented Features 섹션 완료
- [ ] Files Created/Modified 목록 정확
- [ ] Test Results 기록 (실제 pytest 결과와 일치)
- [ ] Changelog 항목 추가

### Test Count Synchronization (Step 3.5)
- [ ] RELEASE 문서 내 테스트 수 = 실제 pytest 수
- [ ] `uv run pytest --collect-only` 결과로 검증
- [ ] FEATURE 문서 테스트 수도 동기화 (권장)

### API Export Verification (Step 3.6)
- [ ] 모든 *API 클래스가 `api/__init__.py`에 export됨
- [ ] `__all__` 리스트에 포함됨
- [ ] `dli/__init__.py`에서도 re-export됨 (필요 시)

### STATUS.md (Step 2 + 2.5)
- [ ] 관련 섹션에 상태 반영 (✅ Complete)
- [ ] Changelog에 버전별 내용 추가
- [ ] Documentation 섹션에 문서 추가
- [ ] Related Documents 링크 추가
- [ ] RELEASE 문서 변경 시 Changelog 트리거 확인

### FEATURE_*.md
- [ ] 버전이 RELEASE와 일치하거나 낮음
- [ ] 구현 완료 항목 체크

### Serena Memory
- [ ] cli_implementation_status 업데이트
- [ ] cli_patterns (필요 시) 업데이트
```

---

## 동기화 결과 출력

### 동기화 완료

```markdown
## Documentation Sync: PASSED ✅

### Synchronized Documents

| Document | Status | Action |
|----------|--------|--------|
| RELEASE_CATALOG.md | ✅ | v1.2.0 확인됨 |
| STATUS.md | ✅ | Changelog 업데이트됨 |
| FEATURE_CATALOG.md | ✅ | 버전 일치 (v1.2.0) |
| Serena memory | ✅ | cli_implementation_status 동기화됨 |

### New Verification (2026-01-01)

| Check | Status | Details |
|-------|--------|---------|
| Test Count Sync | ✅ | RELEASE: 45 = pytest: 45 |
| API Export | ✅ | CatalogAPI in api/__init__.py |
| Changelog Trigger | ✅ | STATUS.md updated with v1.2.0 |

### Summary

- 4/4 문서 동기화 완료
- 3/3 신규 검증 통과
- 0 경고
- 0 실패
```

### 동기화 실패

```markdown
## Documentation Sync: FAILED ❌

### Issues Found

| Document | Issue | Required Action |
|----------|-------|-----------------|
| STATUS.md | Changelog 누락 | v1.2.0 항목 추가 필요 |
| Serena memory | 미동기화 | edit_memory 호출 필요 |

### New Verification Issues (2026-01-01)

| Check | Issue | Required Action |
|-------|-------|-----------------|
| Test Count | DOC_DRIFT: 45 vs 52 | RELEASE 문서 테스트 수 업데이트 |
| API Export | EXPORT_MISSING: NewAPI | api/__init__.py에 export 추가 |
| Changelog Trigger | CHANGELOG_MISSING | STATUS.md에 v1.2.0 항목 추가 |

### Required Actions

1. **Test Count 동기화:**
   ```bash
   # 실제 테스트 수 확인
   cd project-interface-cli && uv run pytest tests/catalog/ --collect-only -q
   # RELEASE 문서 업데이트
   sed -i 's/45 passed/52 passed/' features/RELEASE_CATALOG.md
   ```

2. **API Export 추가:**
   ```python
   # api/__init__.py
   from .catalog import CatalogAPI, NewAPI  # NewAPI 추가
   __all__ = [..., "NewAPI"]
   ```

3. **STATUS.md Changelog 추가:**
   ```markdown
   ### v0.4.0 (2025-12-31)
   - Catalog 커맨드 v1.2.0 통합
   - CatalogAPI Result 모델 추가
   - DLI-7xx 에러 코드 (701-706)
   ```

4. **Serena memory 업데이트:**
   ```python
   mcp__serena__edit_memory(
       "cli_implementation_status",
       "Catalog.*v1.1.0",
       "Catalog v1.2.0 (Result models)",
       "regex"
   )
   ```

### 동기화 재시도

위 작업 완료 후 이 skill을 다시 실행하세요.
```

---

## completion-gate 연동

`completion-gate` 통과 후 자동 연결:

```
completion-gate PASSED
       ↓
[docs-synchronize skill 자동 적용]
       ↓
문서 동기화 검증
       ↓
  ┌─────┴─────┐
  │           │
 PASS        FAIL
  │           │
  ↓           ↓
최종 완료    동기화 작업 요청
  │           │
  ↓           ↓
"완료" 승인   수정 후 재검증
```

---

## STATUS.md 업데이트 템플릿

새 기능 구현 시 STATUS.md에 추가할 내용:

### Changelog 항목

```markdown
### v{version} ({date})
- {Feature}API 구현 ({method_count} methods)
- {Feature} 커맨드 통합 ({command_list})
- DLI-{xxx} 에러 코드 ({code_range})
- {test_count}개 테스트 추가
```

### Documentation 섹션

```markdown
| Document | Status | Location |
|----------|--------|----------|
| FEATURE_{FEATURE}.md | ✅ Created | `features/FEATURE_{FEATURE}.md` |
| RELEASE_{FEATURE}.md | ✅ Created | `features/RELEASE_{FEATURE}.md` |
```

---

## Agent Integration

이 skill은 다음 Agent에서 사용:
- `feature-interface-cli`: CLI 기능 구현 후
- `feature-basecamp-*`: 서비스 기능 구현 후

### Agent 워크플로우에 추가

```markdown
## Post-Implementation (MANDATORY)

completion-gate 통과 후 **반드시** docs-synchronize 실행:

1. RELEASE_*.md 작성/업데이트
2. docs-synchronize skill 실행
3. 동기화 실패 시 수정 후 재실행
4. 동기화 PASSED 후 "최종 완료" 선언
```

---

## 관련 Skills

- `completion-gate`: 코드/테스트 검증 (선행 조건)
- `implementation-checklist`: FEATURE → 체크리스트
- `documentation`: 일반 문서화 가이드
