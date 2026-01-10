# Security API - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-10
> **Status:** Phase 1 Complete

---

## Executive Summary

This release implements OAuth2/OIDC security for the Basecamp API server, providing production-ready JWT authentication via Keycloak with mock authentication support for local development and testing.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Authentication Modes** | 2 (OAuth2/JWT, Mock) |
| **Total Tests** | 69 (43 SecurityContext + 14 SecurityConfig + 12 MockAuth) |
| **Test Success Rate** | 100% |
| **Profiles Supported** | 4 (local, test, dev, prod) |
| **Public Endpoints** | 7 |

---

## Implemented Components

### Core Files

| File | Lines | Description |
|------|-------|-------------|
| `SecurityConfig.kt` | 168 | Security filter chain configuration |
| `SecurityProperties.kt` | 29 | Configuration properties |
| `SecurityContext.kt` | 317 | User context utility object |
| `MockAuthenticationFilter.kt` | 114 | Mock auth for local/test |
| `application.yml` (security) | ~80 | OAuth2 and profile configuration |

### Test Files

| File | Tests | Description |
|------|-------|-------------|
| `SecurityContextTest.kt` | 43 | SecurityContext method tests (JWT, OIDC, Mock) |
| `SecurityConfigTest.kt` | 14 | Filter chain configuration tests |
| `MockAuthenticationFilterTest.kt` | 12 | Mock authentication tests |
| **Total** | **69** | Full coverage of security components |

### Keycloak Configuration

| File | Description |
|------|-------------|
| `_docker/keycloak/realm.json` | Realm config with users, clients, roles |

---

## Security Configuration

### SecurityConfig.kt

Two conditional security filter chains based on `app.security.mock-auth-enabled`:

```kotlin
@Bean
@ConditionalOnProperty(
    name = ["app.security.mock-auth-enabled"],
    havingValue = "false",
    matchIfMissing = true
)
fun oauth2SecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    // JWT validation via Keycloak
}

@Bean
@ConditionalOnProperty(
    name = ["app.security.mock-auth-enabled"],
    havingValue = "true"
)
fun mockSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    // MockAuthenticationFilter
}
```

### JWT Role Extraction

Extracts roles from multiple JWT claim locations:

| Source | Path | Authority Format |
|--------|------|------------------|
| Realm Roles | `realm_access.roles` | `ROLE_{role}` |
| Client Roles | `resource_access.{client}.roles` | `ROLE_{role}` |
| Scopes | `scope` | `SCOPE_{scope}` |

---

## SecurityContext Utility

### Supported Authentication Types

| Type | Class | Source |
|------|-------|--------|
| JWT | `JwtAuthenticationToken` | OAuth2 Resource Server |
| OIDC | `OAuth2AuthenticationToken` | OAuth2 Client (future) |
| Mock | `UsernamePasswordAuthenticationToken` | MockAuthenticationFilter |

### Method Implementation Summary

| Method | JWT | OIDC | Mock |
|--------|-----|------|------|
| `getCurrentUserId()` | `sub` claim | `subject` | `id` property |
| `getCurrentUsername()` | `email` or `preferred_username` | `email` | `email` property |
| `getCurrentRoles()` | Authorities filter | Authorities filter | `roles` property |
| `hasRole(role)` | Authority check | Authority check | Role list check |

---

## Mock Authentication

### MockAuthenticatedUser Data Class

```kotlin
data class MockAuthenticatedUser(
    val id: String,
    val email: String,
    val roles: List<String>,
) {
    fun getUserIdAsLong(): Long? = id.toLongOrNull()
}
```

### Header Override Support

| Header | Default Source |
|--------|----------------|
| `X-Mock-User-Id` | `app.security.mock-user.id` |
| `X-Mock-User-Email` | `app.security.mock-user.email` |
| `X-Mock-User-Roles` | `app.security.mock-user.roles` |

---

## Test Coverage

### SecurityContextTest.kt (43 tests)

| Test Group | Count | Coverage |
|------------|-------|----------|
| JWT Authentication | 12 | All getCurrentUser* methods |
| OIDC Authentication | 12 | OAuth2AuthenticationToken support |
| Mock Authentication | 10 | MockAuthenticatedUser handling |
| Edge Cases | 9 | Null, unauthenticated, malformed |

### SecurityConfigTest.kt (14 tests)

