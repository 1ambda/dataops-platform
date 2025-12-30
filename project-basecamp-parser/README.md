# Project Basecamp Parser

A sidecar SQL parsing service for DataOps Platform using Flask and SQLglot.

## Quick Start

```bash
# Install dependencies
uv sync

# Run development server
uv run python main.py

# Run tests
uv run pytest

# Run with coverage
uv run pytest --cov=src --cov-report=html
```

## API Reference

### Health Check

```bash
curl http://localhost:5000/health
```

### Parse SQL

```bash
curl -X POST http://localhost:5000/parse-sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT col1 FROM schema.table1 WHERE col2 = '\''value'\''"}'
```

Response:
```json
{
  "statement_type": "SELECT",
  "tables": ["table1"],
  "columns": ["col1", "col2"],
  "schema_qualified_tables": ["schema.table1"],
  "parsed": true,
  "error": null
}
```

### Validate SQL

```bash
curl -X POST http://localhost:5000/validate-sql \
  -H "Content-Type: application/json" \
  -d '{"sql": "SELECT * FROM table1"}'
```

Response:
```json
{"valid": true}
```

## Project Structure

```
project-basecamp-parser/
├── src/parser/
│   ├── config.py          # Pydantic configuration
│   ├── exceptions.py      # Custom exceptions
│   ├── logging_config.py  # Logging setup
│   └── sql_parser.py      # TrinoSQLParser class
├── tests/
│   ├── conftest.py        # Pytest fixtures
│   ├── test_api.py        # API integration tests
│   └── test_sql_parser.py # Unit tests
├── docs/
│   └── PATTERNS.md        # Development patterns
└── main.py                # Flask application
```

## Development

### Code Quality

```bash
# Lint
uv run ruff check

# Format
uv run ruff format

# Type check
uv run pyright src/ main.py
```

### Adding a New Endpoint

```python
# 1. Define Pydantic models
class NewRequest(BaseModel):
    field: str

class NewResponse(BaseModel):
    result: str

# 2. Implement endpoint in create_app()
@app.route("/new-endpoint", methods=["POST"])
def new_endpoint() -> tuple[Response, int]:
    if not request.is_json:
        return jsonify(ErrorResponse(error="Content-Type must be application/json").model_dump()), 400

    try:
        data = request.get_json()
        request_data = NewRequest(**data)
        # ... business logic
        response = NewResponse(result="success")
        return jsonify(response.model_dump()), 200
    except ValidationError as e:
        return jsonify(ErrorResponse(error=f"Validation error: {e!s}").model_dump()), 400
```

### Adding a Parser Feature

```python
# In src/parser/sql_parser.py
def _extract_new_info(self, parsed: exp.Expression) -> set[str]:
    """Extract new information from parsed SQL."""
    results = set()
    for item in parsed.find_all(exp.SomeType):
        if item.name:
            results.add(item.name)
    return results
```

### Writing Tests

```python
# API Test (tests/test_api.py)
def test_new_endpoint(self, client):
    payload = {"field": "value"}
    response = client.post(
        "/new-endpoint",
        data=json.dumps(payload),
        content_type="application/json"
    )
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data["result"] == "success"

# Unit Test (tests/test_sql_parser.py)
def test_new_feature(self):
    sql = "SELECT * FROM table1"
    result = self.parser.parse_sql(sql)
    assert result["parsed"] is True
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PARSER_HOST` | `0.0.0.0` | Host to bind to |
| `PARSER_PORT` | `5000` | Port number |
| `PARSER_DEBUG` | `false` | Enable debug mode |
| `PARSER_LOG_LEVEL` | `INFO` | Logging level |

## Tech Stack

- **Python 3.12** - Runtime
- **Flask 3.1** - Web framework
- **SQLglot 28.5** - SQL parsing (Trino/Presto dialect)
- **Pydantic** - Request/response validation
- **UV** - Package manager
- **pytest** - Testing framework

## Detailed Patterns

For comprehensive development patterns including:
- Flask endpoint templates
- SQLglot parsing patterns
- Configuration management
- Exception handling
- Test patterns
- New feature checklists

See [docs/PATTERNS.md](./docs/PATTERNS.md).

---

**Last Updated:** 2025-12-30
