# GAP Analysis: SQL Transpile Feature

> **Auto-generated:** 2026-01-01
> **Last Updated:** 2026-01-01 (P0 gaps resolved)
> **Analyzed by:** meta-agent, expert-doc-writer, expert-spec, feature-interface-cli
> **Implementation Completeness:** 67% (29/43 items)

---

## 1. Executive Summary

### 1.1 Overall Assessment

| Metric | Value |
|--------|-------|
| FEATURE Spec Items | 43 |
| Implemented Items | 29 |
| **Completion Rate** | **67%** |
| Spec Quality Grade | B+ |
| Documentation Drift | Low |

The Transpile feature MVP is **functionally complete** for basic table substitution, METRIC() expansion, and **Jinja template rendering**. Key gaps remain in Server API integration and multiple METRIC() support.

### 1.2 Key Findings

| Finding | Severity | Root Cause | Status |
|---------|----------|------------|--------|
| Server API not integrated | Critical | Mock-first became Mock-only (Phase 3 deferred indefinitely) | Open |
| ~~Jinja not integrated with TranspileEngine~~ | ~~High~~ | ~~Module isolation~~ | ✅ **Resolved** |
| Dead enum values (DUPLICATE_CTE, CORRELATED_SUBQUERY) | Medium | Spec over-specified, implementation incomplete | Open |
| Exception hierarchy simplified (9→4) | Low | Correct simplification, spec was over-engineered | Accepted |
| ~~`--transpile-retry` CLI option missing~~ | ~~Low~~ | ~~Config exists, not exposed~~ | ✅ **Resolved** |

---

## 2. Detailed Gap Inventory

### 2.1 Critical Gaps (Blocks Production Use)

| ID | Gap | FEATURE Spec | RELEASE Status | Business Impact |
|----|-----|--------------|----------------|-----------------|
| GAP-T01 | Server API Integration | Section 6: Full API spec | Mock only | Cannot deploy to production |
| GAP-T02 | Multiple METRIC() Support | Section 3.4.2: Deferred to Phase 2 | Raises error | Common patterns like `SELECT METRIC(a), METRIC(b)` fail |

### 2.2 High Severity Gaps (Major Functionality Missing)

| ID | Gap | FEATURE Spec | RELEASE Status | Impact |
|----|-----|--------------|----------------|--------|
| ~~GAP-T03~~ | ~~Jinja Template Integration~~ | ~~Section 7~~ | ✅ **Resolved (2026-01-01)** | `_render_jinja()` + `jinja_context` param + 8 tests |
| GAP-T04 | DUPLICATE_CTE Detection | Section 3.2 | Enum exists, logic missing | Dead code |
| GAP-T05 | CORRELATED_SUBQUERY Detection | Section 3.2 | Enum exists, logic missing | Dead code |
| GAP-T06 | Exception Hierarchy Incomplete | Section 8.2: 9 classes | 4 implemented | `NetworkError`, `TimeoutError`, etc. missing |

### 2.3 Medium Severity Gaps

| ID | Gap | Notes |
|----|-----|-------|
| GAP-T07 | String literal METRIC parsing | `"SELECT 'METRIC(x)'"` falsely matches |
| GAP-T08 | Comment METRIC parsing | `-- METRIC(x)` falsely matches |
| GAP-T09 | File logging | `~/.dli/logs/transpile-*.log` not implemented |
| GAP-T10 | YAML fixture files | Mock data hardcoded in Python |

### 2.4 Low Severity Gaps

| ID | Gap | Notes |
|----|-----|-------|
| ~~GAP-T11~~ | ~~`--transpile-retry` CLI option~~ | ✅ **Resolved (2026-01-01)** - `--transpile-retry [0-5]` + 7 tests |
| GAP-T12 | `rules_version` in metadata | Always `None` |

---

## 3. Root Cause Analysis

### 3.1 Agent/Skill System Root Causes

| Root Cause | Affected Gaps | Explanation | Recommended Fix |
|------------|---------------|-------------|-----------------|
| **RC-1: Mock-First Became Mock-Only** | GAP-T01, GAP-T02 | Phase 1 completed, Phase 3 never started | Add phase tracking skill |
| **RC-2: Module Isolation** | GAP-T03 | `core/transpile/` created without integrating existing Jinja code | Add integration-finder skill |
| **RC-3: Dead Code Not Detected** | GAP-T04, GAP-T05 | Enum values defined but detection logic not implemented | Add dead-code-detection skill |
| **RC-4: Spec Over-Engineering** | GAP-T06 | 9 exceptions specified, 4 sufficient | Improve spec-validation skill |

### 3.2 Documentation Root Causes

| Root Cause | Symptoms |
|------------|----------|
| **RC-5: No Post-Implementation Sync** | Test counts differ (163/147/178), FEATURE still "Draft" |
| **RC-6: Missing Cross-References** | FEATURE doesn't reference RELEASE, STATUS lacks Transpile changelog |
| **RC-7: Acceptance Criteria Gap** | No explicit "MVP is done when X" checklist |

### 3.3 Specification Root Causes

| Root Cause | Examples |
|------------|----------|
| **RC-8: Over-Specification** | 200+ lines of test code examples, 9 exception classes |
| **RC-9: Under-Specification** | `list_metric_names()` missing from Protocol |
| **RC-10: Ambiguous Phase Markers** | Some items marked "Phase 2", others unmarked but clearly deferred |

---

## 4. Priority Assessment (Agent Consensus)

### 4.1 Priority Matrix

