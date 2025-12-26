# Day 2: dli CLI 구현 가이드

## 핵심 개념

CLI는 `core/` 라이브러리를 호출하는 얇은 레이어입니다.

```
dli query run daily_retention -p date=2024-01-01
       │
       ▼
  dli.cli.query  (CLI layer - 파라미터 파싱, 출력 포맷)
       │
       ▼
  dli.core.service  (비즈니스 로직)
       │
       ▼
  dli.adapters.bigquery  (실행 엔진)
```

---

## 구현 순서 및 시간

| 순서 | 파일 | 시간 | 설명 |
|------|------|------|------|
| 1 | cli/main.py | 2h | Typer 앱 구조, 서비스 초기화 |
| 2 | cli/utils.py | 1h | 파라미터 파싱, 출력 헬퍼 |
| 3 | cli/commands/list.py | 1h | list queries, list params |
| 4 | cli/commands/validate.py | 1.5h | validate, dry-run |
| 5 | cli/commands/run.py | 2.5h | run + 출력 형식 |

---

## 디렉토리 구조

```
src/dli/cli/
├── __init__.py
├── query.py          # query 서브커맨드 (list, params, validate, dry-run, run)
└── utils.py          # 유틸리티 (파라미터 파싱, 출력 헬퍼)
```

> **Note**: 기존 `dli` CLI에 `query` 서브커맨드를 추가하는 방식입니다.

---

## 1. cli/main.py

### 기능
- Typer 앱 생성 및 서브커맨드 등록
- 전역 옵션 (queries-dir, project)
- 서비스 인스턴스 관리

### 코드
```python
import typer
from typing import Optional
from pathlib import Path
import os

from rich.console import Console

from ..core.service import SQLFrameworkService
from ..adapters.bigquery import BigQueryExecutor

app = typer.Typer(
    name="query",
    help="SQL Query Framework - 쿼리 관리 및 실행",
    add_completion=False,
)
console = Console()

# 전역 상태
class State:
    service: Optional[SQLFrameworkService] = None

state = State()


def get_service() -> SQLFrameworkService:
    """서비스 인스턴스 반환"""
    if state.service is None:
        raise typer.Exit("Service not initialized. Check --project option.")
    return state.service


@app.callback()
def main(
    queries_dir: Path = typer.Option(
        Path.cwd() / "queries",
        "--queries-dir", "-q",
        envvar="DLI_QUERIES_DIR",
        help="쿼리 정의 디렉토리",
    ),
    project: str = typer.Option(
        None,
        "--project", "-p",
        envvar="DLI_PROJECT",
        help="GCP 프로젝트 ID",
    ),
):
    """SQL Framework CLI"""
    if project is None:
        project = os.getenv("GOOGLE_CLOUD_PROJECT", "")
    
    if not project:
        console.print("[yellow]Warning: No project specified. Use --project or set SQLFW_PROJECT[/yellow]")
    
    try:
        executor = BigQueryExecutor(project=project) if project else None
        state.service = SQLFrameworkService(
            queries_dir=queries_dir,
            executor=executor,
            dialect="bigquery",
        )
    except Exception as e:
        console.print(f"[red]Failed to initialize: {e}[/red]")
        raise typer.Exit(1)


# 서브커맨드 등록
from .commands import list as list_cmd
from .commands import validate as validate_cmd
from .commands import run as run_cmd

app.add_typer(list_cmd.app, name="list", help="쿼리 목록 조회")
app.command(name="validate")(validate_cmd.validate)
app.command(name="dry-run")(validate_cmd.dry_run)
app.command(name="run")(run_cmd.run)


if __name__ == "__main__":
    app()
```

