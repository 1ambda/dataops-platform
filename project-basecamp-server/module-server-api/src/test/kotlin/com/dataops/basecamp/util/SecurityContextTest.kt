package com.dataops.basecamp.util

import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.filter.MockAuthenticatedUser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/**
 * SecurityContext Utility Tests
 *
 * Tests the SecurityContext utility object for extracting user information
 * from various authentication types:
 * - JWT tokens (OAuth2 Resource Server mode)
 * - MockAuthenticatedUser (Local/Test mode)
 * - Unauthenticated state
 */
@DisplayName("SecurityContext")
class SecurityContextTest {
    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @DisplayName("with JWT Authentication")
    inner class JwtAuthentication {
        private fun createJwtToken(
            subject: String,
            email: String? = null,
            preferredUsername: String? = null,
            realmRoles: List<String> = emptyList(),
            scopes: String? = null,
        ): Jwt {
            val claims =
                mutableMapOf<String, Any>(
                    "sub" to subject,
                )
            email?.let { claims["email"] = it }
            preferredUsername?.let { claims["preferred_username"] = it }

            if (realmRoles.isNotEmpty()) {
                claims["realm_access"] = mapOf("roles" to realmRoles)
            }

            scopes?.let { claims["scope"] = it }

            return Jwt
                .withTokenValue("test-token")
                .headers { it["alg"] = "RS256" }
                .claims { it.putAll(claims) }
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
        }

        private fun setJwtAuthentication(
            jwt: Jwt,
            authorities: List<String> = emptyList(),
        ) {
            val grantedAuthorities = authorities.map { SimpleGrantedAuthority(it) }
            val authentication = JwtAuthenticationToken(jwt, grantedAuthorities, jwt.subject)
            SecurityContextHolder.getContext().authentication = authentication
        }

        @Test
        @DisplayName("getCurrentUserId should extract user ID from JWT subject")
        fun `getCurrentUserId should extract user ID from JWT subject`() {
            // Given
            val jwt = createJwtToken(subject = "12345")
            setJwtAuthentication(jwt)

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isEqualTo(12345L)
        }

        @Test
        @DisplayName("getCurrentUserId should return null for non-numeric subject")
        fun `getCurrentUserId should return null for non-numeric subject`() {
            // Given
            val jwt = createJwtToken(subject = "not-a-number")
            setJwtAuthentication(jwt)

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isNull()
        }

        @Test
        @DisplayName("getCurrentUserSubject should return JWT subject as string")
        fun `getCurrentUserSubject should return JWT subject as string`() {
            // Given
            val jwt = createJwtToken(subject = "user-uuid-12345")
            setJwtAuthentication(jwt)

            // When
            val subject = SecurityContext.getCurrentUserSubject()

            // Then
            assertThat(subject).isEqualTo("user-uuid-12345")
        }

        @Test
        @DisplayName("getCurrentUsername should extract email from JWT")
        fun `getCurrentUsername should extract email from JWT`() {
            // Given
            val jwt =
                createJwtToken(
                    subject = "123",
                    email = "user@example.com",
                )
            setJwtAuthentication(jwt)

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("user@example.com")
        }

        @Test
        @DisplayName("getCurrentUsername should fallback to preferred_username when email is missing")
        fun `getCurrentUsername should fallback to preferred_username when email is missing`() {
            // Given
            val jwt =
                createJwtToken(
                    subject = "123",
                    preferredUsername = "john.doe",
                )
            setJwtAuthentication(jwt)

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("john.doe")
        }

        @Test
        @DisplayName("getCurrentUsername should fallback to subject when email and username are missing")
        fun `getCurrentUsername should fallback to subject when email and username are missing`() {
            // Given
            val jwt = createJwtToken(subject = "user-123")
            setJwtAuthentication(jwt)

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("user-123")
        }

        @Test
        @DisplayName("getCurrentJwt should return the JWT token")
        fun `getCurrentJwt should return the JWT token`() {
            // Given
            val jwt = createJwtToken(subject = "123")
            setJwtAuthentication(jwt)

            // When
            val result = SecurityContext.getCurrentJwt()

            // Then
            assertThat(result).isNotNull
            assertThat(result?.subject).isEqualTo("123")
        }

        @Test
        @DisplayName("getCurrentRoles should extract roles from authorities")
        fun `getCurrentRoles should extract roles from authorities`() {
            // Given
            val jwt = createJwtToken(subject = "123")
            setJwtAuthentication(jwt, listOf("ROLE_admin", "ROLE_editor", "SCOPE_read"))

            // When
            val roles = SecurityContext.getCurrentRoles()

            // Then
            assertThat(roles).containsExactlyInAnyOrder("admin", "editor")
        }

        @Test
        @DisplayName("getCurrentAuthorities should return all authorities")
        fun `getCurrentAuthorities should return all authorities`() {
            // Given
            val jwt = createJwtToken(subject = "123")
            setJwtAuthentication(jwt, listOf("ROLE_admin", "SCOPE_read", "SCOPE_write"))

            // When
            val authorities = SecurityContext.getCurrentAuthorities()

            // Then
            assertThat(authorities).containsExactlyInAnyOrder(
                "ROLE_admin",
                "SCOPE_read",
                "SCOPE_write",
            )
        }

        @Test
        @DisplayName("hasRole should return true when user has the role")
        fun `hasRole should return true when user has the role`() {
            // Given
            val jwt = createJwtToken(subject = "123")
            setJwtAuthentication(jwt, listOf("ROLE_ADMIN", "ROLE_CONSUMER"))

            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isTrue()
            assertThat(SecurityContext.hasRole(UserRole.CONSUMER)).isTrue()
        }

        @Test
        @DisplayName("hasRole should return false when user does not have the role")
        fun `hasRole should return false when user does not have the role`() {
            // Given
            val jwt = createJwtToken(subject = "123")
            setJwtAuthentication(jwt, listOf("ROLE_CONSUMER"))

            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isFalse()
        }

        @Test
        @DisplayName("currentSession should return authenticated session with JWT info")
        fun `currentSession should return authenticated session with JWT info`() {
            // Given
            val jwt =
                createJwtToken(
                    subject = "42",
                    email = "jwt.user@example.com",
                )
            setJwtAuthentication(jwt, listOf("ROLE_admin", "ROLE_viewer"))

            // When
            val session = SecurityContext.currentSession()

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("42")
            assertThat(session.email).isEqualTo("jwt.user@example.com")
            assertThat(session.roles).containsExactlyInAnyOrder("admin", "viewer")
        }
    }

