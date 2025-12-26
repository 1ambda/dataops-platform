"""Logging configuration for the SQL parser service."""

from __future__ import annotations

import logging
import sys
from typing import Any

from .config import get_server_config


def setup_logging() -> None:
    """Set up logging configuration."""
    config = get_server_config()
    
    # Create formatter
    formatter = logging.Formatter(
        fmt="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    
    # Create console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    
    # Configure root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, config.log_level))
    root_logger.addHandler(console_handler)
    
    # Configure Flask logger
    flask_logger = logging.getLogger("werkzeug")
    flask_logger.setLevel(logging.WARNING if not config.debug else logging.INFO)


def get_logger(name: str) -> logging.Logger:
    """Get a logger with the specified name."""
    return logging.getLogger(name)