```
            HIGH BUSINESS VALUE    LOW BUSINESS VALUE
          +----------------------+----------------------+
 EASY TO  | P0: Jinja Integration| P2: Dead warning fix |
 IMPLEMENT| P0: CLI retry option | P2: YAML fixtures    |
          +----------------------+----------------------+
 HARD TO  | P1: Multiple METRIC()| P3: Exception classes|
 IMPLEMENT| P1: Server API       | P3: File logging     |
          +----------------------+----------------------+
```

### 4.2 Recommended Priority Order

| Priority | Item | Effort | Dependencies | Status |
|----------|------|--------|--------------|--------|
| ~~**P0**~~ | ~~GAP-T03: Jinja Integration~~ | ~~1 day~~ | ~~None~~ | ✅ **Done** |
| ~~**P0**~~ | ~~GAP-T11: `--transpile-retry` CLI~~ | ~~1 hour~~ | ~~None~~ | ✅ **Done** |
| **P1** | GAP-T02: Multiple METRIC() | 2 days | None | Pending |
| **P1** | GAP-T01: Server API | 3 days | Server implementation | Pending |
| **P2** | GAP-T04, GAP-T05: Dead warnings | 4 hours | None | Pending |
| **P2** | GAP-T07, GAP-T08: String/comment parsing | 1 day | None | Pending |
| **P3** | GAP-T06: Complete exceptions | 1 day | None | Pending |
| **P3** | GAP-T09: File logging | 1 day | None | Pending |

---

## 5. Documentation Drift Summary

### 5.1 Inconsistencies Found

| Document | Issue | Current | Should Be |
|----------|-------|---------|-----------|
| FEATURE_TRANSPILE.md:5 | Status | "Draft" | "Complete" or "Implemented" |
| FEATURE_TRANSPILE.md:8.2 | Exception count | 9 classes | 4 classes (match implementation) |
| RELEASE_TRANSPILE.md:62 | Test count | "147 passed" | "178 tests" (actual) |
| STATUS.md | Changelog | No Transpile entry | Add v0.2.0 Transpile entry |

### 5.2 Required Documentation Updates

```bash
# Immediate updates needed:
1. FEATURE_TRANSPILE.md:5     → Status: Implemented
2. FEATURE_TRANSPILE.md:8.2   → Reduce to 4 exceptions + "Phase 2+" marker
3. RELEASE_TRANSPILE.md:62    → Update test count to 178
4. STATUS.md changelog        → Add Transpile v1.0.0-MVP entry
5. FEATURE_TRANSPILE.md:1142  → Remove jinja.py from directory structure
```

---

## 6. Agent/Skill Improvement Recommendations

### 6.1 New Skills Recommended

| Skill | Purpose | Addresses |
|-------|---------|-----------|
| `gap-analysis` | Compare FEATURE vs RELEASE automatically | RC-1, RC-5 |
| `integration-finder` | Find existing related modules before new implementation | RC-2 |
| `dead-code-detection` | Flag unused enums, unreachable paths | RC-3 |
| `phase-tracking` | Track multi-phase implementations | RC-1 |

### 6.2 Existing Skill Improvements

| Skill | Improvement |
|-------|-------------|
| `implementation-checklist` | Add "verify enum values have implementation" check |
| `completion-gate` | Add "no dead code" verification |
| `doc-sync` | Auto-detect test count drift, status drift |

### 6.3 Agent Coordination Improvements

| Issue | Current | Recommended |
|-------|---------|-------------|
| No integration check | Modules created in isolation | Add pre-implementation "related module" scan |
| Dead code created | Enums defined without implementation | Add post-implementation dead code scan |
| Documentation drift | Manual sync | Add automated FEATURE↔RELEASE sync check |

---

## 7. Verification Checklist

### 7.1 Gap Resolution Verification

```bash
# After implementing fixes, verify:

# GAP-T03: Jinja Integration
grep -r "from dli.core.renderer" src/dli/core/transpile/
# Should find import in engine.py

# GAP-T04, GAP-T05: Warning detection
grep -n "DUPLICATE_CTE\|CORRELATED_SUBQUERY" src/dli/core/transpile/warnings.py
# Should find detection logic, not just enum

# GAP-T11: CLI retry option
dli transpile --help | grep retry
# Should show --transpile-retry option

# Test count accuracy
uv run pytest tests/core/transpile tests/cli/test_transpile_cmd.py --collect-only | tail -1
# Compare with RELEASE_TRANSPILE.md
```

### 7.2 Documentation Sync Verification

```bash
# Verify documentation consistency
grep "Status:" project-interface-cli/features/FEATURE_TRANSPILE.md
# Should show "Implemented" or "Complete"

grep "passed" project-interface-cli/features/RELEASE_TRANSPILE.md
# Should match actual test count
```

---

## 8. Appendix: Agent Analysis Summaries

### 8.1 meta-agent Summary

- Implementation completeness: 63% (27/43 items)
- Key insight: "Mock-First was Mock-Only" - Phase 1 completed but Phase 3 never started
- Recommended priority: Jinja integration (easy), then Multiple METRIC() (hard but valuable)

### 8.2 expert-doc-writer Summary

- Documentation grade: B+
- Key issue: Test count drift (163/147/178 in different documents)
- Critical fix: Update FEATURE status from "Draft" to "Complete"

### 8.3 expert-spec Summary

- Spec grade: B+
- Key issue: Over-specification (9 exceptions vs 4 needed, 200+ lines test code)
- Recommendation: Use requirement tables instead of code examples

---

**Last Updated:** 2026-01-01
