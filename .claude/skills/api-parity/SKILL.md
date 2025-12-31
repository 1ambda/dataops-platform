# API Parity Skill

CLI 커맨드와 Library API 간 1:1 대응을 검증하여 API 누락을 방지하는 프로세스.

## 문제 배경

LineageAPI GAP (GAP-L01) 발생 원인:
- `lineage.py` CLI 커맨드는 존재하나 `LineageAPI` 클래스 미구현
- FEATURE_LIBRARY.md에 명세되었으나 구현되지 않음
- DatasetAPI, MetricAPI, WorkflowAPI 등 모든 기능에 API가 있으나 Lineage만 누락
- **API parity를 자동으로 검증하는 프로세스 부재**

## 적용 시점

이 skill은 다음 상황에서 **자동** 적용:

| 트리거 | 실행 모드 |
|--------|----------|
| 새 CLI 커맨드 모듈 구현 후 | Full check |
| "Feature Complete" 선언 전 | Full check |
| `completion-gate` 실행 시 | Integrated check |
| Pull Request 코드 리뷰 시 | Quick check |
| "API parity 검사" 요청 시 | On-demand |

---

## 검증 대상

### Parity 매핑 규칙

| CLI Module | Expected API Class | Expected Test File |
|------------|--------------------|--------------------|
| `commands/{feature}.py` | `api/{feature}.py` + `{Feature}API` class | `tests/api/test_{feature}_api.py` |

### 예외 항목 (API 불필요)

| CLI Module | 이유 |
|------------|------|
| `info.py` | 단순 정보 출력 (version, environment) |
| `version.py` | CLI 전용 버전 표시 |
| `base.py` | CLI 공통 유틸리티 |
| `utils.py` | CLI 출력 헬퍼 |

---

## 검증 단계

### Step 1: CLI 모듈 스캔

```bash
# CLI 커맨드 모듈 목록 추출
ls src/dli/commands/*.py | grep -v __init__ | grep -v __pycache__
```

### Step 2: API 클래스 존재 확인

```bash
# API 파일 존재 확인
ls src/dli/api/{feature}.py 2>/dev/null || echo "MISSING: api/{feature}.py"

# API 클래스 존재 확인
grep -r "class {Feature}API" src/dli/api/{feature}.py || echo "MISSING: {Feature}API class"
```

### Step 3: Export 확인

```bash
# __init__.py에서 export 확인
grep "{Feature}API" src/dli/api/__init__.py || echo "MISSING: {Feature}API export"

# 패키지 루트 export 확인
grep "{Feature}API" src/dli/__init__.py || echo "MISSING: root export"
```

### Step 4: 테스트 파일 확인

```bash
# API 테스트 파일 존재 확인
ls tests/api/test_{feature}_api.py 2>/dev/null || echo "MISSING: test_{feature}_api.py"
```

### Step 5: 메서드 커버리지 확인

```bash
# CLI 커맨드 추출
grep -E "@{feature}_app\.command\(" src/dli/commands/{feature}.py

# API 메서드 추출
grep -E "def [a-z_]+\(" src/dli/api/{feature}.py | grep -v "__"

# CLI 커맨드 vs API 메서드 대응 확인
```

---

## 출력 형식

### Parity Matrix

```markdown
## API Parity Check Results

**Date:** {DATE}
**CLI Path:** src/dli/commands/
**API Path:** src/dli/api/

---

### Feature Parity Matrix

| Feature | CLI Module | API File | API Class | Export | Tests | Status |
|---------|------------|----------|-----------|--------|-------|--------|
| catalog | commands/catalog.py | api/catalog.py | CatalogAPI | __init__.py | test_catalog_api.py | OK |
| config | commands/config.py | api/config.py | ConfigAPI | __init__.py | test_config_api.py | OK |
| dataset | commands/dataset.py | api/dataset.py | DatasetAPI | __init__.py | test_dataset_api.py | OK |
| **lineage** | commands/lineage.py | **MISSING** | **MISSING** | **MISSING** | **MISSING** | **GAP** |
| metric | commands/metric.py | api/metric.py | MetricAPI | __init__.py | test_metric_api.py | OK |
| quality | commands/quality.py | api/quality.py | QualityAPI | __init__.py | test_quality_api.py | OK |
| transpile | commands/transpile.py | api/transpile.py | TranspileAPI | __init__.py | test_transpile_api.py | OK |
| workflow | commands/workflow.py | api/workflow.py | WorkflowAPI | __init__.py | test_workflow_api.py | OK |

---

### Summary

| Metric | Count |
|--------|-------|
| Total CLI Features | 8 |
| Features with API | 7 |
| **Features without API** | **1** |
| Parity Rate | 87.5% |

---

### Gap Details

#### GAP-L01: LineageAPI Missing

**Severity:** BLOCKER

**Evidence:**
- CLI exists: `src/dli/commands/lineage.py` (13k)
- CLI commands:
  - `lineage show <identifier>` - Show dependency graph
  - `lineage upstream <identifier>` - List upstream dependencies
  - `lineage downstream <identifier>` - List downstream dependencies
- API file: NOT FOUND
- API class: NOT FOUND
- Tests: NOT FOUND

**Root Cause:**
- FEATURE_LIBRARY.md specified LineageAPI but implementation was missed
- No automated parity check existed

**Required Actions:**
1. Create `src/dli/api/lineage.py`
2. Implement `LineageAPI` class with methods:
   - `show(identifier: str) -> LineageResult`
   - `upstream(identifier: str) -> list[str]`
   - `downstream(identifier: str) -> list[str]`
3. Add export to `src/dli/api/__init__.py`
4. Add export to `src/dli/__init__.py`
5. Create `tests/api/test_lineage_api.py`
```

