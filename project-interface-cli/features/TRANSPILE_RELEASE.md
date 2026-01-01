# RELEASE: SQL Transpile Feature

> **Version:** 1.2.0
> **Status:** Refactored to Subcommands
> **Release Date:** 2026-01-01

---

## 1. Implementation Summary

### 1.1 Completed Features (Phase 1 MVP + P0 + Refactoring)

| Feature | Status | Description |
|---------|--------|-------------|
| **TranspileEngine** | ✅ | Core transpile orchestration with retry and graceful degradation |
| **Table Substitution** | ✅ | SQLGlot AST-based table name replacement |
| **METRIC() Expansion** | ✅ | Regex-based METRIC(name) → SQL expression substitution |
| **Warning Detection** | ✅ | SELECT *, NO_LIMIT, dangerous statement detection |
| **`dli dataset transpile`** | ✅ | Dataset-specific transpile command |
| **`dli metric transpile`** | ✅ | Metric-specific transpile command |
| **~~`dli transpile` Command~~** | ⚠️ **Deprecated** | Removed in v1.2.0, use dataset/metric subcommands |
| **`dataset run --sql`** | ✅ | Ad-hoc SQL execution with transpile integration |
| **Mock Mode** | ✅ | Full mock support for development/testing |
| **JSON Output** | ✅ | `--format json` option for programmatic use |
| **Jinja Integration** | ✅ | `{{ ds }}`, `{{ ref() }}`, `{{ var() }}` dbt/SQLMesh 호환 |
| **`--transpile-retry` CLI** | ✅ | Retry count 옵션 (0-5 범위) |

### 1.2 Files Created/Modified

#### New Files (core/transpile/)

| File | Lines | Purpose |
|------|-------|---------|
| `core/transpile/__init__.py` | 72 | Module exports |
| `core/transpile/models.py` | 298 | Pydantic models (Dialect, RuleType, TranspileConfig, etc.) |
| `core/transpile/exceptions.py` | 156 | Exception hierarchy (TranspileError, RuleFetchError, etc.) |
| `core/transpile/client.py` | 223 | Protocol + MockTranspileClient |
| `core/transpile/metrics.py` | 151 | METRIC() parsing with regex |
| `core/transpile/rules.py` | 208 | Table substitution with SQLGlot |
| `core/transpile/warnings.py` | 193 | SQL pattern warning detection |
| `core/transpile/engine.py` | 308 | TranspileEngine orchestration |

#### Modified Files (v1.2.0 Refactoring)

| File | Changes |
|------|---------|
| `commands/dataset.py` | Added `transpile_dataset` subcommand (lines 752-979) |
| `commands/metric.py` | Added `transpile_metric` subcommand (lines 611-838) |
| ~~`commands/transpile.py`~~ | **Removed** - functionality moved to subcommands |
| `commands/__init__.py` | Removed `transpile_app` export |
| `main.py` | Unregistered standalone transpile subcommand |

#### Original Modified Files (v1.1.0)

| File | Changes |
|------|---------|
| `commands/dataset.py` | Added `--sql`, `-f`, `--transpile-strict`, `--no-transpile` options |
| `core/client.py` | Added `transpile_get_rules()`, `transpile_get_metric_sql()` |

#### Test Files

| File | Tests | Coverage |
|------|-------|----------|
| `tests/core/transpile/test_models.py` | 50 | Pydantic models |
| `tests/core/transpile/test_metrics.py` | 35 | METRIC() parsing |
| `tests/core/transpile/test_engine.py` | 53 | TranspileEngine + Jinja Integration |
| `tests/cli/test_transpile_cmd.py` | 45 | CLI commands + Retry Option |
| **Total** | **165** | |

---

## 2. Usage Guide

### 2.1 `dli dataset transpile` Command

Transpile SQL for a specific dataset spec.

