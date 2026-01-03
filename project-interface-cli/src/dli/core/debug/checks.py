"""Diagnostic check implementations.

This module provides:
- BaseCheck: Abstract base class for diagnostic checks
- Concrete check implementations for various categories

Example:
    >>> from dli.core.debug.checks import PythonVersionCheck
    >>> from dli.models.common import ExecutionContext
    >>>
    >>> check = PythonVersionCheck()
    >>> result = check.execute(ExecutionContext())
    >>> print(f"{check.name}: {result.status.value}")
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path
import platform
import sys
import time
from typing import TYPE_CHECKING, Any

from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus

if TYPE_CHECKING:
    from dli.models.common import ExecutionContext


class BaseCheck(ABC):
    """Base class for diagnostic checks.

    Subclasses must implement:
    - name: Property returning the check name
    - category: Property returning the check category
    - execute: Method that performs the check

    Helper methods are provided for creating results:
    - _pass(): Create a passing result
    - _fail(): Create a failing result
    - _warn(): Create a warning result
    - _skip(): Create a skipped result

    Example:
        >>> class MyCheck(BaseCheck):
        ...     @property
        ...     def name(self) -> str:
        ...         return "My Check"
        ...
        ...     @property
        ...     def category(self) -> CheckCategory:
        ...         return CheckCategory.SYSTEM
        ...
        ...     def execute(self, context: ExecutionContext) -> CheckResult:
        ...         if some_condition:
        ...             return self._pass("Check passed")
        ...         return self._fail("Check failed", "Error details", "Fix it")
    """

    @property
    @abstractmethod
    def name(self) -> str:
        """Return the check name."""
        pass

    @property
    @abstractmethod
    def category(self) -> CheckCategory:
        """Return the check category."""
        pass

    @abstractmethod
    def execute(self, context: ExecutionContext) -> CheckResult:
        """Execute the check.

        Args:
            context: Execution context with configuration.

        Returns:
            CheckResult with status and details.
        """
        pass

    def _pass(
        self,
        message: str,
        duration_ms: int = 0,
        **details: Any,
    ) -> CheckResult:
        """Create a passing check result.

        Args:
            message: Success message.
            duration_ms: Check duration in milliseconds.
            **details: Additional details.

        Returns:
            CheckResult with PASS status.
        """
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.PASS,
            message=message,
            details=details if details else None,
            duration_ms=duration_ms,
        )

    def _fail(
        self,
        message: str,
        error: str,
        remediation: str,
        duration_ms: int = 0,
        **details: Any,
    ) -> CheckResult:
        """Create a failing check result.

        Args:
            message: Failure message.
            error: Error description.
            remediation: Suggested fix.
            duration_ms: Check duration in milliseconds.
            **details: Additional details.

        Returns:
            CheckResult with FAIL status.
        """
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.FAIL,
            message=message,
            error=error,
            remediation=remediation,
            details=details if details else None,
            duration_ms=duration_ms,
        )

    def _warn(
        self,
        message: str,
        duration_ms: int = 0,
        **details: Any,
    ) -> CheckResult:
        """Create a warning check result.

        Args:
            message: Warning message.
            duration_ms: Check duration in milliseconds.
            **details: Additional details.

        Returns:
            CheckResult with WARN status.
        """
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.WARN,
            message=message,
            details=details if details else None,
            duration_ms=duration_ms,
        )

    def _skip(
        self,
        message: str,
        duration_ms: int = 0,
        **details: Any,
    ) -> CheckResult:
        """Create a skipped check result.

        Args:
            message: Skip reason.
            duration_ms: Check duration in milliseconds.
            **details: Additional details.

        Returns:
            CheckResult with SKIP status.
        """
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.SKIP,
            message=message,
            details=details if details else None,
            duration_ms=duration_ms,
        )


# =============================================================================
# System Checks
# =============================================================================


class PythonVersionCheck(BaseCheck):
    """Check Python version meets requirements (>= 3.12)."""

    @property
    def name(self) -> str:
        return "Python version"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.SYSTEM

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check Python version is 3.12 or higher."""
        start = time.perf_counter()
        version = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
        duration_ms = int((time.perf_counter() - start) * 1000)

        if sys.version_info >= (3, 12):
            return self._pass(
                f"Python {version}",
                duration_ms=duration_ms,
                version=version,
            )
        return self._fail(
            f"Python {version}",
            error="Python 3.12+ required",
            remediation="Install Python 3.12 or later: https://python.org/downloads",
            duration_ms=duration_ms,
            version=version,
        )


