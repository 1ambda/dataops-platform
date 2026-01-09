"""Tests for dli.core.trace module.

This module tests TraceContext, with_trace decorator, and get_current_trace function.
"""

from __future__ import annotations

import re
import uuid

import pytest

from dli.core.trace import TraceContext, get_current_trace, with_trace


class TestTraceContextCreate:
    """Tests for TraceContext.create() factory method."""

    def test_trace_context_create_generates_valid_uuid(self) -> None:
        """TraceContext.create() should generate a valid UUID for trace_id."""
        trace = TraceContext.create("test command")

        # Verify it's a valid UUID format
        uuid_pattern = re.compile(
            r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
        assert uuid_pattern.match(trace.trace_id), f"Invalid UUID: {trace.trace_id}"

        # Also verify it can be parsed as UUID
        parsed_uuid = uuid.UUID(trace.trace_id)
        assert str(parsed_uuid) == trace.trace_id

    def test_trace_context_create_sets_command(self) -> None:
        """TraceContext.create() should set the command name."""
        trace = TraceContext.create("workflow backfill")
        assert trace.command == "workflow backfill"

    def test_trace_context_create_sets_cli_version(self) -> None:
        """TraceContext.create() should set the CLI version."""
        from dli import __version__

        trace = TraceContext.create("test")
        assert trace.cli_version == __version__

    def test_trace_context_create_sets_os_name(self) -> None:
        """TraceContext.create() should set the OS name in lowercase."""
        import platform

        trace = TraceContext.create("test")
        assert trace.os_name == platform.system().lower()

    def test_trace_context_create_sets_python_version(self) -> None:
        """TraceContext.create() should set the Python version."""
        import platform

        trace = TraceContext.create("test")
        assert trace.python_version == platform.python_version()


class TestTraceContextUserAgent:
    """Tests for TraceContext.user_agent property."""

    def test_trace_context_user_agent_format(self) -> None:
        """User-Agent should follow format: dli/{version} ({os}; Python/{py}) command/{cmd}."""
        trace = TraceContext(
            trace_id="550e8400-e29b-41d4-a716-446655440000",
            command="workflow backfill",
            cli_version="0.9.0",
            os_name="darwin",
            python_version="3.12.1",
        )

        expected = "dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill"
        assert trace.user_agent == expected

    def test_trace_context_user_agent_space_conversion(self) -> None:
        """Command spaces should be converted to hyphens in User-Agent."""
        trace = TraceContext(
            trace_id="test-id",
            command="dataset list --all",
            cli_version="1.0.0",
            os_name="linux",
            python_version="3.11.0",
        )

        assert "command/dataset-list---all" in trace.user_agent

    def test_trace_context_user_agent_simple_command(self) -> None:
        """Simple command without spaces should work correctly."""
        trace = TraceContext(
            trace_id="test-id",
            command="version",
            cli_version="1.0.0",
            os_name="windows",
            python_version="3.10.0",
        )

        assert "command/version" in trace.user_agent


class TestTraceContextShortId:
    """Tests for TraceContext.short_id property."""

    def test_trace_context_short_id_returns_first_8_chars(self) -> None:
        """short_id should return first 8 characters of trace_id."""
        trace = TraceContext(
            trace_id="550e8400-e29b-41d4-a716-446655440000",
            command="test",
            cli_version="1.0.0",
            os_name="darwin",
            python_version="3.12.0",
        )

        assert trace.short_id == "550e8400"
        assert len(trace.short_id) == 8

    def test_trace_context_short_id_from_created_context(self) -> None:
        """short_id should work with TraceContext.create()."""
        trace = TraceContext.create("test")

        assert len(trace.short_id) == 8
        assert trace.trace_id.startswith(trace.short_id)


class TestTraceContextRepr:
    """Tests for TraceContext.__repr__ method."""

    def test_trace_context_repr_includes_short_id(self) -> None:
        """__repr__ should include short_id."""
        trace = TraceContext(
            trace_id="550e8400-e29b-41d4-a716-446655440000",
            command="workflow backfill",
            cli_version="1.0.0",
            os_name="darwin",
            python_version="3.12.0",
        )

        repr_str = repr(trace)
        assert "550e8400" in repr_str

    def test_trace_context_repr_includes_command(self) -> None:
        """__repr__ should include command name."""
        trace = TraceContext(
            trace_id="test-id-12345678",
            command="dataset list",
            cli_version="1.0.0",
            os_name="darwin",
            python_version="3.12.0",
        )

        repr_str = repr(trace)
        assert "dataset list" in repr_str

    def test_trace_context_repr_format(self) -> None:
        """__repr__ should follow expected format."""
        trace = TraceContext(
            trace_id="550e8400-e29b-41d4-a716-446655440000",
            command="run",
            cli_version="1.0.0",
            os_name="darwin",
            python_version="3.12.0",
        )

        repr_str = repr(trace)
        assert repr_str == "TraceContext(trace_id=550e8400, command='run')"


class TestTraceContextLifecycle:
    """Tests for TraceContext set/get/clear lifecycle."""

    def test_trace_context_set_get_clear(self) -> None:
        """Context lifecycle: set, get, clear should work correctly."""
        trace = TraceContext(
            trace_id="test-trace-id",
            command="test",
            cli_version="1.0.0",
            os_name="darwin",
            python_version="3.12.0",
        )

        # Initially should be None
        assert TraceContext.get_current() is None

        # After set, should return the trace
        trace.set_current()
        assert TraceContext.get_current() is trace

        # After clear, should be None again
        TraceContext.clear_current()
        assert TraceContext.get_current() is None

    def test_get_current_trace_initially_none(self) -> None:
        """get_current_trace() should return None when no context is set."""
        # Ensure clean state
        TraceContext.clear_current()
        assert get_current_trace() is None

    def test_get_current_trace_after_set(self) -> None:
        """get_current_trace() should return the set context."""
        trace = TraceContext.create("test command")

        try:
            trace.set_current()
            current = get_current_trace()
            assert current is not None
            assert current is trace
            assert current.command == "test command"
        finally:
            TraceContext.clear_current()

    def test_multiple_set_overwrites_previous(self) -> None:
        """Setting a new context should overwrite the previous one."""
        trace1 = TraceContext.create("command1")
        trace2 = TraceContext.create("command2")

        try:
            trace1.set_current()
            assert get_current_trace() is trace1

            trace2.set_current()
            assert get_current_trace() is trace2
        finally:
            TraceContext.clear_current()


class TestWithTraceDecorator:
    """Tests for with_trace decorator."""

    def test_with_trace_decorator_sets_context(self) -> None:
        """with_trace should set context inside decorated function."""
        captured_trace: TraceContext | None = None

        @with_trace("decorated command")
        def decorated_func() -> str:
            nonlocal captured_trace
            captured_trace = get_current_trace()
            return "result"

        result = decorated_func()

        assert result == "result"
        assert captured_trace is not None
        assert captured_trace.command == "decorated command"

    def test_with_trace_decorator_clears_context(self) -> None:
        """with_trace should clear context after function exits."""
        # Ensure clean state
        TraceContext.clear_current()

        @with_trace("test command")
        def decorated_func() -> None:
            assert get_current_trace() is not None

        decorated_func()

        # Context should be cleared after function completes
        assert get_current_trace() is None

    def test_with_trace_decorator_cleanup_on_exception(self) -> None:
        """with_trace should clear context even when exception is raised."""
        TraceContext.clear_current()

        @with_trace("failing command")
        def failing_func() -> None:
            assert get_current_trace() is not None
            raise ValueError("Intentional error")

        with pytest.raises(ValueError, match="Intentional error"):
            failing_func()

        # Context should still be cleared after exception
        assert get_current_trace() is None

    def test_with_trace_decorator_preserves_function_metadata(self) -> None:
        """with_trace should preserve function name and docstring (functools.wraps)."""

        @with_trace("test")
        def documented_function() -> str:
            """This is the docstring."""
            return "value"

        assert documented_function.__name__ == "documented_function"
        assert documented_function.__doc__ == "This is the docstring."

    def test_with_trace_decorator_passes_arguments(self) -> None:
        """with_trace should correctly pass arguments to decorated function."""
        captured_args: tuple = ()
        captured_kwargs: dict = {}

        @with_trace("args test")
        def func_with_args(a: int, b: str, *, c: bool = False) -> str:
            nonlocal captured_args, captured_kwargs
            captured_args = (a, b)
            captured_kwargs = {"c": c}
            return f"{a}-{b}-{c}"

        result = func_with_args(1, "two", c=True)

        assert result == "1-two-True"
        assert captured_args == (1, "two")
        assert captured_kwargs == {"c": True}

    def test_with_trace_decorator_returns_value(self) -> None:
        """with_trace should return the function's return value."""

        @with_trace("return test")
        def returning_func() -> dict:
            return {"key": "value", "count": 42}

        result = returning_func()
        assert result == {"key": "value", "count": 42}

    def test_with_trace_creates_unique_trace_ids(self) -> None:
        """Each invocation should create a unique trace ID."""
        trace_ids: list[str] = []

        @with_trace("unique test")
        def capture_trace_id() -> None:
            trace = get_current_trace()
            if trace:
                trace_ids.append(trace.trace_id)

        capture_trace_id()
        capture_trace_id()
        capture_trace_id()

        assert len(trace_ids) == 3
        assert len(set(trace_ids)) == 3  # All unique


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def trace_context() -> TraceContext:
    """Create test trace context with fixed values."""
    return TraceContext(
        trace_id="550e8400-e29b-41d4-a716-446655440000",
        command="workflow backfill",
        cli_version="0.9.0",
        os_name="darwin",
        python_version="3.12.1",
    )


@pytest.fixture
def with_trace_context(trace_context: TraceContext):
    """Set trace context for test, cleanup after."""
    trace_context.set_current()
    yield trace_context
    TraceContext.clear_current()


class TestFixtures:
    """Tests using fixtures."""

    def test_fixture_trace_context(self, trace_context: TraceContext) -> None:
        """Verify trace_context fixture values."""
        assert trace_context.trace_id == "550e8400-e29b-41d4-a716-446655440000"
        assert trace_context.command == "workflow backfill"
        assert trace_context.cli_version == "0.9.0"
        assert trace_context.os_name == "darwin"
        assert trace_context.python_version == "3.12.1"

    def test_fixture_with_trace_context(
        self, with_trace_context: TraceContext
    ) -> None:
        """Verify with_trace_context fixture sets current context."""
        current = get_current_trace()
        assert current is not None
        assert current is with_trace_context
        assert current.short_id == "550e8400"

    def test_fixture_cleanup(self, with_trace_context: TraceContext) -> None:
        """Verify context is available in test (cleanup happens after)."""
        assert get_current_trace() is not None


class TestContextIsolation:
    """Tests for context isolation between tests."""

    def test_context_isolation_1(self) -> None:
        """First test: context should be None initially."""
        # If previous test leaked context, this would fail
        assert get_current_trace() is None

    def test_context_isolation_2(self) -> None:
        """Second test: context should still be None."""
        assert get_current_trace() is None

    def test_context_isolation_with_manual_cleanup(self) -> None:
        """Test that sets context and cleans up."""
        trace = TraceContext.create("isolation test")
        trace.set_current()

        try:
            assert get_current_trace() is not None
        finally:
            TraceContext.clear_current()

    def test_context_isolation_3(self) -> None:
        """Third test: verify cleanup worked."""
        assert get_current_trace() is None