### 테스트
```python
# tests/cli/test_main.py
import pytest
from typer.testing import CliRunner
from sqlfw.cli.main import app

runner = CliRunner()

class TestCLIMain:
    def test_help(self):
        result = runner.invoke(app, ["--help"])
        assert result.exit_code == 0
        assert "SQL Framework" in result.stdout
    
    def test_no_project_warning(self, temp_queries_dir, monkeypatch):
        monkeypatch.delenv("SQLFW_PROJECT", raising=False)
        monkeypatch.delenv("GOOGLE_CLOUD_PROJECT", raising=False)
        
        result = runner.invoke(app, [
            "--queries-dir", str(temp_queries_dir),
            "list", "queries"
        ])
        # 경고는 나오지만 실행은 됨
        assert "Warning" in result.stdout or result.exit_code == 0
```

---

## 2. cli/utils.py

### 기능
- 파라미터 문자열 파싱 (key=value → dict)
- 결과 테이블 출력
- 에러 표시 헬퍼

### 코드
```python
from typing import Any
from rich.console import Console
from rich.table import Table
from rich.syntax import Syntax
from rich.panel import Panel

console = Console()


def parse_params(params: list[str]) -> dict[str, Any]:
    """
    CLI 파라미터 파싱
    
    Examples:
        -p start_date=2024-01-01
        -p count=100
        -p tags=a,b,c  (리스트)
    """
    result = {}
    
    for param in params:
        if "=" not in param:
            raise ValueError(f"Invalid format: '{param}'. Use key=value")
        
        key, value = param.split("=", 1)
        key, value = key.strip(), value.strip()
        
        # 타입 추론
        if "," in value:
            result[key] = [v.strip() for v in value.split(",")]
        elif value.isdigit():
            result[key] = int(value)
        elif value.replace(".", "", 1).isdigit():
            result[key] = float(value)
        elif value.lower() in ("true", "false"):
            result[key] = value.lower() == "true"
        else:
            result[key] = value
    
    return result


def print_error(message: str) -> None:
    """에러 메시지 출력"""
    console.print(f"[red]✗ Error: {message}[/red]")


def print_success(message: str) -> None:
    """성공 메시지 출력"""
    console.print(f"[green]✓ {message}[/green]")


def print_warning(message: str) -> None:
    """경고 메시지 출력"""
    console.print(f"[yellow]⚠ {message}[/yellow]")


def print_sql(sql: str, title: str = "Rendered SQL") -> None:
    """SQL 구문 강조 출력"""
    syntax = Syntax(sql, "sql", theme="monokai", line_numbers=True)
    console.print(Panel(syntax, title=title, border_style="blue"))


def print_data_table(columns: list[str], data: list[dict], title: str = "Results") -> None:
    """데이터 테이블 출력"""
    table = Table(title=title, show_header=True, header_style="bold cyan")
    
    for col in columns:
        table.add_column(col)
    
    for row in data:
        table.add_row(*[str(row.get(col, "")) for col in columns])
    
    console.print(table)


def print_validation_result(is_valid: bool, errors: list[str], warnings: list[str]) -> None:
    """검증 결과 출력"""
    if is_valid:
        print_success("Validation passed")
    else:
        print_error("Validation failed")
        for e in errors:
            console.print(f"  • {e}")
    
    if warnings:
        console.print("\n[yellow]Warnings:[/yellow]")
        for w in warnings:
            console.print(f"  • {w}")
```

### 테스트
```python
# tests/cli/test_utils.py
import pytest
from sqlfw.cli.utils import parse_params

class TestParseParams:
    def test_simple_string(self):
        result = parse_params(["name=hello"])
        assert result == {"name": "hello"}
    
    def test_integer(self):
        result = parse_params(["count=100"])
        assert result == {"count": 100}
    
    def test_float(self):
        result = parse_params(["rate=0.5"])
        assert result == {"rate": 0.5}
    
    def test_boolean(self):
        result = parse_params(["active=true"])
        assert result == {"active": True}
    
    def test_list(self):
        result = parse_params(["tags=a,b,c"])
        assert result == {"tags": ["a", "b", "c"]}
    
    def test_multiple_params(self):
        result = parse_params(["start=2024-01-01", "limit=10"])
        assert result == {"start": "2024-01-01", "limit": 10}
    
    def test_invalid_format(self):
        with pytest.raises(ValueError):
            parse_params(["invalid"])
    
    def test_date_string(self):
        result = parse_params(["date=2024-01-01"])
        assert result == {"date": "2024-01-01"}
```