class DliVersionCheck(BaseCheck):
    """Show current dli version."""

    @property
    def name(self) -> str:
        return "dli version"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.SYSTEM

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Return current dli version."""
        start = time.perf_counter()
        from dli import __version__

        duration_ms = int((time.perf_counter() - start) * 1000)
        return self._pass(
            f"dli {__version__}",
            duration_ms=duration_ms,
            version=__version__,
        )


class OsInfoCheck(BaseCheck):
    """Display OS name and version."""

    @property
    def name(self) -> str:
        return "OS info"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.SYSTEM

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Return OS information."""
        start = time.perf_counter()
        os_name = platform.system().lower()
        os_version = platform.release()
        duration_ms = int((time.perf_counter() - start) * 1000)

        return self._pass(
            f"{os_name} ({platform.platform()})",
            duration_ms=duration_ms,
            os_name=os_name,
            os_version=os_version,
        )


# =============================================================================
# Configuration Checks
# =============================================================================


class ConfigFileCheck(BaseCheck):
    """Check config file existence and validity."""

    @property
    def name(self) -> str:
        return "Config file"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.CONFIG

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check if config file exists."""
        start = time.perf_counter()

        # Check for global config
        global_config = Path.home() / ".dli" / "config.yaml"
        duration_ms = int((time.perf_counter() - start) * 1000)

        if global_config.exists():
            return self._pass(
                f"Found: {global_config}",
                duration_ms=duration_ms,
                path=str(global_config),
            )

        # Check for project-local config
        project_path = context.project_path or Path.cwd()
        local_configs = ["dli.yaml", "dli.yml", ".dli.yaml", ".dli.yml"]

        for config_name in local_configs:
            config_path = project_path / config_name
            if config_path.exists():
                duration_ms = int((time.perf_counter() - start) * 1000)
                return self._pass(
                    f"Found: {config_path}",
                    duration_ms=duration_ms,
                    path=str(config_path),
                )

        duration_ms = int((time.perf_counter() - start) * 1000)
        return self._warn(
            "No config file found",
            duration_ms=duration_ms,
            searched=[str(global_config)]
            + [str(project_path / c) for c in local_configs],
        )


class ProjectPathCheck(BaseCheck):
    """Validate project path configuration."""

    @property
    def name(self) -> str:
        return "Project path"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.CONFIG

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check if project path is valid and accessible."""
        start = time.perf_counter()

        project_path = context.project_path
        if project_path is None:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._warn(
                "Not configured",
                duration_ms=duration_ms,
            )

        if not project_path.exists():
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                f"Path not found: {project_path}",
                error=f"Directory does not exist: {project_path}",
                remediation="Create the directory or update project_path in config",
                duration_ms=duration_ms,
                path=str(project_path),
            )

        if not project_path.is_dir():
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                f"Not a directory: {project_path}",
                error=f"Path is not a directory: {project_path}",
                remediation="Specify a directory path, not a file",
                duration_ms=duration_ms,
                path=str(project_path),
            )

        duration_ms = int((time.perf_counter() - start) * 1000)
        return self._pass(
            str(project_path),
            duration_ms=duration_ms,
            path=str(project_path),
        )


# =============================================================================
# Server Checks
# =============================================================================


class ServerHealthCheck(BaseCheck):
    """Check Basecamp Server connectivity."""

    @property
    def name(self) -> str:
        return "Server connection"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.SERVER

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check server health endpoint."""
        start = time.perf_counter()

        server_url = context.server_url
        if not server_url:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._skip(
                "No server URL configured",
                duration_ms=duration_ms,
            )

        try:
            from dli.core.client import BasecampClient, ServerConfig

            config = ServerConfig(
                url=server_url,
                api_key=context.api_token,
                timeout=min(context.timeout, 10),  # Use shorter timeout for health check
            )
            client = BasecampClient(config=config)
            response = client.health_check()
            duration_ms = int((time.perf_counter() - start) * 1000)

            if response.success:
                return self._pass(
                    f"OK (latency: {duration_ms}ms)",
                    duration_ms=duration_ms,
                    server_url=server_url,
                    latency_ms=duration_ms,
                )
            return self._fail(
                "Health check failed",
                error=response.error or "Unknown error",
                remediation=(
                    "1. Verify server URL is correct\n"
                    "2. Check if server is running\n"
                    "3. Verify network connectivity"
                ),
                duration_ms=duration_ms,
                server_url=server_url,
            )
        except Exception as e:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                "Connection failed",
                error=str(e),
                remediation=(
                    "1. Check server URL is correct\n"
                    "2. Verify network connectivity\n"
                    "3. Check firewall settings"
                ),
                duration_ms=duration_ms,
                server_url=server_url,
            )


class ServerUrlCheck(BaseCheck):
    """Check server URL is configured."""

    @property
    def name(self) -> str:
        return "Server URL"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.SERVER

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check if server URL is configured."""
        start = time.perf_counter()

        server_url = context.server_url
        duration_ms = int((time.perf_counter() - start) * 1000)

        if server_url:
            return self._pass(
                server_url,
                duration_ms=duration_ms,
                server_url=server_url,
            )
        return self._warn(
            "Not configured",
            duration_ms=duration_ms,
        )


