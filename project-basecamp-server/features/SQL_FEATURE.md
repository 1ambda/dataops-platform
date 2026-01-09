# SQL (Saved Query) Management Feature Specification

> **Version:** 2.1.0 | **Status:** ✅ MVP Complete | **Priority:** P2 Medium
> **CLI Commands:** `dli sql list/get/create/update/delete` (planned) | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** 2026-01-09 | **Endpoints:** 14/14 Complete (MVP)
>
> **Data Source:** Self-managed JPA (SqlSnippet, SqlFolder, Project)
> **Entities:** `ProjectEntity`, `SqlFolderEntity`, `SqlSnippetEntity`
>
> **Implementation Details:** [`SQL_RELEASE.md`](./SQL_RELEASE.md)
> **CLI Specification:** [`project-interface-cli/features/SQL_FEATURE.md`](../../project-interface-cli/features/SQL_FEATURE.md)
> **Related Features:** [`PROJECT_FEATURE.md`](./PROJECT_FEATURE.md) - Team & Project Management (ProjectEntity shared)
>
> **MVP Changes (2026-01-09):**
> - Renamed `SavedQueryEntity` → `SqlSnippetEntity` for clarity
> - URL structure: `/api/v1/projects/{projectId}/sql/folders|snippets` (nested under projects)
> - Permission checking deferred to PROJECT_FEATURE (minimal MVP)
> - 14 endpoints implemented with 158+ tests

---

## 1. Overview

### 1.1 Purpose

The SQL (Saved Query) Management API provides endpoints for storing, organizing, and retrieving SQL queries within projects. Users can save frequently used queries, organize them in folders, and share them with project members for collaborative work.

**Target Users:**
- **Data Professionals (DS/DA/DAE/DE):** Save, organize, and manage SQL queries via CLI and UI
- **Non-Technical Users (Marketing, Operations):** Browse and execute shared queries via Basecamp UI

**Key Use Cases:**
- Save and organize SQL queries by project and folder
- Share queries with project members (via Project Membership model)
- Execute saved queries with parameter substitution
- Sync queries between server and local files via CLI

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Project-Based Organization** | Queries organized within projects (e.g., "Marketing Analytics", "Finance Reports") |
| **Flat Folder Structure** | 1-level folders within projects for simple organization |
| **Query CRUD** | Create, read, update, delete saved queries |
| **Search & Filter** | Find queries by name, content, project, folder, starred status |
| **Execution Integration** | Run saved queries (tracked in ExecutionHistoryEntity) |
| **Star/Favorite** | Mark frequently used queries |
| **CLI Sync** | Create/update/delete queries via `dli sql` commands |
| **Project Sharing** | Project members can view and execute queries (based on ProjectRole) |

### 1.3 CLI Integration

```bash
# List saved queries with filters
dli sql list
dli sql list --project marketing --starred
dli sql list --text "revenue"              # Search in name/content

# Get query details
dli sql get 43                              # Get by ID
dli sql get 43 --output ./insight.sql       # Download to file

# Create new query
dli sql create --name "Daily Revenue" --project marketing --file ./revenue.sql
dli sql create --name "User Stats" --project analytics --sql "SELECT * FROM users"

# Update existing query
dli sql update 43 --file ./updated.sql
dli sql update 43 --name "Weekly Revenue" --description "Updated description"

# Delete query
dli sql delete 43
dli sql delete 43 --force                   # Skip confirmation
```

### 1.4 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SQL Management API                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  SqlFolderController          SqlQueryController                     │
│  /api/v1/sql/folders          /api/v1/sql/queries                   │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  SqlFolderService             SqlQueryService                        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ProjectEntity ─────1:N───── SqlFolderEntity ─────1:N───── SavedQueryEntity │
│  (from PROJECT_FEATURE)      (flat, no hierarchy)                   │
│                                                                     │
│  ProjectMemberEntity ───── Role-based Access Control                │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.5 Permission Model

SQL query access follows the **Project Membership Model** from PROJECT_FEATURE.md:

| Role | View Queries | Execute Queries | Create/Edit/Delete |
|------|--------------|-----------------|-------------------|
| **OWNER** | ✓ | ✓ | ✓ |
| **EDITOR** | ✓ | ✓ | ✓ |
| **VIEWER** | ✓ | ✓ | ✗ |
| **Non-Member** | ✗ | ✗ | ✗ |