---

## 3. cli/commands/list.py

### 기능
- `dli query list` - 쿼리 목록 조회
- `dli query params <query_name>` - 파라미터 목록 조회

### 코드
```python
import typer
from typing import Optional
import json

from rich.console import Console
from rich.table import Table

from ...cli.main import get_service

app = typer.Typer()
console = Console()


@app.command("queries")
def list_queries(
    tag: Optional[str] = typer.Option(None, "--tag", "-t", help="태그로 필터링"),
    owner: Optional[str] = typer.Option(None, "--owner", "-o", help="소유자로 필터링"),
    format: str = typer.Option("table", "--format", "-f", help="출력 형식 (table/json)"),
):
    """등록된 쿼리 목록 조회"""
    service = get_service()
    queries = service.list_queries(tag=tag, owner=owner)
    
    if not queries:
        console.print("[yellow]No queries found[/yellow]")
        return
    
    if format == "json":
        data = [q.model_dump() for q in queries]
        console.print_json(json.dumps(data, default=str))
        return
    
    # 테이블 출력
    table = Table(title=f"📋 Queries ({len(queries)})", show_header=True)
    table.add_column("Name", style="cyan", no_wrap=True)
    table.add_column("Description", style="white", max_width=40)
    table.add_column("Owner", style="green")
    table.add_column("Tags", style="yellow")
    table.add_column("Params", style="magenta", justify="center")
    
    for q in queries:
        required = len([p for p in q.parameters if p.required])
        total = len(q.parameters)
        
        table.add_row(
            q.name,
            (q.description[:37] + "...") if len(q.description) > 40 else q.description,
            q.owner or "-",
            ", ".join(q.tags[:3]) + ("..." if len(q.tags) > 3 else "") or "-",
            f"{required}/{total}",
        )
    
    console.print(table)


@app.command("params")
def list_params(
    query_name: str = typer.Argument(..., help="쿼리 이름"),
):
    """쿼리의 파라미터 목록 조회"""
    service = get_service()
    query = service.get_query(query_name)
    
    if not query:
        console.print(f"[red]Query '{query_name}' not found[/red]")
        raise typer.Exit(1)
    
    if not query.parameters:
        console.print(f"[yellow]Query '{query_name}' has no parameters[/yellow]")
        return
    
    table = Table(title=f"📝 Parameters: {query_name}", show_header=True)
    table.add_column("Name", style="cyan")
    table.add_column("Type", style="yellow")
    table.add_column("Required", style="red", justify="center")
    table.add_column("Default", style="green")
    table.add_column("Description", style="white")
    
    for p in query.parameters:
        table.add_row(
            p.name,
            p.type.value,
            "✓" if p.required else "",
            str(p.default) if p.default is not None else "-",
            p.description or "-",
        )
    
    console.print(table)
```

### 테스트
```python
# tests/cli/test_list.py
import pytest
from typer.testing import CliRunner
from sqlfw.cli.main import app

runner = CliRunner()

class TestListCommands:
    def test_list_queries(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["list", "queries"])
        
        assert result.exit_code == 0
        assert "test_query" in result.stdout
    
    def test_list_queries_json(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["list", "queries", "--format", "json"])
        
        assert result.exit_code == 0
        assert '"name"' in result.stdout
    
    def test_list_queries_with_tag(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["list", "queries", "--tag", "test"])
        
        assert result.exit_code == 0
    
    def test_list_params(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["list", "params", "test_query"])
        
        assert result.exit_code == 0
        assert "date" in result.stdout
    
    def test_list_params_not_found(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["list", "params", "nonexistent"])
        
        assert result.exit_code == 1
        assert "not found" in result.stdout
```

