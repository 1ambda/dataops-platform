# Test Coverage Gap Analysis - project-interface-cli

**Date:** 2025-12-28
**Overall Coverage:** 86% (1368 statements, 193 missing)
**Total Tests:** 328 passing

---

## Executive Summary

The project has **good overall coverage (86%)**, but there are **critical gaps** in:
1. **BigQuery adapter** (0% coverage - 58 untested lines)
2. **CLI command edge cases** (list command only 37% coverage)
3. **Error handling paths** in core modules
4. **Phase execution edge cases** in executor
5. **Complex validation scenarios** in validator

---

## 1. CRITICAL GAPS (High Priority)

### 1.1 BigQuery Adapter (0% Coverage) - UNTESTED
**File:** `src/dli/adapters/bigquery.py`
**Coverage:** 0/58 lines (0%)
**Risk Level:** HIGH

**Missing Tests:**
- [ ] BigQueryExecutor initialization with/without google-cloud-bigquery
- [ ] ImportError handling when library not installed
- [ ] SQL execution with successful results
- [ ] Timeout handling (FuturesTimeoutError)
- [ ] BigQuery API error handling
- [ ] Dry run cost estimation
- [ ] Connection testing
- [ ] Table schema retrieval
- [ ] Result set conversion (rows to dictionaries)
- [ ] Column extraction from result schema

**Recommended Test File:** `tests/adapters/test_bigquery.py`

```python
# Suggested test structure
class TestBigQueryExecutor:
    """Tests for BigQueryExecutor."""

    def test_import_error_when_library_missing(self, monkeypatch):
        """Test ImportError when google-cloud-bigquery not installed."""
        # Mock BIGQUERY_AVAILABLE = False
        # Assert ImportError with proper message

    def test_execute_success(self, mock_bigquery_client):
        """Test successful query execution."""
        # Mock client.query() and job.result()
        # Verify ExecutionResult with correct data

    def test_execute_timeout(self, mock_bigquery_client):
        """Test query timeout handling."""
        # Mock FuturesTimeoutError
        # Verify ExecutionResult with timeout error

    def test_execute_api_error(self, mock_bigquery_client):
        """Test BigQuery API error handling."""
        # Mock google.api_core.exceptions
        # Verify ExecutionResult with error message

    def test_dry_run_valid_query(self, mock_bigquery_client):
        """Test dry run with valid query."""
        # Mock job.total_bytes_processed
        # Verify cost estimation ($5 per TB)

    def test_dry_run_invalid_query(self, mock_bigquery_client):
        """Test dry run with invalid query."""
        # Mock BadRequest exception
        # Verify error in result dict

    def test_test_connection_success(self, mock_bigquery_client):
        """Test successful connection test."""
        # Mock SELECT 1 query success

    def test_test_connection_failure(self, mock_bigquery_client):
        """Test connection failure."""
        # Mock query exception

    def test_get_table_schema(self, mock_bigquery_client):
        """Test table schema retrieval."""
        # Mock table.schema fields
        # Verify returned column definitions

    def test_get_table_schema_not_found(self, mock_bigquery_client):
        """Test table not found error."""
        # Mock NotFound exception
        # Verify empty list returned
```

---

### 1.2 List Command (37% Coverage)
**File:** `src/dli/commands/list_cmd.py`
**Coverage:** 19/51 lines (37%)
**Missing Lines:** 67-68, 76-127

**Untested Scenarios:**
- [ ] No dli.yaml found in current or parent directories (lines 66-74)
- [ ] Discovery errors (exception handling, lines 76-82)
- [ ] Type filtering: dataset filter (lines 87-88)
- [ ] Type filtering: metric filter (lines 89-90)
- [ ] Invalid spec type error (lines 91-93)
- [ ] No specs found scenario (lines 95-97)
- [ ] JSON output format (lines 99-109)
- [ ] Table output format (lines 110-127)

**Recommended Test File:** `tests/cli/test_list_cmd.py`

