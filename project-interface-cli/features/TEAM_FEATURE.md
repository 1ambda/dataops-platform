# FEATURE: Team Command - Team Management

| Attribute | Value |
|-----------|-------|
| **Version** | 1.0.0 |
| **Status** | Planned |
| **Created** | 2026-01-10 |
| **Last Updated** | 2026-01-10 |
| **Server Spec** | [`project-basecamp-server/features/TEAM_FEATURE.md`](../../project-basecamp-server/features/TEAM_FEATURE.md) |
| **References** | Slack Teams, GitHub Organizations, Databricks Workspaces |

---

## 1. Overview

### 1.1 Purpose

`dli team` provides management of team context and membership for the CLI. Users can view their teams, switch team context, and list team members. Team management (create/delete/member management) is primarily done via UI; CLI focuses on context switching and read operations.

**Key Use Cases:**
- View teams the user belongs to
- Switch active team context for other commands
- View team members and their roles
- Check current team context

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Read-Heavy Operations** | CLI focuses on list/get operations; mutations via UI |
| **Context Management** | Team context affects `dli sql`, `dli metric`, `dli dataset` commands |
| **Server-Side Authority** | All team data comes from Basecamp Server |
| **Minimal Scope** | Only essential commands for CLI workflow |

### 1.3 Key Features

| Feature | Status | Description |
|---------|--------|-------------|
| **List Teams** | Planned | List teams user belongs to |
| **Get Team** | Planned | View team details and member count |
| **List Members** | Planned | View team members with roles |
| **Set Context** | Planned | Set default team for subsequent commands |
| **Show Context** | Planned | Display current team context |

### 1.4 Industry Benchmarking

| Tool | Key Features | Applied to `dli team` |
|------|--------------|----------------------|
| **Slack Teams** | Workspace switching | Context management |
| **GitHub CLI** | Org context, member listing | Team listing, member view |
| **Databricks CLI** | Workspace config | Context switching |
| **kubectl** | Context/namespace switching | Team context pattern |

### 1.5 System Integration Points

| Integration Area | Existing Pattern | Application |
|------------------|------------------|-------------|
| **CLI Commands** | `commands/config.py` structure | Similar context management |
| **Library API** | `ConfigAPI` facade pattern | `TeamAPI` follows same pattern |
| **Client** | `BasecampClient.*` methods | Add `team_*` methods |
| **Exceptions** | DLI error codes | Use DLI-85x range for Team |
| **Config** | `~/.dli/config.yaml` | Store default team context |

---

## 2. Data Model

### 2.1 Core Entities

| Entity | Description |
|--------|-------------|
| **Team** | Organizational unit that owns resources |
| **TeamMember** | User membership in a team with role |
| **TeamContext** | Current team selection stored in config |

### 2.2 Team Model

| Field | Type | Description |
|-------|------|-------------|
| `id` | int | Unique team identifier |
| `name` | string | Team slug (lowercase, hyphenated) |
| `display_name` | string | Human-readable team name |
| `description` | string | null | Team description |
| `member_count` | int | Number of team members |
| `my_role` | TeamRole | Current user's role in team |
| `created_at` | datetime | Creation timestamp |

### 2.3 TeamMember Model

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | int | User identifier |
| `email` | string | User email |
| `name` | string | User display name |
| `role` | TeamRole | Member role (MANAGER/EDITOR/VIEWER) |
| `joined_at` | datetime | When user joined team |

### 2.4 TeamRole Enum

| Role | Description | Permissions |
|------|-------------|-------------|
| `MANAGER` | Team administrator | Full access + member management |
| `EDITOR` | Content creator | Create/edit resources |
| `VIEWER` | Read-only member | View resources only |

---

## 3. CLI Design

### 3.1 Command Structure

```
dli team <subcommand> [arguments] [options]
```

