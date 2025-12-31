# Implementation Verification Skill

구현 완료 선언 전 실제 코드가 작성되었는지 검증하는 프로세스.

## 문제 배경

Agent가 "구현 완료"라고 보고했지만 실제로는:
- 명세 문서만 작성하고 코드 미작성
- 기존 코드를 "이미 있음"으로 오인
- 테스트 없이 완료 선언

## 검증 체크리스트

### 1. 코드 존재 확인 (필수)

```bash
# 명세에 정의된 클래스/함수가 실제 코드에 존재하는지 확인
grep -r "class CatalogListResult" src/
grep -r "def list_tables" src/
```

**판단 기준**:
- grep 결과가 없으면 → 미구현
- grep 결과가 있으면 → 파일 읽어서 내용 확인

### 2. 구현 완료 선언 조건

"구현 완료" 선언 전 반드시 아래 정보 제시:

| 항목 | 예시 |
|------|------|
| **새로 작성한 파일** | `src/dli/models/common.py:405-485 (+81 lines)` |
| **수정한 파일** | `src/dli/api/catalog.py:89-174 (list_tables 반환타입 변경)` |
| **테스트 결과** | `pytest tests/api/test_catalog_api.py → 30 passed` |
| **전체 테스트** | `pytest tests/ → 1573 passed` |

### 3. 거짓 양성 방지

**위험 패턴**:
```
❌ "이미 구현되어 있습니다" → grep으로 확인 없이 판단
❌ "명세를 작성했습니다" → 코드 작성 없이 완료 선언
❌ "테스트가 통과합니다" → 실제 테스트 실행 없이 판단
```

**올바른 패턴**:
```
✅ grep -r "CatalogListResult" src/ → 결과 없음 → 구현 필요
✅ 코드 작성 후 → pytest 실행 → 결과 확인 → 완료 선언
✅ git diff --stat 으로 변경 내역 제시
```

## 구현 완료 템플릿

```markdown
## 구현 완료

### 새로 작성한 코드
- `src/dli/models/common.py:405-485` - CatalogListResult, TableDetailResult, CatalogSearchResult 모델 (+81 lines)

### 수정한 코드
- `src/dli/api/catalog.py:89-174` - list_tables, get, search 반환타입 변경
- `tests/api/test_catalog_api.py:17-239` - 테스트 반환타입 수정

### 테스트 결과
```
pytest tests/api/test_catalog_api.py → 30 passed
pytest tests/ → 1573 passed
```

### 검증 명령어
```bash
grep -r "class CatalogListResult" src/  # 존재 확인
grep -r "class TableDetailResult" src/  # 존재 확인
```
```

## 적용 시점

이 skill은 다음 상황에서 자동 적용:
- Agent가 "구현 완료", "완료", "done" 등 선언 시
- FEATURE_*.md 명세 기반 구현 후
- 새 기능 추가 후 리뷰 요청 시

## 관련 Skills

- `code-review`: 코드 품질 검증
- `testing`: TDD 워크플로우
- `spec-validation`: 명세 품질 검증
