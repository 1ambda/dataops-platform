# project-interface-cli Implementation Status

> Last Updated: 2025-12-30 | Version: 0.2.1

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

## API DI Support

| API | executor param | _is_mock_mode |
|-----|----------------|---------------|
| DatasetAPI | ✅ | ✅ |
| MetricAPI | ✅ | ✅ |
| TranspileAPI | - | ✅ |
| CatalogAPI | - | ✅ |
| ConfigAPI | - | ✅ |

## Phase 2 Pending

- ServerExecutor 완전 구현
- CLI --local/--server 플래그
- --output 결과 저장

## 상세 문서

- `features/STATUS.md` - 전체 상태
- `features/RELEASE_EXECUTION.md` - 구현 상세
- `docs/PATTERNS.md` - 개발 패턴