| Test Group | Count | Coverage |
|------------|-------|----------|
| Mock Mode Filter Chain | 5 | mockSecurityFilterChain |
| OAuth2 Mode Filter Chain | 5 | oauth2SecurityFilterChain |
| Public Endpoints | 4 | Unauthenticated access |

### MockAuthenticationFilterTest.kt (12 tests)

| Test Group | Count | Coverage |
|------------|-------|----------|
| Default Authentication | 4 | Using configured defaults |
| Header Override | 5 | Custom headers |
| Skip Behavior | 3 | Already authenticated cases |

---

## Keycloak Test Users

### User Credentials

| Username | Email | Password | Roles |
|----------|-------|----------|-------|
| admin | admin@dataops.local | admin123 | admin, editor, viewer |
| editor | editor@dataops.local | editor123 | editor, viewer |
| viewer | viewer@dataops.local | viewer123 | viewer |
| lambda | 1ambda@github.com | changeme | admin, editor, viewer |

### OAuth2 Clients

| Client ID | Grant Types | Use Case |
|-----------|-------------|----------|
| `application` | Authorization Code, Direct Access | Web UI |
| `cli-service` | Device Authorization, Direct Access | CLI Tool |

---

## Profile Configuration

### Profile-Specific Behavior

| Profile | `mock-auth-enabled` | OAuth2 Auto-Config | Use Case |
|---------|--------------------|--------------------|----------|
| `local` | `true` | Excluded | Local development |
| `test` | `true` | Excluded | Unit/Integration tests |
| `dev` | `false` | Enabled | Development with Keycloak |
| `prod` | `false` | Enabled | Production |

### Starting with Different Profiles

```bash
# Local development (no Keycloak)
./gradlew bootRun --args='--spring.profiles.active=local'

# With Keycloak (Docker stack)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Production
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## Implementation Notes

### Design Decisions

1. **Conditional Bean Configuration**: Using `@ConditionalOnProperty` for clean separation between mock and OAuth2 modes
2. **Profile-Based OAuth2 Exclusion**: Auto-configuration excluded in local/test to avoid Keycloak connection errors
3. **SecurityContext as Object**: Kotlin object for static-like access without Spring bean injection
4. **Comprehensive Role Extraction**: Support for realm roles, client roles, and scopes

### Known Limitations

1. **Numeric User ID Assumption**: `getCurrentUserId()` expects numeric `sub` claim (returns null for UUIDs)
2. **No Token Refresh**: Resource Server mode doesn't handle token refresh (client responsibility)
3. **Mock Mode Security Warning**: Should never be enabled in production

---

## Future Phases

### Phase 2: CLI Authentication

| Component | Description | Status |
|-----------|-------------|--------|
| Device Authorization Grant | OAuth2 device flow for CLI | Planned |
| Token Storage | Secure credential storage | Planned |
| `dli auth` Commands | Login/logout/status commands | Planned |

### Phase 3: UI Authentication

| Component | Description | Status |
|-----------|-------------|--------|
| Authorization Code + PKCE | Browser-safe OAuth2 flow | Planned |
| React Auth Provider | Context-based auth state | Planned |
| Silent Refresh | Background token renewal | Planned |

### Phase 4: Advanced Security

| Feature | Description | Status |
|---------|-------------|--------|
| API Keys | Service-to-service auth | Future |
| Rate Limiting | Per-user/role throttling | Future |
| Audit Integration | Security event logging | Future |

---

## Related Documentation

- [SECURITY_FEATURE.md](./SECURITY_FEATURE.md) - Feature specification
- [TEAM_RELEASE.md](./TEAM_RELEASE.md) - Team-based access control
- [AUDIT_RELEASE.md](./AUDIT_RELEASE.md) - Audit logging

---

## Verification Commands

```bash
# Run security tests
./gradlew :module-server-api:test --tests "*Security*" --tests "*MockAuth*"

# Count test methods
grep -c "@Test" module-server-api/src/test/kotlin/com/dataops/basecamp/util/SecurityContextTest.kt
grep -c "@Test" module-server-api/src/test/kotlin/com/dataops/basecamp/config/SecurityConfigTest.kt
grep -c "@Test" module-server-api/src/test/kotlin/com/dataops/basecamp/filter/MockAuthenticationFilterTest.kt

# Start with mock auth
MOCK_AUTH_ENABLED=true ./gradlew bootRun

# Start with OAuth2 (requires Keycloak)
MOCK_AUTH_ENABLED=false ./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

*Last Updated: 2026-01-10 | Phase 1 Complete*
