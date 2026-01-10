# Resource Sharing - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-10
> **Status:** Phase 1 + Phase 2 Complete (10/10 endpoints)

---

## Executive Summary

This release introduces the Resource Sharing feature (Phase 1 + Phase 2), providing team-to-team resource sharing and user-level grant capabilities for the Basecamp data platform. This feature enables Producer Teams to share resources with Consumer Teams and provides fine-grained permission control within Consumer Teams.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 10 (5 Share CRUD + 5 Grant CRUD) |
| **Total Tests** | 44 (30 Service + 14 Controller) |
| **Test Success Rate** | 100% |
| **Architecture** | Pure Hexagonal (Port-Adapter) |

---

## Scope

### Implemented Features

| Feature | Description | Status |
|---------|-------------|--------|
| **ShareableResourceType** | 6 resource types (WORKSHEET, WORKSHEET_FOLDER, DATASET, METRIC, WORKFLOW, QUALITY) | Complete |
| **ResourcePermission** | VIEWER, EDITOR permission levels | Complete |
| **Team Resource Sharing** | TeamResourceShareEntity for Producer-to-Consumer team visibility | Complete |
| **User Resource Grants** | UserResourceGrantEntity for individual permission within Consumer Team | Complete |
| **Visibility Control** | visibleToTeam flag for SHARED list visibility | Complete |
| **Permission Hierarchy** | Grant cannot exceed Share permission | Complete |
| **Cascade Delete** | Share deletion cascades to grants (API-level) | Complete |
| **Security Service** | ResourceSecurityService with canView, canEdit, canShare | Complete |

### Deferred to Phase 3+

| Feature | Target Phase |
|---------|--------------|
| Resource Sync (Airflow) | Phase 3 |
| Share Request Workflow | Phase 4 |
| Expiring Shares | Phase 4 |
| Share Delegation | Phase 4 |

---

## API Endpoints

### Team Resource Share API (5 endpoints)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/resources/{resourceType}/shares` | List all shares for resource type | Member |
| POST | `/api/v1/resources/{resourceType}/shares` | Create new share | Manager+ |
| GET | `/api/v1/resources/{resourceType}/shares/{shareId}` | Get share details | Member |
| PUT | `/api/v1/resources/{resourceType}/shares/{shareId}` | Update share permission/visibility | Manager+ |
| DELETE | `/api/v1/resources/{resourceType}/shares/{shareId}` | Revoke share (cascades to grants) | Manager+ |

### User Resource Grant API (5 endpoints)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/resources/{resourceType}/shares/{shareId}/grants` | List all grants for a share | Manager |
| POST | `/api/v1/resources/{resourceType}/shares/{shareId}/grants` | Create user grant | Manager+ |
| GET | `/api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}` | Get grant details | Manager |
| PUT | `/api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}` | Update grant permission | Manager+ |
| DELETE | `/api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}` | Revoke user grant | Manager+ |

---

## Entity Model

### TeamResourceShareEntity

```kotlin
@Entity
@Table(name = "team_resource_share")
class TeamResourceShareEntity(
    ownerTeamId: Long,           // FK to Team (Producer)
    sharedWithTeamId: Long,      // FK to Team (Consumer)
    resourceType: ShareableResourceType,
    resourceId: Long,            // FK to resource
    permission: ResourcePermission,  // VIEWER, EDITOR
    visibleToTeam: Boolean,      // Show in SHARED list
    grantedBy: Long,             // FK to User
    grantedAt: LocalDateTime,
) : BaseEntity()
```

### UserResourceGrantEntity

```kotlin
@Entity
@Table(name = "user_resource_grant")
class UserResourceGrantEntity(
    shareId: Long,               // FK to TeamResourceShare
    userId: Long,                // FK to User
    permission: ResourcePermission,  // VIEWER, EDITOR (cannot exceed share)
    grantedBy: Long,             // FK to User
    grantedAt: LocalDateTime,
) : BaseEntity()
```

### Enums

