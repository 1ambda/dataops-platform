"""Tests for Slack-Jira reply sync functionality.

Direction: Slack thread replies -> Jira comments (Jira is SOT)
"""

import pytest

from src.connect.clients.jira import MockJiraClient
from src.connect.clients.slack import MockSlackClient, SlackMessageData
from src.connect.models.comment import SlackReplySync, SyncStatus
from src.connect.models.jira import JiraTicket
from src.connect.models.linking import JiraSlackLink
from src.connect.models.slack import SlackThread
from src.connect.services.jira_slack_integration import JiraSlackIntegrationService


class TestSlackReplySync:
    """Tests for syncing Slack thread replies to Jira comments."""

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
    def linked_ticket(self, service, db_session, slack_client, jira_client):
        """Create a ticket with a linked Slack thread."""
        import uuid

        from src.connect.clients.jira import JiraIssue

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"SYNC-{unique_id}"

        # Create the ticket via webhook
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"sync-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Reply sync test ticket",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "SYNC"},
                },
            },
        }
        result = service.handle_jira_webhook(payload)
        assert result["success"] is True

        # Add issue to mock Jira client so add_comment works
        jira_client.add_mock_issue(JiraIssue(
            id=f"sync-{unique_id}",
            key=issue_key,
            project_key="SYNC",
            summary="Reply sync test ticket",
            description=None,
            status="Open",
            priority="Medium",
            issue_type="Task",
        ))

        ticket = (
            db_session.query(JiraTicket).filter_by(jira_key=issue_key).first()
        )
        return ticket

    def test_sync_replies_no_replies(self, service, linked_ticket):
        """Test syncing when there are no replies in the thread."""
        result = service.sync_replies_to_jira(linked_ticket.jira_key)

        assert result["success"] is True
        assert result["synced_count"] == 0
        assert result["skipped_count"] == 0
        assert result["failed_count"] == 0

    def test_sync_replies_single_reply(
        self, service, linked_ticket, slack_client, jira_client, db_session
    ):
        """Test syncing a single Slack reply to Jira."""
        import time

        # Get the linked thread
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()

        # Add a reply to the Slack thread with valid timestamp (slightly after thread)
        unique_ts = f"{float(thread.thread_ts) + 0.001:.6f}"
        reply = SlackMessageData(
            ts=unique_ts,
            channel_id=thread.channel_id,
            user_id="U001",
            user_name="alice",
            text="This is a test reply from Slack",
            thread_ts=thread.thread_ts,
            is_bot=False,
        )
        slack_client._messages[thread.channel_id].append(reply)

        result = service.sync_replies_to_jira(linked_ticket.jira_key)

        assert result["success"] is True
        assert result["synced_count"] == 1
        assert result["skipped_count"] == 0

        # Verify comment was added to Jira
        jira_comments = jira_client.get_comments(linked_ticket.jira_key)
        assert len(jira_comments) >= 1

        # Verify sync record was stored in DB
        synced_replies = (
            db_session.query(SlackReplySync)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .all()
        )
        assert len(synced_replies) == 1
        assert synced_replies[0].slack_message_ts == unique_ts
        assert synced_replies[0].body == "This is a test reply from Slack"
        assert synced_replies[0].sync_status == SyncStatus.SYNCED

    def test_sync_replies_multiple_replies(
        self, service, linked_ticket, slack_client, jira_client, db_session
    ):
        """Test syncing multiple Slack replies."""
        # Get the linked thread
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()
        base_ts = float(thread.thread_ts)

        # Add multiple replies with valid timestamps
        for i in range(3):
            reply = SlackMessageData(
                ts=f"{base_ts + 0.001 * (i + 1):.6f}",
                channel_id=thread.channel_id,
                user_id=f"U00{i + 1}",
                user_name=f"user{i + 1}",
                text=f"Reply {i + 1}",
                thread_ts=thread.thread_ts,
                is_bot=False,
            )
            slack_client._messages[thread.channel_id].append(reply)

        result = service.sync_replies_to_jira(linked_ticket.jira_key)

        assert result["success"] is True
        assert result["synced_count"] == 3
        assert result["skipped_count"] == 0

        # Verify all replies were stored
        synced_replies = (
            db_session.query(SlackReplySync)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .all()
        )
        assert len(synced_replies) == 3

    def test_sync_replies_skips_bot_messages(
        self, service, linked_ticket, slack_client, jira_client, db_session
    ):
        """Test that bot messages are not synced."""
        # Get the linked thread
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()
        base_ts = float(thread.thread_ts)

        # Add a user reply and a bot reply with valid timestamps
        user_reply = SlackMessageData(
            ts=f"{base_ts + 0.001:.6f}",
            channel_id=thread.channel_id,
            user_id="U001",
            user_name="alice",
            text="User message",
            thread_ts=thread.thread_ts,
            is_bot=False,
        )
        bot_reply = SlackMessageData(
            ts=f"{base_ts + 0.002:.6f}",
            channel_id=thread.channel_id,
            user_id="BOT",
            user_name="connect-bot",
            text="Bot message",
            thread_ts=thread.thread_ts,
            is_bot=True,
        )
        slack_client._messages[thread.channel_id].append(user_reply)
        slack_client._messages[thread.channel_id].append(bot_reply)

        result = service.sync_replies_to_jira(linked_ticket.jira_key)

        assert result["success"] is True
        # Only the user reply should be synced
        assert result["synced_count"] == 1

    def test_sync_replies_idempotent(
        self, service, linked_ticket, slack_client, jira_client, db_session
    ):
        """Test that syncing is idempotent - same reply not synced twice."""
        # Get the linked thread
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()
        base_ts = float(thread.thread_ts)

        # Add a reply with valid timestamp
        unique_ts = f"{base_ts + 0.001:.6f}"
        reply = SlackMessageData(
            ts=unique_ts,
            channel_id=thread.channel_id,
            user_id="U001",
            user_name="alice",
            text="Idempotent test reply",
            thread_ts=thread.thread_ts,
            is_bot=False,
        )
        slack_client._messages[thread.channel_id].append(reply)

        # First sync
        result1 = service.sync_replies_to_jira(linked_ticket.jira_key)
        assert result1["synced_count"] == 1
        assert result1["skipped_count"] == 0

        # Second sync - should skip the already synced reply
        result2 = service.sync_replies_to_jira(linked_ticket.jira_key)
        assert result2["synced_count"] == 0
        assert result2["skipped_count"] == 1

        # Verify only one sync record in DB
        synced_replies = (
            db_session.query(SlackReplySync)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .all()
        )
        assert len(synced_replies) == 1

    def test_sync_replies_no_linked_thread(self, service, db_session):
        """Test syncing for a ticket without a linked thread."""
        result = service.sync_replies_to_jira("NONEXISTENT-999")

        assert result["success"] is False
        assert "No linked thread found" in result.get("error", "")

    def test_format_slack_reply_for_jira(self, service):
        """Test Slack reply formatting for Jira."""
        from datetime import datetime

        reply = SlackMessageData(
            ts="1234567890.000001",
            channel_id="C001",
            user_id="U001",
            user_name="John Doe",
            text="This is the reply text",
        )
        # Manually set sent_at since SlackMessageData calculates it from ts
        reply_with_time = SlackMessageData(
            ts=str(datetime(2024, 1, 15, 10, 30).timestamp()),
            channel_id="C001",
            user_id="U001",
            user_name="John Doe",
            text="This is the reply text",
        )

        message = service._format_slack_reply_for_jira(reply_with_time)

        assert "[Slack]" in message
        assert "John Doe" in message
        assert "This is the reply text" in message

    def test_get_synced_replies_for_ticket(
        self, service, linked_ticket, slack_client, jira_client, db_session
    ):
        """Test retrieving synced replies for a ticket."""
        # Get the linked thread
        link = (
            db_session.query(JiraSlackLink)
            .filter_by(jira_ticket_id=linked_ticket.id)
            .first()
        )
        thread = db_session.query(SlackThread).filter_by(id=link.slack_thread_id).first()
        base_ts = float(thread.thread_ts)

        # Add and sync replies with valid timestamps
        for i in range(2):
            reply = SlackMessageData(
                ts=f"{base_ts + 0.001 * (i + 1):.6f}",
                channel_id=thread.channel_id,
                user_id=f"U00{i + 1}",
                user_name=f"user{i + 1}",
                text=f"Reply {i + 1}",
                thread_ts=thread.thread_ts,
                is_bot=False,
            )
            slack_client._messages[thread.channel_id].append(reply)

        service.sync_replies_to_jira(linked_ticket.jira_key)

        # Get synced replies
        replies = service.get_synced_replies_for_ticket(linked_ticket.id)

        assert len(replies) == 2
        assert all(r.sync_status == SyncStatus.SYNCED for r in replies)

    def test_backward_compatible_alias(self, service, linked_ticket):
        """Test that old method names still work."""
        # sync_comments_for_ticket should call sync_replies_to_jira
        result = service.sync_comments_for_ticket(linked_ticket.jira_key)
        assert result["success"] is True


