# SQL (Saved Worksheet) Management Feature Specification

> **Version:** 3.3.0 | **Status:** Team Migration | **Priority:** P2 Medium
> **CLI Commands:** `dli sql list/get/create/update/delete` (planned) | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** 2026-01-10 | **Endpoints:** 9/9 Complete (MVP)
>
> **Data Source:** Self-managed JPA (SqlWorksheet, WorksheetFolder, Team)
> **Entities:** `TeamEntity`, `WorksheetFolderEntity`, `SqlWorksheetEntity`
>
> **Implementation Details:** [`SQL_RELEASE.md`](./SQL_RELEASE.md)
> **CLI Specification:** [`project-interface-cli/features/SQL_FEATURE.md`](../../project-interface-cli/features/SQL_FEATURE.md)
> **Related Features:** [`TEAM_FEATURE.md`](./TEAM_FEATURE.md) - Team Management (TeamEntity shared)
>
> **v3.3.0 Changes (2026-01-10):**
> - Controller renamed: `TeamSqlController` → `TeamController`
> - Entity renamed: `SqlFolderEntity` → `WorksheetFolderEntity`
> - Controller file: `TeamSqlController.kt` → `TeamController.kt`
> - API paths remain unchanged: `/api/v1/teams/{teamId}/sql/worksheets` and `/api/v1/teams/{teamId}/sql/folders`
>
> **v3.2.0 Changes (2026-01-10):**
> - Terminology unified: "Snippet" → "Worksheet" (industry-standard naming like Snowflake, Databricks)
> - Entity: SqlSnippetEntity → SqlWorksheetEntity (documentation only, implementation separate)
> - API paths: `/api/v1/teams/{teamId}/sql/snippets` → `/api/v1/teams/{teamId}/sql/worksheets`
>
> **v3.1.0 Changes (2026-01-10):**
> - Terminology unified: "Query" → "Snippet" throughout documentation
> - API paths aligned with implementation: `/api/v1/teams/{teamId}/sql/snippets`
>
> **v3.0.0 Changes (2026-01-10):**
> - Migrated from Project-based to Team-based organization
> - SqlFolder.projectId → SqlFolder.teamId
> - Permission model: Project Membership → Team Membership
> - Role hierarchy: OWNER/EDITOR/VIEWER → MANAGER/EDITOR/VIEWER
> - URL structure: `/api/v1/teams/{teamId}/sql/folders|worksheets`

---

## 1. Overview

### 1.1 Purpose

The SQL (Saved Worksheet) Management API provides endpoints for storing, organizing, and retrieving SQL worksheets within teams. Users can save frequently used queries, organize them in folders, and share them with team members for collaborative work.

**Target Users:**
- **Data Professionals (DS/DA/DAE/DE):** Save, organize, and manage SQL worksheets via CLI and UI
- **Non-Technical Users (Marketing, Operations):** Browse and execute shared worksheets via Basecamp UI

**Key Use Cases:**
- Save and organize SQL worksheets by team and folder
- Share worksheets with team members (via Team Membership model)
- Execute saved worksheets with parameter substitution
- Sync worksheets between server and local files via CLI

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Team-Based Organization** | Worksheets organized within teams (e.g., "Marketing Analytics Team", "Finance Team") |
| **Flat Folder Structure** | 1-level folders within teams for simple organization |
| **Worksheet CRUD** | Create, read, update, delete saved worksheets |
| **Search & Filter** | Find worksheets by name, content, team, folder, starred status |
| **Execution Integration** | Run saved worksheets (tracked in ExecutionHistoryEntity) |
| **Star/Favorite** | Mark frequently used worksheets |
| **CLI Sync** | Create/update/delete worksheets via `dli sql` commands |
| **Team Sharing** | Team members can view and execute worksheets (based on TeamRole) |

### 1.3 CLI Integration

```bash
# List saved worksheets with filters
dli sql list
dli sql list --team marketing --starred
dli sql list --text "revenue"              # Search in name/content

# Get worksheet details
dli sql get 43                              # Get by ID
dli sql get 43 --output ./insight.sql       # Download to file

# Create new worksheet
dli sql create --name "Daily Revenue" --team marketing --file ./revenue.sql
dli sql create --name "User Stats" --team analytics --sql "SELECT * FROM users"

# Update existing worksheet
dli sql update 43 --file ./updated.sql
dli sql update 43 --name "Weekly Revenue" --description "Updated description"

# Delete worksheet
dli sql delete 43
dli sql delete 43 --force                   # Skip confirmation
```

