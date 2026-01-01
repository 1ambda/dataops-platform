# GAP Analysis: Lineage Feature

> **Auto-generated:** 2026-01-01
> **Last Updated:** 2026-01-01
> **Analyzed by:** meta-agent, expert-python, feature-interface-cli
> **Implementation Completeness:** 58% (11/19 items)

---

## 1. Executive Summary

### 1.1 Overall Assessment

| Metric | Value |
|--------|-------|
| FEATURE Spec Items | 19 |
| Implemented Items | 11 |
| **Completion Rate** | **58%** |
| Spec Quality Grade | B- |
| Documentation Drift | High |

The Lineage feature MVP (CLI commands and core models) is **functionally complete**, but the **Library API layer is completely missing**. This creates an API parity gap where all other features (Dataset, Metric, Quality, Workflow, Catalog, Transpile) have Library APIs, but Lineage does not. The feature also has **documentation drift** where LINEAGE_RELEASE.md states "0 tests" but 52 tests actually exist.

### 1.2 Severity Assessment

| Severity | Count | Impact |
|----------|-------|--------|
| Critical | 1 | Blocks programmatic access for Airflow DAGs |
| High | 2 | Major functionality gaps |
| Medium | 4 | Feature completeness issues |
| Low | 2 | Documentation/polish issues |

### 1.3 Key Findings

| Finding | Severity | Root Cause | Status |
|---------|----------|------------|--------|
| ~~**No LineageAPI class**~~ | Critical | Listed as "Phase 2" in LIBRARY_RELEASE.md, never prioritized | ✅ **Resolved** |
| ~~**No DLI-9xx error codes**~~ | High | LineageClientError not integrated with DLIError hierarchy | ✅ **Resolved** |
| **No column-level lineage** | High | Scoped out of MVP (expected) | Deferred |
| **Documentation drift** (test count) | Medium | RELEASE says "0 tests" but 52 tests exist | Open |
| **No export formats** (Mermaid/DOT) | Medium | Not specified in MVP | Deferred |
| **No OpenLineage integration** | Low | Not specified | Future |

---

## 2. Detailed Gap Inventory

### 2.1 Critical Gaps (Blocks Production Use)

| ID | Gap | FEATURE Spec | Current Status | Business Impact |
|----|-----|--------------|----------------|-----------------|
| GAP-L01 | ~~**LineageAPI class missing**~~ | LIBRARY_FEATURE.md Section 3.7 | ✅ **Implemented** (api/lineage.py, 367 lines) | ~~Airflow DAGs cannot programmatically query lineage~~ |

**Impact Analysis for GAP-L01:**
- All other API classes exist: DatasetAPI, MetricAPI, QualityAPI, WorkflowAPI, CatalogAPI, TranspileAPI
- LineageAPI specified in LIBRARY_FEATURE.md with 4 methods: `get_lineage()`, `get_upstream()`, `get_downstream()`, `get_impact_analysis()`
- Without LineageAPI, users must shell out to CLI (`subprocess.run(["dli", "lineage", ...])`)

### 2.2 High Severity Gaps (Major Functionality Missing)

| ID | Gap | FEATURE Spec | Current Status | Impact |
|----|-----|--------------|----------------|--------|
| GAP-L02 | ~~**No DLI-9xx error codes**~~ | Pattern from DLI-6xx (Quality), DLI-7xx (Catalog), DLI-8xx (Workflow) | ✅ **Implemented** (DLI-900 ~ DLI-904, 3 exception classes) | ~~No programmatic error handling~~ |
| GAP-L03 | **Column-level lineage not supported** | LINEAGE_RELEASE.md:334 "Phase 2" | Table-level only | Cannot trace column transformations |

**Recommended DLI-9xx Error Codes:**

| Code | Name | Description |
|------|------|-------------|
| DLI-900 | LINEAGE_NOT_FOUND | Resource not found in lineage graph |
| DLI-901 | LINEAGE_DEPTH_EXCEEDED | Maximum depth limit exceeded |
| DLI-902 | LINEAGE_CYCLE_DETECTED | Circular dependency detected |
| DLI-903 | LINEAGE_SERVER_ERROR | Server failed to compute lineage |
| DLI-904 | LINEAGE_TIMEOUT | Lineage query timed out |

### 2.3 Medium Severity Gaps

