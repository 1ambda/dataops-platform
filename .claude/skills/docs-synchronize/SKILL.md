# Documentation Synchronization Skill

구현 완료 후 문서 간 일관성을 검증하고 동기화하는 프로세스.

## 문제 배경

Agent가 코드 구현은 완료했으나 문서 동기화를 누락하는 문제:
- RELEASE_*.md 작성 후 STATUS.md 업데이트 누락
- FEATURE vs RELEASE 버전 불일치
- Serena memory 동기화 누락
- Changelog 항목 누락

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
- [ ] Test Results 기록
- [ ] Changelog 항목 추가

### STATUS.md
- [ ] 관련 섹션에 상태 반영 (✅ Complete)
- [ ] Changelog에 버전별 내용 추가
- [ ] Documentation 섹션에 문서 추가
- [ ] Related Documents 링크 추가

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

### Summary

- 4/4 문서 동기화 완료
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

### Required Actions

1. **STATUS.md Changelog 추가:**
   ```markdown
   ### v0.4.0 (2025-12-31)
   - Catalog 커맨드 v1.2.0 통합
   - CatalogAPI Result 모델 추가
   - DLI-7xx 에러 코드 (701-706)
   ```

2. **Serena memory 업데이트:**
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