| Subcommand | Status | Arguments | Description |
|------------|--------|-----------|-------------|
| `list` | Planned | - | List teams user belongs to |
| `get` | Planned | `<TEAM>` | Get team details |
| `members` | Planned | `<TEAM>` | List team members |
| `use` | Planned | `<TEAM>` | Set default team context |
| `current` | Planned | - | Show current team context |

### 3.2 Subcommand: `list` - List Teams

```bash
dli team list [options]
```

Lists all teams the current user belongs to.

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# List all teams
$ dli team list

# JSON output for scripting
$ dli team list --format json
```

**Output Example (Table):**

```
$ dli team list

Name              Display Name           Role      Members
----------------  ---------------------  --------  --------
marketing         Marketing Analytics    MANAGER   8
data-engineering  Data Engineering       EDITOR    15
finance           Finance Reporting      VIEWER    5

You belong to 3 teams
```

### 3.3 Subcommand: `get` - Get Team Details

```bash
dli team get <TEAM> [options]
```

Gets detailed information about a specific team.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `TEAM` | TEXT | Yes | Team name or ID |

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# Get team by name
$ dli team get marketing

# Get team by ID
$ dli team get 1

# JSON output
$ dli team get marketing --format json
```

**Output Example:**

```
$ dli team get marketing

Team: marketing
Display Name: Marketing Analytics
Description: Marketing team for campaign analytics and reporting
Your Role: MANAGER
Members: 8
Created: 2026-01-01

Resources:
  SQL Queries: 45
  Datasets: 12
  Metrics: 28
  Quality Specs: 8
```

### 3.4 Subcommand: `members` - List Team Members

```bash
dli team members <TEAM> [options]
```

Lists all members of a team with their roles.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `TEAM` | TEXT | Yes | Team name or ID |

**Options:**

| Option | Short | Type | Default | Description |
|--------|-------|------|---------|-------------|
| `--role` | `-r` | ENUM | - | Filter by role: `MANAGER`, `EDITOR`, `VIEWER` |
| `--format` | `-f` | ENUM | `table` | Output format: `table`, `json` |

**Examples:**

```bash
# List all members
$ dli team members marketing

# Filter by role
$ dli team members marketing --role MANAGER

# JSON output
$ dli team members marketing --format json
```

**Output Example:**

```
$ dli team members marketing

Email                    Name              Role      Joined
-----------------------  ----------------  --------  ----------
admin@example.com        Alice Admin       MANAGER   2026-01-01
editor@example.com       Bob Editor        EDITOR    2026-01-05
viewer@example.com       Carol Viewer      VIEWER    2026-01-10

Showing 3 of 3 members
```

### 3.5 Subcommand: `use` - Set Team Context

```bash
dli team use <TEAM>
```

Sets the default team context for subsequent commands.

**Arguments:**

| Argument | Type | Required | Description |
|----------|------|----------|-------------|
| `TEAM` | TEXT | Yes | Team name or ID |

**Examples:**

```bash
# Set default team
$ dli team use marketing
Default team set to: marketing

# Now other commands use this team by default
$ dli sql list  # Equivalent to: dli sql list --team marketing
```

**Behavior:**
- Stores team name in `~/.dli/config.yaml` under `default_team`
- Validates team exists and user has access
- Subsequent commands use this team unless overridden with `--team`

### 3.6 Subcommand: `current` - Show Current Context

```bash
dli team current
```

Shows the currently configured default team.

**Examples:**

```bash
# Show current team
$ dli team current
Current team: marketing (Marketing Analytics)
Your role: MANAGER

# When no team is set
$ dli team current
No default team set. Use 'dli team use <TEAM>' to set one.
```

---

## 4. API Design (TeamAPI)

### 4.1 TeamAPI Class

| Method | Status | Returns | Description |
|--------|--------|---------|-------------|
| `list_teams()` | Planned | `TeamListResult` | List user's teams |
| `get()` | Planned | `TeamDetail` | Get team details |
| `list_members()` | Planned | `TeamMemberListResult` | List team members |
| `set_context()` | Planned | `None` | Set default team in config |
| `get_context()` | Planned | `TeamContext | None` | Get current team context |

