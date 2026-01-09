# ACL (Access Control List) Feature Specification

> **Version:** 1.1.0 | **Status:** Draft | **Priority:** P1 High
> **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** TBD | **Endpoints:** 0/12+ Complete
>
> **Dependencies:**
> - [`PROJECT_FEATURE.md`](./PROJECT_FEATURE.md) - Team & Project Management (TeamRole, ProjectRole)
> - [`SQL_FEATURE.md`](./SQL_FEATURE.md) - SQL Query Management (permission patterns)
>
> **Implementation Details:** [`ACL_RELEASE.md`](./ACL_RELEASE.md) (TBD)

---

## 1. Overview

### 1.1 Purpose

The ACL (Access Control List) feature provides a unified authentication and authorization framework for the Basecamp platform. It implements a layered security model that supports:

1. **User Authentication** - OIDC/Keycloak for production, in-memory users for local development
2. **API Token Authentication** - For automation (Airflow DAGs, CI/CD pipelines)
3. **Resource-Based Authorization** - Custom Security Expressions for Team/Project access control
4. **System Admin Bypass** - ADMIN role bypasses Team/Project membership checks

**Target Users:**
- **Data Professionals (DS/DA/DAE/DE):** CLI (`dli`) access via API tokens or user credentials
- **Automation Systems:** Airflow DAGs, CI/CD pipelines using API tokens
- **Platform Administrators:** System-wide access and token management

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **User Authentication** | OIDC/Keycloak integration for production, in-memory for dev |
| **API Token Authentication** | Token-based auth for automation (Airflow, CI/CD) |
| **Custom Security Expressions** | `@PreAuthorize("@teamSecurity.canEdit(#teamId)")` pattern |
| **Role Hierarchy** | System roles (ADMIN/CONSUMER/PUBLIC) + Resource roles (Owner/Editor/Viewer) |
| **Admin Bypass** | System ADMIN bypasses Team/Project membership checks |
| **Token Management** | Create, list, revoke API tokens |
| **CLI Authentication** | `dli` supports both user login and API token |

### 1.3 Architecture Overview

```
+-----------------------------------------------------------------------------------+
|                              ACL Architecture                                      |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  +------------------+     +------------------+     +------------------+           |
|  |  User Auth       |     |  API Token Auth  |     |  Dev Mode Auth   |           |
|  |  (OIDC/Keycloak) |     |  (Bearer Token)  |     |  (In-Memory)     |           |
|  +--------+---------+     +--------+---------+     +--------+---------+           |
|           |                        |                        |                     |
|           +------------------------+------------------------+                     |
|                                    |                                              |
|                                    v                                              |
|                          +---------+---------+                                    |
|                          | AuthenticationMgr |                                    |
|                          +---------+---------+                                    |
|                                    |                                              |
|                                    v                                              |
|                          +---------+---------+                                    |
|                          | SecurityContext   |                                    |
|                          | (Principal)       |                                    |
|                          +---------+---------+                                    |
|                                    |                                              |
|                                    v                                              |
|  +----------------+       +---------+---------+       +------------------+        |
|  | System Role    |       | Security          |       | Resource Role    |        |
|  | Check          | <---- | Expressions       | ----> | Check            |        |
|  | (ADMIN bypass) |       | (@PreAuthorize)   |       | (Team/Project)   |        |
|  +----------------+       +-------------------+       +------------------+        |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 1.4 Role Hierarchy

```
+-----------------------------------------------------------------------------------+
|                              Role Hierarchy                                        |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  System Roles (UserRole)           Resource Roles (TeamRole/ProjectRole)          |
|  ========================          ======================================          |
|                                                                                   |
|  ADMIN ----+                       TeamRole:                                      |
|            |                         OWNER   - Full team access, manage members   |
|            +--> Bypass all           EDITOR  - Create/edit team resources         |
|            |    resource checks      VIEWER  - Read-only, can execute             |
|            |                                                                       |
|  CONSUMER -+                       ProjectRole:                                   |
|            |                         OWNER   - Full project access, manage members|
|            +--> Subject to           EDITOR  - Create/edit SQL queries            |
|                 resource checks      VIEWER  - Read-only, can execute             |
|                                                                                   |
|  PUBLIC ---+--> Public endpoints                                                  |
|               only (health, info)                                                 |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 2. Authentication Architecture

### 2.1 User Authentication (OIDC/Keycloak)

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
|     Client -----> Basecamp API                                                    |
|                   (Authorization: Bearer <jwt>)                                   |
|                                                                                   |
|  4. Token Validation                                                              |
|     Basecamp API -----> Keycloak (JWKS endpoint) -----> Validate Signature        |
|                                                                                   |
|  5. Extract Principal                                                             |
|     JWT Claims -----> UserPrincipal (userId, email, roles)                        |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2.2 API Token Authentication

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

### 2.3 CLI (`dli`) Authentication Flow

```bash
# Option 1: Interactive Login (OIDC Device Flow)
dli auth login
# Opens browser for OIDC login, stores refresh token locally

# Option 2: API Token (for automation)
export DLI_API_TOKEN="dli_xxxx..."
dli dataset list

# Option 3: Config file
# ~/.dli/config.yaml
# api_token: dli_xxxx...
```

### 2.4 Local Development Authentication

**Strategy: Profile-Based Configuration**

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

---

## 3. Authorization Architecture

### 3.1 Custom Security Expressions Pattern

**Decision: Use Custom Security Expressions** (Pattern B from interview)
- Consistent with SQL_FEATURE.md patterns
- Better IDE support (auto-completion for `@securityBean.method()`)
- Easier unit testing (mock the security beans)

```kotlin
// Controller Example
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/teams")
class TeamController(
    private val teamService: TeamService,
) {
    // Anyone authenticated
    @GetMapping
    fun listTeams(): ResponseEntity<...> { ... }

    // Team member (any role)
    @GetMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    fun getTeam(@PathVariable teamId: Long): ResponseEntity<...> { ... }

    // Team Editor or Owner
    @PostMapping("/{teamId}/resources")
    @PreAuthorize("@teamSecurity.canEdit(#teamId)")
    fun createResource(@PathVariable teamId: Long, ...): ResponseEntity<...> { ... }

    // Team Owner only
    @PutMapping("/{teamId}/settings")
    @PreAuthorize("@teamSecurity.isOwner(#teamId)")
    fun updateSettings(@PathVariable teamId: Long, ...): ResponseEntity<...> { ... }

    // System Admin only
    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun deleteTeam(@PathVariable teamId: Long): ResponseEntity<...> { ... }
}
```

### 3.2 Admin Bypass Logic

**Decision: System ADMIN bypasses Team/Project membership checks**

