# DLI CLI Development Patterns (Quick Reference)

> 상세 내용: `project-interface-cli/docs/PATTERNS.md`

## 1. CLI Command 핵심 패턴

```python
from dli.commands.base import ListOutputFormat, get_client, get_project_path
from dli.commands.utils import console, print_error, print_success, format_datetime

feature_app = typer.Typer(name="feature", help="...", no_args_is_help=True)

@feature_app.command("list")
def list_items(
    format_output: Annotated[ListOutputFormat, typer.Option("--format", "-f")] = "table",
    path: Annotated[Path | None, typer.Option("--path")] = None,
) -> None:
    project_path = get_project_path(path)
    client = get_client(project_path)
    response = client.feature_list()
    if not response.success:
        print_error(response.error or "Failed")
        raise typer.Exit(1)
    # Output...
```

## 2. Pydantic Model 핵심 패턴

```python
from pydantic import BaseModel, Field

__all__ = ["FeatureInfo", "FeatureStatus"]

class FeatureStatus(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"

class FeatureInfo(BaseModel):
    name: str = Field(..., description="Feature name")
    status: FeatureStatus = Field(default=FeatureStatus.ACTIVE)
```

## 3. Client Method 핵심 패턴

```python
def feature_list(self, *, filter: str | None = None) -> ServerResponse:
    if self.mock_mode:
        data = self._mock_data.get("features", [])
        return ServerResponse(success=True, data=data)
    return ServerResponse(success=False, error="Not implemented", status_code=501)
```

## 4. Library API 핵심 패턴 (NEW)

**API 클래스 (Facade Pattern):**
```python
from dli.models.common import ExecutionContext, ResultStatus, ValidationResult
from dli.exceptions import ConfigurationError, FeatureNotFoundError, ErrorCode

class FeatureAPI:
    def __init__(self, context: ExecutionContext | None = None) -> None:
        self.context = context or ExecutionContext()
        self._service: FeatureService | None = None  # Lazy init

    def _get_service(self) -> FeatureService:
        if self._service is None:
            if self.context.project_path is None and not self.context.mock_mode:
                raise ConfigurationError(message="project_path required", code=ErrorCode.CONFIG_INVALID)
            self._service = FeatureServiceImpl(project_path=self.context.project_path)
        return self._service

    @property
    def _is_mock_mode(self) -> bool:
        return self.context.execution_mode == ExecutionMode.MOCK

    def run(self, name: str, *, parameters: dict = None, dry_run: bool = False) -> FeatureResult:
        if self._is_mock_mode:
            return FeatureResult(name=name, status=ResultStatus.SUCCESS, ...)
        # Real execution...
```

**ExecutionMode (v0.2.1):**
```python
from dli import ExecutionContext, ExecutionMode

# ExecutionMode enum: LOCAL, SERVER, MOCK
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,  # or SERVER, MOCK
    project_path=Path("/opt/airflow/dags/models"),
    timeout=300,  # 1-3600 초
    dialect="trino",
    parameters={"execution_date": "2025-01-01"},
)

# Deprecated: mock_mode=True → execution_mode=ExecutionMode.MOCK
```

**DI (Dependency Injection):**
```python
from dli.core.executor import MockExecutor

mock_executor = MockExecutor(mock_data=[{"id": 1}])
api = DatasetAPI(context=ctx, executor=mock_executor)
```

**Exception Handling:**
```python
from dli.exceptions import DLIError, DatasetNotFoundError, ExecutionError

try:
    result = api.run("catalog.schema.dataset")
except DatasetNotFoundError as e:
    print(f"[{e.code.value}] {e.name}")  # Error code: DLI-101
except ExecutionError as e:
    print(f"Caused by: {e.cause}")
except DLIError as e:
    print(f"[{e.code.value}] {e.message}")
```

## 5. Test 핵심 패턴

**CLI Test:**
```python
from typer.testing import CliRunner
from dli.main import app
runner = CliRunner()

def test_list():
    result = runner.invoke(app, ["feature", "list"])
    assert result.exit_code == 0
```

**API Test:**
```python
from dli import FeatureAPI, ExecutionContext
from dli.models.common import ResultStatus

@pytest.fixture
def mock_api() -> FeatureAPI:
    return FeatureAPI(context=ExecutionContext(mock_mode=True))

def test_run_mock(mock_api):
    result = mock_api.run("my_feature")
    assert result.status == ResultStatus.SUCCESS
    assert result.duration_ms == 0
```

## 6. 공유 유틸리티 (commands/utils.py)

| 함수 | 용도 |
|------|------|
| `console` | Rich Console 인스턴스 |
| `print_error(msg)` | 에러 메시지 (빨강) |
| `print_success(msg)` | 성공 메시지 (초록) |
| `print_warning(msg)` | 경고 메시지 (노랑) |
| `format_datetime(dt)` | datetime 포맷팅 |
| `parse_params(["k=v"])` | CLI 파라미터 파싱 |

## 7. Error Code Reference

| Code | Category | Exception |
|------|----------|-----------|
| DLI-0xx | Configuration | `ConfigurationError` |
| DLI-1xx | Not Found | `DatasetNotFoundError`, `MetricNotFoundError` |
| DLI-2xx | Validation | `DLIValidationError` |
| DLI-3xx | Transpile | `TranspileError` |
| DLI-4xx | Execution | `ExecutionError` |
| DLI-5xx | Server | `ServerError` |
| DLI-6xx | Quality | `QualitySpecNotFoundError`, `QualityNotFoundError` |
| DLI-7xx | Catalog | `CatalogError`, `CatalogTableNotFoundError`, `InvalidIdentifierError` |

## 8. Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Subcommand app | `{feature}_app` | `catalog_app` |
| CLI command | kebab-case | `dli catalog list` |
| Python function | snake_case | `list_items` |
| Model class | PascalCase | `TableInfo` |
| API class | `{Feature}API` | `DatasetAPI`, `MetricAPI` |
| Test class | `Test{Feature}{Action}` | `TestDatasetAPIRun` |