**Usage Example:**

```python
from dli import TeamAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(
    execution_mode=ExecutionMode.SERVER,
    server_url="http://basecamp:8080",
)
api = TeamAPI(context=ctx)

# List teams
result = api.list_teams()
for team in result.teams:
    print(f"{team.name}: {team.display_name} ({team.my_role})")

# Get team details
team = api.get(team="marketing")
print(f"Members: {team.member_count}")

# List members
members = api.list_members(team="marketing", role="EDITOR")
for member in members.members:
    print(f"{member.email}: {member.role}")

# Context management
api.set_context(team="marketing")
current = api.get_context()
print(f"Current team: {current.team_name}")
```

### 4.2 Result Models

| Model | Status | Purpose |
|-------|--------|---------|
| `TeamInfo` | Planned | Summary team info for list views |
| `TeamDetail` | Planned | Full team details with resource counts |
| `TeamMemberInfo` | Planned | Member info with role |
| `TeamListResult` | Planned | List operation result |
| `TeamMemberListResult` | Planned | Member list result |
| `TeamContext` | Planned | Current team context |

---

## 5. Data Models

### 5.1 Core Models

```python
from enum import Enum
from datetime import datetime
from pydantic import BaseModel, Field

class TeamRole(str, Enum):
    """Team member role."""
    MANAGER = "MANAGER"
    EDITOR = "EDITOR"
    VIEWER = "VIEWER"

class TeamInfo(BaseModel):
    """Summary team info for list views."""
    id: int = Field(..., description="Team ID")
    name: str = Field(..., description="Team slug")
    display_name: str = Field(..., description="Human-readable name")
    my_role: TeamRole = Field(..., description="Current user's role")
    member_count: int = Field(..., description="Number of members")

class TeamDetail(BaseModel):
    """Full team details."""
    id: int
    name: str
    display_name: str
    description: str | None = None
    my_role: TeamRole
    member_count: int
    created_at: datetime
    resource_counts: dict[str, int] = Field(
        default_factory=dict,
        description="Count of resources by type"
    )

class TeamMemberInfo(BaseModel):
    """Team member information."""
    user_id: int
    email: str
    name: str
    role: TeamRole
    joined_at: datetime

class TeamListResult(BaseModel):
    """Team list operation result."""
    teams: list[TeamInfo]
    total: int

class TeamMemberListResult(BaseModel):
    """Team member list result."""
    members: list[TeamMemberInfo]
    total: int

class TeamContext(BaseModel):
    """Current team context."""
    team_id: int
    team_name: str
    team_display_name: str
    my_role: TeamRole
```

---

## 6. Error Codes (DLI-85x)

Team errors use the DLI-85x range.

| Code | Name | Description |
|------|------|-------------|
| DLI-850 | TEAM_NOT_FOUND | Team not found |
| DLI-851 | TEAM_ACCESS_DENIED | No access to team |
| DLI-852 | TEAM_MEMBER_NOT_FOUND | Team member not found |
| DLI-853 | TEAM_CONTEXT_NOT_SET | No default team configured |
| DLI-854 | TEAM_INVALID_ROLE | Invalid role specified |

**Exception Classes:**

```python
class TeamNotFoundError(DLIError):
    """Team not found."""
    def __init__(self, team: str):
        super().__init__(
            message=f"Team not found: {team}",
            code=ErrorCode.TEAM_NOT_FOUND,
        )
        self.team = team

class TeamAccessDeniedError(DLIError):
    """Access denied to team."""
    def __init__(self, team: str):
        super().__init__(
            message=f"Access denied to team: {team}",
            code=ErrorCode.TEAM_ACCESS_DENIED,
        )
        self.team = team

class TeamContextNotSetError(DLIError):
    """No default team configured."""
    def __init__(self):
        super().__init__(
            message="No default team set. Use 'dli team use <TEAM>' to set one.",
            code=ErrorCode.TEAM_CONTEXT_NOT_SET,
        )
```

