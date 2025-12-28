# Day 5: 테스트 + 문서화 + 안정화 가이드 (dli)

## 핵심 목표

1. **라이브러리 통합 테스트** - wheel 빌드 후 설치하여 전체 흐름 검증
2. **문서화** - README, 쿼리 작성 가이드
3. **버그 수정 버퍼** - Day 1-4에서 발견된 이슈 해결

---

## 시간 배분

| 순서 | 항목 | 시간 | 설명 |
|------|------|------|------|
| 1 | 라이브러리 빌드 테스트 | 1h | wheel 설치 후 import 검증 |
| 2 | conftest.py | 0.5h | 공통 Fixture |
| 3 | Core 테스트 보강 | 1h | 누락된 엣지 케이스 |
| 4 | CLI/Airflow 통합 테스트 | 2h | End-to-End 테스트 |
| 5 | README.md | 1.5h | 사용자 가이드 |
| 6 | 쿼리 작성 가이드 | 1h | 개발자 문서 |
| 7 | 버퍼 | 1h | 버그 수정 |

---

## 0. 라이브러리 빌드 테스트

### 빌드 및 설치 검증

```bash
#!/bin/bash
# scripts/test_wheel.sh

set -e

echo "=== Building wheel ==="
uv build

echo "=== Creating test environment ==="
python -m venv test_wheel_env
source test_wheel_env/bin/activate

echo "=== Installing wheel ==="
pip install dist/dli-*.whl

echo "=== Testing CLI import ==="
dli --help

echo "=== Testing core import ==="
python -c "from dli.core import SQLFrameworkService; print('core: OK')"

echo "=== Testing airflow import (without apache-airflow) ==="
python -c "
try:
    from dli.airflow import SQLFrameworkOperator
    print('airflow: FAIL - should not import without apache-airflow')
except ImportError as e:
    print('airflow: OK - correctly fails without apache-airflow')
"

echo "=== Installing with airflow extra ==="
pip install dist/dli-*.whl[airflow]

echo "=== Testing airflow import (with apache-airflow) ==="
python -c "from dli.airflow import SQLFrameworkOperator; print('airflow: OK')"

echo "=== Cleanup ==="
deactivate
rm -rf test_wheel_env

echo "=== All tests passed! ==="
```

### pytest 라이브러리 테스트

```python
# tests/integration/test_library_import.py
"""라이브러리 import 테스트"""
import pytest
import subprocess
import sys


class TestLibraryImport:
    def test_core_imports(self):
        """core 모듈 import 테스트"""
        from dli.core import (
            SQLFrameworkService,
            QueryRegistry,
            SQLRenderer,
            SQLValidator,
        )
        from dli.core.models import (
            QueryDefinition,
            QueryParameter,
            ValidationResult,
            ExecutionResult,
            ParameterType,
        )
        # import 성공하면 테스트 통과
        assert SQLFrameworkService is not None

    def test_adapters_imports(self):
        """adapters 모듈 import 테스트"""
        from dli.adapters import BigQueryExecutor
        from dli.core.executor import BaseExecutor, MockExecutor
        assert BaseExecutor is not None

    def test_cli_import(self):
        """CLI 모듈 import 테스트"""
        from dli.main import app
        assert app is not None

    @pytest.mark.skipif(
        "apache-airflow" not in subprocess.run(
            [sys.executable, "-m", "pip", "list"],
            capture_output=True, text=True
        ).stdout,
        reason="apache-airflow not installed"
    )
    def test_airflow_imports(self):
        """airflow 모듈 import 테스트 (airflow 설치 시)"""
        from dli.airflow import (
            SQLFrameworkOperator,
            SQLFrameworkValidateOperator,
            SQLFrameworkSensor,
        )
        assert SQLFrameworkOperator is not None
```

---

## 테스트 디렉토리 구조

```
tests/
├── conftest.py              # 공통 Fixture
├── core/
│   ├── test_models.py
│   ├── test_registry.py
│   ├── test_renderer.py
│   ├── test_validator.py
│   ├── test_executor.py
│   └── test_service.py
├── cli/
│   ├── test_main.py
│   ├── test_utils.py
│   └── test_commands.py
├── api/
│   └── test_routes.py
├── airflow/
│   ├── test_operators.py
│   └── test_sensors.py
└── integration/
    ├── test_cli_e2e.py
    └── test_api_e2e.py
```

---

## 1. conftest.py (공통 Fixture)

