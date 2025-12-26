"""Jira API client interface and implementations.

Based on Jira REST API v3:
https://developer.atlassian.com/cloud/jira/platform/rest/v3/

This module provides:
- Protocol-based interface for structural typing (duck typing)
- ABC-based interface for traditional inheritance
- Real implementation using httpx
- Mock implementation for testing
- Factory function for dependency injection
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Protocol, runtime_checkable

import httpx

from src.connect.config import get_integration_config
from src.connect.exceptions import JiraError
from src.connect.logging_config import get_logger

logger = get_logger(__name__)


@dataclass
class JiraIssue:
    """Represents a Jira issue.

    Attributes:
        id: Internal Jira issue ID
        key: Issue key (e.g., "PROJ-123")
        project_key: Project key (e.g., "PROJ")
        summary: Issue summary/title
        description: Issue description (can be None)
        status: Current issue status
        priority: Issue priority
        issue_type: Type of issue (Bug, Story, Task, etc.)
        assignee_id: Assignee account ID
        assignee_name: Assignee display name
        reporter_id: Reporter account ID
        reporter_name: Reporter display name
        labels: List of labels
        created: Issue creation timestamp
        updated: Issue last update timestamp
    """

    id: str
    key: str
    project_key: str
    summary: str
    description: str | None
    status: str
    priority: str | None
    issue_type: str
    assignee_id: str | None = None
    assignee_name: str | None = None
    reporter_id: str | None = None
    reporter_name: str | None = None
    labels: list[str] = field(default_factory=list)
    created: datetime | None = None
    updated: datetime | None = None

    @classmethod
    def from_api_response(cls, data: dict[str, Any]) -> JiraIssue:
        """Create a JiraIssue from Jira API response."""
        fields = data.get("fields", {})

        # Extract assignee info
        assignee = fields.get("assignee") or {}
        assignee_id = assignee.get("accountId")
        assignee_name = assignee.get("displayName")

        # Extract reporter info
        reporter = fields.get("reporter") or {}
        reporter_id = reporter.get("accountId")
        reporter_name = reporter.get("displayName")

        # Extract status
        status_obj = fields.get("status") or {}
        status = status_obj.get("name", "Unknown")

        # Extract priority
        priority_obj = fields.get("priority") or {}
        priority = priority_obj.get("name")

        # Extract issue type
        issue_type_obj = fields.get("issuetype") or {}
        issue_type = issue_type_obj.get("name", "Task")

        # Extract project
        project = fields.get("project") or {}
        project_key = project.get("key", "")

        # Parse timestamps
        created = None
        updated = None
        if fields.get("created"):
            try:
                created = datetime.fromisoformat(
                    fields["created"].replace("Z", "+00:00")
                )
            except ValueError:
                pass
        if fields.get("updated"):
            try:
                updated = datetime.fromisoformat(
                    fields["updated"].replace("Z", "+00:00")
                )
            except ValueError:
                pass

        # Extract description (handle ADF format)
        description = None
        desc_field = fields.get("description")
        if isinstance(desc_field, str):
            description = desc_field
        elif isinstance(desc_field, dict):
            # Atlassian Document Format - extract text content
            description = cls._extract_adf_text(desc_field)

        return cls(
            id=data["id"],
            key=data["key"],
            project_key=project_key,
            summary=fields.get("summary", ""),
            description=description,
            status=status,
            priority=priority,
            issue_type=issue_type,
            assignee_id=assignee_id,
            assignee_name=assignee_name,
            reporter_id=reporter_id,
            reporter_name=reporter_name,
            labels=fields.get("labels", []),
            created=created,
            updated=updated,
        )

    @staticmethod
    def _extract_adf_text(adf: dict[str, Any]) -> str:
        """Extract plain text from Atlassian Document Format."""
        text_parts = []

        def extract_content(node: dict) -> None:
            if node.get("type") == "text":
                text_parts.append(node.get("text", ""))
            for child in node.get("content", []):
                extract_content(child)

        extract_content(adf)
        return "".join(text_parts)


@dataclass
class JiraCommentData:
    """Represents a Jira comment.

    Attributes:
        id: Internal Jira comment ID
        author_id: Author account ID
        author_name: Author display name
        body: Comment text content
        created: Comment creation timestamp
        updated: Comment last update timestamp
    """

    id: str
    author_id: str | None = None
    author_name: str | None = None
    body: str | None = None
    created: datetime | None = None
    updated: datetime | None = None

    @classmethod
    def from_api_response(cls, data: dict[str, Any]) -> JiraCommentData:
        """Create a JiraCommentData from Jira API response."""
        author = data.get("author") or {}
        author_id = author.get("accountId")
        author_name = author.get("displayName")

        # Extract body (handle ADF format)
        body = None
        body_field = data.get("body")
        if isinstance(body_field, str):
            body = body_field
        elif isinstance(body_field, dict):
            body = JiraIssue._extract_adf_text(body_field)

        # Parse timestamps
        created = None
        updated = None
        if data.get("created"):
            try:
                created = datetime.fromisoformat(
                    data["created"].replace("Z", "+00:00")
                )
            except ValueError:
                pass
        if data.get("updated"):
            try:
                updated = datetime.fromisoformat(
                    data["updated"].replace("Z", "+00:00")
                )
            except ValueError:
                pass

        return cls(
            id=data["id"],
            author_id=author_id,
            author_name=author_name,
            body=body,
            created=created,
            updated=updated,
        )


@dataclass
class JiraWebhookEvent:
    """Represents a Jira webhook event.

    Attributes:
        webhook_event: Event type (e.g., "jira:issue_created")
        issue: The issue data
        user: User who triggered the event
        timestamp: Event timestamp
        changelog: Change log for update events
    """

    webhook_event: str
    issue: JiraIssue
    user_id: str | None = None
    user_name: str | None = None
    timestamp: datetime | None = None
    changelog: dict[str, Any] | None = None

    @classmethod
    def from_webhook_payload(cls, payload: dict[str, Any]) -> JiraWebhookEvent:
        """Create from Jira webhook payload."""
        issue_data = payload.get("issue", {})
        issue = JiraIssue.from_api_response(issue_data)

        user = payload.get("user") or {}
        user_id = user.get("accountId")
        user_name = user.get("displayName")

        timestamp = None
        if payload.get("timestamp"):
            try:
                timestamp = datetime.fromtimestamp(payload["timestamp"] / 1000)
            except (ValueError, TypeError):
                pass

        return cls(
            webhook_event=payload.get("webhookEvent", ""),
            issue=issue,
            user_id=user_id,
            user_name=user_name,
            timestamp=timestamp,
            changelog=payload.get("changelog"),
        )


@runtime_checkable
class JiraClientProtocol(Protocol):
    """Protocol-based interface for Jira API client.

    This protocol enables structural typing (duck typing), allowing any object
    that implements the required methods to be used as a Jira client without
    explicit inheritance. Use this for type hints in function signatures.

    Example:
        def process_issue(client: JiraClientProtocol) -> None:
            issue = client.get_issue("PROJ-123")
    """

    def get_issue(self, issue_key: str) -> JiraIssue:
        """Fetch a single issue by key."""
        ...

    def search_issues(
        self,
        jql: str,
        start_at: int = 0,
        max_results: int = 50,
    ) -> list[JiraIssue]:
        """Search for issues using JQL."""
        ...

    def get_recently_created_issues(
        self,
        project_key: str | None = None,
        since_minutes: int = 60,
    ) -> list[JiraIssue]:
        """Get issues created within the last N minutes."""
        ...

    def add_comment(self, issue_key: str, body: str) -> dict[str, Any]:
        """Add a comment to an issue."""
        ...

    def get_comments(self, issue_key: str) -> list[JiraCommentData]:
        """Get all comments for an issue."""
        ...


class JiraClientInterface(ABC):
    """Abstract base class for Jira API client.

    This interface defines the contract for interacting with Jira using
    traditional inheritance. Use JiraClientProtocol for structural typing.

    Note:
        Both JiraClient and MockJiraClient inherit from this ABC.
        For dependency injection, prefer using JiraClientProtocol in type hints.
    """

    @abstractmethod
    def get_issue(self, issue_key: str) -> JiraIssue:
        """Fetch a single issue by key.

        Args:
            issue_key: The issue key (e.g., "PROJ-123")

        Returns:
            JiraIssue object

        Raises:
            JiraError: If the issue cannot be fetched
        """

    @abstractmethod
    def search_issues(
        self,
        jql: str,
        start_at: int = 0,
        max_results: int = 50,
    ) -> list[JiraIssue]:
        """Search for issues using JQL.

        Args:
            jql: JQL query string
            start_at: Starting index for pagination
            max_results: Maximum number of results

        Returns:
            List of JiraIssue objects

        Raises:
            JiraError: If the search fails
        """

    @abstractmethod
    def get_recently_created_issues(
        self,
        project_key: str | None = None,
        since_minutes: int = 60,
    ) -> list[JiraIssue]:
        """Get issues created within the last N minutes.

        Args:
            project_key: Optional project to filter by
            since_minutes: Look back this many minutes

        Returns:
            List of recently created JiraIssue objects
        """

    @abstractmethod
    def add_comment(self, issue_key: str, body: str) -> dict[str, Any]:
        """Add a comment to an issue.

        Args:
            issue_key: The issue key
            body: Comment body text

        Returns:
            API response with comment details
        """

    @abstractmethod
    def get_comments(self, issue_key: str) -> list[JiraCommentData]:
        """Get all comments for an issue.

        Args:
            issue_key: The issue key (e.g., "PROJ-123")

        Returns:
            List of JiraCommentData objects

        Raises:
            JiraError: If the comments cannot be fetched
        """


class JiraClient(JiraClientInterface):
    """Real Jira API client using httpx.

    Uses Jira REST API v3 with Basic Auth (email + API token).
    """

    def __init__(
        self,
        base_url: str | None = None,
        email: str | None = None,
        api_token: str | None = None,
    ) -> None:
        """Initialize the Jira client.

        Args:
            base_url: Jira base URL (e.g., "https://yoursite.atlassian.net")
            email: Atlassian account email
            api_token: Jira API token
        """
        config = get_integration_config()
        self.base_url = (base_url or config.jira_base_url or "").rstrip("/")
        self.api_token = api_token or config.jira_api_token
        self._email = email

        if not self.base_url:
            raise JiraError("Jira base URL not configured")
        if not self.api_token:
            raise JiraError("Jira API token not configured")

        self._client = httpx.Client(
            base_url=f"{self.base_url}/rest/api/3",
            auth=(self._email or "", self.api_token),
            headers={"Content-Type": "application/json"},
            timeout=30.0,
        )

    def get_issue(self, issue_key: str) -> JiraIssue:
        """Fetch a single issue by key."""
        try:
            response = self._client.get(f"/issue/{issue_key}")
            response.raise_for_status()
            return JiraIssue.from_api_response(response.json())
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to fetch issue {issue_key}: {e}")
            raise JiraError(f"Failed to fetch issue {issue_key}: {e.response.text}")
        except Exception as e:
            logger.error(f"Unexpected error fetching issue {issue_key}: {e}")
            raise JiraError(f"Unexpected error: {e}")

    def search_issues(
        self,
        jql: str,
        start_at: int = 0,
        max_results: int = 50,
    ) -> list[JiraIssue]:
        """Search for issues using JQL."""
        try:
            response = self._client.post(
                "/search",
                json={
                    "jql": jql,
                    "startAt": start_at,
                    "maxResults": max_results,
                    "fields": [
                        "summary",
                        "description",
                        "status",
                        "priority",
                        "issuetype",
                        "project",
                        "assignee",
                        "reporter",
                        "labels",
                        "created",
                        "updated",
                    ],
                },
            )
            response.raise_for_status()
            data = response.json()
            return [
                JiraIssue.from_api_response(issue) for issue in data.get("issues", [])
            ]
        except httpx.HTTPStatusError as e:
            logger.error(f"JQL search failed: {e}")
            raise JiraError(f"JQL search failed: {e.response.text}")
        except Exception as e:
            logger.error(f"Unexpected error in JQL search: {e}")
            raise JiraError(f"Unexpected error: {e}")

    def get_recently_created_issues(
        self,
        project_key: str | None = None,
        since_minutes: int = 60,
    ) -> list[JiraIssue]:
        """Get issues created within the last N minutes."""
        jql_parts = [f"created >= -{since_minutes}m"]
        if project_key:
            jql_parts.append(f"project = {project_key}")
        jql = " AND ".join(jql_parts) + " ORDER BY created DESC"
        return self.search_issues(jql)

    def add_comment(self, issue_key: str, body: str) -> dict[str, Any]:
        """Add a comment to an issue."""
        try:
            response = self._client.post(
                f"/issue/{issue_key}/comment",
                json={
                    "body": {
                        "type": "doc",
                        "version": 1,
                        "content": [
                            {
                                "type": "paragraph",
                                "content": [{"type": "text", "text": body}],
                            }
                        ],
                    }
                },
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to add comment to {issue_key}: {e}")
            raise JiraError(f"Failed to add comment: {e.response.text}")
        except Exception as e:
            logger.error(f"Unexpected error adding comment: {e}")
            raise JiraError(f"Unexpected error: {e}")

    def get_comments(self, issue_key: str) -> list[JiraCommentData]:
        """Get all comments for an issue."""
        try:
            response = self._client.get(f"/issue/{issue_key}/comment")
            response.raise_for_status()
            data = response.json()
            return [
                JiraCommentData.from_api_response(comment)
                for comment in data.get("comments", [])
            ]
        except httpx.HTTPStatusError as e:
            logger.error(f"Failed to get comments for {issue_key}: {e}")
            raise JiraError(f"Failed to get comments: {e.response.text}")
        except Exception as e:
            logger.error(f"Unexpected error getting comments: {e}")
            raise JiraError(f"Unexpected error: {e}")


class MockJiraClient(JiraClientInterface):
    """Mock Jira client for testing.

    Provides controllable responses without making real API calls.
    """

    def __init__(self) -> None:
        """Initialize the mock client with sample data."""
        self._issues: dict[str, JiraIssue] = {}
        self._comments: dict[str, list[JiraCommentData]] = {}
        self._setup_sample_data()

    def _setup_sample_data(self) -> None:
        """Set up sample issues for testing."""
        now = datetime.utcnow()

        # Sample issues
        samples = [
            JiraIssue(
                id="10001",
                key="PROJ-1",
                project_key="PROJ",
                summary="Implement user authentication",
                description="Add OAuth2 authentication flow with refresh tokens.",
                status="Open",
                priority="High",
                issue_type="Story",
                assignee_id="user-001",
                assignee_name="John Doe",
                reporter_id="user-002",
                reporter_name="Jane Smith",
                labels=["security", "auth"],
                created=now,
                updated=now,
            ),
            JiraIssue(
                id="10002",
                key="PROJ-2",
                project_key="PROJ",
                summary="Fix login button not working on mobile",
                description="The login button does not respond to taps on iOS Safari.",
                status="In Progress",
                priority="Critical",
                issue_type="Bug",
                assignee_id="user-003",
                assignee_name="Bob Wilson",
                reporter_id="user-001",
                reporter_name="John Doe",
                labels=["mobile", "bug", "urgent"],
                created=now,
                updated=now,
            ),
            JiraIssue(
                id="10003",
                key="DATA-1",
                project_key="DATA",
                summary="Create data pipeline for user analytics",
                description="Build ETL pipeline to process user events.",
                status="Open",
                priority="Medium",
                issue_type="Task",
                assignee_id=None,
                assignee_name=None,
                reporter_id="user-002",
                reporter_name="Jane Smith",
                labels=["data", "pipeline"],
                created=now,
                updated=now,
            ),
        ]

        for issue in samples:
            self._issues[issue.key] = issue
            self._comments[issue.key] = []

    def add_mock_issue(self, issue: JiraIssue) -> None:
        """Add a mock issue for testing."""
        self._issues[issue.key] = issue
        self._comments[issue.key] = []

    def get_issue(self, issue_key: str) -> JiraIssue:
        """Fetch a single issue by key."""
        if issue_key not in self._issues:
            raise JiraError(f"Issue {issue_key} not found")
        return self._issues[issue_key]

    def search_issues(
        self,
        jql: str,
        start_at: int = 0,
        max_results: int = 50,
    ) -> list[JiraIssue]:
        """Search for issues using JQL (simplified mock implementation)."""
        # Simple filtering based on project key if present in JQL
        results = list(self._issues.values())

        # Parse simple JQL patterns
        jql_lower = jql.lower()
        if "project = " in jql_lower:
            # Extract project key
            parts = jql_lower.split("project = ")
            if len(parts) > 1:
                project = parts[1].split()[0].strip().upper()
                results = [i for i in results if i.project_key == project]

        if "status = " in jql_lower:
            parts = jql_lower.split("status = ")
            if len(parts) > 1:
                status = parts[1].split()[0].strip().strip("'\"")
                results = [i for i in results if i.status.lower() == status]

        return results[start_at : start_at + max_results]

    def get_recently_created_issues(
        self,
        project_key: str | None = None,
        since_minutes: int = 60,
    ) -> list[JiraIssue]:
        """Get issues created within the last N minutes."""
        results = list(self._issues.values())
        if project_key:
            results = [i for i in results if i.project_key == project_key]
        return results

    def add_comment(self, issue_key: str, body: str) -> dict[str, Any]:
        """Add a comment to an issue."""
        if issue_key not in self._issues:
            raise JiraError(f"Issue {issue_key} not found")

        now = datetime.utcnow()
        comment_id = f"comment-{len(self._comments.get(issue_key, [])) + 1}"

        # Store as JiraCommentData
        comment_data = JiraCommentData(
            id=comment_id,
            author_id="user-bot",
            author_name="Connect Bot",
            body=body,
            created=now,
            updated=now,
        )
        self._comments.setdefault(issue_key, []).append(comment_data)

        # Return dict format for API compatibility
        return {
            "id": comment_id,
            "body": body,
            "created": now.isoformat(),
        }

    def get_comments(self, issue_key: str) -> list[JiraCommentData]:
        """Get comments for an issue."""
        # Return comments if they exist, empty list otherwise
        # (Don't require issue to exist in _issues since tickets may be
        # created via webhook and stored in DB, not in mock client)
        return self._comments.get(issue_key, [])

    def add_mock_comment(
        self,
        issue_key: str,
        comment_id: str,
        body: str,
        author_id: str | None = None,
        author_name: str | None = None,
    ) -> JiraCommentData:
        """Add a mock comment for testing."""
        # Initialize comments list if it doesn't exist (without overwriting)
        if issue_key not in self._comments:
            self._comments[issue_key] = []

        now = datetime.utcnow()
        comment = JiraCommentData(
            id=comment_id,
            author_id=author_id or "user-mock",
            author_name=author_name or "Mock User",
            body=body,
            created=now,
            updated=now,
        )
        self._comments[issue_key].append(comment)
        return comment


def create_jira_client(
    use_mock: bool = False,
    base_url: str | None = None,
    email: str | None = None,
    api_token: str | None = None,
) -> JiraClientProtocol:
    """Factory function to create a Jira client.

    This factory function provides a clean way to instantiate Jira clients
    with dependency injection support. It returns a client that satisfies
    the JiraClientProtocol interface.

    Args:
        use_mock: If True, returns a MockJiraClient for testing
        base_url: Jira base URL (e.g., "https://yoursite.atlassian.net")
        email: Atlassian account email
        api_token: Jira API token

    Returns:
        A Jira client instance implementing JiraClientProtocol

    Example:
        # Production usage
        client = create_jira_client()

        # Testing usage
        client = create_jira_client(use_mock=True)

        # Custom configuration
        client = create_jira_client(
            base_url="https://custom.atlassian.net",
            email="user@example.com",
            api_token="token123"
        )
    """
    if use_mock:
        return MockJiraClient()
    return JiraClient(base_url=base_url, email=email, api_token=api_token)
