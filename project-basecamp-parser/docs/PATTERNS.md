# Flask + SQLglot Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.

---

## Quick Reference

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| Flask endpoint | `main.py` | `@app.route` + Pydantic models |
| SQL parsing | `src/parser/sql_parser.py` | `TrinoSQLParser.parse_sql()` |
| Config management | `src/parser/config.py` | Pydantic BaseModel + `from_env()` |
| Custom exception | `src/parser/exceptions.py` | Exception with context attributes |
| API test | `tests/test_api.py` | Flask test client + fixtures |
| Unit test | `tests/test_sql_parser.py` | pytest class with `setup_method` |

---

## 1. Flask Endpoint Pattern

### Request/Response Models (Pydantic)

```python
from pydantic import BaseModel

class SQLParseRequest(BaseModel):
    """Request model for SQL parsing."""
    sql: str

class SQLParseResponse(BaseModel):
    """Response model for SQL parsing."""
    statement_type: str | None
    tables: list[str]
    columns: list[str]
    schema_qualified_tables: list[str]
    parsed: bool
    error: str | None

class ErrorResponse(BaseModel):
    """Response model for errors."""
    error: str
```

### Endpoint Implementation

```python
@app.route("/parse-sql", methods=["POST"])
def parse_sql() -> tuple[Response, int]:
    """Parse SQL statement endpoint."""
    logger.debug("Parse SQL request received")

    # 1. Validate content type
    if not request.is_json:
        logger.warning("Invalid content type")
        error_response = ErrorResponse(error="Content-Type must be application/json")
        return jsonify(error_response.model_dump()), 400

    try:
        # 2. Parse JSON payload
        data = request.get_json()
        if data is None:
            error_response = ErrorResponse(error="Invalid JSON payload")
            return jsonify(error_response.model_dump()), 400

        # 3. Validate with Pydantic
        request_data = SQLParseRequest(**data)

        # 4. Business logic
        result = parser.parse_sql(request_data.sql.strip())

        # 5. Return response
        response = SQLParseResponse(**result)
        status_code = 200 if response.parsed else 400
        return jsonify(response.model_dump()), status_code

    except ValidationError as e:
        error_response = ErrorResponse(error=f"Validation error: {e!s}")
        return jsonify(error_response.model_dump()), 400
    except ValueError as e:
        error_response = ErrorResponse(error=str(e))
        return jsonify(error_response.model_dump()), 400
    except Exception as e:
        logger.error(f"Unexpected error: {e}", exc_info=True)
        error_response = ErrorResponse(error="Internal server error")
        return jsonify(error_response.model_dump()), 500
```

### Error Handler Pattern

```python
@app.errorhandler(404)
def not_found(error: Any) -> tuple[Response, int]:
    logger.warning(f"404 error: {request.url}")
    error_response = ErrorResponse(error="Endpoint not found")
    return jsonify(error_response.model_dump()), 404

@app.errorhandler(500)
def internal_error(error: Any) -> tuple[Response, int]:
    logger.error(f"500 error: {error}", exc_info=True)
    error_response = ErrorResponse(error="Internal server error")
    return jsonify(error_response.model_dump()), 500
```

---

## 2. SQLglot Parsing Pattern

### Parser Class Structure

```python
import sqlglot
from sqlglot import exp
from typing import Any, Final

class TrinoSQLParser:
    """Parser for Trino SQL statements using SQLglot."""

    def __init__(self) -> None:
        self.config = get_parser_config()
        self.logger = get_logger(__name__)
        self.dialect: Final[str] = self.config.dialect  # "presto" for Trino

    def parse_sql(self, sql: str) -> dict[str, Any]:
        """Parse SQL and extract metadata."""
        # 1. Input validation
        if not sql or not sql.strip():
            raise ValueError("SQL cannot be empty")

        if len(sql) > self.config.max_query_length:
            raise ValueError(f"SQL exceeds max length of {self.config.max_query_length}")

        try:
            # 2. Parse with SQLglot
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)

            if not parsed:
                return self._error_result("Failed to parse SQL")

            # 3. Extract information
            statement_type = self._get_statement_type(parsed)
            tables = self._extract_tables(parsed)
            columns = self._extract_columns(parsed)

            return {
                "statement_type": statement_type,
                "tables": sorted(list(tables)),
                "columns": sorted(list(columns)),
                "parsed": True,
                "error": None,
            }

        except sqlglot.ParseError as e:
            return self._error_result(f"SQL syntax error: {e!s}")
```

