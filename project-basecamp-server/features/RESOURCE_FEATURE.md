# Resource Management Feature Specification

> **Version:** 1.0.0 | **Status:** Phase 1+2 Complete | **Priority:** P1 High
> **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** 2026-01-10 | **Endpoints:** 10/20+ Complete
>
> **Merges:** [`ACL_FEATURE.md`](./archived/ACL_FEATURE.md) (authentication, authorization, security expressions)
>
> **Dependencies:**
> - [`TEAM_FEATURE.md`](./TEAM_FEATURE.md) - Team Management (TeamRole, TeamMemberEntity)
> - [`SQL_FEATURE.md`](./SQL_FEATURE.md) - SQL Worksheet (SqlWorksheetEntity, WorksheetFolderEntity)
>
> **Implementation Details:** [`RESOURCE_RELEASE.md`](./RESOURCE_RELEASE.md)

---

## 1. Overview

### 1.1 Purpose

The Resource Management feature provides a unified model for resource classification, sharing, and access control across the Basecamp data platform. This specification:

1. **Resource Classification** - Categorizes resources as SHARED, DEDICATED, or SYSTEM
2. **Team-to-Team Sharing** - Enables Producer Teams to share resources with Consumer Teams
3. **User-Level Grants** - Provides fine-grained permission control within Consumer Teams
4. **Access Control** - Unified authentication and authorization framework (merged from ACL_FEATURE)

**Target Users:**
- **Data Professionals (DS/DA/DAE/DE):** Access owned and shared resources
- **Team Managers:** Share resources with other teams, manage user grants
- **Platform Administrators (Governance Team):** System-wide access, audit all resources

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Resource Classification** | SHARED (공유 가능), DEDICATED (팀 전용), SYSTEM (거버넌스 관리) |
| **Team-to-Team Sharing** | TeamResourceShareEntity for Producer-to-Consumer team visibility |
| **User-Level Grants** | UserResourceGrantEntity for individual permission within Consumer Team |
| **Visibility Control** | `visibleToTeam` - Grant 없이도 SHARED 목록 확인 가능 |
| **Permission Model** | VIEWER (read-only), EDITOR (read + write, no delete) |
| **Resource Sync (Airflow)** | `/api/v1/resources/sync` endpoint for automated resource collection |
| **Authentication** | OIDC/Keycloak for production, in-memory users for local development |
| **API Token Auth** | Token-based auth for automation (Airflow, CI/CD, CLI) |
| **Security Expressions** | `@resourceSecurity.canView()`, `@resourceSecurity.canEdit()` |

### 1.3 Architecture Overview

```
+-----------------------------------------------------------------------------+
|                         Resource Management Architecture                       |
+-----------------------------------------------------------------------------+
|                                                                             |
|  ResourceController           TeamController              AuthController     |
|  /api/v1/resources            /api/v1/teams               /api/v1/auth       |
|                                                                             |
+-----------------------------------------------------------------------------+
|                                                                             |
|  ResourceService              ResourceShareService        ApiTokenService    |
|                               UserGrantService                               |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +------------------------+   +------------------------+                    |
|  | TeamResourceShare      |   | UserResourceGrant      |                    |
|  | Entity                 |   | Entity                 |                    |
|  +------------------------+   +------------------------+                    |
|                                                                             |
|  Shareable Resources:                                                       |
|  - SqlWorksheetEntity (WORKSHEET)                                           |
|  - WorksheetFolderEntity (WORKSHEET_FOLDER)                                 |
|  - DatasetEntity (DATASET)                                                  |
|  - MetricEntity (METRIC)                                                    |
|  - WorkflowEntity (WORKFLOW)                                                |
|  - QualitySpecEntity (QUALITY)                                              |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 1.4 Resource Classification Model

```
+-----------------------------------------------------------------------------+
|                           Resource Classification                             |
+-----------------------------------------------------------------------------+
|                                                                             |
|  SHARED (공유 가능) - Can be shared between teams                            |
|  ================================================================           |
|  - SqlWorksheetEntity (SQL Worksheets)                                      |
|  - WorksheetFolderEntity (Worksheet Folders)                                |
|  - DatasetEntity (Datasets)                                                 |
|  - MetricEntity (Metrics)                                                   |
|  - WorkflowEntity (Workflows)                                               |
|  - QualitySpecEntity (Quality Specs)                                        |
|                                                                             |
|  DEDICATED (팀 전용) - Team-only, not shareable                              |
|  ================================================================           |
|  - QueryHistoryEntity (Query execution history)                             |
|  - AuditAccessEntity (Access audit logs)                                    |
|  - AuditResourceEntity (Resource change audit)                              |
|                                                                             |
|  SYSTEM (거버넌스 관리) - Managed by platform governance                     |
|  ================================================================           |
|  - CatalogTableEntity (Data catalog tables)                                 |
|  - CatalogColumnEntity (Data catalog columns)                               |
|  - UserEntity (Platform users)                                              |
|  - TeamEntity (Platform teams)                                              |
|  - TranspileRuleEntity (SQL transpilation rules)                            |
|                                                                             |
+-----------------------------------------------------------------------------+
```

---

## 2. Data Model

### 2.1 Entity Relationship Diagram

```
+---------------------+                       +---------------------+
| TeamResourceShare   |                       | UserResourceGrant   |
| Entity              |                       | Entity              |
+---------------------+                       +---------------------+
| id (PK)             |                       | id (PK)             |
| ownerTeamId         |  1:N                  | shareId             |<--+
| sharedWithTeamId    |<--------------------->| userId              |   |
| resourceType        |                       | permission          |   |
| resourceId          |                       | grantedBy           |   |
| permission          |                       | grantedAt           |   |
| visibleToTeam       |                       | [BaseEntity]        |   |
| grantedBy           |                       +---------------------+   |
| grantedAt           |                                                  |
| [BaseEntity]        |--------------------------------------------------+
+---------------------+