---

## 7. Server API Endpoints

| Operation | Method | Endpoint |
|-----------|--------|----------|
| List My Teams | GET | `/api/v1/teams/me` |
| Get Team | GET | `/api/v1/teams/{teamId}` |
| List Members | GET | `/api/v1/teams/{teamId}/members` |

### 7.1 List My Teams

**Request:**
```http
GET /api/v1/teams/me
```

**Response:**
```json
{
  "teams": [
    {
      "id": 1,
      "name": "marketing",
      "displayName": "Marketing Analytics",
      "myRole": "MANAGER",
      "memberCount": 8
    },
    {
      "id": 2,
      "name": "data-engineering",
      "displayName": "Data Engineering",
      "myRole": "EDITOR",
      "memberCount": 15
    }
  ],
  "total": 2
}
```

### 7.2 Get Team

**Request:**
```http
GET /api/v1/teams/1
```

**Response:**
```json
{
  "id": 1,
  "name": "marketing",
  "displayName": "Marketing Analytics",
  "description": "Marketing team for campaign analytics",
  "myRole": "MANAGER",
  "memberCount": 8,
  "createdAt": "2026-01-01T09:00:00Z",
  "resourceCounts": {
    "sqlSnippets": 45,
    "datasets": 12,
    "metrics": 28,
    "qualitySpecs": 8
  }
}
```

### 7.3 List Members

**Request:**
```http
GET /api/v1/teams/1/members?role=EDITOR
```

**Response:**
```json
{
  "members": [
    {
      "userId": 10,
      "email": "editor@example.com",
      "name": "Bob Editor",
      "role": "EDITOR",
      "joinedAt": "2026-01-05T10:00:00Z"
    }
  ],
  "total": 1
}
```

---

## 8. Config Integration

### 8.1 Config File Structure

Team context is stored in `~/.dli/config.yaml`:

```yaml
# ~/.dli/config.yaml
server:
  url: http://basecamp:8080

# Team context
team:
  default: marketing  # Set by 'dli team use'
```

### 8.2 Context Resolution

Commands that accept `--team` option follow this resolution order:

1. **CLI argument**: `--team marketing` (highest priority)
2. **Environment variable**: `DLI_TEAM=marketing`
3. **Config file**: `team.default` in config.yaml
4. **Prompt**: If required, prompt user to select team

```python
def resolve_team(cli_team: str | None) -> str:
    """Resolve team from CLI arg, env, or config."""
    if cli_team:
        return cli_team
    if env_team := os.getenv("DLI_TEAM"):
        return env_team
    if config_team := config.get("team.default"):
        return config_team
    raise TeamContextNotSetError()
```

---

## 9. Success Criteria

### 9.1 Feature Completion

| Feature | Status | Completion Condition |
|---------|--------|----------------------|
| TeamAPI | Planned | All methods work in mock mode |
| CLI list | Planned | `dli team list` returns formatted output |
| CLI get | Planned | `dli team get <team>` shows details |
| CLI members | Planned | `dli team members <team>` lists members |
| CLI use | Planned | `dli team use <team>` sets context |
| CLI current | Planned | `dli team current` shows context |
| Context integration | Planned | `dli sql list` uses default team |

### 9.2 Test Quality

| Metric | Target | Status |
|--------|--------|--------|
| Unit test coverage | >= 80% | Planned |
| Mock mode tests | All methods | Planned |
| CLI command tests | Each subcommand | Planned |

---

## 10. Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # Add TeamAPI export
├── api/
│   ├── __init__.py       # Add TeamAPI export
│   └── team.py           # TeamAPI class
├── models/
│   ├── __init__.py       # Add team model exports
│   └── team.py           # TeamInfo, TeamDetail, etc.
├── commands/
│   ├── __init__.py       # Add team_app export
│   └── team.py           # CLI commands
├── core/
│   └── client.py         # Add team_* methods
└── exceptions.py         # Add DLI-85x codes

