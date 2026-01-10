# Team Management Feature Specification

> **Version:** 1.1.0 | **Status:** Phase 1 Complete | **Priority:** P1 High
> **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** Phase 1 Complete (2026-01-10) | **Endpoints:** 10/35+ Complete
>
> **Data Source:** Self-managed JPA (Team, TeamMember)
> **External Data:** External Scheduler collects Ranger/BigQuery/Superset resources
>
> **Replaces:** [`PROJECT_FEATURE.md`](./PROJECT_FEATURE.md) (deprecated)
> **Related Documents:**
> - [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md) - Resource Sharing (TeamResourceShareEntity, UserResourceGrantEntity)
> - [`AUDIT_FEATURE.md`](./AUDIT_FEATURE.md) - Audit Logging
>
> **Implementation Details:** [`TEAM_RELEASE.md`](./TEAM_RELEASE.md)

---

## 1. Overview

### 1.1 Purpose

The Team Management API provides a unified resource ownership and collaboration model for the Basecamp data platform. Teams own all data resources including Metric, Dataset, Workflow, Quality, GitHub, Query History, and SQL Snippets. Resource sharing between teams is supported with granular permission control.

**Target Users:**
- Data professionals (DS/DA/DAE/DE): Develop locally using CLI, push specs to GitHub, execute via Airflow
- Non-technical users (Marketing, Operations): Browse and execute queries via Basecamp UI

**Key Use Cases:**
- Organize all data resources by team ownership
- Share individual resources between teams with View/Edit permissions
- Manage external resource (BigQuery, Superset, Ranger) associations
- Control resource access with role-based permissions (Manager/Editor/Viewer)

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Team Management** | Create teams, manage members with Manager/Editor/Viewer roles |
| **Resource Ownership** | Team owns Metric, Dataset, Workflow, Quality, GitHub, QueryHistory, SqlSnippet |
| **Resource Sharing** | Share individual resources between teams (see [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md)) |
| **SQL Ownership** | Team owns SQL resources directly (1-level folder structure) |
| **External Resources** | Associate Ranger/BigQuery tables, Superset dashboards with teams |
| **Slack Alerts** | Notify on share request/grant events (via AlertService interface) |
| **Context Switching** | UI supports Team context selection |

### 1.3 Architecture Overview

```
+-----------------------------------------------------------------------------+
|                         Team Management API                                   |
+-----------------------------------------------------------------------------+
|                                                                             |
|  TeamController         ResourceShareController      AlertController         |
|  /api/v1/teams          /api/v1/shares               /api/v1/alerts          |
|                                                                             |
+-----------------------------------------------------------------------------+
|                                                                             |
|  TeamService            ResourceShareService         AlertService            |
|                                                      (Interface)             |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +---------------+    +---------------------+    +------------------------+ |
|  | TeamEntity    |    | TeamResourceShare   |    | External Resources     | |
|  |               |    | Entity              |    | (Ranger/BQ/Superset)   | |
|  |  1:N          |    +---------------------+    +------------------------+ |
|  |  v            |                                                          |
|  | TeamMember    |    +---------------------+                               |
|  | Entity        |    | TeamExternalResource|                               |
|  +---------------+    | Entity              |                               |
|                       +---------------------+                               |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 1.4 Resource Ownership Model

```
+-----------------------------------------------------------------------------+
|                           Resource Ownership Model                            |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +------------------------------------------+                               |
|  |              TeamEntity                   |                               |
|  |  Owns (Managed Resources):                |                               |
|  |  - MetricEntity (teamId FK)              |                               |
|  |  - DatasetEntity (teamId FK)             |                               |
|  |  - WorkflowEntity (teamId FK)            |                               |
|  |  - QualitySpecEntity (teamId FK)         |                               |
|  |  - GitHubRepoEntity (teamId FK)          |                               |
|  |  - QueryHistoryEntity (teamId)           |                               |
|  |  - SqlFolderEntity (teamId FK)           |  <- NEW: Team owns SQL       |
|  |    - SqlSnippetEntity (folderId FK)      |                               |
|  |                                          |                               |
|  |  Associates (External Resources):         |                               |
|  |  - RangerTableEntity (via join table)    |                               |
|  |  - BigQueryTableEntity (via join table)  |                               |
|  |  - SupersetDashboardEntity (via join)    |                               |
|  +------------------------------------------+                               |
|                                                                             |
|  Resource Types:                                                            |
|  - MANAGED: Created/owned by team (Metric, Dataset, SQL, etc.)             |
|  - EXTERNAL: Collected from external systems, can be associated            |
|  - COLLECTED: Aggregated data (QueryHistory, etc.)                         |
|                                                                             |
+-----------------------------------------------------------------------------+
```

---

## 2. Data Model

### 2.1 Entity Relationship Diagram

```
+---------------------+           +---------------------+
|     TeamEntity      |           | TeamResourceShare   |
+---------------------+           | Entity              |
| id (PK)             |           +---------------------+
| name (unique)       |           | id (PK)             |
| displayName         |           | ownerTeamId (FK)    |
| description         |           | sharedWithTeamId(FK)|
| [BaseEntity]        |           | resourceType        |
+----------+----------+           | resourceId          |
           |                      | permission (VIEW/   |
           | 1:N                  |   EDIT)             |
           v                      | grantedBy           |
+---------------------+           | grantedAt           |
|  TeamMemberEntity   |           | [BaseEntity]        |
+---------------------+           +---------------------+
| id (PK)             |
| teamId (FK)         |           +---------------------+
| userId (FK)         |           | TeamExternalResource|
| role (MANAGER/      |           | Entity              |
|   EDITOR/VIEWER)    |           +---------------------+
| [BaseEntity]        |           | id (PK)             |
+---------------------+           | teamId (FK)         |
                                  | resourceType        |
                                  | resourceId          |
                                  | assignedBy          |
                                  | assignedAt          |
                                  | [BaseEntity]        |
                                  +---------------------+