Share Flow:
1. Producer Team creates TeamResourceShareEntity (visible to Consumer Team)
2. Consumer Team members see SHARED resource in list (visibleToTeam=true)
3. User requests access or Manager grants UserResourceGrantEntity
4. Only granted users can USE the shared resource

Permission Levels:
- Share Level: Controls visibility to Consumer Team
- Grant Level: Controls individual user access within Consumer Team
```

### 2.2 Entity Definitions

#### ShareableResourceType Enum

```kotlin
// module-core-common/src/main/kotlin/com/dataops/basecamp/common/enums/ResourceEnums.kt
package com.dataops.basecamp.common.enums

/**
 * Types of resources that can be shared between teams.
 * Note: Catalog columns are handled via CATALOG_TABLE, not separately.
 */
enum class ShareableResourceType {
    WORKSHEET,          // SqlWorksheetEntity
    WORKSHEET_FOLDER,   // WorksheetFolderEntity
    DATASET,            // DatasetEntity
    METRIC,             // MetricEntity
    WORKFLOW,           // WorkflowEntity
    QUALITY,            // QualitySpecEntity
}

/**
 * Permission level for shared resources and user grants.
 */
enum class ResourcePermission {
    VIEWER,   // Read-only access, can execute but not modify
    EDITOR,   // Read + write access, cannot delete
}
```

#### TeamResourceShareEntity

```kotlin
@Entity
@Table(
    name = "team_resource_share",
    indexes = [
        Index(name = "idx_resource_share_owner_team", columnList = "owner_team_id"),
        Index(name = "idx_resource_share_shared_team", columnList = "shared_with_team_id"),
        Index(name = "idx_resource_share_resource", columnList = "resource_type, resource_id"),
        Index(name = "idx_resource_share_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_resource_share",
            columnNames = ["owner_team_id", "shared_with_team_id", "resource_type", "resource_id"]
        )
    ]
)
class TeamResourceShareEntity(
    /**
     * Team that owns the resource (Producer Team).
     * Note: No MySQL FK - use ID-based join via QueryDSL.
     */
    @Column(name = "owner_team_id", nullable = false)
    val ownerTeamId: Long,

    /**
     * Team receiving access to the resource (Consumer Team).
     * Note: No MySQL FK - use ID-based join via QueryDSL.
     */
    @Column(name = "shared_with_team_id", nullable = false)
    val sharedWithTeamId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    val resourceType: ShareableResourceType,

    /**
     * ID of the shared resource.
     * Note: No MySQL FK - validated at API level, cascade delete via API.
     */
    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,

    /**
     * Default permission level for the share.
     * Individual user grants can have same or lower permission.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    var permission: ResourcePermission = ResourcePermission.VIEWER,

    /**
     * Whether Consumer Team members can see this resource in their SHARED list
     * even without a UserResourceGrant. true = visible, false = hidden until granted.
     */
    @Column(name = "visible_to_team", nullable = false)
    var visibleToTeam: Boolean = true,

    /**
     * User ID of who created this share.
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "granted_by", nullable = false)
    val grantedBy: Long,

    @Column(name = "granted_at", nullable = false)
    var grantedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()
```

#### UserResourceGrantEntity

```kotlin
@Entity
@Table(
    name = "user_resource_grant",
    indexes = [
        Index(name = "idx_user_grant_share_id", columnList = "share_id"),
        Index(name = "idx_user_grant_user_id", columnList = "user_id"),
        Index(name = "idx_user_grant_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_grant",
            columnNames = ["share_id", "user_id"]
        )
    ]
)
class UserResourceGrantEntity(
    /**
     * Reference to the TeamResourceShareEntity.
     * Note: No MySQL FK - cascade delete handled at API level.
     * When TeamResourceShareEntity is deleted, all UserResourceGrantEntity with this shareId
     * must be deleted by the service layer.
     */
    @Column(name = "share_id", nullable = false)
    val shareId: Long,

    /**
     * User ID receiving the grant within Consumer Team.
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /**
     * Permission for this specific user.
     * Cannot exceed the share-level permission.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    var permission: ResourcePermission = ResourcePermission.VIEWER,

    /**
     * User ID of who created this grant (typically Team Manager).
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "granted_by", nullable = false)
    val grantedBy: Long,

    @Column(name = "granted_at", nullable = false)
    var grantedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()
```

### 2.3 Visibility and Permission Rules

#### Resource List Display

| User Type | List View | Can Use/Execute |
|-----------|-----------|-----------------|
| Producer Team Member (OWNED) | Yes - shows as "OWNED" | Yes (per TeamRole) |
| Consumer Team Member (with Grant) | Yes - shows as "SHARED" | Yes (per Grant permission) |
| Consumer Team Member (without Grant, visibleToTeam=true) | Yes - shows as "SHARED" | No |
| Consumer Team Member (without Grant, visibleToTeam=false) | No | No |
| System Admin (Governance) | Yes - shows as "ALL" | Yes (bypass) |

#### Permission Hierarchy

```
Share Permission >= Grant Permission

Example:
- Share permission: EDITOR
- Grant permission: VIEWER -> User can only VIEW (not EDIT)
- Grant permission: EDITOR -> User can EDIT

If Share permission: VIEWER
- Grant permission: VIEWER -> User can only VIEW
- Grant permission: EDITOR -> Invalid (exceeds share permission)
```

---

## 3. API Specifications

### 3.1 Resource Controller (ResourceController)

Base path: `/api/v1/resources`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/resources` | List resources with classification filter | Authenticated |
| GET | `/resources/{resourceType}` | List resources by type | Authenticated |
| GET | `/resources/{resourceType}/{resourceId}` | Get resource details | Member/Granted |
| POST | `/resources/sync` | Sync resources from Airflow | System/Airflow Token |

### 3.2 Share Controller (ResourceShareController)

Base path: `/api/v1/resources/{resourceType}/shares`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/shares` | List all shares for resource type | Member |
| POST | `/shares` | Create new share | Manager/Admin |
| GET | `/shares/{shareId}` | Get share details | Member |
| PUT | `/shares/{shareId}` | Update share permission/visibility | Manager/Admin |
| DELETE | `/shares/{shareId}` | Revoke share (cascades to grants) | Manager/Admin |

### 3.3 Grant Controller (UserGrantController)

Base path: `/api/v1/resources/{resourceType}/shares/{shareId}/grants`

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/grants` | List all grants for a share | Manager |
| POST | `/grants` | Create user grant | Manager/Admin |
| GET | `/grants/{grantId}` | Get grant details | Manager |
| PUT | `/grants/{grantId}` | Update grant permission | Manager/Admin |
| DELETE | `/grants/{grantId}` | Revoke user grant | Manager/Admin |

### 3.4 Team Resource Integration

For backward compatibility with existing TeamController:

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/teams/{teamId}/resources` | List team resources (OWNED + SHARED) | Member |
| GET | `/api/v1/teams/{teamId}/resources/owned` | List only owned resources | Member |
| GET | `/api/v1/teams/{teamId}/resources/shared` | List only shared resources | Member |

---

## 4. API Details

### 4.1 List Resources with Classification

**`GET /api/v1/resources?classification=SHARED&type=WORKSHEET`**

```http
GET /api/v1/resources?classification=SHARED&type=WORKSHEET&page=0&size=20
Authorization: Bearer <token>
```

**Response:**
```json
{
  "content": [
    {
      "id": 101,
      "type": "WORKSHEET",
      "name": "Daily Active Users Query",
      "description": "DAU calculation for marketing",
      "classification": "SHARED",
      "ownerTeamId": 1,
      "ownerTeamName": "Data Engineering",
      "ownership": "SHARED",
      "permission": "VIEWER",
      "hasGrant": true,
      "updatedAt": "2026-01-06T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### 4.2 Create Resource Share

**`POST /api/v1/resources/WORKSHEET/shares`**

```http
POST /api/v1/resources/WORKSHEET/shares
Content-Type: application/json
Authorization: Bearer <token>

{
  "resourceId": 101,
  "sharedWithTeamId": 2,
  "permission": "VIEWER",
  "visibleToTeam": true
}
```

**Response:** `201 Created`
```json
{
  "id": 456,
  "ownerTeamId": 1,
  "ownerTeamName": "Data Engineering",
  "sharedWithTeamId": 2,
  "sharedWithTeamName": "ML Infrastructure",
  "resourceType": "WORKSHEET",
  "resourceId": 101,
  "resourceName": "Daily Active Users Query",
  "permission": "VIEWER",
  "visibleToTeam": true,
  "grantedBy": "admin@example.com",
  "grantedAt": "2026-01-06T10:00:00Z"
}
```

### 4.3 Create User Grant

**`POST /api/v1/resources/WORKSHEET/shares/456/grants`**

```http
POST /api/v1/resources/WORKSHEET/shares/456/grants
Content-Type: application/json
Authorization: Bearer <token>

{
  "userId": 789,
  "permission": "VIEWER"
}
```

**Response:** `201 Created`
```json
{
  "id": 1001,
  "shareId": 456,
  "userId": 789,
  "userEmail": "analyst@example.com",
  "permission": "VIEWER",
  "grantedBy": "manager@example.com",
  "grantedAt": "2026-01-06T11:00:00Z"
}
```

### 4.4 Resource Sync (Airflow Integration)

**`POST /api/v1/resources/sync`**

Used by Airflow DAGs to sync collected resources (Metric, Dataset, Workflow, Quality) from Git repositories.

```http
POST /api/v1/resources/sync
Content-Type: application/json
Authorization: Bearer <airflow-api-token>

{
  "resourceType": "METRIC",
  "teamId": 1,
  "resources": [
    {
      "name": "daily_active_users",
      "description": "DAU metric",
      "sql": "SELECT COUNT(DISTINCT user_id) FROM events WHERE date = '{{ date }}'",
      "sourceFile": "metrics/dau.sql",
      "gitCommit": "abc123"
    }
  ]
}
```

**Response:** `200 OK`
```json
{
  "resourceType": "METRIC",
  "teamId": 1,
  "synced": 1,
  "created": 0,
  "updated": 1,
  "deleted": 0,
  "errors": []
}
```

---

## 5. Access Control (Merged from ACL_FEATURE)

### 5.1 Authentication Architecture

#### User Authentication (OIDC/Keycloak)

**Production Environment:**
- Primary authentication via OIDC (OpenID Connect) with Keycloak
- JWT token validation
- User info endpoint for profile data

```
+-----------------------------------------------------------------------------------+
|                         OIDC Authentication Flow                                   |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. User Login                                                                    |
|     Client -----> Keycloak -----> Authorization Code                              |
|                                                                                   |
|  2. Token Exchange                                                                |
|     Client -----> Keycloak -----> Access Token (JWT) + Refresh Token              |
|                                                                                   |
|  3. API Request                                                                   |
|     Client -----> Basecamp API (Authorization: Bearer <jwt>)                      |
|                                                                                   |
|  4. Token Validation                                                              |
|     Basecamp API -----> Keycloak (JWKS endpoint) -----> Validate Signature        |
|                                                                                   |
|  5. Extract Principal                                                             |
|     JWT Claims -----> UserPrincipal (userId, email, roles)                        |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

#### API Token Authentication

**Use Cases:**
- Airflow DAGs executing datasets/workflows
- CI/CD pipelines deploying specs
- CLI (`dli`) automation scripts

```
+-----------------------------------------------------------------------------------+
|                         API Token Authentication Flow                              |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. Token Creation (via UI/API)                                                   |
|     User -----> POST /api/v1/auth/tokens                                          |
|                 { "name": "airflow-dag", "expiresAt": "2027-01-01" }               |
|                                                                                   |
|  2. Response                                                                      |
|     <---- { "token": "dli_xxxx...", "id": 123 }                                   |
|           (token shown only once)                                                 |
|                                                                                   |
|  3. API Request                                                                   |
|     Automation -----> Basecamp API                                                |
|                       (Authorization: Bearer dli_xxxx...)                         |
|                       OR (X-API-Token: dli_xxxx...)                               |
|                                                                                   |
|  4. Token Validation                                                              |
|     - Lookup hashed token in database                                             |
|     - Check expiry, revocation status                                             |
|     - Load associated user as principal                                           |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

#### Local Development Authentication

| Profile | Authentication Method | Use Case |
|---------|----------------------|----------|
| `dev` | In-memory UserDetailsService | Local `bootRun` development |
| `test` | Mock User Filter | Unit/Integration tests |
| `prod` | OIDC/Keycloak | Production deployment |

**Development Users (In-Memory):**

| Username | Password | System Role | Description |
|----------|----------|-------------|-------------|
| `admin@test.com` | `admin` | ADMIN | Full system access |
| `user@test.com` | `user` | CONSUMER | Standard user |
| `viewer@test.com` | `viewer` | CONSUMER | Viewer-only user |

### 5.2 Role Hierarchy

```
+-----------------------------------------------------------------------------------+
|                              Role Hierarchy                                        |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  System Roles (UserRole)           Team Roles (TeamRole)                          |
|  ========================          =========================                      |
|                                                                                   |
|  ADMIN ----+                       TeamRole:                                      |
|            |                         MANAGER - Full team access, manage members   |
|            +--> Bypass all           EDITOR  - Create/edit team resources         |
|            |    resource checks      VIEWER  - Read-only, can execute             |
|            |                                                                       |
|  CONSUMER -+--> Subject to         Resource Permission:                           |
|                 resource checks      EDITOR  - Read + write, no delete            |
|                                      VIEWER  - Read-only, can execute             |
|  PUBLIC ---+--> Public endpoints                                                  |
|               only (health, info)                                                 |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 5.3 Security Expressions

#### ResourceSecurityService

```kotlin
// module-server-api/security/ResourceSecurityService.kt
package com.dataops.basecamp.security

@Component("resourceSecurity")
class ResourceSecurityService(
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl,
    private val teamResourceShareRepositoryDsl: TeamResourceShareRepositoryDsl,
    private val userResourceGrantRepositoryDsl: UserResourceGrantRepositoryDsl,
) {
    /**
     * Check if current user can view a resource.
     * True if: Admin, Owner Team member, or has Grant for shared resource.
     */
    fun canView(resourceType: ShareableResourceType, resourceId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        // Check if user is member of owner team
        val resource = getResourceOwnerId(resourceType, resourceId) ?: return false
        if (isTeamMember(resource.teamId, principal.userId)) return true

        // Check if user has grant for shared resource
        return hasValidGrant(resourceType, resourceId, principal.userId)
    }

    /**
     * Check if current user can edit a resource.
     * True if: Admin, Owner Team editor+, or has EDITOR grant.
     */
    fun canEdit(resourceType: ShareableResourceType, resourceId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        // Check if user is editor+ in owner team
        val resource = getResourceOwnerId(resourceType, resourceId) ?: return false
        if (isTeamEditor(resource.teamId, principal.userId)) return true

        // Check if user has EDITOR grant
        return hasEditorGrant(resourceType, resourceId, principal.userId)
    }

    /**
     * Check if current user can manage shares for a resource.
     * True if: Admin or Owner Team manager.
     */
    fun canShare(resourceType: ShareableResourceType, resourceId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val resource = getResourceOwnerId(resourceType, resourceId) ?: return false
        return isTeamManager(resource.teamId, principal.userId)
    }

    private fun getCurrentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal

    // ... helper methods
}
```

#### TeamSecurityService (From ACL_FEATURE)

```kotlin
// module-server-api/security/TeamSecurityService.kt
@Component("teamSecurity")
class TeamSecurityService(
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl,
) {
    /**
     * Check if current user is a team member (any role).
     */
    fun isMember(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true
        return teamMemberRepositoryDsl.existsByTeamIdAndUserId(teamId, principal.userId)
    }

    /**
     * Check if current user can edit team resources (EDITOR or MANAGER).
     */
    fun canEdit(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = teamMemberRepositoryDsl.findRoleByTeamIdAndUserId(teamId, principal.userId)
        return role in listOf(TeamRole.MANAGER, TeamRole.EDITOR)
    }

    /**
     * Check if current user is team manager.
     */
    fun isManager(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = teamMemberRepositoryDsl.findRoleByTeamIdAndUserId(teamId, principal.userId)
        return role == TeamRole.MANAGER
    }
}
```

### 5.4 Permission Matrix

#### Team Permissions

| Action | ADMIN | MANAGER | EDITOR | VIEWER | Non-Member |
|--------|-------|---------|--------|--------|------------|
| View team info | Y | Y | Y | Y | N |
| View team members | Y | Y | Y | Y | N |
| View team resources | Y | Y | Y | Y | N |
| Execute resources | Y | Y | Y | Y | N |
| Create resources | Y | Y | Y | N | N |
| Update resources | Y | Y | Y | N | N |
| Delete resources | Y | Y | N | N | N |
| Share resources | Y | Y | N | N | N |
| Update team settings | Y | Y | N | N | N |
| Manage members | Y | N | N | N | N |
| Delete team | Y | N | N | N | N |

#### Shared Resource Permissions

| Action | EDITOR Grant | VIEWER Grant | No Grant (visible) |
|--------|--------------|--------------|-------------------|
| View resource | Y | Y | Y (metadata only) |
| Execute resource | Y | Y | N |
| Update resource | Y | N | N |
| Delete resource | N | N | N |

---

## 6. DTO Definitions

### 6.1 Request DTOs

> **Naming Convention:** Request DTOs use `*Request` suffix (no `Dto` suffix) for clarity.
> Response DTOs use `*Dto` suffix to distinguish from entity classes.

```kotlin
// module-server-api/src/main/kotlin/com/dataops/basecamp/dto/resource/ResourceDtos.kt