---

## 4. cli/commands/validate.py

### 기능
- `dli query validate <query> -p key=value` - 쿼리 검증
- `dli query dry-run <query> -p key=value` - Dry-run (비용 추정)

### 코드
```python
import typer
from rich.console import Console

from ...cli.main import get_service
from ...cli.utils import parse_params, print_sql, print_validation_result, print_error

console = Console()


def validate(
    query_name: str = typer.Argument(..., help="쿼리 이름"),
    params: list[str] = typer.Option([], "--param", "-p", help="파라미터 (key=value)"),
    show_sql: bool = typer.Option(True, "--show-sql/--no-sql", help="렌더링된 SQL 표시"),
):
    """쿼리 검증 (렌더링 + SQL 문법 검사)"""
    service = get_service()
    
    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)
    
    with console.status("[bold green]Validating..."):
        result = service.validate(query_name, param_dict)
    
    print_validation_result(result.is_valid, result.errors, result.warnings)
    
    if show_sql and result.rendered_sql:
        console.print()
        print_sql(result.rendered_sql)
    
    if not result.is_valid:
        raise typer.Exit(1)


def dry_run(
    query_name: str = typer.Argument(..., help="쿼리 이름"),
    params: list[str] = typer.Option([], "--param", "-p", help="파라미터 (key=value)"),
    show_sql: bool = typer.Option(False, "--show-sql", help="렌더링된 SQL 표시"),
):
    """Dry-run 실행 (비용 추정)"""
    service = get_service()
    
    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)
    
    with console.status("[bold green]Running dry-run..."):
        result = service.dry_run(query_name, param_dict)
    
    if not result.get("valid"):
        print_error(result.get("error", "Dry-run failed"))
        if result.get("errors"):
            for e in result["errors"]:
                console.print(f"  • {e}")
        raise typer.Exit(1)
    
    # 결과 출력
    bytes_gb = result.get("bytes_processed_gb", 0)
    cost = result.get("estimated_cost_usd", 0)
    
    console.print(f"\n[green]✓ Dry-run successful[/green]")
    console.print(f"  📊 Estimated scan: [cyan]{bytes_gb:.2f} GB[/cyan]")
    console.print(f"  💰 Estimated cost: [yellow]${cost:.4f}[/yellow]")
    
    if show_sql and result.get("rendered_sql"):
        console.print()
        print_sql(result["rendered_sql"])
```

### 테스트
```python
# tests/cli/test_validate.py
import pytest
from typer.testing import CliRunner
from unittest.mock import patch, Mock
from sqlfw.cli.main import app

runner = CliRunner()

class TestValidateCommand:
    def test_validate_success(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "validate", "test_query",
            "-p", "date=2024-01-01"
        ])
        
        assert result.exit_code == 0
        assert "passed" in result.stdout.lower() or "✓" in result.stdout
    
    def test_validate_missing_param(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, ["validate", "test_query"])
        
        assert result.exit_code == 1
    
    def test_validate_invalid_param_format(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "validate", "test_query",
            "-p", "invalid"
        ])
        
        assert result.exit_code == 1
        assert "Invalid" in result.stdout
    
    def test_validate_query_not_found(self, temp_queries_dir, monkeypatch):
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "validate", "nonexistent",
            "-p", "date=2024-01-01"
        ])
        
        assert result.exit_code == 1


class TestDryRunCommand:
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_dry_run_success(self, mock_executor, temp_queries_dir, monkeypatch):
        mock_executor.return_value.dry_run.return_value = {
            "valid": True,
            "bytes_processed": 1000000000,
            "bytes_processed_gb": 1.0,
            "estimated_cost_usd": 0.005,
        }
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "dry-run", "test_query",
            "-p", "date=2024-01-01"
        ])
        
        assert "GB" in result.stdout or "cost" in result.stdout.lower()
```