+-------------------------------------------------------------------+
|                    External Resource Entities                       |
+-------------------------------------------------------------------+
|                                                                   |
|  +---------------------+  +---------------------+                 |
|  | RangerTableEntity   |  | BigQueryTableEntity |                 |
|  +---------------------+  +---------------------+                 |
|  | id (PK)             |  | id (PK)             |                 |
|  | tableName           |  | gcpProjectId        |                 |
|  | databaseName        |  | datasetId           |                 |
|  | schemaName          |  | tableId             |                 |
|  | owner               |  | location            |                 |
|  | lastSyncAt          |  | lastSyncAt          |                 |
|  | [BaseEntity]        |  | [BaseEntity]        |                 |
|  +---------------------+  +---------------------+                 |
|                                                                   |
|  +---------------------+                                          |
|  |SupersetDashboard    |                                          |
|  |Entity               |                                          |
|  +---------------------+                                          |
|  | id (PK)             |                                          |
|  | dashboardId         |                                          |
|  | title               |                                          |
|  | url                 |                                          |
|  | owner               |                                          |
|  | lastSyncAt          |                                          |
|  | [BaseEntity]        |                                          |
|  +---------------------+                                          |
|                                                                   |
|  Note: External resources are populated by Airflow scheduler.     |
|  Basecamp provides read-only access + team association.           |
+-------------------------------------------------------------------+
```

### 2.2 Role Definitions

#### Team Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| **MANAGER** | Team administrator | Full access, manage members, approve shares, manage settings |
| **EDITOR** | Resource contributor | Create, update, delete team resources (Metric, Dataset, SQL, etc.) |
| **VIEWER** | Read-only member | View and execute team resources, cannot modify |

### 2.3 Entity Definitions

#### TeamEntity

```kotlin
@Entity
@Table(
    name = "team",
    indexes = [
        Index(name = "idx_team_name", columnList = "name", unique = true),
        Index(name = "idx_team_deleted_at", columnList = "deleted_at"),
    ],
)
class TeamEntity(
    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    var name: String,

    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,

    @field:Size(max = 500)
    @Column(name = "description", length = 500)
    var description: String? = null,
) : BaseEntity()
```

#### TeamMemberEntity

```kotlin
@Entity
@Table(
    name = "team_member",
    indexes = [
        Index(name = "idx_team_member_team_id", columnList = "team_id"),
        Index(name = "idx_team_member_user_id", columnList = "user_id"),
        Index(name = "idx_team_member_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_team_member_team_user",
            columnNames = ["team_id", "user_id"]
        )
    ]
)
class TeamMemberEntity(
    @Column(name = "team_id", nullable = false)
    val teamId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: TeamRole = TeamRole.VIEWER,
) : BaseEntity()

// NOTE: Define in module-core-common/enums/TeamEnums.kt
enum class TeamRole {
    MANAGER,
    EDITOR,
    VIEWER
}
```

#### TeamResourceShareEntity

> **MOVED TO:** [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md)
>
> Resource sharing is now managed through a more comprehensive model:
> - `TeamResourceShareEntity` - Team-to-team visibility
> - `UserResourceGrantEntity` - User-specific permissions within Consumer Team
> - `ShareableResourceType` - 6 shareable resource types (WORKSHEET, WORKSHEET_FOLDER, DATASET, METRIC, WORKFLOW, QUALITY)
> - `ResourcePermission` - VIEWER/EDITOR permissions
>
> See [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md) for complete details.

#### TeamExternalResourceEntity

```kotlin
@Entity
@Table(
    name = "team_external_resource",
    indexes = [
        Index(name = "idx_team_external_team_id", columnList = "team_id"),
        Index(name = "idx_team_external_resource", columnList = "external_resource_type, external_resource_id"),
        Index(name = "idx_team_external_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_team_external_resource",
            columnNames = ["team_id", "external_resource_type", "external_resource_id"]
        )
    ]
)
class TeamExternalResourceEntity(
    @Column(name = "team_id", nullable = false)
    val teamId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "external_resource_type", nullable = false, length = 30)
    val externalResourceType: ExternalResourceType,

    @Column(name = "external_resource_id", nullable = false)
    val externalResourceId: Long,

    @Column(name = "assigned_by", nullable = false)
    val assignedBy: Long,

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()

