# FEATURE: Debug Command - Environment Diagnostics and Connection Testing

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | ✅ Implemented |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-01 |
| **References** | dbt debug, BigQuery CLI, Trino CLI |
| **Release Notes** | [DEBUG_RELEASE.md](./DEBUG_RELEASE.md) |

---

## 1. Overview

### 1.1 Purpose

`dli debug` provides comprehensive environment diagnostics and connection testing for the DLI CLI. It validates the complete development environment including system configuration, project setup, authentication, and database connectivity.

**Key Use Cases:**

| Use Case | Description |
|----------|-------------|
| **First-time Setup** | Validate initial CLI installation and configuration |
| **Connection Troubleshooting** | Diagnose BigQuery/Trino connectivity issues |
| **Authentication Verification** | Confirm Service Account or OAuth credentials |
| **CI/CD Pipeline Validation** | Pre-flight checks before running data pipelines |
| **Environment Migration** | Verify configuration after environment changes |

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Connection Test First** | Database connectivity is the primary diagnostic focus |
| **Progressive Disclosure** | Default shows summary; flags enable detailed diagnostics |
| **Actionable Output** | Every failure includes specific remediation guidance |
| **Non-Destructive** | All checks are read-only; no modifications to environment |
| **Exit Code Semantics** | Exit 0 = all checks pass; Exit 1 = any check fails |

### 1.3 Key Features

| Feature | Description |
|---------|-------------|
| **System Environment** | Python version, dli version, OS information |
| **Project Configuration** | Config file location, project path validation |
| **Server Connection** | Basecamp Server reachability and latency |
| **Database Connectivity** | BigQuery/Trino connection test with sample query |
| **Authentication Status** | Credential validation (Service Account, OAuth, API Key) |
| **Network Diagnostics** | Endpoint reachability, proxy detection, SSL verification |
| **Focused Checks** | `--connection`, `--auth`, `--network` for targeted diagnostics |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to `dli debug` |
|------|--------------|------------------------|
| **dbt debug** | Project validation, adapter connection test, profiles.yml check | Project config + connection pattern |
| **BigQuery bq** | `bq show` for connection test, credential validation | Auth and connection checks |
| **Trino CLI** | `--execute "SELECT 1"` health check | Database connectivity test |
| **kubectl** | `kubectl cluster-info` diagnostic output format | Clear status output format |

### 1.5 System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **CLI Commands** | `commands/config.py` status check | Extend config status pattern |
| **Client** | `BasecampClient.health_check()` | Reuse server health method |
| **Exceptions** | DLI-xxx error codes | Add DLI-95x for debug errors |
| **Library API** | `ConfigAPI` facade pattern | `DebugAPI` follows same pattern |
| **Models** | `ExecutionContext` | Reuse for connection testing |

---

## 2. CLI Design

### 2.1 Command Structure

```
dli debug [options]
```

### 2.2 Subcommands and Options

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--connection` | `-c` | FLAG | `false` | Test database connectivity only |
| `--auth` | `-a` | FLAG | `false` | Test authentication only |
| `--network` | `-n` | FLAG | `false` | Test network connectivity only |
| `--server` | `-s` | FLAG | `false` | Test Basecamp Server connection only |
| `--project` | `-p` | FLAG | `false` | Validate project configuration only |
| `--verbose` | `-v` | FLAG | `false` | Show detailed diagnostic information |
| `--json` | - | FLAG | `false` | Output in JSON format |
| `--dialect` | `-d` | ENUM | (auto) | Target dialect: `bigquery`, `trino` |
| `--path` | - | PATH | `.` | Project path for config resolution |
| `--timeout` | `-t` | INT | `30` | Connection timeout in seconds |

### 2.3 Flag Behavior

| Flag Combination | Behavior |
|------------------|----------|
| (no flags) | Run all diagnostic checks |
| `--connection` | Database connection test only |
| `--auth` | Authentication validation only |
| `--network` | Network endpoint checks only |
| `--server` | Basecamp Server health check only |
| `--project` | Project configuration validation only |
| Multiple flags | Run only specified checks |

### 2.4 Examples

```bash
# Full diagnostic (default)
$ dli debug
dli debug v0.7.0