```python
class TestListCommand:
    """Tests for list command."""

    def test_list_no_dli_yaml(self, tmp_path):
        """Test list command when no dli.yaml exists."""
        # Create temp directory without dli.yaml
        # Verify yellow warning message and exit 0

    def test_list_discovery_error(self, tmp_path, monkeypatch):
        """Test list command with discovery errors."""
        # Mock discovery to raise exception
        # Verify error message and exit 1

    def test_list_filter_by_dataset_type(self, sample_project_path):
        """Test filtering by dataset type."""
        # Run with --type dataset
        # Verify only datasets shown

    def test_list_filter_by_metric_type(self, sample_project_path):
        """Test filtering by metric type."""
        # Run with --type metric
        # Verify only metrics shown

    def test_list_invalid_type_filter(self, sample_project_path):
        """Test invalid type filter."""
        # Run with --type invalid
        # Verify error and exit 1

    def test_list_no_specs_found(self, tmp_path):
        """Test when no specs exist."""
        # Create dli.yaml but no specs
        # Verify yellow warning and exit 0

    def test_list_json_format(self, sample_project_path):
        """Test JSON output format."""
        # Run with --format json
        # Verify JSON structure with name, type, owner, team

    def test_list_table_format(self, sample_project_path):
        """Test table output format (default)."""
        # Run with --format table or default
        # Verify Rich table with columns

    def test_list_custom_path(self, sample_project_path):
        """Test list with custom path."""
        # Run with --path option
        # Verify specs discovered from custom path
```

---

## 2. MODERATE GAPS (Medium Priority)

### 2.1 Executor - Phase Execution (85% Coverage)
**File:** `src/dli/core/executor.py`
**Missing Lines:** 272, 299, 313, 328-334, 368-381

**Untested Edge Cases:**
- [ ] Pre-statements as single string (not list) - line 272
- [ ] Main SQL as list (taking first element) - line 299
- [ ] Post-statements as single string (not list) - line 313
- [ ] Post-statement with continue_on_error=True - lines 328-334
- [ ] execute_phase() with "pre" phase - lines 373-375
- [ ] execute_phase() with "post" phase - lines 376-378
- [ ] execute_phase() with "main" phase - lines 379-381

```python
class TestDatasetExecutorEdgeCases:
    """Edge case tests for DatasetExecutor."""

    def test_pre_statements_as_string(self):
        """Test pre-statements provided as single string."""
        # Create spec with pre_statements
        # Provide rendered_sqls["pre"] as string (not list)
        # Verify it's converted to list

    def test_main_sql_as_list(self):
        """Test main SQL provided as list."""
        # Provide rendered_sqls["main"] as list
        # Verify first element is used

    def test_post_statement_continue_on_error(self):
        """Test post-statement with continue_on_error=True."""
        # Create failing post statement with continue_on_error
        # Verify execution continues to next statement

    def test_execute_phase_pre_only(self):
        """Test execute_phase with 'pre' phase."""
        # Call execute_phase(spec, sqls, "pre")
        # Verify only pre-statements executed

    def test_execute_phase_post_only(self):
        """Test execute_phase with 'post' phase."""
        # Call execute_phase(spec, sqls, "post")
        # Verify only post-statements executed

    def test_execute_phase_main_only(self):
        """Test execute_phase with 'main' phase."""
        # Call execute_phase(spec, sqls, "main")
        # Verify only main query executed
```

---

### 2.2 Validator - Edge Cases (88% Coverage)
**File:** `src/dli/core/validator.py`
**Missing Lines:** 52-53, 58, 143-144, 157, 231, 234-239

**Untested Scenarios:**
- [ ] Parsed statement is None in list - lines 52-53, 57-58
- [ ] ParseError in extract_columns() - lines 143-144
- [ ] Complex expression column extraction (fallback) - line 157
- [ ] MERGE statement query type - line 231
- [ ] CREATE/DROP/ALTER statement types - lines 233-239

