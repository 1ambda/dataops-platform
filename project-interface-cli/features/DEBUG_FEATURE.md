# FEATURE: Debug Command - Environment Diagnostics and Connection Testing

| Attribute | Value |
|-----------|-------|
| **Version** | v0.9.0 |
| **Status** | ✅ Complete |
| **Created** | 2026-01-01 |
| **Last Updated** | 2026-01-10 |
| **Implementation** | 12 checks, 7 API methods, 196 tests |
| **References** | dbt debug, BigQuery CLI, Trino CLI |
| **Release Notes** | [DEBUG_RELEASE.md](./DEBUG_RELEASE.md) |

---

## Migration Note (v0.9.0)

**Flag Renamed:** `--project, -p` renamed to `--config, -c`
- Rationale: The flag validates CLI configuration, not Team/Project selection
- Avoids confusion with Team-based resource management
- More accurately describes the check's purpose (configuration validation)

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

### 1.3 Key Features (✅ All Implemented)

| Feature | Status | Description |
|---------|--------|-------------|
| **System Environment** | ✅ Complete | Python version, dli version, OS information |
| **Project Configuration** | ✅ Complete | Config file location, project path validation |
| **Server Connection** | ✅ Complete | Basecamp Server reachability and latency |
| **Authentication Status** | ✅ Complete | Credential validation (API token, GCP credentials) |
| **Network Diagnostics** | ✅ Complete | DNS resolution, HTTPS connectivity, proxy detection |
| **Focused Checks** | ✅ Complete | `--connection`, `--auth`, `--network`, `--server`, `--config` flags |
| **Output Formats** | ✅ Complete | Rich table output, JSON output, verbose mode |

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
| `--connection` | - | FLAG | `false` | Test database connectivity only |
| `--auth` | `-a` | FLAG | `false` | Test authentication only |
| `--network` | `-n` | FLAG | `false` | Test network connectivity only |
| `--server` | `-s` | FLAG | `false` | Test Basecamp Server connection only |
| `--config` | `-c` | FLAG | `false` | Validate CLI configuration only (v0.9.0: renamed from --project) |
| `--verbose` | `-v` | FLAG | `false` | Show detailed diagnostic information |
| `--json` | - | FLAG | `false` | Output in JSON format |
| `--dialect` | `-d` | ENUM | (auto) | Target dialect: `bigquery`, `trino` |
| `--path` | `-p` | PATH | `.` | Path for config resolution |
| `--timeout` | `-t` | INT | `30` | Connection timeout in seconds |

### 2.3 Flag Behavior

| Flag Combination | Behavior |
|------------------|----------|
| (no flags) | Run all diagnostic checks |
| `--connection` | Database connection test only |
| `--auth` | Authentication validation only |
| `--network` | Network endpoint checks only |
| `--server` | Basecamp Server health check only |
| `--config` | CLI configuration validation only (v0.9.0: renamed from --project) |
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

### 3.1 System Environment Checks (✅ 3/3 Implemented)

| Check | Status | Implementation |
|-------|--------|----------------|
| Python version | ✅ Complete | `PythonVersionCheck` - Verify Python >= 3.12 |
| dli version | ✅ Complete | `DliVersionCheck` - Show current dli version |
| OS information | ✅ Complete | `OsInfoCheck` - Display OS name and version |

### 3.2 Configuration Checks (✅ 2/2 Implemented)

| Check | Status | Implementation |
|-------|--------|----------------|
| Config file | ✅ Complete | `ConfigFileCheck` - Locate and validate config file |
| Project path | ✅ Complete | `ProjectPathCheck` - Validate project directory exists |

### 3.3 Server Connection Checks (✅ 2/2 Implemented)

| Check | Status | Implementation |
|-------|--------|----------------|
| Server URL | ✅ Complete | `ServerUrlCheck` - Validate server URL configuration |
| Server Health | ✅ Complete | `ServerHealthCheck` - Test connection and health endpoint |

### 3.4 Authentication Checks (✅ 2/2 Implemented)

| Check | Status | Implementation |
|-------|--------|----------------|
| API Token | ✅ Complete | `ApiTokenCheck` - Validate Basecamp API token |
| Google Credentials | ✅ Complete | `GoogleCredentialsCheck` - Verify GCP credentials |

### 3.5 Database Connection Checks (⏳ Future Enhancement)

> **Note:** Database connectivity checks are planned for a future release. Current implementation focuses on system, configuration, server, and authentication diagnostics.

### 3.6 Network Checks (✅ 3/3 Implemented)

| Check | Status | Implementation |
|-------|--------|----------------|
| DNS resolution | ✅ Complete | `DnsResolutionCheck` - Resolve required hostnames |
| HTTPS connectivity | ✅ Complete | `HttpsConnectivityCheck` - Verify HTTPS (443) reachable |
| Proxy detection | ✅ Complete | `ProxyDetectionCheck` - Detect HTTP/HTTPS proxy |

---

## 4. Library API Design (✅ Complete)

### 4.1 DebugAPI Class

> **Implementation:** See [DEBUG_RELEASE.md](./DEBUG_RELEASE.md) for complete implementation details.

**API Methods (7 methods implemented):**