System:
  [PASS] Python version: 3.12.1
  [PASS] dli version: 0.7.0
  [PASS] OS: darwin (Darwin 24.4.0)

Configuration:
  [PASS] Config file: ~/.dli/config.yaml
  [PASS] Project path: /opt/airflow/dags/models
  [PASS] Active environment: production

Server:
  [PASS] Basecamp Server: https://basecamp.example.com
  [PASS] Connection: OK (latency: 45ms)
  [PASS] API version: v1.2.0

Authentication:
  [PASS] Method: Service Account
  [PASS] Account: dataops@project.iam.gserviceaccount.com
  [PASS] Token status: Valid (expires in 58m)

Database:
  [PASS] BigQuery project: my-gcp-project
  [PASS] Connection: OK (latency: 120ms)
  [PASS] Test query: Success (SELECT 1)

All checks passed (12/12)

# Connection test only
$ dli debug --connection
dli debug v0.7.0

Database:
  [PASS] BigQuery project: my-gcp-project
  [PASS] Connection: OK (latency: 120ms)
  [PASS] Test query: Success (SELECT 1)

Connection checks passed (3/3)

# Auth check only
$ dli debug --auth
dli debug v0.7.0

Authentication:
  [PASS] Method: Service Account
  [PASS] Account: dataops@project.iam.gserviceaccount.com
  [PASS] Token status: Valid (expires in 58m)

Authentication checks passed (3/3)

# Network diagnostics
$ dli debug --network
dli debug v0.7.0

Network:
  [PASS] DNS resolution: bigquery.googleapis.com -> 142.250.x.x
  [PASS] HTTPS connectivity: bigquery.googleapis.com:443
  [PASS] SSL certificate: Valid (expires 2026-04-15)
  [PASS] Proxy: Not detected
  [PASS] DNS resolution: basecamp.example.com -> 10.0.1.x
  [PASS] HTTPS connectivity: basecamp.example.com:443

Network checks passed (6/6)

# Verbose output
$ dli debug --connection --verbose
dli debug v0.7.0

Database:
  [PASS] BigQuery project: my-gcp-project
         └─ Dataset: analytics
         └─ Location: US
  [PASS] Connection: OK (latency: 120ms)
         └─ Attempt 1: 120ms
         └─ Retries: 0
  [PASS] Test query: Success (SELECT 1)
         └─ Bytes processed: 0
         └─ Cache hit: false
         └─ Slot time: 0ms

Connection checks passed (3/3)

# JSON output for automation
$ dli debug --json
{
  "version": "0.7.0",
  "timestamp": "2026-01-01T10:30:00Z",
  "success": true,
  "checks": {
    "system": {"status": "pass", "details": {...}},
    "config": {"status": "pass", "details": {...}},
    "server": {"status": "pass", "details": {...}},
    "auth": {"status": "pass", "details": {...}},
    "database": {"status": "pass", "details": {...}}
  },
  "summary": {"passed": 12, "failed": 0, "total": 12}
}

# Failure example with remediation
$ dli debug --connection
dli debug v0.7.0

Database:
  [PASS] BigQuery project: my-gcp-project
  [FAIL] Connection: Failed
         └─ Error: Could not connect to BigQuery API
         └─ Cause: Network timeout after 30s

         Remediation:
         1. Check network connectivity: ping bigquery.googleapis.com
         2. Verify firewall rules allow outbound HTTPS (443)
         3. If using proxy, set HTTPS_PROXY environment variable
         4. Run 'dli debug --network' for detailed network diagnostics
  [SKIP] Test query: Skipped (connection failed)

Connection checks failed (1/3)

# Trino dialect
$ dli debug --connection --dialect trino
dli debug v0.7.0

Database:
  [PASS] Trino cluster: trino.example.com:8443
  [PASS] Connection: OK (latency: 85ms)
  [PASS] Test query: Success (SELECT 1)
  [PASS] Catalog: hive (accessible)