// Create Share
data class CreateResourceShareRequest(
    val resourceId: Long,
    val sharedWithTeamId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
    val visibleToTeam: Boolean = true,
)

// Update Share
data class UpdateResourceShareRequest(
    val permission: ResourcePermission? = null,
    val visibleToTeam: Boolean? = null,
)

// Create Grant
data class CreateUserGrantRequest(
    val userId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
)

// Update Grant
data class UpdateUserGrantRequest(
    val permission: ResourcePermission,
)

// Resource Sync (Airflow)
data class ResourceSyncRequest(
    val resourceType: ShareableResourceType,
    val teamId: Long,
    val resources: List<ResourceSyncItemRequest>,
)

data class ResourceSyncItemRequest(
    val name: String,
    val description: String?,
    val sql: String?,
    val sourceFile: String?,
    val gitCommit: String?,
    val metadata: Map<String, Any> = emptyMap(),
)
```

### 6.2 Response DTOs

```kotlin
// Resource Summary
data class ResourceSummaryDto(
    val id: Long,
    val type: ShareableResourceType,
    val name: String,
    val description: String?,
    val classification: String,  // "SHARED", "DEDICATED", "SYSTEM"
    val ownerTeamId: Long,
    val ownerTeamName: String,
    val ownership: String,  // "OWNED", "SHARED"
    val permission: ResourcePermission?,  // null if OWNED
    val hasGrant: Boolean,  // true if user has UserResourceGrant
    val updatedAt: LocalDateTime,
)