---

## Gap 유형

| Type | 의미 | 심각도 | 액션 |
|------|------|--------|------|
| `OK` | API와 CLI 모두 존재 | - | 없음 |
| `GAP` | CLI 존재, API 미존재 | BLOCKER | 즉시 API 구현 |
| `PARTIAL` | API 존재, 일부 메서드 누락 | CRITICAL | 메서드 추가 |
| `NO_EXPORT` | API 존재, export 누락 | MAJOR | export 추가 |
| `NO_TEST` | API 존재, 테스트 누락 | MAJOR | 테스트 작성 |
| `EXEMPT` | 예외 대상 (info, version 등) | - | 없음 |

---

## 검증 명령어

### Quick Check (파일 존재만)

```bash
#!/bin/bash
# api-parity-quick.sh

CLI_PATH="src/dli/commands"
API_PATH="src/dli/api"

# 예외 목록
EXEMPT="info version base utils __init__"

echo "| Feature | CLI | API | Status |"
echo "|---------|-----|-----|--------|"

for cli_file in $CLI_PATH/*.py; do
    feature=$(basename "$cli_file" .py)

    # 예외 체크
    if echo "$EXEMPT" | grep -qw "$feature"; then
        continue
    fi

    cli_exists="Y"
    api_exists="N"
    status="GAP"

    if [ -f "$API_PATH/$feature.py" ]; then
        api_exists="Y"
        status="OK"
    fi

    echo "| $feature | $cli_exists | $api_exists | $status |"
done
```

### Full Check (클래스 + export + 테스트)

```bash
#!/bin/bash
# api-parity-full.sh

CLI_PATH="src/dli/commands"
API_PATH="src/dli/api"
TEST_PATH="tests/api"
INIT_FILE="src/dli/api/__init__.py"

EXEMPT="info version base utils __init__"

echo "## API Parity Full Check"
echo ""
echo "| Feature | CLI | API File | API Class | Export | Test | Status |"
echo "|---------|-----|----------|-----------|--------|------|--------|"

gap_count=0
total_count=0

for cli_file in $CLI_PATH/*.py; do
    feature=$(basename "$cli_file" .py)

    if echo "$EXEMPT" | grep -qw "$feature"; then
        continue
    fi

    total_count=$((total_count + 1))

    # PascalCase로 변환
    api_class=$(echo "$feature" | sed -r 's/(^|_)([a-z])/\U\2/g')API

    cli_exists="Y"
    api_file_exists="N"
    api_class_exists="N"
    export_exists="N"
    test_exists="N"
    status="GAP"

    # API 파일 확인
    if [ -f "$API_PATH/$feature.py" ]; then
        api_file_exists="Y"
    fi

    # API 클래스 확인
    if grep -q "class $api_class" "$API_PATH/$feature.py" 2>/dev/null; then
        api_class_exists="Y"
    fi

    # Export 확인
    if grep -q "$api_class" "$INIT_FILE" 2>/dev/null; then
        export_exists="Y"
    fi

    # 테스트 확인
    if [ -f "$TEST_PATH/test_${feature}_api.py" ]; then
        test_exists="Y"
    fi

    # 상태 판정
    if [ "$api_file_exists" = "Y" ] && [ "$api_class_exists" = "Y" ] && \
       [ "$export_exists" = "Y" ] && [ "$test_exists" = "Y" ]; then
        status="OK"
    elif [ "$api_file_exists" = "N" ]; then
        status="**GAP**"
        gap_count=$((gap_count + 1))
    elif [ "$api_class_exists" = "N" ]; then
        status="PARTIAL"
    elif [ "$export_exists" = "N" ]; then
        status="NO_EXPORT"
    elif [ "$test_exists" = "N" ]; then
        status="NO_TEST"
    fi

    echo "| $feature | $cli_exists | $api_file_exists | $api_class_exists | $export_exists | $test_exists | $status |"
done

echo ""
echo "**Summary:** $gap_count gaps found out of $total_count features"
```

---

## completion-gate 연동

`api-parity`는 `completion-gate`의 선행 조건으로 통합:

```
Feature 구현 완료 선언
       |
       v
[api-parity check] <-- 이 스킬
       |
   GAP 발견?
   /       \
  Y         N
  |         |
  v         v
GATE FAIL  completion-gate 계속
(GAP 해결 요청)
```

### Gate 조건 추가

```markdown
## Completion Gate 조건

| # | 조건 | 검증 방법 | 실패 시 액션 |
|---|------|-----------|--------------|
| 0 | **API Parity** | api-parity check | API 구현 요청 |
| 1 | FEATURE 전체 항목 구현 | ... | ... |
| ... | ... | ... | ... |
```

---

## 관련 Skills

- `completion-gate`: 완료 조건 검증 (api-parity를 포함)
- `gap-analysis`: FEATURE vs RELEASE 비교 (API 누락도 탐지)
- `implementation-checklist`: FEATURE 항목 체크리스트
- `testing`: 테스트 작성 워크플로우

---

## 사용 예시

### 명시적 호출

```markdown
# 전체 parity 검사
"API parity 검사해줘"
"CLI와 API 대응 확인해줘"

# 특정 기능 검사
"lineage API 있어?"
"quality API와 CLI 메서드 매핑 확인"
```

### 암묵적 호출

```markdown
# 새 CLI 구현 후
"lineage CLI 구현 완료"
→ api-parity 자동 실행
→ "LineageAPI가 없습니다. 구현하시겠습니까?"

# completion-gate 시
"Feature Complete"
→ completion-gate 실행
→ api-parity 포함하여 검증
→ GAP 발견 시 GATE FAIL
```

---

## 메서드 커버리지 상세 검증

### CLI 커맨드 vs API 메서드 매핑

특정 기능의 메서드 수준 parity 검증:

```markdown
## Method Parity: workflow

| CLI Command | API Method | Status |
|-------------|------------|--------|
| `run` | `run()` | OK |
| `backfill` | `backfill()` | OK |
| `stop` | `stop()` | OK |
| `status` | `get_status()` | OK |
| `list` | `list_workflows()` | OK |
| `history` | `history()` | OK |
| `pause` | `pause()` | OK |
| `unpause` | `unpause()` | OK |
| `register` | `register()` | OK |
| `unregister` | `unregister()` | OK |

**Coverage:** 10/10 (100%)
```

### 메서드 매핑 검증 명령어

```bash
# CLI 커맨드 추출
cli_commands=$(grep -oP '@\w+_app\.command\("\K[^"]+' src/dli/commands/workflow.py)

# API 메서드 추출 (public만)
api_methods=$(grep -oP 'def \K[a-z_]+(?=\()' src/dli/api/workflow.py | grep -v "^_")

# 비교
echo "CLI commands: $cli_commands"
echo "API methods: $api_methods"
```

---

## 자동 생성 템플릿

GAP 발견 시 API 클래스 템플릿 제안:

```python
# src/dli/api/lineage.py
"""Lineage Library API.

Provides programmatic access to lineage visualization and dependency analysis.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING

from dli.core.lineage import LineageClient
from dli.exceptions import DLIError

if TYPE_CHECKING:
    from dli.models import ExecutionContext


@dataclass
class LineageResult:
    """Result of lineage operation."""
    identifier: str
    upstream: list[str] = field(default_factory=list)
    downstream: list[str] = field(default_factory=list)
    graph: dict | None = None


class LineageAPI:
    """Library API for lineage operations.

    Example:
        >>> from dli.api import LineageAPI
        >>> from dli.models import ExecutionContext
        >>>
        >>> ctx = ExecutionContext(project_path=Path("/opt/airflow/dags"))
        >>> api = LineageAPI(context=ctx)
        >>> result = api.show("catalog.schema.dataset")
        >>> print(result.upstream)
    """

    def __init__(self, context: ExecutionContext) -> None:
        self._context = context
        self._client = LineageClient(context.project_path)

    def show(self, identifier: str) -> LineageResult:
        """Show full lineage graph for an identifier."""
        # TODO: Implement
        raise NotImplementedError

    def upstream(self, identifier: str, depth: int = -1) -> list[str]:
        """Get upstream dependencies."""
        # TODO: Implement
        raise NotImplementedError

    def downstream(self, identifier: str, depth: int = -1) -> list[str]:
        """Get downstream dependencies."""
        # TODO: Implement
        raise NotImplementedError
```

---

## Anti-Patterns

| Anti-Pattern | 문제 | 해결책 |
|--------------|------|--------|
| CLI만 구현 후 완료 선언 | API 사용자 지원 불가 | api-parity 검증 필수 |
| API export 누락 | `from dli import XXX` 불가 | export 검증 포함 |
| 테스트 없는 API | 품질 보증 불가 | 테스트 파일 검증 포함 |
| 메서드 불일치 | CLI/API 기능 불일치 | 메서드 커버리지 검증 |