### 코드
```python
import pytest
import tempfile
from pathlib import Path
from unittest.mock import Mock
import yaml


@pytest.fixture
def temp_queries_dir():
    """테스트용 임시 쿼리 디렉토리"""
    with tempfile.TemporaryDirectory() as tmpdir:
        queries_dir = Path(tmpdir)
        
        # _schema.yml
        schema = {
            "queries": [
                {
                    "name": "test_query",
                    "description": "Test query for unit tests",
                    "sql_file": "test_query.sql",
                    "parameters": [
                        {"name": "date", "type": "date", "required": True},
                        {"name": "limit", "type": "integer", "default": 10},
                    ],
                    "tags": ["test", "unit"],
                    "owner": "test-team",
                },
                {
                    "name": "no_params_query",
                    "description": "Query without parameters",
                    "sql_file": "no_params.sql",
                    "parameters": [],
                    "tags": ["test"],
                    "owner": "test-team",
                },
            ]
        }
        (queries_dir / "_schema.yml").write_text(yaml.dump(schema))
        
        # test_query.sql
        (queries_dir / "test_query.sql").write_text(
            "SELECT * FROM users WHERE created_at = '{{ date }}' LIMIT {{ limit }}"
        )
        
        # no_params.sql
        (queries_dir / "no_params.sql").write_text(
            "SELECT COUNT(*) as cnt FROM users"
        )
        
        yield queries_dir


@pytest.fixture
def mock_executor():
    """Mock BigQuery Executor"""
    executor = Mock()
    executor.test_connection.return_value = True
    executor.dry_run.return_value = {
        "valid": True,
        "bytes_processed": 1_000_000_000,
        "bytes_processed_gb": 1.0,
        "estimated_cost_usd": 0.005,
    }
    executor.execute.return_value = Mock(
        success=True,
        query_name="test_query",
        row_count=5,
        columns=["id", "name", "created_at"],
        data=[
            {"id": 1, "name": "Alice", "created_at": "2024-01-01"},
            {"id": 2, "name": "Bob", "created_at": "2024-01-01"},
            {"id": 3, "name": "Charlie", "created_at": "2024-01-01"},
            {"id": 4, "name": "David", "created_at": "2024-01-01"},
            {"id": 5, "name": "Eve", "created_at": "2024-01-01"},
        ],
        rendered_sql="SELECT * FROM users WHERE created_at = '2024-01-01' LIMIT 10",
        execution_time_ms=150,
        error_message=None,
    )
    return executor


@pytest.fixture
def service(temp_queries_dir, mock_executor):
    """테스트용 Service 인스턴스"""
    from sqlfw.core.service import SQLFrameworkService
    return SQLFrameworkService(
        queries_dir=temp_queries_dir,
        executor=mock_executor,
        dialect="bigquery",
    )


@pytest.fixture
def sample_execution_result():
    """샘플 ExecutionResult"""
    from sqlfw.core.models import ExecutionResult
    return ExecutionResult(
        query_name="test_query",
        success=True,
        row_count=3,
        columns=["id", "name"],
        data=[
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
            {"id": 3, "name": "Charlie"},
        ],
        rendered_sql="SELECT id, name FROM users",
        execution_time_ms=100,
    )


@pytest.fixture
def sample_validation_result():
    """샘플 ValidationResult"""
    from sqlfw.core.models import ValidationResult
    return ValidationResult(
        is_valid=True,
        errors=[],
        warnings=["SELECT without LIMIT"],
        rendered_sql="SELECT * FROM users",
    )
```

---

## 2. 통합 테스트