# =============================================================================
# Authentication Checks
# =============================================================================


class ApiTokenCheck(BaseCheck):
    """Check API token is configured."""

    @property
    def name(self) -> str:
        return "API token"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.AUTH

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check if API token is configured."""
        start = time.perf_counter()

        api_token = context.api_token
        duration_ms = int((time.perf_counter() - start) * 1000)

        if api_token:
            # Mask the token for display
            masked = api_token[:4] + "****" + api_token[-4:] if len(api_token) > 8 else "****"
            return self._pass(
                f"Configured ({masked})",
                duration_ms=duration_ms,
            )
        return self._warn(
            "Not configured",
            duration_ms=duration_ms,
        )


class GoogleCredentialsCheck(BaseCheck):
    """Check Google Application Default Credentials."""

    @property
    def name(self) -> str:
        return "Google credentials"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.AUTH

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check if Google credentials are configured."""
        import os

        start = time.perf_counter()

        # Check GOOGLE_APPLICATION_CREDENTIALS env var
        gac = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
        duration_ms = int((time.perf_counter() - start) * 1000)

        if gac:
            gac_path = Path(gac)
            if gac_path.exists():
                return self._pass(
                    f"Found: {gac}",
                    duration_ms=duration_ms,
                    path=gac,
                )
            return self._fail(
                "File not found",
                error=f"GOOGLE_APPLICATION_CREDENTIALS points to non-existent file: {gac}",
                remediation="Verify the path in GOOGLE_APPLICATION_CREDENTIALS exists",
                duration_ms=duration_ms,
                path=gac,
            )
        # Check for default credentials location
        default_path = Path.home() / ".config" / "gcloud" / "application_default_credentials.json"
        if default_path.exists():
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._pass(
                f"Found: {default_path}",
                duration_ms=duration_ms,
                path=str(default_path),
            )
        return self._warn(
            "Not configured",
            duration_ms=duration_ms,
        )


# =============================================================================
# Network Checks
# =============================================================================


class DnsResolutionCheck(BaseCheck):
    """Check DNS resolution for endpoints."""

    def __init__(self, hostname: str = ""):
        """Initialize with optional hostname.

        Args:
            hostname: Hostname to resolve. If empty, uses a default.
        """
        self._hostname = hostname or "bigquery.googleapis.com"

    @property
    def name(self) -> str:
        return f"DNS resolution ({self._hostname})"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.NETWORK

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check DNS resolution."""
        import socket

        start = time.perf_counter()

        try:
            ip = socket.gethostbyname(self._hostname)
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._pass(
                f"{self._hostname} -> {ip}",
                duration_ms=duration_ms,
                hostname=self._hostname,
                ip=ip,
            )
        except socket.gaierror as e:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                f"Failed to resolve {self._hostname}",
                error=str(e),
                remediation=(
                    "1. Check DNS settings\n"
                    "2. Verify network connectivity\n"
                    "3. Try using a different DNS server"
                ),
                duration_ms=duration_ms,
                hostname=self._hostname,
            )


class HttpsConnectivityCheck(BaseCheck):
    """Check HTTPS connectivity to an endpoint."""

    def __init__(self, url: str = ""):
        """Initialize with optional URL.

        Args:
            url: URL to check. If empty, uses a default.
        """
        self._url = url or "https://www.googleapis.com"

    @property
    def name(self) -> str:
        return f"HTTPS connectivity ({self._url})"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.NETWORK

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Check HTTPS connectivity."""
        import urllib.error
        import urllib.request

        start = time.perf_counter()

        try:
            req = urllib.request.Request(
                self._url,
                method="HEAD",
                headers={"User-Agent": "dli-debug/1.0"},
            )
            with urllib.request.urlopen(req, timeout=10) as response:
                status = response.status
            duration_ms = int((time.perf_counter() - start) * 1000)

            if status < 400:
                return self._pass(
                    f"OK (HTTP {status}, latency: {duration_ms}ms)",
                    duration_ms=duration_ms,
                    url=self._url,
                    status_code=status,
                )
            # 4xx/5xx received, but connection was successful
            return self._pass(
                f"OK (HTTP {status}, latency: {duration_ms}ms)",
                duration_ms=duration_ms,
                url=self._url,
                status_code=status,
            )
        except urllib.error.HTTPError as e:
            # HTTPError means we connected but got an error status code
            # This still means connectivity works!
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._pass(
                f"OK (HTTP {e.code}, latency: {duration_ms}ms)",
                duration_ms=duration_ms,
                url=self._url,
                status_code=e.code,
            )
        except urllib.error.URLError as e:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                "Connection failed",
                error=str(e.reason),
                remediation=(
                    "1. Check network connectivity\n"
                    "2. Verify firewall rules\n"
                    "3. Check proxy settings"
                ),
                duration_ms=duration_ms,
                url=self._url,
            )
        except Exception as e:
            duration_ms = int((time.perf_counter() - start) * 1000)
            return self._fail(
                "Connection error",
                error=str(e),
                remediation="Check network connectivity and proxy settings",
                duration_ms=duration_ms,
                url=self._url,
            )


