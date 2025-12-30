# Flask + SQLAlchemy Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.

---

## Quick Reference

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| Flask endpoint | `main.py` | Pydantic models + jsonify |
| SQLAlchemy model | `models/jira.py` | Base inheritance + relationships |
| Client interface | `clients/jira.py` | ABC + Protocol + Mock |
| Service layer | `services/jira_monitor.py` | Session injection + client injection |
| API test | `tests/test_api.py` | Flask test_client + fixtures |
| Service test | `tests/test_jira_monitor_service.py` | pytest class + db_session fixture |
| Configuration | `config.py` | Pydantic BaseModel + from_env |

---

## 1. Flask Endpoint Pattern

### Request/Response Models (Pydantic)

```python
from pydantic import BaseModel

class CreateItemRequest(BaseModel):
    """Request model for item creation."""
    name: str
    description: str | None = None
    priority: str = "medium"

class ItemResponse(BaseModel):
    """Response model for item data."""
    id: int
    name: str
    status: str
    created_at: str | None = None

class ErrorResponse(BaseModel):
    """Response model for errors."""
    error: str
    code: str | None = None
```

### Endpoint Template

```python
from flask import Flask, Response, g, jsonify, request
from pydantic import ValidationError

@app.route("/api/v1/items", methods=["POST"])
def create_item() -> tuple[Response, int]:
    """Create a new item."""
    logger.debug("Create item request received")

    # 1. Validate content type
    if not request.is_json:
        error_response = ErrorResponse(
            error="Content-Type must be application/json",
            code="INVALID_CONTENT_TYPE",
        )
        return jsonify(error_response.model_dump()), 400

    try:
        # 2. Parse and validate request
        data = request.get_json()
        if data is None:
            error_response = ErrorResponse(
                error="Invalid JSON payload", code="INVALID_JSON"
            )
            return jsonify(error_response.model_dump()), 400

        request_data = CreateItemRequest(**data)

        # 3. Business logic via service
        service = ItemService(
            session=g.db_session,
            client=g.external_client,
        )
        item = service.create_item(request_data)

        # 4. Map to response
        response = ItemResponse(
            id=item.id,
            name=item.name,
            status=item.status,
        )
        return jsonify(response.model_dump()), 201

    except ValidationError as e:
        logger.error(f"Validation error: {e}")
        error_response = ErrorResponse(
            error=f"Validation error: {e!s}", code="VALIDATION_ERROR"
        )
        return jsonify(error_response.model_dump()), 400
    except Exception as e:
        logger.error(f"Unexpected error: {e}", exc_info=True)
        error_response = ErrorResponse(
            error="Internal server error", code="INTERNAL_ERROR"
        )
        return jsonify(error_response.model_dump()), 500


@app.route("/api/v1/items/<int:item_id>", methods=["GET"])
def get_item(item_id: int) -> tuple[Response, int]:
    """Get a specific item by ID."""
    logger.debug(f"Get item request: {item_id}")

    try:
        service = ItemService(session=g.db_session)
        item = service.get_item_by_id(item_id)

        if item is None:
            error_response = ErrorResponse(
                error=f"Item not found: {item_id}",
                code="NOT_FOUND",
            )
            return jsonify(error_response.model_dump()), 404

        response = ItemResponse(
            id=item.id,
            name=item.name,
            status=item.status,
        )
        return jsonify(response.model_dump()), 200

    except Exception as e:
        logger.error(f"Error getting item: {e}", exc_info=True)
        error_response = ErrorResponse(
            error="Internal server error", code="INTERNAL_ERROR"
        )
        return jsonify(error_response.model_dump()), 500
```

### Request Lifecycle (Flask `g` context)

```python
from flask import g

@app.before_request
def before_request() -> None:
    """Set up request context."""
    g.db_session = get_session()
    g.jira_client = get_jira_client()
    g.slack_client = get_slack_client()

@app.teardown_request
def teardown_request(exception: BaseException | None = None) -> None:
    """Clean up request context."""
    session = g.pop("db_session", None)
    if session is not None:
        if exception:
            session.rollback()
        session.close()
```

---

## 2. SQLAlchemy Model Pattern

### Model Template

