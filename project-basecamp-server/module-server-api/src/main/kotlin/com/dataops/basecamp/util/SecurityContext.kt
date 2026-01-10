package com.dataops.basecamp.util

import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.dto.SessionResponse
import com.dataops.basecamp.filter.MockAuthenticatedUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.security.Principal

/**
 * Security context utility for extracting user information from various authentication types.
 *
 * Supports:
 * - JWT tokens (OAuth2 Resource Server mode)
 * - OidcUser (OAuth2 Client mode)
 * - MockAuthenticatedUser (Local/Test mode)
 */
@Component
object SecurityContext {
    /**
     * Returns the current user ID.
     *
     * Extracts from:
     * - JWT: `sub` claim
     * - OidcUser: `subject` property
     * - MockAuthenticatedUser: `id` property
     *
     * @return User ID as Long, or null if not authenticated or ID cannot be parsed
     */
    fun getCurrentUserId(): Long? {
        val context = SecurityContextHolder.getContext()
        val authentication = context?.authentication ?: return null
        if (!authentication.isAuthenticated) {
            return null
        }

        return when (authentication) {
            is JwtAuthenticationToken -> {
                // JWT token - extract 'sub' claim
                val subject = authentication.token.subject
                subject?.toLongOrNull()
            }
            is OAuth2AuthenticationToken -> {
                // OIDC user - extract subject
                val oidcUser = authentication.principal as? OidcUser
                oidcUser?.subject?.toLongOrNull()
            }
            else -> {
                // Mock authenticated user or other types
                val principal = authentication.principal
                when (principal) {
                    is MockAuthenticatedUser -> principal.getUserIdAsLong()
                    is String -> principal.toLongOrNull()
                    else -> null
                }
            }
        }
    }

    /**
     * Returns the current user ID or throws an exception if not found.
     *
     * @throws IllegalArgumentException if user ID is not found in security context
     */
    fun getCurrentUserIdOrThrow(): Long =
        getCurrentUserId()
            ?: throw IllegalArgumentException("User ID not found in security context")

    /**
     * Returns the current user's subject (string identifier).
     *
     * This is useful when the user ID is a UUID or non-numeric string.
     *
     * @return User subject string, or null if not authenticated
     */
    fun getCurrentUserSubject(): String? {
        val context = SecurityContextHolder.getContext()
        val authentication = context?.authentication ?: return null
        if (!authentication.isAuthenticated) {
            return null
        }

        return when (authentication) {
            is JwtAuthenticationToken -> authentication.token.subject
            is OAuth2AuthenticationToken -> {
                val oidcUser = authentication.principal as? OidcUser
                oidcUser?.subject
            }
            else -> {
                val principal = authentication.principal
                when (principal) {
                    is MockAuthenticatedUser -> principal.id
                    is String -> principal
                    else -> null
                }
            }
        }
    }

    /**
     * Checks if the current user has the specified role.
     *
     * @param role The role to check
     * @return true if the user has the role
     */
    fun hasRole(role: UserRole): Boolean {
        val context = SecurityContextHolder.getContext()
        val authorities = context?.authentication?.authorities ?: return false

        val roleString = "ROLE_${role.name}"
        return authorities.any { it.authority.equals(roleString, ignoreCase = true) }
    }

    /**
     * Returns all roles for the current user.
     *
     * @return List of role names (without ROLE_ prefix)
     */
    fun getCurrentRoles(): List<String> {
        val context = SecurityContextHolder.getContext()
        return context
            ?.authentication
            ?.authorities
            ?.mapNotNull { it.authority }
            ?.filter { it.startsWith("ROLE_") }
            ?.map { it.removePrefix("ROLE_") }
            ?: emptyList()
    }

    /**
     * Returns all authorities (roles + scopes) for the current user.
     *
     * @return List of authority strings
     */
    fun getCurrentAuthorities(): List<String> {
        val context = SecurityContextHolder.getContext()
        return context?.authentication?.authorities?.mapNotNull { it.authority } ?: emptyList()
    }

