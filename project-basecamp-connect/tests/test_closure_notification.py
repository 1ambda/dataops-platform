"""Tests for ticket closure notification functionality."""

import pytest

from src.connect.clients.jira import MockJiraClient
from src.connect.clients.slack import MockSlackClient
from src.connect.models.closure import TicketClosureNotification
from src.connect.models.jira import JiraTicket
from src.connect.models.linking import JiraSlackLink
from src.connect.models.slack import SlackThread
from src.connect.services.jira_slack_integration import JiraSlackIntegrationService


class TestClosureNotification:
    """Tests for ticket closure notification."""

    @pytest.fixture
    def jira_client(self):
        """Create a mock Jira client."""
        return MockJiraClient()

    @pytest.fixture
    def slack_client(self):
        """Create a mock Slack client."""
        return MockSlackClient()

    @pytest.fixture
    def service(self, db_session, jira_client, slack_client):
        """Create a JiraSlackIntegrationService."""
        return JiraSlackIntegrationService(
            session=db_session,
            jira_client=jira_client,
            slack_client=slack_client,
            notification_channel="C0003",
        )

    @pytest.fixture
    def linked_ticket(self, service, db_session):
        """Create a ticket with a linked Slack thread."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"CLOSE-{unique_id}"

        # Create the ticket via webhook
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"close-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Closure notification test ticket",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "CLOSE"},
                },
            },
        }
        result = service.handle_jira_webhook(payload)
        assert result["success"] is True

        ticket = (
            db_session.query(JiraTicket).filter_by(jira_key=issue_key).first()
        )
        return ticket

    def test_send_closure_notification(
        self, service, linked_ticket, slack_client, db_session
    ):
        """Test sending a closure notification."""
        # Update ticket status to Done
        linked_ticket.status = "Done"
        db_session.commit()

        # Get link
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()

        result = service.send_closure_notification(linked_ticket, link)

        assert result["success"] is True
        assert result["message_sent"] is True
        assert result["reaction_added"] is True
        assert result["emoji"] == "white_check_mark"

        # Verify notification was stored in DB
        notification = (
            db_session.query(TicketClosureNotification)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        assert notification is not None
        assert notification.jira_status == "Done"
        assert notification.reaction_added == 1

        # Verify reaction was added to Slack
        reactions = slack_client.get_reactions(
            thread.channel_id, thread.parent_message_ts
        )
        assert "white_check_mark" in reactions

    def test_closure_notification_idempotent(
        self, service, linked_ticket, db_session
    ):
        """Test that closure notification is not sent twice."""
        linked_ticket.status = "Closed"
        db_session.commit()

        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )

        # First notification
        result1 = service.send_closure_notification(linked_ticket, link)
        assert result1["success"] is True
        assert result1.get("already_notified") is not True

        # Second notification - should skip
        result2 = service.send_closure_notification(linked_ticket, link)
        assert result2["success"] is True
        assert result2.get("already_notified") is True

        # Verify only one notification in DB
        notifications = (
            db_session.query(TicketClosureNotification)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .all()
        )
        assert len(notifications) == 1

    def test_closure_notification_via_webhook(
        self, service, linked_ticket, db_session, slack_client
    ):
        """Test that closure notification is triggered via webhook update."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # First create a new ticket
        create_payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"webhook-close-{unique_id}",
                "key": f"WCLOSE-{unique_id}",
                "fields": {
                    "summary": "Webhook closure test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "WCLOSE"},
                },
            },
        }
        create_result = service.handle_jira_webhook(create_payload)
        assert create_result["success"] is True

        # Now update to Done status
        update_payload = {
            "webhookEvent": "jira:issue_updated",
            "issue": {
                "id": f"webhook-close-{unique_id}",
                "key": f"WCLOSE-{unique_id}",
                "fields": {
                    "summary": "Webhook closure test",
                    "status": {"name": "Done"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "WCLOSE"},
                },
            },
        }
        update_result = service.handle_jira_webhook(update_payload)

        assert update_result["success"] is True
        assert "closure_notification" in update_result
        assert update_result["closure_notification"]["success"] is True

    def test_closure_notification_format(self, service, linked_ticket):
        """Test closure message formatting."""
        linked_ticket.status = "Done"

        message = service._format_closure_message(linked_ticket)

        assert ":white_check_mark:" in message
        assert "Ticket Closed" in message
        assert linked_ticket.jira_key in message
        assert "Done" in message

    def test_closure_with_custom_status(
        self, service, linked_ticket, db_session
    ):
        """Test closure notification with a custom closed status."""
        # 'Closed' is also a valid terminal status
        linked_ticket.status = "Closed"
        db_session.commit()

        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )

        result = service.send_closure_notification(linked_ticket, link)

        assert result["success"] is True
        assert result["status"] == "Closed"