```python
class TestSQLValidatorEdgeCases:
    """Edge case tests for SQLValidator."""

    def test_validate_none_statement_in_list(self):
        """Test validation when parsed list contains None."""
        # Provide SQL that parses to [None, valid_stmt]
        # Verify None is skipped

    def test_extract_columns_parse_error(self):
        """Test extract_columns with invalid SQL."""
        # Provide invalid SQL
        # Verify empty list returned

    def test_extract_columns_complex_expression(self):
        """Test extract_columns with complex expressions."""
        # SELECT CASE WHEN ... END, aggregations, etc.
        # Verify fallback to str(col_expr)

    def test_get_query_type_merge(self):
        """Test MERGE statement type detection."""
        # MERGE INTO table USING source ON ...
        # Verify "MERGE" returned

    def test_get_query_type_ddl_statements(self):
        """Test CREATE/DROP/ALTER detection."""
        # CREATE TABLE, DROP TABLE, ALTER TABLE
        # Verify correct types returned

    def test_get_query_type_unknown(self):
        """Test unknown statement type."""
        # Provide unusual SQL statement
        # Verify fallback to __class__.__name__.upper()
```

---

### 2.3 Service - Error Handling (87% Coverage)
**File:** `src/dli/core/service.py`
**Missing Lines:** 127-128, 158-159, 242, 260, 278, 316, 330-338, 364, 414

**Untested Error Paths:**
- [ ] OSError when loading pre-statement SQL - lines 127-128
- [ ] ValueError when loading post-statement SQL - lines 158-159
- [ ] No dataset found in render_sql() - line 242
- [ ] No dataset found in execute() - line 260
- [ ] No dataset found in execute_phase() - line 278
- [ ] No dataset found in get_info() - line 316
- [ ] No dataset or missing main in get_columns() - lines 330-338
- [ ] No dataset in format_sql() - line 364
- [ ] Executor is None in test_connection() - line 414

```python
class TestDatasetServiceErrorHandling:
    """Error handling tests for DatasetService."""

    def test_validate_pre_statement_os_error(self, monkeypatch):
        """Test validation when pre-statement file not found."""
        # Mock stmt.get_sql() to raise OSError
        # Verify ValidationResult with error

    def test_validate_post_statement_value_error(self, monkeypatch):
        """Test validation when post-statement has ValueError."""
        # Mock stmt.get_sql() to raise ValueError
        # Verify ValidationResult with error

    def test_render_sql_dataset_not_found(self):
        """Test render_sql with non-existent dataset."""
        # Call with invalid dataset name
        # Verify None returned

    def test_execute_dataset_not_found(self):
        """Test execute with non-existent dataset."""
        # Call with invalid dataset name
        # Verify None returned

    def test_get_columns_main_sql_as_list(self):
        """Test get_columns when main SQL is list."""
        # Mock render_sql to return main as list
        # Verify first element used

    def test_test_connection_no_executor(self):
        """Test connection test when executor is None."""
        # Create service without executor
        # Verify False returned
```

---

### 2.4 Discovery - Warning Paths (86% Coverage)
**File:** `src/dli/core/discovery.py`
**Missing Lines:** 118, 132-133, 150, 156-157, 180, 187-188, 223, 261-266, 277-280, 306, 346, 387, 431, 438, 463

**Untested Scenarios:**
- [ ] Datasets directory not exists (warning) - line 132
- [ ] Failed to load legacy spec (warning) - lines 156-157
- [ ] Failed to load spec in _discover_specs_in_dir (warning) - lines 187-188
- [ ] Type detection fallback logic - lines 261-266
- [ ] Query type defaults for metric vs dataset - lines 277-280
- [ ] Legacy metric detection (skip logic) - lines 305-306
- [ ] find_spec() returns None - line 346
- [ ] find_dataset() returns None - line 387
- [ ] get_spec_by_path() file not found - line 431
- [ ] load_config() missing datasets_dir - line 438
- [ ] load_project() FileNotFoundError - line 463