### Statement Type Detection

```python
def _get_statement_type(self, parsed: exp.Expression) -> str:
    """Determine SQL statement type."""
    statement_type_mapping: dict[type[exp.Expression], str] = {
        exp.Select: "SELECT",
        exp.Insert: "INSERT",
        exp.Update: "UPDATE",
        exp.Delete: "DELETE",
        exp.Create: "CREATE",
        exp.Drop: "DROP",
        exp.Alter: "ALTER",
        exp.Merge: "MERGE",
    }

    for expr_type, statement_type in statement_type_mapping.items():
        if isinstance(parsed, expr_type):
            return statement_type

    return "UNKNOWN"
```

### Table/Column Extraction

```python
def _extract_tables(self, parsed: exp.Expression) -> set[str]:
    """Extract table names from parsed SQL."""
    tables = set()
    for table in parsed.find_all(exp.Table):
        if table.name:
            tables.add(table.name)
    return tables

def _extract_columns(self, parsed: exp.Expression) -> set[str]:
    """Extract column names from parsed SQL."""
    columns = set()
    for column in parsed.find_all(exp.Column):
        if column.name and column.name != "*":
            columns.add(column.name)
    return columns

def _extract_schema_qualified_tables(self, parsed: exp.Expression) -> set[str]:
    """Extract schema-qualified table names (schema.table)."""
    qualified_tables = set()
    for table in parsed.find_all(exp.Table):
        if table.catalog and table.db and table.name:
            qualified_tables.add(f"{table.catalog}.{table.db}.{table.name}")
        elif table.db and table.name:
            qualified_tables.add(f"{table.db}.{table.name}")
    return qualified_tables
```

---

## 3. Configuration Pattern

### Config Class with Pydantic

```python
from pydantic import BaseModel, Field
import os

class ServerConfig(BaseModel):
    """Server configuration settings."""
    host: str = Field(default="0.0.0.0", description="Host to bind to")
    port: int = Field(default=5000, description="Port to bind to")
    debug: bool = Field(default=False, description="Enable debug mode")
    log_level: str = Field(default="INFO", description="Logging level")

    @classmethod
    def from_env(cls) -> "ServerConfig":
        """Create configuration from environment variables."""
        return cls(
            host=os.getenv("PARSER_HOST", "0.0.0.0"),
            port=int(os.getenv("PARSER_PORT", "5000")),
            debug=os.getenv("PARSER_DEBUG", "false").lower() == "true",
            log_level=os.getenv("PARSER_LOG_LEVEL", "INFO").upper(),
        )

class SQLParserConfig(BaseModel):
    """SQL parser configuration."""
    dialect: str = Field(default="presto", description="SQL dialect")
    max_query_length: int = Field(default=100000, description="Max query length")
    timeout_seconds: int = Field(default=30, description="Parsing timeout")

# Global configuration instances (singleton pattern)
from typing import Final
SERVER_CONFIG: Final[ServerConfig] = ServerConfig.from_env()
PARSER_CONFIG: Final[SQLParserConfig] = SQLParserConfig()

def get_server_config() -> ServerConfig:
    return SERVER_CONFIG

def get_parser_config() -> SQLParserConfig:
    return PARSER_CONFIG
```

---

## 4. Exception Pattern

### Custom Exception with Context

