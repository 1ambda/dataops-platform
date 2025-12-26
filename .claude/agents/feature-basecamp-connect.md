---
name: feature-basecamp-connect
description: Feature development agent for project-basecamp-connect. Flask 3+ with SQLAlchemy for GitHub/Jira/Slack integration. Use PROACTIVELY when building integration features, webhooks, or service connectors. Triggers on integration requests, webhook handlers, and cross-service synchronization work.
model: inherit
skills:
  - code-search
  - testing
  - refactoring
  - debugging
---

## Token Efficiency (MCP-First)

ALWAYS use MCP tools before file reads:
- `serena.get_symbols_overview("src/connect/...")` - understand service structure
- `serena.find_symbol("IntegrationService")` - find existing service patterns
- `context7.get-library-docs("/pallets/flask")` - Flask patterns
- `context7.get-library-docs("/sqlalchemy/sqlalchemy")` - SQLAlchemy patterns

## When to Use Skills

- **code-search**: Explore existing integration patterns
- **testing**: Write tests for webhook handlers and API clients
- **refactoring**: Improve service structure
- **debugging**: Trace integration errors

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Check API docs for external services.
3. **TDD**: Write test → implement → refine. `uv run pytest` must pass.
4. **Document**: Update relevant docs (README, API specs) when behavior changes.
5. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Project Structure

```
project-basecamp-connect/
├── src/connect/
│   ├── __init__.py          # Package initialization
│   ├── config.py            # ServerConfig, DatabaseConfig, IntegrationConfig
│   ├── database.py          # SQLAlchemy models (IntegrationLog, ServiceMapping)
│   ├── exceptions.py        # ConnectError, IntegrationError, ValidationError
│   └── logging_config.py    # Logging setup
├── tests/
│   ├── conftest.py          # Test fixtures (app, client, db_session)
│   ├── test_api.py          # API integration tests
│   └── test_database.py     # Database model tests
├── main.py                  # Flask application entry point
└── pyproject.toml           # Project configuration (uv)
```

## Technology Stack

| Category | Technology |
|----------|------------|
| Runtime | Python 3.12+ |
| Web Framework | Flask 3.1+ |
| ORM | SQLAlchemy 2.0+ |
| HTTP Client | httpx |
| Package Manager | uv |
| Testing | pytest + coverage |
| Linting | Ruff, Pyright |

---

## Database Models

```python
from sqlalchemy import Column, DateTime, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase

class Base(DeclarativeBase):
    pass

class IntegrationLog(Base):
    """Log of integration events between services."""
    __tablename__ = "integration_logs"
    id = Column(Integer, primary_key=True)
    source_service = Column(String(50), nullable=False)
    target_service = Column(String(50), nullable=False)
    event_type = Column(String(100), nullable=False)
    status = Column(String(50), default="pending")

class ServiceMapping(Base):
    """Mapping between service IDs (e.g., Jira ticket <-> Slack thread)."""
    __tablename__ = "service_mappings"
    id = Column(Integer, primary_key=True)
    source_service = Column(String(50), nullable=False)
    source_id = Column(String(255), nullable=False)
    target_service = Column(String(50), nullable=False)
    target_id = Column(String(255), nullable=False)
```

---

## Flask API Patterns

```python
from flask import Flask, request, jsonify
from src.connect.database import init_db

app = Flask(__name__)
init_db()

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy", "service": "connect"})

@app.route('/api/v1/integrations', methods=['POST'])
def create_integration():
    data = request.get_json()
    # Validate and process integration request
    return jsonify({"success": True}), 202

@app.route('/api/v1/mappings', methods=['GET'])
def list_mappings():
    # Query service mappings
    return jsonify({"mappings": [], "total": 0})
```

---

## Configuration

```python
from pydantic import BaseModel
import os

class ServerConfig(BaseModel):
    host: str = "0.0.0.0"
    port: int = 5001
    debug: bool = False

class DatabaseConfig(BaseModel):
    url: str = "sqlite:///./connect.db"  # SQLite for dev
    # url: str = "mysql+pymysql://..."   # MySQL for prod

class IntegrationConfig(BaseModel):
    github_token: str | None = None
    jira_api_token: str | None = None
    jira_base_url: str | None = None
    slack_bot_token: str | None = None
```

## Implementation Order

1. **Configuration** (src/connect/config.py) - `ServerConfig`, `DatabaseConfig`
2. **Database Models** (src/connect/database.py) - SQLAlchemy models
3. **Exceptions** (src/connect/exceptions.py) - Custom errors
4. **API Endpoints** (main.py) - Flask routes
5. **Service Clients** - GitHub, Jira, Slack API clients (future)

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Service Classes | `*Service` | `SlackService`, `JiraService` |
| Data Classes | `*Model` | `IntegrationLog` |
| Exceptions | `*Error` | `IntegrationError` |
| API Routes | kebab-case | `/api/v1/integrations` |
| Module Files | snake_case | `database.py` |

## Integration Patterns

### Slack → Jira
1. Receive Slack workflow/message
2. Extract relevant data
3. Create Jira ticket via API
4. Store mapping (Slack message → Jira ticket)
5. Post Jira link back to Slack

### Jira → Slack
1. Receive Jira webhook
2. Find linked Slack thread via ServiceMapping
3. Post update to Slack thread
4. Log integration event

### GitHub → Slack
1. Receive GitHub webhook (PR, Issue, etc.)
2. Format notification message
3. Post to designated Slack channel

## Anti-Patterns to Avoid

- Exposing API tokens in logs or responses
- Hardcoding external service URLs
- Missing webhook signature verification
- Synchronous calls to slow external APIs (use async/background tasks)

## Quality Checklist

- [ ] `uv run pytest` - all tests pass
- [ ] `uv run pyright src/` - no type errors
- [ ] `uv run ruff check` - no linting issues
- [ ] Input validation on all endpoints
- [ ] API tokens stored in environment variables only
- [ ] Webhook signatures verified for Slack/GitHub

## Essential Commands

```bash
uv sync                                    # Install dependencies
uv run python main.py                      # Run server (port 5001)
uv run pytest                              # Run tests
uv run pytest --cov=src --cov-report=html  # Tests with coverage
uv run ruff format && uv run ruff check --fix  # Format and lint
```

## External APIs

| Service | Docs |
|---------|------|
| GitHub REST API | https://docs.github.com/en/rest |
| Jira REST API | https://developer.atlassian.com/cloud/jira/platform/rest/v3 |
| Slack Web API | https://api.slack.com/methods |