// NOTE: Define in module-core-common/enums/TeamEnums.kt
enum class ExternalResourceType {
    RANGER_TABLE,
    BIGQUERY_TABLE,
    SUPERSET_DASHBOARD
}
```

#### External Resource Entities (Read-Only)

```kotlin
// Ranger Table (collected by external scheduler - read-only in Basecamp)
@Entity
@Table(
    name = "ranger_table",
    indexes = [
        Index(name = "idx_ranger_table_name", columnList = "table_name"),
        Index(name = "idx_ranger_table_database", columnList = "database_name"),
        Index(name = "idx_ranger_table_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_ranger_table",
            columnNames = ["database_name", "schema_name", "table_name"]
        )
    ]
)
class RangerTableEntity(
    @Column(name = "table_name", nullable = false, length = 200)
    var tableName: String,

    @Column(name = "database_name", nullable = false, length = 100)
    var databaseName: String,

    @Column(name = "schema_name", length = 100)
    var schemaName: String? = null,

    @Column(name = "owner", length = 100)
    var owner: String? = null,

    @Column(name = "last_sync_at", nullable = false)
    var lastSyncAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()

// BigQuery Table (collected by external scheduler - read-only in Basecamp)
@Entity
@Table(
    name = "bigquery_table",
    indexes = [
        Index(name = "idx_bigquery_table_project", columnList = "gcp_project_id"),
        Index(name = "idx_bigquery_table_dataset", columnList = "dataset_id"),
        Index(name = "idx_bigquery_table_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_bigquery_table",
            columnNames = ["gcp_project_id", "dataset_id", "table_id"]
        )
    ]
)
class BigQueryTableEntity(
    @Column(name = "gcp_project_id", nullable = false, length = 100)
    var gcpProjectId: String,

    @Column(name = "dataset_id", nullable = false, length = 100)
    var datasetId: String,

    @Column(name = "table_id", nullable = false, length = 200)
    var tableId: String,

    @Column(name = "location", length = 50)
    var location: String? = null,

    @Column(name = "last_sync_at", nullable = false)
    var lastSyncAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()

// Superset Dashboard (collected by external scheduler - read-only in Basecamp)
@Entity
@Table(
    name = "superset_dashboard",
    indexes = [
        Index(name = "idx_superset_dashboard_id", columnList = "dashboard_id"),
        Index(name = "idx_superset_dashboard_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_superset_dashboard",
            columnNames = ["dashboard_id"]
        )
    ]
)
class SupersetDashboardEntity(
    @Column(name = "dashboard_id", nullable = false)
    var dashboardId: Long,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "url", nullable = false, length = 500)
    var url: String,

    @Column(name = "owner", length = 100)
    var owner: String? = null,

    @Column(name = "last_sync_at", nullable = false)
    var lastSyncAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity()
```

### 2.4 Existing Entity Updates

기존 리소스 Entity에 `teamId` FK 추가 (Project 참조 제거):

```kotlin
// MetricEntity, DatasetEntity, WorkflowEntity, QualitySpecEntity 등에 추가
@Column(name = "team_id", nullable = false)
val teamId: Long

// SqlFolderEntity - projectId를 teamId로 변경
@Column(name = "team_id", nullable = false)
val teamId: Long
```

---

## 3. API Specifications

### 3.1 Team API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/teams` | List all teams | Authenticated |
| POST | `/api/v1/teams` | Create team | Admin |
| GET | `/api/v1/teams/{teamId}` | Get team details | Member |
| PUT | `/api/v1/teams/{teamId}` | Update team | Manager+ |
| DELETE | `/api/v1/teams/{teamId}` | Delete team (blocked if has resources) | Admin |
| GET | `/api/v1/teams/{teamId}/members` | List team members | Member |
| POST | `/api/v1/teams/{teamId}/members` | Add member to team | Admin |
| PUT | `/api/v1/teams/{teamId}/members/{userId}` | Update member role | Admin |
| DELETE | `/api/v1/teams/{teamId}/members/{userId}` | Remove member | Admin |
| GET | `/api/v1/teams/{teamId}/resources` | List team resources | Member |

### 3.2 Resource Sharing API

> **MOVED TO:** [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md)
>
> Resource sharing APIs are now under `/api/v1/resources/{resourceType}/shares`:
> - Create, update, revoke shares
> - User-level grants within Consumer Team
> - See [`RESOURCE_FEATURE.md`](./RESOURCE_FEATURE.md) Section 3 for complete API specifications.

### 3.3 External Resource Association API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/teams/{teamId}/external-resources` | List team's external resources | Member |
| POST | `/api/v1/teams/{teamId}/external-resources` | Assign external resource to team | Admin |
| DELETE | `/api/v1/teams/{teamId}/external-resources/{id}` | Remove external resource association | Admin |

### 3.4 External Resource API (Read-Only)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/external/ranger-tables` | List Ranger tables | Authenticated |
| GET | `/api/v1/external/ranger-tables/{id}` | Get Ranger table details | Authenticated |
| GET | `/api/v1/external/bigquery-tables` | List BigQuery tables | Authenticated |
| GET | `/api/v1/external/bigquery-tables/{id}` | Get BigQuery table details | Authenticated |
| GET | `/api/v1/external/superset-dashboards` | List Superset dashboards | Authenticated |
| GET | `/api/v1/external/superset-dashboards/{id}` | Get dashboard details | Authenticated |

### 3.5 Context API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/context/me` | Get current user's teams | Authenticated |
| PUT | `/api/v1/context/current` | Set current team context | Authenticated |
| GET | `/api/v1/context/current` | Get current context | Authenticated |

---

## 4. API Details

### 4.1 Get User Context

**`GET /api/v1/context/me`**

Returns the current user's team memberships.

**Response:**
```json
{
  "userId": 123,
  "email": "user@example.com",
  "teams": [
    {
      "id": 1,
      "name": "data-engineering",
      "displayName": "Data Engineering Team",
      "role": "EDITOR"
    },
    {
      "id": 2,
      "name": "ml-infra",
      "displayName": "ML Infrastructure Team",
      "role": "VIEWER"
    }
  ],
  "currentContext": {
    "teamId": 1,
    "teamName": "data-engineering"
  }
}
```

### 4.2 Create Resource Share

**`POST /api/v1/teams/{teamId}/shares`**

```http
POST /api/v1/teams/1/shares
Content-Type: application/json

{
  "sharedWithTeamId": 2,
  "resourceType": "METRIC",
  "resourceId": 101,
  "permission": "VIEW"
}
```

**Response:** `201 Created`
```json
{
  "id": 456,
  "ownerTeamId": 1,
  "sharedWithTeamId": 2,
  "resourceType": "METRIC",
  "resourceId": 101,
  "resourceName": "daily_active_users",
  "permission": "VIEW",
  "grantedBy": "admin@example.com",
  "grantedAt": "2026-01-06T10:00:00Z"
}
```

> **Note:** Slack Alert is triggered on share creation (via AlertService).

### 4.3 List Team Resources

**`GET /api/v1/teams/{teamId}/resources`**

```http
GET /api/v1/teams/1/resources?type=METRIC&page=0&size=20
```

**Response:**
```json
{
  "owned": [
    {
      "type": "METRIC",
      "id": 101,
      "name": "daily_active_users",
      "description": "Daily active user count",
      "updatedAt": "2026-01-05T15:30:00Z"
    }
  ],
  "shared": [
    {
      "type": "DATASET",
      "id": 201,
      "name": "user_events",
      "description": "User event stream",
      "permission": "VIEW",
      "sharedByTeamName": "data-platform",
      "updatedAt": "2026-01-04T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2
}
```

### 4.4 Assign External Resource to Team

**`POST /api/v1/teams/{teamId}/external-resources`** (Admin Only)

```http
POST /api/v1/teams/1/external-resources
Content-Type: application/json

{
  "externalResourceType": "BIGQUERY_TABLE",
  "externalResourceId": 501
}
```

**Response:** `201 Created`
```json
{
  "id": 789,
  "teamId": 1,
  "externalResourceType": "BIGQUERY_TABLE",
  "externalResourceId": 501,
  "resourceName": "project.dataset.table",
  "assignedBy": "admin@example.com",
  "assignedAt": "2026-01-06T11:00:00Z"
}
```

---

## 5. DTO Definitions

### 5.1 Request DTOs

```kotlin
// Team
data class CreateTeamRequest(
    @field:NotBlank @field:Size(max = 50)
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Name must be lowercase alphanumeric with hyphens")
    val name: String,
    @field:NotBlank @field:Size(max = 100)
    val displayName: String,
    @field:Size(max = 500)
    val description: String? = null,
)

data class UpdateTeamRequest(
    @field:Size(max = 100)
    val displayName: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
)

data class AddTeamMemberRequest(
    val userId: Long,
    val role: TeamRole = TeamRole.VIEWER,
)

data class UpdateTeamMemberRequest(
    val role: TeamRole,
)

// Resource Sharing - MOVED TO RESOURCE_FEATURE.md
// Use CreateResourceShareRequest, UpdateResourceShareRequest from RESOURCE_FEATURE.md
// See: /api/v1/resources/{resourceType}/shares endpoints

// External Resource Association
data class AssignExternalResourceRequest(
    val externalResourceType: ExternalResourceType,
    val externalResourceId: Long,
)

// Context
data class SetContextRequest(
    val teamId: Long?,
)
```

### 5.2 Response DTOs

```kotlin
// Team
data class TeamSummaryDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val createdAt: LocalDateTime,
)

data class TeamDetailDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val resourceCounts: Map<TeamResourceType, Int>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class TeamMemberDto(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
)

// Resource Sharing - MOVED TO RESOURCE_FEATURE.md
// Use ResourceShareDto from RESOURCE_FEATURE.md

// External Resource Association
data class TeamExternalResourceDto(
    val id: Long,
    val teamId: Long,
    val externalResourceType: ExternalResourceType,
    val externalResourceId: Long,
    val resourceName: String,
    val assignedBy: String,
    val assignedAt: LocalDateTime,
)

// Resource
data class TeamResourceDto(
    val type: TeamResourceType,
    val id: Long,
    val name: String,
    val description: String?,
    val permission: ResourcePermission?,  // null if owned, VIEW/EDIT if shared
    val sharedByTeamName: String?,     // null if owned
    val updatedAt: LocalDateTime,
)

// Context
data class UserContextDto(
    val userId: Long,
    val email: String,
    val teams: List<UserTeamDto>,
    val currentContext: CurrentContextDto?,
)

data class UserTeamDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val role: TeamRole,
)

data class CurrentContextDto(
    val teamId: Long?,
    val teamName: String?,
)
```

### 5.3 Command/Projection Definitions

#### Command Classes

```kotlin
// module-core-domain/command/team/TeamCommands.kt
data class CreateTeamCommand(
    val name: String,
    val displayName: String,
    val description: String?,
)

data class UpdateTeamCommand(
    val teamId: Long,
    val displayName: String?,
    val description: String?,
)

data class AddTeamMemberCommand(
    val teamId: Long,
    val userId: Long,
    val role: TeamRole,
)

data class RemoveTeamMemberCommand(
    val teamId: Long,
    val userId: Long,
)

data class UpdateTeamMemberRoleCommand(
    val teamId: Long,
    val userId: Long,
    val newRole: TeamRole,
)

// module-core-domain/command/team/ResourceShareCommands.kt
data class CreateResourceShareCommand(
    val ownerTeamId: Long,
    val targetTeamId: Long,
    val resourceType: TeamResourceType,
    val resourceId: Long,
    val permission: ResourcePermission,
    val requestedBy: Long,
)

data class UpdateResourceSharePermissionCommand(
    val shareId: Long,
    val newPermission: ResourcePermission,
    val updatedBy: Long,
)

data class RevokeResourceShareCommand(
    val shareId: Long,
    val revokedBy: Long,
)

// module-core-domain/command/team/ExternalResourceCommands.kt
data class AssignExternalResourceCommand(
    val teamId: Long,
    val externalResourceType: ExternalResourceType,
    val externalResourceId: Long,
    val assignedBy: Long,
)

data class RemoveExternalResourceCommand(
    val teamId: Long,
    val externalResourceId: Long,
    val removedBy: Long,
)
```

#### Projection Classes

```kotlin
// module-core-domain/projection/team/TeamProjections.kt
data class TeamStatisticsProjection(
    val teamId: Long,
    val memberCount: Int,
    val resourceCounts: Map<TeamResourceType, Int>,
)

data class TeamMemberWithUserProjection(
    val memberId: Long,
    val userId: Long,
    val username: String,
    val email: String,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
)

data class TeamResourceSummaryProjection(
    val resourceType: TeamResourceType,
    val resourceId: Long,
    val resourceName: String,
    val isOwned: Boolean,           // true if team owns, false if shared
    val permission: ResourcePermission?, // null if owned, VIEW/EDIT if shared
    val sharedByTeamId: Long?,
    val sharedByTeamName: String?,
)

data class TeamShareSummaryProjection(
    val shareId: Long,
    val direction: ShareDirection,  // OUTGOING (owned), INCOMING (received)
    val counterpartyTeamId: Long,
    val counterpartyTeamName: String,
    val resourceType: TeamResourceType,
    val resourceId: Long,
    val resourceName: String,
    val permission: ResourcePermission,
    val grantedAt: LocalDateTime,
)

enum class ShareDirection {
    OUTGOING,  // Team owns the resource and shared it
    INCOMING   // Team received access from another team
}
```

---

## 6. Permission Matrix

### 6.1 Team Permissions

| Action | ADMIN | MANAGER | EDITOR | VIEWER | Non-Member |
|--------|-------|---------|--------|--------|------------|
| View team info | Y | Y | Y | Y | N |
| View team members | Y | Y | Y | Y | N |
| View team resources | Y | Y | Y | Y | N |
| Execute resources | Y | Y | Y | Y | N |
| Create resources | Y | Y | Y | N | N |
| Update resources | Y | Y | Y | N | N |
| Delete resources | Y | Y | Y | N | N |
| Update team settings | Y | Y | N | N | N |
| Manage shares (grant) | Y | Y | N | N | N |
| Manage members | Y | N | N | N | N |
| Delete team | Y | N | N | N | N |
| Assign external resources | Y | N | N | N | N |

### 6.2 Shared Resource Permissions

| Action | EDIT Share | VIEW Share |
|--------|------------|------------|
| View resource | Y | Y |
| Execute resource | Y | Y |
| Update resource | Y | N |
| Delete resource | N | N |

> **Note:** DELETE is never allowed on shared resources. Only the owning team can delete.

---

## 7. Alert Service Design

### 7.1 AlertService Interface

```kotlin
// module-core-domain/service/AlertService.kt
interface AlertService {
    /**
     * Send alert for share request/grant events.
     */
    fun sendShareAlert(event: ShareAlertEvent)

    /**
     * Send alert for permission change events.
     */
    fun sendPermissionAlert(event: PermissionAlertEvent)
}

data class ShareAlertEvent(
    val eventType: ShareEventType,  // SHARE_REQUESTED, SHARE_GRANTED, SHARE_REVOKED
    val ownerTeamId: Long,
    val ownerTeamName: String,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String,
    val resourceType: TeamResourceType,
    val resourceId: Long,
    val resourceName: String,
    val permission: ResourcePermission,
    val actorUserId: Long,
    val actorEmail: String,
    val timestamp: LocalDateTime,
)

enum class ShareEventType {
    SHARE_REQUESTED,
    SHARE_GRANTED,
    SHARE_REVOKED,
    PERMISSION_CHANGED
}
```

### 7.2 AlertService 위치 및 근거

#### 현재 위치

| Component | Module | Package |
|-----------|--------|---------|
| AlertService (interface) | module-core-domain | `service/AlertService.kt` |
| ShareAlertEvent, PermissionAlertEvent | module-core-domain | `service/AlertService.kt` |
| MockSlackAlertAdapter | module-core-infra | `adapter/MockSlackAlertAdapter.kt` |
| SlackAlertAdapter (Phase 2) | module-core-infra | `adapter/SlackAlertAdapter.kt` |

#### 설계 근거

**AlertService가 module-core-domain에 위치하는 이유:**

1. **도메인 이벤트 알림**: AlertService는 Resource Sharing 신청/발급과 같은 도메인 이벤트 알림 용도로 사용됩니다. 외부 시스템(Slack)과 통신하지만, 알림 자체가 도메인 로직의 일부입니다.

2. **Port-Adapter 패턴 준수**: AlertService는 Port (인터페이스)로서 도메인 계층에 위치하고, MockSlackAlertAdapter/SlackAlertAdapter는 Adapter (구현체)로서 인프라 계층에 위치합니다.

3. **테스트 용이성**: 인터페이스가 도메인에 있으므로 테스트 시 Mock 구현을 쉽게 주입할 수 있습니다.

#### 대안 구조 (추후 확장 시)

알림 요구사항이 복잡해지면 다음과 같이 분리를 고려할 수 있습니다:

```
module-core-domain/external/alert/
├── AlertClient.kt           # interface (Port)
├── AlertRequest.kt          # 알림 요청 모델
└── AlertResponse.kt         # 알림 응답 모델

module-core-infra/external/alert/
├── SlackAlertClientImpl.kt  # Slack 구현 (Adapter)
├── MockAlertClientImpl.kt   # Mock 구현 (Adapter)
└── EmailAlertClientImpl.kt  # Email 구현 (Future)
```

### 7.3 MockSlackAlertAdapter

```kotlin
// module-core-infra/adapter/MockSlackAlertAdapter.kt
@Component
@Profile("dev", "test")
class MockSlackAlertAdapter(
    private val logger: Logger = LoggerFactory.getLogger(MockSlackAlertAdapter::class.java)
) : AlertService {

    override fun sendShareAlert(event: ShareAlertEvent) {
        logger.info("""
            [MOCK SLACK ALERT] Share Event
            Type: ${event.eventType}
            From Team: ${event.ownerTeamName}
            To Team: ${event.sharedWithTeamName}
            Resource: ${event.resourceType}/${event.resourceName}
            Permission: ${event.permission}
            Actor: ${event.actorEmail}
        """.trimIndent())
    }

    override fun sendPermissionAlert(event: PermissionAlertEvent) {
        logger.info("[MOCK SLACK ALERT] Permission Event: $event")
    }
}
```

### 7.4 Real Slack Implementation (Phase 2)

```kotlin
// module-core-infra/adapter/SlackAlertAdapter.kt (Phase 2)
@Component
@Profile("prod")
class SlackAlertAdapter(
    private val slackClient: SlackClient,
    private val teamRepositoryDsl: TeamRepositoryDsl,
) : AlertService {
    // Real Slack webhook implementation
}
```

---

## 8. Module Placement

### 8.1 Location Overview

| Component | Module | Package |
|-----------|--------|---------|
| TeamEntity, TeamMemberEntity | module-core-domain | `entity/team/` |
| TeamResourceShareEntity | module-core-domain | `entity/team/` |
| TeamExternalResourceEntity | module-core-domain | `entity/team/` |
| External Entities (Ranger/BQ/Superset) | module-core-domain | `entity/external/` |
| Repositories (interfaces) | module-core-domain | `repository/team/`, `repository/external/` |
| Repositories (impl) | module-core-infra | `repository/team/`, `repository/external/` |
| AlertService (interface) | module-core-domain | `service/` |
| AlertAdapter (impl) | module-core-infra | `adapter/` |
| Services | module-core-domain | `service/` |
| Controllers | module-server-api | `controller/` |
| DTOs | module-server-api | `dto/team/` |
| Enums | module-core-common | `common/enums/TeamEnums.kt` |

### 8.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    └── TeamEnums.kt        # TeamRole, TeamResourceType, ResourcePermission, ExternalResourceType

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/
    │   ├── team/
    │   │   ├── TeamEntity.kt
    │   │   ├── TeamMemberEntity.kt
    │   │   ├── TeamResourceShareEntity.kt
    │   │   └── TeamExternalResourceEntity.kt
    │   └── external/
    │       ├── RangerTableEntity.kt
    │       ├── BigQueryTableEntity.kt
    │       └── SupersetDashboardEntity.kt
    ├── repository/
    │   ├── team/
    │   │   ├── TeamRepositoryJpa.kt
    │   │   ├── TeamRepositoryDsl.kt
    │   │   ├── TeamMemberRepositoryJpa.kt
    │   │   ├── TeamMemberRepositoryDsl.kt
    │   │   ├── TeamResourceShareRepositoryJpa.kt
    │   │   ├── TeamResourceShareRepositoryDsl.kt
    │   │   ├── TeamExternalResourceRepositoryJpa.kt
    │   │   └── TeamExternalResourceRepositoryDsl.kt
    │   └── external/
    │       ├── RangerTableRepositoryJpa.kt
    │       ├── BigQueryTableRepositoryJpa.kt
    │       └── SupersetDashboardRepositoryJpa.kt
    └── service/
        ├── TeamService.kt
        ├── ResourceShareService.kt
        ├── ExternalResourceService.kt
        ├── ContextService.kt
        └── AlertService.kt          # Interface

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/
    ├── repository/team/
    │   ├── TeamRepositoryJpaImpl.kt
    │   ├── TeamRepositoryDslImpl.kt
    │   ├── TeamMemberRepositoryJpaImpl.kt
    │   ├── TeamMemberRepositoryDslImpl.kt
    │   ├── TeamResourceShareRepositoryJpaImpl.kt
    │   ├── TeamResourceShareRepositoryDslImpl.kt
    │   ├── TeamExternalResourceRepositoryJpaImpl.kt
    │   └── TeamExternalResourceRepositoryDslImpl.kt
    └── adapter/
        ├── MockSlackAlertAdapter.kt   # dev/test profile
        └── SlackAlertAdapter.kt       # prod profile (Phase 2)

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── controller/
    │   ├── TeamController.kt
    │   ├── ResourceShareController.kt
    │   ├── ExternalResourceController.kt
    │   └── ContextController.kt
    └── dto/team/
        └── TeamDtos.kt
```

### 8.3 Enum Definitions

모든 Team Domain 관련 Enum은 `module-core-common/enums/TeamEnums.kt`에 정의합니다.

```kotlin
// module-core-common/src/main/kotlin/com/dataops/basecamp/common/enums/TeamEnums.kt
package com.dataops.basecamp.common.enums

/**
 * Team member role defining access level within a team.
 */
enum class TeamRole {
    MANAGER,  // Full access, manage members, approve shares, manage settings
    EDITOR,   // Create, update, delete team resources
    VIEWER    // Read-only access, execute queries
}

/**
 * Type of internal resource that can be owned by teams.
 */
enum class TeamResourceType {
    METRIC,
    DATASET,
    WORKFLOW,
    QUALITY,
    GITHUB_REPO,
    SQL_FOLDER,
    SQL_SNIPPET,
    QUERY_HISTORY
}

/**
 * Permission level for shared resources.
 * @deprecated Use ResourcePermission from RESOURCE_FEATURE.md instead
 * @see com.dataops.basecamp.common.enums.ResourcePermission
 */
// REMOVED: ResourcePermission enum moved to RESOURCE_FEATURE.md as ResourcePermission (VIEWER/EDITOR)

/**
 * Type of external resource collected by scheduler.
 */
enum class ExternalResourceType {
    RANGER_TABLE,
    BIGQUERY_TABLE,
    SUPERSET_DASHBOARD
}

/**
 * Share event type for AlertService notifications.
 */
enum class ShareEventType {
    SHARE_REQUESTED,
    SHARE_GRANTED,
    SHARE_REVOKED,
    PERMISSION_CHANGED
}
```

---

## 9. Database Schema

### 9.1 Tables

```sql
-- Team table
CREATE TABLE team (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_team_name (name),
    INDEX idx_team_deleted_at (deleted_at)
);

-- Team Member table
CREATE TABLE team_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_team_member_team_id (team_id),
    INDEX idx_team_member_user_id (user_id),
    INDEX idx_team_member_deleted_at (deleted_at),
    UNIQUE KEY uk_team_member_team_user (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES team(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Team Resource Share table
CREATE TABLE team_resource_share (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_team_id BIGINT NOT NULL,
    shared_with_team_id BIGINT NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    granted_by BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_resource_share_owner_team (owner_team_id),
    INDEX idx_resource_share_shared_team (shared_with_team_id),
    INDEX idx_resource_share_resource (resource_type, resource_id),
    INDEX idx_resource_share_deleted_at (deleted_at),
    UNIQUE KEY uk_resource_share (owner_team_id, shared_with_team_id, resource_type, resource_id),
    FOREIGN KEY (owner_team_id) REFERENCES team(id),
    FOREIGN KEY (shared_with_team_id) REFERENCES team(id)
);

-- Team External Resource table
CREATE TABLE team_external_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    external_resource_type VARCHAR(30) NOT NULL,
    external_resource_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_team_external_team_id (team_id),
    INDEX idx_team_external_resource (external_resource_type, external_resource_id),
    INDEX idx_team_external_deleted_at (deleted_at),
    UNIQUE KEY uk_team_external_resource (team_id, external_resource_type, external_resource_id),
    FOREIGN KEY (team_id) REFERENCES team(id)
);

-- External: Ranger Table
CREATE TABLE ranger_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_name VARCHAR(200) NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100),
    owner VARCHAR(100),
    last_sync_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    INDEX idx_ranger_table_name (table_name),
    INDEX idx_ranger_table_database (database_name),
    INDEX idx_ranger_table_deleted_at (deleted_at),
    UNIQUE KEY uk_ranger_table (database_name, schema_name, table_name)
);

-- External: BigQuery Table
CREATE TABLE bigquery_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    gcp_project_id VARCHAR(100) NOT NULL,
    dataset_id VARCHAR(100) NOT NULL,
    table_id VARCHAR(200) NOT NULL,
    location VARCHAR(50),
    last_sync_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    INDEX idx_bigquery_table_project (gcp_project_id),
    INDEX idx_bigquery_table_dataset (dataset_id),
    INDEX idx_bigquery_table_deleted_at (deleted_at),
    UNIQUE KEY uk_bigquery_table (gcp_project_id, dataset_id, table_id)
);