Connection checks passed (4/4)
```

---

## 3. Diagnostic Checks

### 3.1 System Environment Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| Python version | Verify Python >= 3.12 | Install Python 3.12+ |
| dli version | Show current dli version | Run `uv pip install --upgrade dli` |
| OS information | Display OS name and version | Informational only |
| Required packages | Verify critical dependencies | Run `uv sync` |

### 3.2 Configuration Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| Config file | Locate and validate config file | Run `dli config init` |
| Project path | Validate project directory exists | Set correct `--path` or update config |
| Environment | Verify active environment is valid | Check `environments.yaml` |
| Credentials file | Locate service account JSON | Set `GOOGLE_APPLICATION_CREDENTIALS` |

### 3.3 Server Connection Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| URL resolution | DNS lookup for server hostname | Check DNS settings |
| HTTPS connection | TLS handshake with server | Verify SSL certificate |
| Health endpoint | Call `/health` API | Check server status |
| API version | Verify compatible API version | Update dli or server |
| Latency | Measure round-trip time | Informational |

### 3.4 Authentication Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| Auth method | Detect Service Account vs OAuth | Configure credentials |
| Credential validity | Verify credentials are valid | Refresh or recreate credentials |
| Token status | Check token expiration | Re-authenticate if expired |
| Permissions | Verify required IAM roles | Grant necessary permissions |

### 3.5 Database Connection Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| Project/Cluster | Validate target exists | Check configuration |
| Connection | Establish database connection | Check network/credentials |
| Test query | Execute `SELECT 1` | Check query permissions |
| Dataset/Catalog | Verify default dataset accessible | Grant dataset access |

### 3.6 Network Checks

| Check | Description | Failure Remediation |
|-------|-------------|---------------------|
| DNS resolution | Resolve all required hostnames | Check DNS settings |
| Port connectivity | Verify HTTPS (443) reachable | Check firewall rules |
| SSL certificate | Validate certificate chain | Update CA certificates |
| Proxy detection | Detect HTTP/HTTPS proxy | Configure proxy settings |
| VPC connectivity | Test internal endpoints | Check VPC peering |

---

## 4. Library API Design

### 4.1 DebugAPI Class

```python
from dli.api.debug import DebugAPI, DebugResult, CheckResult, CheckStatus
from dli.models.common import ExecutionContext

# Initialize
api = DebugAPI(context=ExecutionContext(project_path=Path(".")))

# Run all diagnostics
result: DebugResult = api.run_all()
print(f"Passed: {result.passed_count}/{result.total_count}")

# Run specific checks
conn_result = api.check_connection(dialect="bigquery")
auth_result = api.check_auth()
network_result = api.check_network()
server_result = api.check_server()
project_result = api.check_project()

# Get individual check status
for check in result.checks:
    print(f"{check.name}: {check.status.value}")
    if check.status == CheckStatus.FAIL:
        print(f"  Error: {check.error}")
        print(f"  Remediation: {check.remediation}")
```

### 4.2 API Methods

| Method | Parameters | Return Type | Description |
|--------|------------|-------------|-------------|
| `run_all()` | `timeout: int = 30` | `DebugResult` | Run all diagnostic checks |
| `check_connection()` | `dialect: str = None` | `DebugResult` | Database connectivity only |
| `check_auth()` | - | `DebugResult` | Authentication validation only |
| `check_network()` | `endpoints: list = None` | `DebugResult` | Network diagnostics only |
| `check_server()` | - | `DebugResult` | Basecamp Server health only |
| `check_project()` | - | `DebugResult` | Project configuration only |
| `check_system()` | - | `DebugResult` | System environment only |

### 4.3 Mock Mode

```python
from dli import DebugAPI, ExecutionContext, ExecutionMode

# Mock mode for testing
ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
api = DebugAPI(context=ctx)

result = api.run_all()
assert result.success  # Always succeeds in mock mode
```

---

## 5. Data Models

### 5.1 DebugResult

```python
from enum import Enum
from pydantic import BaseModel, Field
from datetime import datetime, UTC

class CheckStatus(str, Enum):
    """Status of a diagnostic check."""
    PASS = "pass"
    FAIL = "fail"
    WARN = "warn"
    SKIP = "skip"