    @Nested
    @DisplayName("with OAuth2 OIDC Authentication")
    inner class OidcAuthentication {
        private fun createOidcUser(
            subject: String,
            email: String? = null,
        ): OidcUser {
            val claims =
                mutableMapOf<String, Any>(
                    "sub" to subject,
                )
            email?.let { claims["email"] = it }

            val idToken =
                OidcIdToken
                    .withTokenValue("test-oidc-token")
                    .claims { it.putAll(claims) }
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build()

            return DefaultOidcUser(emptyList(), idToken)
        }

        private fun setOidcAuthentication(
            oidcUser: OidcUser,
            authorities: List<String> = emptyList(),
        ) {
            val grantedAuthorities = authorities.map { SimpleGrantedAuthority(it) }
            val authentication =
                OAuth2AuthenticationToken(
                    DefaultOidcUser(grantedAuthorities, (oidcUser as DefaultOidcUser).idToken),
                    grantedAuthorities,
                    "keycloak",
                )
            SecurityContextHolder.getContext().authentication = authentication
        }

        @Test
        @DisplayName("getCurrentUserId should extract user ID from OIDC subject")
        fun `getCurrentUserId should extract user ID from OIDC subject`() {
            // Given
            val oidcUser = createOidcUser(subject = "12345")
            setOidcAuthentication(oidcUser)

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isEqualTo(12345L)
        }

        @Test
        @DisplayName("getCurrentUserId should return null for non-numeric OIDC subject")
        fun `getCurrentUserId should return null for non-numeric OIDC subject`() {
            // Given
            val oidcUser = createOidcUser(subject = "uuid-not-a-number")
            setOidcAuthentication(oidcUser)

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isNull()
        }

        @Test
        @DisplayName("getCurrentUserSubject should return OIDC subject")
        fun `getCurrentUserSubject should return OIDC subject`() {
            // Given
            val oidcUser = createOidcUser(subject = "oidc-user-uuid-12345")
            setOidcAuthentication(oidcUser)

            // When
            val subject = SecurityContext.getCurrentUserSubject()

            // Then
            assertThat(subject).isEqualTo("oidc-user-uuid-12345")
        }

        @Test
        @DisplayName("getCurrentUsername should return OIDC email")
        fun `getCurrentUsername should return OIDC email`() {
            // Given
            val oidcUser = createOidcUser(subject = "123", email = "oidc.user@example.com")
            setOidcAuthentication(oidcUser)

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("oidc.user@example.com")
        }

        @Test
        @DisplayName("getCurrentUsername should fallback to subject when email is missing")
        fun `getCurrentUsername should fallback to subject when email is missing`() {
            // Given
            val oidcUser = createOidcUser(subject = "oidc-user-456")
            setOidcAuthentication(oidcUser)

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("oidc-user-456")
        }

        @Test
        @DisplayName("getCurrentRoles should extract roles from OIDC authorities")
        fun `getCurrentRoles should extract roles from OIDC authorities`() {
            // Given
            val oidcUser = createOidcUser(subject = "123")
            setOidcAuthentication(oidcUser, listOf("ROLE_admin", "ROLE_viewer", "SCOPE_openid"))

            // When
            val roles = SecurityContext.getCurrentRoles()

            // Then
            assertThat(roles).containsExactlyInAnyOrder("admin", "viewer")
        }

        @Test
        @DisplayName("hasRole should return true when OIDC user has the role")
        fun `hasRole should return true when OIDC user has the role`() {
            // Given
            val oidcUser = createOidcUser(subject = "123")
            setOidcAuthentication(oidcUser, listOf("ROLE_ADMIN", "ROLE_CONSUMER"))

            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isTrue()
            assertThat(SecurityContext.hasRole(UserRole.CONSUMER)).isTrue()
        }

        @Test
        @DisplayName("hasRole should return false when OIDC user does not have the role")
        fun `hasRole should return false when OIDC user does not have the role`() {
            // Given
            val oidcUser = createOidcUser(subject = "123")
            setOidcAuthentication(oidcUser, listOf("ROLE_CONSUMER"))

            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isFalse()
        }

        @Test
        @DisplayName("getCurrentJwt should return null for OIDC authentication")
        fun `getCurrentJwt should return null for OIDC authentication`() {
            // Given
            val oidcUser = createOidcUser(subject = "123")
            setOidcAuthentication(oidcUser)

            // When
            val jwt = SecurityContext.getCurrentJwt()

            // Then
            assertThat(jwt).isNull()
        }

        @Test
        @DisplayName("currentSession should return authenticated session with OIDC user info")
        fun `currentSession should return authenticated session with OIDC user info`() {
            // Given
            val oidcUser = createOidcUser(subject = "77", email = "oidc.session@example.com")
            setOidcAuthentication(oidcUser, listOf("ROLE_editor", "ROLE_viewer"))

            // When
            val session = SecurityContext.currentSession()

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("77")
            assertThat(session.email).isEqualTo("oidc.session@example.com")
            assertThat(session.roles).containsExactlyInAnyOrder("editor", "viewer")
        }

        @Test
        @DisplayName("of should convert OAuth2AuthenticationToken to SessionResponse")
        fun `of should convert OAuth2AuthenticationToken to SessionResponse`() {
            // Given
            val claims =
                mapOf<String, Any>(
                    "sub" to "oidc-user-99",
                    "email" to "oidc@example.com",
                )
            val idToken =
                OidcIdToken
                    .withTokenValue("test-oidc-token")
                    .claims { it.putAll(claims) }
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build()
            val authorities = listOf(SimpleGrantedAuthority("ROLE_admin"))
            val oidcUser = DefaultOidcUser(authorities, idToken)
            val principal =
                OAuth2AuthenticationToken(
                    oidcUser,
                    authorities,
                    "keycloak",
                )

            // When
            val session = SecurityContext.of(principal)

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("oidc-user-99")
            assertThat(session.email).isEqualTo("oidc@example.com")
            assertThat(session.roles).containsExactly("admin")
        }
    }