// Share Summary
data class ResourceShareDto(
    val id: Long,
    val ownerTeamId: Long,
    val ownerTeamName: String,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String,
    val resourceType: ShareableResourceType,
    val resourceId: Long,
    val resourceName: String,
    val permission: ResourcePermission,
    val visibleToTeam: Boolean,
    val grantCount: Int,  // Number of UserResourceGrants
    val grantedBy: String,
    val grantedAt: LocalDateTime,
)

// Grant Summary
data class UserGrantDto(
    val id: Long,
    val shareId: Long,
    val userId: Long,
    val userEmail: String,
    val userName: String,
    val permission: ResourcePermission,
    val grantedBy: String,
    val grantedAt: LocalDateTime,
)

// Sync Result
data class ResourceSyncResultDto(
    val resourceType: ShareableResourceType,
    val teamId: Long,
    val synced: Int,
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val errors: List<ResourceSyncError>,
)

data class ResourceSyncError(
    val name: String,
    val error: String,
)
```

### 6.3 Command/Projection Classes

#### Commands

```kotlin
// module-core-domain/command/resource/ResourceShareCommands.kt
data class CreateResourceShareCommand(
    val ownerTeamId: Long,
    val sharedWithTeamId: Long,
    val resourceType: ShareableResourceType,
    val resourceId: Long,
    val permission: ResourcePermission,
    val visibleToTeam: Boolean,
    val grantedBy: Long,
)