class TestClosureNotificationModel:
    """Tests for the TicketClosureNotification model."""

    def test_closure_notification_creation(self, db_session):
        """Test creating a TicketClosureNotification record."""
        import random
        from datetime import datetime

        unique_id = random.randint(100000, 999999)
        notification = TicketClosureNotification(
            jira_ticket_id=unique_id,
            slack_thread_id=unique_id,
            jira_status="Done",
            notification_message_ts=f"{unique_id}.000001",
            reaction_added=1,
            reaction_emoji="white_check_mark",
            notified_at=datetime.utcnow(),
        )
        db_session.add(notification)
        db_session.commit()
        db_session.refresh(notification)

        assert notification.id is not None
        assert notification.jira_status == "Done"
        assert notification.reaction_added == 1
        assert notification.reaction_emoji == "white_check_mark"

    def test_closure_notification_to_dict(self, db_session):
        """Test TicketClosureNotification.to_dict() method."""
        import random
        from datetime import datetime

        unique_id = random.randint(200000, 299999)
        now = datetime.utcnow()
        notification = TicketClosureNotification(
            jira_ticket_id=unique_id,
            slack_thread_id=unique_id,
            jira_status="Closed",
            notification_message_ts=f"{unique_id}.000001",
            reaction_added=0,
            notified_at=now,
        )
        db_session.add(notification)
        db_session.commit()
        db_session.refresh(notification)

        result = notification.to_dict()

        assert result["jira_status"] == "Closed"
        assert result["reaction_added"] is False
        assert result["notification_message_ts"] == f"{unique_id}.000001"

    def test_closure_notification_repr(self, db_session):
        """Test TicketClosureNotification __repr__ method."""
        import random

        unique_id = random.randint(300000, 399999)
        notification = TicketClosureNotification(
            jira_ticket_id=unique_id,
            slack_thread_id=unique_id,
            jira_status="Done",
        )
        db_session.add(notification)
        db_session.commit()
        db_session.refresh(notification)

        repr_str = repr(notification)

        assert "TicketClosureNotification" in repr_str
        assert "Done" in repr_str


class TestIntegrationConfig:
    """Tests for closure-related configuration."""

    def test_default_closed_statuses(self):
        """Test default closed statuses configuration."""
        from src.connect.config import IntegrationConfig

        config = IntegrationConfig()

        assert "Done" in config.jira_closed_statuses
        assert "Closed" in config.jira_closed_statuses
        assert config.jira_closed_emoji == "white_check_mark"

    def test_is_closed_status(self):
        """Test is_closed_status helper method."""
        from src.connect.config import IntegrationConfig

        config = IntegrationConfig(
            jira_closed_statuses=["Done", "Closed", "Resolved"]
        )

        assert config.is_closed_status("Done") is True
        assert config.is_closed_status("Closed") is True
        assert config.is_closed_status("Resolved") is True
        assert config.is_closed_status("Open") is False
        assert config.is_closed_status("In Progress") is False
