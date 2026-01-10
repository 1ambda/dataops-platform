# SQL (Saved Worksheet) Management - Release Document

> **Version:** 3.3.0
> **Release Date:** 2026-01-10
> **Status:** MVP Complete (9/9 endpoints - Team-based)

---

## v3.3.0 Changes (2026-01-10)

- **Controller Rename**: `TeamSqlController` → `TeamController`
- Controller now handles all Team-scoped resources (SQL Worksheets/Folders)
- API paths remain unchanged: `/api/v1/teams/{teamId}/sql/worksheets` and `/api/v1/teams/{teamId}/sql/folders`
- Controller file renamed: `TeamSqlController.kt` → `TeamController.kt`

---

## v3.2.0 Changes (2026-01-10)

- **Terminology**: "Snippet" → "Worksheet" (industry-standard naming like Snowflake, Databricks)
- Entity names: SqlSnippetEntity → SqlWorksheetEntity (documentation only)
- API paths: `/sql/snippets` → `/sql/worksheets`

---

## Migration Note (v3.0.0)

**Breaking Change:** Project-based organization replaced with Team-based architecture.
- All `/api/v1/projects/{projectId}/sql/*` endpoints migrated to `/api/v1/teams/{teamId}/sql/*`
- Project API (5 endpoints) removed - See [TEAM_FEATURE.md](./TEAM_FEATURE.md) for Team management
- `ProjectEntity` references replaced with `TeamEntity`
- `projectId` parameters replaced with `teamId`

---

## Executive Summary

This release introduces the SQL (Saved Worksheet) Management feature, enabling users to organize and manage reusable SQL worksheets within Teams. The implementation includes SQL Folder organization and SQL Worksheet CRUD operations under Team ownership.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 9 (4 Folder + 5 Worksheet) |
| **Total Tests** | 100+ (Service: 48, Controller: 53) |
| **Test Success Rate** | 100% |
| **Architecture** | Pure Hexagonal (Port-Adapter) |

---

## API Endpoints

### SQL Folder API (4 endpoints)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/teams/{teamId}/sql/folders` | List folders with worksheet count |
| POST | `/api/v1/teams/{teamId}/sql/folders` | Create folder |
| GET | `/api/v1/teams/{teamId}/sql/folders/{folderId}` | Get folder details |
| DELETE | `/api/v1/teams/{teamId}/sql/folders/{folderId}` | Soft delete folder |

### SQL Worksheet API (5 endpoints)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/teams/{teamId}/sql/worksheets` | List worksheets with filters/pagination |
| POST | `/api/v1/teams/{teamId}/sql/worksheets` | Create worksheet |
| GET | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Get worksheet details |
| PUT | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Update worksheet |
| DELETE | `/api/v1/teams/{teamId}/sql/worksheets/{worksheetId}` | Soft delete worksheet |

---

## Entity Model

### SqlFolderEntity

```kotlin
@Entity
@Table(name = "sql_folder")
class SqlFolderEntity(
    teamId: Long,           // FK to Team
    name: String,           // Folder name (unique per team)
    description: String?,   // Optional description
    displayOrder: Int,      // Sort order
) : BaseEntity()
```

### SqlWorksheetEntity

```kotlin
@Entity
@Table(name = "sql_worksheet")
class SqlWorksheetEntity(
    folderId: Long,         // FK to SqlFolder
    name: String,           // Worksheet name
    description: String?,   // Optional description
    sqlText: String,        // SQL query text
    dialect: SqlDialect,    // BIGQUERY, TRINO, SPARK, etc.
    runCount: Long,         // Execution count
    lastRunAt: LocalDateTime?, // Last execution time
    isStarred: Boolean,     // Favorite flag
) : BaseEntity()
```

### Entity Relationships

```
Team (see TEAM_FEATURE.md)
  └── SqlFolder (teamId FK)
        └── SqlWorksheet (folderId FK)
```

---

## Implementation Details

### Architecture Compliance

| Component | Pattern | Location |
|-----------|---------|----------|
| **Services** | Concrete classes | `module-core-domain/service/` |
| **Repository Interfaces** | Jpa/Dsl separation | `module-core-domain/repository/` |
| **Repository Implementations** | QueryDSL + Spring Data | `module-core-infra/repository/` |
| **Controller** | TeamController (v3.3.0) | `module-server-api/controller/` |
| **DTOs** | Request/Response pattern | `module-server-api/dto/` |
| **Mappers** | Static object mappers | `module-server-api/mapper/` |

### Key Design Decisions

1. **Unified Controller**: All 9 SQL endpoints in `TeamController` (renamed from SqlController in v3.3.0) for cohesive API surface
2. **Long FK Pattern**: No JPA `@ManyToOne` relationships, only Long FK fields
3. **Soft Delete**: `deletedAt` field in `BaseEntity` for all entities
4. **QueryDSL**: Complex queries use `JPAQueryFactory` with BooleanBuilder
5. **CQRS**: Jpa repositories for CRUD, Dsl repositories for complex queries
6. **Team Ownership**: SQL resources owned by Teams, not Projects (v3.0.0)

### Files Created/Modified