| ID | Gap | Notes | Status |
|----|-----|-------|--------|
| GAP-L04 | **Documentation drift: test count** | LINEAGE_RELEASE.md says "Test count: 0 (pending)" but 52 tests exist | Open |
| GAP-L05 | **No Mermaid/GraphViz export** | Only table/JSON supported; RELEASE mentions Phase 3 | Deferred |
| GAP-L06 | **No local SQLGlot processing** | Server-based only; cannot extract lineage from local specs | Deferred |
| GAP-L07 | **No impact analysis flag** | `--impact` flag mentioned in RELEASE but not implemented | Deferred |

### 2.4 Low Severity Gaps

| ID | Gap | Notes | Status |
|----|-----|-------|--------|
| GAP-L08 | **No lineage caching** | Each query hits the server | Future |
| GAP-L09 | **No OpenLineage integration** | Industry-standard lineage format not supported | Future |

### 2.5 Documentation Gaps

| ID | Gap | Location | Issue |
|----|-----|----------|-------|
| GAP-L10 | Test count mismatch | LINEAGE_RELEASE.md:374 | States "0" but actual count is 52 |
| GAP-L11 | LineageAPI not in api/__init__.py | api/__init__.py | Missing from public exports |
| GAP-L12 | No LINEAGE_FEATURE.md | features/ | Only RELEASE exists, no FEATURE spec |

---

## 3. Root Cause Analysis

### 3.1 Agent/Skill System Root Causes

| Root Cause | Affected Gaps | Explanation | Recommended Fix |
|------------|---------------|-------------|-----------------|
| **RC-1: Phase 2 Deprioritization** | GAP-L01, GAP-L03, GAP-L05-07 | LineageAPI listed as "P1" in LIBRARY_RELEASE.md but never implemented; QualityAPI and WorkflowAPI were prioritized first | Add phase-completion gates to `completion-gate` skill |
| **RC-2: Exception Hierarchy Isolation** | GAP-L02 | `LineageClientError` created as standalone class, not integrated with `DLIError` hierarchy | Add "exception integration check" to `implementation-checklist` skill |
| **RC-3: Documentation-Implementation Drift** | GAP-L04, GAP-L10 | RELEASE document frozen after initial creation; tests added but RELEASE not updated | Strengthen `docs-synchronize` skill triggers |
| **RC-4: API Parity Check Missing** | GAP-L01 | No skill verifies all features have corresponding API classes | Add `api-parity` skill |

### 3.2 Process Root Causes

| Root Cause | Symptoms | Recommended Fix |
|------------|----------|-----------------|
| **RC-5: No Feature-API Alignment Gate** | LineageAPI specified but never implemented | Add pre-release "API parity check" |
| **RC-6: Backlog Decay** | P1 items in LIBRARY_RELEASE.md never addressed | Add backlog review skill |
| **RC-7: No Cross-Module Exception Standard** | Each module creates its own exception class | Create "exception standardization" checklist |

### 3.3 Why LineageAPI Was Not Created

**Timeline Analysis:**

1. **LIBRARY_FEATURE.md** specified LineageAPI (Section 3.7) with 4 methods
2. **LIBRARY_RELEASE.md** listed LineageAPI as "P1" priority in "Future Work"
3. **Implementation order:** DatasetAPI -> MetricAPI -> TranspileAPI -> CatalogAPI -> ConfigAPI -> QualityAPI -> WorkflowAPI
4. **LineageAPI was skipped** because:
   - Lineage CLI was already working (reduced urgency)
   - QualityAPI and WorkflowAPI had more business pressure
   - No automated check for API parity

### 3.4 Why Tests Exist But Documentation Says Zero

**Root Cause:** The LINEAGE_RELEASE.md was created when MVP shipped, correctly stating "0 tests". Tests were added later (52 tests) but the RELEASE was never updated. The `docs-synchronize` skill exists but lacks triggers for test count changes.

---

## 4. Priority Assessment (Agent Consensus)

### 4.1 Priority Matrix

```
            HIGH BUSINESS VALUE    LOW BUSINESS VALUE
          +----------------------+----------------------+
 EASY TO  | P0: LineageAPI class | P2: Doc sync (tests) |
 IMPLEMENT| P0: DLI-9xx errors   | P2: FEATURE_LINEAGE  |
          +----------------------+----------------------+
 HARD TO  | P1: Column lineage   | P3: OpenLineage      |
 IMPLEMENT| P1: Export formats   | P3: Caching          |
          +----------------------+----------------------+
```

### 4.2 Recommended Priority Order