class ProxyDetectionCheck(BaseCheck):
    """Detect HTTP/HTTPS proxy configuration."""

    @property
    def name(self) -> str:
        return "Proxy configuration"

    @property
    def category(self) -> CheckCategory:
        return CheckCategory.NETWORK

    def execute(self, context: ExecutionContext) -> CheckResult:
        """Detect proxy configuration."""
        import os

        start = time.perf_counter()

        http_proxy = os.environ.get("HTTP_PROXY") or os.environ.get("http_proxy")
        https_proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
        no_proxy = os.environ.get("NO_PROXY") or os.environ.get("no_proxy")

        duration_ms = int((time.perf_counter() - start) * 1000)

        proxies = []
        if http_proxy:
            proxies.append(f"HTTP: {http_proxy}")
        if https_proxy:
            proxies.append(f"HTTPS: {https_proxy}")

        if proxies:
            return self._pass(
                ", ".join(proxies),
                duration_ms=duration_ms,
                http_proxy=http_proxy,
                https_proxy=https_proxy,
                no_proxy=no_proxy,
            )
        return self._pass(
            "Not detected",
            duration_ms=duration_ms,
        )


# =============================================================================
# Check Registry
# =============================================================================

# Default checks for each category
SYSTEM_CHECKS: list[type[BaseCheck]] = [
    PythonVersionCheck,
    DliVersionCheck,
    OsInfoCheck,
]

CONFIG_CHECKS: list[type[BaseCheck]] = [
    ConfigFileCheck,
    ProjectPathCheck,
]

SERVER_CHECKS: list[type[BaseCheck]] = [
    ServerUrlCheck,
    ServerHealthCheck,
]

AUTH_CHECKS: list[type[BaseCheck]] = [
    ApiTokenCheck,
    GoogleCredentialsCheck,
]

NETWORK_CHECKS: list[type[BaseCheck]] = [
    DnsResolutionCheck,
    HttpsConnectivityCheck,
    ProxyDetectionCheck,
]

# All default checks
ALL_CHECKS: list[type[BaseCheck]] = (
    SYSTEM_CHECKS + CONFIG_CHECKS + SERVER_CHECKS + AUTH_CHECKS + NETWORK_CHECKS
)


__all__ = [
    # Base class
    "BaseCheck",
    # System checks
    "PythonVersionCheck",
    "DliVersionCheck",
    "OsInfoCheck",
    # Config checks
    "ConfigFileCheck",
    "ProjectPathCheck",
    # Server checks
    "ServerUrlCheck",
    "ServerHealthCheck",
    # Auth checks
    "ApiTokenCheck",
    "GoogleCredentialsCheck",
    # Network checks
    "DnsResolutionCheck",
    "HttpsConnectivityCheck",
    "ProxyDetectionCheck",
    # Check lists
    "SYSTEM_CHECKS",
    "CONFIG_CHECKS",
    "SERVER_CHECKS",
    "AUTH_CHECKS",
    "NETWORK_CHECKS",
    "ALL_CHECKS",
]
