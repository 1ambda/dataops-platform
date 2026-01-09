# Team & Project Management Feature Specification

> **Version:** 1.0.1 | **Status:** 📋 Draft | **Priority:** P1 High
> **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** TBD | **Endpoints:** 0/30+ Complete
>
> **Data Source:** Self-managed JPA (Team, Project, TeamMember, ProjectMember, ProjectResourceRef)
> **External Data:** External Scheduler collects Ranger/BigQuery/Superset resources
>
> **Implementation Details:** [`PROJECT_RELEASE.md`](./PROJECT_RELEASE.md) (TBD)

---

## 1. Overview

### 1.1 Purpose

The Team & Project Management API provides a unified resource access and collaboration model for the Basecamp data platform. Teams own data resources (Metric, Dataset, Workflow, Quality, GitHub, Query History), while Projects serve as collaboration units that can reference team resources with controlled permissions.

**Target Users:**
- Data professionals (DS/DA/DAE/DE): Develop locally using CLI, push specs to GitHub, execute via Airflow
- Non-technical users (Marketing, Operations): Browse and execute queries via Basecamp UI

**Key Use Cases:**
- Organize data resources by team ownership
- Create cross-team collaboration projects
- Control resource access with fine-grained permissions
- Integrate external resource references (Ranger tables, BigQuery tables, Superset dashboards)

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Team Management** | Create teams, manage members with Owner/Editor/Viewer roles |
| **Project Management** | Create projects as independent work units |
| **Resource Ownership** | Team owns Metric, Dataset, Workflow, Quality, GitHub, Query History |
| **Resource Reference** | Projects reference team resources with Read+Execute permissions |
| **SQL Ownership** | Projects own SQL resources (1-level folder structure) |
| **External Resources** | View Ranger/BigQuery tables, Superset dashboards (collected externally) |
| **Context Switching** | UI supports Team + Project dual context selection |

### 1.3 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Team & Project Management API                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TeamController         ProjectController        ContextController           │
│  /api/v1/teams          /api/v1/projects         /api/v1/context            │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TeamService            ProjectService           ContextService              │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌───────────────────┐    ┌──────────────────────────┐ │
│  │ TeamEntity  │    │  ProjectEntity     │    │  ExternalResourceEntity │ │
│  │             │    │                    │    │  (Ranger/BQ/Superset)   │ │
│  │  1:N        │    │  1:N               │    └──────────────────────────┘ │
│  │  ▼          │    │  ▼                 │                                 │
│  │ TeamMember  │    │ ProjectMember      │                                 │
│  │ Entity      │    │ Entity             │                                 │
│  └─────────────┘    └───────────────────┘                                  │
│                              │                                              │
│                              │ 1:N                                          │
│                              ▼                                              │
│                     ┌────────────────────┐                                 │
│                     │ ProjectResourceRef │ ─── References Team Resources   │
│                     │ Entity             │                                  │
│                     └────────────────────┘                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.4 Relationship Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Resource Ownership Model                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────┐                                       │
│  │           TeamEntity            │                                       │
│  │  Owns:                          │                                       │
│  │  • MetricEntity (teamId FK)     │                                       │
│  │  • DatasetEntity (teamId FK)    │                                       │
│  │  • WorkflowEntity (teamId FK)   │                                       │
│  │  • QualitySpecEntity (teamId FK)│                                       │
│  │  • GitHubRepoEntity (teamId FK) │                                       │
│  │  • QueryHistoryEntity (teamId)  │                                       │
│  └─────────────────────────────────┘                                       │
│                                                                             │
│  ┌─────────────────────────────────┐                                       │
│  │         ProjectEntity           │                                       │
│  │  Owns:                          │                                       │
│  │  • SqlFolderEntity              │   (Project owns SQL resources)        │
│  │  • SavedQueryEntity             │                                       │
│  │                                 │                                       │
│  │  References (Read+Execute):     │                                       │
│  │  • Team's Metric/Dataset/etc    │   via ProjectResourceRefEntity        │
│  └─────────────────────────────────┘                                       │
│                                                                             │
│  Team과 Project는 독립적 (소유 관계 없음)                                   │
│  Project는 여러 Team의 리소스를 참조 가능                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Model

