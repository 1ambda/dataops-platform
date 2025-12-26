"""Database configuration and session management for the Connect service.

Supports both SQLite (local development) and MySQL (production).
Configure via DATABASE_URL environment variable:
- SQLite: sqlite:///./connect.db
- MySQL: mysql+pymysql://user:password@localhost:3306/connect
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from src.connect.config import get_database_config
from src.connect.logging_config import get_logger

# Import all models to ensure they are registered with Base
from src.connect.models import (
    Base,
    IntegrationLog,
    JiraSlackLink,
    JiraTicket,
    ServiceMapping,
    SlackMessage,
    SlackThread,
)

if TYPE_CHECKING:
    from sqlalchemy.engine import Engine

logger = get_logger(__name__)

# Re-export models for backward compatibility
__all__ = [
    "Base",
    "IntegrationLog",
    "ServiceMapping",
    "JiraTicket",
    "SlackMessage",
    "SlackThread",
    "JiraSlackLink",
    "get_engine",
    "get_session_factory",
    "get_session",
    "init_db",
    "reset_db",
]

# Global engine and session factory
_engine: Engine | None = None
_session_factory: sessionmaker[Session] | None = None


def get_engine() -> Engine:
    """Get or create the database engine."""
    global _engine
    if _engine is None:
        config = get_database_config()
        logger.info(f"Creating database engine with URL: {_mask_url(config.url)}")
        _engine = create_engine(config.url, echo=config.echo)
    return _engine


def get_session_factory() -> sessionmaker[Session]:
    """Get or create the session factory."""
    global _session_factory
    if _session_factory is None:
        engine = get_engine()
        _session_factory = sessionmaker(bind=engine)
    return _session_factory


def get_session() -> Session:
    """Create a new database session."""
    factory = get_session_factory()
    return factory()


def init_db() -> None:
    """Initialize the database by creating all tables."""
    engine = get_engine()
    logger.info("Initializing database tables...")
    Base.metadata.create_all(engine)
    logger.info("Database tables created successfully")


def reset_db() -> None:
    """Reset the database by dropping and recreating all tables.

    WARNING: This will delete all data!
    """
    engine = get_engine()
    logger.warning("Resetting database - all data will be lost!")
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    logger.info("Database reset complete")


def _mask_url(url: str) -> str:
    """Mask sensitive parts of database URL for logging."""
    if "@" in url:
        # Mask password in connection string
        parts = url.split("@")
        if ":" in parts[0]:
            prefix = parts[0].rsplit(":", 1)[0]
            return f"{prefix}:****@{parts[1]}"
    return url