```python
from datetime import datetime
from sqlalchemy import Column, DateTime, Integer, String, Text
from sqlalchemy.orm import relationship
from src.connect.models.base import Base


class Item(Base):
    """Item entity.

    Attributes:
        id: Primary key
        external_id: External system ID (unique)
        name: Item name
        description: Full description
        status: Current status
        created_at: Local creation timestamp
        updated_at: Local update timestamp
    """

    __tablename__ = "items"

    id = Column(Integer, primary_key=True, autoincrement=True)
    external_id = Column(String(50), nullable=False, unique=True, index=True)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(String(50), nullable=False, default="pending", index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Relationships
    links = relationship(
        "ItemLink", back_populates="item", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<Item(id={self.id}, name='{self.name}', status={self.status})>"

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "external_id": self.external_id,
            "name": self.name,
            "description": self.description,
            "status": self.status,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
```

### Relationship Pattern

```python
# Parent model
class JiraTicket(Base):
    __tablename__ = "jira_tickets"
    id = Column(Integer, primary_key=True)
    # ... other columns

    # One-to-many relationship
    slack_links = relationship(
        "JiraSlackLink",
        back_populates="jira_ticket",
        cascade="all, delete-orphan"
    )


# Child model with foreign key
class JiraSlackLink(Base):
    __tablename__ = "jira_slack_links"
    id = Column(Integer, primary_key=True)
    jira_ticket_id = Column(
        Integer,
        ForeignKey("jira_tickets.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # Back-reference
    jira_ticket = relationship("JiraTicket", back_populates="slack_links")
```

---

## 3. Client Interface Pattern

### Interface Design (ABC + Protocol)

```python
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Protocol, runtime_checkable


@dataclass
class ExternalItem:
    """Data transfer object for external API responses."""
    id: str
    name: str
    status: str

    @classmethod
    def from_api_response(cls, data: dict[str, Any]) -> "ExternalItem":
        """Create from API response."""
        return cls(
            id=data["id"],
            name=data.get("name", ""),
            status=data.get("status", {}).get("name", "Unknown"),
        )


@runtime_checkable
class ExternalClientProtocol(Protocol):
    """Protocol for structural typing (duck typing)."""

    def get_item(self, item_id: str) -> ExternalItem:
        """Fetch a single item."""
        ...

    def search_items(self, query: str) -> list[ExternalItem]:
        """Search for items."""
        ...


class ExternalClientInterface(ABC):
    """Abstract base class for external API client."""

    @abstractmethod
    def get_item(self, item_id: str) -> ExternalItem:
        """Fetch a single item by ID."""

    @abstractmethod
    def search_items(self, query: str) -> list[ExternalItem]:
        """Search for items using query string."""
```

### Real Implementation

```python
import httpx
from src.connect.config import get_integration_config
from src.connect.exceptions import ExternalAPIError


class ExternalClient(ExternalClientInterface):
    """Real API client using httpx."""

    def __init__(self, base_url: str | None = None, api_token: str | None = None):
        config = get_integration_config()
        self.base_url = (base_url or config.api_base_url or "").rstrip("/")
        self.api_token = api_token or config.api_token

        if not self.base_url or not self.api_token:
            raise ExternalAPIError("API configuration missing")

        self._client = httpx.Client(
            base_url=self.base_url,
            headers={"Authorization": f"Bearer {self.api_token}"},
            timeout=30.0,
        )

    def get_item(self, item_id: str) -> ExternalItem:
        try:
            response = self._client.get(f"/items/{item_id}")
            response.raise_for_status()
            return ExternalItem.from_api_response(response.json())
        except httpx.HTTPStatusError as e:
            raise ExternalAPIError(f"Failed to fetch item: {e.response.text}")
```

### Mock Implementation

