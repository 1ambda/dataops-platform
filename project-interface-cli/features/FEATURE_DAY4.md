# Day 4: Airflow 연동 + 라이브러리 배포 가이드 (dli)

## 핵심 개념

`dataops-cli`는 **라이브러리**로 빌드되어 Airflow에서 import하여 사용합니다.

```python
# Airflow DAG 파일에서
from dli.airflow import SQLFrameworkOperator
from dli.core import SQLFrameworkService  # 직접 사용도 가능
```

---

## 구현 순서 및 시간

| 순서 | 작업 | 시간 | 설명 |
|------|------|------|------|
| 1 | pyproject.toml 업데이트 | 0.5h | 선택적 의존성 추가 |
| 2 | airflow/operators.py | 2.5h | Operator, ValidateOperator |
| 3 | airflow/sensors.py | 1h | Sensor |
| 4 | airflow/__init__.py | 0.5h | 패키지 export |
| 5 | 라이브러리 빌드/배포 | 1.5h | wheel 빌드, PyPI/내부 레지스트리 |
| 6 | examples/dags/ | 1h | 샘플 DAG |
| 7 | 테스트 | 1h | Operator 테스트 |

---

## 0. pyproject.toml 선택적 의존성

```toml
[project.optional-dependencies]
airflow = [
    "apache-airflow>=2.7.0",
]
bigquery = [
    "google-cloud-bigquery>=3.0",
]
snowflake = [
    "snowflake-connector-python>=3.0",
]
all = [
    "dataops-cli[airflow,bigquery,snowflake]",
]
```

### 설치 방법

```bash
# 기본 설치 (CLI + Core)
pip install dataops-cli

# Airflow 연동용
pip install dataops-cli[airflow]

# BigQuery 실행용
pip install dataops-cli[bigquery]

# 전체 설치
pip install dataops-cli[all]
```

---

## 디렉토리 구조

```
src/dli/airflow/
├── __init__.py         # 패키지 export
├── operators.py        # Operator 클래스
└── sensors.py          # Sensor 클래스

examples/dags/
├── daily_retention_dag.py
└── data_quality_dag.py
```

---

## 1. airflow/operators.py

### 기능
- SQLFrameworkOperator: 쿼리 실행
- SQLFrameworkValidateOperator: 검증 전용 (CI/CD)

### 코드
```python
from typing import Any, Optional, Sequence
from airflow.models import BaseOperator
from airflow.utils.context import Context

from ..core.service import SQLFrameworkService
from ..adapters.bigquery import BigQueryExecutor


class SQLFrameworkOperator(BaseOperator):
    """
    SQL Framework 쿼리 실행 Operator
    
    Example:
        task = SQLFrameworkOperator(
            task_id='run_retention',
            query_name='daily_retention',
            params={'date': '{{ ds }}'},
        )
    """
    
    # Jinja 템플릿 지원 필드 (필수!)
    template_fields: Sequence[str] = ('query_name', 'params')
    template_ext: Sequence[str] = ()
    ui_color = '#e8f7e4'
    
    def __init__(
        self,
        *,
        query_name: str,
        params: Optional[dict[str, Any]] = None,
        queries_dir: str = '/opt/airflow/queries',
        project: Optional[str] = None,
        dialect: str = 'bigquery',
        dry_run_first: bool = True,
        fail_on_empty: bool = False,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.query_name = query_name
        self.params = params or {}
        self.queries_dir = queries_dir
        self.project = project
        self.dialect = dialect
        self.dry_run_first = dry_run_first
        self.fail_on_empty = fail_on_empty
    
    def execute(self, context: Context) -> dict[str, Any]:
        self.log.info(f"Executing query: {self.query_name}")
        self.log.info(f"Parameters: {self.params}")
        
        # 서비스 초기화
        project = self.project or context.get('params', {}).get('project')
        executor = BigQueryExecutor(project=project) if project else None
        
        service = SQLFrameworkService(
            queries_dir=self.queries_dir,
            executor=executor,
            dialect=self.dialect,
        )
        
        # 실행
        result = service.execute(
            self.query_name,
            self.params,
            dry_run_first=self.dry_run_first,
        )
        
        if not result.success:
            raise Exception(f"Query failed: {result.error_message}")
        
        if self.fail_on_empty and result.row_count == 0:
            raise Exception(f"Query returned no rows: {self.query_name}")
        
        self.log.info(f"Query completed: {result.row_count} rows in {result.execution_time_ms}ms")
        
        # XCom으로 결과 반환
        return {
            'query_name': result.query_name,
            'row_count': result.row_count,
            'columns': result.columns,
            'execution_time_ms': result.execution_time_ms,
            'rendered_sql': result.rendered_sql,
        }


class SQLFrameworkValidateOperator(BaseOperator):
    """
    SQL Framework 쿼리 검증 Operator (CI/CD용)
    
    Example:
        task = SQLFrameworkValidateOperator(
            task_id='validate_queries',
            query_names=['daily_retention', 'user_cohort'],
        )
    """
    
    template_fields: Sequence[str] = ('query_names', 'params')
    ui_color = '#fff7e6'
    
    def __init__(
        self,
        *,
        query_names: list[str],
        params: Optional[dict[str, dict[str, Any]]] = None,
        queries_dir: str = '/opt/airflow/queries',
        dialect: str = 'bigquery',
        fail_fast: bool = True,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.query_names = query_names
        self.params = params or {}
        self.queries_dir = queries_dir
        self.dialect = dialect
        self.fail_fast = fail_fast
    
    def execute(self, context: Context) -> dict[str, Any]:
        self.log.info(f"Validating {len(self.query_names)} queries")
        
        service = SQLFrameworkService(
            queries_dir=self.queries_dir,
            executor=None,  # 검증만 하므로 executor 불필요
            dialect=self.dialect,
        )
        
        results = {}
        failed = []
        
        for query_name in self.query_names:
            query_params = self.params.get(query_name, {})
            self.log.info(f"Validating: {query_name}")
            
            result = service.validate(query_name, query_params)
            results[query_name] = {
                'is_valid': result.is_valid,
                'errors': result.errors,
                'warnings': result.warnings,
            }
            
            if not result.is_valid:
                failed.append(query_name)
                self.log.error(f"Validation failed for {query_name}: {result.errors}")
                
                if self.fail_fast:
                    raise Exception(f"Validation failed: {query_name} - {result.errors}")
        
        if failed:
            raise Exception(f"Validation failed for queries: {failed}")
        
        self.log.info(f"All {len(self.query_names)} queries validated successfully")
        return results
```

