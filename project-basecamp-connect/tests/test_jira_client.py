"""Tests for Jira API client."""

from datetime import datetime

import pytest

from src.connect.clients.jira import (
    JiraIssue,
    JiraWebhookEvent,
    MockJiraClient,
)
from src.connect.exceptions import JiraError


class TestJiraIssue:
    """Tests for JiraIssue data class."""

    def test_from_api_response_basic(self):
        """Test parsing a basic Jira API response."""
        data = {
            "id": "10001",
            "key": "PROJ-123",
            "fields": {
                "summary": "Test issue",
                "description": "Test description",
                "status": {"name": "Open"},
                "priority": {"name": "High"},
                "issuetype": {"name": "Bug"},
                "project": {"key": "PROJ"},
                "assignee": {
                    "accountId": "user-123",
                    "displayName": "John Doe",
                },
                "reporter": {
                    "accountId": "user-456",
                    "displayName": "Jane Smith",
                },
                "labels": ["bug", "urgent"],
                "created": "2024-01-15T10:00:00.000Z",
                "updated": "2024-01-16T15:30:00.000Z",
            },
        }

        issue = JiraIssue.from_api_response(data)

        assert issue.id == "10001"
        assert issue.key == "PROJ-123"
        assert issue.project_key == "PROJ"
        assert issue.summary == "Test issue"
        assert issue.description == "Test description"
        assert issue.status == "Open"
        assert issue.priority == "High"
        assert issue.issue_type == "Bug"
        assert issue.assignee_id == "user-123"
        assert issue.assignee_name == "John Doe"
        assert issue.reporter_id == "user-456"
        assert issue.reporter_name == "Jane Smith"
        assert issue.labels == ["bug", "urgent"]
        assert issue.created is not None
        assert issue.updated is not None

    def test_from_api_response_with_adf_description(self):
        """Test parsing description in Atlassian Document Format."""
        data = {
            "id": "10002",
            "key": "PROJ-124",
            "fields": {
                "summary": "ADF test",
                "description": {
                    "type": "doc",
                    "version": 1,
                    "content": [
                        {
                            "type": "paragraph",
                            "content": [
                                {"type": "text", "text": "Hello "},
                                {"type": "text", "text": "World"},
                            ],
                        }
                    ],
                },
                "status": {"name": "Open"},
                "issuetype": {"name": "Task"},
                "project": {"key": "PROJ"},
            },
        }

        issue = JiraIssue.from_api_response(data)

        assert issue.description == "Hello World"

    def test_from_api_response_minimal(self):
        """Test parsing with minimal fields."""
        data = {
            "id": "10003",
            "key": "PROJ-125",
            "fields": {
                "summary": "Minimal issue",
                "status": {},
                "issuetype": {},
                "project": {},
            },
        }

        issue = JiraIssue.from_api_response(data)

        assert issue.id == "10003"
        assert issue.key == "PROJ-125"
        assert issue.summary == "Minimal issue"
        assert issue.status == "Unknown"
        assert issue.issue_type == "Task"
        assert issue.assignee_id is None
        assert issue.description is None


class TestJiraWebhookEvent:
    """Tests for JiraWebhookEvent data class."""

    def test_from_webhook_payload(self):
        """Test parsing a Jira webhook payload."""
        payload = {
            "webhookEvent": "jira:issue_created",
            "issue": {
                "id": "10001",
                "key": "PROJ-123",
                "fields": {
                    "summary": "New issue",
                    "status": {"name": "Open"},
                    "issuetype": {"name": "Bug"},
                    "project": {"key": "PROJ"},
                },
            },
            "user": {
                "accountId": "user-123",
                "displayName": "John Doe",
            },
            "timestamp": 1705320000000,
        }

        event = JiraWebhookEvent.from_webhook_payload(payload)

        assert event.webhook_event == "jira:issue_created"
        assert event.issue.key == "PROJ-123"
        assert event.user_id == "user-123"
        assert event.user_name == "John Doe"
        assert event.timestamp is not None


class TestMockJiraClient:
    """Tests for MockJiraClient."""

    def test_get_issue_existing(self):
        """Test getting an existing issue."""
        client = MockJiraClient()

        issue = client.get_issue("PROJ-1")

        assert issue.key == "PROJ-1"
        assert issue.project_key == "PROJ"
        assert issue.summary == "Implement user authentication"

    def test_get_issue_not_found(self):
        """Test getting a non-existent issue."""
        client = MockJiraClient()

        with pytest.raises(JiraError) as exc_info:
            client.get_issue("NONEXISTENT-999")

        assert "not found" in str(exc_info.value)

    def test_search_issues(self):
        """Test searching issues."""
        client = MockJiraClient()

        issues = client.search_issues("project = PROJ")

        assert len(issues) == 2
        assert all(i.project_key == "PROJ" for i in issues)

    def test_search_issues_with_status(self):
        """Test searching issues with status filter."""
        client = MockJiraClient()

        issues = client.search_issues("project = PROJ AND status = Open")

        assert len(issues) == 1
        assert issues[0].status == "Open"

    def test_get_recently_created_issues(self):
        """Test getting recently created issues."""
        client = MockJiraClient()

        issues = client.get_recently_created_issues(project_key="PROJ")

        assert len(issues) == 2
        assert all(i.project_key == "PROJ" for i in issues)

    def test_add_comment(self):
        """Test adding a comment."""
        client = MockJiraClient()

        result = client.add_comment("PROJ-1", "Test comment")

        assert "id" in result
        assert result["body"] == "Test comment"

        # Verify comment was stored (returns JiraCommentData objects)
        comments = client.get_comments("PROJ-1")
        assert len(comments) == 1
        assert comments[0].body == "Test comment"

    def test_add_comment_issue_not_found(self):
        """Test adding comment to non-existent issue."""
        client = MockJiraClient()

        with pytest.raises(JiraError) as exc_info:
            client.add_comment("NONEXISTENT-999", "Comment")

        assert "not found" in str(exc_info.value)

    def test_add_mock_issue(self):
        """Test adding a custom mock issue."""
        client = MockJiraClient()

        custom_issue = JiraIssue(
            id="99999",
            key="CUSTOM-1",
            project_key="CUSTOM",
            summary="Custom test issue",
            description="Custom description",
            status="Open",
            priority="Low",
            issue_type="Epic",
        )
        client.add_mock_issue(custom_issue)

        retrieved = client.get_issue("CUSTOM-1")

        assert retrieved.key == "CUSTOM-1"
        assert retrieved.project_key == "CUSTOM"
        assert retrieved.issue_type == "Epic"