> **Sharing Workflow:** Admin adds users as Project members with appropriate roles.
> Project VIEWERs can browse and execute all queries in the project.

---

## 2. CLI Command Mapping

### 2.1 Command to API Mapping

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli sql list` | GET | `/api/v1/sql/queries` | List/search saved queries |
| `dli sql get <id>` | GET | `/api/v1/sql/queries/{queryId}` | Get query details |
| `dli sql create` | POST | `/api/v1/sql/queries` | Create new query |
| `dli sql update <id>` | PUT | `/api/v1/sql/queries/{queryId}` | Update query |
| `dli sql delete <id>` | DELETE | `/api/v1/sql/queries/{queryId}` | Delete query (soft) |

### 2.2 CLI Options to Query Parameters

| CLI Option | Query Parameter | Default | Description |
|------------|----------------|---------|-------------|
| `--project` | `projectName` | - | Filter by project name |
| `--folder` | `folderName` | - | Filter by folder name |
| `--starred` | `starred` | `false` | Show only starred queries |
| `--text` | `searchText` | - | Search in name, description, SQL content |
| `--limit` | `size` | `20` | Max results per page |
| `--offset` | `page` | `0` | Page number |

---

## 3. Data Model

### 3.1 Entity Relationship Diagram

```
┌─────────────────┐
│  ProjectEntity  │ (from PROJECT_FEATURE.md)
├─────────────────┤
│ id (PK)         │
│ name (unique)   │
│ displayName     │
│ description     │
│ [BaseEntity]    │
└────────┬────────┘
         │
         │ 1:N
         ▼
┌─────────────────────┐
│   SqlFolderEntity   │
├─────────────────────┤
│ id (PK)             │
│ projectId (FK)      │
│ name                │ ──► Unique per project
│ description         │
│ displayOrder        │
│ [BaseEntity]        │
└────────┬────────────┘
         │
         │ 1:N
         ▼
┌─────────────────────┐
│  SavedQueryEntity   │
├─────────────────────┤
│ id (PK)             │
│ folderId (FK)       │
│ name                │
│ description         │
│ sqlText             │
│ dialect             │
│ runCount            │
│ lastRunAt           │
│ isStarred           │
│ [BaseEntity]        │
└─────────────────────┘
```

### 3.2 Entity Definitions

#### SqlFolderEntity

```kotlin
@Entity
@Table(
    name = "sql_folder",
    indexes = [
        Index(name = "idx_sql_folder_project_id", columnList = "project_id"),
        Index(name = "idx_sql_folder_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_sql_folder_name_project",
            columnNames = ["name", "project_id"]
        )
    ]
)
class SqlFolderEntity(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,

    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @field:Size(max = 500)
    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : BaseEntity()
```

> **Design Decision:** Flat 1-level folder structure. No `parentFolderId` field.
> Folders are unique per project by (name, project_id).

#### SavedQueryEntity

```kotlin
@Entity
@Table(
    name = "saved_query",
    indexes = [
        Index(name = "idx_saved_query_folder_id", columnList = "folder_id"),
        Index(name = "idx_saved_query_name", columnList = "name"),
        Index(name = "idx_saved_query_starred", columnList = "is_starred"),
        Index(name = "idx_saved_query_deleted_at", columnList = "deleted_at"),
    ],
)
class SavedQueryEntity(
    @Column(name = "folder_id", nullable = false)
    val folderId: Long,

    @field:NotBlank
    @field:Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @field:Size(max = 1000)
    @Column(name = "description", length = 1000)
    var description: String? = null,

    @field:NotBlank
    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    var sqlText: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "dialect", nullable = false, length = 20)
    var dialect: SqlDialect = SqlDialect.BIGQUERY,

    @Column(name = "run_count", nullable = false)
    var runCount: Int = 0,

    @Column(name = "last_run_at")
    var lastRunAt: LocalDateTime? = null,

    @Column(name = "is_starred", nullable = false)
    var isStarred: Boolean = false,
) : BaseEntity()
```

#### ProjectEntity (Reference)

> **Note:** ProjectEntity is defined in [`PROJECT_FEATURE.md`](./PROJECT_FEATURE.md).
> SQL feature uses ProjectEntity for organizing queries and enforcing access control via ProjectMemberEntity.

```kotlin
// See PROJECT_FEATURE.md Section 2.3 for full definition
@Entity
@Table(name = "project")
class ProjectEntity(
    var name: String,           // Unique identifier (lowercase, hyphenated)
    var displayName: String,    // Human-readable name
    var description: String?,
) : BaseEntity()
```

### 3.3 Enum Definitions

```kotlin
// module-core-common/src/main/kotlin/com/dataops/basecamp/common/enums/SqlEnums.kt
package com.dataops.basecamp.common.enums