```kotlin
// ShareableResourceType - Resources that can be shared between teams
enum class ShareableResourceType {
    WORKSHEET,          // SqlWorksheetEntity
    WORKSHEET_FOLDER,   // WorksheetFolderEntity
    DATASET,            // DatasetEntity
    METRIC,             // MetricEntity
    WORKFLOW,           // WorkflowEntity
    QUALITY,            // QualitySpecEntity
}

// ResourcePermission - Permission levels for shares and grants
enum class ResourcePermission {
    VIEWER,   // Read-only, can execute
    EDITOR,   // Read + write, no delete
}
```

### Entity Relationships

```
TeamResourceShareEntity (root)
  └── UserResourceGrantEntity (shareId FK)

Resource Types (via resourceId FK):
  └── SqlWorksheetEntity (WORKSHEET)
  └── WorksheetFolderEntity (WORKSHEET_FOLDER)
  └── DatasetEntity (DATASET)
  └── MetricEntity (METRIC)
  └── WorkflowEntity (WORKFLOW)
  └── QualitySpecEntity (QUALITY)
```

---

## Implementation Details

### Architecture Compliance

| Component | Pattern | Location |
|-----------|---------|----------|
| **Services** | Concrete classes | `module-core-domain/service/` |
| **Repository Interfaces** | Jpa/Dsl separation | `module-core-domain/repository/resource/` |
| **Repository Implementations** | QueryDSL + Spring Data | `module-core-infra/repository/resource/` |
| **Controllers** | ResourceShareController, UserGrantController | `module-server-api/controller/` |
| **DTOs** | Command + Response pattern | `module-server-api/dto/resource/` |
| **Enums** | Common module | `module-core-common/enums/ResourceEnums.kt` |
| **Security** | ResourceSecurityService | `module-server-api/security/` |

### Key Design Decisions

1. **Separate Controllers**: ResourceShareController for shares, UserGrantController for grants
2. **Long FK Pattern**: No JPA `@ManyToOne` relationships, only Long FK fields
3. **Soft Delete**: `deletedAt` field in `BaseEntity` for all entities
4. **Cascade Delete (API-level)**: Share deletion cascades to grants via service layer
5. **CQRS**: Jpa repositories for CRUD, Dsl repositories for complex queries
6. **Permission Hierarchy**: Grant permission cannot exceed share permission
7. **Visibility Control**: visibleToTeam flag for Consumer Team list visibility

### Files Created/Modified

#### New Files (module-core-common)

- `enums/ResourceEnums.kt` - ShareableResourceType, ResourcePermission enums

#### New Files (module-core-domain)

- `entity/resource/TeamResourceShareEntity.kt` - Share entity
- `entity/resource/UserResourceGrantEntity.kt` - Grant entity
- `service/ResourceShareService.kt` - Share business logic
- `service/UserGrantService.kt` - Grant business logic
- `repository/resource/TeamResourceShareRepositoryJpa.kt` - Share CRUD interface
- `repository/resource/TeamResourceShareRepositoryDsl.kt` - Share query interface
- `repository/resource/UserResourceGrantRepositoryJpa.kt` - Grant CRUD interface
- `repository/resource/UserResourceGrantRepositoryDsl.kt` - Grant query interface
- `command/resource/ResourceShareCommands.kt` - Command objects
- `command/resource/UserGrantCommands.kt` - Command objects
- `projection/resource/ResourceProjections.kt` - Query projections

#### New Files (module-core-infra)

- `repository/resource/TeamResourceShareRepositoryJpaImpl.kt` - Share CRUD implementation
- `repository/resource/TeamResourceShareRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/resource/TeamResourceShareRepositoryDslImpl.kt` - Share QueryDSL
- `repository/resource/UserResourceGrantRepositoryJpaImpl.kt` - Grant CRUD implementation
- `repository/resource/UserResourceGrantRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/resource/UserResourceGrantRepositoryDslImpl.kt` - Grant QueryDSL

#### New Files (module-server-api)

- `controller/ResourceShareController.kt` - REST controller (5 endpoints)
- `controller/UserGrantController.kt` - REST controller (5 endpoints)
- `dto/resource/ResourceDtos.kt` - Share and Grant DTOs (requests/responses)
- `security/ResourceSecurityService.kt` - Permission check service

