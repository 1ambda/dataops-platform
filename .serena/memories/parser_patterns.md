# Basecamp Parser Development Patterns (Quick Reference)

> Flask 3+ with SQLglot for Trino SQL parsing

## 1. SQLglot Parsing Pattern

```python
import sqlglot
from sqlglot import exp

def parse_sql(sql: str, dialect: str = "presto") -> ParseResult:
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
```

## 2. Flask API Pattern

```python
@app.route('/parse-sql', methods=['POST'])
def parse_sql():
    data = request.get_json()
    if not data or 'sql' not in data:
        return jsonify({"error": "Missing 'sql' field"}), 400
    try:
        result = parser_service.parse_sql_statement(data['sql'])
        return jsonify(result.to_dict())
    except SqlParsingError as e:
        return jsonify({"error": str(e)}), 400
```

## 3. Project Structure

```
project-basecamp-parser/
├── src/parser/
│   ├── config.py         # ParserConfig
│   ├── exceptions.py     # SqlParsingError
│   └── sql_parser.py     # SqlParserService
├── tests/
└── main.py               # Flask entry point
```

## 4. Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Service | `*Service` | `SqlParserService` |
| Data Class | `*Result` | `ParseResult` |
| Exception | `*Error` | `SqlParsingError` |
| API Routes | kebab-case | `/parse-sql` |

## 5. Essential Commands

```bash
uv sync                  # Install dependencies
uv run python main.py    # Run server (port 5000)
uv run pytest            # Run tests
uv run pyright src/      # Type check
```

## 6. SQL Dialect

SQLglot dialect for Trino is `"presto"`.
