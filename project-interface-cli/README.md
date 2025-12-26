# DataOps CLI (dli)

**DataOps CLI** is a command-line interface for the DataOps platform, providing easy access to platform operations, pipeline management, and SQL utilities.

## Features

- 🚀 **Health Checking**: Check the status of DataOps server with detailed response
- 📊 **Pipeline Management**: List and manage data pipelines with rich table display
- 🔍 **SQL Parsing**: Parse and format SQL queries with SQLGlot (multiple output formats)
- ⚙️ **Configuration Management**: Persistent configuration with JSON storage
- 🎨 **Rich Output**: Beautiful terminal output with colors, panels, and tables
- ⚡ **Async Operations**: Non-blocking API calls with proper timeout handling
- 📝 **Structured Logging**: Configurable logging with Rich formatting
- 🛡️ **Robust Error Handling**: Custom exceptions with meaningful error messages
- 🔧 **Environment Configuration**: Support for environment variables
- 📦 **Standalone Builds**: Create single-file executables for easy deployment
- 🧪 **Comprehensive Testing**: Full test coverage with async testing support
- 🎯 **Type Safety**: Full type hints with static type checking

## Installation

### Prerequisites

- Python 3.12+
- [uv](https://github.com/astral-sh/uv) (recommended) or pip

### Using uv (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd project-interface-cli

# Install dependencies
uv sync

# Install in development mode
uv pip install -e .
```

### Using pip

```bash
# Clone the repository
git clone <repository-url>
cd project-interface-cli

# Create virtual environment
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -e .
```

## Usage

Once installed, you can use the `dli` command:

### Basic Commands

```bash
# Show version
dli version

# Check server health
dli health

# Check health with custom server URL
dli health --url http://localhost:9090

# List all pipelines
dli pipelines

# List pipelines from custom server
dli pipelines --url http://localhost:9090
```

### SQL Utilities

```bash
# Parse and format SQL (pretty format)
dli sql-parse "SELECT * FROM users WHERE id = 1"

# Parse SQL with specific dialect  
dli sql-parse "SELECT * FROM users" --dialect mysql

# Parse SQL with compact output format
dli sql-parse "SELECT col1, col2 FROM table1" --format compact

# Parse complex SQL with joins
dli sql-parse "SELECT u.id, p.name FROM users u JOIN profiles p ON u.id = p.user_id"
```

### Configuration Management

```bash
# Show current configuration
dli config --show

# Set base URL for API calls
dli config --set-url http://localhost:9090

# Set request timeout
dli config --set-timeout 60

# Reset configuration to defaults
dli config --reset

# Use custom config file
dli config --config ~/.my-dli-config.json --show
```

### Environment Variables

You can configure the CLI using environment variables:

```bash
# Logging configuration
export DLI_LOG_LEVEL=DEBUG
export DLI_DEBUG=true

# Configuration file location
export DLI_CONFIG_FILE=~/.my-custom-dli-config.json

# Run CLI with environment config
dli health
```

## Development

### Local Development Setup

```bash
# Clone the repository
git clone <repository-url>
cd project-interface-cli

# Install uv if not already installed
curl -LsSf https://astral.sh/uv/install.sh | sh

# Create virtual environment and install dependencies
uv sync

# Install development dependencies
uv sync --group dev

# Install in editable mode
uv pip install -e .
```

### Running Tests

```bash
# Run all tests
uv run pytest

# Run tests with coverage
uv run pytest --cov=dli

# Run specific test file
uv run pytest tests/test_main.py

# Run tests in verbose mode
uv run pytest -v
```

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

### Building the Package

#### Standard Package Build

```bash
# Build wheel and source distribution
uv build

# The built packages will be in dist/
ls dist/
```

#### Standalone Executable Build

For creating a single executable file that includes all dependencies (no Python installation required):

```bash
# Install build dependencies
uv sync --group build

# Run the standalone builder script
uv run python build_standalone.py

# The executable will be in dist_standalone/
# On macOS/Linux: dist_standalone/dli
# On Windows: dist_standalone/dli.exe
```

**Benefits of Standalone Build:**
- ✅ No Python installation required on target machines
- ✅ No dependency conflicts
- ✅ Single file distribution
- ✅ Easy deployment to production servers

**Note:** Standalone executables are larger (~50-100MB) as they include the Python runtime and all dependencies.

## Project Structure

```
project-interface-cli/
├── src/
│   └── dli/
│       ├── __init__.py          # Package initialization with version
│       ├── config.py            # Configuration management and persistence
│       ├── exceptions.py        # Custom exception definitions
│       ├── logging_config.py    # Rich logging setup and configuration
│       └── main.py              # Main CLI application with Typer
├── tests/
│   ├── __init__.py              # Test package
│   └── test_main.py             # Comprehensive CLI tests
├── .env.example                 # Environment variable template
├── build_standalone.py          # PyInstaller build script
├── dli.spec                     # PyInstaller specification
├── main.py                      # Entry point that delegates to CLI
├── pyproject.toml              # Project configuration and dependencies
├── pytest.ini                 # Pytest configuration
├── README.md                   # This file
└── .python-version            # Python version specification
```

## Dependencies

### Core Dependencies

- **[Typer](https://typer.tiangolo.com/)**: Modern CLI framework with rich features
- **[Rich](https://rich.readthedocs.io/)**: Beautiful terminal output
- **[SQLGlot](https://sqlglot.com/)**: SQL parser and transpiler
- **[httpx](https://www.python-httpx.org/)**: Modern async HTTP client
- **[Pydantic](https://pydantic.dev/)**: Data validation and settings

### Development Dependencies

- **pytest**: Testing framework
- **pytest-asyncio**: Async testing support
- **pytest-httpx**: HTTP testing utilities
- **black**: Code formatter
- **ruff**: Fast Python linter and formatter
- **pyright**: Static type checker

## Configuration

The CLI supports configuration through a JSON file (default: `~/.dli/config.json`):

```json
{
  "base_url": "http://localhost:8080"
}
```

You can specify a custom config file location using the `--config` option.

## API Integration

The CLI integrates with the DataOps platform server through REST APIs:

- `GET /api/health` - Health check endpoint
- `GET /api/pipelines` - List pipelines endpoint

Default server URL is `http://localhost:8080`, but can be customized via:
- Command-line options (`--url`)
- Configuration file
- Environment variables (planned)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run tests: `uv run pytest`
6. Run code quality checks:
   ```bash
   uv run ruff check --fix
   uv run ruff format
   uv run pyright src/ main.py
   uv run black --check src/ tests/ main.py
   ```
7. Verify all checks pass
8. Submit a pull request

## License

[License information to be added]

## Support

For issues and questions:
- Create an issue in the project repository
- Check existing documentation
- Review test cases for usage examples
