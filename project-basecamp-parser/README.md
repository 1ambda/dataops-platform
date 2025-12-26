# Project Basecamp Parser

A Sidecar SQL parsing service for DataOps Platform that provides Trino SQL parsing capabilities using SQLglot.

## Overview

The project-basecamp-parser is a lightweight Flask-based microservice designed to run alongside the basecamp-server as a sidecar. It provides SQL parsing functionality specifically for Trino SQL statements, extracting key information such as statement types, table names, and column references.

## Features

- **Trino SQL Support**: Full support for Trino SQL dialect using SQLglot parser
- **Statement Type Detection**: Identifies SQL statement types (SELECT, INSERT, UPDATE, DELETE, CREATE, etc.)
- **Table and Column Extraction**: Extracts table names and column references from SQL statements
- **Schema-Qualified Names**: Supports parsing of schema-qualified table names (schema.table)
- **REST API**: Production-ready REST API with comprehensive error handling
- **Health Checks**: Built-in health check endpoint for monitoring
- **Structured Logging**: Configurable logging with different levels and Rich formatting
- **Configuration Management**: Environment-based configuration with validation
- **Custom Exceptions**: Proper error handling with custom exception types
- **Input Validation**: Request validation with detailed error messages
- **Performance Monitoring**: Query length limits and timeout handling
- **Comprehensive Testing**: Full test suite with pytest and coverage reporting

## Tech Stack

- **Python 3.12+**: Modern Python runtime
- **Flask 3.1+**: Lightweight web framework
- **SQLglot 28.5+**: SQL parsing and analysis library
- **UV**: Fast Python package manager and environment manager
- **pytest**: Testing framework with coverage support

## Project Structure

```
project-basecamp-parser/
├── src/
│   └── parser/
│       ├── __init__.py
│       ├── config.py          # Configuration management
│       ├── exceptions.py      # Custom exception definitions
│       ├── logging_config.py  # Logging setup and configuration
│       └── sql_parser.py      # Core SQL parsing logic
├── tests/
│   ├── __init__.py
│   ├── conftest.py           # Test configuration with fixtures
│   ├── test_sql_parser.py    # SQL parser unit tests
│   └── test_api.py           # API endpoint integration tests
├── .env.example             # Environment variable template
├── main.py                  # Flask application entry point
├── pyproject.toml          # Project configuration and dependencies
├── README.md               # This file
└── .python-version         # Python version specification
```

## Installation

### Prerequisites

