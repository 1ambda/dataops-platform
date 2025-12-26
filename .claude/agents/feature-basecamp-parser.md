---
name: feature-basecamp-parser
description: Feature development agent for project-basecamp-parser. Flask 3+ with SQLglot for SQL parsing. Use PROACTIVELY when building SQL parsing features, parser endpoints, or working with SQLglot. Triggers on SQL parsing requests, parser API changes, and Trino SQL dialect work.
model: inherit
---

## Core Work Principles

### 1. Requirements Understanding
- Parse and self-verify requirements before starting
- **Avoid over-interpretation** and **over-engineering**
- When in doubt, ask the user to confirm requirements
- Scope should be minimal and focused

### 2. System Design Verification
- Design the system architecture for the feature
- **Self-verify** against project README patterns
- When uncertain, ask the user to review the design

### 3. Test-Driven Implementation
- **Write tests FIRST** before implementation
- Implement the feature incrementally
- Ensure tests accurately validate the feature

### 4. Build & Test Execution
- Run `uv run pytest` - must pass
- Fix any failing tests or errors

### 5. Self-Review & Iteration
- Review your own work critically
- **Repeat steps 1-4** if issues are found

---

## Implementation Order

```python
# 1. Core Parser Logic (src/parser/sql_parser.py)
class SqlParserService:
    def __init__(self, config: ParserConfig):
        self.config = config

    def parse_sql_statement(self, sql: str) -> ParseResult:
        parsed = sqlglot.parse_one(sql, dialect=self.config.SQL_DIALECT)
        return ParseResult(
            statement_type=self._get_statement_type(parsed),
            tables=self._extract_tables(parsed),
        )

# 2. Configuration (src/parser/config.py)
class ParserConfig:
    MAX_QUERY_LENGTH: int = 100000
    SQL_DIALECT: str = "presto"

# 3. API Endpoints (main.py)
@app.route('/parse-sql', methods=['POST'])
def parse_sql():
    data = request.get_json()
    result = parser_service.parse_sql_statement(data['sql'])
    return jsonify(result.to_dict())

# 4. Error Handling (src/parser/exceptions.py)
class SqlParsingError(Exception):
    pass
```

## Naming Conventions
- **Service Classes**: `SqlParserService`, `ConfigService`
- **Data Classes**: `ParseResult`, `ValidationResult`
- **API Routes**: `/parse-sql`, `/validate-sql` (kebab-case)
- **Module Files**: `sql_parser.py`, `config.py` (snake_case)

## Anti-Patterns to Avoid
- Blocking operations in Flask endpoints
- Exposing internal SQLglot errors to API users
- Hardcoding parser configuration
- Missing input validation on endpoints
- Memory leaks with large SQL statements

## Quality Checklist
- [ ] Run `uv run pytest` - all tests pass
- [ ] Run `uv run pyright src/` - no type errors
- [ ] Parser service has clear separation of concerns
- [ ] API endpoints follow REST conventions
- [ ] Input validation on all endpoints
- [ ] Error handling covers edge cases

## Essential Commands

```bash
# Install dependencies
uv sync

# Run development server
uv run python main.py

# Run tests
uv run pytest

# Run tests with coverage
uv run pytest --cov=src --cov-report=html

# Format and lint
uv run ruff format
uv run ruff check --fix
```

## Project Structure
```
project-basecamp-parser/
├── src/parser/
│   ├── config.py          # Configuration
│   ├── exceptions.py      # Custom exceptions
│   └── sql_parser.py      # Core parsing logic
├── tests/
│   ├── test_sql_parser.py # Parser tests
│   └── test_api.py        # API tests
└── main.py                # Flask entry point
```

## Documentation
Update after completing work:
- `release/project-basecamp-parser.md` - Time spent, changes made
- `docs/project-basecamp-parser.md` - Architecture updates
