package com.dataops.basecamp.config

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import com.dataops.basecamp.domain.service.HealthService
import com.dataops.basecamp.domain.service.TeamService
import com.dataops.basecamp.filter.MockAuthenticationFilter
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * SecurityConfig Integration Tests
 *
 * Tests the security configuration with Mock authentication mode enabled.
 * Verifies:
 * - Public endpoints are accessible without authentication
 * - Protected endpoints require authentication (via Mock headers)
 * - CORS OPTIONS requests are allowed
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("SecurityConfig Integration Tests")
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean(relaxed = true)
    private lateinit var healthService: HealthService

    @MockkBean(relaxed = true)
    private lateinit var buildProperties: BuildProperties

    @MockkBean(relaxed = true)
    private lateinit var teamService: TeamService

    @MockkBean(relaxed = true)
    private lateinit var userRepositoryJpa: UserRepositoryJpa

    @BeforeEach
    fun setUp() {
        // Setup healthy components to avoid 503 errors
        every { healthService.checkHealth() } returns emptyMap()
        every { healthService.getOverallStatus(any()) } returns com.dataops.basecamp.common.enums.HealthStatus.UP
        every { buildProperties.version } returns "1.0.0-TEST"

        // Setup team service returns
        every { teamService.getTeamsByUserId(any()) } returns emptyList()
        every { userRepositoryJpa.findByEmail(any()) } returns null
    }

    @Nested
    @DisplayName("Public Endpoints")
    inner class PublicEndpoints {
        @Test
        @DisplayName("GET /api/health should be accessible without authentication")
        fun `GET api health should be accessible without authentication`() {
            mockMvc
                .perform(get("${CommonConstants.Api.BASE_PATH}${CommonConstants.Api.HEALTH_PATH}"))
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("GET /api/v1/health should be accessible without authentication")
        fun `GET api v1 health should be accessible without authentication`() {
            mockMvc
                .perform(get("${CommonConstants.Api.BASE_PATH}/v1/health"))
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("GET /api/info should be accessible without authentication")
        fun `GET api info should be accessible without authentication`() {
            mockMvc
                .perform(get("${CommonConstants.Api.BASE_PATH}/info"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }

        @Test
        @DisplayName("GET /actuator/health should be accessible without authentication")
        fun `GET actuator health should be accessible without authentication`() {
            mockMvc
                .perform(get("/actuator/health"))
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("GET /swagger-ui.html should be accessible without authentication")
        fun `GET swagger-ui html should be accessible without authentication`() {
            // Swagger may redirect, so we check for 2xx or 3xx status
            mockMvc
                .perform(get("/swagger-ui.html"))
                .andExpect { result ->
                    val status = result.response.status
                    assert(status in 200..399) { "Expected 2xx or 3xx status but got $status" }
                }
        }
    }

    @Nested
    @DisplayName("Protected Endpoints with Mock Authentication")
    inner class ProtectedEndpointsWithMockAuth {
        @Test
        @DisplayName("should authenticate with default mock user when no headers provided")
        fun `should authenticate with default mock user when no headers provided`() {
            // In test profile with mock auth enabled, requests are auto-authenticated
            // The MockAuthenticationFilter should set default user
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
        }

        @Test
        @DisplayName("should authenticate with custom user ID from header")
        fun `should authenticate with custom user ID from header`() {
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .header(MockAuthenticationFilter.HEADER_USER_ID, "999")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("999"))
        }

        @Test
        @DisplayName("should authenticate with custom email from header")
        fun `should authenticate with custom email from header`() {
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .header(MockAuthenticationFilter.HEADER_USER_EMAIL, "test@custom.com")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.email").value("test@custom.com"))
        }

        @Test
        @DisplayName("should authenticate with custom roles from header")
        fun `should authenticate with custom roles from header`() {
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .header(MockAuthenticationFilter.HEADER_USER_ROLES, "custom-role")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.roles[0]").value("custom-role"))
        }

        @Test
        @DisplayName("should authenticate with all custom headers")
        fun `should authenticate with all custom headers`() {
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .header(MockAuthenticationFilter.HEADER_USER_ID, "42")
                        .header(MockAuthenticationFilter.HEADER_USER_EMAIL, "full@custom.test")
                        .header(MockAuthenticationFilter.HEADER_USER_ROLES, "admin,operator")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.email").value("full@custom.test"))
                .andExpect(jsonPath("$.roles").isArray)
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    inner class CorsConfiguration {
        @Test
        @DisplayName("OPTIONS requests should be allowed for CORS preflight")
        fun `OPTIONS requests should be allowed for CORS preflight`() {
            mockMvc
                .perform(
                    options("/api/session/whoami")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"),
                ).andExpect(status().isOk)
        }

        @Test
        @DisplayName("OPTIONS to protected endpoint should not require authentication")
        fun `OPTIONS to protected endpoint should not require authentication`() {
            mockMvc
                .perform(
                    options("${CommonConstants.Api.BASE_PATH}/v1/metrics")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"),
                ).andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("Session Endpoint")
    inner class SessionEndpoint {
        @Test
        @DisplayName("GET /api/session/whoami should return current user session")
        fun `GET api session whoami should return current user session`() {
            mockMvc
                .perform(
                    get("/api/session/whoami")
                        .header(MockAuthenticationFilter.HEADER_USER_ID, "123")
                        .header(MockAuthenticationFilter.HEADER_USER_EMAIL, "session@test.com")
                        .header(MockAuthenticationFilter.HEADER_USER_ROLES, "viewer,editor")
                        .accept(MediaType.APPLICATION_JSON),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("123"))
                .andExpect(jsonPath("$.email").value("session@test.com"))
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("GET /error should be accessible without authentication")
        fun `GET error should be accessible without authentication`() {
            // The /error endpoint is for Spring Boot error handling
            // It should be accessible for error responses
            mockMvc
                .perform(get("/error"))
                .andExpect { result ->
                    // /error may return 404 or 500 depending on context
                    // but it should not return 401/403
                    val status = result.response.status
                    assert(status != 401 && status != 403) {
                        "Expected non-auth error status but got $status"
                    }
                }
        }
    }
}
