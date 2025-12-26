# project-basecamp-connect

Integration Service for DataOps Platform - connects GitHub, Jira, and Slack for seamless workflow automation.

## Overview

`project-basecamp-connect` is a Flask-based microservice that handles integrations between:

- **Slack ↔ Jira**: Create Jira tickets from Slack workflows/messages, sync comments
- **Jira ↔ Slack**: Open Slack threads for Jira tickets, sync updates
- **GitHub ↔ Slack**: PR/Issue notifications, deployment updates

## Technology Stack

| Category | Technology |
|----------|------------|
| Runtime | Python 3.12+ |
| Web Framework | Flask 3.1+ |
| ORM | SQLAlchemy 2.0+ |
| HTTP Client | httpx |
| Database | SQLite (dev) / MySQL (prod) |
| Package Manager | uv |
| Testing | pytest + coverage |
| Linting | Ruff, Pyright |

## Quick Start

### Prerequisites

- Python 3.12+
- [uv](https://github.com/astral-sh/uv) package manager

### Installation

```bash
# Install dependencies
uv sync

# Run the development server (port 5001)
uv run python main.py
```

### Configuration

Copy `.env.example` to `.env` and configure:

```bash
# Server Configuration
CONNECT_HOST=0.0.0.0
CONNECT_PORT=5001
CONNECT_DEBUG=false
CONNECT_LOG_LEVEL=INFO

# Database Configuration
# SQLite for local development (default)
DATABASE_URL=sqlite:///./connect.db

# MySQL for production
# DATABASE_URL=mysql+pymysql://user:password@localhost:3306/connect

# External Service API Keys
GITHUB_TOKEN=your_github_token
JIRA_API_TOKEN=your_jira_api_token
JIRA_BASE_URL=https://your-domain.atlassian.net
SLACK_BOT_TOKEN=xoxb-your-slack-bot-token
SLACK_SIGNING_SECRET=your_slack_signing_secret
```

## Project Structure

```
project-basecamp-connect/
├── src/connect/
│   ├── __init__.py          # Package initialization
│   ├── config.py            # Configuration management
│   ├── database.py          # SQLAlchemy models
│   ├── exceptions.py        # Custom exceptions
│   └── logging_config.py    # Logging setup
├── tests/
│   ├── conftest.py          # Test fixtures
│   ├── test_api.py          # API tests
│   └── test_database.py     # Database model tests
├── main.py                  # Flask application
├── pyproject.toml           # Project configuration
├── Makefile                 # Development automation
└── README.md                # This file
```

## API Endpoints

### Health Check

```bash
GET /health
```

Response:
```json
{
  "status": "healthy",
  "service": "connect",
  "database": "connected"
}
```

### Create Integration

```bash
POST /api/v1/integrations
Content-Type: application/json

{
  "source_service": "slack",
  "target_service": "jira",
  "event_type": "create_ticket",
  "payload": {
    "message": "Bug report from user",
    "channel": "C12345678"
  }
}
```

Response:
```json
{
  "success": true,
  "message": "Integration created",
  "integration_id": 1
}
```

### List Integrations

```bash
GET /api/v1/integrations
```

Query parameters:
- `source_service` - Filter by source
- `target_service` - Filter by target
- `status` - Filter by status

### List Mappings

```bash
GET /api/v1/mappings
```

Returns service mappings (e.g., Jira ticket ↔ Slack thread).

## Database Models

### IntegrationLog

Logs all integration events for auditing and debugging:

| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| source_service | String | Origin service (slack, jira, github) |
| target_service | String | Destination service |
| event_type | String | Type of event |
| source_id | String | ID in source system |
| target_id | String | ID in target system |
| payload | Text | JSON payload |
| status | String | pending, completed, failed |
| error_message | Text | Error details if failed |
| created_at | DateTime | Creation timestamp |
| updated_at | DateTime | Last update timestamp |

### ServiceMapping

Maps identifiers across services:

| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| source_service | String | Source service |
| source_id | String | ID in source |
| target_service | String | Target service |
| target_id | String | ID in target |
| mapping_type | String | Type of mapping |
| extra_data | Text | Additional JSON data |

## Development Commands

```bash
# Show all commands
make help

# Install dependencies
make dev-install

# Run development server
make run-dev

# Run tests with coverage
make test

# Run tests without coverage (faster)
make test-fast

# Lint and format
make lint-fix

# Type checking
make type-check

# Run all checks
make check-all

# Initialize database
make db-init

# Reset database (WARNING: deletes all data)
make db-reset

# Clean up cache files
make clean
```

## Testing

```bash
# Run all tests
uv run pytest

# Run with coverage
uv run pytest --cov=src --cov-report=html

# Run specific test file
uv run pytest tests/test_api.py

# Run specific test
uv run pytest tests/test_api.py::TestHealthEndpoint::test_health_returns_200
```

## Integration Workflows (Planned)

### Slack → Jira
1. User triggers Slack workflow or mentions bot
2. Service extracts request details
3. Creates Jira ticket via Jira API
4. Stores mapping (Slack message → Jira ticket)
5. Posts Jira ticket link back to Slack

### Jira → Slack
1. Jira webhook fires on ticket update
2. Service looks up linked Slack thread
3. Posts update to thread
4. Logs integration event

### GitHub → Slack
1. GitHub webhook fires (PR merged, issue created, etc.)
2. Service formats notification
3. Posts to designated Slack channel

## Docker

```bash
# Build image
make docker-build

# Run container
make docker-run
```

## Production Deployment

For production, use MySQL instead of SQLite:

```bash
# Set production database URL
DATABASE_URL=mysql+pymysql://user:password@mysql-host:3306/connect

# Run with Gunicorn
make run-prod
```

## Contributing

1. Follow the code style (Ruff + Black formatting)
2. Add tests for new features
3. Ensure all checks pass: `make check-all`
4. Update documentation as needed

## License

Internal use only - DataOps Platform