---

## 2. airflow/sensors.py

### 기능
- SQLFrameworkSensor: 조건 만족까지 대기

### 코드
```python
from typing import Any, Optional, Sequence
from airflow.sensors.base import BaseSensorOperator
from airflow.utils.context import Context

from ..core.service import SQLFrameworkService
from ..adapters.bigquery import BigQueryExecutor


class SQLFrameworkSensor(BaseSensorOperator):
    """
    SQL Framework 쿼리 결과 대기 Sensor
    
    쿼리 결과가 조건을 만족할 때까지 대기합니다.
    기본: row_count > 0 이면 성공
    
    Example:
        sensor = SQLFrameworkSensor(
            task_id='wait_for_data',
            query_name='check_data_ready',
            params={'date': '{{ ds }}'},
            poke_interval=300,  # 5분마다 체크
            timeout=3600,       # 1시간 타임아웃
        )
    """
    
    template_fields: Sequence[str] = ('query_name', 'params')
    ui_color = '#e6f3ff'
    
    def __init__(
        self,
        *,
        query_name: str,
        params: Optional[dict[str, Any]] = None,
        queries_dir: str = '/opt/airflow/queries',
        project: Optional[str] = None,
        dialect: str = 'bigquery',
        min_row_count: int = 1,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.query_name = query_name
        self.params = params or {}
        self.queries_dir = queries_dir
        self.project = project
        self.dialect = dialect
        self.min_row_count = min_row_count
    
    def poke(self, context: Context) -> bool:
        self.log.info(f"Checking: {self.query_name} with params {self.params}")
        
        project = self.project or context.get('params', {}).get('project')
        executor = BigQueryExecutor(project=project) if project else None
        
        service = SQLFrameworkService(
            queries_dir=self.queries_dir,
            executor=executor,
            dialect=self.dialect,
        )
        
        result = service.execute(self.query_name, self.params, dry_run_first=False)
        
        if not result.success:
            self.log.warning(f"Query failed: {result.error_message}")
            return False
        
        self.log.info(f"Query returned {result.row_count} rows (need >= {self.min_row_count})")
        return result.row_count >= self.min_row_count
```

---

## 3. airflow/__init__.py

### 코드
```python
from .operators import SQLFrameworkOperator, SQLFrameworkValidateOperator
from .sensors import SQLFrameworkSensor

__all__ = [
    'SQLFrameworkOperator',
    'SQLFrameworkValidateOperator',
    'SQLFrameworkSensor',
]
```