### 2.1 Entity Relationship Diagram

```
┌─────────────────────┐           ┌─────────────────────┐
│     TeamEntity      │           │    ProjectEntity    │
├─────────────────────┤           ├─────────────────────┤
│ id (PK)             │           │ id (PK)             │
│ name (unique)       │           │ name (unique)       │
│ displayName         │           │ displayName         │
│ description         │           │ description         │
│ [BaseEntity]        │           │ [BaseEntity]        │
└─────────┬───────────┘           └─────────┬───────────┘
          │                                 │
          │ 1:N                             │ 1:N
          ▼                                 ▼
┌─────────────────────┐           ┌─────────────────────┐
│  TeamMemberEntity   │           │ ProjectMemberEntity │
├─────────────────────┤           ├─────────────────────┤
│ id (PK)             │           │ id (PK)             │
│ teamId (FK)         │           │ projectId (FK)      │
│ userId (FK)         │           │ userId (FK)         │
│ role (OWNER/EDITOR/ │           │ role (OWNER/EDITOR/ │
│       VIEWER)       │           │       VIEWER)       │
│ [BaseEntity]        │           │ [BaseEntity]        │
└─────────────────────┘           └─────────────────────┘

                                  ┌─────────────────────────┐
                                  │ ProjectResourceRefEntity │
                                  ├─────────────────────────┤
                                  │ id (PK)                  │
                                  │ projectId (FK)           │
                                  │ resourceType (METRIC/    │
                                  │   DATASET/WORKFLOW/etc)  │
                                  │ resourceId               │
                                  │ permission (READ_EXECUTE)│
                                  │ addedBy                  │
                                  │ [BaseEntity]             │
                                  └─────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│                  External Resource Entities                    │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────┐  ┌─────────────────────┐            │
│  │ RangerTableEntity   │  │ BigQueryTableEntity │            │
│  ├─────────────────────┤  ├─────────────────────┤            │
│  │ id (PK)             │  │ id (PK)             │            │
│  │ tableName           │  │ projectId           │            │
│  │ database            │  │ datasetId           │            │
│  │ schema              │  │ tableId             │            │
│  │ owner               │  │ location            │            │
│  │ lastSyncAt          │  │ lastSyncAt          │            │
│  │ [BaseEntity]        │  │ [BaseEntity]        │            │
│  └─────────────────────┘  └─────────────────────┘            │
│                                                               │
│  ┌─────────────────────┐                                     │
│  │SupersetDashboardEntity│                                    │
│  ├─────────────────────┤                                     │
│  │ id (PK)             │                                     │
│  │ dashboardId         │                                     │
│  │ title               │                                     │
│  │ url                 │                                     │
│  │ owner               │                                     │
│  │ lastSyncAt          │                                     │
│  │ [BaseEntity]        │                                     │
│  └─────────────────────┘                                     │
│                                                               │
│  Note: These entities are populated by external Airflow       │
│  scheduler. Basecamp only provides read-only access.          │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 Role Definitions

#### Team Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| **OWNER** | Team administrator | Full access to team resources, can manage team settings and members |
| **EDITOR** | Resource contributor | Create, update, delete team resources (Metric, Dataset, etc.) |
| **VIEWER** | Read-only member | View and execute team resources, cannot modify |

#### Project Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| **OWNER** | Project administrator | Manage project settings, members, and SQL resources |
| **EDITOR** | Active contributor | Create/edit/delete SQL queries, execute referenced resources |
| **VIEWER** | Read-only member | View and execute resources, cannot create/modify SQL |

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
    OWNER,
    EDITOR,
    VIEWER
}

// NOTE: Due to soft delete, re-adding a removed member requires
// application-level handling: either un-delete the existing record
// or use hard delete for membership records.
// See SqlFolderService pattern for application-level validation.
```

#### ProjectEntity (Updated)

