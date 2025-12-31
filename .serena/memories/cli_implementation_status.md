# project-interface-cli Implementation Status

> Last Updated: 2025-12-31 | Version: 0.4.0

## Quick Check

| Component | Status |
|-----------|--------|
| ExecutionMode | ✅ `models/common.py` |
| ExecutorFactory | ✅ `core/executor.py` |
| DI Support | ✅ DatasetAPI, MetricAPI |
| ServerExecutor | ⏳ Stub only |

## ExecutionMode (v0.2.1)

```python
# 모든 모드 구현 완료
class ExecutionMode(str, Enum):
    LOCAL = "local"    # ✅ BigQueryExecutor 연동
    SERVER = "server"  # ⏳ ServerExecutor stub
    MOCK = "mock"      # ✅ MockExecutor
```

## Error Codes

| Range | Latest | Status |
|-------|--------|--------|
| DLI-4xx | DLI-405 | ✅ 404, 405 추가 |
| DLI-5xx | DLI-504 | ✅ 504 추가 |
| DLI-7xx | DLI-705 | ✅ Catalog errors 추가 |
| DLI-8xx | DLI-803 | ✅ Workflow errors 추가 |

## API DI Support

| API | executor param | _is_mock_mode |
|-----|----------------|---------------|
| DatasetAPI | ✅ | ✅ |
| MetricAPI | ✅ | ✅ |
| TranspileAPI | - | ✅ |
| CatalogAPI | - | ✅ |
| ConfigAPI | - | ✅ |
| QualityAPI | - | ✅ |
| WorkflowAPI | client param | ✅ |

## Catalog (v1.2.0)

| Component | Location | Status |
|-----------|----------|--------|
| CatalogAPI | `api/catalog.py` | ✅ |
| Result Models | `models/common.py:405-485` | ✅ v1.2.0 |
| Mock Data | `core/client.py:_init_mock_catalog_tables` | ✅ |
| Tests | `tests/api/test_catalog_api.py` | ✅ 30 tests |

### Result Models (v1.2.0)

```python
CatalogListResult    # status, tables, total_count, has_more
TableDetailResult    # status, table, error_message
CatalogSearchResult  # status, tables, total_matches, keyword
```

### CatalogAPI Methods

```python
list_tables(identifier) → CatalogListResult
get(table) → TableDetailResult
search(pattern) → CatalogSearchResult
```

## Phase 2 Pending

- ServerExecutor 완전 구현
- CLI --local/--server 플래그
- --output 결과 저장

## 상세 문서

- `features/STATUS.md` - 전체 상태
- `features/RELEASE_EXECUTION.md` - 구현 상세
- `docs/PATTERNS.md` - 개발 패턴
