"""Trace ID context for CLI command execution.

This module provides trace context management for CLI commands,
enabling request tracking across server API calls.

Example:
    >>> from dli.core.trace import TraceContext, with_trace
    >>> trace = TraceContext.create("workflow backfill")
    >>> trace.set_current()
    >>> print(trace.short_id)
    550e8400
    >>> TraceContext.clear_current()
"""

from __future__ import annotations

import functools
import platform
import uuid
from contextvars import ContextVar
from dataclasses import dataclass
from typing import Callable, ParamSpec, TypeVar

__all__ = ["TraceContext", "get_current_trace", "with_trace"]

# Context variable for current trace - single trace per CLI command invocation.
# Note: Designed for synchronous CLI commands, not for concurrent/async patterns.
_current_trace: ContextVar[TraceContext | None] = ContextVar("current_trace", default=None)


def get_current_trace() -> TraceContext | None:
    """Get the current trace context (convenience function).

    Returns:
        Current TraceContext if set, None otherwise.

    Example:
        >>> from dli.core.trace import get_current_trace
        >>> trace = get_current_trace()
        >>> if trace:
        ...     print(f"Trace ID: {trace.trace_id}")
    """
    return _current_trace.get()


@dataclass
class TraceContext:
    """Trace context for a CLI command execution.

    Attributes:
        trace_id: Unique identifier for this command execution (UUID format).
        command: CLI command being executed (e.g., "workflow backfill").
        cli_version: dli CLI version.
        os_name: Operating system name (lowercase).
        python_version: Python version string.

    Example:
        >>> trace = TraceContext.create("run")
        >>> trace.set_current()
        >>> # ... perform operations ...
        >>> TraceContext.clear_current()
    """

    trace_id: str
    command: str
    cli_version: str
    os_name: str
    python_version: str

    def __repr__(self) -> str:
        """Return concise representation for debugging."""
        return f"TraceContext(trace_id={self.short_id}, command={self.command!r})"

    @classmethod
    def create(cls, command: str) -> TraceContext:
        """Create a new trace context for a command.

        Args:
            command: CLI command being executed (e.g., "workflow backfill").

        Returns:
            New TraceContext instance with generated UUID and system info.

        Example:
            >>> trace = TraceContext.create("dataset list")
            >>> print(trace.user_agent)
            dli/0.9.0 (darwin; Python/3.12.1) command/dataset-list
        """
        from dli import __version__

        return cls(
            trace_id=str(uuid.uuid4()),
            command=command,
            cli_version=__version__,
            os_name=platform.system().lower(),
            python_version=platform.python_version(),
        )

    @property
    def user_agent(self) -> str:
        """Generate User-Agent header value.

        Format: dli/{version} ({os}; Python/{py_version}) command/{cmd}

        Returns:
            User-Agent string for HTTP headers.
        """
        # Convert spaces to hyphens in command name
        cmd_slug = self.command.replace(" ", "-")
        return (
            f"dli/{self.cli_version} "
            f"({self.os_name}; Python/{self.python_version}) "
            f"command/{cmd_slug}"
        )

    @property
    def short_id(self) -> str:
        """Return first 8 characters of trace_id for display.

        Returns:
            Shortened trace ID (8 characters).
        """
        return self.trace_id[:8]

    def set_current(self) -> None:
        """Set this context as the current trace context."""
        _current_trace.set(self)

    @classmethod
    def get_current(cls) -> TraceContext | None:
        """Get the current trace context.

        Returns:
            Current TraceContext if set, None otherwise.
        """
        return _current_trace.get()

    @classmethod
    def clear_current(cls) -> None:
        """Clear the current trace context."""
        _current_trace.set(None)


# Type variables for decorator
P = ParamSpec("P")
R = TypeVar("R")


def with_trace(command_name: str) -> Callable[[Callable[P, R]], Callable[P, R]]:
    """Decorator to add trace context to CLI commands.

    Creates a TraceContext before function execution and clears it after,
    ensuring proper cleanup even if an exception is raised.

    Args:
        command_name: Name of the command (e.g., "workflow backfill").

    Returns:
        Decorator function that wraps the command with trace context.

    Usage:
        @app.command()
        @with_trace("run")
        def run(file: str) -> None:
            ...

    Note:
        Place @with_trace AFTER @app.command() so it wraps the actual function.
        The trace context is available via get_current_trace() within the command.

    Example:
        >>> @with_trace("test command")
        ... def my_command():
        ...     trace = get_current_trace()
        ...     return trace.short_id if trace else None
        >>> result = my_command()
        >>> len(result)  # Returns 8-char short ID
        8
    """

    def decorator(func: Callable[P, R]) -> Callable[P, R]:
        @functools.wraps(func)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            trace = TraceContext.create(command_name)
            trace.set_current()
            try:
                return func(*args, **kwargs)
            finally:
                TraceContext.clear_current()

        return wrapper

    return decorator
