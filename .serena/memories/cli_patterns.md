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

## 4. Test 핵심 패턴

**CLI Test:**
```python
from typer.testing import CliRunner
from dli.main import app
runner = CliRunner()

def test_list():
    result = runner.invoke(app, ["feature", "list"])
    assert result.exit_code == 0
```

**Model Test:**
```python
def test_model_creation():
    info = FeatureInfo(name="test")
    assert info.name == "test"
```

## 5. 공유 유틸리티 (commands/utils.py)

| 함수 | 용도 |
|------|------|
| `console` | Rich Console 인스턴스 |
| `print_error(msg)` | 에러 메시지 (빨강) |
| `print_success(msg)` | 성공 메시지 (초록) |
| `print_warning(msg)` | 경고 메시지 (노랑) |
| `format_datetime(dt)` | datetime 포맷팅 |
| `parse_params(["k=v"])` | CLI 파라미터 파싱 |

## 6. Registration Checklist

1. `commands/__init__.py` → export 추가
2. `main.py` → `app.add_typer()` 등록
3. `main.py` docstring → Commands 목록 업데이트

## 7. Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Subcommand app | `{feature}_app` | `catalog_app` |
| CLI command | kebab-case | `dli catalog list` |
| Python function | snake_case | `list_items` |
| Model class | PascalCase | `TableInfo` |
| Test class | `Test{Feature}{Action}` | `TestCatalogList` |