### 1.4 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SQL Management API                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  TeamController (v3.3.0 - renamed from SqlController)               │
│  /api/v1/teams/{teamId}/sql/folders                                 │
│  /api/v1/teams/{teamId}/sql/worksheets                              │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  WorksheetFolderService         SqlWorksheetService                 │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  TeamEntity ─────1:N───── WorksheetFolderEntity ─────1:N───── SqlWorksheetEntity │
│  (from TEAM_FEATURE)      (flat, no hierarchy)                      │
│                                                                     │
│  TeamMemberEntity ───── Role-based Access Control                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.5 Permission Model

SQL worksheet access follows the **Team Membership Model** from TEAM_FEATURE.md:

| Role | View Worksheets | Execute Worksheets | Create/Edit/Delete |
|------|---------------|------------------|-------------------|
| **MANAGER** | Yes | Yes | Yes |
| **EDITOR** | Yes | Yes | Yes |
| **VIEWER** | Yes | Yes | No |
| **Non-Member** | No | No | No |

> **Sharing Workflow:** Team Manager adds users as Team members with appropriate roles.
> Team VIEWERs can browse and execute all worksheets in the team.

---

## 2. CLI Command Mapping

### 2.1 Command to API Mapping

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli sql list` | GET | `/api/v1/teams/{teamId}/sql/worksheets` | List/search saved worksheets |
| `dli sql get <id>` | GET | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Get worksheet details |
| `dli sql create` | POST | `/api/v1/teams/{teamId}/sql/worksheets` | Create new worksheet |
| `dli sql update <id>` | PUT | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Update worksheet |
| `dli sql delete <id>` | DELETE | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Delete worksheet (soft) |

### 2.2 CLI Options to Query Parameters

| CLI Option | Query Parameter | Default | Description |
|------------|----------------|---------|-------------|
| `--team` | Path param `teamId` | - | Team ID (resolved from name) |
| `--folder` | `folderName` | - | Filter by folder name |
| `--starred` | `starred` | `false` | Show only starred worksheets |
| `--text` | `searchText` | - | Search in name, description, SQL content |
| `--limit` | `size` | `20` | Max results per page |
| `--offset` | `page` | `0` | Page number |

---

## 3. Data Model

### 3.1 Entity Relationship Diagram

```
┌─────────────────┐
│   TeamEntity    │ (from TEAM_FEATURE.md)
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
┌──────────────────────────┐
│  WorksheetFolderEntity   │ (v3.3.0 - renamed from SqlFolderEntity)
├──────────────────────────┤
│ id (PK)                  │
│ teamId (FK)              │
│ name                     │ ──► Unique per team
│ description              │
│ displayOrder             │
│ [BaseEntity]             │
└────────┬─────────────────┘
         │
         │ 1:N
         ▼
┌─────────────────────┐
│  SqlWorksheetEntity │
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

#### WorksheetFolderEntity (v3.3.0 - renamed from SqlFolderEntity)