enum class SqlDialect {
    BIGQUERY,
    TRINO,
    SPARK,
    MYSQL,
    POSTGRESQL
}
```

> **Note:** All enums MUST be placed in `module-core-common/enums/` per PATTERNS.md.
> Import as: `import com.dataops.basecamp.common.enums.SqlDialect`

### 3.4 Design Decisions

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | **Organization** | Project → Folder → Query | Clear hierarchy, team-friendly |
| 2 | **Folder Depth** | Flat 1-level | Simple UX, avoid deep nesting complexity |
| 3 | **Folder Uniqueness** | Unique per project | Prevent duplicate folder names |
| 4 | **Query Uniqueness** | Not unique | Allow duplicate query names |
| 5 | **Folder Deletion** | Block if not empty | Explicit cleanup required |
| 6 | **Execution Tracking** | Unified ExecutionHistoryEntity | Consistent with Dataset/Quality runs |
| 7 | **Sharing Model** | Project Membership | Simple RBAC via ProjectRole |
| 8 | **FK Pattern** | FK-only (no @ManyToOne) | Follows project architecture pattern |
| 9 | **Tags** | Phase 2 (Common TagEntity) | Deferred for MVP simplicity |

---

## 4. API Specifications

### 4.1 Folder API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/sql/projects/{projectId}/folders` | List folders in project | Member |
| POST | `/api/v1/sql/projects/{projectId}/folders` | Create folder | Editor+ |
| GET | `/api/v1/sql/folders/{folderId}` | Get folder details | Member |
| PUT | `/api/v1/sql/folders/{folderId}` | Update folder | Editor+ |
| DELETE | `/api/v1/sql/folders/{folderId}` | Delete folder (block if not empty) | Editor+ |

### 4.2 Query API

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/sql/queries` | List/search queries (CLI primary) | Member |
| POST | `/api/v1/sql/queries` | Create query | Editor+ |
| GET | `/api/v1/sql/queries/{queryId}` | Get query details | Member |
| PUT | `/api/v1/sql/queries/{queryId}` | Update query | Editor+ |
| DELETE | `/api/v1/sql/queries/{queryId}` | Delete query (soft) | Editor+ |
| POST | `/api/v1/sql/queries/{queryId}/run` | Execute query | Member |
| POST | `/api/v1/sql/queries/{queryId}/star` | Toggle star status | Member |
| POST | `/api/v1/sql/queries/{queryId}/move` | Move to another folder | Editor+ |
| POST | `/api/v1/sql/queries/{queryId}/duplicate` | Duplicate query | Editor+ |

### 4.3 Permission Annotation Pattern

```kotlin
// SqlQueryController.kt - Permission enforcement examples

@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/sql/queries")
class SqlQueryController(
    private val sqlQueryService: SqlQueryService,
    private val projectMemberService: ProjectMemberService,
) {
    // Member role required (VIEWER, EDITOR, OWNER)
    @GetMapping
    @PreAuthorize("@projectMemberCheck.isMember(#projectName)")
    fun listQueries(
        @RequestParam projectName: String?,
        ...
    ): ResponseEntity<...> { ... }

    // Editor+ role required (EDITOR, OWNER)
    @PostMapping
    @PreAuthorize("@projectMemberCheck.hasRole(#request.folderId, {'OWNER', 'EDITOR'})")
    fun createQuery(
        @RequestBody request: CreateQueryRequest,
    ): ResponseEntity<...> { ... }

    // Query-level permission check (via folderId -> projectId)
    @PutMapping("/{queryId}")
    @PreAuthorize("@projectMemberCheck.canEditQuery(#queryId)")
    fun updateQuery(
        @PathVariable queryId: Long,
        @RequestBody request: UpdateQueryRequest,
    ): ResponseEntity<...> { ... }
}
```

> **Note:** `ProjectMemberCheck` is a Spring component that validates user's project role.
> See PROJECT_FEATURE.md for ProjectMemberService implementation details.

---

## 5. API Details

### 5.1 List Queries (CLI Primary Endpoint)

**`GET /api/v1/sql/queries`**

```http
GET /api/v1/sql/queries?projectName=marketing&searchText=revenue&starred=true&size=20&page=0
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `projectName` | string | No | Filter by project name |
| `folderName` | string | No | Filter by folder name |
| `searchText` | string | No | Search in name, description, SQL content |
| `starred` | boolean | No | Filter starred queries only |
| `dialect` | string | No | Filter by SQL dialect |
| `size` | int | No | Page size (default: 20, max: 100) |
| `page` | int | No | Page number (default: 0) |