#### New Files (module-core-domain)

- `entity/sql/SqlFolderEntity.kt` - SQL Folder entity (teamId FK)
- `entity/sql/SqlWorksheetEntity.kt` - SQL Worksheet entity
- `service/SqlFolderService.kt` - Folder business logic
- `service/SqlWorksheetService.kt` - Worksheet business logic
- `repository/sql/SqlFolderRepositoryJpa.kt` - Folder CRUD interface
- `repository/sql/SqlFolderRepositoryDsl.kt` - Folder query interface
- `repository/sql/SqlWorksheetRepositoryJpa.kt` - Worksheet CRUD interface
- `repository/sql/SqlWorksheetRepositoryDsl.kt` - Worksheet query interface

#### New Files (module-core-infra)

- `repository/sql/SqlFolderRepositoryJpaImpl.kt` - Folder CRUD implementation
- `repository/sql/SqlFolderRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/sql/SqlFolderRepositoryDslImpl.kt` - Folder QueryDSL
- `repository/sql/SqlWorksheetRepositoryJpaImpl.kt` - Worksheet CRUD implementation
- `repository/sql/SqlWorksheetRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/sql/SqlWorksheetRepositoryDslImpl.kt` - Worksheet QueryDSL

#### New Files (module-server-api)

- `controller/TeamController.kt` - Unified Team-scoped REST controller (9 SQL endpoints, renamed from SqlController in v3.3.0)
- `dto/sql/*.kt` - SQL DTOs (Folder, Worksheet requests/responses)
- `mapper/SqlFolderMapper.kt` - Folder entity-DTO mapping (replaced by WorksheetFolderMapper in v3.3.0)
- `mapper/SqlWorksheetMapper.kt` - Worksheet entity-DTO mapping

#### New Files (module-core-common)

- `enums/SqlDialect.kt` - SQL dialect enumeration
- Exception classes added to `CommonExceptions.kt`

#### Test Files

- `SqlFolderServiceTest.kt` - 19 tests
- `SqlFolderControllerTest.kt` - 20 tests
- `SqlWorksheetServiceTest.kt` - 29 tests
- `SqlWorksheetControllerTest.kt` - 33 tests

---

## Test Coverage

### Service Tests (48 tests)

| Service | Test Count | Coverage |
|---------|------------|----------|
| SqlFolderService | 19 | CRUD + validation + cascade |
| SqlWorksheetService | 29 | CRUD + filters + star/run tracking |

### Controller Tests (53 tests)

| Controller | Test Count | Coverage |
|------------|------------|----------|
| TeamController (Folder) | 20 | All 4 endpoints |
| TeamController (Worksheet) | 33 | All 5 endpoints |

> **Note:** Controller tests are in `WorksheetFolderControllerTest.kt` and `SqlWorksheetControllerTest.kt`

---

## Cross-Review Findings

### Fixed Issues (HIGH Priority)

1. **SqlFolderRepositoryDslImpl**: Converted from JPQL + field injection to QueryDSL + constructor injection
2. **Controller Code Duplication**: Extracted `findWorksheetInProject()` helper method to eliminate 4x duplicated worksheet lookup logic

### Documented Issues (MEDIUM/LOW)

- N+1 query risk in folder name lookups (documented for future optimization)
- DTO naming uses `*Request/*Response` instead of `*Dto` (consistent within project)
- Missing `updateFolder` in SqlFolderService (deferred - not in MVP scope)

---

## CLI Integration (Future)

The SQL Feature will integrate with CLI via the following commands (planned):

| CLI Command | Server API |
|-------------|------------|
| `dli sql folders list --team <id>` | GET `/api/v1/teams/{teamId}/sql/folders` |
| `dli sql folders create --team <id>` | POST `/api/v1/teams/{teamId}/sql/folders` |
| `dli sql worksheets list --team <id>` | GET `/api/v1/teams/{teamId}/sql/worksheets` |
| `dli sql worksheets get --team <id>` | GET `/api/v1/teams/{teamId}/sql/worksheets/{id}` |
| `dli sql worksheets create --team <id>` | POST `/api/v1/teams/{teamId}/sql/worksheets` |
| `dli sql worksheets update --team <id>` | PUT `/api/v1/teams/{teamId}/sql/worksheets/{id}` |
| `dli sql worksheets delete --team <id>` | DELETE `/api/v1/teams/{teamId}/sql/worksheets/{id}` |

---

## Deferred Features (v4.0)

The following features were excluded from MVP per scope discussion:

1. **Permission Checking**: User access control (deferred to TEAM_FEATURE)
2. **Folder Update API**: PUT endpoint for folder updates
3. **Worksheet Move**: Move worksheet between folders
4. **Folder Nesting**: Hierarchical folder structure

---

## Verification

```bash
# Run SQL Feature tests (v3.3.0 test file names)
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.WorksheetFolderControllerTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.SqlWorksheetControllerTest"

# Service tests
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.WorksheetFolderServiceTest"
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.SqlWorksheetServiceTest"

# All 53 controller tests pass
# All 48 service tests pass
```

---

*Document Version: 3.3.0 | Last Updated: 2026-01-10*