---

## 4. 테스트 코드

### tests/airflow/test_operators.py
```python
import pytest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime

from dli.airflow.operators import SQLFrameworkOperator, SQLFrameworkValidateOperator
from dli.core.models import ExecutionResult, ValidationResult


class TestSQLFrameworkOperator:
    @pytest.fixture
    def mock_context(self):
        return {
            'ds': '2024-01-01',
            'params': {'project': 'test-project'},
        }
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    @patch('dli.airflow.operators.BigQueryExecutor')
    def test_execute_success(self, mock_executor_cls, mock_service_cls, mock_context):
        # Mock 설정
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='test_query',
            success=True,
            row_count=10,
            columns=['id', 'name'],
            execution_time_ms=150,
            rendered_sql='SELECT * FROM test',
        )
        mock_service_cls.return_value = mock_service
        
        # Operator 실행
        operator = SQLFrameworkOperator(
            task_id='test_task',
            query_name='test_query',
            params={'date': '2024-01-01'},
            project='test-project',
        )
        
        result = operator.execute(mock_context)
        
        # 검증
        assert result['query_name'] == 'test_query'
        assert result['row_count'] == 10
        mock_service.execute.assert_called_once()
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    @patch('dli.airflow.operators.BigQueryExecutor')
    def test_execute_failure(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='test_query',
            success=False,
            error_message='Query failed',
        )
        mock_service_cls.return_value = mock_service
        
        operator = SQLFrameworkOperator(
            task_id='test_task',
            query_name='test_query',
            project='test-project',
        )
        
        with pytest.raises(Exception, match="Query failed"):
            operator.execute(mock_context)
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    @patch('dli.airflow.operators.BigQueryExecutor')
    def test_fail_on_empty(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='test_query',
            success=True,
            row_count=0,
            columns=[],
            execution_time_ms=100,
        )
        mock_service_cls.return_value = mock_service
        
        operator = SQLFrameworkOperator(
            task_id='test_task',
            query_name='test_query',
            project='test-project',
            fail_on_empty=True,
        )
        
        with pytest.raises(Exception, match="no rows"):
            operator.execute(mock_context)
    
    def test_template_fields(self):
        """template_fields에 params 포함 확인 (Airflow 매크로 지원)"""
        assert 'params' in SQLFrameworkOperator.template_fields
        assert 'query_name' in SQLFrameworkOperator.template_fields


class TestSQLFrameworkValidateOperator:
    @pytest.fixture
    def mock_context(self):
        return {}
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    def test_validate_all_success(self, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.validate.return_value = ValidationResult(is_valid=True)
        mock_service_cls.return_value = mock_service
        
        operator = SQLFrameworkValidateOperator(
            task_id='validate_task',
            query_names=['query1', 'query2'],
        )
        
        result = operator.execute(mock_context)
        
        assert result['query1']['is_valid'] is True
        assert result['query2']['is_valid'] is True
        assert mock_service.validate.call_count == 2
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    def test_validate_failure_fail_fast(self, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.validate.return_value = ValidationResult(
            is_valid=False,
            errors=['Missing parameter']
        )
        mock_service_cls.return_value = mock_service
        
        operator = SQLFrameworkValidateOperator(
            task_id='validate_task',
            query_names=['query1', 'query2'],
            fail_fast=True,
        )
        
        with pytest.raises(Exception, match="Validation failed"):
            operator.execute(mock_context)
        
        # fail_fast이므로 첫 번째 실패 후 중단
        assert mock_service.validate.call_count == 1
    
    @patch('dli.airflow.operators.SQLFrameworkService')
    def test_validate_with_params(self, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.validate.return_value = ValidationResult(is_valid=True)
        mock_service_cls.return_value = mock_service
        
        operator = SQLFrameworkValidateOperator(
            task_id='validate_task',
            query_names=['query1'],
            params={'query1': {'date': '2024-01-01'}},
        )
        
        operator.execute(mock_context)
        
        mock_service.validate.assert_called_with('query1', {'date': '2024-01-01'})
```

