# FEATURE: Format Command (SQL + YAML 포맷팅)

> **Version:** 1.0.0
> **Status:** ✅ Implemented (v0.9.0)
> **Priority:** P1 (High)
> **Last Updated:** 2026-01-01

---

## Implementation Notes (v0.9.0)

**Phase 1 (MVP) Complete:**
- `dli dataset format` / `dli metric format` commands
- SqlFormatter (sqlfluff), YamlFormatter (ruamel.yaml)
- FormatConfig with hierarchy (.sqlfluff, .dli-format.yaml)
- DLI-15xx error codes (1501-1506)
- 239 tests (35 skipped for optional deps)

**Implementation Details:** See [FORMAT_RELEASE.md](./FORMAT_RELEASE.md)

---

## 1. 개요

### 1.1 목적

Dataset/Metric Spec의 SQL 및 YAML 파일을 일관된 스타일로 자동 포맷팅하여 코드 품질과 가독성을 향상시킵니다.

**핵심 문제:**
- 팀원 간 SQL/YAML 코딩 스타일 불일치로 코드 리뷰 시 불필요한 논쟁 발생
- Jinja 템플릿이 포함된 SQL 포맷팅이 어려움 (일반 SQL 포맷터는 Jinja 문법 미지원)
- YAML 파일의 키 순서가 일관되지 않아 diff 비교 시 혼란 발생
- CI 파이프라인에서 포맷 검증 자동화 필요

**해결 방향:**
- `sqlfluff` 기반 SQL 포맷팅 (Jinja 네이티브 지원)
- `ruamel.yaml` 기반 YAML 포맷팅 (주석 보존, 키 순서 정렬)
- 기존 validate 커맨드와 유사한 리소스 기반 인터페이스
- `--check` 옵션으로 CI 파이프라인 통합

### 1.2 핵심 원칙

1. **리소스 기반**: `dli dataset format <name>` (validate 패턴과 일관성)
2. **Jinja 보존**: SQL 내 Jinja 템플릿(`{{ ref() }}`, `{{ ds }}`) 유지
3. **비파괴적**: 원본 파일 백업 후 수정 (--check로 검증만 가능)
4. **확장 가능**: 프로젝트별 `.sqlfluff` 설정 지원
5. **선택적 Lint**: `--lint` 옵션으로 린트 규칙 적용 (기본 비활성화)

### 1.3 주요 기능

| 기능 | 설명 | MVP | Status |
|------|------|-----|--------|
| SQL 포맷팅 | sqlfluff 기반, Jinja 템플릿 보존 | O | ✅ |
| YAML 포맷팅 | DLI 표준 키 순서, 주석 보존 | O | ✅ |
| 검증 모드 | `--check` (CI용, 변경 없이 검증) | O | ✅ |
| 방언 지원 | BigQuery, Trino, Snowflake 등 | O | ✅ |
| Lint 규칙 | `--lint` 옵션으로 추가 검증 | O | ✅ |
| 자동 수정 | `--lint --fix` 조합 | O | ✅ |

### 1.4 유사 도구 참조