class CheckCategory(str, Enum):
    """Category of diagnostic check."""
    SYSTEM = "system"
    CONFIG = "config"
    SERVER = "server"
    AUTH = "auth"
    DATABASE = "database"
    NETWORK = "network"

class CheckResult(BaseModel):
    """Result of a single diagnostic check."""
    name: str = Field(..., description="Check name")
    category: CheckCategory = Field(..., description="Check category")
    status: CheckStatus = Field(..., description="Check status")
    message: str = Field(..., description="Status message")
    details: dict | None = Field(default=None, description="Additional details")
    error: str | None = Field(default=None, description="Error message if failed")
    remediation: str | None = Field(default=None, description="Fix suggestion if failed")
    duration_ms: int = Field(default=0, description="Check duration in milliseconds")

class DebugResult(BaseModel):
    """Complete debug diagnostic result."""
    version: str = Field(..., description="dli version")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))
    success: bool = Field(..., description="All checks passed")
    checks: list[CheckResult] = Field(default_factory=list)

    @property
    def passed_count(self) -> int:
        return sum(1 for c in self.checks if c.status == CheckStatus.PASS)

    @property
    def failed_count(self) -> int:
        return sum(1 for c in self.checks if c.status == CheckStatus.FAIL)

    @property
    def total_count(self) -> int:
        return len(self.checks)

    @property
    def by_category(self) -> dict[CheckCategory, list[CheckResult]]:
        result = {}
        for check in self.checks:
            if check.category not in result:
                result[check.category] = []
            result[check.category].append(check)
        return result
```

### 5.2 Connection Models

```python
class ConnectionInfo(BaseModel):
    """Database connection information."""
    dialect: str = Field(..., description="Database dialect (bigquery, trino)")
    host: str | None = Field(default=None, description="Host for Trino")
    project: str | None = Field(default=None, description="GCP project for BigQuery")
    dataset: str | None = Field(default=None, description="Default dataset")
    catalog: str | None = Field(default=None, description="Default catalog for Trino")
    latency_ms: int | None = Field(default=None, description="Connection latency")

class AuthInfo(BaseModel):
    """Authentication information."""
    method: str = Field(..., description="Auth method (service_account, oauth, api_key)")
    account: str | None = Field(default=None, description="Service account email")
    token_valid: bool = Field(default=False, description="Token is valid")
    token_expires: datetime | None = Field(default=None, description="Token expiration")

class ServerInfo(BaseModel):
    """Basecamp Server information."""
    url: str = Field(..., description="Server URL")
    reachable: bool = Field(default=False, description="Server is reachable")
    api_version: str | None = Field(default=None, description="API version")
    latency_ms: int | None = Field(default=None, description="Connection latency")
```

---

## 6. Error Codes

### 6.1 Debug Error Codes (DLI-95x)

> **Note:** DLI-9xx (900-904) is reserved for Lineage errors. Debug uses DLI-95x sub-range.

| Code | Name | Description |
|------|------|-------------|
| `DLI-950` | `DEBUG_SYSTEM_CHECK_FAILED` | System environment check failed |
| `DLI-951` | `DEBUG_CONFIG_CHECK_FAILED` | Configuration check failed |
| `DLI-952` | `DEBUG_SERVER_CHECK_FAILED` | Server connection check failed |
| `DLI-953` | `DEBUG_AUTH_CHECK_FAILED` | Authentication check failed |
| `DLI-954` | `DEBUG_CONNECTION_CHECK_FAILED` | Database connection check failed |
| `DLI-955` | `DEBUG_NETWORK_CHECK_FAILED` | Network check failed |
| `DLI-956` | `DEBUG_TIMEOUT` | Check timed out |

### 6.2 Exit Codes

| Exit Code | Meaning |
|-----------|---------|
| `0` | All checks passed |
| `1` | One or more checks failed |
| `2` | Invalid arguments or configuration |

---

## 7. Implementation Details

### 7.1 Directory Structure

```
src/dli/
├── api/
│   └── debug.py          # DebugAPI class
├── commands/
│   └── debug.py          # CLI command (debug_app)
├── core/
│   └── debug/
│       ├── __init__.py
│       ├── checks.py     # Individual check implementations
│       ├── models.py     # CheckResult, DebugResult
│       └── formatters.py # Output formatters (table, json)
└── main.py               # Register debug_app
```

### 7.2 Check Implementation Pattern

```python
# src/dli/core/debug/checks.py