```kotlin
@Component("teamSecurity")
class TeamSecurityService(
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl,
) {
    /**
     * Check if current user can view team resources.
     * ADMIN users bypass the membership check.
     */
    fun isMember(teamId: Long): Boolean {
        val principal = SecurityContextHolder.getContext().authentication?.principal
            as? UserPrincipal ?: return false

        // Admin bypass
        if (principal.hasRole(UserRole.ADMIN)) {
            return true
        }

        // Check membership
        return teamMemberRepositoryDsl.findByTeamIdAndUserId(teamId, principal.userId) != null
    }

    /**
     * Check if current user can edit team resources (EDITOR or OWNER).
     */
    fun canEdit(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false

        // Admin bypass
        if (principal.hasRole(UserRole.ADMIN)) {
            return true
        }

        val membership = teamMemberRepositoryDsl.findByTeamIdAndUserId(teamId, principal.userId)
        return membership?.role in listOf(TeamRole.OWNER, TeamRole.EDITOR)
    }

    /**
     * Check if current user is team owner.
     */
    fun isOwner(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false

        // Admin bypass
        if (principal.hasRole(UserRole.ADMIN)) {
            return true
        }

        val membership = teamMemberRepositoryDsl.findByTeamIdAndUserId(teamId, principal.userId)
        return membership?.role == TeamRole.OWNER
    }

    private fun getCurrentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
```

### 3.3 Permission Matrix

#### Team Permissions

| Action | ADMIN | OWNER | EDITOR | VIEWER | Non-Member |
|--------|-------|-------|--------|--------|------------|
| View team info | Y | Y | Y | Y | N |
| View team members | Y | Y | Y | Y | N |
| View team resources | Y | Y | Y | Y | N |
| Execute resources | Y | Y | Y | Y | N |
| Create resources | Y | Y | Y | N | N |
| Update resources | Y | Y | Y | N | N |
| Delete resources | Y | Y | Y | N | N |
| Update team settings | Y | Y | N | N | N |
| Manage members | Y | N | N | N | N |
| Delete team | Y | N | N | N | N |

#### Project Permissions

| Action | ADMIN | OWNER | EDITOR | VIEWER | Non-Member |
|--------|-------|-------|--------|--------|------------|
| View project info | Y | Y | Y | Y | N |
| View project members | Y | Y | Y | Y | N |
| View SQL queries | Y | Y | Y | Y | N |
| Execute SQL queries | Y | Y | Y | Y | N |
| Create SQL queries | Y | Y | Y | N | N |
| Update SQL queries | Y | Y | Y | N | N |
| Delete SQL queries | Y | Y | Y | N | N |
| Update project settings | Y | Y | N | N | N |
| Manage members | Y | N | N | N | N |
| Manage resource refs | Y | N | N | N | N |
| Delete project | Y | N | N | N | N |

---

## 4. Entity Designs

### 4.1 ApiTokenEntity

```kotlin
@Entity
@Table(
    name = "api_token",
    indexes = [
        Index(name = "idx_api_token_user_id", columnList = "user_id"),
        Index(name = "idx_api_token_hash", columnList = "token_hash"),
        Index(name = "idx_api_token_deleted_at", columnList = "deleted_at"),
    ],
)
class ApiTokenEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @field:Size(max = 500)
    @Column(name = "description", length = 500)
    var description: String? = null,

    /**
     * SHA-256 hash of the token.
     * The actual token is only shown once at creation.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    /**
     * Token prefix for identification (e.g., "dli_abc123").
     * Stored for display in token list.
     */
    @Column(name = "token_prefix", nullable = false, length = 16)
    val tokenPrefix: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    var scopeType: ApiTokenScopeType = ApiTokenScopeType.INHERIT_USER,

    /**
     * JSON array of explicit scopes (when scopeType = EXPLICIT_SCOPE).
     * Example: ["read:team", "write:project:123"]
     */
    @Column(name = "scopes", columnDefinition = "TEXT")
    var scopes: String? = null,

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,

    @Column(name = "last_used_ip", length = 45)
    var lastUsedIp: String? = null,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "revoked_by")
    var revokedBy: Long? = null,
) : BaseEntity() {
    val isRevoked: Boolean
        get() = revokedAt != null

    val isExpired: Boolean
        get() = expiresAt?.isBefore(LocalDateTime.now()) == true

    val isValid: Boolean
        get() = !isDeleted && !isRevoked && !isExpired
}
```

### 4.2 Enum Definitions

```kotlin
// module-core-common/src/main/kotlin/com/dataops/basecamp/common/enums/AuthEnums.kt
package com.dataops.basecamp.common.enums

/**
 * API Token scope type - determines how permissions are calculated.
 *
 * Phase 1: INHERIT_USER only (MVP)
 * Phase 2: EXPLICIT_SCOPE support
 * Phase 3: HYBRID support
 */
enum class ApiTokenScopeType {
    /**
     * Token inherits all permissions of the owning user.
     * Simplest model - token acts exactly as the user would.
     */
    INHERIT_USER,

    /**
     * Token has explicit scopes defined at creation.
     * More restrictive - token can only do what scopes allow.
     * Example scopes: "read:team", "write:project:123", "execute:*"
     */
    EXPLICIT_SCOPE,

    /**
     * Token has both user permissions AND explicit scope restrictions.
     * Most flexible - intersection of user permissions and explicit scopes.
     */
    HYBRID
}

/**
 * Predefined scope patterns for explicit scope tokens.
 * Format: action:resource[:resourceId]
 */
object ApiTokenScopes {
    // Read scopes
    const val READ_ALL = "read:*"
    const val READ_TEAM = "read:team"
    const val READ_PROJECT = "read:project"
    const val READ_DATASET = "read:dataset"
    const val READ_METRIC = "read:metric"

    // Write scopes
    const val WRITE_ALL = "write:*"
    const val WRITE_TEAM = "write:team"
    const val WRITE_PROJECT = "write:project"
    const val WRITE_DATASET = "write:dataset"
    const val WRITE_METRIC = "write:metric"

    // Execute scopes
    const val EXECUTE_ALL = "execute:*"
    const val EXECUTE_DATASET = "execute:dataset"
    const val EXECUTE_QUERY = "execute:query"
    const val EXECUTE_WORKFLOW = "execute:workflow"

    // Resource-specific patterns
    fun readTeam(teamId: Long) = "read:team:$teamId"
    fun writeProject(projectId: Long) = "write:project:$projectId"
}
```

### 4.3 UserPrincipal

