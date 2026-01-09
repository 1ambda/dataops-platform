"""Enums for Basecamp Server client.

This module contains enum definitions used by the BasecampClient.
"""

from __future__ import annotations

from enum import Enum


class WorkflowSource(str, Enum):
    """Source type for workflow runs.

    Indicates how the workflow was registered:
    - CODE: Registered via CI/CD pipeline from Git
    - MANUAL: User registered via CLI/API
    """

    CODE = "code"
    MANUAL = "manual"


class RunStatus(str, Enum):
    """Status of a workflow run.

    Status values match the server API response format:
    - PENDING: Run is queued, waiting to start
    - RUNNING: Run is currently executing
    - COMPLETED: Run finished successfully
    - FAILED: Run finished with errors
    - KILLED: Run was forcefully terminated
    """

    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    KILLED = "KILLED"
