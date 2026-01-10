package com.dataops.basecamp.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Security configuration properties for OAuth2 and mock authentication.
 */
@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    /**
     * Enable mock authentication for local/test profiles (bypasses OAuth2).
     * When true, the MockAuthenticationFilter is activated.
     */
    val mockAuthEnabled: Boolean = false,
    /**
     * Default mock user configuration for development.
     */
    val mockUser: MockUserProperties = MockUserProperties(),
)

/**
 * Mock user configuration for local development.
 */
data class MockUserProperties(
    val id: String = "1",
    val email: String = "dev@dataops.local",
    val roles: List<String> = listOf("admin", "editor", "viewer"),
)
