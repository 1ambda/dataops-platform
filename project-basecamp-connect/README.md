# project-basecamp-connect

Integration Service for DataOps Platform - connects GitHub, Jira, and Slack for seamless workflow automation.

## Quick Start

```bash
# Install dependencies
uv sync

# Run development server (port 5001)
uv run python main.py

# Run tests
uv run pytest

# Run with coverage
uv run pytest --cov=src --cov-report=html
```

## Configuration

```bash
# .env file
CONNECT_HOST=0.0.0.0
CONNECT_PORT=5001
CONNECT_DEBUG=false
DATABASE_URL=sqlite:///./connect.db

# External Services
JIRA_API_TOKEN=your_jira_api_token
JIRA_BASE_URL=https://your-domain.atlassian.net
SLACK_BOT_TOKEN=xoxb-your-slack-bot-token
SLACK_SIGNING_SECRET=your_slack_signing_secret
GITHUB_TOKEN=your_github_token
```

## Project Structure

```
project-basecamp-connect/
├── src/connect/
│   ├── clients/          # External API clients (Jira, Slack)
│   │   ├── jira.py       # JiraClientInterface + Mock
│   │   └── slack.py      # SlackClientInterface + Mock
│   ├── models/           # SQLAlchemy models
│   │   ├── jira.py       # JiraTicket
│   │   ├── slack.py      # SlackMessage, SlackThread
│   │   └── linking.py    # JiraSlackLink
│   ├── services/         # Business logic
│   │   ├── jira_monitor.py
│   │   ├── jira_slack_integration.py
│   │   └── slack_message.py
│   ├── config.py         # Pydantic configuration
│   ├── database.py       # SQLAlchemy setup
│   └── exceptions.py     # Custom exceptions
├── tests/                # pytest tests
├── docs/
│   └── PATTERNS.md       # Development patterns reference
├── main.py               # Flask application
└── pyproject.toml
```

## API Endpoints

### Health Check

```bash
GET /health
# Response: {"status": "healthy", "service": "connect", "database": "connected"}
```

### Jira Webhook

```bash
POST /api/v1/jira/webhook
# Receives Jira issue events, creates Slack threads
```

### Jira Tickets

```bash
GET /api/v1/jira/tickets                    # List tickets
GET /api/v1/jira/tickets/<jira_key>         # Get ticket
POST /api/v1/jira/tickets/<jira_key>/sync   # Sync from Jira
```

### Slack

```bash
GET /api/v1/slack/channels/<id>/messages    # Get channel messages
POST /api/v1/slack/channels/<id>/sync       # Sync from Slack
GET /api/v1/slack/threads/<id>              # Get thread with messages
```

### Links

```bash
GET /api/v1/links                           # List Jira-Slack links
```

## Development Commands

```bash
make help           # Show all commands
make run-dev        # Run development server
make test           # Run tests with coverage
make test-fast      # Run tests without coverage
make lint-fix       # Lint and format code
make type-check     # Run type checking
make check-all      # Run all checks
make db-init        # Initialize database
make db-reset       # Reset database (WARNING: deletes data)
```

## Technology Stack

| Category | Technology |
|----------|------------|
| Runtime | Python 3.12+ |
| Web Framework | Flask 3.1+ |
| ORM | SQLAlchemy 2.0+ |
| HTTP Client | httpx |
| Validation | Pydantic |
| Database | SQLite (dev) / MySQL (prod) |
| Package Manager | uv |
| Testing | pytest + coverage |
| Linting | Ruff, Pyright |

## Code Templates

### Adding a New Endpoint

```python
from flask import Response, g, jsonify, request
from pydantic import BaseModel

class MyRequest(BaseModel):
    name: str
    value: int

class MyResponse(BaseModel):
    id: int
    name: str

@app.route("/api/v1/my-resource", methods=["POST"])
def create_my_resource() -> tuple[Response, int]:
    if not request.is_json:
        return jsonify({"error": "Invalid content type"}), 400

    data = MyRequest(**request.get_json())
    service = MyService(session=g.db_session)
    result = service.create(data)

    return jsonify(MyResponse(id=result.id, name=result.name).model_dump()), 201
```

### Adding a New Model

```python
from datetime import datetime
from sqlalchemy import Column, DateTime, Integer, String
from src.connect.models.base import Base

class MyModel(Base):
    __tablename__ = "my_models"

    id = Column(Integer, primary_key=True)
    name = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    def to_dict(self) -> dict:
        return {"id": self.id, "name": self.name}
```

### Adding a Test

```python
import uuid
import pytest

class TestMyService:
    @pytest.fixture
    def service(self, db_session):
        return MyService(db_session)

    def test_create(self, service):
        unique_id = uuid.uuid4().hex[:8]
        result = service.create(name=f"test-{unique_id}")

        assert result is not None
        assert result.id is not None
```

## Documentation

- **[docs/PATTERNS.md](./docs/PATTERNS.md)** - Complete development patterns and templates
- **[Makefile](./Makefile)** - Development automation (run `make help`)

## License

Internal use only - DataOps Platform