```python
class TestSpecDiscoveryEdgeCases:
    """Edge case tests for SpecDiscovery."""

    def test_discover_datasets_dir_not_exists(self, tmp_path):
        """Test when datasets directory doesn't exist."""
        # Create config pointing to non-existent dir
        # Verify warning logged and empty iterator

    def test_discover_datasets_legacy_load_error(self, tmp_path):
        """Test legacy spec load error handling."""
        # Create invalid legacy spec.*.yaml
        # Verify warning logged and spec skipped

    def test_type_detection_fallback(self):
        """Test spec type fallback when not in filename."""
        # Create spec without type prefix
        # Verify fallback type used

    def test_set_type_defaults_metric(self):
        """Test defaults for metric type."""
        # Verify SELECT query_type for metrics

    def test_legacy_metric_skip_logic(self):
        """Test legacy file identified as metric is skipped."""
        # Legacy spec with query_type=SELECT and metrics field
        # Verify None returned (handled by discover_metrics)
```

---

## 3. MINOR GAPS (Low Priority)

### 3.1 Render Command (80% Coverage)
**File:** `src/dli/commands/render.py`
**Missing Lines:** 65-67, 95-97, 103-105

**Untested:**
- [ ] No dataset found error - lines 65-67
- [ ] Rendering error handling - lines 95-97
- [ ] SQL output format - lines 103-105

### 3.2 Info Command (92% Coverage)
**File:** `src/dli/commands/info.py`
**Missing Lines:** 41-42

**Untested:**
- [ ] No dataset found in get_info() - lines 41-42

### 3.3 Validate Command (91% Coverage)
**File:** `src/dli/commands/validate.py`
**Missing Lines:** 57-59

**Untested:**
- [ ] File read error handling - lines 57-59

---

## 4. NOT APPLICABLE (Can Skip)

### 4.1 Entry Points
- `src/dli/__main__.py` (0% coverage) - Entry point, tested via CLI
- `src/dli/adapters/__init__.py` (0% coverage) - Just imports

### 4.2 High Coverage Modules (>95%)
- `src/dli/core/config.py` - 97%
- `src/dli/core/registry.py` - 96%
- `src/dli/core/models/spec.py` - 99%
- `src/dli/core/templates.py` - 99%
- `src/dli/core/renderer.py` - 92%
- All 100% coverage modules (base.py, dataset.py, sql_filters.py, etc.)

---

## 5. RECOMMENDED TESTING STRATEGY

### Phase 1: Critical Gaps (Week 1)
1. ✅ Create `tests/adapters/test_bigquery.py` (58 lines to cover)
2. ✅ Create `tests/cli/test_list_cmd.py` (cover lines 67-127)
3. ✅ Add edge case tests to `tests/core/test_executor.py`

### Phase 2: Error Handling (Week 2)
4. ✅ Enhance `tests/core/test_service.py` with error scenarios
5. ✅ Enhance `tests/core/test_validator.py` with edge cases
6. ✅ Enhance `tests/core/test_discovery.py` with warning paths

### Phase 3: CLI Coverage (Week 3)
7. ✅ Add error scenarios to `tests/cli/test_main.py`
8. ✅ Add render command tests
9. ✅ Add info command tests

---

## 6. TESTING BEST PRACTICES TO FOLLOW

### 6.1 Test Structure
```python
# Use descriptive class names
class TestBigQueryExecutor:
    """Tests for BigQueryExecutor class."""

    # Use fixture-based setup
    @pytest.fixture
    def executor(self):
        return BigQueryExecutor(project="test-project")

    # Test one behavior per test
    def test_execute_returns_success_result(self, executor):
        """Test execute returns successful ExecutionResult."""
        # Arrange
        # Act
        # Assert
```

### 6.2 Mock External Dependencies
```python
# Use monkeypatch for imports
def test_bigquery_not_available(monkeypatch):
    monkeypatch.setattr("dli.adapters.bigquery.BIGQUERY_AVAILABLE", False)
    with pytest.raises(ImportError):
        BigQueryExecutor(project="test")

# Use pytest-mock for complex mocking
def test_execute_with_mock_client(mocker):
    mock_client = mocker.MagicMock()
    mock_job = mocker.MagicMock()
    mock_client.query.return_value = mock_job
    # ...
```

