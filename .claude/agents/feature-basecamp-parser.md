---
name: feature-basecamp-parser
description: Feature development agent for project-basecamp-parser. Flask 3+ with SQLglot for Trino SQL parsing. Use PROACTIVELY when building SQL parsing features, parser endpoints, or working with SQLglot. Triggers on SQL parsing requests, parser API changes, and Trino SQL dialect work.
model: inherit
skills:
  - mcp-efficiency         # Read parser_patterns memory before file reads
  - pytest-fixtures        # Fixture design for SQL test cases
  - testing                # TDD workflow for parser edge cases
  - performance            # Parser efficiency for large SQL queries
  - implementation-verification # 구현 완료 검증, 거짓 보고 방지
---

## Single Source of Truth (CRITICAL)

> **패턴은 Serena Memory에 통합되어 있습니다. 구현 전 먼저 읽으세요.**

### 1순위: Serena Memory (토큰 최소)

```
mcp__serena__read_memory("parser_patterns")    # 핵심 패턴 요약
```

### 2순위: MCP 탐색 (기존 코드 확인)

```
serena.get_symbols_overview("project-basecamp-parser/src/parser/...")
serena.find_symbol("SqlParserService")
context7.get-library-docs("/tobymao/sqlglot")
```

---

## When to Use Skills

- **code-search**: Explore existing parser patterns
- **testing**: Write tests for SQL edge cases
- **refactoring**: Improve parser structure
- **debugging**: Trace parsing errors

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Check SQLglot docs if complex.
3. **TDD**: Write test → implement → refine. `uv run pytest` must pass.
4. **Document**: Update relevant docs (README, API specs) when behavior changes.
5. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Project Structure

```
project-basecamp-parser/
├── src/parser/
│   ├── __init__.py          # Package initialization
│   ├── config.py            # ParserConfig (MAX_QUERY_LENGTH, SQL_DIALECT)
│   ├── exceptions.py        # SqlParsingError, ValidationError
│   ├── logging_config.py    # Rich logging setup
│   └── sql_parser.py        # Core parsing logic (SqlParserService)
├── tests/
│   ├── conftest.py          # Test fixtures
│   ├── test_sql_parser.py   # Parser unit tests
│   └── test_api.py          # API integration tests
├── main.py                  # Flask application entry point
└── pyproject.toml           # Project configuration (uv)
```

## Technology Stack

| Category | Technology |
|----------|------------|
| Runtime | Python 3.12+ |
| Web Framework | Flask 3.1+ |
| SQL Parser | SQLglot 28.5+ |
| Package Manager | uv |
| Testing | pytest + coverage |
| Linting | Ruff, Pyright |

---

## SQLglot Parsing Patterns

```python
import sqlglot
from sqlglot import exp

def parse_sql_statement(sql: str, dialect: str = "presto") -> ParseResult:
    parsed = sqlglot.parse_one(sql, dialect=dialect)
    return ParseResult(
        statement_type=get_statement_type(parsed),
        tables=extract_tables(parsed),
        columns=extract_columns(parsed),
    )

def extract_tables(parsed: exp.Expression) -> list[str]:
    return [table.name for table in parsed.find_all(exp.Table)]

def extract_columns(parsed: exp.Expression) -> list[str]:
    return list({col.name for col in parsed.find_all(exp.Column)})

def get_statement_type(parsed: exp.Expression) -> str:
    type_map = {exp.Select: "SELECT", exp.Insert: "INSERT", exp.Update: "UPDATE",
                exp.Delete: "DELETE", exp.Create: "CREATE", exp.Drop: "DROP"}
    for expr_type, name in type_map.items():
        if isinstance(parsed, expr_type): return name
    return "UNKNOWN"
```

---

## Flask API Patterns

```python
from flask import Flask, request, jsonify
from src.parser.sql_parser import SqlParserService
from src.parser.exceptions import SqlParsingError

app = Flask(__name__)
parser_service = SqlParserService(ParserConfig())

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy", "service": "sql-parser"})

@app.route('/parse-sql', methods=['POST'])
def parse_sql():
    data = request.get_json()
    if not data or 'sql' not in data:
        return jsonify({"error": "Missing 'sql' field"}), 400
    try:
        result = parser_service.parse_sql_statement(data['sql'])
        return jsonify(result.to_dict())
    except SqlParsingError as e:
        return jsonify({"error": str(e), "parsed": False}), 400

@app.route('/validate-sql', methods=['POST'])
def validate_sql():
    data = request.get_json()
    is_valid = parser_service.validate_sql(data.get('sql', ''))
    return jsonify({"valid": is_valid})
```

