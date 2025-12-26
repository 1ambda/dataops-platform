"""Integration tests for the API endpoints."""

import json

import pytest


class TestJiraWebhookAPI:
    """Tests for Jira webhook API endpoints."""

    def test_jira_webhook_issue_created(self, client):
        """Test Jira webhook for issue created event."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"API-{unique_id}"

        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"api-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "API test ticket",
                    "description": "Test from API",
                    "status": {"name": "Open"},
                    "priority": {"name": "High"},
                    "issuetype": {"name": "Bug"},
                    "project": {"key": "API"},
                },
            },
        }

        response = client.post(
            "/api/v1/jira/webhook",
            data=json.dumps(payload),
            content_type="application/json",
        )

        assert response.status_code == 200
        data = response.get_json()
        assert data["success"] is True, f"Expected success but got: {data}"
        assert data["event"] == "jira:issue_created"
        assert data["issue_key"] == issue_key
        assert data["ticket_id"] is not None
        assert data["thread_id"] is not None

    def test_jira_webhook_invalid_content_type(self, client):
        """Test Jira webhook with invalid content type."""
        response = client.post(
            "/api/v1/jira/webhook",
            data="not json",
            content_type="text/plain",
        )

        assert response.status_code == 400
        data = response.get_json()
        assert "INVALID_CONTENT_TYPE" in data.get("code", "")

    def test_jira_webhook_invalid_json(self, client):
        """Test Jira webhook with invalid JSON."""
        response = client.post(
            "/api/v1/jira/webhook",
            data="not valid json",
            content_type="application/json",
        )

        # Flask may return 400 or 500 for invalid JSON depending on configuration
        assert response.status_code in (400, 500)


class TestJiraTicketAPI:
    """Tests for Jira ticket API endpoints."""

    def test_list_tickets_empty(self, client):
        """Test listing tickets when none exist."""
        response = client.get("/api/v1/jira/tickets")

        assert response.status_code == 200
        data = response.get_json()
        assert "tickets" in data
        assert "total" in data

    def test_list_tickets_with_data(self, client):
        """Test listing tickets after creating some."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Create a ticket via webhook
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"list-{unique_id}",
                "key": f"LIST-{unique_id}",
                "fields": {
                    "summary": "List test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "LIST"},
                },
            },
        }
        client.post(
            "/api/v1/jira/webhook",
            data=json.dumps(payload),
            content_type="application/json",
        )

        response = client.get("/api/v1/jira/tickets")

        assert response.status_code == 200
        data = response.get_json()
        assert data["total"] >= 1

    def test_list_tickets_by_project(self, client):
        """Test listing tickets filtered by project."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # Create tickets in different projects
        for i, project in enumerate(["PROJA", "PROJB"]):
            key = f"{project}-{unique_id}-{i}"
            payload = {
                "webhookEvent": "jira:issue_created",
                "issue": {
                    "id": f"id-{key}",
                    "key": key,
                    "fields": {
                        "summary": f"Test {key}",
                        "status": {"name": "Open"},
                        "issuetype": {"name": "Task"},
                        "project": {"key": project},
                    },
                },
            }
            client.post(
                "/api/v1/jira/webhook",
                data=json.dumps(payload),
                content_type="application/json",
            )

        response = client.get("/api/v1/jira/tickets?project=PROJA")

        assert response.status_code == 200
        data = response.get_json()
        assert all(t["project_key"] == "PROJA" for t in data["tickets"])

    def test_get_ticket(self, client):
        """Test getting a specific ticket."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]
        issue_key = f"GETAPI-{unique_id}"

        # Create a ticket
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"getapi-{unique_id}",
                "key": issue_key,
                "fields": {
                    "summary": "Get test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Story"},
                    "project": {"key": "GETAPI"},
                    "priority": {"name": "Medium"},
                },
            },
        }
        client.post(
            "/api/v1/jira/webhook",
            data=json.dumps(payload),
            content_type="application/json",
        )

        response = client.get(f"/api/v1/jira/tickets/{issue_key}")

        assert response.status_code == 200
        data = response.get_json()
        assert data["jira_key"] == issue_key
        assert data["summary"] == "Get test"
        assert data["thread_id"] is not None  # Should have linked thread

    def test_get_ticket_not_found(self, client):
        """Test getting a non-existent ticket."""
        response = client.get("/api/v1/jira/tickets/NONEXISTENT-999")

        assert response.status_code == 404
        data = response.get_json()
        assert "NOT_FOUND" in data.get("code", "")

    def test_sync_ticket(self, client):
        """Test syncing a ticket from Jira."""
        # Mock client has PROJ-1
        response = client.post("/api/v1/jira/tickets/PROJ-1/sync")

        assert response.status_code == 200
        data = response.get_json()
        assert data["jira_key"] == "PROJ-1"