    @Nested
    @DisplayName("with Mock Authentication")
    inner class MockAuthentication {
        private fun setMockAuthentication(
            id: String,
            email: String,
            roles: List<String>,
        ) {
            val mockUser = MockAuthenticatedUser(id, email, roles)
            val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
            val authentication = UsernamePasswordAuthenticationToken(mockUser, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication
        }

        @Test
        @DisplayName("getCurrentUserId should extract user ID from MockAuthenticatedUser")
        fun `getCurrentUserId should extract user ID from MockAuthenticatedUser`() {
            // Given
            setMockAuthentication(id = "999", email = "mock@test.com", roles = listOf("admin"))

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isEqualTo(999L)
        }

        @Test
        @DisplayName("getCurrentUserId should return null for non-numeric mock ID")
        fun `getCurrentUserId should return null for non-numeric mock ID`() {
            // Given
            setMockAuthentication(id = "uuid-string", email = "mock@test.com", roles = listOf("admin"))

            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isNull()
        }

        @Test
        @DisplayName("getCurrentUserSubject should return mock user ID")
        fun `getCurrentUserSubject should return mock user ID`() {
            // Given
            setMockAuthentication(id = "mock-user-123", email = "mock@test.com", roles = listOf("admin"))

            // When
            val subject = SecurityContext.getCurrentUserSubject()

            // Then
            assertThat(subject).isEqualTo("mock-user-123")
        }

        @Test
        @DisplayName("getCurrentUsername should return mock user email")
        fun `getCurrentUsername should return mock user email`() {
            // Given
            setMockAuthentication(id = "1", email = "mockuser@example.com", roles = listOf("admin"))

            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("mockuser@example.com")
        }

        @Test
        @DisplayName("getCurrentJwt should return null for mock authentication")
        fun `getCurrentJwt should return null for mock authentication`() {
            // Given
            setMockAuthentication(id = "1", email = "mock@test.com", roles = listOf("admin"))

            // When
            val jwt = SecurityContext.getCurrentJwt()

            // Then
            assertThat(jwt).isNull()
        }

        @Test
        @DisplayName("getCurrentRoles should return mock user roles")
        fun `getCurrentRoles should return mock user roles`() {
            // Given
            setMockAuthentication(id = "1", email = "mock@test.com", roles = listOf("admin", "editor"))

            // When
            val roles = SecurityContext.getCurrentRoles()

            // Then
            assertThat(roles).containsExactlyInAnyOrder("admin", "editor")
        }

        @Test
        @DisplayName("hasRole should check mock user roles")
        fun `hasRole should check mock user roles`() {
            // Given
            setMockAuthentication(id = "1", email = "mock@test.com", roles = listOf("ADMIN", "CONSUMER"))

            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isTrue()
            assertThat(SecurityContext.hasRole(UserRole.CONSUMER)).isTrue()
            assertThat(SecurityContext.hasRole(UserRole.PUBLIC)).isFalse()
        }

        @Test
        @DisplayName("currentSession should return authenticated session with mock user info")
        fun `currentSession should return authenticated session with mock user info`() {
            // Given
            setMockAuthentication(id = "100", email = "mock.session@test.com", roles = listOf("viewer"))

            // When
            val session = SecurityContext.currentSession()

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("100")
            assertThat(session.email).isEqualTo("mock.session@test.com")
            assertThat(session.roles).containsExactly("viewer")
        }
    }

    @Nested
    @DisplayName("when not authenticated")
    inner class NotAuthenticated {
        @Test
        @DisplayName("getCurrentUserId should return null")
        fun `getCurrentUserId should return null`() {
            // When
            val userId = SecurityContext.getCurrentUserId()

            // Then
            assertThat(userId).isNull()
        }

        @Test
        @DisplayName("getCurrentUserIdOrThrow should throw exception")
        fun `getCurrentUserIdOrThrow should throw exception`() {
            // When & Then
            assertThatThrownBy { SecurityContext.getCurrentUserIdOrThrow() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("User ID not found")
        }

        @Test
        @DisplayName("getCurrentUserSubject should return null")
        fun `getCurrentUserSubject should return null`() {
            // When
            val subject = SecurityContext.getCurrentUserSubject()

            // Then
            assertThat(subject).isNull()
        }

        @Test
        @DisplayName("getCurrentUsername should return unknown")
        fun `getCurrentUsername should return unknown`() {
            // When
            val username = SecurityContext.getCurrentUsername()

            // Then
            assertThat(username).isEqualTo("unknown")
        }

        @Test
        @DisplayName("getCurrentJwt should return null")
        fun `getCurrentJwt should return null`() {
            // When
            val jwt = SecurityContext.getCurrentJwt()

            // Then
            assertThat(jwt).isNull()
        }

        @Test
        @DisplayName("getCurrentRoles should return empty list")
        fun `getCurrentRoles should return empty list`() {
            // When
            val roles = SecurityContext.getCurrentRoles()

            // Then
            assertThat(roles).isEmpty()
        }

        @Test
        @DisplayName("getCurrentAuthorities should return empty list")
        fun `getCurrentAuthorities should return empty list`() {
            // When
            val authorities = SecurityContext.getCurrentAuthorities()

            // Then
            assertThat(authorities).isEmpty()
        }

        @Test
        @DisplayName("hasRole should return false")
        fun `hasRole should return false`() {
            // When & Then
            assertThat(SecurityContext.hasRole(UserRole.ADMIN)).isFalse()
        }

        @Test
        @DisplayName("currentSession should return unauthenticated session")
        fun `currentSession should return unauthenticated session`() {
            // When
            val session = SecurityContext.currentSession()

            // Then
            assertThat(session.authenticated).isFalse()
            assertThat(session.userId).isEmpty()
            assertThat(session.email).isEmpty()
            assertThat(session.roles).isEmpty()
        }

        @Test
        @DisplayName("of with null principal should return unauthenticated session")
        fun `of with null principal should return unauthenticated session`() {
            // When
            val session = SecurityContext.of(null)

            // Then
            assertThat(session.authenticated).isFalse()
            assertThat(session.userId).isEmpty()
            assertThat(session.email).isEmpty()
            assertThat(session.roles).isEmpty()
        }
    }

    @Nested
    @DisplayName("of method")
    inner class OfMethod {
        @Test
        @DisplayName("should convert JwtAuthenticationToken to SessionResponse")
        fun `should convert JwtAuthenticationToken to SessionResponse`() {
            // Given
            val jwt =
                Jwt
                    .withTokenValue("test-token")
                    .headers { it["alg"] = "RS256" }
                    .claims {
                        it["sub"] = "jwt-user-99"
                        it["email"] = "jwt@example.com"
                    }.issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build()
            val authorities = listOf(SimpleGrantedAuthority("ROLE_admin"))
            val principal = JwtAuthenticationToken(jwt, authorities, jwt.subject)

            // When
            val session = SecurityContext.of(principal)

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("jwt-user-99")
            assertThat(session.email).isEqualTo("jwt@example.com")
            assertThat(session.roles).containsExactly("admin")
        }

        @Test
        @DisplayName("should convert mock authentication from SecurityContext")
        fun `should convert mock authentication from SecurityContext`() {
            // Given
            val mockUser = MockAuthenticatedUser("50", "mock@of.test", listOf("editor"))
            val authorities = listOf(SimpleGrantedAuthority("ROLE_editor"))
            val authentication = UsernamePasswordAuthenticationToken(mockUser, null, authorities)
            SecurityContextHolder.getContext().authentication = authentication

            // When - passing null should read from SecurityContext
            val session = SecurityContext.of(null)

            // Then
            assertThat(session.authenticated).isTrue()
            assertThat(session.userId).isEqualTo("50")
            assertThat(session.email).isEqualTo("mock@of.test")
            assertThat(session.roles).containsExactly("editor")
        }
    }
}