```python
class MockExternalClient(ExternalClientInterface):
    """Mock client for testing."""

    def __init__(self):
        self._items: dict[str, ExternalItem] = {}
        self._setup_sample_data()

    def _setup_sample_data(self) -> None:
        """Set up sample data for testing."""
        samples = [
            ExternalItem(id="1", name="Sample Item 1", status="Active"),
            ExternalItem(id="2", name="Sample Item 2", status="Pending"),
        ]
        for item in samples:
            self._items[item.id] = item

    def add_mock_item(self, item: ExternalItem) -> None:
        """Add a mock item for testing."""
        self._items[item.id] = item

    def get_item(self, item_id: str) -> ExternalItem:
        if item_id not in self._items:
            raise ExternalAPIError(f"Item {item_id} not found")
        return self._items[item_id]

    def search_items(self, query: str) -> list[ExternalItem]:
        return [i for i in self._items.values() if query.lower() in i.name.lower()]
```

### Factory Function

```python
def get_external_client() -> ExternalClientProtocol:
    """Get external client (real or mock based on configuration)."""
    config = get_integration_config()
    if config.api_token and config.api_base_url:
        return ExternalClient()
    return MockExternalClient()
```

---

## 4. Service Layer Pattern

### Service Template

```python
from sqlalchemy.orm import Session
from src.connect.clients.external import ExternalClientInterface, ExternalItem
from src.connect.logging_config import get_logger
from src.connect.models.item import Item

logger = get_logger(__name__)


class ItemService:
    """Service for managing items.

    Handles business logic between API layer and database.
    """

    def __init__(
        self,
        session: Session,
        external_client: ExternalClientInterface | None = None,
    ) -> None:
        """Initialize the service.

        Args:
            session: SQLAlchemy database session
            external_client: External API client (optional)
        """
        self._session = session
        self._external_client = external_client

    def get_item_by_id(self, item_id: int) -> Item | None:
        """Get an item by its database ID."""
        return self._session.query(Item).filter(Item.id == item_id).first()

    def get_item_by_external_id(self, external_id: str) -> Item | None:
        """Get an item by its external system ID."""
        return (
            self._session.query(Item)
            .filter(Item.external_id == external_id)
            .first()
        )

    def create_item(self, external_item: ExternalItem) -> Item:
        """Create a new item from external data."""
        logger.info(f"Creating item: {external_item.id}")

        item = Item(
            external_id=external_item.id,
            name=external_item.name,
            status=self._normalize_status(external_item.status),
        )
        self._session.add(item)
        self._session.commit()
        self._session.refresh(item)

        logger.info(f"Created item: id={item.id}")
        return item

    def update_item(self, item: Item, external_item: ExternalItem) -> Item:
        """Update an existing item from external data."""
        item.name = external_item.name
        item.status = self._normalize_status(external_item.status)
        self._session.commit()
        self._session.refresh(item)
        return item

    def sync_item(self, external_id: str) -> Item:
        """Sync an item from the external system."""
        if self._external_client is None:
            raise ValueError("External client not configured")

        external_item = self._external_client.get_item(external_id)
        existing = self.get_item_by_external_id(external_id)

        if existing:
            return self.update_item(existing, external_item)
        return self.create_item(external_item)

    @staticmethod
    def _normalize_status(status: str) -> str:
        """Normalize external status to internal status."""
        status_lower = status.lower()
        status_map = {
            "active": "active",
            "pending": "pending",
            "completed": "completed",
            "done": "completed",
        }
        return status_map.get(status_lower, status_lower.replace(" ", "_"))
```

---

## 5. Test Patterns

### conftest.py Fixtures

```python
import logging
import os
from unittest.mock import patch

import pytest
from main import create_app


@pytest.fixture(autouse=True)
def setup_test_env():
    """Set up test environment variables."""
    with patch.dict(
        os.environ,
        {
            "CONNECT_DEBUG": "false",
            "CONNECT_LOG_LEVEL": "WARNING",
            "DATABASE_URL": "sqlite:///:memory:",
        },
    ):
        yield


@pytest.fixture
def app():
    """Create test Flask application."""
    logging.getLogger().setLevel(logging.CRITICAL)

    # Reset global engine for fresh test database
    import src.connect.database as db_module
    db_module._engine = None
    db_module._session_factory = None

    app = create_app()
    app.config.update({"TESTING": True})
    return app


@pytest.fixture
def client(app):
    """Flask test client."""
    return app.test_client()


@pytest.fixture
def db_session(app):
    """Database session for service tests."""
    from src.connect.database import get_session

    session = get_session()
    yield session
    session.rollback()
    session.close()


@pytest.fixture
def mock_external_client():
    """Mock external client."""
    from src.connect.clients.external import MockExternalClient
    return MockExternalClient()
```