**Response:**
```json
{
  "content": [
    {
      "id": 43,
      "name": "daily_revenue",
      "description": "Daily revenue by channel",
      "projectId": 1,
      "projectName": "marketing",
      "folderId": 5,
      "folderName": "Revenue Reports",
      "dialect": "BIGQUERY",
      "starred": true,
      "runCount": 127,
      "lastRunAt": "2026-01-06T10:30:00Z",
      "createdBy": "user@example.com",
      "createdAt": "2026-01-01T09:00:00Z",
      "updatedAt": "2026-01-06T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 5.2 Get Query Details

**`GET /api/v1/sql/queries/{queryId}`**

**Response:**
```json
{
  "id": 43,
  "name": "daily_revenue",
  "description": "Daily revenue by channel",
  "projectId": 1,
  "projectName": "marketing",
  "folderId": 5,
  "folderName": "Revenue Reports",
  "sqlText": "SELECT channel, SUM(revenue) as total\nFROM sales\nWHERE date = '{{ date }}'\nGROUP BY channel",
  "dialect": "BIGQUERY",
  "starred": true,
  "runCount": 127,
  "lastRunAt": "2026-01-06T10:30:00Z",
  "createdBy": "user@example.com",
  "createdAt": "2026-01-01T09:00:00Z",
  "updatedBy": "admin@example.com",
  "updatedAt": "2026-01-06T10:30:00Z"
}
```

### 5.3 Create Query

**`POST /api/v1/sql/queries`**

```http
POST /api/v1/sql/queries
Content-Type: application/json

{
  "folderId": 5,
  "name": "daily_revenue",
  "description": "Daily revenue by channel",
  "sqlText": "SELECT channel, SUM(revenue) as total\nFROM sales\nWHERE date = '{{ date }}'\nGROUP BY channel",
  "dialect": "BIGQUERY"
}
```

**Response:** `201 Created`
```json
{
  "id": 43,
  "name": "daily_revenue",
  "folderId": 5,
  "createdAt": "2026-01-06T14:20:00Z"
}
```

### 5.4 Update Query

**`PUT /api/v1/sql/queries/{queryId}`**

```http
PUT /api/v1/sql/queries/43
Content-Type: application/json

{
  "name": "daily_revenue_v2",
  "description": "Updated daily revenue query",
  "sqlText": "SELECT channel, SUM(revenue) as total\nFROM sales\nWHERE date = '{{ date }}'\nGROUP BY channel\nORDER BY total DESC"
}
```

**Response:** `200 OK`
```json
{
  "id": 43,
  "name": "daily_revenue_v2",
  "updatedAt": "2026-01-06T14:25:00Z"
}
```

### 5.5 Delete Query

**`DELETE /api/v1/sql/queries/{queryId}`**

**Response:** `204 No Content`

### 5.6 Execute Query

**`POST /api/v1/sql/queries/{queryId}/run`**

```http
POST /api/v1/sql/queries/43/run
Content-Type: application/json

