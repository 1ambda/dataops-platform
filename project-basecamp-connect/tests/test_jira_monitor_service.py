"""Tests for Jira monitoring service."""

from datetime import datetime

import pytest

from src.connect.clients.jira import JiraIssue, MockJiraClient
from src.connect.models.jira import JiraTicket
from src.connect.services.jira_monitor import JiraMonitorService


class TestJiraMonitorService:
    """Tests for JiraMonitorService."""

    @pytest.fixture
    def jira_client(self):
        """Create a mock Jira client."""
        return MockJiraClient()

    @pytest.fixture
    def service(self, db_session, jira_client):
        """Create a JiraMonitorService."""
        return JiraMonitorService(db_session, jira_client)

    def test_process_issue_created_webhook(self, service, db_session):
        """Test processing an issue created webhook."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"TEST-{unique_id}"

        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"test-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Test ticket from webhook",
                    "description": "Test description",
                    "status": {"name": "Open"},
                    "priority": {"name": "High"},
                    "issuetype": {"name": "Bug"},
                    "project": {"key": "TEST"},
                    "assignee": {
                        "accountId": "user-123",
                        "displayName": "John Doe",
                    },
                    "labels": ["test", "webhook"],
                    "created": "2024-01-15T10:00:00.000Z",
                },
            },
            "user": {"accountId": "user-456"},
        }

        ticket = service.process_webhook_event(payload)

        assert ticket is not None
        assert ticket.id is not None
        assert ticket.jira_key == issue_key
        assert ticket.summary == "Test ticket from webhook"
        assert ticket.status == "open"
        assert ticket.priority == "High"
        assert ticket.assignee_name == "John Doe"

        # Verify it's in the database
        saved = db_session.query(JiraTicket).filter_by(jira_key=issue_key).first()
        assert saved is not None
        assert saved.id == ticket.id

    def test_process_issue_updated_webhook_existing(self, service, db_session):
        """Test processing an issue updated webhook for existing ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_id = f"upd-{unique_id}"
        issue_key = f"UPD-{unique_id}"

        # First create a ticket
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
        service.process_webhook_event(create_payload)

        # Then update it
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
        updated = service.process_webhook_event(update_payload)

        assert updated is not None
        assert updated.jira_key == issue_key
        assert updated.summary == "Updated summary"
        assert updated.status == "in_progress"

    def test_process_issue_updated_webhook_new(self, service, db_session):
        """Test processing an issue updated webhook for non-existent ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"NEW-{unique_id}"

        payload = {
            "webhookEvent": "jira:issue_updated",
            "issue": {
                "id": f"new-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "New from update",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Story"},
                    "project": {"key": "NEW"},
                },
            },
        }

        ticket = service.process_webhook_event(payload)

        assert ticket is not None
        assert ticket.jira_key == issue_key

    def test_process_ignored_webhook_event(self, service):
        """Test that unhandled events are ignored."""
        payload = {
            "webhookEvent": "jira:issue_deleted",
            "issue": {"id": "99996", "key": "TEST-4"},
        }

        result = service.process_webhook_event(payload)

        assert result is None

    def test_get_ticket_by_key(self, service, db_session):
        """Test getting a ticket by key."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"GET-{unique_id}"

        # Create a ticket
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"get-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Test ticket",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "GET"},
                },
            },
        }
        service.process_webhook_event(payload)

        ticket = service.get_ticket_by_key(issue_key)

        assert ticket is not None
        assert ticket.jira_key == issue_key

    def test_get_ticket_by_key_not_found(self, service):
        """Test getting a non-existent ticket."""
        ticket = service.get_ticket_by_key("NONEXISTENT-999")

        assert ticket is None

    def test_get_tickets_by_project(self, service, db_session):
        """Test getting tickets by project."""
        import uuid

        # Create tickets in different projects with unique IDs
        # Use completely unique project keys to avoid collisions with other tests
        base_id = uuid.uuid4().hex[:8]
        proj_a_key = f"PA{base_id[:4]}"
        proj_b_key = f"PB{base_id[:4]}"

        for i, (project_key, count) in enumerate([(proj_a_key, 2), (proj_b_key, 1)]):
            for j in range(count):
                idx = i * 2 + j
                payload = {
                    "webhookEvent": "jira:issue_created",
                    "issue": {
                        "id": f"{base_id}-{idx}",
                        "key": f"{project_key}-{idx}",
                        "fields": {
                            "summary": f"Ticket {idx}",
                            "status": {"name": "Open"},
                            "issuetype": {"name": "Task"},
                            "project": {"key": project_key},
                        },
                    },
                }
                service.process_webhook_event(payload)

        # Query for project A - should find exactly 2 tickets
        tickets = service.get_tickets_by_project(proj_a_key)

        assert len(tickets) == 2

    def test_normalize_status(self, service):
        """Test status normalization."""
        assert service._normalize_status("Open") == "open"
        assert service._normalize_status("To Do") == "open"
        assert service._normalize_status("In Progress") == "in_progress"
        assert service._normalize_status("Resolved") == "resolved"
        assert service._normalize_status("Closed") == "closed"
        assert service._normalize_status("Custom Status") == "custom_status"

    def test_poll_for_new_tickets(self, service, jira_client, db_session):
        """Test polling for new tickets."""
        import uuid
        from src.connect.clients.jira import JiraIssue

        # Create unique sample issues in the mock client to avoid conflicts
        unique_id = uuid.uuid4().hex[:8]
        poll_project = f"POLL{unique_id[:4]}"

        # Add unique issues to the mock client (as a dict keyed by issue key)
        jira_client._issues = {
            f"{poll_project}-1": JiraIssue(
                id=f"poll-{unique_id}-1",
                key=f"{poll_project}-1",
                project_key=poll_project,
                summary="Poll test ticket 1",
                description="Description 1",
                status="To Do",
                priority="Medium",
                issue_type="Task",
            ),
            f"{poll_project}-2": JiraIssue(
                id=f"poll-{unique_id}-2",
                key=f"{poll_project}-2",
                project_key=poll_project,
                summary="Poll test ticket 2",
                description="Description 2",
                status="In Progress",
                priority="High",
                issue_type="Bug",
            ),
        }

        # Poll for new tickets
        new_tickets = service.poll_for_new_tickets(project_key=poll_project)

        # Should find our 2 new tickets
        assert len(new_tickets) == 2

        # Polling again should not create duplicates
        new_tickets_again = service.poll_for_new_tickets(project_key=poll_project)

        assert len(new_tickets_again) == 0

    def test_sync_ticket(self, service, jira_client, db_session):
        """Test syncing a single ticket."""
        import uuid
        from src.connect.clients.jira import JiraIssue

        # Create a unique issue for this test
        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"SYNC-{unique_id}"

        jira_client._issues = {
            issue_key: JiraIssue(
                id=f"sync-{unique_id}",
                key=issue_key,
                project_key="SYNC",
                summary="Unique sync test ticket",
                description="Test description",
                status="Open",
                priority="Medium",
                issue_type="Task",
            ),
        }

        # First sync
        ticket = service.sync_ticket(issue_key)

        assert ticket is not None
        assert ticket.jira_key == issue_key
        assert ticket.summary == "Unique sync test ticket"

        # Sync again should update
        ticket_again = service.sync_ticket(issue_key)

        assert ticket_again.id == ticket.id
