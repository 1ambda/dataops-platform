"""Test configuration and fixtures."""

import logging
import os
from unittest.mock import patch

import pytest

from main import create_app


@pytest.fixture(autouse=True)
def setup_test_env():
    """Set up test environment variables."""
    with patch.dict(os.environ, {
        "PARSER_DEBUG": "false",
        "PARSER_LOG_LEVEL": "WARNING",  # Reduce log noise in tests
    }):
        yield


@pytest.fixture
def app():
    """Create and configure a test instance of the app."""
    # Disable logging during tests to reduce noise
    logging.getLogger().setLevel(logging.CRITICAL)

    app = create_app()
    app.config.update({
        "TESTING": True,
    })
    return app


@pytest.fixture
def client(app):
    """A test client for the app."""
    return app.test_client()
