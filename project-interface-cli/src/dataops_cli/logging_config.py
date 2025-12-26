"""Logging configuration for the DataOps CLI."""

from __future__ import annotations

import logging
import sys
from typing import Any

from rich.console import Console
from rich.logging import RichHandler

from .config import get_cli_config


def setup_logging() -> None:
    """Set up logging configuration with Rich formatting."""
    config = get_cli_config()
    
    # Configure Rich console
    console = Console(stderr=True)
    
    # Create Rich handler
    rich_handler = RichHandler(
        console=console,
        show_path=config.debug,
        show_time=config.debug,
        rich_tracebacks=True,
        tracebacks_show_locals=config.debug,
    )
    
    # Set log format
    if config.debug:
        log_format = "%(name)s - %(funcName)s:%(lineno)d - %(message)s"
    else:
        log_format = "%(message)s"
    
    rich_handler.setFormatter(logging.Formatter(log_format))
    
    # Configure root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, config.log_level))
    root_logger.addHandler(rich_handler)
    
    # Silence noisy loggers
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)


def get_logger(name: str) -> logging.Logger:
    """Get a logger with the specified name.
    
    Args:
        name: Logger name
        
    Returns:
        Logger instance
    """
    return logging.getLogger(name)