```python
class SQLParseError(Exception):
    """Raised when SQL parsing fails."""

    def __init__(self, message: str, sql: str | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.sql = sql

    def __str__(self) -> str:
        if self.sql:
            return f"{self.message} (SQL: {self.sql[:50]}...)"
        return self.message

class SQLValidationError(Exception):
    """Raised when SQL validation fails."""

    def __init__(self, message: str, sql: str | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.sql = sql

class ConfigurationError(Exception):
    """Raised for configuration issues."""
    pass
```

---

## 5. Test Patterns

### Test Fixtures (conftest.py)

```python
import pytest
from unittest.mock import patch
import os
import logging

from main import create_app

@pytest.fixture(autouse=True)
def setup_test_env():
    """Set up test environment variables."""
    with patch.dict(os.environ, {
        "PARSER_DEBUG": "false",
        "PARSER_LOG_LEVEL": "WARNING",  # Reduce log noise
    }):
        yield

@pytest.fixture
def app():
    """Create test instance of the Flask app."""
    logging.getLogger().setLevel(logging.CRITICAL)

    app = create_app()
    app.config.update({"TESTING": True})
    return app

@pytest.fixture
def client(app):
    """Flask test client."""
    return app.test_client()
```

### API Test Pattern

```python
import json

class TestAPIEndpoints:
    """Test cases for API endpoints."""

    def test_health_endpoint(self, client):
        """Test health check endpoint."""
        response = client.get("/health")
        assert response.status_code == 200

        data = json.loads(response.data)
        assert data["status"] == "healthy"
        assert data["service"] == "sql-parser"

    def test_parse_sql_valid_select(self, client):
        """Test parse-sql with valid SELECT."""
        payload = {"sql": "SELECT col1, col2 FROM schema.table1"}
        response = client.post(
            "/parse-sql",
            data=json.dumps(payload),
            content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)

        assert data["parsed"] is True
        assert data["error"] is None
        assert data["statement_type"] == "SELECT"
        assert "table1" in data["tables"]

    def test_parse_sql_invalid(self, client):
        """Test parse-sql with invalid SQL."""
        payload = {"sql": "INVALID SQL"}
        response = client.post(
            "/parse-sql",
            data=json.dumps(payload),
            content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert data["parsed"] is False
        assert data["error"] is not None

    def test_parse_sql_wrong_content_type(self, client):
        """Test parse-sql with wrong content type."""
        response = client.post(
            "/parse-sql",
            data="plain text",
            content_type="text/plain"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "application/json" in data["error"]
```

### Unit Test Pattern (SQL Parser)

```python
import pytest
from src.parser.sql_parser import TrinoSQLParser

class TestTrinoSQLParser:
    """Test cases for TrinoSQLParser."""

    def setup_method(self):
        """Set up test fixtures."""
        self.parser = TrinoSQLParser()

    def test_parse_simple_select(self):
        """Test parsing simple SELECT."""
        sql = "SELECT col1, col2 FROM table1"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["error"] is None
        assert result["statement_type"] == "SELECT"
        assert "table1" in result["tables"]
        assert "col1" in result["columns"]

    def test_parse_with_schema(self):
        """Test parsing with schema-qualified table."""
        sql = "SELECT col1 FROM schema1.table1"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert "schema1.table1" in result["schema_qualified_tables"]

    def test_parse_join_query(self):
        """Test parsing query with JOINs."""
        sql = """
        SELECT t1.col1, t2.col2
        FROM schema1.table1 t1
        JOIN schema2.table2 t2 ON t1.id = t2.id
        """
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert "table1" in result["tables"]
        assert "table2" in result["tables"]

    def test_parse_empty_sql(self):
        """Test parsing empty SQL raises ValueError."""
        with pytest.raises(ValueError, match="SQL cannot be empty"):
            self.parser.parse_sql("")

    def test_validate_valid_sql(self):
        """Test SQL validation with valid SQL."""
        assert self.parser.validate_sql("SELECT * FROM table1") is True

    def test_validate_invalid_sql(self):
        """Test SQL validation with invalid SQL."""
        assert self.parser.validate_sql("INVALID SQL") is False
```

