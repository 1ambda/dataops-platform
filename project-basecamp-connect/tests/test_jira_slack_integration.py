"""Tests for Jira-Slack integration service."""

import pytest

from src.connect.clients.jira import MockJiraClient
from src.connect.clients.slack import MockSlackClient
from src.connect.models.jira import JiraTicket
from src.connect.models.linking import JiraSlackLink
from src.connect.models.slack import SlackThread
from src.connect.services.jira_slack_integration import JiraSlackIntegrationService


class TestJiraSlackIntegrationService:
    """Tests for JiraSlackIntegrationService."""

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
            notification_channel="C0003",  # jira-notifications
        )

    def test_handle_jira_webhook_issue_created(
        self, service, db_session, slack_client
    ):
        """Test handling issue created webhook."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"int-{unique_id}",
                "key": f"INT-{unique_id}",
                "fields": {
                    "summary": "Integration test ticket",
                    "description": "Test description",
                    "status": {"name": "Open"},
                    "priority": {"name": "High"},
                    "issuetype": {"name": "Bug"},
                    "project": {"key": "INT"},
                },
            },
        }

        result = service.handle_jira_webhook(payload)

        assert result["success"] is True, f"Expected success but got: {result}"
        assert result["event"] == "jira:issue_created"
        assert "INT-" in result["issue_key"]
        assert result["ticket_id"] is not None
        assert result["thread_id"] is not None
        assert result["link_id"] is not None
        assert "thread_permalink" in result

        # Verify ticket was created
        ticket = db_session.query(JiraTicket).filter_by(jira_key=result["issue_key"]).first()
        assert ticket is not None

        # Verify thread was created
        thread = db_session.query(SlackThread).filter_by(id=result["thread_id"]).first()
        assert thread is not None

        # Verify link was created
        link = db_session.query(JiraSlackLink).filter_by(id=result["link_id"]).first()
        assert link is not None
        assert link.jira_ticket_id == ticket.id
        assert link.slack_thread_id == thread.id

    def test_handle_jira_webhook_issue_updated_with_link(
        self, service, db_session, slack_client
    ):
        """Test handling issue updated webhook for linked ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_id = f"upd-{unique_id}"
        issue_key = f"UPD-{unique_id}"

        # First create the ticket with a thread
        create_payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": issue_id,
                "key": issue_key,
                "fields": {
                    "summary": "Original summary",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "UPD"},
                },
            },
        }
        create_result = service.handle_jira_webhook(create_payload)
        assert create_result["success"] is True, f"Create failed: {create_result}"
        original_thread_id = create_result["thread_id"]

        # Clear messages for easier verification
        slack_client.clear_messages("C0003")

        # Now update the ticket
        update_payload = {
            "webhookEvent": "jira:issue_updated",
            "issue": {
                "id": issue_id,
                "key": issue_key,
                "fields": {
                    "summary": "Updated summary",
                    "status": {"name": "In Progress"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "UPD"},
                },
            },
        }
        update_result = service.handle_jira_webhook(update_payload)

        assert update_result["success"] is True
        assert update_result["thread_id"] == original_thread_id

        # Verify update message was posted
        messages = slack_client.get_all_messages("C0003")
        update_messages = [m for m in messages if "Updated" in (m.text or "")]
        assert len(update_messages) >= 1

    def test_handle_jira_webhook_issue_updated_no_link(
        self, service, db_session, slack_client
    ):
        """Test handling issue updated webhook for unlinked ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Skip creating the ticket first
        payload = {
            "webhookEvent": "jira:issue_updated",
            "issue": {
                "id": f"new-{unique_id}",
                "key": f"NEW-{unique_id}",
                "fields": {
                    "summary": "New ticket via update",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Story"},
                    "project": {"key": "NEW"},
                },
            },
        }

        result = service.handle_jira_webhook(payload)

        assert result["success"] is True
        assert result["ticket_id"] is not None
        assert result["thread_id"] is not None
        assert result["link_id"] is not None

    def test_handle_jira_webhook_ignored_event(self, service):
        """Test that unhandled events are handled gracefully."""
        payload = {
            "webhookEvent": "jira:issue_deleted",
            "issue": {"id": "10004", "key": "INT-4"},
        }

        result = service.handle_jira_webhook(payload)

        assert result["success"] is True
        assert "ignored" in result.get("message", "").lower()

    def test_create_slack_thread_for_ticket(
        self, service, db_session, slack_client
    ):
        """Test creating a Slack thread for a ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Create a ticket directly
        ticket = JiraTicket(
            jira_id=f"thrd-{unique_id}",
            jira_key=f"THREAD-{unique_id}",
            project_key="THREAD",
            summary="Test thread creation",
            description="Test description for thread",
            status="open",
            priority="High",
            issue_type="Bug",
            assignee_name="John Doe",
        )
        db_session.add(ticket)
        db_session.commit()
        db_session.refresh(ticket)

        thread, link = service.create_slack_thread_for_ticket(ticket)

        assert thread is not None
        assert thread.id is not None
        assert thread.channel_id == "C0003"
        assert thread.permalink is not None

        assert link is not None
        assert link.jira_ticket_id == ticket.id
        assert link.slack_thread_id == thread.id
        assert link.link_type == "ticket_thread"
        assert link.sync_enabled == 1

    def test_create_slack_thread_custom_channel(
        self, service, db_session, slack_client
    ):
        """Test creating a thread in a custom channel."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        ticket = JiraTicket(
            jira_id=f"cust-{unique_id}",
            jira_key=f"CUST-{unique_id}",
            project_key="CUST",
            summary="Custom channel test",
            status="open",
            issue_type="Task",
        )
        db_session.add(ticket)
        db_session.commit()
        db_session.refresh(ticket)

        thread, link = service.create_slack_thread_for_ticket(ticket, channel="C0002")

        assert thread.channel_id == "C0002"

    def test_get_link_for_ticket(self, service, db_session, slack_client):
        """Test getting the link for a ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Create ticket with thread
        create_payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"link1-{unique_id}",
                "key": f"LINK1-{unique_id}",
                "fields": {
                    "summary": "Link test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "LINK1"},
                },
            },
        }
        result = service.handle_jira_webhook(create_payload)

        link = service.get_link_for_ticket(result["ticket_id"])

        assert link is not None
        assert link.id == result["link_id"]

    def test_get_links_for_thread(self, service, db_session, slack_client):
        """Test getting all links for a thread."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Create ticket with thread
        create_payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"link2-{unique_id}",
                "key": f"LINK2-{unique_id}",
                "fields": {
                    "summary": "Thread links test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "LINK2"},
                },
            },
        }
        result = service.handle_jira_webhook(create_payload)

        links = service.get_links_for_thread(result["thread_id"])

        assert len(links) == 1
        assert links[0].jira_ticket_id == result["ticket_id"]

    def test_get_ticket_with_thread(self, service, db_session, slack_client):
        """Test getting a ticket with its linked thread."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"FULL-{unique_id}"

        # Create ticket with thread
        create_payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"full-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Full lookup test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Story"},
                    "project": {"key": "FULL"},
                },
            },
        }
        service.handle_jira_webhook(create_payload)

        result = service.get_ticket_with_thread(issue_key)

        assert result is not None
        ticket, thread = result
        assert ticket.jira_key == issue_key
        assert thread is not None

    def test_get_ticket_with_thread_not_found(self, service):
        """Test getting non-existent ticket with thread."""
        result = service.get_ticket_with_thread("NONEXISTENT-999")

        assert result is None

    def test_format_ticket_message(self, service):
        """Test ticket message formatting."""
        ticket = JiraTicket(
            jira_id="40001",
            jira_key="FMT-1",
            project_key="FMT",
            summary="Format test ticket",
            status="open",
            priority="High",
            issue_type="Bug",
            assignee_name="Jane Doe",
        )

        message = service._format_ticket_message(ticket)

        assert "FMT-1" in message
        assert "Format test ticket" in message
        assert "High" in message
        assert "Bug" in message
        assert "Jane Doe" in message

    def test_format_ticket_blocks(self, service):
        """Test ticket Block Kit formatting."""
        ticket = JiraTicket(
            jira_id="40002",
            jira_key="FMT-2",
            project_key="FMT",
            summary="Block format test",
            description="This is a test description.",
            status="in_progress",
            priority="Medium",
            issue_type="Story",
        )

        blocks = service._format_ticket_blocks(ticket)

        assert len(blocks) >= 3  # Header, fields, context
        assert blocks[0]["type"] == "header"
        assert "FMT-2" in blocks[0]["text"]["text"]
