"""Configuration management for the Connect service."""

from __future__ import annotations

import os
from typing import Final

from pydantic import BaseModel, Field


class ServerConfig(BaseModel):
    """Server configuration settings."""

    host: str = Field(default="0.0.0.0", description="Host to bind to")
    port: int = Field(default=5001, description="Port to bind to")
    debug: bool = Field(default=False, description="Enable debug mode")
    log_level: str = Field(default="INFO", description="Logging level")

    @classmethod
    def from_env(cls) -> ServerConfig:
        """Create configuration from environment variables."""
        return cls(
            host=os.getenv("CONNECT_HOST", "0.0.0.0"),
            port=int(os.getenv("CONNECT_PORT", "5001")),
            debug=os.getenv("CONNECT_DEBUG", "false").lower() == "true",
            log_level=os.getenv("CONNECT_LOG_LEVEL", "INFO").upper(),
        )


class DatabaseConfig(BaseModel):
    """Database configuration."""

    url: str = Field(
        default="sqlite:///./connect.db",
        description="Database connection URL",
    )
    echo: bool = Field(default=False, description="Echo SQL queries")

    @classmethod
    def from_env(cls) -> DatabaseConfig:
        """Create configuration from environment variables."""
        return cls(
            url=os.getenv("DATABASE_URL", "sqlite:///./connect.db"),
            echo=os.getenv("DATABASE_ECHO", "false").lower() == "true",
        )


class IntegrationConfig(BaseModel):
    """External service integration configuration."""

    github_token: str | None = Field(default=None, description="GitHub API token")
    jira_api_token: str | None = Field(default=None, description="Jira API token")
    jira_base_url: str | None = Field(default=None, description="Jira base URL")
    slack_bot_token: str | None = Field(default=None, description="Slack bot token")
    slack_signing_secret: str | None = Field(
        default=None, description="Slack signing secret"
    )

    # Ticket closure notification settings
    # Note: These should match NORMALIZED status values (see JiraMonitorService._normalize_status)
    # "Done" -> "resolved", "Closed" -> "closed"
    jira_closed_statuses: list[str] = Field(
        default=["resolved", "closed", "Done", "Closed"],
        description="Jira statuses that trigger closure notifications",
    )
    jira_closed_emoji: str = Field(
        default="white_check_mark",
        description="Emoji to add when ticket is closed",
    )

    @classmethod
    def from_env(cls) -> IntegrationConfig:
        """Create configuration from environment variables."""
        # Parse closed statuses from comma-separated string
        closed_statuses_str = os.getenv("JIRA_CLOSED_STATUSES", "resolved,closed,Done,Closed")
        closed_statuses = [s.strip() for s in closed_statuses_str.split(",") if s.strip()]

        return cls(
            github_token=os.getenv("GITHUB_TOKEN"),
            jira_api_token=os.getenv("JIRA_API_TOKEN"),
            jira_base_url=os.getenv("JIRA_BASE_URL"),
            slack_bot_token=os.getenv("SLACK_BOT_TOKEN"),
            slack_signing_secret=os.getenv("SLACK_SIGNING_SECRET"),
            jira_closed_statuses=closed_statuses,
            jira_closed_emoji=os.getenv("JIRA_CLOSED_EMOJI", "white_check_mark"),
        )

    def is_closed_status(self, status: str) -> bool:
        """Check if a status is a terminal/closed status."""
        return status in self.jira_closed_statuses


# Global configuration instances
SERVER_CONFIG: Final[ServerConfig] = ServerConfig.from_env()
DATABASE_CONFIG: Final[DatabaseConfig] = DatabaseConfig.from_env()
INTEGRATION_CONFIG: Final[IntegrationConfig] = IntegrationConfig.from_env()


def get_server_config() -> ServerConfig:
    """Get server configuration."""
    return SERVER_CONFIG


def get_database_config() -> DatabaseConfig:
    """Get database configuration."""
    return DATABASE_CONFIG


def get_integration_config() -> IntegrationConfig:
    """Get integration configuration."""
    return INTEGRATION_CONFIG