### tests/airflow/test_sensors.py
```python
import pytest
from unittest.mock import Mock, patch

from dli.airflow.sensors import SQLFrameworkSensor
from dli.core.models import ExecutionResult


class TestSQLFrameworkSensor:
    @pytest.fixture
    def mock_context(self):
        return {'params': {'project': 'test-project'}}
    
    @patch('dli.airflow.sensors.SQLFrameworkService')
    @patch('dli.airflow.sensors.BigQueryExecutor')
    def test_poke_success(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='check_query',
            success=True,
            row_count=5,
        )
        mock_service_cls.return_value = mock_service
        
        sensor = SQLFrameworkSensor(
            task_id='wait_task',
            query_name='check_query',
            params={'date': '2024-01-01'},
            project='test-project',
        )
        
        result = sensor.poke(mock_context)
        
        assert result is True
    
    @patch('dli.airflow.sensors.SQLFrameworkService')
    @patch('dli.airflow.sensors.BigQueryExecutor')
    def test_poke_no_rows(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='check_query',
            success=True,
            row_count=0,
        )
        mock_service_cls.return_value = mock_service
        
        sensor = SQLFrameworkSensor(
            task_id='wait_task',
            query_name='check_query',
            project='test-project',
        )
        
        result = sensor.poke(mock_context)
        
        assert result is False
    
    @patch('dli.airflow.sensors.SQLFrameworkService')
    @patch('dli.airflow.sensors.BigQueryExecutor')
    def test_poke_min_row_count(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='check_query',
            success=True,
            row_count=5,
        )
        mock_service_cls.return_value = mock_service
        
        sensor = SQLFrameworkSensor(
            task_id='wait_task',
            query_name='check_query',
            project='test-project',
            min_row_count=10,  # 10개 이상 필요
        )
        
        result = sensor.poke(mock_context)
        
        assert result is False  # 5 < 10이므로 False
    
    @patch('dli.airflow.sensors.SQLFrameworkService')
    @patch('dli.airflow.sensors.BigQueryExecutor')
    def test_poke_query_failure(self, mock_executor_cls, mock_service_cls, mock_context):
        mock_service = Mock()
        mock_service.execute.return_value = ExecutionResult(
            query_name='check_query',
            success=False,
            error_message='Query error',
        )
        mock_service_cls.return_value = mock_service
        
        sensor = SQLFrameworkSensor(
            task_id='wait_task',
            query_name='check_query',
            project='test-project',
        )
        
        result = sensor.poke(mock_context)
        
        assert result is False
    
    def test_template_fields(self):
        assert 'params' in SQLFrameworkSensor.template_fields
        assert 'query_name' in SQLFrameworkSensor.template_fields
```

---

## 5. 샘플 DAG

### examples/dags/daily_retention_dag.py
```python
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.empty import EmptyOperator

from dli.airflow import (
    SQLFrameworkOperator,
    SQLFrameworkValidateOperator,
    SQLFrameworkSensor,
)

default_args = {
    'owner': 'data-team',
    'depends_on_past': False,
    'email_on_failure': True,
    'email': ['data-team@example.com'],
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    dag_id='daily_retention_analysis',
    default_args=default_args,
    description='일별 리텐션 분석',
    schedule_interval='0 9 * * *',  # 매일 9시
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=['retention', 'marketing'],
    params={'project': 'my-gcp-project'},
) as dag:
    
    start = EmptyOperator(task_id='start')
    
    # 데이터 준비 대기
    wait_for_data = SQLFrameworkSensor(
        task_id='wait_for_data',
        query_name='check_events_ready',
        params={'date': '{{ ds }}'},
        poke_interval=300,  # 5분마다
        timeout=3600,       # 1시간 타임아웃
        mode='reschedule',  # Worker 점유 안 함
    )
    
    # 리텐션 분석 실행
    run_retention = SQLFrameworkOperator(
        task_id='run_daily_retention',
        query_name='daily_retention',
        params={
            'start_date': '{{ ds }}',
            'end_date': '{{ ds }}',
            'cohort_size': 7,
        },
    )
    
    # 코호트 분석 실행
    run_cohort = SQLFrameworkOperator(
        task_id='run_user_cohort',
        query_name='user_cohort',
        params={
            'date': '{{ ds }}',
        },
    )
    
    end = EmptyOperator(task_id='end')
    
    # 의존성 정의
    start >> wait_for_data >> [run_retention, run_cohort] >> end
```

