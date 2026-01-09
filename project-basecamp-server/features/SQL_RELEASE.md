# SQL (Saved Query) Management - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-09
> **Status:** MVP Complete (14/14 endpoints)

---

## Executive Summary

This release introduces the SQL (Saved Query) Management feature, enabling users to organize and manage reusable SQL queries within projects. The implementation includes Project management, SQL Folder organization, and SQL Snippet CRUD operations.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 14 (5 Project + 4 Folder + 5 Snippet) |
| **Total Tests** | 158+ (Service: 75, Controller: 83) |
| **Test Success Rate** | 100% |
| **Architecture** | Pure Hexagonal (Port-Adapter) |

---

## API Endpoints

### Project API (5 endpoints)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/projects` | List projects with search/pagination |
| POST | `/api/v1/projects` | Create project |
| GET | `/api/v1/projects/{projectId}` | Get project details |
| PUT | `/api/v1/projects/{projectId}` | Update project |
| DELETE | `/api/v1/projects/{projectId}` | Soft delete project |

### SQL Folder API (4 endpoints)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/projects/{projectId}/sql/folders` | List folders with snippet count |
| POST | `/api/v1/projects/{projectId}/sql/folders` | Create folder |
| GET | `/api/v1/projects/{projectId}/sql/folders/{folderId}` | Get folder details |
| DELETE | `/api/v1/projects/{projectId}/sql/folders/{folderId}` | Soft delete folder |

### SQL Snippet API (5 endpoints)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/projects/{projectId}/sql/snippets` | List snippets with filters/pagination |
| POST | `/api/v1/projects/{projectId}/sql/snippets` | Create snippet |
| GET | `/api/v1/projects/{projectId}/sql/snippets/{snippetId}` | Get snippet details |
| PUT | `/api/v1/projects/{projectId}/sql/snippets/{snippetId}` | Update snippet |
| DELETE | `/api/v1/projects/{projectId}/sql/snippets/{snippetId}` | Soft delete snippet |

---

## Entity Model

### ProjectEntity

```kotlin
@Entity
@Table(name = "project")
class ProjectEntity(
    name: String,           // Unique identifier
    displayName: String,    // Human-readable name
    description: String?,   // Optional description
) : BaseEntity()
```

### SqlFolderEntity

```kotlin
@Entity
@Table(name = "sql_folder")
class SqlFolderEntity(
    projectId: Long,        // FK to Project
    name: String,           // Folder name (unique per project)
    description: String?,   // Optional description
    displayOrder: Int,      // Sort order
) : BaseEntity()
```

### SqlSnippetEntity

```kotlin
@Entity
@Table(name = "sql_snippet")
class SqlSnippetEntity(
    folderId: Long,         // FK to SqlFolder
    name: String,           // Snippet name
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
Project
  └── SqlFolder (projectId FK)
        └── SqlSnippet (folderId FK)
```

---

## Implementation Details

### Architecture Compliance

| Component | Pattern | Location |
|-----------|---------|----------|
| **Services** | Concrete classes | `module-core-domain/service/` |
| **Repository Interfaces** | Jpa/Dsl separation | `module-core-domain/repository/` |
| **Repository Implementations** | QueryDSL + Spring Data | `module-core-infra/repository/` |
| **Controller** | Unified ProjectController | `module-server-api/controller/` |
| **DTOs** | Request/Response pattern | `module-server-api/dto/` |
| **Mappers** | Static object mappers | `module-server-api/mapper/` |

### Key Design Decisions

1. **Unified Controller**: All 14 endpoints in `ProjectController` for cohesive API surface
2. **Long FK Pattern**: No JPA `@ManyToOne` relationships, only Long FK fields
3. **Soft Delete**: `deletedAt` field in `BaseEntity` for all entities
4. **QueryDSL**: Complex queries use `JPAQueryFactory` with BooleanBuilder
5. **CQRS**: Jpa repositories for CRUD, Dsl repositories for complex queries

### Files Created/Modified

#### New Files (module-core-domain)

- `entity/project/ProjectEntity.kt` - Project domain entity
- `entity/sql/SqlFolderEntity.kt` - SQL Folder entity
- `entity/sql/SqlSnippetEntity.kt` - SQL Snippet entity
- `service/ProjectService.kt` - Project business logic
- `service/SqlFolderService.kt` - Folder business logic
- `service/SqlSnippetService.kt` - Snippet business logic
- `repository/project/ProjectRepositoryJpa.kt` - Project CRUD interface
- `repository/project/ProjectRepositoryDsl.kt` - Project query interface
- `repository/sql/SqlFolderRepositoryJpa.kt` - Folder CRUD interface
- `repository/sql/SqlFolderRepositoryDsl.kt` - Folder query interface
- `repository/sql/SqlSnippetRepositoryJpa.kt` - Snippet CRUD interface
- `repository/sql/SqlSnippetRepositoryDsl.kt` - Snippet query interface