```kotlin
@Entity
@Table(
    name = "worksheet_folder",
    indexes = [
        Index(name = "idx_worksheet_folder_team_id", columnList = "team_id"),
        Index(name = "idx_worksheet_folder_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_worksheet_folder_name_team",
            columnNames = ["name", "team_id"]
        )
    ]
)
class WorksheetFolderEntity(
    @Column(name = "team_id", nullable = false)
    val teamId: Long,

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
> Folders are unique per team by (name, team_id).

#### SqlWorksheetEntity

```kotlin
@Entity
@Table(
    name = "sql_worksheet",
    indexes = [
        Index(name = "idx_sql_worksheet_folder_id", columnList = "folder_id"),
        Index(name = "idx_sql_worksheet_name", columnList = "name"),
        Index(name = "idx_sql_worksheet_starred", columnList = "is_starred"),
        Index(name = "idx_sql_worksheet_deleted_at", columnList = "deleted_at"),
    ],
)
class SqlWorksheetEntity(
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

#### TeamEntity (Reference)

> **Note:** TeamEntity is defined in [`TEAM_FEATURE.md`](./TEAM_FEATURE.md).
> SQL feature uses TeamEntity for organizing worksheets and enforcing access control via TeamMemberEntity.

```kotlin
// See TEAM_FEATURE.md Section 2.1 for full definition
@Entity
@Table(name = "team")
class TeamEntity(
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
| 1 | **Organization** | Team → Folder → Worksheet | Clear hierarchy, team-friendly |
| 2 | **Folder Depth** | Flat 1-level | Simple UX, avoid deep nesting complexity |
| 3 | **Folder Uniqueness** | Unique per team | Prevent duplicate folder names |
| 4 | **Worksheet Uniqueness** | Not unique | Allow duplicate worksheet names |
| 5 | **Folder Deletion** | Block if not empty | Explicit cleanup required |
| 6 | **Execution Tracking** | Unified ExecutionHistoryEntity | Consistent with Dataset/Quality runs |
| 7 | **Sharing Model** | Team Membership | Simple RBAC via TeamRole |
| 8 | **FK Pattern** | FK-only (no @ManyToOne) | Follows project architecture pattern |
| 9 | **Tags** | Phase 2 (Common TagEntity) | Deferred for MVP simplicity |

---

## 4. API Specifications

### 4.1 Folder API (4 endpoints - MVP Complete)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/teams/{teamId}/sql/folders` | List folders in team | Member |
| POST | `/api/v1/teams/{teamId}/sql/folders` | Create folder | Editor+ |
| GET | `/api/v1/teams/{teamId}/sql/folders/{folderId}` | Get folder details | Member |
| DELETE | `/api/v1/teams/{teamId}/sql/folders/{folderId}` | Delete folder (block if not empty) | Editor+ |

### 4.2 Worksheet API (5 endpoints - MVP Complete)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/teams/{teamId}/sql/worksheets` | List/search worksheets | Member |
| POST | `/api/v1/teams/{teamId}/sql/worksheets` | Create worksheet | Editor+ |
| GET | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Get worksheet details | Member |
| PUT | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Update worksheet | Editor+ |
| DELETE | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Delete worksheet (soft) | Editor+ |

### 4.3 Deferred API (Phase 2)

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| PUT | `/api/v1/teams/{teamId}/sql/folders/{folderId}` | Update folder | Deferred |
| POST | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}/run` | Execute worksheet | Deferred |
| POST | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}/star` | Toggle star status | Deferred |
| POST | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}/move` | Move to another folder | Deferred |
| POST | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}/duplicate` | Duplicate worksheet | Deferred |

### 4.4 Permission Annotation Pattern

```kotlin
// TeamController.kt - Permission enforcement examples (v3.3.0 - renamed from SqlController)

@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/teams/{teamId}/sql")
class TeamController(
    private val worksheetFolderService: WorksheetFolderService,
    private val sqlWorksheetService: SqlWorksheetService,
) {
    // Member role required (VIEWER, EDITOR, MANAGER)
    @GetMapping("/worksheets")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    fun listWorksheets(
        @PathVariable teamId: Long,
        ...
    ): ResponseEntity<...> { ... }

    // Editor+ role required (EDITOR, MANAGER)
    @PostMapping("/worksheets")
    @PreAuthorize("@teamSecurity.canEdit(#teamId)")
    fun createWorksheet(
        @PathVariable teamId: Long,
        @RequestBody request: CreateWorksheetRequest,
    ): ResponseEntity<...> { ... }

    // Worksheet-level permission check (via folderId -> teamId)
    @PutMapping("/worksheets/{worksheetId}")
    @PreAuthorize("@teamSecurity.canEditWorksheet(#teamId, #worksheetId)")
    fun updateWorksheet(
        @PathVariable teamId: Long,
        @PathVariable worksheetId: Long,
        @RequestBody request: UpdateWorksheetRequest,
    ): ResponseEntity<...> { ... }
}
```

> **Note:** `TeamSecurityService` is a Spring component that validates user's team role.
> See TEAM_FEATURE.md for TeamMemberService implementation details.

---

## 5. API Details

### 5.1 List Worksheets (CLI Primary Endpoint)

**`GET /api/v1/teams/{teamId}/sql/worksheets`**

```http
GET /api/v1/teams/1/sql/worksheets?searchText=revenue&starred=true&size=20&page=0
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `folderName` | string | No | Filter by folder name |
| `searchText` | string | No | Search in name, description, SQL content |
| `starred` | boolean | No | Filter starred worksheets only |
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
      "teamId": 1,
      "teamName": "marketing",
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

### 5.2 Get Worksheet Details

**`GET /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}`**

**Response:**
```json
{
  "id": 43,
  "name": "daily_revenue",
  "description": "Daily revenue by channel",
  "teamId": 1,
  "teamName": "marketing",
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

### 5.3 Create Worksheet

**`POST /api/v1/teams/{teamId}/sql/worksheets`**

```http
POST /api/v1/teams/1/sql/worksheets
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

### 5.4 Update Worksheet

**`PUT /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}`**

```http
PUT /api/v1/teams/1/sql/worksheets/43
Content-Type: application/json