```kotlin
// module-core-domain/src/main/kotlin/com/dataops/basecamp/domain/security/UserPrincipal.kt
package com.dataops.basecamp.domain.security

import com.dataops.basecamp.common.enums.UserRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Unified principal for both OIDC users and API tokens.
 */
data class UserPrincipal(
    val userId: Long,
    val email: String,
    val displayName: String,
    val systemRole: UserRole,
    val isApiToken: Boolean = false,
    val apiTokenId: Long? = null,
    val tokenScopes: List<String>? = null,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(
            org.springframework.security.core.authority.SimpleGrantedAuthority(
                when (systemRole) {
                    UserRole.ADMIN -> "ROLE_ADMIN"
                    UserRole.CONSUMER -> "ROLE_USER"
                    UserRole.PUBLIC -> "ROLE_PUBLIC"
                }
            )
        )

    override fun getPassword(): String? = null
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true

    fun hasRole(role: UserRole): Boolean = systemRole == role

    fun hasScope(scope: String): Boolean {
        if (!isApiToken || tokenScopes == null) return true // User always has implicit scopes
        return tokenScopes.contains(scope) || tokenScopes.contains("*")
    }
}
```

---

## 5. Security Service Classes

### 5.1 TeamSecurityService

```kotlin
// module-core-domain/src/main/kotlin/com/dataops/basecamp/domain/security/TeamSecurityService.kt
package com.dataops.basecamp.domain.security

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryDsl
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

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
     * Check if current user can edit team resources (EDITOR or OWNER).
     */
    fun canEdit(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = teamMemberRepositoryDsl.findRoleByTeamIdAndUserId(teamId, principal.userId)
        return role in listOf(TeamRole.OWNER, TeamRole.EDITOR)
    }

    /**
     * Check if current user is team owner.
     */
    fun isOwner(teamId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = teamMemberRepositoryDsl.findRoleByTeamIdAndUserId(teamId, principal.userId)
        return role == TeamRole.OWNER
    }

    /**
     * Check if current user has at least the specified role in the team.
     */
    fun hasRole(teamId: Long, requiredRoles: Set<String>): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = teamMemberRepositoryDsl.findRoleByTeamIdAndUserId(teamId, principal.userId)
        return role?.name in requiredRoles
    }

    private fun getCurrentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
```

### 5.2 ProjectSecurityService

```kotlin
// module-core-domain/src/main/kotlin/com/dataops/basecamp/domain/security/ProjectSecurityService.kt
package com.dataops.basecamp.domain.security

import com.dataops.basecamp.common.enums.ProjectRole
import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.repository.project.ProjectMemberRepositoryDsl
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component("projectSecurity")
class ProjectSecurityService(
    private val projectMemberRepositoryDsl: ProjectMemberRepositoryDsl,
) {
    /**
     * Check if current user is a project member (any role).
     */
    fun isMember(projectId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true
        return projectMemberRepositoryDsl.existsByProjectIdAndUserId(projectId, principal.userId)
    }

    /**
     * Check membership by project name instead of ID.
     */
    fun isMemberByName(projectName: String): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true
        return projectMemberRepositoryDsl.existsByProjectNameAndUserId(projectName, principal.userId)
    }

    /**
     * Check if current user can edit project resources (EDITOR or OWNER).
     */
    fun canEdit(projectId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = projectMemberRepositoryDsl.findRoleByProjectIdAndUserId(projectId, principal.userId)
        return role in listOf(ProjectRole.OWNER, ProjectRole.EDITOR)
    }

    /**
     * Check if current user is project owner.
     */
    fun isOwner(projectId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = projectMemberRepositoryDsl.findRoleByProjectIdAndUserId(projectId, principal.userId)
        return role == ProjectRole.OWNER
    }

    /**
     * Check permission by folder ID (for SQL queries).
     */
    fun canEditFolder(folderId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val role = projectMemberRepositoryDsl.findRoleByFolderId(folderId, principal.userId)
        return role in listOf(ProjectRole.OWNER, ProjectRole.EDITOR)
    }

    private fun getCurrentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
```

### 5.3 QuerySecurityService

```kotlin
// module-core-domain/src/main/kotlin/com/dataops/basecamp/domain/security/QuerySecurityService.kt
package com.dataops.basecamp.domain.security

import com.dataops.basecamp.common.enums.ProjectRole
import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.repository.project.ProjectMemberRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SavedQueryRepositoryDsl
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component("querySecurity")
class QuerySecurityService(
    private val savedQueryRepositoryDsl: SavedQueryRepositoryDsl,
    private val projectMemberRepositoryDsl: ProjectMemberRepositoryDsl,
) {
    /**
     * Check if current user can view the query.
     * Requires project membership.
     */
    fun canView(queryId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val projectId = savedQueryRepositoryDsl.findProjectIdByQueryId(queryId) ?: return false
        return projectMemberRepositoryDsl.existsByProjectIdAndUserId(projectId, principal.userId)
    }

    /**
     * Check if current user can edit the query.
     * Requires EDITOR or OWNER role in the project.
     */
    fun canEdit(queryId: Long): Boolean {
        val principal = getCurrentPrincipal() ?: return false
        if (principal.hasRole(UserRole.ADMIN)) return true

        val projectId = savedQueryRepositoryDsl.findProjectIdByQueryId(queryId) ?: return false
        val role = projectMemberRepositoryDsl.findRoleByProjectIdAndUserId(projectId, principal.userId)
        return role in listOf(ProjectRole.OWNER, ProjectRole.EDITOR)
    }

    private fun getCurrentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
```

---

## 6. Controller Annotation Examples

### 6.1 TeamController

```kotlin
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/teams")
@PreAuthorize("hasRole('ROLE_USER')")  // Require authentication
class TeamController(
    private val teamService: TeamService,
) {
    // List all teams (any authenticated user)
    @GetMapping
    fun listTeams(@RequestParam params: ListTeamsRequest): ResponseEntity<...> { ... }

    // View team details (member only)
    @GetMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    fun getTeam(@PathVariable teamId: Long): ResponseEntity<...> { ... }

    // View team members (member only)
    @GetMapping("/{teamId}/members")
    @PreAuthorize("@teamSecurity.isMember(#teamId)")
    fun listMembers(@PathVariable teamId: Long): ResponseEntity<...> { ... }

    // Create resource (editor+)
    @PostMapping("/{teamId}/resources")
    @PreAuthorize("@teamSecurity.canEdit(#teamId)")
    fun createResource(
        @PathVariable teamId: Long,
        @RequestBody request: CreateResourceRequest,
    ): ResponseEntity<...> { ... }

    // Update team settings (owner only)
    @PutMapping("/{teamId}")
    @PreAuthorize("@teamSecurity.isOwner(#teamId)")
    fun updateTeam(
        @PathVariable teamId: Long,
        @RequestBody request: UpdateTeamRequest,
    ): ResponseEntity<...> { ... }

    // Create team (admin only)
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun createTeam(@RequestBody request: CreateTeamRequest): ResponseEntity<...> { ... }

    // Delete team (admin only)
    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun deleteTeam(@PathVariable teamId: Long): ResponseEntity<...> { ... }

    // Manage members (admin only)
    @PostMapping("/{teamId}/members")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun addMember(
        @PathVariable teamId: Long,
        @RequestBody request: AddMemberRequest,
    ): ResponseEntity<...> { ... }
}
```