### API Test Pattern

```python
class TestHealthEndpoint:
    """Tests for health check endpoint."""

    def test_health_returns_200(self, client):
        """Test health endpoint returns healthy status."""
        response = client.get("/health")

        assert response.status_code == 200
        data = response.get_json()
        assert data["status"] == "healthy"
        assert data["service"] == "connect"


class TestItemEndpoints:
    """Tests for item API endpoints."""

    def test_create_item_success(self, client):
        """Test creating an item."""
        response = client.post(
            "/api/v1/items",
            json={
                "name": "Test Item",
                "description": "Test description",
            },
            content_type="application/json",
        )

        assert response.status_code == 201
        data = response.get_json()
        assert data["success"] is True

    def test_create_item_invalid_json(self, client):
        """Test creating item with invalid content type."""
        response = client.post(
            "/api/v1/items",
            data="not json",
            content_type="text/plain",
        )

        assert response.status_code == 400
        data = response.get_json()
        assert data["code"] == "INVALID_CONTENT_TYPE"

    def test_get_item_not_found(self, client):
        """Test getting non-existent item."""
        response = client.get("/api/v1/items/99999")

        assert response.status_code == 404
        data = response.get_json()
        assert data["code"] == "NOT_FOUND"
```

### Service Test Pattern

```python
import uuid
import pytest
from src.connect.clients.external import ExternalItem, MockExternalClient
from src.connect.services.item import ItemService


class TestItemService:
    """Tests for ItemService."""

    @pytest.fixture
    def external_client(self):
        """Create a mock external client."""
        return MockExternalClient()

    @pytest.fixture
    def service(self, db_session, external_client):
        """Create an ItemService."""
        return ItemService(db_session, external_client)

    def test_create_item(self, service, db_session):
        """Test creating an item from external data."""
        unique_id = uuid.uuid4().hex[:8]
        external_item = ExternalItem(
            id=f"ext-{unique_id}",
            name="Test Item",
            status="Active",
        )

        item = service.create_item(external_item)

        assert item is not None
        assert item.id is not None
        assert item.external_id == f"ext-{unique_id}"
        assert item.status == "active"

    def test_get_item_by_id(self, service, db_session):
        """Test getting item by database ID."""
        # Create item first
        unique_id = uuid.uuid4().hex[:8]
        external_item = ExternalItem(
            id=f"ext-{unique_id}",
            name="Test Item",
            status="Active",
        )
        created = service.create_item(external_item)

        # Get by ID
        item = service.get_item_by_id(created.id)

        assert item is not None
        assert item.id == created.id

    def test_get_item_by_id_not_found(self, service):
        """Test getting non-existent item."""
        item = service.get_item_by_id(99999)

        assert item is None

    def test_sync_item_creates_new(self, service, external_client):
        """Test syncing creates new item if not exists."""
        unique_id = uuid.uuid4().hex[:8]
        external_client.add_mock_item(
            ExternalItem(
                id=f"sync-{unique_id}",
                name="Sync Test",
                status="Pending",
            )
        )

        item = service.sync_item(f"sync-{unique_id}")

        assert item is not None
        assert item.external_id == f"sync-{unique_id}"

    def test_sync_item_updates_existing(self, service, external_client, db_session):
        """Test syncing updates existing item."""
        unique_id = uuid.uuid4().hex[:8]
        external_id = f"sync-{unique_id}"

        # Create initial item
        external_client.add_mock_item(
            ExternalItem(id=external_id, name="Original", status="Active")
        )
        original = service.sync_item(external_id)

        # Update mock and sync again
        external_client._items[external_id] = ExternalItem(
            id=external_id, name="Updated", status="Completed"
        )
        updated = service.sync_item(external_id)

        assert updated.id == original.id
        assert updated.name == "Updated"
        assert updated.status == "completed"
```

---

## 6. Configuration Pattern

### Pydantic Config Classes