#### New Files (module-core-infra)

- `repository/project/ProjectRepositoryJpaImpl.kt` - Project CRUD implementation
- `repository/project/ProjectRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/project/ProjectRepositoryDslImpl.kt` - Project QueryDSL
- `repository/sql/SqlFolderRepositoryJpaImpl.kt` - Folder CRUD implementation
- `repository/sql/SqlFolderRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/sql/SqlFolderRepositoryDslImpl.kt` - Folder QueryDSL
- `repository/sql/SqlSnippetRepositoryJpaImpl.kt` - Snippet CRUD implementation
- `repository/sql/SqlSnippetRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/sql/SqlSnippetRepositoryDslImpl.kt` - Snippet QueryDSL

#### New Files (module-server-api)

- `controller/ProjectController.kt` - Unified REST controller (14 endpoints)
- `dto/project/*.kt` - Project DTOs (Create, Update, Response, List)
- `dto/sql/*.kt` - SQL DTOs (Folder, Snippet requests/responses)
- `mapper/ProjectMapper.kt` - Project entity-DTO mapping
- `mapper/SqlFolderMapper.kt` - Folder entity-DTO mapping
- `mapper/SqlSnippetMapper.kt` - Snippet entity-DTO mapping

#### New Files (module-core-common)

- `enums/SqlDialect.kt` - SQL dialect enumeration
- Exception classes added to `CommonExceptions.kt`

#### Test Files

- `ProjectServiceTest.kt` - 27 tests
- `ProjectControllerTest.kt` - 30 tests
- `SqlFolderServiceTest.kt` - 19 tests
- `SqlFolderControllerTest.kt` - 20 tests
- `SqlSnippetServiceTest.kt` - 29 tests
- `SqlSnippetControllerTest.kt` - 33 tests

---

## Test Coverage

### Service Tests (75 tests)

| Service | Test Count | Coverage |
|---------|------------|----------|
| ProjectService | 27 | CRUD + validation + pagination |
| SqlFolderService | 19 | CRUD + validation + cascade |
| SqlSnippetService | 29 | CRUD + filters + star/run tracking |

### Controller Tests (83 tests)

| Controller | Test Count | Coverage |
|------------|------------|----------|
| ProjectController (Project) | 30 | All 5 endpoints |
| ProjectController (Folder) | 20 | All 4 endpoints |
| ProjectController (Snippet) | 33 | All 5 endpoints |

---

## Cross-Review Findings

### Fixed Issues (HIGH Priority)

1. **SqlFolderRepositoryDslImpl**: Converted from JPQL + field injection to QueryDSL + constructor injection
2. **Controller Code Duplication**: Extracted `findSnippetInProject()` helper method to eliminate 4x duplicated snippet lookup logic

### Documented Issues (MEDIUM/LOW)

- N+1 query risk in folder name lookups (documented for future optimization)
- DTO naming uses `*Request/*Response` instead of `*Dto` (consistent within project)
- Missing `updateFolder` in SqlFolderService (deferred - not in MVP scope)

---

## CLI Integration (Future)

The SQL Feature will integrate with CLI via the following commands (planned):

| CLI Command | Server API |
|-------------|------------|
| `dli sql folders list` | GET `/api/v1/projects/{id}/sql/folders` |
| `dli sql folders create` | POST `/api/v1/projects/{id}/sql/folders` |
| `dli sql snippets list` | GET `/api/v1/projects/{id}/sql/snippets` |
| `dli sql snippets get` | GET `/api/v1/projects/{id}/sql/snippets/{id}` |
| `dli sql snippets create` | POST `/api/v1/projects/{id}/sql/snippets` |
| `dli sql snippets update` | PUT `/api/v1/projects/{id}/sql/snippets/{id}` |
| `dli sql snippets delete` | DELETE `/api/v1/projects/{id}/sql/snippets/{id}` |

---

## Deferred Features (v2.0)

The following features were excluded from MVP per scope discussion:

1. **Permission Checking**: User access control (deferred to PROJECT_FEATURE)
2. **Folder Update API**: PUT endpoint for folder updates
3. **Snippet Move**: Move snippet between folders
4. **Folder Nesting**: Hierarchical folder structure

---

## Verification

```bash
# Run SQL Feature tests
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.ProjectControllerTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.SqlFolderControllerTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.SqlSnippetControllerTest"

# All 83 controller tests pass
# All 75 service tests pass
```

---

*Document Version: 1.0.0 | Last Updated: 2026-01-09*