-- External: Superset Dashboard
CREATE TABLE superset_dashboard (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dashboard_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    owner VARCHAR(100),
    last_sync_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    INDEX idx_superset_dashboard_id (dashboard_id),
    INDEX idx_superset_dashboard_deleted_at (deleted_at),
    UNIQUE KEY uk_superset_dashboard (dashboard_id)
);
```

### 9.2 Existing Table Updates

```sql
-- Add team_id to existing resource tables (remove project_id where applicable)
ALTER TABLE metric ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE metric ADD INDEX idx_metric_team_id (team_id);
ALTER TABLE metric ADD FOREIGN KEY (team_id) REFERENCES team(id);

ALTER TABLE dataset ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE dataset ADD INDEX idx_dataset_team_id (team_id);
ALTER TABLE dataset ADD FOREIGN KEY (team_id) REFERENCES team(id);

ALTER TABLE workflow ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE workflow ADD INDEX idx_workflow_team_id (team_id);
ALTER TABLE workflow ADD FOREIGN KEY (team_id) REFERENCES team(id);

ALTER TABLE quality_spec ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE quality_spec ADD INDEX idx_quality_spec_team_id (team_id);
ALTER TABLE quality_spec ADD FOREIGN KEY (team_id) REFERENCES team(id);

ALTER TABLE github_repo ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE github_repo ADD INDEX idx_github_repo_team_id (team_id);
ALTER TABLE github_repo ADD FOREIGN KEY (team_id) REFERENCES team(id);