- Python 3.12 or higher
- UV package manager (https://docs.astral.sh/uv/)

### Setup

1. **Clone the repository** (if part of the dataops-platform monorepo, navigate to the parser directory):
   ```bash
   cd project-basecamp-parser
   ```

2. **Install dependencies using UV**:
   ```bash
   uv sync
   ```

3. **Verify installation**:
   ```bash
   uv run python --version
   ```

## Usage

### Development Server

Start the Flask development server:

```bash
uv run python main.py
```

The service will be available at `http://localhost:5000`.

### API Endpoints

#### Health Check
```http
GET /health
```

**Response:**
```json
{
  "status": "healthy",
  "service": "sql-parser"
}
```

#### Parse SQL Statement
```http
POST /parse-sql
Content-Type: application/json

{
  "sql": "SELECT col1, col2 FROM schema.table1 WHERE col3 = 'value'"
}
```

**Response:**
```json
{
  "statement_type": "SELECT",
  "tables": ["table1"],
  "columns": ["col1", "col2", "col3"],
  "schema_qualified_tables": ["schema.table1"],
  "parsed": true,
  "error": null
}
```

#### Validate SQL Statement
```http
POST /validate-sql
Content-Type: application/json

{
  "sql": "SELECT * FROM table1"
}
```

**Response:**
```json
{
  "valid": true
}
```

### Example Usage

#### Simple SELECT Query
```bash
curl -X POST http://localhost:5000/parse-sql \\
  -H "Content-Type: application/json" \\
  -d '{
    "sql": "SELECT customer_id, order_total FROM orders.customer_orders WHERE order_date >= '\''2024-01-01'\''"
  }'
```

#### Complex Query with JOINs
```bash
curl -X POST http://localhost:5000/parse-sql \\
  -H "Content-Type: application/json" \\
  -d '{
    "sql": "SELECT o.order_id, c.customer_name FROM orders.customer_orders o JOIN customers.customer_info c ON o.customer_id = c.customer_id"
  }'
```

#### DML Statement
```bash
curl -X POST http://localhost:5000/parse-sql \\
  -H "Content-Type: application/json" \\
  -d '{
    "sql": "INSERT INTO warehouse.products (product_name, price) VALUES ('\''Widget'\'', 29.99)"
  }'
```

## Development

### Code Quality Tools

This project uses several tools to maintain high code quality:

#### Linting with Ruff
```bash
# Check for linting issues
uv run ruff check

# Check and auto-fix issues
uv run ruff check --fix

# Format code
uv run ruff format
```

#### Type Checking with Pyright
```bash
# Run type checking
uv run pyright src/ main.py
```

#### Code Formatting with Black
```bash
# Format code
uv run black src/ tests/ main.py

# Check formatting without changes
uv run black --check src/ tests/ main.py
```

#### Run All Quality Checks
```bash
# Run all checks in sequence
uv run ruff check && uv run pyright src/ main.py && uv run black --check src/ tests/ main.py
```

### Testing

#### Run All Tests

```bash
uv run pytest
```

#### Run Tests with Coverage

```bash
uv run pytest --cov=src --cov-report=html
```

#### Run Specific Test Files

```bash
# Test only the SQL parser
uv run pytest tests/test_sql_parser.py

# Test only the API endpoints
uv run pytest tests/test_api.py
```

#### Test Output

The test suite includes:
- **SQL Parser Tests**: Unit tests for the core parsing functionality
- **API Tests**: Integration tests for Flask endpoints
- **Coverage Reporting**: Code coverage analysis

## Building

### Development Build

For development, simply run:

```bash
uv sync
```

### Production Build

For production deployment, you can build a wheel:

```bash
uv build
```

## Configuration

### Environment Variables

The service can be configured using the following environment variables:

#### Server Configuration
- `PARSER_HOST`: Host address to bind to (default: `0.0.0.0`)
- `PARSER_PORT`: Port number for the service (default: `5000`)
- `PARSER_DEBUG`: Enable debug mode (default: `false`)
- `PARSER_LOG_LEVEL`: Logging level - DEBUG, INFO, WARNING, ERROR, CRITICAL (default: `INFO`)

#### Parser Configuration
Built-in configuration (not environment-configurable):
- `MAX_QUERY_LENGTH`: Maximum SQL query length (default: `100000`)
- `TIMEOUT_SECONDS`: Query parsing timeout (default: `30`)
- `DIALECT`: SQL dialect for parsing (default: `presto`)

### Example Configuration

#### Development
```bash
export PARSER_DEBUG=true
export PARSER_LOG_LEVEL=DEBUG
export PARSER_PORT=8080
uv run python main.py
```

#### Production
```bash
export PARSER_HOST=0.0.0.0
export PARSER_PORT=5000
export PARSER_DEBUG=false
export PARSER_LOG_LEVEL=INFO
uv run python main.py
```

#### Using .env file
Copy `.env.example` to `.env` and customize:
```bash
cp .env.example .env
# Edit .env with your preferred settings
uv run python main.py
```

## Deployment

### As a Sidecar Service

This service is designed to run as a sidecar alongside the basecamp-server. Typical deployment patterns:

#### Docker Deployment

```dockerfile
FROM python:3.12-slim

WORKDIR /app
COPY . .

RUN pip install uv
RUN uv sync --frozen

EXPOSE 5000
CMD ["uv", "run", "python", "main.py"]
```

#### Kubernetes Sidecar

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: basecamp-server
    image: basecamp-server:latest
  - name: sql-parser
    image: project-basecamp-parser:latest
    ports:
    - containerPort: 5000
```

## API Reference

### Error Handling

All endpoints return consistent error responses:

```json
{
  "error": "Error description"
}
```

### Status Codes

- `200`: Success
- `400`: Bad Request (invalid SQL, missing fields, etc.)
- `404`: Endpoint not found
- `500`: Internal server error

### SQL Statement Types

The parser recognizes the following statement types:

- `SELECT`: Query statements
- `INSERT`: Data insertion
- `UPDATE`: Data updates
- `DELETE`: Data deletion
- `CREATE`: Table/schema creation
- `DROP`: Table/schema deletion
- `ALTER`: Table/schema modification
- `MERGE`: Merge operations
- `DML`: General data manipulation (for complex statements)
- `DDL`: General data definition (for complex statements)
- `UNKNOWN`: Unrecognized statement types

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## License

This project is part of the DataOps Platform and follows the same licensing terms.

## Support

For issues and questions:
1. Check the test files for usage examples
2. Review the API documentation above
3. Create an issue in the main dataops-platform repository