### tests/integration/test_cli_e2e.py
```python
import pytest
from typer.testing import CliRunner
from pathlib import Path
import os

runner = CliRunner()


class TestCLIEndToEnd:
    """CLI End-to-End 테스트 (Mock 없이 실제 흐름 테스트)"""
    
    @pytest.fixture(autouse=True)
    def setup(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "")  # Executor 없이 테스트
    
    def test_list_queries_flow(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["list", "queries"])
        
        assert result.exit_code == 0
        assert "test_query" in result.stdout
        assert "no_params_query" in result.stdout
    
    def test_list_queries_with_tag_filter(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["list", "queries", "--tag", "unit"])
        
        assert result.exit_code == 0
        assert "test_query" in result.stdout
    
    def test_list_params_flow(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["list", "params", "test_query"])
        
        assert result.exit_code == 0
        assert "date" in result.stdout
        assert "limit" in result.stdout
    
    def test_validate_flow_success(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, [
            "validate", "test_query",
            "-p", "date=2024-01-01",
        ])
        
        # 검증은 executor 없이도 가능
        assert "SELECT" in result.stdout or result.exit_code == 0
    
    def test_validate_flow_missing_param(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["validate", "test_query"])
        
        assert result.exit_code == 1
    
    def test_help_displays(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["--help"])
        assert result.exit_code == 0
        assert "SQL Framework" in result.stdout
        
        result = runner.invoke(app, ["list", "--help"])
        assert result.exit_code == 0
        
        result = runner.invoke(app, ["run", "--help"])
        assert result.exit_code == 0


class TestCLIErrorHandling:
    """CLI 에러 핸들링 테스트"""
    
    @pytest.fixture(autouse=True)
    def setup(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "")
    
    def test_nonexistent_query(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, ["list", "params", "nonexistent"])
        
        assert result.exit_code == 1
        assert "not found" in result.stdout.lower()
    
    def test_invalid_param_format(self):
        from sqlfw.cli.main import app
        
        result = runner.invoke(app, [
            "validate", "test_query",
            "-p", "invalid-format",
        ])
        
        assert result.exit_code == 1
    
    def test_nonexistent_queries_dir(self, monkeypatch):
        from sqlfw.cli.main import app
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", "/nonexistent/path")
        
        result = runner.invoke(app, ["list", "queries"])
        
        # 에러 처리 확인
        assert result.exit_code != 0 or "error" in result.stdout.lower()
```

### tests/integration/test_api_e2e.py
```python
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch


class TestAPIEndToEnd:
    """API End-to-End 테스트"""
    
    @pytest.fixture
    def client(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "")
        
        from sqlfw.api.main import app
        from sqlfw.api.dependencies import reset_service
        
        reset_service()  # 캐시 초기화
        return TestClient(app)
    
    def test_health_check(self, client):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "healthy"
    
    def test_list_queries_flow(self, client):
        response = client.get("/api/queries")
        
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        
        names = [q["name"] for q in data]
        assert "test_query" in names
        assert "no_params_query" in names
    
    def test_get_query_detail(self, client):
        response = client.get("/api/queries/test_query")
        
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "test_query"
        assert len(data["parameters"]) == 2
    
    def test_get_query_not_found(self, client):
        response = client.get("/api/queries/nonexistent")
        
        assert response.status_code == 404
    
    def test_validate_flow(self, client):
        response = client.post("/api/validate", json={
            "query_name": "test_query",
            "params": {"date": "2024-01-01"},
        })
        
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is True
        assert "SELECT" in data["rendered_sql"]
    
    def test_validate_missing_param(self, client):
        response = client.post("/api/validate", json={
            "query_name": "test_query",
            "params": {},
        })
        
        assert response.status_code == 200
        data = response.json()
        assert data["is_valid"] is False
        assert len(data["errors"]) > 0
    
    def test_reload(self, client):
        response = client.post("/api/reload")
        
        assert response.status_code == 200
        assert response.json()["status"] == "reloaded"
    
    def test_swagger_docs(self, client):
        response = client.get("/docs")
        assert response.status_code == 200
        
        response = client.get("/openapi.json")
        assert response.status_code == 200
        assert "paths" in response.json()
```

---

## 3. 엣지 케이스 테스트 보강