tests/
├── api/
│   └── test_team_api.py  # API tests
├── cli/
│   └── test_team_cmd.py  # CLI tests
└── core/
    └── test_team.py      # Team model tests
```

---

## 11. Reference Patterns

| Implementation | Reference File | Pattern |
|----------------|----------------|---------|
| `TeamAPI` | `api/config.py` | Facade pattern, context management |
| Result models | `models/common.py` | Pydantic `BaseModel` with `Field` |
| CLI command | `commands/config.py` | Typer command structure |
| Client methods | `core/client.py` | `ServerResponse`, error handling |
| Config storage | `api/config.py` | ConfigAPI pattern |

---

## Appendix A: Command Summary

### List Teams

```bash
dli team list [options]

Options:
  -f, --format ENUM     Output: table, json (default: table)
```

### Get Team

```bash
dli team get <TEAM> [options]

Arguments:
  TEAM                  Team name or ID (required)

Options:
  -f, --format ENUM     Output: table, json (default: table)
```

### List Members

```bash
dli team members <TEAM> [options]

Arguments:
  TEAM                  Team name or ID (required)

Options:
  -r, --role ENUM       Filter by role: MANAGER, EDITOR, VIEWER
  -f, --format ENUM     Output: table, json (default: table)
```

### Set Context

```bash
dli team use <TEAM>

Arguments:
  TEAM                  Team name or ID (required)
```

### Show Context

```bash
dli team current
```

---

## Appendix B: Design Decisions

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Command scope | Read-only + context | Team mutations via UI for safety |
| 2 | Context storage | Config file | Persistent across sessions |
| 3 | Context resolution | CLI > ENV > Config | Standard precedence |
| 4 | Team identifier | Name or ID | Flexible user input |
| 5 | Error code range | DLI-85x | Dedicated range for team errors |
| 6 | Member management | Not included | Security-sensitive, UI-only |
| 7 | Resource counts | Include in get | Useful context without extra calls |
| 8 | Default format | Table | Human-readable default |

---

## Appendix C: Workflow Examples

### Initial Setup Workflow

```bash
# 1. List available teams
$ dli team list
Name              Display Name           Role      Members
marketing         Marketing Analytics    MANAGER   8
data-engineering  Data Engineering       EDITOR    15

# 2. Set default team
$ dli team use marketing
Default team set to: marketing

# 3. Verify context
$ dli team current
Current team: marketing (Marketing Analytics)
Your role: MANAGER

# 4. Now use other commands without --team
$ dli sql list
# Shows queries from marketing team
```

### Multi-Team Workflow

```bash
# Use different team for specific command
$ dli sql list --team data-engineering

# Check current default
$ dli team current
Current team: marketing

# Switch default
$ dli team use data-engineering
Default team set to: data-engineering
```

### Scripting Workflow

```bash
# Use environment variable for CI/CD
$ export DLI_TEAM=marketing
$ dli sql list  # Uses marketing team

# Override in specific command
$ DLI_TEAM=finance dli sql list  # Uses finance team
```

---

## Appendix D: Integration with Other Commands

Team context affects these commands:

| Command | Team Usage |
|---------|------------|
| `dli sql list` | Filter by `--team` or default |
| `dli sql get` | Verify team access |
| `dli metric list` | Filter by `--team` or default |
| `dli dataset list` | Filter by `--team` or default |
| `dli quality list` | Filter by `--team` or default |
| `dli workflow list` | Filter by `--team` or default |

**Example Integration:**

```bash
# Without team context set
$ dli sql list
Error [DLI-853]: No default team set. Use 'dli team use <TEAM>' to set one.

# Set context
$ dli team use marketing
Default team set to: marketing

# Now works
$ dli sql list
ID   Name                    Folder              Dialect   Updated
43   insight_analysis        Campaign Analytics  bigquery  2026-01-05
```

---

**Last Updated:** 2026-01-10 (v1.0.0 - Initial specification)