-- SQL Folder: Change project_id to team_id
ALTER TABLE sql_folder DROP FOREIGN KEY fk_sql_folder_project;
ALTER TABLE sql_folder DROP COLUMN project_id;
ALTER TABLE sql_folder ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE sql_folder ADD INDEX idx_sql_folder_team_id (team_id);
ALTER TABLE sql_folder ADD FOREIGN KEY (team_id) REFERENCES team(id);
ALTER TABLE sql_folder DROP INDEX uk_sql_folder_name_project;
ALTER TABLE sql_folder ADD UNIQUE KEY uk_sql_folder_name_team (name, team_id);
```

---

## 10. Implementation Phases

### Phase 1: Core Team Management (MVP) - **COMPLETE** (2026-01-10)

> **Release Document:** [`TEAM_RELEASE.md`](./TEAM_RELEASE.md)

| Component | Status | Description |
|-----------|--------|-------------|
| TeamEntity, TeamMemberEntity | **Complete** | Team CRUD, member management |
| TeamRole: Manager/Editor/Viewer | **Complete** | Role-based access control |
| TeamManagementController (10 endpoints) | **Complete** | REST APIs for teams, members, resources |
| TeamResourceType enum | **Complete** | Resource type classification |
| Deletion protection | **Complete** | TeamHasResourcesException when team has resources |

**Test Coverage:** 32 tests (22 service + 10 controller)

**Endpoints Implemented:**
1. GET `/api/v1/team-management` - List teams
2. POST `/api/v1/team-management` - Create team (Admin)
3. GET `/api/v1/team-management/{teamId}` - Get team details
4. PUT `/api/v1/team-management/{teamId}` - Update team
5. DELETE `/api/v1/team-management/{teamId}` - Delete team (Admin)
6. GET `/api/v1/team-management/{teamId}/members` - List members
7. POST `/api/v1/team-management/{teamId}/members` - Add member (Admin)
8. PUT `/api/v1/team-management/{teamId}/members/{userId}` - Update role (Admin)
9. DELETE `/api/v1/team-management/{teamId}/members/{userId}` - Remove member (Admin)
10. GET `/api/v1/team-management/{teamId}/resources` - List resources

### Phase 2: Resource Sharing

| Component | Status | Description |
|-----------|--------|-------------|
| TeamResourceShareEntity | Planned | Individual resource sharing |
| ResourcePermission: View/Edit | Planned | Granular permissions |
| AlertService interface | Planned | Slack alert abstraction |
| MockSlackAlertAdapter | Planned | Dev/test mock implementation |

### Phase 3: External Resources

| Component | Status | Description |
|-----------|--------|-------------|
| RangerTableEntity | Planned | Ranger table metadata |
| BigQueryTableEntity | Planned | BigQuery table metadata |
| SupersetDashboardEntity | Planned | Superset dashboard metadata |
| TeamExternalResourceEntity | Planned | Team-external resource association |
| External Resource API | Planned | Read-only listing + team assignment |

### Phase 4: Enhanced Features (Future)

| Component | Status | Description |
|-----------|--------|-------------|
| Real Slack integration | Future | SlackAlertAdapter for production |
| Share request workflow | Future | Request -> Approve flow |
| Audit logging | Future | See AUDIT_FEATURE.md |
| Team hierarchy | Future | Division -> Team structure |

---

## 11. Success Criteria

### 11.1 Feature Completion

| Feature | Completion Condition |
|---------|----------------------|
| Team CRUD | All 10 endpoints working |
| Resource Sharing | All 5 endpoints working |
| External Resources | All 6+ endpoints working |
| Context API | All 3 endpoints working |
| Role enforcement | Permissions correctly applied |
| Alert service | Mock alerts logged in dev mode |

### 11.2 Test Coverage

| Metric | Target |
|--------|--------|
| Unit test coverage | >= 80% |
| Controller tests | All endpoints |
| Service tests | All business logic |
| Permission tests | All role combinations |
| Share permission tests | VIEW/EDIT enforcement |

---

## Appendix A: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Team role model | Manager/Editor/Viewer | Clear hierarchy, Manager for admin tasks |
| 2 | Resource sharing unit | Individual resource | Granular control, flexibility |
| 3 | Share permission | VIEW/EDIT | Simple, sufficient for collaboration |
| 4 | SQL ownership | Team-based | Simplified from Project-based |
| 5 | Folder structure | 1-level under Team | Consistent with previous SQL spec |
| 6 | External resource ownership | Optional (Admin assigns) | Flexibility for DW vs Datalake |
| 7 | Resource ownership transfer | Admin only | Prevent accidental transfers |
| 8 | Team deletion | Block if has resources | Data safety |
| 9 | Alert service | Interface + Mock adapter | Testability, Phase 2 for real Slack |
| 10 | Share process | Immediate (no approval) | MVP simplicity |

### A.1 Team 삭제 시 리소스 체크 상세 (Decision #8)

팀 삭제 요청 시 다음 리소스가 존재하면 삭제를 차단합니다:

#### 체크 대상 리소스

| Category | Resource | Check Logic |
|----------|----------|-------------|
| **Internal Resources** | SqlFolder | `sql_folder.team_id = ?` |
| | SqlSnippet | `sql_snippet.folder_id IN (SELECT id FROM sql_folder WHERE team_id = ?)` |
| **Collected Resources** | Metric | `metric.team_id = ?` |
| | Dataset | `dataset.team_id = ?` |
| | Workflow | `workflow.team_id = ?` |
| | Quality Spec | `quality_spec.team_id = ?` |
| | GitHub Repo | `github_repo.team_id = ?` |
| **Sharing References** | Outgoing Shares | `team_resource_share.owner_team_id = ?` |
| | Incoming Shares | `team_resource_share.shared_with_team_id = ?` |
| **External Resources** | Team External | `team_external_resource.team_id = ?` |
| **Members** | Team Members | `team_member.team_id = ?` |

#### 구현: TeamRepositoryDsl

```kotlin
// module-core-domain/repository/team/TeamRepositoryDsl.kt
interface TeamRepositoryDsl {
    fun findByConditions(query: ListTeamsQuery): Page<TeamEntity>
    fun hasResources(teamId: Long): TeamResourceCheckResult
}