data class UpdateResourceShareCommand(
    val shareId: Long,
    val permission: ResourcePermission?,
    val visibleToTeam: Boolean?,
    val updatedBy: Long,
)

data class RevokeResourceShareCommand(
    val shareId: Long,
    val revokedBy: Long,
)

// module-core-domain/command/resource/UserGrantCommands.kt
data class CreateUserGrantCommand(
    val shareId: Long,
    val userId: Long,
    val permission: ResourcePermission,
    val grantedBy: Long,
)

data class UpdateUserGrantCommand(
    val grantId: Long,
    val permission: ResourcePermission,
    val updatedBy: Long,
)

data class RevokeUserGrantCommand(
    val grantId: Long,
    val revokedBy: Long,
)
```

#### Projections

```kotlin
// module-core-domain/projection/resource/ResourceProjections.kt
data class ResourceWithOwnershipProjection(
    val resourceId: Long,
    val resourceType: ShareableResourceType,
    val resourceName: String,
    val ownerTeamId: Long,
    val ownerTeamName: String,
    val isOwned: Boolean,
    val sharePermission: ResourcePermission?,
    val grantPermission: ResourcePermission?,
    val visibleToTeam: Boolean?,
)

data class ShareWithGrantsProjection(
    val shareId: Long,
    val resourceId: Long,
    val resourceName: String,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String,
    val permission: ResourcePermission,
    val grantCount: Int,
)
```

---

## 7. Module Placement

### 7.1 Location Overview

| Component | Module | Full Package Path |
|-----------|--------|-------------------|
| TeamResourceShareEntity | module-core-domain | `com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity` |
| UserResourceGrantEntity | module-core-domain | `com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity` |
| Repositories (interfaces) | module-core-domain | `com.dataops.basecamp.domain.repository.resource.*` |
| Repositories (impl) | module-core-infra | `com.dataops.basecamp.infra.repository.resource.*` |
| ResourceShareService | module-core-domain | `com.dataops.basecamp.domain.service.ResourceShareService` |
| UserGrantService | module-core-domain | `com.dataops.basecamp.domain.service.UserGrantService` |
| ResourceSecurityService | **module-server-api** | `com.dataops.basecamp.security.ResourceSecurityService` |
| TeamSecurityService | **module-server-api** | `com.dataops.basecamp.security.TeamSecurityService` |
| ResourceController | module-server-api | `com.dataops.basecamp.controller.ResourceController` |
| ResourceShareController | module-server-api | `com.dataops.basecamp.controller.ResourceShareController` |
| UserGrantController | module-server-api | `com.dataops.basecamp.controller.UserGrantController` |
| DTOs | module-server-api | `com.dataops.basecamp.dto.resource.ResourceDtos` |
| Enums | module-core-common | `com.dataops.basecamp.common.enums.ResourceEnums` |

> **Note:** Security services are in `module-server-api/security/` because they depend on `SecurityContextHolder` which is a Spring Security framework component. Per hexagonal architecture, framework-dependent code belongs in outer layers.

### 7.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    └── ResourceEnums.kt        # ShareableResourceType, ResourcePermission

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/resource/
    │   ├── TeamResourceShareEntity.kt
    │   └── UserResourceGrantEntity.kt
    ├── command/resource/
    │   ├── ResourceShareCommands.kt
    │   └── UserGrantCommands.kt
    ├── projection/resource/
    │   └── ResourceProjections.kt
    ├── repository/resource/
    │   ├── TeamResourceShareRepositoryJpa.kt
    │   ├── TeamResourceShareRepositoryDsl.kt
    │   ├── UserResourceGrantRepositoryJpa.kt
    │   └── UserResourceGrantRepositoryDsl.kt
    └── service/
        ├── ResourceService.kt
        ├── ResourceShareService.kt
        └── UserGrantService.kt

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/repository/resource/
    ├── TeamResourceShareRepositoryJpaImpl.kt
    ├── TeamResourceShareRepositoryDslImpl.kt
    ├── UserResourceGrantRepositoryJpaImpl.kt
    └── UserResourceGrantRepositoryDslImpl.kt

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── controller/
    │   ├── ResourceController.kt
    │   ├── ResourceShareController.kt
    │   └── UserGrantController.kt
    ├── security/
    │   ├── ResourceSecurityService.kt
    │   ├── TeamSecurityService.kt
    │   └── JwtAuthenticationConverter.kt
    └── dto/resource/
        └── ResourceDtos.kt
```