---

## 6. Logging Pattern

### Logger Setup

```python
import logging
import sys

def setup_logging() -> None:
    """Set up logging configuration."""
    config = get_server_config()

    formatter = logging.Formatter(
        fmt="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, config.log_level))
    root_logger.addHandler(console_handler)

def get_logger(name: str) -> logging.Logger:
    """Get a logger with the specified name."""
    return logging.getLogger(name)
```

### Logger Usage

```python
class TrinoSQLParser:
    def __init__(self) -> None:
        self.logger = get_logger(__name__)
        self.logger.info("Initialized TrinoSQLParser")

    def parse_sql(self, sql: str) -> dict[str, Any]:
        self.logger.debug(f"Parsing SQL of length {len(sql)}")

        try:
            # ... parsing logic
            self.logger.info(f"Successfully parsed {statement_type} statement")
        except sqlglot.ParseError as e:
            self.logger.error(f"SQL syntax error: {e}")
```

---

## 7. New Feature Checklist

### Adding a New Endpoint

- [ ] Define request model (`class NewRequest(BaseModel)`)
- [ ] Define response model (`class NewResponse(BaseModel)`)
- [ ] Implement endpoint function with `@app.route`
- [ ] Add content-type validation
- [ ] Add Pydantic validation
- [ ] Handle exceptions (ValidationError, ValueError, generic Exception)
- [ ] Add logging at appropriate levels
- [ ] Write API test in `tests/test_api.py`

### Adding a New Parser Feature

- [ ] Add method to `TrinoSQLParser` class
- [ ] Use appropriate SQLglot expression types (`exp.Table`, `exp.Column`, etc.)
- [ ] Handle edge cases (empty results, None values)
- [ ] Add input validation with appropriate error messages
- [ ] Write unit tests in `tests/test_sql_parser.py`
- [ ] Test with various SQL dialects/complexity levels

### Adding Configuration

- [ ] Add field to appropriate config class (`ServerConfig` or `SQLParserConfig`)
- [ ] Add environment variable mapping in `from_env()` if needed
- [ ] Update `.env.example` with new variable
- [ ] Document in README.md

### Adding a Test

- [ ] Import required fixtures from `conftest.py`
- [ ] Use `client` fixture for API tests
- [ ] Use class-based test organization
- [ ] Test both success and error cases
- [ ] Test edge cases (empty input, invalid types, etc.)

---

## 8. SQLglot Expression Types Reference

Common expression types for SQL parsing:

| Expression | Description | Example Usage |
|------------|-------------|---------------|
| `exp.Select` | SELECT statement | `isinstance(parsed, exp.Select)` |
| `exp.Insert` | INSERT statement | `isinstance(parsed, exp.Insert)` |
| `exp.Update` | UPDATE statement | `isinstance(parsed, exp.Update)` |
| `exp.Delete` | DELETE statement | `isinstance(parsed, exp.Delete)` |
| `exp.Create` | CREATE statement | `isinstance(parsed, exp.Create)` |
| `exp.Table` | Table reference | `parsed.find_all(exp.Table)` |
| `exp.Column` | Column reference | `parsed.find_all(exp.Column)` |
| `exp.Identifier` | General identifier | For INSERT column lists |
| `exp.Join` | JOIN clause | `parsed.find_all(exp.Join)` |
| `exp.Where` | WHERE clause | `parsed.find(exp.Where)` |

### Accessing Table Properties

```python
for table in parsed.find_all(exp.Table):
    table.name      # Table name: "orders"
    table.db        # Schema name: "ecommerce"
    table.catalog   # Catalog name: "hive"
    # Full path: "hive.ecommerce.orders"
```

---

## See Also

- [README.md](../README.md) - Project overview and quick start
- [SQLglot Documentation](https://sqlglot.com/) - SQLglot library reference
- [Flask Documentation](https://flask.palletsprojects.com/) - Flask framework reference
