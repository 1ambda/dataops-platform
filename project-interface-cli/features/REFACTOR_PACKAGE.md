# REFACTOR_PACKAGE: 패키지 구조 분석 및 개선

> **Status**: Analysis Complete
> **Date**: 2025-12-30
> **Agents**: feature-interface-cli, expert-python

---

## 1. Executive Summary

두 Agent의 협업 분석 결과, **현재 패키지 구조는 의도적이며 Python 커뮤니티 best practice에 부합**합니다.

| 질문 | 결론 |
|------|------|
| `tests/cli/`를 `tests/commands/`로 변경해야 하는가? | **아니오** - 현재 naming이 더 정확함 |
| src와 tests 구조가 다른 것이 문제인가? | **아니오** - semantic grouping은 업계 표준 |
| 개선이 필요한 부분은? | **P0**: adapters 테스트 추가, **P1**: 문서화 개선 |

---

## 2. Current Structure Analysis

### 2.1 Source Structure (`src/dli/`)

```
src/dli/
├── api/              # Library API (DatasetAPI, MetricAPI, etc.)
├── commands/         # CLI layer (Typer commands)
├── core/             # Business logic (shared by api/ and commands/)
├── models/           # Common models (ExecutionContext, Results)
├── adapters/         # External backends (BigQuery)
├── exceptions.py     # Exception hierarchy
└── main.py           # CLI entry point
```

### 2.2 Test Structure (`tests/`)

```
tests/
├── api/              # API tests ✓ (1:1 mapping)
├── cli/              # CLI tests ⚠ (semantic name, not "commands")
├── core/             # Core tests ✓ (1:1 mapping)
├── fixtures/         # Test data
└── [adapters/]       # ✗ MISSING
```

### 2.3 Dependency Flow

```
commands/     api/
    \          /
     \        /
      v      v
       core/        ← Single source of truth
         |
         v
      adapters/     ← External integrations
```

**핵심 설계 원칙**: `core/`가 비즈니스 로직의 단일 진실점이며, `api/`와 `commands/` 모두 `core/`를 공유합니다.

---

## 3. Agent Consensus: Naming Analysis

### 3.1 feature-interface-cli Agent 의견

> "The `commands/` -> `cli/` naming difference is **intentional and documented** in CONTRIBUTING.md. The test directory name describes the **type of testing** rather than strictly mirroring source paths."

**근거:**
- CONTRIBUTING.md에 이미 문서화됨
- `test_{feature}_cmd.py` 패턴으로 일관성 유지
- 마이그레이션 비용(13개 파일 이동) > 이점

### 3.2 expert-python Agent 의견

> "The naming difference is **intentional and beneficial**. It correctly signals that CLI tests verify user-facing behavior, not internal command module implementation."

**근거:**
- PyPA/pytest는 exact mirror를 강제하지 않음
- pytest, FastAPI, Click 모두 semantic grouping 사용
- `commands/` = 구현 세부사항 vs `cli/` = 테스트 카테고리

### 3.3 Industry Examples

| Project | Source | Tests | Pattern |
|---------|--------|-------|---------|
| pytest | `src/pytest/` | `testing/` | Semantic |
| FastAPI | `fastapi/` | `tests/` | Semantic |
| Click | `src/click/` | `tests/` | Behavior-based |
| Typer | `typer/` | `tests/` | Flat |

---

## 4. Issues & Priorities

### P0: Missing Adapter Tests (Critical)

| Source | Test | Status |
|--------|------|--------|
| `adapters/bigquery.py` | None | ❌ **MISSING** |

**BigQueryExecutor** (150+ lines)는 다음 메서드를 포함:
- `execute()` - 쿼리 실행 + 타임아웃
- `dry_run()` - 비용 추정
- `test_connection()` - 연결 테스트
- `get_table_schema()` - 스키마 조회

**Action Required:**
```bash
mkdir -p tests/adapters
touch tests/adapters/__init__.py
touch tests/adapters/test_bigquery.py
```

### P1: Documentation Clarification (High)

CONTRIBUTING.md에 naming rationale 추가 필요:

```markdown
### Test Directory Naming Convention

| Source | Tests | Rationale |
|--------|-------|-----------|
| `commands/` | `cli/` | Tests user-facing CLI behavior, not internal commands |
| `api/` | `api/` | Direct 1:1 mapping |
| `core/` | `core/` | Direct 1:1 mapping |
| `adapters/` | `adapters/` | Direct 1:1 mapping |
```

### P2: Structure Alignment (Low - No Action)