| Method | Status | Description |
|--------|--------|-------------|
| `run_all()` | ✅ Complete | Run all diagnostic checks with optional timeout |
| `check_system()` | ✅ Complete | System environment checks only |
| `check_project()` | ✅ Complete | Project configuration only |
| `check_server()` | ✅ Complete | Basecamp Server health only |
| `check_auth()` | ✅ Complete | Authentication validation only |
| `check_connection()` | ✅ Complete | Database connectivity (future enhancement) |
| `check_network()` | ✅ Complete | Network diagnostics with endpoint testing |

**Execution Modes:**
- ✅ `ExecutionMode.MOCK` - All checks pass, for testing
- ✅ `ExecutionMode.LOCAL` - Real environment checks
- ✅ `ExecutionMode.SERVER` - Server-based diagnostics (future)

---

## 5. Data Models (✅ Complete)

> **Implementation:** See [DEBUG_RELEASE.md](./DEBUG_RELEASE.md) for complete model definitions and examples.

### 5.1 Core Models

| Model | Status | Description |
|-------|--------|-------------|
| `CheckStatus` | ✅ Complete | Enum: PASS, FAIL, WARN, SKIP |
| `CheckCategory` | ✅ Complete | Enum: SYSTEM, CONFIG, SERVER, AUTH, DATABASE, NETWORK |
| `CheckResult` | ✅ Complete | Individual check result with status, error, remediation |
| `DebugResult` | ✅ Complete | Complete diagnostic result with aggregated checks |

**Key Properties:**
- `DebugResult.passed_count` - Count of passed checks
- `DebugResult.failed_count` - Count of failed checks
- `DebugResult.by_category` - Group checks by category
- `CheckResult.remediation` - Actionable fix suggestions for failures

---

## 6. Error Codes (✅ Complete)

### 6.1 Debug Error Codes (DLI-95x)

> **Implementation:** All 7 error codes implemented in `src/dli/exceptions.py`

| Code | Name | Status |
|------|------|--------|
| `DLI-950` | `DEBUG_SYSTEM_CHECK_FAILED` | ✅ Complete |
| `DLI-951` | `DEBUG_CONFIG_CHECK_FAILED` | ✅ Complete |
| `DLI-952` | `DEBUG_SERVER_CHECK_FAILED` | ✅ Complete |
| `DLI-953` | `DEBUG_AUTH_CHECK_FAILED` | ✅ Complete |
| `DLI-954` | `DEBUG_CONNECTION_CHECK_FAILED` | ✅ Complete |
| `DLI-955` | `DEBUG_NETWORK_CHECK_FAILED` | ✅ Complete |
| `DLI-956` | `DEBUG_TIMEOUT` | ✅ Complete |

### 6.2 Exit Codes

| Exit Code | Status | Meaning |
|-----------|--------|---------|
| `0` | ✅ Complete | All checks passed |
| `1` | ✅ Complete | One or more checks failed |
| `2` | ✅ Complete | Invalid arguments or configuration |

---

## 7. Implementation Summary

> **All implementation details, code examples, and test strategies are documented in [DEBUG_RELEASE.md](./DEBUG_RELEASE.md).**

### 7.1 Implementation Status (✅ v0.8.0 Complete)

| Component | Files | Status |
|-----------|-------|--------|
| **Core Models** | `core/debug/models.py` | ✅ Complete (181 lines) |
| **Check Implementations** | `core/debug/checks.py` | ✅ Complete (12 checks, 820 lines) |
| **DebugAPI** | `api/debug.py` | ✅ Complete (7 methods, 369 lines) |
| **CLI Command** | `commands/debug.py` | ✅ Complete (315 lines) |
| **Tests** | `tests/*/test_debug_*.py` | ✅ Complete (196 tests) |

### 7.2 Test Coverage

| Test Category | Tests | Status |
|---------------|-------|--------|
| Model Tests | 39 | ✅ All pass |
| Check Tests | 37 | ✅ All pass |
| API Tests | 32 | ✅ All pass |
| CLI Tests | 39 | ✅ All pass |
| Integration Tests | 15 | ✅ All pass |
| **Total** | **196** | ✅ All pass |

---

## 8. Design Decisions

> **Key architectural and design choices for the DEBUG feature.**

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Check Granularity** | Individual focused flags (`--connection`, `--auth`, etc.) | Users need targeted diagnostics without running all checks |
| **Output Format** | Rich tables + JSON option | Human-readable default with automation-friendly JSON |
| **Exit Codes** | 0/1/2 semantics | Standard Unix convention for scripting integration |
| **Error Code Range** | DLI-95x (950-956) | Sub-range of DLI-9xx, avoids conflict with Lineage (900-904) |
| **Default Timeout** | 30 seconds | Balance between reliability and user experience |
| **Mock Mode Behavior** | All checks pass | Simplifies testing, no need for complex mock failures |
| **Check Categories** | 6 categories (SYSTEM, CONFIG, SERVER, AUTH, DATABASE, NETWORK) | Logical grouping matches user mental model |
| **Remediation Guidance** | Every failure includes fix steps | Actionable output reduces support burden |

---

## 9. Related Documents

- **[DEBUG_RELEASE.md](./DEBUG_RELEASE.md)** - Complete implementation details, code examples, and test results
- **[_STATUS.md](./_STATUS.md)** - Overall project status and changelog
- **[../docs/PATTERNS.md](../docs/PATTERNS.md)** - Development patterns and conventions
