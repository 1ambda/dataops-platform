# Team Management - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-10
> **Status:** Phase 1 Complete (10/10 endpoints)

---

## Executive Summary

This release introduces the Team Management feature Phase 1, providing team organization, member management, and resource ownership capabilities for the Basecamp data platform. Teams serve as the primary ownership unit for all data resources including Metrics, Datasets, Workflows, Quality Specs, and SQL Worksheets.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 10 (5 Team CRUD + 4 Member + 1 Resources) |
| **Total Tests** | 32 (22 Service + 10 Controller) |
| **Test Success Rate** | 100% |
| **Architecture** | Pure Hexagonal (Port-Adapter) |

---

## Phase 1 Scope

### Implemented Features

| Feature | Description | Status |
|---------|-------------|--------|
| **Team CRUD** | Create, read, update, delete teams | Complete |
| **Team Members** | Add, update role, remove members | Complete |
| **Team Roles** | Manager/Editor/Viewer role-based access | Complete |
| **Resource Listing** | List resources owned by team | Complete |
| **Deletion Protection** | Block deletion if team has resources | Complete |

### Deferred to Phase 2+

| Feature | Target Phase |
|---------|--------------|
| Resource Sharing (TeamResourceShareEntity) | Phase 2 |
| External Resource Association | Phase 3 |
| Alert Service (Slack integration) | Phase 2 |
| Context API (team switching) | Phase 2 |

---

## API Endpoints

### Team CRUD (5 endpoints)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/team-management` | List all teams | Authenticated |
| POST | `/api/v1/team-management` | Create team | Admin |
| GET | `/api/v1/team-management/{teamId}` | Get team details | Member |
| PUT | `/api/v1/team-management/{teamId}` | Update team | Manager+ |
| DELETE | `/api/v1/team-management/{teamId}` | Delete team (blocked if has resources) | Admin |

### Team Member Management (4 endpoints)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/team-management/{teamId}/members` | List team members | Member |
| POST | `/api/v1/team-management/{teamId}/members` | Add member to team | Admin |
| PUT | `/api/v1/team-management/{teamId}/members/{userId}` | Update member role | Admin |
| DELETE | `/api/v1/team-management/{teamId}/members/{userId}` | Remove member | Admin |

### Team Resources (1 endpoint)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/team-management/{teamId}/resources` | List team resources by type | Member |

---

## Entity Model

### TeamEntity

```kotlin
@Entity
@Table(name = "team")
class TeamEntity(
    name: String,           // Unique team identifier (lowercase, hyphenated)
    displayName: String,    // Human-readable name
    description: String?,   // Optional description
) : BaseEntity()
```

### TeamMemberEntity

```kotlin
@Entity
@Table(name = "team_member")
class TeamMemberEntity(
    teamId: Long,           // FK to Team
    userId: Long,           // FK to User
    role: TeamRole,         // MANAGER, EDITOR, VIEWER
) : BaseEntity()
```

### Enums

```kotlin
// TeamRole - Member access level
enum class TeamRole {
    MANAGER,  // Full access, manage members, approve shares
    EDITOR,   // Create, update, delete team resources
    VIEWER    // Read-only access, execute queries
}

// TeamResourceType - Resource types that can be owned by teams
enum class TeamResourceType {
    METRIC,
    DATASET,
    WORKFLOW,
    QUALITY,
    GITHUB_REPO,
    SQL_FOLDER,
    SQL_WORKSHEET,
    QUERY_HISTORY
}
```

### Entity Relationships

```
TeamEntity (root)
  └── TeamMemberEntity (teamId FK, userId FK)

Resource Ownership (via teamId FK):
  └── MetricEntity
  └── DatasetEntity
  └── WorkflowEntity
  └── QualitySpecEntity
  └── WorksheetFolderEntity
  └── GitHubRepoEntity
```

---

## Implementation Details

### Architecture Compliance

| Component | Pattern | Location |
|-----------|---------|----------|
| **Services** | Concrete classes | `module-core-domain/service/` |
| **Repository Interfaces** | Jpa/Dsl separation | `module-core-domain/repository/team/` |
| **Repository Implementations** | QueryDSL + Spring Data | `module-core-infra/repository/team/` |
| **Controller** | TeamManagementController | `module-server-api/controller/` |
| **DTOs** | Command + Response pattern | `module-server-api/dto/team/` |
| **Exceptions** | Typed domain exceptions | `module-core-common/exception/` |

### Key Design Decisions

1. **Unified Controller**: All 10 team management endpoints in `TeamManagementController`
2. **Long FK Pattern**: No JPA `@ManyToOne` relationships, only Long FK fields
3. **Soft Delete**: `deletedAt` field in `BaseEntity` for all entities
4. **Deletion Protection**: Teams with resources cannot be deleted (TeamHasResourcesException)
5. **CQRS**: Jpa repositories for CRUD, Dsl repositories for complex queries
6. **defaultTeamId**: UserEntity includes `defaultTeamId` for user's primary team