// module-core-domain/projection/team/TeamResourceCheckResult.kt
data class TeamResourceCheckResult(
    val hasResources: Boolean,
    val sqlFolderCount: Int,
    val sqlSnippetCount: Int,
    val metricCount: Int,
    val datasetCount: Int,
    val workflowCount: Int,
    val qualityCount: Int,
    val githubRepoCount: Int,
    val outgoingShareCount: Int,
    val incomingShareCount: Int,
    val externalResourceCount: Int,
    val memberCount: Int,
) {
    fun toErrorMessage(): String {
        val resources = mutableListOf<String>()
        if (sqlFolderCount > 0) resources.add("SqlFolder($sqlFolderCount)")
        if (sqlSnippetCount > 0) resources.add("SqlSnippet($sqlSnippetCount)")
        if (metricCount > 0) resources.add("Metric($metricCount)")
        if (datasetCount > 0) resources.add("Dataset($datasetCount)")
        if (workflowCount > 0) resources.add("Workflow($workflowCount)")
        if (qualityCount > 0) resources.add("Quality($qualityCount)")
        if (githubRepoCount > 0) resources.add("GitHubRepo($githubRepoCount)")
        if (outgoingShareCount > 0) resources.add("OutgoingShare($outgoingShareCount)")
        if (incomingShareCount > 0) resources.add("IncomingShare($incomingShareCount)")
        if (externalResourceCount > 0) resources.add("ExternalResource($externalResourceCount)")
        if (memberCount > 0) resources.add("Member($memberCount)")
        return "Cannot delete team. Has resources: ${resources.joinToString(", ")}"
    }
}
```

#### 삭제 프로세스

1. **멤버 먼저 제거**: 팀 삭제 전 모든 멤버를 제거해야 합니다.
2. **리소스 이전 또는 삭제**: 리소스를 다른 팀으로 이전하거나 개별 삭제해야 합니다.
3. **공유 정리**: Outgoing/Incoming share를 모두 해제해야 합니다.
4. **최종 삭제**: 위 조건이 모두 충족되면 팀 삭제 (soft delete)가 가능합니다.

```kotlin
// TeamService.kt
@Transactional
fun deleteTeam(teamId: Long, deletedBy: Long) {
    val team = teamRepositoryJpa.findById(teamId)
        ?: throw TeamNotFoundException(teamId)

    val checkResult = teamRepositoryDsl.hasResources(teamId)
    if (checkResult.hasResources) {
        throw TeamHasResourcesException(teamId, checkResult.toErrorMessage())
    }

    team.delete(deletedBy)
    teamRepositoryJpa.save(team)
}
```

---

## Appendix B: Migration from PROJECT_FEATURE.md

### Breaking Changes

| Change | PROJECT_FEATURE.md | TEAM_FEATURE.md | Migration |
|--------|-------------------|-----------------|-----------|
| SQL ownership | Project owns SqlFolder | Team owns SqlFolder | Change FK from projectId to teamId |
| Cross-team collaboration | ProjectResourceRefEntity | TeamResourceShareEntity | Migrate refs to shares |
| Role names | Owner/Editor/Viewer | Manager/Editor/Viewer | Rename OWNER to MANAGER |
| Context model | Team + Project dual | Team only | Remove project context |

### Database Migration

```sql
-- 1. Create new team_resource_share from project_resource_ref
INSERT INTO team_resource_share (owner_team_id, shared_with_team_id, resource_type, ...)
SELECT ... FROM project_resource_ref;

