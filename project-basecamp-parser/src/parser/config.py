"""Configuration management for the SQL parser service."""

from __future__ import annotations

import os
from typing import Final

from pydantic import BaseModel, Field


class ServerConfig(BaseModel):
    """Server configuration settings."""

    host: str = Field(default="0.0.0.0", description="Host to bind to")
    port: int = Field(default=5000, description="Port to bind to")
    debug: bool = Field(default=False, description="Enable debug mode")
    log_level: str = Field(default="INFO", description="Logging level")

    @classmethod
    def from_env(cls) -> ServerConfig:
        """Create configuration from environment variables."""
        return cls(
            host=os.getenv("PARSER_HOST", "0.0.0.0"),
            port=int(os.getenv("PARSER_PORT", "5000")),
            debug=os.getenv("PARSER_DEBUG", "false").lower() == "true",
            log_level=os.getenv("PARSER_LOG_LEVEL", "INFO").upper(),
        )


class SQLParserConfig(BaseModel):
    """SQL parser configuration."""

    dialect: str = Field(default="presto", description="SQL dialect for parsing")
    max_query_length: int = Field(default=100000, description="Maximum query length")
    timeout_seconds: int = Field(default=30, description="Query parsing timeout")


# Global configuration instances
SERVER_CONFIG: Final[ServerConfig] = ServerConfig.from_env()
PARSER_CONFIG: Final[SQLParserConfig] = SQLParserConfig()


def get_server_config() -> ServerConfig:
    """Get server configuration."""
    return SERVER_CONFIG


def get_parser_config() -> SQLParserConfig:
    """Get parser configuration."""
    return PARSER_CONFIG