```python
import os
from pydantic import BaseModel, Field


class ServerConfig(BaseModel):
    """Server configuration settings."""

    host: str = Field(default="0.0.0.0", description="Host to bind to")
    port: int = Field(default=5001, description="Port to bind to")
    debug: bool = Field(default=False, description="Enable debug mode")
    log_level: str = Field(default="INFO", description="Logging level")

    @classmethod
    def from_env(cls) -> "ServerConfig":
        """Create configuration from environment variables."""
        return cls(
            host=os.getenv("CONNECT_HOST", "0.0.0.0"),
            port=int(os.getenv("CONNECT_PORT", "5001")),
            debug=os.getenv("CONNECT_DEBUG", "false").lower() == "true",
            log_level=os.getenv("CONNECT_LOG_LEVEL", "INFO").upper(),
        )


class IntegrationConfig(BaseModel):
    """External service configuration."""

    api_token: str | None = Field(default=None, description="API token")
    api_base_url: str | None = Field(default=None, description="API base URL")

    @classmethod
    def from_env(cls) -> "IntegrationConfig":
        """Create configuration from environment variables."""
        return cls(
            api_token=os.getenv("API_TOKEN"),
            api_base_url=os.getenv("API_BASE_URL"),
        )


# Global instances (initialized once)
SERVER_CONFIG = ServerConfig.from_env()
INTEGRATION_CONFIG = IntegrationConfig.from_env()


def get_server_config() -> ServerConfig:
    """Get server configuration."""
    return SERVER_CONFIG


def get_integration_config() -> IntegrationConfig:
    """Get integration configuration."""
    return INTEGRATION_CONFIG
```

---

## 7. New Feature Checklist

### Adding a New Model

- [ ] Create `{model}.py` in `src/connect/models/`
- [ ] Inherit from `Base` (SQLAlchemy declarative base)
- [ ] Add `__tablename__` attribute
- [ ] Add columns with proper types and constraints
- [ ] Add `to_dict()` method for serialization
- [ ] Add relationship definitions if needed
- [ ] Register in `src/connect/models/__init__.py`
- [ ] Import in `src/connect/database.py` for table creation

### Adding a New Client

- [ ] Create `{client}.py` in `src/connect/clients/`
- [ ] Define dataclass for API response mapping
- [ ] Create Protocol interface (for duck typing)
- [ ] Create ABC interface (for inheritance)
- [ ] Implement real client using `httpx`
- [ ] Implement mock client for testing
- [ ] Create factory function for dependency injection
- [ ] Add configuration fields to `IntegrationConfig`
- [ ] Export in `src/connect/clients/__init__.py`

### Adding a New Service

- [ ] Create `{service}.py` in `src/connect/services/`
- [ ] Accept `Session` and client interfaces in constructor
- [ ] Implement business logic methods
- [ ] Use logging for important operations
- [ ] Handle errors gracefully
- [ ] Export in `src/connect/services/__init__.py`

### Adding a New Endpoint

- [ ] Create Pydantic request/response models in `main.py`
- [ ] Add route with proper HTTP method
- [ ] Validate content type for POST/PUT requests
- [ ] Parse request with Pydantic validation
- [ ] Instantiate service with `g.db_session` and clients
- [ ] Call service method
- [ ] Map result to response model
- [ ] Return `jsonify(response.model_dump())` with status code
- [ ] Handle exceptions with error responses

### Adding Tests

- [ ] Add fixtures in `tests/conftest.py` if needed
- [ ] Create `tests/test_{feature}.py`
- [ ] Use unique IDs (`uuid.uuid4().hex[:8]`) to avoid collisions
- [ ] Test success cases
- [ ] Test error cases (not found, validation errors)
- [ ] Test edge cases

---

## 8. Common Imports

```python
# Pydantic (request/response models)
from pydantic import BaseModel, Field, ValidationError

# Flask
from flask import Flask, Response, g, jsonify, request

# SQLAlchemy
from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Session, relationship

# Typing
from typing import Any, Protocol, runtime_checkable

# Dataclasses (for API DTOs)
from dataclasses import dataclass, field

# HTTP Client
import httpx

# Testing
import pytest
from unittest.mock import patch
import uuid
```

---

## See Also

- [README.md](../README.md) - Project overview and quick start
- [Makefile](../Makefile) - Development automation commands