---

## Test Coverage

### Service Tests (30 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| ResourceShareServiceTest | 13 | Full CRUD + validation + cascade |
| UserGrantServiceTest | 17 | Full CRUD + permission hierarchy |

**Test Categories:**
- Share CRUD: createShare, updateShare, revokeShare (success + exceptions)
- Grant CRUD: createGrant, updateGrant, revokeGrant (success + exceptions)
- Validation: duplicate share, share not found, permission exceeds share
- Cascade delete: share deletion cascades to grants
- Permission hierarchy: grant cannot exceed share permission

### Controller Tests (14 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| ResourceShareControllerTest | 6 | All 5 endpoints + validation |
| UserGrantControllerTest | 8 | All 5 endpoints + permission checks |

**Test Categories:**
- List shares, Create share, Get share details, Update share, Delete share
- List grants, Create grant, Get grant details, Update grant, Delete grant
- Command value verification in all write operations
- Permission enforcement tests

---

## Security

### ResourceSecurityService

```kotlin
@Component("resourceSecurity")
class ResourceSecurityService {
    fun canView(resourceType: ShareableResourceType, resourceId: Long): Boolean
    fun canEdit(resourceType: ShareableResourceType, resourceId: Long): Boolean
    fun canShare(resourceType: ShareableResourceType, resourceId: Long): Boolean
}
```

### Permission Matrix

| Action | ADMIN | Owner Manager | Owner Editor | Grant EDITOR | Grant VIEWER | No Grant |
|--------|-------|---------------|--------------|--------------|--------------|----------|
| View resource | Y | Y | Y | Y | Y | Y (if visibleToTeam) |
| Execute resource | Y | Y | Y | Y | Y | N |
| Update resource | Y | Y | Y | Y | N | N |
| Delete resource | Y | Y | N | N | N | N |
| Create share | Y | Y | N | N | N | N |
| Manage grants | Y | Y | N | N | N | N |

---

## Exception Handling

| Exception | HTTP Status | Error Code | Trigger |
|-----------|-------------|------------|---------|
| ShareNotFoundException | 404 | RESOURCE-001 | Share ID not found |
| ShareAlreadyExistsException | 409 | RESOURCE-002 | Duplicate share |
| GrantNotFoundException | 404 | RESOURCE-003 | Grant ID not found |
| GrantAlreadyExistsException | 409 | RESOURCE-004 | User already granted |
| PermissionExceedsShareException | 400 | RESOURCE-005 | Grant permission > share permission |
| ResourceNotFoundException | 404 | RESOURCE-006 | Target resource not found |

---

## Verification

```bash
# Run Resource Sharing tests
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.ResourceShareServiceTest"
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.UserGrantServiceTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.ResourceShareControllerTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.UserGrantControllerTest"

# Expected results
# ResourceShareServiceTest: 13 tests passed
# UserGrantServiceTest: 17 tests passed
# ResourceShareControllerTest: 6 tests passed
# UserGrantControllerTest: 8 tests passed
# Total: 44 tests passed
```

---

## Related Documentation

- **Feature Specification:** [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md) - Full feature specification
- **Team Management:** [`TEAM_FEATURE.md`](./TEAM_FEATURE.md) - Team roles and membership
- **Entity Relationships:** [`../docs/ENTITY_RELATION.md`](../docs/ENTITY_RELATION.md) - Resource sharing domain relationships

---

## Roadmap

### Phase 3: Airflow Integration (Planned)

- Resource sync endpoint `/api/v1/resources/sync`
- Sync from Git repos (Metric, Dataset, Workflow, Quality)
- Delta sync support (create, update, delete detection)
- Sync audit logging

### Phase 4: Enhanced Features (Future)

- Share request workflow (Request -> Approve flow)
- Expiring shares (auto-revoke after date)
- Share delegation (allow granted users to re-share)

---

*Document Version: 1.0.0 | Last Updated: 2026-01-10*