| 도구 | 특징 | 참조 포인트 |
|------|------|------------|
| [SQLMesh format](https://sqlmesh.readthedocs.io/en/stable/reference/cli/#format) | `--check`, `--normalize`, `--transpile` | CI 통합 패턴 |
| [sqlfluff](https://docs.sqlfluff.com/) | Jinja 네이티브, 27개 방언 | 핵심 포맷팅 엔진 |
| [dbt SQL Style Guide](https://docs.getdbt.com/best-practices/how-we-style/2-how-we-style-our-sql) | 업계 표준 스타일 | 규칙 참조 |

---

## 2. 설계 결정

### 2.1 도구 선택: sqlfluff

**선택 근거:**

| 대안 | 장점 | 단점 | 결론 |
|------|------|------|------|
| **sqlfluff** | Jinja 네이티브, 27개 방언, Lint 통합 | 의존성 추가 | **선택** |
| sqlglot (기존) | 이미 사용 중, 가벼움 | Jinja 미지원, Lint 없음 | 보류 |
| sqlfmt | 빠름, 간단 | 방언 제한, Lint 없음 | 탈락 |

**sqlfluff 장점:**
- Jinja2 템플릿 네이티브 지원 (`templater = jinja`)
- BigQuery, Trino, Snowflake 등 27개 SQL 방언 지원
- Lint 규칙 내장 (포맷팅 + 품질 검증 통합)
- `.sqlfluff` 설정 파일로 프로젝트별 커스터마이징

### 2.2 인터페이스 패턴: 리소스 기반

**기존 패턴과의 일관성:**

| Command | Pattern | 예시 |
|---------|---------|------|
| `dli dataset validate` | 리소스 기반 | `dli dataset validate my_dataset` |
| `dli dataset format` | 리소스 기반 (NEW) | `dli dataset format my_dataset` |
| `dli transpile` | SQL 문자열/파일 기반 | `dli transpile --sql "SELECT 1"` |

**결정:** validate 패턴을 따라 리소스 기반으로 구현

### 2.3 핵심 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| 포맷팅 엔진 | sqlfluff | Jinja 네이티브, Lint 통합 |
| CLI 패턴 | 리소스 기반 (`dataset format`, `metric format`) | validate 패턴 일관성 |
| YAML 라이브러리 | ruamel.yaml | 주석 보존, round-trip 지원 |
| 기본 Lint | 비활성화 (`--lint`로 활성화) | 포맷팅과 린트 분리 |
| 방언 기본값 | 프로젝트 설정 또는 BigQuery | 가장 일반적인 사용 사례 |

---

## 3. CLI 인터페이스

### 3.1 커맨드 구조

```
dli dataset format <name>     # Dataset SQL + YAML 포맷팅
dli metric format <name>      # Metric SQL + YAML 포맷팅
```

### 3.2 커맨드별 옵션

#### `dli dataset format`

```bash
dli dataset format NAME [OPTIONS]

Arguments:
  NAME                          포맷팅할 Dataset 이름 (catalog.schema.name)

Options:
  --sql-only                    SQL 파일만 포맷팅 (YAML 제외)
  --yaml-only                   YAML 파일만 포맷팅 (SQL 제외)
  --check                       변경 없이 검증만 수행 (CI용, exit code 1 if changes)
  --lint                        Lint 규칙 적용 (기본: 비활성화)
  --fix                         Lint 오류 자동 수정 (--lint와 함께 사용)
  --dialect, -d TEXT            SQL 방언 지정 (bigquery, trino, snowflake 등)
  --format, -f [table|json]     출력 포맷 (기본: table)
  --path, -p PATH               프로젝트 경로 (기본: 현재 디렉토리)
  --diff                        변경 내역 diff로 출력
```

#### `dli metric format`

```bash
dli metric format NAME [OPTIONS]

Arguments:
  NAME                          포맷팅할 Metric 이름 (catalog.schema.name)

Options:
  # dataset format과 동일
```

### 3.3 사용 예시

```bash
# 기본 포맷팅 (SQL + YAML)
dli dataset format iceberg.analytics.daily_clicks

# SQL만 포맷팅
dli dataset format iceberg.analytics.daily_clicks --sql-only

# CI용 검증 (변경 사항 있으면 exit code 1)
dli dataset format iceberg.analytics.daily_clicks --check

# Lint 규칙 적용
dli dataset format iceberg.analytics.daily_clicks --lint

# Lint 오류 자동 수정
dli dataset format iceberg.analytics.daily_clicks --lint --fix

# Trino 방언으로 포맷팅
dli dataset format iceberg.analytics.daily_clicks --dialect trino

# 변경 내역 diff 출력
dli dataset format iceberg.analytics.daily_clicks --check --diff
```

### 3.4 출력 예시

```
Format Result
=============
Dataset: iceberg.analytics.daily_clicks
Mode: format

Files:
  [check] dataset.iceberg.analytics.daily_clicks.yaml   CHANGED
  [check] sql/daily_clicks.sql                          CHANGED

Summary: 2 files would be changed
Use --diff to see changes, or run without --check to apply.
```

```
Format Result (with --diff)
===========================
Dataset: iceberg.analytics.daily_clicks

--- sql/daily_clicks.sql (before)
+++ sql/daily_clicks.sql (after)
@@ -1,5 +1,5 @@
-SELECT user_id,COUNT(*) as click_count
+SELECT
+    user_id,
+    COUNT(*) AS click_count
 FROM {{ ref('raw_clicks') }}
-WHERE dt = '{{ ds }}'
-GROUP BY user_id
+WHERE
+    dt = '{{ ds }}'
+GROUP BY user_id
```

---

## 4. YAML 포맷팅

### 4.1 DLI 표준 키 순서

YAML 파일의 키는 다음 순서로 정렬됩니다:

```yaml
# 1. 필수 필드 (Required)
name: catalog.schema.table
owner: engineer@example.com
team: "@data-analytics"
type: Dataset
query_type: DML

# 2. 설명 (Description)
description: "Daily user activity aggregation"

# 3. 분류 (Classification)
domains:
  - analytics
  - user-behavior
tags:
  - daily
  - incremental

# 4. SQL 정의 (Query)
query_file: sql/daily_activity.sql
# or query_statement: |
#   SELECT ...

# 5. 파라미터 (Parameters)
parameters:
  - name: execution_date
    type: string
    default: "{{ ds }}"

# 6. 실행 설정 (Execution)
execution:
  timeout: 3600
  retries: 3

# 7. 의존성 (Dependencies)
depends_on:
  - catalog.schema.upstream_table

# 8. 스키마 (Schema)
schema:
  - name: user_id
    type: INT64

# 9. Dataset 전용 (Dataset-specific)
pre_statements:
  - name: delete_partition
    sql: "DELETE FROM t WHERE dt = '{{ ds }}'"
post_statements:
  - name: optimize
    sql: "ALTER TABLE t EXECUTE optimize"

# 10. 버전 (Versions)
versions:
  - version: "1.0.0"
    created_at: "2026-01-01"
```

### 4.2 YAML 포맷팅 규칙

| 규칙 | 설명 | 예시 |
|------|------|------|
| 들여쓰기 | 2-space | `  key: value` |
| 키 순서 | DLI 표준 순서 (위 정의) | name -> owner -> team -> ... |
| 빈 리스트 | 축약 표현 | `tags: []` |
| 빈 맵 | 축약 표현 | `metadata: {}` |
| 멀티라인 문자열 | Literal block scalar | `sql: \|` (pipe character) |
| 주석 | 보존 | 기존 주석 유지 |

### 4.3 ruamel.yaml 사용

```python
from ruamel.yaml import YAML

yaml = YAML()
yaml.indent(mapping=2, sequence=4, offset=2)
yaml.preserve_quotes = True
yaml.width = 120  # 줄 바꿈 폭
```

---

## 5. SQL 포맷팅

### 5.1 sqlfluff 기반 포맷팅

**지원 방언:**

| 방언 | sqlfluff dialect | 비고 |
|------|------------------|------|
| BigQuery | `bigquery` | 기본값 |
| Trino | `trino` | |
| Snowflake | `snowflake` | |
| Spark | `sparksql` | |
| Hive | `hive` | |
| PostgreSQL | `postgres` | |

### 5.2 Jinja 템플릿 처리

**원본 (Jinja 파라미터 포함):**
```sql
SELECT
    user_id,
    COUNT(*) as click_count
FROM {{ ref('raw_clicks') }}
WHERE dt = '{{ ds }}'
  AND {{ filter_condition }}
GROUP BY user_id
```

**포맷팅 후 (Jinja 보존):**
```sql
SELECT
    user_id,
    COUNT(*) AS click_count
FROM {{ ref('raw_clicks') }}
WHERE
    dt = '{{ ds }}'
    AND {{ filter_condition }}
GROUP BY user_id
```

### 5.3 기본 포맷팅 규칙

| 규칙 | 설명 | Before | After |
|------|------|--------|-------|
| 키워드 대문자 | SQL 키워드 대문자 | `select`, `from` | `SELECT`, `FROM` |
| 들여쓰기 | 4-space | 불일치 | 일관된 4-space |
| 줄바꿈 | 절별 줄바꿈 | `WHERE a AND b` | `WHERE\n    a\n    AND b` |
| 별칭 키워드 | AS 명시 | `COUNT(*) cnt` | `COUNT(*) AS cnt` |
| 쉼표 위치 | 줄 끝 (trailing) | `, col` | `col,` |

---

## 6. Lint 규칙

### 6.1 기본 비활성화

Lint 규칙은 `--lint` 옵션으로 활성화됩니다. 기본 포맷팅과 분리하여 점진적 도입을 지원합니다.

### 6.2 Lint 규칙 목록

| 코드 | 규칙 | 설명 | 심각도 |
|------|------|------|--------|
| L001 | trailing-whitespace | 불필요한 공백 | Warning |
| L003 | indentation | 들여쓰기 일관성 | Error |
| L010 | inconsistent-capitalisation-keywords | 키워드 대소문자 | Warning |
| L014 | inconsistent-capitalisation-identifiers | 식별자 일관성 | Warning |
| L031 | alias-table-required | 테이블 별칭 필수 | Warning |
| L044 | query-complexity | 쿼리 복잡도 | Info |
| L046 | jinja-spacing | Jinja 블록 공백 | Warning |

### 6.3 출력 예시 (--lint)

```
Lint Report
===========
Dataset: iceberg.analytics.daily_clicks

Violations:
  sql/daily_clicks.sql:5:1   L010  Keywords should be upper case.
  sql/daily_clicks.sql:12:4  L031  Avoid aliases in from clauses.
  sql/daily_clicks.sql:18:1  L044  Query produces more than 10 columns.

Summary: 3 violations (2 warning, 1 info)
```

---

## 7. 설정 파일

### 7.1 프로젝트 설정 (`.sqlfluff`)

프로젝트 루트에 `.sqlfluff` 파일을 생성하여 포맷팅 규칙을 커스터마이징합니다:

```ini
[sqlfluff]
dialect = bigquery
templater = jinja
max_line_length = 120
exclude_rules = L031,L044

[sqlfluff:templater:jinja]
apply_dbt_builtins = True

[sqlfluff:indentation]
indent_unit = space
tab_space_size = 4

[sqlfluff:rules:capitalisation.keywords]
capitalisation_policy = upper

[sqlfluff:rules:capitalisation.identifiers]
capitalisation_policy = lower

[sqlfluff:rules:layout.trailing_comments]
ignore_comment_lines = True
```

### 7.2 DLI 포맷 설정 (`.dli-format.yaml`)

YAML 포맷팅 및 추가 설정:

```yaml
# .dli-format.yaml
format:
  yaml:
    indent: 2
    line_width: 120
    preserve_quotes: true
    key_order: dli_standard  # DLI 표준 키 순서 사용

  sql:
    dialect: bigquery  # 기본 방언 (.sqlfluff보다 낮은 우선순위)
    use_project_sqlfluff: true  # .sqlfluff 파일 우선 적용

  backup:
    enabled: true
    suffix: ".bak"
```

### 7.3 설정 우선순위

```
1. CLI 옵션           --dialect trino
2. .sqlfluff         [sqlfluff] dialect = bigquery
3. .dli-format.yaml  format.sql.dialect: bigquery
4. 기본값            bigquery
```

---

## 8. Library API

### 8.1 FormatAPI 또는 기존 API 확장

**Option A: 기존 DatasetAPI/MetricAPI 확장**

```python
from dli import DatasetAPI, MetricAPI, ExecutionContext

ctx = ExecutionContext(project_path=Path("/opt/airflow/dags/models"))

# DatasetAPI에 format 메서드 추가
api = DatasetAPI(context=ctx)
result = api.format(
    "iceberg.analytics.daily_clicks",
    check_only=False,
    sql_only=False,
    yaml_only=False,
    dialect="bigquery",
    lint=False,
)
```

**Option B: 별도 FormatAPI 클래스**

```python
from dli import FormatAPI, ExecutionContext
from dli.models.format import FormatResult, FormatOptions

ctx = ExecutionContext(project_path=Path("/opt/airflow/dags/models"))
api = FormatAPI(context=ctx)

# Dataset 포맷팅
result: FormatResult = api.format_dataset(
    "iceberg.analytics.daily_clicks",
    options=FormatOptions(
        check_only=True,
        dialect="trino",
        lint=False,
    ),
)

# Metric 포맷팅
result = api.format_metric("iceberg.analytics.user_engagement")

# 결과 확인
print(f"Status: {result.status}")  # SUCCESS, CHANGED, FAILED
for file_result in result.files:
    print(f"  {file_result.path}: {file_result.status}")
```

**결정:** Option A 선택 (기존 API 확장)

**근거:**
- validate/run 등 기존 메서드와 일관성 유지
- API 클래스 수 증가 방지
- 리소스 중심 인터페이스 패턴 유지

### 8.2 API 시그니처

```python
class DatasetAPI:
    def format(
        self,
        name: str,
        *,
        check_only: bool = False,
        sql_only: bool = False,
        yaml_only: bool = False,
        dialect: str | None = None,
        lint: bool = False,
        fix: bool = False,
    ) -> FormatResult:
        """Format dataset SQL and YAML files.

        Args:
            name: Dataset name (catalog.schema.name)
            check_only: If True, only check without modifying files
            sql_only: Format SQL file only
            yaml_only: Format YAML file only
            dialect: SQL dialect (bigquery, trino, snowflake, etc.)
            lint: Apply lint rules
            fix: Auto-fix lint violations (requires lint=True)

        Returns:
            FormatResult with status and file changes

        Raises:
            DatasetNotFoundError: Dataset not found
            FormatError: Formatting failed
        """
        ...
```

### 8.3 결과 모델

```python
from enum import Enum
from pydantic import BaseModel, Field

class FormatStatus(str, Enum):
    """Format operation status."""
    SUCCESS = "success"      # No changes needed
    CHANGED = "changed"      # Files were changed (or would be changed in check mode)
    FAILED = "failed"        # Formatting failed

class FileFormatStatus(str, Enum):
    """Individual file format status."""
    UNCHANGED = "unchanged"
    CHANGED = "changed"
    ERROR = "error"

class FileFormatResult(BaseModel):
    """Format result for a single file."""
    path: str
    status: FileFormatStatus
    changes: list[str] = Field(default_factory=list)  # Diff lines
    lint_violations: list[str] = Field(default_factory=list)

class FormatResult(BaseModel):
    """Overall format result."""
    name: str  # Resource name
    resource_type: str  # "dataset" or "metric"
    status: FormatStatus
    files: list[FileFormatResult]
    message: str | None = None

    @property
    def changed_count(self) -> int:
        return sum(1 for f in self.files if f.status == FileFormatStatus.CHANGED)

    @property
    def error_count(self) -> int:
        return sum(1 for f in self.files if f.status == FileFormatStatus.ERROR)
```

---

## 9. 에러 코드

### 9.1 새 에러 코드 (DLI-15xx)

| Code | 이름 | 설명 |
|------|------|------|
| DLI-1501 | `FormatError` | 일반 포맷팅 오류 |
| DLI-1502 | `FormatSqlError` | SQL 포맷팅 실패 |
| DLI-1503 | `FormatYamlError` | YAML 포맷팅 실패 |
| DLI-1504 | `FormatDialectError` | 지원하지 않는 SQL 방언 |
| DLI-1505 | `FormatConfigError` | 설정 파일 오류 (.sqlfluff, .dli-format.yaml) |
| DLI-1506 | `FormatLintError` | Lint 규칙 위반 (--lint --check 시) |

### 9.2 기존 에러 코드 재사용

| Code | 이름 | 사용 상황 |
|------|------|----------|
| DLI-101 | `DatasetNotFoundError` | 대상 Dataset을 찾을 수 없음 |
| DLI-201 | `MetricNotFoundError` | 대상 Metric을 찾을 수 없음 |
| DLI-001 | `ConfigurationError` | 프로젝트 경로 오류 |

### 9.3 예외 클래스

```python
# dli/exceptions.py에 추가

class FormatError(DLIError):
    """Base format error."""

    def __init__(
        self,
        message: str,
        code: ErrorCode = ErrorCode.FORMAT_ERROR,
        resource_name: str | None = None,
        file_path: str | None = None,
        cause: Exception | None = None,
    ) -> None:
        super().__init__(message=message, code=code, cause=cause)
        self.resource_name = resource_name
        self.file_path = file_path


class FormatSqlError(FormatError):
    """SQL formatting error."""

    def __init__(self, message: str, file_path: str, line: int | None = None, **kwargs):
        super().__init__(
            message=message,
            code=ErrorCode.FORMAT_SQL_ERROR,
            file_path=file_path,
            **kwargs,
        )
        self.line = line


class FormatDialectError(FormatError):
    """Unsupported SQL dialect."""

    def __init__(self, dialect: str, supported: list[str], **kwargs):
        message = f"Unsupported dialect '{dialect}'. Supported: {', '.join(supported)}"
        super().__init__(
            message=message,
            code=ErrorCode.FORMAT_DIALECT_ERROR,
            **kwargs,
        )
        self.dialect = dialect
        self.supported = supported
```

---

## 10. 테스트 전략

### 10.1 단위 테스트

```python
# tests/core/format/test_sql_formatter.py
import pytest
from dli.core.format import SqlFormatter

class TestSqlFormatter:
    """SQL formatter tests."""

    @pytest.mark.parametrize("dialect", ["bigquery", "trino", "snowflake"])
    def test_format_simple_sql(self, dialect: str) -> None:
        """Test basic SQL formatting."""
        formatter = SqlFormatter(dialect=dialect)
        sql = "select a,b from t where x=1"
        result = formatter.format(sql)
        assert "SELECT" in result
        assert "FROM" in result

    def test_preserve_jinja_template(self) -> None:
        """Test Jinja template preservation."""
        formatter = SqlFormatter(dialect="bigquery")
        sql = "SELECT * FROM {{ ref('my_table') }} WHERE dt = '{{ ds }}'"
        result = formatter.format(sql)
        assert "{{ ref('my_table') }}" in result
        assert "{{ ds }}" in result

    def test_format_complex_jinja(self) -> None:
        """Test complex Jinja blocks."""
        formatter = SqlFormatter(dialect="bigquery")
        sql = """
        SELECT *
        FROM table
        {% if condition %}
        WHERE status = 'active'
        {% endif %}
        """
        result = formatter.format(sql)
        assert "{% if condition %}" in result
        assert "{% endif %}" in result

    def test_unsupported_dialect_raises(self) -> None:
        """Test error on unsupported dialect."""
        with pytest.raises(FormatDialectError) as exc:
            SqlFormatter(dialect="unknown")
        assert "unknown" in str(exc.value)
```

```python
# tests/core/format/test_yaml_formatter.py
import pytest
from dli.core.format import YamlFormatter

class TestYamlFormatter:
    """YAML formatter tests."""

    def test_key_order(self) -> None:
        """Test DLI standard key ordering."""
        formatter = YamlFormatter()
        yaml_content = """
        tags: [daily]
        name: my_dataset
        owner: test@example.com
        """
        result = formatter.format(yaml_content)
        # name should come before tags
        assert result.index("name:") < result.index("tags:")

    def test_preserve_comments(self) -> None:
        """Test comment preservation."""
        formatter = YamlFormatter()
        yaml_content = """
        name: my_dataset  # important dataset
        owner: test@example.com
        """
        result = formatter.format(yaml_content)
        assert "# important dataset" in result

    def test_indent_consistency(self) -> None:
        """Test 2-space indentation."""
        formatter = YamlFormatter()
        yaml_content = """
        name: test
        parameters:
            - name: date
              type: string
        """
        result = formatter.format(yaml_content)
        lines = result.split("\n")
        # Check 2-space indent
        param_line = next(l for l in lines if "- name:" in l)
        assert param_line.startswith("  ")  # 2-space indent
```

### 10.2 API 테스트

```python
# tests/api/test_format_api.py
import pytest
from pathlib import Path
from dli import DatasetAPI, ExecutionContext, ExecutionMode
from dli.models.format import FormatStatus

class TestDatasetAPIFormat:
    """Dataset format API tests."""

    @pytest.fixture
    def mock_api(self, tmp_path: Path) -> DatasetAPI:
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=tmp_path,
        )
        return DatasetAPI(context=ctx)

    def test_format_check_only(self, mock_api: DatasetAPI) -> None:
        """Test check mode returns status without modifying."""
        result = mock_api.format("test_dataset", check_only=True)
        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

    def test_format_sql_only(self, mock_api: DatasetAPI) -> None:
        """Test SQL-only formatting."""
        result = mock_api.format("test_dataset", sql_only=True)
        # Only SQL file should be in results
        sql_files = [f for f in result.files if f.path.endswith(".sql")]
        yaml_files = [f for f in result.files if f.path.endswith(".yaml")]
        assert len(sql_files) >= 1
        assert len(yaml_files) == 0

    def test_format_with_lint(self, mock_api: DatasetAPI) -> None:
        """Test formatting with lint rules."""
        result = mock_api.format("test_dataset", lint=True)
        # Check lint violations are reported
        assert hasattr(result.files[0], "lint_violations")
```

### 10.3 CLI 테스트

```python
# tests/cli/test_format_cmd.py
from typer.testing import CliRunner
from dli.main import app

runner = CliRunner()

class TestFormatCommand:
    """Format command tests."""

    def test_format_help(self) -> None:
        """Test format command help."""
        result = runner.invoke(app, ["dataset", "format", "--help"])
        assert result.exit_code == 0
        assert "--check" in result.output
        assert "--lint" in result.output

    def test_format_check_mode(self, tmp_project: Path) -> None:
        """Test check mode exit code."""
        result = runner.invoke(
            app,
            ["dataset", "format", "test_dataset", "--check", "--path", str(tmp_project)],
        )
        # Exit code 0 if no changes, 1 if changes needed
        assert result.exit_code in [0, 1]

    def test_format_unknown_dataset(self, tmp_project: Path) -> None:
        """Test error on unknown dataset."""
        result = runner.invoke(
            app,
            ["dataset", "format", "nonexistent", "--path", str(tmp_project)],
        )
        assert result.exit_code == 1
        assert "not found" in result.output.lower()

    def test_format_unsupported_dialect(self, tmp_project: Path) -> None:
        """Test error on unsupported dialect."""
        result = runner.invoke(
            app,
            ["dataset", "format", "test", "--dialect", "unknown", "--path", str(tmp_project)],
        )
        assert result.exit_code == 1
        assert "unsupported" in result.output.lower() or "dialect" in result.output.lower()
```

### 10.4 통합 테스트

```python
# tests/integration/test_format_integration.py
import pytest
from pathlib import Path
from dli import DatasetAPI, ExecutionContext

class TestFormatIntegration:
    """End-to-end format tests."""

    @pytest.fixture
    def sample_dataset(self, tmp_path: Path) -> Path:
        """Create sample dataset for testing."""
        # Create YAML
        yaml_path = tmp_path / "dataset.test.my_dataset.yaml"
        yaml_path.write_text("""
name: test.my_dataset
owner: test@example.com
query_file: sql/my_dataset.sql
        """)

        # Create SQL with unformatted content
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        sql_path = sql_dir / "my_dataset.sql"
        sql_path.write_text("""
select a,b,c from table where x=1 and y='{{ ds }}'
        """)

        return tmp_path

    def test_full_format_flow(self, sample_dataset: Path) -> None:
        """Test complete format workflow."""
        ctx = ExecutionContext(project_path=sample_dataset)
        api = DatasetAPI(context=ctx)

        # Check first
        check_result = api.format("test.my_dataset", check_only=True)
        assert check_result.changed_count > 0

        # Apply format
        format_result = api.format("test.my_dataset")
        assert format_result.status in ["success", "changed"]

        # Verify formatting applied
        sql_path = sample_dataset / "sql" / "my_dataset.sql"
        content = sql_path.read_text()
        assert "SELECT" in content  # Uppercase keywords
        assert "{{ ds }}" in content  # Jinja preserved
```

---

## 11. 의존성

### 11.1 새 의존성

```toml
# pyproject.toml
[project.dependencies]
# ... 기존 의존성 ...

[project.optional-dependencies]
format = [
    "sqlfluff>=3.0.0",      # SQL formatting + linting
    "ruamel.yaml>=0.18.0",  # YAML formatting (preserves comments)
]

# 또는 기본 의존성에 포함 (권장)
[project.dependencies]
sqlfluff = ">=3.0.0"
ruamel.yaml = ">=0.18.0"
```

### 11.2 의존성 선택 근거

| 패키지 | 버전 | 용도 | 대안 |
|--------|------|------|------|
| sqlfluff | >=3.0.0 | SQL 포맷팅, Lint | sqlfmt (Jinja 미지원) |
| ruamel.yaml | >=0.18.0 | YAML 포맷팅 (주석 보존) | PyYAML (주석 삭제됨) |

### 11.3 기존 의존성 재사용

| 패키지 | 현재 사용 | 추가 활용 |
|--------|----------|----------|
| pydantic | 모델 정의 | FormatResult 등 |
| rich | CLI 출력 | diff 출력 |
| typer | CLI 프레임워크 | 커맨드 정의 |

---

## 12. 구현 우선순위

### Phase 1 (MVP) - ✅ Complete

1. **Core 포맷터 구현** ✅
   - `SqlFormatter` 클래스 (sqlfluff 래퍼)
   - `YamlFormatter` 클래스 (ruamel.yaml 래퍼)
   - 기본 설정 로딩 (.sqlfluff)

2. **DatasetAPI/MetricAPI 확장** ✅
   - `format()` 메서드 추가
   - FormatResult 모델

3. **CLI 커맨드 구현** ✅
   - `dli dataset format`
   - `dli metric format`
   - 기본 옵션 (--check, --sql-only, --yaml-only, --dialect)

4. **Lint 기능** ✅ (moved from Phase 2)
   - `--lint` 옵션
   - `--fix` 옵션
   - Lint 결과 출력

### Phase 2 (Future Enhancements)

1. **설정 확장**
   - `.dli-format.yaml` 지원 (partially implemented)
   - 프로젝트별 키 순서 커스터마이징

2. **CI 통합**
   - GitHub Actions 예제
   - pre-commit hook 설정

3. **Performance**
   - `--parallel` option for large projects

---

## Appendix: 결정 사항 (인터뷰 기반)

### A.1 포맷팅 엔진 선택

- **결정**: sqlfluff (Jinja 네이티브 지원)
- **근거**: SQLglot은 포맷팅 기능이 제한적이고 Jinja 미지원. sqlfluff는 27개 방언과 Jinja 템플릿 지원.

### A.2 CLI 패턴

- **결정**: 리소스 기반 (`dli dataset format <name>`)
- **근거**: 기존 validate 패턴과 일관성 유지, 사용자 학습 곡선 최소화

### A.3 Lint 기본값

- **결정**: 기본 비활성화 (`--lint`로 활성화)
- **근거**: 포맷팅과 린트 분리, 점진적 도입 지원, CI에서 선택적 적용

### A.4 YAML 키 순서

- **결정**: DLI 표준 순서 (name -> owner -> team -> ...)
- **근거**: 일관된 스타일로 코드 리뷰 효율화, diff 비교 용이

### A.5 에러 코드 범위

- **결정**: DLI-15xx 범위 사용
- **근거**: 기존 에러 코드 패턴 (0xx~14xx) 유지, Format 전용 범위 확보

---

## Appendix B: 참조 패턴

### B.1 기존 CLI 패턴 참조

| 참조 파일 | 적용 패턴 |
|-----------|-----------|
| `commands/dataset.py` | validate 커맨드 구조 |
| `commands/metric.py` | validate 커맨드 구조 |
| `commands/utils.py` | console, print_error, print_success |
| `commands/base.py` | get_client, get_project_path |

### B.2 기존 API 패턴 참조

| 참조 파일 | 적용 패턴 |
|-----------|-----------|
| `api/dataset.py` | DatasetAPI.validate() 시그니처 |
| `api/metric.py` | MetricAPI.validate() 시그니처 |
| `models/common.py` | ExecutionContext, ResultStatus |

### B.3 기존 예외 패턴 참조

| 참조 파일 | 적용 패턴 |
|-----------|-----------|
| `exceptions.py` | ErrorCode enum 확장 |
| `exceptions.py` | DLIError 상속 구조 |

---

## Appendix C: 구현 체크리스트

### C.1 파일 생성 예정

| 구분 | 파일 | 설명 |
|------|------|------|
| **Core** | `dli/core/format/__init__.py` | Format 모듈 |
| **Core** | `dli/core/format/sql_formatter.py` | SqlFormatter 클래스 |
| **Core** | `dli/core/format/yaml_formatter.py` | YamlFormatter 클래스 |
| **Core** | `dli/core/format/config.py` | 설정 로딩 |
| **Models** | `dli/models/format.py` | FormatResult, FormatOptions 등 |
| **Tests** | `tests/core/format/test_sql_formatter.py` | SQL 포맷터 테스트 |
| **Tests** | `tests/core/format/test_yaml_formatter.py` | YAML 포맷터 테스트 |
| **Tests** | `tests/cli/test_format_cmd.py` | CLI 테스트 |

### C.2 파일 수정 예정

| 파일 | 수정 내용 |
|------|----------|
| `dli/api/dataset.py` | `format()` 메서드 추가 |
| `dli/api/metric.py` | `format()` 메서드 추가 |
| `dli/commands/dataset.py` | `format` 서브커맨드 추가 |
| `dli/commands/metric.py` | `format` 서브커맨드 추가 |
| `dli/exceptions.py` | DLI-15xx 에러 코드 추가 |
| `pyproject.toml` | sqlfluff, ruamel.yaml 의존성 추가 |

---

## Implementation Review (feature-interface-cli)

### Strengths

1. **Consistent CLI Pattern**: `dli dataset format <name>` follows the existing `validate` pattern - minimal learning curve
2. **Error Code Range**: DLI-15xx is appropriate; current range is DLI-0xx to DLI-9xx
3. **API Design**: Extending `DatasetAPI`/`MetricAPI` with `format()` method aligns with existing patterns (`validate()`, `run()`)
4. **Configuration Hierarchy**: `.sqlfluff` -> `.dli-format.yaml` -> defaults is sensible

### Technical Concerns

| Item | Issue | Recommendation |
|------|-------|----------------|
| **Mutual Exclusivity** | `--sql-only` and `--yaml-only` can be used together | Add CLI-level validation: `if sql_only and yaml_only: raise BadParameter` |
| **Backup Mechanism** | "Non-destructive" mentions backup but Section 7 only shows `.bak` suffix | Clarify backup file location and cleanup policy |
| **Exit Codes** | `--check` returns 1 on changes | Add exit code 2 for errors (distinguish from "needs formatting") |

### Architecture Alignment

```
# Proposed structure matches existing patterns:
dli/core/format/           # New module (like core/quality/, core/workflow/)
    __init__.py
    sql_formatter.py       # Wraps sqlfluff
    yaml_formatter.py      # Wraps ruamel.yaml
    config.py              # Config loading
```

### Missing Details

1. **File Discovery**: How does `format()` locate SQL/YAML files from resource name? Reference `SpecLoader` pattern from `core/models/`
2. **Parallel Formatting**: Large projects may need `--parallel` option (sqlfluff supports this)
3. **Dry-run Semantics**: `--check --diff` vs `--diff` without `--check` - clarify behavior

### Verdict

**Ready for Phase 1 implementation** with minor clarifications. Recommend starting with:
1. Core formatters (`SqlFormatter`, `YamlFormatter`)
2. API extension (`DatasetAPI.format()`)
3. CLI commands

---

## Python Review (expert-python)

### Library Choices

| Library | Version | Assessment |
|---------|---------|------------|
| `sqlfluff>=3.0.0` | Correct | v3.0+ has improved Jinja handling; pin minimum version |
| `ruamel.yaml>=0.18.0` | Correct | Stable API; v0.18+ has better type hints |

### Code Quality Suggestions

**1. sqlfluff Integration Pattern:**

```python
# Recommended: Use sqlfluff Python API, not subprocess
from sqlfluff.core import Linter

class SqlFormatter:
    def __init__(self, dialect: str = "bigquery"):
        self._linter = Linter(dialect=dialect)

    def format(self, sql: str) -> str:
        result = self._linter.fix_string(sql)
        return result.output_string
```

**2. ruamel.yaml Configuration:**

```python
# Section 4.3 is correct, but add explicit type safety:
from ruamel.yaml import YAML
from io import StringIO

yaml = YAML(typ='rt')  # Round-trip mode (preserves comments)
yaml.indent(mapping=2, sequence=4, offset=2)
yaml.preserve_quotes = True
yaml.default_flow_style = False  # Always block style for readability
```

**3. Error Handling Pattern:**

```python
# Wrap sqlfluff exceptions properly
from sqlfluff.core.errors import SQLFluffError

try:
    result = self._linter.fix_string(sql)
except SQLFluffError as e:
    raise FormatSqlError(
        message=str(e),
        file_path=file_path,
        cause=e,  # Preserve original exception
    )
```

### Test Strategy Improvements

1. **Fixture Deduplication**: `tmp_project` fixture appears in multiple test files - move to `tests/conftest.py`
2. **Parameterized Dialect Tests**: Good use of `@pytest.mark.parametrize` in Section 10.1
3. **Missing Edge Cases**:
   - Empty SQL file
   - YAML with syntax errors
   - Mixed encoding (UTF-8 BOM)

### Dependency Management

```toml
# Recommend optional-dependencies for backwards compatibility:
[project.optional-dependencies]
format = [
    "sqlfluff>=3.0.0,<4.0.0",  # Pin major version
    "ruamel.yaml>=0.18.0,<0.19.0",
]

# Installation: uv pip install dli[format]
```

### Type Safety

All new models use Pydantic v2 - ensure:
- `FormatResult` uses `model_validator` if cross-field validation needed
- `FileFormatResult.changes` should be `list[str]` not `List[str]` (Python 3.9+ syntax)

### Verdict

**Python implementation is sound.** Key recommendations:
1. Use sqlfluff Python API directly (not subprocess)
2. Consider optional dependency for users who do not need formatting
3. Add encoding handling for non-UTF-8 files

---

**Document Complete**