```kotlin
@Entity
@Table(
    name = "project",
    indexes = [
        Index(name = "idx_project_name", columnList = "name", unique = true),
        Index(name = "idx_project_deleted_at", columnList = "deleted_at"),
    ],
)
class ProjectEntity(
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

#### ProjectMemberEntity

```kotlin
@Entity
@Table(
    name = "project_member",
    indexes = [
        Index(name = "idx_project_member_project_id", columnList = "project_id"),
        Index(name = "idx_project_member_user_id", columnList = "user_id"),
        Index(name = "idx_project_member_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_project_member_project_user",
            columnNames = ["project_id", "user_id"]
        )
    ]
)
class ProjectMemberEntity(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: ProjectRole = ProjectRole.VIEWER,
) : BaseEntity()

// NOTE: Define in module-core-common/enums/ProjectEnums.kt
enum class ProjectRole {
    OWNER,
    EDITOR,
    VIEWER
}
```

#### ProjectResourceRefEntity

```kotlin
@Entity
@Table(
    name = "project_resource_ref",
    indexes = [
        Index(name = "idx_project_resource_ref_project_id", columnList = "project_id"),
        Index(name = "idx_project_resource_ref_resource", columnList = "resource_type, resource_id"),
        Index(name = "idx_project_resource_ref_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_project_resource_ref",
            columnNames = ["project_id", "resource_type", "resource_id"]
        )
    ]
)
class ProjectResourceRefEntity(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    val resourceType: ProjectResourceType,

    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    var permission: ResourcePermission = ResourcePermission.READ_EXECUTE,

    @Column(name = "added_by", nullable = false)
    val addedBy: Long,
) : BaseEntity()

// NOTE: Define in module-core-common/enums/ProjectEnums.kt
// (Renamed from ResourceType to avoid conflict with existing QualityEnums.ResourceType)
enum class ProjectResourceType {
    METRIC,
    DATASET,
    WORKFLOW,
    QUALITY,
    GITHUB_REPO,
    RANGER_TABLE,
    BIGQUERY_TABLE,
    SUPERSET_DASHBOARD
}

// NOTE: Define in module-core-common/enums/ProjectEnums.kt
enum class ResourcePermission {
    READ_ONLY,
    READ_EXECUTE
}
```

#### External Resource Entities

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

기존 리소스 Entity에 `teamId` FK 추가:

```kotlin
// MetricEntity, DatasetEntity, WorkflowEntity, QualitySpecEntity 등에 추가
@Column(name = "team_id", nullable = false)
val teamId: Long
```

---

## 3. API Specifications

### 3.1 Team API

| Method | Endpoint | Description | Admin Only |
|--------|----------|-------------|------------|
| GET | `/api/v1/teams` | List all teams | No |
| POST | `/api/v1/teams` | Create team | Yes |
| GET | `/api/v1/teams/{teamId}` | Get team details | No |
| PUT | `/api/v1/teams/{teamId}` | Update team | Yes |
| DELETE | `/api/v1/teams/{teamId}` | Delete team (soft) | Yes |
| GET | `/api/v1/teams/{teamId}/members` | List team members | No |
| POST | `/api/v1/teams/{teamId}/members` | Add member to team | Yes |
| PUT | `/api/v1/teams/{teamId}/members/{userId}` | Update member role | Yes |
| DELETE | `/api/v1/teams/{teamId}/members/{userId}` | Remove member | Yes |
| GET | `/api/v1/teams/{teamId}/resources` | List team resources | No |

### 3.2 Project API

| Method | Endpoint | Description | Admin Only |
|--------|----------|-------------|------------|
| GET | `/api/v1/projects` | List all projects | No |
| POST | `/api/v1/projects` | Create project | Yes |
| GET | `/api/v1/projects/{projectId}` | Get project details | No |
| PUT | `/api/v1/projects/{projectId}` | Update project | Yes |
| DELETE | `/api/v1/projects/{projectId}` | Delete project (soft) | Yes |
| GET | `/api/v1/projects/{projectId}/members` | List project members | No |
| POST | `/api/v1/projects/{projectId}/members` | Add member | Yes |
| PUT | `/api/v1/projects/{projectId}/members/{userId}` | Update member role | Yes |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | Remove member | Yes |
| GET | `/api/v1/projects/{projectId}/resources` | List referenced resources | No |
| POST | `/api/v1/projects/{projectId}/resources` | Add resource reference | Yes |
| DELETE | `/api/v1/projects/{projectId}/resources/{refId}` | Remove resource ref | Yes |

### 3.3 Context API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/context/me` | Get current user's teams and projects |
| PUT | `/api/v1/context/current` | Set current team/project context |
| GET | `/api/v1/context/current` | Get current context |