---

## 8. Database Schema

### 8.1 Tables

```sql
-- Team Resource Share table
CREATE TABLE team_resource_share (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_team_id BIGINT NOT NULL,
    shared_with_team_id BIGINT NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    visible_to_team BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,

    -- Note: No FOREIGN KEY constraints - validation at API level

    INDEX idx_resource_share_owner_team (owner_team_id),
    INDEX idx_resource_share_shared_team (shared_with_team_id),
    INDEX idx_resource_share_resource (resource_type, resource_id),
    INDEX idx_resource_share_deleted_at (deleted_at),
    UNIQUE KEY uk_resource_share (owner_team_id, shared_with_team_id, resource_type, resource_id)
);

-- User Resource Grant table
CREATE TABLE user_resource_grant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    share_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    granted_by BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,

    -- Note: No FOREIGN KEY constraints - cascade delete at API level

    INDEX idx_user_grant_share_id (share_id),
    INDEX idx_user_grant_user_id (user_id),
    INDEX idx_user_grant_deleted_at (deleted_at),
    UNIQUE KEY uk_user_grant (share_id, user_id)
);
```

### 8.2 Cascade Delete (API Level)

Since MySQL FKs are not used, cascade delete must be implemented at the service layer:

```kotlin
// ResourceShareService.kt
@Transactional
fun revokeShare(command: RevokeResourceShareCommand) {
    val share = teamResourceShareRepositoryJpa.findById(command.shareId)
        ?: throw ShareNotFoundException(command.shareId)

    // 1. Delete all UserResourceGrants for this share
    userResourceGrantRepositoryJpa.deleteByShareId(command.shareId)

    // 2. Soft delete the share
    share.delete(command.revokedBy)
    teamResourceShareRepositoryJpa.save(share)
}
```

