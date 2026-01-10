package com.dataops.basecamp.filter

import com.dataops.basecamp.config.MockUserProperties
import com.dataops.basecamp.config.SecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * MockAuthenticationFilter Unit Tests
 *
 * Tests the mock authentication filter used for local development and testing.
 * The filter should:
 * 1. Create mock authentication when no headers are provided (using defaults)
 * 2. Use custom user info from request headers when provided
 * 3. Set authentication in SecurityContextHolder
 */
@DisplayName("MockAuthenticationFilter")
class MockAuthenticationFilterTest {
    private lateinit var filter: MockAuthenticationFilter
    private lateinit var securityProperties: SecurityProperties
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: MockFilterChain

    @BeforeEach
    fun setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext()

        // Setup Spring mock objects
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        filterChain = MockFilterChain()

        // Default security properties
        securityProperties =
            SecurityProperties(
                mockAuthEnabled = true,
                mockUser =
                    MockUserProperties(
                        id = "1",
                        email = "dev@dataops.local",
                        roles = listOf("admin", "editor", "viewer"),
                    ),
            )
        filter = MockAuthenticationFilter(securityProperties)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @DisplayName("when no headers are provided")
    inner class NoHeaders {
        @Test
        @DisplayName("should create authentication with default mock user")
        fun `should create authentication with default mock user`() {
            // Given - no headers set

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val authentication = SecurityContextHolder.getContext().authentication
            assertThat(authentication).isNotNull
            assertThat(authentication!!.isAuthenticated).isTrue()

            val principal = authentication.principal as MockAuthenticatedUser
            assertThat(principal.id).isEqualTo("1")
            assertThat(principal.email).isEqualTo("dev@dataops.local")
            assertThat(principal.roles).containsExactlyInAnyOrder("admin", "editor", "viewer")
        }

        @Test
        @DisplayName("should set correct authorities based on default roles")
        fun `should set correct authorities based on default roles`() {
            // Given - no headers set

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val authentication = SecurityContextHolder.getContext().authentication!!
            val authorities = authentication.authorities.map { it.authority }
            assertThat(authorities).containsExactlyInAnyOrder(
                "ROLE_admin",
                "ROLE_editor",
                "ROLE_viewer",
            )
        }
    }

    @Nested
    @DisplayName("when custom headers are provided")
    inner class CustomHeaders {
        @Test
        @DisplayName("should use X-Mock-User-Id header for user ID")
        fun `should use X-Mock-User-Id header for user ID`() {
            // Given
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ID, "999")

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val principal = SecurityContextHolder.getContext().authentication!!.principal as MockAuthenticatedUser
            assertThat(principal.id).isEqualTo("999")
            // Other fields should use defaults
            assertThat(principal.email).isEqualTo("dev@dataops.local")
        }

        @Test
        @DisplayName("should use X-Mock-User-Email header for email")
        fun `should use X-Mock-User-Email header for email`() {
            // Given
            request.addHeader(MockAuthenticationFilter.HEADER_USER_EMAIL, "custom@test.com")

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val principal = SecurityContextHolder.getContext().authentication!!.principal as MockAuthenticatedUser
            assertThat(principal.email).isEqualTo("custom@test.com")
        }

        @Test
        @DisplayName("should use X-Mock-User-Roles header for roles")
        fun `should use X-Mock-User-Roles header for roles`() {
            // Given
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ROLES, "superadmin,operator")

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val principal = SecurityContextHolder.getContext().authentication!!.principal as MockAuthenticatedUser
            assertThat(principal.roles).containsExactlyInAnyOrder("superadmin", "operator")

            val authorities =
                SecurityContextHolder
                    .getContext()
                    .authentication!!
                    .authorities
                    .map { it.authority }
            assertThat(authorities).containsExactlyInAnyOrder("ROLE_superadmin", "ROLE_operator")
        }

        @Test
        @DisplayName("should trim whitespace from role names")
        fun `should trim whitespace from role names`() {
            // Given
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ROLES, "  admin  ,  editor  ")

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val principal = SecurityContextHolder.getContext().authentication!!.principal as MockAuthenticatedUser
            assertThat(principal.roles).containsExactlyInAnyOrder("admin", "editor")
        }

        @Test
        @DisplayName("should use all custom headers together")
        fun `should use all custom headers together`() {
            // Given
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ID, "42")
            request.addHeader(MockAuthenticationFilter.HEADER_USER_EMAIL, "test@example.com")
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ROLES, "readonly")

            // When
            filter.doFilter(request, response, filterChain)

            // Then
            val principal = SecurityContextHolder.getContext().authentication!!.principal as MockAuthenticatedUser
            assertThat(principal.id).isEqualTo("42")
            assertThat(principal.email).isEqualTo("test@example.com")
            assertThat(principal.roles).containsExactly("readonly")
        }
    }

    @Nested
    @DisplayName("when already authenticated")
    inner class AlreadyAuthenticated {
        @Test
        @DisplayName("should skip filter when authentication already exists")
        fun `should skip filter when authentication already exists`() {
            // Given - setup existing authentication
            val existingUser =
                MockAuthenticatedUser(
                    id = "existing-user",
                    email = "existing@test.com",
                    roles = listOf("existing-role"),
                )
            val existingAuth =
                UsernamePasswordAuthenticationToken(
                    existingUser,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_existing-role")),
                )
            SecurityContextHolder.getContext().authentication = existingAuth

            // Request headers that should be ignored
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ID, "new-id")
            request.addHeader(MockAuthenticationFilter.HEADER_USER_EMAIL, "new@test.com")
            request.addHeader(MockAuthenticationFilter.HEADER_USER_ROLES, "new-role")

            // When
            filter.doFilter(request, response, filterChain)

            // Then - existing authentication should be preserved
            val authentication = SecurityContextHolder.getContext().authentication!!
            val principal = authentication.principal as MockAuthenticatedUser
            assertThat(principal.id).isEqualTo("existing-user")
            assertThat(principal.email).isEqualTo("existing@test.com")
        }
    }

    @Nested
    @DisplayName("MockAuthenticatedUser")
    inner class MockAuthenticatedUserTest {
        @Test
        @DisplayName("getName should return user ID")
        fun `getName should return user ID`() {
            // Given
            val user =
                MockAuthenticatedUser(
                    id = "123",
                    email = "test@example.com",
                    roles = listOf("admin"),
                )

            // Then
            assertThat(user.name).isEqualTo("123")
        }

        @Test
        @DisplayName("getUserIdAsLong should return Long when ID is numeric")
        fun `getUserIdAsLong should return Long when ID is numeric`() {
            // Given
            val user =
                MockAuthenticatedUser(
                    id = "12345",
                    email = "test@example.com",
                    roles = listOf("admin"),
                )

            // Then
            assertThat(user.getUserIdAsLong()).isEqualTo(12345L)
        }

        @Test
        @DisplayName("getUserIdAsLong should return null when ID is not numeric")
        fun `getUserIdAsLong should return null when ID is not numeric`() {
            // Given
            val user =
                MockAuthenticatedUser(
                    id = "not-a-number",
                    email = "test@example.com",
                    roles = listOf("admin"),
                )

            // Then
            assertThat(user.getUserIdAsLong()).isNull()
        }

        @Test
        @DisplayName("should implement Principal interface")
        fun `should implement Principal interface`() {
            // Given
            val user =
                MockAuthenticatedUser(
                    id = "100",
                    email = "test@example.com",
                    roles = listOf("admin"),
                )

            // Then
            assertThat(user).isInstanceOf(java.security.Principal::class.java)
        }
    }
}