---

## 5. cli/commands/run.py

### 기능
- `sqlfw run <query> -p key=value` - 쿼리 실행
- 출력 형식: table, json, csv
- 결과 행 수 제한

### 코드
```python
import typer
import json
import csv
import sys
from typing import Optional

from rich.console import Console
from rich.progress import Progress, SpinnerColumn, TextColumn

from ...cli.main import get_service
from ...cli.utils import (
    parse_params, 
    print_error, 
    print_success, 
    print_sql, 
    print_data_table
)

console = Console()


def run(
    query_name: str = typer.Argument(..., help="쿼리 이름"),
    params: list[str] = typer.Option([], "--param", "-p", help="파라미터 (key=value)"),
    output: str = typer.Option("table", "--output", "-o", help="출력 형식 (table/json/csv)"),
    limit: Optional[int] = typer.Option(None, "--limit", "-l", help="출력 행 수 제한"),
    no_dry_run: bool = typer.Option(False, "--no-dry-run", help="Dry-run 건너뛰기"),
    show_sql: bool = typer.Option(False, "--show-sql", help="실행된 SQL 표시"),
):
    """쿼리 실행"""
    service = get_service()
    
    # 파라미터 파싱
    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)
    
    # 실행
    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console,
        transient=True,
    ) as progress:
        progress.add_task("Executing query...", total=None)
        
        result = service.execute(
            query_name,
            param_dict,
            dry_run_first=not no_dry_run,
        )
    
    # 에러 처리
    if not result.success:
        print_error(result.error_message)
        raise typer.Exit(1)
    
    # 성공 메시지
    print_success(f"Query executed: {result.row_count} rows in {result.execution_time_ms}ms")
    
    # SQL 표시
    if show_sql:
        console.print()
        print_sql(result.rendered_sql)
    
    # 데이터 없으면 종료
    if not result.data:
        console.print("[yellow]No data returned[/yellow]")
        return
    
    # 데이터 제한
    data = result.data
    if limit:
        data = data[:limit]
    
    console.print()
    
    # 출력 형식별 처리
    if output == "json":
        console.print_json(json.dumps(data, default=str, indent=2))
    
    elif output == "csv":
        writer = csv.DictWriter(
            sys.stdout, 
            fieldnames=result.columns,
            extrasaction='ignore'
        )
        writer.writeheader()
        writer.writerows(data)
    
    else:  # table
        print_data_table(
            result.columns, 
            data, 
            title=f"Results ({len(data)}{'+' if limit and len(result.data) > limit else ''} rows)"
        )
        
        if limit and len(result.data) > limit:
            console.print(f"[dim]Showing {limit} of {result.row_count} rows. Use --limit to see more.[/dim]")
```