**결정: 현재 구조 유지**

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| A: Keep `cli/` | 이미 문서화됨, 마이그레이션 불필요 | IDE auto-resolve 안됨 | ✓ **선택** |
| B: Rename to `commands/` | 완벽한 미러링 | Git history 손실, 문서 수정 필요 | ✗ |

---

## 5. Implementation Plan

### Phase 1: Add Adapter Tests (P0)

**Effort**: 2-4 hours

```python
# tests/adapters/test_bigquery.py
import pytest
from unittest.mock import MagicMock, patch

class TestBigQueryExecutorImport:
    """Test optional dependency handling."""

    def test_import_error_when_missing_dependency(self):
        """Should handle missing google-cloud-bigquery gracefully."""
        pass

class TestBigQueryExecutor:
    """Tests with mocked BigQuery client."""

    @pytest.fixture
    def mock_bq_client(self):
        with patch('dli.adapters.bigquery.bigquery') as mock:
            yield mock

    def test_execute_success(self, mock_bq_client):
        """Should return ExecutionResult with data."""
        pass

    def test_execute_timeout(self, mock_bq_client):
        """Should handle query timeout."""
        pass

    def test_dry_run_cost_estimation(self, mock_bq_client):
        """Should calculate cost from bytes processed."""
        pass

    def test_connection_test(self, mock_bq_client):
        """Should verify connectivity."""
        pass
```

### Phase 2: Update Documentation (P1)

**Effort**: 30 minutes

1. CONTRIBUTING.md에 "Test Directory Naming Convention" 섹션 추가
2. PATTERNS.md Quick Reference 업데이트

### Phase 3: Memory Update

```bash
# Update Serena memory with new test patterns
mcp__serena__edit_memory cli_test_patterns ...
```

---

## 6. Final Test Structure

```
tests/
├── __init__.py
├── conftest.py              # Root fixtures
│
├── api/                     # [KEEP] Library API tests
│   ├── test_catalog_api.py
│   ├── test_config_api.py
│   ├── test_dataset_api.py
│   ├── test_exceptions.py   # Exception hierarchy tests
│   ├── test_common.py       # Common models tests
│   ├── test_metric_api.py
│   └── test_transpile_api.py
│
├── cli/                     # [KEEP] CLI integration tests
│   ├── conftest.py          # CliRunner fixtures
│   ├── test_base.py
│   ├── test_catalog_cmd.py
│   ├── test_config_cmd.py
│   ├── test_dataset_cmd.py
│   ├── test_info_cmd.py
│   ├── test_lineage_cmd.py
│   ├── test_metric_cmd.py
│   ├── test_quality_cmd.py
│   ├── test_transpile_cmd.py
│   ├── test_utils.py
│   ├── test_workflow_cmd.py
│   └── test_main.py
│
├── core/                    # [KEEP] Core module unit tests
│   ├── catalog/
│   ├── lineage/
│   ├── quality/
│   ├── transpile/
│   ├── validation/
│   ├── workflow/
│   └── test_*.py
│
├── adapters/                # [NEW] External adapter tests
│   ├── __init__.py
│   └── test_bigquery.py
│
└── fixtures/                # [KEEP] Test data
    └── sample_project/
```

---

## 7. Test File Mapping Reference

| Source Path | Test Path | Status |
|-------------|-----------|--------|
| `src/dli/commands/{feature}.py` | `tests/cli/test_{feature}_cmd.py` | ✓ |
| `src/dli/api/{feature}.py` | `tests/api/test_{feature}_api.py` | ✓ |
| `src/dli/core/{module}.py` | `tests/core/test_{module}.py` | ✓ |
| `src/dli/core/{subdir}/` | `tests/core/{subdir}/` | ✓ |
| `src/dli/adapters/bigquery.py` | `tests/adapters/test_bigquery.py` | ⚠ **TODO** |
| `src/dli/exceptions.py` | `tests/api/test_exceptions.py` | ✓ |
| `src/dli/models/common.py` | `tests/api/test_common.py` | ✓ |

---

## 8. Conclusion

### Key Decisions

1. **`tests/cli/` 유지** - semantic naming이 더 명확함
2. **adapters 테스트 추가** - P0 priority
3. **문서화 개선** - P1 priority

### Rationale Summary

| Aspect | Decision | Reasoning |
|--------|----------|-----------|
| Naming | Keep `cli/` | Tests behavior, not implementation |
| Mirroring | Partial | Semantic grouping is Python standard |
| Gaps | Fix adapters | Critical production code without tests |

### Next Steps

- [ ] Create `tests/adapters/test_bigquery.py`
- [ ] Update CONTRIBUTING.md with naming rationale
- [ ] Update `cli_test_patterns` memory