### 6.2 ProjectController

```kotlin
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/projects")
@PreAuthorize("hasRole('ROLE_USER')")
class ProjectController(
    private val projectService: ProjectService,
) {
    // List all projects
    @GetMapping
    fun listProjects(): ResponseEntity<...> { ... }

    // View project details (member only)
    @GetMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.isMember(#projectId)")
    fun getProject(@PathVariable projectId: Long): ResponseEntity<...> { ... }

    // Create project (admin only)
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun createProject(@RequestBody request: CreateProjectRequest): ResponseEntity<...> { ... }

    // Update project (owner only)
    @PutMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.isOwner(#projectId)")
    fun updateProject(
        @PathVariable projectId: Long,
        @RequestBody request: UpdateProjectRequest,
    ): ResponseEntity<...> { ... }

    // Delete project (admin only)
    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun deleteProject(@PathVariable projectId: Long): ResponseEntity<...> { ... }
}
```

### 6.3 SqlQueryController

```kotlin
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/sql/queries")
@PreAuthorize("hasRole('ROLE_USER')")
class SqlQueryController(
    private val sqlQueryService: SqlQueryService,
) {
    // List queries (project member check via filter)
    @GetMapping
    fun listQueries(
        @RequestParam projectName: String?,
        @RequestParam searchText: String?,
    ): ResponseEntity<...> {
        // Service layer applies project membership filter
        ...
    }

    // Get query details (member via query -> project)
    @GetMapping("/{queryId}")
    @PreAuthorize("@querySecurity.canView(#queryId)")
    fun getQuery(@PathVariable queryId: Long): ResponseEntity<...> { ... }

    // Create query (editor via folder -> project)
    @PostMapping
    @PreAuthorize("@projectSecurity.canEditFolder(#request.folderId)")
    fun createQuery(@RequestBody request: CreateQueryRequest): ResponseEntity<...> { ... }

    // Update query (editor via query -> project)
    @PutMapping("/{queryId}")
    @PreAuthorize("@querySecurity.canEdit(#queryId)")
    fun updateQuery(
        @PathVariable queryId: Long,
        @RequestBody request: UpdateQueryRequest,
    ): ResponseEntity<...> { ... }

    // Delete query (editor via query -> project)
    @DeleteMapping("/{queryId}")
    @PreAuthorize("@querySecurity.canEdit(#queryId)")
    fun deleteQuery(@PathVariable queryId: Long): ResponseEntity<...> { ... }

    // Execute query (member via query -> project)
    @PostMapping("/{queryId}/run")
    @PreAuthorize("@querySecurity.canView(#queryId)")
    fun runQuery(
        @PathVariable queryId: Long,
        @RequestBody request: RunQueryRequest,
    ): ResponseEntity<...> { ... }
}
```

---

## 7. Public Endpoints

### 7.1 Public Endpoint Policy

| Category | Endpoint | Public | Description |
|----------|----------|--------|-------------|
| Health | `/api/health` | Yes | Health check |
| Health | `/api/info` | Yes | Application info |
| Actuator | `/actuator/**` | Yes | Spring Boot actuator |
| Swagger | `/swagger-ui/**` | Yes | API documentation |
| Swagger | `/v3/api-docs/**` | Yes | OpenAPI spec |
| CORS | `OPTIONS *` | Yes | CORS preflight |
| Auth | `POST /api/v1/auth/token` | Yes | Token exchange (credentials in body) |

### 7.2 Authenticated (No Resource Check)

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/auth/whoami` | Current user info |
| `GET /api/v1/auth/session` | Session status |
| `GET /api/v1/context/me` | User's teams/projects |
| `GET /api/v1/teams` | List all teams |
| `GET /api/v1/projects` | List all projects |

### 7.3 SecurityConfig Implementation

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers("/api/health", "/api/info").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                    // Everything else requires authentication
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }
            .addFilterBefore(apiTokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun apiTokenAuthenticationFilter(): ApiTokenAuthenticationFilter =
        ApiTokenAuthenticationFilter(apiTokenService)
}
```

---

## 8. API Token Management API

### 8.1 Token API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/auth/tokens` | List user's tokens | User |
| POST | `/api/v1/auth/tokens` | Create new token | User |
| GET | `/api/v1/auth/tokens/{tokenId}` | Get token details | User |
| DELETE | `/api/v1/auth/tokens/{tokenId}` | Revoke token | User |

### 8.2 Create Token

**`POST /api/v1/auth/tokens`**

```http
POST /api/v1/auth/tokens
Content-Type: application/json

{
  "name": "airflow-prod",
  "description": "Airflow production DAGs",
  "expiresAt": "2027-01-01T00:00:00Z",
  "scopeType": "INHERIT_USER"
}
```

**Response:** `201 Created`
```json
{
  "id": 123,
  "name": "airflow-prod",
  "tokenPrefix": "dli_abc123",
  "token": "dli_abc123xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "scopeType": "INHERIT_USER",
  "expiresAt": "2027-01-01T00:00:00Z",
  "createdAt": "2026-01-06T10:00:00Z"
}
```

> **Note:** The full `token` is only returned once at creation. Store it securely.

### 8.3 List Tokens

**`GET /api/v1/auth/tokens`**

**Response:**
```json
{
  "content": [
    {
      "id": 123,
      "name": "airflow-prod",
      "tokenPrefix": "dli_abc123",
      "scopeType": "INHERIT_USER",
      "expiresAt": "2027-01-01T00:00:00Z",
      "lastUsedAt": "2026-01-06T09:30:00Z",
      "createdAt": "2026-01-01T10:00:00Z"
    }
  ]
}
```

### 8.4 Revoke Token

**`DELETE /api/v1/auth/tokens/{tokenId}`**

**Response:** `204 No Content`

---

## 9. Development & Testing Strategy

### 9.1 Test Configuration (Mock User Filter)

```kotlin
// module-server-api/src/test/kotlin/com/dataops/basecamp/config/TestSecurityConfig.kt
@TestConfiguration
class TestSecurityConfig {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}

// Test with mock user
@SpringBootTest
@AutoConfigureMockMvc
class TeamControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(username = "admin@test.com", roles = ["ADMIN"])
    fun `admin can delete team`() {
        mockMvc.perform(delete("/api/v1/teams/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = ["USER"])
    fun `user cannot delete team`() {
        mockMvc.perform(delete("/api/v1/teams/1"))
            .andExpect(status().isForbidden)
    }
}
```

### 9.2 Custom Test Annotation