---

## 9. Implementation Phases

### Phase 1: Core Resource Sharing (MVP) - Complete (2026-01-10)

| Component | Status | Description |
|-----------|--------|-------------|
| ShareableResourceType enum | **Complete** | 6 resource types |
| ResourcePermission enum | **Complete** | VIEWER, EDITOR |
| TeamResourceShareEntity | **Complete** | Team-to-team sharing |
| Share CRUD APIs | **Complete** | Create, update, revoke shares (5 endpoints) |
| Visibility control | **Complete** | visibleToTeam flag |
| ResourceSecurityService | **Complete** | Permission checks (canView, canEdit, canShare) |

### Phase 2: User Grants - Complete (2026-01-10)

| Component | Status | Description |
|-----------|--------|-------------|
| UserResourceGrantEntity | **Complete** | User-level grants |
| Grant CRUD APIs | **Complete** | Create, update, revoke grants (5 endpoints) |
| Permission hierarchy | **Complete** | Grant cannot exceed share |
| Team resource listing | **Complete** | OWNED vs SHARED display |

### Phase 3: Airflow Integration

| Component | Status | Description |
|-----------|--------|-------------|
| Resource sync endpoint | Planned | `/api/v1/resources/sync` |
| Sync from Git repos | Planned | Metric, Dataset, Workflow, Quality |
| Delta sync support | Planned | Create, update, delete detection |
| Sync audit logging | Planned | Track all sync operations |

### Phase 4: Enhanced Features (Future)

| Component | Status | Description |
|-----------|--------|-------------|
| Share request workflow | Future | Request -> Approve flow |
| Expiring shares | Future | Auto-revoke after date |
| Share delegation | Future | Allow granted users to re-share |