{
  "parameters": {
    "date": "2026-01-06"
  },
  "limit": 1000
}
```

**Response:** `202 Accepted`
```json
{
  "executionId": "exec_abc123",
  "queryId": 43,
  "status": "RUNNING",
  "startedAt": "2026-01-06T14:30:00Z"
}
```

> **Note:** Execution is tracked in `ExecutionHistoryEntity` (unified with Dataset/Quality runs).
> Poll `/api/v1/executions/{executionId}` for status updates.

### 5.7 Delete Folder

**`DELETE /api/v1/sql/folders/{folderId}`**

Returns `204 No Content` on success.

Returns `400 Bad Request` if folder contains queries:
```json
{
  "error": "FOLDER_NOT_EMPTY",
  "message": "Cannot delete folder: contains 3 queries",
  "details": {
    "queryCount": 3
  }
}
```

---

## 6. DTO Definitions

### 6.1 Command DTOs (Request)

```kotlin
// module-core-domain/command/sql/SqlCommands.kt

data class CreateFolderCommand(
    val projectId: Long,
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    val displayOrder: Int? = null,
)

data class UpdateFolderCommand(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
    val displayOrder: Int? = null,
)

data class CreateQueryCommand(
    val folderId: Long,
    @field:NotBlank @field:Size(max = 200)
    val name: String,
    @field:Size(max = 1000)
    val description: String? = null,
    @field:NotBlank
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

data class UpdateQueryCommand(
    @field:Size(max = 200)
    val name: String? = null,
    @field:Size(max = 1000)
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

data class MoveQueryCommand(
    val targetFolderId: Long,
)

data class RunQueryCommand(
    val parameters: Map<String, String>? = null,
    val limit: Int? = 1000,
)

data class ListQueriesQuery(
    val projectName: String? = null,
    val folderName: String? = null,
    val searchText: String? = null,
    val starred: Boolean? = null,
    val dialect: SqlDialect? = null,
    val page: Int = 0,
    val size: Int = 20,
)
```

### 6.2 Projection DTOs (Response)

```kotlin
// module-core-domain/projection/sql/SqlProjections.kt

data class FolderSummaryProjection(
    val id: Long,
    val projectId: Long,
    val name: String,
    val description: String?,
    val displayOrder: Int,
    val queryCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class QuerySummaryProjection(
    val id: Long,
    val name: String,
    val description: String?,
    val projectId: Long,
    val projectName: String,
    val folderId: Long,
    val folderName: String,
    val dialect: SqlDialect,
    val starred: Boolean,
    val runCount: Int,
    val lastRunAt: LocalDateTime?,
    val createdBy: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class QueryDetailProjection(
    val id: Long,
    val name: String,
    val description: String?,
    val projectId: Long,
    val projectName: String,
    val folderId: Long,
    val folderName: String,
    val sqlText: String,
    val dialect: SqlDialect,
    val starred: Boolean,
    val runCount: Int,
    val lastRunAt: LocalDateTime?,
    val createdBy: String?,
    val createdAt: LocalDateTime,
    val updatedBy: String?,
    val updatedAt: LocalDateTime,
)

data class QueryExecutionProjection(
    val executionId: String,
    val queryId: Long,
    val status: ExecutionStatus,
    val startedAt: LocalDateTime,
)
```

### 6.3 API DTOs

```kotlin
// module-server-api/dto/sql/SqlDtos.kt

// Request DTOs
data class CreateFolderRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    val displayOrder: Int? = null,
)

data class UpdateFolderRequest(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
    val displayOrder: Int? = null,
)

data class CreateQueryRequest(
    val folderId: Long,
    @field:NotBlank @field:Size(max = 200)
    val name: String,
    @field:Size(max = 1000)
    val description: String? = null,
    @field:NotBlank
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

data class UpdateQueryRequest(
    @field:Size(max = 200)
    val name: String? = null,
    @field:Size(max = 1000)
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

data class MoveQueryRequest(
    val targetFolderId: Long,
)

data class RunQueryRequest(
    val parameters: Map<String, String>? = null,
    val limit: Int? = 1000,
)

// Response DTOs
data class FolderResponse(
    val id: Long,
    val projectId: Long,
    val name: String,
    val description: String?,
    val displayOrder: Int,
    val queryCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class QueryListResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val projectId: Long,
    val projectName: String,
    val folderId: Long,
    val folderName: String,
    val dialect: SqlDialect,
    val starred: Boolean,
    val runCount: Int,
    val lastRunAt: LocalDateTime?,
    val createdBy: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class QueryDetailResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val projectId: Long,
    val projectName: String,
    val folderId: Long,
    val folderName: String,
    val sqlText: String,
    val dialect: SqlDialect,
    val starred: Boolean,
    val runCount: Int,
    val lastRunAt: LocalDateTime?,
    val createdBy: String?,
    val createdAt: LocalDateTime,
    val updatedBy: String?,
    val updatedAt: LocalDateTime,
)

data class QueryCreatedResponse(
    val id: Long,
    val name: String,
    val folderId: Long,
    val createdAt: LocalDateTime,
)

data class QueryUpdatedResponse(
    val id: Long,
    val name: String,
    val updatedAt: LocalDateTime,
)

data class QueryExecutionResponse(
    val executionId: String,
    val queryId: Long,
    val status: ExecutionStatus,  // Use enum for type safety
    val startedAt: LocalDateTime,
)
```

---

## 7. Module Placement

### 7.1 Location Overview

| Component | Module | Package |
|-----------|--------|---------|
| Entities | module-core-domain | `entity/sql/` |
| Commands | module-core-domain | `command/sql/` |
| Projections | module-core-domain | `projection/sql/` |
| Repositories (interfaces) | module-core-domain | `repository/sql/` |
| Repositories (impl) | module-core-infra | `repository/sql/` |
| Services | module-core-domain | `service/` |
| Controllers | module-server-api | `controller/` |
| DTOs | module-server-api | `dto/sql/` |
| Mappers | module-server-api | `mapper/sql/` |
| Enums | module-core-common | `enums/SqlEnums.kt` |

### 7.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    └── SqlEnums.kt                    # SqlDialect

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/sql/
    │   ├── SqlFolderEntity.kt
    │   └── SavedQueryEntity.kt
    ├── command/sql/
    │   └── SqlCommands.kt             # All command/query classes
    ├── projection/sql/
    │   └── SqlProjections.kt          # All projection classes
    ├── repository/sql/
    │   ├── SqlFolderRepositoryJpa.kt
    │   ├── SqlFolderRepositoryDsl.kt
    │   ├── SavedQueryRepositoryJpa.kt
    │   └── SavedQueryRepositoryDsl.kt
    └── service/
        ├── SqlFolderService.kt
        └── SqlQueryService.kt

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/repository/sql/
    ├── SqlFolderRepositoryJpaImpl.kt
    ├── SqlFolderRepositoryDslImpl.kt
    ├── SavedQueryRepositoryJpaImpl.kt
    └── SavedQueryRepositoryDslImpl.kt

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── controller/
    │   ├── SqlFolderController.kt
    │   └── SqlQueryController.kt
    ├── dto/sql/
    │   └── SqlDtos.kt                 # All request/response DTOs
    └── mapper/sql/
        ├── SqlFolderMapper.kt         # Request -> Command, Projection -> Response
        └── SqlQueryMapper.kt          # Request -> Command, Projection -> Response
```

---

## 8. Database Schema

### 8.1 Tables

```sql
-- SQL Folder table (flat structure, no parent_folder_id)
CREATE TABLE sql_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_sql_folder_project_id (project_id),
    INDEX idx_sql_folder_deleted_at (deleted_at),
    UNIQUE KEY uk_sql_folder_name_project (name, project_id),
    FOREIGN KEY (project_id) REFERENCES project(id)
);

-- Saved Query table
CREATE TABLE saved_query (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    folder_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    sql_text TEXT NOT NULL,
    dialect VARCHAR(20) NOT NULL DEFAULT 'BIGQUERY',
    run_count INT NOT NULL DEFAULT 0,
    last_run_at TIMESTAMP,
    is_starred BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_saved_query_folder_id (folder_id),
    INDEX idx_saved_query_name (name),
    INDEX idx_saved_query_starred (is_starred),
    INDEX idx_saved_query_deleted_at (deleted_at),
    FOREIGN KEY (folder_id) REFERENCES sql_folder(id)
);
```

---

## 9. Implementation Phases

### Phase 1: MVP (Core Functionality)

| Component | Status | Description |
|-----------|--------|-------------|
| SqlFolderEntity | Planned | Flat folder structure |
| SavedQueryEntity | Planned | Query storage |
| Folder CRUD API | Planned | 5 endpoints |
| Query CRUD API | Planned | 9 endpoints |
| CLI commands | Planned | list/get/create/update/delete |
| Project permission check | Planned | Role-based access via ProjectMemberEntity |

### Phase 2: Enhanced Features

| Component | Status | Description |
|-----------|--------|-------------|
| **Common TagEntity** | Planned | Shared tag system for SQL/Dataset/Quality |
| Full-text search | Planned | Optimized SQL content search |
| Query versioning | Future | Track query changes |
| Import/Export | Future | Bulk query migration |

### Phase 2: Tag Entity Design (Preview)

> Tags will be implemented as a **common entity** shared across SQL queries, Datasets, and Quality specs.

```kotlin
// module-core-domain/entity/common/TagEntity.kt
@Entity
@Table(
    name = "tag",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tag_name", columnNames = ["name"])
    ]
)
class TagEntity(
    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    var name: String,

    @field:Size(max = 200)
    @Column(name = "description", length = 200)
    var description: String? = null,

    @Column(name = "color", length = 7)
    var color: String? = null,  // Hex color, e.g., "#FF5733"
) : BaseEntity()

// module-core-domain/entity/common/ResourceTagEntity.kt
@Entity
@Table(
    name = "resource_tag",
    indexes = [
        Index(name = "idx_resource_tag_resource", columnList = "resource_type, resource_id"),
        Index(name = "idx_resource_tag_tag_id", columnList = "tag_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_resource_tag",
            columnNames = ["resource_type", "resource_id", "tag_id"]
        )
    ]
)
class ResourceTagEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    val resourceType: TaggableResourceType,  // SAVED_QUERY, DATASET, QUALITY_SPEC

    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,

    @Column(name = "tag_id", nullable = false)
    val tagId: Long,
) : BaseEntity()

// module-core-common/enums/TagEnums.kt
enum class TaggableResourceType {
    SAVED_QUERY,
    DATASET,
    QUALITY_SPEC,
    METRIC,
    WORKFLOW
}
```

---

## 10. Success Criteria

### 10.1 Feature Completion

| Feature | Completion Condition |
|---------|----------------------|
| Folder CRUD | All 5 endpoints working |
| Query CRUD | All 9 endpoints working |
| CLI integration | `dli sql list/get/create/update/delete` working |
| Permission check | Project role enforcement working |
| Folder constraints | Unique names per project, deletion blocking |
| Execution tracking | Runs recorded in ExecutionHistoryEntity |

### 10.2 Test Coverage

| Metric | Target |
|--------|--------|
| Unit test coverage | >= 80% |
| Controller tests | All endpoints |
| Service tests | All business logic |
| Repository tests | Complex queries |
| Permission tests | All role combinations |

---

## Appendix A: Industry Research

| Tool | Organization | Key Features |
|------|--------------|--------------|
| **Databricks SQL Editor** | Folder-based | Folders, sharing, scheduling |
| **BigQuery Saved Queries** | Project-based | Labels, project scope |
| **Redash** | Dashboard-based | Versioning, favorites |
| **Metabase** | Collection-based | Collections, permissions |
| **PopSQL** | Team-based | Real-time collaboration, sharing |

**Design Choices Applied:**
- Flat folder structure (simpler than Databricks' nested)
- Project-based organization (from BigQuery)
- Starred/favorites (from Redash)
- Team sharing via membership (from PopSQL)

---

## Appendix B: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Sharing model | Project Membership | Simple, consistent with PROJECT_FEATURE.md |
| 2 | Folder depth | Flat 1-level | Simpler UX, easier navigation |
| 3 | Folder uniqueness | Unique per project | Prevent confusion |
| 4 | Query uniqueness | Not unique | Allow duplicate names |
| 5 | Folder deletion | Block if not empty | Explicit cleanup required |
| 6 | Execution tracking | Unified ExecutionHistoryEntity | Consistent with Dataset/Quality |
| 7 | CLI scope | list/get/create/update/delete | Full CRUD, search via list --text |
| 8 | Dry-run | Not supported | Simplicity, always execute |
| 9 | Tags | Phase 2 (Common TagEntity) | Shared across resources |
| 10 | FK pattern | No @ManyToOne | Follows project architecture |

---

## Appendix C: Repository Implementation Pattern

```kotlin
// ========================================
// Domain Interfaces (module-core-domain/repository/sql/)
// ========================================

// SqlFolderRepositoryJpa.kt
interface SqlFolderRepositoryJpa {
    fun save(folder: SqlFolderEntity): SqlFolderEntity
    fun findById(id: Long): SqlFolderEntity?
    fun findByProjectIdAndName(projectId: Long, name: String): SqlFolderEntity?
    fun deleteById(id: Long)
}

// SqlFolderRepositoryDsl.kt
interface SqlFolderRepositoryDsl {
    fun findByProjectId(projectId: Long): List<FolderSummaryProjection>
    fun countQueriesByFolderId(folderId: Long): Int
}

// SavedQueryRepositoryJpa.kt
interface SavedQueryRepositoryJpa {
    fun save(query: SavedQueryEntity): SavedQueryEntity
    fun findById(id: Long): SavedQueryEntity?
    fun deleteById(id: Long)
}

// SavedQueryRepositoryDsl.kt
interface SavedQueryRepositoryDsl {
    fun findByConditions(query: ListQueriesQuery): Page<QuerySummaryProjection>
    fun findDetailById(id: Long): QueryDetailProjection?
    fun countByFolderId(folderId: Long): Int
}

// ========================================
// Infrastructure Implementations (module-core-infra/repository/sql/)
// ========================================

// SqlFolderRepositoryJpaImpl.kt
// Note: Spring Data auto-generates query methods - no override needed
@Repository("sqlFolderRepositoryJpa")
interface SqlFolderRepositoryJpaImpl :
    SqlFolderRepositoryJpa,
    JpaRepository<SqlFolderEntity, Long> {

    // Spring Data auto-implements: findByProjectIdAndName(projectId, name)
}

// SavedQueryRepositoryJpaImpl.kt
@Repository("savedQueryRepositoryJpa")
interface SavedQueryRepositoryJpaImpl :
    SavedQueryRepositoryJpa,
    JpaRepository<SavedQueryEntity, Long>

// SqlFolderRepositoryDslImpl.kt
@Repository("sqlFolderRepositoryDsl")
class SqlFolderRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlFolderRepositoryDsl {
    // QueryDSL implementation
}

// SavedQueryRepositoryDslImpl.kt
@Repository("savedQueryRepositoryDsl")
class SavedQueryRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SavedQueryRepositoryDsl {
    // QueryDSL implementation with search and pagination
}
```

---

## Appendix D: Migration from v1.x

### Breaking Changes from v1.1.0

| Change | v1.1.0 | v2.0.0 | Migration |
|--------|--------|--------|-----------|
| Folder hierarchy | `parentFolderId` (nested) | Flat (no parent) | Remove parent references, flatten structure |
| CLI `put` command | `dli sql put` | `dli sql update` | Update CLI scripts |
| Search endpoint | Separate `/search` | `list --text` | Use list with searchText param |
| Dry-run | Supported | Removed | Remove dryRun parameter usage |

### Database Migration

```sql
-- Remove parentFolderId column (if exists)
ALTER TABLE sql_folder DROP COLUMN parent_folder_id;

-- Update unique constraint
ALTER TABLE sql_folder DROP INDEX uk_sql_folder_name_parent_project;
ALTER TABLE sql_folder ADD UNIQUE KEY uk_sql_folder_name_project (name, project_id);
```

---

**Last Updated:** 2026-01-07 (v2.0.1 - Applied architectural review feedback)

---

## Appendix E: Architectural Review Notes (v2.0.1)

### Review Feedback Applied

| # | Issue | Resolution |
|---|-------|------------|
| C1 | SqlDialect enum location | Added full package declaration and import note |
| C2 | Repository JpaImpl override keyword | Removed override, added Spring Data auto-generation note |
| I3 | QueryExecutionResponse status type | Changed from `String` to `ExecutionStatus` enum |
| I4 | Missing permission annotations | Added Section 4.3 with controller annotation examples |
| M2 | Missing mapper classes | Added `mapper/sql/` to file structure and module overview |

### Approved Patterns (from review)

- ✅ FK-only entity pattern (projectId: Long, folderId: Long)
- ✅ Soft delete index on both entities
- ✅ Unique constraint naming (`uk_sql_folder_name_project`)
- ✅ Repository Jpa/Dsl naming convention
- ✅ DTO organization (Commands, Projections, API DTOs)
- ✅ Project permission model integration
- ✅ Phase 2 Tag Entity preview design