### 6.3 Test Error Paths Explicitly
```python
def test_execute_timeout_error(self):
    """Test timeout error is handled correctly."""
    # Explicitly test timeout scenario

def test_execute_api_error(self):
    """Test API error is handled correctly."""
    # Explicitly test API error scenario
```

### 6.4 Use Parametrize for Multiple Cases
```python
@pytest.mark.parametrize("query_type,expected", [
    ("SELECT", "SELECT"),
    ("INSERT", "INSERT"),
    ("MERGE", "MERGE"),
])
def test_get_query_type(query_type, expected):
    """Test query type detection."""
    # ...
```

---

## 7. COVERAGE GOALS

| Module | Current | Target | Priority |
|--------|---------|--------|----------|
| **adapters/bigquery.py** | 0% | 90%+ | CRITICAL |
| **commands/list_cmd.py** | 37% | 85%+ | HIGH |
| **core/executor.py** | 85% | 95%+ | MEDIUM |
| **core/validator.py** | 88% | 95%+ | MEDIUM |
| **core/service.py** | 87% | 95%+ | MEDIUM |
| **core/discovery.py** | 86% | 92%+ | MEDIUM |
| **Overall Project** | 86% | 92%+ | - |

---

## 8. NEXT STEPS

1. **Create missing test files:**
   - `tests/adapters/__init__.py`
   - `tests/adapters/test_bigquery.py`
   - `tests/cli/test_list_cmd.py`

2. **Run coverage to establish baseline:**
   ```bash
   uv run pytest tests/ --cov=dli --cov-report=html
   open htmlcov/index.html
   ```

3. **Implement Phase 1 tests first** (BigQuery + list command)

4. **Track progress:**
   ```bash
   # After each test file
   uv run pytest tests/ --cov=dli --cov-report=term-missing
   ```

5. **Set up CI coverage enforcement:**
   - Add coverage threshold to `pytest.ini`
   - Fail CI if coverage drops below 85%

---

## 9. SPECIFIC TEST FILES TO CREATE

### Priority 1: Critical
```
tests/adapters/test_bigquery.py        (NEW - ~400 lines)
tests/cli/test_list_cmd.py             (NEW - ~300 lines)
```

### Priority 2: Enhancements
```
tests/core/test_executor.py            (ADD ~150 lines)
tests/core/test_service.py             (ADD ~200 lines)
tests/core/test_validator.py           (ADD ~150 lines)
```

### Priority 3: Edge Cases
```
tests/core/test_discovery.py           (ADD ~100 lines)
tests/cli/test_main.py                 (ADD ~100 lines)
```

**Total Estimated New/Modified Lines:** ~1,400 lines of test code
**Estimated Effort:** 2-3 weeks (1 developer)
**Expected Coverage Increase:** 86% → 92%+

---

## 10. EXAMPLE: COMPLETE TEST FOR BIGQUERY

Create `/Users/kun/github/1ambda/dataops-platform/project-interface-cli/tests/adapters/test_bigquery.py`:

```python
"""Tests for BigQuery executor.

These tests use mocking to avoid actual BigQuery API calls.
Install pytest-mock: uv add --dev pytest-mock
"""

from concurrent.futures import TimeoutError as FuturesTimeoutError
from unittest.mock import MagicMock, Mock, patch

import pytest

from dli.core.models import ExecutionResult


class TestBigQueryExecutorImport:
    """Tests for BigQuery import handling."""

    def test_import_error_when_not_installed(self, monkeypatch):
        """Test ImportError when google-cloud-bigquery not installed."""
        # Mock BIGQUERY_AVAILABLE as False
        import dli.adapters.bigquery as bq_module
        monkeypatch.setattr(bq_module, "BIGQUERY_AVAILABLE", False)

        from dli.adapters.bigquery import BigQueryExecutor

        with pytest.raises(ImportError, match="google-cloud-bigquery is not installed"):
            BigQueryExecutor(project="test-project")

    def test_successful_init_when_installed(self, mock_bigquery_client):
        """Test successful initialization when library is available."""
        from dli.adapters.bigquery import BigQueryExecutor

        executor = BigQueryExecutor(project="test-project", location="US")

        assert executor.project == "test-project"
        assert executor.location == "US"
        assert executor.client is not None


class TestBigQueryExecutorExecute:
    """Tests for execute() method."""

    def test_execute_success_with_results(self, mock_bigquery_executor):
        """Test successful query execution with results."""
        executor, mock_client = mock_bigquery_executor

        # Mock successful query
        mock_row_1 = {"id": 1, "name": "Alice"}
        mock_row_2 = {"id": 2, "name": "Bob"}
        mock_client.query.return_value.result.return_value = [
            Mock(items=Mock(return_value=mock_row_1.items())),
            Mock(items=Mock(return_value=mock_row_2.items())),
        ]

        # Mock schema
        mock_field_1 = Mock(name="id")
        mock_field_2 = Mock(name="name")
        mock_client.query.return_value.result.return_value.schema = [
            mock_field_1, mock_field_2
        ]

        result = executor.execute("SELECT id, name FROM users")

        assert result.success is True
        assert result.row_count == 2
        assert result.columns == ["id", "name"]
        assert len(result.data) == 2
        assert result.execution_time_ms >= 0

    def test_execute_timeout_error(self, mock_bigquery_executor):
        """Test timeout error handling."""
        executor, mock_client = mock_bigquery_executor

        # Mock timeout
        mock_client.query.return_value.result.side_effect = FuturesTimeoutError()

        result = executor.execute("SELECT * FROM big_table", timeout=10)

        assert result.success is False
        assert "timed out after 10 seconds" in result.error_message

    def test_execute_api_error(self, mock_bigquery_executor):
        """Test BigQuery API error handling."""
        executor, mock_client = mock_bigquery_executor

        # Mock API error
        mock_client.query.side_effect = Exception("Invalid table reference")

        result = executor.execute("SELECT * FROM nonexistent_table")

        assert result.success is False
        assert "Invalid table reference" in result.error_message


class TestBigQueryExecutorDryRun:
    """Tests for dry_run() method."""

    def test_dry_run_valid_query(self, mock_bigquery_executor):
        """Test dry run with valid query."""
        executor, mock_client = mock_bigquery_executor

        # Mock dry run job
        mock_job = Mock()
        mock_job.total_bytes_processed = 1_000_000_000  # 1 GB
        mock_client.query.return_value = mock_job

        result = executor.dry_run("SELECT * FROM users")

        assert result["valid"] is True
        assert result["bytes_processed"] == 1_000_000_000
        assert result["bytes_processed_gb"] == 1.0
        # $5 per TB = $0.005 per GB
        assert abs(result["estimated_cost_usd"] - 0.005) < 0.0001

    def test_dry_run_invalid_query(self, mock_bigquery_executor):
        """Test dry run with invalid query."""
        executor, mock_client = mock_bigquery_executor

        # Mock API error
        mock_client.query.side_effect = Exception("Syntax error")

        result = executor.dry_run("SELEC * FROM users")

        assert result["valid"] is False
        assert "Syntax error" in result["error"]


# Fixtures
@pytest.fixture
def mock_bigquery_client():
    """Mock BigQuery client."""
    with patch("dli.adapters.bigquery.BIGQUERY_AVAILABLE", True):
        with patch("dli.adapters.bigquery._bigquery_module") as mock_bq:
            mock_client = MagicMock()
            mock_bq.Client.return_value = mock_client
            yield mock_client


@pytest.fixture
def mock_bigquery_executor(mock_bigquery_client):
    """Create BigQueryExecutor with mocked client."""
    from dli.adapters.bigquery import BigQueryExecutor

    executor = BigQueryExecutor(project="test-project")
    return executor, mock_bigquery_client
```

---

**END OF ANALYSIS**