```kotlin
// Custom annotation for team member tests
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = TeamMemberSecurityContextFactory::class)
annotation class WithTeamMember(
    val teamId: Long,
    val role: String = "VIEWER",
    val userId: Long = 100L,
    val email: String = "member@test.com",
)

class TeamMemberSecurityContextFactory : WithSecurityContextFactory<WithTeamMember> {
    override fun createSecurityContext(annotation: WithTeamMember): SecurityContext {
        val principal = UserPrincipal(
            userId = annotation.userId,
            email = annotation.email,
            displayName = "Test Member",
            systemRole = UserRole.CONSUMER,
        )
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return SecurityContextHolder.createEmptyContext().apply { authentication = auth }
    }
}

// Usage
@Test
@WithTeamMember(teamId = 1, role = "EDITOR")
fun `editor can create resource`() { ... }
```

### 9.3 Development Profile (In-Memory Users)

```kotlin
// module-server-api/src/main/kotlin/com/dataops/basecamp/config/DevSecurityConfig.kt
@Configuration
@Profile("dev")
class DevSecurityConfig {
    @Bean
    fun inMemoryUserDetailsManager(): InMemoryUserDetailsManager {
        val admin = User.builder()
            .username("admin@test.com")
            .password("{noop}admin")
            .roles("ADMIN")
            .build()

        val user = User.builder()
            .username("user@test.com")
            .password("{noop}user")
            .roles("USER")
            .build()

        val viewer = User.builder()
            .username("viewer@test.com")
            .password("{noop}viewer")
            .roles("USER")
            .build()

        return InMemoryUserDetailsManager(admin, user, viewer)
    }

    @Bean
    @Primary
    fun devSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic { }
            .formLogin { }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/health", "/api/info").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }
            .build()
}
```

### 9.4 Application Properties

```yaml
# application-dev.yaml
spring:
  security:
    user:
      name: admin@test.com
      password: admin
      roles: ADMIN

# application-prod.yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}
```

---

## 10. API Token Scope Options (Future Discussion)

### 10.1 Option 1: Token Inherits Owner's Permissions

**Pros:**
- Simplest implementation
- Token acts exactly as user would
- No scope management complexity

**Cons:**
- Token has full user access (may be more than needed)
- Revoking user access revokes all tokens
- Difficult to limit token to specific resources

**Implementation:** MVP - Phase 1

### 10.2 Option 2: Explicit Scope per Token

**Pros:**
- Fine-grained control
- Token limited to specified actions
- Clear audit trail

**Cons:**
- Complex scope definition
- User must understand scope syntax
- Validation complexity

**Scope Syntax:**
```
read:team           - Read all teams
write:project:123   - Write to project 123
execute:*           - Execute any resource
```

**Implementation:** Phase 2

### 10.3 Option 3: Hybrid (Owner Permissions + Scope Restrictions)

**Pros:**
- Most flexible
- Can restrict token below user level
- Best for automation use cases

**Cons:**
- Most complex to implement
- Permission calculation requires intersection

**Example:**
- User has EDITOR role in Team A and Team B
- Token created with scope `read:team:A`
- Token can only READ Team A (not write, not Team B)

**Implementation:** Phase 3

### 10.4 Recommendation

| Phase | Scope Type | Use Case |
|-------|------------|----------|
| Phase 1 (MVP) | `INHERIT_USER` only | Quick automation setup |
| Phase 2 | Add `EXPLICIT_SCOPE` | Fine-grained CI/CD tokens |
| Phase 3 | Add `HYBRID` | Advanced security requirements |

---

## 11. Module Placement

### 11.1 Location Overview

| Component | Module | Package | Note |
|-----------|--------|---------|------|
| ApiTokenEntity | module-core-domain | `entity/auth/` | |
| UserPrincipal | module-core-domain | `security/` | Pure data class |
| Security Services | **module-server-api** | `security/` | Uses SecurityContextHolder (Spring Security) |
| Commands | module-core-domain | `command/auth/` | Input validation |
| Projections | module-core-domain | `projection/auth/` | Output objects |
| Repositories (interfaces) | module-core-domain | `repository/auth/` | |
| Repositories (impl) | module-core-infra | `repository/auth/` | |
| Filters | module-server-api | `filter/` | |
| Config | module-server-api | `config/` | |
| Controllers | module-server-api | `controller/` | |
| DTOs | module-server-api | `dto/auth/` | |
| Enums | module-core-common | `enums/AuthEnums.kt` | |

> **Note:** Security services (`TeamSecurityService`, `ProjectSecurityService`, `QuerySecurityService`) are placed in `module-server-api/security/` because they depend on `SecurityContextHolder` which is a Spring Security framework component. Per hexagonal architecture, framework-dependent code belongs in the outer layers.

### 11.2 File Structure

```
module-core-common/
└── src/main/kotlin/com/dataops/basecamp/common/enums/
    └── AuthEnums.kt              # ApiTokenScopeType, ApiTokenScopes

module-core-domain/
└── src/main/kotlin/com/dataops/basecamp/domain/
    ├── entity/auth/
    │   └── ApiTokenEntity.kt
    ├── security/
    │   └── UserPrincipal.kt      # Pure data class only
    ├── command/auth/
    │   └── AuthCommands.kt       # CreateApiTokenCommand, RevokeApiTokenCommand
    ├── projection/auth/
    │   └── AuthProjections.kt    # ApiTokenSummaryProjection, ApiTokenCreatedProjection
    ├── service/
    │   └── ApiTokenService.kt    # Token validation and management
    └── repository/auth/
        ├── ApiTokenRepositoryJpa.kt
        └── ApiTokenRepositoryDsl.kt

module-core-infra/
└── src/main/kotlin/com/dataops/basecamp/infra/repository/auth/
    ├── ApiTokenRepositoryJpaImpl.kt
    └── ApiTokenRepositoryDslImpl.kt

module-server-api/
└── src/main/kotlin/com/dataops/basecamp/
    ├── config/
    │   ├── SecurityConfig.kt
    │   ├── DevSecurityConfig.kt
    │   └── CorsConfig.kt
    ├── security/                 # Security beans with Spring Security deps
    │   ├── TeamSecurityService.kt
    │   ├── ProjectSecurityService.kt
    │   ├── QuerySecurityService.kt
    │   └── JwtAuthenticationConverter.kt
    ├── filter/
    │   └── ApiTokenAuthenticationFilter.kt
    ├── controller/
    │   └── AuthController.kt
    └── dto/auth/
        └── AuthDtos.kt
```

---

## 12. Database Schema

### 12.1 Tables

