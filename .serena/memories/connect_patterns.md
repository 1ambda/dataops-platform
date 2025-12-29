# Basecamp Connect Development Patterns (Quick Reference)

> Flask 3+ with SQLAlchemy for GitHub/Jira/Slack integration

## 1. SQLAlchemy Models

```python
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import DeclarativeBase

class Base(DeclarativeBase):
    pass

class IntegrationLog(Base):
    __tablename__ = "integration_logs"
    id = Column(Integer, primary_key=True)
    source_service = Column(String(50), nullable=False)
    target_service = Column(String(50), nullable=False)
    event_type = Column(String(100), nullable=False)
    status = Column(String(50), default="pending")

class ServiceMapping(Base):
    __tablename__ = "service_mappings"
    id = Column(Integer, primary_key=True)
    source_service = Column(String(50), nullable=False)
    source_id = Column(String(255), nullable=False)
    target_service = Column(String(50), nullable=False)
    target_id = Column(String(255), nullable=False)
```

## 2. Flask API Pattern

```python
@app.route('/api/v1/integrations', methods=['POST'])
def create_integration():
    data = request.get_json()
    # Validate and process
    return jsonify({"success": True}), 202

@app.route('/api/v1/mappings', methods=['GET'])
def list_mappings():
    return jsonify({"mappings": [], "total": 0})
```

## 3. Project Structure

```
project-basecamp-connect/
├── src/connect/
│   ├── config.py         # ServerConfig, DatabaseConfig
│   ├── database.py       # SQLAlchemy models
│   └── exceptions.py     # ConnectError
├── tests/
└── main.py               # Flask entry point
```

## 4. Integration Patterns

**Slack → Jira:**
1. Receive Slack message → 2. Create Jira ticket → 3. Store mapping → 4. Post Jira link to Slack

**Jira → Slack:**
1. Receive Jira webhook → 2. Find Slack thread → 3. Post update

**GitHub → Slack:**
1. Receive GitHub webhook → 2. Format message → 3. Post to Slack

## 5. Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Service | `*Service` | `SlackService` |
| Model | `*Model` or direct | `IntegrationLog` |
| Exception | `*Error` | `IntegrationError` |
| API Routes | kebab-case | `/api/v1/integrations` |

## 6. Essential Commands

```bash
uv sync                  # Install dependencies
uv run python main.py    # Run server (port 5001)
uv run pytest            # Run tests
uv run pyright src/      # Type check
```

## 7. Security Notes

- Store API tokens in environment variables only
- Verify webhook signatures for Slack/GitHub
- Never log tokens
