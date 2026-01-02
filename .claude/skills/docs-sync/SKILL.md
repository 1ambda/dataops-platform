# Documentation Sync Skill

코드 변경 후 문서 동기화 검증.

## 적용 시점

- 기능 구현 완료 후
- FEATURE → RELEASE 전환 시
- 코드 리팩토링 후

---

## 검증 항목

### 1. FEATURE vs 구현 Gap 분석

```bash
# FEATURE 문서에서 API 목록 추출
grep -E "^\| .* \|" *_FEATURE.md | grep -i "api\|method"

# 실제 구현된 API 확인
grep -r "def \|fun " src/
```

### 2. README 업데이트

변경된 내용이 README에 반영되었는지:
- 새 명령어/API
- 변경된 사용법
- 새 의존성

### 3. PATTERNS.md 동기화

새 패턴 추가 시:
- 프로젝트별 `docs/PATTERNS.md` 업데이트
- 코드 예제 정확성 확인

---

## Gap 발견 시 출력

```markdown
## Documentation Gap

**FEATURE에는 있으나 미구현:**
- [ ] `XxxAPI.method_a()` - Section 4.2

**구현되었으나 문서에 없음:**
- [ ] `XxxAPI.new_method()` - 문서 추가 필요
```