### tests/core/test_edge_cases.py
```python
import pytest
from sqlfw.core.models import QueryParameter, ParameterType
from sqlfw.core.validator import SQLValidator


class TestParameterEdgeCases:
    """파라미터 엣지 케이스"""
    
    def test_empty_string_value(self):
        param = QueryParameter(name="filter", required=False, default="")
        assert param.validate_value("") == ""
    
    def test_special_characters_in_string(self):
        param = QueryParameter(name="name", type=ParameterType.STRING)
        # SQL Injection 시도
        value = param.validate_value("'; DROP TABLE users; --")
        assert value == "'; DROP TABLE users; --"
        # 실제 방어는 렌더러에서 처리
    
    def test_negative_integer(self):
        param = QueryParameter(name="offset", type=ParameterType.INTEGER)
        assert param.validate_value("-10") == -10
    
    def test_float_precision(self):
        param = QueryParameter(name="rate", type=ParameterType.FLOAT)
        assert param.validate_value("0.123456789") == 0.123456789
    
    def test_boolean_variations(self):
        param = QueryParameter(name="active", type=ParameterType.BOOLEAN)
        assert param.validate_value("true") is True
        assert param.validate_value("True") is True
        assert param.validate_value("TRUE") is True
        assert param.validate_value("1") is True
        assert param.validate_value("false") is False
        assert param.validate_value("0") is False


class TestValidatorEdgeCases:
    """SQL 검증 엣지 케이스"""
    
    def test_multiline_sql(self):
        validator = SQLValidator("bigquery")
        sql = """
        SELECT 
            id,
            name
        FROM users
        WHERE active = true
        """
        result = validator.validate(sql)
        assert result.is_valid
    
    def test_sql_with_comments(self):
        validator = SQLValidator("bigquery")
        sql = """
        -- This is a comment
        SELECT * FROM users /* inline comment */
        """
        result = validator.validate(sql)
        assert result.is_valid
    
    def test_cte_query(self):
        validator = SQLValidator("bigquery")
        sql = """
        WITH active_users AS (
            SELECT * FROM users WHERE active = true
        )
        SELECT * FROM active_users
        """
        result = validator.validate(sql)
        assert result.is_valid
    
    def test_union_query(self):
        validator = SQLValidator("bigquery")
        sql = """
        SELECT id, name FROM users_a
        UNION ALL
        SELECT id, name FROM users_b
        """
        result = validator.validate(sql)
        assert result.is_valid
    
    def test_empty_sql(self):
        validator = SQLValidator("bigquery")
        result = validator.validate("")
        assert not result.is_valid
    
    def test_whitespace_only_sql(self):
        validator = SQLValidator("bigquery")
        result = validator.validate("   \n\t  ")
        assert not result.is_valid


class TestRegistryEdgeCases:
    """레지스트리 엣지 케이스"""
    
    def test_empty_queries_dir(self, tmp_path):
        from sqlfw.core.registry import QueryRegistry
        
        registry = QueryRegistry(tmp_path)
        assert registry.list_all() == []
    
    def test_duplicate_query_names(self, tmp_path):
        import yaml
        from sqlfw.core.registry import QueryRegistry
        
        # 같은 이름의 쿼리가 두 번 정의된 경우
        schema = {
            "queries": [
                {"name": "dup", "sql_file": "a.sql", "parameters": []},
                {"name": "dup", "sql_file": "b.sql", "parameters": []},
            ]
        }
        (tmp_path / "_schema.yml").write_text(yaml.dump(schema))
        (tmp_path / "a.sql").write_text("SELECT 1")
        (tmp_path / "b.sql").write_text("SELECT 2")
        
        registry = QueryRegistry(tmp_path)
        # 마지막 정의가 우선
        assert registry.get("dup") is not None
```

---

## 4. README.md

### docs/README.md
```markdown
# dli SQL Framework

데이터 분석가/마케터를 위한 SQL 쿼리 관리 및 실행 프레임워크

## 특징

- **쿼리 등록**: YAML 기반 쿼리 정의 및 파라미터 관리
- **검증**: SQLGlot 기반 SQL 문법 검증
- **Dry-run**: BigQuery 비용 사전 추정
- **다중 인터페이스**: CLI (`dli query`), Web UI, Airflow Operator

## 설치

```bash
pip install dataops-cli[bigquery]
```

## 빠른 시작

### 1. 쿼리 정의

```yaml
# queries/_schema.yml
queries:
  - name: daily_retention
    description: "일별 리텐션 분석"
    sql_file: daily_retention.sql
    parameters:
      - name: start_date
        type: date
        required: true
      - name: end_date
        type: date
        required: true
```

### 2. SQL 파일

```sql
-- queries/daily_retention.sql
SELECT 
    cohort_date,
    COUNT(DISTINCT user_id) as users
FROM analytics.retention
WHERE cohort_date BETWEEN '{{ start_date }}' AND '{{ end_date }}'
GROUP BY 1
```

### 3. 실행

```bash
# CLI
dli query run daily_retention -p start_date=2024-01-01 -p end_date=2024-01-31

# Web UI
uvicorn dli.api.main:app --port 8000
```

## CLI 사용법

```bash
# 쿼리 목록
dli query list
dli query list --tag marketing

# 파라미터 확인
dli query params daily_retention

# 검증
dli query validate daily_retention -p start_date=2024-01-01

# Dry-run (비용 추정)
dli query dry-run daily_retention -p start_date=2024-01-01

# 실행
dli query run daily_retention -p start_date=2024-01-01 -o json
dli query run daily_retention -p start_date=2024-01-01 -o csv > result.csv
```

## Airflow 연동

```python
from dli.airflow import SQLFrameworkOperator

