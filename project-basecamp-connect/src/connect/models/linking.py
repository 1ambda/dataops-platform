"""Jira-Slack linking model."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import relationship

from src.connect.models.base import Base


class LinkType(str):
    """Types of Jira-Slack links."""

    TICKET_THREAD = "ticket_thread"  # Jira ticket linked to Slack thread
    TICKET_NOTIFICATION = "ticket_notification"  # Jira ticket notification in channel
    COMMENT_SYNC = "comment_sync"  # Synced comments between Jira and Slack


class JiraSlackLink(Base):
    """Link between a Jira ticket and a Slack thread.

    Represents a bidirectional link between a Jira ticket and a Slack thread,
    allowing for synchronization and navigation between the two systems.

    Attributes:
        id: Local database primary key
        jira_ticket_id: Foreign key to jira_tickets table
        slack_thread_id: Foreign key to slack_threads table
        link_type: Type of link (ticket_thread, notification, etc.)
        sync_enabled: Whether bidirectional sync is enabled
        last_sync_at: When the link was last synchronized
        sync_status: Current sync status (active, paused, error)
        link_metadata: Additional JSON metadata about the link
        created_at: When the link was created
        updated_at: When the link was last updated
    """

    __tablename__ = "jira_slack_links"

    id = Column(Integer, primary_key=True, autoincrement=True)
    jira_ticket_id = Column(
        Integer, ForeignKey("jira_tickets.id"), nullable=False, index=True
    )
    slack_thread_id = Column(
        Integer, ForeignKey("slack_threads.id"), nullable=False, index=True
    )
    link_type = Column(
        String(50), nullable=False, default=LinkType.TICKET_THREAD, index=True
    )
    sync_enabled = Column(Integer, default=1)  # SQLite boolean compatibility
    last_sync_at = Column(DateTime, nullable=True)
    sync_status = Column(String(20), nullable=False, default="active")
    link_metadata = Column(Text, nullable=True)  # JSON string for extra data
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Relationships
    jira_ticket = relationship("JiraTicket", back_populates="slack_links")
    slack_thread = relationship("SlackThread", back_populates="jira_links")

    # Ensure unique combination of ticket and thread per link type
    __table_args__ = (
        UniqueConstraint(
            "jira_ticket_id", "slack_thread_id", "link_type", name="uq_jira_slack_link"
        ),
    )

    def __repr__(self) -> str:
        return (
            f"<JiraSlackLink(id={self.id}, jira_ticket_id={self.jira_ticket_id}, "
            f"slack_thread_id={self.slack_thread_id}, type={self.link_type})>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "jira_ticket_id": self.jira_ticket_id,
            "slack_thread_id": self.slack_thread_id,
            "link_type": self.link_type,
            "sync_enabled": bool(self.sync_enabled),
            "last_sync_at": (
                self.last_sync_at.isoformat() if self.last_sync_at else None
            ),
            "sync_status": self.sync_status,
            "link_metadata": self.link_metadata,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
