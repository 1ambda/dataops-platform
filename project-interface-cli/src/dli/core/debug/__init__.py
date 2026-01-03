"""Debug module for environment diagnostics and connection testing.

This module provides:
- CheckResult, DebugResult: Data models for diagnostic results
- CheckStatus, CheckCategory: Enums for check state
- BaseCheck: Abstract base class for implementing checks
- Concrete check implementations for system, config, server, etc.

Example:
    >>> from dli.core.debug import CheckStatus, CheckCategory
    >>> from dli.core.debug.checks import PythonVersionCheck
    >>> check = PythonVersionCheck()
    >>> result = check.execute(context)
    >>> if result.status == CheckStatus.PASS:
    ...     print(f"Check passed: {result.message}")
"""

from dli.core.debug.models import (
    CheckCategory,
    CheckResult,
    CheckStatus,
    DebugResult,
)

__all__ = [
    "CheckCategory",
    "CheckResult",
    "CheckStatus",
    "DebugResult",
]
