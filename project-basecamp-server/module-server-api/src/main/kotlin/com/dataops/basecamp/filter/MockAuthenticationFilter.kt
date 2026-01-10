package com.dataops.basecamp.filter

import com.dataops.basecamp.config.SecurityProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

private val logger = KotlinLogging.logger {}

/**
 * Mock authentication filter for local development and testing.
 *
 * This filter bypasses OAuth2/JWT authentication and creates a mock authentication
 * based on request headers or default configuration.
 *
 * ## Request Header Authentication
 * - X-Mock-User-Id: User ID (defaults to configured mock user id)
 * - X-Mock-User-Email: User email (defaults to configured mock user email)
 * - X-Mock-User-Roles: Comma-separated roles (defaults to configured mock user roles)
 *
 * ## Example Usage in Tests
 * ```kotlin
 * mockMvc.get("/api/v1/pipelines") {
 *     header("X-Mock-User-Id", "123")
 *     header("X-Mock-User-Email", "test@example.com")
 *     header("X-Mock-User-Roles", "admin,editor")
 * }
 * ```
 *
 * **Security Warning**: This filter should ONLY be enabled in local/test profiles.
 * Never enable in production environments.
 */
class MockAuthenticationFilter(
    private val securityProperties: SecurityProperties,
) : OncePerRequestFilter() {
    companion object {
        const val HEADER_USER_ID = "X-Mock-User-Id"
        const val HEADER_USER_EMAIL = "X-Mock-User-Email"
        const val HEADER_USER_ROLES = "X-Mock-User-Roles"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Skip if already authenticated
        if (SecurityContextHolder.getContext().authentication?.isAuthenticated == true) {
            filterChain.doFilter(request, response)
            return
        }

        val mockUser = createMockUser(request)
        val authorities = mockUser.roles.map { SimpleGrantedAuthority("ROLE_$it") }

        val authentication =
            UsernamePasswordAuthenticationToken(
                mockUser,
                null,
                authorities,
            )

        SecurityContextHolder.getContext().authentication = authentication

        logger.debug {
            "Mock authentication set for user: id=${mockUser.id}, email=${mockUser.email}, roles=${mockUser.roles}"
        }

        filterChain.doFilter(request, response)
    }

    private fun createMockUser(request: HttpServletRequest): MockAuthenticatedUser {
        val userId = request.getHeader(HEADER_USER_ID) ?: securityProperties.mockUser.id
        val email = request.getHeader(HEADER_USER_EMAIL) ?: securityProperties.mockUser.email
        val roles =
            request.getHeader(HEADER_USER_ROLES)?.split(",")?.map { it.trim() }
                ?: securityProperties.mockUser.roles

        return MockAuthenticatedUser(
            id = userId,
            email = email,
            roles = roles,
        )
    }
}

/**
 * Principal object for mock authenticated users.
 *
 * Implements [java.security.Principal] so that [Authentication.getName()] returns the user ID.
 */
data class MockAuthenticatedUser(
    val id: String,
    val email: String,
    val roles: List<String>,
) : java.security.Principal {
    /**
     * Returns the user ID as the principal name.
     * This ensures `authentication.name` returns the correct user ID.
     */
    override fun getName(): String = id

    /**
     * Returns the user ID as a Long for database operations.
     * Returns null if the ID cannot be parsed as a Long.
     */
    fun getUserIdAsLong(): Long? = id.toLongOrNull()
}