```sql
-- API Token table
CREATE TABLE api_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(16) NOT NULL,
    scope_type VARCHAR(30) NOT NULL DEFAULT 'INHERIT_USER',
    scopes TEXT,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    last_used_ip VARCHAR(45),
    revoked_at TIMESTAMP,
    revoked_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    INDEX idx_api_token_user_id (user_id),
    INDEX idx_api_token_hash (token_hash),
    INDEX idx_api_token_deleted_at (deleted_at),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

## 13. Implementation Phases

### Phase 1: Core ACL (MVP)

| Component | Status | Description |
|-----------|--------|-------------|
| UserPrincipal | Planned | Unified principal class |
| TeamSecurityService | Planned | Team permission checks |
| ProjectSecurityService | Planned | Project permission checks |
| QuerySecurityService | Planned | SQL query permission checks |
| Admin bypass logic | Planned | ADMIN role bypasses resource checks |
| Dev profile config | Planned | In-memory users for local dev |
| Test configuration | Planned | Mock user support |

### Phase 2: API Token

| Component | Status | Description |
|-----------|--------|-------------|
| ApiTokenEntity | Planned | Token storage |
| Token authentication filter | Planned | Authenticate via token header |
| Token management API | Planned | CRUD for tokens |
| CLI integration | Planned | `dli auth token` commands |
| INHERIT_USER scope | Planned | Token inherits user permissions |

### Phase 3: Enhanced Scopes (Future)

| Component | Status | Description |
|-----------|--------|-------------|
| EXPLICIT_SCOPE type | Future | Fine-grained scope definitions |
| HYBRID scope type | Future | Intersection of user + explicit scopes |
| Scope validation | Future | Validate scopes on API calls |

---

## 14. Implementation Checklist

### 14.1 Phase 1 Checklist

- [ ] Create `AuthEnums.kt` in `module-core-common/enums/`
- [ ] Create `UserPrincipal.kt` in `module-core-domain/security/`
- [ ] Create `TeamSecurityService.kt` with `@Component("teamSecurity")`
- [ ] Create `ProjectSecurityService.kt` with `@Component("projectSecurity")`
- [ ] Create `QuerySecurityService.kt` with `@Component("querySecurity")`
- [ ] Update `SecurityConfig.kt` with `@EnableMethodSecurity`
- [ ] Create `DevSecurityConfig.kt` with in-memory users
- [ ] Create `TestSecurityConfig.kt` for tests
- [ ] Add `@PreAuthorize` annotations to existing controllers
- [ ] Write unit tests for security services
- [ ] Write integration tests with mock users

### 14.2 Phase 2 Checklist

- [ ] Create `ApiTokenEntity.kt`
- [ ] Create `ApiTokenRepositoryJpa.kt` and `ApiTokenRepositoryDsl.kt`
- [ ] Create `ApiTokenRepositoryJpaImpl.kt` and `ApiTokenRepositoryDslImpl.kt`
- [ ] Create `ApiTokenAuthenticationFilter.kt`
- [ ] Create `AuthController.kt` for token CRUD
- [ ] Create `AuthDtos.kt`
- [ ] Update CLI to support API token authentication
- [ ] Write tests for token authentication flow
- [ ] Document token usage in CLI README

---

## Appendix A: Design Decisions Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Auth scope | User + API Token | Support automation (Airflow, CI/CD) |
| 2 | Permission pattern | Custom Security Expressions | Consistent with SQL_FEATURE.md, better IDE support |
| 3 | Admin bypass | Yes | Administrative convenience, emergency access |
| 4 | Token scope (MVP) | INHERIT_USER | Simplest implementation for initial release |
| 5 | Local dev auth | In-memory users | Easy setup, no external dependencies |
| 6 | Test auth | Mock user filter | Fast tests, no security overhead |
| 7 | Token storage | Hash only | Security best practice, token shown once |
| 8 | Token prefix | `dli_` | Easy identification in logs/configs |

---

## Appendix B: Security Expression Reference

| Expression | Component | Description |
|------------|-----------|-------------|
| `@teamSecurity.isMember(#teamId)` | TeamSecurityService | Any team member |
| `@teamSecurity.canEdit(#teamId)` | TeamSecurityService | Team EDITOR or OWNER |
| `@teamSecurity.isOwner(#teamId)` | TeamSecurityService | Team OWNER only |
| `@projectSecurity.isMember(#projectId)` | ProjectSecurityService | Any project member |
| `@projectSecurity.canEdit(#projectId)` | ProjectSecurityService | Project EDITOR or OWNER |
| `@projectSecurity.isOwner(#projectId)` | ProjectSecurityService | Project OWNER only |
| `@projectSecurity.canEditFolder(#folderId)` | ProjectSecurityService | EDITOR+ via folder |
| `@querySecurity.canView(#queryId)` | QuerySecurityService | Member via query |
| `@querySecurity.canEdit(#queryId)` | QuerySecurityService | EDITOR+ via query |
| `hasRole('ROLE_ADMIN')` | Built-in | System ADMIN role |
| `hasRole('ROLE_USER')` | Built-in | Authenticated user |

---

## Appendix C: CLI Authentication Integration

### C.1 CLI Configuration

```yaml
# ~/.dli/config.yaml
server:
  url: https://basecamp.example.com

# Option 1: API Token (recommended for automation)
auth:
  type: token
  api_token: ${DLI_API_TOKEN}  # or hardcoded value

# Option 2: OIDC (interactive)
auth:
  type: oidc
  client_id: dli-cli
  issuer_url: https://keycloak.example.com/realms/dataops
```

### C.2 CLI Commands

```bash
# Interactive login (OIDC device flow)
dli auth login
# > Opening browser for authentication...
# > Successfully logged in as user@example.com

# Check authentication status
dli auth status
# > Authenticated as: user@example.com
# > Token expires: 2026-01-07T10:00:00Z

# Create API token (requires login)
dli auth token create --name "airflow-prod"
# > Token created: dli_xxxxxxxxxxxx
# > Store this token securely - it won't be shown again

# List tokens
dli auth token list
# > ID    NAME           CREATED      LAST USED    EXPIRES
# > 123   airflow-prod   2026-01-01   2026-01-06   2027-01-01

# Revoke token
dli auth token revoke 123
# > Token 123 revoked

# Logout
dli auth logout
# > Logged out successfully
```

---

## Appendix D: SQL_FEATURE.md Alignment

### Bean Naming Update Required

SQL_FEATURE.md Section 4.3 uses `@projectMemberCheck` bean name for permission checks.
This ACL specification introduces a more modular approach with separate security service beans.

**Migration from SQL_FEATURE.md patterns:**

| SQL_FEATURE.md (Current) | ACL_FEATURE.md (New) |
|--------------------------|----------------------|
| `@projectMemberCheck.isMember(#projectName)` | `@projectSecurity.isMemberByName(#projectName)` |
| `@projectMemberCheck.hasRole(#folderId, {'OWNER', 'EDITOR'})` | `@projectSecurity.canEditFolder(#folderId)` |
| `@projectMemberCheck.canEditQuery(#queryId)` | `@querySecurity.canEdit(#queryId)` |

**Rationale for separate beans:**
- **Single Responsibility:** Each security service handles one resource type
- **Testability:** Easier to mock individual security services
- **Clarity:** Clear separation between team, project, and query permissions

**SQL_FEATURE.md should be updated** to reference the new security service bean names defined in this specification.

---

---

## Appendix E: Required Repository Interface Definitions

### E.1 TeamMemberRepositoryDsl (Addition)

```kotlin
// module-core-domain/repository/team/TeamMemberRepositoryDsl.kt
interface TeamMemberRepositoryDsl {
    fun existsByTeamIdAndUserId(teamId: Long, userId: Long): Boolean
    fun findRoleByTeamIdAndUserId(teamId: Long, userId: Long): TeamRole?
    fun findByTeamIdAndUserId(teamId: Long, userId: Long): TeamMemberEntity?
}
```

### E.2 ProjectMemberRepositoryDsl (Addition)

```kotlin
// module-core-domain/repository/project/ProjectMemberRepositoryDsl.kt
interface ProjectMemberRepositoryDsl {
    fun existsByProjectIdAndUserId(projectId: Long, userId: Long): Boolean
    fun existsByProjectNameAndUserId(projectName: String, userId: Long): Boolean
    fun findRoleByProjectIdAndUserId(projectId: Long, userId: Long): ProjectRole?
    fun findRoleByFolderId(folderId: Long, userId: Long): ProjectRole?
}
```

### E.3 SavedQueryRepositoryDsl (Addition)

```kotlin
// module-core-domain/repository/sql/SavedQueryRepositoryDsl.kt (add method)
interface SavedQueryRepositoryDsl {
    // ... existing methods
    fun findProjectIdByQueryId(queryId: Long): Long?
}
```

### E.4 ApiTokenRepositoryDsl

```kotlin
// module-core-domain/repository/auth/ApiTokenRepositoryDsl.kt
interface ApiTokenRepositoryDsl {
    fun findByTokenHash(tokenHash: String): ApiTokenEntity?
    fun findByUserIdAndNotRevoked(userId: Long): List<ApiTokenEntity>
}
```

---

## Appendix F: Command/Projection Classes

### F.1 AuthCommands.kt

```kotlin
// module-core-domain/command/auth/AuthCommands.kt
package com.dataops.basecamp.domain.command.auth

import com.dataops.basecamp.common.enums.ApiTokenScopeType
import java.time.LocalDateTime

data class CreateApiTokenCommand(
    val name: String,
    val description: String? = null,
    val expiresAt: LocalDateTime? = null,
    val scopeType: ApiTokenScopeType = ApiTokenScopeType.INHERIT_USER,
    val scopes: List<String>? = null,
) {
    init {
        require(name.isNotBlank()) { "Token name cannot be blank" }
        require(name.length <= 100) { "Token name must not exceed 100 characters" }
        if (scopeType == ApiTokenScopeType.EXPLICIT_SCOPE) {
            require(!scopes.isNullOrEmpty()) { "Scopes required for EXPLICIT_SCOPE type" }
        }
    }
}

data class RevokeApiTokenCommand(
    val tokenId: Long,
)
```

### F.2 AuthProjections.kt

```kotlin
// module-core-domain/projection/auth/AuthProjections.kt
package com.dataops.basecamp.domain.projection.auth

import com.dataops.basecamp.common.enums.ApiTokenScopeType
import java.time.LocalDateTime

data class ApiTokenSummaryProjection(
    val id: Long,
    val name: String,
    val tokenPrefix: String,
    val scopeType: ApiTokenScopeType,
    val expiresAt: LocalDateTime?,
    val lastUsedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val isRevoked: Boolean,
    val isExpired: Boolean,
)

/**
 * Returned only once when a token is created.
 * The full token value is included.
 */
data class ApiTokenCreatedProjection(
    val id: Long,
    val name: String,
    val tokenPrefix: String,
    val token: String,  // Full token - shown only once
    val scopeType: ApiTokenScopeType,
    val expiresAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)
```

---

## Appendix G: ApiTokenService Implementation

```kotlin
// module-core-domain/service/ApiTokenService.kt
package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ApiTokenScopeType
import com.dataops.basecamp.domain.command.auth.CreateApiTokenCommand
import com.dataops.basecamp.domain.entity.auth.ApiTokenEntity
import com.dataops.basecamp.domain.projection.auth.ApiTokenCreatedProjection
import com.dataops.basecamp.domain.projection.auth.ApiTokenSummaryProjection
import com.dataops.basecamp.domain.repository.auth.ApiTokenRepositoryDsl
import com.dataops.basecamp.domain.repository.auth.ApiTokenRepositoryJpa
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import com.dataops.basecamp.domain.security.UserPrincipal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

@Service
@Transactional(readOnly = true)
class ApiTokenService(
    private val apiTokenRepositoryJpa: ApiTokenRepositoryJpa,
    private val apiTokenRepositoryDsl: ApiTokenRepositoryDsl,
    private val userRepositoryJpa: UserRepositoryJpa,
) {
    companion object {
        private const val TOKEN_PREFIX = "dli_"
        private const val TOKEN_LENGTH = 32
    }

    /**
     * Validate an API token and return the associated user principal.
     * Updates last_used_at on successful validation.
     */
    @Transactional
    fun validateToken(tokenValue: String): UserPrincipal? {
        if (!tokenValue.startsWith(TOKEN_PREFIX)) return null

        val hash = hashToken(tokenValue)
        val token = apiTokenRepositoryDsl.findByTokenHash(hash) ?: return null

        if (!token.isValid) return null

        val user = userRepositoryJpa.findById(token.userId) ?: return null

        // Update last used timestamp
        token.lastUsedAt = LocalDateTime.now()
        apiTokenRepositoryJpa.save(token)

        return UserPrincipal(
            userId = user.id,
            email = user.email,
            displayName = user.username,
            systemRole = user.role,
            isApiToken = true,
            apiTokenId = token.id,
            tokenScopes = parseScopes(token.scopes),
        )
    }

    /**
     * Create a new API token for the given user.
     * The full token is only returned in this response.
     */
    @Transactional
    fun createToken(userId: Long, command: CreateApiTokenCommand): ApiTokenCreatedProjection {
        val rawToken = generateToken()
        val tokenHash = hashToken(rawToken)
        val tokenPrefix = rawToken.substring(0, TOKEN_PREFIX.length + 8)

        val entity = ApiTokenEntity(
            userId = userId,
            name = command.name,
            description = command.description,
            tokenHash = tokenHash,
            tokenPrefix = tokenPrefix,
            scopeType = command.scopeType,
            scopes = command.scopes?.let { Json.encodeToString(it) },
            expiresAt = command.expiresAt,
        )

        val saved = apiTokenRepositoryJpa.save(entity)

        return ApiTokenCreatedProjection(
            id = saved.id,
            name = saved.name,
            tokenPrefix = saved.tokenPrefix,
            token = rawToken,
            scopeType = saved.scopeType,
            expiresAt = saved.expiresAt,
            createdAt = saved.createdAt,
        )
    }

    /**
     * List all active tokens for a user.
     */
    fun listTokens(userId: Long): List<ApiTokenSummaryProjection> {
        return apiTokenRepositoryDsl.findByUserIdAndNotRevoked(userId).map {
            ApiTokenSummaryProjection(
                id = it.id,
                name = it.name,
                tokenPrefix = it.tokenPrefix,
                scopeType = it.scopeType,
                expiresAt = it.expiresAt,
                lastUsedAt = it.lastUsedAt,
                createdAt = it.createdAt,
                isRevoked = it.isRevoked,
                isExpired = it.isExpired,
            )
        }
    }

    /**
     * Revoke a token (soft delete by setting revokedAt).
     */
    @Transactional
    fun revokeToken(tokenId: Long, revokedByUserId: Long) {
        val token = apiTokenRepositoryJpa.findById(tokenId)
            ?: throw IllegalArgumentException("Token not found")

        token.revokedAt = LocalDateTime.now()
        token.revokedBy = revokedByUserId
        apiTokenRepositoryJpa.save(token)
    }

    private fun generateToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(TOKEN_LENGTH)
        random.nextBytes(bytes)
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }

    private fun parseScopes(scopes: String?): List<String>? =
        scopes?.let { Json.decodeFromString<List<String>>(it) }
}
```

---

## Appendix H: JwtAuthenticationConverter

```kotlin
// module-server-api/security/JwtAuthenticationConverter.kt
package com.dataops.basecamp.security

import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.security.UserPrincipal
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationConverter : Converter<Jwt, Authentication> {

    override fun convert(jwt: Jwt): Authentication {
        val principal = extractPrincipal(jwt)
        return UsernamePasswordAuthenticationToken(
            principal,
            jwt,
            principal.authorities,
        )
    }

    private fun extractPrincipal(jwt: Jwt): UserPrincipal {
        val userId = jwt.getClaimAsString("sub")?.toLongOrNull()
            ?: throw IllegalArgumentException("Missing 'sub' claim in JWT")

        val email = jwt.getClaimAsString("email")
            ?: jwt.getClaimAsString("preferred_username")
            ?: throw IllegalArgumentException("Missing email/username in JWT")

        val displayName = jwt.getClaimAsString("name")
            ?: jwt.getClaimAsString("given_name")
            ?: email

        val systemRole = extractSystemRole(jwt)

        return UserPrincipal(
            userId = userId,
            email = email,
            displayName = displayName,
            systemRole = systemRole,
            isApiToken = false,
        )
    }

    private fun extractSystemRole(jwt: Jwt): UserRole {
        // Try Keycloak realm_access.roles format
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        val roles = (realmAccess?.get("roles") as? List<*>)?.filterIsInstance<String>()

        return when {
            roles?.contains("admin") == true -> UserRole.ADMIN
            roles?.contains("ADMIN") == true -> UserRole.ADMIN
            roles?.contains("consumer") == true -> UserRole.CONSUMER
            roles?.contains("CONSUMER") == true -> UserRole.CONSUMER
            else -> UserRole.CONSUMER // Default role
        }
    }
}
```

---

## Appendix I: CORS Configuration

```kotlin
// module-server-api/config/CorsConfig.kt
package com.dataops.basecamp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            // Development origins
            allowedOrigins = listOf(
                "http://localhost:3000",  // React dev server
                "http://localhost:5173",  // Vite dev server
            )
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }
}
```

---

## Appendix J: ApiTokenAuthenticationFilter

```kotlin
// module-server-api/filter/ApiTokenAuthenticationFilter.kt
package com.dataops.basecamp.filter

import com.dataops.basecamp.domain.service.ApiTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiTokenAuthenticationFilter(
    private val apiTokenService: ApiTokenService,
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private const val API_TOKEN_HEADER = "X-API-Token"
        private const val DLI_TOKEN_PREFIX = "dli_"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)

        if (token != null && token.startsWith(DLI_TOKEN_PREFIX)) {
            val principal = apiTokenService.validateToken(token)

            if (principal != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.authorities,
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        // Check X-API-Token header first
        request.getHeader(API_TOKEN_HEADER)?.let { return it }

        // Check Authorization header with Bearer prefix
        val authHeader = request.getHeader("Authorization")
        if (authHeader?.startsWith(BEARER_PREFIX) == true) {
            val token = authHeader.substring(BEARER_PREFIX.length)
            // Only process if it's a DLI token (not JWT)
            if (token.startsWith(DLI_TOKEN_PREFIX)) {
                return token
            }
        }

        return null
    }
}
```

---

## Appendix K: Implementation Review Notes

This section documents the feedback from the implementation review by the feature-basecamp-server agent.

### K.1 Issues Addressed in v1.1.0

| Issue | Resolution |
|-------|------------|
| Security Services in wrong module | Moved to `module-server-api/security/` |
| Missing Repository interfaces | Added in Appendix E |
| Missing Command/Projection classes | Added in Appendix F |
| Missing ApiTokenService | Added in Appendix G |
| Missing JwtAuthenticationConverter | Added in Appendix H |
| Missing CORS configuration | Added in Appendix I |
| Missing ApiTokenAuthenticationFilter | Added in Appendix J |

### K.2 SQL_FEATURE.md Bean Naming Migration

SQL_FEATURE.md Section 4.3 uses `@projectMemberCheck` bean name. This should be migrated to the new modular pattern:

| SQL_FEATURE.md (Current) | ACL_FEATURE.md (New) |
|--------------------------|----------------------|
| `@projectMemberCheck.isMember(#projectName)` | `@projectSecurity.isMemberByName(#projectName)` |
| `@projectMemberCheck.hasRole(#folderId, {'OWNER', 'EDITOR'})` | `@projectSecurity.canEditFolder(#folderId)` |
| `@projectMemberCheck.canEditQuery(#queryId)` | `@querySecurity.canEdit(#queryId)` |

**Action Required:** Update SQL_FEATURE.md Section 4.3 to reference the new bean names, or create bean aliases for backward compatibility.

### K.3 Spring Boot 4 Considerations

1. **Method Security:** `@EnableMethodSecurity(prePostEnabled = true)` - `prePostEnabled` defaults to `true` in Spring Security 6+
2. **Filter Chain:** Use `SecurityFilterChain` bean pattern; avoid deprecated `WebSecurityConfigurerAdapter`
3. **CORS:** Configure via `CorsConfigurationSource` bean

---

**Last Updated:** 2026-01-07 (v1.1.0 - Added implementation review feedback)