### Files Created/Modified

#### New Files (module-core-domain)

- `entity/team/TeamEntity.kt` - Team entity
- `entity/team/TeamMemberEntity.kt` - Team member entity
- `service/TeamService.kt` - Team business logic
- `repository/team/TeamRepositoryJpa.kt` - Team CRUD interface
- `repository/team/TeamRepositoryDsl.kt` - Team query interface
- `repository/team/TeamMemberRepositoryJpa.kt` - Member CRUD interface
- `repository/team/TeamMemberRepositoryDsl.kt` - Member query interface
- `command/team/TeamCommands.kt` - Command objects
- `projection/team/TeamProjections.kt` - Query projections
- `exception/TeamExceptions.kt` - Domain exceptions

#### New Files (module-core-infra)

- `repository/team/TeamRepositoryJpaImpl.kt` - Team CRUD implementation
- `repository/team/TeamRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/team/TeamRepositoryDslImpl.kt` - Team QueryDSL
- `repository/team/TeamMemberRepositoryJpaImpl.kt` - Member CRUD implementation
- `repository/team/TeamMemberRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/team/TeamMemberRepositoryDslImpl.kt` - Member QueryDSL

#### New Files (module-server-api)

- `controller/TeamManagementController.kt` - REST controller (10 endpoints)
- `dto/team/TeamDtos.kt` - Team DTOs (requests/responses)

#### New Files (module-core-common)

- `enums/TeamEnums.kt` - TeamRole, TeamResourceType enums
- Exception classes in `exception/CommonExceptions.kt`

#### Modified Files

- `entity/user/UserEntity.kt` - Added `defaultTeamId` field

---

## Test Coverage

### Service Tests (22 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| TeamServiceTest | 22 | Full CRUD + member management + validation |

**Test Categories:**
- Team CRUD: createTeam, updateTeam (success + not found), deleteTeam (success + with resources)
- Member management: addMember, removeMember (success + exceptions), updateMemberRole (success + exceptions)
- Validation: duplicate team name, team not found, member not found
- Membership checks: isMember, getRole

### Controller Tests (10 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| TeamManagementControllerTest | 10 | All 10 endpoints |

**Test Categories:**
- List teams, Create team, Get team details, Update team, Delete team
- List members, Add member, Update member role, Remove member
- List team resources
- Command value verification in all write operations

---

## Cross-Review Findings

### Applied Fixes (HIGH Priority)

1. **deleteTeam Tests**: Added tests verifying TeamHasResourcesException is thrown when team has resources
2. **updateTeam Tests**: Added success and not found exception path tests
3. **updateMemberRole Exception Tests**: Added MemberNotFoundException path test
4. **removeMember Exception Tests**: Added TeamNotFoundException path test
5. **Controller Command Verification**: Enhanced tests to verify command values passed to service

### Code Quality

- All tests follow AAA (Arrange-Act-Assert) pattern
- MockK used for service mocking in controller tests
- JUnit 5 assertions with clear error messages
- Comprehensive exception path coverage

---

## Exception Handling

| Exception | HTTP Status | Error Code | Trigger |
|-----------|-------------|------------|---------|
| TeamNotFoundException | 404 | TEAM-001 | Team ID not found |
| TeamAlreadyExistsException | 409 | TEAM-002 | Duplicate team name |
| TeamHasResourcesException | 409 | TEAM-003 | Delete team with resources |
| MemberNotFoundException | 404 | TEAM-004 | Member not in team |
| MemberAlreadyExistsException | 409 | TEAM-005 | User already member |

---

## Verification

```bash
# Run Team Management tests
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.TeamServiceTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.TeamManagementControllerTest"

# Expected results
# TeamServiceTest: 22 tests passed
# TeamManagementControllerTest: 10 tests passed
# Total: 32 tests passed
```

---

## Related Documentation

- **Feature Specification:** [`TEAM_FEATURE.md`](./TEAM_FEATURE.md) - Full feature specification
- **Resource Sharing:** [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md) - Team-to-team resource sharing (Phase 2)
- **Entity Relationships:** [`../docs/ENTITY_RELATION.md`](../docs/ENTITY_RELATION.md) - Team domain relationships
- **Audit Logging:** [`AUDIT_FEATURE.md`](./AUDIT_FEATURE.md) - Team action audit trails

---

## Roadmap

### Phase 2: Resource Sharing (Planned)

- TeamResourceShareEntity for team-to-team visibility
- UserResourceGrantEntity for user-specific permissions
- AlertService interface with MockSlackAlertAdapter
- Share request/grant/revoke endpoints

### Phase 3: External Resources (Planned)

- TeamExternalResourceEntity for Ranger/BigQuery/Superset associations
- External resource listing and team assignment

### Phase 4: Enhanced Features (Future)

- Real Slack integration (SlackAlertAdapter)
- Context API for team switching
- Audit logging integration

---

*Document Version: 1.0.0 | Last Updated: 2026-01-10*