from abc import ABC, abstractmethod
from dli.core.debug.models import CheckResult, CheckStatus, CheckCategory

class BaseCheck(ABC):
    """Base class for diagnostic checks."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Check name."""
        pass

    @property
    @abstractmethod
    def category(self) -> CheckCategory:
        """Check category."""
        pass

    @abstractmethod
    def execute(self, context: ExecutionContext) -> CheckResult:
        """Execute the check."""
        pass

    def _pass(self, message: str, **details) -> CheckResult:
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.PASS,
            message=message,
            details=details or None,
        )

    def _fail(self, message: str, error: str, remediation: str, **details) -> CheckResult:
        return CheckResult(
            name=self.name,
            category=self.category,
            status=CheckStatus.FAIL,
            message=message,
            error=error,
            remediation=remediation,
            details=details or None,
        )

class PythonVersionCheck(BaseCheck):
    """Check Python version meets requirements."""

    name = "Python version"
    category = CheckCategory.SYSTEM

    def execute(self, context: ExecutionContext) -> CheckResult:
        import sys
        version = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"

        if sys.version_info >= (3, 12):
            return self._pass(f"Python {version}", version=version)
        else:
            return self._fail(
                f"Python {version}",
                error="Python 3.12+ required",
                remediation="Install Python 3.12 or later: https://python.org/downloads",
                version=version,
            )

class BigQueryConnectionCheck(BaseCheck):
    """Check BigQuery connectivity."""

    name = "BigQuery connection"
    category = CheckCategory.DATABASE

    def execute(self, context: ExecutionContext) -> CheckResult:
        from google.cloud import bigquery
        import time

        try:
            client = bigquery.Client()
            start = time.time()
            result = client.query("SELECT 1").result()
            latency_ms = int((time.time() - start) * 1000)

            return self._pass(
                f"OK (latency: {latency_ms}ms)",
                project=client.project,
                latency_ms=latency_ms,
            )
        except Exception as e:
            return self._fail(
                "Failed",
                error=str(e),
                remediation=(
                    "1. Check GOOGLE_APPLICATION_CREDENTIALS is set\n"
                    "2. Verify service account has BigQuery access\n"
                    "3. Run 'dli debug --network' to check connectivity"
                ),
            )
```

### 7.3 CLI Command Pattern

```python
# src/dli/commands/debug.py

from pathlib import Path
from typing import Annotated
import typer
from dli.api.debug import DebugAPI
from dli.commands.base import get_project_path
from dli.commands.utils import console, print_error, print_success
from dli.models.common import ExecutionContext

debug_app = typer.Typer(name="debug", help="Environment diagnostics and connection testing")

@debug_app.callback(invoke_without_command=True)
def debug(
    ctx: typer.Context,
    connection: Annotated[bool, typer.Option("--connection", "-c", help="Test database connectivity only")] = False,
    auth: Annotated[bool, typer.Option("--auth", "-a", help="Test authentication only")] = False,
    network: Annotated[bool, typer.Option("--network", "-n", help="Test network connectivity only")] = False,
    server: Annotated[bool, typer.Option("--server", "-s", help="Test Basecamp Server only")] = False,
    project: Annotated[bool, typer.Option("--project", "-p", help="Validate project configuration only")] = False,
    verbose: Annotated[bool, typer.Option("--verbose", "-v", help="Show detailed output")] = False,
    json_output: Annotated[bool, typer.Option("--json", help="Output in JSON format")] = False,
    dialect: Annotated[str | None, typer.Option("--dialect", "-d", help="Target dialect")] = None,
    path: Annotated[Path | None, typer.Option("--path", help="Project path")] = None,
    timeout: Annotated[int, typer.Option("--timeout", "-t", help="Timeout in seconds")] = 30,
) -> None:
    """Run environment diagnostics and connection tests."""

    project_path = get_project_path(path)
    context = ExecutionContext(project_path=project_path, timeout=timeout)
    api = DebugAPI(context=context)

    # Determine which checks to run
    run_all = not any([connection, auth, network, server, project])

    if run_all:
        result = api.run_all()
    else:
        # Run selected checks
        checks = []
        if connection:
            checks.append(api.check_connection(dialect=dialect))
        if auth:
            checks.append(api.check_auth())
        if network:
            checks.append(api.check_network())
        if server:
            checks.append(api.check_server())
        if project:
            checks.append(api.check_project())

        # Merge results
        result = merge_debug_results(checks)

    # Output
    if json_output:
        console.print_json(result.model_dump_json())
    else:
        print_debug_result(result, verbose=verbose)

    # Exit code
    if not result.success:
        raise typer.Exit(1)