### 테스트
```python
# tests/cli/test_run.py
import pytest
from typer.testing import CliRunner
from unittest.mock import patch, Mock
from sqlfw.cli.main import app
from sqlfw.core.models import ExecutionResult

runner = CliRunner()

class TestRunCommand:
    @pytest.fixture
    def mock_result(self):
        return ExecutionResult(
            query_name="test_query",
            success=True,
            row_count=3,
            columns=["id", "name", "value"],
            data=[
                {"id": 1, "name": "Alice", "value": 100},
                {"id": 2, "name": "Bob", "value": 200},
                {"id": 3, "name": "Charlie", "value": 300},
            ],
            rendered_sql="SELECT * FROM test",
            execution_time_ms=150,
        )
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_table_output(self, mock_executor, mock_result, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = mock_result
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01"
        ])
        
        assert result.exit_code == 0
        assert "3 rows" in result.stdout
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_json_output(self, mock_executor, mock_result, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = mock_result
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01",
            "-o", "json"
        ])
        
        assert result.exit_code == 0
        assert '"id"' in result.stdout
        assert '"name"' in result.stdout
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_csv_output(self, mock_executor, mock_result, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = mock_result
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01",
            "-o", "csv"
        ])
        
        assert result.exit_code == 0
        assert "id,name,value" in result.stdout
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_with_limit(self, mock_executor, mock_result, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = mock_result
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01",
            "--limit", "1"
        ])
        
        assert result.exit_code == 0
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_failure(self, mock_executor, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = ExecutionResult(
            query_name="test_query",
            success=False,
            error_message="Query failed",
        )
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01"
        ])
        
        assert result.exit_code == 1
        assert "failed" in result.stdout.lower()
    
    @patch('sqlfw.adapters.bigquery.BigQueryExecutor')
    def test_run_show_sql(self, mock_executor, mock_result, temp_queries_dir, monkeypatch):
        mock_executor.return_value.execute.return_value = mock_result
        mock_executor.return_value.dry_run.return_value = {"valid": True}
        
        monkeypatch.setenv("SQLFW_QUERIES_DIR", str(temp_queries_dir))
        monkeypatch.setenv("SQLFW_PROJECT", "test-project")
        
        result = runner.invoke(app, [
            "run", "test_query",
            "-p", "date=2024-01-01",
            "--show-sql"
        ])
        
        assert result.exit_code == 0
        assert "SELECT" in result.stdout
```

---

## CLI 사용 예시

```bash
# 쿼리 목록
$ dli query list
$ dli query list --tag marketing
$ dli query list --format json

# 파라미터 확인
$ dli query params daily_retention

# 검증
$ dli query validate daily_retention -p start_date=2024-01-01 -p end_date=2024-01-31
$ dli query validate daily_retention -p start_date=2024-01-01 --no-sql

# Dry-run
$ dli query dry-run daily_retention -p start_date=2024-01-01 -p end_date=2024-01-31

# 실행
$ dli query run daily_retention -p start_date=2024-01-01 -p end_date=2024-01-31
$ dli query run daily_retention -p start_date=2024-01-01 -o json
$ dli query run daily_retention -p start_date=2024-01-01 -o csv > result.csv
$ dli query run daily_retention -p start_date=2024-01-01 --limit 10
$ dli query run daily_retention -p start_date=2024-01-01 --show-sql
```

---

## conftest.py (공통 Fixture)

```python
# tests/conftest.py
import pytest
import tempfile
from pathlib import Path
import yaml

@pytest.fixture
def temp_queries_dir():
    """테스트용 임시 쿼리 디렉토리"""
    with tempfile.TemporaryDirectory() as tmpdir:
        queries_dir = Path(tmpdir)
        
        # _schema.yml
        schema = {
            "queries": [{
                "name": "test_query",
                "description": "Test query",
                "sql_file": "test.sql",
                "parameters": [
                    {"name": "date", "type": "date", "required": True}
                ],
                "tags": ["test"],
                "owner": "test-team",
            }]
        }
        (queries_dir / "_schema.yml").write_text(yaml.dump(schema))
        
        # test.sql
        sql = "SELECT * FROM test_table WHERE dt = '{{ date }}'"
        (queries_dir / "test.sql").write_text(sql)
        
        yield queries_dir
```

---

## Day 2 체크리스트

- [ ] cli/main.py + 테스트
- [ ] cli/utils.py + 테스트
- [ ] cli/commands/list.py + 테스트
- [ ] cli/commands/validate.py + 테스트
- [ ] cli/commands/run.py + 테스트
- [ ] 전체 CLI 통합 테스트
- [ ] --help 문서 확인

---

## 참고 코드

| 참고 | URL |
|------|-----|
| dbt CLI 구조 | https://github.com/dbt-labs/dbt-core/tree/main/core/dbt/cli |
| Typer 문서 | https://typer.tiangolo.com/ |
| Rich 문서 | https://rich.readthedocs.io/ |
