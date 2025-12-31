# Integration Finder Skill

새로운 모듈 구현 시 기존 관련 모듈과의 연동점을 사전에 찾아 연결을 보장하는 프로세스.

## 문제 배경

> **배경 사례**: Transpile 구현 시 `core/transpile/` 모듈이 기존 `core/renderer.py`, `core/templates.py` (Jinja)와 연결되지 않은 채 독립적으로 구현됨

Agent가 새 모듈을 구현할 때 발생하는 문제:
- 기존 관련 기능이 이미 존재하는데 새로 구현
- 기존 모듈과 연동이 필요하지만 연결 누락
- 모듈 간 중복 코드 발생
- FEATURE 명세에서 기존 모듈 참조 누락

## 적용 시점

이 skill은 다음 상황에서 **자동** 적용:
- 새 모듈/패키지 생성 시 (`mkdir`, `touch` 등)
- FEATURE_*.md에서 새 기능 구현 시작 시
- "XX 모듈 구현", "XX 기능 추가" 요청 시

---

## Integration 탐색 프로세스

### Step 1: 키워드 기반 관련 모듈 탐색

새 모듈의 핵심 키워드로 기존 코드베이스 검색:

```bash
# 예: Transpile 모듈 구현 시
keywords=("jinja" "template" "render" "sql" "transform")

for keyword in "${keywords[@]}"; do
  echo "=== Searching for: $keyword ==="
  grep -r "$keyword" src/dli/ --include="*.py" -l | head -10
done
```

**자동 키워드 추출:**
- FEATURE 문서에서 기술 키워드 추출
- 모듈 이름에서 유사 단어 파생
- 관련 도구/라이브러리 이름 (예: SQLGlot, Jinja)

### Step 2: Import Graph 분석

기존 모듈 간 연결 관계 파악:

```bash
# 기존 core 모듈의 import 관계 확인
grep -r "^from dli.core" src/dli/core/ --include="*.py" | head -20

# 특정 모듈 사용처 확인
grep -r "from dli.core.renderer" src/dli/ --include="*.py"
grep -r "from dli.core.templates" src/dli/ --include="*.py"
```

### Step 3: 연동 후보 목록 생성

```markdown
## Integration Candidates: {NEW_MODULE}

### 발견된 관련 모듈

| 관련 모듈 | 유사도 | 연동 필요성 | 권장 액션 |
|-----------|--------|-------------|-----------|
| `core/renderer.py` | 높음 (Jinja) | ✅ 필수 | import 필요 |
| `core/templates.py` | 높음 (Template) | ✅ 필수 | import 필요 |
| `core/validation/` | 중간 (SQL 검증) | ⚠️ 검토 | 재사용 고려 |
| `core/lineage/` | 낮음 (SQL 분석) | ❓ 선택 | Phase 2 연동 |

### 연동 권장 사항

1. **필수 연동** (BLOCKER):
   - `core/renderer.py` → Jinja 템플릿 렌더링
   - `core/templates.py` → 템플릿 로딩

2. **권장 연동** (WARNING):
   - 기존 validation 패턴 재사용

3. **향후 연동** (INFO):
   - Phase 2에서 lineage 연동 고려
```

---

## 연동 검증

### Pre-Implementation Check

새 모듈 생성 전 확인:

```markdown
## Pre-Implementation: Integration Check

### 질문

1. **기존 구현 확인**: 이 기능이 이미 존재하는가?
   - 검색 결과: [관련 모듈 목록]

2. **연동 필요성**: 기존 모듈과 연결이 필요한가?
   - 권장: [연동 대상 목록]

3. **중복 방지**: 새로 구현하는 것이 맞는가?
   - 결론: [신규 구현 / 기존 확장]
```

### Post-Implementation Check

구현 완료 후 확인:

```bash
# 새 모듈에서 관련 모듈 import 확인
grep -r "from dli.core.renderer\|from dli.core.templates" src/dli/core/{new_module}/

# import 없으면 INTEGRATION_MISSING
if [ -z "$(grep result)" ]; then
  echo "INTEGRATION_MISSING: {new_module} should import from related modules"
fi
```

---

## gap-analysis 연동

`gap-analysis` skill에서 `INTEGRATION_MISSING` gap 감지:

```markdown
### Integration Gap Detection

| New Module | Related Existing Module | Integration | Status |
|------------|------------------------|-------------|--------|
| `core/transpile/engine.py` | `core/renderer.py` (Jinja) | Not connected | ⚠️ **INTEGRATION_MISSING** |
| `core/transpile/engine.py` | `core/templates.py` | Not connected | ⚠️ **INTEGRATION_MISSING** |

**Action Required:** Use `integration-finder` skill to connect modules.
```

---

## 출력 형식

### 연동 발견 시

```markdown
## Integration Finder: core/transpile/

### 🔍 Related Modules Found

기존 코드베이스에서 다음 관련 모듈을 발견했습니다:

| Module | Relevance | Reason |
|--------|-----------|--------|
| `core/renderer.py` | 높음 | Jinja2 템플릿 렌더링 |
| `core/templates.py` | 높음 | 템플릿 파일 로딩 |
| `core/validation/sql_validator.py` | 중간 | SQL 검증 로직 |

### ⚠️ Integration Required

다음 연동을 **구현 시작 전** 검토하세요:

1. **Jinja 렌더링**:
   - `core/renderer.py`의 `render_template()` 재사용
   - 새로 구현하지 말고 import

2. **템플릿 로딩**:
   - `core/templates.py`의 `load_template()` 재사용

### 예시 연동 코드

```python
# core/transpile/engine.py
from dli.core.renderer import render_template
from dli.core.templates import load_template

class TranspileEngine:
    def __init__(self):
        self.template_loader = load_template  # 기존 모듈 활용
```

### 다음 단계

1. [ ] 위 관련 모듈 코드 확인
2. [ ] 연동 필요 여부 결정
3. [ ] FEATURE 문서에 연동 명시
4. [ ] 구현 시 import 추가
```

### 연동 없이 진행 시 (경고)

```markdown
## ⚠️ Integration Warning

`core/transpile/` 구현이 시작되었으나 관련 모듈 연동이 감지되지 않습니다.

### Missing Integrations

| Expected Integration | Status |
|---------------------|--------|
| `from dli.core.renderer` | ❌ Not found |
| `from dli.core.templates` | ❌ Not found |

### Action Required

1. 연동이 **불필요**한 경우:
   - 사유를 FEATURE 문서에 명시
   - 예: "Jinja 렌더링은 Phase 2에서 추가"

2. 연동이 **필요**한 경우:
   - 구현 전에 import 추가
   - 기존 함수 재사용
```

---

## 자동화 트리거

| 트리거 | 실행 |
|--------|------|
| `mkdir src/dli/core/{new}/` | 자동 실행 |
| FEATURE 문서 내 "신규 모듈" 언급 | 권장 |
| `implementation-checklist` 생성 시 | 연동 |

---

## 관련 Skills

- `gap-analysis`: INTEGRATION_MISSING gap 감지 (후속)
- `implementation-checklist`: 연동 항목 체크리스트 포함 (연동)
- `completion-gate`: 연동 검증 (검증)
- `code-search`: 관련 코드 탐색 (도구)

---

## Agent Integration

### feature-interface-cli Agent 워크플로우

```
FEATURE_*.md 수신
       ↓
[integration-finder skill 실행] ← 신규
       ↓
관련 모듈 목록 출력
       ↓
implementation-checklist 생성
       ↓
구현 진행 (연동 포함)
       ↓
completion-gate (연동 검증 포함)
```

### 검색 패턴

```python
INTEGRATION_KEYWORDS = {
    "transpile": ["jinja", "template", "render", "sql", "transform"],
    "workflow": ["airflow", "dag", "schedule", "orchestration"],
    "quality": ["validation", "test", "check", "assertion"],
    "catalog": ["metadata", "schema", "table", "column"],
}
```

---

## 사용 예시

```markdown
# 암묵적 호출
"Transpile 모듈 구현 시작해줘"
→ integration-finder 자동 실행
→ 관련 모듈 (renderer, templates) 발견 및 안내

# 명시적 호출
"Transpile과 관련된 기존 모듈 찾아줘"
→ integration-finder 실행
→ 연동 후보 목록 출력
```

---

**Last Updated:** 2026-01-01
**Created by:** GAP Analysis (Transpile Feature)