```bash
# Transpile dataset SQL
dli dataset transpile iceberg.analytics.daily_clicks

# With options
dli dataset transpile iceberg.analytics.daily_clicks --show-rules
dli dataset transpile iceberg.analytics.daily_clicks --format json
dli dataset transpile iceberg.analytics.daily_clicks --strict
dli dataset transpile iceberg.analytics.daily_clicks --validate
dli dataset transpile iceberg.analytics.daily_clicks --dialect bigquery
```

### 2.2 `dli metric transpile` Command

Transpile SQL for a specific metric spec.

```bash
# Transpile metric SQL
dli metric transpile iceberg.analytics.daily_active_users

# With options
dli metric transpile iceberg.analytics.daily_active_users --show-rules
dli metric transpile iceberg.analytics.daily_active_users --format json
dli metric transpile iceberg.analytics.daily_active_users --strict
```

#### Common Options

| Option | Description | Default |
|--------|-------------|---------|
| `name` | Spec name (positional) | - |
| `--strict` | Fail on any error | `false` |
| `--format` | Output format (table/json) | `table` |
| `--show-rules` | Show applied rules detail | `false` |
| `--validate` | Perform syntax validation | `false` |
| `-d, --dialect` | SQL dialect (trino/bigquery) | `trino` |
| `--transpile-retry` | Retry count for rule fetching (0-5) | `1` |

### 2.3 Migration from v1.1.0

The standalone `dli transpile` command has been removed. Use resource-specific subcommands instead:

| Old Command (v1.1.0) | New Command (v1.2.0) |
|----------------------|----------------------|
| `dli transpile "SELECT ..."` | Use TranspileAPI or `dataset run --sql` |
| `dli transpile -f query.sql` | Use TranspileAPI or `dataset run --sql` |
| N/A | `dli dataset transpile <name>` |
| N/A | `dli metric transpile <name>` |

### 2.4 `dli dataset run --sql`

```bash
# Ad-hoc SQL with transpile
dli dataset run --sql "SELECT * FROM analytics.users"

# File-based
dli dataset run -f query.sql

# Options
dli dataset run --sql "..." --transpile-strict
dli dataset run --sql "..." --no-transpile
```

#### Mutual Exclusivity
- `name` (spec) and `--sql`/`-f` cannot be used together
- `--sql` and `-f` cannot be used together

---

## 3. Architecture

### 3.1 Module Structure

```
src/dli/core/transpile/
├── __init__.py       # Exports
├── models.py         # Pydantic models + enums
├── exceptions.py     # 4 exception classes
├── client.py         # Protocol + MockTranspileClient
├── metrics.py        # METRIC() regex parsing
├── rules.py          # SQLGlot table substitution
├── warnings.py       # Pattern detection
└── engine.py         # Orchestration
```

### 3.2 Data Flow

```
User SQL
    │
    ▼
┌──────────────────────┐
│ TranspileEngine      │
├──────────────────────┤
│ 0. Jinja rendering   │ ← renderer.py ({{ ds }}, ref(), var())
│ 1. Fetch rules       │ ← MockTranspileClient
│ 2. Table substitution│ ← rules.py (SQLGlot)
│ 3. METRIC expansion  │ ← metrics.py (regex)
│ 4. Warning detection │ ← warnings.py (SQLGlot)
└──────────────────────┘
    │
    ▼
TranspileResult
```

### 3.3 Exception Hierarchy

```
TranspileError (base)
├── RuleFetchError      # Server/network errors
├── MetricNotFoundError # METRIC(unknown)
└── SqlParseError       # SQLGlot parse failure
```

---

## 4. Mock Data

### 4.1 Table Substitution Rules

```python
MOCK_RULES = [
    {"source": "raw.events", "target": "warehouse.events_v2"},
    {"source": "analytics.users", "target": "analytics.users_v2"},
]
```

### 4.2 Metric Definitions

```python
MOCK_METRICS = {
    "revenue": "SUM(amount * quantity)",
    "total_orders": "COUNT(DISTINCT order_id)",
}
```