    /**
     * Returns the current user's email.
     *
     * Extracts from:
     * - JWT: `email` claim
     * - OidcUser: `email` property
     * - MockAuthenticatedUser: `email` property
     *
     * @return User email, or "unknown" if not available
     */
    fun getCurrentUsername(): String {
        val context = SecurityContextHolder.getContext()
        val authentication = context?.authentication ?: return "unknown"
        if (!authentication.isAuthenticated) {
            return "unknown"
        }

        return when (authentication) {
            is JwtAuthenticationToken -> {
                authentication.token.getClaimAsString("email")
                    ?: authentication.token.getClaimAsString("preferred_username")
                    ?: authentication.token.subject
                    ?: "unknown"
            }
            is OAuth2AuthenticationToken -> {
                val oidcUser = authentication.principal as? OidcUser
                oidcUser?.email ?: oidcUser?.subject ?: "unknown"
            }
            else -> {
                val principal = authentication.principal
                when (principal) {
                    is MockAuthenticatedUser -> principal.email
                    is String -> principal
                    else -> "unknown"
                }
            }
        }
    }

    /**
     * Returns the current JWT token if available.
     *
     * @return JWT token, or null if not using JWT authentication
     */
    fun getCurrentJwt(): Jwt? {
        val context = SecurityContextHolder.getContext()
        val authentication = context?.authentication

        return when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else -> null
        }
    }

    /**
     * Converts a Principal to SessionResponse.
     *
     * @param principal The security principal
     * @return SessionResponse with user information
     */
    fun of(principal: Principal?): SessionResponse =
        when (principal) {
            is JwtAuthenticationToken -> {
                val jwt = principal.token
                SessionResponse(
                    authenticated = true,
                    userId = jwt.subject ?: "unknown",
                    email = jwt.getClaimAsString("email") ?: "",
                    roles =
                        principal.authorities
                            ?.mapNotNull { it.authority }
                            ?.filter { it.startsWith("ROLE_") }
                            ?.map { it.removePrefix("ROLE_") }
                            ?: emptyList(),
                )
            }
            is OAuth2AuthenticationToken -> {
                val oidcUser = principal.principal as? OidcUser
                SessionResponse(
                    authenticated = true,
                    userId = oidcUser?.subject ?: "unknown",
                    email = oidcUser?.email ?: "",
                    roles =
                        principal.authorities
                            ?.mapNotNull { it.authority }
                            ?.filter { it.startsWith("ROLE_") }
                            ?.map { it.removePrefix("ROLE_") }
                            ?: emptyList(),
                )
            }
            else -> {
                // Try to get from SecurityContext for Mock auth
                val context = SecurityContextHolder.getContext()
                val auth = context?.authentication
                val mockUser = auth?.principal as? MockAuthenticatedUser

                if (mockUser != null) {
                    SessionResponse(
                        authenticated = true,
                        userId = mockUser.id,
                        email = mockUser.email,
                        roles = mockUser.roles,
                    )
                } else {
                    SessionResponse(
                        authenticated = false,
                        userId = "",
                        email = "",
                        roles = emptyList(),
                    )
                }
            }
        }

    /**
     * Creates a SessionResponse from the current security context.
     *
     * @return SessionResponse with current user information
     */
    fun currentSession(): SessionResponse {
        val context = SecurityContextHolder.getContext()
        val authentication = context?.authentication

        if (authentication == null || !authentication.isAuthenticated) {
            return SessionResponse(
                authenticated = false,
                userId = "",
                email = "",
                roles = emptyList(),
            )
        }

        return when (authentication) {
            is JwtAuthenticationToken -> {
                val jwt = authentication.token
                SessionResponse(
                    authenticated = true,
                    userId = jwt.subject ?: "unknown",
                    email = jwt.getClaimAsString("email") ?: "",
                    roles = getCurrentRoles(),
                )
            }
            is OAuth2AuthenticationToken -> {
                val oidcUser = authentication.principal as? OidcUser
                SessionResponse(
                    authenticated = true,
                    userId = oidcUser?.subject ?: "unknown",
                    email = oidcUser?.email ?: "",
                    roles = getCurrentRoles(),
                )
            }
            else -> {
                val principal = authentication.principal
                when (principal) {
                    is MockAuthenticatedUser ->
                        SessionResponse(
                            authenticated = true,
                            userId = principal.id,
                            email = principal.email,
                            roles = principal.roles,
                        )
                    else ->
                        SessionResponse(
                            authenticated = true,
                            userId = getCurrentUserSubject() ?: "unknown",
                            email = getCurrentUsername(),
                            roles = getCurrentRoles(),
                        )
                }
            }
        }
    }
}