{
  "name": "daily_revenue_v2",
  "description": "Updated daily revenue worksheet",
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

### 5.5 Delete Worksheet

**`DELETE /api/v1/teams/{teamId}/sql/worksheets/{worksheetId}`**

**Response:** `204 No Content`

### 5.6 Delete Folder

**`DELETE /api/v1/teams/{teamId}/sql/folders/{folderId}`**

Returns `204 No Content` on success.

Returns `400 Bad Request` if folder contains worksheets:
```json
{
  "error": "FOLDER_NOT_EMPTY",
  "message": "Cannot delete folder: contains 3 worksheets",
  "details": {
    "worksheetCount": 3
  }
}
```

---

## 6. DTO Definitions

### 6.1 Command DTOs (Request)

```kotlin
// module-core-domain/command/sql/SqlCommands.kt

data class CreateFolderCommand(
    val teamId: Long,
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

data class CreateWorksheetCommand(
    val folderId: Long,
    @field:NotBlank @field:Size(max = 200)
    val name: String,
    @field:Size(max = 1000)
    val description: String? = null,
    @field:NotBlank
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

data class UpdateWorksheetCommand(
    @field:Size(max = 200)
    val name: String? = null,
    @field:Size(max = 1000)
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

data class MoveWorksheetCommand(
    val targetFolderId: Long,
)

data class RunWorksheetCommand(
    val parameters: Map<String, String>? = null,
    val limit: Int? = 1000,
)

data class ListWorksheetsQuery(
    val teamId: Long,
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
    val teamId: Long,
    val name: String,
    val description: String?,
    val displayOrder: Int,
    val worksheetCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class WorksheetSummaryProjection(
    val id: Long,
    val name: String,
    val description: String?,
    val teamId: Long,
    val teamName: String,
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

data class WorksheetDetailProjection(
    val id: Long,
    val name: String,
    val description: String?,
    val teamId: Long,
    val teamName: String,
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

data class WorksheetExecutionProjection(
    val executionId: String,
    val worksheetId: Long,
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

data class CreateWorksheetRequest(
    val folderId: Long,
    @field:NotBlank @field:Size(max = 200)
    val name: String,
    @field:Size(max = 1000)
    val description: String? = null,
    @field:NotBlank
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

data class UpdateWorksheetRequest(
    @field:Size(max = 200)
    val name: String? = null,
    @field:Size(max = 1000)
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

data class MoveWorksheetRequest(
    val targetFolderId: Long,
)

data class RunWorksheetRequest(
    val parameters: Map<String, String>? = null,
    val limit: Int? = 1000,
)

// Response DTOs
data class FolderResponse(
    val id: Long,
    val teamId: Long,
    val name: String,
    val description: String?,
    val displayOrder: Int,
    val worksheetCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class WorksheetListResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val teamId: Long,
    val teamName: String,
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

data class WorksheetDetailResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val teamId: Long,
    val teamName: String,
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

data class WorksheetCreatedResponse(
    val id: Long,
    val name: String,
    val folderId: Long,
    val createdAt: LocalDateTime,
)

data class WorksheetUpdatedResponse(
    val id: Long,
    val name: String,
    val updatedAt: LocalDateTime,
)

data class WorksheetExecutionResponse(
    val executionId: String,
    val worksheetId: Long,
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
| Mappers | module-server-api | `mapper/` |
| Enums | module-core-common | `enums/SqlEnums.kt` |

### 7.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    └── SqlEnums.kt                    # SqlDialect

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/sql/
    │   ├── WorksheetFolderEntity.kt   # v3.3.0 (renamed from SqlFolderEntity)
    │   └── SqlWorksheetEntity.kt
    ├── command/sql/
    │   └── SqlCommands.kt             # All command/query classes
    ├── projection/sql/
    │   └── SqlProjections.kt          # All projection classes
    ├── repository/sql/
    │   ├── WorksheetFolderRepositoryJpa.kt   # v3.3.0
    │   ├── WorksheetFolderRepositoryDsl.kt   # v3.3.0
    │   ├── SqlWorksheetRepositoryJpa.kt
    │   └── SqlWorksheetRepositoryDsl.kt
    └── service/
        ├── WorksheetFolderService.kt  # v3.3.0 (renamed from SqlFolderService)
        └── SqlWorksheetService.kt

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/repository/sql/
    ├── WorksheetFolderRepositoryJpaImpl.kt   # v3.3.0
    ├── WorksheetFolderRepositoryDslImpl.kt   # v3.3.0
    ├── SqlWorksheetRepositoryJpaImpl.kt
    └── SqlWorksheetRepositoryDslImpl.kt

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── controller/
    │   └── TeamController.kt          # v3.3.0 (renamed from SqlController)
    ├── dto/sql/
    │   ├── WorksheetFolderDtos.kt     # v3.3.0
    │   └── SqlWorksheetDtos.kt
    └── mapper/
        ├── WorksheetFolderMapper.kt   # v3.3.0 (renamed from SqlFolderMapper)
        └── SqlWorksheetMapper.kt
```

---

## 8. Database Schema

### 8.1 Tables

```sql
-- Worksheet Folder table (v3.3.0 - renamed from sql_folder)
CREATE TABLE worksheet_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_worksheet_folder_team_id (team_id),
    INDEX idx_worksheet_folder_deleted_at (deleted_at),
    UNIQUE KEY uk_worksheet_folder_name_team (name, team_id),
    FOREIGN KEY (team_id) REFERENCES team(id)
);

-- SQL Worksheet table
CREATE TABLE sql_worksheet (
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
    INDEX idx_sql_worksheet_folder_id (folder_id),
    INDEX idx_sql_worksheet_name (name),
    INDEX idx_sql_worksheet_starred (is_starred),
    INDEX idx_sql_worksheet_deleted_at (deleted_at),
    FOREIGN KEY (folder_id) REFERENCES worksheet_folder(id)
);
```

---

## 9. Implementation Phases

### Phase 1: MVP (Core Functionality) - ✅ Complete

| Component | Status | Description |
|-----------|--------|-------------|
| SqlFolderEntity | ✅ Complete | Flat folder structure |
| SqlWorksheetEntity | ✅ Complete | Worksheet storage |
| Folder CRUD API | ✅ Complete | 4 endpoints |
| Worksheet CRUD API | ✅ Complete | 5 endpoints |
| CLI commands | Planned | list/get/create/update/delete |
| Team permission check | Planned | Role-based access via TeamMemberEntity |

### Phase 2: Enhanced Features

| Component | Status | Description |
|-----------|--------|-------------|
| Folder Update API | Planned | PUT endpoint for folder updates |
| Worksheet Run API | Planned | Execute worksheet with parameters |
| Worksheet Star API | Planned | Toggle star status |
| Worksheet Move API | Planned | Move between folders |
| Worksheet Duplicate API | Planned | Clone worksheet |
| **Common TagEntity** | Planned | Shared tag system for SQL/Dataset/Quality |
| Full-text search | Planned | Optimized SQL content search |

### Phase 2: Tag Entity Design (Preview)

> Tags will be implemented as a **common entity** shared across SQL worksheets, Datasets, and Quality specs.

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
    val resourceType: TaggableResourceType,  // SQL_SNIPPET, DATASET, QUALITY_SPEC

    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,

    @Column(name = "tag_id", nullable = false)
    val tagId: Long,
) : BaseEntity()

