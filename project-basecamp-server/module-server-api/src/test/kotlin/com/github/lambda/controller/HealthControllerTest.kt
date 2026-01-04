package com.github.lambda.controller

import com.github.lambda.common.enums.HealthStatus
import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.model.health.ComponentHealth
import com.github.lambda.domain.service.HealthService
import com.github.lambda.exception.GlobalExceptionHandler
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * HealthController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler
 */
@WebMvcTest(HealthController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class HealthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean(relaxed = true)
    private lateinit var healthService: HealthService

    @MockkBean(relaxed = true)
    private lateinit var buildProperties: BuildProperties

    private lateinit var healthyComponents: Map<String, ComponentHealth>
    private lateinit var unhealthyComponents: Map<String, ComponentHealth>
    private lateinit var mixedComponents: Map<String, ComponentHealth>

    @BeforeEach
    fun setUp() {
        // Setup healthy components
        healthyComponents =
            mapOf(
                "database" to
                    ComponentHealth.up(
                        mapOf(
                            "type" to "mysql",
                            "pool" to mapOf("active" to 5, "idle" to 10, "max" to 20),
                        ),
                    ),
                "redis" to
                    ComponentHealth.up(
                        mapOf("mode" to "standalone", "version" to "7.0.11"),
                    ),
                "airflow" to
                    ComponentHealth.unknown(
                        mapOf("note" to "Airflow integration pending", "version" to null, "dagCount" to null),
                    ),
            )

        // Setup unhealthy components (database down)
        unhealthyComponents =
            mapOf(
                "database" to
                    ComponentHealth.down(
                        error = "Connection timeout",
                        details = mapOf("type" to "mysql"),
                    ),
                "redis" to
                    ComponentHealth.up(
                        mapOf("mode" to "standalone", "version" to "7.0.11"),
                    ),
                "airflow" to
                    ComponentHealth.unknown(
                        mapOf("note" to "Airflow integration pending"),
                    ),
            )

        // Setup mixed components (all unknown)
        mixedComponents =
            mapOf(
                "database" to ComponentHealth.unknown(),
                "redis" to ComponentHealth.unknown(),
                "airflow" to ComponentHealth.unknown(),
            )

        // Default build properties setup
        every { buildProperties.version } returns "1.0.0-TEST"
        every { buildProperties.time } returns java.time.Instant.now()
    }

    @Nested
    @DisplayName("GET /api/health")
    inner class LegacyHealth {
        @Test
        @DisplayName("should return simple pong response with status UP")
        fun `should return simple pong response with status UP`() {
            // When & Then
            mockMvc
                .perform(get("/api/health"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("dataops-basecamp-server"))
                .andExpect(jsonPath("$.data.timestamp").exists())
        }

        @Test
        @DisplayName("should include version from build properties")
        fun `should include version from build properties`() {
            // Given
            every { buildProperties.version } returns "2.0.0"

            // When & Then
            mockMvc
                .perform(get("/api/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.version").value("2.0.0"))
        }

        @Test
        @DisplayName("should include buildTime from build properties")
        fun `should include buildTime from build properties`() {
            // Given
            val buildTime = java.time.Instant.parse("2026-01-01T12:00:00Z")
            every { buildProperties.time } returns buildTime

            // When & Then
            mockMvc
                .perform(get("/api/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.buildTime").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/health")
    inner class BasicHealth {
        @Test
        @DisplayName("should return 200 with component health status when all healthy")
        fun `should return 200 with component health status when all healthy`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.components").exists())
                .andExpect(jsonPath("$.components.database.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.airflow.status").value("UNKNOWN"))

            verify(exactly = 1) { healthService.checkHealth() }
            verify(exactly = 1) { healthService.getOverallStatus(healthyComponents) }
        }

        @Test
        @DisplayName("should return 200 when overall status is UP")
        fun `should return 200 when overall status is UP`() {
            // Given
            val allUpComponents =
                mapOf(
                    "database" to ComponentHealth.up(),
                    "redis" to ComponentHealth.up(),
                    "airflow" to ComponentHealth.up(),
                )
            every { healthService.checkHealth() } returns allUpComponents
            every { healthService.getOverallStatus(allUpComponents) } returns HealthStatus.UP

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("UP"))
        }

        @Test
        @DisplayName("should return 503 when database is DOWN")
        fun `should return 503 when database is DOWN`() {
            // Given
            every { healthService.checkHealth() } returns unhealthyComponents
            every { healthService.getOverallStatus(unhealthyComponents) } returns HealthStatus.DOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.database.status").value("DOWN"))
                .andExpect(jsonPath("$.components.database.details.error").value("Connection timeout"))
        }

        @Test
        @DisplayName("should return 200 when overall status is UNKNOWN")
        fun `should return 200 when overall status is UNKNOWN`() {
            // Given
            every { healthService.checkHealth() } returns mixedComponents
            every { healthService.getOverallStatus(mixedComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
        }

        @Test
        @DisplayName("should include database pool details when available")
        fun `should include database pool details when available`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.database.details.type").value("mysql"))
                .andExpect(jsonPath("$.components.database.details.pool.active").value(5))
                .andExpect(jsonPath("$.components.database.details.pool.idle").value(10))
                .andExpect(jsonPath("$.components.database.details.pool.max").value(20))
        }

        @Test
        @DisplayName("should include Redis mode and version")
        fun `should include Redis mode and version`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.redis.details.mode").value("standalone"))
                .andExpect(jsonPath("$.components.redis.details.version").value("7.0.11"))
        }

        @Test
        @DisplayName("should include Airflow pending note")
        fun `should include Airflow pending note`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.airflow.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.airflow.details.note").value("Airflow integration pending"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/health/extended")
    inner class ExtendedHealth {
        @Test
        @DisplayName("should return extended health with version info")
        fun `should return extended health with version info`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN
            every { buildProperties.version } returns "1.2.3"

            // When & Then
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.version.api").value("v1"))
                .andExpect(jsonPath("$.version.build").value("1.2.3"))
                .andExpect(jsonPath("$.components").exists())

            verify(exactly = 1) { healthService.checkHealth() }
        }

        @Test
        @DisplayName("should return 503 when any component is DOWN")
        fun `should return 503 when any component is DOWN`() {
            // Given
            every { healthService.checkHealth() } returns unhealthyComponents
            every { healthService.getOverallStatus(unhealthyComponents) } returns HealthStatus.DOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("DOWN"))
        }

        @Test
        @DisplayName("should include connection pool details for database")
        fun `should include connection pool details for database`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.database.status").value("UP"))
                .andExpect(jsonPath("$.components.database.type").value("mysql"))
                .andExpect(jsonPath("$.components.database.connectionPool.active").value(5))
                .andExpect(jsonPath("$.components.database.connectionPool.idle").value(10))
                .andExpect(jsonPath("$.components.database.connectionPool.max").value(20))
        }

        @Test
        @DisplayName("should include Redis mode and version in extended format")
        fun `should include Redis mode and version in extended format`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.mode").value("standalone"))
                .andExpect(jsonPath("$.components.redis.version").value("7.0.11"))
        }

        @Test
        @DisplayName("should include Airflow mock status in extended format")
        fun `should include Airflow mock status in extended format`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN

            // When & Then
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.components.airflow.status").value("UNKNOWN"))
        }

        @Test
        @DisplayName("should handle null build version gracefully")
        fun `should handle null build version gracefully`() {
            // Given
            every { healthService.checkHealth() } returns healthyComponents
            every { healthService.getOverallStatus(healthyComponents) } returns HealthStatus.UNKNOWN
            every { buildProperties.version } returns null

            // When & Then
            // Note: When build version is null, @JsonInclude(NON_NULL) excludes the field
            mockMvc
                .perform(get("/api/v1/health/extended"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.version.api").value("v1"))
                .andExpect(jsonPath("$.version.build").doesNotExist())
        }
    }

    @Nested
    @DisplayName("GET /api/info")
    inner class Info {
        @Test
        @DisplayName("should return service info")
        fun `should return service info`() {
            // When & Then
            mockMvc
                .perform(get("/api/info"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("DataOps Basecamp Server"))
                .andExpect(jsonPath("$.data.description").value(containsString("데이터 파이프라인")))
                .andExpect(jsonPath("$.data.timestamp").exists())
        }

        @Test
        @DisplayName("should include build info from build properties")
        fun `should include build info from build properties`() {
            // Given
            every { buildProperties.version } returns "1.2.3"
            every { buildProperties.group } returns "com.github.lambda"
            every { buildProperties.artifact } returns "basecamp-server"
            every { buildProperties.time } returns java.time.Instant.parse("2026-01-01T12:00:00Z")

            // When & Then
            mockMvc
                .perform(get("/api/info"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.build.version").value("1.2.3"))
                .andExpect(jsonPath("$.data.build.group").value("com.github.lambda"))
                .andExpect(jsonPath("$.data.build.artifact").value("basecamp-server"))
        }
    }
}