### 3.4 External Resource API (Read-Only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/external/ranger-tables` | List Ranger tables |
| GET | `/api/v1/external/ranger-tables/{id}` | Get Ranger table details |
| GET | `/api/v1/external/bigquery-tables` | List BigQuery tables |
| GET | `/api/v1/external/bigquery-tables/{id}` | Get BigQuery table details |
| GET | `/api/v1/external/superset-dashboards` | List Superset dashboards |
| GET | `/api/v1/external/superset-dashboards/{id}` | Get dashboard details |

---

## 4. API Details

### 4.1 Get User Context

**`GET /api/v1/context/me`**

Returns the current user's team and project memberships.

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
  "projects": [
    {
      "id": 10,
      "name": "marketing-analytics",
      "displayName": "Marketing Analytics",
      "role": "OWNER"
    },
    {
      "id": 11,
      "name": "recommendation-system",
      "displayName": "Recommendation System",
      "role": "EDITOR"
    }
  ],
  "currentContext": {
    "teamId": 1,
    "teamName": "data-engineering",
    "projectId": 10,
    "projectName": "marketing-analytics"
  }
}
```

### 4.2 Set Current Context

**`PUT /api/v1/context/current`**

```http
PUT /api/v1/context/current
Content-Type: application/json

{
  "teamId": 2,
  "projectId": 11
}
```

**Response:**
```json
{
  "teamId": 2,
  "teamName": "ml-infra",
  "projectId": 11,
  "projectName": "recommendation-system"
}
```

### 4.3 Create Team

**`POST /api/v1/teams`** (Admin Only)

```http
POST /api/v1/teams
Content-Type: application/json

{
  "name": "data-engineering",
  "displayName": "Data Engineering Team",
  "description": "Data pipeline and infrastructure team"
}
```

**Response:**
```json
{
  "id": 1,
  "name": "data-engineering",
  "displayName": "Data Engineering Team",
  "description": "Data pipeline and infrastructure team",
  "memberCount": 0,
  "createdAt": "2026-01-06T10:00:00Z"
}
```

### 4.4 Add Team Member

**`POST /api/v1/teams/{teamId}/members`** (Admin Only)

```http
POST /api/v1/teams/1/members
Content-Type: application/json