class TestSlackReplySyncModel:
    """Tests for the SlackReplySync model."""

    def test_slack_reply_sync_creation(self, db_session):
        """Test creating a SlackReplySync record."""
        import uuid

        unique_ts = f"model-create-{uuid.uuid4().hex[:8]}"
        sync = SlackReplySync(
            jira_ticket_id=9001,
            slack_thread_id=1,
            slack_message_ts=unique_ts,
            slack_user_id="U001",
            slack_user_name="Test User",
            body="Test reply body",
            sync_status=SyncStatus.SYNCED,
        )
        db_session.add(sync)
        db_session.commit()
        db_session.refresh(sync)

        assert sync.id is not None
        assert sync.slack_message_ts == unique_ts
        assert sync.sync_status == SyncStatus.SYNCED

    def test_slack_reply_sync_to_dict(self, db_session):
        """Test SlackReplySync.to_dict() method."""
        import uuid
        from datetime import datetime

        unique_ts = f"model-dict-{uuid.uuid4().hex[:8]}"
        now = datetime.utcnow()
        sync = SlackReplySync(
            jira_ticket_id=9002,
            slack_thread_id=1,
            slack_message_ts=unique_ts,
            slack_user_name="Dict User",
            body="Dict body",
            sync_status=SyncStatus.SYNCED,
            synced_at=now,
        )
        db_session.add(sync)
        db_session.commit()
        db_session.refresh(sync)

        result = sync.to_dict()

        assert result["slack_message_ts"] == unique_ts
        assert result["slack_user_name"] == "Dict User"
        assert result["body"] == "Dict body"
        assert result["sync_status"] == SyncStatus.SYNCED

    def test_slack_reply_sync_repr(self, db_session):
        """Test SlackReplySync __repr__ method."""
        import uuid

        unique_ts = f"model-repr-{uuid.uuid4().hex[:8]}"
        sync = SlackReplySync(
            jira_ticket_id=9003,
            slack_thread_id=1,
            slack_message_ts=unique_ts,
            body="Test body for repr",
            sync_status=SyncStatus.SYNCED,
        )
        db_session.add(sync)
        db_session.commit()
        db_session.refresh(sync)

        repr_str = repr(sync)

        assert "SlackReplySync" in repr_str
        assert unique_ts in repr_str