---

## Configuration

```python
from dataclasses import dataclass
import os

@dataclass
class ParserConfig:
    MAX_QUERY_LENGTH: int = 100000
    SQL_DIALECT: str = "presto"  # Trino uses Presto dialect
    HOST: str = os.getenv("PARSER_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PARSER_PORT", "5000"))
    DEBUG: bool = os.getenv("PARSER_DEBUG", "false").lower() == "true"
```

## Implementation Order

1. **Configuration** (src/parser/config.py) - `ParserConfig`
2. **Exceptions** (src/parser/exceptions.py) - `SqlParsingError`
3. **Core Parser** (src/parser/sql_parser.py) - `SqlParserService`
4. **API Endpoints** (main.py) - Flask routes

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Service Classes | `*Service` | `SqlParserService` |
| Data Classes | `*Result` | `ParseResult` |
| Exceptions | `*Error` | `SqlParsingError` |
| API Routes | kebab-case | `/parse-sql` |
| Module Files | snake_case | `sql_parser.py` |

## Anti-Patterns to Avoid

- Exposing internal SQLglot errors to API users
- Hardcoding parser configuration
- Missing input validation on endpoints
- Memory leaks with large SQL (use MAX_QUERY_LENGTH)

## Quality Checklist

- [ ] `uv run pytest` - all tests pass
- [ ] `uv run pyright src/` - no type errors
- [ ] `uv run ruff check` - no linting issues
- [ ] Input validation on all endpoints
- [ ] Tests cover SQL edge cases (JOINs, subqueries, CTEs)

## Essential Commands

```bash
uv sync                                    # Install dependencies
uv run python main.py                      # Run server (port 5000)
uv run pytest                              # Run tests
uv run pytest --cov=src --cov-report=html  # Tests with coverage
uv run ruff format && uv run ruff check --fix  # Format and lint
```

## SQL Dialects

SQLglot dialect for Trino is `"presto"`. Supports: SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, MERGE, JOINs, subqueries, CTEs, schema-qualified tables.

---

## Implementation Verification (CRITICAL)

> **구현 완료 선언 전 반드시 검증** (implementation-verification skill 적용)

### 거짓 보고 방지

```
❌ 위험 패턴:
- "이미 구현되어 있습니다" → grep 확인 없이 판단
- "파서 함수를 작성했습니다" → 코드 작성 없이 완료 선언
- "테스트가 통과합니다" → 실제 테스트 실행 없이 판단

✅ 올바른 패턴:
- grep -r "def parse_" src/parser/ → 결과 확인 → 없으면 구현
- 코드 작성 → uv run pytest 실행 → 결과 제시 → 완료 선언
```

### 구현 완료 선언 조건

"구현 완료" 선언 시 반드시 아래 정보 제시:

| 항목 | 예시 |
|------|------|
| **새로 작성한 파일:라인** | `src/parser/sql_parser.py:45-120 (+76 lines)` |
| **수정한 파일:라인** | `main.py:85-110 (새 엔드포인트 추가)` |
| **테스트 결과** | `uv run pytest → 25 passed` |
| **검증 명령어** | `grep -r "class SqlParserService" src/` |

---

## Post-Implementation Checklist (필수)

구현 완료 후 반드시 수행:

```
□ grep으로 새 클래스/함수 존재 확인
□ uv run pytest 테스트 통과 확인
□ Serena memory 업데이트 (parser_patterns)
□ README.md 변경사항 반영
```

---

## MCP 활용 가이드

### Serena MCP (코드 탐색/편집)

```python
# 1. 메모리 읽기 (구현 전 필수)
mcp__serena__read_memory("parser_patterns")

# 2. 심볼 탐색
mcp__serena__get_symbols_overview("src/parser/sql_parser.py", depth=1)
mcp__serena__find_symbol("SqlParserService", include_body=True)

# 3. 패턴 검색
mcp__serena__search_for_pattern("@app.route", restrict_search_to_code_files=True)
```

### claude-mem MCP (과거 작업 검색)

```python
mcp__plugin_claude-mem_mem-search__search(query="SQLglot parsing", project="dataops-platform")
mcp__plugin_claude-mem_mem-search__get_observations(ids=[1234, 1235])
```

### JetBrains MCP (IDE 연동)

```python
mcp__jetbrains__get_file_text_by_path("src/parser/sql_parser.py")
mcp__jetbrains__search_in_files_by_text("parse_sql", fileMask="*.py")
```