-- 2. Update sql_folder.project_id to sql_folder.team_id
-- (Requires mapping project -> team relationship)

-- 3. Drop project-related tables after migration
DROP TABLE project_resource_ref;
DROP TABLE project_member;
DROP TABLE project;
```

---

## Appendix C: ACL_FEATURE.md Updates Required

The following updates are needed in `ACL_FEATURE.md` to align with this specification:

1. **Role names:** Change `TeamRole.OWNER` to `TeamRole.MANAGER`
2. **Remove ProjectRole:** No longer needed
3. **Security services:** Update `TeamSecurityService`, remove `ProjectSecurityService`
4. **Permission checks:** Update `@teamSecurity.isOwner()` to `@teamSecurity.isManager()`

---

## Appendix D: SQL_FEATURE.md Updates Required

The following updates are needed in `SQL_FEATURE.md`:

1. **SqlFolderEntity:** Change `projectId` to `teamId`
2. **Permission model:** Use TeamRole instead of ProjectRole
3. **URL structure:** Consider `/api/v1/teams/{teamId}/sql/folders|snippets`
4. **Remove project references:** All references to ProjectEntity

---

## Appendix E: Repository Bean Naming Pattern

```kotlin
// ========================================
// Domain Interfaces (module-core-domain/repository/team/)
// ========================================

