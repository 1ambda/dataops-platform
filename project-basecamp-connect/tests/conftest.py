"""Test configuration and fixtures."""

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
            "CONNECT_LOG_LEVEL": "WARNING",  # Reduce log noise in tests
            "DATABASE_URL": "sqlite:///:memory:",  # Use in-memory SQLite for tests
        },
    ):
        yield


@pytest.fixture
def app():
    """Create and configure a test instance of the app."""
    # Disable logging during tests to reduce noise
    logging.getLogger().setLevel(logging.CRITICAL)

    # Reset global engine for fresh test database
    import src.connect.database as db_module

    db_module._engine = None
    db_module._session_factory = None

    app = create_app()
    app.config.update(
        {
            "TESTING": True,
        }
    )
    return app


@pytest.fixture
def client(app):
    """A test client for the app."""
    return app.test_client()


@pytest.fixture
def db_session(app):
    """Create a database session for tests.

    This fixture provides a clean database session for each test,
    which is particularly useful for service-level tests.
    """
    from src.connect.database import get_session

    session = get_session()
    yield session
    session.rollback()  # Roll back any uncommitted changes
    session.close()


@pytest.fixture
def mock_jira_client():
    """Create a mock Jira client for testing."""
    from src.connect.clients.jira import MockJiraClient

    return MockJiraClient()


@pytest.fixture
def mock_slack_client():
    """Create a mock Slack client for testing."""
    from src.connect.clients.slack import MockSlackClient

    return MockSlackClient()