---

## 10. Implementation Checklist

### 10.1 Phase 1 Checklist - Complete (2026-01-10)

- [x] Create `ResourceEnums.kt` in `module-core-common/enums/`
- [x] Create `TeamResourceShareEntity.kt` in `module-core-domain/entity/resource/`
- [x] Create `TeamResourceShareRepositoryJpa.kt` and `TeamResourceShareRepositoryDsl.kt`
- [x] Create `TeamResourceShareRepositoryJpaImpl.kt` and `TeamResourceShareRepositoryDslImpl.kt`
- [x] Create `ResourceShareService.kt`
- [x] Create `ResourceSecurityService.kt` with `@Component("resourceSecurity")`
- [x] Create `ResourceShareController.kt`
- [x] Create `ResourceDtos.kt`
- [x] Add `@PreAuthorize` annotations to controllers
- [x] Write unit tests for services (13 tests)
- [x] Write controller tests with mock users (6 tests)

### 10.2 Phase 2 Checklist - Complete (2026-01-10)

- [x] Create `UserResourceGrantEntity.kt` in `module-core-domain/entity/resource/`
- [x] Create `UserResourceGrantRepositoryJpa.kt` and `UserResourceGrantRepositoryDsl.kt`
- [x] Create `UserResourceGrantRepositoryJpaImpl.kt` and `UserResourceGrantRepositoryDslImpl.kt`
- [x] Create `UserGrantService.kt`
- [x] Create `UserGrantController.kt`
- [x] Implement cascade delete in `ResourceShareService`
- [x] Update `ResourceSecurityService` to check grants
- [x] Write tests for grant permission hierarchy (17 service + 8 controller tests)

---

## Appendix A: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Entity design | Share + Grant separation | Fine-grained control, clear Producer/Consumer model |
| 2 | MySQL FK | Not used | ID-based joins, API-level cascade, flexibility |
| 3 | Visibility default | visibleToTeam = true | Consumer Team can see SHARED list without grant |
| 4 | Permission model | VIEWER/EDITOR | Simple, covers read/write needs |
| 5 | Grant hierarchy | Grant <= Share permission | Prevent privilege escalation |
| 6 | Resource types | 6 shareable types | Covers main collaboration use cases |
| 7 | Catalog columns | Via CATALOG_TABLE | No separate CATALOG_COLUMN type |
| 8 | URL pattern | `/api/v1/resources/{type}/shares` | Resource-centric API design |
| 9 | Security location | module-server-api | Spring Security dependency |
| 10 | ACL merge | Into RESOURCE_FEATURE | Single source of truth for access control |

---

## Appendix B: Security Expression Reference

| Expression | Component | Description |
|------------|-----------|-------------|
| `@resourceSecurity.canView(#resourceType, #resourceId)` | ResourceSecurityService | Owner member or has grant |
| `@resourceSecurity.canEdit(#resourceType, #resourceId)` | ResourceSecurityService | Owner editor+ or EDITOR grant |
| `@resourceSecurity.canShare(#resourceType, #resourceId)` | ResourceSecurityService | Owner manager |
| `@teamSecurity.isMember(#teamId)` | TeamSecurityService | Any team member |
| `@teamSecurity.canEdit(#teamId)` | TeamSecurityService | Team EDITOR or MANAGER |
| `@teamSecurity.isManager(#teamId)` | TeamSecurityService | Team MANAGER only |
| `hasRole('ROLE_ADMIN')` | Built-in | System ADMIN role |
| `hasRole('ROLE_USER')` | Built-in | Authenticated user |

---

## Appendix C: Migration from ACL_FEATURE.md

The following content has been merged from `ACL_FEATURE.md`:

| Section | Source | Destination |
|---------|--------|-------------|
| Authentication Architecture | ACL 2.x | Section 5.1 |
| Role Hierarchy | ACL 1.4 | Section 5.2 |
| Security Expressions | ACL 3.x | Section 5.3 |
| Permission Matrix | ACL 3.3 | Section 5.4 |
| ApiTokenEntity | ACL 4.x | See `AUTH_FEATURE.md` (TBD) |
| API Token Management | ACL 8.x | See `AUTH_FEATURE.md` (TBD) |

**Note:** `ACL_FEATURE.md` will be moved to `archived/ACL_FEATURE.md` after this specification is approved.

---

## Appendix D: Related Documents

- [`TEAM_FEATURE.md`](./TEAM_FEATURE.md) - Team Management (TeamRole, TeamMemberEntity)
- [`SQL_FEATURE.md`](./SQL_FEATURE.md) - SQL Worksheet (SqlWorksheetEntity, WorksheetFolderEntity)
- [`AUDIT_FEATURE.md`](./AUDIT_FEATURE.md) - Audit Logging

---

**Last Updated:** 2026-01-10 (v1.0.0 - Phase 1 + Phase 2 Complete, 10 endpoints, 44 tests)
