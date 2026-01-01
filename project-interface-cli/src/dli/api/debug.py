"""DebugAPI - Library API for environment diagnostics.

This module provides programmatic access to debug functionality:
- Run all diagnostics
- Run specific category checks
- Check connection, auth, network, server, project, system

Example:
    >>> from dli import DebugAPI, ExecutionContext, ExecutionMode
    >>>
    >>> # Basic usage
    >>> api = DebugAPI(context=ExecutionContext())
    >>> result = api.run_all()
    >>> print(f"Passed: {result.passed_count}/{result.total_count}")
    >>>
    >>> # Mock mode for testing
    >>> ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
    >>> api = DebugAPI(context=ctx)
    >>> result = api.run_all()
    >>> assert result.success  # Always succeeds in mock mode
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from dli import __version__
from dli.core.debug.checks import (
    ALL_CHECKS,
    AUTH_CHECKS,
    CONFIG_CHECKS,
    NETWORK_CHECKS,
    SERVER_CHECKS,
    SYSTEM_CHECKS,
    BaseCheck,
    DnsResolutionCheck,
    HttpsConnectivityCheck,
)
from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus, DebugResult
from dli.models.common import ExecutionContext, ExecutionMode

if TYPE_CHECKING:
    pass


class DebugAPI:
    """Library API for environment diagnostics and connection testing.

    Provides comprehensive environment diagnostics including:
    - System checks: Python version, dli version, OS info
    - Configuration checks: Config file, project path
    - Server checks: Basecamp Server connectivity
    - Authentication checks: API token, Google credentials
    - Network checks: DNS resolution, HTTPS connectivity, proxy

    Attributes:
        context: Execution context with configuration.

    Example:
        >>> from dli import DebugAPI, ExecutionContext
        >>>
        >>> api = DebugAPI(context=ExecutionContext())
        >>>
        >>> # Run all checks
        >>> result = api.run_all()
        >>> for check in result.checks:
        ...     print(f"{check.name}: {check.status.value}")
        >>>
        >>> # Run specific checks
        >>> conn_result = api.check_connection()
        >>> auth_result = api.check_auth()
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
    ) -> None:
        """Initialize DebugAPI.

        Args:
            context: Execution context. If not provided, creates a default one.
        """
        self.context = context or ExecutionContext()

    def __repr__(self) -> str:
        """Return concise representation."""
        mode = self.context.execution_mode.value
        return f"DebugAPI(execution_mode={mode!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _run_checks(self, check_classes: list[type[BaseCheck]]) -> list[CheckResult]:
        """Run a list of check classes.

        Args:
            check_classes: List of check classes to instantiate and run.

        Returns:
            List of check results.
        """
        results: list[CheckResult] = []
        for check_class in check_classes:
            try:
                check = check_class()
                result = check.execute(self.context)
                results.append(result)
            except Exception as e:
                # If check fails to run, record it as an error
                results.append(
                    CheckResult(
                        name=check_class.__name__,
                        category=CheckCategory.SYSTEM,
                        status=CheckStatus.FAIL,
                        message="Check failed to execute",
                        error=str(e),
                        remediation="Check the error message and fix the underlying issue",
                    )
                )
        return results

    def _create_mock_result(self, categories: list[CheckCategory]) -> DebugResult:
        """Create a mock result with all checks passing.

        Args:
            categories: List of categories to include in mock result.

        Returns:
            DebugResult with all checks passing.
        """
        mock_checks: list[CheckResult] = []

        category_names = {
            CheckCategory.SYSTEM: ["Python version", "dli version", "OS info"],
            CheckCategory.CONFIG: ["Config file", "Project path"],
            CheckCategory.SERVER: ["Server URL", "Server connection"],
            CheckCategory.AUTH: ["API token", "Google credentials"],
            CheckCategory.DATABASE: ["Database connection", "Test query"],
            CheckCategory.NETWORK: ["DNS resolution", "HTTPS connectivity", "Proxy configuration"],
        }

        for cat in categories:
            for name in category_names.get(cat, []):
                mock_checks.append(
                    CheckResult(
                        name=name,
                        category=cat,
                        status=CheckStatus.PASS,
                        message="OK (mock)",
                        duration_ms=0,
                    )
                )

        return DebugResult(
            version=__version__,
            success=True,
            checks=mock_checks,
        )

    def _build_result(self, checks: list[CheckResult]) -> DebugResult:
        """Build DebugResult from check results.

        Args:
            checks: List of check results.

        Returns:
            DebugResult with aggregated status.
        """
        # Success if no failures
        success = all(c.status != CheckStatus.FAIL for c in checks)

        return DebugResult(
            version=__version__,
            success=success,
            checks=checks,
        )

    def run_all(self, timeout: int = 30) -> DebugResult:
        """Run all diagnostic checks.

        Args:
            timeout: Overall timeout in seconds (not currently enforced per-check).

        Returns:
            DebugResult with all check results.

        Example:
            >>> result = api.run_all()
            >>> print(f"Passed: {result.passed_count}/{result.total_count}")
        """
        if self._is_mock_mode:
            return self._create_mock_result(
                [
                    CheckCategory.SYSTEM,
                    CheckCategory.CONFIG,
                    CheckCategory.SERVER,
                    CheckCategory.AUTH,
                    CheckCategory.NETWORK,
                ]
            )

        checks = self._run_checks(ALL_CHECKS)
        return self._build_result(checks)

    def check_system(self) -> DebugResult:
        """Run system environment checks only.

        Returns:
            DebugResult with system check results.

        Example:
            >>> result = api.check_system()
            >>> for check in result.checks:
            ...     print(f"{check.name}: {check.message}")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.SYSTEM])

        checks = self._run_checks(SYSTEM_CHECKS)
        return self._build_result(checks)

    def check_project(self) -> DebugResult:
        """Run project configuration checks only.

        Returns:
            DebugResult with config check results.

        Example:
            >>> result = api.check_project()
            >>> if not result.success:
            ...     print("Project configuration issues found")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.CONFIG])

        checks = self._run_checks(CONFIG_CHECKS)
        return self._build_result(checks)

    def check_server(self) -> DebugResult:
        """Run Basecamp Server checks only.

        Returns:
            DebugResult with server check results.

        Example:
            >>> result = api.check_server()
            >>> for check in result.checks:
            ...     if check.status == CheckStatus.FAIL:
            ...         print(f"Server issue: {check.error}")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.SERVER])

        checks = self._run_checks(SERVER_CHECKS)
        return self._build_result(checks)

    def check_auth(self) -> DebugResult:
        """Run authentication checks only.

        Returns:
            DebugResult with auth check results.

        Example:
            >>> result = api.check_auth()
            >>> if result.success:
            ...     print("Authentication configured correctly")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.AUTH])

        checks = self._run_checks(AUTH_CHECKS)
        return self._build_result(checks)

    def check_connection(self, dialect: str | None = None) -> DebugResult:
        """Run database connection checks only.

        Note: Currently this checks network connectivity.
        Full database connectivity checks require database-specific implementations.

        Args:
            dialect: Target dialect (bigquery, trino). Currently informational only.

        Returns:
            DebugResult with connection check results.

        Example:
            >>> result = api.check_connection(dialect="bigquery")
            >>> print(f"Connection checks: {result.passed_count}/{result.total_count}")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.DATABASE])

        # For now, we run network checks that verify endpoint connectivity
        # Full database connection checks would require dialect-specific implementations
        checks: list[CheckResult] = []

        # Check DNS for relevant endpoints based on dialect
        endpoints = {
            "bigquery": [
                ("bigquery.googleapis.com", "https://bigquery.googleapis.com"),
            ],
            "trino": [],  # Would need server-specific URL
        }

        if dialect and dialect in endpoints:
            for hostname, url in endpoints[dialect]:
                dns_check = DnsResolutionCheck(hostname)
                checks.append(dns_check.execute(self.context))
                https_check = HttpsConnectivityCheck(url)
                checks.append(https_check.execute(self.context))
        else:
            # Default: check Google APIs
            dns_check = DnsResolutionCheck("bigquery.googleapis.com")
            checks.append(dns_check.execute(self.context))
            https_check = HttpsConnectivityCheck("https://www.googleapis.com")
            checks.append(https_check.execute(self.context))

        # Update category to DATABASE for these checks
        connection_checks = [
            CheckResult(
                name=c.name,
                category=CheckCategory.DATABASE,
                status=c.status,
                message=c.message,
                details=c.details,
                error=c.error,
                remediation=c.remediation,
                duration_ms=c.duration_ms,
            )
            for c in checks
        ]

        return self._build_result(connection_checks)

    def check_network(self, endpoints: list[str] | None = None) -> DebugResult:
        """Run network diagnostics only.

        Args:
            endpoints: Optional list of URLs to check. If not provided,
                       uses default endpoints.

        Returns:
            DebugResult with network check results.

        Example:
            >>> result = api.check_network()
            >>> for check in result.checks:
            ...     print(f"{check.name}: {check.status.value}")
        """
        if self._is_mock_mode:
            return self._create_mock_result([CheckCategory.NETWORK])

        if endpoints:
            # Run custom endpoint checks
            checks: list[CheckResult] = []
            for url in endpoints:
                https_check = HttpsConnectivityCheck(url)
                checks.append(https_check.execute(self.context))
            return self._build_result(checks)
        # Run default network checks
        checks = self._run_checks(NETWORK_CHECKS)
        return self._build_result(checks)


__all__ = [
    "DebugAPI",
]