| Priority | Gap | Effort | Dependencies | Status |
|----------|-----|--------|--------------|--------|
| **P0** | GAP-L01: LineageAPI | 4 hours | None (LineageClient exists) | ✅ **Done** |
| **P0** | GAP-L02: DLI-9xx error codes | 1 hour | None | ✅ **Done** |
| **P0** | GAP-L04/L10: Fix documentation drift | 30 min | None | ✅ **Done** |
| **P1** | GAP-L03: Column-level lineage | 2 weeks | Server API support | Backlog |
| **P2** | GAP-L05: Mermaid/GraphViz export | 1 day | None | Backlog |
| **P2** | GAP-L06: Local SQLGlot processing | 3 days | SQLGlot integration | Backlog |
| **P3** | GAP-L09: OpenLineage integration | 1 week | Design decision | Future |

### 4.3 Quick Wins (< 1 Day Implementation)

| Item | Effort | Impact | Implementation |
|------|--------|--------|----------------|
| LineageAPI class | 4 hours | High | Wrap LineageClient in API pattern |
| DLI-9xx errors | 1 hour | Medium | Add to exceptions.py, refactor LineageClientError |
| Fix RELEASE test count | 10 min | Low | Update LINEAGE_RELEASE.md:374 |
| Add test_lineage_api.py | 2 hours | Medium | Follow test_workflow_api.py pattern |

---

## 5. Implementation Templates

### 5.1 LineageAPI Class (GAP-L01 Fix)

Reference: `src/dli/api/workflow.py` for pattern

```python
# src/dli/api/lineage.py
"""Lineage Library API."""

from __future__ import annotations

from dli.core.lineage import LineageResult
from dli.core.lineage.client import LineageClient
from dli.models import ExecutionContext

__all__ = ["LineageAPI"]


class LineageAPI:
    """Lineage Library API for programmatic access."""

    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()

    def get_lineage(
        self,
        resource_name: str,
        direction: str = "both",
        depth: int = -1,
    ) -> LineageResult:
        """Get full lineage for a resource."""
        client = self._get_client()
        return client.get_lineage(resource_name, direction, depth)

    def get_upstream(self, resource_name: str, depth: int = -1) -> LineageResult:
        """Get upstream dependencies."""
        client = self._get_client()
        return client.get_upstream(resource_name, depth)

    def get_downstream(self, resource_name: str, depth: int = -1) -> LineageResult:
        """Get downstream dependents."""
        client = self._get_client()
        return client.get_downstream(resource_name, depth)

    def _get_client(self) -> LineageClient:
        """Get or create LineageClient."""
        from dli.commands.base import get_client
        basecamp_client = get_client(self.context.project_path)
        return LineageClient(basecamp_client)
```

### 5.2 DLI-9xx Error Codes (GAP-L02 Fix)

Add to `src/dli/exceptions.py`:

```python
# Lineage Errors (DLI-9xx)
LINEAGE_NOT_FOUND = "DLI-900"
LINEAGE_DEPTH_EXCEEDED = "DLI-901"
LINEAGE_CYCLE_DETECTED = "DLI-902"
LINEAGE_SERVER_ERROR = "DLI-903"
LINEAGE_TIMEOUT = "DLI-904"


@dataclass
class LineageNotFoundError(DLIError):
    """Lineage resource not found."""
    code: ErrorCode = ErrorCode.LINEAGE_NOT_FOUND
    resource_name: str = ""


@dataclass
class LineageError(DLIError):
    """General lineage error."""
    code: ErrorCode = ErrorCode.LINEAGE_SERVER_ERROR
    resource_name: str = ""
```

---

## 6. Agent/Skill Improvement Recommendations

### 6.1 New Skills Recommended

| Skill | Purpose | Addresses |
|-------|---------|-----------|
| `api-parity` | Verify all features have corresponding API classes | RC-4, GAP-L01 |
| `exception-integration` | Ensure new exceptions use DLIError hierarchy | RC-2, GAP-L02 |
| `backlog-review` | Periodic review of P1/P2 items in RELEASE docs | RC-6 |

### 6.2 Existing Skill Improvements

| Skill | Current | Improvement |
|-------|---------|-------------|
| `completion-gate` | Checks tests exist | Add "API class exists" check for features with CLI |
| `docs-synchronize` | Manual trigger | Add test count change detection trigger |
| `implementation-checklist` | Generic checks | Add "exception uses DLIError hierarchy" check |
| `integration-finder` | Finds related modules | Add "API class check" for new features |