task = SQLFrameworkOperator(
    task_id='run_retention',
    query_name='daily_retention',
    params={
        'start_date': '{{ ds }}',
        'end_date': '{{ ds }}',
    },
)
```

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| DLI_QUERIES_DIR | 쿼리 디렉토리 | ./queries |
| DLI_PROJECT | GCP 프로젝트 ID | - |
| DLI_DIALECT | SQL 방언 | bigquery |

## 라이선스

MIT
```

---

## 5. 쿼리 작성 가이드

### docs/QUERY_GUIDE.md
```markdown
# 쿼리 작성 가이드

## 쿼리 정의 (_schema.yml)

```yaml
queries:
  - name: query_name           # 필수: 고유 식별자
    description: "설명"        # 권장: 쿼리 설명
    sql_file: query.sql        # 필수: SQL 파일 경로
    parameters:                # 선택: 파라미터 목록
      - name: param1
        type: string           # string, integer, float, date, boolean, list
        required: true
        default: null
        description: "파라미터 설명"
    owner: team-name           # 선택: 담당 팀
    tags: [tag1, tag2]         # 선택: 태그
    timeout_seconds: 300       # 선택: 타임아웃 (기본 300초)
```

## 파라미터 타입

| 타입 | 예시 | 설명 |
|------|------|------|
| string | "hello" | 문자열 |
| integer | 100 | 정수 |
| float | 0.5 | 실수 |
| date | "2024-01-01" | 날짜 (YYYY-MM-DD) |
| boolean | true/false | 불리언 |
| list | ["a", "b"] | 리스트 |

## Jinja 템플릿

### 기본 사용
```sql
SELECT * FROM users WHERE date = '{{ date }}'
```

### 조건문
```sql
SELECT * FROM users
WHERE 1=1
{% if status %}
  AND status = '{{ status }}'
{% endif %}
```

### 반복문
```sql
SELECT * FROM users
WHERE id IN (
  {% for id in user_ids %}
    {{ id }}{% if not loop.last %},{% endif %}
  {% endfor %}
)
```

### 내장 필터
```sql
-- 문자열 이스케이프
WHERE name = {{ name | sql_string }}

-- 리스트를 IN 절로
WHERE id IN {{ ids | sql_list }}
```

## 베스트 프랙티스

1. **LIMIT 사용**: 대용량 테이블 조회 시 LIMIT 추가
2. **파티션 필터**: 날짜 파티션 컬럼 조건 필수
3. **파라미터 검증**: required 파라미터 명시
4. **설명 작성**: description으로 쿼리 목적 명시
5. **태그 활용**: 팀/목적별 태그로 분류
```

---

## 6. pytest.ini

```ini
[pytest]
testpaths = tests
python_files = test_*.py
python_classes = Test*
python_functions = test_*
addopts = -v --tb=short --strict-markers
markers =
    slow: marks tests as slow
    integration: marks tests as integration tests
filterwarnings =
    ignore::DeprecationWarning
```

---

## 7. 테스트 실행 스크립트

### scripts/run_tests.sh
```bash
#!/bin/bash
set -e

echo "=== Running unit tests ==="
pytest tests/core tests/cli tests/api tests/airflow -v

echo "=== Running integration tests ==="
pytest tests/integration -v

echo "=== Checking coverage ==="
pytest --cov=sqlfw --cov-report=term-missing --cov-fail-under=80

echo "=== All tests passed! ==="
```

---

## Day 5 체크리스트

- [ ] conftest.py (공통 Fixture)
- [ ] 통합 테스트
  - [ ] CLI E2E
  - [ ] API E2E
- [ ] 엣지 케이스 테스트
  - [ ] 파라미터 엣지 케이스
  - [ ] SQL 검증 엣지 케이스
  - [ ] 레지스트리 엣지 케이스
- [ ] README.md
- [ ] QUERY_GUIDE.md
- [ ] pytest.ini
- [ ] 테스트 커버리지 80% 이상
- [ ] 최종 버그 수정

---

## 참고 코드

| 참고 | URL |
|------|-----|
| pytest 문서 | https://docs.pytest.org/ |
| pytest-cov | https://pytest-cov.readthedocs.io/ |
| FastAPI TestClient | https://fastapi.tiangolo.com/tutorial/testing/ |
| Typer Testing | https://typer.tiangolo.com/tutorial/testing/ |
