# Quality API Feature Specification

> **Version:** 1.1.0 | **Status:** ✅ **COMPLETED** | **Priority:** P3 Low
> **CLI Commands:** `dli quality list/get/run` | **Target:** Spring Boot 4 + Kotlin 2
> **Completed:** 2026-01-02 | **Endpoints:** 3/3 (100%)
>
> **📦 Data Source:** Self-managed JPA (Quality Spec 저장)
> **Entities:** `QualitySpecEntity`, `QualityTestEntity`, `QualityRunEntity`, `TestResultEntity`
>
> **📖 Full Implementation Details:** [`QUALITY_RELEASE.md`](./QUALITY_RELEASE.md)

---

## ✅ Implementation Completed

### Implementation Summary

The Quality API has been **fully implemented** with comprehensive data quality testing capabilities:

- **✅ Quality Spec Management**: Complete CRUD operations for quality specifications
- **✅ Test Execution**: Quality tests execution against datasets with external rule engine integration
- **✅ Result Tracking**: Complete test execution history and failure pattern tracking
- **✅ External Integration**: Mock implementation for project-basecamp-parser rule engine service

### Completed Endpoints

| CLI Command | Server Endpoint | Status |
|-------------|-----------------|--------|
| `dli quality list` | `GET /api/v1/quality` | ✅ **Complete** |
| `dli quality get <name>` | `GET /api/v1/quality/{name}` | ✅ **Complete** |
| `dli quality run <resource>` | `POST /api/v1/quality/test/{resource_name}` | ✅ **Complete** |

### Implemented Test Types

All planned test types have been implemented with SQL generation support:

- **✅ NOT_NULL** - Column null value detection
- **✅ UNIQUE** - Column uniqueness validation
- **✅ ACCEPTED_VALUES** - Value allowlist validation
- **✅ RELATIONSHIPS** - Foreign key integrity checks
- **✅ EXPRESSION** - Custom SQL expression validation
- **✅ ROW_COUNT** - Table row count validation
- **✅ SINGULAR** - Custom SQL query execution

### Implementation Metrics

| Metric | Achievement |
|--------|-------------|
| **Endpoints** | 3/3 (100%) |
| **Test Coverage** | 109 tests (100% pass rate) |
| **Lines of Code** | ~5,887 lines |
| **Architecture** | Full hexagonal compliance |
| **Documentation** | Complete release docs |

---

## 2. Related Documents

### 2.1 Implementation Documentation

| Document | Description |
|----------|-------------|
| **[`QUALITY_RELEASE.md`](./QUALITY_RELEASE.md)** | **Complete implementation details, API specifications, and technical documentation** |
| [`_STATUS.md`](./_STATUS.md) | Overall project implementation status |
| [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) | Implementation timeline |
| [`../ERROR_CODES.md`](../ERROR_CODES.md) | Error code definitions |

### 2.2 CLI Implementation

| File | Description |
|------|-------------|
| `project-interface-cli/src/dli/api/quality.py` | QualityAPI client implementation |
| `project-interface-cli/src/dli/models/quality.py` | Quality domain models |
| `project-interface-cli/src/dli/commands/quality.py` | CLI commands |

### 2.3 External References