{
  "userId": 123,
  "role": "EDITOR"
}
```

### 4.5 List Team Resources

**`GET /api/v1/teams/{teamId}/resources`**

```http
GET /api/v1/teams/1/resources?type=METRIC&page=0&size=20
```

**Response:**
```json
{
  "resources": [
    {
      "type": "METRIC",
      "id": 101,
      "name": "daily_active_users",
      "description": "Daily active user count",
      "updatedAt": "2026-01-05T15:30:00Z"
    },
    {
      "type": "METRIC",
      "id": 102,
      "name": "revenue_daily",
      "description": "Daily revenue metric",
      "updatedAt": "2026-01-04T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

### 4.6 Add Project Resource Reference

**`POST /api/v1/projects/{projectId}/resources`** (Admin Only)

```http
POST /api/v1/projects/10/resources
Content-Type: application/json

{
  "resourceType": "METRIC",
  "resourceId": 101,
  "permission": "READ_EXECUTE"
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

// Project
data class CreateProjectRequest(
    @field:NotBlank @field:Size(max = 50)
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Name must be lowercase alphanumeric with hyphens")
    val name: String,
    @field:NotBlank @field:Size(max = 100)
    val displayName: String,
    @field:Size(max = 500)
    val description: String? = null,
)

data class UpdateProjectRequest(
    @field:Size(max = 100)
    val displayName: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
)

data class AddProjectMemberRequest(
    val userId: Long,
    val role: ProjectRole = ProjectRole.VIEWER,
)

data class UpdateProjectMemberRequest(
    val role: ProjectRole,
)

data class AddProjectResourceRequest(
    val resourceType: ProjectResourceType,
    val resourceId: Long,
    val permission: ResourcePermission = ResourcePermission.READ_EXECUTE,
)

// Context
data class SetContextRequest(
    val teamId: Long?,
    val projectId: Long?,
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
    val resourceCounts: Map<ProjectResourceType, Int>,
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

// Project
data class ProjectSummaryDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val createdAt: LocalDateTime,
)

data class ProjectDetailDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val sqlQueryCount: Int,
    val referencedResourceCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ProjectMemberDto(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: ProjectRole,
    val joinedAt: LocalDateTime,
)

data class ProjectResourceRefDto(
    val id: Long,
    val resourceType: ProjectResourceType,
    val resourceId: Long,
    val resourceName: String,
    val permission: ResourcePermission,
    val addedBy: String,
    val addedAt: LocalDateTime,
)

// Context
data class UserContextDto(
    val userId: Long,
    val email: String,
    val teams: List<UserTeamDto>,
    val projects: List<UserProjectDto>,
    val currentContext: CurrentContextDto?,
)

data class UserTeamDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val role: TeamRole,
)

data class UserProjectDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val role: ProjectRole,
)

data class CurrentContextDto(
    val teamId: Long?,
    val teamName: String?,
    val projectId: Long?,
    val projectName: String?,
)

// Resource
data class TeamResourceDto(
    val type: ProjectResourceType,
    val id: Long,
    val name: String,
    val description: String?,
    val updatedAt: LocalDateTime,
)
```

---

## 6. Permission Matrix

### 6.1 Team Permissions

| Action | OWNER | EDITOR | VIEWER | Admin |
|--------|-------|--------|--------|-------|
| View team info | ✓ | ✓ | ✓ | ✓ |
| View team members | ✓ | ✓ | ✓ | ✓ |
| Create resources | ✓ | ✓ | ✗ | ✓ |
| Update resources | ✓ | ✓ | ✗ | ✓ |
| Delete resources | ✓ | ✓ | ✗ | ✓ |
| Execute resources | ✓ | ✓ | ✓ | ✓ |
| Update team settings | ✓ | ✗ | ✗ | ✓ |
| Manage members | ✗ | ✗ | ✗ | ✓ |

### 6.2 Project Permissions

| Action | OWNER | EDITOR | VIEWER | Admin |
|--------|-------|--------|--------|-------|
| View project info | ✓ | ✓ | ✓ | ✓ |
| View project members | ✓ | ✓ | ✓ | ✓ |
| Create SQL queries | ✓ | ✓ | ✗ | ✓ |
| Update SQL queries | ✓ | ✓ | ✗ | ✓ |
| Delete SQL queries | ✓ | ✓ | ✗ | ✓ |
| Execute SQL queries | ✓ | ✓ | ✓ | ✓ |
| View referenced resources | ✓ | ✓ | ✓ | ✓ |
| Execute referenced resources | ✓ | ✓ | ✓ | ✓ |
| Update project settings | ✓ | ✗ | ✗ | ✓ |
| Manage members | ✗ | ✗ | ✗ | ✓ |
| Manage resource refs | ✗ | ✗ | ✗ | ✓ |

---

## 7. Module Placement

### 7.1 Location Overview

| Component | Module | Package |
|-----------|--------|---------|
| TeamEntity, TeamMemberEntity | module-core-domain | `entity/team/` |
| ProjectEntity, ProjectMemberEntity, ProjectResourceRefEntity | module-core-domain | `entity/project/` (shared with SQL) |
| External Entities (Ranger/BQ/Superset) | module-core-domain | `entity/external/` (read-only) |
| Repositories (interfaces) | module-core-domain | `repository/team/`, `repository/project/` |
| Repositories (impl) | module-core-infra | `repository/team/`, `repository/project/` |
| Services | module-core-domain | `service/` |
| Controllers | module-server-api | `controller/` |
| DTOs | module-server-api | `dto/team/`, `dto/project/` |
| TeamRole enum | module-core-common | `common/enums/TeamEnums.kt` |
| ProjectRole, ProjectResourceType, ResourcePermission enums | module-core-common | `common/enums/ProjectEnums.kt` |

> **Note on ProjectEntity:** ProjectEntity is shared between Team/Project management and SQL management features.
> It is placed in `entity/project/` and referenced by SQL feature. See SQL_FEATURE.md for details.

### 7.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    ├── TeamEnums.kt        # TeamRole
    └── ProjectEnums.kt     # ProjectRole, ProjectResourceType, ResourcePermission

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/
    │   ├── team/
    │   │   ├── TeamEntity.kt
    │   │   └── TeamMemberEntity.kt
    │   ├── project/
    │   │   ├── ProjectEntity.kt           # Updated
    │   │   ├── ProjectMemberEntity.kt
    │   │   └── ProjectResourceRefEntity.kt
    │   └── external/
    │       ├── RangerTableEntity.kt
    │       ├── BigQueryTableEntity.kt
    │       └── SupersetDashboardEntity.kt
    ├── repository/
    │   ├── team/
    │   │   ├── TeamRepositoryJpa.kt
    │   │   ├── TeamRepositoryDsl.kt
    │   │   ├── TeamMemberRepositoryJpa.kt
    │   │   └── TeamMemberRepositoryDsl.kt
    │   └── project/
    │       ├── ProjectRepositoryJpa.kt     # Updated
    │       ├── ProjectRepositoryDsl.kt     # Updated
    │       ├── ProjectMemberRepositoryJpa.kt
    │       ├── ProjectMemberRepositoryDsl.kt
    │       ├── ProjectResourceRefRepositoryJpa.kt
    │       └── ProjectResourceRefRepositoryDsl.kt
    └── service/
        ├── TeamService.kt
        ├── ProjectService.kt               # Updated
        └── ContextService.kt

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/repository/
    ├── team/
    │   ├── TeamRepositoryJpaImpl.kt
    │   ├── TeamRepositoryDslImpl.kt
    │   ├── TeamMemberRepositoryJpaImpl.kt
    │   └── TeamMemberRepositoryDslImpl.kt
    └── project/
        ├── ProjectRepositoryJpaImpl.kt
        ├── ProjectRepositoryDslImpl.kt
        ├── ProjectMemberRepositoryJpaImpl.kt
        ├── ProjectMemberRepositoryDslImpl.kt
        ├── ProjectResourceRefRepositoryJpaImpl.kt
        └── ProjectResourceRefRepositoryDslImpl.kt

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── controller/
    │   ├── TeamController.kt
    │   ├── ProjectController.kt           # Updated
    │   ├── ContextController.kt
    │   └── ExternalResourceController.kt
    └── dto/
        ├── team/
        │   └── TeamDtos.kt
        ├── project/
        │   └── ProjectDtos.kt
        └── context/
            └── ContextDtos.kt
```

---

## 8. Database Schema

### 8.1 Tables

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

-- Project table (updated with display_name)
CREATE TABLE project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_project_name (name),
    INDEX idx_project_deleted_at (deleted_at)
);

-- Project Member table
CREATE TABLE project_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_project_member_project_id (project_id),
    INDEX idx_project_member_user_id (user_id),
    INDEX idx_project_member_deleted_at (deleted_at),
    UNIQUE KEY uk_project_member_project_user (project_id, user_id),
    FOREIGN KEY (project_id) REFERENCES project(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Project Resource Reference table
CREATE TABLE project_resource_ref (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BIGINT NOT NULL,
    permission VARCHAR(20) NOT NULL DEFAULT 'READ_EXECUTE',
    added_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_project_resource_ref_project_id (project_id),
    INDEX idx_project_resource_ref_resource (resource_type, resource_id),
    INDEX idx_project_resource_ref_deleted_at (deleted_at),
    UNIQUE KEY uk_project_resource_ref (project_id, resource_type, resource_id),
    FOREIGN KEY (project_id) REFERENCES project(id)
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
    INDEX idx_ranger_table_database (database_name)
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
    INDEX idx_bigquery_table_dataset (dataset_id)
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
    INDEX idx_superset_dashboard_id (dashboard_id)
);
```

### 8.2 Existing Table Updates

```sql
-- Add team_id to existing resource tables
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

-- GitHub repo table (if exists)
ALTER TABLE github_repo ADD COLUMN team_id BIGINT NOT NULL;
ALTER TABLE github_repo ADD INDEX idx_github_repo_team_id (team_id);
ALTER TABLE github_repo ADD FOREIGN KEY (team_id) REFERENCES team(id);
```

---

## 9. Implementation Phases

### Phase 1: Core Team & Project (MVP)

| Component | Status | Description |
|-----------|--------|-------------|
| TeamEntity, TeamMemberEntity | Planned | Team CRUD, member management |
| ProjectEntity update | Planned | Add displayName, member support |
| ProjectMemberEntity | Planned | Project member management |
| Team/Project Controllers | Planned | REST APIs |
| Context API | Planned | Team+Project context switching |

### Phase 2: Resource Ownership

| Component | Status | Description |
|-----------|--------|-------------|
| Add teamId to existing entities | Planned | Metric, Dataset, Workflow, Quality |
| ProjectResourceRefEntity | Planned | Cross-team resource references |
| Permission enforcement | Planned | Role-based access control |

### Phase 3: External Resources

| Component | Status | Description |
|-----------|--------|-------------|
| RangerTableEntity | Planned | Ranger table metadata |
| BigQueryTableEntity | Planned | BigQuery table metadata |
| SupersetDashboardEntity | Planned | Superset dashboard metadata |
| External Resource API | Planned | Read-only listing |

### Phase 4: Enhanced Features (Future)

| Component | Status | Description |
|-----------|--------|-------------|
| Approval workflow | Future | Resource request/approval |
| Audit logging | Future | See AUDIT_FEATURE.md |
| Fine-grained permissions | Future | Per-resource permissions |

---

## 10. Success Criteria

### 10.1 Feature Completion

| Feature | Completion Condition |
|---------|----------------------|
| Team CRUD | All 10 endpoints working |
| Project CRUD | All 12 endpoints working |
| Context API | All 3 endpoints working |
| External Resources | All 6 endpoints working |
| Role enforcement | Permissions correctly applied |

### 10.2 Test Coverage

| Metric | Target |
|--------|--------|
| Unit test coverage | >= 80% |
| Controller tests | All endpoints |
| Service tests | All business logic |
| Permission tests | All role combinations |

---

## Appendix A: Industry Research

| Tool | Organization Model | Key Features |
|------|-------------------|--------------|
| **Tableau** | Sites → Projects → Workbooks | Role-based (Creator/Explorer/Viewer), project permissions |
| **Databricks** | Workspaces → Folders | Admin/User roles, ACL inheritance |
| **BigQuery** | Projects → Datasets | IAM integration, fine-grained permissions |
| **DBT Cloud** | Accounts → Projects → Environments | Developers/Read-only/Admin roles |

**Design Choices Applied:**
- Dual entity (Team + Project) from Tableau's flexible structure
- Owner/Editor/Viewer 3-tier roles from common industry patterns
- Resource references with explicit permissions from BigQuery IAM model
- Independent Team/Project relationship for maximum flexibility

---

## Appendix B: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Team role model | Owner/Editor/Viewer | Industry standard, sufficient granularity |
| 2 | Project role model | Owner/Editor/Viewer | Consistent with team roles |
| 3 | Team-Project relationship | Independent | Maximum flexibility, no ownership dependency |
| 4 | Resource ownership | Team-based | Clear responsibility, team accountability |
| 5 | Project resource access | Read+Execute via reference | Safe cross-team collaboration |
| 6 | SQL ownership | Project-based | 1-level folder per project |
| 7 | Context selection | Team + Project dual select | Explicit context, clear scope |
| 8 | External resources | Separate entities | Different schema per source |
| 9 | Admin operations | System admin only | Centralized control for security |
| 10 | Approval workflow | Phase 2 | MVP simplicity |

---

## Appendix C: SQL_FEATURE.md Updates Required

The following updates are needed in `SQL_FEATURE.md` to align with this specification:

1. **ProjectEntity**: Now shared between SQL and Team/Project features
2. **ProjectMemberEntity**: New entity for project membership
3. **Permission enforcement**: SQL operations must check ProjectRole
4. **Folder structure**: Confirmed as 1-level within project

See Section 2.3 for updated ProjectEntity definition.

---

## Appendix C: Repository Bean Naming Pattern

Repository implementations use the **Simplified Pattern** where `JpaImpl` directly extends both the domain interface and `JpaRepository`:

```kotlin
// ========================================
// Domain Interfaces (module-core-domain/repository/)
// ========================================

// module-core-domain/repository/team/TeamRepositoryJpa.kt
interface TeamRepositoryJpa {
    fun save(team: TeamEntity): TeamEntity
    fun findById(id: Long): TeamEntity?
    fun findByName(name: String): TeamEntity?
    fun deleteById(id: Long)
}

// module-core-domain/repository/team/TeamRepositoryDsl.kt
interface TeamRepositoryDsl {
    fun findByConditions(query: ListTeamsQuery): Page<TeamEntity>
}

// ========================================
// Infrastructure Implementations (module-core-infra/repository/)
// ========================================

// module-core-infra/repository/team/TeamRepositoryJpaImpl.kt
// Simplified Pattern: Interface extends both domain port and JpaRepository
@Repository("teamRepositoryJpa")
interface TeamRepositoryJpaImpl :
    TeamRepositoryJpa,
    JpaRepository<TeamEntity, Long> {

    override fun findByName(name: String): TeamEntity?
}

// module-core-infra/repository/team/TeamRepositoryDslImpl.kt
@Repository("teamRepositoryDsl")
class TeamRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TeamRepositoryDsl {
    override fun findByConditions(query: ListTeamsQuery): Page<TeamEntity> {
        // QueryDSL implementation
    }
}

// module-core-infra/repository/team/TeamMemberRepositoryJpaImpl.kt
@Repository("teamMemberRepositoryJpa")
interface TeamMemberRepositoryJpaImpl :
    TeamMemberRepositoryJpa,
    JpaRepository<TeamMemberEntity, Long>

// module-core-infra/repository/project/ProjectMemberRepositoryJpaImpl.kt
@Repository("projectMemberRepositoryJpa")
interface ProjectMemberRepositoryJpaImpl :
    ProjectMemberRepositoryJpa,
    JpaRepository<ProjectMemberEntity, Long>

// module-core-infra/repository/project/ProjectResourceRefRepositoryJpaImpl.kt
@Repository("projectResourceRefRepositoryJpa")
interface ProjectResourceRefRepositoryJpaImpl :
    ProjectResourceRefRepositoryJpa,
    JpaRepository<ProjectResourceRefEntity, Long>
```

### Naming Convention Summary

| Layer | Pattern | Example |
|-------|---------|---------|
| **module-core-domain** | `{Entity}RepositoryJpa` | `TeamRepositoryJpa` |
| **module-core-domain** | `{Entity}RepositoryDsl` | `TeamRepositoryDsl` |
| **module-core-infra** | `{Entity}RepositoryJpaImpl` | `TeamRepositoryJpaImpl` |
| **module-core-infra** | `{Entity}RepositoryDslImpl` | `TeamRepositoryDslImpl` |

> **Note:** No separate `SpringData` interface is used. The `JpaImpl` interface directly extends both the domain interface and `JpaRepository`.

---

## Appendix D: Review Notes (v1.0.1)

### Architectural Review Feedback Applied

| # | Issue | Resolution |
|---|-------|------------|
| 1 | `ResourceType` naming conflict | Renamed to `ProjectResourceType` |
| 2 | Enums defined inside entity files | Added notes to define in `module-core-common/enums/` |
| 3 | Missing soft delete indexes on external entities | Added `idx_*_deleted_at` indexes |
| 4 | Missing unique constraints on external entities | Added UK constraints for natural keys |
| 5 | Membership soft delete handling | Added note about application-level handling |
| 6 | Missing repository bean naming examples | Added Appendix C |
| 7 | PackageEntity location ambiguity | Clarified shared location in Module Placement |

---

**Last Updated:** 2026-01-06 (v1.0.1 - Applied architectural review feedback)