### 6.3 Agent Coordination Improvements

| Issue | Current | Recommended |
|-------|---------|-------------|
| API parity not enforced | Features ship without API | Add API-completeness gate to feature-interface-cli agent |
| Exception isolation | Each module creates own exceptions | Add exception review step to code-review agent |
| Documentation drift | RELEASE not updated after changes | Add doc-sync reminder to commit workflow |

---

## 7. Verification Checklist

### 7.1 Pre-Implementation Verification

```bash
# Verify LineageAPI does not exist
grep -r "class LineageAPI" project-interface-cli/src/dli/api/
# Should return: (no output)

# Verify DLI-9xx codes do not exist
grep "DLI-9" project-interface-cli/src/dli/exceptions.py
# Should return: (no output)

# Count existing lineage tests
cd project-interface-cli && uv run pytest tests/core/lineage/ tests/cli/test_lineage_cmd.py --collect-only 2>/dev/null | grep "<Function" | wc -l
# Should return: 52
```

### 7.2 Post-Implementation Verification

```bash
# Verify LineageAPI exists
grep -r "class LineageAPI" project-interface-cli/src/dli/api/
# Should return: src/dli/api/lineage.py:class LineageAPI:

# Verify LineageAPI in exports
grep "LineageAPI" project-interface-cli/src/dli/api/__init__.py
# Should return: from dli.api.lineage import LineageAPI

# Verify DLI-9xx codes exist
grep "DLI-9" project-interface-cli/src/dli/exceptions.py
# Should return: 5 codes (900-904)

# Verify test_lineage_api.py exists
ls -la project-interface-cli/tests/api/test_lineage_api.py
# Should exist

# Run all lineage tests
cd project-interface-cli && uv run pytest tests/core/lineage/ tests/cli/test_lineage_cmd.py tests/api/test_lineage_api.py -v
```

### 7.3 Documentation Sync Verification

```bash
# Verify LINEAGE_RELEASE.md test count is updated
grep "Test count" project-interface-cli/features/LINEAGE_RELEASE.md
# Should return: 52+ (not 0)

# Verify LineageAPI in api/__init__.py __all__
grep -A20 "__all__" project-interface-cli/src/dli/api/__init__.py | grep "LineageAPI"
# Should return: "LineageAPI",
```

---

## 8. Appendix: Comparison with Other API Modules

### 8.1 API Completeness Matrix

| Feature | CLI | Core Model | Client | Library API | API Tests | Error Codes |
|---------|-----|------------|--------|-------------|-----------|-------------|
| Dataset | show/run/list | DatasetSpec | DatasetService | DatasetAPI | 35 tests | DLI-1xx |
| Metric | show/run/list | MetricSpec | MetricService | MetricAPI | 35 tests | DLI-1xx |
| Quality | run/validate | QualitySpec | QualityExecutor | QualityAPI | 30 tests | DLI-6xx |
| Workflow | run/status/list | WorkflowSpec | BasecampClient | WorkflowAPI | 45 tests | DLI-8xx |
| Catalog | list/get | TableInfo | CatalogReader | CatalogAPI | 30 tests | DLI-7xx |
| Transpile | sql/file | TranspileResult | TranspileEngine | TranspileAPI | 28 tests | DLI-3xx |
| **Lineage** | show/up/down | LineageResult | LineageClient | **MISSING** | **0 API tests** | **MISSING** |

### 8.2 Test Count Comparison

| Module | Core Tests | CLI Tests | API Tests | Total |
|--------|------------|-----------|-----------|-------|
| Quality | 838 | - | 30 | 868 |
| Workflow | 946+668 | 1279 | 45 | 2938 |
| Catalog | 806 | 523 | 30 | 1359 |
| Transpile | 713 | 605 | 28 | 1346 |
| **Lineage** | **872** | **560** | **0** | **1432** |

**Observation:** Lineage has robust core and CLI tests (1432 lines), but zero API tests because LineageAPI doesn't exist.

---

## 9. Completion Tracking

| Date | Gap ID | Action | Status |
|------|--------|--------|--------|
| 2026-01-01 | - | Initial GAP analysis created | Complete |
| - | GAP-L01 | Implement LineageAPI class | Pending |
| - | GAP-L02 | Add DLI-9xx error codes | Pending |
| - | GAP-L04 | Update LINEAGE_RELEASE.md test count | Pending |

---

**Last Updated:** 2026-01-01