```

---

## 8. Test Strategy

### 8.1 Unit Tests

```python
# tests/cli/test_debug_cmd.py

from typer.testing import CliRunner
from dli.main import app

runner = CliRunner()

class TestDebugCommand:
    """Test debug CLI command."""

    def test_debug_basic(self):
        """Test basic debug command."""
        result = runner.invoke(app, ["debug"])
        assert result.exit_code in [0, 1]  # 0=pass, 1=fail
        assert "dli debug" in result.output

    def test_debug_connection_flag(self):
        """Test --connection flag."""
        result = runner.invoke(app, ["debug", "--connection"])
        assert "Database:" in result.output or "Connection" in result.output

    def test_debug_auth_flag(self):
        """Test --auth flag."""
        result = runner.invoke(app, ["debug", "--auth"])
        assert "Authentication:" in result.output

    def test_debug_json_output(self):
        """Test --json output format."""
        result = runner.invoke(app, ["debug", "--json"])
        import json
        data = json.loads(result.output)
        assert "success" in data
        assert "checks" in data

    def test_debug_verbose(self):
        """Test --verbose flag shows more details."""
        result = runner.invoke(app, ["debug", "--verbose"])
        # Verbose output contains indented details
        assert "└─" in result.output or result.exit_code == 1
```

### 8.2 API Tests

```python
# tests/api/test_debug_api.py

import pytest
from dli import DebugAPI, ExecutionContext, ExecutionMode
from dli.core.debug.models import CheckStatus, CheckCategory

class TestDebugAPI:
    """Test DebugAPI class."""

    @pytest.fixture
    def mock_api(self) -> DebugAPI:
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return DebugAPI(context=ctx)

    def test_run_all_mock(self, mock_api):
        """Test run_all in mock mode."""
        result = mock_api.run_all()
        assert result.success
        assert result.total_count > 0
        assert result.passed_count == result.total_count

    def test_check_connection_mock(self, mock_api):
        """Test check_connection in mock mode."""
        result = mock_api.check_connection()
        assert result.success

    def test_check_auth_mock(self, mock_api):
        """Test check_auth in mock mode."""
        result = mock_api.check_auth()
        assert result.success

    def test_result_by_category(self, mock_api):
        """Test grouping results by category."""
        result = mock_api.run_all()
        by_cat = result.by_category
        assert CheckCategory.SYSTEM in by_cat

    def test_failed_check_has_remediation(self):
        """Test that failed checks include remediation."""
        # Use real context that will fail without proper config
        ctx = ExecutionContext(project_path=Path("/nonexistent"))
        api = DebugAPI(context=ctx)
        result = api.check_project()

        for check in result.checks:
            if check.status == CheckStatus.FAIL:
                assert check.remediation is not None
```

### 8.3 Integration Tests

```python
# tests/integration/test_debug_integration.py

import pytest
from pathlib import Path
from dli import DebugAPI, ExecutionContext

@pytest.mark.integration
class TestDebugIntegration:
    """Integration tests for debug functionality."""

    @pytest.fixture
    def real_api(self, tmp_path) -> DebugAPI:
        ctx = ExecutionContext(project_path=tmp_path)
        return DebugAPI(context=ctx)

    def test_system_checks_pass(self, real_api):
        """System checks should pass in valid environment."""
        result = real_api.check_system()
        assert result.success

    @pytest.mark.skipif(
        not Path.home().joinpath(".dli/config.yaml").exists(),
        reason="No config file"
    )
    def test_config_checks(self, real_api):
        """Config checks with real config file."""
        result = real_api.check_project()
        # May pass or fail depending on environment
        assert result.total_count > 0
