"""Jira ticket monitoring service.

Monitors Jira for new tickets and stores them in the local database.
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from src.connect.clients.jira import JiraClientInterface, JiraIssue, JiraWebhookEvent
from src.connect.logging_config import get_logger
from src.connect.models.jira import JiraTicket

if TYPE_CHECKING:
    pass

logger = get_logger(__name__)


class JiraMonitorService:
    """Service for monitoring and storing Jira tickets.

    This service handles:
    - Processing Jira webhook events
    - Polling for new tickets (if webhooks are not available)
    - Storing ticket metadata in the local database
    """

    def __init__(
        self,
        session: Session,
        jira_client: JiraClientInterface,
    ) -> None:
        """Initialize the Jira monitor service.

        Args:
            session: SQLAlchemy database session
            jira_client: Jira API client (real or mock)
        """
        self._session = session
        self._jira_client = jira_client

    def process_webhook_event(self, payload: dict) -> JiraTicket | None:
        """Process an incoming Jira webhook event.

        Handles issue created and updated events.

        Args:
            payload: Raw webhook payload from Jira

        Returns:
            JiraTicket if created/updated, None if event was ignored
        """
        event = JiraWebhookEvent.from_webhook_payload(payload)
        webhook_event = event.webhook_event

        logger.info(
            f"Processing Jira webhook event: {webhook_event} for {event.issue.key}"
        )

        if webhook_event == "jira:issue_created":
            return self._handle_issue_created(event)
        elif webhook_event == "jira:issue_updated":
            return self._handle_issue_updated(event)
        else:
            logger.debug(f"Ignoring event type: {webhook_event}")
            return None

    def _handle_issue_created(self, event: JiraWebhookEvent) -> JiraTicket:
        """Handle issue created event.

        Args:
            event: Parsed webhook event

        Returns:
            Created JiraTicket
        """
        issue = event.issue
        logger.info(f"Creating new ticket record for {issue.key}")

        ticket = self._create_ticket_from_issue(issue)
        self._session.add(ticket)
        self._session.commit()
        self._session.refresh(ticket)

        logger.info(f"Created ticket record: id={ticket.id}, key={ticket.jira_key}")
        return ticket

    def _handle_issue_updated(self, event: JiraWebhookEvent) -> JiraTicket | None:
        """Handle issue updated event.

        Args:
            event: Parsed webhook event

        Returns:
            Updated JiraTicket or None if not found
        """
        issue = event.issue
        logger.info(f"Updating ticket record for {issue.key}")

        ticket = self.get_ticket_by_key(issue.key)
        if ticket is None:
            # Ticket doesn't exist yet, create it
            logger.info(f"Ticket {issue.key} not found, creating new record")
            return self._handle_issue_created(event)

        # Update existing ticket
        self._update_ticket_from_issue(ticket, issue)
        self._session.commit()
        self._session.refresh(ticket)

        logger.info(f"Updated ticket record: id={ticket.id}, key={ticket.jira_key}")
        return ticket

    def _create_ticket_from_issue(self, issue: JiraIssue) -> JiraTicket:
        """Create a JiraTicket from a JiraIssue.

        Args:
            issue: JiraIssue from API

        Returns:
            New JiraTicket entity (not yet persisted)
        """
        return JiraTicket(
            jira_id=issue.id,
            jira_key=issue.key,
            project_key=issue.project_key,
            summary=issue.summary,
            description=issue.description,
            status=self._normalize_status(issue.status),
            priority=issue.priority,
            issue_type=issue.issue_type,
            assignee_id=issue.assignee_id,
            assignee_name=issue.assignee_name,
            reporter_id=issue.reporter_id,
            reporter_name=issue.reporter_name,
            labels=",".join(issue.labels) if issue.labels else None,
            created_at_jira=issue.created,
            updated_at_jira=issue.updated,
        )

    def _update_ticket_from_issue(
        self, ticket: JiraTicket, issue: JiraIssue
    ) -> None:
        """Update a JiraTicket from a JiraIssue.

        Args:
            ticket: Existing JiraTicket to update
            issue: JiraIssue with new data
        """
        ticket.summary = issue.summary
        ticket.description = issue.description
        ticket.status = self._normalize_status(issue.status)
        ticket.priority = issue.priority
        ticket.issue_type = issue.issue_type
        ticket.assignee_id = issue.assignee_id
        ticket.assignee_name = issue.assignee_name
        ticket.reporter_id = issue.reporter_id
        ticket.reporter_name = issue.reporter_name
        ticket.labels = ",".join(issue.labels) if issue.labels else None
        ticket.updated_at_jira = issue.updated

    @staticmethod
    def _normalize_status(status: str) -> str:
        """Normalize Jira status to internal status.

        Args:
            status: Jira status string

        Returns:
            Normalized status string
        """
        status_lower = status.lower()
        if status_lower in ("open", "to do", "new", "backlog"):
            return "open"
        elif status_lower in ("in progress", "in review", "dev", "development"):
            return "in_progress"
        elif status_lower in ("resolved", "done", "complete", "completed"):
            return "resolved"
        elif status_lower in ("closed", "cancelled", "rejected"):
            return "closed"
        return status_lower.replace(" ", "_")

    def get_ticket_by_key(self, jira_key: str) -> JiraTicket | None:
        """Get a ticket by its Jira key.

        Args:
            jira_key: The Jira issue key (e.g., "PROJ-123")

        Returns:
            JiraTicket or None if not found
        """
        return (
            self._session.query(JiraTicket)
            .filter(JiraTicket.jira_key == jira_key)
            .first()
        )

    def get_ticket_by_id(self, ticket_id: int) -> JiraTicket | None:
        """Get a ticket by its local database ID.

        Args:
            ticket_id: Local database ID

        Returns:
            JiraTicket or None if not found
        """
        return self._session.query(JiraTicket).filter(JiraTicket.id == ticket_id).first()

    def get_tickets_by_project(
        self, project_key: str, status: str | None = None
    ) -> list[JiraTicket]:
        """Get all tickets for a project.

        Args:
            project_key: Jira project key
            status: Optional status filter

        Returns:
            List of JiraTicket entities
        """
        query = self._session.query(JiraTicket).filter(
            JiraTicket.project_key == project_key
        )
        if status:
            query = query.filter(JiraTicket.status == status)
        return query.order_by(JiraTicket.created_at.desc()).all()

    def poll_for_new_tickets(
        self,
        project_key: str | None = None,
        since_minutes: int = 60,
    ) -> list[JiraTicket]:
        """Poll Jira for recently created tickets.

        This is an alternative to webhooks for environments where
        webhooks are not available.

        Args:
            project_key: Optional project to filter by
            since_minutes: Look back this many minutes

        Returns:
            List of newly stored JiraTicket entities
        """
        logger.info(
            f"Polling for new tickets (project={project_key}, since_minutes={since_minutes})"
        )

        issues = self._jira_client.get_recently_created_issues(
            project_key=project_key,
            since_minutes=since_minutes,
        )

        new_tickets = []
        for issue in issues:
            existing = self.get_ticket_by_key(issue.key)
            if existing is None:
                ticket = self._create_ticket_from_issue(issue)
                self._session.add(ticket)
                new_tickets.append(ticket)
                logger.info(f"Found new ticket: {issue.key}")

        if new_tickets:
            self._session.commit()
            for ticket in new_tickets:
                self._session.refresh(ticket)

        logger.info(f"Poll complete: found {len(new_tickets)} new tickets")
        return new_tickets

    def sync_ticket(self, jira_key: str) -> JiraTicket:
        """Sync a single ticket from Jira.

        Fetches the latest data from Jira and updates the local record.

        Args:
            jira_key: The Jira issue key

        Returns:
            Updated JiraTicket
        """
        logger.info(f"Syncing ticket {jira_key}")

        issue = self._jira_client.get_issue(jira_key)
        existing = self.get_ticket_by_key(jira_key)

        if existing:
            self._update_ticket_from_issue(existing, issue)
            self._session.commit()
            self._session.refresh(existing)
            return existing
        else:
            ticket = self._create_ticket_from_issue(issue)
            self._session.add(ticket)
            self._session.commit()
            self._session.refresh(ticket)
            return ticket
