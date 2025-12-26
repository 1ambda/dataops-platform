"""Logging configuration for the Connect service."""

from __future__ import annotations

import logging
import sys

from src.connect.config import get_server_config


def setup_logging() -> None:
    """Set up logging configuration for the application."""
    config = get_server_config()
    log_level = getattr(logging, config.log_level.upper(), logging.INFO)

    # Configure root logger
    logging.basicConfig(
        level=log_level,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        handlers=[
            logging.StreamHandler(sys.stdout),
        ],
    )

    # Set log levels for noisy libraries
    logging.getLogger("urllib3").setLevel(logging.WARNING)
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("sqlalchemy.engine").setLevel(
        logging.DEBUG if config.debug else logging.WARNING
    )


def get_logger(name: str) -> logging.Logger:
    """Get a logger instance with the given name.

    Args:
        name: The name for the logger (typically __name__)

    Returns:
        A configured logger instance
    """
    return logging.getLogger(name)


class LoggerMixin:
    """Mixin class that provides a logger instance."""

    @property
    def logger(self) -> logging.Logger:
        """Get logger for this class."""
        return get_logger(self.__class__.__name__)