```

---

## 9. Success Criteria

### 9.1 Functional Requirements

| Requirement | Acceptance Criteria |
|-------------|---------------------|
| Full diagnostic | `dli debug` runs all checks and reports summary |
| Focused checks | Each `--connection`, `--auth`, `--network`, `--server`, `--project` works independently |
| JSON output | `--json` produces valid, parseable JSON |
| Verbose mode | `--verbose` shows additional detail for each check |
| Exit codes | Exit 0 when all checks pass, Exit 1 when any fails |
| Remediation | Every failed check includes actionable remediation guidance |

### 9.2 Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Full diagnostic time | < 30 seconds (default timeout) |
| Connection check time | < 5 seconds per endpoint |
| Memory usage | < 100MB additional |
| No side effects | All checks are read-only |

### 9.3 Test Coverage

| Area | Target Coverage |
|------|-----------------|
| CLI commands | 90%+ |
| API methods | 95%+ |
| Check implementations | 85%+ |
| Error paths | 100% of error codes tested |

---

## 10. Implementation Phases

### Phase 1: Core Infrastructure (Week 1)

| Task | Description |
|------|-------------|
| Models | Implement `CheckResult`, `DebugResult` models |
| Base check | Create `BaseCheck` abstract class |
| System checks | Python version, dli version, OS info |
| CLI skeleton | Basic `dli debug` command with system checks |

### Phase 2: Configuration and Server (Week 2)

| Task | Description |
|------|-------------|
| Config checks | Config file, project path, environment |
| Server checks | Basecamp Server health, API version |
| Output formatters | Table and JSON formatters |
| Focused flags | `--project`, `--server` flags |

### Phase 3: Database and Auth (Week 3)

| Task | Description |
|------|-------------|
| BigQuery check | Connection, test query |
| Trino check | Connection, test query |
| Auth checks | Service Account, OAuth validation |
| Focused flags | `--connection`, `--auth` flags |

### Phase 4: Network and Polish (Week 4)

| Task | Description |
|------|-------------|
| Network checks | DNS, HTTPS, SSL, proxy detection |
| Focused flag | `--network` flag |
| Verbose mode | Detailed output implementation |
| Documentation | Update README, add examples |

---

## Appendix A: Reference Patterns

### A.1 Existing Code References

| Pattern | Reference File | Applied To |
|---------|----------------|------------|
| CLI command structure | `commands/config.py` | `debug.py` command |
| API class pattern | `api/config.py` | `DebugAPI` class |
| Model definitions | `models/common.py` | `CheckResult`, `DebugResult` |
| Client methods | `core/client.py` | Server health check |
| Rich output | `commands/utils.py` | Console output formatting |

### A.2 dbt debug Reference

```bash
# dbt debug output format (reference)
$ dbt debug
Running with dbt=1.7.0
dbt version: 1.7.0
python version: 3.12.1
python path: /usr/bin/python3
os info: Linux-5.15.0
Using profiles.yml file at ~/.dbt/profiles.yml
Using dbt_project.yml file at /opt/dbt/dbt_project.yml

Configuration:
  profiles.yml file [OK found and valid]
  dbt_project.yml file [OK found and valid]

Required dependencies:
  - git [OK found]

Connection:
  method: service_account
  project: my-gcp-project
  dataset: analytics
  timeout: 300
  client_id: None
  priority: interactive
  Connection test: [OK connection ok]