### examples/dags/data_quality_dag.py
```python
from datetime import datetime
from airflow import DAG
from airflow.operators.empty import EmptyOperator

from dli.airflow import SQLFrameworkValidateOperator

with DAG(
    dag_id='query_validation_ci',
    description='쿼리 검증 (CI/CD)',
    schedule_interval=None,  # 수동 트리거
    start_date=datetime(2024, 1, 1),
    catchup=False,
    tags=['ci', 'validation'],
) as dag:
    
    start = EmptyOperator(task_id='start')
    
    # 모든 쿼리 검증
    validate_all = SQLFrameworkValidateOperator(
        task_id='validate_all_queries',
        query_names=[
            'daily_retention',
            'user_cohort',
            'revenue_report',
        ],
        params={
            'daily_retention': {'start_date': '2024-01-01', 'end_date': '2024-01-01'},
            'user_cohort': {'date': '2024-01-01'},
            'revenue_report': {'month': '2024-01'},
        },
        fail_fast=False,  # 모든 쿼리 검증 후 결과 반환
    )
    
    end = EmptyOperator(task_id='end')
    
    start >> validate_all >> end
```

---

## 핵심 주의사항

### 1. template_fields 필수
```python
# Airflow 매크로 ({{ ds }}, {{ params.xxx }}) 사용하려면 반드시 포함
template_fields: Sequence[str] = ('query_name', 'params')
```

### 2. XCom 결과 반환
```python
# execute()에서 dict 반환 → 다음 태스크에서 사용 가능
return {
    'row_count': result.row_count,
    'columns': result.columns,
}
```

### 3. Sensor mode
```python
# 'reschedule': Worker 점유 안 함 (권장)
# 'poke': Worker 점유 (빠른 응답 필요 시)
mode='reschedule'
```

### 4. 에러 핸들링
```python
# 실패 시 Exception raise → Airflow가 retry 처리
if not result.success:
    raise Exception(f"Query failed: {result.error_message}")
```

---

## 6. 라이브러리 빌드 및 배포

### 빌드

```bash
cd project-interface-cli

# wheel 빌드
uv build

# 결과물 확인
ls dist/
# dli-0.1.0-py3-none-any.whl
# dli-0.1.0.tar.gz
```

### 로컬 테스트

```bash
# 가상환경에서 테스트
python -m venv test_env
source test_env/bin/activate

# 빌드한 wheel 설치
pip install dist/dli-0.1.0-py3-none-any.whl[airflow,bigquery]

# import 테스트
python -c "from dli.airflow import SQLFrameworkOperator; print('OK')"
```

### Airflow 환경에 배포

**방법 1: requirements.txt (간단)**
```txt
# airflow/requirements.txt
dataops-cli[airflow,bigquery] @ https://your-artifact-server/dli-0.1.0-py3-none-any.whl
```

**방법 2: Private PyPI (권장)**
```bash
# 내부 PyPI 서버에 업로드
twine upload --repository internal dist/*

# Airflow 환경에서 설치
pip install --extra-index-url https://pypi.internal.company.com/simple dataops-cli[airflow,bigquery]
```

**방법 3: Docker 이미지 빌드**
```dockerfile
# airflow/Dockerfile
FROM apache/airflow:2.7.0-python3.12

# 라이브러리 설치
COPY dist/dli-0.1.0-py3-none-any.whl /tmp/
RUN pip install /tmp/dli-0.1.0-py3-none-any.whl[airflow,bigquery]

# 쿼리 디렉토리 복사
COPY queries/ /opt/airflow/queries/
```

### Airflow DAG에서 사용

```python
# dags/my_dag.py
from dli.airflow import SQLFrameworkOperator

task = SQLFrameworkOperator(
    task_id='run_retention',
    query_name='daily_retention',
    params={'date': '{{ ds }}'},
    queries_dir='/opt/airflow/queries',
)
```

---

## Day 4 체크리스트

- [ ] pyproject.toml 선택적 의존성 추가
  - [ ] `[project.optional-dependencies]`
  - [ ] airflow, bigquery, snowflake, all
- [ ] airflow/operators.py
  - [ ] SQLFrameworkOperator
  - [ ] SQLFrameworkValidateOperator
- [ ] airflow/sensors.py
  - [ ] SQLFrameworkSensor
- [ ] airflow/__init__.py
- [ ] 라이브러리 빌드
  - [ ] `uv build`
  - [ ] wheel 로컬 테스트
- [ ] 테스트 코드
- [ ] 샘플 DAG 작성
- [ ] Airflow UI에서 동작 확인

---

## 참고 코드

| 참고 | URL |
|------|-----|
| Airflow Custom Operator | https://airflow.apache.org/docs/apache-airflow/stable/howto/custom-operator.html |
| Airflow Sensors | https://airflow.apache.org/docs/apache-airflow/stable/core-concepts/sensors.html |
| Airflow Best Practices | https://airflow.apache.org/docs/apache-airflow/stable/best-practices.html |