// TeamRepositoryJpa.kt
interface TeamRepositoryJpa {
    fun save(team: TeamEntity): TeamEntity
    fun findById(id: Long): TeamEntity?
    fun findByName(name: String): TeamEntity?
    fun deleteById(id: Long)
}

// TeamRepositoryDsl.kt
interface TeamRepositoryDsl {
    fun findByConditions(query: ListTeamsQuery): Page<TeamEntity>
    fun hasResources(teamId: Long): Boolean  // Check before deletion
}

// ========================================
// Infrastructure Implementations (module-core-infra/repository/team/)
// ========================================

// TeamRepositoryJpaImpl.kt
@Repository("teamRepositoryJpa")
interface TeamRepositoryJpaImpl :
    TeamRepositoryJpa,
    JpaRepository<TeamEntity, Long> {

    override fun findByName(name: String): TeamEntity?
}

// TeamRepositoryDslImpl.kt
@Repository("teamRepositoryDsl")
class TeamRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TeamRepositoryDsl {
    override fun findByConditions(query: ListTeamsQuery): Page<TeamEntity> {
        // QueryDSL implementation
    }

    override fun hasResources(teamId: Long): Boolean {
        // Check if team has any resources (blocks deletion)
    }
}
```

---

**Last Updated:** 2026-01-10 (v1.1.0 - Phase 1 Complete, Resource sharing moved to RESOURCE_FEATURE.md)