All checks passed!
```

---

## Appendix B: Design Decisions

| Decision | Choice | Rationale | Alternatives Considered |
|----------|--------|-----------|-------------------------|
| Check granularity | Individual flags | Users need targeted diagnostics | Single comprehensive check |
| Output format | Rich tables + JSON | Human readable + automation | Plain text only |
| Exit codes | 0/1/2 | Standard Unix convention | More granular codes |
| Error codes | DLI-95x range | Follows existing pattern (avoids DLI-9xx Lineage) | Reuse DLI-4xx/5xx |
| Timeout | 30s default | Balance speed vs reliability | Fixed timeout |
| Mock mode | Always succeeds | Simplifies testing | Mock failures |

---

## Appendix C: Implementation Agent Review Log

| Reviewer | Date | Status | Notes |
|----------|------|--------|-------|
| feature-interface-cli | 2026-01-01 | Approved with changes | See Implementation Review below |
| expert-python | 2026-01-01 | Approved with suggestions | See Python Review below |

---

## Implementation Review (feature-interface-cli)

### Status: Approved with Minor Changes

**Strengths:**
- Well-structured CLI design following existing `dli` patterns (`typer.Typer`, `--format`, callback pattern)
- API class design aligns with `DatasetAPI`, `MetricAPI` facade patterns
- Proper use of `ExecutionContext` and `ExecutionMode.MOCK` for testing
- Comprehensive check categories covering system, config, server, auth, database, network
- Exit code semantics (0/1/2) follow Unix conventions

**Required Changes (Applied in this review):**

1. ~~**Error Code Conflict (CRITICAL):**~~ -> Fixed to DLI-95x range (DLI-950 to DLI-956)

2. ~~**Test Directory Path:**~~ -> Fixed to `tests/cli/test_debug_cmd.py`

**Pending Changes (for implementation):**

3. **Export Registration:** Add `debug_app` to `commands/__init__.py` and `main.py`

4. **CLI Option Alignment:** Consider using `ListOutputFormat` enum from `base.py` instead of raw `--json` flag for consistency with other commands

**Compatibility Notes:**
- `BasecampClient.health_check()` exists - reuse for server checks
- `get_project_path()` from `base.py` - already handles path resolution
- Rich console utilities in `utils.py` - use `print_error`, `print_success`

---

## Python Review (expert-python)

### Status: Approved with Suggestions

**Strengths:**
- Abstract base class pattern (`BaseCheck`) is clean and extensible
- Pydantic models with proper `Field` descriptions and type hints
- Factory methods (`_pass`, `_fail`) reduce boilerplate in check implementations
- `by_category` property uses proper groupby semantics

**Suggestions:**

1. **Use `functools.cached_property`** for `by_category` to avoid recomputation:
   ```python
   from functools import cached_property

   @cached_property
   def by_category(self) -> dict[CheckCategory, list[CheckResult]]:
       from itertools import groupby
       sorted_checks = sorted(self.checks, key=lambda c: c.category.value)
       return {k: list(v) for k, v in groupby(sorted_checks, key=lambda c: c.category)}
   ```

2. **Timing decorator** for check duration measurement:
   ```python
   def timed_check(func):
       @functools.wraps(func)
       def wrapper(self, context):
           start = time.perf_counter()
           result = func(self, context)
           result.duration_ms = int((time.perf_counter() - start) * 1000)
           return result
       return wrapper
   ```

3. **Use `Protocol`** instead of ABC for `BaseCheck` (structural typing):
   ```python
   from typing import Protocol

   class Check(Protocol):
       name: str
       category: CheckCategory
       def execute(self, context: ExecutionContext) -> CheckResult: ...
   ```

4. **Concurrent check execution** for network checks (async-ready design):
   ```python
   async def check_network_async(self, endpoints: list[str]) -> DebugResult:
       async with asyncio.TaskGroup() as tg:
           tasks = [tg.create_task(self._check_endpoint(ep)) for ep in endpoints]
       return self._merge_results([t.result() for t in tasks])
   ```

5. **Test fixture organization:** Use `conftest.py` for shared fixtures:
   ```python
   # tests/conftest.py
   @pytest.fixture
   def mock_debug_context() -> ExecutionContext:
       return ExecutionContext(execution_mode=ExecutionMode.MOCK)
   ```

**Minor Issues (Fixed in this review):**
- ~~Line 401: `datetime.utcnow` is deprecated in Python 3.12+~~ -> Fixed to `datetime.now(UTC)`
- ~~Import `Path` missing in CLI command example~~ -> Fixed
- Consider `httpx` over `requests` for async-compatible HTTP checks (future enhancement)