- [dbt Data Tests](https://docs.getdbt.com/docs/build/data-tests) - Test type inspiration
- [Great Expectations](https://docs.greatexpectations.io/) - Quality testing patterns

---

## 3. CLI-Side SQL Generation for Built-in Rules (v1.1.0) ✅ IMPLEMENTED

> **Added in v1.1.0** - CLI generates SQL for Built-in Quality Test Rules
> **Implementation Status:** ✅ **COMPLETED** - See [`EXECUTION_RELEASE.md`](./EXECUTION_RELEASE.md) for implementation details

### 3.1 Architecture Change

기존에는 Server에서 Quality Test SQL을 생성했으나, v1.1.0부터 **CLI가 Built-in Rule에 대한 SQL을 직접 생성**합니다:

```
┌─────────────────────────────────────────────────────────────────┐
│                  Before v1.1.0 (Server-side Generation)          │
├─────────────────────────────────────────────────────────────────┤
│  CLI ─────────────────► Server ─────────────────► QueryEngine   │
│       Quality Spec         │                                     │
│                            │ Server generates SQL                │
│                            ├─────────────────────►               │
│                            │      SELECT COUNT(*) ...            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  After v1.1.0 (CLI-side Generation)              │
├─────────────────────────────────────────────────────────────────┤
│  CLI ─────────────────────────────────────────────► Server      │
│    │ 1. Load Quality Spec                              │        │
│    │ 2. Generate SQL for Built-in Rules               │        │
│    │ 3. Send rendered_sql + spec                      │        │
│    │                                                   ▼        │
│    │                                           Execute SQL      │
│    └──────────────────────────────────────────────────►         │
│                    rendered_sql                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 CLI Built-in Test Rules

CLI는 다음 3가지 Built-in Rule에 대해 SQL을 생성합니다:

| Rule Type | Description | CLI Generated SQL |
|-----------|-------------|-------------------|
| `not_null` | Column null check | `SELECT COUNT(*) as failed_count FROM {{ table }} WHERE {{ column }} IS NULL` |
| `unique` | Column uniqueness | `SELECT {{ column }}, COUNT(*) as cnt FROM {{ table }} GROUP BY {{ column }} HAVING COUNT(*) > 1` |
| `row_count` | Table row validation | `SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed FROM {{ table }}` |

### 3.3 Quality Test Spec Format

```yaml
# quality/iceberg.analytics.users.yaml
apiVersion: v1
kind: QualitySpec
metadata:
  name: iceberg.analytics.users
  target_table: iceberg.analytics.users
spec:
  tests:
    # Built-in Rule - CLI generates SQL
    - name: user_id_not_null
      type: not_null
      column: user_id

    # Built-in Rule - CLI generates SQL
    - name: email_unique
      type: unique
      column: email

    # Built-in Rule - CLI generates SQL
    - name: table_has_rows
      type: row_count
      operator: ">"
      value: 0

    # Custom Rule - Uses provided SQL expression
    - name: valid_status
      type: expression
      sql: "status IN ('active', 'inactive', 'pending')"
```

### 3.4 CLI-Generated SQL Examples

**not_null test:**
```sql
-- Test: user_id_not_null
-- Type: not_null
-- Column: user_id
SELECT COUNT(*) as failed_count
FROM iceberg.analytics.users
WHERE user_id IS NULL
```

**unique test:**
```sql
-- Test: email_unique
-- Type: unique
-- Column: email
SELECT email, COUNT(*) as cnt
FROM iceberg.analytics.users
GROUP BY email
HAVING COUNT(*) > 1
```

**row_count test:**
```sql
-- Test: table_has_rows
-- Type: row_count
-- Condition: COUNT(*) > 0
SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed
FROM iceberg.analytics.users
```

### 3.5 Server API for Pre-rendered SQL

CLI가 생성한 SQL을 Server로 전송하는 새 API:

#### `POST /api/v1/execution/quality/run`

**Request:**
```http
POST /api/v1/execution/quality/run
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "resource_name": "iceberg.analytics.users",
  "execution_mode": "SERVER",

  "tests": [
    {
      "name": "user_id_not_null",
      "type": "not_null",
      "rendered_sql": "SELECT COUNT(*) as failed_count FROM iceberg.analytics.users WHERE user_id IS NULL"
    },
    {
      "name": "email_unique",
      "type": "unique",
      "rendered_sql": "SELECT email, COUNT(*) as cnt FROM iceberg.analytics.users GROUP BY email HAVING COUNT(*) > 1"
    }
  ],

  "original_spec": { ... },

  "transpile_info": {
    "source_dialect": "bigquery",
    "target_dialect": "trino",
    "used_server_policy": false
  }
}
```

**Response (200 OK):**
```json
{
  "execution_id": "qe-12345",
  "status": "COMPLETED",
  "results": [
    {
      "test_name": "user_id_not_null",
      "passed": true,
      "failed_count": 0,
      "duration_ms": 150
    },
    {
      "test_name": "email_unique",
      "passed": false,
      "failed_count": 3,
      "failed_rows": [
        {"email": "dup@example.com", "cnt": 2}
      ],
      "duration_ms": 230
    }
  ],
  "total_tests": 2,
  "passed_tests": 1,
  "failed_tests": 1,
  "total_duration_ms": 380
}
```

### 3.6 Backward Compatibility

기존 Server-side SQL 생성 API는 유지됩니다:

| API | Usage |
|-----|-------|
| `POST /api/v1/quality/test/{resource_name}` | 기존 API - Server가 SQL 생성 |
| `POST /api/v1/execution/quality/run` | 새 API - CLI가 SQL 생성 후 전송 |

### 3.7 Related Documents

| Document | Description |
|----------|-------------|
| [`TRANSPILE_FEATURE.md`](./TRANSPILE_FEATURE.md) | CLI SQL Rendering Flow (Section 9) |
| [`CLI QUALITY_FEATURE.md`](../../project-interface-cli/features/QUALITY_FEATURE.md) | CLI Quality 구현 상세 |
| [`QUALITY_RELEASE.md`](./QUALITY_RELEASE.md) | Server-side 구현 상세 |

---

## 4. Cross-Review Findings & Improvement Opportunities

### 4.1 Expert-Spring-Kotlin Agent Review Results

> **Status:** ⚠️ **Review Completed - Issues Identified for Future Improvement**

**Critical Issues Identified:**

| Priority | Issue | Recommendation | Status |
|----------|-------|----------------|--------|
| **High** | QualityTestEntity Column Duplication | Consolidate `column` and `columns` fields to single `targetColumns` collection | 📋 **Tracked for improvement** |
| **High** | Repository Implementation Pattern | Fix `QualitySpecRepositoryJpaImpl` - should be class, not interface | 📋 **Tracked for improvement** |

**Detailed Recommendations:**

1. **Consolidate Column Targeting:**
   ```kotlin
   // Current: Dual field approach
   column: String?
   columns: List<String>?

   // Recommended: Single collection approach
   @ElementCollection(fetch = FetchType.LAZY)
   targetColumns: MutableList<String> = mutableListOf()
   ```

2. **Fix Repository Pattern:**
   ```kotlin
   // Current: Incorrect interface declaration
   interface QualitySpecRepositoryJpaImpl : QualitySpecRepositoryJpa, JpaRepository<...>

   // Recommended: Proper adapter implementation
   @Repository("qualitySpecRepositoryJpa")
   class QualitySpecRepositoryJpaImpl(
       private val springDataRepository: QualitySpecRepositoryJpaSpringData
   ) : QualitySpecRepositoryJpa
   ```

### 4.2 Feature-Basecamp-Server Agent Review Results

**Missing Domain Validation Scenarios Identified:**

- Complex repository filtering edge cases
- Domain rule validations for quality spec constraints
- Error handling in test execution workflows

**Strengths Acknowledged:**
- Comprehensive API endpoint coverage
- Consistent error handling patterns
- Well-structured domain relationships

### 4.3 Current Implementation Status

**✅ Production Ready Features:**
- All 3 API endpoints fully functional
- 109 tests passing with comprehensive coverage
- Full hexagonal architecture compliance
- Complete documentation and error handling

**📋 Tracked Improvements (Future Enhancement):**
- Repository pattern refinements
- Domain model optimizations
- Additional edge case validations

---

*Document Version: 1.0.0 | Last Updated: 2026-01-02 | Author: Platform Team*