// module-core-common/enums/TagEnums.kt
enum class TaggableResourceType {
    SQL_SNIPPET,
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
| Folder CRUD | All 4 endpoints working |
| Worksheet CRUD | All 5 endpoints working |
| CLI integration | `dli sql list/get/create/update/delete` working |
| Permission check | Team role enforcement working |
| Folder constraints | Unique names per team, deletion blocking |
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
- Team-based organization (from PopSQL)
- Starred/favorites (from Redash)
- Team sharing via membership (from PopSQL)

---

## Appendix B: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Sharing model | Team Membership | Simple, consistent with TEAM_FEATURE.md |
| 2 | Folder depth | Flat 1-level | Simpler UX, easier navigation |
| 3 | Folder uniqueness | Unique per team | Prevent confusion |
| 4 | Worksheet uniqueness | Not unique | Allow duplicate names |
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
    fun findByTeamIdAndName(teamId: Long, name: String): SqlFolderEntity?
    fun deleteById(id: Long)
}

// SqlFolderRepositoryDsl.kt
interface SqlFolderRepositoryDsl {
    fun findByTeamId(teamId: Long): List<FolderSummaryProjection>
    fun countWorksheetsByFolderId(folderId: Long): Int
}

// SqlWorksheetRepositoryJpa.kt
interface SqlWorksheetRepositoryJpa {
    fun save(worksheet: SqlWorksheetEntity): SqlWorksheetEntity
    fun findById(id: Long): SqlWorksheetEntity?
    fun deleteById(id: Long)
}