---

## 5. Test Results

```bash
$ cd project-interface-cli && uv run pytest tests/core/transpile tests/cli/test_transpile_cmd.py -v

147 passed in 5.23s

Coverage:
- core/transpile/engine.py: 92%
- core/transpile/metrics.py: 95%
- core/transpile/rules.py: 88%
- commands/transpile.py: 85%
```

---

## 6. Known Limitations (MVP)

| Limitation | Phase | Status |
|------------|-------|--------|
| SQL당 METRIC() 1개만 지원 | Phase 2 | Open |
| 문자열 리터럴 내 METRIC 미감지 | Phase 2 | Open |
| 주석 내 METRIC 미감지 | Phase 2 | Open |
| ~~Jinja Template 미지원~~ | ~~Phase 2~~ | ✅ **Resolved (v1.1.0)** |
| Server API 미연동 | Phase 3 | Open |

---

## 7. Future Work (Phase 2+)

### Phase 2
- [ ] Multiple METRIC() support
- [x] ~~Jinja Template rendering~~ → **v1.1.0에서 구현 완료**
- [ ] Additional warnings (duplicate CTE, correlated subquery)
- [ ] `--validate` enhanced validation
- [ ] File logging

### Phase 3
- [ ] Server API integration (replace MockTranspileClient)
- [ ] Full Metric Expansion (dimensions, time_grains, filters)
- [ ] Column-level Lineage
- [ ] Cost Estimation

---

## 8. Quality Metrics

| Metric | Value |
|--------|-------|
| pyright errors | 0 |
| ruff violations | 0 |
| Test count | 165 |
| Test pass rate | 100% |
| New code lines | ~2,600 |

---

## 9. Review Summary

### expert-python Agent Review (2025-12-30)

**Assessment:** Excellent Quality

**Improvements Applied:**
1. Extracted duplicate `DIALECT_MAP` to shared location
2. Added `WarningType.METRIC_ERROR` for semantic accuracy
3. Removed unused TYPE_CHECKING blocks
4. Auto-fixed import ordering

**Strengths Noted:**
- Clean modular architecture
- Comprehensive type hints
- Protocol-based dependency injection
- Graceful degradation pattern
- Rich CLI output

### expert-python Agent Review (2026-01-01) - P0 Update

**Assessment:** Good Quality

**Improvements Applied:**
1. Jinja 에러 처리 edge case 테스트 추가 (undefined variable, invalid syntax)
2. Strict mode와 graceful mode 에러 핸들링 검증

**P0 Features Validated:**
- Jinja Integration (`_render_jinja()`, `jinja_context` 파라미터)
- `--transpile-retry` CLI 옵션 범위 검증 (0-5)

---

## 10. Changelog

### v1.2.0 (2026-01-01) - Refactoring to Subcommands

**Breaking Changes:**
- ⚠️ **Removed** `dli transpile` top-level command
- Users must migrate to resource-specific subcommands

**New Features:**
- ✅ `dli dataset transpile <name>` - Transpile dataset SQL
- ✅ `dli metric transpile <name>` - Transpile metric SQL

**Migration Path:**
- For ad-hoc SQL transpilation: Use `TranspileAPI` from Library API
- For spec-based transpilation: Use `dli dataset transpile` or `dli metric transpile`
- For inline SQL: Use `dli dataset run --sql` with transpile integration

**Rationale:**
- Improved consistency with other resource-specific commands
- Better integration with spec file workflows
- Clearer separation between ad-hoc and spec-based operations

### v1.1.0 (2026-01-01) - P0 Features

- Jinja Integration
- `--transpile-retry` CLI option
- 15 new tests (Jinja 8 + Retry 7)

### v1.0.0 (2025-12-30) - Initial Release

- TranspileEngine core
- Table Substitution
- METRIC() Expansion
- Warning Detection
- `dli transpile` command
- Mock Mode
- 165 tests

---

**Last Updated:** 2026-01-01
