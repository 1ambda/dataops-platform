---
name: feature-basecamp-parser
description: Feature development agent for project-basecamp-parser. Flask 3+ with SQLglot for Trino SQL parsing. Use PROACTIVELY when building SQL parsing features, parser endpoints, or working with SQLglot. Triggers on SQL parsing requests, parser API changes, and Trino SQL dialect work.
model: inherit
skills:
  - mcp-efficiency         # Read parser_patterns memory before file reads
  - pytest-fixtures        # Fixture design for SQL test cases
  - testing                # TDD workflow for parser edge cases
  - performance            # Parser efficiency for large SQL queries
  - completion-gate             # 완료 선언 Gate + 코드 존재 검증
  - implementation-checklist    # FEATURE → 체크리스트 자동 생성
  - gap-analysis                # FEATURE vs RELEASE 체계적 비교
  - phase-tracking              # 다단계 기능 관리 (Phase 1/2)
  - dependency-coordination     # 크로스 Agent 의존성 추적
  - docs-synchronize            # 문서 동기화 검증
  - integration-finder          # 기존 모듈 연동점 탐색
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

### CRITICAL: search_for_pattern Limits

> **WARNING: 잘못된 search_for_pattern 사용은 20k+ 토큰 응답 발생!**

```python
# BAD - 20k+ 토큰:
search_for_pattern(substring_pattern=r"def.*parse")

# GOOD - 제한된 응답:
search_for_pattern(
    substring_pattern=r"@app.route",
    relative_path="project-basecamp-parser/src/",
    context_lines_after=1,
    max_answer_chars=3000
)
```

**파일 검색은 find_file 사용:** `find_file(file_mask="*.py", relative_path="...")`

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

> **Protocol**: `completion-gate` skill 참조
> **Gate**: `completion-gate` skill 참조

### Project Commands

| Action | Command |
|--------|---------|
| Test | `uv run pytest` |
| Type Check | `uv run pyright src/` |
| Lint | `uv run ruff check --fix` |
| Run | `uv run python main.py` |

### Project Paths

| Category | Path |
|----------|------|
| Parser | `src/parser/sql_parser.py` |
| Config | `src/parser/config.py` |
| Exceptions | `src/parser/exceptions.py` |
| API | `main.py` |
| Tests | `tests/test_*.py` |

### Post-Implementation

```
□ Serena memory 업데이트 (parser_patterns)
□ README.md 변경사항 반영
```

---

## FEATURE → Implementation Workflow (CRITICAL)

> **Workflow**: `implementation-checklist` skill 참조
> **Gate**: `completion-gate` skill 참조

### 구현 순서

```
Config → Exception → Parser → Endpoint → Tests
```

### FEATURE 섹션별 검증

| FEATURE 섹션 | 필수 구현 | 검증 방법 |
|--------------|-----------|-----------|
| Parser Logic | `*Service` | `grep -r "class.*Service" src/parser/` |
| API Endpoints | `@app.route` | `grep -r "@app.route" main.py` |
| Exceptions | `*Error` | `grep -r "class.*Error" src/parser/exceptions.py` |
| Tests | 테스트 파일 | `ls tests/test_*.py` |

---

## MCP 활용 (Token Efficiency CRITICAL)

> **상세 가이드**: `mcp-efficiency` skill 참조

### MCP Query Anti-Patterns (AVOID)

```python
# BAD: Returns 10k+ tokens (entire parser bodies)
search_for_pattern("def parse.*", context_lines_after=20)

# BAD: Broad search without scope
search_for_pattern("import sqlglot", restrict_search_to_code_files=True)

# BAD: Reading files before understanding structure
Read("src/parser/sql_parser.py")  # 4000+ tokens wasted
```

### Token-Efficient Patterns (USE)

```python
# GOOD: List files first (~200 tokens)
list_dir("src/parser", recursive=False)

# GOOD: Get structure without bodies (~300 tokens)
get_symbols_overview("src/parser/sql_parser.py")

# GOOD: Signatures only (~400 tokens)
find_symbol("SqlParserService", depth=1, include_body=False)

# GOOD: Specific method body only when needed (~500 tokens)
find_symbol("SqlParserService/parse_sql_statement", include_body=True)

# GOOD: Minimal context for pattern search
search_for_pattern(
    "@app.route",
    context_lines_before=0,
    context_lines_after=1,
    relative_path="project-basecamp-parser/",
    max_answer_chars=3000
)
```

### Decision Tree

```
Need file list?       → list_dir()
Need class structure? → get_symbols_overview()
Need method list?     → find_symbol(depth=1, include_body=False)
Need implementation?  → find_symbol(include_body=True) for SPECIFIC method
Need to find pattern? → search_for_pattern with context=0
LAST RESORT          → Read() full file
```

### Quick Reference

| 도구 | 용도 |
|------|------|
| `serena.read_memory("parser_patterns")` | Parser 패턴 로드 |
| `serena.get_symbols_overview("src/parser/")` | 파서 구조 파악 |
| `serena.find_symbol("SqlParserService")` | 서비스 상세 조회 |
| `claude-mem.search("SQLglot")` | 과거 구현 참조 |
| `jetbrains.search_in_files_by_text("parse_sql")` | API 검색 |
