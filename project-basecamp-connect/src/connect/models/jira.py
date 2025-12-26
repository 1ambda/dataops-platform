"""Jira ticket database model."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, Enum, Integer, String, Text
from sqlalchemy.orm import relationship

from src.connect.models.base import Base


class TicketStatus(str):
    """Jira ticket status values."""

    OPEN = "open"
    IN_PROGRESS = "in_progress"
    RESOLVED = "resolved"
    CLOSED = "closed"


class TicketPriority(str):
    """Jira ticket priority values."""

    LOWEST = "lowest"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    HIGHEST = "highest"


class JiraTicket(Base):
    """Jira ticket stored from monitoring.

    Represents a Jira ticket that has been monitored and stored in the local database.
    This allows tracking of tickets and linking them to Slack threads.

    Attributes:
        id: Local database primary key
        jira_id: Jira internal ticket ID (e.g., "10001")
        jira_key: Jira ticket key (e.g., "PROJ-123")
        project_key: Jira project key (e.g., "PROJ")
        summary: Ticket summary/title
        description: Full ticket description
        status: Current ticket status
        priority: Ticket priority level
        issue_type: Type of issue (e.g., "Bug", "Story", "Task")
        assignee_id: Jira account ID of assignee
        assignee_name: Display name of assignee
        reporter_id: Jira account ID of reporter
        reporter_name: Display name of reporter
        labels: Comma-separated list of labels
        created_at_jira: When the ticket was created in Jira
        updated_at_jira: When the ticket was last updated in Jira
        created_at: When the record was created locally
        updated_at: When the record was last updated locally
    """

    __tablename__ = "jira_tickets"

    id = Column(Integer, primary_key=True, autoincrement=True)
    jira_id = Column(String(50), nullable=False, unique=True, index=True)
    jira_key = Column(String(50), nullable=False, unique=True, index=True)
    project_key = Column(String(20), nullable=False, index=True)
    summary = Column(String(500), nullable=False)
    description = Column(Text, nullable=True)
    status = Column(String(50), nullable=False, default=TicketStatus.OPEN, index=True)
    priority = Column(String(20), nullable=True, default=TicketPriority.MEDIUM)
    issue_type = Column(String(50), nullable=False, index=True)
    assignee_id = Column(String(100), nullable=True)
    assignee_name = Column(String(255), nullable=True)
    reporter_id = Column(String(100), nullable=True)
    reporter_name = Column(String(255), nullable=True)
    labels = Column(Text, nullable=True)  # Comma-separated labels
    created_at_jira = Column(DateTime, nullable=True)
    updated_at_jira = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Relationship to Slack links
    slack_links = relationship(
        "JiraSlackLink", back_populates="jira_ticket", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return (
            f"<JiraTicket(id={self.id}, key={self.jira_key}, "
            f"summary='{self.summary[:30]}...', status={self.status})>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "jira_id": self.jira_id,
            "jira_key": self.jira_key,
            "project_key": self.project_key,
            "summary": self.summary,
            "description": self.description,
            "status": self.status,
            "priority": self.priority,
            "issue_type": self.issue_type,
            "assignee_id": self.assignee_id,
            "assignee_name": self.assignee_name,
            "reporter_id": self.reporter_id,
            "reporter_name": self.reporter_name,
            "labels": self.labels.split(",") if self.labels else [],
            "created_at_jira": (
                self.created_at_jira.isoformat() if self.created_at_jira else None
            ),
            "updated_at_jira": (
                self.updated_at_jira.isoformat() if self.updated_at_jira else None
            ),
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