// SqlWorksheetRepositoryDsl.kt
interface SqlWorksheetRepositoryDsl {
    fun findByConditions(query: ListWorksheetsQuery): Page<WorksheetSummaryProjection>
    fun findDetailById(id: Long): WorksheetDetailProjection?
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

    // Spring Data auto-implements: findByTeamIdAndName(teamId, name)
}

// SqlWorksheetRepositoryJpaImpl.kt
@Repository("sqlWorksheetRepositoryJpa")
interface SqlWorksheetRepositoryJpaImpl :
    SqlWorksheetRepositoryJpa,
    JpaRepository<SqlWorksheetEntity, Long>

// SqlFolderRepositoryDslImpl.kt
@Repository("sqlFolderRepositoryDsl")
class SqlFolderRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlFolderRepositoryDsl {
    // QueryDSL implementation
}

// SqlWorksheetRepositoryDslImpl.kt
@Repository("sqlWorksheetRepositoryDsl")
class SqlWorksheetRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlWorksheetRepositoryDsl {
    // QueryDSL implementation with search and pagination
}
```

---

## Appendix D: Migration from v2.x (Project-based to Team-based)

### Breaking Changes from v2.1.0

| Change | v2.1.0 | v3.0.0 | Migration |
|--------|--------|--------|-----------|
| Organization | Project-based | Team-based | Update teamId FK |
| CLI option | `--project` | `--team` | Update CLI scripts |
| URL structure | `/projects/{projectId}/sql/` | `/teams/{teamId}/sql/` | Update API clients |
| Permission check | ProjectMemberEntity | TeamMemberEntity | Update security beans |
| Role hierarchy | OWNER/EDITOR/VIEWER | MANAGER/EDITOR/VIEWER | Update role references |

### Database Migration

```sql
-- Step 1: Add team_id column to sql_folder
ALTER TABLE sql_folder ADD COLUMN team_id BIGINT;

-- Step 2: Migrate data (if project->team mapping exists)
-- Note: Development in progress, no migration needed for fresh deployment
-- UPDATE sql_folder sf
-- SET sf.team_id = (SELECT team_id FROM project_team_mapping WHERE project_id = sf.project_id);

-- Step 3: Drop project_id column
ALTER TABLE sql_folder DROP COLUMN project_id;

-- Step 4: Update constraints
ALTER TABLE sql_folder DROP INDEX uk_sql_folder_name_project;
ALTER TABLE sql_folder ADD UNIQUE KEY uk_sql_folder_name_team (name, team_id);
ALTER TABLE sql_folder DROP INDEX idx_sql_folder_project_id;
ALTER TABLE sql_folder ADD INDEX idx_sql_folder_team_id (team_id);
ALTER TABLE sql_folder ADD FOREIGN KEY (team_id) REFERENCES team(id);
```

---

## Appendix E: Version History

| Version | Date | Changes |
|---------|------|---------|
| 3.3.0 | 2026-01-10 | Controller renamed: TeamSqlController → TeamController; Entity: SqlFolderEntity → WorksheetFolderEntity |
| 3.2.0 | 2026-01-10 | Terminology unified: "Snippet" → "Worksheet" (Snowflake/Databricks style) |
| 3.1.0 | 2026-01-10 | Terminology unified: "Query" → "Snippet" throughout documentation |
| 3.0.0 | 2026-01-10 | Migration from Project-based to Team-based organization |
| 2.1.0 | 2026-01-09 | MVP complete with 14 endpoints, 158+ tests |
| 2.0.1 | 2026-01-07 | Architectural review feedback applied |
| 2.0.0 | 2026-01-06 | Flat folder structure, SavedQueryEntity renamed to SqlSnippetEntity |

---

**Last Updated:** 2026-01-10 (v3.3.0 - TeamController, WorksheetFolderEntity)