class TestSlackAPI:
    """Tests for Slack API endpoints."""

    def test_get_channel_messages_empty(self, client):
        """Test getting channel messages when empty."""
        response = client.get("/api/v1/slack/channels/C0001/messages")

        assert response.status_code == 200
        data = response.get_json()
        assert "messages" in data
        assert "total" in data

    def test_sync_channel_messages(self, client):
        """Test syncing channel messages."""
        response = client.post("/api/v1/slack/channels/C0001/sync")

        assert response.status_code == 200
        data = response.get_json()
        assert "synced" in data
        assert data["synced"] >= 0

    def test_get_thread(self, client):
        """Test getting a thread."""
        import uuid

        unique_id = uuid.uuid4().hex[:8]

        # First create a ticket to get a thread
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": f"thapi-{unique_id}",
                "key": f"THAPI-{unique_id}",
                "fields": {
                    "summary": "Thread API test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "THAPI"},
                },
            },
        }
        webhook_response = client.post(
            "/api/v1/jira/webhook",
            data=json.dumps(payload),
            content_type="application/json",
        )
        response_data = webhook_response.get_json()
        assert "thread_id" in response_data, f"Expected thread_id in response: {response_data}"
        thread_id = response_data["thread_id"]

        response = client.get(f"/api/v1/slack/threads/{thread_id}")

        assert response.status_code == 200
        data = response.get_json()
        assert "thread" in data
        assert data["thread"]["id"] == thread_id

    def test_get_thread_not_found(self, client):
        """Test getting a non-existent thread."""
        response = client.get("/api/v1/slack/threads/999999")

        assert response.status_code == 404


class TestLinksAPI:
    """Tests for links API endpoints."""

    def test_list_links_empty(self, client):
        """Test listing links when empty."""
        response = client.get("/api/v1/links")

        assert response.status_code == 200
        data = response.get_json()
        assert "links" in data
        assert "total" in data

    def test_list_links_with_data(self, client):
        """Test listing links after creating some."""
        # Create a ticket to generate a link
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": "90001",
                "key": "LINKS-1",
                "fields": {
                    "summary": "Links test",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Task"},
                    "project": {"key": "LINKS"},
                },
            },
        }
        client.post(
            "/api/v1/jira/webhook",
            data=json.dumps(payload),
            content_type="application/json",
        )

        response = client.get("/api/v1/links")

        assert response.status_code == 200
        data = response.get_json()
        assert data["total"] >= 1
        assert len(data["links"]) >= 1


class TestHealthAPI:
    """Tests for health check endpoint."""

    def test_health_check(self, client):
        """Test health check endpoint."""
        response = client.get("/health")

        assert response.status_code == 200
        data = response.get_json()
        assert data["status"] == "healthy"
        assert data["service"] == "connect"
        assert data["database"] == "connected"


class TestLegacyAPI:
    """Tests for legacy integration endpoints."""

    def test_create_integration(self, client):
        """Test creating an integration (legacy)."""
        payload = {
            "source_service": "slack",
            "target_service": "jira",
            "event_type": "create_ticket",
            "payload": {"key": "value"},
        }

        response = client.post(
            "/api/v1/integrations",
            data=json.dumps(payload),
            content_type="application/json",
        )

        assert response.status_code == 202
        data = response.get_json()
        assert data["success"] is True

    def test_list_integrations(self, client):
        """Test listing integrations (legacy)."""
        response = client.get("/api/v1/integrations")

        assert response.status_code == 200
        data = response.get_json()
        assert "integrations" in data

    def test_list_mappings(self, client):
        """Test listing mappings (legacy)."""
        response = client.get("/api/v1/mappings")

        assert response.status_code == 200
        data = response.get_json()
        assert "mappings" in data
